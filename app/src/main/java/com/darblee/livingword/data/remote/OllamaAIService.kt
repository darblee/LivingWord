package com.darblee.livingword.data.remote

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * AI service for Ollama models (currently configured for Reformed Christian Bible Expert)
 */
class OllamaAIService private constructor() {
    companion object {
        @Volatile
        private var INSTANCE: OllamaAIService? = null
        private const val TAG = "OllamaAI"
        
        // Hardcoded server IP as requested - Updated to match actual network
        private const val BASE_URL = "http://192.168.1.16:11434/"
        private const val MODEL_NAME = "hf.co/sleepdeprived3/Reformed-Christian-Bible-Expert-v1.1-12B-Q8_0-GGUF:Q8_0"
        
        fun getInstance(): OllamaAIService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OllamaAIService().also { INSTANCE = it }
            }
        }
    }
    
    private var isInitialized = false
    private var initializationError: String? = null
    internal var ollamaService: OllamaService? = null
    
    init {
        initialize()
    }
    
    private fun initialize() {
        try {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS) // Longer timeout for AI responses
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
            
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            
            ollamaService = retrofit.create(OllamaService::class.java)
            isInitialized = true
            Log.i(TAG, "Ollama AI Service initialized successfully with server: $BASE_URL")
        } catch (e: Exception) {
            initializationError = "Failed to initialize Ollama AI Service: ${e.message}"
            Log.e(TAG, initializationError, e)
        }
    }
    
    fun isInitialized(): Boolean = isInitialized
    
    fun getInitializationError(): String? = initializationError
    
    /**
     * Get key takeaway for a Bible verse from Reformed perspective
     */
    suspend fun getKeyTakeaway(verseReference: String): AiServiceResult<String> {
        if (!isInitialized || ollamaService == null) {
            return AiServiceResult.Error(initializationError ?: "Service not initialized")
        }
        
        return try {
            val prompt = buildTakeawayPrompt(verseReference)
            val request = OllamaRequest(
                model = MODEL_NAME,
                prompt = prompt,
                stream = false,
                options = OllamaOptions(temperature = 0.7f, max_tokens = 800)
            )
            
            Log.d(TAG, "Sending takeaway request for: $verseReference")
            Log.d(TAG, "Using model: $MODEL_NAME")
            
            val response = ollamaService!!.generateResponse(request)
            
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null && responseBody.done) {
                    val cleanResponse = responseBody.response.trim()
                    Log.d(TAG, "Takeaway response received: $cleanResponse")
                    Log.d(TAG, "Response stats - eval_count: ${responseBody.eval_count}, total_duration: ${responseBody.total_duration}")
                    AiServiceResult.Success(cleanResponse)
                } else {
                    val errorMsg = "Invalid response from Ollama AI: ${responseBody?.response ?: "null response"}"
                    Log.e(TAG, errorMsg)
                    AiServiceResult.Error(errorMsg)
                }
            } else {
                val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                Log.e(TAG, "API request failed: $errorMsg")
                AiServiceResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting takeaway for $verseReference", e)
            AiServiceResult.Error("Network error: ${e.message}")
        }
    }
    
    private fun buildTakeawayPrompt(verseReference: String): String {
        return """
            As a Christian Bible expert, provide key takeaway for $verseReference.
            
            Please:
            1. Focus on the main theological point
            2. Keep the response to 10 sentences maximum
            3. Emphasize practical application for Christian living
            4. Use clear, accessible language
            
            Verse reference: $verseReference
            
            Key takeaway:
        """.trimIndent()
    }
    
    /**
     * Validate if a takeaway response is theologically accurate
     */
    suspend fun validateKeyTakeawayResponse(verseReference: String, takeaway: String): AiServiceResult<Boolean> {
        if (!isInitialized || ollamaService == null) {
            return AiServiceResult.Error(initializationError ?: "Service not initialized")
        }
        
        return try {
            val prompt = buildValidationPrompt(verseReference, takeaway)
            val request = OllamaRequest(
                model = MODEL_NAME,
                prompt = prompt,
                stream = false,
                options = OllamaOptions(temperature = 0.3f, max_tokens = 50)
            )
            
            Log.d(TAG, "Sending validation request for: $verseReference")
            val response = ollamaService!!.generateResponse(request)
            
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null && responseBody.done) {
                    val validationResponse = responseBody.response.trim().lowercase()
                    val isValid = validationResponse.startsWith("yes")
                    Log.d(TAG, "Validation result for '$takeaway': $isValid (response: '$validationResponse')")
                    AiServiceResult.Success(isValid)
                } else {
                    val errorMsg = "Invalid validation response: ${responseBody?.response ?: "null response"}"
                    Log.e(TAG, errorMsg)
                    AiServiceResult.Error(errorMsg)
                }
            } else {
                val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                Log.e(TAG, "Validation request failed: $errorMsg")
                AiServiceResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating takeaway for $verseReference", e)
            AiServiceResult.Error("Validation error: ${e.message}")
        }
    }
    
    private fun buildValidationPrompt(verseReference: String, takeaway: String): String {
        return """
            As a Christian Bible expert, evaluate if this takeaway is theologically accurate and appropriate for $verseReference:
            
            Takeaway: "$takeaway"
            
            Respond with only "Yes" or "No" based on whether this takeaway is:
            1. Biblically accurate
            2. Consistent with Christian theology
            3. Appropriate for the verse context
            
            Response:
        """.trimIndent()
    }
    
    /**
     * Test connection to the Ollama server
     */
    suspend fun testConnection(): AiServiceResult<String> {
        return try {
            val testPrompt = "Hello, respond with 'Ollama AI is working' to confirm connection."
            val request = OllamaRequest(
                model = MODEL_NAME,
                prompt = testPrompt,
                stream = false,
                options = OllamaOptions(temperature = 0.1f, max_tokens = 20)
            )
            
            Log.d(TAG, "Testing connection to Ollama AI...")
            val response = ollamaService?.generateResponse(request)
            
            if (response?.isSuccessful == true) {
                val responseBody = response.body()
                if (responseBody != null && responseBody.done) {
                    Log.i(TAG, "Connection test successful")
                    AiServiceResult.Success("Connection successful: ${responseBody.response.trim()}")
                } else {
                    AiServiceResult.Error("Connection test failed: Invalid response")
                }
            } else {
                AiServiceResult.Error("Connection test failed: HTTP ${response?.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            AiServiceResult.Error("Connection test failed: ${e.message}")
        }
    }
}