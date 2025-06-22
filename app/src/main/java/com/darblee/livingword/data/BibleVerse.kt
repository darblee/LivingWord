package com.darblee.livingword.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "BibleVerse_Items")
@TypeConverters(Converters::class)
data class BibleVerse(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val book: String,
    val chapter: Int,
    val startVerse: Int,
    val endVerse: Int,
    val aiResponse: String,
    val topics: List<String>,
    val memorizedSuccessCount: Int = 0,
    val memorizedFailedCount: Int = 0,
    val userDirectQuote: String = "",
    val userDirectQuoteScore: Int = 0,
    val userContext: String = "",
    val userContextScore: Int = 0,
    val translation: String = "",
    val favorite: Boolean = false,
    val scriptureJson: ScriptureContent = ScriptureContent(translation = "", verses = emptyList()),
    val dateCreated: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
)

@Serializable
data class Verse(
    @SerialName("verse_num")
    val verseNum: Int,
    @SerialName("verse_string")
    val verseString: String
)

@Serializable
data class ScriptureContent(
    val translation: String,
    val verses: List<Verse>
)

class Converters {
    @TypeConverter
    fun fromList(value: List<String>): String {
        return value.joinToString(",")
    }

    @TypeConverter
    fun toList(value: String): List<String> {
        return value.split(",").map { it.trim() }
    }

    @TypeConverter
    fun fromScriptureContent(value: ScriptureContent): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toScriptureContent(value: String): ScriptureContent {
        return if (value.isEmpty()) {
            ScriptureContent(translation = "", verses = emptyList())
        } else {
            try {
                Json.decodeFromString<ScriptureContent>(value)
            } catch (e: Exception) {
                ScriptureContent(translation = "", verses = emptyList())
            }
        }
    }
}

fun verseReference(verseItem: BibleVerse): String {
    val book = verseItem.book
    val chapter = verseItem.chapter
    val startVerse = verseItem.startVerse
    val endVerse = verseItem.endVerse
    if (startVerse == endVerse) {
        return ("$book $chapter:$startVerse")
    }
    return ("$book $chapter:$startVerse-$endVerse")
}

fun verseReferenceBibleVerseRef(verseItem: BibleVerseRef): String {
    val book = verseItem.book
    val chapter = verseItem.chapter
    val startVerse = verseItem.startVerse
    val endVerse = verseItem.endVerse
    if (startVerse == endVerse) {
        return ("$book $chapter:$startVerse")
    }
    return ("$book $chapter:$startVerse-$endVerse")
}