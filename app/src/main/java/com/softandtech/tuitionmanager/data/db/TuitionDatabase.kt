package com.softandtech.tuitionmanager.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.softandtech.tuitionmanager.data.db.dao.AttendanceDao
import com.softandtech.tuitionmanager.data.db.dao.BatchDao
import com.softandtech.tuitionmanager.data.db.dao.EnrollmentDao
import com.softandtech.tuitionmanager.data.db.dao.ExpenseDao
import com.softandtech.tuitionmanager.data.db.dao.FeeDueDao
import com.softandtech.tuitionmanager.data.db.dao.PaymentDao
import com.softandtech.tuitionmanager.data.db.dao.StudentDao
import com.softandtech.tuitionmanager.data.db.entity.AttendanceEntity
import com.softandtech.tuitionmanager.data.db.entity.BatchEntity
import com.softandtech.tuitionmanager.data.db.entity.EnrollmentEntity
import com.softandtech.tuitionmanager.data.db.entity.ExpenseEntity
import com.softandtech.tuitionmanager.data.db.entity.FeeDueEntity
import com.softandtech.tuitionmanager.data.db.entity.PaymentAllocationEntity
import com.softandtech.tuitionmanager.data.db.entity.PaymentEntity
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        StudentEntity::class,
        BatchEntity::class,
        EnrollmentEntity::class,
        FeeDueEntity::class,
        PaymentEntity::class,
        PaymentAllocationEntity::class,
        AttendanceEntity::class,
        ExpenseEntity::class,
    ],
    version = TuitionDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TuitionDatabase : RoomDatabase() {

    abstract fun studentDao(): StudentDao
    abstract fun batchDao(): BatchDao
    abstract fun enrollmentDao(): EnrollmentDao
    abstract fun feeDueDao(): FeeDueDao
    abstract fun paymentDao(): PaymentDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        const val NAME = "tuition.db"

        /** Schema version; also stamped into backups so newer backups aren't restored into older apps. */
        const val VERSION = 1

        /** Opens the database encrypted with SQLCipher using [key]. */
        fun build(context: Context, key: ByteArray, name: String = NAME): TuitionDatabase =
            Room.databaseBuilder(context, TuitionDatabase::class.java, name)
                .openHelperFactory(SupportOpenHelperFactory(key.copyOf()))
                .build()
    }
}
