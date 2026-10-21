package com.softandtech.tuitionmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.softandtech.tuitionmanager.data.db.entity.FeeDueEntity
import com.softandtech.tuitionmanager.data.db.model.ExistingPeriod
import com.softandtech.tuitionmanager.data.db.model.FeeDueWithPaid
import com.softandtech.tuitionmanager.data.db.model.StudentOverdue
import com.softandtech.tuitionmanager.domain.OutstandingDue
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

@Dao
interface FeeDueDao {

    @Insert
    suspend fun insert(due: FeeDueEntity): Long

    /** IGNORE keeps generation idempotent if two runs race on the (enrollment, period) unique index. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoringDuplicates(dues: List<FeeDueEntity>): List<Long>

    @Query("SELECT enrollment_id AS enrollmentId, period FROM fee_dues WHERE enrollment_id IS NOT NULL AND period IS NOT NULL")
    suspend fun existingPeriods(): List<ExistingPeriod>

    @Query("SELECT student_id FROM fee_dues WHERE id = :dueId")
    suspend fun studentIdOf(dueId: Long): Long?

    @Query("UPDATE fee_dues SET waived = :waived WHERE id = :dueId")
    suspend fun setWaived(dueId: Long, waived: Boolean)

    @Query(
        """
        SELECT d.*, COALESCE((SELECT SUM(a.amount_paise) FROM payment_allocations a WHERE a.fee_due_id = d.id), 0) AS paid_paise
        FROM fee_dues d
        WHERE d.student_id = :studentId
        ORDER BY d.due_date DESC, d.id DESC
        """,
    )
    fun observeForStudent(studentId: Long): Flow<List<FeeDueWithPaid>>

    /** Unpaid, non-waived dues of one student, oldest first — the order payments are applied in. */
    @Query(
        """
        SELECT dueId, outstandingPaise FROM (
            SELECT d.id AS dueId, d.due_date AS dueDate,
                   d.amount_paise - COALESCE((SELECT SUM(a.amount_paise) FROM payment_allocations a WHERE a.fee_due_id = d.id), 0) AS outstandingPaise
            FROM fee_dues d
            WHERE d.student_id = :studentId AND d.waived = 0
        )
        WHERE outstandingPaise > 0
        ORDER BY dueDate, dueId
        """,
    )
    suspend fun outstandingForStudent(studentId: Long): List<OutstandingDue>

    @Query(
        """
        SELECT COALESCE(SUM(d.amount_paise - COALESCE((SELECT SUM(a.amount_paise) FROM payment_allocations a WHERE a.fee_due_id = d.id), 0)), 0)
        FROM fee_dues d
        WHERE d.student_id = :studentId AND d.waived = 0
        """,
    )
    fun observeTotalOutstandingForStudent(studentId: Long): Flow<Long>

    /** Students with money overdue (due date before [today]), longest overdue first. */
    @Query(
        """
        SELECT s.id AS studentId, s.name AS name, s.parent_phone AS parentPhone, s.phone AS phone,
               SUM(o.outstanding) AS outstandingPaise, MIN(o.due_date) AS oldestDueDate
        FROM (
            SELECT d.student_id, d.due_date,
                   d.amount_paise - COALESCE((SELECT SUM(a.amount_paise) FROM payment_allocations a WHERE a.fee_due_id = d.id), 0) AS outstanding
            FROM fee_dues d
            WHERE d.waived = 0 AND d.due_date < :today
        ) o
        JOIN students s ON s.id = o.student_id
        WHERE o.outstanding > 0 AND s.active = 1
        GROUP BY s.id
        ORDER BY oldestDueDate, s.name COLLATE NOCASE
        """,
    )
    fun observeOverdue(today: LocalDate): Flow<List<StudentOverdue>>

    /** Total billed (non-waived) for a month's monthly dues. */
    @Query("SELECT COALESCE(SUM(amount_paise), 0) FROM fee_dues WHERE period = :period AND waived = 0")
    fun observeBilledForPeriod(period: YearMonth): Flow<Long>
}
