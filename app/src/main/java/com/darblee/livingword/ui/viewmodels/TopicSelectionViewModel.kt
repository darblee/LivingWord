package com.darblee.livingword.ui.viewmodels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.collections.plus


// 1. Create a dedicated state class
data class TopicSelectionState(
    val topicSelections: Map<String, Boolean> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

// 2. Create a ViewModel
class TopicSelectionViewModel : ViewModel() {
    private val _state = MutableStateFlow(TopicSelectionState())
    val state: StateFlow<TopicSelectionState> = _state.asStateFlow()

    // --- Result State for passing topics back ---
    private val _selectedTopicsResult = MutableStateFlow<List<String>?>(null)

    fun initializeTopics(
        topics: List<String>,
        preSelectedTopics: List<String> = emptyList()
    ) {
        if (_state.value.topicSelections.isEmpty()) {
            _state.update { current ->
                current.copy(
                    /***
                     * This code is responsible for creating a map that indicates which topics from a comprehensive
                     * list should be marked as pre-selected.
                     *
                     * topics: This is a list of strings, representing all available topics.
                     * .associateWith { ... }: This is a Kotlin collection function that transforms a list into a map.
                     * It iterates through each item in the topics list. For each item, it uses the item itself as the
                     * key in the new map and the result of the provided lambda expression as the value. The result is
                     * the boolean value of true if item is found in preSelectedTopics
                     */
                    topicSelections = topics.associateWith { it in preSelectedTopics }
                )
            }
        }
    }

    fun toggleTopic(topic: String) {
        _state.update { current ->
            val currentSelection = current.topicSelections[topic] == true
            current.copy(
                /***
                 * (topic to !currentSelection): This creates a Kotlin Pair. In this case, it's a pair where the first
                 * element is the topic string and the second element is its new, inverted selection state (!currentSelection).
                 *
                 * current.topicSelections + (topic to !currentSelection): In Kotlin, when the + operator is used with a Map
                 * and a Pair, it results in a new map. This new map contains all the entries from the original map
                 * (current.topicSelections), but with the entry for the specified topic either added or updated.
                 *
                 * If the topic (the key from the Pair) already exists as a key in current.topicSelections, its corresponding
                 * value is updated to the new value from the Pair (which is !currentSelection).
                 *
                 * If the topic does not exist as a key (which is unlikely in this scenario if topics are initialized first),
                 * a new entry with topic as the key and !currentSelection as the value would be added.
                 *
                 * (topic to !currentSelection):
                 *                  *   The + operator, when used with a Kotlin Map and a Pair, produces a new map.
                 *                  *   This new map will contain all the entries from the original current.topicSelections map.
                 */
                topicSelections = current.topicSelections + (topic to !currentSelection)
            )
        }
    }

    /***
     * Add the new topic to the selection
     */
    fun addNewTopic(topic: String) {
        if (topic.isBlank()) return

        val newTopic = topic.trim()

        // Check if topic already exists
        if (_state.value.topicSelections.containsKey(newTopic)) return

        _state.update { current ->
            current.copy(
                /***
                 * (newTopic to true): This Kotlin syntax creates a Pair<String, Boolean>. Here, it's a pair consisting of
                 * the newTopic string and the boolean value true. This pair represents the new entry to be added or used to update the map.
                 *
                 * current.topicSelections + (newTopic to true):
                 *   The + operator, when used with a Kotlin Map and a Pair, produces a new map.
                 *   This new map will contain all the entries from the original current.topicSelections map.
                 */
                topicSelections = current.topicSelections + (newTopic to true)
            )
        }
    }

    // --- Result Passing Functions ---

    /**
     * Sets the selected topics result. Called by TopicSelectionScreen when confirmed.
     * @param topics The list of topics selected.
     */
    fun setTopicsResult(topics: List<String>) {
        _selectedTopicsResult.value = topics
    }

    /**
     * Clears the selected topics result after it has been consumed by the receiving screen/dialog.
     */
    fun consumeTopicsResult() {
        _selectedTopicsResult.value = null
    }

    // Optional: Function to initialize selected topics from a previous state
    // Useful if you want TopicSelectionScreen to open with some topics pre-selected
    fun initializeSelectedTopics(topics: List<String>) {
        _state.update { current ->
            current.copy(
                topicSelections = current.topicSelections.mapValues { (key, value) ->
                    // Keep existing selection if already selected, otherwise check if in the provided list
                    value || topics.contains(key)
                }
            )
        }
    }
}
