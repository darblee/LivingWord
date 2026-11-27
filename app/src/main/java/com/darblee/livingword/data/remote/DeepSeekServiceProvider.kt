package com.darblee.livingword.data.remote

import android.util.Log
import com.darblee.livingword.AIServiceConfig
import com.darblee.livingword.AIServiceType
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse
import com.darblee.livingword.ui.viewmodels.ScoreData
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * AIServiceProvider implementation that delegates to [DeepSeekService] for all AI operations.
 * Wraps each call with a 30-second timeout and translates exceptions into [AiServiceResult.Error].
 */
class DeepSeekServiceProvider : AIServiceProvider {

    override val providerId: String = "deepseek_ai"
    override val displayName: String = "DeepSeek AI"
    override val serviceType: AIServiceType = AIServiceType.DEEPSEEK
    override val defaultModel: String = "deepseek-chat"
    override val priority: Int = 100

    private var initializationError: String? = null

    /** Validates the API key and forwards configuration to [DeepSeekService]. Returns false on empty key or any exception. */
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

    /** Delegates initialization check directly to [DeepSeekService]. */
    override fun isInitialized(): Boolean = DeepSeekService.isInitialized()

    /** Sends a lightweight connectivity test to DeepSeek. Returns false if the service is unreachable or throws. */
    override suspend fun test(): Boolean {
        return try {
            DeepSeekService.test()
        } catch (e: Exception) {
            Log.e("DeepSeekServiceProvider", "Test failed", e)
            false
        }
    }

    /**
     * Fetches scripture verses for a given reference via DeepSeek, timing out after 30 seconds.
     *
     * @param verseRef The book, chapter, and verse range to retrieve.
     * @param translation Bible translation code (e.g. "ESV") passed to the AI prompt.
     * @param systemInstruction System-role prompt that sets the AI's behavior and output format.
     * @param userPrompt User-role prompt containing the specific fetch request.
     * @return [AiServiceResult.Success] with the parsed verse list, or [AiServiceResult.Error] on timeout or failure.
     */
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

    /**
     * Requests a key takeaway summary for a scripture verse via DeepSeek, timing out after 30 seconds.
     *
     * @param verseRef Human-readable verse reference string (e.g. "John 3:16").
     * @param systemInstruction System-role prompt that sets the AI's behaviour and output format.
     * @param userPrompt User-role prompt specifying what takeaway to generate.
     * @return [AiServiceResult.Success] with the takeaway text, or [AiServiceResult.Error] on timeout or failure.
     */
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

    /**
     * Scores the user's application comment against a scripture verse via DeepSeek, timing out after 30 seconds.
     *
     * @param verseRef Human-readable verse reference string (e.g. "Romans 8:28").
     * @param userApplicationComment The user's written reflection or application of the verse being evaluated.
     * @param systemInstruction System-role prompt that sets the AI's scoring behaviour and output format.
     * @param userPrompt User-role prompt containing the scoring request.
     * @param applicationFeedbackPrompt Additional prompt used to request written feedback alongside the score.
     * @return [AiServiceResult.Success] with a [ScoreData] containing the numeric score and feedback, or [AiServiceResult.Error] on timeout or failure.
     */
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

    /**
     * Asks DeepSeek to validate whether a previously generated key takeaway response is acceptable, timing out after 30 seconds.
     *
     * @param systemInstruction System-role prompt that sets the AI's validation criteria and output format.
     * @param userPrompt User-role prompt containing the takeaway text to be validated.
     * @return [AiServiceResult.Success] with `true` if the response is valid, `false` if not, or [AiServiceResult.Error] on timeout or failure.
     */
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

    /**
     * Asks DeepSeek to suggest new scripture verses that match a user-supplied description, timing out after 30 seconds.
     *
     * @param description Free-text description of the topic, theme, or concept to find verses for.
     * @param systemInstruction System-role prompt that sets the AI's search behaviour and output format.
     * @param userPrompt User-role prompt containing the verse discovery request.
     * @return [AiServiceResult.Success] with a list of matching [BibleVerseRef] objects, or [AiServiceResult.Error] on timeout or failure.
     */
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

    /** Returns the most recent configuration error message, checking the local field first then falling back to [DeepSeekService]. */
    override fun getInitializationError(): String? {
        return initializationError ?: DeepSeekService.getInitializationError()
    }
}
