package com.darblee.livingword.data

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class VotdResponse(
    val votd: Votd
)

@Serializable
data class Votd(
    val reference: String
)

object VotdService {
    private const val VOTD_URL = "https://www.biblegateway.com/votd/get/?format=JSON&version=9"
    private val jsonParser = Json { ignoreUnknownKeys = true }

    suspend fun fetchVerseOfTheDayReference(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(VOTD_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000 // 10 seconds
                connection.readTimeout = 10000 // 10 seconds

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()
                    connection.disconnect()

                    Log.d("VotdService", "VOTD API Response: $response")

                    val votdResponse = jsonParser.decodeFromString<VotdResponse>(response)
                    votdResponse.votd.reference
                } else {
                    Log.e("VotdService", "Failed to fetch VOTD. HTTP error code: $responseCode")
                    null
                }
            } catch (e: Exception) {
                Log.e("VotdService", "Error fetching Verse of the Day: ${e.message}", e)
                null
            }
        }
    }
}
