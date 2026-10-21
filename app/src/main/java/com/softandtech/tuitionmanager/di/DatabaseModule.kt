package com.softandtech.tuitionmanager.di

import android.content.Context
import com.softandtech.tuitionmanager.data.db.TuitionDatabase
import com.softandtech.tuitionmanager.data.db.dao.StudentDao
import com.softandtech.tuitionmanager.data.security.DatabaseKeyManager
import com.softandtech.tuitionmanager.data.security.KeystoreKeyWrapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabaseKeyManager(@ApplicationContext context: Context): DatabaseKeyManager =
        DatabaseKeyManager(
            keyFile = File(context.noBackupFilesDir, "db.key"),
            wrapper = KeystoreKeyWrapper(),
        )

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyManager: DatabaseKeyManager,
    ): TuitionDatabase {
        val key = keyManager.getOrCreateKey()
        return TuitionDatabase.build(context, key).also { key.fill(0) }
    }

    @Provides
    fun provideStudentDao(db: TuitionDatabase): StudentDao = db.studentDao()
}
