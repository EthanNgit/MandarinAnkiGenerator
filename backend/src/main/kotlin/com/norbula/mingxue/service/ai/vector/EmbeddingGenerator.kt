package com.norbula.mingxue.service.ai.vector

interface EmbeddingGenerator {
    fun getEmbedding(word: String): List<Float>
    fun getBatchEmbedding(words: List<String>): List<List<Float>>
}