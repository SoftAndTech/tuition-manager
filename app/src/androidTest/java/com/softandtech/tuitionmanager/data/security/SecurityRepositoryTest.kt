package com.softandtech.tuitionmanager.data.security

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurityRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storeName = "test-security-${System.nanoTime()}"
    private val repo = SecurityRepository(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(storeName) })

    @After
    fun tearDown() {
        context.preferencesDataStoreFile(storeName).delete()
    }

    @Test
    fun correctPinUnlocks_wrongPinsCountDownThenLockOut() = runTest {
        repo.setPin("2580")
        assertTrue(repo.current().pinSet)
        val now = 1_000_000L

        assertEquals(PinCheck.Correct, repo.checkPin("2580", now))
        assertEquals(PinCheck.Wrong(attemptsLeft = 4), repo.checkPin("1111", now))
        repeat(3) { repo.checkPin("1111", now) }
        assertEquals(PinCheck.LockedOut(now + 30_000), repo.checkPin("1111", now)) // 5th miss

        // Even the right PIN is refused during the wait…
        assertTrue(repo.checkPin("2580", now + 10_000) is PinCheck.LockedOut)
        // …and works once it's over, resetting the count
        assertEquals(PinCheck.Correct, repo.checkPin("2580", now + 31_000))
        assertEquals(PinCheck.Wrong(attemptsLeft = 4), repo.checkPin("1111", now + 32_000))
    }

    @Test
    fun removingPinAlsoTurnsOffFingerprint() = runTest {
        repo.setPin("123456")
        repo.setBiometric(true)
        repo.clearPin()
        val s = repo.current()
        assertFalse(s.pinSet)
        assertFalse(s.biometricEnabled)
    }
}
