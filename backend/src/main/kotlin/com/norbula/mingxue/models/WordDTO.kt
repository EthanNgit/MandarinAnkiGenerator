package com.norbula.mingxue.models

data class WordDTO(
    val id: Int? = null,
    val simplifiedWord: String,
    val traditionalWord: String,
    val pinyin: String,
    val partOfSpeech: String,
    val translation: String,
)
