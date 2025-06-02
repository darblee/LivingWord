package com.darblee.livingword

import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Global variables used throughout the entire app
 */
internal object Global {
    const val DEBUG_PREFIX = "InChrist:"
    val DEFAULT_TOPICS = listOf("Intimacy", "Overcome Temptation")
    const val BUTTON_WIDTH = 60
    const val BUTTON_HEIGHT = 30
    val SMALL_ACTION_BUTTON_MODIFIER = Modifier.height(40.dp).width(80.dp)
    val SMALL_ACTION_BUTTON_PADDING = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
    const val VERSE_RESULT_KEY = "selectedVerseResult" // Key to transfer saveStateHandle info from GetEndVerseNumberScreen to NewVerseScreen
    const val TOPIC_SELECTION_RESULT_KEY = "selected_topics_result"   // Key to transfer saveStateHandle info from TopicSelectionScreen to LearnScreen
}

/**
 * Perform haptic feedback.
 */
fun View.click() = run {
    this.let { this.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)}
    this.playSoundEffect(SoundEffectConstants.CLICK)
}
