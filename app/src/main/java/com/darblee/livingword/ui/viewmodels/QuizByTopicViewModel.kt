package com.darblee.livingword.ui.viewmodels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Data class for verse display in quiz
data class QuizVerseItem(
    val verseReference: String,
    val verseId: Long,
    val isCorrectMatch: Boolean, // true if this verse actually has the selected topic
    val isSelected: Boolean = false, // user's checkbox selection
    val validationResult: ValidationResult? = null // validation feedback after user clicks validate
)

// Enum for validation results
enum class ValidationResult {
    CORRECT,
    INCORRECT
}

// State class for QuizByTopic screen
data class QuizByTopicState(
    val currentSelectedTopic: String? = null,
    val quizVerses: List<QuizVerseItem> = emptyList(),
    val isValidated: Boolean = false, // true after user clicks validate
    val error: String? = null,
)

class QuizByTopicViewModel : ViewModel() {
    private val _state = MutableStateFlow(QuizByTopicState())
    val state: StateFlow<QuizByTopicState> = _state.asStateFlow()

    /**
     * Set the current selected topic
     */
    fun setCurrentSelectedTopic(topic: String) {
        _state.update { current ->
            current.copy(
                currentSelectedTopic = topic,
                quizVerses = emptyList(), // Clear verses so LaunchedEffect regenerates them
                isValidated = false,
            )
        }
    }

    /**
     * Set the quiz verses for the current topic
     */
    fun setQuizVerses(verses: List<QuizVerseItem>) {
        _state.update { current ->
            current.copy(quizVerses = verses)
        }
    }

    /**
     * Toggle verse selection
     */
    fun toggleVerseSelection(verseId: Long) {
        _state.update { current ->
            current.copy(
                quizVerses = current.quizVerses.map { verse ->
                    if (verse.verseId == verseId) {
                        verse.copy(isSelected = !verse.isSelected)
                    } else verse
                }
            )
        }
    }

    /**
     * Validate user's answers and mark each verse as correct or incorrect
     */
    fun validateAnswers() {
        _state.update { current ->
            current.copy(
                quizVerses = current.quizVerses.map { verse ->
                    val isCorrectAnswer = (verse.isSelected && verse.isCorrectMatch) ||
                                        (!verse.isSelected && !verse.isCorrectMatch)

                    verse.copy(
                        validationResult = if (isCorrectAnswer) ValidationResult.CORRECT else ValidationResult.INCORRECT
                    )
                },
                isValidated = true
            )
        }
    }

    /**
     * Reset validation results
     */
    fun resetValidation() {
        _state.update { current ->
            current.copy(
                quizVerses = current.quizVerses.map { verse ->
                    verse.copy(validationResult = null)
                },
                isValidated = false
            )
        }
    }
}