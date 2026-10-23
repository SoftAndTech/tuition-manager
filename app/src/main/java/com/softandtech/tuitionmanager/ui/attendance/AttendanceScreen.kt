package com.softandtech.tuitionmanager.ui.attendance

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.domain.model.AttendanceStatus
import com.softandtech.tuitionmanager.ui.common.BackTopBar
import com.softandtech.tuitionmanager.ui.common.DateField
import com.softandtech.tuitionmanager.ui.common.DiscardChangesGuard
import com.softandtech.tuitionmanager.ui.common.EmptyState
import com.softandtech.tuitionmanager.ui.students.InitialAvatar

@Composable
fun AttendanceScreen(
    onBack: () -> Unit,
    viewModel: AttendanceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val guardedBack = DiscardChangesGuard(hasChanges = state.hasChanges, onDiscard = onBack)

    LaunchedEffect(Unit) {
        viewModel.saved.collect { snackbar.showSnackbar(context.getString(R.string.attendance_saved)) }
    }

    Scaffold(
        topBar = { BackTopBar(title = state.batch?.name ?: stringResource(R.string.attendance_title), onBack = guardedBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.students.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = viewModel::save,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    ) {
                        Text(stringResource(if (state.alreadyMarked) R.string.attendance_update else R.string.attendance_save))
                    }
                }
            }
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        Column(Modifier.padding(padding)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DateField(stringResource(R.string.attendance_date), state.date, viewModel::setDate, Modifier.fillMaxWidth())
                if (state.notAClassDay) {
                    Text(
                        stringResource(R.string.attendance_not_class_day),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.students.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(
                                R.string.attendance_counts,
                                state.count(AttendanceStatus.PRESENT),
                                state.count(AttendanceStatus.ABSENT),
                                state.count(AttendanceStatus.LEAVE),
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::markAllPresent) { Text(stringResource(R.string.attendance_all_present)) }
                    }
                }
            }
            HorizontalDivider()

            if (state.students.isEmpty()) {
                EmptyState(stringResource(R.string.attendance_no_students))
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(state.students, key = { it.id }) { student ->
                        val status = state.marks[student.id] ?: AttendanceStatus.PRESENT
                        ListItem(
                            headlineContent = { Text(student.name) },
                            supportingContent = student.className?.let { { Text(it) } },
                            leadingContent = { InitialAvatar(student.name) },
                            trailingContent = {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    StatusChip(R.string.attendance_short_present, R.string.attendance_present, AttendanceStatus.PRESENT, status) {
                                        viewModel.setStatus(student.id, it)
                                    }
                                    StatusChip(R.string.attendance_short_absent, R.string.attendance_absent, AttendanceStatus.ABSENT, status) {
                                        viewModel.setStatus(student.id, it)
                                    }
                                    StatusChip(R.string.attendance_short_leave, R.string.attendance_leave, AttendanceStatus.LEAVE, status) {
                                        viewModel.setStatus(student.id, it)
                                    }
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** One-letter chip ("P", "A", "L") that screen readers announce with the full word. */
@Composable
private fun StatusChip(
    @StringRes short: Int,
    @StringRes full: Int,
    value: AttendanceStatus,
    selected: AttendanceStatus,
    onSelect: (AttendanceStatus) -> Unit,
) {
    val fullLabel = stringResource(full)
    FilterChip(
        selected = value == selected,
        onClick = { onSelect(value) },
        label = { Text(stringResource(short)) },
        modifier = Modifier.semantics { contentDescription = fullLabel },
    )
}
