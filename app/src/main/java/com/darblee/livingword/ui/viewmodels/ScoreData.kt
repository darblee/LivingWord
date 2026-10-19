package com.darblee.livingword.ui.viewmodels

import kotlinx.serialization.Serializable

@Serializable
data class ScoreData(
    val ContextScore: Int,
    val ContextExplanation: String,
    var ApplicationFeedback: String
)
