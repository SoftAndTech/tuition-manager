package com.softandtech.tuitionmanager.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.softandtech.tuitionmanager.data.db.TuitionDatabase
import com.softandtech.tuitionmanager.data.db.entity.BatchEntity
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import com.softandtech.tuitionmanager.domain.model.AttendanceStatus
import com.softandtech.tuitionmanager.domain.model.DueStatus
import com.softandtech.tuitionmanager.domain.model.PaymentMode
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises repositories and SQL against an in-memory database (plain SQLite; encryption is tested separately). */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {

    private lateinit var db: TuitionDatabase
    private lateinit var fees: FeeRepository
    private lateinit var students: StudentRepository
    private lateinit var batches: BatchRepository
    private lateinit var attendance: AttendanceRepository

    private val oct21 = LocalDate.of(2026, 10, 21)
    private var studentId = 0L
    private var batchId = 0L

    @Before
    fun setUp() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TuitionDatabase::class.java).build()
        fees = FeeRepository(db)
        students = StudentRepository(db, fees)
        batches = BatchRepository(db)
        attendance = AttendanceRepository(db)

        batchId = batches.add(BatchEntity(name = "Class 10 Maths", monthlyFeePaise = 100_000, feeDueDay = 5))
        studentId = students.add(StudentEntity(name = "Asha", joinDate = LocalDate.of(2026, 8, 1), parentPhone = "9800000000"))
        batches.enroll(studentId, batchId, joinedOn = LocalDate.of(2026, 8, 1))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun dues() = fees.observeDues(studentId).first().sortedBy { it.due.dueDate }

    @Test
    fun generateDues_backfillsAndIsIdempotent() = runTest {
        assertEquals(3, fees.generateDues(oct21))
        assertEquals(0, fees.generateDues(oct21))
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)), dues().map { it.due.period })
        assertEquals(300_000, fees.observeOutstanding(studentId).first())
    }

    @Test
    fun payment_appliedOldestFirst_withPartialRemainder() = runTest {
        fees.generateDues(oct21)
        val payment = fees.collectPayment(studentId, 250_000, PaymentMode.UPI, oct21)

        assertEquals(1, payment.receiptSeq)
        assertEquals(listOf(DueStatus.PAID, DueStatus.PAID, DueStatus.PARTIAL), dues().map { it.status })
        assertEquals(50_000, fees.observeOutstanding(studentId).first())
        assertEquals(3, fees.allocationsFor(payment.id).size)

        assertEquals(2, fees.collectPayment(studentId, 50_000, PaymentMode.CASH, oct21).receiptSeq)
        assertEquals(0, fees.observeOutstanding(studentId).first())
    }

    @Test
    fun advancePayment_isUsedForNextMonthAutomatically() = runTest {
        fees.generateDues(oct21)
        fees.collectPayment(studentId, 350_000, PaymentMode.CASH, oct21) // ₹500 more than owed
        assertEquals(50_000, fees.observeAdvanceCredit(studentId).first())

        fees.generateDues(LocalDate.of(2026, 11, 2))
        val nov = dues().last()
        assertEquals(YearMonth.of(2026, 11), nov.due.period)
        assertEquals(DueStatus.PARTIAL, nov.status)
        assertEquals(50_000, nov.paidPaise)
        assertEquals(0, fees.observeAdvanceCredit(studentId).first())
    }

    @Test
    fun oneTimeDue_consumesExistingCredit() = runTest {
        fees.collectPayment(studentId, 20_000, PaymentMode.CASH, oct21)
        fees.addOneTimeDue(studentId, "Admission fee", 20_000, oct21)
        val admission = dues().single { it.due.period == null }
        assertEquals(DueStatus.PAID, admission.status)
    }

    @Test
    fun deletingPayment_reopensDues() = runTest {
        fees.generateDues(oct21)
        val payment = fees.collectPayment(studentId, 300_000, PaymentMode.CASH, oct21)
        assertEquals(0, fees.observeOutstanding(studentId).first())

        fees.deletePayment(payment.id)
        assertEquals(300_000, fees.observeOutstanding(studentId).first())
        assertTrue(dues().all { it.status == DueStatus.DUE })
    }

    @Test
    fun overdue_listsStudentWithOldestDueDate_andWaiverClearsIt() = runTest {
        fees.generateDues(oct21)
        val overdue = fees.observeOverdue(oct21).first().single()
        assertEquals("Asha", overdue.name)
        assertEquals(300_000, overdue.outstandingPaise)
        assertEquals(LocalDate.of(2026, 8, 5), overdue.oldestDueDate)

        dues().forEach { fees.setWaived(it.due.id, true) }
        assertTrue(fees.observeOverdue(oct21).first().isEmpty())
        assertEquals(DueStatus.WAIVED, dues().first().status)
    }

    @Test
    fun deactivatedStudent_isBilledToLeavingMonthOnly() = runTest {
        students.deactivate(studentId, oct21)
        assertEquals(3, dues().size)
        assertEquals(0, fees.generateDues(LocalDate.of(2026, 12, 1)))
        assertEquals(0, students.observeActiveCount().first())
    }

    @Test
    fun monthlySummary_billedVsCollected() = runTest {
        fees.generateDues(oct21)
        fees.collectPayment(studentId, 120_000, PaymentMode.UPI, oct21)
        assertEquals(100_000, fees.observeBilled(YearMonth.of(2026, 10)).first())
        assertEquals(120_000, fees.observeCollected(YearMonth.of(2026, 10)).first())
        assertEquals(0, fees.observeCollected(YearMonth.of(2026, 9)).first())
    }

    @Test
    fun attendance_remarkingOverwrites_andSummaryCounts() = runTest {
        val day1 = LocalDate.of(2026, 10, 19)
        val day2 = LocalDate.of(2026, 10, 20)
        attendance.mark(batchId, day1, mapOf(studentId to AttendanceStatus.ABSENT))
        attendance.mark(batchId, day1, mapOf(studentId to AttendanceStatus.PRESENT)) // correction
        attendance.mark(batchId, day2, mapOf(studentId to AttendanceStatus.ABSENT))

        assertEquals(AttendanceStatus.PRESENT, attendance.observeForBatchOnDate(batchId, day1).first().single().status)
        val summary = attendance.observeSummary(studentId, day1, day2).first()
        assertEquals(1, summary.present)
        assertEquals(1, summary.absent)
        assertEquals(50, summary.percent)
    }

    @Test
    fun register_onlyIncludesStudentsEnrolledOnThatDate() = runTest {
        val late = students.add(StudentEntity(name = "Ravi", joinDate = LocalDate.of(2026, 10, 15)))
        batches.enroll(late, batchId, joinedOn = LocalDate.of(2026, 10, 15))

        assertEquals(listOf("Asha"), batches.observeStudentsInBatch(batchId, LocalDate.of(2026, 10, 10)).first().map { it.name })
        assertEquals(listOf("Asha", "Ravi"), batches.observeStudentsInBatch(batchId, oct21).first().map { it.name })
    }

    @Test
    fun search_matchesNameAndPhone() = runTest {
        assertEquals(1, students.observeStudents("ash").first().size)
        assertEquals(1, students.observeStudents("98000").first().size)
        assertTrue(students.observeStudents("zzz").first().isEmpty())
    }
}
