package com.darblee.livingword.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.darblee.livingword.Global
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Database(entities = [BibleVerse::class, Topic::class, CrossRefBibleVerseTopics::class], version = 3, exportSchema = false)
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

        // Migration from version 2 to version 3
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add the new scriptureJson column
                database.execSQL("ALTER TABLE BibleVerse_Items ADD COLUMN scriptureJson TEXT NOT NULL DEFAULT ''")

                // Update existing records to populate scriptureJson based on existing scripture and translation
                val cursor = database.query("SELECT id, scripture, translation FROM BibleVerse_Items")

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val scripture = cursor.getString(1) ?: ""
                    val translation = cursor.getString(2) ?: ""

                    // Parse the scripture string to create ScriptureContent
                    val verses = parseScriptureString(scripture)
                    val scriptureContent = ScriptureContent(
                        translation = translation,
                        verses = verses
                    )

                    // Convert to JSON string
                    val jsonString = try {
                        Json.encodeToString(scriptureContent)
                    } catch (e: Exception) {
                        // Fallback to empty JSON if parsing fails
                        Json.encodeToString(ScriptureContent(translation = translation, verses = emptyList()))
                    }

                    // Update the record
                    database.execSQL(
                        "UPDATE BibleVerse_Items SET scriptureJson = ? WHERE id = ?",
                        arrayOf(jsonString, id)
                    )
                }
                cursor.close()
            }

            /**
             * Helper function to parse scripture string during migration
             * Parses format like "[1] verse text [2] more verse text"
             */
            private fun parseScriptureString(scripture: String): List<Verse> {
                val verses = mutableListOf<Verse>()
                val regex = "\\[(\\d+)\\]\\s*([^\\[]+?)(?=\\s*\\[\\d+\\]|\\$)".toRegex()


                regex.findAll(scripture).forEach { matchResult ->
                    val verseNum = matchResult.groupValues[1].toIntOrNull() ?: 0
                    val verseText = matchResult.groupValues[2].trim()
                    if (verseText.isNotEmpty()) {
                        verses.add(Verse(verseNum = verseNum, verseString = verseText))
                    }
                }

                // If no verses were parsed (maybe different format), create a single verse
                if (verses.isEmpty() && scripture.isNotEmpty()) {
                    verses.add(Verse(verseNum = 1, verseString = scripture))
                }

                return verses
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    Global.DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}