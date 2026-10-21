package com.softandtech.tuitionmanager.data.security

/** Encrypts small secrets (like the database key) before they are written to disk. */
interface KeyWrapper {
    fun wrap(plain: ByteArray): ByteArray
    fun unwrap(wrapped: ByteArray): ByteArray
}
