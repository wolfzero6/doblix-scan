package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ScanRecord::class,
        OpenPortRecord::class,
        TechRecord::class,
        PathRecord::class,
        VulnRecord::class,
        EmailRecord::class,
        DeviceRecord::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ScanDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao

    companion object {
        @Volatile
        private var INSTANCE: ScanDatabase? = null

        fun getDatabase(context: Context): ScanDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScanDatabase::class.java,
                    "doblix_scanner_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
