# LivingWord

LivingWord is a native Android application designed to help users engage with the Bible in a meaningful way. The app allows users to read, save, and categorize Bible verses, as well as get AI-powered insights and explanations.

## Features

*   **Read the Bible:** Browse and read the entire Bible.
*   **Verse of the Day:** Get a daily Bible verse to inspire you.
*   **Save and Organize Verses:** Save your favorite verses and organize them by topic.
*   **AI-Powered Insights:** Get explanations and insights on Bible verses using the Gemini AI SDK.
*   **Text-to-Speech:** Listen to Bible verses read aloud.
*   **Google Drive Backup:** Back up and restore your data using Google Drive.
*   **Search:** Search for verses by description.

## Tech Stack

*   **Language:** [Kotlin](https://kotlinlang.org/)
*   **UI:** [Jetpack Compose](https://developer.android.com/jetpack/compose) with [Material Design 3](https://m3.material.io/)
*   **Architecture:** Clean Architecture with MVVM
*   **Asynchronous Programming:** [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
*   **Database:** [Room](https://developer.android.com/training/data-storage/room)
*   **Data Storage:** [Preferences Datastore](https://developer.android.com/topic/libraries/architecture/datastore)
*   **Navigation:** [Jetpack Navigation for Compose](https://developer.android.com/jetpack/compose/navigation)
*   **AI:** [Google Generative AI SDK](https://ai.google.dev/docs)
*   **APIs:**
    *   [ESV Bible API](https://api.esv.org/)
    *   [Google Drive API](https://developers.google.com/drive)

## Project Structure

The project follows a clean architecture pattern, with the following main packages:

*   **`data`:** Contains the data sources for the application, such as the Room database and the logic for fetching data from the Bible API.
*   **`domain`:** Contains the core business logic of the application, such as use cases and repository interfaces.
*   **`ui`:** Contains all the Jetpack Compose UI components, including screens, view models, and navigation.

## Setup

To set up and run the project, follow these steps:

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/your-username/LivingWord-2.git
    ```
2.  **Open the project in Android Studio.**
3.  **Create a `secrets.properties` file in the `app` directory.**
4.  **Add your API keys to the `secrets.properties` file:**
    ```properties
    GEMINI_API_KEY=YOUR_GEMINI_API_KEY
    ESV_BIBLE_API_KEY=YOUR_ESV_BIBLE_API_KEY
    ```
5.  **Sync the project with Gradle.**
6.  **Run the app on an emulator or a real device.**

## Build

To build the application, you can use the following Gradle command:

```bash
./gradlew assembleDebug
```

This will create an APK file in the `app/build/outputs/apk/debug` directory.

## Screenshots

| Home Screen | Verse Detail | Engage | Topic Management |
| :---: | :---: | :---: | :---: |
| ![Home Screen](./Screenshots/Home%20Screen.png) | ![Verse Detail](./Screenshots/Verse%20Detail.png) | ![Engage](./Screenshots/Engage%20in%20scripture.png) | ![Topic Management](./Screenshots/Topic%20Management.png) |


## AI Provider Architecture

LivingWord uses a modular AI provider system that supports multiple AI services with automatic fallback mechanisms. Currently supported providers:

- **Gemini AI** (Primary) - Google's Generative AI SDK
- **OpenAI** (Secondary) - OpenAI's GPT models
- **ESV Bible API** (Scripture Provider) - For direct Bible text lookup

### Multi-Provider Features

- **Automatic Fallback:** If one provider fails, the system automatically tries the next available provider
- **Priority-Based Routing:** Providers are prioritized (ESV → Gemini → OpenAI)
- **Centralized Prompts:** Consistent AI prompts across all providers
- **Provider Registry:** Dynamic provider discovery and management

## Adding a New AI Provider

Follow these steps to add a new AI provider (e.g., DeepSeek) to the application:

### Step 1: Update AIServiceType Enum

Add your new provider to the `AIServiceType` enum in `PreferenceStore.kt`:

```kotlin
// File: app/src/main/java/com/darblee/livingword/PreferenceStore.kt
enum class AIServiceType(val displayName: String, val defaultModel: String) {
    GEMINI("Gemini AI", "gemini-1.5-flash"),
    OPENAI("OpenAI", "gpt-4o-mini"),
    DEEPSEEK("DeepSeek", "deepseek-chat")  // Add new provider
}
```

### Step 2: Update AISettings Data Class

Add configuration support in `PreferenceStore.kt`:

```kotlin
// File: app/src/main/java/com/darblee/livingword/PreferenceStore.kt
data class AISettings(
    val selectedService: AIServiceType = AIServiceType.GEMINI,
    val geminiConfig: AIServiceConfig = /* ... */,
    val openAiConfig: AIServiceConfig = /* ... */,
    val deepSeekConfig: AIServiceConfig = AIServiceConfig(  // Add new config
        serviceType = AIServiceType.DEEPSEEK,
        modelName = AIServiceType.DEEPSEEK.defaultModel,
        apiKey = "",
        temperature = 0.7f
    )
) {
    // Update compatibility methods
    val apiKey: String get() = when (selectedService) {
        AIServiceType.GEMINI -> geminiConfig.apiKey
        AIServiceType.OPENAI -> openAiConfig.apiKey
        AIServiceType.DEEPSEEK -> deepSeekConfig.apiKey
    }
    
    val deepSeekApiKey: String get() = deepSeekConfig.apiKey  // Add getter
    // ... other methods
}
```

### Step 3: Create the AI Service Implementation

Create a new service class (e.g., `DeepSeekService.kt`) in the `data/remote` package:

```kotlin
// File: app/src/main/java/com/darblee/livingword/data/remote/DeepSeekService.kt
object DeepSeekService {
    private var isConfigured = false
    private var currentSettings: AISettings? = null
    
    fun configure(settings: AISettings) {
        // Initialize DeepSeek SDK/HTTP client
        // Set API key, model, temperature
        currentSettings = settings
        isConfigured = true
    }
    
    fun isInitialized(): Boolean = isConfigured
    
    suspend fun fetchScripture(/* parameters */): AiServiceResult<List<Verse>> {
        // Implement scripture fetching logic
    }
    
    suspend fun getKeyTakeaway(/* parameters */): AiServiceResult<String> {
        // Implement takeaway generation logic
    }
    
    suspend fun getAIScore(/* parameters */): AiServiceResult<ScoreData> {
        // Implement scoring logic
    }
    
    // ... other required methods
}
```

### Step 4: Create the Provider Wrapper

Create a provider class (e.g., `DeepSeekServiceProvider.kt`):

```kotlin
// File: app/src/main/java/com/darblee/livingword/data/remote/DeepSeekServiceProvider.kt
class DeepSeekServiceProvider : AIServiceProvider {
    override val providerId: String = "deepseek"
    override val displayName: String = "DeepSeek"
    override val serviceType: AIServiceType = AIServiceType.DEEPSEEK
    override val defaultModel: String = AIServiceType.DEEPSEEK.defaultModel
    override val priority: Int = 20 // Set priority (higher number = lower priority)
    
    override fun configure(config: AIServiceConfig): Boolean {
        return try {
            val legacySettings = AISettings(
                selectedService = AIServiceType.DEEPSEEK,
                deepSeekConfig = config,
                // ... other configs with empty keys
            )
            DeepSeekService.configure(legacySettings)
            DeepSeekService.isInitialized()
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun fetchScripture(/* parameters */): AiServiceResult<List<Verse>> {
        return DeepSeekService.fetchScripture(/* forward parameters */)
    }
    
    // ... implement all AIServiceProvider methods
}
```

### Step 5: Register the Provider

Add your provider to the registry in `AIServiceRegistry.kt`:

```kotlin
// File: app/src/main/java/com/darblee/livingword/data/remote/AIServiceRegistry.kt
private fun registerBuiltInProviders() {
    // Register AI providers
    registerProvider(GeminiAIServiceProvider())
    registerProvider(OpenAIServiceProvider())
    registerProvider(DeepSeekServiceProvider()) // Add new provider
    
    // Register scripture providers
    registerScriptureProvider(ESVScriptureProvider())
}
```

### Step 6: Update Configuration Logic

Update the configuration logic in `AIServiceRegistry.kt`:

```kotlin
// File: app/src/main/java/com/darblee/livingword/data/remote/AIServiceRegistry.kt
fun configureProviders(settings: AISettings): ConfigurationResult {
    // ...
    providers.values.forEach { provider ->
        try {
            val config = when (provider.serviceType) {
                AIServiceType.GEMINI -> settings.geminiConfig
                AIServiceType.OPENAI -> settings.openAiConfig
                AIServiceType.DEEPSEEK -> settings.deepSeekConfig // Add new case
            }
            // ... rest of configuration logic
        }
    }
}
```

### Step 7: Add UI Settings (Optional)

If you want users to configure the new provider in the app's settings:

1. Update preference keys in `PreferenceStore.kt`
2. Add UI components in settings screens
3. Update preference save/load logic

### Step 8: Update API Key Configuration

Add your API key to `secrets.properties`:

```properties
GEMINI_API_KEY=your_gemini_key
ESV_BIBLE_API_KEY=your_esv_key  
DEEPSEEK_API_KEY=your_deepseek_key
```

Update `build.gradle.kts` (app level):

```kotlin
buildConfigField("String", "DEEPSEEK_API_KEY", "\"${project.findProperty("DEEPSEEK_API_KEY") ?: ""}\"")
```

## Testing AI Providers

LivingWord includes a comprehensive test suite for validating AI provider functionality. The test suite supports both multi-provider and single-provider testing scenarios.

### Running All AI Provider Tests

```bash
# Run all AI service tests
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.darblee.livingword.AIServiceTestSuite
```

### Multi-Provider Testing

Multi-provider tests validate the automatic fallback system:

```bash
# Test multi-provider fallback mechanisms
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.method="*multipleSequentialRequests*,*aiServiceMethods*"
```

**Multi-provider tests validate:**
- ✅ Automatic fallback when primary provider fails
- ✅ Priority-based provider selection  
- ✅ Error handling across providers
- ✅ Consistent results across different providers

### Single-Provider Testing

For debugging specific providers, use single-provider tests:

```bash
# Test Gemini only
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.method="geminiProvider_*"

# Test OpenAI only  
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.method="openAIProvider_*"

# Test all single-provider tests
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.method="*Provider_*"
```

**Single-provider tests validate:**
- ✅ Provider-specific functionality
- ✅ API key authentication
- ✅ Rate limiting behavior
- ✅ Provider isolation
- ✅ Error handling per provider

### Adding Tests for New Providers (e.g., DeepSeek)

When you add a new provider, create corresponding test methods in `AIServiceTestSuite.kt`:

```kotlin
// File: app/src/androidTest/java/com/darblee/livingword/AIServiceTestSuite.kt

/**
 * Test DeepSeek provider specifically for scripture fetching
 */
@Test
fun deepSeekProvider_fetchScripture_shouldWork() = runBlocking {
    // Arrange - Configure only DeepSeek
    val deepSeekOnlySettings = AISettings(
        selectedService = AIServiceType.DEEPSEEK,
        geminiConfig = AIServiceConfig(/* empty key to disable */),
        openAiConfig = AIServiceConfig(/* empty key to disable */),
        deepSeekConfig = AIServiceConfig(
            serviceType = AIServiceType.DEEPSEEK,
            modelName = "deepseek-chat",
            apiKey = BuildConfig.DEEPSEEK_API_KEY.ifEmpty { "test-deepseek-key" },
            temperature = 0.7f
        )
    )
    
    AIService.configure(deepSeekOnlySettings)
    delay(1000)
    
    // Get DeepSeek provider specifically
    val deepSeekProvider = AIServiceRegistry.getProvider("deepseek")
    assertNotNull("DeepSeek provider should be available", deepSeekProvider)
    
    val testVerseRef = BibleVerseRef("John", 3, 16, 16)
    
    // Act - Test directly through provider
    val result = deepSeekProvider!!.fetchScripture(
        testVerseRef, 
        "ESV",
        AIService.SystemInstructions.SCRIPTURE_SCHOLAR,
        AIService.UserPrompts.getScripturePrompt(testVerseRef, "ESV")
    )
    
    // Assert
    when (result) {
        is AiServiceResult.Success -> {
            assertTrue("DeepSeek should return verses", result.data.isNotEmpty())
            assertTrue("Verse should have content", result.data[0].verseString.isNotBlank())
            println("✅ DeepSeek provider test passed - fetched ${result.data.size} verse(s)")
        }
        is AiServiceResult.Error -> {
            println("⚠️ DeepSeek provider test failed: ${result.message}")
            assertTrue("Error should be descriptive", result.message.isNotBlank())
        }
    }
}

/**
 * Test DeepSeek provider initialization and status
 */
@Test
fun deepSeekProvider_initialization_shouldReportCorrectStatus() = runBlocking {
    // ... similar pattern for other test methods
}

// Add similar test methods for:
// - deepSeekProvider_getKeyTakeaway_shouldWork()
// - deepSeekProvider_getAIScore_shouldWork()
// - etc.
```

### Test Categories

The test suite includes these categories:

| Category | Purpose | Example Methods |
|----------|---------|-----------------|
| **Configuration Tests** | Validate provider setup | `configure_withValidSettings_shouldSucceed()` |
| **Scripture Tests** | Test Bible verse fetching | `fetchScripture_withValidReference_shouldReturnVerse()` |
| **AI Function Tests** | Test AI-powered features | `getKeyTakeaway_withValidReference_shouldReturnTakeaway()` |
| **Error Handling Tests** | Test failure scenarios | `aiServiceMethods_whenNotConfigured_shouldReturnError()` |
| **Single Provider Tests** | Test individual providers | `geminiProvider_fetchScripture_shouldWork()` |
| **Integration Tests** | Test provider interactions | `multipleSequentialRequests_shouldMaintainStability()` |

### Running Specific Test Categories

```bash
# Test only scripture fetching across all providers
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.method="*fetchScripture*"

# Test only AI scoring functionality  
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.method="*getAIScore*"

# Test only initialization and configuration
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.method="*initialization*,*configure*"
```

### Test Output and Debugging

The test suite provides detailed logging for debugging:

```
🔍 Provider isolation test:
   Gemini initialized: true
   OpenAI initialized: false
✅ Gemini provider test passed - fetched 1 verse(s)
⚠️ OpenAI provider test failed (expected if API key not configured): Invalid API key
📋 Currently registered providers:
   - Gemini AI (gemini_ai) - Priority: 10
   - OpenAI (openai) - Priority: 20
   - DeepSeek (deepseek) - Priority: 30
```

This comprehensive testing framework ensures that new AI providers work correctly and integrate seamlessly with the existing system.

## Contributing

Contributions are welcome! If you have any ideas, suggestions, or bug reports, please open an issue or submit a pull request.

When adding new AI providers, please:
1. Follow the provider implementation steps above
2. Add comprehensive test coverage using the test patterns described
3. Update this documentation with provider-specific notes
4. Test both single-provider and multi-provider scenarios

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.