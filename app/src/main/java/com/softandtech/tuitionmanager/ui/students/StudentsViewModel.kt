package com.softandtech.tuitionmanager.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softandtech.tuitionmanager.data.db.model.StudentListItem
import com.softandtech.tuitionmanager.data.billing.ProRepository
import com.softandtech.tuitionmanager.data.repository.StudentRepository
import com.softandtech.tuitionmanager.domain.Plan
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class StudentsUiState(
    val query: String = "",
    val showLeft: Boolean = false,
    val students: List<StudentListItem> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class StudentsViewModel @Inject constructor(
    studentRepository: StudentRepository,
    proRepository: ProRepository,
) : ViewModel() {

    /** On the free plan with the student limit reached — adding asks to upgrade instead. */
    val atFreeLimit: StateFlow<Boolean> = combine(proRepository.isPro, studentRepository.observeActiveCount()) { pro, count ->
        !Plan.canAdd(count, Plan.FREE_STUDENT_LIMIT, pro)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val query = MutableStateFlow("")
    private val showLeft = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<StudentsUiState> = combine(query, showLeft, ::Pair)
        .flatMapLatest { (q, left) ->
            studentRepository.observeListItems(q, active = !left).map { StudentsUiState(q, left, it, loading = false) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentsUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onShowLeftChange(value: Boolean) {
        showLeft.value = value
    }
}
