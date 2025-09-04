package com.darblee.livingword

import android.util.Log
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.darblee.livingword.ui.theme.ColorThemeOption
import com.darblee.livingword.data.remote.AIServiceRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

// Enum for AI service types
enum class AIServiceType(val displayName: String, val defaultModel: String) {
    GEMINI("Gemini AI", "gemini-1.5-flash"),
    OPENAI("OpenAI", "gpt-4o-mini"),
    DEEPSEEK("DeepSeek AI", "deepseek-chat"),
    OLLAMA("Ollama AI", "hf.co/mradermacher/Protestant-Christian-Bible-Expert-v2.0-12B-i1-GGUF:IQ4_XS")
}

// Data class for individual AI service configuration
data class AIServiceConfig(
    val serviceType: AIServiceType,
    val modelName: String,
    val apiKey: String,
    val temperature: Float = 0.7f
)

// Dynamic AI configuration for any registered provider
@Serializable
data class DynamicAIConfig(
    val providerId: String,
    val displayName: String,
    val serviceType: AIServiceType,
    val modelName: String,
    val apiKey: String,
    val temperature: Float = 0.7f,
    val isEnabled: Boolean = true
)

// Enhanced data class to hold AI settings for multiple services
data class AISettings(
    val selectedService: AIServiceType = AIServiceType.GEMINI,
    val selectedProviderId: String = "gemini_ai",
    val dynamicConfigs: Map<String, DynamicAIConfig> = emptyMap()
) {
    // Get currently selected config
    val selectedConfig: DynamicAIConfig?
        get() = dynamicConfigs[selectedProviderId]
    
    // Get config for a specific provider
    fun getConfigForProvider(providerId: String): DynamicAIConfig? {
        return dynamicConfigs[providerId]
    }
    
    // Get all configs for a service type
    fun getConfigsForServiceType(serviceType: AIServiceType): List<DynamicAIConfig> {
        return dynamicConfigs.values.filter { it.serviceType == serviceType }
    }
    
    // Compatibility methods for existing code
    val modelName: String 
        get() = selectedConfig?.modelName ?: selectedService.defaultModel
    
    val apiKey: String 
        get() = selectedConfig?.apiKey ?: ""
        
    val openAiApiKey: String 
        get() = getConfigsForServiceType(AIServiceType.OPENAI).firstOrNull()?.apiKey ?: ""
        
    val temperature: Float 
        get() = selectedConfig?.temperature ?: 0.7f
    
    // Legacy compatibility - creates AIServiceConfig objects from dynamic configs
    val geminiConfig: AIServiceConfig
        get() = getConfigsForServiceType(AIServiceType.GEMINI).firstOrNull()?.let {
            AIServiceConfig(
                serviceType = it.serviceType,
                modelName = it.modelName,
                apiKey = it.apiKey,
                temperature = it.temperature
            )
        } ?: AIServiceConfig(
            serviceType = AIServiceType.GEMINI,
            modelName = AIServiceType.GEMINI.defaultModel,
            apiKey = "",
            temperature = 0.7f
        )
    
    val openAiConfig: AIServiceConfig
        get() = getConfigsForServiceType(AIServiceType.OPENAI).firstOrNull()?.let {
            AIServiceConfig(
                serviceType = it.serviceType,
                modelName = it.modelName,
                apiKey = it.apiKey,
                temperature = it.temperature
            )
        } ?: AIServiceConfig(
            serviceType = AIServiceType.OPENAI,
            modelName = AIServiceType.OPENAI.defaultModel,
            apiKey = "",
            temperature = 0.7f
        )
}

class PreferenceStore(private val context: Context) {
    companion object {
        private val Context.datastore: DataStore<Preferences> by preferencesDataStore(name = "APP_SETTING_KEY")

        // Keys for Color Mode
        private val COLOR_MODE_KEY = stringPreferencesKey("ColorMode")

        // AI Settings keys (dynamic system)
        private val SELECTED_AI_SERVICE_KEY = stringPreferencesKey("selected_ai_service")
        private val SELECTED_AI_PROVIDER_ID_KEY = stringPreferencesKey("selected_ai_provider_id")
        private val DYNAMIC_AI_CONFIGS_KEY = stringPreferencesKey("dynamic_ai_configs")
        val TRANSLATION_KEY = stringPreferencesKey("translation")

        // Key for AI Disclaimer Dialog
        val AI_DISCLAIMER_SHOWN_KEY = booleanPreferencesKey("ai_disclaimer_shown")

        // Keys for VOTD Cache
        val VOTD_REFERENCE_CACHE_KEY = stringPreferencesKey("votd_reference_cache")
        val VOTD_CONTENT_CACHE_KEY = stringPreferencesKey("votd_content_cache")
        val VOTD_CACHED_TRANSLATION_KEY = stringPreferencesKey("votd_cached_translation")
        val VOTD_LAST_FETCH_DATE_KEY = stringPreferencesKey("votd_last_fetch_date")

        // Default Settings
        const val DEFAULT_TRANSLATION = "ESV"
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

    // Save AI Settings (fully dynamic)
    suspend fun saveAISettings(aiSettings: AISettings) {
        Log.d("PreferenceStore", "Saving AI Settings: $aiSettings")
        context.datastore.edit { preferences ->
            // Save dynamic configurations as JSON
            try {
                val dynamicConfigsJson = Json.encodeToString(aiSettings.dynamicConfigs)
                preferences[DYNAMIC_AI_CONFIGS_KEY] = dynamicConfigsJson
            } catch (e: Exception) {
                // Fall back to empty map if serialization fails
                preferences[DYNAMIC_AI_CONFIGS_KEY] = "{}"
            }
            
            // Save selection settings
            preferences[SELECTED_AI_SERVICE_KEY] = aiSettings.selectedService.name
            preferences[SELECTED_AI_PROVIDER_ID_KEY] = aiSettings.selectedProviderId
        }
    }

    // Read AI Settings as a Flow (pure dynamic system)
    val aiSettingsFlow: Flow<AISettings> = context.datastore.data
        .catch { exception ->
            // dataStore.data throws an IOException when an error is encountered when reading data
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            // Read dynamic configurations from JSON
            val dynamicConfigs = try {
                val dynamicConfigsJson = preferences[DYNAMIC_AI_CONFIGS_KEY]
                if (dynamicConfigsJson != null && dynamicConfigsJson.isNotEmpty()) {
                    Json.decodeFromString<Map<String, DynamicAIConfig>>(dynamicConfigsJson)
                } else {
                    // Generate dynamic configs from registered providers if none exist
                    generateDynamicConfigsFromRegistry()
                }
            } catch (e: Exception) {
                // Fall back to generating from registry if deserialization fails
                generateDynamicConfigsFromRegistry()
            }
            
            // Read selection settings
            val selectedServiceString = preferences[SELECTED_AI_SERVICE_KEY]
            val selectedService = try {
                if (selectedServiceString != null) {
                    AIServiceType.valueOf(selectedServiceString)
                } else {
                    AIServiceType.GEMINI // Default to Gemini
                }
            } catch (_: IllegalArgumentException) {
                AIServiceType.GEMINI
            }
            
            // Determine selected provider based on service type if not explicitly set
            val selectedProviderId = preferences[SELECTED_AI_PROVIDER_ID_KEY] 
                ?: dynamicConfigs.values.firstOrNull { it.serviceType == selectedService }?.providerId
                ?: "gemini_ai"
            
            AISettings(
                selectedService = selectedService,
                selectedProviderId = selectedProviderId,
                dynamicConfigs = dynamicConfigs
            )
        }

    // Read AI Settings once (suspending function)
    suspend fun readAISettings(): AISettings {
        return aiSettingsFlow.first()
    }
    
    // Generate dynamic configs from registry providers
    private fun generateDynamicConfigsFromRegistry(): Map<String, DynamicAIConfig> {
        val dynamicConfigs = mutableMapOf<String, DynamicAIConfig>()
        
        try {
            // Get all registered AI providers from the registry
            val providers = AIServiceRegistry.getAllProviders()
            
            providers.forEach { provider ->
                val apiKey = when (provider.serviceType) {
                    AIServiceType.GEMINI -> BuildConfig.GEMINI_API_KEY
                    AIServiceType.OPENAI -> BuildConfig.OPENAI_API_KEY
                    AIServiceType.DEEPSEEK -> "" // No default API key for DeepSeek
                    AIServiceType.OLLAMA -> "" // No API key needed for local Ollama server
                }
                
                dynamicConfigs[provider.providerId] = DynamicAIConfig(
                    providerId = provider.providerId,
                    displayName = provider.displayName,
                    serviceType = provider.serviceType,
                    modelName = provider.defaultModel,
                    apiKey = apiKey,
                    temperature = 0.7f,
                    isEnabled = true
                )
            }
        } catch (e: Exception) {
            // Fall back to basic defaults if registry is not available
            dynamicConfigs["gemini_ai"] = DynamicAIConfig(
                providerId = "gemini_ai",
                displayName = "Gemini AI",
                serviceType = AIServiceType.GEMINI,
                modelName = AIServiceType.GEMINI.defaultModel,
                apiKey = BuildConfig.GEMINI_API_KEY,
                temperature = 0.7f,
                isEnabled = true
            )
            
            dynamicConfigs["openai"] = DynamicAIConfig(
                providerId = "openai",
                displayName = "OpenAI",
                serviceType = AIServiceType.OPENAI,
                modelName = AIServiceType.OPENAI.defaultModel,
                apiKey = BuildConfig.OPENAI_API_KEY,
                temperature = 0.7f,
                isEnabled = true
            )
        }
        
        return dynamicConfigs
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


