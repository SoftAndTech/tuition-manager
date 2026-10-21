package com.softandtech.tuitionmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.softandtech.tuitionmanager.data.db.entity.AttendanceEntity
import com.softandtech.tuitionmanager.data.db.model.StatusCount
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    /** REPLACE uses the (batch, student, date) unique index, so re-marking a day overwrites it. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(marks: List<AttendanceEntity>)

    @Query("SELECT * FROM attendance WHERE batch_id = :batchId AND date = :date")
    fun observeForBatchOnDate(batchId: Long, date: LocalDate): Flow<List<AttendanceEntity>>

    @Query(
        """
        SELECT status, COUNT(*) AS count FROM attendance
        WHERE student_id = :studentId AND date BETWEEN :from AND :to
        GROUP BY status
        """,
    )
    fun observeStatusCounts(studentId: Long, from: LocalDate, to: LocalDate): Flow<List<StatusCount>>
}
