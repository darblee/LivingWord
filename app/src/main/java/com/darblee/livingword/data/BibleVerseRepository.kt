package com.darblee.livingword.data


import android.util.Log
import com.darblee.livingword.Global
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BibleVerseRepository(private val bibleVerseDao: BibleVerseDao) {

    suspend fun insertVerseWithTopics(
        book: String,
        chapter: Int,
        startVerse: Int,
        endVerse: Int,
        scripture: String,
        aiResponse: String,
        topics: List<String>
    ): Long {
        return bibleVerseDao.insertVerseWithTopics(book, chapter, startVerse, endVerse, scripture, aiResponse, topics )
    }

    fun getAllVerses(): Flow<List<BibleVerse>> = bibleVerseDao.getAllVerses()

    fun getAllTopics(): Flow<List<Topic>> = bibleVerseDao.getAllTopics()

    suspend fun updateVerse(bibleVerse: BibleVerse) {
        bibleVerseDao.updateVerse(bibleVerse)
    }

    /**
     * Retrieves a specific Bible verse from the database by its ID.
     * This is a suspend function as it performs a database operation.
     * @param id The unique ID of the Bible verse to retrieve.
     * @return The BibleVerse object matching the ID.
     */
    suspend fun getVerseById(id: Long): BibleVerse {
        return bibleVerseDao.getVerseById(id)
    }

    suspend fun findVerseByReference(book: String, chapter: Int, startVerse: Int): BibleVerse? {
        return bibleVerseDao.findVerseByReference(book, chapter, startVerse)
    }

    suspend fun deleteVerse(bibleVerse: BibleVerse) {
        // It's important to get the topics associated with the verse *before* it's deleted,
        // as the BibleVerse object itself contains this list.
        val topicsOfDeletedVerse = bibleVerse.topics.toList() // Create a copy

        // Delete the verse. Room's CASCADE will handle deleting entries from CrossRefBibleVerseTopics.
        bibleVerseDao.deleteVerse(bibleVerse)

        /***
         * Now, check each topic that was associated with the deleted verse
         * Run this in a background thread.
         *
         * When you launch a coroutine (e.g., using viewModelScope.launch { ... } or scope.launch { ... }),
         * it runs on a specific thread or a pool of threads.
         *
         * Dispatchers in Kotlin coroutines are objects that determine which thread or threads the coroutine
         * will use for its execution. They are part of the kotlinx.coroutines library.
         *
         * Dispatchers.IO: This dispatcher is optimized for I/O-bound operations. These are tasks that
         * involve reading from or writing to disk (like database operations with Room)
         */
        withContext(Dispatchers.IO) { // Ensure DB operations are off the main thread
            for (topicName in topicsOfDeletedVerse) {
                // 1. Check if it's a default topic; if so, skip deletion.
                // Ensure case-insensitive comparison if defaultTopics might have different casing.
                if (Global.DEFAULT_TOPICS.any { it.equals(topicName, ignoreCase = true) }) {
                    Log.d("BibleVerseRepository", "Topic '$topicName' is a default topic, not deleting.")
                    continue
                }

                // 2. Get the Topic entity to find its ID.
                val topicEntity = bibleVerseDao.getTopicByName(topicName)
                if (topicEntity != null) {
                    // 3. Check if any *other* verses are still associated with this topicId.
                    // Since the cross-references for the deleted verse are already gone (due to CASCADE),
                    // this count will accurately reflect remaining usages.
                    val usageCount = bibleVerseDao.countVersesForTopicId(topicEntity.id)
                    Log.d("BibleVerseRepository", "Topic '$topicName' (ID: ${topicEntity.id}) usage count: $usageCount")

                    if (usageCount == 0) {
                        // 4. If no other verse uses this topic, and it's not a default topic, delete it.
                        Log.i("BibleVerseRepository", "Deleting orphaned topic: '$topicName' (ID: ${topicEntity.id})")
                        bibleVerseDao.deleteTopicById(topicEntity.id)
                        // Alternatively, if you prefer, and if topic names are unique:
                        // bibleVerseDao.deleteTopicByName(topicName)
                    }
                } else {
                    Log.w("BibleVerseRepository", "Topic '$topicName' from deleted verse not found in Topics table. Skipping cleanup for it.")
                }
            }
        }
    }
}
