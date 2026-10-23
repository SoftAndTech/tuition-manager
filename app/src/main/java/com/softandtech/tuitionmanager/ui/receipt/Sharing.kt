package com.softandtech.tuitionmanager.ui.receipt

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.softandtech.tuitionmanager.R
import java.io.File

/** Every hand-off to another app (share sheet, WhatsApp, dialer) goes through here. */
object Sharing {

    /**
     * Set by MainActivity to the app lock's "expect to come straight back" hook, so returning from
     * WhatsApp or the share sheet doesn't ask for the PIN when auto-lock is set to Immediately.
     */
    var beforeLeavingApp: (() -> Unit)? = null

    /** Opens the share sheet for a receipt PDF (the tutor picks WhatsApp, email, etc.). */
    fun sharePdf(context: Context, file: File, message: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, message)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        beforeLeavingApp?.invoke()
        context.startActivity(Intent.createChooser(send, context.getString(R.string.receipt_share_title)))
    }

    /**
     * Opens a WhatsApp chat with [waNumber] and [message] pre-filled; the tutor reviews and taps Send.
     * wa.me falls back to the browser if WhatsApp isn't installed. Returns false if nothing can open it.
     */
    fun openWhatsApp(context: Context, waNumber: String, message: String): Boolean =
        launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$waNumber?text=${Uri.encode(message)}")))

    /** Opens the dialer with the number filled in (no call permission needed; the tutor taps Call). */
    fun dial(context: Context, number: String): Boolean =
        launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))

    private fun launch(context: Context, intent: Intent): Boolean = try {
        beforeLeavingApp?.invoke()
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
