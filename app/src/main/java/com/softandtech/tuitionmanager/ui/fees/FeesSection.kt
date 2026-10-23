package com.softandtech.tuitionmanager.ui.fees

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.data.db.entity.PaymentEntity
import com.softandtech.tuitionmanager.data.db.model.FeeDueWithPaid
import com.softandtech.tuitionmanager.domain.Money
import com.softandtech.tuitionmanager.domain.Receipts
import com.softandtech.tuitionmanager.domain.model.DueStatus
import com.softandtech.tuitionmanager.domain.model.PaymentMode
import com.softandtech.tuitionmanager.ui.common.DateField
import com.softandtech.tuitionmanager.ui.common.FormField
import com.softandtech.tuitionmanager.ui.common.Formatters
import com.softandtech.tuitionmanager.ui.students.StudentFees
import java.time.LocalDate

/** Fee summary, actions, history and payments, added to the student page's list. */
fun LazyListScope.feesSection(
    fees: StudentFees,
    canCollect: Boolean,
    onCollect: () -> Unit,
    onRemind: () -> Unit,
    onAddCharge: () -> Unit,
    onDueClick: (FeeDueWithPaid) -> Unit,
    onPaymentClick: (PaymentEntity) -> Unit,
) {
    item {
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.fees_due_now), style = MaterialTheme.typography.labelLarge)
                Text(
                    Money.format(fees.outstandingPaise),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (fees.outstandingPaise > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                if (fees.advancePaise > 0) {
                    Text(stringResource(R.string.fees_advance, Money.format(fees.advancePaise)), style = MaterialTheme.typography.bodyMedium)
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCollect, enabled = canCollect) { Text(stringResource(R.string.fees_collect)) }
                    OutlinedButton(onClick = onRemind, enabled = fees.outstandingPaise > 0) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.fees_remind))
                    }
                }
            }
        }
    }

    item {
        SectionHeader(R.string.fees_history) {
            if (canCollect) {
                TextButton(onClick = onAddCharge) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.fees_add_charge))
                }
            }
        }
    }
    if (fees.dues.isEmpty()) {
        item { EmptyLine(R.string.fees_no_dues) }
    }
    items(fees.dues, key = { "due-${it.due.id}" }) { due ->
        DueRow(due, onClick = { onDueClick(due) })
        HorizontalDivider()
    }

    item { SectionHeader(R.string.fees_payments) }
    if (fees.payments.isEmpty()) {
        item { EmptyLine(R.string.fees_no_payments) }
    }
    items(fees.payments, key = { "pay-${it.id}" }) { payment ->
        ListItem(
            headlineContent = { Text("${Money.format(payment.amountPaise)} · ${stringResource(modeLabel(payment.mode))}") },
            supportingContent = {
                Text(
                    listOfNotNull(
                        Receipts.number(fees.settings.receiptPrefix, payment.receiptSeq),
                        Formatters.date(payment.paidOn),
                        payment.note,
                    ).joinToString(" · "),
                )
            },
            modifier = Modifier.clickable { onPaymentClick(payment) },
        )
        HorizontalDivider()
    }
}

@Composable
private fun SectionHeader(@StringRes title: Int, action: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action()
    }
}

@Composable
private fun EmptyLine(@StringRes text: Int) {
    Text(
        stringResource(text),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun DueRow(due: FeeDueWithPaid, onClick: () -> Unit) {
    val d = due.due
    val headline = d.period?.let { "${Formatters.month(it)} · ${d.title}" } ?: d.title
    val detail = when (due.status) {
        DueStatus.PARTIAL -> stringResource(R.string.due_partial_detail, Money.format(due.paidPaise), Money.format(d.amountPaise))
        else -> Money.format(d.amountPaise)
    }
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = { Text("$detail · ${stringResource(R.string.due_on, Formatters.date(d.dueDate))}") },
        trailingContent = { StatusLabel(due.status) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
fun StatusLabel(status: DueStatus) {
    val dark = isSystemInDarkTheme()
    val (text, container, content) = when (status) {
        DueStatus.PAID -> Triple(R.string.status_paid, if (dark) Color(0xFF1B5E20) else Color(0xFFDFF3E0), if (dark) Color(0xFFC8E6C9) else Color(0xFF1B5E20))
        DueStatus.PARTIAL -> Triple(R.string.status_partial, if (dark) Color(0xFF6D4C00) else Color(0xFFFFF0C2), if (dark) Color(0xFFFFE08A) else Color(0xFF6D4C00))
        DueStatus.DUE -> Triple(R.string.status_due, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        DueStatus.WAIVED -> Triple(R.string.status_waived, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(stringResource(text), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@StringRes
fun modeLabel(mode: PaymentMode): Int = when (mode) {
    PaymentMode.CASH -> R.string.mode_cash
    PaymentMode.UPI -> R.string.mode_upi
    PaymentMode.BANK -> R.string.mode_bank
    PaymentMode.OTHER -> R.string.mode_other
}

/** [onSave] validates and returns an error message id, or null once the payment is being saved. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectPaymentDialog(
    suggestedPaise: Long,
    today: LocalDate,
    onSave: (amount: String, mode: PaymentMode, paidOn: LocalDate, note: String) -> Int?,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf(if (suggestedPaise > 0) Money.toInput(suggestedPaise) else "") }
    var mode by remember { mutableStateOf(PaymentMode.CASH) }
    var paidOn by remember { mutableStateOf(today) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fees_collect)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(
                    value = amount,
                    onValueChange = { amount = it; error = null },
                    label = stringResource(R.string.field_amount),
                    error = error,
                    keyboardType = KeyboardType.Decimal,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentMode.entries.forEach {
                        FilterChip(selected = mode == it, onClick = { mode = it }, label = { Text(stringResource(modeLabel(it))) })
                    }
                }
                DateField(stringResource(R.string.field_paid_on), paidOn, { paidOn = it }, Modifier.fillMaxWidth())
                FormField(
                    value = note,
                    onValueChange = { note = it },
                    label = stringResource(R.string.field_note_optional),
                    capitalization = KeyboardCapitalization.Sentences,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = onSave(amount, mode, paidOn, note)
                if (error == null) onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun AddChargeDialog(
    today: LocalDate,
    onSave: (title: String, amount: String, dueDate: LocalDate) -> Int?,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(today) }
    var error by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fees_add_charge)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(
                    value = title,
                    onValueChange = { title = it; error = null },
                    label = stringResource(R.string.field_charge_title),
                    placeholder = stringResource(R.string.charge_title_hint),
                    capitalization = KeyboardCapitalization.Sentences,
                )
                FormField(
                    value = amount,
                    onValueChange = { amount = it; error = null },
                    label = stringResource(R.string.field_amount),
                    keyboardType = KeyboardType.Decimal,
                )
                DateField(stringResource(R.string.field_due_date), dueDate, { dueDate = it }, Modifier.fillMaxWidth())
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = onSave(title, amount, dueDate)
                if (error == null) onDismiss()
            }) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Choices for one due: waive it, or undo a waiver. */
@Composable
fun DueActionsDialog(due: FeeDueWithPaid, onSetWaived: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val waived = due.due.waived
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(due.due.period?.let { "${Formatters.month(it)} · ${due.due.title}" } ?: due.due.title) },
        text = { Text(stringResource(if (waived) R.string.due_unwaive_text else R.string.due_waive_text)) },
        confirmButton = {
            TextButton(onClick = {
                onSetWaived(!waived)
                onDismiss()
            }) { Text(stringResource(if (waived) R.string.action_unwaive else R.string.action_waive)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Choices for one payment: share its receipt or delete it (with a second confirmation). */
@Composable
fun PaymentActionsDialog(
    payment: PaymentEntity,
    receiptPrefix: String,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val number = Receipts.number(receiptPrefix, payment.receiptSeq)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${Money.format(payment.amountPaise)} · $number") },
        text = {
            Text(
                if (confirmDelete) stringResource(R.string.payment_delete_text)
                else "${stringResource(modeLabel(payment.mode))} · ${Formatters.date(payment.paidOn)}",
            )
        },
        confirmButton = {
            if (confirmDelete) {
                TextButton(onClick = {
                    onDelete()
                    onDismiss()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            } else {
                TextButton(onClick = {
                    onShare()
                    onDismiss()
                }) { Text(stringResource(R.string.action_share_receipt)) }
            }
        },
        dismissButton = {
            if (confirmDelete) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            } else {
                TextButton(onClick = { confirmDelete = true }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}

@Composable
fun PaymentSavedDialog(payment: PaymentEntity, receiptPrefix: String, onShare: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.payment_saved_title)) },
        text = {
            Text(
                stringResource(
                    R.string.payment_saved_text,
                    Money.format(payment.amountPaise),
                    Receipts.number(receiptPrefix, payment.receiptSeq),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onShare()
                onDismiss()
            }) { Text(stringResource(R.string.action_share_receipt)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}
