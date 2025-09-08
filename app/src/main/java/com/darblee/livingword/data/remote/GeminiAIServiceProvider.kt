package com.darblee.livingword.data.remote

import android.util.Log
import com.darblee.livingword.AIServiceConfig
import com.darblee.livingword.AIServiceType
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse
import com.darblee.livingword.ui.viewmodels.ScoreData
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Provider implementation for Gemini AI Service.
 * Wraps the existing GeminiAIService to conform to the AIServiceProvider interface.
 */
class GeminiAIServiceProvider : AIServiceProvider {
    
    override val providerId: String = "gemini_ai"
    override val displayName: String = "Gemini AI"
    override val serviceType: AIServiceType = AIServiceType.GEMINI
    override val defaultModel: String = AIServiceType.GEMINI.defaultModel
    override val priority: Int = 3
    
    private var currentConfig: AIServiceConfig? = null
    private var initializationError: String? = null
    
    override fun configure(config: AIServiceConfig): Boolean {
        return try {
            Log.d("GeminiAIServiceProvider", "Received config - ServiceType: ${config.serviceType}, Model: ${config.modelName}, ApiKey length: ${config.apiKey.length}")
            currentConfig = config
            
            // Create AISettings for backward compatibility with existing GeminiAIService
            val geminiDynamicConfig = com.darblee.livingword.DynamicAIConfig(
                providerId = "gemini_ai",
                displayName = "Gemini AI",
                serviceType = config.serviceType,
                modelName = config.modelName,
                apiKey = config.apiKey,
                temperature = config.temperature,
                isEnabled = true
            )
            
            val legacySettings = com.darblee.livingword.AISettings(
                selectedService = AIServiceType.GEMINI,
                selectedProviderId = "gemini_ai",
                dynamicConfigs = mapOf("gemini_ai" to geminiDynamicConfig)
            )
            
            GeminiAIService.configure(legacySettings)
            initializationError = null
            
            val success = GeminiAIService.isInitialized()
            Log.d("GeminiAIServiceProvider", "Configuration result: $success")
            success
            
        } catch (e: Exception) {
            initializationError = "Failed to configure Gemini AI: ${e.message}"
            Log.e("GeminiAIServiceProvider", "Configuration failed", e)
            false
        }
    }
    
    override fun isInitialized(): Boolean {
        return GeminiAIService.isInitialized()
    }
    
    override suspend fun test(): Boolean {
        return try {
            Log.d("GeminiAIServiceProvider", "Running test")
            GeminiAIService.test()
        } catch (e: Exception) {
            Log.e("GeminiAIServiceProvider", "Test failed", e)
            false
        }
    }
    
    override suspend fun fetchScripture(
        verseRef: BibleVerseRef,
        translation: String,
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<List<Verse>> {
        return try {
            if (!isInitialized()) {
                AiServiceResult.Error("Gemini AI provider not initialized")
            } else {
                withTimeoutOrNull(10000L) {
                    GeminiAIService.fetchScripture(verseRef, translation, systemInstruction, userPrompt)
                } ?: AiServiceResult.Error("Gemini AI error: Timed out waiting for 10000 ms")
            }
        } catch (e: Exception) {
            Log.e("GeminiAIServiceProvider", "fetchScripture failed", e)
            AiServiceResult.Error("Gemini AI error: ${e.message}", e)
        }
    }
    
    override suspend fun getKeyTakeaway(
        verseRef: String,
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<String> {
        return try {
            if (!isInitialized()) {
                AiServiceResult.Error("Gemini AI provider not initialized")
            } else {
                withTimeoutOrNull(10000L) {
                    GeminiAIService.getKeyTakeaway(verseRef, systemInstruction, userPrompt)
                } ?: AiServiceResult.Error("Gemini AI error: Timed out waiting for 10000 ms")
            }
        } catch (e: Exception) {
            Log.e("GeminiAIServiceProvider", "getKeyTakeaway failed", e)
            AiServiceResult.Error("Gemini AI error: ${e.message}", e)
        }
    }
    
    override suspend fun getAIScore(
        verseRef: String,
        userApplicationComment: String,
        systemInstruction: String,
        userPrompt: String,
        applicationFeedbackPrompt: String
    ): AiServiceResult<ScoreData> {
        return try {
            if (!isInitialized()) {
                AiServiceResult.Error("Gemini AI provider not initialized")
            } else {
                withTimeoutOrNull(10000L) {
                    GeminiAIService.getAIScore(verseRef, userApplicationComment, systemInstruction, userPrompt, applicationFeedbackPrompt)
                } ?: AiServiceResult.Error("Gemini AI error: Timed out waiting for 10000 ms")
            }
        } catch (e: Exception) {
            Log.e("GeminiAIServiceProvider", "getAIScore failed", e)
            AiServiceResult.Error("Gemini AI error: ${e.message}", e)
        }
    }
    
    override suspend fun validateKeyTakeawayResponse(
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<Boolean> {
        return try {
            if (!isInitialized()) {
                AiServiceResult.Error("Gemini AI provider not initialized")
            } else {
                withTimeoutOrNull(10000L) {
                    GeminiAIService.validateKeyTakeawayResponse(systemInstruction, userPrompt)
                } ?: AiServiceResult.Error("Gemini AI error: Timed out waiting for 10000 ms")
            }
        } catch (e: Exception) {
            Log.e("GeminiAIServiceProvider", "validateKeyTakeawayResponse failed", e)
            AiServiceResult.Error("Gemini AI error: ${e.message}", e)
        }
    }
    
    override suspend fun getNewVersesBasedOnDescription(
        description: String,
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<List<BibleVerseRef>> {
        return try {
            if (!isInitialized()) {
                AiServiceResult.Error("Gemini AI provider not initialized")
            } else {
                withTimeoutOrNull(10000L) {
                    GeminiAIService.getNewVersesBasedOnDescription(description, systemInstruction, userPrompt)
                } ?: AiServiceResult.Error("Gemini AI error: Timed out waiting for 10000 ms")
            }
        } catch (e: Exception) {
            Log.e("GeminiAIServiceProvider", "getNewVersesBasedOnDescription failed", e)
            AiServiceResult.Error("Gemini AI error: ${e.message}", e)
        }
    }
    
    override fun getInitializationError(): String? {
        return initializationError ?: GeminiAIService.getInitializationError()
    }
}