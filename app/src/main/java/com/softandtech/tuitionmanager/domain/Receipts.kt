package com.softandtech.tuitionmanager.domain

object Receipts {
    /** "R-0042" — prefix from settings plus the running number, padded to 4 digits. */
    fun number(prefix: String, seq: Int): String = prefix + seq.toString().padStart(4, '0')

    /** Safe file name for sharing, e.g. "Receipt_R-0042.pdf". */
    fun fileName(prefix: String, seq: Int): String =
        "Receipt_" + number(prefix, seq).replace(Regex("[^A-Za-z0-9_-]"), "_") + ".pdf"
}
