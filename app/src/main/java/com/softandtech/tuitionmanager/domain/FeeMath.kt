package com.softandtech.tuitionmanager.domain

import com.softandtech.tuitionmanager.domain.model.AttendanceStatus
import com.softandtech.tuitionmanager.domain.model.DueStatus

object FeeMath {
    fun dueStatus(amountPaise: Long, paidPaise: Long, waived: Boolean): DueStatus = when {
        waived -> DueStatus.WAIVED
        paidPaise >= amountPaise -> DueStatus.PAID
        paidPaise > 0 -> DueStatus.PARTIAL
        else -> DueStatus.DUE
    }
}

data class AttendanceSummary(val present: Int, val absent: Int, val leave: Int) {
    /** Leave days are excluded from the percentage. Null when there are no counted classes yet. */
    val percent: Int?
        get() {
            val counted = present + absent
            return if (counted == 0) null else (present * 100 + counted / 2) / counted
        }

    companion object {
        fun from(counts: Map<AttendanceStatus, Int>) = AttendanceSummary(
            present = counts[AttendanceStatus.PRESENT] ?: 0,
            absent = counts[AttendanceStatus.ABSENT] ?: 0,
            leave = counts[AttendanceStatus.LEAVE] ?: 0,
        )
    }
}
