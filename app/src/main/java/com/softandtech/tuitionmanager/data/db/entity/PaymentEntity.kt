package com.softandtech.tuitionmanager.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.softandtech.tuitionmanager.domain.model.PaymentMode
import java.time.LocalDate

/**
 * Money received from a student — one row per receipt. How it was split across dues lives
 * in [PaymentAllocationEntity]; any unallocated remainder is advance credit for future dues.
 *
 * @property receiptSeq running receipt number; shown with the tutor's prefix, e.g. "TM-0042"
 */
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(entity = StudentEntity::class, parentColumns = ["id"], childColumns = ["student_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("student_id"), Index("paid_on"), Index(value = ["receipt_seq"], unique = true)],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "student_id") val studentId: Long,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    val mode: PaymentMode,
    @ColumnInfo(name = "paid_on") val paidOn: LocalDate,
    @ColumnInfo(name = "receipt_seq") val receiptSeq: Int,
    val note: String? = null,
)
