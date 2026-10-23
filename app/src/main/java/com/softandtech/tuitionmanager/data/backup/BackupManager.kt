package com.softandtech.tuitionmanager.data.backup

import android.content.Context
import com.softandtech.tuitionmanager.data.db.TuitionDatabase
import com.softandtech.tuitionmanager.data.security.DatabaseKeyManager
import com.softandtech.tuitionmanager.data.security.SecurityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Backup and restore through files the tutor picks (Google Drive, WhatsApp to self, pen drive…).
 * Working copies live briefly in the private cache and are always encrypted.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: TuitionDatabase,
    private val keyManager: DatabaseKeyManager,
    private val securityRepository: SecurityRepository,
    private val clock: Clock,
) {
    private val workDir: File get() = File(context.cacheDir, "backup").apply { mkdirs() }
    private val pendingRestore: File get() = File(workDir, "restore.tmbak")

    fun suggestedFileName(): String = "TuitionManager_backup_${LocalDate.now(clock)}.tmbak"

    suspend fun export(output: OutputStream, passphrase: String) = withContext(Dispatchers.IO) {
        require(passphrase.length >= BackupFiles.MIN_PASSPHRASE_LENGTH)
        val temp = File(workDir, "export.tmbak")
        val key = keyManager.getOrCreateKey()
        try {
            BackupFiles.checkpoint(database.openHelper.writableDatabase)
            BackupFiles.export(context.getDatabasePath(TuitionDatabase.NAME), key, temp, passphrase, TuitionDatabase.VERSION)
            temp.inputStream().use { it.copyTo(output) }
            output.flush()
        } finally {
            key.fill(0)
            temp.delete()
        }
        securityRepository.markBackedUp(LocalDate.now(clock))
    }

    /** Copies the chosen file in and checks it; keeps it for [restore]. Throws [BackupException]. */
    suspend fun inspect(input: InputStream, passphrase: String): BackupSummary = withContext(Dispatchers.IO) {
        pendingRestore.outputStream().use { input.copyTo(it) }
        try {
            BackupFiles.inspect(pendingRestore, passphrase, TuitionDatabase.VERSION)
        } catch (e: Exception) {
            pendingRestore.delete()
            throw e
        }
    }

    /**
     * Replaces all data with the inspected backup. Closes the database — the app must restart
     * afterwards (see [com.softandtech.tuitionmanager.ui.settings.restartApp]).
     */
    suspend fun restore(passphrase: String) = withContext(Dispatchers.IO) {
        val staged = context.getDatabasePath("tuition-restore.db")
        val key = keyManager.getOrCreateKey()
        try {
            BackupFiles.reencrypt(pendingRestore, passphrase, staged, key)
        } finally {
            key.fill(0)
            pendingRestore.delete()
        }

        database.close()
        val live = context.getDatabasePath(TuitionDatabase.NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { File(live.path + it).delete() }
        check(staged.renameTo(live)) { "Could not put the restored data in place" }
    }

    fun discardPendingRestore() {
        pendingRestore.delete()
    }
}
