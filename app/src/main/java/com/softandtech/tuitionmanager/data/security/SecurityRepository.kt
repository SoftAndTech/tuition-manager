package com.softandtech.tuitionmanager.data.security

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.softandtech.tuitionmanager.domain.security.LockPolicy
import com.softandtech.tuitionmanager.domain.security.PinHasher
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class SecuritySettings(
    val pinSet: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockSeconds: Int = LockPolicy.DEFAULT_AUTO_LOCK_SECONDS,
    /** Blocks screenshots and blanks the app in the recent-apps view. */
    val secureScreen: Boolean = false,
    val lastBackup: LocalDate? = null,
)

sealed interface PinCheck {
    data object Correct : PinCheck
    /** [attemptsLeft] before a waiting period starts. */
    data class Wrong(val attemptsLeft: Int) : PinCheck
    /** Too many wrong PINs; try again after [untilMillis] (wall-clock time). */
    data class LockedOut(val untilMillis: Long) : PinCheck
}

/**
 * App-lock and backup bookkeeping. The PIN is kept only as a salted PBKDF2 hash. It guards the
 * app's screens; the database itself stays encrypted with the separate Keystore-protected key.
 */
@Singleton
class SecurityRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<SecuritySettings> = dataStore.data.map { p ->
        SecuritySettings(
            pinSet = p[PIN_HASH] != null,
            biometricEnabled = p[BIOMETRIC] ?: false,
            autoLockSeconds = p[AUTO_LOCK] ?: LockPolicy.DEFAULT_AUTO_LOCK_SECONDS,
            secureScreen = p[SECURE_SCREEN] ?: false,
            lastBackup = p[LAST_BACKUP]?.let(LocalDate::ofEpochDay),
        )
    }

    suspend fun current(): SecuritySettings = settings.first()

    suspend fun setPin(pin: String) {
        require(PinHasher.isValidPin(pin)) { "PIN must be 4–6 digits" }
        val salt = PinHasher.newSalt()
        val hash = withContext(Dispatchers.Default) { PinHasher.hash(pin, salt) }
        dataStore.edit {
            it[PIN_SALT] = encode(salt)
            it[PIN_HASH] = encode(hash)
            it[FAILED] = 0
            it.remove(LOCKOUT_UNTIL)
        }
    }

    /** Removing the PIN also turns off fingerprint unlock, which needs the PIN as a fallback. */
    suspend fun clearPin() {
        dataStore.edit {
            it.remove(PIN_HASH)
            it.remove(PIN_SALT)
            it.remove(FAILED)
            it.remove(LOCKOUT_UNTIL)
            it[BIOMETRIC] = false
        }
    }

    /** Checks a PIN, counting misses and enforcing [LockPolicy.lockoutAfter]. */
    suspend fun checkPin(pin: String, nowMillis: Long = System.currentTimeMillis()): PinCheck {
        val p = dataStore.data.first()
        val lockedUntil = p[LOCKOUT_UNTIL] ?: 0
        if (nowMillis < lockedUntil) return PinCheck.LockedOut(lockedUntil)

        val hash = p[PIN_HASH]?.let(::decode) ?: return PinCheck.Correct
        val salt = p[PIN_SALT]?.let(::decode) ?: return PinCheck.Correct
        val ok = withContext(Dispatchers.Default) { PinHasher.matches(pin, salt, hash) }
        if (ok) {
            recordUnlock()
            return PinCheck.Correct
        }

        val failures = (p[FAILED] ?: 0) + 1
        val wait = LockPolicy.lockoutAfter(failures)
        dataStore.edit {
            it[FAILED] = failures
            if (!wait.isZero) it[LOCKOUT_UNTIL] = nowMillis + wait.toMillis()
        }
        return if (wait.isZero) PinCheck.Wrong(LockPolicy.FREE_ATTEMPTS - failures)
        else PinCheck.LockedOut(nowMillis + wait.toMillis())
    }

    /** Resets the miss counter — after a correct PIN or a successful fingerprint. */
    suspend fun recordUnlock() {
        dataStore.edit {
            it[FAILED] = 0
            it.remove(LOCKOUT_UNTIL)
        }
    }

    suspend fun lockedOutUntil(): Long = dataStore.data.first()[LOCKOUT_UNTIL] ?: 0

    suspend fun setBiometric(enabled: Boolean) = dataStore.edit { it[BIOMETRIC] = enabled }

    suspend fun setAutoLockSeconds(seconds: Int) = dataStore.edit { it[AUTO_LOCK] = seconds }

    suspend fun setSecureScreen(enabled: Boolean) = dataStore.edit { it[SECURE_SCREEN] = enabled }

    suspend fun markBackedUp(date: LocalDate) = dataStore.edit { it[LAST_BACKUP] = date.toEpochDay() }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val FAILED = intPreferencesKey("pin_failed_attempts")
        val LOCKOUT_UNTIL = longPreferencesKey("pin_lockout_until")
        val BIOMETRIC = booleanPreferencesKey("biometric_unlock")
        val AUTO_LOCK = intPreferencesKey("auto_lock_seconds")
        val SECURE_SCREEN = booleanPreferencesKey("secure_screen")
        val LAST_BACKUP = longPreferencesKey("last_backup_epoch_day")
    }
}
