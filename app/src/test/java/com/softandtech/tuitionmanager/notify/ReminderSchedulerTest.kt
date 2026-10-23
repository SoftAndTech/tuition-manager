package com.softandtech.tuitionmanager.notify

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderSchedulerTest {

    private val nine = LocalTime.of(9, 0)

    @Test
    fun beforeNine_runsLaterToday() {
        assertEquals(Duration.ofHours(2), ReminderScheduler.delayUntil(LocalDateTime.of(2026, 10, 23, 7, 0), nine))
    }

    @Test
    fun afterNine_runsTomorrow() {
        assertEquals(Duration.ofHours(21), ReminderScheduler.delayUntil(LocalDateTime.of(2026, 10, 23, 12, 0), nine))
    }

    @Test
    fun exactlyNine_waitsForTomorrow() {
        assertEquals(Duration.ofDays(1), ReminderScheduler.delayUntil(LocalDateTime.of(2026, 10, 23, 9, 0), nine))
    }

    @Test
    fun acrossMonthEnd() {
        assertEquals(Duration.ofMinutes(30), ReminderScheduler.delayUntil(LocalDateTime.of(2026, 10, 31, 23, 30).plusHours(9), nine))
    }
}
