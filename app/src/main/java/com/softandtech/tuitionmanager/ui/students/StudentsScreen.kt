package com.softandtech.tuitionmanager.ui.students

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.softandtech.tuitionmanager.domain.Plan
import com.softandtech.tuitionmanager.ui.pro.UpgradeDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.data.db.model.StudentListItem
import com.softandtech.tuitionmanager.ui.common.EmptyState
import com.softandtech.tuitionmanager.ui.common.TitleTopBar

@Composable
fun StudentsScreen(
    onStudentClick: (Long) -> Unit,
    onAddStudent: () -> Unit,
    onUpgrade: () -> Unit,
    viewModel: StudentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val atFreeLimit by viewModel.atFreeLimit.collectAsStateWithLifecycle()
    var showUpgrade by remember { mutableStateOf(false) }

    if (showUpgrade) {
        UpgradeDialog(R.string.upgrade_students, Plan.FREE_STUDENT_LIMIT, onUpgrade, onDismiss = { showUpgrade = false })
    }

    Scaffold(
        topBar = { TitleTopBar(stringResource(R.string.nav_students)) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (atFreeLimit) showUpgrade = true else onAddStudent() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.student_add)) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text(stringResource(R.string.students_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !state.showLeft,
                    onClick = { viewModel.onShowLeftChange(false) },
                    label = { Text(stringResource(R.string.students_filter_current)) },
                )
                FilterChip(
                    selected = state.showLeft,
                    onClick = { viewModel.onShowLeftChange(true) },
                    label = { Text(stringResource(R.string.students_filter_left)) },
                )
            }

            when {
                state.loading -> Unit
                state.students.isEmpty() -> EmptyState(
                    when {
                        state.query.isNotBlank() -> stringResource(R.string.students_empty_search, state.query)
                        state.showLeft -> stringResource(R.string.students_empty_left)
                        else -> stringResource(R.string.students_empty)
                    },
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(state.students, key = { it.id }) { student ->
                        StudentRow(student, onClick = { onStudentClick(student.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentRow(student: StudentListItem, onClick: () -> Unit) {
    val details = listOfNotNull(student.className, student.batchNames).joinToString(" · ")
    ListItem(
        headlineContent = { Text(student.name) },
        supportingContent = if (details.isNotEmpty()) ({ Text(details, maxLines = 1) }) else null,
        leadingContent = { InitialAvatar(student.name) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
fun InitialAvatar(name: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                name.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
