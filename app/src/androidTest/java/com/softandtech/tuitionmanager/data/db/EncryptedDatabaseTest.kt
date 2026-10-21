package com.softandtech.tuitionmanager.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import com.softandtech.tuitionmanager.data.security.DatabaseKeyManager
import com.softandtech.tuitionmanager.data.security.KeystoreKeyWrapper
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "encrypted-test.db"

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun dataRoundTripsAndFileIsNotPlainSqlite() = runTest {
        val key = ByteArray(32) { it.toByte() }
        TuitionDatabase.build(context, key, dbName).apply {
            studentDao().insert(StudentEntity(name = "Asha", joinDate = LocalDate.of(2026, 10, 1)))
            assertEquals(1, studentDao().observeActiveCount().first())
            close()
        }

        // A plain SQLite file starts with "SQLite format 3\0"; an encrypted one must not.
        val header = context.getDatabasePath(dbName).inputStream().use { input -> ByteArray(16).also { input.read(it) } }
        assertFalse(header.contentEquals("SQLite format 3\u0000".toByteArray()))

        TuitionDatabase.build(context, key, dbName).apply {
            assertEquals("Asha", studentDao().getById(1)?.name)
            close()
        }
    }

    @Test(expected = Exception::class)
    fun wrongKeyCannotOpenDatabase() = runTest {
        TuitionDatabase.build(context, ByteArray(32) { 1 }, dbName).apply {
            studentDao().insert(StudentEntity(name = "Asha", joinDate = LocalDate.of(2026, 10, 1)))
            close()
        }
        TuitionDatabase.build(context, ByteArray(32) { 2 }, dbName).studentDao().getById(1)
    }

    @Test
    fun keystoreWrappedKeySurvivesReload() {
        val file = File(context.noBackupFilesDir, "test-db.key").apply { delete() }
        val wrapper = KeystoreKeyWrapper(alias = "tuition_test_kek")
        val first = DatabaseKeyManager(file, wrapper).getOrCreateKey()
        val second = DatabaseKeyManager(file, wrapper).getOrCreateKey()
        assertArrayEquals(first, second)
        file.delete()
    }
}
