package com.softandtech.tuitionmanager.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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

            item { MonthCard(state) }

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
                item {
                    Text(
                        stringResource(R.string.home_no_overdue),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(state.overdue, key = { it.studentId }) { item ->
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.home_overdue_detail,
                                Money.format(item.outstandingPaise),
                                Formatters.date(item.oldestDueDate),
                            ),
                        )
                    },
                    leadingContent = { InitialAvatar(item.name) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.remind(item) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.home_remind_cd, item.name),
                            )
                        }
                    },
                    modifier = Modifier.clickable { onStudentClick(item.studentId) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun MonthCard(state: HomeUiState) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
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
