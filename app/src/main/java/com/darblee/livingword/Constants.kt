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
    // Initial list of topics made available on new deploymemt of app. User can delete them (if desired)
    val DEFAULT_TOPICS = listOf("Faith", "Love", "Forgiveness")
    const val BUTTON_WIDTH = 100
    const val BUTTON_HEIGHT = 30
    val SMALL_ACTION_BUTTON_MODIFIER = Modifier.height(40.dp).width(80.dp)
    val SMALL_ACTION_BUTTON_PADDING = PaddingValues(horizontal = 4.dp, vertical = 4.dp)

    // Key to transfer saveStateHandle info from GetEndVerseNumberScreen  (or AddVerseByDescription)
    // to AllVerseScreen to process adding a new verse + take-away text to the system.
    const val VERSE_RESULT_KEY = "selectedVerseResult"

    // Key to transfer saveStateHandle info from TopicSelectionScreen to VerseDetailScreen
    const val TOPIC_SELECTION_RESULT_KEY = "selected_topics_result"

    const val DATABASE_NAME = "bible_verse_database"
    const val BACKUP_FOLDER_PATH = "Android/LivingWord/Backup"

    val bibleTranslations = listOf("ESV", "NIV", "AMP", "NKJV", "KJV")
}

/**
 * Perform haptic feedback.
 */
fun View.click() = run {
    this.let { this.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)}
    this.playSoundEffect(SoundEffectConstants.CLICK)
}