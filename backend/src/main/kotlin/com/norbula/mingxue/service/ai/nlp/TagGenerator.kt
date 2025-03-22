package com.norbula.mingxue.service.ai.nlp

interface TagGenerator {
    fun getTags(content: String): List<String>
}