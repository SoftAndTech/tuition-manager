package com.softandtech.tuitionmanager.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.domain.Money
import com.softandtech.tuitionmanager.ui.common.BackTopBar
import com.softandtech.tuitionmanager.ui.common.DateField
import com.softandtech.tuitionmanager.ui.common.DiscardChangesGuard
import com.softandtech.tuitionmanager.ui.common.FormField
import com.softandtech.tuitionmanager.ui.common.Formatters

/** [onSaved] receives the student id, and whether this was a new student. */
@Composable
fun StudentEditScreen(
    onBack: () -> Unit,
    onSaved: (studentId: Long, isNew: Boolean) -> Unit,
    viewModel: StudentEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.savedStudentId) {
        state.savedStudentId?.let { onSaved(it, state.isNew) }
    }
    val guardedBack = DiscardChangesGuard(hasChanges = state.hasChanges && state.savedStudentId == null, onDiscard = onBack)

    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(if (state.isNew) R.string.student_new_title else R.string.student_edit_title),
                onBack = guardedBack,
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
                label = stringResource(R.string.field_name),
                error = state.nameError,
                capitalization = KeyboardCapitalization.Words,
            )
            FormField(
                value = state.className,
                onValueChange = viewModel::onClassChange,
                label = stringResource(R.string.field_class),
                capitalization = KeyboardCapitalization.Words,
            )
            FormField(
                value = state.phone,
                onValueChange = viewModel::onPhoneChange,
                label = stringResource(R.string.field_phone),
                error = state.phoneError,
                keyboardType = KeyboardType.Phone,
            )
            FormField(
                value = state.parentName,
                onValueChange = viewModel::onParentNameChange,
                label = stringResource(R.string.field_parent_name),
                capitalization = KeyboardCapitalization.Words,
            )
            FormField(
                value = state.parentPhone,
                onValueChange = viewModel::onParentPhoneChange,
                label = stringResource(R.string.field_parent_phone),
                error = state.parentPhoneError,
                keyboardType = KeyboardType.Phone,
            )
            DateField(
                label = stringResource(R.string.field_join_date),
                date = state.joinDate,
                onDateChange = viewModel::onJoinDateChange,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.isNew) {
                BatchPicker(state, viewModel)
            }

            FormField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = stringResource(R.string.field_notes),
                singleLine = false,
                capitalization = KeyboardCapitalization.Sentences,
            )

            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}

@Composable
private fun BatchPicker(state: StudentEditUiState, viewModel: StudentEditViewModel) {
    Text(stringResource(R.string.student_batches_header), style = MaterialTheme.typography.titleMedium)
    if (state.batches.isEmpty()) {
        Text(
            stringResource(R.string.student_no_batches),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    state.batches.forEach { choice ->
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = choice.selected, role = Role.Checkbox, onValueChange = { viewModel.onBatchToggle(choice.batch.id) }),
            ) {
                Checkbox(checked = choice.selected, onCheckedChange = null)
                Text(
                    "${choice.batch.name} · ${stringResource(R.string.batch_fee_per_month, Money.format(choice.batch.monthlyFeePaise))}",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (choice.selected) {
                FormField(
                    value = choice.customFee,
                    onValueChange = { viewModel.onBatchFeeChange(choice.batch.id, it) },
                    label = stringResource(R.string.field_custom_fee),
                    placeholder = stringResource(R.string.custom_fee_hint, Money.format(choice.batch.monthlyFeePaise)),
                    error = choice.feeError,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.padding(start = 40.dp),
                )
            }
        }
    }

    if (state.showBillFromChoice) {
        Text(stringResource(R.string.bill_from_header), style = MaterialTheme.typography.titleSmall)
        BillFromOption(
            text = stringResource(R.string.bill_from_current, Formatters.month(state.currentMonth)),
            selected = !state.billFromJoinMonth,
            onClick = { viewModel.onBillFromJoinMonthChange(false) },
        )
        BillFromOption(
            text = stringResource(R.string.bill_from_join, Formatters.month(state.joinMonth)),
            selected = state.billFromJoinMonth,
            onClick = { viewModel.onBillFromJoinMonthChange(true) },
        )
    }
}

@Composable
private fun BillFromOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text, modifier = Modifier.padding(start = 8.dp))
    }
}
