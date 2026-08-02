package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SavedAudit::class, User::class, LoginInfo::class, PumpInfo::class, RegisteredNozzle::class, MsNozzleReading::class, HsdNozzleReading::class, PhoneAccess::class, Staff::class, TtReceiptEntry::class], version = 13, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedAuditDao(): SavedAuditDao
    abstract fun userDao(): UserDao
    abstract fun loginInfoDao(): LoginInfoDao
    abstract fun pumpInfoDao(): PumpInfoDao
    abstract fun registeredNozzleDao(): RegisteredNozzleDao
    abstract fun msNozzleReadingDao(): MsNozzleReadingDao
    abstract fun hsdNozzleReadingDao(): HsdNozzleReadingDao
    abstract fun phoneAccessDao(): PhoneAccessDao
    abstract fun staffDao(): StaffDao
    abstract fun ttReceiptEntryDao(): TtReceiptEntryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "petrol_pump_audit_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
