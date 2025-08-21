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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    Global.DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE BibleVerse_Items ADD COLUMN aiDirectQuoteExplanationText TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE BibleVerse_Items ADD COLUMN aiContextExplanationText TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE BibleVerse_Items ADD COLUMN applicationFeedback TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}