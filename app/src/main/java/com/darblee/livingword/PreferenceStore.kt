package com.darblee.livingword

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.darblee.livingword.PreferenceStore.Companion.VOTD_CACHED_TRANSLATION_KEY
import com.darblee.livingword.PreferenceStore.Companion.VOTD_CONTENT_CACHE_KEY
import com.darblee.livingword.PreferenceStore.Companion.VOTD_LAST_FETCH_DATE_KEY
import com.darblee.livingword.PreferenceStore.Companion.VOTD_REFERENCE_CACHE_KEY
import com.darblee.livingword.ui.theme.ColorThemeOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

// Data class to hold AI settings
data class AISettings(
    val modelName: String,
    val apiKey: String,
    val temperature: Float
)

class PreferenceStore(private val context: Context) {
    companion object {
        private val Context.datastore: DataStore<Preferences> by preferencesDataStore(name = "APP_SETTING_KEY")

        // Keys for Color Mode
        private val COLOR_MODE_KEY = stringPreferencesKey("ColorMode")

        // Keys for AI Settings
        val AI_MODEL_NAME_KEY = stringPreferencesKey("ai_model_name")
        val AI_API_KEY_KEY = stringPreferencesKey("ai_api_key")
        val AI_TEMPERATURE_KEY = floatPreferencesKey("ai_temperature")
        val TRANSLATION_KEY = stringPreferencesKey("translation")

        // Key for AI Disclaimer Dialog
        val AI_DISCLAIMER_SHOWN_KEY = booleanPreferencesKey("ai_disclaimer_shown")

        // Keys for VOTD Cache
        val VOTD_REFERENCE_CACHE_KEY = stringPreferencesKey("votd_reference_cache")
        val VOTD_CONTENT_CACHE_KEY = stringPreferencesKey("votd_content_cache")
        val VOTD_CACHED_TRANSLATION_KEY = stringPreferencesKey("votd_cached_translation")
        val VOTD_LAST_FETCH_DATE_KEY = stringPreferencesKey("votd_last_fetch_date")

        // Default AI Settings
        val DEFAULT_AI_MODEL_NAME = "gemini-2.0-flash"
        val DEFAULT_AI_API_KEY = BuildConfig.GEMINI_API_KEY
        const val DEFAULT_AI_TEMPERATURE = 0.7f
        val DEFAULT_TRANSLATION = "ESV"
        const val DEFAULT_AI_DISCLAIMER_SHOWN = false
    }

    // Save Color Mode
    suspend fun saveColorModeToSetting(colorMode: ColorThemeOption) {
        context.datastore.edit { preferences ->
            preferences[COLOR_MODE_KEY] = colorMode.toString()
        }
    }

    // Read Color Mode
    suspend fun readColorModeFromSetting(): ColorThemeOption {
        val preferences = context.datastore.data.first()
        val colorModeString = preferences[COLOR_MODE_KEY]
        return when (colorModeString) {
            ColorThemeOption.Light.toString() -> ColorThemeOption.Light
            ColorThemeOption.Dark.toString() -> ColorThemeOption.Dark
            else -> ColorThemeOption.System
        }
    }

    // Save Translation
    suspend fun saveTranslationToSetting(translation: String) {
        context.datastore.edit { preferences ->
            preferences[TRANSLATION_KEY] = translation
        }
    }

    // Read Translation
    suspend fun readTranslationFromSetting(): String {
        val preferences = context.datastore.data.first()
        return preferences[TRANSLATION_KEY] ?: DEFAULT_TRANSLATION
    }

    // Save AI Disclaimer Shown Status
    suspend fun saveAIDisclaimerShown(shown: Boolean) {
        context.datastore.edit { preferences ->
            preferences[AI_DISCLAIMER_SHOWN_KEY] = shown
        }
    }

    // Read AI Disclaimer Shown Status
    suspend fun readAIDisclaimerShown(): Boolean {
        val preferences = context.datastore.data.first()
        return preferences[AI_DISCLAIMER_SHOWN_KEY] ?: DEFAULT_AI_DISCLAIMER_SHOWN
    }

    // Save AI Settings
    suspend fun saveAISettings(aiSettings: AISettings) {
        context.datastore.edit { preferences ->
            preferences[AI_MODEL_NAME_KEY] = aiSettings.modelName
            preferences[AI_API_KEY_KEY] = aiSettings.apiKey
            preferences[AI_TEMPERATURE_KEY] = aiSettings.temperature
        }
    }

    // Read AI Settings as a Flow
    val aiSettingsFlow: Flow<AISettings> = context.datastore.data
        .catch { exception ->
            // dataStore.data throws an IOException when an error is encountered when reading data
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            val modelName = preferences[AI_MODEL_NAME_KEY] ?: DEFAULT_AI_MODEL_NAME
            val apiKey = preferences[AI_API_KEY_KEY] ?: DEFAULT_AI_API_KEY
            val temperature = preferences[AI_TEMPERATURE_KEY] ?: DEFAULT_AI_TEMPERATURE
            AISettings(modelName, apiKey, temperature)
        }

    // Read AI Settings once (suspending function)
    suspend fun readAISettings(): AISettings {
        return aiSettingsFlow.first()
    }

    // Save VOTD Cache
    suspend fun saveVotdCache(reference: String, content: String, translation: String, date: String) {
        context.datastore.edit { preferences ->
            preferences[VOTD_REFERENCE_CACHE_KEY] = reference
            preferences[VOTD_CONTENT_CACHE_KEY] = content
            preferences[VOTD_CACHED_TRANSLATION_KEY] = translation
            preferences[VOTD_LAST_FETCH_DATE_KEY] = date
        }
    }

    // Read VOTD Cache
    suspend fun readVotdCache(): VotdCache? {
        val preferences = context.datastore.data.first()
        val reference = preferences[VOTD_REFERENCE_CACHE_KEY]
        val content = preferences[VOTD_CONTENT_CACHE_KEY]
        val translation = preferences[VOTD_CACHED_TRANSLATION_KEY]
        val date = preferences[VOTD_LAST_FETCH_DATE_KEY]

        return if (reference != null && content != null && translation != null && date != null) {
            VotdCache(reference, content, translation, date)
        } else {
            null
        }
    }
}

// Data class to hold cached VOTD data
data class VotdCache(
    val reference: String,
    val content: String,
    val translation: String,
    val date: String
)


