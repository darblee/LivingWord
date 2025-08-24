package com.darblee.livingword.data.remote

import android.util.Log
import com.darblee.livingword.AIServiceConfig
import com.darblee.livingword.AIServiceType
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse
import com.darblee.livingword.ui.viewmodels.ScoreData

/**
 * Provider implementation for OpenAI Service.
 * Wraps the existing OpenAIService to conform to the AIServiceProvider interface.
 */
class OpenAIServiceProvider : AIServiceProvider {
    
    override val providerId: String = "openai"
    override val displayName: String = "OpenAI"
    override val serviceType: AIServiceType = AIServiceType.OPENAI
    override val defaultModel: String = AIServiceType.OPENAI.defaultModel
    override val priority: Int = 20 // Lower priority than Gemini
    
    private var currentConfig: AIServiceConfig? = null
    private var initializationError: String? = null
    
    override fun configure(config: AIServiceConfig): Boolean {
        return try {
            currentConfig = config
            
            // Create AISettings for backward compatibility with existing OpenAIService
            val legacySettings = com.darblee.livingword.AISettings(
                selectedService = AIServiceType.OPENAI,
                geminiConfig = com.darblee.livingword.AIServiceConfig(
                    serviceType = AIServiceType.GEMINI,
                    modelName = AIServiceType.GEMINI.defaultModel,
                    apiKey = "",
                    temperature = 0.7f
                ),
                openAiConfig = config
            )
            
            OpenAIService.configure(legacySettings)
            initializationError = null
            
            val success = OpenAIService.isInitialized()
            Log.d("OpenAIServiceProvider", "Configuration result: $success")
            success
            
        } catch (e: Exception) {
            initializationError = "Failed to configure OpenAI: ${e.message}"
            Log.e("OpenAIServiceProvider", "Configuration failed", e)
            false
        }
    }
    
    override fun isInitialized(): Boolean {
        return OpenAIService.isInitialized()
    }
    
    override suspend fun test(): Boolean {
        return try {
            Log.d("OpenAIServiceProvider", "Running test")
            OpenAIService.test()
        } catch (e: Exception) {
            Log.e("OpenAIServiceProvider", "Test failed", e)
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
                AiServiceResult.Error("OpenAI provider not initialized")
            } else {
                OpenAIService.fetchScripture(verseRef, translation, systemInstruction, userPrompt)
            }
        } catch (e: Exception) {
            Log.e("OpenAIServiceProvider", "fetchScripture failed", e)
            AiServiceResult.Error("OpenAI error: ${e.message}", e)
        }
    }
    
    override suspend fun getKeyTakeaway(
        verseRef: String,
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<String> {
        return try {
            if (!isInitialized()) {
                AiServiceResult.Error("OpenAI provider not initialized")
            } else {
                OpenAIService.getKeyTakeaway(verseRef, systemInstruction, userPrompt)
            }
        } catch (e: Exception) {
            Log.e("OpenAIServiceProvider", "getKeyTakeaway failed", e)
            AiServiceResult.Error("OpenAI error: ${e.message}", e)
        }
    }
    
    override suspend fun getAIScore(
        verseRef: String,
        userApplicationComment: String,
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<ScoreData> {
        return try {
            if (!isInitialized()) {
                AiServiceResult.Error("OpenAI provider not initialized")
            } else {
                OpenAIService.getAIScore(verseRef, userApplicationComment, systemInstruction, userPrompt)
            }
        } catch (e: Exception) {
            Log.e("OpenAIServiceProvider", "getAIScore failed", e)
            AiServiceResult.Error("OpenAI error: ${e.message}", e)
        }
    }
    
    override suspend fun validateKeyTakeawayResponse(
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<Boolean> {
        return try {
            if (!isInitialized()) {
                AiServiceResult.Error("OpenAI provider not initialized")
            } else {
                OpenAIService.validateKeyTakeawayResponse(systemInstruction, userPrompt)
            }
        } catch (e: Exception) {
            Log.e("OpenAIServiceProvider", "validateKeyTakeawayResponse failed", e)
            AiServiceResult.Error("OpenAI error: ${e.message}", e)
        }
    }
    
    override suspend fun getNewVersesBasedOnDescription(
        description: String,
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<List<BibleVerseRef>> {
        return try {
            if (!isInitialized()) {
                AiServiceResult.Error("OpenAI provider not initialized")
            } else {
                OpenAIService.getNewVersesBasedOnDescription(description, systemInstruction, userPrompt)
            }
        } catch (e: Exception) {
            Log.e("OpenAIServiceProvider", "getNewVersesBasedOnDescription failed", e)
            AiServiceResult.Error("OpenAI error: ${e.message}", e)
        }
    }
    
    override fun getInitializationError(): String? {
        return initializationError ?: OpenAIService.getInitializationError()
    }
}