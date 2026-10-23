package com.softandtech.tuitionmanager.ui.batches

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.data.db.entity.BatchEntity
import com.softandtech.tuitionmanager.data.repository.BatchRepository
import com.softandtech.tuitionmanager.domain.Money
import com.softandtech.tuitionmanager.domain.WeekDays
import com.softandtech.tuitionmanager.ui.navigation.BatchEditRoute
import com.softandtech.tuitionmanager.ui.navigation.NEW_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BatchEditResult { SAVED, ARCHIVED }

data class BatchEditUiState(
    val isNew: Boolean,
    val loading: Boolean,
    val name: String = "",
    val subject: String = "",
    val days: Set<DayOfWeek> = emptySet(),
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val fee: String = "",
    val dueDay: String = "5",
    @StringRes val nameError: Int? = null,
    @StringRes val feeError: Int? = null,
    @StringRes val dueDayError: Int? = null,
    @StringRes val timeError: Int? = null,
    val saving: Boolean = false,
    val result: BatchEditResult? = null,
    val hasChanges: Boolean = false,
)

@HiltViewModel
class BatchEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val batchRepository: BatchRepository,
) : ViewModel() {

    private val batchId = savedStateHandle.toRoute<BatchEditRoute>().batchId
    private var original: BatchEntity? = null

    private val _state = MutableStateFlow(BatchEditUiState(isNew = batchId == NEW_ID, loading = batchId != NEW_ID))
    val state: StateFlow<BatchEditUiState> = _state.asStateFlow()

    init {
        if (batchId != NEW_ID) {
            viewModelScope.launch {
                val batch = batchRepository.getById(batchId)
                original = batch
                _state.update {
                    if (batch == null) it.copy(loading = false)
                    else it.copy(
                        loading = false,
                        name = batch.name,
                        subject = batch.subject.orEmpty(),
                        days = WeekDays.fromMask(batch.daysMask),
                        startMinute = batch.startMinute,
                        endMinute = batch.endMinute,
                        fee = Money.toInput(batch.monthlyFeePaise),
                        dueDay = batch.feeDueDay.toString(),
                    )
                }
            }
        }
    }

    fun onNameChange(v: String) = edit { it.copy(name = v, nameError = null) }
    fun onSubjectChange(v: String) = edit { it.copy(subject = v) }
    fun onFeeChange(v: String) = edit { it.copy(fee = v, feeError = null) }
    fun onDueDayChange(v: String) = edit { it.copy(dueDay = v.filter(Char::isDigit).take(2), dueDayError = null) }
    fun onStartChange(v: Int?) = edit { it.copy(startMinute = v, timeError = null) }
    fun onEndChange(v: Int?) = edit { it.copy(endMinute = v, timeError = null) }

    fun onDayToggle(day: DayOfWeek) = edit {
        it.copy(days = if (day in it.days) it.days - day else it.days + day)
    }

    /** Applies a user edit and remembers that the form now has unsaved changes. */
    private fun edit(transform: (BatchEditUiState) -> BatchEditUiState) =
        _state.update { transform(it).copy(hasChanges = true) }

    fun save() {
        val s = _state.value
        if (s.saving) return

        val fee = Money.parse(s.fee)
        val dueDay = s.dueDay.toIntOrNull()?.takeIf { it in 1..31 }
        val start = s.startMinute
        val end = s.endMinute
        val errors = s.copy(
            nameError = if (s.name.isBlank()) R.string.error_name_required else null,
            feeError = if (fee == null) R.string.error_amount_invalid else null,
            dueDayError = if (dueDay == null) R.string.error_due_day else null,
            timeError = if (start != null && end != null && end <= start) R.string.error_end_before_start else null,
        )
        if (errors.nameError != null || errors.feeError != null || errors.dueDayError != null || errors.timeError != null) {
            _state.value = errors
            return
        }

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val batch = (original ?: BatchEntity(name = "", monthlyFeePaise = 0)).copy(
                name = s.name.trim(),
                subject = s.subject.trim().ifEmpty { null },
                daysMask = WeekDays.toMask(s.days),
                startMinute = start,
                endMinute = if (start == null) null else end,
                monthlyFeePaise = fee!!,
                feeDueDay = dueDay!!,
            )
            if (s.isNew) batchRepository.add(batch) else batchRepository.update(batch)
            _state.update { it.copy(saving = false, result = BatchEditResult.SAVED) }
        }
    }

    fun archive() {
        viewModelScope.launch {
            batchRepository.archive(batchId)
            _state.update { it.copy(result = BatchEditResult.ARCHIVED) }
        }
    }
}
