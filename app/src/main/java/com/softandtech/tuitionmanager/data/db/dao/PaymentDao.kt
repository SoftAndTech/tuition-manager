package com.softandtech.tuitionmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.softandtech.tuitionmanager.data.db.entity.PaymentAllocationEntity
import com.softandtech.tuitionmanager.data.db.entity.PaymentEntity
import com.softandtech.tuitionmanager.domain.PaymentCredit
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {

    @Insert
    suspend fun insert(payment: PaymentEntity): Long

    @Insert
    suspend fun insertAllocations(allocations: List<PaymentAllocationEntity>)

    /** Allocations are removed by the foreign-key cascade. */
    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(receipt_seq), 0) + 1 FROM payments")
    suspend fun nextReceiptSeq(): Int

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getById(id: Long): PaymentEntity?

    @Query("SELECT * FROM payments WHERE student_id = :studentId ORDER BY paid_on DESC, id DESC")
    fun observeForStudent(studentId: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payment_allocations WHERE payment_id = :paymentId")
    suspend fun allocationsFor(paymentId: Long): List<PaymentAllocationEntity>

    /** A student's payments that still have unspent money, oldest first. */
    @Query(
        """
        SELECT paymentId, remainingPaise FROM (
            SELECT p.id AS paymentId, p.paid_on AS paidOn,
                   p.amount_paise - COALESCE((SELECT SUM(a.amount_paise) FROM payment_allocations a WHERE a.payment_id = p.id), 0) AS remainingPaise
            FROM payments p
            WHERE p.student_id = :studentId
        )
        WHERE remainingPaise > 0
        ORDER BY paidOn, paymentId
        """,
    )
    suspend fun creditsForStudent(studentId: Long): List<PaymentCredit>

    @Query("SELECT DISTINCT student_id FROM payments")
    suspend fun studentIdsWithPayments(): List<Long>

    @Query(
        """
        SELECT COALESCE(SUM(p.amount_paise), 0)
             - COALESCE((SELECT SUM(a.amount_paise) FROM payment_allocations a JOIN payments p2 ON p2.id = a.payment_id WHERE p2.student_id = :studentId), 0)
        FROM payments p WHERE p.student_id = :studentId
        """,
    )
    fun observeAdvanceCredit(studentId: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount_paise), 0) FROM payments WHERE paid_on BETWEEN :from AND :to")
    fun observeCollectedBetween(from: LocalDate, to: LocalDate): Flow<Long>
}
