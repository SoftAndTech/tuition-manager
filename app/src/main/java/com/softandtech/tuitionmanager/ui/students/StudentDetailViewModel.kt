package com.softandtech.tuitionmanager.ui.students

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.data.db.entity.BatchEntity
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import com.softandtech.tuitionmanager.data.db.model.EnrollmentWithBatch
import com.softandtech.tuitionmanager.data.repository.BatchRepository
import com.softandtech.tuitionmanager.data.repository.StudentRepository
import com.softandtech.tuitionmanager.domain.Money
import com.softandtech.tuitionmanager.ui.navigation.StudentDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StudentDetailUiState(
    val loading: Boolean = true,
    val student: StudentEntity? = null,
    val enrollments: List<EnrollmentWithBatch> = emptyList(),
    /** Active batches the student is not currently in — choices for "Add to batch". */
    val availableBatches: List<BatchEntity> = emptyList(),
)

@HiltViewModel
class StudentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val studentRepository: StudentRepository,
    private val batchRepository: BatchRepository,
    private val clock: Clock,
) : ViewModel() {

    private val studentId = savedStateHandle.toRoute<StudentDetailRoute>().studentId

    val today: LocalDate get() = LocalDate.now(clock)

    val state: StateFlow<StudentDetailUiState> = combine(
        studentRepository.observeStudent(studentId),
        batchRepository.observeEnrollments(studentId),
        batchRepository.observeActive(),
    ) { student, enrollments, batches ->
        val currentBatchIds = enrollments.filter { it.isCurrent }.map { it.enrollment.batchId }.toSet()
        StudentDetailUiState(
            loading = false,
            student = student,
            enrollments = enrollments,
            availableBatches = batches.filter { it.id !in currentBatchIds },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentDetailUiState())

    /** Returns an error message if the fee is invalid; otherwise enrolls and returns null. */
    @StringRes
    fun addToBatch(batchId: Long, joinedOn: LocalDate, customFee: String): Int? {
        val fee = if (customFee.isBlank()) null else Money.parse(customFee) ?: return R.string.error_amount_invalid
        viewModelScope.launch { batchRepository.enroll(studentId, batchId, joinedOn, fee) }
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

    fun rejoin() {
        viewModelScope.launch { studentRepository.reactivate(studentId) }
    }
}
