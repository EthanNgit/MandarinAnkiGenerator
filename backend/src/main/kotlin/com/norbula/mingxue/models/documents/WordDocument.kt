package com.norbula.mingxue.models.documents

import com.norbula.mingxue.models.WordDTO
import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document

@Document(indexName = "words_index")
data class WordDocument (
    @Id
    val id: Int? = null,
    val simplifiedWord: String,
    val traditionalWord: String,
    val pinyin: String,
    val partOfSpeech: String,
    val translation: String,
    val tagIds: List<Int>,
    val embedding: List<Float>
) {
    fun toDTO() = WordDTO(
        id = id,
        simplifiedWord = simplifiedWord,
        traditionalWord = traditionalWord,
        pinyin = pinyin,
        partOfSpeech = partOfSpeech,
        translation = translation,
    )
}