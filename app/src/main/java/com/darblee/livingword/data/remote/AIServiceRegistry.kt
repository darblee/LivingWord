package com.darblee.livingword.data.remote

import android.util.Log
import com.darblee.livingword.AIServiceConfig
import com.darblee.livingword.AIServiceType
import com.darblee.livingword.AISettings
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for managing AI service providers in a modular, plugin-like architecture.
 * This prepares the codebase for future MCP migration while maintaining current functionality.
 */
object AIServiceRegistry {
    
    private val providers = ConcurrentHashMap<String, AIServiceProvider>()
    private val scriptureProviders = ConcurrentHashMap<String, ScriptureProvider>()
    private var isInitialized = false
    
    /**
     * Initializes the registry with built-in providers
     */
    fun initialize() {
        if (isInitialized) {
            Log.d("AIServiceRegistry", "Registry already initialized")
            return
        }
        
        try {
            // Register built-in providers
            registerBuiltInProviders()
            isInitialized = true
            Log.i("AIServiceRegistry", "AI Service Registry initialized with ${providers.size} AI providers and ${scriptureProviders.size} scripture providers")
        } catch (e: Exception) {
            Log.e("AIServiceRegistry", "Failed to initialize registry", e)
            throw e
        }
    }
    
    /**
     * Registers built-in service providers
     */
    private fun registerBuiltInProviders() {
        // Register AI providers
        registerProvider(GeminiAIServiceProvider())
        registerProvider(OpenAIServiceProvider())
        
        // Register scripture providers
        registerScriptureProvider(ESVScriptureProvider())
    }
    
    /**
     * Registers an AI service provider
     */
    fun registerProvider(provider: AIServiceProvider) {
        providers[provider.providerId] = provider
        Log.d("AIServiceRegistry", "Registered AI provider: ${provider.displayName} (${provider.providerId})")
    }
    
    /**
     * Registers a scripture provider
     */
    fun registerScriptureProvider(provider: ScriptureProvider) {
        scriptureProviders[provider.providerId] = provider
        Log.d("AIServiceRegistry", "Registered scripture provider: ${provider.displayName} (${provider.providerId})")
    }
    
    /**
     * Gets a provider by ID
     */
    fun getProvider(providerId: String): AIServiceProvider? {
        return providers[providerId]
    }
    
    /**
     * Gets a scripture provider by ID
     */
    fun getScriptureProvider(providerId: String): ScriptureProvider? {
        return scriptureProviders[providerId]
    }
    
    /**
     * Gets all registered AI providers sorted by priority
     */
    fun getAllProviders(): List<AIServiceProvider> {
        return providers.values.sortedBy { it.priority }
    }
    
    /**
     * Gets all scripture providers sorted by priority
     */
    fun getAllScriptureProviders(): List<ScriptureProvider> {
        return scriptureProviders.values.sortedBy { it.priority }
    }
    
    /**
     * Gets providers for a specific service type
     */
    fun getProvidersByType(serviceType: AIServiceType): List<AIServiceProvider> {
        return providers.values.filter { it.serviceType == serviceType }.sortedBy { it.priority }
    }
    
    /**
     * Gets available providers (initialized and ready)
     */
    fun getAvailableProviders(): List<AIServiceProvider> {
        return providers.values.filter { it.isInitialized() }.sortedBy { it.priority }
    }
    
    /**
     * Gets available scripture providers
     */
    fun getAvailableScriptureProviders(): List<ScriptureProvider> {
        return scriptureProviders.values.filter { it.isInitialized() }.sortedBy { it.priority }
    }
    
    /**
     * Configures all providers with the given settings
     */
    fun configureProviders(settings: AISettings): ConfigurationResult {
        val results = mutableMapOf<String, Boolean>()
        val errors = mutableListOf<String>()
        
        providers.values.forEach { provider ->
            try {
                val config = when (provider.serviceType) {
                    AIServiceType.GEMINI -> settings.geminiConfig
                    AIServiceType.OPENAI -> settings.openAiConfig
                }
                
                val success = provider.configure(config)
                results[provider.providerId] = success
                
                if (!success) {
                    provider.getInitializationError()?.let { error ->
                        errors.add("${provider.displayName}: $error")
                    }
                }
                
                Log.d("AIServiceRegistry", "Configured ${provider.displayName}: $success")
            } catch (e: Exception) {
                results[provider.providerId] = false
                errors.add("${provider.displayName}: ${e.message}")
                Log.e("AIServiceRegistry", "Failed to configure ${provider.displayName}", e)
            }
        }
        
        return ConfigurationResult(results, errors)
    }
    
    /**
     * Gets metadata for all registered providers
     */
    fun getProvidersMetadata(): List<ProviderMetadata> {
        return providers.values.map { provider ->
            ProviderMetadata(
                providerId = provider.providerId,
                displayName = provider.displayName,
                serviceType = provider.serviceType,
                version = "1.0.0", // Could be extended to read from provider
                capabilities = ProviderCapabilities(), // Default capabilities
                isEnabled = provider.isInitialized()
            )
        }
    }
    
    /**
     * Unregisters a provider (useful for dynamic loading/unloading)
     */
    fun unregisterProvider(providerId: String): Boolean {
        val removed = providers.remove(providerId) != null
        if (removed) {
            Log.d("AIServiceRegistry", "Unregistered provider: $providerId")
        }
        return removed
    }
    
    /**
     * Clears all providers (mainly for testing)
     */
    fun clear() {
        providers.clear()
        scriptureProviders.clear()
        isInitialized = false
        Log.d("AIServiceRegistry", "Registry cleared")
    }
    
    /**
     * Gets registry statistics
     */
    fun getStatistics(): RegistryStatistics {
        val totalProviders = providers.size
        val availableProviders = providers.values.count { it.isInitialized() }
        val providersByType = AIServiceType.values().associateWith { type ->
            providers.values.count { it.serviceType == type }
        }
        
        return RegistryStatistics(
            totalProviders = totalProviders,
            availableProviders = availableProviders,
            totalScriptureProviders = scriptureProviders.size,
            availableScriptureProviders = scriptureProviders.values.count { it.isInitialized() },
            providersByType = providersByType
        )
    }
}

/**
 * Result of provider configuration
 */
data class ConfigurationResult(
    val results: Map<String, Boolean>,
    val errors: List<String>
) {
    val hasSuccessfulConfigurations: Boolean
        get() = results.values.any { it }
        
    val allConfigurationsSuccessful: Boolean
        get() = results.values.all { it }
}

/**
 * Registry statistics for monitoring and debugging
 */
data class RegistryStatistics(
    val totalProviders: Int,
    val availableProviders: Int,
    val totalScriptureProviders: Int,
    val availableScriptureProviders: Int,
    val providersByType: Map<AIServiceType, Int>
)