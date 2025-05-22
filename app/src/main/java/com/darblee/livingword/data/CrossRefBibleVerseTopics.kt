package com.darblee.livingword.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "CrossRefBibleVerseTopics",
    primaryKeys = ["bibleVerseId", "topicId"],
    foreignKeys = [
        ForeignKey(
            entity = BibleVerse::class,
            parentColumns = ["id"],
            childColumns = ["bibleVerseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Topic::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.CASCADE
        )
    ],

    // The index on topicId is a performance optimization.  It anticipates that many queries will
    // involve looking up Bible verses by their associated topics.  Whether to also include an
    // index on bibleVerseId depends on the specific needs of your application and the queries it
    // will execute.
    indices = [
        Index("topicId")
    ]
)
data class CrossRefBibleVerseTopics(
    val bibleVerseId: Long,
    val topicId: Long
)