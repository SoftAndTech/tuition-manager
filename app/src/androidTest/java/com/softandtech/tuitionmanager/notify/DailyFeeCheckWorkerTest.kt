package com.softandtech.tuitionmanager.notify

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real worker inside the real app (its encrypted database and settings), bypassing the
 * 9 AM schedule. With reminders on and fees overdue, a notification appears on the device.
 */
@RunWith(AndroidJUnit4::class)
class DailyFeeCheckWorkerTest {

    @Test
    fun workerCompletesSuccessfully() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val worker = TestListenableWorkerBuilder<DailyFeeCheckWorker>(context).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }
}
