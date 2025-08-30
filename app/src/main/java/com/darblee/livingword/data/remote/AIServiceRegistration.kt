package com.darblee.livingword.data.remote

import android.util.Log

/**
 * External registration utility for AI service providers.
 * This handles the registration of providers from outside the registry.
 */
object AIServiceRegistration {
    
    /**
     * Registers all available AI service providers with the registry.
     * This function should be called after AIServiceRegistry.initialize()
     * and before using any AI services.
     */
    fun registerAllProviders() {
        try {
            Log.i("AIServiceRegistration", "Starting provider registration process")
            
            // Register AI providers
            registerAIProviders()
            
            // Register scripture providers
            registerScriptureProviders()
            
            val stats = AIServiceRegistry.getStatistics()
            Log.i("AIServiceRegistration", "Provider registration completed - ${stats.totalProviders} providers registered")
            
        } catch (e: Exception) {
            Log.e("AIServiceRegistration", "Failed to register providers", e)
            throw e
        }
    }
    
    /**
     * Registers AI service providers
     */
    private fun registerAIProviders() {
        try {
            // Register Gemini AI provider
            val geminiProvider = GeminiAIServiceProvider()
            AIServiceRegistry.registerProvider(geminiProvider)
            Log.d("AIServiceRegistration", "Registered Gemini AI provider")
            
            // Register OpenAI provider
            val openAIProvider = OpenAIServiceProvider()
            AIServiceRegistry.registerProvider(openAIProvider)
            Log.d("AIServiceRegistration", "Registered OpenAI provider")
            
        } catch (e: Exception) {
            Log.e("AIServiceRegistration", "Failed to register AI providers", e)
            throw e
        }
    }
    
    /**
     * Registers scripture providers
     */
    private fun registerScriptureProviders() {
        try {
            // Register ESV Scripture provider
            val esvProvider = ESVScriptureProvider()
            AIServiceRegistry.registerScriptureProvider(esvProvider)
            Log.d("AIServiceRegistration", "Registered ESV Scripture provider")
            
        } catch (e: Exception) {
            Log.e("AIServiceRegistration", "Failed to register scripture providers", e)
            throw e
        }
    }
    
    /**
     * Registers individual AI provider (for dynamic registration)
     */
    fun registerAIProvider(provider: AIServiceProvider): Boolean {
        return try {
            AIServiceRegistry.registerProvider(provider)
            Log.i("AIServiceRegistration", "Dynamically registered AI provider: ${provider.displayName}")
            true
        } catch (e: Exception) {
            Log.e("AIServiceRegistration", "Failed to register AI provider: ${provider.displayName}", e)
            false
        }
    }
    
    /**
     * Registers individual scripture provider (for dynamic registration)
     */
    fun registerScriptureProvider(provider: ScriptureProvider): Boolean {
        return try {
            AIServiceRegistry.registerScriptureProvider(provider)
            Log.i("AIServiceRegistration", "Dynamically registered scripture provider: ${provider.displayName}")
            true
        } catch (e: Exception) {
            Log.e("AIServiceRegistration", "Failed to register scripture provider: ${provider.displayName}", e)
            false
        }
    }
    
    /**
     * Gets registration status summary
     */
    fun getRegistrationStatus(): RegistrationStatus {
        val stats = AIServiceRegistry.getStatistics()
        return RegistrationStatus(
            totalProviders = stats.totalProviders,
            availableProviders = stats.availableProviders,
            aiProviders = stats.providersByType.values.sum(),
            scriptureProviders = AIServiceRegistry.getAllScriptureProviders().size,
            isReady = stats.availableProviders > 0
        )
    }
}

/**
 * Data class representing the registration status
 */
data class RegistrationStatus(
    val totalProviders: Int,
    val availableProviders: Int,
    val aiProviders: Int,
    val scriptureProviders: Int,
    val isReady: Boolean
)