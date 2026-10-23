package com.softandtech.tuitionmanager.ui.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.softandtech.tuitionmanager.data.db.TuitionDatabase
import com.softandtech.tuitionmanager.data.db.entity.BatchEntity
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import com.softandtech.tuitionmanager.data.repository.BatchRepository
import com.softandtech.tuitionmanager.data.repository.FeeRepository
import com.softandtech.tuitionmanager.data.repository.StudentRepository
import com.softandtech.tuitionmanager.data.settings.AppSettings
import com.softandtech.tuitionmanager.domain.model.PaymentMode
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders a receipt with Android's PdfRenderer to prove it's a readable one-page document.
 * Also saves a PNG preview to the app's external files dir for visual review:
 *   adb pull /sdcard/Android/data/com.softandtech.tuitionmanager/files/receipt-preview.png
 */
@RunWith(AndroidJUnit4::class)
class ReceiptRenderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun receiptRendersAsOnePageWithInk() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, TuitionDatabase::class.java).build()
        try {
            val fees = FeeRepository(db)
            val batches = BatchRepository(db)
            val maths = batches.add(BatchEntity(name = "Class 10 Maths", monthlyFeePaise = 100_000))
            val science = batches.add(BatchEntity(name = "Class 10 Science", monthlyFeePaise = 80_000))
            val id = StudentRepository(db, fees).add(
                StudentEntity(name = "Asha Sharma", className = "Class 10 DPS", joinDate = LocalDate.of(2026, 9, 1)),
            )
            batches.enroll(id, maths, LocalDate.of(2026, 9, 1))
            batches.enroll(id, science, LocalDate.of(2026, 9, 1))
            val today = LocalDate.of(2026, 10, 23)
            fees.generateDues(today) // ₹3,600 over two months
            fees.addOneTimeDue(id, "Admission fee", 50_000, LocalDate.of(2026, 9, 1))
            val payment = fees.collectPayment(id, 300_000, PaymentMode.UPI, today, note = "Paid by father")

            val settings = AppSettings(
                instituteName = "Bright Tuition Classes",
                tutorPhone = "98765 43210",
                upiId = "bright@okaxis",
                receiptPrefix = "BT-",
            )
            val pdf = ReceiptPdfWriter(context).write(fees.getReceipt(payment.id)!!, settings)

            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    assertEquals(1, renderer.pageCount)
                    renderer.openPage(0).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val dark = (0 until bitmap.height step 4).sumOf { y ->
                            (0 until bitmap.width step 4).count { x -> Color.red(bitmap.getPixel(x, y)) < 128 }
                        }
                        assertTrue("Page looks blank", dark > 500)

                        File(context.getExternalFilesDir(null), "receipt-preview.png").outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    }
                }
            }
        } finally {
            db.close()
        }
    }
}
