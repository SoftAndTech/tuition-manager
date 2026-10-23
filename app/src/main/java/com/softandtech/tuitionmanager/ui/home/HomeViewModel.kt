package com.softandtech.tuitionmanager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.data.db.model.BatchWithCount
import com.softandtech.tuitionmanager.data.db.model.StudentOverdue
import com.softandtech.tuitionmanager.data.repository.AttendanceRepository
import com.softandtech.tuitionmanager.data.repository.BatchRepository
import com.softandtech.tuitionmanager.data.repository.FeeRepository
import com.softandtech.tuitionmanager.data.repository.FeeSync
import com.softandtech.tuitionmanager.data.repository.StudentRepository
import com.softandtech.tuitionmanager.data.security.SecurityRepository
import com.softandtech.tuitionmanager.data.settings.SettingsRepository
import com.softandtech.tuitionmanager.domain.WeekDays
import com.softandtech.tuitionmanager.ui.receipt.ReminderTextProvider
import com.softandtech.tuitionmanager.ui.receipt.Reminders
import com.softandtech.tuitionmanager.ui.students.StudentDetailEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A batch meeting today, and how many of its students have been marked. */
data class TodayClass(val batch: BatchWithCount, val markedCount: Int) {
    val isMarked: Boolean get() = markedCount > 0
}

data class HomeUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val billedPaise: Long = 0,
    val collectedPaise: Long = 0,
    val overdue: List<StudentOverdue> = emptyList(),
    val hasProfile: Boolean = true,
    val todayClasses: List<TodayClass> = emptyList(),
    val activeStudents: Int = 0,
    val hasBatches: Boolean = false,
    /** There are students and no backup in the last [BACKUP_REMINDER_DAYS] days. */
    val backupDue: Boolean = false,
    val lastBackup: LocalDate? = null,
) {
    val month: YearMonth get() = YearMonth.from(today)
    val overdueTotalPaise: Long get() = overdue.sumOf { it.outstandingPaise }
}

/** Losing a phone loses everything not backed up; nudge monthly. */
const val BACKUP_REMINDER_DAYS = 30L

private data class FeeOverview(
    val billed: Long,
    val collected: Long,
    val overdue: List<StudentOverdue>,
    val hasProfile: Boolean,
    val lastBackup: LocalDate?,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val feeRepository: FeeRepository,
    private val studentRepository: StudentRepository,
    private val settingsRepository: SettingsRepository,
    private val reminderText: ReminderTextProvider,
    batchRepository: BatchRepository,
    attendanceRepository: AttendanceRepository,
    securityRepository: SecurityRepository,
    feeSync: FeeSync,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)
    private val month = YearMonth.from(today)

    private val _events = Channel<StudentDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val feeOverview = combine(
        feeRepository.observeBilled(month),
        feeRepository.observeCollected(month),
        feeRepository.observeOverdue(today),
        settingsRepository.settings,
        securityRepository.settings,
    ) { billed, collected, overdue, settings, security ->
        FeeOverview(billed, collected, overdue, settings.signature.isNotEmpty(), security.lastBackup)
    }

    val state: StateFlow<HomeUiState> = combine(
        feeOverview,
        batchRepository.observeActiveWithCount(),
        attendanceRepository.observeMarkedCounts(today),
        studentRepository.observeActiveCount(),
    ) { fees, batches, marked, studentCount ->
        HomeUiState(
            loading = false,
            today = today,
            billedPaise = fees.billed,
            collectedPaise = fees.collected,
            overdue = fees.overdue,
            hasProfile = fees.hasProfile,
            todayClasses = WeekDays.meetingOn(batches, today.dayOfWeek) { it.batch.daysMask }
                .map { TodayClass(it, marked[it.batch.id] ?: 0) },
            activeStudents = studentCount,
            hasBatches = batches.isNotEmpty(),
            backupDue = studentCount > 0 &&
                (fees.lastBackup == null || fees.lastBackup.plusDays(BACKUP_REMINDER_DAYS) <= today),
            lastBackup = fees.lastBackup,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(today = today))

    init {
        viewModelScope.launch { feeSync.run() }
    }

    fun remind(item: StudentOverdue) {
        viewModelScope.launch {
            val student = studentRepository.getById(item.studentId) ?: return@launch
            val number = Reminders.waNumberFor(student)
            val unpaid = feeRepository.unpaidDues(student.id, today)
            _events.send(
                when {
                    number == null -> StudentDetailEvent.Message(R.string.reminder_no_phone)
                    unpaid.isEmpty() -> StudentDetailEvent.Message(R.string.reminder_nothing_due)
                    else -> StudentDetailEvent.OpenReminder(
                        number,
                        reminderText.feeReminder(student.name, student.parentName, unpaid, settingsRepository.current()),
                    )
                },
            )
        }
    }
}
