package com.softandtech.tuitionmanager.ui.students

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.data.db.entity.BatchEntity
import com.softandtech.tuitionmanager.data.db.entity.PaymentEntity
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import com.softandtech.tuitionmanager.data.db.model.EnrollmentWithBatch
import com.softandtech.tuitionmanager.data.db.model.FeeDueWithPaid
import com.softandtech.tuitionmanager.data.repository.AttendanceRepository
import com.softandtech.tuitionmanager.data.repository.BatchRepository
import com.softandtech.tuitionmanager.data.repository.FeeRepository
import com.softandtech.tuitionmanager.data.repository.FeeSync
import com.softandtech.tuitionmanager.data.repository.StudentRepository
import com.softandtech.tuitionmanager.data.settings.AppSettings
import com.softandtech.tuitionmanager.data.settings.SettingsRepository
import com.softandtech.tuitionmanager.data.billing.ProRepository
import com.softandtech.tuitionmanager.domain.AttendanceSummary
import com.softandtech.tuitionmanager.domain.Plan
import kotlinx.coroutines.flow.first
import com.softandtech.tuitionmanager.domain.Money
import com.softandtech.tuitionmanager.domain.model.PaymentMode
import com.softandtech.tuitionmanager.ui.navigation.StudentDetailRoute
import com.softandtech.tuitionmanager.ui.receipt.ReceiptPdfWriter
import com.softandtech.tuitionmanager.ui.receipt.ReminderTextProvider
import com.softandtech.tuitionmanager.ui.receipt.Reminders
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StudentFees(
    val dues: List<FeeDueWithPaid> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
    val outstandingPaise: Long = 0,
    val advancePaise: Long = 0,
    val settings: AppSettings = AppSettings(),
)

data class StudentDetailUiState(
    val loading: Boolean = true,
    val student: StudentEntity? = null,
    val enrollments: List<EnrollmentWithBatch> = emptyList(),
    /** Active batches the student is not currently in — choices for "Add to batch". */
    val availableBatches: List<BatchEntity> = emptyList(),
    val fees: StudentFees = StudentFees(),
)

/** One-shot actions the screen must perform with an Activity (share sheet, WhatsApp) or show once. */
sealed interface StudentDetailEvent {
    data class PaymentSaved(val payment: PaymentEntity) : StudentDetailEvent
    data class ShareReceipt(val file: File, val message: String) : StudentDetailEvent
    data class OpenReminder(val waNumber: String, val message: String) : StudentDetailEvent
    data class Message(@StringRes val text: Int) : StudentDetailEvent
    data object UpgradeNeeded : StudentDetailEvent
}

@HiltViewModel
class StudentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val studentRepository: StudentRepository,
    private val batchRepository: BatchRepository,
    private val feeRepository: FeeRepository,
    attendanceRepository: AttendanceRepository,
    private val feeSync: FeeSync,
    private val settingsRepository: SettingsRepository,
    private val receiptPdfWriter: ReceiptPdfWriter,
    private val reminderText: ReminderTextProvider,
    private val proRepository: ProRepository,
    private val clock: Clock,
) : ViewModel() {

    private val studentId = savedStateHandle.toRoute<StudentDetailRoute>().studentId

    val today: LocalDate get() = LocalDate.now(clock)

    private val _events = Channel<StudentDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val fees = combine(
        feeRepository.observeDues(studentId),
        feeRepository.observePayments(studentId),
        feeRepository.observeOutstanding(studentId),
        feeRepository.observeAdvanceCredit(studentId),
        settingsRepository.settings,
        ::StudentFees,
    )

    val state: StateFlow<StudentDetailUiState> = combine(
        studentRepository.observeStudent(studentId),
        batchRepository.observeEnrollments(studentId),
        batchRepository.observeActive(),
        fees,
    ) { student, enrollments, batches, fees ->
        val currentBatchIds = enrollments.filter { it.isCurrent }.map { it.enrollment.batchId }.toSet()
        StudentDetailUiState(
            loading = false,
            student = student,
            enrollments = enrollments,
            availableBatches = batches.filter { it.id !in currentBatchIds },
            fees = fees,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentDetailUiState())

    /** Attendance for the current month so far. */
    val attendance: StateFlow<AttendanceSummary> = attendanceRepository
        .observeSummary(studentId, YearMonth.from(today).atDay(1), today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AttendanceSummary(0, 0, 0))

    init {
        // Make sure this month's dues exist before showing what the student owes
        viewModelScope.launch { feeSync.run() }
    }

    /** Returns an error message if the fee is invalid; otherwise enrolls and returns null. */
    @StringRes
    fun addToBatch(batchId: Long, joinedOn: LocalDate, customFee: String): Int? {
        val fee = if (customFee.isBlank()) null else Money.parse(customFee) ?: return R.string.error_amount_invalid
        viewModelScope.launch {
            batchRepository.enroll(studentId, batchId, joinedOn, fee)
            feeSync.run()
        }
        return null
    }

    fun leaveBatch(enrollment: EnrollmentWithBatch) {
        // A batch joined with a future date can be left "today" only by cancelling it on its join date
        val leftOn = maxOf(today, enrollment.enrollment.joinedOn)
        viewModelScope.launch { batchRepository.leave(enrollment.enrollment.id, leftOn) }
    }

    fun markLeft() {
        viewModelScope.launch { studentRepository.deactivate(studentId, today) }
    }

    /** Rejoining adds an active student, so the free-plan limit applies as it does to adding one. */
    fun rejoin() {
        viewModelScope.launch {
            if (Plan.canAdd(studentRepository.activeCount(), Plan.FREE_STUDENT_LIMIT, proRepository.isPro.first())) {
                studentRepository.reactivate(studentId)
            } else {
                _events.send(StudentDetailEvent.UpgradeNeeded)
            }
        }
    }

    /** Validates synchronously and returns an error, or saves and returns null. */
    @StringRes
    fun collectPayment(amount: String, mode: PaymentMode, paidOn: LocalDate, note: String): Int? {
        val paise = Money.parse(amount)?.takeIf { it > 0 } ?: return R.string.error_amount_invalid
        viewModelScope.launch {
            val payment = feeRepository.collectPayment(studentId, paise, mode, paidOn, note.trim().ifEmpty { null })
            _events.send(StudentDetailEvent.PaymentSaved(payment))
        }
        return null
    }

    fun shareReceipt(paymentId: Long) {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            val data = feeRepository.getReceipt(paymentId) ?: return@launch
            val file = withContext(Dispatchers.IO) { receiptPdfWriter.write(data, settings) }
            _events.send(StudentDetailEvent.ShareReceipt(file, reminderText.receiptMessage(data, settings)))
        }
    }

    fun deletePayment(paymentId: Long) {
        viewModelScope.launch { feeRepository.deletePayment(paymentId) }
    }

    fun setWaived(dueId: Long, waived: Boolean) {
        viewModelScope.launch { feeRepository.setWaived(dueId, waived) }
    }

    @StringRes
    fun addCharge(title: String, amount: String, dueDate: LocalDate): Int? {
        if (title.isBlank()) return R.string.error_title_required
        val paise = Money.parse(amount)?.takeIf { it > 0 } ?: return R.string.error_amount_invalid
        viewModelScope.launch { feeRepository.addOneTimeDue(studentId, title.trim(), paise, dueDate) }
        return null
    }

    fun sendReminder() {
        viewModelScope.launch {
            val student = state.value.student ?: return@launch
            val number = Reminders.waNumberFor(student)
            val unpaid = feeRepository.unpaidDues(studentId, today)
            val event = when {
                unpaid.isEmpty() -> StudentDetailEvent.Message(R.string.reminder_nothing_due)
                number == null -> StudentDetailEvent.Message(R.string.reminder_no_phone)
                else -> StudentDetailEvent.OpenReminder(
                    number,
                    reminderText.feeReminder(student.name, student.parentName, unpaid, settingsRepository.current()),
                )
            }
            _events.send(event)
        }
    }
}
