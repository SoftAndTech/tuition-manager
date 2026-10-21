package com.softandtech.tuitionmanager.data.security

import java.io.File
import java.security.SecureRandom

/**
 * Provides the 256-bit key that encrypts the SQLCipher database.
 *
 * On first use a random key is generated, wrapped with [wrapper] and stored in [keyFile]
 * (which should live in `noBackupFilesDir`). Later calls unwrap and return the same key.
 */
class DatabaseKeyManager(
    private val keyFile: File,
    private val wrapper: KeyWrapper,
    private val random: SecureRandom = SecureRandom(),
) {

    @Synchronized
    fun getOrCreateKey(): ByteArray {
        if (keyFile.exists()) {
            return wrapper.unwrap(keyFile.readBytes())
        }

        val key = ByteArray(KEY_SIZE_BYTES).also(random::nextBytes)
        keyFile.parentFile?.mkdirs()
        // Write to a temp file first so a crash can never leave a half-written key behind
        val tmp = File(keyFile.parentFile, keyFile.name + ".tmp")
        tmp.writeBytes(wrapper.wrap(key))
        check(tmp.renameTo(keyFile)) { "Could not save database key" }
        return key
    }

    companion object {
        const val KEY_SIZE_BYTES = 32
    }
}
