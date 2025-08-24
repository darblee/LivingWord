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

// Enum for AI service types
enum class AIServiceType(val displayName: String, val defaultModel: String) {
    GEMINI("Gemini AI", "gemini-1.5-flash"),
    OPENAI("OpenAI", "gpt-4o-mini")
}

// Data class for individual AI service configuration
data class AIServiceConfig(
    val serviceType: AIServiceType,
    val modelName: String,
    val apiKey: String,
    val temperature: Float = 0.7f
)

// Enhanced data class to hold AI settings for multiple services
data class AISettings(
    val selectedService: AIServiceType = AIServiceType.GEMINI,
    val geminiConfig: AIServiceConfig = AIServiceConfig(
        serviceType = AIServiceType.GEMINI,
        modelName = AIServiceType.GEMINI.defaultModel,
        apiKey = "",
        temperature = 0.7f
    ),
    val openAiConfig: AIServiceConfig = AIServiceConfig(
        serviceType = AIServiceType.OPENAI,
        modelName = AIServiceType.OPENAI.defaultModel,
        apiKey = "",
        temperature = 0.7f
    )
) {
    // Compatibility methods for existing code
    val modelName: String get() = when (selectedService) {
        AIServiceType.GEMINI -> geminiConfig.modelName
        AIServiceType.OPENAI -> openAiConfig.modelName
    }
    
    val apiKey: String get() = geminiConfig.apiKey
    val openAiApiKey: String get() = openAiConfig.apiKey
    val temperature: Float get() = when (selectedService) {
        AIServiceType.GEMINI -> geminiConfig.temperature
        AIServiceType.OPENAI -> openAiConfig.temperature
    }
}

class PreferenceStore(private val context: Context) {
    companion object {
        private val Context.datastore: DataStore<Preferences> by preferencesDataStore(name = "APP_SETTING_KEY")

        // Keys for Color Mode
        private val COLOR_MODE_KEY = stringPreferencesKey("ColorMode")

        // Keys for AI Settings (legacy)
        val AI_MODEL_NAME_KEY = stringPreferencesKey("ai_model_name")
        val AI_API_KEY_KEY = stringPreferencesKey("ai_api_key")
        val OPENAI_API_KEY_KEY = stringPreferencesKey("openai_api_key")
        val AI_TEMPERATURE_KEY = floatPreferencesKey("ai_temperature")
        
        // Enhanced AI Settings keys for multiple services
        val SELECTED_AI_SERVICE_KEY = stringPreferencesKey("selected_ai_service")
        val GEMINI_MODEL_NAME_KEY = stringPreferencesKey("gemini_model_name")
        val GEMINI_API_KEY_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_TEMPERATURE_KEY = floatPreferencesKey("gemini_temperature")
        val OPENAI_MODEL_NAME_KEY = stringPreferencesKey("openai_model_name")
        val OPENAI_TEMPERATURE_KEY = floatPreferencesKey("openai_temperature")
        val TRANSLATION_KEY = stringPreferencesKey("translation")

        // Key for AI Disclaimer Dialog
        val AI_DISCLAIMER_SHOWN_KEY = booleanPreferencesKey("ai_disclaimer_shown")

        // Keys for VOTD Cache
        val VOTD_REFERENCE_CACHE_KEY = stringPreferencesKey("votd_reference_cache")
        val VOTD_CONTENT_CACHE_KEY = stringPreferencesKey("votd_content_cache")
        val VOTD_CACHED_TRANSLATION_KEY = stringPreferencesKey("votd_cached_translation")
        val VOTD_LAST_FETCH_DATE_KEY = stringPreferencesKey("votd_last_fetch_date")

        // Default AI Settings
        val DEFAULT_AI_MODEL_NAME = "gemini-2.5-flash"
        val DEFAULT_AI_API_KEY = BuildConfig.GEMINI_API_KEY
        val DEFAULT_OPENAI_API_KEY = BuildConfig.OPENAI_API_KEY
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

    // Save AI Settings (enhanced for multiple services)
    suspend fun saveAISettings(aiSettings: AISettings) {
        context.datastore.edit { preferences ->
            // Save enhanced settings
            preferences[SELECTED_AI_SERVICE_KEY] = aiSettings.selectedService.name
            preferences[GEMINI_MODEL_NAME_KEY] = aiSettings.geminiConfig.modelName
            preferences[GEMINI_API_KEY_KEY] = aiSettings.geminiConfig.apiKey
            preferences[GEMINI_TEMPERATURE_KEY] = aiSettings.geminiConfig.temperature
            preferences[OPENAI_MODEL_NAME_KEY] = aiSettings.openAiConfig.modelName
            preferences[OPENAI_TEMPERATURE_KEY] = aiSettings.openAiConfig.temperature
            
            // Maintain backward compatibility with legacy keys
            preferences[AI_MODEL_NAME_KEY] = aiSettings.modelName
            preferences[AI_API_KEY_KEY] = aiSettings.apiKey
            preferences[OPENAI_API_KEY_KEY] = aiSettings.openAiApiKey
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
            // Read enhanced settings first, fallback to legacy if not found
            val selectedServiceString = preferences[SELECTED_AI_SERVICE_KEY]
            val selectedService = try {
                if (selectedServiceString != null) {
                    AIServiceType.valueOf(selectedServiceString)
                } else {
                    AIServiceType.GEMINI // Default to Gemini
                }
            } catch (e: IllegalArgumentException) {
                AIServiceType.GEMINI
            }
            
            // Gemini configuration
            val geminiModelName = preferences[GEMINI_MODEL_NAME_KEY] 
                ?: preferences[AI_MODEL_NAME_KEY] // Fallback to legacy
                ?: DEFAULT_AI_MODEL_NAME
            val geminiApiKey = preferences[GEMINI_API_KEY_KEY] 
                ?: preferences[AI_API_KEY_KEY] // Fallback to legacy
                ?: DEFAULT_AI_API_KEY
            val geminiTemperature = preferences[GEMINI_TEMPERATURE_KEY] 
                ?: preferences[AI_TEMPERATURE_KEY] // Fallback to legacy
                ?: DEFAULT_AI_TEMPERATURE
            
            // OpenAI configuration
            val openAiModelName = preferences[OPENAI_MODEL_NAME_KEY] 
                ?: AIServiceType.OPENAI.defaultModel
            val openAiApiKey = preferences[OPENAI_API_KEY_KEY] 
                ?: DEFAULT_OPENAI_API_KEY
            val openAiTemperature = preferences[OPENAI_TEMPERATURE_KEY] 
                ?: DEFAULT_AI_TEMPERATURE
            
            AISettings(
                selectedService = selectedService,
                geminiConfig = AIServiceConfig(
                    serviceType = AIServiceType.GEMINI,
                    modelName = geminiModelName,
                    apiKey = geminiApiKey,
                    temperature = geminiTemperature
                ),
                openAiConfig = AIServiceConfig(
                    serviceType = AIServiceType.OPENAI,
                    modelName = openAiModelName,
                    apiKey = openAiApiKey,
                    temperature = openAiTemperature
                )
            )
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


