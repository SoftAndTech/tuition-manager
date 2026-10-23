package com.softandtech.tuitionmanager.domain

object WhatsApp {
    /** Numbers typed without '+' are treated as Indian, matching how tutors enter them. */
    private const val DEFAULT_COUNTRY_CODE = "91"

    /**
     * Converts a stored phone number to the digits-only international form wa.me needs,
     * e.g. "098765 43210" → "919876543210". Returns null if it can't be a valid number.
     */
    fun toWaNumber(phone: String?): String? {
        if (phone.isNullOrBlank() || !Phone.isValid(phone)) return null
        val digits = phone.filter(Char::isDigit)
        return when {
            phone.trim().startsWith("+") -> digits
            digits.length == 10 -> DEFAULT_COUNTRY_CODE + digits
            digits.length == 11 -> DEFAULT_COUNTRY_CODE + digits.drop(1) // leading trunk 0
            else -> digits // 12 digits already starting with 91
        }
    }
}
