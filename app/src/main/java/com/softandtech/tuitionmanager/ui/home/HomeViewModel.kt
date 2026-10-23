package com.softandtech.tuitionmanager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.data.db.model.StudentOverdue
import com.softandtech.tuitionmanager.data.repository.FeeRepository
import com.softandtech.tuitionmanager.data.repository.FeeSync
import com.softandtech.tuitionmanager.data.repository.StudentRepository
import com.softandtech.tuitionmanager.data.settings.SettingsRepository
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

data class HomeUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val billedPaise: Long = 0,
    val collectedPaise: Long = 0,
    val overdue: List<StudentOverdue> = emptyList(),
    val hasProfile: Boolean = true,
) {
    val overdueTotalPaise: Long get() = overdue.sumOf { it.outstandingPaise }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val feeRepository: FeeRepository,
    private val studentRepository: StudentRepository,
    private val settingsRepository: SettingsRepository,
    private val reminderText: ReminderTextProvider,
    feeSync: FeeSync,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)
    private val month = YearMonth.from(today)

    private val _events = Channel<StudentDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<HomeUiState> = combine(
        feeRepository.observeBilled(month),
        feeRepository.observeCollected(month),
        feeRepository.observeOverdue(today),
        settingsRepository.settings,
    ) { billed, collected, overdue, settings ->
        HomeUiState(
            loading = false,
            month = month,
            billedPaise = billed,
            collectedPaise = collected,
            overdue = overdue,
            hasProfile = settings.signature.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(month = month))

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
