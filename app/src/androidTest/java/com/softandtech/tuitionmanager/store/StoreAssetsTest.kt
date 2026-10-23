package com.softandtech.tuitionmanager.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.data.db.TuitionDatabase
import com.softandtech.tuitionmanager.data.db.entity.BatchEntity
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import com.softandtech.tuitionmanager.data.repository.AttendanceRepository
import com.softandtech.tuitionmanager.data.repository.BatchRepository
import com.softandtech.tuitionmanager.data.repository.FeeRepository
import com.softandtech.tuitionmanager.data.repository.StudentRepository
import com.softandtech.tuitionmanager.data.security.DatabaseKeyManager
import com.softandtech.tuitionmanager.data.security.KeystoreKeyWrapper
import com.softandtech.tuitionmanager.data.settings.AppSettings
import com.softandtech.tuitionmanager.data.settings.SettingsRepository
import com.softandtech.tuitionmanager.domain.WeekDays
import com.softandtech.tuitionmanager.domain.model.AttendanceStatus
import com.softandtech.tuitionmanager.domain.model.PaymentMode
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tools for the Play Store listing, not regular tests — they only run when asked:
 *
 *   adb shell am instrument -w -e storeAssets true \
 *     -e class com.softandtech.tuitionmanager.store.StoreAssetsTest \
 *     com.softandtech.tuitionmanager.test/androidx.test.runner.AndroidJUnitRunner
 *
 * Output goes to /sdcard/Android/data/com.softandtech.tuitionmanager/files/store/.
 */
@RunWith(AndroidJUnit4::class)
class StoreAssetsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val enabled = InstrumentationRegistry.getArguments().getString("storeAssets") == "true"
    private val outDir get() = File(context.getExternalFilesDir(null), "store").apply { mkdirs() }

    private val indigo = Color.parseColor("#3949AB")

    /** 512×512 Play Store icon: full square, Play rounds the corners itself. */
    @Test
    fun renderStoreIcon() {
        assumeTrue(enabled)
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(indigo)
        ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)!!.apply {
            setBounds(0, 0, 512, 512)
            draw(canvas)
        }
        save(bitmap, "icon-512.png")
    }

    /** 1024×500 feature graphic shown at the top of the store page. */
    @Test
    fun renderFeatureGraphic() {
        assumeTrue(enabled)
        val bitmap = Bitmap.createBitmap(1024, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(indigo)
        ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)!!.apply {
            setBounds(10, 0, 510, 500)
            draw(canvas)
        }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E8EAF6"); textSize = 34f }
        val accent = Paint(line).apply { color = Color.parseColor("#FFC107") }
        canvas.drawText("Tuition Manager", 470f, 200f, title)
        canvas.drawText("Fees · Attendance · Receipts", 472f, 262f, line)
        canvas.drawText("WhatsApp reminders", 472f, 310f, line)
        canvas.drawText("Private. Offline. No ads.", 472f, 380f, accent)
        save(bitmap, "feature-graphic-1024x500.png")
    }

    /**
     * Fills the real app with a realistic demo institute for store screenshots.
     * Replaces nothing — run it on a fresh install (or after Erase all data).
     */
    @Test
    fun seedDemoData() = runTest {
        assumeTrue(enabled)
        System.loadLibrary("sqlcipher")
        val key = DatabaseKeyManager(File(context.noBackupFilesDir, "db.key"), KeystoreKeyWrapper()).getOrCreateKey()
        val db = TuitionDatabase.build(context, key)
        try {
            val today = LocalDate.now()
            val fees = FeeRepository(db)
            val students = StudentRepository(db, fees)
            val batches = BatchRepository(db)
            val attendance = AttendanceRepository(db)

            val mwf = WeekDays.toMask(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
            val tts = WeekDays.toMask(listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY))
            val todayMask = WeekDays.toMask(listOf(today.dayOfWeek))
            val maths = batches.add(BatchEntity(name = "Class 10 Maths", subject = "Maths", daysMask = mwf or todayMask, startMinute = 17 * 60, endMinute = 18 * 60 + 30, monthlyFeePaise = 150_000))
            val science = batches.add(BatchEntity(name = "Class 9 Science", subject = "Science", daysMask = tts, startMinute = 16 * 60, endMinute = 17 * 60, monthlyFeePaise = 120_000))

            val started = today.minusMonths(2).withDayOfMonth(1)
            val roster = listOf(
                Triple("Aarav Sharma", "Class 10, DAV", maths), Triple("Ananya Iyer", "Class 10, KV", maths),
                Triple("Ishaan Verma", "Class 10, DPS", maths), Triple("Diya Patel", "Class 10, KV", maths),
                Triple("Kabir Singh", "Class 9, DAV", science), Triple("Meera Nair", "Class 9, St. Mary's", science),
                Triple("Rohan Gupta", "Class 9, DPS", science), Triple("Saanvi Reddy", "Class 9, KV", science),
            )
            val ids = roster.mapIndexed { i, (name, cls, batch) ->
                val id = students.add(
                    StudentEntity(name = name, className = cls, parentName = "Mr/Mrs " + name.substringAfter(' '), parentPhone = "98${(10_000_000 + i * 1_234_567).toString().take(8)}", joinDate = started),
                )
                batches.enroll(id, batch, started)
                id
            }
            fees.generateDues(today)
            // Most have paid; a couple are behind so the overdue list has something to show
            ids.forEachIndexed { i, id ->
                val months = when (i) { 2, 5 -> 1; 6 -> 0; else -> 3 }
                val fee = if (roster[i].third == maths) 150_000L else 120_000L
                if (months > 0) fees.collectPayment(id, fee * months, if (i % 2 == 0) PaymentMode.UPI else PaymentMode.CASH, today.minusDays(i.toLong()))
            }
            attendance.mark(maths, today, ids.take(4).associateWith { AttendanceStatus.PRESENT } + (ids[2] to AttendanceStatus.ABSENT))

            SettingsRepository(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }).save(
                AppSettings(instituteName = "Bright Tuition Classes", tutorName = "Meena Iyer", tutorPhone = "98765 43210", upiId = "brighttuition@okaxis"),
            )
        } finally {
            db.close()
            key.fill(0)
        }
    }

    private fun save(bitmap: Bitmap, name: String) {
        File(outDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
