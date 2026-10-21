package com.softandtech.tuitionmanager.domain

import java.time.DayOfWeek

/** Encodes the days a batch meets as a bitmask: bit 0 = Monday … bit 6 = Sunday. */
object WeekDays {
    fun toMask(days: Collection<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or bit(day) }

    fun fromMask(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filterTo(LinkedHashSet()) { mask and bit(it) != 0 }

    fun contains(mask: Int, day: DayOfWeek): Boolean = mask and bit(day) != 0

    private fun bit(day: DayOfWeek) = 1 shl (day.value - 1)
}
