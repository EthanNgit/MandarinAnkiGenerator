package com.norbula.mingxue.service.ai.nlp.context

interface ContextChecker {
    fun isSameWordBasedOnContext(word: String, str1: String, str2: String): Boolean
    fun areWordsSameBasedOnContextBatch(comparisons: List<Triple<String, String, String>>): List<Boolean>
}