package com.softandtech.tuitionmanager.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.data.security.PinCheck
import com.softandtech.tuitionmanager.data.security.SecurityRepository
import com.softandtech.tuitionmanager.ui.common.BackTopBar
import com.softandtech.tuitionmanager.ui.navigation.PinSetupRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PinSetupMode { SET, CHANGE, REMOVE }

private enum class PinStep { CURRENT, NEW, CONFIRM }

data class PinSetupUiState(
    val step: Int = 0,
    val pin: String = "",
    val error: Int? = null,
    val busy: Boolean = false,
    val done: Boolean = false,
)

@HiltViewModel
class PinSetupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val securityRepository: SecurityRepository,
) : ViewModel() {

    val mode = PinSetupMode.valueOf(savedStateHandle.toRoute<PinSetupRoute>().mode)

    /** Changing or removing the PIN first proves the tutor knows the current one. */
    private val steps = when (mode) {
        PinSetupMode.SET -> listOf(PinStep.NEW, PinStep.CONFIRM)
        PinSetupMode.CHANGE -> listOf(PinStep.CURRENT, PinStep.NEW, PinStep.CONFIRM)
        PinSetupMode.REMOVE -> listOf(PinStep.CURRENT)
    }
    private var firstEntry = ""

    private val _state = MutableStateFlow(PinSetupUiState())
    val state: StateFlow<PinSetupUiState> = _state.asStateFlow()

    fun titleFor(step: Int): Int = when (steps[step]) {
        PinStep.CURRENT -> R.string.pin_enter_current
        PinStep.NEW -> R.string.pin_choose_new
        PinStep.CONFIRM -> R.string.pin_confirm_new
    }

    fun onPinChange(pin: String) = _state.update { it.copy(pin = pin, error = null) }

    fun submit() {
        val s = _state.value
        if (s.busy) return
        when (steps[s.step]) {
            PinStep.CURRENT -> {
                _state.update { it.copy(busy = true) }
                viewModelScope.launch {
                    when (val result = securityRepository.checkPin(s.pin)) {
                        PinCheck.Correct -> if (mode == PinSetupMode.REMOVE) {
                            securityRepository.clearPin()
                            _state.update { it.copy(done = true) }
                        } else {
                            _state.update { it.copy(step = it.step + 1, pin = "", busy = false) }
                        }
                        is PinCheck.Wrong -> _state.update { it.copy(pin = "", busy = false, error = R.string.pin_wrong_current) }
                        is PinCheck.LockedOut -> _state.update { it.copy(pin = "", busy = false, error = R.string.pin_too_many) }
                    }
                }
            }
            PinStep.NEW -> {
                firstEntry = s.pin
                _state.update { it.copy(step = it.step + 1, pin = "") }
            }
            PinStep.CONFIRM -> {
                if (s.pin != firstEntry) {
                    // Start the new PIN again rather than leaving the tutor guessing which entry was wrong
                    _state.update { it.copy(step = it.step - 1, pin = "", error = R.string.pin_mismatch) }
                    return
                }
                _state.update { it.copy(busy = true) }
                viewModelScope.launch {
                    securityRepository.setPin(s.pin)
                    _state.update { it.copy(done = true) }
                }
            }
        }
    }
}

@Composable
fun PinSetupScreen(onDone: () -> Unit, onBack: () -> Unit, viewModel: PinSetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }

    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(
                    when (viewModel.mode) {
                        PinSetupMode.SET -> R.string.pin_setup_title
                        PinSetupMode.CHANGE -> R.string.pin_change_title
                        PinSetupMode.REMOVE -> R.string.pin_remove_title
                    },
                ),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(viewModel.titleFor(state.step)), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (state.step == 0 && viewModel.mode == PinSetupMode.SET) {
                Text(
                    stringResource(R.string.pin_setup_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            PinDots(state.pin.length)
            Text(
                state.error?.let { stringResource(it) }.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 12.dp).height(40.dp),
            )
            PinPad(pin = state.pin, onPinChange = viewModel::onPinChange, onSubmit = viewModel::submit, enabled = !state.busy)
        }
    }
}
