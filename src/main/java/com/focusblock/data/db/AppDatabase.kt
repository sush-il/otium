package com.focusblock.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProfileEntity::class, ProfileAppEntity::class, ScheduleEntity::class, AppLimitEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun appLimitDao(): AppLimitDao

    companion object {
        // v2 adds the hourly-limit, launch-limit and block-until columns to existing profiles
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN hourlyLimitMinutes INTEGER")
                db.execSQL("ALTER TABLE profiles ADD COLUMN launchLimit INTEGER")
                db.execSQL("ALTER TABLE profiles ADD COLUMN blockUntil INTEGER")
            }
        }

        // v3 adds the pause column
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN pausedUntil INTEGER")
            }
        }

        // v4 adds the whole-phone flag
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN wholePhone INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v5 adds the app_limits table (stackable per-app rules)
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS app_limits (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "packageName TEXT NOT NULL, type TEXT NOT NULL, " +
                        "dailyLimitMinutes INTEGER, hourlyLimitMinutes INTEGER, launchLimit INTEGER, " +
                        "blockUntil INTEGER, daysMask INTEGER, startMinute INTEGER, endMinute INTEGER)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_app_limits_packageName ON app_limits (packageName)")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "focusblock.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
