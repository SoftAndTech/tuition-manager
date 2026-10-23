package com.softandtech.tuitionmanager.ui.batches

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.data.db.entity.BatchEntity
import com.softandtech.tuitionmanager.data.db.model.BatchWithCount
import com.softandtech.tuitionmanager.data.repository.BatchRepository
import com.softandtech.tuitionmanager.domain.Money
import com.softandtech.tuitionmanager.ui.common.EmptyState
import com.softandtech.tuitionmanager.ui.common.Formatters
import com.softandtech.tuitionmanager.ui.common.TitleTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class BatchesViewModel @Inject constructor(batchRepository: BatchRepository) : ViewModel() {
    /** Null while loading. */
    val batches: StateFlow<List<BatchWithCount>?> = batchRepository.observeActiveWithCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun BatchesScreen(
    onBatchClick: (Long) -> Unit,
    onAddBatch: () -> Unit,
    viewModel: BatchesViewModel = hiltViewModel(),
) {
    val batches by viewModel.batches.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TitleTopBar(stringResource(R.string.nav_batches)) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddBatch,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.batch_add)) },
            )
        },
    ) { padding ->
        val list = batches ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(stringResource(R.string.batches_empty), Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
                items(list, key = { it.batch.id }) { item ->
                    ListItem(
                        headlineContent = { Text(item.batch.name) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    scheduleText(item.batch),
                                    stringResource(R.string.batch_fee_per_month, Money.format(item.batch.monthlyFeePaise)),
                                ).joinToString("\n"),
                            )
                        },
                        trailingContent = {
                            Text(pluralStringResource(R.plurals.student_count, item.studentCount, item.studentCount))
                        },
                        modifier = Modifier.clickable { onBatchClick(item.batch.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/** "Maths · Mon, Wed, Fri · 5:00 PM – 6:00 PM", skipping parts that aren't set. */
fun scheduleText(batch: BatchEntity): String? =
    listOfNotNull(batch.subject, Formatters.days(batch.daysMask), Formatters.timeRange(batch.startMinute, batch.endMinute))
        .joinToString(" · ")
        .ifEmpty { null }
