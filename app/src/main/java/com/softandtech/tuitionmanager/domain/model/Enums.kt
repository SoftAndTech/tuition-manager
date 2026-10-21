package com.softandtech.tuitionmanager.domain.model

enum class PaymentMode { CASH, UPI, BANK, OTHER }

enum class AttendanceStatus { PRESENT, ABSENT, LEAVE }

/** Derived from a due's amount, what has been paid against it, and whether it was waived. */
enum class DueStatus { DUE, PARTIAL, PAID, WAIVED }

/** How to charge the month in which a student joins a batch. */
enum class JoinMonthPolicy {
    /** Charge the full monthly fee for the joining month. */
    FULL,

    /** Charge only for the days remaining in the joining month. */
    PRORATED,

    /** Start charging from the month after joining. */
    NEXT_MONTH,
}
