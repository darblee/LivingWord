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

    init {
        getAllVerses()
        getAllTopicsWithCount() // Changed from getAllTopics()
    }

    fun saveNewVerse(
        verse: BibleVerseRef,
        scripture: String,
        aiResponse: String,
        topics: List<String>,
        newVerseViewModel: NewVerseViewModel? = null
    ) {
        viewModelScope.launch {
            try {
                val newVerseID = repository.insertVerseWithTopics(
                    book = verse.book,
                    chapter = verse.chapter,
                    startVerse = verse.startVerse,
                    endVerse = verse.endVerse,
                    scripture = scripture,
                    aiResponse = aiResponse,
                    topics = topics
                )
                newVerseViewModel?.contentSavedSuccessfully(newVerseID)
            } catch (e: Exception) {
                // Log the exception
                Log.e("BibleVerseViewModel", "Error saving verse with topics: ${e.message}", e)
                // Notify LearnViewModel about the error
                // Assuming LearnViewModel has a way to handle general errors or save errors.
                // You might need to add a specific function in LearnViewModel for this.
                // For example: learnViewModel?.handleSaveError(e.message ?: "Unknown error during save")
                // For now, let's assume LearnViewModel's generalError can be used or you'll add a specific handler.
                newVerseViewModel?.updateGeneralError("Failed to save content: ${e.localizedMessage}")
            }
        }
    }

    private fun NewVerseViewModel?.updateGeneralError(string: String) {

    }

    private fun getAllVerses() {
        viewModelScope.launch {
            repository.getAllVerses().collectLatest { verses ->
                _allVerses.value = verses
            }
        }
    }

    // Replace the existing getAllTopics() method with this:
    private fun getAllTopicsWithCount() {
        viewModelScope.launch {
            repository.getAllTopicsWithCount().collectLatest { topicsWithCount ->
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
    fun updateUserMemorizationData(
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
    fun hasUserMemorizationData(bibleVerse: BibleVerse): Boolean {
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

    // --- Added for Rename Topic ---
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