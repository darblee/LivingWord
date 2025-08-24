package com.darblee.livingword.data.remote

import android.util.Log
import com.darblee.livingword.AISettings
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse
import com.darblee.livingword.ui.viewmodels.ScoreData

/**
 * Centralized AI Service that manages multiple AI providers with fallback mechanisms.
 * Provides centralized prompts to ensure consistency across AI services.
 * Priority order: ESV → Gemini → OpenAI
 */
object AIService {
    
    // Centralized AI System Instructions
    object SystemInstructions {
        const val SCRIPTURE_SCHOLAR = "You are a Biblical scholar with deep knowledge of scripture. Your task is to provide accurate Bible verses in the requested translation format."
        const val TAKEAWAY_EXPERT = "You are a scripture expert, skilled at extracting key takeaways from religious texts. Your task is to analyze a given verse reference and provide the key takeaway."
        const val SCORING_EXPERT = "You are an expert in theology. You are an expert in analyzing Bible verses and determining the accuracy of direct quotes and their context."
        const val TAKEAWAY_VALIDATOR = "You are a theological text evaluator. You will be given a verse reference and a take-away text. Your task is to evaluate the take-away text in the context of the verse and respond accurately."
        const val VERSE_FINDER = "You are a Biblical scholar with comprehensive knowledge of scripture. Your task is to suggest relevant Bible verses based on topics or descriptions provided."
    }
    
    // Centralized AI User Prompts
    object UserPrompts {
        fun getScorePrompt(verseRef: String, directQuoteToEvaluate: String): String = """
            You will be provided with a Bible verse reference and a direct quote to evaluate.

            Bible verse reference: $verseRef
            Direct quote to evaluate: $directQuoteToEvaluate
            
            Follow these steps:
            
            1. Calculate the `ContextScore`: Evaluate the contextual accuracy of the direct quote. Consider whether the quote is used in a way that aligns with the original meaning and intent of the verse. Provide a score between 0 and 100.
            2. Provide `ContextExplanation`: Explain how you derived the `ContextScore`. Discuss the context of the verse and whether the provided quote aligns with that context.
            
            Respond ONLY in the following JSON format:
            
            {
            "DirectQuoteScore": 0,
            "ContextScore": integer between 0 to 100,
            "DirectQuoteExplanation": "",
            "ContextExplanation": "Explanation of the ContextScore",
            "ApplicationFeedback": ""
            }
            
            Note: DirectQuoteScore and DirectQuoteExplanation are hardcoded values and not used in evaluation.
            ApplicationFeedback will be populated separately.
            
            Ensure that your response is ONLY in JSON format with no other text or explanations outside of the JSON structure.
            """.trimIndent()
            
        fun getKeyTakeawayPrompt(verseRef: String): String = """
            Please provide a key takeaway or main message from the Bible verse $verseRef.
            
            Provide a concise, meaningful explanation of the verse's core message or teaching.
            Keep the response to 2-3 sentences maximum.
            Focus on practical application and spiritual significance.
            """.trimIndent()
            
        fun getApplicationFeedbackPrompt(verseRef: String, userApplicationComment: String): String = """
            Please provide feedback on how a user is applying the Bible verse $verseRef to their life.
            
            User's application: "$userApplicationComment"
            
            Provide constructive feedback that is:
            1. Insightful: Offer a deeper understanding of the verse and its implications.
            2. Encouraging: Affirm the user's efforts and provide motivation.
            """.trimIndent()
            
        fun getTakeawayValidationPrompt(verseRef: String, takeawayToEvaluate: String): String = """
            Please evaluate whether the following takeaway accurately represents the Bible verse $verseRef:
            
            Takeaway to evaluate: "$takeawayToEvaluate"
            
            Respond with only "true" if the takeaway is accurate and appropriate, or "false" if it misrepresents the verse.
            """.trimIndent()
            
        fun getVerseSearchPrompt(description: String): String = """
            Please suggest Bible verses that relate to the following description or topic: "$description"
            
            Return ONLY a JSON array of verse references in this exact format:
            [
                {"book": "BookName", "chapter": number, "startVerse": number, "endVerse": number}
            ]
            
            Suggest 3-5 relevant verses. Do not include any other text or explanations.
            """.trimIndent()
            
        fun getScripturePrompt(verseRef: BibleVerseRef, translation: String): String {
            return if (verseRef.startVerse == verseRef.endVerse) {
                """
                Please provide the Bible verse for ${verseRef.book} ${verseRef.chapter}:${verseRef.startVerse} in the $translation translation.

                Return ONLY a JSON array in the following format:
                [
                    {
                        "verse_num": ${verseRef.startVerse},
                        "verse_string": "verse_text"
                    }
                ]

                Do not include any other text or explanations.
                """.trimIndent()
            } else {
                """
                Please provide the Bible verses for ${verseRef.book} ${verseRef.chapter}:${verseRef.startVerse}-${verseRef.endVerse} in the $translation translation.

                Return ONLY a JSON array in the following format:
                [
                    {
                        "verse_num": verse_number,
                        "verse_string": "verse_text"
                    }
                ]

                Do not include any other text or explanations.
                """.trimIndent()
            }
        }
    }
    
    
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
     * Fetches scripture with priority order: ESV → Gemini → OpenAI
     */
    suspend fun fetchScripture(verseRef: BibleVerseRef, translation: String): AiServiceResult<List<Verse>> {
        if (!isConfigured) {
            return AiServiceResult.Error("AI service not configured: ${getInitializationError()}")
        }
        
        // Priority 1: Try ESV service first if translation is ESV
        if (translation.equals("ESV", ignoreCase = true)) {
            Log.d("AIService", "ESV translation requested. Attempting ESV service...")
            val esvService = ESVBibleLookupService()
            val esvResult = esvService.fetchScripture(verseRef)
            
            when (esvResult) {
                is AiServiceResult.Success -> {
                    Log.d("AIService", "Scripture fetch successful with ESV service")
                    return esvResult
                }
                is AiServiceResult.Error -> {
                    Log.w("AIService", "ESV service failed: ${esvResult.message}. Falling back to AI services.")
                }
            }
        }
        
        // Get centralized prompts
        val systemInstruction = SystemInstructions.SCRIPTURE_SCHOLAR
        val userPrompt = UserPrompts.getScripturePrompt(verseRef, translation)
        
        // Priority 2: Try Gemini AI
        if (GeminiAIService.isInitialized()) {
            Log.d("AIService", "Attempting scripture fetch with Gemini using centralized prompts...")
            val geminiResult = GeminiAIService.fetchScripture(verseRef, translation, systemInstruction, userPrompt)
            
            if (geminiResult is AiServiceResult.Success) {
                Log.d("AIService", "Scripture fetch successful with Gemini")
                return geminiResult
            } else {
                Log.w("AIService", "Gemini failed for scripture fetch: ${(geminiResult as AiServiceResult.Error).message}")
            }
        }
        
        // Priority 3: Fallback to OpenAI
        if (OpenAIService.isInitialized()) {
            Log.d("AIService", "Falling back to OpenAI for scripture fetch using centralized prompts...")
            val openAiResult = OpenAIService.fetchScripture(verseRef, translation, systemInstruction, userPrompt)
            
            if (openAiResult is AiServiceResult.Success) {
                Log.d("AIService", "Scripture fetch successful with OpenAI fallback")
            } else {
                Log.w("AIService", "OpenAI also failed for scripture fetch: ${(openAiResult as AiServiceResult.Error).message}")
            }
            
            return openAiResult
        }
        
        return AiServiceResult.Error("All scripture services (ESV, Gemini, OpenAI) are unavailable")
    }
    
    /**
     * Gets key takeaway with fallback mechanism.
     */
    suspend fun getKeyTakeaway(verseRef: String): AiServiceResult<String> {
        if (!isConfigured) {
            return AiServiceResult.Error("AI service not configured: ${getInitializationError()}")
        }
        
        // Get centralized prompts
        val systemInstruction = SystemInstructions.TAKEAWAY_EXPERT
        val userPrompt = UserPrompts.getKeyTakeawayPrompt(verseRef)
        
        // Try Gemini first with centralized prompts
        if (GeminiAIService.isInitialized()) {
            Log.d("AIService", "Attempting key takeaway with Gemini using centralized prompts...")
            val geminiResult = GeminiAIService.getKeyTakeaway(verseRef, systemInstruction, userPrompt)
            
            if (geminiResult is AiServiceResult.Success) {
                Log.d("AIService", "Key takeaway successful with Gemini")
                return geminiResult
            } else {
                Log.w("AIService", "Gemini failed for key takeaway: ${(geminiResult as AiServiceResult.Error).message}")
            }
        }
        
        // Fallback to OpenAI
        if (OpenAIService.isInitialized()) {
            Log.d("AIService", "Falling back to OpenAI for key takeaway using centralized prompts...")
            val openAiResult = OpenAIService.getKeyTakeaway(verseRef, systemInstruction, userPrompt)
            
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
        
        // Get centralized prompts
        val systemInstruction = SystemInstructions.SCORING_EXPERT
        val userPrompt = UserPrompts.getScorePrompt(verseRef, directQuoteToEvaluate)
        
        // Try Gemini first
        if (GeminiAIService.isInitialized()) {
            Log.d("AIService", "Attempting AI score with Gemini using centralized prompts...")
            val geminiResult = GeminiAIService.getAIScore(verseRef, userApplicationComment, systemInstruction, userPrompt)
            
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
            Log.d("AIService", "Falling back to OpenAI for AI score using centralized prompts...")
            val openAiResult = OpenAIService.getAIScore(verseRef, userApplicationComment, systemInstruction, userPrompt)
            
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
     * Validates key takeaway response with fallback mechanism.
     */
    suspend fun validateKeyTakeawayResponse(verseRef: String, takeawayToEvaluate: String): AiServiceResult<Boolean> {
        if (!isConfigured) {
            return AiServiceResult.Error("AI service not configured: ${getInitializationError()}")
        }
        
        // Get centralized prompts
        val systemInstruction = SystemInstructions.TAKEAWAY_VALIDATOR
        val userPrompt = UserPrompts.getTakeawayValidationPrompt(verseRef, takeawayToEvaluate)
        
        // Try Gemini first
        if (GeminiAIService.isInitialized()) {
            Log.d("AIService", "Attempting takeaway validation with Gemini using centralized prompts...")
            val geminiResult = GeminiAIService.validateKeyTakeawayResponse(systemInstruction, userPrompt)
            
            if (geminiResult is AiServiceResult.Success) {
                Log.d("AIService", "Takeaway validation successful with Gemini")
                return geminiResult
            } else {
                Log.w("AIService", "Gemini failed for takeaway validation: ${(geminiResult as AiServiceResult.Error).message}")
            }
        }
        
        // Fallback to OpenAI
        if (OpenAIService.isInitialized()) {
            Log.d("AIService", "Falling back to OpenAI for takeaway validation using centralized prompts...")
            val openAiResult = OpenAIService.validateKeyTakeawayResponse(systemInstruction, userPrompt)
            
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
        
        // Get centralized prompts
        val systemInstruction = SystemInstructions.VERSE_FINDER
        val userPrompt = UserPrompts.getVerseSearchPrompt(description)
        
        // Try Gemini first
        if (GeminiAIService.isInitialized()) {
            Log.d("AIService", "Attempting verse search with Gemini using centralized prompts...")
            val geminiResult = GeminiAIService.getNewVersesBasedOnDescription(description, systemInstruction, userPrompt)
            
            if (geminiResult is AiServiceResult.Success) {
                Log.d("AIService", "Verse search successful with Gemini")
                return geminiResult
            } else {
                Log.w("AIService", "Gemini failed for verse search: ${(geminiResult as AiServiceResult.Error).message}")
            }
        }
        
        // Fallback to OpenAI
        if (OpenAIService.isInitialized()) {
            Log.d("AIService", "Falling back to OpenAI for verse search using centralized prompts...")
            val openAiResult = OpenAIService.getNewVersesBasedOnDescription(description, systemInstruction, userPrompt)
            
            if (openAiResult is AiServiceResult.Success) {
                Log.d("AIService", "Verse search successful with OpenAI fallback")
            } else {
                Log.w("AIService", "OpenAI also failed for verse search: ${(openAiResult as AiServiceResult.Error).message}")
            }
            
            return openAiResult
        }
        
        return AiServiceResult.Error("Both Gemini and OpenAI services are unavailable")
    }
}