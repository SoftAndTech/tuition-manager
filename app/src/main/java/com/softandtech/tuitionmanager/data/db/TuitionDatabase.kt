package com.softandtech.tuitionmanager.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.softandtech.tuitionmanager.data.db.dao.StudentDao
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [StudentEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TuitionDatabase : RoomDatabase() {

    abstract fun studentDao(): StudentDao

    companion object {
        const val NAME = "tuition.db"

        /** Opens the database encrypted with SQLCipher using [key]. */
        fun build(context: Context, key: ByteArray, name: String = NAME): TuitionDatabase =
            Room.databaseBuilder(context, TuitionDatabase::class.java, name)
                .openHelperFactory(SupportOpenHelperFactory(key.copyOf()))
                .build()
    }
}
