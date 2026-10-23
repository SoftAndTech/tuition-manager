package com.softandtech.tuitionmanager.notify

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    /** Schedules (or cancels) the daily check. Safe to call on every app start. */
    fun apply(enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<DailyFeeCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntil(LocalDateTime.now(clock), REMINDER_TIME).toMinutes(), TimeUnit.MINUTES)
            .build()
        // KEEP: re-applying on each launch must not push the first run back
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val WORK_NAME = "daily-fee-check"

        /** Morning, before most tuition classes start. */
        val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

        /** Time from [now] until the next [at] — later today, or tomorrow if it has passed. */
        fun delayUntil(now: LocalDateTime, at: LocalTime): Duration {
            var next = now.toLocalDate().atTime(at)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next)
        }
    }
}
