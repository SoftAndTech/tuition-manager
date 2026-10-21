package com.softandtech.tuitionmanager.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.softandtech.tuitionmanager.domain.model.AttendanceStatus
import java.time.LocalDate

@Entity(
    tableName = "attendance",
    foreignKeys = [
        ForeignKey(entity = BatchEntity::class, parentColumns = ["id"], childColumns = ["batch_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = StudentEntity::class, parentColumns = ["id"], childColumns = ["student_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("student_id"),
        // Re-marking a day replaces the earlier mark instead of duplicating it
        Index(value = ["batch_id", "student_id", "date"], unique = true),
    ],
)
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "batch_id") val batchId: Long,
    @ColumnInfo(name = "student_id") val studentId: Long,
    val date: LocalDate,
    val status: AttendanceStatus,
)
