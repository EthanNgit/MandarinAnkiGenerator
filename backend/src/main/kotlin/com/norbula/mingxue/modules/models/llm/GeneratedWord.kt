package com.norbula.mingxue.modules.models.llm

data class GeneratedWord (
    val simplifiedWord: String,
    val traditionalWord: String,
    val partOfSpeech: String,
    val pinyin: String,
    val translation: String,
    val simpleSentence: String,
    val usageFrequency: String,
    val tags: List<String>,
)