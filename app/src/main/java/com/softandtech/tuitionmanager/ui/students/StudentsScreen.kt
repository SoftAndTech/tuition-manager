package com.softandtech.tuitionmanager.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softandtech.tuitionmanager.R

/** Phase 0: proves the encrypted database is wired end to end. Full CRUD comes in Phase 2. */
@Composable
fun StudentsScreen(
    modifier: Modifier = Modifier,
    viewModel: StudentsViewModel = hiltViewModel(),
) {
    val count by viewModel.studentCount.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.students_count, count), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.privacy_tagline), style = MaterialTheme.typography.bodyMedium)
    }
}
