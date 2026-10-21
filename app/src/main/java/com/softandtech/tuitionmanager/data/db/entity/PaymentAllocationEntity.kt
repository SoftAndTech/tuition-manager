package com.softandtech.tuitionmanager.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** The part of a payment applied to one fee due. */
@Entity(
    tableName = "payment_allocations",
    foreignKeys = [
        ForeignKey(entity = PaymentEntity::class, parentColumns = ["id"], childColumns = ["payment_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = FeeDueEntity::class, parentColumns = ["id"], childColumns = ["fee_due_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("payment_id"), Index("fee_due_id")],
)
data class PaymentAllocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "payment_id") val paymentId: Long,
    @ColumnInfo(name = "fee_due_id") val feeDueId: Long,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
)
