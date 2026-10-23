package com.softandtech.tuitionmanager.domain.security

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityLogicTest {

    @Test
    fun pinValidation() {
        assertTrue(PinHasher.isValidPin("1234"))
        assertTrue(PinHasher.isValidPin("123456"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("1234567"))
        assertFalse(PinHasher.isValidPin("12a4"))
    }

    @Test
    fun hashVerifiesOnlyTheRightPin() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("2580", salt, iterations = 1_000)
        assertTrue(PinHasher.matches("2580", salt, hash, iterations = 1_000))
        assertFalse(PinHasher.matches("2581", salt, hash, iterations = 1_000))
        assertFalse(PinHasher.matches("2580", PinHasher.newSalt(), hash, iterations = 1_000))
    }

    @Test
    fun samePinWithDifferentSaltGivesDifferentHash() {
        assertFalse(PinHasher.hash("1111", PinHasher.newSalt(), 1_000).contentEquals(PinHasher.hash("1111", PinHasher.newSalt(), 1_000)))
    }

    @Test
    fun lockoutGrowsAfterFiveMisses() {
        assertEquals(Duration.ZERO, LockPolicy.lockoutAfter(4))
        assertEquals(Duration.ofSeconds(30), LockPolicy.lockoutAfter(5))
        assertEquals(Duration.ofSeconds(60), LockPolicy.lockoutAfter(6))
        assertEquals(Duration.ofSeconds(120), LockPolicy.lockoutAfter(7))
        assertEquals(Duration.ofMinutes(15), LockPolicy.lockoutAfter(12))
        assertEquals(Duration.ofMinutes(15), LockPolicy.lockoutAfter(1_000))
    }

    @Test
    fun autoLockDecision() {
        assertFalse(LockPolicy.shouldLock(pinSet = false, awayMillis = null, autoLockSeconds = 0))
        assertTrue(LockPolicy.shouldLock(pinSet = true, awayMillis = null, autoLockSeconds = 900))
        assertTrue(LockPolicy.shouldLock(pinSet = true, awayMillis = 0, autoLockSeconds = 0))
        assertFalse(LockPolicy.shouldLock(pinSet = true, awayMillis = 59_000, autoLockSeconds = 60))
        assertTrue(LockPolicy.shouldLock(pinSet = true, awayMillis = 60_000, autoLockSeconds = 60))
    }
}
