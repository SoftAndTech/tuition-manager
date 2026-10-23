package com.softandtech.tuitionmanager

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.softandtech.tuitionmanager.data.repository.FeeSync
import com.softandtech.tuitionmanager.data.security.SecurityRepository
import com.softandtech.tuitionmanager.data.settings.SettingsRepository
import com.softandtech.tuitionmanager.notify.ReminderScheduler
import com.softandtech.tuitionmanager.ui.lock.AppLockManager
import com.softandtech.tuitionmanager.ui.lock.LockScreen
import com.softandtech.tuitionmanager.ui.lock.LockState
import com.softandtech.tuitionmanager.ui.navigation.AppNavHost
import com.softandtech.tuitionmanager.ui.receipt.Sharing
import com.softandtech.tuitionmanager.ui.theme.TuitionTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** A FragmentActivity because the fingerprint prompt (BiometricPrompt) requires one. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var feeSync: FeeSync
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var securityRepository: SecurityRepository
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var lockManager: AppLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Sharing.beforeLeavingApp = lockManager::allowExternalScreen

        // Optional: block screenshots and blank the app in the recent-apps view
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                securityRepository.settings.collect { s ->
                    if (s.secureScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        setContent {
            TuitionTheme {
                val lockState by lockManager.state.collectAsStateWithLifecycle()
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
                        // Keep the app composed under the lock so the tutor returns to the same screen,
                        // but hide it from screen readers while locked
                        if (lockState != LockState.Unknown) {
                            Box(if (lockState == LockState.Locked) Modifier.clearAndSetSemantics {} else Modifier) {
                                AppNavHost()
                            }
                        }
                        if (lockState == LockState.Locked) LockScreen()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            lockManager.onAppForeground()
            // Each time the app comes to the foreground — a new month may have started since last time
            feeSync.run()
            // Re-applying is harmless (KEEP) and restores the schedule if the system ever dropped it
            reminderScheduler.apply(settingsRepository.current().dailyReminder)
        }
    }

    override fun onStop() {
        super.onStop()
        lockManager.onAppBackground()
    }
}
