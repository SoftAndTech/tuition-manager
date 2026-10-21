package com.softandtech.tuitionmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.softandtech.tuitionmanager.data.db.entity.BatchEntity
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

    @Query("SELECT COUNT(*) FROM batches WHERE active = 1")
    suspend fun activeCount(): Int

    @Query("UPDATE batches SET active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)
}
