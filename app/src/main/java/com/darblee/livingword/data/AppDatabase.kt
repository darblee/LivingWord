package com.darblee.livingword.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.darblee.livingword.Global

@Database(entities = [BibleVerse::class, Topic::class, CrossRefBibleVerseTopics::class], version = 3, exportSchema = false)
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Remove unused columns: userDirectQuoteScore and aiDirectQuoteExplanationText
                // SQLite doesn't support DROP COLUMN directly, so we need to recreate the table
                
                // 1. Create new table with desired schema
                database.execSQL("""
                    CREATE TABLE BibleVerse_Items_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        book TEXT NOT NULL,
                        chapter INTEGER NOT NULL,
                        startVerse INTEGER NOT NULL,
                        endVerse INTEGER NOT NULL,
                        aiTakeAwayResponse TEXT NOT NULL,
                        topics TEXT NOT NULL,
                        memorizedSuccessCount INTEGER NOT NULL DEFAULT 0,
                        memorizedFailedCount INTEGER NOT NULL DEFAULT 0,
                        userDirectQuote TEXT NOT NULL DEFAULT '',
                        userContext TEXT NOT NULL DEFAULT '',
                        userContextScore INTEGER NOT NULL DEFAULT 0,
                        aiContextExplanationText TEXT NOT NULL DEFAULT '',
                        applicationFeedback TEXT NOT NULL DEFAULT '',
                        translation TEXT NOT NULL DEFAULT '',
                        favorite INTEGER NOT NULL DEFAULT 0,
                        scriptureVerses TEXT NOT NULL DEFAULT '',
                        dateCreated INTEGER NOT NULL DEFAULT 0,
                        lastModified INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                
                // 2. Copy data from old table to new table (excluding removed columns)
                database.execSQL("""
                    INSERT INTO BibleVerse_Items_new (
                        id, book, chapter, startVerse, endVerse, aiTakeAwayResponse, topics,
                        memorizedSuccessCount, memorizedFailedCount, userDirectQuote,
                        userContext, userContextScore, aiContextExplanationText,
                        applicationFeedback, translation, favorite, scriptureVerses,
                        dateCreated, lastModified
                    )
                    SELECT 
                        id, book, chapter, startVerse, endVerse, aiTakeAwayResponse, topics,
                        memorizedSuccessCount, memorizedFailedCount, userDirectQuote,
                        userContext, userContextScore, aiContextExplanationText,
                        applicationFeedback, translation, favorite, scriptureVerses,
                        dateCreated, lastModified
                    FROM BibleVerse_Items
                """.trimIndent())
                
                // 3. Drop old table
                database.execSQL("DROP TABLE BibleVerse_Items")
                
                // 4. Rename new table to original name
                database.execSQL("ALTER TABLE BibleVerse_Items_new RENAME TO BibleVerse_Items")
            }
        }
    }
}