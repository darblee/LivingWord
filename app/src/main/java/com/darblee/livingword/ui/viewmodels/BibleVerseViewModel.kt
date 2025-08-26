package com.darblee.livingword.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.darblee.livingword.SnackBarController
import com.darblee.livingword.data.AppDatabase
import com.darblee.livingword.data.BibleVerse
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.BibleVerseRepository
import com.darblee.livingword.data.TopicWithCount
import com.darblee.livingword.data.Verse
import com.darblee.livingword.data.remote.AiServiceResult
import com.darblee.livingword.data.remote.AIService
import com.darblee.livingword.data.verseReferenceBibleVerseRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BibleVerseViewModel(private val repository: BibleVerseRepository) : ViewModel() {

    private val _allVerses = MutableStateFlow<List<BibleVerse>>(emptyList())
    val allVerses: StateFlow<List<BibleVerse>> = _allVerses

    private val _allTopicsWithCount = MutableStateFlow<List<TopicWithCount>>(emptyList())
    val allTopicsWithCount: StateFlow<List<TopicWithCount>> = _allTopicsWithCount

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _favoriteVerses = MutableStateFlow<List<BibleVerse>>(emptyList())
    val favoriteVerses: StateFlow<List<BibleVerse>> = _favoriteVerses

    init {
        viewModelScope.launch {
            repository.addDefaultTopicsIfEmpty()
            // Clean up any migration data issues
            cleanupMigrationData()
        }
        getAllVerses()
        getAllTopics()
        getAllFavoriteVerses()
    }

    /**
     * Clean up any data inconsistencies from database migration
     */
    private suspend fun cleanupMigrationData() {
        try {
            repository.cleanupMigrationData()
            Log.i("BibleVerseViewModel", "Migration data cleanup completed")
        } catch (e: Exception) {
            Log.e("BibleVerseViewModel", "Error during migration cleanup", e)
        }
    }

    /**
     * Public method to force cleanup migration data if needed
     * This can be called if users experience issues after database migration
     */
    fun forceCleanupMigrationData() {
        viewModelScope.launch {
            try {
                cleanupMigrationData()
                Log.i("BibleVerseViewModel", "Forced migration cleanup completed")
                _errorMessage.value = "Database cleanup completed. Please try again."
            } catch (e: Exception) {
                Log.e("BibleVerseViewModel", "Error during forced migration cleanup", e)
                _errorMessage.value = "Error during database cleanup: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Retrieves a specific Bible verse as a Flow by its ID.
     * This allows the UI to observe changes to the verse in real-time.
     * Note: This relies on a corresponding function being added to the BibleVerseRepository.
     *
     * @param id The unique ID of the Bible verse to retrieve.
     * @return A Flow that emits the BibleVerse object whenever it's updated in the database.
     */
    fun getVerseFlow(id: Long): Flow<BibleVerse> {
        return repository.getVerseFlow(id)
    }

    /**
     * Reloads the scripture text for a given verse with a new translation.
     * It fetches the new text from an external service and updates the local database.
     *
     * @param verseId The ID of the verse to update.
     * @param newTranslation The new translation code (e.g., "NIV", "ESV").
     */
    fun reloadVerseWithNewTranslation(verseId: Long, newTranslation: String) {
        viewModelScope.launch {
            try {
                // Step 1: Get the current verse object to create the reference.
                val verseToUpdate = repository.getVerseById(verseId)
                val verseRef = BibleVerseRef(
                    book = verseToUpdate.book,
                    chapter = verseToUpdate.chapter,
                    startVerse = verseToUpdate.startVerse,
                    endVerse = verseToUpdate.endVerse
                )

                // Step 2: Fetch the new scripture text from the service.
                Log.i("BibleVerseViewModel", "Fetching new scripture for $verseRef in $newTranslation")
                when (val result = AIService.fetchScripture(verseRef, newTranslation)) {
                    is AiServiceResult.Success -> {
                        // Step 3a: On success, create the updated verse and save it.
                        val newScriptureVerses = result.data
                        val updatedVerse = verseToUpdate.copy(
                            translation = newTranslation,
                            scriptureVerses = newScriptureVerses
                        )
                        repository.updateVerse(updatedVerse)
                        SnackBarController.showMessage("Verse updated to $newTranslation")
                        Log.i("BibleVerseViewModel", "Successfully updated verse $verseId to $newTranslation.")
                    }
                    is AiServiceResult.Error -> {
                        // Step 3b: On error, log and display the message.
                        val errorMessage = "Failed to update translation: ${result.message}"
                        Log.e("BibleVerseViewModel", errorMessage, result.cause)
                        _errorMessage.value = errorMessage
                    }
                }
            } catch (e: Exception) {
                // Catch errors from repository.getVerseById or repository.updateVerse
                val errorMessage = "A database error occurred while updating translation."
                Log.e("BibleVerseViewModel", "$errorMessage - VerseID: $verseId", e)
                _errorMessage.value = "$errorMessage Please try again."
            }
        }
    }

    /**
     * Updates the favorite status of a verse.
     */
    fun updateFavoriteStatus(verseId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            try {
                repository.updateFavoriteStatus(verseId, isFavorite)
                val message = if (isFavorite) "Added to favorites" else "Removed from favorites"
                Log.i("BibleVerseViewModel", "Updated favorite status for verse ID: $verseId to $isFavorite")
                _errorMessage.value = message
            } catch (e: Exception) {
                Log.e("BibleVerseViewModel", "Error updating favorite status for verse ID: $verseId", e)
                _errorMessage.value = "Error updating favorite status: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Updates the translation of a verse.
     */
    fun updateTranslation(verseId: Long, translation: String) {
        viewModelScope.launch {
            try {
                repository.updateTranslation(verseId, translation)
                Log.i("BibleVerseViewModel", "Updated translation for verse ID: $verseId to $translation")
                _errorMessage.value = "Translation updated to $translation"
            } catch (e: Exception) {
                Log.e("BibleVerseViewModel", "Error updating translation for verse ID: $verseId", e)
                _errorMessage.value = "Error updating translation: ${e.localizedMessage}"
            }
        }
    }

    fun getVersesByTopic(topic: String): Flow<List<BibleVerse>> {
        return repository.getVersesByTopic(topic)
    }

    /**
     * Gets all favorite verses.
     */
    fun getAllFavoriteVerses() {
        viewModelScope.launch {
            repository.getAllFavoriteVerses().collectLatest { favorites ->
                _favoriteVerses.value = favorites
            }
        }
    }

    /**
     * Gets verses by translation.
     */
    fun getVersesByTranslation(translation: String): Flow<List<BibleVerse>> {
        return repository.getVersesByTranslation(translation)
    }

    /**
     * Gets favorite verses by translation.
     */
    fun getFavoriteVersesByTranslation(translation: String): Flow<List<BibleVerse>> {
        return repository.getFavoriteVersesByTranslation(translation)
    }

    fun saveNewVerse(
        verse: BibleVerseRef,
        aiTakeAwayResponse: String,
        topics: List<String>,
        translation: String,
        favorite: Boolean = false,
        newVerseViewModel: NewVerseViewModel? = null,
        scriptureVerses: List<Verse>,
        isEarlySave: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                val newVerseID = repository.insertVerseWithTopics(
                    book = verse.book,
                    chapter = verse.chapter,
                    startVerse = verse.startVerse,
                    endVerse = verse.endVerse,
                    aiTakeAwayResponse = aiTakeAwayResponse,
                    topics = topics,
                    favorite = favorite,
                    translation = translation,
                    verses = scriptureVerses
                )
                if (isEarlySave) {
                    newVerseViewModel?.scriptureSavedSuccessfully(newVerseID)
                } else {
                    newVerseViewModel?.contentSavedSuccessfully(newVerseID)
                }
            } catch (e: Exception) {
                Log.e("BibleVerseViewModel", "Error saving verse with topics: ${e.message}", e)
                newVerseViewModel?.updateGeneralError("Failed to save content: ${e.localizedMessage}")
            }
        }
    }

    fun updateVerseAIContent(
        verseId: Long,
        aiTakeAwayResponse: String,
        newVerseViewModel: NewVerseViewModel? = null
    ) {
        viewModelScope.launch {
            try {
                Log.d("BibleVerseViewModel", "Updating verse $verseId with AI content: ${aiTakeAwayResponse.take(50)}...")
                val existingVerse = repository.getVerseById(verseId)
                val updatedVerse = existingVerse.copy(aiTakeAwayResponse = aiTakeAwayResponse)
                repository.updateVerse(updatedVerse)
                Log.d("BibleVerseViewModel", "Successfully updated verse $verseId with AI content")
                newVerseViewModel?.contentSavedSuccessfully(verseId)
            } catch (e: Exception) {
                Log.e("BibleVerseViewModel", "Error updating verse AI content: ${e.message}", e)
                newVerseViewModel?.updateGeneralError("Failed to update AI content: ${e.localizedMessage}")
            }
        }
    }

    suspend fun fetchAITakeawayForVerse(verseId: Long): Pair<Boolean, String> {
        return try {
            Log.d("BibleVerseViewModel", "Fetching AI takeaway for verse $verseId...")
            val existingVerse = repository.getVerseById(verseId)
            
            // Convert to BibleVerseRef for AI service
            val verseRef = BibleVerseRef(
                book = existingVerse.book,
                chapter = existingVerse.chapter,
                startVerse = existingVerse.startVerse,
                endVerse = existingVerse.endVerse
            )
            
            // Get AI takeaway
            when (val takeAwayResult = AIService.getKeyTakeaway(verseReferenceBibleVerseRef(verseRef))) {
                is AiServiceResult.Success -> {
                    val takeawayResponseText = takeAwayResult.data

                    // Validate the takeaway
                    when (val validationResult = AIService.validateKeyTakeawayResponse(verseReferenceBibleVerseRef(verseRef), takeawayResponseText)) {
                        is AiServiceResult.Success -> {
                            if (validationResult.data) {
                                Log.i("BibleVerseViewModel", "Takeaway for verse $verseId is acceptable: ${takeawayResponseText.take(50)}...")
                                
                                // Update the verse with AI content
                                val updatedVerse = existingVerse.copy(aiTakeAwayResponse = takeawayResponseText)
                                repository.updateVerse(updatedVerse)
                                Log.d("BibleVerseViewModel", "Successfully updated verse $verseId with AI takeaway")
                                
                                Pair(true, takeawayResponseText)
                            } else {
                                Log.w("BibleVerseViewModel", "Takeaway for verse $verseId was rejected by validator")
                                Pair(false, "The AI-generated insight was rejected by the validator. Please try again.")
                            }
                        }
                        is AiServiceResult.Error -> {
                            Log.e("BibleVerseViewModel", "Validation failed for verse $verseId: ${validationResult.message}")
                            Pair(false, "Validation failed: ${validationResult.message}")
                        }
                    }
                }
                is AiServiceResult.Error -> {
                    Log.e("BibleVerseViewModel", "Failed to get AI takeaway for verse $verseId: ${takeAwayResult.message}")
                    Pair(false, takeAwayResult.message)
                }
            }
        } catch (e: Exception) {
            Log.e("BibleVerseViewModel", "Error fetching AI takeaway for verse $verseId: ${e.message}", e)
            Pair(false, "Error fetching AI takeaway: ${e.localizedMessage}")
        }
    }

    fun saveNewVerseHome(
        verse: BibleVerseRef,
        aiTakeAwayResponse: String,
        topics: List<String>,
        translation: String,
        favorite: Boolean = false,
        homeViewModel: HomeViewModel? = null,
        scriptureVerses: List<Verse>
    ) {
        Log.i("BibleVerseViewModel", "Home: Saving new verse")
        viewModelScope.launch {
            try {
                val newVerseID = repository.insertVerseWithTopics(
                    book = verse.book,
                    chapter = verse.chapter,
                    startVerse = verse.startVerse,
                    endVerse = verse.endVerse,
                    aiTakeAwayResponse = aiTakeAwayResponse,
                    topics = topics,
                    favorite = favorite,
                    translation = translation,
                    verses = scriptureVerses
                )
                homeViewModel?.contentSavedSuccessfully(newVerseID)
            } catch (e: Exception) {
                Log.e("BibleVerseViewModel", "Error saving verse with topics: ${e.message}", e)
                homeViewModel?.updateGeneralError("Failed to save content: ${e.localizedMessage}")
            }
        }
    }

    private fun NewVerseViewModel?.updateGeneralError(string: String) {
        // This method appears to be incomplete in the original code
        // You may need to implement this based on your NewVerseViewModel class
    }

    fun addTopic(topicName: String) {
        viewModelScope.launch {
            try {
                val trimmedName = topicName.trim()
                if (trimmedName.isBlank()) {
                    _errorMessage.value = "Topic name cannot be blank"
                    return@launch
                }

                repository.addTopic(trimmedName)
                Log.i("BibleVerseViewModel", "Successfully added topic: '$trimmedName'")
                _errorMessage.value = "Topic '$trimmedName' added successfully"
            } catch (e: Exception) {
                Log.e("BibleVerseViewModel", "Error adding topic '$topicName': ${e.message}", e)
                _errorMessage.value = "Error adding topic: ${e.localizedMessage}"
            }
        }
    }

    fun getAllVerses() {
        viewModelScope.launch {
            repository.getAllVerses().collectLatest { verses ->
                _allVerses.value = verses
            }
        }
    }

    // Replace the existing getAllTopics() method with this:
    private fun getAllTopics() {
        viewModelScope.launch {
            repository.getAllTopics().collectLatest { topicsWithCount ->
                _allTopicsWithCount.value = topicsWithCount
            }
        }
    }

    suspend fun deleteVerse(bibleVerse: BibleVerse) {
        repository.deleteVerse(bibleVerse)
    }

    suspend fun updateVerse(bibleVerse: BibleVerse) {
        repository.updateVerse(bibleVerse)
    }

    /**
     * Deletes multiple topics by their names.
     * Only topics with 0 verse count should be deleted.
     * This function runs in a coroutine scope.
     */
    fun deleteTopics(topicNames: List<String>) {
        viewModelScope.launch {
            try {
                repository.deleteTopics(topicNames)
                Log.i("BibleVerseViewModel", "Successfully processed deletion for topics: $topicNames")
            } catch (e: Exception) {
                Log.e("BibleVerseViewModel", "Error deleting topics: ${e.message}", e)
                _errorMessage.value = "Error during topic deletion: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Updates a verse with the user's memorized direct quote, context, and the scores.
     * This function now uses safe handling for the new AI feedback fields.
     */
    fun updateUserData(
        verseId: Long,
        userDirectQuote: String,
        userDirectQuoteScore: Int,
        userContext: String,
        userContextScore: Int,
        aiDirectQuoteExplanationText: String,
        aiContextExplanationText: String,
        applicationFeedback: String
    ) {
        viewModelScope.launch {
            try {
                // Use the safe DAO method for updating AI feedback data
                repository.updateAIFeedbackData(
                    verseId = verseId,
                    aiDirectQuoteExplanation = aiDirectQuoteExplanationText,
                    aiContextExplanation = aiContextExplanationText,
                    applicationFeedback = applicationFeedback,
                    directQuoteScore = userDirectQuoteScore,
                    contextScore = userContextScore
                )

                // Also update the basic user data (direct quote and context)
                val verseToUpdate = repository.getVerseById(verseId)
                val updatedVerse = verseToUpdate.copy(
                    userDirectQuote = userDirectQuote,
                    userContext = userContext,
                    userDirectQuoteScore = userDirectQuoteScore,
                    userContextScore = userContextScore,
                    aiDirectQuoteExplanationText = aiDirectQuoteExplanationText,
                    aiContextExplanationText = aiContextExplanationText,
                    applicationFeedback = applicationFeedback,
                    lastModified = System.currentTimeMillis()
                )

                repository.updateVerse(updatedVerse)
                SnackBarController.showMessage("Memorized Content is saved")
                Log.i("BibleVerseViewModel", "Successfully updated user memorization data for verse ID: $verseId")
                _errorMessage.value = "Memorization progress saved!"
            } catch (e: Exception) {
                Log.e("BibleVerseViewModel", "Error updating user memorization data for verse ID: $verseId", e)
                _errorMessage.value = "Error saving memorization data: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Checks if a BibleVerse has any user memorization data saved.
     * Updated to use safe accessor methods for the new fields.
     *
     * @param bibleVerse The BibleVerse object to check.
     * @return True if user-entered text exists in either the direct quote or context fields, false otherwise.
     */
    fun hasUserData(bibleVerse: BibleVerse): Boolean {
        return try {
            // Data is considered "set" if either the direct quote or context text is not empty.
            bibleVerse.userDirectQuote.isNotEmpty() || bibleVerse.userContext.isNotEmpty()
        } catch (e: Exception) {
            Log.w("BibleVerseViewModel", "Error checking user data for verse ${bibleVerse.id}: ${e.message}", e)
            false // Return false if there's any serialization/access error
        }
    }

    /**
     * Retrieves a specific Bible verse by its ID using the repository.
     * Updated with error handling for migration issues.
     * @param id The unique ID of the Bible verse to retrieve.
     * @return The BibleVerse object matching the ID.
     */
    suspend fun getVerseById(id: Long): BibleVerse {
        return try {
            repository.getVerseById(id)
        } catch (e: Exception) {
            Log.e("BibleVerseViewModel", "Error retrieving verse by ID $id: ${e.message}", e)
            // Try to get the verse safely (this should handle migration issues)
            repository.getVerseSafely(id) ?: throw e
        }
    }

    fun renameOrMergeTopic(oldTopicName: String, newTopicName: String, isMergeIntent: Boolean) {
        val trimmedNewName = newTopicName.trim()
        if (trimmedNewName.isBlank()) {
            _errorMessage.value = "New topic name cannot be blank."
            Log.w("BibleVerseViewModel", "New topic name cannot be blank.")
            return
        }
        // If it's not a merge, and names are identical (case-sensitive), do nothing.
        // If it IS a merge, newName will be an existing different topic, so this check is fine.
        if (oldTopicName == trimmedNewName && !isMergeIntent) {
            Log.i("BibleVerseViewModel", "Old and new topic names are the same. No action needed for rename.")
            return
        }

        viewModelScope.launch {
            try {
                repository.renameOrMergeTopic(oldTopicName, trimmedNewName, isMergeIntent)
                val action = if (isMergeIntent) "merged into" else "renamed to"
                Log.i("BibleVerseViewModel", "Topic '$oldTopicName' $action '$trimmedNewName' successfully.")
                _errorMessage.value = "Topic '$oldTopicName' $action '$trimmedNewName'."
            } catch (e: IllegalArgumentException) {
                Log.w("BibleVerseViewModel", "Validation error during topic operation: ${e.message}")
                _errorMessage.value = e.message
            } catch (e: NoSuchElementException) {
                Log.w("BibleVerseViewModel", "Error during topic operation: ${e.message}")
                _errorMessage.value = e.message
            } catch (e: Exception) {
                Log.e("BibleVerseViewModel", "Error processing topic '$oldTopicName' to '$trimmedNewName': ${e.message}", e)
                _errorMessage.value = "Failed to process topic: ${e.localizedMessage}"
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    /**
     * Posts a custom message to be shown to the user (e.g., via Snackbar).
     */
    fun postUserMessage(message: String) {
        _errorMessage.value = message
    }

    companion object {
        fun Factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val database = AppDatabase.Companion.getDatabase(context)
                val repository = BibleVerseRepository(database.bibleVerseDao())
                BibleVerseViewModel(repository)
            }
        }
    }

    /***
     * Check if verse exist on database. We only need to check for matching book, chapter, and startingVerse.
     * Updated with error handling for migration issues.
     */
    suspend fun findExistingVerse(book: String, chapter: Int, startVerse: Int): BibleVerse? {
        return try {
            repository.findVerseByReference(book, chapter, startVerse)
        } catch (e: Exception) {
            Log.e("BibleVerseViewModel", "Error finding existing verse: ${e.message}", e)
            null // Return null if there's any error during lookup
        }
    }

    /**
     * Check if the verse has AI feedback data for the given input
     * Updated to use safe accessor methods.
     */
    fun hasCachedAIFeedback(verse: BibleVerse, directQuote: String, userApplication: String): Boolean {
        return try {
            verse.hasCachedAIFeedback() && verse.matchesCachedInput(directQuote, userApplication)
        } catch (e: Exception) {
            Log.w("BibleVerseViewModel", "Error checking cached AI feedback: ${e.message}", e)
            false // Return false if there's any error accessing the data
        }
    }

    /**
     * Get cached AI feedback if it exists for the given input
     * Updated to use safe accessor methods.
     */
    fun getCachedAIFeedback(verse: BibleVerse): Triple<String, String, String>? {
        return try {
            if (verse.hasCachedAIFeedback()) {
                Triple(
                    verse.getSafeAIDirectQuoteExplanation(),
                    verse.getSafeAIContextExplanation(),
                    verse.getSafeApplicationFeedback()
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w("BibleVerseViewModel", "Error getting cached AI feedback: ${e.message}", e)
            null // Return null if there's any error accessing the data
        }
    }

    /**
     * Check if the verse has valid AI feedback data
     * This is useful for determining if feedback can be displayed
     */
    suspend fun hasValidAIFeedback(verseId: Long): Boolean {
        return try {
            repository.hasValidAIFeedback(verseId) > 0
        } catch (e: Exception) {
            Log.w("BibleVerseViewModel", "Error checking valid AI feedback: ${e.message}", e)
            false
        }
    }
}
