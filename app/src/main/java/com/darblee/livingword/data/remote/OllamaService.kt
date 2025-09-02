package com.darblee.livingword.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit interface for Ollama API communication
 */
interface OllamaService {
    @POST("api/generate")
    suspend fun generateResponse(@Body request: OllamaRequest): Response<OllamaResponse>
}

/**
 * Request data class for Ollama API
 */
data class OllamaRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false,
    val options: OllamaOptions? = null
)

/**
 * Options for controlling model behavior
 */
data class OllamaOptions(
    val temperature: Float = 0.7f,
    val max_tokens: Int = 1000
)

/**
 * Response data class from Ollama API
 */
data class OllamaResponse(
    val model: String,
    val created_at: String,
    val response: String,
    val done: Boolean,
    val context: List<Int>? = null,
    val total_duration: Long? = null,
    val load_duration: Long? = null,
    val prompt_eval_count: Int? = null,
    val prompt_eval_duration: Long? = null,
    val eval_count: Int? = null,
    val eval_duration: Long? = null
)