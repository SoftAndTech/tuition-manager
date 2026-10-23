package com.softandtech.tuitionmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.softandtech.tuitionmanager.data.db.entity.BatchEntity
import com.softandtech.tuitionmanager.data.db.model.BatchWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface BatchDao {

    @Insert
    suspend fun insert(batch: BatchEntity): Long

    @Update
    suspend fun update(batch: BatchEntity)

    @Query("SELECT * FROM batches WHERE id = :id")
    suspend fun getById(id: Long): BatchEntity?

    @Query("SELECT * FROM batches WHERE active = 1 ORDER BY start_minute IS NULL, start_minute, name COLLATE NOCASE")
    fun observeActive(): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batches WHERE id = :id")
    fun observeById(id: Long): Flow<BatchEntity?>

    /** Active batches with how many active students are currently enrolled. */
    @Query(
        """
        SELECT b.*, (SELECT COUNT(DISTINCT e.student_id) FROM enrollments e JOIN students s ON s.id = e.student_id
                     WHERE e.batch_id = b.id AND s.active = 1 AND e.left_on IS NULL) AS student_count
        FROM batches b
        WHERE b.active = 1
        ORDER BY b.start_minute IS NULL, b.start_minute, b.name COLLATE NOCASE
        """,
    )
    fun observeActiveWithCount(): Flow<List<BatchWithCount>>

    @Query("SELECT COUNT(*) FROM batches WHERE active = 1")
    suspend fun activeCount(): Int

    @Query("UPDATE batches SET active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)
}
