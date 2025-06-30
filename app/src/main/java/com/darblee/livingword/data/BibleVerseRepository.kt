package com.darblee.livingword.data

import android.util.Log
import com.darblee.livingword.Global
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map // Ensure this import is present
import kotlinx.coroutines.withContext

class BibleVerseRepository(private val bibleVerseDao: BibleVerseDao) {

    fun getAllVerses(): Flow<List<BibleVerse>> = bibleVerseDao.getAllVerses()

    /**
     * Updates an existing Bible verse and its associated topics.
     * This function will:
     * 1. Atomically update the verse data, including its list of topics.
     * 2. Add any new topics from the list to the main Topics table if they don't already exist.
     * 3. Update the relationship table (CrossRefBibleVerseTopics) to reflect the new set of topics.
     * This function does not delete any topics, even if they become orphaned after the update.
     * The user is expected to manually delete topics with a verse count of zero.
     *
     * @param bibleVerse The [BibleVerse] object with the updated information.
     */
    suspend fun updateVerse(bibleVerse: BibleVerse) {
        withContext(Dispatchers.IO) {
            // Call the DAO transaction to atomically update the verse, its topics, and the cross-references.
            // This transaction will also create any new topics that don't exist in the Topics table.
            bibleVerseDao.updateVerseAndTopics(bibleVerse)
        }
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
                    val topicEntity = bibleVerseDao.getTopicByName(topicName)
                    if (topicEntity != null) {
                        // 3. Double-check if any verses are still associated with this topicId.
                        val usageCount = bibleVerseDao.countVersesForTopicId(topicEntity.id)
                        Log.d("BibleVerseRepository", "Topic '$topicName' (ID: ${topicEntity.id}) usage count: $usageCount")

                        if (usageCount == 0) {
                            // 4. Safe to delete the topic
                            Log.i("BibleVerseRepository", "Deleting topic: '${topicName}' (ID: ${topicEntity.id})")
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
        // Delete the verse. Room's CASCADE setting on the foreign key in CrossRefBibleVerseTopics
        // will automatically handle deleting the entries from the join table.
        // We are no longer cleaning up orphaned topics here, as per the user's request.
        // The user will manually delete topics with a verse count of 0.
        bibleVerseDao.deleteVerse(bibleVerse)
    }

    /**
     * Retrieves all topics along with their verse counts.
     * This function ensures that all topics defined in Global.DEFAULT_TOPICS are included in the list.
     * If a default topic is not found in the database, it's added to the list with a verse count of 0.
     * The final list is sorted alphabetically by topic name.
     *
     * Note: This assumes the existence of a data class `TopicWithCount(val topic: String, val verseCount: Int)`.
     */
    fun getAllTopics(): Flow<List<TopicWithCount>> {
        return bibleVerseDao.getAllTopicsWithCount().map { dbTopicsWithCount ->
            val mutableTopics = dbTopicsWithCount.toMutableList()

            // Sort the final list by topic name, case-insensitively, for consistent ordering.
            mutableTopics.sortBy { it.topic.lowercase() }
            mutableTopics.toList() // Return an immutable list
        }
    }

    /**
     * Checks if the topics table is empty and, if so, populates it with default topics.
     *
     * Since there isn't a DAO method to add a list of topics all at once, this function accomplish this by
     * creating and then deleting a temporary verse* It creates a temporary verse with the default topics
     * and then deletes it, leaving the topics.
     */
    suspend fun addDefaultTopicsIfEmpty() {
        withContext(Dispatchers.IO) {
            // Use .first() to get the current list from the Flow once
            if (bibleVerseDao.getAllTopicsWithCount().first().isEmpty()) {
                Log.i("BibleVerseRepository", "No topics found, populating with default topics.")
                try {
                    val verseId = insertVerseWithTopics(
                        book = "System",
                        chapter = 0,
                        startVerse = 0,
                        endVerse = 0,
                        aiTakeAwayResponse = "Initial topic setup",
                        topics = Global.DEFAULT_TOPICS,
                        favorite = false,
                        translation = "Internal",
                        verses = emptyList()
                    )
                    val tempVerse = getVerseById(verseId)
                    deleteVerse(tempVerse)
                    Log.i("BibleVerseRepository", "Default topics created successfully.")
                } catch (e: Exception) {
                    Log.e("BibleVerseRepository", "Error creating default topics", e)
                }
            }
        }
    }

    suspend fun renameOrMergeTopic(oldTopicName: String, newTopicName: String, isMergeIntent: Boolean) {

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
            if (targetTopicEntity != null && targetTopicEntity.topic == newTopicName) {
                Log.i("BibleVerseRepository", "Topic rename: target is same topic with identical name ('$newTopicName'). No DB change for topic name itself needed, but checking verses.")
                // Still call renameTopicInDb as verse topic strings might need case update if oldTopicName was different case
            }
            withContext(Dispatchers.IO) {
                bibleVerseDao.renameTopicInDb(oldTopicName, newTopicName, oldTopicEntity.id)
            }
        }
    }


    /**
     * Adds a new topic to the database if it doesn't already exist.
     * @param topicName The name of the topic to add
     * @return The ID of the topic (either newly created or existing)
     */
    suspend fun addTopic(topicName: String): Long {
        return withContext(Dispatchers.IO) {
            val trimmedName = topicName.trim()
            if (trimmedName.isBlank()) {
                throw IllegalArgumentException("Topic name cannot be blank")
            }

            val topic = Topic(topic = trimmedName)
            bibleVerseDao.insertTopicIfNotExists(topic)
        }
    }

    suspend fun insertVerseWithTopics(
        book: String,
        chapter: Int,
        startVerse: Int,
        endVerse: Int,
        aiTakeAwayResponse: String,
        topics: List<String>,
        favorite: Boolean = false,
        translation: String,
        verses: List<Verse>
    ): Long {
        return bibleVerseDao.insertVerseWithTopics(
            book, chapter, startVerse, endVerse, aiTakeAwayResponse, topics, favorite, translation, verses)
    }

    /**
     * Updates the favorite status of a Bible verse.
     */
    suspend fun updateFavoriteStatus(verseId: Long, isFavorite: Boolean) {
        withContext(Dispatchers.IO) {
            bibleVerseDao.updateFavoriteStatus(verseId, isFavorite)
        }
    }

    /**
     * Updates the translation of a Bible verse.
     */
    suspend fun updateTranslation(verseId: Long, translation: String) {
        withContext(Dispatchers.IO) {
            bibleVerseDao.updateTranslation(verseId, translation)
        }
    }

    /**
     * Gets all favorite verses.
     */
    fun getAllFavoriteVerses(): Flow<List<BibleVerse>> = bibleVerseDao.getAllFavoriteVerses()

    /**
     * Gets verses by translation.
     */
    fun getVersesByTranslation(translation: String): Flow<List<BibleVerse>> =
        bibleVerseDao.getVersesByTranslation(translation)

    /**
     * Gets favorite verses by translation.
     */
    fun getFavoriteVersesByTranslation(translation: String): Flow<List<BibleVerse>> =
        bibleVerseDao.getFavoriteVersesByTranslation(translation)
}
