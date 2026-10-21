package com.softandtech.tuitionmanager.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softandtech.tuitionmanager.data.db.dao.StudentDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class StudentsViewModel @Inject constructor(
    studentDao: StudentDao,
) : ViewModel() {

    val studentCount: StateFlow<Int> = studentDao.observeActiveCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
