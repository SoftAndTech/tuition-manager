package com.softandtech.tuitionmanager.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.softandtech.tuitionmanager.data.db.TuitionDatabase
import com.softandtech.tuitionmanager.data.db.entity.BatchEntity
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import com.softandtech.tuitionmanager.data.repository.BatchRepository
import com.softandtech.tuitionmanager.data.repository.FeeRepository
import com.softandtech.tuitionmanager.data.repository.StudentRepository
import com.softandtech.tuitionmanager.domain.model.PaymentMode
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real SQLCipher end to end: phone A's database → passphrase backup → phone B's database. */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val keyA = ByteArray(32) { 7 }
    private val keyB = ByteArray(32) { 42 }
    private val passphrase = "correct horse battery"
    private lateinit var backupFile: File

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        cleanUp()
        backupFile = File(context.cacheDir, "test-backup.tmbak")
    }

    @After
    fun cleanUp() {
        listOf(SOURCE, TARGET).forEach { context.deleteDatabase(it) }
        File(context.cacheDir, "test-backup.tmbak").delete()
    }

    private suspend fun makeBackup() {
        val db = TuitionDatabase.build(context, keyA, SOURCE)
        try {
            val fees = FeeRepository(db)
            val batch = BatchRepository(db).add(BatchEntity(name = "Maths", monthlyFeePaise = 100_000))
            val id = StudentRepository(db, fees).add(StudentEntity(name = "Asha Sharma", joinDate = LocalDate.of(2026, 9, 1)))
            BatchRepository(db).enroll(id, batch, LocalDate.of(2026, 9, 1))
            fees.generateDues(LocalDate.of(2026, 10, 23))
            fees.collectPayment(id, 150_000, PaymentMode.UPI, LocalDate.of(2026, 10, 23))

            // Exported while Room still has the database open, exactly as in the app
            BackupFiles.checkpoint(db.openHelper.writableDatabase)
            BackupFiles.export(context.getDatabasePath(SOURCE), keyA, backupFile, passphrase, TuitionDatabase.VERSION)
        } finally {
            db.close()
        }
    }

    @Test
    fun backupRestoresOnAPhoneWithADifferentKey() = runTest {
        makeBackup()

        val summary = BackupFiles.inspect(backupFile, passphrase, TuitionDatabase.VERSION)
        assertEquals(BackupSummary(TuitionDatabase.VERSION, students = 1, payments = 1), summary)

        BackupFiles.reencrypt(backupFile, passphrase, context.getDatabasePath(TARGET), keyB)

        val restored = TuitionDatabase.build(context, keyB, TARGET)
        try {
            val student = restored.studentDao().search("", true).first().single()
            assertEquals("Asha Sharma", student.name)
            // Payment allocations came across too: ₹1,500 paid against ₹2,000 billed
            assertEquals(50_000L, restored.feeDueDao().observeTotalOutstandingForStudent(student.id).first())
        } finally {
            restored.close()
        }
    }

    @Test
    fun backupFileIsNotReadableWithoutPassphrase() = runTest {
        makeBackup()
        val header = backupFile.inputStream().use { input -> ByteArray(16).also { input.read(it) } }
        assertFalse(header.contentEquals("SQLite format 3\u0000".toByteArray()))
    }

    @Test(expected = BackupException.CannotOpen::class)
    fun wrongPassphraseIsRejected() = runTest {
        makeBackup()
        BackupFiles.inspect(backupFile, "wrong passphrase", TuitionDatabase.VERSION)
    }

    @Test(expected = BackupException.CannotOpen::class)
    fun randomFileIsRejected() {
        backupFile.writeText("these are my holiday photos")
        BackupFiles.inspect(backupFile, passphrase, TuitionDatabase.VERSION)
    }

    @Test(expected = BackupException.TooNew::class)
    fun backupFromNewerAppIsRejected() = runTest {
        makeBackup()
        SQLiteDatabase.openDatabase(backupFile.absolutePath, passphrase, null, SQLiteDatabase.OPEN_READWRITE, null, null).use {
            it.execSQL("PRAGMA user_version = 99")
        }
        BackupFiles.inspect(backupFile, passphrase, TuitionDatabase.VERSION)
    }

    private companion object {
        const val SOURCE = "backup-source.db"
        const val TARGET = "backup-target.db"
    }
}
