package com.softandtech.tuitionmanager.ui.batches

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.data.db.entity.BatchEntity
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import com.softandtech.tuitionmanager.data.repository.BatchRepository
import com.softandtech.tuitionmanager.domain.Money
import com.softandtech.tuitionmanager.ui.common.BackTopBar
import com.softandtech.tuitionmanager.ui.navigation.BatchDetailRoute
import com.softandtech.tuitionmanager.ui.students.InitialAvatar
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class BatchDetailUiState(
    val loading: Boolean = true,
    val batch: BatchEntity? = null,
    val students: List<StudentEntity> = emptyList(),
)

@HiltViewModel
class BatchDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    batchRepository: BatchRepository,
    clock: Clock,
) : ViewModel() {
    private val batchId = savedStateHandle.toRoute<BatchDetailRoute>().batchId

    val state: StateFlow<BatchDetailUiState> = combine(
        batchRepository.observeBatch(batchId),
        batchRepository.observeStudentsInBatch(batchId, LocalDate.now(clock)),
    ) { batch, students -> BatchDetailUiState(loading = false, batch = batch, students = students) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BatchDetailUiState())
}

@Composable
fun BatchDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onStudentClick: (Long) -> Unit,
    viewModel: BatchDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val batch = state.batch

    Scaffold(
        topBar = {
            BackTopBar(
                title = batch?.name.orEmpty(),
                onBack = onBack,
                actions = {
                    if (batch != null && batch.active) {
                        IconButton(onClick = { onEdit(batch.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (batch == null) return@Scaffold
        LazyColumn(Modifier.padding(padding)) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        scheduleText(batch)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        Text(
                            stringResource(R.string.batch_fee_per_month, Money.format(batch.monthlyFeePaise)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(stringResource(R.string.batch_due_day, batch.feeDueDay), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.batch_students_header),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (state.students.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.batch_no_students),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(state.students, key = { it.id }) { student ->
                ListItem(
                    headlineContent = { Text(student.name) },
                    supportingContent = student.className?.let { { Text(it) } },
                    leadingContent = { InitialAvatar(student.name) },
                    modifier = Modifier.clickable { onStudentClick(student.id) },
                )
                HorizontalDivider()
            }
        }
    }
}
