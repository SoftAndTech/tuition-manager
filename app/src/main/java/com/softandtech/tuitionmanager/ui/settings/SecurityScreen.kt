package com.softandtech.tuitionmanager.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.data.security.SecurityRepository
import com.softandtech.tuitionmanager.data.security.SecuritySettings
import com.softandtech.tuitionmanager.domain.security.LockPolicy
import com.softandtech.tuitionmanager.ui.common.BackTopBar
import com.softandtech.tuitionmanager.ui.common.ConfirmDialog
import com.softandtech.tuitionmanager.ui.lock.Biometrics
import com.softandtech.tuitionmanager.ui.lock.PinSetupMode
import com.softandtech.tuitionmanager.ui.lock.eraseAllAppData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val securityRepository: SecurityRepository,
) : ViewModel() {
    /** Null while loading. */
    val settings: StateFlow<SecuritySettings?> = securityRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setBiometric(enabled: Boolean) = viewModelScope.launch { securityRepository.setBiometric(enabled) }
    fun setAutoLock(seconds: Int) = viewModelScope.launch { securityRepository.setAutoLockSeconds(seconds) }
    fun setSecureScreen(enabled: Boolean) = viewModelScope.launch { securityRepository.setSecureScreen(enabled) }
}

@Composable
fun SecurityScreen(
    onBack: () -> Unit,
    onPin: (PinSetupMode) -> Unit,
    viewModel: SecurityViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val biometricAvailable = remember { Biometrics.isAvailable(context) }
    var confirmErase by remember { mutableStateOf(false) }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.security_title), onBack) }) { padding ->
        val s = settings ?: return@Scaffold
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.security_lock_header), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(if (s.pinSet) R.string.security_lock_on else R.string.security_lock_off),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!s.pinSet) {
                Button(onClick = { onPin(PinSetupMode.SET) }) { Text(stringResource(R.string.security_set_pin)) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onPin(PinSetupMode.CHANGE) }) { Text(stringResource(R.string.security_change_pin)) }
                    OutlinedButton(onClick = { onPin(PinSetupMode.REMOVE) }) { Text(stringResource(R.string.security_remove_pin)) }
                }

                SwitchRow(
                    title = stringResource(R.string.security_biometric),
                    help = stringResource(if (biometricAvailable) R.string.security_biometric_help else R.string.security_biometric_unavailable),
                    checked = s.biometricEnabled && biometricAvailable,
                    enabled = biometricAvailable,
                    onChange = viewModel::setBiometric,
                )

                Text(stringResource(R.string.security_auto_lock), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                LockPolicy.AUTO_LOCK_CHOICES.forEach { seconds ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().selectable(
                            selected = s.autoLockSeconds == seconds,
                            role = Role.RadioButton,
                            onClick = { viewModel.setAutoLock(seconds) },
                        ),
                    ) {
                        RadioButton(selected = s.autoLockSeconds == seconds, onClick = null)
                        Text(autoLockLabel(seconds), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SwitchRow(
                title = stringResource(R.string.security_secure_screen),
                help = stringResource(R.string.security_secure_screen_help),
                checked = s.secureScreen,
                onChange = viewModel::setSecureScreen,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.security_erase_header), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.security_erase_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { confirmErase = true }) {
                Text(stringResource(R.string.action_erase_everything), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmErase) {
        ConfirmDialog(
            title = stringResource(R.string.security_erase_confirm_title),
            text = stringResource(R.string.security_erase_confirm_text),
            confirmLabel = stringResource(R.string.action_erase_everything),
            onConfirm = { eraseAllAppData(context) },
            onDismiss = { confirmErase = false },
        )
    }
}

@Composable
private fun autoLockLabel(seconds: Int): String = when (seconds) {
    0 -> stringResource(R.string.auto_lock_immediately)
    else -> pluralStringResource(R.plurals.auto_lock_after_minutes, seconds / 60, seconds / 60)
}

@Composable
fun SwitchRow(title: String, help: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
