package com.softandtech.tuitionmanager.data.security

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatabaseKeyManagerTest {

    @get:Rule val tmp = TemporaryFolder()

    /** Reversible stand-in for Android Keystore, which is not available on the JVM. */
    private class XorWrapper : KeyWrapper {
        override fun wrap(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        override fun unwrap(wrapped: ByteArray) = wrap(wrapped)
    }

    private fun manager(file: File) = DatabaseKeyManager(file, XorWrapper())

    @Test
    fun createsKeyOfExpectedSize() {
        val key = manager(File(tmp.root, "db.key")).getOrCreateKey()
        assertEquals(DatabaseKeyManager.KEY_SIZE_BYTES, key.size)
    }

    @Test
    fun returnsSameKeyAcrossInstances() {
        val file = File(tmp.root, "db.key")
        val first = manager(file).getOrCreateKey()
        val second = manager(file).getOrCreateKey()
        assertArrayEquals(first, second)
    }

    @Test
    fun storesOnlyWrappedKeyOnDisk() {
        val file = File(tmp.root, "db.key")
        val key = manager(file).getOrCreateKey()
        assertTrue(file.exists())
        assertFalse(key.contentEquals(file.readBytes()))
    }

    @Test
    fun differentInstallsGetDifferentKeys() {
        val a = manager(File(tmp.root, "a.key")).getOrCreateKey()
        val b = manager(File(tmp.root, "b.key")).getOrCreateKey()
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun createsMissingParentDirectory() {
        val file = File(tmp.root, "nested/dir/db.key")
        manager(file).getOrCreateKey()
        assertTrue(file.exists())
        assertFalse(File(file.parentFile, "db.key.tmp").exists())
    }
}
