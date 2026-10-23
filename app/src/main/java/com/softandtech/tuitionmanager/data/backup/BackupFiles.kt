package com.softandtech.tuitionmanager.data.backup

import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase

/** What a backup file contains, shown to the tutor before restoring over their data. */
data class BackupSummary(val schemaVersion: Int, val students: Int, val payments: Int)

sealed class BackupException(message: String) : Exception(message) {
    /** Wrong passphrase, or not a Tuition Manager backup at all — SQLCipher can't tell these apart. */
    class CannotOpen : BackupException("Wrong passphrase or not a backup file")
    class NotTuitionBackup : BackupException("Not a Tuition Manager backup")
    class TooNew(val version: Int) : BackupException("Backup is from a newer app version ($version)")
}

/**
 * Backup files are themselves SQLCipher databases, encrypted with a passphrase the tutor chooses
 * (SQLCipher derives the key with PBKDF2 and stores a random salt in the file). Data moves between
 * encrypted databases with sqlcipher_export, so no plain-text copy ever touches the disk.
 */
object BackupFiles {

    const val MIN_PASSPHRASE_LENGTH = 8

    /**
     * Writes everything in the live database [liveFile] (encrypted with [deviceKey]) to [target],
     * encrypted with [passphrase].
     *
     * The backup file is the main connection and the live database is attached to it read-only.
     * Attaching on Room's own connection instead would make SQLCipher try to switch Room's
     * write-ahead log off, which fails whenever Room is busy.
     */
    fun export(liveFile: File, deviceKey: ByteArray, target: File, passphrase: String, schemaVersion: Int) {
        target.delete()
        val db = SQLiteDatabase.openOrCreateDatabase(target, passphrase, null, null, null)
        try {
            db.execSQL("ATTACH DATABASE ? AS live KEY ?", arrayOf<Any>(liveFile.absolutePath, deviceKey))
            try {
                db.rawQuery("SELECT sqlcipher_export('main', 'live')", null).use { it.moveToFirst() }
                db.execSQL("PRAGMA main.user_version = $schemaVersion")
            } finally {
                db.execSQL("DETACH DATABASE live")
            }
        } finally {
            db.close()
        }
    }

    /** Folds Room's write-ahead log into the main file so a copy made from the file is complete. */
    fun checkpoint(room: SupportSQLiteDatabase) {
        room.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
    }

    /** Opens [file] with [passphrase] and checks it's a backup this app can restore. */
    fun inspect(file: File, passphrase: String, maxSchemaVersion: Int): BackupSummary {
        val db = try {
            SQLiteDatabase.openDatabase(file.absolutePath, passphrase, null, SQLiteDatabase.OPEN_READONLY, null, null)
        } catch (e: Exception) {
            throw BackupException.CannotOpen()
        }
        try {
            // A wrong key only shows up on the first read
            val tables = try {
                db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
                    buildSet<String> { while (c.moveToNext()) add(c.getString(0)) }
                }
            } catch (e: Exception) {
                throw BackupException.CannotOpen()
            }
            if (!tables.containsAll(listOf("students", "payments", "fee_dues"))) throw BackupException.NotTuitionBackup()

            val version = db.rawQuery("PRAGMA user_version", null).use { it.moveToFirst(); it.getInt(0) }
            if (version > maxSchemaVersion) throw BackupException.TooNew(version)

            fun count(table: String) = db.rawQuery("SELECT COUNT(*) FROM $table", null).use { it.moveToFirst(); it.getInt(0) }
            return BackupSummary(version, count("students"), count("payments"))
        } finally {
            db.close()
        }
    }

    /**
     * Creates [target] encrypted with this phone's [deviceKey] (the same way Room opens it) and
     * copies everything from the backup into it.
     */
    fun reencrypt(backup: File, passphrase: String, target: File, deviceKey: ByteArray) {
        listOf(target, File(target.path + "-wal"), File(target.path + "-shm"), File(target.path + "-journal")).forEach { it.delete() }
        val db = SQLiteDatabase.openOrCreateDatabase(target, deviceKey, null, null, null)
        try {
            db.execSQL("ATTACH DATABASE ? AS backup KEY ?", arrayOf<Any>(backup.absolutePath, passphrase))
            try {
                db.rawQuery("SELECT sqlcipher_export('main', 'backup')", null).use { it.moveToFirst() }
                val version = db.rawQuery("PRAGMA backup.user_version", null).use { it.moveToFirst(); it.getInt(0) }
                db.execSQL("PRAGMA main.user_version = $version")
            } finally {
                db.execSQL("DETACH DATABASE backup")
            }
        } finally {
            db.close()
        }
    }
}
