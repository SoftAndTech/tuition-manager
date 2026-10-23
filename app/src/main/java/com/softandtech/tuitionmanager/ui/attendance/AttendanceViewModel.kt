package com.softandtech.tuitionmanager.ui.attendance

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.softandtech.tuitionmanager.data.db.entity.BatchEntity
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import com.softandtech.tuitionmanager.data.repository.AttendanceRepository
import com.softandtech.tuitionmanager.data.repository.BatchRepository
import com.softandtech.tuitionmanager.domain.WeekDays
import com.softandtech.tuitionmanager.domain.model.AttendanceStatus
import com.softandtech.tuitionmanager.ui.navigation.AttendanceRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AttendanceUiState(
    val loading: Boolean = true,
    val batch: BatchEntity? = null,
    val date: LocalDate,
    val today: LocalDate,
    val students: List<StudentEntity> = emptyList(),
    /** What each student is shown as: unsaved edit, else saved mark, else Present. */
    val marks: Map<Long, AttendanceStatus> = emptyMap(),
    /** True once a register has been saved for this date. */
    val alreadyMarked: Boolean = false,
    val hasChanges: Boolean = false,
) {
    fun count(status: AttendanceStatus) = students.count { marks[it.id] == status }

    /** The batch has set class days and this date isn't one of them. */
    val notAClassDay: Boolean
        get() = batch != null && batch.daysMask != 0 && !WeekDays.contains(batch.daysMask, date.dayOfWeek)
}

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    batchRepository: BatchRepository,
    private val attendanceRepository: AttendanceRepository,
    clock: Clock,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<AttendanceRoute>()
    private val batchId = route.batchId
    private val today = LocalDate.now(clock)

    private val date = MutableStateFlow(minOf(LocalDate.ofEpochDay(route.epochDay), today))
    private val edits = MutableStateFlow<Map<Long, AttendanceStatus>>(emptyMap())

    private val _saved = Channel<Unit>(Channel.CONFLATED)
    val saved = _saved.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val register = date.flatMapLatest { d ->
        combine(
            batchRepository.observeStudentsInBatch(batchId, d),
            attendanceRepository.observeForBatchOnDate(batchId, d),
        ) { students, saved -> Triple(d, students, saved.associate { it.studentId to it.status }) }
    }

    val state: StateFlow<AttendanceUiState> = combine(
        batchRepository.observeBatch(batchId),
        register,
        edits,
    ) { batch, (d, students, saved), edits ->
        val marks = students.associate { s -> s.id to (edits[s.id] ?: saved[s.id] ?: AttendanceStatus.PRESENT) }
        AttendanceUiState(
            loading = false,
            batch = batch,
            date = d,
            today = today,
            students = students,
            marks = marks,
            alreadyMarked = saved.isNotEmpty(),
            hasChanges = edits.any { (id, status) -> (saved[id] ?: AttendanceStatus.PRESENT) != status } ||
                (saved.isEmpty() && students.isNotEmpty() && edits.isNotEmpty()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AttendanceUiState(date = date.value, today = today))

    fun setStatus(studentId: Long, status: AttendanceStatus) {
        edits.value = edits.value + (studentId to status)
    }

    fun markAllPresent() {
        edits.value = state.value.students.associate { it.id to AttendanceStatus.PRESENT }
    }

    /** Future dates can't be marked; picking one snaps back to today. Unsaved marks for the old date are dropped. */
    fun setDate(newDate: LocalDate) {
        date.value = minOf(newDate, today)
        edits.value = emptyMap()
    }

    fun save() {
        val s = state.value
        if (s.students.isEmpty()) return
        viewModelScope.launch {
            attendanceRepository.mark(batchId, s.date, s.marks)
            edits.value = emptyMap()
            _saved.send(Unit)
        }
    }
}
