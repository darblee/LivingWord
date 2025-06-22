package com.darblee.livingword.data

import kotlinx.serialization.encodeToString
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
     * Converts scripture string and translation to ScriptureContent object
     *
     * @param scripture The raw scripture string
     * @param translation The translation version (e.g., "ESV", "NIV")
     * @return ScriptureContent object
     */
    fun createScriptureContent(scripture: String, translation: String): ScriptureContent {
        val verses = parseScriptureToVerses(scripture)
        return ScriptureContent(
            translation = translation,
            verses = verses
        )
    }

    /**
     * Converts ScriptureContent back to the original scripture string format
     *
     * @param scriptureContent The ScriptureContent object
     * @return Formatted scripture string
     */
    fun scriptureContentToString(scriptureContent: ScriptureContent): String {
        return scriptureContent.verses.joinToString(" ") { verse ->
            "[${verse.verseNum}] ${verse.verseString}"
        }
    }

    /**
     * Converts ScriptureContent to JSON string for database storage
     *
     * @param scriptureContent The ScriptureContent object
     * @return JSON string representation
     */
    fun scriptureContentToJson(scriptureContent: ScriptureContent): String {
        return try {
            Json.encodeToString(scriptureContent)
        } catch (e: Exception) {
            // Return empty ScriptureContent JSON if serialization fails
            Json.encodeToString(ScriptureContent(translation = "", verses = emptyList()))
        }
    }
}