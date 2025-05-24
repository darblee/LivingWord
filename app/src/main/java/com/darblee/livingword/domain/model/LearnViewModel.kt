package com.darblee.livingword.domain.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darblee.livingword.BibleVerseT
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import com.darblee.livingword.BuildConfig // Assuming BuildConfig is correctly configured
import com.darblee.livingword.SnackBarController


/**
 * Represents a single item for learning, combining scripture, AI insights, and topics.
 * This structure will be used when saving data to the database.
 */
data class ContentItem(
    val book: String,
    val chapter: String,
    val startVerse: Int,
    val endVerse: Int,
    val scripture: String,
    val aiResponse: String,
    val topics: List<String>
)

/**
 * Represents the JSON structure expected from the ESV API.
 */
@Serializable
private data class EsvApiResponse(
    val query: String? = null,
    val passages: List<String>? = null,
)

/**
 * ViewModel for the Learn Screen, responsible for managing state and fetching data for a
 * single verse display.
 */
class LearnViewModel() : ViewModel() {

    /**
     * Represents the UI state for the LearnScreen.
     */
    data class LearnScreenState(
        // State related to topic selection for the *current* item
        val selectedTopics: List<String> = emptyList(),
        val isTopicContentLoading: Boolean = false, // Loading state for topic-based content

        // State for the currently displayed single verse
        val selectedVerse: BibleVerseT? = null,
        val scriptureText: String = "",
        val aiResponseText: String = "",
        val isScriptureLoading: Boolean = false,
        val aiResponseLoading: Boolean = false,
        val scriptureError: String? = null,
        val aiResponseError: String? = null,
        val generalError: String? = null, // For other errors like Gemini init
        val isContentSaved: Boolean = false, // Track if current content has been saved
        val newlySavedVerseId: Long? = null,
        val isLoading: Boolean = false
    )

    // Private mutable state flow
    private val _state = MutableStateFlow(LearnScreenState())
    // Public immutable state flow exposed to the UI
    val state: StateFlow<LearnScreenState> = _state.asStateFlow()

    // Keep track of the current fetching job to allow cancellation
    private var fetchDataJob: Job? = null

    // --- API Keys and Configuration ---
    // Retrieve API keys from BuildConfig (ensure these are properly configured)
    private val esvApiKey = BuildConfig.ESV_BIBLE_API_KEY
    private val geminiApiKey = BuildConfig.GEMINI_API_KEY

    // Configure Json parser (Consider using dependency injection for this)
    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    // Initialize Gemini Model (Consider using dependency injection)
    private val generativeModel: GenerativeModel? = initializeGeminiModel()

    /**
     * Initializes the Gemini GenerativeModel, handling potential errors.
     * Returns the initialized model or null if initialization fails or key is missing.
     */
    private fun initializeGeminiModel(): GenerativeModel? {
        return try {
            if (geminiApiKey.isNotBlank()) {
                GenerativeModel(
                    modelName = "gemini-1.5-flash", // Or your desired model
                    apiKey = geminiApiKey,
                    generationConfig = generationConfig {
                        temperature = 0.7f // Adjust creativity as needed
                    }
                )
            } else {
                Log.w("LearnViewModel", "Gemini API Key is missing or invalid.")
                // Update state immediately with the error if key is missing
                _state.update { it.copy(aiResponseError = "Gemini API Key missing. Cannot get take-away.") }
                null
            }
        } catch (e: Exception) {
            Log.e("LearnViewModel", "Error initializing GenerativeModel", e)
            // Update state immediately with the initialization error
            _state.update { it.copy(generalError = "Failed to initialize AI Model.") }
            null
        }
    }

    /**
     * Updates the selected topics in the state.
     * Placeholder for future logic to fetch content based on topics.
     *
     * @param selectedTopics List of topic names selected by the user.
     */
    fun updateSelectedTopics(selectedTopics: List<String>) {

        selectedTopics.forEach {
            Log.i("LearnViewModel", "Topic: $it")
        }

        _state.update { currentState ->
            currentState.copy(
                selectedTopics = selectedTopics,
                // Optionally reset verse data when topics change?
                // selectedVerse = null, scriptureText = "", keyTakeAwayText = "", ...
                // isTopicContentLoading = true // Start loading topic content if implemented
                isContentSaved = false, // Reset saved state when topics change
            )
        }
        // TODO: Implement fetching content based on selected topics if needed
        // fetchContentForTopics(selectedTopics)
    }

    // Mark content as saved to the database
    // This function will be called from BibleVerseViewModel after successful save,
    // It also start to display content is save message
    fun contentSavedSuccessfully(newVerseId: Long) {
        _state.update { it.copy(
            selectedTopics = emptyList(),

            // Clear other relevant fields after successful save if needed
            selectedVerse = null,
            scriptureText = "",
            aiResponseText = "",

            isContentSaved = true,
            newlySavedVerseId = newVerseId, // Store the ID
            isLoading = false, // Stop any loading indicators

        ) }
        viewModelScope.launch {
            SnackBarController.showMessage("Verse is saved")
        }
    }

    /**
     * Clears the currently displayed verse, scripture, take-away, and related errors/loading states.
     * Preserves errors related to API key or model initialization.
     */
    fun clearVerseData() {
        fetchDataJob?.cancel() // Cancel any ongoing fetch for the previous verse
        _state.update { currentState ->
            currentState.copy(
                selectedVerse = null,
                scriptureText = "",
                aiResponseText = "",
                selectedTopics = emptyList(), // Also clear topics when clearing verse
                isScriptureLoading = false,
                aiResponseLoading = false,
                scriptureError = null,
                // Preserve API key/init error, clear other take-away errors
                aiResponseError = currentState.aiResponseError?.takeIf { it.contains("API Key") || it.contains("initialize") },
                generalError = currentState.generalError?.takeIf { it.contains("AI Model") }, // Preserve init error
                isContentSaved = false, // Reset saved state
                newlySavedVerseId = null,
            )
        }
    }

    /**
     * Sets the selected verse and triggers fetching scripture and AI response data.
     * Cancels any previous fetch operation.
     *
     * @param verse The BibleVerse selected by the user, or null to clear.
     */
    fun setSelectedVerseAndFetchData(verse: BibleVerseT?) {
        if (verse == null) {
            clearVerseData()
            return
        }

        // Avoid re-fetching if the exact same verse is already selected and loaded without errors
        val currentState = _state.value
        if (verse == currentState.selectedVerse &&
            currentState.scriptureText.isNotEmpty() && currentState.scriptureError == null &&
            (currentState.aiResponseText.isNotEmpty() || currentState.aiResponseError != null || generativeModel == null) // Also check take-away state
        ) {
            Log.d("LearnViewModel", "Verse $verse already loaded. Skipping fetch.")
            // If take-away had an error before (and model exists), maybe retry?
            if (generativeModel != null && currentState.aiResponseText.isEmpty() && currentState.aiResponseError != null && !currentState.aiResponseLoading) {
                Log.d("LearnViewModel", "Retrying AI response fetch for $verse.")
                fetchKeyTakeAwayOnly(verse)
            }
            return
        }

        // Cancel any previous job before starting a new one
        fetchDataJob?.cancel()

        // Update state to indicate loading
        _state.update {
            it.copy(
                selectedVerse = verse,
                isScriptureLoading = true,
                // Start AI response loading only if the model was initialized successfully
                aiResponseLoading = generativeModel != null,

                scriptureText = "Loading Scripture...", // Placeholder text
                // Set AI Response placeholder or existing init/key error
                aiResponseText = if (generativeModel != null) "Getting AI Response ..." else (it.aiResponseError ?: ""),
                scriptureError = null, // Clear previous errors
                aiResponseError = if (generativeModel != null) null else it.aiResponseError, // Clear previous errors only if model exists
                generalError = null, // Clear previous general errors
                isContentSaved = false, // Reset saved state for new verse
            )
        }

        // Launch a new coroutine for fetching data
        fetchDataJob = viewModelScope.launch {
            // Fetch scripture first
            val scriptureResult = fetchScriptureInternal(verse)

            // Update state with scripture result (success or error)
            _state.update {
                it.copy(
                    isScriptureLoading = false,
                    scriptureText = if (scriptureResult.isSuccess) scriptureResult.getOrDefault("") else "",
                    scriptureError = if (scriptureResult.isFailure) scriptureResult.exceptionOrNull()?.message else null
                )
            }

            // If scripture fetch was successful and the AI model is available, fetch the take-away
            if (scriptureResult.isSuccess && generativeModel != null) {
                val verseRef = "${verse.book} ${verse.chapter}:${verse.startVerse}-${verse.endVerse}"
                val takeAwayResult = getKeyTakeAwayInternal(generativeModel, verseRef)

                // Update state with take-away result (success or error)
                _state.update {
                    it.copy(
                        aiResponseLoading = false,
                        aiResponseText = if (takeAwayResult.isSuccess) takeAwayResult.getOrDefault("") else "",
                        aiResponseError = if (takeAwayResult.isFailure) takeAwayResult.exceptionOrNull()?.message else null
                    )
                }
            } else if (generativeModel != null) {
                // Scripture fetch failed, but model exists. Update take-away loading state and potentially set an error.
                _state.update {
                    it.copy(
                        aiResponseLoading = false,
                        // Optionally clear take-away text or set a specific error
                        // keyTakeAwayText = "",
                        aiResponseError = it.aiResponseError ?: "Cannot fetch take-away due to scripture error."
                    )
                }
            } else {
                // Model doesn't exist, ensure loading is false
                _state.update { it.copy(aiResponseLoading = false) }
            }
        }
    }

    /**
     * Fetches only the key take-away for the given verse.
     * Used internally for retrying if the initial attempt failed.
     */
    private fun fetchKeyTakeAwayOnly(verse: BibleVerseT) {
        if (generativeModel == null) {
            // If the model is null (likely due to init/key error), don't attempt to fetch
            Log.w("LearnViewModel", "Skipping take-away retry as GenerativeModel is null.")
            _state.update { it.copy(aiResponseLoading = false) } // Ensure loading is off
            return
        }

        // Update state to show loading for take-away
        _state.update { it.copy(aiResponseLoading = true, aiResponseError = null, aiResponseText = "Getting key take-away...") }

        val verseRef = "${verse.book} ${verse.chapter}:${verse.startVerse}-${verse.endVerse}"

        // Launch coroutine specifically for take-away fetch
        viewModelScope.launch {
            val takeAwayResult = getKeyTakeAwayInternal(generativeModel, verseRef)
            _state.update {
                it.copy(
                    aiResponseLoading = false,
                    aiResponseText = if (takeAwayResult.isSuccess) takeAwayResult.getOrDefault("") else "",
                    aiResponseError = if (takeAwayResult.isFailure) takeAwayResult.exceptionOrNull()?.message else null
                )
            }
        }
    }

    // --- Internal API Fetching Functions ---

    /**
     * Fetches scripture from the ESV API. Runs on the IO dispatcher.
     * Returns a Result object containing the scripture text or an exception.
     */
    private suspend fun fetchScriptureInternal(verse: BibleVerseT): Result<String> {
        // Ensure API key is present
        if (esvApiKey.isBlank()) {
            Log.e("LearnViewModel", "ESV API Key is missing.")
            return Result.failure(Exception("Error: ESV API Key is missing."))
        }

        return withContext(Dispatchers.IO) {
            try {
                // Construct query and URL
                val encodedBook = URLEncoder.encode(verse.book, "UTF-8")
                val passage = "$encodedBook ${verse.chapter}:${verse.startVerse}-${verse.endVerse}"
                val urlString = "https://api.esv.org/v3/passage/text/?q=$passage&include-passage-references=false&include-verse-numbers=false&include-footnotes=false&include-headings=false"
                val url = URL(urlString)

                // Open connection and make request
                val connection = url.openConnection()
                connection.setRequestProperty("Authorization", "Token $esvApiKey")
                // Set reasonable timeouts
                connection.connectTimeout = 10000 // 10 seconds
                connection.readTimeout = 10000 // 10 seconds
                connection.connect()

                // Read and parse response
                val responseText = connection.getInputStream().bufferedReader().use { it.readText() }
                Log.d("LearnViewModel", "Raw ESV API Response: $responseText")
                val apiResponse = jsonParser.decodeFromString<EsvApiResponse>(responseText)

                // Extract passage, trim, and remove suffix
                val passageText = apiResponse.passages?.joinToString("\n")?.trim()
                    ?: return@withContext Result.failure(Exception("Error: Passage not found in ESV response."))

                val cleanedText = if (passageText.endsWith(" (ESV)")) {
                    passageText.removeSuffix(" (ESV)").trim()
                } else {
                    passageText
                }
                Result.success(cleanedText)

            } catch (e: IOException) {
                Log.e("LearnViewModel", "Network error fetching scripture: ${e.message}", e)
                Result.failure(Exception("Error: Network request failed fetching scripture.", e))
            } catch (e: SerializationException) {
                Log.e("LearnViewModel", "ESV JSON Parsing Error: ${e.message}", e)
                Result.failure(Exception("Error: Could not parse scripture response.", e))
            } catch (e: Exception) {
                Log.e("LearnViewModel", "Unexpected error fetching scripture: ${e.message}", e)
                Result.failure(Exception("Error: An unexpected error occurred fetching scripture.", e))
            }
        }
    }

    /**
     * Calls the Gemini API to get a key take-away.
     * Returns a Result object containing the take-away text or an exception.
     */
    private suspend fun getKeyTakeAwayInternal(model: GenerativeModel, verseRef: String): Result<String> {
        // The Gemini SDK likely handles its own dispatchers, but wrap if needed.
        // return withContext(Dispatchers.IO) { ... }
        return try {
            val prompt = "Tell me the key take-away for $verseRef"
            Log.d("LearnViewModel", "Sending prompt to Gemini: \"$prompt\"")

            // Call the Gemini API
            val response: GenerateContentResponse = model.generateContent(prompt)
            val responseText = response.text

            Log.d("LearnViewModel", "Gemini Response: $responseText")

            if (responseText != null) {
                Result.success(responseText)
            } else {
                Result.failure(Exception("Error: Received empty response from AI."))
            }

        } catch (e: Exception) {
            // Catch more specific exceptions from the Gemini SDK if possible
            Log.e("LearnViewModel", "Error calling Gemini API: ${e.message}", e)
            Result.failure(Exception("Error: Could not get take-away from AI (${e.javaClass.simpleName}).", e))
        }
    }

    fun resetNavigationState() { // Call this after navigation
        _state.update {
            it.copy(newlySavedVerseId = null, isContentSaved = false)
        }
    }
}