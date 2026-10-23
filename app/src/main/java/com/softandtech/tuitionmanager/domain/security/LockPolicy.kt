package com.softandtech.tuitionmanager.domain.security

import java.time.Duration

object LockPolicy {
    /** Wrong PINs allowed before a waiting period starts. */
    const val FREE_ATTEMPTS = 5
    private val FIRST_LOCKOUT: Duration = Duration.ofSeconds(30)
    private val MAX_LOCKOUT: Duration = Duration.ofMinutes(15)

    /** Auto-lock choices offered in Settings, in seconds. 0 = as soon as the app leaves the screen. */
    val AUTO_LOCK_CHOICES = listOf(0, 60, 300, 900)
    const val DEFAULT_AUTO_LOCK_SECONDS = 60

    /**
     * How long to wait after [failures] consecutive wrong PINs: nothing for the first 5,
     * then 30 s, doubling with each further miss up to 15 minutes.
     */
    fun lockoutAfter(failures: Int): Duration {
        if (failures < FREE_ATTEMPTS) return Duration.ZERO
        val doublings = (failures - FREE_ATTEMPTS).coerceAtMost(10)
        return minOf(FIRST_LOCKOUT.multipliedBy(1L shl doublings), MAX_LOCKOUT)
    }

    /**
     * Whether returning to the app needs the PIN. [awayMillis] is how long the app was in the
     * background (null = it was never in the background, i.e. a cold start).
     */
    fun shouldLock(pinSet: Boolean, awayMillis: Long?, autoLockSeconds: Int): Boolean = when {
        !pinSet -> false
        awayMillis == null -> true
        else -> awayMillis >= autoLockSeconds * 1000L
    }
}
