package com.softandtech.tuitionmanager.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.YearMonth

/**
 * A student's membership of a batch. A student may be in several batches, and may
 * leave and rejoin the same batch (a new row each time).
 *
 * @property feeOverridePaise per-student fee (discount/sibling rate); null = batch fee
 * @property billFrom first month to bill — the join month for new students, or the current
 *   month when entering an existing student so years of old dues aren't created
 */
@Entity(
    tableName = "enrollments",
    foreignKeys = [
        ForeignKey(entity = StudentEntity::class, parentColumns = ["id"], childColumns = ["student_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = BatchEntity::class, parentColumns = ["id"], childColumns = ["batch_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("student_id"), Index("batch_id")],
)
data class EnrollmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "student_id") val studentId: Long,
    @ColumnInfo(name = "batch_id") val batchId: Long,
    @ColumnInfo(name = "fee_override_paise") val feeOverridePaise: Long? = null,
    @ColumnInfo(name = "joined_on") val joinedOn: LocalDate,
    @ColumnInfo(name = "left_on") val leftOn: LocalDate? = null,
    @ColumnInfo(name = "bill_from") val billFrom: YearMonth = YearMonth.from(joinedOn),
)
