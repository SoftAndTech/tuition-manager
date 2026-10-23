package com.softandtech.tuitionmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.softandtech.tuitionmanager.data.repository.FeeSync
import com.softandtech.tuitionmanager.data.settings.SettingsRepository
import com.softandtech.tuitionmanager.notify.ReminderScheduler
import com.softandtech.tuitionmanager.ui.navigation.AppNavHost
import com.softandtech.tuitionmanager.ui.theme.TuitionTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var feeSync: FeeSync
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var reminderScheduler: ReminderScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TuitionTheme {
                AppNavHost()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            // Each time the app comes to the foreground — a new month may have started since last time
            feeSync.run()
            // Re-applying is harmless (KEEP) and restores the schedule if the system ever dropped it
            reminderScheduler.apply(settingsRepository.current().dailyReminder)
        }
    }
}
