package com.darblee.livingword.data


import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BibleVerseDao {
    @Insert
    suspend fun insertVerse(bibleVerse: BibleVerse): Long

    @Insert
    suspend fun insertTopic(topic: Topic): Long

    @Insert
    suspend fun insertTopicIfNotExists(topic: Topic): Long {
        val existingTopic = getTopicByName(topic.topic)
        return if (existingTopic != null) {
            existingTopic.id
        } else {
            insertTopic(topic)
        }
    }

    /***
     * The insertCrossRef() function is a Room Data Access Object (DAO) function designed to insert
     * a record into the CrossRefBibleVerseTopics table. This table serves as a join table in a
     * many-to-many relationship between BibleVerse and Topic.
     *
     * In this case, you have a Bible verse and a topic, and you want to link them together in your
     * database. You would create a CrossRefBibleVerseTopics object with the ID of that verse and
     * the ID of that topic, and then pass that object to the insertCrossRef() function. This
     * function then adds a new entry in the CrossRefBibleVerseTopics table, creating that link.
     */
    @Insert
    suspend fun insertCrossRef(crossRef: CrossRefBibleVerseTopics)

    @Update
    suspend fun updateVerse(bibleVerse: BibleVerse)

    @Query("DELETE FROM CrossRefBibleVerseTopics WHERE bibleVerseId = :verseId")
    suspend fun deleteAllCrossRefsForVerseId(verseId: Long)

    @Transaction
    suspend fun updateVerseAndTopics(bibleVerse: BibleVerse) {
        // 1. First, clear all existing topic associations for this verse in the join table.
        deleteAllCrossRefsForVerseId(bibleVerse.id)

        // 2. Now, iterate through the list of topics in the passed BibleVerse object.
        // For each topic, ensure it exists in the Topics table and create a new cross-reference.
        val newTopics = bibleVerse.topics
        newTopics.forEach { topicName ->
            // Find if the topic already exists.
            val existingTopic = getTopicByName(topicName)
            // If topic doesn't exist, create it and get its new ID. Otherwise, use existing ID.
            val topicId = existingTopic?.id ?: insertTopic(Topic(topic = topicName))
            // Create the new cross-reference linking the verse and the topic.
            insertCrossRef(CrossRefBibleVerseTopics(bibleVerseId = bibleVerse.id, topicId = topicId))
        }

        // 3. Finally, update the BibleVerse entity itself in the BibleVerse_Items table.
        // The `bibleVerse` object contains all the latest data, including the updated `topics`
        // list (which Room will convert to a string), scripture, aiResponse, etc.
        updateVerse(bibleVerse)
    }

    @Delete
    suspend fun deleteVerse(bibleVerse: BibleVerse)

    @Transaction
    suspend fun insertVerseWithTopics(
        book: String,
        chapter: Int,
        startVerse: Int,
        endVerse: Int,
        aiTakeAwayResponse: String,
        topics: List<String>,
        favorite: Boolean = false,
        translation: String,
        scriptureVerses: List<Verse>
    ): Long {
        val verseId = insertVerse(
            BibleVerse(
                book = book,
                chapter = chapter,
                startVerse = startVerse,
                endVerse = endVerse,
                aiTakeAwayResponse = aiTakeAwayResponse,
                topics = emptyList(), // Insert with empty topics initially, will be updated
                translation = translation,
                favorite = favorite,
                scriptureVerses = scriptureVerses
            )
        )

        topics.forEach { topicName ->
            val existingTopic = getTopicByName(topicName)
            val topicId = existingTopic?.id ?: insertTopic(Topic(topic = topicName))
            insertCrossRef(CrossRefBibleVerseTopics(bibleVerseId = verseId, topicId = topicId))
        }

        // Update the BibleVerse entity with the actual topics (for easier retrieval if needed later)
        val topicsString = topics.joinToString(",")
        updateVerseTopics(verseId, topicsString)

        return verseId
    }

    @Query("UPDATE BibleVerse_Items SET topics = :topics WHERE id = :verseId")
    suspend fun updateVerseTopics(verseId: Long, topics: String)

    @Query("SELECT * FROM BibleVerse_Items ORDER BY lastModified DESC")
    fun getAllVerses(): Flow<List<BibleVerse>>

    @Query("SELECT * FROM Topics")
    fun getAllTopics(): Flow<List<Topic>>

    @Query("SELECT * FROM BibleVerse_Items WHERE id = :id")
    suspend fun getVerseById(id: Long): BibleVerse

    @Query("SELECT * FROM BibleVerse_Items WHERE id = :id")
    fun getVerseFlow(id: Long): Flow<BibleVerse>

    @Query("SELECT * FROM Topics WHERE topic = :topicName")
    suspend fun getTopicByName(topicName: String): Topic?

    @Query("SELECT * FROM BibleVerse_Items WHERE book = :book AND chapter = :chapter AND startVerse = :startVerse LIMIT 1")
    suspend fun findVerseByReference(book: String, chapter: Int, startVerse: Int): BibleVerse? // Nullable if not found

    /**
     * Counts how many Bible verses are currently associated with a given topic ID.
     * This checks the join table (CrossRefBibleVerseTopics).
     */
    @Query("SELECT COUNT(*) FROM CrossRefBibleVerseTopics WHERE topicId = :topicId")
    suspend fun countVersesForTopicId(topicId: Long): Int

    /**
     * Deletes a topic from the Topics table by its ID.
     */
    @Query("DELETE FROM Topics WHERE id = :topicId")
    suspend fun deleteTopicById(topicId: Long)

    // Alternative to deleteTopicById if you prefer deleting by name directly after checks):
    /**
     * Deletes a topic from the Topics table by its name.
     */
    @Query("DELETE FROM Topics WHERE topic = :topicName")
    suspend fun deleteTopicByName(topicName: String)

    /**
     * Gets all topics with their verse counts using a JOIN query.
     * This returns topics ordered by name, including the count of verses for each topic.
     */
    @Query("""
    SELECT t.topic, COUNT(c.bibleVerseId) as verseCount
    FROM Topics t
    LEFT JOIN CrossRefBibleVerseTopics c ON t.id = c.topicId
    GROUP BY t.id, t.topic
    ORDER BY t.topic
""")
    fun getAllTopicsWithCount(): Flow<List<TopicWithCount>>

    // The following functions is used to support renaming Topic
    @Query("UPDATE Topics SET topic = :newName WHERE id = :topicId")
    suspend fun updateTopicNameById(topicId: Long, newName: String)

    @Query("SELECT * FROM BibleVerse_Items WHERE topics LIKE '%' || :topicName || '%'")
    suspend fun getVersesContainingTopic(topicName: String): List<BibleVerse>

    @Transaction
    suspend fun renameTopicInDb(oldTopicName: String, newTopicName: String, oldTopicId: Long) {
        updateTopicNameById(oldTopicId, newTopicName)
        val potentiallyAffectedVerses: List<BibleVerse> = getVersesContainingTopic(oldTopicName)
        for (verse in potentiallyAffectedVerses) {
            val currentTopicsList: List<String> = verse.topics
            if (currentTopicsList.contains(oldTopicName)) {
                val updatedTopicsList: List<String> = currentTopicsList.map { topic ->
                    if (topic == oldTopicName) newTopicName else topic
                }
                if (updatedTopicsList != currentTopicsList) {
                    val newTopicsStringForDb = updatedTopicsList.joinToString(",")
                    updateVerseTopics(verse.id, newTopicsStringForDb)
                }
            }
        }
    }

    // --- Added for Merge Topics ---
    @Query("SELECT bibleVerseId FROM CrossRefBibleVerseTopics WHERE topicId = :topicId")
    suspend fun getVerseIdsForTopicId(topicId: Long): List<Long>

    @Query("DELETE FROM CrossRefBibleVerseTopics WHERE bibleVerseId = :verseId AND topicId = :topicId")
    suspend fun deleteCrossRef(verseId: Long, topicId: Long)

    // For safety, you might want to ensure no direct cross-references to the old topic ID remain after specific deletions.
    @Query("DELETE FROM CrossRefBibleVerseTopics WHERE topicId = :topicId")
    suspend fun deleteAllCrossRefsForTopicId(topicId: Long)

    @Transaction
    suspend fun mergeTopics(oldTopicId: Long, oldTopicName: String, targetTopicId: Long, targetTopicName: String) {
        // 1. Re-associate BibleVerses from oldTopicId to targetTopicId in CrossRefBibleVerseTopics
        val verseIdsWithOldTopic = getVerseIdsForTopicId(oldTopicId)
        val verseIdsAlreadyWithTargetTopic = getVerseIdsForTopicId(targetTopicId)

        for (verseIdInOld in verseIdsWithOldTopic) {
            // Remove the old association first.
            deleteCrossRef(verseIdInOld, oldTopicId)
            // Add new association to targetTopicId, only if this verse isn't already linked to the target topic.
            if (!verseIdsAlreadyWithTargetTopic.contains(verseIdInOld)) {
                insertCrossRef(CrossRefBibleVerseTopics(bibleVerseId = verseIdInOld, topicId = targetTopicId))
            }
        }
        // As an alternative to the loop, for more complex SQL scenarios (might need adjustments for Room):
        // First, remove cross references from the old topic that would cause duplicates if simply updated to target topic.
        // val conflictingVerseIdsQuery = "SELECT bibleVerseId FROM CrossRefBibleVerseTopics WHERE topicId = $oldTopicId AND bibleVerseId IN (SELECT bibleVerseId FROM CrossRefBibleVerseTopics WHERE topicId = $targetTopicId)"
        // Then update remaining: "UPDATE CrossRefBibleVerseTopics SET topicId = $targetTopicId WHERE topicId = $oldTopicId"
        // The programmatic loop above is safer and clearer with Room's DAO methods.

        // 2. Update the 'topics' string in BibleVerse_Items
        val versesToUpdate = getVersesContainingTopic(oldTopicName) // Find verses containing the old name string
        for (verse in versesToUpdate) {
            val currentTopicsList: List<String> = verse.topics

            if (currentTopicsList.contains(oldTopicName)) {
                // Map oldName to targetName, then make the list distinct to handle cases
                // where the verse might have contained both oldName and targetName.
                val updatedList = currentTopicsList
                    .map { if (it.equals(oldTopicName, ignoreCase = false)) targetTopicName else it }
                    .distinct() // Ensures targetTopicName is not duplicated

                if (updatedList != currentTopicsList) {
                    val newTopicsStringForDb = updatedList.joinToString(",")
                    updateVerseTopics(verse.id, newTopicsStringForDb)
                }
            }
        }

        // 3. Delete the old topic from Topics table (it's now merged)
        // Ensure all cross-references are gone before deleting the topic itself to prevent foreign key issues if any remain.
        deleteAllCrossRefsForTopicId(oldTopicId) // Belt-and-suspenders, primary logic is above.
        deleteTopicById(oldTopicId)
    }

    // Method to update favorite status
    @Query("UPDATE BibleVerse_Items SET favorite = :isFavorite WHERE id = :verseId")
    suspend fun updateFavoriteStatus(verseId: Long, isFavorite: Boolean)

    // Method to update translation
    @Query("UPDATE BibleVerse_Items SET translation = :translation WHERE id = :verseId")
    suspend fun updateTranslation(verseId: Long, translation: String)

    // Method to get all favorite verses
    @Query("SELECT * FROM BibleVerse_Items WHERE favorite = 1 ORDER BY lastModified DESC")
    fun getAllFavoriteVerses(): Flow<List<BibleVerse>>

    // Method to get verses by translation
    @Query("SELECT * FROM BibleVerse_Items WHERE translation = :translation ORDER BY lastModified DESC")
    fun getVersesByTranslation(translation: String): Flow<List<BibleVerse>>

    // Method to get favorite verses by translation
    @Query("SELECT * FROM BibleVerse_Items WHERE favorite = 1 AND translation = :translation ORDER BY lastModified DESC")
    fun getFavoriteVersesByTranslation(translation: String): Flow<List<BibleVerse>>

    @Query("""
        SELECT bv.* FROM BibleVerse_Items bv
        INNER JOIN CrossRefBibleVerseTopics cr ON bv.id = cr.bibleVerseId
        INNER JOIN Topics t ON cr.topicId = t.id
        WHERE t.topic = :topic
        ORDER BY bv.lastModified DESC
    """)
    fun getVersesByTopic(topic: String): Flow<List<BibleVerse>>

    // Helper method to create ScriptureContent from existing data
    @Query("SELECT * FROM BibleVerse_Items WHERE scriptureVerses = '' OR scriptureVerses IS NULL")
    suspend fun getVersesWithEmptyScriptureVerses(): List<BibleVerse>

    // Method to update scriptureJson for a specific verse
    @Query("UPDATE BibleVerse_Items SET scriptureVerses = :scriptureVerses WHERE id = :verseId")
    suspend fun updateScriptureVerses(verseId: Long, scriptureVerses: String)

}