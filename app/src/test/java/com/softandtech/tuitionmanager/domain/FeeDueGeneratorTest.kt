package com.softandtech.tuitionmanager.domain

import com.softandtech.tuitionmanager.domain.model.JoinMonthPolicy
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeeDueGeneratorTest {

    private val today = LocalDate.of(2026, 10, 21)

    private fun enrollment(
        joinedOn: LocalDate,
        fee: Long = 100_000, // ₹1000
        override: Long? = null,
        dueDay: Int = 5,
        leftOn: LocalDate? = null,
        billFrom: YearMonth = YearMonth.from(joinedOn),
    ) = BillableEnrollment(
        enrollmentId = 1,
        studentId = 10,
        batchName = "Class 10 Maths",
        monthlyFeePaise = fee,
        feeOverridePaise = override,
        feeDueDay = dueDay,
        joinedOn = joinedOn,
        leftOn = leftOn,
        billFrom = billFrom,
    )

    private fun generate(
        e: BillableEnrollment,
        policy: JoinMonthPolicy = JoinMonthPolicy.FULL,
        existing: Set<Pair<Long, YearMonth>> = emptySet(),
        on: LocalDate = today,
    ) = FeeDueGenerator.generate(listOf(e), existing, on, policy)

    @Test
    fun joinedOnFirst_billsCurrentMonthOnDueDay() {
        val dues = generate(enrollment(LocalDate.of(2026, 10, 1)))
        assertEquals(1, dues.size)
        with(dues.single()) {
            assertEquals(YearMonth.of(2026, 10), period)
            assertEquals(100_000, amountPaise)
            assertEquals(LocalDate.of(2026, 10, 5), dueDate)
            assertEquals("Class 10 Maths", title)
            assertEquals(10, studentId)
        }
    }

    @Test
    fun backfillsEveryMonthSinceJoining() {
        val dues = generate(enrollment(LocalDate.of(2026, 8, 1)))
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)), dues.map { it.period })
    }

    @Test
    fun crossesYearBoundary() {
        val dues = generate(enrollment(LocalDate.of(2026, 11, 1)), on = LocalDate.of(2027, 1, 10))
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 12), YearMonth.of(2027, 1)), dues.map { it.period })
    }

    @Test
    fun skipsMonthsAlreadyBilled_soReRunningIsSafe() {
        val e = enrollment(LocalDate.of(2026, 8, 1))
        val dues = generate(e, existing = setOf(1L to YearMonth.of(2026, 9)))
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 10)), dues.map { it.period })

        val all = generate(e).map { 1L to it.period }.toSet()
        assertTrue(generate(e, existing = all).isEmpty())
    }

    @Test
    fun neverBillsFutureMonths() {
        assertTrue(generate(enrollment(LocalDate.of(2026, 11, 3))).isEmpty())
    }

    @Test
    fun midMonthJoin_fullPolicy_chargesFullFeeDueOnJoiningDay() {
        val due = generate(enrollment(LocalDate.of(2026, 10, 15))).single()
        assertEquals(100_000, due.amountPaise)
        // Batch due day (5th) is before joining, so it falls due on the joining date instead
        assertEquals(LocalDate.of(2026, 10, 15), due.dueDate)
    }

    @Test
    fun midMonthJoin_proratedPolicy_chargesRemainingDaysRoundedToRupee() {
        // Oct has 31 days; joining on the 17th = 15 days. ₹1000 × 15/31 = ₹483.87 → ₹484
        val dues = generate(enrollment(LocalDate.of(2026, 10, 17)), JoinMonthPolicy.PRORATED, on = LocalDate.of(2026, 11, 2))
        assertEquals(48_400, dues[0].amountPaise)
        assertEquals(100_000, dues[1].amountPaise) // following months are full
    }

    @Test
    fun prorate_joinedOnLastDay_chargesOneDay() {
        // ₹1000 / 30 days = ₹33.33 → ₹33
        assertEquals(3_300, FeeDueGenerator.prorate(100_000, LocalDate.of(2026, 9, 30)))
    }

    @Test
    fun prorate_roundsHalfUp() {
        // ₹15 × 15/30 = ₹7.50 → ₹8
        assertEquals(800, FeeDueGenerator.prorate(1_500, LocalDate.of(2026, 9, 16)))
    }

    @Test
    fun proratedPolicy_joinedOnFirst_chargesFullMonth() {
        val due = generate(enrollment(LocalDate.of(2026, 10, 1)), JoinMonthPolicy.PRORATED).single()
        assertEquals(100_000, due.amountPaise)
    }

    @Test
    fun midMonthJoin_nextMonthPolicy_skipsJoiningMonth() {
        val e = enrollment(LocalDate.of(2026, 10, 15))
        assertTrue(generate(e, JoinMonthPolicy.NEXT_MONTH).isEmpty())
        val nov = generate(e, JoinMonthPolicy.NEXT_MONTH, on = LocalDate.of(2026, 11, 2))
        assertEquals(listOf(YearMonth.of(2026, 11)), nov.map { it.period })
        assertEquals(LocalDate.of(2026, 11, 5), nov.single().dueDate)
    }

    @Test
    fun nextMonthPolicy_joinedOnFirst_chargesJoiningMonth() {
        val dues = generate(enrollment(LocalDate.of(2026, 10, 1)), JoinMonthPolicy.NEXT_MONTH)
        assertEquals(listOf(YearMonth.of(2026, 10)), dues.map { it.period })
    }

    @Test
    fun feeOverrideReplacesBatchFee() {
        val due = generate(enrollment(LocalDate.of(2026, 10, 1), override = 80_000)).single()
        assertEquals(80_000, due.amountPaise)
    }

    @Test
    fun zeroFee_createsNoDues() {
        assertTrue(generate(enrollment(LocalDate.of(2026, 10, 1), fee = 0)).isEmpty())
        assertTrue(generate(enrollment(LocalDate.of(2026, 10, 1), override = 0)).isEmpty())
    }

    @Test
    fun stopsAfterLeavingMonth_leavingMonthStillBilled() {
        val dues = generate(enrollment(LocalDate.of(2026, 8, 1), leftOn = LocalDate.of(2026, 9, 10)))
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9)), dues.map { it.period })
    }

    @Test
    fun dueDay31_clampsToShortMonths() {
        val e = enrollment(LocalDate.of(2027, 1, 1), dueDay = 31)
        val dues = generate(e, on = LocalDate.of(2027, 3, 1))
        assertEquals(LocalDate.of(2027, 1, 31), dues[0].dueDate)
        assertEquals(LocalDate.of(2027, 2, 28), dues[1].dueDate)
        assertEquals(LocalDate.of(2027, 3, 31), dues[2].dueDate)
    }

    @Test
    fun dueDay31_leapYearFebruary() {
        val e = enrollment(LocalDate.of(2028, 2, 1), dueDay = 31)
        assertEquals(LocalDate.of(2028, 2, 29), generate(e, on = LocalDate.of(2028, 2, 10)).single().dueDate)
    }

    @Test
    fun billFromLaterThanJoining_skipsOldMonthsAndProration() {
        val e = enrollment(LocalDate.of(2025, 1, 10), billFrom = YearMonth.of(2026, 10))
        val due = generate(e, JoinMonthPolicy.PRORATED).single()
        assertEquals(YearMonth.of(2026, 10), due.period)
        assertEquals(100_000, due.amountPaise)
        assertEquals(LocalDate.of(2026, 10, 5), due.dueDate)
    }

    @Test
    fun multipleEnrollmentsAreBilledIndependently() {
        val a = enrollment(LocalDate.of(2026, 10, 1))
        val b = enrollment(LocalDate.of(2026, 9, 1), fee = 50_000).copy(enrollmentId = 2, batchName = "Science")
        val dues = FeeDueGenerator.generate(listOf(a, b), setOf(1L to YearMonth.of(2026, 10)), today, JoinMonthPolicy.FULL)
        assertEquals(listOf(2L to YearMonth.of(2026, 9), 2L to YearMonth.of(2026, 10)), dues.map { it.enrollmentId to it.period })
    }
}
