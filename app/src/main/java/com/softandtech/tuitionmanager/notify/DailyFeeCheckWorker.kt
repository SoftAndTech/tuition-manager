package com.softandtech.tuitionmanager.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.softandtech.tuitionmanager.data.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Runs once a day (morning): if fees are overdue and reminders are on, posts a notification. */
class DailyFeeCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    /** Lets the worker reach Hilt singletons without the extra hilt-work setup. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun settings(): SettingsRepository
        fun dailyFeeCheck(): DailyFeeCheck
        fun notifier(): FeeReminderNotifier
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        if (!deps.settings().current().dailyReminder) return Result.success()

        val summary = deps.dailyFeeCheck().run()
        if (summary.students > 0) deps.notifier().showOverdue(summary)
        return Result.success()
    }
}
