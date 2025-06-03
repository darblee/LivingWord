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

    @Delete
    suspend fun deleteVerse(bibleVerse: BibleVerse)

    @Transaction
    suspend fun insertVerseWithTopics(
        book: String,
        chapter: Int,
        startVerse: Int,
        endVerse: Int,
        scripture: String,
        aiResponse: String,
        topics: List<String>
    ): Long {
        val verseId = insertVerse(
            BibleVerse(
                book = book,
                chapter = chapter,
                startVerse = startVerse,
                endVerse = endVerse,
                scripture = scripture,
                aiResponse = aiResponse,
                topics = emptyList(), // Insert with empty topics initially, will be updated
            )
        )
        topics.forEach { topicName ->
            val existingTopic = getTopicByName(topicName)
            val topicId = existingTopic?.id ?: insertTopic(Topic(topic = topicName))
            insertCrossRef(CrossRefBibleVerseTopics(bibleVerseId = verseId, topicId = topicId))
        }


        // Update the BibleVerse entity with the actual topics (for easier retrieval if needed later)

        // Room DB is interpreting  the list of topics as multiple parameters in the SQL UPDATE statement, which is incorrect.
        // To fix this, you need to store the list of topics as a single string in the database and then convert it back to a list
        //
        // Convert the list of topics to a string representation before updating
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
}
