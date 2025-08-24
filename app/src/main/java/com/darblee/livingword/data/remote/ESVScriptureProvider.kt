package com.darblee.livingword.data.remote

import android.util.Log
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse

/**
 * Provider implementation for ESV Bible Lookup Service.
 * Wraps the existing ESVBibleLookupService to conform to the ScriptureProvider interface.
 */
class ESVScriptureProvider : ScriptureProvider {
    
    override val providerId: String = "esv_bible"
    override val displayName: String = "ESV Bible"
    override val supportedTranslations: List<String> = listOf("ESV")
    override val priority: Int = 1 // Highest priority for ESV translations
    
    private val esvService = ESVBibleLookupService()
    
    override fun isInitialized(): Boolean {
        // ESV service is always ready if API key is configured
        // The actual initialization check happens in the service itself
        return true
    }
    
    override suspend fun test(): Boolean {
        return try {
            Log.d("ESVScriptureProvider", "Running test")
            esvService.test()
        } catch (e: Exception) {
            Log.e("ESVScriptureProvider", "Test failed", e)
            false
        }
    }
    
    override suspend fun fetchScripture(verseRef: BibleVerseRef): AiServiceResult<List<Verse>> {
        return try {
            Log.d("ESVScriptureProvider", "Fetching scripture: ${verseRef.book} ${verseRef.chapter}:${verseRef.startVerse}-${verseRef.endVerse}")
            esvService.fetchScripture(verseRef)
        } catch (e: Exception) {
            Log.e("ESVScriptureProvider", "fetchScripture failed", e)
            AiServiceResult.Error("ESV Bible error: ${e.message}", e)
        }
    }
}