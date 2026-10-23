package com.softandtech.tuitionmanager.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.data.db.model.StudentOverdue
import com.softandtech.tuitionmanager.domain.Money
import com.softandtech.tuitionmanager.ui.common.Formatters
import com.softandtech.tuitionmanager.ui.common.TitleTopBar
import com.softandtech.tuitionmanager.ui.receipt.Sharing
import com.softandtech.tuitionmanager.ui.students.InitialAvatar
import com.softandtech.tuitionmanager.ui.students.StudentDetailEvent

@Composable
fun HomeScreen(
    onStudentClick: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onTakeAttendance: (batchId: Long) -> Unit,
    onOpenBackup: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StudentDetailEvent.OpenReminder ->
                    if (!Sharing.openWhatsApp(context, event.waNumber, event.message)) {
                        snackbar.showSnackbar(context.getString(R.string.reminder_cannot_open))
                    }
                is StudentDetailEvent.Message -> snackbar.showSnackbar(context.getString(event.text))
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = { TitleTopBar(stringResource(R.string.app_name)) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) return@Scaffold
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (!state.hasProfile) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
                            Text(stringResource(R.string.home_setup_profile), modifier = Modifier.weight(1f))
                            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.home_setup_action)) }
                        }
                    }
                }
            }

            if (state.backupDue) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
                            Text(
                                state.lastBackup?.let { stringResource(R.string.home_backup_old, Formatters.date(it)) }
                                    ?: stringResource(R.string.home_backup_never),
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onOpenBackup) { Text(stringResource(R.string.home_backup_action)) }
                        }
                    }
                }
            }

            item { QuickStats(state) }

            todaySection(state, onTakeAttendance)

            item { MonthCard(state) }

            overdueSection(state, onStudentClick, viewModel::remind)
        }
    }
}

@Composable
private fun QuickStats(state: HomeUiState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        StatCard(R.string.home_stat_students, state.activeStudents.toString(), Modifier.weight(1f))
        StatCard(
            R.string.home_stat_overdue,
            Money.format(state.overdueTotalPaise),
            Modifier.weight(1f),
            highlight = state.overdueTotalPaise > 0,
        )
    }
}

@Composable
private fun StatCard(@StringRes label: Int, value: String, modifier: Modifier, highlight: Boolean = false) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun LazyListScope.todaySection(state: HomeUiState, onTakeAttendance: (Long) -> Unit) {
    item {
        val day = Formatters.dayName(state.today.dayOfWeek)
        SectionTitle(stringResource(R.string.home_today_classes, day))
    }
    if (state.todayClasses.isEmpty()) {
        item {
            Hint(stringResource(if (state.hasBatches) R.string.home_no_classes_today else R.string.home_no_batches))
        }
    }
    items(state.todayClasses, key = { "class-${it.batch.batch.id}" }) { today ->
        val batch = today.batch.batch
        ListItem(
            headlineContent = { Text(batch.name) },
            supportingContent = {
                Text(
                    listOfNotNull(
                        Formatters.timeRange(batch.startMinute, batch.endMinute),
                        if (today.isMarked) stringResource(R.string.home_marked_count, today.markedCount, today.batch.studentCount) else null,
                    ).joinToString(" · ").ifEmpty { stringResource(R.string.home_not_marked) },
                )
            },
            trailingContent = {
                if (today.isMarked) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.home_marked_cd),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else if (today.batch.studentCount > 0) {
                    TextButton(onClick = { onTakeAttendance(batch.id) }) { Text(stringResource(R.string.attendance_take_short)) }
                }
            },
            modifier = Modifier.clickable(enabled = today.batch.studentCount > 0) { onTakeAttendance(batch.id) },
        )
        HorizontalDivider()
    }
}

private fun LazyListScope.overdueSection(
    state: HomeUiState,
    onStudentClick: (Long) -> Unit,
    onRemind: (StudentOverdue) -> Unit,
) {
    item {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        ) {
            Text(stringResource(R.string.home_overdue), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (state.overdue.isNotEmpty()) {
                Text(Money.format(state.overdueTotalPaise), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (state.overdue.isEmpty()) {
        item { Hint(stringResource(R.string.home_no_overdue)) }
    }
    items(state.overdue, key = { "due-${it.studentId}" }) { item ->
        ListItem(
            headlineContent = { Text(item.name) },
            supportingContent = {
                Text(stringResource(R.string.home_overdue_detail, Money.format(item.outstandingPaise), Formatters.date(item.oldestDueDate)))
            },
            leadingContent = { InitialAvatar(item.name) },
            trailingContent = {
                IconButton(onClick = { onRemind(item) }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.home_remind_cd, item.name))
                }
            },
            modifier = Modifier.clickable { onStudentClick(item.studentId) },
        )
        HorizontalDivider()
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun MonthCard(state: HomeUiState) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).padding(top = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.home_this_month, Formatters.month(state.month)), style = MaterialTheme.typography.labelLarge)
            Text(Money.format(state.collectedPaise), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.home_collected_of, Money.format(state.billedPaise)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.billedPaise > 0) {
                LinearProgressIndicator(
                    progress = { (state.collectedPaise.toFloat() / state.billedPaise).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}
