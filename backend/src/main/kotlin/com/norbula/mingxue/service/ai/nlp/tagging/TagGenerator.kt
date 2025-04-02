package com.norbula.mingxue.service.ai.nlp.tagging

interface TagGenerator {
    fun getTags(content: String): List<String>
}