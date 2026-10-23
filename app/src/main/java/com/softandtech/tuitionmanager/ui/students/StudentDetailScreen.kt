package com.softandtech.tuitionmanager.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.data.db.entity.BatchEntity
import com.softandtech.tuitionmanager.data.db.entity.PaymentEntity
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import com.softandtech.tuitionmanager.data.db.model.EnrollmentWithBatch
import com.softandtech.tuitionmanager.data.db.model.FeeDueWithPaid
import com.softandtech.tuitionmanager.ui.fees.AddChargeDialog
import com.softandtech.tuitionmanager.ui.fees.CollectPaymentDialog
import com.softandtech.tuitionmanager.ui.fees.DueActionsDialog
import com.softandtech.tuitionmanager.ui.fees.PaymentActionsDialog
import com.softandtech.tuitionmanager.ui.fees.PaymentSavedDialog
import com.softandtech.tuitionmanager.ui.fees.feesSection
import com.softandtech.tuitionmanager.ui.receipt.Sharing
import com.softandtech.tuitionmanager.domain.AttendanceSummary
import com.softandtech.tuitionmanager.domain.Money
import com.softandtech.tuitionmanager.domain.Plan
import com.softandtech.tuitionmanager.ui.pro.UpgradeDialog
import com.softandtech.tuitionmanager.ui.common.BackTopBar
import com.softandtech.tuitionmanager.ui.common.ConfirmDialog
import com.softandtech.tuitionmanager.ui.common.DateField
import com.softandtech.tuitionmanager.ui.common.FormField
import com.softandtech.tuitionmanager.ui.common.Formatters
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun StudentDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onUpgrade: () -> Unit,
    viewModel: StudentDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val attendance by viewModel.attendance.collectAsStateWithLifecycle()
    val student = state.student
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var showAddToBatch by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf<EnrollmentWithBatch?>(null) }
    var confirmMarkLeft by remember { mutableStateOf(false) }
    var showCollect by remember { mutableStateOf(false) }
    var showAddCharge by remember { mutableStateOf(false) }
    var selectedDue by remember { mutableStateOf<FeeDueWithPaid?>(null) }
    var selectedPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var savedPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var showUpgrade by remember { mutableStateOf(false) }

    if (showUpgrade) {
        UpgradeDialog(R.string.upgrade_students, Plan.FREE_STUDENT_LIMIT, onUpgrade, onDismiss = { showUpgrade = false })
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StudentDetailEvent.PaymentSaved -> savedPayment = event.payment
                is StudentDetailEvent.ShareReceipt -> Sharing.sharePdf(context, event.file, event.message)
                is StudentDetailEvent.OpenReminder ->
                    if (!Sharing.openWhatsApp(context, event.waNumber, event.message)) {
                        snackbar.showSnackbar(context.getString(R.string.reminder_cannot_open))
                    }
                is StudentDetailEvent.Message -> snackbar.showSnackbar(context.getString(event.text))
                StudentDetailEvent.UpgradeNeeded -> showUpgrade = true
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            BackTopBar(
                title = student?.name.orEmpty(),
                onBack = onBack,
                actions = {
                    if (student != null) {
                        IconButton(onClick = { onEdit(student.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (student == null) return@Scaffold
        LazyColumn(Modifier.padding(padding)) {
            if (!student.active) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Text(stringResource(R.string.student_left_banner), modifier = Modifier.padding(16.dp))
                    }
                }
            }
            item { DetailsCard(student) }
            item { AttendanceCard(attendance, YearMonth.from(viewModel.today)) }

            feesSection(
                fees = state.fees,
                canCollect = true,
                onCollect = { showCollect = true },
                onRemind = viewModel::sendReminder,
                onAddCharge = { showAddCharge = true },
                onDueClick = { selectedDue = it },
                onPaymentClick = { selectedPayment = it },
            )

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp),
                ) {
                    Text(
                        stringResource(R.string.student_batches_header),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (student.active && state.availableBatches.isNotEmpty()) {
                        TextButton(onClick = { showAddToBatch = true }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.student_add_to_batch))
                        }
                    }
                }
            }
            if (state.enrollments.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.student_no_current_batches),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(state.enrollments, key = { it.enrollment.id }) { enrollment ->
                EnrollmentRow(enrollment, canLeave = student.active, onLeave = { leaving = enrollment })
                HorizontalDivider()
            }

            item {
                OutlinedButton(
                    onClick = { if (student.active) confirmMarkLeft = true else viewModel.rejoin() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(stringResource(if (student.active) R.string.action_mark_left else R.string.action_rejoin))
                }
            }
        }
    }

    if (showAddToBatch) {
        AddToBatchDialog(
            batches = state.availableBatches,
            today = viewModel.today,
            onAdd = viewModel::addToBatch,
            onDismiss = { showAddToBatch = false },
        )
    }
    leaving?.let { enrollment ->
        ConfirmDialog(
            title = stringResource(R.string.leave_batch_title, enrollment.batchName),
            text = stringResource(R.string.leave_batch_text),
            confirmLabel = stringResource(R.string.action_leave_batch),
            onConfirm = { viewModel.leaveBatch(enrollment) },
            onDismiss = { leaving = null },
        )
    }
    if (showCollect) {
        CollectPaymentDialog(
            suggestedPaise = state.fees.outstandingPaise,
            today = viewModel.today,
            onSave = viewModel::collectPayment,
            onDismiss = { showCollect = false },
        )
    }
    if (showAddCharge) {
        AddChargeDialog(today = viewModel.today, onSave = viewModel::addCharge, onDismiss = { showAddCharge = false })
    }
    selectedDue?.let { due ->
        DueActionsDialog(due, onSetWaived = { viewModel.setWaived(due.due.id, it) }, onDismiss = { selectedDue = null })
    }
    selectedPayment?.let { payment ->
        PaymentActionsDialog(
            payment = payment,
            receiptPrefix = state.fees.settings.receiptPrefix,
            onShare = { viewModel.shareReceipt(payment.id) },
            onDelete = { viewModel.deletePayment(payment.id) },
            onDismiss = { selectedPayment = null },
        )
    }
    savedPayment?.let { payment ->
        PaymentSavedDialog(
            payment = payment,
            receiptPrefix = state.fees.settings.receiptPrefix,
            onShare = { viewModel.shareReceipt(payment.id) },
            onDismiss = { savedPayment = null },
        )
    }
    if (confirmMarkLeft && student != null) {
        ConfirmDialog(
            title = stringResource(R.string.mark_left_title, student.name),
            text = stringResource(R.string.mark_left_text),
            confirmLabel = stringResource(R.string.action_mark_left),
            onConfirm = viewModel::markLeft,
            onDismiss = { confirmMarkLeft = false },
        )
    }
}

@Composable
private fun DetailsCard(student: StudentEntity) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            student.className?.let { DetailRow(stringResource(R.string.field_class), it) }
            student.phone?.let { DetailRow(stringResource(R.string.field_phone), it, callNumber = it) }
            student.parentName?.let { DetailRow(stringResource(R.string.field_parent_name), it) }
            student.parentPhone?.let { DetailRow(stringResource(R.string.field_parent_phone), it, callNumber = it) }
            DetailRow(stringResource(R.string.field_join_date), Formatters.date(student.joinDate))
            student.notes?.let { DetailRow(stringResource(R.string.field_notes), it) }
        }
    }
}

@Composable
private fun AttendanceCard(summary: AttendanceSummary, month: YearMonth) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.student_attendance_title, Formatters.month(month)), style = MaterialTheme.typography.labelLarge)
            val percent = summary.percent
            if (percent == null) {
                Text(stringResource(R.string.student_attendance_none), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (percent < LOW_ATTENDANCE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.student_attendance_detail, summary.present, summary.absent, summary.leave),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Below this, the percentage is shown in red as a cue to talk to the parent. */
private const val LOW_ATTENDANCE = 75

@Composable
private fun DetailRow(label: String, value: String, callNumber: String? = null) {
    val context = LocalContext.current
    ListItem(
        // Transparent so the card's colour shows through instead of each row painting its own
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        overlineContent = { Text(label) },
        headlineContent = { Text(value) },
        trailingContent = callNumber?.let {
            {
                // Returns false on phones without a dialer (e.g. Wi-Fi-only tablets) — nothing to do then
                IconButton(onClick = { Sharing.dial(context, it) }) {
                    Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.action_call))
                }
            }
        },
    )
}

@Composable
private fun EnrollmentRow(enrollment: EnrollmentWithBatch, canLeave: Boolean, onLeave: () -> Unit) {
    val leftOn = enrollment.enrollment.leftOn
    ListItem(
        headlineContent = { Text(enrollment.batchName) },
        supportingContent = {
            Text(
                if (leftOn != null) stringResource(R.string.enrollment_left, Formatters.date(leftOn))
                else stringResource(
                    R.string.enrollment_fee_since,
                    Money.format(enrollment.feePaise),
                    Formatters.date(enrollment.enrollment.joinedOn),
                ),
            )
        },
        trailingContent = if (enrollment.isCurrent && canLeave) {
            { TextButton(onClick = onLeave) { Text(stringResource(R.string.action_leave_batch)) } }
        } else null,
    )
}

@Composable
private fun AddToBatchDialog(
    batches: List<BatchEntity>,
    today: LocalDate,
    onAdd: (batchId: Long, joinedOn: LocalDate, customFee: String) -> Int?,
    onDismiss: () -> Unit,
) {
    var selectedId by remember { mutableStateOf(batches.first().id) }
    var joinedOn by remember { mutableStateOf(today) }
    var customFee by remember { mutableStateOf("") }
    var feeError by remember { mutableStateOf<Int?>(null) }
    val selected = batches.firstOrNull { it.id == selectedId } ?: batches.first()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.student_add_to_batch)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                batches.forEach { batch ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = batch.id == selectedId, role = Role.RadioButton, onClick = { selectedId = batch.id }),
                    ) {
                        RadioButton(selected = batch.id == selectedId, onClick = null)
                        Text(batch.name, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                DateField(stringResource(R.string.field_join_date), joinedOn, { joinedOn = it }, Modifier.fillMaxWidth())
                FormField(
                    value = customFee,
                    onValueChange = { customFee = it; feeError = null },
                    label = stringResource(R.string.field_custom_fee),
                    placeholder = stringResource(R.string.custom_fee_hint, Money.format(selected.monthlyFeePaise)),
                    error = feeError,
                    keyboardType = KeyboardType.Decimal,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                feeError = onAdd(selected.id, joinedOn, customFee)
                if (feeError == null) onDismiss()
            }) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
