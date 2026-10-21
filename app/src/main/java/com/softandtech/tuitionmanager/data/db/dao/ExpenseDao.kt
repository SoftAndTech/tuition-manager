package com.softandtech.tuitionmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.softandtech.tuitionmanager.data.db.entity.ExpenseEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE date BETWEEN :from AND :to ORDER BY date DESC, id DESC")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<ExpenseEntity>>

    @Query("SELECT COALESCE(SUM(amount_paise), 0) FROM expenses WHERE date BETWEEN :from AND :to")
    fun observeTotalBetween(from: LocalDate, to: LocalDate): Flow<Long>
}
