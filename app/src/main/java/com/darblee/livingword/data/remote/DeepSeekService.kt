package com.darblee.livingword.data.remote

import android.util.Log
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse
import com.darblee.livingword.ui.viewmodels.ScoreData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object DeepSeekService {

    private var apiKey: String? = null
    private var modelName: String = "deepseek-chat"
    private var initializationErrorMessage: String? = null
    private var isConfigured = false

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun configure(key: String, model: String) {
        apiKey = key
        modelName = model
        if (key.isBlank()) {
            initializationErrorMessage = "DeepSeek API key missing"
            isConfigured = false
            Log.e("DeepSeekService", "Configuration failed: API key missing")
        } else {
            isConfigured = true
            initializationErrorMessage = null
            Log.i("DeepSeekService", "Configured successfully with model $modelName")
        }
    }

    fun isInitialized(): Boolean = isConfigured && !apiKey.isNullOrBlank()

    fun getInitializationError(): String? = initializationErrorMessage

    suspend fun fetchScripture(
        verseRef: BibleVerseRef,
        translation: String,
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<List<Verse>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized()) {
                    return@withContext AiServiceResult.Error("DeepSeek service not initialized: ${getInitializationError()}")
                }
                Log.d("DeepSeekService", "Fetching scripture for $verseRef in $translation")
                val response = callDeepSeek(userPrompt, maxTokens = 500, systemInstruction = systemInstruction)
                when (response) {
                    is AiServiceResult.Success -> {
                        try {
                            AiServiceResult.Success(parseScriptureResponse(response.data))
                        } catch (e: Exception) {
                            AiServiceResult.Error("Failed to parse scripture response: ${e.message}", e)
                        }
                    }
                    is AiServiceResult.Error -> response
                }
            } catch (e: Exception) {
                Log.e("DeepSeekService", "Error fetching scripture", e)
                AiServiceResult.Error("Error fetching scripture: ${e.message}", e)
            }
        }
    }

    suspend fun getKeyTakeaway(
        verseRef: String,
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized()) {
                    return@withContext AiServiceResult.Error("DeepSeek service not initialized: ${getInitializationError()}")
                }
                Log.d("DeepSeekService", "Getting key takeaway for $verseRef")
                val response = callDeepSeek(userPrompt, maxTokens = 300, systemInstruction = systemInstruction)
                when (response) {
                    is AiServiceResult.Success -> AiServiceResult.Success(response.data.trim())
                    is AiServiceResult.Error -> response
                }
            } catch (e: Exception) {
                Log.e("DeepSeekService", "Error getting key takeaway", e)
                AiServiceResult.Error("Error getting key takeaway: ${e.message}", e)
            }
        }
    }

    suspend fun getAIScore(
        verseRef: String,
        userApplicationComment: String,
        systemInstruction: String,
        userPrompt: String,
        applicationFeedbackPrompt: String
    ): AiServiceResult<ScoreData> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized()) {
                    return@withContext AiServiceResult.Error("DeepSeek service not initialized: ${getInitializationError()}")
                }
                Log.d("DeepSeekService", "Getting AI score for $verseRef")
                val response = callDeepSeek(userPrompt, maxTokens = 800, systemInstruction = systemInstruction)
                when (response) {
                    is AiServiceResult.Success -> {
                        try {
                            var cleanedJson = response.data
                                .replace("```json", "")
                                .replace("```", "")
                                .trim()
                            val jsonStart = cleanedJson.indexOf('{')
                            val jsonEnd = cleanedJson.lastIndexOf('}')
                            if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                                cleanedJson = cleanedJson.substring(jsonStart, jsonEnd + 1)
                            }
                            val parseResponse = jsonParser.decodeFromString<ScoreData>(cleanedJson)
                            val feedbackInstruction = "You are an expert in theology. You provide insightful and encouraging feedback on how users apply Bible verses to their lives."
                            parseResponse.ApplicationFeedback = getApplicationFeedback(verseRef, feedbackInstruction, applicationFeedbackPrompt)
                            AiServiceResult.Success(parseResponse)
                        } catch (e: Exception) {
                            Log.e("DeepSeekService", "Failed to parse AI response JSON: ${response.data}", e)
                            AiServiceResult.Error("DeepSeek returned invalid JSON format. Please try again.", e)
                        }
                    }
                    is AiServiceResult.Error -> response
                }
            } catch (e: Exception) {
                Log.e("DeepSeekService", "Error calling DeepSeek API", e)
                AiServiceResult.Error("Could not get AI score from DeepSeek (${e.javaClass.simpleName})", e)
            }
        }
    }

    suspend fun getApplicationFeedback(
        verseRef: String,
        systemInstruction: String,
        userPrompt: String
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized()) return@withContext ""
                Log.d("DeepSeekService", "Getting application feedback for $verseRef")
                val response = callDeepSeek(userPrompt, maxTokens = 300, systemInstruction = systemInstruction)
                when (response) {
                    is AiServiceResult.Success -> response.data.trim()
                    is AiServiceResult.Error -> {
                        Log.w("DeepSeekService", "Failed to get application feedback: ${response.message}")
                        ""
                    }
                }
            } catch (e: Exception) {
                Log.e("DeepSeekService", "Error getting application feedback: ${e.message}", e)
                ""
            }
        }
    }

    suspend fun validateKeyTakeawayResponse(
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized()) {
                    return@withContext AiServiceResult.Error("DeepSeek service not initialized: ${getInitializationError()}")
                }
                Log.d("DeepSeekService", "Validating key takeaway response")
                val response = callDeepSeek(userPrompt, maxTokens = 50, systemInstruction = systemInstruction)
                when (response) {
                    is AiServiceResult.Success -> AiServiceResult.Success(response.data.trim().lowercase() == "true")
                    is AiServiceResult.Error -> response
                }
            } catch (e: Exception) {
                Log.e("DeepSeekService", "Error validating takeaway", e)
                AiServiceResult.Error("Error validating takeaway: ${e.message}", e)
            }
        }
    }

    suspend fun getNewVersesBasedOnDescription(
        description: String,
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<List<BibleVerseRef>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized()) {
                    return@withContext AiServiceResult.Error("DeepSeek service not initialized: ${getInitializationError()}")
                }
                Log.d("DeepSeekService", "Finding verses for description: $description")
                val response = callDeepSeek(userPrompt, maxTokens = 400, systemInstruction = systemInstruction)
                when (response) {
                    is AiServiceResult.Success -> {
                        try {
                            AiServiceResult.Success(parseVerseReferences(response.data))
                        } catch (e: Exception) {
                            AiServiceResult.Error("Failed to parse verse references: ${e.message}", e)
                        }
                    }
                    is AiServiceResult.Error -> response
                }
            } catch (e: Exception) {
                Log.e("DeepSeekService", "Error getting verses by description", e)
                AiServiceResult.Error("Error getting verses by description: ${e.message}", e)
            }
        }
    }

    suspend fun test(): Boolean {
        return try {
            val testVerseRef = BibleVerseRef(book = "John", chapter = 3, startVerse = 16, endVerse = 16)
            val systemInstruction = "You are a Biblical scholar. Provide accurate Bible verses in the requested translation format."
            val userPrompt = """
                Provide the Bible verse for John 3:16 in the ESV translation.
                Return ONLY a JSON array:
                [{"verse_num": 16, "verse_string": "verse_text"}]
                Do not include any other text.
            """.trimIndent()
            val result = fetchScripture(testVerseRef, "ESV", systemInstruction, userPrompt)
            when (result) {
                is AiServiceResult.Success -> {
                    Log.d("DeepSeekService", "Test successful: ${result.data.size} verse(s)")
                    true
                }
                is AiServiceResult.Error -> {
                    Log.e("DeepSeekService", "Test failed: ${result.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("DeepSeekService", "Test failed: ${e.message}", e)
            false
        }
    }

    private fun callDeepSeek(prompt: String, maxTokens: Int = 500, systemInstruction: String? = null): AiServiceResult<String> {
        return try {
            val url = URL("https://api.deepseek.com/v1/chat/completions")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val jsonBody = JSONObject().apply {
                put("model", modelName)
                put("messages", org.json.JSONArray().apply {
                    systemInstruction?.let { sysInst ->
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", sysInst)
                        })
                    }
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("max_tokens", maxTokens)
                put("temperature", 0.7)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val errorText = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                Log.e("DeepSeekService", "API error $responseCode: $errorText")
                return AiServiceResult.Error("DeepSeek API error $responseCode: $errorText")
            }

            val responseText = connection.inputStream.bufferedReader().readText()
            Log.d("DeepSeekService", "Response code: $responseCode")

            val jsonResponse = JSONObject(responseText)
            val choices = jsonResponse.getJSONArray("choices")
            if (choices.length() > 0) {
                val content = choices.getJSONObject(0).getJSONObject("message").getString("content")
                AiServiceResult.Success(content)
            } else {
                AiServiceResult.Error("No choices returned from DeepSeek")
            }
        } catch (e: Exception) {
            Log.e("DeepSeekService", "Error calling API", e)
            AiServiceResult.Error("Network error calling DeepSeek: ${e.message}", e)
        }
    }

    private fun parseScriptureResponse(jsonResponse: String): List<Verse> {
        if (jsonResponse.isBlank()) throw Exception("Empty response from DeepSeek")
        val cleanedJson = jsonResponse.replace("```json", "").replace("```", "").trim()
        if (cleanedJson.isBlank()) throw Exception("Response empty after cleaning")
        return jsonParser.decodeFromString<List<Verse>>(cleanedJson)
    }

    private fun parseVerseReferences(jsonResponse: String): List<BibleVerseRef> {
        if (jsonResponse.isBlank()) throw Exception("Empty response from DeepSeek")
        val cleanedJson = jsonResponse.replace("```json", "").replace("```", "").trim()
        if (cleanedJson.isBlank()) throw Exception("Response empty after cleaning")
        return jsonParser.decodeFromString<List<BibleVerseRef>>(cleanedJson)
    }
}
