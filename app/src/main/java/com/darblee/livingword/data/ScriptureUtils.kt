package com.darblee.livingword.data

import kotlinx.serialization.json.Json

/**
 * Utility class for handling scripture format conversions
 */
object ScriptureUtils {

    /**
     * Parses a scripture string with format like "[1] verse text [2] more verse text"
     * and converts it to a list of Verse objects.
     *
     * @param scripture The raw scripture string to parse
     * @return List of Verse objects
     */
    fun parseScriptureToVerses(scripture: String): List<Verse> {
        val verses = mutableListOf<Verse>()

        // Regex to match [number] followed by text until next [number] or end of string
        val regex = "\\[(\\d+)]\\s*([^\\[]+?)(?=\\s*\\[\\d+]|\\$)".toRegex()

        regex.findAll(scripture).forEach { matchResult ->
            val verseNum = matchResult.groupValues[1].toIntOrNull() ?: 0
            val verseText = matchResult.groupValues[2].trim()
            if (verseText.isNotEmpty()) {
                verses.add(Verse(verseNum = verseNum, verseString = verseText))
            }
        }

        // If no verses were parsed (maybe different format), create a single verse
        if (verses.isEmpty() && scripture.isNotEmpty()) {
            // Try to extract verse number from the beginning if it exists
            val singleVerseRegex = """^(\d+)\.?\s*(.+)""".toRegex()
            val match = singleVerseRegex.find(scripture.trim())

            if (match != null) {
                val verseNum = match.groupValues[1].toIntOrNull() ?: 1
                val verseText = match.groupValues[2].trim()
                verses.add(Verse(verseNum = verseNum, verseString = verseText))
            } else {
                // No verse number found, use the entire text as verse 1
                verses.add(Verse(verseNum = 1, verseString = scripture.trim()))
            }
        }

        return verses
    }

    /**
     * Converts scripture string to a list of verse objects
     *
     * @param scripture The raw scripture string
     * @return List of Verse objects
     */
    fun createVerseList(scripture: String): List<Verse> {
        return parseScriptureToVerses(scripture)
    }

    /**
     * Converts a list of verses back to the original scripture string format
     *
     * @param verses The list of Verse objects
     * @return Formatted scripture string
     */
    fun verseListToString(verses: List<Verse>): String {
        return verses.joinToString(" ") { verse ->
            "[${verse.verseNum}] ${verse.verseString}"
        }
    }

    /**
     * Converts a list of verses to JSON string for database storage
     *
     * @param verses The List of Verse objects
     * @return JSON string representation
     */
    fun verseListToJson(verses: List<Verse>): String {
        return try {
            Json.encodeToString(verses)
        } catch (e: Exception) {
            // Return empty JSON array if serialization fails
            "[]"
        }
    }
}