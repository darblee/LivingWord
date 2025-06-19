package com.darblee.livingword.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.darblee.livingword.Global

@Database(entities = [BibleVerse::class, Topic::class, CrossRefBibleVerseTopics::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bibleVerseDao(): BibleVerseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 1 to version 2
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add the new columns to the BibleVerse_Items table
                database.execSQL("ALTER TABLE BibleVerse_Items ADD COLUMN translation TEXT NOT NULL DEFAULT 'ESV'")
                database.execSQL("ALTER TABLE BibleVerse_Items ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    Global.DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}