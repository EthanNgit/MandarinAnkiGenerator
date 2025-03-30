package com.norbula.mingxue.models.ai.speech

import com.fasterxml.jackson.annotation.JsonProperty

data class SpeechWord (
    @JsonProperty("context_id")
    val contextId: Int,
    val text: String,
    val pronunciation: String,
)