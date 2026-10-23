package com.softandtech.tuitionmanager.ui.settings

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.softandtech.tuitionmanager.MainActivity
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.data.backup.BackupException
import com.softandtech.tuitionmanager.data.backup.BackupFiles
import com.softandtech.tuitionmanager.data.backup.BackupManager
import com.softandtech.tuitionmanager.data.backup.BackupSummary
import com.softandtech.tuitionmanager.data.security.SecurityRepository
import com.softandtech.tuitionmanager.ui.common.BackTopBar
import com.softandtech.tuitionmanager.ui.common.Formatters
import com.softandtech.tuitionmanager.ui.lock.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    val busy: Boolean = false,
    /** Checked backup waiting for the tutor's "replace my data" confirmation. */
    val pendingSummary: BackupSummary? = null,
    val restored: Boolean = false,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: BackupManager,
    private val lockManager: AppLockManager,
    securityRepository: SecurityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val lastBackup: StateFlow<LocalDate?> = securityRepository.settings.map { it.lastBackup }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Passphrase held only in memory between the dialog and the file picker returning. */
    private var passphrase: String? = null

    val suggestedFileName: String get() = backupManager.suggestedFileName()

    /** Called just before the system file picker opens, so returning from it doesn't trigger the app lock. */
    fun beforeFilePicker(newPassphrase: String? = null) {
        if (newPassphrase != null) passphrase = newPassphrase
        lockManager.allowExternalScreen()
    }

    fun export(uri: Uri) {
        val pass = passphrase ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val message = try {
                val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot write")
                out.use { backupManager.export(it, pass) }
                R.string.backup_done
            } catch (e: Exception) {
                R.string.backup_failed
            }
            passphrase = null
            _state.update { it.copy(busy = false) }
            _messages.send(message)
        }
    }

    /** Opens and checks the picked file. Returns via state (summary) or a message. */
    fun inspect(uri: Uri, pass: String) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val input = context.contentResolver.openInputStream(uri) ?: error("Cannot read")
                val summary = input.use { backupManager.inspect(it, pass) }
                passphrase = pass
                _state.update { it.copy(busy = false, pendingSummary = summary) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false) }
                _messages.send(
                    when (e) {
                        is BackupException.CannotOpen -> R.string.restore_cannot_open
                        is BackupException.NotTuitionBackup -> R.string.restore_not_backup
                        is BackupException.TooNew -> R.string.restore_too_new
                        else -> R.string.restore_failed
                    },
                )
            }
        }
    }

    fun cancelRestore() {
        passphrase = null
        backupManager.discardPendingRestore()
        _state.update { it.copy(pendingSummary = null) }
    }

    fun confirmRestore() {
        val pass = passphrase ?: return
        _state.update { it.copy(busy = true, pendingSummary = null) }
        viewModelScope.launch {
            try {
                backupManager.restore(pass)
                _state.update { it.copy(busy = false, restored = true) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false) }
                _messages.send(R.string.restore_failed)
            } finally {
                passphrase = null
            }
        }
    }
}

@Composable
fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lastBackup by viewModel.lastBackup.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var askBackupPassphrase by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(context.getString(it)) }
    }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) viewModel.export(uri)
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreUri = uri
    }

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.backup_title), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(stringResource(R.string.backup_explainer), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }

            Text(
                if (lastBackup == null) stringResource(R.string.backup_last_never)
                else stringResource(R.string.backup_last, Formatters.date(lastBackup!!)),
                style = MaterialTheme.typography.titleSmall,
            )
            Button(onClick = { askBackupPassphrase = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.backup_create))
            }

            Text(stringResource(R.string.restore_header), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            Text(stringResource(R.string.restore_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                onClick = {
                    viewModel.beforeFilePicker()
                    openFile.launch(arrayOf("*/*"))
                },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.restore_choose_file)) }
        }
    }

    if (askBackupPassphrase) {
        PassphraseDialog(
            title = R.string.backup_passphrase_title,
            help = R.string.backup_passphrase_help,
            confirmTwice = true,
            onDone = { pass ->
                askBackupPassphrase = false
                viewModel.beforeFilePicker(pass)
                createFile.launch(viewModel.suggestedFileName)
            },
            onDismiss = { askBackupPassphrase = false },
        )
    }
    restoreUri?.let { uri ->
        PassphraseDialog(
            title = R.string.restore_passphrase_title,
            help = R.string.restore_passphrase_help,
            confirmTwice = false,
            onDone = { pass ->
                restoreUri = null
                viewModel.inspect(uri, pass)
            },
            onDismiss = { restoreUri = null },
        )
    }
    state.pendingSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_text, summary.students, summary.payments)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestore) {
                    Text(stringResource(R.string.restore_confirm_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (state.restored) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.restore_done_title)) },
            text = { Text(stringResource(R.string.restore_done_text)) },
            confirmButton = { TextButton(onClick = { (context as? Activity)?.let(::restartApp) }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

/** Asks for a backup passphrase (twice when creating one, so a typo can't lock the tutor out). */
@Composable
private fun PassphraseDialog(
    @StringRes title: Int,
    @StringRes help: Int,
    confirmTwice: Boolean,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(help), style = MaterialTheme.typography.bodyMedium)
                PassphraseField(first, { first = it; error = null }, R.string.field_passphrase)
                if (confirmTwice) PassphraseField(second, { second = it; error = null }, R.string.field_passphrase_again)
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    first.length < BackupFiles.MIN_PASSPHRASE_LENGTH && confirmTwice -> R.string.error_passphrase_short
                    first.isEmpty() -> R.string.error_passphrase_short
                    confirmTwice && first != second -> R.string.error_passphrase_mismatch
                    else -> null
                }
                if (error == null) onDone(first)
            }) { Text(stringResource(R.string.action_continue)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun PassphraseField(value: String, onChange: (String) -> Unit, @StringRes label: Int) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Starts a fresh app process so everything reopens on the restored database. */
fun restartApp(activity: Activity) {
    activity.startActivity(Intent.makeRestartActivityTask(ComponentName(activity, MainActivity::class.java)))
    Runtime.getRuntime().exit(0)
}
