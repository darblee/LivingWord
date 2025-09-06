package com.darblee.livingword.data.remote

import android.util.Log
import com.darblee.livingword.AIServiceConfig
import com.darblee.livingword.AIServiceType
import com.darblee.livingword.DynamicAIConfig
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse
import com.darblee.livingword.ui.viewmodels.ScoreData
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Provider implementation for OpenAI Service.
 * Wraps the existing OpenAIService to conform to the AIServiceProvider interface.
 */
class OpenAIServiceProvider : AIServiceProvider {
    
    override val providerId: String = "openai"
    override val displayName: String = "OpenAI"
    override val serviceType: AIServiceType = AIServiceType.OPENAI
    override val defaultModel: String = AIServiceType.OPENAI.defaultModel
    override val priority: Int = 2
    
    private var currentConfig: AIServiceConfig? = null
    private var initializationError: String? = null
    
    override fun configure(config: AIServiceConfig): Boolean {
        return try {
            currentConfig = config
            
            // Create AISettings for backward compatibility with existing OpenAIService
            val openAiDynamicConfig = DynamicAIConfig(
                providerId = "openai",
                displayName = "OpenAI",
                serviceType = config.serviceType,
                modelName = config.modelName,
                apiKey = config.apiKey,
                temperature = config.temperature,
                isEnabled = true
            )
            
            val legacySettings = com.darblee.livingword.AISettings(
                selectedService = AIServiceType.OPENAI,
                selectedProviderId = "openai",
                dynamicConfigs = mapOf("openai" to openAiDynamicConfig)
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
                withTimeoutOrNull(10000L) {
                    OpenAIService.fetchScripture(verseRef, translation, systemInstruction, userPrompt)
                } ?: AiServiceResult.Error("OpenAI error: Timed out waiting for 10000 ms")
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
                withTimeoutOrNull(10000L) {
                    OpenAIService.getKeyTakeaway(verseRef, systemInstruction, userPrompt)
                } ?: AiServiceResult.Error("OpenAI error: Timed out waiting for 10000 ms")
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
        userPrompt: String,
        applicationFeedbackPrompt: String
    ): AiServiceResult<ScoreData> {
        return try {
            if (!isInitialized()) {
                AiServiceResult.Error("OpenAI provider not initialized")
            } else {
                withTimeoutOrNull(10000L) {
                    OpenAIService.getAIScore(verseRef, userApplicationComment, systemInstruction, userPrompt, applicationFeedbackPrompt)
                } ?: AiServiceResult.Error("OpenAI error: Timed out waiting for 10000 ms")
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
                withTimeoutOrNull(10000L) {
                    OpenAIService.validateKeyTakeawayResponse(systemInstruction, userPrompt)
                } ?: AiServiceResult.Error("OpenAI error: Timed out waiting for 10000 ms")
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
                withTimeoutOrNull(10000L) {
                    OpenAIService.getNewVersesBasedOnDescription(description, systemInstruction, userPrompt)
                } ?: AiServiceResult.Error("OpenAI error: Timed out waiting for 10000 ms")
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