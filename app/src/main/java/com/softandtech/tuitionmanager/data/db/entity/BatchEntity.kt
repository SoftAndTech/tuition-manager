package com.softandtech.tuitionmanager.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A class group, e.g. "Class 10 Maths — Mon/Wed/Fri 5 PM".
 *
 * @property daysMask bit 0 = Monday … bit 6 = Sunday (see [com.softandtech.tuitionmanager.domain.WeekDays])
 * @property startMinute minutes after midnight, e.g. 17:30 = 1050
 * @property feeDueDay day of month fees fall due; clamped to month length (31 → 28 in Feb)
 */
@Entity(tableName = "batches")
data class BatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val subject: String? = null,
    @ColumnInfo(name = "days_mask") val daysMask: Int = 0,
    @ColumnInfo(name = "start_minute") val startMinute: Int? = null,
    @ColumnInfo(name = "end_minute") val endMinute: Int? = null,
    @ColumnInfo(name = "monthly_fee_paise") val monthlyFeePaise: Long,
    @ColumnInfo(name = "fee_due_day") val feeDueDay: Int = 5,
    val active: Boolean = true,
)
