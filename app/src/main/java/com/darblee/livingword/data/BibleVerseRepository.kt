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

    /**
     * Deletes multiple topics by their names.
     * Only deletes topics that have 0 verse count and are not default topics.
     * Runs on IO dispatcher for database operations.
     */
    suspend fun deleteTopics(topicNames: List<String>) {
        withContext(Dispatchers.IO) {
            for (topicName in topicNames) {
                try {
                    // 1. Check if it's a default topic; if so, skip deletion.
                    if (Global.DEFAULT_TOPICS.any { it.equals(topicName, ignoreCase = true) }) {
                        Log.d("BibleVerseRepository", "Topic '$topicName' is a default topic, not deleting.")
                        continue
                    }

                    // 2. Get the Topic entity to find its ID.
                    val topicEntity = bibleVerseDao.getTopicByName(topicName)
                    if (topicEntity != null) {
                        // 3. Double-check if any verses are still associated with this topicId.
                        val usageCount = bibleVerseDao.countVersesForTopicId(topicEntity.id)
                        Log.d("BibleVerseRepository", "Topic '$topicName' (ID: ${topicEntity.id}) usage count: $usageCount")

                        if (usageCount == 0) {
                            // 4. Safe to delete the topic
                            Log.i("BibleVerseRepository", "Deleting topic: '$topicName' (ID: ${topicEntity.id})")
                            bibleVerseDao.deleteTopicById(topicEntity.id)
                        } else {
                            Log.w("BibleVerseRepository", "Cannot delete topic '$topicName' - it has $usageCount associated verses")
                        }
                    } else {
                        Log.w("BibleVerseRepository", "Topic '$topicName' not found in Topics table")
                    }
                } catch (e: Exception) {
                    Log.e("BibleVerseRepository", "Error deleting topic '$topicName': ${e.message}", e)
                    // Continue with other topics even if one fails
                }
            }
        }
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

    fun getAllTopicsWithCount(): Flow<List<TopicWithCount>> = bibleVerseDao.getAllTopicsWithCount()

    // --- Support for Rename Topic ---
    /**
     * Renames a topic.
     * @param oldTopicName The current name of the topic.
     * @param newTopicName The new name for the topic.
     * @throws IllegalArgumentException if the topic is a default topic, or if newTopicName already exists as a different topic.
     * @throws NoSuchElementException if the oldTopicName is not found.
     */
    suspend fun renameTopic(oldTopicName: String, newTopicName: String) {
        if (Global.DEFAULT_TOPICS.any { it.equals(oldTopicName, ignoreCase = true) }) {
            Log.w("BibleVerseRepository", "Attempt to rename default topic '$oldTopicName' was blocked.")
            throw IllegalArgumentException("Default topics cannot be renamed.")
        }

        val oldTopicEntity = bibleVerseDao.getTopicByName(oldTopicName)
            ?: throw NoSuchElementException("Topic '$oldTopicName' not found and cannot be renamed.")

        // If new and old names are identical (case-sensitive), no action needed.
        if (oldTopicName == newTopicName) {
            Log.i("BibleVerseRepository", "Topic rename: old and new names are identical ('$oldTopicName'). No change.")
            return
        }

        val newTopicCheck = bibleVerseDao.getTopicByName(newTopicName)
        if (newTopicCheck != null && newTopicCheck.id != oldTopicEntity.id) {
            // newTopicName exists and belongs to a *different* topic.
            Log.w("BibleVerseRepository", "Attempt to rename topic '$oldTopicName' to '$newTopicName', but '$newTopicName' already exists as a different topic.")
            throw IllegalArgumentException("Topic '$newTopicName' already exists.")
        }

        // If newTopicCheck is not null and newTopicCheck.id == oldTopicEntity.id,
        // it means newTopicName is just a case variation of oldTopicName. This is permissible.

        withContext(Dispatchers.IO) {
            bibleVerseDao.renameTopicInDb(oldTopicName, newTopicName, oldTopicEntity.id)
        }
    }

    suspend fun renameOrMergeTopic(oldTopicName: String, newTopicName: String, isMergeIntent: Boolean) {
        if (Global.DEFAULT_TOPICS.any { it.equals(oldTopicName, ignoreCase = true) }) {
            throw IllegalArgumentException("Default topic '$oldTopicName' cannot be renamed or merged from.")
        }

        val oldTopicEntity = bibleVerseDao.getTopicByName(oldTopicName)
            ?: throw NoSuchElementException("Topic '$oldTopicName' not found.")

        // If old and new names are effectively the same (case-insensitive check for non-merge)
        // and it's not an explicit merge intent, treat as potential case-only rename.
        if (oldTopicName.equals(newTopicName, ignoreCase = true) && !isMergeIntent) {
            if (oldTopicName == newTopicName) {
                Log.i("BibleVerseRepository", "Topic rename: old and new names are identical ('$oldTopicName'). No change.")
                return // No actual change needed if case is also identical
            }
            // This is a case-only rename. The renameTopicInDb can handle this.
        }

        val targetTopicEntity = bibleVerseDao.getTopicByName(newTopicName)

        if (targetTopicEntity != null && targetTopicEntity.id != oldTopicEntity.id) {
            // New name matches a DIFFERENT existing topic.
            if (isMergeIntent) {
                if (Global.DEFAULT_TOPICS.any { it.equals(targetTopicEntity.topic, ignoreCase = true) }) {
                    throw IllegalArgumentException("Cannot merge into a default topic: '${targetTopicEntity.topic}'.")
                }
                // Proceed with merge
                withContext(Dispatchers.IO) {
                    bibleVerseDao.mergeTopics(
                        oldTopicId = oldTopicEntity.id,
                        oldTopicName = oldTopicName, // Pass original oldTopicName for string replacement
                        targetTopicId = targetTopicEntity.id,
                        targetTopicName = targetTopicEntity.topic // Pass actual name of target
                    )
                }
            } else {
                // Should ideally be caught by UI, but as a safeguard:
                throw IllegalArgumentException("Topic '$newTopicName' already exists. Merge was not explicitly allowed.")
            }
        } else {
            // Simple rename (new name doesn't exist or is a case change of the same topic)
            // targetTopicEntity will be null if newTopicName doesn't exist.
            // targetTopicEntity will be non-null but targetTopicEntity.id == oldTopicEntity.id if it's a case change.
            if (targetTopicEntity != null && targetTopicEntity.id == oldTopicEntity.id && targetTopicEntity.topic == newTopicName) {
                Log.i("BibleVerseRepository", "Topic rename: target is same topic with identical name ('$newTopicName'). No DB change for topic name itself needed, but checking verses.")
                // Still call renameTopicInDb as verse topic strings might need case update if oldTopicName was different case
            }
            withContext(Dispatchers.IO) {
                bibleVerseDao.renameTopicInDb(oldTopicName, newTopicName, oldTopicEntity.id)
            }
        }
    }


}