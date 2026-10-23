package com.softandtech.tuitionmanager.data.db.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.softandtech.tuitionmanager.data.db.entity.BatchEntity
import com.softandtech.tuitionmanager.data.db.entity.EnrollmentEntity
import com.softandtech.tuitionmanager.data.db.entity.FeeDueEntity
import com.softandtech.tuitionmanager.domain.FeeMath
import com.softandtech.tuitionmanager.domain.model.AttendanceStatus
import com.softandtech.tuitionmanager.domain.model.DueStatus
import java.time.LocalDate
import java.time.YearMonth

/** Read-only shapes returned by DAO queries. */

data class FeeDueWithPaid(
    @Embedded val due: FeeDueEntity,
    @ColumnInfo(name = "paid_paise") val paidPaise: Long,
) {
    val status: DueStatus get() = FeeMath.dueStatus(due.amountPaise, paidPaise, due.waived)
    val outstandingPaise: Long get() = if (due.waived) 0 else (due.amountPaise - paidPaise).coerceAtLeast(0)
}

data class ExistingPeriod(val enrollmentId: Long, val period: YearMonth)

data class StudentOverdue(
    val studentId: Long,
    val name: String,
    val parentPhone: String?,
    val phone: String?,
    val outstandingPaise: Long,
    val oldestDueDate: LocalDate,
)

data class StatusCount(val status: AttendanceStatus, val count: Int)

/** One row of the students list. [batchNames] lists current batches, comma-separated. */
data class StudentListItem(
    val id: Long,
    val name: String,
    val className: String?,
    val phone: String?,
    val parentPhone: String?,
    val batchNames: String?,
)

data class BatchWithCount(
    @Embedded val batch: BatchEntity,
    @ColumnInfo(name = "student_count") val studentCount: Int,
)

data class EnrollmentWithBatch(
    @Embedded val enrollment: EnrollmentEntity,
    @ColumnInfo(name = "batch_name") val batchName: String,
    @ColumnInfo(name = "batch_fee_paise") val batchFeePaise: Long,
) {
    val feePaise: Long get() = enrollment.feeOverridePaise ?: batchFeePaise
    val isCurrent: Boolean get() = enrollment.leftOn == null
}
