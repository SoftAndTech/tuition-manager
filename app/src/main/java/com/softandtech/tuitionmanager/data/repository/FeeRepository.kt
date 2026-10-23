package com.softandtech.tuitionmanager.data.repository

import androidx.room.withTransaction
import com.softandtech.tuitionmanager.data.db.TuitionDatabase
import com.softandtech.tuitionmanager.data.db.entity.FeeDueEntity
import com.softandtech.tuitionmanager.data.db.entity.PaymentAllocationEntity
import com.softandtech.tuitionmanager.data.db.entity.PaymentEntity
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import com.softandtech.tuitionmanager.data.db.model.FeeDueWithPaid
import com.softandtech.tuitionmanager.data.db.model.ReceiptLine
import com.softandtech.tuitionmanager.data.db.model.StudentOverdue
import com.softandtech.tuitionmanager.domain.FeeDueGenerator
import com.softandtech.tuitionmanager.domain.PaymentAllocator
import com.softandtech.tuitionmanager.domain.model.JoinMonthPolicy
import com.softandtech.tuitionmanager.domain.model.PaymentMode
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class ReceiptData(
    val payment: PaymentEntity,
    val student: StudentEntity,
    val lines: List<ReceiptLine>,
    /** What the student still owes after this and all other payments. */
    val balanceDuePaise: Long,
) {
    /** Part of the payment not yet applied to any due — held as advance for future months. */
    val advancePaise: Long get() = payment.amountPaise - lines.sumOf { it.amountPaise }
}

/**
 * Fees, payments and receipts. All amounts are in paise (₹1 = 100).
 *
 * Every change that affects what a student owes ends with [settle], which applies any unspent
 * payment money to the oldest unpaid dues — so advance payments are used up automatically.
 */
@Singleton
class FeeRepository @Inject constructor(
    private val db: TuitionDatabase,
) {
    private val feeDueDao = db.feeDueDao()
    private val paymentDao = db.paymentDao()
    private val enrollmentDao = db.enrollmentDao()

    /** Creates any missing monthly dues up to [today]'s month. Safe to call repeatedly. Returns how many were created. */
    suspend fun generateDues(today: LocalDate, policy: JoinMonthPolicy = JoinMonthPolicy.FULL): Int =
        db.withTransaction {
            val existing = feeDueDao.existingPeriods().mapTo(HashSet()) { it.enrollmentId to it.period }
            val newDues = FeeDueGenerator.generate(enrollmentDao.getBillable(), existing, today, policy)
            if (newDues.isEmpty()) return@withTransaction 0

            val ids = feeDueDao.insertAllIgnoringDuplicates(
                newDues.map {
                    FeeDueEntity(
                        studentId = it.studentId,
                        enrollmentId = it.enrollmentId,
                        period = it.period,
                        title = it.title,
                        amountPaise = it.amountPaise,
                        dueDate = it.dueDate,
                    )
                },
            )
            newDues.map { it.studentId }.distinct().forEach { settle(it) }
            ids.count { it != -1L }
        }

    /** Records money received and applies it to the oldest dues. Any excess becomes advance credit. */
    suspend fun collectPayment(
        studentId: Long,
        amountPaise: Long,
        mode: PaymentMode,
        paidOn: LocalDate,
        note: String? = null,
    ): PaymentEntity {
        require(amountPaise > 0) { "Payment amount must be positive" }
        return db.withTransaction {
            val payment = PaymentEntity(
                studentId = studentId,
                amountPaise = amountPaise,
                mode = mode,
                paidOn = paidOn,
                receiptSeq = paymentDao.nextReceiptSeq(),
                note = note,
            )
            val id = paymentDao.insert(payment)
            settle(studentId)
            payment.copy(id = id)
        }
    }

    /** Removes a payment entered by mistake; its money is taken back off the dues it covered. */
    suspend fun deletePayment(paymentId: Long) = db.withTransaction {
        val payment = paymentDao.getById(paymentId) ?: return@withTransaction
        paymentDao.delete(paymentId)
        settle(payment.studentId)
    }

    /** Adds a one-off charge such as an admission fee or study material. */
    suspend fun addOneTimeDue(studentId: Long, title: String, amountPaise: Long, dueDate: LocalDate): Long {
        require(amountPaise > 0) { "Due amount must be positive" }
        return db.withTransaction {
            val id = feeDueDao.insert(FeeDueEntity(studentId = studentId, title = title, amountPaise = amountPaise, dueDate = dueDate))
            settle(studentId)
            id
        }
    }

    /** A waived due stops counting as owed. Anything already paid against it stays recorded. */
    suspend fun setWaived(dueId: Long, waived: Boolean) = db.withTransaction {
        val studentId = feeDueDao.studentIdOf(dueId) ?: return@withTransaction
        feeDueDao.setWaived(dueId, waived)
        settle(studentId)
    }

    suspend fun allocationsFor(paymentId: Long): List<PaymentAllocationEntity> = paymentDao.allocationsFor(paymentId)

    /** Everything printed on a receipt. Null if the payment was deleted. */
    suspend fun getReceipt(paymentId: Long): ReceiptData? = db.withTransaction {
        val payment = paymentDao.getById(paymentId) ?: return@withTransaction null
        val student = db.studentDao().getById(payment.studentId) ?: return@withTransaction null
        ReceiptData(
            payment = payment,
            student = student,
            lines = paymentDao.receiptLines(paymentId),
            balanceDuePaise = feeDueDao.observeTotalOutstandingForStudent(student.id).first(),
        )
    }

    /** Dues already due on [asOf] and not fully paid, oldest first. */
    suspend fun unpaidDues(studentId: Long, asOf: LocalDate): List<FeeDueWithPaid> = feeDueDao.unpaidDueBy(studentId, asOf)

    fun observeDues(studentId: Long): Flow<List<FeeDueWithPaid>> = feeDueDao.observeForStudent(studentId)

    fun observePayments(studentId: Long): Flow<List<PaymentEntity>> = paymentDao.observeForStudent(studentId)

    fun observeOutstanding(studentId: Long): Flow<Long> = feeDueDao.observeTotalOutstandingForStudent(studentId)

    fun observeAdvanceCredit(studentId: Long): Flow<Long> = paymentDao.observeAdvanceCredit(studentId)

    fun observeOverdue(today: LocalDate): Flow<List<StudentOverdue>> = feeDueDao.observeOverdue(today)

    fun observeBilled(month: YearMonth): Flow<Long> = feeDueDao.observeBilledForPeriod(month)

    fun observeCollected(month: YearMonth): Flow<Long> =
        paymentDao.observeCollectedBetween(month.atDay(1), month.atEndOfMonth())

    private suspend fun settle(studentId: Long) {
        val allocations = PaymentAllocator.allocate(
            credits = paymentDao.creditsForStudent(studentId),
            dues = feeDueDao.outstandingForStudent(studentId),
        )
        if (allocations.isNotEmpty()) {
            paymentDao.insertAllocations(
                allocations.map { PaymentAllocationEntity(paymentId = it.paymentId, feeDueId = it.dueId, amountPaise = it.amountPaise) },
            )
        }
    }
}
