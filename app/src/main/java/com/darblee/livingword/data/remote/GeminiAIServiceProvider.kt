package com.darblee.livingword.data.remote

import android.util.Log
import com.darblee.livingword.AIServiceConfig
import com.darblee.livingword.AIServiceType
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse
import com.darblee.livingword.ui.viewmodels.ScoreData

/**
 * Provider implementation for Gemini AI Service.
 * Wraps the existing GeminiAIService to conform to the AIServiceProvider interface.
 */
class GeminiAIServiceProvider : AIServiceProvider {
    
    override val providerId: String = "gemini_ai"
    override val displayName: String = "Gemini AI"
    override val serviceType: AIServiceType = AIServiceType.GEMINI
    override val defaultModel: String = AIServiceType.GEMINI.defaultModel
    override val priority: Int = 10 // Higher priority than OpenAI
    
    private var currentConfig: AIServiceConfig? = null
    private var initializationError: String? = null
    
    override fun configure(config: AIServiceConfig): Boolean {
        return try {
            currentConfig = config
            
            // Create AISettings for backward compatibility with existing GeminiAIService
            val legacySettings = com.darblee.livingword.AISettings(
                selectedService = AIServiceType.GEMINI,
                geminiConfig = config,
                openAiConfig = com.darblee.livingword.AIServiceConfig(
                    serviceType = AIServiceType.OPENAI,
                    modelName = AIServiceType.OPENAI.defaultModel,
                    apiKey = "",
                    temperature = 0.7f
                )
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
                GeminiAIService.fetchScripture(verseRef, translation, systemInstruction, userPrompt)
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
                GeminiAIService.getKeyTakeaway(verseRef, systemInstruction, userPrompt)
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
                GeminiAIService.getAIScore(verseRef, userApplicationComment, systemInstruction, userPrompt, applicationFeedbackPrompt)
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
                GeminiAIService.validateKeyTakeawayResponse(systemInstruction, userPrompt)
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
                GeminiAIService.getNewVersesBasedOnDescription(description, systemInstruction, userPrompt)
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