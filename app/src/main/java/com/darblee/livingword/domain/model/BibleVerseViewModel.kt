package com.darblee.livingword.domain.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.darblee.livingword.BibleVerseT
import com.darblee.livingword.data.AppDatabase
import com.darblee.livingword.data.BibleVerse
import com.darblee.livingword.data.BibleVerseRepository
import com.darblee.livingword.data.Topic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BibleVerseViewModel(private val repository: BibleVerseRepository) : ViewModel() {

    private val _allVerses = MutableStateFlow<List<BibleVerse>>(emptyList())
    val allVerses: StateFlow<List<BibleVerse>> = _allVerses

    private val _allTopics = MutableStateFlow<List<Topic>>(emptyList())
    val allTopicItems: StateFlow<List<Topic>> = _allTopics

    init {
        getAllVerses()
        getAllTopics()
    }

    fun saveNewVerse(
        verse: BibleVerseT,
        scripture: String,
        aiResponse: String,
        topics: List<String>,
        learnViewModel: LearnViewModel? = null
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
                learnViewModel?.contentSavedSuccessfully(newVerseID)
            } catch (e: Exception) {
                // Log the exception
                Log.e("BibleVerseViewModel", "Error saving verse with topics: ${e.message}", e)
                // Notify LearnViewModel about the error
                // Assuming LearnViewModel has a way to handle general errors or save errors.
                // You might need to add a specific function in LearnViewModel for this.
                // For example: learnViewModel?.handleSaveError(e.message ?: "Unknown error during save")
                // For now, let's assume LearnViewModel's generalError can be used or you'll add a specific handler.
                learnViewModel?.updateGeneralError("Failed to save content: ${e.localizedMessage}")
            }
        }
    }

    private fun LearnViewModel?.updateGeneralError(string: String) {

    }

    private fun getAllVerses() {
        viewModelScope.launch {
            repository.getAllVerses().collectLatest { verses ->
                _allVerses.value = verses
            }
        }
    }

    private fun getAllTopics() {
        viewModelScope.launch {
            repository.getAllTopics().collectLatest { topics ->
                _allTopics.value = topics
            }
        }
    }

    fun deleteVerse(bibleVerse: BibleVerse) {
        viewModelScope.launch {
            repository.deleteVerse(bibleVerse)
        }
    }

    suspend fun updateVerse(bibleVerse: BibleVerse) {
        viewModelScope.launch {
            repository.updateVerse(bibleVerse)
        }
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