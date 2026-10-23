package com.softandtech.tuitionmanager.domain

/** Free plan limits. Pro (a one-time purchase) removes them. */
object Plan {
    const val FREE_STUDENT_LIMIT = 15
    const val FREE_BATCH_LIMIT = 2

    /** Whether one more can be added on the current plan. Only counts active records. */
    fun canAdd(activeCount: Int, freeLimit: Int, isPro: Boolean): Boolean = isPro || activeCount < freeLimit
}
