package com.darblee.livingword.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Topics")
data class Topic(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val topic: String
)

