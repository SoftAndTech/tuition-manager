package com.softandtech.tuitionmanager.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Tutor's own expenses (rent, books, travel) — a Pro feature. */
@Entity(tableName = "expenses", indices = [Index("date")])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    val date: LocalDate,
    val note: String? = null,
)
