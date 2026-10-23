package com.softandtech.tuitionmanager.domain

import com.softandtech.tuitionmanager.domain.model.AttendanceStatus
import com.softandtech.tuitionmanager.domain.model.DueStatus
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeeMathTest {

    @Test
    fun dueStatus() {
        assertEquals(DueStatus.DUE, FeeMath.dueStatus(1000, 0, waived = false))
        assertEquals(DueStatus.PARTIAL, FeeMath.dueStatus(1000, 400, waived = false))
        assertEquals(DueStatus.PAID, FeeMath.dueStatus(1000, 1000, waived = false))
        assertEquals(DueStatus.WAIVED, FeeMath.dueStatus(1000, 400, waived = true))
    }

    @Test
    fun attendancePercent_excludesLeaveAndRounds() {
        val summary = AttendanceSummary.from(
            mapOf(AttendanceStatus.PRESENT to 2, AttendanceStatus.ABSENT to 1, AttendanceStatus.LEAVE to 5),
        )
        assertEquals(67, summary.percent) // 2 of 3 counted classes
        assertEquals(5, summary.leave)
    }

    @Test
    fun attendancePercent_nullWithoutClasses() {
        assertNull(AttendanceSummary.from(emptyMap()).percent)
        assertNull(AttendanceSummary.from(mapOf(AttendanceStatus.LEAVE to 3)).percent)
    }

    @Test
    fun attendancePercent_allPresent() {
        assertEquals(100, AttendanceSummary.from(mapOf(AttendanceStatus.PRESENT to 12)).percent)
    }

    @Test
    fun weekDaysMaskRoundTrips() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)
        val mask = WeekDays.toMask(days)
        assertEquals(0b1000101, mask)
        assertEquals(days, WeekDays.fromMask(mask))
        assertTrue(WeekDays.contains(mask, DayOfWeek.WEDNESDAY))
        assertFalse(WeekDays.contains(mask, DayOfWeek.TUESDAY))
    }

    @Test
    fun meetingOn_filtersByDay_orShowsAllWhenNoTimetable() {
        val monWed = WeekDays.toMask(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        val sat = WeekDays.toMask(listOf(DayOfWeek.SATURDAY))
        val batches = listOf("Maths" to monWed, "Art" to sat, "Unscheduled" to 0)

        assertEquals(listOf("Maths"), WeekDays.meetingOn(batches, DayOfWeek.MONDAY) { it.second }.map { it.first })
        assertEquals(emptyList<String>(), WeekDays.meetingOn(batches, DayOfWeek.SUNDAY) { it.second }.map { it.first })

        val noTimetable = listOf("A" to 0, "B" to 0)
        assertEquals(listOf("A", "B"), WeekDays.meetingOn(noTimetable, DayOfWeek.SUNDAY) { it.second }.map { it.first })
    }
}
