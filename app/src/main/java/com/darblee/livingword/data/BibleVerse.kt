package com.darblee.livingword.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Entity(tableName = "BibleVerse_Items")
@TypeConverters(Converters::class)
data class BibleVerse(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val book: String,
    val chapter: Int,
    val startVerse: Int,
    val endVerse: Int,
    val scripture: String,
    val aiResponse: String,
    val topics: List<String>,
    val memorizedSuccessCount: Int = 0,
    val memorizedFailedCount: Int = 0,
    val userDirectQuote: String = "",
    val userDirectQuoteScore: Int = 0,
    val userContext: String = "",
    val userContextScore: Int = 0,
    val dateCreated: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
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
}

fun verseReference(verseItem: BibleVerse): String
{
    val book = verseItem.book
    val chapter = verseItem.chapter
    val startVerse = verseItem.startVerse
    val endVerse = verseItem.endVerse
    if (startVerse == endVerse) {
        return ("$book $chapter:$startVerse")
    }
    return ("$book $chapter:$startVerse-$endVerse")
}

fun verseReferenceT(verseItem: BibleVerseRef): String
{
    val book = verseItem.book
    val chapter = verseItem.chapter
    val startVerse = verseItem.startVerse
    val endVerse = verseItem.endVerse
    if (startVerse == endVerse) {
        return ("$book $chapter:$startVerse")
    }
    return ("$book $chapter:$startVerse-$endVerse")
}