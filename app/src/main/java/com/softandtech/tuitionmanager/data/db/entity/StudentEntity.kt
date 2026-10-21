package com.softandtech.tuitionmanager.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Dates are stored as epoch days (LocalDate.toEpochDay) to avoid timezone issues. */
@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
    @ColumnInfo(name = "parent_name") val parentName: String? = null,
    @ColumnInfo(name = "parent_phone") val parentPhone: String? = null,
    @ColumnInfo(name = "class_name") val className: String? = null,
    @ColumnInfo(name = "join_date") val joinDate: Long,
    val notes: String? = null,
    val active: Boolean = true,
)
