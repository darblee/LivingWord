package com.darblee.livingword.data

import androidx.room.Entity
import androidx.room.PrimaryKey

data class TopicWithCount(
    val topic: String,
    val verseCount: Int
)

@Entity(tableName = "Topics")
data class Topic(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val topic: String
)

