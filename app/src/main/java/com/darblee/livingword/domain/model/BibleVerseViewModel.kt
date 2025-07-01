package com.darblee.livingword.domain.model

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
import com.darblee.livingword.data.remote.GeminiAIService
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
        }
        getAllVerses()
        getAllTopics()
        getAllFavoriteVerses() // Add this line to your existing init block
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
                when (val result = GeminiAIService.fetchScripture(verseRef, newTranslation)) {
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

    /**
     * Gets all favorite verses.
     */
    private fun getAllFavoriteVerses() {
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
        scriptureVerses: List<Verse>
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
                newVerseViewModel?.contentSavedSuccessfully(newVerseID)
            } catch (e: Exception) {
                Log.e("BibleVerseViewModel", "Error saving verse with topics: ${e.message}", e)
                newVerseViewModel?.updateGeneralError("Failed to save content: ${e.localizedMessage}")
            }
        }
    }

    private fun NewVerseViewModel?.updateGeneralError(string: String) {

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

    private fun getAllVerses() {
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
     * This function assumes the BibleVerse data class has fields for this data.
     */
    fun updateUserData(
        verseId: Long,
        userDirectQuote: String,
        userDirectQuoteScore: Int,
        userContext: String,
        userContextScore: Int
    ) {
        viewModelScope.launch {
            try {
                // Get the existing verse from the repository
                val verseToUpdate = repository.getVerseById(verseId)

                // Create an updated instance of the verse using the .copy() method
                val updatedVerse = verseToUpdate.copy(
                    userDirectQuote = userDirectQuote,
                    userDirectQuoteScore = userDirectQuoteScore,
                    userContext = userContext,
                    userContextScore = userContextScore
                )

                // Call the repository to update the verse in the database
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
     * The determination is based on whether the user-provided text fields are empty or not.
     *
     * @param bibleVerse The BibleVerse object to check.
     * @return True if user-entered text exists in either the direct quote or context fields, false otherwise.
     */
    fun hasUserData(bibleVerse: BibleVerse): Boolean {
        // Data is considered "set" if either the direct quote or context text is not empty.
        return bibleVerse.userDirectQuote.isNotEmpty() || bibleVerse.userContext.isNotEmpty()
    }


    /**
     * Retrieves a specific Bible verse by its ID using the repository.
     * This is a suspend function as it calls a suspend function in the repository.
     * @param id The unique ID of the Bible verse to retrieve.
     * @return The BibleVerse object matching the ID.
     */
    suspend fun getVerseById(id: Long): BibleVerse {
        return repository.getVerseById(id)
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
        fun Factory(context: android.content.Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val database = AppDatabase.getDatabase(context)
                val repository = BibleVerseRepository(database.bibleVerseDao())
                BibleVerseViewModel(repository)
            }
        }
    }

    /***
     * Check if verse exist on database. We only need to check for matching book, chapter, and startingVerse.
     * You'll need a way to communicate the found verse or the "not found" status. A StateFlow or a simple
     * callback could work. For simplicity, let's make it a suspend function that returns the BibleVerse or null.
     */
    suspend fun findExistingVerse(book: String, chapter: Int, startVerse: Int): BibleVerse? {
        return repository.findVerseByReference(book, chapter, startVerse)
    }

}