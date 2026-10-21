package com.softandtech.tuitionmanager.domain

import com.softandtech.tuitionmanager.domain.model.JoinMonthPolicy
import java.time.LocalDate
import java.time.YearMonth

/** Everything needed to bill one enrollment. Amounts are in paise. */
data class BillableEnrollment(
    val enrollmentId: Long,
    val studentId: Long,
    val batchName: String,
    val monthlyFeePaise: Long,
    val feeOverridePaise: Long?,
    val feeDueDay: Int,
    val joinedOn: LocalDate,
    val leftOn: LocalDate?,
    /** First month to bill. Later than the join month when importing existing students. */
    val billFrom: YearMonth,
)

data class NewMonthlyDue(
    val studentId: Long,
    val enrollmentId: Long,
    val period: YearMonth,
    val title: String,
    val amountPaise: Long,
    val dueDate: LocalDate,
)

/**
 * Works out which monthly dues are missing, up to and including the current month.
 *
 * Rules:
 * - A month is billed if the student was enrolled on any day of it, from [BillableEnrollment.billFrom]
 *   until the month they left. Future months are never billed.
 * - The joining month follows [JoinMonthPolicy] (only when billing starts in the joining month and the
 *   student joined after the 1st).
 * - Due date is the batch's due day, clamped to the month's length, and never before the joining date.
 * - Months already in [existing] are skipped, so running this repeatedly is safe.
 */
object FeeDueGenerator {

    fun generate(
        enrollments: List<BillableEnrollment>,
        existing: Set<Pair<Long, YearMonth>>,
        today: LocalDate,
        policy: JoinMonthPolicy,
    ): List<NewMonthlyDue> = enrollments.flatMap { generateFor(it, existing, today, policy) }

    private fun generateFor(
        e: BillableEnrollment,
        existing: Set<Pair<Long, YearMonth>>,
        today: LocalDate,
        policy: JoinMonthPolicy,
    ): List<NewMonthlyDue> {
        val fee = e.feeOverridePaise ?: e.monthlyFeePaise
        if (fee <= 0) return emptyList()

        val joinMonth = YearMonth.from(e.joinedOn)
        val midMonthJoin = e.joinedOn.dayOfMonth > 1 && e.billFrom == joinMonth

        var first = maxOf(e.billFrom, joinMonth)
        if (midMonthJoin && policy == JoinMonthPolicy.NEXT_MONTH) first = first.plusMonths(1)

        var last = YearMonth.from(today)
        e.leftOn?.let { last = minOf(last, YearMonth.from(it)) }

        val dues = mutableListOf<NewMonthlyDue>()
        var month = first
        while (month <= last) {
            if ((e.enrollmentId to month) !in existing) {
                val prorate = midMonthJoin && month == joinMonth && policy == JoinMonthPolicy.PRORATED
                dues += NewMonthlyDue(
                    studentId = e.studentId,
                    enrollmentId = e.enrollmentId,
                    period = month,
                    title = e.batchName,
                    amountPaise = if (prorate) prorate(fee, e.joinedOn) else fee,
                    dueDate = dueDate(month, e.feeDueDay, e.joinedOn),
                )
            }
            month = month.plusMonths(1)
        }
        return dues
    }

    /** Fee for the days from [joinedOn] to month end (inclusive), rounded half-up to whole rupees. */
    internal fun prorate(feePaise: Long, joinedOn: LocalDate): Long {
        val daysInMonth = joinedOn.lengthOfMonth().toLong()
        val daysCharged = daysInMonth - joinedOn.dayOfMonth + 1
        val rupees = (feePaise * daysCharged + 50 * daysInMonth) / (100 * daysInMonth)
        return rupees * 100
    }

    internal fun dueDate(month: YearMonth, feeDueDay: Int, joinedOn: LocalDate): LocalDate {
        val date = month.atDay(feeDueDay.coerceIn(1, month.lengthOfMonth()))
        return maxOf(date, joinedOn)
    }
}
