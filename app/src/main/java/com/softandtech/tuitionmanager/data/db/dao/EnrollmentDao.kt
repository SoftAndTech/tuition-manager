package com.softandtech.tuitionmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.softandtech.tuitionmanager.data.db.entity.EnrollmentEntity
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import com.softandtech.tuitionmanager.domain.BillableEnrollment
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface EnrollmentDao {

    @Insert
    suspend fun insert(enrollment: EnrollmentEntity): Long

    @Update
    suspend fun update(enrollment: EnrollmentEntity)

    @Query("SELECT * FROM enrollments WHERE id = :id")
    suspend fun getById(id: Long): EnrollmentEntity?

    @Query("SELECT * FROM enrollments WHERE student_id = :studentId ORDER BY joined_on DESC")
    fun observeForStudent(studentId: Long): Flow<List<EnrollmentEntity>>

    @Query("UPDATE enrollments SET left_on = :leftOn WHERE student_id = :studentId AND left_on IS NULL")
    suspend fun closeOpenEnrollments(studentId: Long, leftOn: LocalDate)

    /** Active students enrolled in [batchId] on [date] — the attendance register for that day. */
    @Query(
        """
        SELECT DISTINCT s.* FROM students s
        JOIN enrollments e ON e.student_id = s.id
        WHERE e.batch_id = :batchId AND s.active = 1
          AND e.joined_on <= :date AND (e.left_on IS NULL OR e.left_on >= :date)
        ORDER BY s.name COLLATE NOCASE
        """,
    )
    fun observeStudentsInBatch(batchId: Long, date: LocalDate): Flow<List<StudentEntity>>

    /** Enrollments of active students in active batches, with the batch fee details needed for billing. */
    @Query(
        """
        SELECT e.id AS enrollmentId, e.student_id AS studentId, b.name AS batchName,
               b.monthly_fee_paise AS monthlyFeePaise, e.fee_override_paise AS feeOverridePaise,
               b.fee_due_day AS feeDueDay, e.joined_on AS joinedOn, e.left_on AS leftOn,
               e.bill_from AS billFrom
        FROM enrollments e
        JOIN batches b ON b.id = e.batch_id
        JOIN students s ON s.id = e.student_id
        WHERE s.active = 1 AND b.active = 1
        """,
    )
    suspend fun getBillable(): List<BillableEnrollment>
}
