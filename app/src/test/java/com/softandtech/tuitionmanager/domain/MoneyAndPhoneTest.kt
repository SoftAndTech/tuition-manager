package com.softandtech.tuitionmanager.domain

import com.softandtech.tuitionmanager.ui.common.Formatters
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyAndPhoneTest {

    @Test
    fun formatUsesIndianGrouping() {
        assertEquals("₹0", Money.format(0))
        assertEquals("₹999", Money.format(99_900))
        assertEquals("₹1,000", Money.format(100_000))
        assertEquals("₹1,00,000", Money.format(10_000_000))
        assertEquals("₹12,34,567", Money.format(123_456_700))
        assertEquals("₹483.50", Money.format(48_350))
        assertEquals("₹0.05", Money.format(5))
        assertEquals("-₹500", Money.format(-50_000))
    }

    @Test
    fun parseAcceptsCommonInput() {
        assertEquals(100_000L, Money.parse("1000"))
        assertEquals(100_000L, Money.parse(" ₹1,000 "))
        assertEquals(99_950L, Money.parse("999.5"))
        assertEquals(99_955L, Money.parse("999.55"))
        assertEquals(50L, Money.parse(".5"))
        assertEquals(0L, Money.parse("0"))
    }

    @Test
    fun parseRejectsInvalidInput() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("."))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("-100"))
        assertNull(Money.parse("10.555"))
        assertNull(Money.parse("1.2.3"))
        assertNull(Money.parse("99999999999"))
    }

    @Test
    fun toInputRoundTrips() {
        assertEquals("1000", Money.toInput(100_000))
        assertEquals("999.5", Money.toInput(99_950))
        assertEquals("999.55", Money.toInput(99_955))
        assertEquals(99_950L, Money.parse(Money.toInput(99_950)))
    }

    @Test
    fun phoneValidation() {
        assertTrue(Phone.isValid("9876543210"))
        assertTrue(Phone.isValid("+91 98765 43210"))
        assertTrue(Phone.isValid("098765-43210"))
        assertTrue(Phone.isValid("91 98765 43210"))
        assertTrue(Phone.isValid("+1 (415) 555-0100"))
        assertFalse(Phone.isValid("9876543210800")) // 13 digits without + — a typo, not a number
        assertFalse(Phone.isValid("19876543210")) // 11 digits must start with 0
        assertFalse(Phone.isValid("98765+43210"))
        assertFalse(Phone.isValid("12345"))
        assertFalse(Phone.isValid("98765abc10"))
        assertFalse(Phone.isValid(""))
        assertEquals("+919876543210", Phone.normalize("+91 98765-43210"))
        assertNull(Phone.normalize("   "))
    }

    @Test
    fun datesFollowAppLanguage() {
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("en-IN"))
            assertEquals("5 Sep 2026", Formatters.date(LocalDate.of(2026, 9, 5))) // not "Sept"
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("hi-IN"))
            val hindi = Formatters.date(LocalDate.of(2026, 9, 5))
            assertTrue(hindi, hindi.any { it in 'ऀ'..'ॿ' }) // Devanagari month name
            assertEquals("रोज़", Formatters.days(0b1111111))
        } finally {
            java.util.Locale.setDefault(original)
        }
    }

    @Test
    fun formatters() {
        assertEquals("21 Oct 2026", Formatters.date(LocalDate.of(2026, 10, 21)))
        assertEquals("Oct 2026", Formatters.month(YearMonth.of(2026, 10)))
        assertEquals("5:30 PM", Formatters.time(17 * 60 + 30))
        assertEquals("12:00 AM", Formatters.time(0))
        assertEquals("5:00 PM – 6:30 PM", Formatters.timeRange(1020, 1110))
        assertNull(Formatters.timeRange(null, 1110))
        assertEquals("Mon, Wed, Fri", Formatters.days(WeekDays.toMask(listOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))))
        assertEquals("Daily", Formatters.days(0b1111111))
        assertNull(Formatters.days(0))
        val date = LocalDate.of(2026, 2, 28)
        assertEquals(date, Formatters.fromPickerMillis(Formatters.toPickerMillis(date)))
    }
}
