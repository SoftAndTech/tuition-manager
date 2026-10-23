package com.softandtech.tuitionmanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsAppAndReceiptsTest {

    @Test
    fun indianNumbersGetCountryCode() {
        assertEquals("919876543210", WhatsApp.toWaNumber("9876543210"))
        assertEquals("919876543210", WhatsApp.toWaNumber("98765 43210"))
        assertEquals("919876543210", WhatsApp.toWaNumber("098765-43210"))
        assertEquals("919876543210", WhatsApp.toWaNumber("91 98765 43210"))
        assertEquals("919876543210", WhatsApp.toWaNumber("+91 98765 43210"))
    }

    @Test
    fun internationalNumbersKeepTheirCode() {
        assertEquals("14155550100", WhatsApp.toWaNumber("+1 (415) 555-0100"))
        assertEquals("971501234567", WhatsApp.toWaNumber("+971 50 123 4567"))
    }

    @Test
    fun invalidOrMissingNumbersGiveNull() {
        assertNull(WhatsApp.toWaNumber(null))
        assertNull(WhatsApp.toWaNumber(""))
        assertNull(WhatsApp.toWaNumber("12345"))
        assertNull(WhatsApp.toWaNumber("9876543210800"))
    }

    @Test
    fun receiptNumbers() {
        assertEquals("R-0001", Receipts.number("R-", 1))
        assertEquals("TM/2026/0042", Receipts.number("TM/2026/", 42))
        assertEquals("R-12345", Receipts.number("R-", 12345))
        assertEquals("Receipt_TM_2026_0042.pdf", Receipts.fileName("TM/2026/", 42))
        assertEquals("Receipt_0007.pdf", Receipts.fileName("", 7))
    }
}
