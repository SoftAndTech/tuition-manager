package com.softandtech.tuitionmanager.domain

/** Rupee formatting and parsing. Amounts are always paise (₹1 = 100). */
object Money {

    /** "₹1,00,000", "₹483.50" — Indian digit grouping, paise shown only when non-zero. */
    fun format(paise: Long): String {
        val sign = if (paise < 0) "-" else ""
        val abs = Math.abs(paise)
        val rupees = groupIndian((abs / 100).toString())
        val fraction = abs % 100
        return if (fraction == 0L) "$sign₹$rupees" else "$sign₹$rupees.${fraction.toString().padStart(2, '0')}"
    }

    /** Plain value for an edit field: "1000" or "1000.5". */
    fun toInput(paise: Long): String {
        val fraction = paise % 100
        return if (fraction == 0L) (paise / 100).toString()
        else "${paise / 100}.${fraction.toString().padStart(2, '0').trimEnd('0')}"
    }

    /** Parses what a user typed ("1000", "1,000", "₹ 999.50") into paise; null if not a valid non-negative amount. */
    fun parse(text: String): Long? {
        val cleaned = text.replace("₹", "").replace(",", "").trim()
        if (!AMOUNT.matches(cleaned) || cleaned.none(Char::isDigit)) return null
        val parts = cleaned.split('.')
        val rupees = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
        if (rupees > MAX_RUPEES) return null
        val paise = parts.getOrNull(1)?.padEnd(2, '0')?.toLong() ?: 0
        return rupees * 100 + paise
    }

    /** 1234567 → 12,34,567 */
    private fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return "$rest,$last3"
    }

    private val AMOUNT = Regex("""\d*(\.\d{1,2})?""")
    private const val MAX_RUPEES = 100_000_000L // ₹10 crore — guards against typos and overflow
}
