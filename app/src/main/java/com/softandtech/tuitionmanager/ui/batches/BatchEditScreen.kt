package com.softandtech.tuitionmanager.ui.batches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.ui.common.BackTopBar
import com.softandtech.tuitionmanager.ui.common.ConfirmDialog
import com.softandtech.tuitionmanager.ui.common.DiscardChangesGuard
import com.softandtech.tuitionmanager.ui.common.FormField
import com.softandtech.tuitionmanager.ui.common.Formatters
import com.softandtech.tuitionmanager.ui.common.TimeField
import java.time.DayOfWeek

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onArchived: () -> Unit,
    viewModel: BatchEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmArchive by remember { mutableStateOf(false) }

    LaunchedEffect(state.result) {
        when (state.result) {
            BatchEditResult.SAVED -> onSaved()
            BatchEditResult.ARCHIVED -> onArchived()
            null -> Unit
        }
    }
    val guardedBack = DiscardChangesGuard(hasChanges = state.hasChanges && state.result == null, onDiscard = onBack)

    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(if (state.isNew) R.string.batch_new_title else R.string.batch_edit_title),
                onBack = guardedBack,
                actions = {
                    if (!state.isNew) {
                        TextButton(onClick = { confirmArchive = true }) { Text(stringResource(R.string.action_archive)) }
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = stringResource(R.string.field_batch_name),
                placeholder = stringResource(R.string.batch_name_hint),
                error = state.nameError,
                capitalization = KeyboardCapitalization.Words,
            )
            FormField(
                value = state.subject,
                onValueChange = viewModel::onSubjectChange,
                label = stringResource(R.string.field_subject),
                capitalization = KeyboardCapitalization.Words,
            )

            Text(stringResource(R.string.field_days), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day in state.days,
                        onClick = { viewModel.onDayToggle(day) },
                        label = { Text(Formatters.shortDay(day)) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField(stringResource(R.string.field_start_time), state.startMinute, viewModel::onStartChange, Modifier.weight(1f))
                TimeField(stringResource(R.string.field_end_time), state.endMinute, viewModel::onEndChange, Modifier.weight(1f))
            }
            state.timeError?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            FormField(
                value = state.fee,
                onValueChange = viewModel::onFeeChange,
                label = stringResource(R.string.field_monthly_fee),
                error = state.feeError,
                keyboardType = KeyboardType.Decimal,
            )
            FormField(
                value = state.dueDay,
                onValueChange = viewModel::onDueDayChange,
                label = stringResource(R.string.field_due_day),
                placeholder = stringResource(R.string.due_day_help),
                error = state.dueDayError,
                keyboardType = KeyboardType.Number,
            )

            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }

    if (confirmArchive) {
        ConfirmDialog(
            title = stringResource(R.string.archive_title, state.name),
            text = stringResource(R.string.archive_text),
            confirmLabel = stringResource(R.string.action_archive),
            onConfirm = viewModel::archive,
            onDismiss = { confirmArchive = false },
        )
    }
}
