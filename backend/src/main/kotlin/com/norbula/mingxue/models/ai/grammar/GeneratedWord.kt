package com.norbula.mingxue.models.ai.grammar

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeneratedWord (
    val simplifiedWord: String,
    val traditionalWord: String,
    val partOfSpeech: String,
    val pinyin: String,
    val translation: String,
    val simplifiedSentence: String,
    val traditionalSentence: String,
    val sentencePinyin: String,
    val sentenceTranslation: String,
    val usageFrequency: String,
    val tags: List<String>,
)