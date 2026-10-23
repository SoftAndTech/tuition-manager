package com.softandtech.tuitionmanager.ui.receipt

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.data.repository.ReceiptData
import com.softandtech.tuitionmanager.data.settings.AppSettings
import com.softandtech.tuitionmanager.domain.Money
import com.softandtech.tuitionmanager.domain.Receipts
import com.softandtech.tuitionmanager.domain.model.PaymentMode
import com.softandtech.tuitionmanager.ui.common.Formatters
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Draws a one-page A5 fee receipt with Android's built-in PdfDocument (no third-party library).
 * Files go to the app's private cache, are shared through FileProvider, and are cleared on the next run.
 */
@Singleton
class ReceiptPdfWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun write(data: ReceiptData, settings: AppSettings): File {
        val dir = File(context.cacheDir, RECEIPTS_DIR).apply { mkdirs() }
        // Receipts contain student names; don't let old copies pile up in the cache
        dir.listFiles()?.forEach { it.delete() }

        val file = File(dir, Receipts.fileName(settings.receiptPrefix, data.payment.receiptSeq))
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
            draw(page.canvas, data, settings)
            document.finishPage(page)
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        return file
    }

    private fun draw(canvas: android.graphics.Canvas, data: ReceiptData, settings: AppSettings) {
        val right = (PAGE_WIDTH - MARGIN).toFloat()
        val width = right - MARGIN
        var y = MARGIN + 10f

        // Header: who issued the receipt
        val header = settings.signature.ifBlank { context.getString(R.string.app_name) }
        text(canvas, header, MARGIN.toFloat(), y, paint(18f, bold = true), width)
        y += 18
        val contact = listOf(settings.tutorPhone, settings.upiId.takeIf { it.isNotBlank() }?.let { "UPI: $it" })
            .filter { !it.isNullOrBlank() }.joinToString("  ·  ")
        if (contact.isNotEmpty()) {
            text(canvas, contact, MARGIN.toFloat(), y, paint(9f, color = GREY), width)
            y += 12
        }
        y += 8
        line(canvas, y, right); y += 22

        // Title row
        text(canvas, s(R.string.receipt_title), MARGIN.toFloat(), y, paint(14f, bold = true), width / 2)
        val number = s(R.string.receipt_no, Receipts.number(settings.receiptPrefix, data.payment.receiptSeq))
        textRight(canvas, number, right, y, paint(11f, bold = true))
        y += 16
        textRight(canvas, s(R.string.receipt_date, Formatters.date(data.payment.paidOn)), right, y, paint(10f))
        y += 22

        // Student
        text(canvas, s(R.string.receipt_received_from), MARGIN.toFloat(), y, paint(9f, color = GREY), width)
        y += 14
        val student = listOfNotNull(data.student.name, data.student.className).joinToString(" · ")
        text(canvas, student, MARGIN.toFloat(), y, paint(12f, bold = true), width)
        y += 22

        // Paid-for lines
        line(canvas, y, right); y += 16
        text(canvas, s(R.string.receipt_paid_for), MARGIN.toFloat(), y, paint(9f, color = GREY), width)
        textRight(canvas, s(R.string.receipt_amount), right, y, paint(9f, color = GREY))
        y += 16
        val rowPaint = paint(10.5f)
        data.lines.take(MAX_LINES).forEach { lineItem ->
            val label = listOfNotNull(lineItem.title, lineItem.period?.let(Formatters::month)).joinToString(" — ")
            text(canvas, label, MARGIN.toFloat(), y, rowPaint, width - 90)
            textRight(canvas, Money.format(lineItem.amountPaise), right, y, rowPaint)
            y += 16
        }
        if (data.lines.size > MAX_LINES) {
            val rest = data.lines.drop(MAX_LINES).sumOf { it.amountPaise }
            text(canvas, s(R.string.receipt_more_items, data.lines.size - MAX_LINES), MARGIN.toFloat(), y, rowPaint, width - 90)
            textRight(canvas, Money.format(rest), right, y, rowPaint)
            y += 16
        }
        if (data.advancePaise > 0) {
            text(canvas, s(R.string.receipt_advance), MARGIN.toFloat(), y, rowPaint, width - 90)
            textRight(canvas, Money.format(data.advancePaise), right, y, rowPaint)
            y += 16
        }
        y += 4
        line(canvas, y, right); y += 22

        // Total
        text(canvas, s(R.string.receipt_total), MARGIN.toFloat(), y, paint(13f, bold = true), width / 2)
        textRight(canvas, Money.format(data.payment.amountPaise), right, y, paint(16f, bold = true))
        y += 18
        text(canvas, s(R.string.receipt_mode, modeLabel(data.payment.mode)), MARGIN.toFloat(), y, paint(10f), width)
        y += 14
        data.payment.note?.let {
            text(canvas, s(R.string.receipt_note, it), MARGIN.toFloat(), y, paint(10f), width)
            y += 14
        }
        y += 8
        val balance = if (data.balanceDuePaise > 0) s(R.string.receipt_balance, Money.format(data.balanceDuePaise))
        else s(R.string.receipt_no_balance)
        text(canvas, balance, MARGIN.toFloat(), y, paint(10f, bold = true), width)

        // Footer
        val footerY = (PAGE_HEIGHT - MARGIN).toFloat()
        text(canvas, s(R.string.receipt_footer), MARGIN.toFloat(), footerY, paint(8f, color = GREY), width)
    }

    private fun modeLabel(mode: PaymentMode): String = context.getString(
        when (mode) {
            PaymentMode.CASH -> R.string.mode_cash
            PaymentMode.UPI -> R.string.mode_upi
            PaymentMode.BANK -> R.string.mode_bank
            PaymentMode.OTHER -> R.string.mode_other
        },
    )

    private fun s(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    /** Draws left-aligned text, shortened with "…" if it doesn't fit in [maxWidth]. */
    private fun text(canvas: android.graphics.Canvas, value: String, x: Float, y: Float, paint: TextPaint, maxWidth: Float) {
        val fitted = TextUtils.ellipsize(value, paint, maxWidth, TextUtils.TruncateAt.END).toString()
        canvas.drawText(fitted, x, y, paint)
    }

    private fun textRight(canvas: android.graphics.Canvas, value: String, right: Float, y: Float, paint: TextPaint) {
        canvas.drawText(value, right - paint.measureText(value), y, paint)
    }

    private fun line(canvas: android.graphics.Canvas, y: Float, right: Float) {
        canvas.drawLine(MARGIN.toFloat(), y, right, y, Paint().apply { color = LINE; strokeWidth = 0.8f })
    }

    companion object {
        const val RECEIPTS_DIR = "receipts"
        private const val PAGE_WIDTH = 420 // A5 in PostScript points
        private const val PAGE_HEIGHT = 595
        private const val MARGIN = 32
        private const val MAX_LINES = 14
        private const val GREY = 0xFF666666.toInt()
        private const val LINE = 0xFFBBBBBB.toInt()
    }
}
