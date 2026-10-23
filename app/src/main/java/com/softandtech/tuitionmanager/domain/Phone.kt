package com.softandtech.tuitionmanager.domain

object Phone {
    /** Strips spaces, dashes and brackets; keeps a leading '+'. Blank input becomes null. */
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val plus = if (trimmed.startsWith("+")) "+" else ""
        return plus + trimmed.filter(Char::isDigit)
    }

    /**
     * Accepts Indian numbers as people usually type them — 98765 43210, 098765 43210, 91 98765 43210 —
     * and any international number written with '+' (8–15 digits, the E.164 limit).
     */
    fun isValid(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || !trimmed.all { it.isDigit() || it in " -()+" }) return false
        if (trimmed.lastIndexOf('+') > 0) return false
        val digits = trimmed.filter(Char::isDigit)
        return if (trimmed.startsWith("+")) {
            digits.length in 8..15
        } else {
            digits.length == 10 ||
                (digits.length == 11 && digits.startsWith("0")) ||
                (digits.length == 12 && digits.startsWith("91"))
        }
    }
}
