package com.softandtech.tuitionmanager.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import com.softandtech.tuitionmanager.notify.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.data.settings.AppSettings
import com.softandtech.tuitionmanager.data.settings.SettingsRepository
import com.softandtech.tuitionmanager.domain.Phone
import com.softandtech.tuitionmanager.domain.model.JoinMonthPolicy
import com.softandtech.tuitionmanager.ui.common.FormField
import com.softandtech.tuitionmanager.ui.common.TitleTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val form: AppSettings = AppSettings(),
    @StringRes val phoneError: Int? = null,
    @StringRes val upiError: Int? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    val dailyReminder: StateFlow<Boolean> = settingsRepository.settings
        .map { it.dailyReminder }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Takes effect immediately; the screen has already obtained notification permission if needed. */
    fun setDailyReminder(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDailyReminder(enabled)
            reminderScheduler.apply(enabled)
        }
    }

    private val _saved = Channel<Unit>(Channel.CONFLATED)
    val saved = _saved.receiveAsFlow()

    init {
        viewModelScope.launch { _state.value = SettingsUiState(loading = false, form = settingsRepository.current()) }
    }

    fun edit(transform: (AppSettings) -> AppSettings) =
        _state.update { it.copy(form = transform(it.form), phoneError = null, upiError = null) }

    fun save() {
        val form = _state.value.form
        val phoneError = if (form.tutorPhone.isNotBlank() && !Phone.isValid(form.tutorPhone)) R.string.error_phone_invalid else null
        // UPI IDs look like name@bank; catch obvious typos without being strict about bank handles
        val upiError = if (form.upiId.isNotBlank() && !UPI_ID.matches(form.upiId.trim())) R.string.error_upi_invalid else null
        if (phoneError != null || upiError != null) {
            _state.update { it.copy(phoneError = phoneError, upiError = upiError) }
            return
        }
        viewModelScope.launch {
            settingsRepository.save(form)
            _saved.send(Unit)
        }
    }

    private companion object {
        val UPI_ID = Regex("""[A-Za-z0-9._-]{2,}@[A-Za-z][A-Za-z0-9.-]*""")
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dailyReminder by viewModel.dailyReminder.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.saved.collect { snackbar.showSnackbar(context.getString(R.string.settings_saved)) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setDailyReminder(true)
        else scope.launch { snackbar.showSnackbar(context.getString(R.string.settings_notify_denied)) }
    }
    val onReminderToggle: (Boolean) -> Unit = { enable ->
        val needsPermission = enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else viewModel.setDailyReminder(enable)
    }

    Scaffold(
        topBar = { TitleTopBar(stringResource(R.string.nav_settings)) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) return@Scaffold
        val form = state.form
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_profile_header), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_profile_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FormField(
                value = form.instituteName,
                onValueChange = { v -> viewModel.edit { it.copy(instituteName = v) } },
                label = stringResource(R.string.field_institute),
                capitalization = KeyboardCapitalization.Words,
            )
            FormField(
                value = form.tutorName,
                onValueChange = { v -> viewModel.edit { it.copy(tutorName = v) } },
                label = stringResource(R.string.field_tutor_name),
                capitalization = KeyboardCapitalization.Words,
            )
            FormField(
                value = form.tutorPhone,
                onValueChange = { v -> viewModel.edit { it.copy(tutorPhone = v) } },
                label = stringResource(R.string.field_tutor_phone),
                error = state.phoneError,
                keyboardType = KeyboardType.Phone,
            )
            FormField(
                value = form.upiId,
                onValueChange = { v -> viewModel.edit { it.copy(upiId = v) } },
                label = stringResource(R.string.field_upi),
                placeholder = stringResource(R.string.upi_hint),
                error = state.upiError,
                keyboardType = KeyboardType.Email,
            )
            FormField(
                value = form.receiptPrefix,
                onValueChange = { v -> viewModel.edit { it.copy(receiptPrefix = v.take(12)) } },
                label = stringResource(R.string.field_receipt_prefix),
                placeholder = AppSettings.DEFAULT_RECEIPT_PREFIX,
            )

            Text(stringResource(R.string.settings_join_policy), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            PolicyOption(R.string.policy_full, JoinMonthPolicy.FULL, form.joinMonthPolicy) { p -> viewModel.edit { it.copy(joinMonthPolicy = p) } }
            PolicyOption(R.string.policy_prorated, JoinMonthPolicy.PRORATED, form.joinMonthPolicy) { p -> viewModel.edit { it.copy(joinMonthPolicy = p) } }
            PolicyOption(R.string.policy_next_month, JoinMonthPolicy.NEXT_MONTH, form.joinMonthPolicy) { p -> viewModel.edit { it.copy(joinMonthPolicy = p) } }

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.action_save))
            }

            Text(stringResource(R.string.settings_notifications_header), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().toggleable(value = dailyReminder, role = Role.Switch, onValueChange = onReminderToggle),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_daily_reminder), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_daily_reminder_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = dailyReminder, onCheckedChange = null)
            }

            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.settings_privacy_note), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PolicyOption(@StringRes text: Int, value: JoinMonthPolicy, selected: JoinMonthPolicy, onSelect: (JoinMonthPolicy) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) }),
    ) {
        RadioButton(selected = value == selected, onClick = null)
        Text(stringResource(text), modifier = Modifier.padding(start = 8.dp))
    }
}
