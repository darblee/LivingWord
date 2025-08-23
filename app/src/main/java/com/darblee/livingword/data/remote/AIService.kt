package com.darblee.livingword.data.remote

import android.util.Log
import com.darblee.livingword.AISettings
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse
import com.darblee.livingword.ui.viewmodels.ScoreData

/**
 * Hybrid AI Service that uses Gemini as primary and OpenAI as fallback.
 * Automatically switches to OpenAI when Gemini fails due to quota or other issues.
 */
object AIService {
    
    private var isConfigured = false
    private var initializationErrorMessage: String? = null
    private var currentSettings: AISettings? = null
    
    /**
     * Configures both services with the provided settings.
     */
    fun configure(settings: AISettings) {
        try {
            currentSettings = settings
            
            // Configure both services
            GeminiAIService.configure(settings)
            OpenAIService.configure(settings)
            
            val geminiReady = GeminiAIService.isInitialized()
            val openAiReady = OpenAIService.isInitialized()
            
            if (geminiReady || openAiReady) {
                isConfigured = true
                initializationErrorMessage = null
                Log.i("AIService", "AI service configured - Gemini: $geminiReady, OpenAI: $openAiReady")
            } else {
                initializationErrorMessage = "Both Gemini and OpenAI services failed to initialize"
                isConfigured = false
                Log.e("AIService", "Configuration failed: Neither service is available")
            }
            
        } catch (e: Exception) {
            initializationErrorMessage = "Failed to initialize AI service: ${e.localizedMessage}"
            isConfigured = false
            Log.e("AIService", "Configuration error", e)
        }
    }
    
    /**
     * Checks if at least one service is properly initialized.
     */
    fun isInitialized(): Boolean = isConfigured && (GeminiAIService.isInitialized() || OpenAIService.isInitialized())
    
    /**
     * Gets the initialization error message, if any.
     */
    fun getInitializationError(): String? = initializationErrorMessage
    
    /**
     * Fetches scripture with fallback mechanism.
     */
    suspend fun fetchScripture(verseRef: BibleVerseRef, translation: String): AiServiceResult<List<Verse>> {
        if (!isConfigured) {
            return AiServiceResult.Error("AI service not configured: ${getInitializationError()}")
        }
        
        // Try Gemini first
        if (GeminiAIService.isInitialized()) {
            Log.d("AIService", "Attempting scripture fetch with Gemini...")
            val geminiResult = GeminiAIService.fetchScripture(verseRef, translation)
            
            if (geminiResult is AiServiceResult.Success) {
                Log.d("AIService", "Scripture fetch successful with Gemini")
                return geminiResult
            } else {
                Log.w("AIService", "Gemini failed for scripture fetch: ${(geminiResult as AiServiceResult.Error).message}")
            }
        }
        
        // Fallback to OpenAI
        if (OpenAIService.isInitialized()) {
            Log.d("AIService", "Falling back to OpenAI for scripture fetch...")
            val openAiResult = OpenAIService.fetchScripture(verseRef, translation)
            
            if (openAiResult is AiServiceResult.Success) {
                Log.d("AIService", "Scripture fetch successful with OpenAI fallback")
            } else {
                Log.w("AIService", "OpenAI also failed for scripture fetch: ${(openAiResult as AiServiceResult.Error).message}")
            }
            
            return openAiResult
        }
        
        return AiServiceResult.Error("Both Gemini and OpenAI services are unavailable")
    }
    
    /**
     * Gets key takeaway with fallback mechanism.
     */
    suspend fun getKeyTakeaway(verseRef: String): AiServiceResult<String> {
        if (!isConfigured) {
            return AiServiceResult.Error("AI service not configured: ${getInitializationError()}")
        }
        
        // Try Gemini first
        if (GeminiAIService.isInitialized()) {
            Log.d("AIService", "Attempting key takeaway with Gemini...")
            val geminiResult = GeminiAIService.getKeyTakeaway(verseRef)
            
            if (geminiResult is AiServiceResult.Success) {
                Log.d("AIService", "Key takeaway successful with Gemini")
                return geminiResult
            } else {
                Log.w("AIService", "Gemini failed for key takeaway: ${(geminiResult as AiServiceResult.Error).message}")
            }
        }
        
        // Fallback to OpenAI
        if (OpenAIService.isInitialized()) {
            Log.d("AIService", "Falling back to OpenAI for key takeaway...")
            val openAiResult = OpenAIService.getKeyTakeaway(verseRef)
            
            if (openAiResult is AiServiceResult.Success) {
                Log.d("AIService", "Key takeaway successful with OpenAI fallback")
            } else {
                Log.w("AIService", "OpenAI also failed for key takeaway: ${(openAiResult as AiServiceResult.Error).message}")
            }
            
            return openAiResult
        }
        
        return AiServiceResult.Error("Both Gemini and OpenAI services are unavailable")
    }
    
    /**
     * Gets AI score with fallback mechanism.
     * This is the most important method for the engagement feature.
     */
    suspend fun getAIScore(verseRef: String, directQuoteToEvaluate: String, userApplicationComment: String): AiServiceResult<ScoreData> {
        if (!isConfigured) {
            return AiServiceResult.Error("AI service not configured: ${getInitializationError()}")
        }
        
        // Try Gemini first
        if (GeminiAIService.isInitialized()) {
            Log.d("AIService", "Attempting AI score with Gemini...")
            val geminiResult = GeminiAIService.getAIScore(verseRef, directQuoteToEvaluate, userApplicationComment)
            
            if (geminiResult is AiServiceResult.Success) {
                Log.d("AIService", "AI score successful with Gemini")
                return geminiResult
            } else {
                val errorMessage = (geminiResult as AiServiceResult.Error).message
                Log.w("AIService", "Gemini failed for AI score: $errorMessage")
                
                // Check if it's a quota exceeded error specifically
                if (errorMessage.contains("quota", ignoreCase = true) || 
                    errorMessage.contains("exceeded", ignoreCase = true) ||
                    errorMessage.contains("rate limit", ignoreCase = true)) {
                    Log.i("AIService", "Detected quota/rate limit issue with Gemini, switching to OpenAI")
                }
            }
        }
        
        // Fallback to OpenAI
        if (OpenAIService.isInitialized()) {
            Log.d("AIService", "Falling back to OpenAI for AI score...")
            val openAiResult = OpenAIService.getAIScore(verseRef, directQuoteToEvaluate, userApplicationComment)
            
            if (openAiResult is AiServiceResult.Success) {
                Log.i("AIService", "AI score successful with OpenAI fallback")
            } else {
                Log.w("AIService", "OpenAI also failed for AI score: ${(openAiResult as AiServiceResult.Error).message}")
            }
            
            return openAiResult
        }
        
        return AiServiceResult.Error("Both Gemini and OpenAI services are unavailable")
    }
    
    /**
     * Gets application feedback with fallback mechanism.
     */
    suspend fun getApplicationFeedback(verseRef: String, userApplicationComment: String): String {
        if (!isConfigured) {
            Log.w("AIService", "Service not configured, returning empty feedback")
            return ""
        }
        
        // Try Gemini first
        if (GeminiAIService.isInitialized()) {
            try {
                val geminiResult = GeminiAIService.getApplicationFeedback(verseRef, userApplicationComment)
                if (geminiResult.isNotBlank()) {
                    Log.d("AIService", "Application feedback successful with Gemini")
                    return geminiResult
                }
            } catch (e: Exception) {
                Log.w("AIService", "Gemini failed for application feedback: ${e.message}")
            }
        }
        
        // Fallback to OpenAI
        if (OpenAIService.isInitialized()) {
            try {
                val openAiResult = OpenAIService.getApplicationFeedback(verseRef, userApplicationComment)
                if (openAiResult.isNotBlank()) {
                    Log.d("AIService", "Application feedback successful with OpenAI fallback")
                    return openAiResult
                } else {
                    Log.w("AIService", "OpenAI returned empty feedback")
                }
            } catch (e: Exception) {
                Log.w("AIService", "OpenAI also failed for application feedback: ${e.message}")
            }
        }
        
        Log.w("AIService", "Both services failed for application feedback")
        return ""
    }
    
    /**
     * Validates key takeaway response with fallback mechanism.
     */
    suspend fun validateKeyTakeawayResponse(verseRef: String, takeawayToEvaluate: String): AiServiceResult<Boolean> {
        if (!isConfigured) {
            return AiServiceResult.Error("AI service not configured: ${getInitializationError()}")
        }
        
        // Try Gemini first
        if (GeminiAIService.isInitialized()) {
            Log.d("AIService", "Attempting takeaway validation with Gemini...")
            val geminiResult = GeminiAIService.validateKeyTakeawayResponse(verseRef, takeawayToEvaluate)
            
            if (geminiResult is AiServiceResult.Success) {
                Log.d("AIService", "Takeaway validation successful with Gemini")
                return geminiResult
            } else {
                Log.w("AIService", "Gemini failed for takeaway validation: ${(geminiResult as AiServiceResult.Error).message}")
            }
        }
        
        // Fallback to OpenAI
        if (OpenAIService.isInitialized()) {
            Log.d("AIService", "Falling back to OpenAI for takeaway validation...")
            val openAiResult = OpenAIService.validateKeyTakeawayResponse(verseRef, takeawayToEvaluate)
            
            if (openAiResult is AiServiceResult.Success) {
                Log.d("AIService", "Takeaway validation successful with OpenAI fallback")
            } else {
                Log.w("AIService", "OpenAI also failed for takeaway validation: ${(openAiResult as AiServiceResult.Error).message}")
            }
            
            return openAiResult
        }
        
        return AiServiceResult.Error("Both Gemini and OpenAI services are unavailable")
    }
    
    /**
     * Gets new verses based on description with fallback mechanism.
     */
    suspend fun getNewVersesBasedOnDescription(description: String): AiServiceResult<List<BibleVerseRef>> {
        if (!isConfigured) {
            return AiServiceResult.Error("AI service not configured: ${getInitializationError()}")
        }
        
        // Try Gemini first
        if (GeminiAIService.isInitialized()) {
            Log.d("AIService", "Attempting verse search with Gemini...")
            val geminiResult = GeminiAIService.getNewVersesBasedOnDescription(description)
            
            if (geminiResult is AiServiceResult.Success) {
                Log.d("AIService", "Verse search successful with Gemini")
                return geminiResult
            } else {
                Log.w("AIService", "Gemini failed for verse search: ${(geminiResult as AiServiceResult.Error).message}")
            }
        }
        
        // Fallback to OpenAI
        if (OpenAIService.isInitialized()) {
            Log.d("AIService", "Falling back to OpenAI for verse search...")
            val openAiResult = OpenAIService.getNewVersesBasedOnDescription(description)
            
            if (openAiResult is AiServiceResult.Success) {
                Log.d("AIService", "Verse search successful with OpenAI fallback")
            } else {
                Log.w("AIService", "OpenAI also failed for verse search: ${(openAiResult as AiServiceResult.Error).message}")
            }
            
            return openAiResult
        }
        
        return AiServiceResult.Error("Both Gemini and OpenAI services are unavailable")
    }
    
    /**
     * Gets the currently active service name for debugging/logging.
     */
    fun getActiveServiceStatus(): String {
        val geminiStatus = if (GeminiAIService.isInitialized()) "✓" else "✗"
        val openAiStatus = if (OpenAIService.isInitialized()) "✓" else "✗"
        return "Gemini: $geminiStatus, OpenAI: $openAiStatus"
    }
}