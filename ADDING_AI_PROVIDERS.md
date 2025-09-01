# Adding New AI Providers

This guide explains how to add new AI assistants (like DeepSeek, Claude, etc.) to the dynamic AI system.

## Overview

The system now supports dynamic AI provider registration. The PreferenceStore automatically detects registered providers and manages their configurations without requiring code changes.

## Steps to Add a New AI Provider

### 1. Add Service Type (Optional)
If your provider represents a new service type, add it to the enum:

```kotlin
// In PreferenceStore.kt
enum class AIServiceType(val displayName: String, val defaultModel: String) {
    GEMINI("Gemini AI", "gemini-1.5-flash"),
    OPENAI("OpenAI", "gpt-4o-mini"),
    DEEPSEEK("DeepSeek AI", "deepseek-chat"),
    CLAUDE("Claude AI", "claude-3-sonnet") // Example new service type
}
```

### 2. Create Your Provider Class
Implement the `AIServiceProvider` interface:

```kotlin
class ClaudeAIServiceProvider : AIServiceProvider {
    override val providerId: String = "claude_ai"
    override val displayName: String = "Claude AI"
    override val serviceType: AIServiceType = AIServiceType.CLAUDE
    override val defaultModel: String = "claude-3-sonnet"
    override val priority: Int = 20
    
    private var currentConfig: AIServiceConfig? = null
    private var initializationError: String? = null
    
    override fun configure(config: AIServiceConfig): Boolean {
        // Implementation details...
    }
    
    override fun isInitialized(): Boolean {
        // Implementation details...
    }
    
    override suspend fun test(): Boolean {
        // Implementation details...
    }
    
    // Implement other required methods...
}
```

### 3. Register Your Provider
Add the provider registration in `AIServiceRegistration.kt`:

```kotlin
private fun registerAIProviders() {
    try {
        // Existing registrations...
        
        // Register your new provider
        val claudeProvider = ClaudeAIServiceProvider()
        AIServiceRegistry.registerProvider(claudeProvider)
        Log.d("AIServiceRegistration", "Registered Claude provider")
        
    } catch (e: Exception) {
        Log.e("AIServiceRegistration", "Failed to register AI providers", e)
        throw e
    }
}
```

### 4. Update Migration Logic (Optional)
If you need to handle migration for new service types, update the `migrateLegacyConfigsToDynamic` function in PreferenceStore.kt:

```kotlin
AIServiceType.CLAUDE -> "" // Add default API key handling
```

## That's It!

The system will automatically:
- ✅ Detect your new provider at runtime
- ✅ Create configuration entries in PreferenceStore
- ✅ Display it in the UI settings
- ✅ Handle user configuration changes
- ✅ Save and restore settings across app sessions
- ✅ Configure the provider when settings change

## Example: DeepSeek Provider

The codebase includes a complete example with `DeepSeekServiceProvider` that demonstrates:
- Service registration
- Configuration management
- Error handling
- Integration with the existing UI

## Clean Architecture

The system is now fully dynamic with no legacy code:
- ✅ No hardcoded provider configurations
- ✅ No migration logic needed
- ✅ Pure dynamic provider registration
- ✅ Minimal codebase footprint

## Testing

Your provider will be automatically included in:
- Registry statistics
- Configuration validation
- UI testing scenarios
- Provider isolation tests

The dynamic system makes adding AI providers as simple as implementing the interface and registering the provider!