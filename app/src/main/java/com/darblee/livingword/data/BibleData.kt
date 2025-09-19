package com.darblee.livingword.data


import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Represents the selected Bible verse components.
 * This is used both as a result type and potentially part of a screen's state.
 * Marked as Serializable to be passed via SavedStateHandle.
 */
@Serializable
data class BibleVerseRef(
    val book: String,
    val chapter: Int,
    val startVerse: Int,
    val endVerse: Int
)

/**
 * Represents the detailed information for a single book, loaded from JSON.
 * @param abbreviation The common abbreviation for the book (e.g., "Gen").
 * @param chapters A map where the key is the chapter number (as Int) and the value is the verse count (Int).
 */
@Serializable
data class BookDetails(
    val abbreviation: String,
    val chapters: Map<Int, Int> // Changed key type from String to Int
)

/**
 * Represents basic info about a book for display purposes.
 * @param fullName The full name of the book (e.g., "Genesis").
 * @param abbreviation The common abbreviation for the book (e.g., "Gen").
 */
data class BookInfo(
    val fullName: String,
    val abbreviation: String
)

/**
 * Provides static data for Bible books, loading details (abbreviations, chapters, verses) from assets.
 * IMPORTANT: Call `BibleData.init(context)` once during app startup.
 */
object BibleData {

    // Static lists defining the order and classification of books.
    // These are now primarily used for grouping/ordering the data loaded from JSON.
    private val oldTestamentBookNames = setOf( // Use Set for efficient lookup
        "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy",
        "Joshua", "Judges", "Ruth", "1 Samuel", "2 Samuel",
        "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra",
        "Nehemiah", "Esther", "Job", "Psalms", "Proverbs",
        "Ecclesiastes", "Song of Solomon", "Isaiah", "Jeremiah", "Lamentations",
        "Ezekiel", "Daniel", "Hosea", "Joel", "Amos",
        "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk",
        "Zephaniah", "Haggai", "Zechariah", "Malachi"
    )

    private val newTestamentBookNames = setOf( // Use Set for efficient lookup
        "Matthew", "Mark", "Luke", "John", "Acts",
        "Romans", "1 Corinthians", "2 Corinthians", "Galatians", "Ephesians",
        "Philippians", "Colossians", "1 Thessalonians", "2 Thessalonians", "1 Timothy",
        "2 Timothy", "Titus", "Philemon", "Hebrews", "James",
        "1 Peter", "2 Peter", "1 John", "2 John", "3 John",
        "Jude", "Revelation"
    )

    /**
     * Determines the color associated with an Old Testament book based on its index.
     * The colors are used to visually group books by their traditional biblical sections.
     * - Pentateuch (index 0-4): Yellow
     * - History (index 5-16): Green
     * - Wisdom/Poetry (index 17-21): Purple (0xFF800080)
     * - Major Prophets (index 22-26): Red
     * - Minor Prophets (index > 26): Blue
     *
     * @param index The zero-based index of the Old Testament book.
     * @return The [Color] corresponding to the book's section.
     */
    fun oldTestamentBookColor(index: Int): Color {
        return when (index) {
            in 0..4 -> Color.Yellow               // Pentateuch
            in 5..16 -> Color.Green               // History
            in 17..21 -> Color(0xFF800080) // Wisdom/Poetry
            in 22..26 -> Color.Red                // Major Prophets
            else -> Color.Blue                          // Minor Prophets
        }
    }


    /**
     * Provides a color for New Testament books based on their index (position in a list).
     * This is typically used for UI theming or visual differentiation.
     * The color coding is:
     * - Gospels (index 0-3): Yellow
     * - Acts (History, index 4): Green
     * - Pauline Epistles (General, index 5-13): Purple (0xFF800080)
     * - Pauline Epistles (Pastoral, index 14-17): Orange (0xFFFFA500)
     * - General Epistles (index 18-25): Red
     * - Revelation (Apocalyptic, other indices): Blue
     *
     * @param index The 0-based index of the New Testament book.
     * @return A [Color] object representing the color for that book category.
     */
    fun newTestamentBookColor(index: Int): Color {
        return when (index) {
            in 0..3 -> Color.Yellow               // Gospels
            4 -> Color.Green                            // Acts (History)
            in 5..13 -> Color(0xFF800080)  // Pauline Epistles (General)
            in 14..17 -> Color(0xFFFFA500) // Pauline Epistles (Pastoral)
            in 18..25 -> Color.Red                // General Epistles
            else -> Color.Blue                          // Revelation (Apocalyptic)
        }
    }



    // Holds the fully parsed data loaded from the JSON asset file.
    // Structure: Map<FullNameString, BookDetails>
    private var bookDetailsMap: Map<String, BookDetails>? = null
    private const val BIBLE_DATA_FILENAME = "bible_verses.json" // Renamed constant
    private const val TAG = "BibleData" // For logging

    // Configure Json parser once
    // IMPORTANT: Allow parsing non-standard JSON map keys (integers without quotes)
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true // Allow non-string map keys if JSON parser supports it
        // Note: Standard JSON requires string keys. If isLenient doesn't work,
        // you might need a custom serializer or keep keys as strings in JSON.
    }


    /**
     * Initializes the BibleData object by loading book details from the JSON asset file.
     * This MUST be called once during application startup.
     *
     * @param context An application or activity context to access assets.
     */
    fun init(context: Context) {
        if (bookDetailsMap != null) {
            Log.d(TAG, "BibleData already initialized.")
            return // Avoid re-initialization
        }
        try {
            Log.d(TAG, "Initializing BibleData - loading $BIBLE_DATA_FILENAME...")
            // Read the JSON file from assets
            val jsonString = context.assets.open(BIBLE_DATA_FILENAME).bufferedReader().use { it.readText() }
            // Parse the entire JSON string directly into the target Map type
            bookDetailsMap = jsonParser.decodeFromString<Map<String, BookDetails>>(jsonString)
            Log.d(TAG, "BibleData initialized successfully. Loaded details for ${bookDetailsMap?.size} books.")
        } catch (e: IOException) {
            Log.e(TAG, "Error reading $BIBLE_DATA_FILENAME from assets", e)
            bookDetailsMap = emptyMap() // Indicate failure
        } catch (e: SerializationException) {
            Log.e(TAG, "Error parsing $BIBLE_DATA_FILENAME (Serialization)", e)
            bookDetailsMap = emptyMap()
        } catch (e: Exception) { // Catch other potential errors
            Log.e(TAG, "Error initializing BibleData: ${e.message}", e)
            bookDetailsMap = emptyMap()
        }
    }

    // Helper function to ensure initialization before accessing data
    private fun getInitializedData(): Map<String, BookDetails> {
        return bookDetailsMap
            ?: throw IllegalStateException("BibleData.init(context) must be called before accessing data.")
    }

    /**
     * Returns a list of BookInfo objects for all Old Testament books,
     * ordered according to the predefined list.
     */
    fun getOldTestamentBooks(): List<BookInfo> {
        val data = getInitializedData()
        // Preserve the order defined in the static list
        return oldTestamentBookNames.mapNotNull { bookName ->
            data[bookName]?.let { details -> BookInfo(bookName, details.abbreviation) }
        }
    }

    /**
     * Returns a list of BookInfo objects for all New Testament books,
     * ordered according to the predefined list.
     */
    fun getNewTestamentBooks(): List<BookInfo> {
        val data = getInitializedData()
        // Preserve the order defined in the static list
        return newTestamentBookNames.mapNotNull { bookName ->
            data[bookName]?.let { details -> BookInfo(bookName, details.abbreviation) }
        }
    }


    /**
     * Returns the list of chapter numbers for a given book name by consulting
     * the keys within the loaded JSON data.
     *
     * @param book The full name of the book.
     * @return A sorted list of integers representing chapter numbers, or an empty list if not found.
     */
    fun getChaptersForBook(book: String): List<Int> {
        val details = getInitializedData()[book]
        if (details == null) {
            Log.w(TAG, "Chapter data not found for book: $book")
            return emptyList()
        }

        // Get the keys (chapter numbers as Int) and sort them
        return details.chapters.keys.sorted()
    }

    /**
     * Returns the list of verse numbers for a given book and chapter, starting from a specific verse.
     * Consults the data loaded from the assets JSON file.
     *
     * Throws IllegalStateException if `init(context)` has not been called successfully first.
     *
     * @param book The full name of the book.
     * @param chapter The chapter number.
     * @param startVerse The verse number to start the list from (defaults to 1).
     * @return A list of integers representing verse numbers (e.g., [startVerse, startVerse+1, ..., N]),
     * or an empty list if not found or if startVerse is invalid.
     */
    fun getVersesForBookChapter(book: String, chapter: Int, startVerse: Int = 1): List<Int> {
        val details = getInitializedData()[book]
        if (details == null) {
            Log.w(TAG, "Verse data not found for book: $book")
            return emptyList()
        }

        // Look up the total verse count using the chapter number (as Int key)
        val count = details.chapters[chapter] // Use Int key directly

        // Validate the verse count and the startVerse
        if (count == null || count <= 0) {
            if (count == null) {
                Log.w(TAG, "Verse count not found for: $book $chapter")
            } else {
                Log.w(TAG, "Verse count is zero or negative for: $book $chapter")
            }
            return emptyList()
        }

        // Validate startVerse
        if (startVerse <= 0 || startVerse > count) {
            Log.w(TAG, "Invalid startVerse ($startVerse) for: $book $chapter (Total verses: $count)")
            return emptyList() // Return empty list if startVerse is out of bounds
        }

        // Generate the list starting from startVerse up to the total count
        return (startVerse..count).toList()
    }

    // Helper function to clean scripture text for TTS by removing bracketed verse numbers
    fun String.removePassageRef(): String {
        return this.replace(Regex("""\[\d+]\s?"""), "") // Removes "[N]" and an optional space after it
    }

    /**
     * Helper function to create a VersePicker navigation route.
     * This starts the VersePicker navigation flow that guides the user through
     * book -> chapter -> start verse -> end verse selection.
     *
     * Note for return screen:
     * Use LaunchedEffect to observe the back stack size. This is used when selecting the verse, which
     * involve navigating couple of screens to select the verse. WHen the final ending verse is selected.
     * saveStateHandle will have verse result and user will navigate back to this screen.
     *
     * @param route The screen to return to when user cancels or completes selection
     * @return The Screen route to navigate to for starting verse picking
     */
    fun createVersePickerRoute(route: com.darblee.livingword.Screen): com.darblee.livingword.Screen {
        val screenId = when (route) {
            is com.darblee.livingword.Screen.FlashCardScreen -> "FlashCardScreen"
            is com.darblee.livingword.Screen.AllVersesScreen -> "AllVersesScreen"
            is com.darblee.livingword.Screen.TopicScreen -> "TopicScreen"
            else -> "FlashCardScreen" // Default fallback
        }
        return com.darblee.livingword.Screen.VersePickerBookScreen(screenId)
    }
}
