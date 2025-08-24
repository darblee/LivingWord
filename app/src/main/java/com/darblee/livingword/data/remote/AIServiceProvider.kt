package com.darblee.livingword.data.remote

import com.darblee.livingword.AIServiceConfig
import com.darblee.livingword.AIServiceType
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse
import com.darblee.livingword.ui.viewmodels.ScoreData

/**
 * Abstract interface for AI service providers.
 * This provides a standardized contract for all AI services and prepares for future MCP migration.
 */
interface AIServiceProvider {
    /**
     * Unique identifier for this provider
     */
    val providerId: String
    
    /**
     * Human-readable display name for this provider
     */
    val displayName: String
    
    /**
     * The service type this provider implements
     */
    val serviceType: AIServiceType
    
    /**
     * Default model name for this provider
     */
    val defaultModel: String
    
    /**
     * Priority level for fallback ordering (lower numbers = higher priority)
     */
    val priority: Int
    
    /**
     * Configures the provider with the given settings
     */
    fun configure(config: AIServiceConfig): Boolean
    
    /**
     * Checks if the provider is properly initialized and ready to use
     */
    fun isInitialized(): Boolean
    
    /**
     * Tests the provider connection with a simple request
     */
    suspend fun test(): Boolean
    
    /**
     * Fetches scripture verses for the given reference and translation
     */
    suspend fun fetchScripture(
        verseRef: BibleVerseRef, 
        translation: String,
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<List<Verse>>
    
    /**
     * Gets a key takeaway from the specified verse
     */
    suspend fun getKeyTakeaway(
        verseRef: String,
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<String>
    
    /**
     * Gets AI scoring for verse engagement
     */
    suspend fun getAIScore(
        verseRef: String, 
        userApplicationComment: String,
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<ScoreData>
    
    /**
     * Validates a key takeaway response
     */
    suspend fun validateKeyTakeawayResponse(
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<Boolean>
    
    /**
     * Gets new verses based on description
     */
    suspend fun getNewVersesBasedOnDescription(
        description: String,
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<List<BibleVerseRef>>
    
    /**
     * Gets initialization error message if any
     */
    fun getInitializationError(): String?
}

/**
 * Special provider interface for ESV Bible service
 * ESV doesn't follow the AI pattern but provides scripture lookup
 */
interface ScriptureProvider {
    val providerId: String
    val displayName: String
    val supportedTranslations: List<String>
    val priority: Int
    
    fun isInitialized(): Boolean
    suspend fun test(): Boolean
    suspend fun fetchScripture(verseRef: BibleVerseRef): AiServiceResult<List<Verse>>
}

/**
 * Provider capabilities for future extensibility
 */
data class ProviderCapabilities(
    val supportsScripture: Boolean = true,
    val supportsTakeaway: Boolean = true,
    val supportsScoring: Boolean = true,
    val supportsValidation: Boolean = true,
    val supportsVerseSearch: Boolean = true,
    val supportedTranslations: List<String> = emptyList(),
    val maxConcurrentRequests: Int = 1,
    val rateLimitPerMinute: Int = 60
)

/**
 * Provider metadata for service discovery and management
 */
data class ProviderMetadata(
    val providerId: String,
    val displayName: String,
    val serviceType: AIServiceType,
    val version: String,
    val capabilities: ProviderCapabilities,
    val configurationSchema: Map<String, Any> = emptyMap(),
    val isEnabled: Boolean = true
)