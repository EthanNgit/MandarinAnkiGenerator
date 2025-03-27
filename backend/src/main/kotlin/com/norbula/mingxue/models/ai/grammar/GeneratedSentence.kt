package com.norbula.mingxue.models.ai.grammar

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.Column

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeneratedSentence (
    val simplifiedSentence: String,
    val traditionalSentence: String,
    val pinyin: String,
    val translation: String,
)