package com.softandtech.tuitionmanager.notify

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.softandtech.tuitionmanager.data.db.TuitionDatabase
import com.softandtech.tuitionmanager.data.db.entity.BatchEntity
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import com.softandtech.tuitionmanager.data.repository.AttendanceRepository
import com.softandtech.tuitionmanager.data.repository.BatchRepository
import com.softandtech.tuitionmanager.data.repository.FeeRepository
import com.softandtech.tuitionmanager.data.repository.FeeSync
import com.softandtech.tuitionmanager.data.repository.StudentRepository
import com.softandtech.tuitionmanager.data.settings.SettingsRepository
import com.softandtech.tuitionmanager.domain.model.AttendanceStatus
import com.softandtech.tuitionmanager.domain.model.PaymentMode
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyFeeCheckTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: TuitionDatabase
    private lateinit var fees: FeeRepository
    private lateinit var students: StudentRepository
    private lateinit var batches: BatchRepository
    private val storeName = "test-settings-${System.nanoTime()}"

    /** "Today" is fixed so dues and overdue status are predictable. */
    private val today = LocalDate.of(2026, 10, 23)
    private val clock = Clock.fixed(today.atTime(10, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, TuitionDatabase::class.java).build()
        fees = FeeRepository(db)
        students = StudentRepository(db, fees)
        batches = BatchRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        context.preferencesDataStoreFile(storeName).delete()
    }

    private fun dailyCheck(): DailyFeeCheck {
        val settings = SettingsRepository(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(storeName) })
        return DailyFeeCheck(FeeSync(fees, settings, clock), fees, clock)
    }

    @Test
    fun countsStudentsWithOverdueFees_afterBillingTheNewMonth() = runTest {
        val batch = batches.add(BatchEntity(name = "Maths", monthlyFeePaise = 100_000, feeDueDay = 5))
        val asha = students.add(StudentEntity(name = "Asha", joinDate = LocalDate.of(2026, 9, 1)))
        val ravi = students.add(StudentEntity(name = "Ravi", joinDate = LocalDate.of(2026, 9, 1)))
        val meera = students.add(StudentEntity(name = "Meera", joinDate = LocalDate.of(2026, 9, 1)))
        listOf(asha, ravi, meera).forEach { batches.enroll(it, batch, LocalDate.of(2026, 9, 1)) }

        // Nothing billed yet: the check itself must create Sep + Oct dues
        val check = dailyCheck()
        fees.collectPayment(meera, 200_000, PaymentMode.CASH, today) // Meera pays in full (as advance first)
        val summary = check.run()

        assertEquals(2, summary.students) // Asha and Ravi
        assertEquals(400_000, summary.totalPaise) // 2 students × 2 months × ₹1,000
    }

    @Test
    fun nothingOverdue_givesZero() = runTest {
        assertEquals(OverdueSummary(0, 0), dailyCheck().run())
    }

    @Test
    fun markedCountsPerBatch() = runTest {
        val attendance = AttendanceRepository(db)
        val maths = batches.add(BatchEntity(name = "Maths", monthlyFeePaise = 0))
        val art = batches.add(BatchEntity(name = "Art", monthlyFeePaise = 0))
        val a = students.add(StudentEntity(name = "A", joinDate = today))
        val b = students.add(StudentEntity(name = "B", joinDate = today))
        attendance.mark(maths, today, mapOf(a to AttendanceStatus.PRESENT, b to AttendanceStatus.ABSENT))
        attendance.mark(art, today.minusDays(1), mapOf(a to AttendanceStatus.PRESENT))

        assertEquals(mapOf(maths to 2), attendance.observeMarkedCounts(today).first())
    }
}
