package com.softandtech.tuitionmanager.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.YearMonth

/**
 * An amount a student owes. Monthly dues are generated automatically and have an
 * [enrollmentId] + [period]; one-time dues (admission fee, books) have both null.
 *
 * Paid/partial status is not stored — it is derived from [PaymentAllocationEntity] rows,
 * so it can never drift out of sync with the payments.
 */
@Entity(
    tableName = "fee_dues",
    foreignKeys = [
        ForeignKey(entity = StudentEntity::class, parentColumns = ["id"], childColumns = ["student_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EnrollmentEntity::class, parentColumns = ["id"], childColumns = ["enrollment_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("student_id"),
        Index("due_date"),
        // One monthly due per enrollment per month; SQLite allows many NULLs, so one-time dues are unaffected
        Index(value = ["enrollment_id", "period"], unique = true),
    ],
)
data class FeeDueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "student_id") val studentId: Long,
    @ColumnInfo(name = "enrollment_id") val enrollmentId: Long? = null,
    val period: YearMonth? = null,
    val title: String,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "due_date") val dueDate: LocalDate,
    val waived: Boolean = false,
)
