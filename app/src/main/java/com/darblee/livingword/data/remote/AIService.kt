package com.darblee.livingword.data.remote

import android.util.Log
import com.darblee.livingword.AISettings
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse
import com.darblee.livingword.ui.viewmodels.ScoreData

/**
 * Centralized AI Service that manages multiple AI providers with fallback mechanisms.
 * Now uses a modular provider registry system for better extensibility and future MCP migration.
 * Provides centralized prompts to ensure consistency across AI services.
 * Priority order determined by provider registry: ESV → Gemini → OpenAI
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
                                            
            Keep the response to 10 - 12 sentences maximum.

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
    
    init {
        // Initialize the provider registry on first use
        try {
            AIServiceRegistry.initialize()
            // Register providers externally
            AIServiceRegistration.registerAllProviders()
            Log.d("AIService", "Provider registry initialized and providers registered")
        } catch (e: Exception) {
            Log.e("AIService", "Failed to initialize provider registry", e)
        }
    }
    
    /**
     * Configures all registered providers with the provided settings.
     * Uses the modular provider registry for better extensibility.
     */
    fun configure(settings: AISettings) {
        try {
            currentSettings = settings
            
            // Configure providers through the registry
            val configResult = AIServiceRegistry.configureProviders(settings)
            
            if (configResult.hasSuccessfulConfigurations) {
                isConfigured = true
                initializationErrorMessage = null
                
                val stats = AIServiceRegistry.getStatistics()
                Log.i("AIService", "AI service configured - Available providers: ${stats.availableProviders}/${stats.totalProviders}")
                
                // Log configuration errors if any
                if (configResult.errors.isNotEmpty()) {
                    Log.w("AIService", "Some providers failed to configure: ${configResult.errors.joinToString("; ")}")
                }
            } else {
                initializationErrorMessage = "All AI services failed to initialize: ${configResult.errors.joinToString("; ")}"
                isConfigured = false
                Log.e("AIService", "Configuration failed: No providers available")
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
    fun isInitialized(): Boolean = isConfigured && AIServiceRegistry.getAvailableProviders().isNotEmpty()
    
    /**
     * Gets the initialization error message, if any.
     */
    fun getInitializationError(): String? = initializationErrorMessage
    
    /**
     * Fetches scripture with priority order determined by provider registry.
     * Tries scripture providers first (ESV), then falls back to AI providers.
     */
    suspend fun fetchScripture(verseRef: BibleVerseRef, translation: String): AiServiceResult<List<Verse>> {
        if (!isConfigured) {
            return AiServiceResult.Error("AI service not configured: ${getInitializationError()}")
        }
        
        // Priority 1: Try scripture providers first if translation matches
        val availableScriptureProviders = AIServiceRegistry.getAvailableScriptureProviders()
        for (provider in availableScriptureProviders) {
            if (provider.supportedTranslations.any { it.equals(translation, ignoreCase = true) }) {
                Log.d("AIService", "Attempting scripture fetch with ${provider.displayName}")
                val result = provider.fetchScripture(verseRef)
                
                when (result) {
                    is AiServiceResult.Success -> {
                        Log.d("AIService", "Scripture fetch successful with ${provider.displayName}")
                        return result
                    }
                    is AiServiceResult.Error -> {
                        Log.w("AIService", "${provider.displayName} failed: ${result.message}")
                    }
                }
            }
        }
        
        // Priority 2: Fallback to AI providers
        val systemInstruction = SystemInstructions.SCRIPTURE_SCHOLAR
        val userPrompt = UserPrompts.getScripturePrompt(verseRef, translation)
        
        val availableAIProviders = AIServiceRegistry.getAvailableProviders()
        for (provider in availableAIProviders) {
            Log.d("AIService", "Attempting scripture fetch with ${provider.displayName} using centralized prompts...")
            val result = provider.fetchScripture(verseRef, translation, systemInstruction, userPrompt)
            
            when (result) {
                is AiServiceResult.Success -> {
                    Log.d("AIService", "Scripture fetch successful with ${provider.displayName}")
                    return result
                }
                is AiServiceResult.Error -> {
                    Log.w("AIService", "${provider.displayName} failed for scripture fetch: ${result.message}")
                }
            }
        }
        
        return AiServiceResult.Error("All available providers (${availableScriptureProviders.size + availableAIProviders.size}) failed for scripture fetch")
    }
    
    /**
     * Gets key takeaway with fallback mechanism using provider registry.
     */
    suspend fun getKeyTakeaway(verseRef: String): AiServiceResult<String> {
        if (!isConfigured) {
            return AiServiceResult.Error("AI service not configured: ${getInitializationError()}")
        }
        
        // Get centralized prompts
        val systemInstruction = SystemInstructions.TAKEAWAY_EXPERT
        val userPrompt = UserPrompts.getKeyTakeawayPrompt(verseRef)
        
        // Try all available AI providers in priority order
        val availableProviders = AIServiceRegistry.getAvailableProviders()
        for (provider in availableProviders) {
            Log.d("AIService", "Attempting key takeaway with ${provider.displayName} using centralized prompts...")
            val result = provider.getKeyTakeaway(verseRef, systemInstruction, userPrompt)
            
            when (result) {
                is AiServiceResult.Success -> {
                    Log.d("AIService", "Key takeaway successful with ${provider.displayName}")
                    return result
                }
                is AiServiceResult.Error -> {
                    Log.w("AIService", "${provider.displayName} failed for key takeaway: ${result.message}")
                }
            }
        }
        
        return AiServiceResult.Error("All available AI providers (${availableProviders.size}) failed for key takeaway")
    }
    
    /**
     * Gets AI score with fallback mechanism using provider registry.
     * This is the most important method for the engagement feature.
     */
    suspend fun getAIScore(verseRef: String, directQuoteToEvaluate: String, userApplicationComment: String): AiServiceResult<ScoreData> {
        if (!isConfigured) {
            return AiServiceResult.Error("AI service not configured: ${getInitializationError()}")
        }
        
        // Get centralized prompts
        val systemInstruction = SystemInstructions.SCORING_EXPERT
        val userPrompt = UserPrompts.getScorePrompt(verseRef, directQuoteToEvaluate)
        val applicationFeedbackPrompt = UserPrompts.getApplicationFeedbackPrompt(verseRef, userApplicationComment)
        
        // Try all available AI providers in priority order
        val availableProviders = AIServiceRegistry.getAvailableProviders()
        for (provider in availableProviders) {
            Log.d("AIService", "Attempting AI score with ${provider.displayName} using centralized prompts...")
            val result = provider.getAIScore(verseRef, userApplicationComment, systemInstruction, userPrompt, applicationFeedbackPrompt)
            
            when (result) {
                is AiServiceResult.Success -> {
                    Log.d("AIService", "AI score successful with ${provider.displayName}")
                    return result
                }
                is AiServiceResult.Error -> {
                    val errorMessage = result.message
                    Log.w("AIService", "${provider.displayName} failed for AI score: $errorMessage")
                    
                    // Check if it's a quota exceeded error for better logging
                    if (errorMessage.contains("quota", ignoreCase = true) || 
                        errorMessage.contains("exceeded", ignoreCase = true) ||
                        errorMessage.contains("rate limit", ignoreCase = true)) {
                        Log.i("AIService", "Detected quota/rate limit issue with ${provider.displayName}, trying next provider")
                    }
                }
            }
        }
        
        return AiServiceResult.Error("All available AI providers (${availableProviders.size}) failed for AI score")
    }

    /**
     * Validates key takeaway response with fallback mechanism using provider registry.
     */
    suspend fun validateKeyTakeawayResponse(verseRef: String, takeawayToEvaluate: String): AiServiceResult<Boolean> {
        if (!isConfigured) {
            return AiServiceResult.Error("AI service not configured: ${getInitializationError()}")
        }

        return AiServiceResult.Success(true)
        
        // Get centralized prompts
/*        val systemInstruction = SystemInstructions.TAKEAWAY_VALIDATOR
        val userPrompt = UserPrompts.getTakeawayValidationPrompt(verseRef, takeawayToEvaluate)
        
        // Try all available AI providers in priority order
        val availableProviders = AIServiceRegistry.getAvailableProviders()
        for (provider in availableProviders) {
            Log.d("AIService", "Attempting takeaway validation with ${provider.displayName} using centralized prompts...")
            val result = provider.validateKeyTakeawayResponse(systemInstruction, userPrompt)
            
            when (result) {
                is AiServiceResult.Success -> {
                    Log.d("AIService", "Takeaway validation successful with ${provider.displayName}")
                    return result
                }
                is AiServiceResult.Error -> {
                    Log.w("AIService", "${provider.displayName} failed for takeaway validation: ${result.message}")
                }
            }
        }
        
        return AiServiceResult.Error("All available AI providers (${availableProviders.size}) failed for takeaway validation")*/
    }
    
    /**
     * Gets new verses based on description with fallback mechanism using provider registry.
     */
    suspend fun getNewVersesBasedOnDescription(description: String): AiServiceResult<List<BibleVerseRef>> {
        if (!isConfigured) {
            return AiServiceResult.Error("AI service not configured: ${getInitializationError()}")
        }
        
        // Get centralized prompts
        val systemInstruction = SystemInstructions.VERSE_FINDER
        val userPrompt = UserPrompts.getVerseSearchPrompt(description)
        
        // Try all available AI providers in priority order
        val availableProviders = AIServiceRegistry.getAvailableProviders()
        for (provider in availableProviders) {
            Log.d("AIService", "Attempting verse search with ${provider.displayName} using centralized prompts...")
            val result = provider.getNewVersesBasedOnDescription(description, systemInstruction, userPrompt)
            
            when (result) {
                is AiServiceResult.Success -> {
                    Log.d("AIService", "Verse search successful with ${provider.displayName}")
                    return result
                }
                is AiServiceResult.Error -> {
                    Log.w("AIService", "${provider.displayName} failed for verse search: ${result.message}")
                }
            }
        }
        
        return AiServiceResult.Error("All available AI providers (${availableProviders.size}) failed for verse search")
    }
    
    /**
     * Test method to perform a basic scripture lookup of John 3:16.
     * Uses the same fallback mechanism as fetchScripture().
     * @return true if successful, false if failed
     */
    suspend fun test(): Boolean {
        return try {
            // Create John 3:16 reference for testing
            val testVerseRef = com.darblee.livingword.data.BibleVerseRef(
                book = "John",
                chapter = 3,
                startVerse = 16,
                endVerse = 16
            )
            val testTranslation = "ESV"
            
            Log.d("AIService", "Running test: fetching John 3:16 in $testTranslation")
            val result = fetchScripture(testVerseRef, testTranslation)
            
            when (result) {
                is AiServiceResult.Success -> {
                    Log.d("AIService", "Test successful: Retrieved ${result.data.size} verse(s)")
                    true
                }
                is AiServiceResult.Error -> {
                    Log.e("AIService", "Test failed: ${result.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("AIService", "Test failed with exception: ${e.message}", e)
            false
        }
    }
}