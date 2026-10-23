package com.softandtech.tuitionmanager.ui.receipt

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.softandtech.tuitionmanager.data.db.TuitionDatabase
import com.softandtech.tuitionmanager.data.db.entity.BatchEntity
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import com.softandtech.tuitionmanager.data.repository.BatchRepository
import com.softandtech.tuitionmanager.data.repository.FeeRepository
import com.softandtech.tuitionmanager.data.repository.StudentRepository
import com.softandtech.tuitionmanager.data.settings.AppSettings
import com.softandtech.tuitionmanager.domain.model.PaymentMode
import com.softandtech.tuitionmanager.ui.common.Formatters
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiptAndReminderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: TuitionDatabase
    private lateinit var fees: FeeRepository
    private var studentId = 0L

    private val oct21 = LocalDate.of(2026, 10, 21)
    private val settings = AppSettings(instituteName = "Bright Tuition", upiId = "bright@okaxis", receiptPrefix = "BT-")

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, TuitionDatabase::class.java).build()
        fees = FeeRepository(db)
        val students = StudentRepository(db, fees)
        val batches = BatchRepository(db)
        val batchId = batches.add(BatchEntity(name = "Class 10 Maths", monthlyFeePaise = 100_000))
        studentId = students.add(StudentEntity(name = "Asha Sharma", className = "Class 10", parentName = "Mrs Sharma", joinDate = LocalDate.of(2026, 9, 1)))
        batches.enroll(studentId, batchId, LocalDate.of(2026, 9, 1))
        fees.generateDues(oct21) // Sep + Oct = ₹2,000
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun receiptData_showsWhatThePaymentCoveredAndAdvance() = runTest {
        val payment = fees.collectPayment(studentId, 250_000, PaymentMode.UPI, oct21) // ₹500 extra
        val receipt = fees.getReceipt(payment.id)!!

        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10)), receipt.lines.map { it.period })
        assertEquals(50_000, receipt.advancePaise)
        assertEquals(0, receipt.balanceDuePaise)
        assertEquals("Asha Sharma", receipt.student.name)
    }

    @Test
    fun pdfIsWrittenAndValid() = runTest {
        val payment = fees.collectPayment(studentId, 100_000, PaymentMode.CASH, oct21, note = "September")
        val file = ReceiptPdfWriter(context).write(fees.getReceipt(payment.id)!!, settings)

        assertEquals("Receipt_BT-0001.pdf", file.name)
        assertTrue(file.length() > 500)
        assertEquals("%PDF", file.inputStream().use { input -> String(ByteArray(4).also { input.read(it) }) })
    }

    @Test
    fun pdfWriterClearsOldReceipts() = runTest {
        val writer = ReceiptPdfWriter(context)
        val first = writer.write(fees.getReceipt(fees.collectPayment(studentId, 50_000, PaymentMode.CASH, oct21).id)!!, settings)
        val second = writer.write(fees.getReceipt(fees.collectPayment(studentId, 50_000, PaymentMode.CASH, oct21).id)!!, settings)
        assertTrue(second.exists())
        assertTrue(!first.exists())
    }

    @Test
    fun reminderListsAmountMonthsAndUpi() = runTest {
        val unpaid = fees.unpaidDues(studentId, oct21)
        val message = Reminders.feeReminder(context, "Asha Sharma", "Mrs Sharma", unpaid, settings)

        // Checked in a language-neutral way: the device may run the app in English or Hindi
        assertTrue(message, message.lines().first().contains("Mrs Sharma"))
        assertTrue(message, message.contains("₹2,000"))
        assertTrue(message, message.contains("Asha Sharma"))
        assertTrue(message, message.contains(Formatters.month(YearMonth.of(2026, 9)) + ", " + Formatters.month(YearMonth.of(2026, 10))))
        assertTrue(message, message.contains("bright@okaxis"))
        assertTrue(message, message.trimEnd().endsWith("Bright Tuition"))
    }

    @Test
    fun reminderOnlyCountsWhatIsDueByToday() = runTest {
        // Oct due date is the 5th; on 3 Oct only September counts
        val unpaid = fees.unpaidDues(studentId, LocalDate.of(2026, 10, 3))
        assertEquals(listOf(YearMonth.of(2026, 9)), unpaid.map { it.due.period })
    }

    @Test
    fun reminderPrefersParentPhone() {
        assertEquals("919876543210", Reminders.waNumberFor(parentPhone = "9876543210", studentPhone = "9123456780"))
        assertEquals("919123456780", Reminders.waNumberFor(parentPhone = null, studentPhone = "9123456780"))
        assertEquals("919123456780", Reminders.waNumberFor(parentPhone = "123", studentPhone = "9123456780"))
    }
}
