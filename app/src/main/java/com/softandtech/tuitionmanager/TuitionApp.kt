package com.softandtech.tuitionmanager

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TuitionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // SQLCipher's native library must be loaded before the database is opened.
        System.loadLibrary("sqlcipher")
    }
}
