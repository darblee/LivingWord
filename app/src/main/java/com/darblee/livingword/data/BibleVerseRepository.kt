package com.darblee.livingword.data


import android.util.Log
import com.darblee.livingword.Global
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map // Ensure this import is present
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

    /**
     * Updates an existing Bible verse and its associated topics.
     * This function will:
     * 1. Atomically update the verse data, including its list of topics.
     * 2. Add any new topics from the list to the main Topics table if they don't already exist.
     * 3. Update the relationship table (CrossRefBibleVerseTopics) to reflect the new set of topics.
     * 4. After the update, it cleans up any topics that are no longer associated with any verses ("orphaned" topics),
     * as long as they are not designated as default topics.
     *
     * @param bibleVerse The [BibleVerse] object with the updated information.
     */
    suspend fun updateVerse(bibleVerse: BibleVerse) {
        withContext(Dispatchers.IO) {
            // First, get the original verse from the database to find out which topics were associated with it before the update.
            val originalVerse = bibleVerseDao.getVerseById(bibleVerse.id)
            val originalTopics = originalVerse.topics

            // The new list of topics is in the `bibleVerse` object being passed to this function.
            val newTopics = bibleVerse.topics

            // Determine which topics, if any, were removed during the update.
            val removedTopics = originalTopics.toSet() - newTopics.toSet()

            // Call the DAO transaction to atomically update the verse, its topics, and the cross-references.
            // This transaction will also create any new topics that don't exist in the Topics table.
            bibleVerseDao.updateVerseAndTopics(bibleVerse)

            // After the update is committed, check if any of the removed topics have become "orphaned"
            // (i.e., they are no longer associated with any verses).
            for (topicName in removedTopics) {
                // Do not delete default topics, even if they become orphaned.
                if (Global.DEFAULT_TOPICS.any { it.equals(topicName, ignoreCase = true) }) {
                    Log.d("BibleVerseRepository", "Topic '$topicName' is a default topic, not checking for orphan status.")
                    continue
                }

                // Find the topic entity to get its ID.
                val topicEntity = bibleVerseDao.getTopicByName(topicName)
                if (topicEntity != null) {
                    // Check how many verses are currently associated with this topic.
                    val usageCount = bibleVerseDao.countVersesForTopicId(topicEntity.id)
                    Log.d("BibleVerseRepository", "Post-update check for topic '$topicName' (ID: ${topicEntity.id}), usage count: $usageCount")

                    // If the usage count is zero, it's safe to delete the topic.
                    if (usageCount == 0) {
                        Log.i("BibleVerseRepository", "Deleting orphaned topic after update: '$topicName' (ID: ${topicEntity.id})")
                        bibleVerseDao.deleteTopicById(topicEntity.id)
                    }
                } else {
                    Log.w("BibleVerseRepository", "Could not find topic '$topicName' during orphan check. It may have already been deleted.")
                }
            }
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
    fun getAllTopicsWithCount(): Flow<List<TopicWithCount>> {
        return bibleVerseDao.getAllTopicsWithCount().map { dbTopicsWithCount ->
            val mutableTopics = dbTopicsWithCount.toMutableList()
            val existingTopicNamesLowercase = mutableTopics.map { it.topic.lowercase() }.toMutableSet()

            Global.DEFAULT_TOPICS.forEach { defaultTopicName ->
                val defaultTopicLowercase = defaultTopicName.lowercase()
                if (!existingTopicNamesLowercase.contains(defaultTopicLowercase)) {
                    // Check if a case-variant of the default topic already exists from the DB.
                    // This handles scenarios where DB might have "Prayer" and default is "prayer".
                    // The .any check is more robust if existingTopicNamesLowercase isn't perfectly synced
                    // or if casing in Global.DEFAULT_TOPICS isn't consistent.
                    val alreadyExistsWithDifferentCase = mutableTopics.any {
                        it.topic.equals(defaultTopicName, ignoreCase = true)
                    }
                    if (!alreadyExistsWithDifferentCase) {
                        mutableTopics.add(TopicWithCount(topic = defaultTopicName, verseCount = 0))
                        // Add to set as well to prevent re-adding if multiple default topic entries are just case variations of each other
                        existingTopicNamesLowercase.add(defaultTopicLowercase)
                    }
                }
            }
            // Sort the final list by topic name, case-insensitively, for consistent ordering.
            mutableTopics.sortBy { it.topic.lowercase() }
            mutableTopics.toList() // Return an immutable list
        }
    }


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
