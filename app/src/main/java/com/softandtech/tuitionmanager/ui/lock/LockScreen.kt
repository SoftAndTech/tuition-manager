package com.softandtech.tuitionmanager.ui.lock

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.data.security.PinCheck
import com.softandtech.tuitionmanager.data.security.SecurityRepository
import com.softandtech.tuitionmanager.domain.security.PinHasher
import com.softandtech.tuitionmanager.ui.common.ConfirmDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LockUiState(
    val pin: String = "",
    val checking: Boolean = false,
    val attemptsLeft: Int? = null,
    /** Seconds until another try is allowed; 0 = not locked out. */
    val waitSeconds: Long = 0,
    val biometricEnabled: Boolean = false,
)

@HiltViewModel
class LockViewModel @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val lockManager: AppLockManager,
) : ViewModel() {

    private val _state = MutableStateFlow(LockUiState())
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(biometricEnabled = securityRepository.current().biometricEnabled) }
            startCountdown(securityRepository.lockedOutUntil())
        }
    }

    fun onPinChange(pin: String) = _state.update { it.copy(pin = pin.filter(Char::isDigit).take(PinHasher.PIN_LENGTHS.last)) }

    fun submit() {
        val s = _state.value
        if (s.checking || s.waitSeconds > 0 || !PinHasher.isValidPin(s.pin)) return
        _state.update { it.copy(checking = true) }
        viewModelScope.launch {
            when (val result = securityRepository.checkPin(s.pin)) {
                PinCheck.Correct -> lockManager.unlock()
                is PinCheck.Wrong -> _state.update { it.copy(pin = "", checking = false, attemptsLeft = result.attemptsLeft) }
                is PinCheck.LockedOut -> {
                    _state.update { it.copy(pin = "", checking = false, attemptsLeft = null) }
                    startCountdown(result.untilMillis)
                }
            }
        }
    }

    fun onBiometricSuccess() {
        viewModelScope.launch {
            securityRepository.recordUnlock()
            lockManager.unlock()
        }
    }

    private fun startCountdown(untilMillis: Long) {
        viewModelScope.launch {
            while (true) {
                val left = (untilMillis - System.currentTimeMillis() + 999) / 1000
                _state.update { it.copy(waitSeconds = left.coerceAtLeast(0)) }
                if (left <= 0) break
                delay(1_000)
            }
        }
    }
}

@Composable
fun LockScreen(viewModel: LockViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val canUseBiometric = state.biometricEnabled && activity != null && Biometrics.isAvailable(context)
    var askErase by remember { mutableStateOf(false) }

    // Offer fingerprint straight away; the PIN pad stays underneath as the fallback
    LaunchedEffect(canUseBiometric) {
        if (canUseBiometric) Biometrics.prompt(activity!!, viewModel::onBiometricSuccess)
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.lock_enter_pin), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(20.dp))
            PinDots(state.pin.length)
            Spacer(Modifier.height(12.dp))
            val message = when {
                state.waitSeconds > 0 -> stringResource(R.string.lock_wait, state.waitSeconds)
                state.attemptsLeft != null -> stringResource(R.string.lock_wrong_pin, state.attemptsLeft!!)
                else -> ""
            }
            Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.height(40.dp))
            PinPad(
                pin = state.pin,
                onPinChange = viewModel::onPinChange,
                onSubmit = viewModel::submit,
                enabled = state.waitSeconds == 0L && !state.checking,
            )
            Spacer(Modifier.height(16.dp))
            if (canUseBiometric) {
                OutlinedButton(onClick = { Biometrics.prompt(activity!!, viewModel::onBiometricSuccess) }) {
                    Text(stringResource(R.string.lock_use_biometric))
                }
            }
            TextButton(onClick = { askErase = true }) { Text(stringResource(R.string.lock_forgot_pin)) }
        }
    }

    if (askErase) {
        ConfirmDialog(
            title = stringResource(R.string.lock_forgot_title),
            text = stringResource(R.string.lock_forgot_text),
            confirmLabel = stringResource(R.string.action_erase_everything),
            onConfirm = { eraseAllAppData(context) },
            onDismiss = { askErase = false },
        )
    }
}

/**
 * Wipes the database, its key, settings and the PIN, then closes the app — like a fresh install.
 * The only way past a forgotten PIN; data comes back only from a backup file.
 */
fun eraseAllAppData(context: Context) {
    context.getSystemService(ActivityManager::class.java).clearApplicationUserData()
}
