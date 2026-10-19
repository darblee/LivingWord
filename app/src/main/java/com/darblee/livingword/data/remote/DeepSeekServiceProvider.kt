package com.darblee.livingword.data.remote

import android.util.Log
import com.darblee.livingword.AIServiceConfig
import com.darblee.livingword.AIServiceType
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse
import com.darblee.livingword.ui.viewmodels.ScoreData
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

class DeepSeekServiceProvider : AIServiceProvider {

    override val providerId: String = "deepseek_ai"
    override val displayName: String = "DeepSeek AI"
    override val serviceType: AIServiceType = AIServiceType.DEEPSEEK
    override val defaultModel: String = "deepseek-chat"
    override val priority: Int = 100

    private var initializationError: String? = null

    override fun configure(config: AIServiceConfig): Boolean {
        return try {
            if (config.apiKey.isEmpty()) {
                initializationError = "DeepSeek API key is required"
                Log.e("DeepSeekServiceProvider", "Configuration failed: API key missing")
                return false
            }
            DeepSeekService.configure(config.apiKey, config.modelName)
            initializationError = null
            val success = DeepSeekService.isInitialized()
            Log.d("DeepSeekServiceProvider", "Configuration result: $success")
            success
        } catch (e: Exception) {
            initializationError = "Failed to configure DeepSeek AI: ${e.message}"
            Log.e("DeepSeekServiceProvider", "Configuration failed", e)
            false
        }
    }

    override fun isInitialized(): Boolean = DeepSeekService.isInitialized()

    override suspend fun test(): Boolean {
        return try {
            DeepSeekService.test()
        } catch (e: Exception) {
            Log.e("DeepSeekServiceProvider", "Test failed", e)
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
            if (!isInitialized()) return AiServiceResult.Error("DeepSeek provider not initialized")
            withTimeoutOrNull(30000L.milliseconds) {
                DeepSeekService.fetchScripture(verseRef, translation, systemInstruction, userPrompt)
            } ?: AiServiceResult.Error("DeepSeek error: Timed out after 30 seconds")
        } catch (e: Exception) {
            Log.e("DeepSeekServiceProvider", "fetchScripture failed", e)
            AiServiceResult.Error("DeepSeek error: ${e.message}", e)
        }
    }

    override suspend fun getKeyTakeaway(
        verseRef: String,
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<String> {
        return try {
            if (!isInitialized()) return AiServiceResult.Error("DeepSeek provider not initialized")
            withTimeoutOrNull(30000L.milliseconds) {
                DeepSeekService.getKeyTakeaway(verseRef, systemInstruction, userPrompt)
            } ?: AiServiceResult.Error("DeepSeek error: Timed out after 30 seconds")
        } catch (e: Exception) {
            Log.e("DeepSeekServiceProvider", "getKeyTakeaway failed", e)
            AiServiceResult.Error("DeepSeek error: ${e.message}", e)
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
            if (!isInitialized()) return AiServiceResult.Error("DeepSeek provider not initialized")
            withTimeoutOrNull(30000L.milliseconds) {
                DeepSeekService.getAIScore(verseRef, userApplicationComment, systemInstruction, userPrompt, applicationFeedbackPrompt)
            } ?: AiServiceResult.Error("DeepSeek error: Timed out after 30 seconds")
        } catch (e: Exception) {
            Log.e("DeepSeekServiceProvider", "getAIScore failed", e)
            AiServiceResult.Error("DeepSeek error: ${e.message}", e)
        }
    }

    override suspend fun validateKeyTakeawayResponse(
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<Boolean> {
        return try {
            if (!isInitialized()) return AiServiceResult.Error("DeepSeek provider not initialized")
            withTimeoutOrNull(30000L.milliseconds) {
                DeepSeekService.validateKeyTakeawayResponse(systemInstruction, userPrompt)
            } ?: AiServiceResult.Error("DeepSeek error: Timed out after 30 seconds")
        } catch (e: Exception) {
            Log.e("DeepSeekServiceProvider", "validateKeyTakeawayResponse failed", e)
            AiServiceResult.Error("DeepSeek error: ${e.message}", e)
        }
    }

    override suspend fun getNewVersesBasedOnDescription(
        description: String,
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<List<BibleVerseRef>> {
        return try {
            if (!isInitialized()) return AiServiceResult.Error("DeepSeek provider not initialized")
            withTimeoutOrNull(30000L.milliseconds) {
                DeepSeekService.getNewVersesBasedOnDescription(description, systemInstruction, userPrompt)
            } ?: AiServiceResult.Error("DeepSeek error: Timed out after 30 seconds")
        } catch (e: Exception) {
            Log.e("DeepSeekServiceProvider", "getNewVersesBasedOnDescription failed", e)
            AiServiceResult.Error("DeepSeek error: ${e.message}", e)
        }
    }

    override fun getInitializationError(): String? {
        return initializationError ?: DeepSeekService.getInitializationError()
    }
}
