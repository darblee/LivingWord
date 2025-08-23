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
    val aiTakeAwayResponse: String,
    val topics: List<String>,
    val memorizedSuccessCount: Int = 0,
    val memorizedFailedCount: Int = 0,
    val userDirectQuote: String = "",
    val userDirectQuoteScore: Int = 0,
    val userContext: String = "",
    val userContextScore: Int = 0,
    // Add null-safe getters for migrated fields
    val aiDirectQuoteExplanationText: String = "",
    val aiContextExplanationText: String = "",
    val applicationFeedback: String = "",
    val translation: String = "",
    val favorite: Boolean = false,
    val scriptureVerses: List<Verse> = emptyList(),
    val dateCreated: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
) {
    // Provide safe accessors for AI feedback fields to handle migration edge cases
    fun getSafeAIDirectQuoteExplanation(): String {
        return aiDirectQuoteExplanationText.takeIf { it.isNotBlank() } ?: ""
    }

    fun getSafeAIContextExplanation(): String {
        return aiContextExplanationText.takeIf { it.isNotBlank() } ?: ""
    }

    fun getSafeApplicationFeedback(): String {
        return applicationFeedback.takeIf { it.isNotBlank() } ?: ""
    }

    // Helper method to check if this verse has cached AI feedback
    fun hasCachedAIFeedback(): Boolean {
        return (getSafeAIContextExplanation().isNotEmpty() ||
                getSafeApplicationFeedback().isNotEmpty()) &&
                (userContextScore > 0)
        // Note: DirectQuoteScore is always 0 and DirectQuoteExplanation is always empty for token optimization
    }

    // Helper method to check if input matches cached evaluation
    fun matchesCachedInput(directQuote: String, userApplication: String): Boolean {
        return userDirectQuote.trim() == directQuote.trim() &&
                userContext.trim() == userApplication.trim()
    }
}

@Serializable
data class Verse(
    @SerialName("verse_num")
    val verseNum: Int,
    @SerialName("verse_string")
    val verseString: String
)

class Converters {
    @TypeConverter
    fun fromList(value: List<String>): String {
        return value.joinToString(",")
    }

    @TypeConverter
    fun toList(value: String): List<String> {
        return if (value.isBlank()) {
            emptyList()
        } else {
            value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    @TypeConverter
    fun fromVerseList(value: List<Verse>): String {
        return try {
            Json.encodeToString(value)
        } catch (e: Exception) {
            "[]" // Return empty array JSON if serialization fails
        }
    }

    @TypeConverter
    fun toVerseList(value: String): List<Verse> {
        return if (value.isEmpty() || value.isBlank()) {
            emptyList()
        } else {
            try {
                Json.decodeFromString<List<Verse>>(value)
            } catch (e: Exception) {
                emptyList()
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