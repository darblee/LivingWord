package com.darblee.livingword

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

internal class PreferenceStore(private val context: Context) {
    companion object {
        private val Context.datastore: DataStore<Preferences> by preferencesDataStore(name = "APP_SETTING_KEY")

        // Keys for Color Mode
        private val COLOR_MODE_KEY = stringPreferencesKey("ColorMode")

        // Keys for AI Settings
        val AI_MODEL_NAME_KEY = stringPreferencesKey("ai_model_name")
        val AI_API_KEY_KEY = stringPreferencesKey("ai_api_key")
        val AI_TEMPERATURE_KEY = floatPreferencesKey("ai_temperature")

        // Default AI Settings
        val DEFAULT_AI_MODEL_NAME = "gemini-1.5-flash"
        val DEFAULT_AI_API_KEY = BuildConfig.GEMINI_API_KEY
        const val DEFAULT_AI_TEMPERATURE = 0.7f
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
}