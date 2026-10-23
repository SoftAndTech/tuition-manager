package com.softandtech.tuitionmanager.ui.common

import com.softandtech.tuitionmanager.domain.WeekDays
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Display formats used across screens. Kept pure so they are unit-testable. */
object Formatters {
    private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val monthFormat = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun date(date: LocalDate): String = date.format(dateFormat)

    fun month(month: YearMonth): String = month.format(monthFormat)

    /** Minutes after midnight → "5:30 PM". */
    fun time(minutes: Int): String = LocalTime.of(minutes / 60, minutes % 60).format(timeFormat)

    /** "5:00 PM – 6:30 PM", "5:00 PM", or null when no start time. */
    fun timeRange(start: Int?, end: Int?): String? = when {
        start == null -> null
        end == null -> time(start)
        else -> "${time(start)} – ${time(end)}"
    }

    /** "Mon, Wed, Fri"; "Daily" for all seven; null for none. */
    fun days(mask: Int): String? {
        val days = WeekDays.fromMask(mask)
        return when (days.size) {
            0 -> null
            7 -> "Daily"
            else -> days.joinToString(", ") { shortDay(it) }
        }
    }

    fun shortDay(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    /** Material date pickers work in UTC milliseconds. */
    fun toPickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromPickerMillis(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
