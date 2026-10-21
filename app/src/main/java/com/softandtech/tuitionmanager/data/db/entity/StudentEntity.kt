package com.softandtech.tuitionmanager.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Students are never hard-deleted in normal use; set [active] = false to keep fee history. */
@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
    @ColumnInfo(name = "parent_name") val parentName: String? = null,
    @ColumnInfo(name = "parent_phone") val parentPhone: String? = null,
    @ColumnInfo(name = "class_name") val className: String? = null,
    @ColumnInfo(name = "join_date") val joinDate: LocalDate,
    val notes: String? = null,
    val active: Boolean = true,
)
