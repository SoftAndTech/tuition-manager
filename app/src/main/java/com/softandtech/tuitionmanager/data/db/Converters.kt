package com.softandtech.tuitionmanager.data.db

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.YearMonth

/**
 * LocalDate is stored as epoch day and YearMonth as "yyyy-MM" text,
 * so both sort correctly in SQL and carry no timezone.
 */
class Converters {
    @TypeConverter fun localDateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()
    @TypeConverter fun epochDayToLocalDate(day: Long?): LocalDate? = day?.let(LocalDate::ofEpochDay)

    @TypeConverter fun yearMonthToText(month: YearMonth?): String? = month?.toString()
    @TypeConverter fun textToYearMonth(text: String?): YearMonth? = text?.let(YearMonth::parse)
}
