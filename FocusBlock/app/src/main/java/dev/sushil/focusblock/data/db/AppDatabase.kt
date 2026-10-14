package dev.sushil.focusblock.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProfileEntity::class, ProfileAppEntity::class, ScheduleEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    companion object {
        // v2 adds the hourly-limit, launch-limit and block-until columns to existing profiles
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN hourlyLimitMinutes INTEGER")
                db.execSQL("ALTER TABLE profiles ADD COLUMN launchLimit INTEGER")
                db.execSQL("ALTER TABLE profiles ADD COLUMN blockUntil INTEGER")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "focusblock.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
