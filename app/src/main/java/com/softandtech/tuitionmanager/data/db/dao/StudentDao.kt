package com.softandtech.tuitionmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {

    @Insert
    suspend fun insert(student: StudentEntity): Long

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun getById(id: Long): StudentEntity?

    @Query("SELECT COUNT(*) FROM students WHERE active = 1")
    fun observeActiveCount(): Flow<Int>
}
