package com.softandtech.tuitionmanager.ui.receipt

import android.content.Context
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import com.softandtech.tuitionmanager.data.db.model.FeeDueWithPaid
import com.softandtech.tuitionmanager.data.repository.ReceiptData
import com.softandtech.tuitionmanager.data.settings.AppSettings
import com.softandtech.tuitionmanager.domain.Money
import com.softandtech.tuitionmanager.domain.Receipts
import com.softandtech.tuitionmanager.domain.WhatsApp
import com.softandtech.tuitionmanager.ui.common.Formatters
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Builds the text of fee reminders and receipt messages from string resources (so they can be translated). */
object Reminders {

    /** Parent's number first — they usually pay — else the student's own. Null if neither is usable. */
    fun waNumberFor(parentPhone: String?, studentPhone: String?): String? =
        WhatsApp.toWaNumber(parentPhone) ?: WhatsApp.toWaNumber(studentPhone)

    fun waNumberFor(student: StudentEntity): String? = waNumberFor(student.parentPhone, student.phone)

    fun feeReminder(
        context: Context,
        studentName: String,
        parentName: String?,
        unpaid: List<FeeDueWithPaid>,
        settings: AppSettings,
    ): String {
        val total = unpaid.sumOf { it.outstandingPaise }
        val items = unpaid.map { due -> due.due.period?.let(Formatters::month) ?: due.due.title }.distinct()
        val itemsText = if (items.size <= MAX_ITEMS) items.joinToString(", ")
        else items.take(MAX_ITEMS).joinToString(", ") + " " + context.getString(R.string.reminder_more, items.size - MAX_ITEMS)

        return buildString {
            appendLine(
                if (parentName.isNullOrBlank()) context.getString(R.string.reminder_greeting)
                else context.getString(R.string.reminder_greeting_named, parentName.trim()),
            )
            appendLine()
            appendLine(context.getString(R.string.reminder_body, Money.format(total), studentName, itemsText))
            if (settings.upiId.isNotBlank()) appendLine(context.getString(R.string.reminder_upi, settings.upiId.trim()))
            appendLine()
            append(context.getString(R.string.reminder_thanks))
            if (settings.signature.isNotEmpty()) append("\n").append(settings.signature)
        }
    }

    fun receiptMessage(context: Context, data: ReceiptData, settings: AppSettings): String =
        context.getString(
            R.string.receipt_share_message,
            Receipts.number(settings.receiptPrefix, data.payment.receiptSeq),
            data.student.name,
            Money.format(data.payment.amountPaise),
        ) + (if (settings.signature.isNotEmpty()) "\n" + settings.signature else "")

    private const val MAX_ITEMS = 6
}

/** Injectable access to [Reminders] for ViewModels, which hold no Context of their own. */
@Singleton
class ReminderTextProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun feeReminder(studentName: String, parentName: String?, unpaid: List<FeeDueWithPaid>, settings: AppSettings) =
        Reminders.feeReminder(context, studentName, parentName, unpaid, settings)

    fun receiptMessage(data: ReceiptData, settings: AppSettings) = Reminders.receiptMessage(context, data, settings)
}
