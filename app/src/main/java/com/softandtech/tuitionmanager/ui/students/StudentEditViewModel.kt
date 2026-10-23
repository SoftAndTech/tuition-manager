package com.softandtech.tuitionmanager.ui.students

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.data.db.entity.BatchEntity
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import com.softandtech.tuitionmanager.data.repository.BatchRepository
import com.softandtech.tuitionmanager.data.repository.NewEnrollment
import com.softandtech.tuitionmanager.data.repository.StudentRepository
import com.softandtech.tuitionmanager.domain.Money
import com.softandtech.tuitionmanager.domain.Phone
import com.softandtech.tuitionmanager.ui.navigation.NEW_ID
import com.softandtech.tuitionmanager.ui.navigation.StudentEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BatchChoice(
    val batch: BatchEntity,
    val selected: Boolean = false,
    val customFee: String = "",
    @StringRes val feeError: Int? = null,
)

data class StudentEditUiState(
    val isNew: Boolean,
    val loading: Boolean,
    val currentMonth: YearMonth,
    val joinDate: LocalDate,
    val name: String = "",
    val phone: String = "",
    val parentName: String = "",
    val parentPhone: String = "",
    val className: String = "",
    val notes: String = "",
    val batches: List<BatchChoice> = emptyList(),
    /** For students who joined in an earlier month: true = bill from joining month, false = from this month. */
    val billFromJoinMonth: Boolean = false,
    @StringRes val nameError: Int? = null,
    @StringRes val phoneError: Int? = null,
    @StringRes val parentPhoneError: Int? = null,
    val saving: Boolean = false,
    val savedStudentId: Long? = null,
    val hasChanges: Boolean = false,
) {
    val joinMonth: YearMonth get() = YearMonth.from(joinDate)
    val showBillFromChoice: Boolean get() = isNew && batches.any { it.selected } && joinMonth < currentMonth
}

@HiltViewModel
class StudentEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val studentRepository: StudentRepository,
    private val batchRepository: BatchRepository,
    clock: Clock,
) : ViewModel() {

    private val studentId = savedStateHandle.toRoute<StudentEditRoute>().studentId
    private var original: StudentEntity? = null
    private val today = LocalDate.now(clock)

    private val _state = MutableStateFlow(
        StudentEditUiState(
            isNew = studentId == NEW_ID,
            loading = true,
            currentMonth = YearMonth.from(today),
            joinDate = today,
        ),
    )
    val state: StateFlow<StudentEditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (studentId == NEW_ID) {
                val batches = batchRepository.observeActive().first()
                _state.update { it.copy(loading = false, batches = batches.map(::BatchChoice)) }
            } else {
                val student = studentRepository.getById(studentId)
                original = student
                _state.update {
                    if (student == null) it.copy(loading = false)
                    else it.copy(
                        loading = false,
                        name = student.name,
                        phone = student.phone.orEmpty(),
                        parentName = student.parentName.orEmpty(),
                        parentPhone = student.parentPhone.orEmpty(),
                        className = student.className.orEmpty(),
                        notes = student.notes.orEmpty(),
                        joinDate = student.joinDate,
                    )
                }
            }
        }
    }

    fun onNameChange(v: String) = edit { it.copy(name = v, nameError = null) }
    fun onPhoneChange(v: String) = edit { it.copy(phone = v, phoneError = null) }
    fun onParentNameChange(v: String) = edit { it.copy(parentName = v) }
    fun onParentPhoneChange(v: String) = edit { it.copy(parentPhone = v, parentPhoneError = null) }
    fun onClassChange(v: String) = edit { it.copy(className = v) }
    fun onNotesChange(v: String) = edit { it.copy(notes = v) }
    fun onJoinDateChange(v: LocalDate) = edit { it.copy(joinDate = v) }
    fun onBillFromJoinMonthChange(v: Boolean) = edit { it.copy(billFromJoinMonth = v) }

    fun onBatchToggle(batchId: Long) = updateBatch(batchId) { it.copy(selected = !it.selected) }

    fun onBatchFeeChange(batchId: Long, fee: String) = updateBatch(batchId) { it.copy(customFee = fee, feeError = null) }

    private fun updateBatch(batchId: Long, transform: (BatchChoice) -> BatchChoice) = edit { s ->
        s.copy(batches = s.batches.map { if (it.batch.id == batchId) transform(it) else it })
    }

    /** Applies a user edit and remembers that the form now has unsaved changes. */
    private fun edit(transform: (StudentEditUiState) -> StudentEditUiState) =
        _state.update { transform(it).copy(hasChanges = true) }

    fun save() {
        val s = _state.value
        if (s.saving) return

        val nameError = if (s.name.isBlank()) R.string.error_name_required else null
        val phoneError = phoneError(s.phone)
        val parentPhoneError = phoneError(s.parentPhone)
        val batches = s.batches.map { choice ->
            val invalidFee = choice.selected && choice.customFee.isNotBlank() && Money.parse(choice.customFee) == null
            choice.copy(feeError = if (invalidFee) R.string.error_amount_invalid else null)
        }
        if (nameError != null || phoneError != null || parentPhoneError != null || batches.any { it.feeError != null }) {
            _state.update { it.copy(nameError = nameError, phoneError = phoneError, parentPhoneError = parentPhoneError, batches = batches) }
            return
        }

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val student = (original ?: StudentEntity(name = "", joinDate = s.joinDate)).copy(
                name = s.name.trim(),
                phone = Phone.normalize(s.phone),
                parentName = s.parentName.trim().ifEmpty { null },
                parentPhone = Phone.normalize(s.parentPhone),
                className = s.className.trim().ifEmpty { null },
                notes = s.notes.trim().ifEmpty { null },
                joinDate = s.joinDate,
            )
            val id = if (s.isNew) {
                val billFrom = if (s.billFromJoinMonth || s.joinMonth >= s.currentMonth) s.joinMonth else s.currentMonth
                val enrollments = s.batches.filter { it.selected }.map {
                    NewEnrollment(
                        batchId = it.batch.id,
                        feeOverridePaise = it.customFee.takeIf(String::isNotBlank)?.let(Money::parse),
                        billFrom = billFrom,
                    )
                }
                studentRepository.add(student, enrollments)
            } else {
                studentRepository.update(student)
                student.id
            }
            _state.update { it.copy(saving = false, savedStudentId = id) }
        }
    }

    private fun phoneError(phone: String): Int? =
        if (phone.isNotBlank() && !Phone.isValid(phone)) R.string.error_phone_invalid else null
}
