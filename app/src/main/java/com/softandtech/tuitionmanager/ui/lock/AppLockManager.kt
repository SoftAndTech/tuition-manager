package com.softandtech.tuitionmanager.ui.lock

import android.os.SystemClock
import com.softandtech.tuitionmanager.data.security.SecurityRepository
import com.softandtech.tuitionmanager.domain.security.LockPolicy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LockState {
    /** Settings not read yet — show nothing, so app content never flashes before the lock. */
    Unknown,
    Locked,
    Unlocked,
}

/**
 * Decides when the PIN is needed: on a cold start, and when the app returns after being away
 * longer than the tutor's auto-lock time.
 */
@Singleton
class AppLockManager @Inject constructor(
    private val securityRepository: SecurityRepository,
) {
    private val _state = MutableStateFlow(LockState.Unknown)
    val state: StateFlow<LockState> = _state.asStateFlow()

    /** Elapsed-realtime when the app last left the screen; null until it first does. */
    private var stoppedAt: Long? = null
    private var skipNextLock = false

    /** Call from Activity.onStart. */
    suspend fun onAppForeground() {
        val settings = securityRepository.current()
        val away = stoppedAt?.let { SystemClock.elapsedRealtime() - it }
        val lock = when {
            _state.value == LockState.Locked -> true
            _state.value == LockState.Unknown -> settings.pinSet
            skipNextLock -> false
            else -> LockPolicy.shouldLock(settings.pinSet, away, settings.autoLockSeconds)
        }
        skipNextLock = false
        _state.value = if (lock) LockState.Locked else LockState.Unlocked
    }

    /** Call from Activity.onStop. */
    fun onAppBackground() {
        stoppedAt = SystemClock.elapsedRealtime()
    }

    /**
     * The app is about to open another screen on the tutor's behalf (file picker, share sheet,
     * WhatsApp). Coming straight back from it shouldn't ask for the PIN again.
     */
    fun allowExternalScreen() {
        skipNextLock = true
    }

    fun unlock() {
        _state.value = LockState.Unlocked
    }
}

