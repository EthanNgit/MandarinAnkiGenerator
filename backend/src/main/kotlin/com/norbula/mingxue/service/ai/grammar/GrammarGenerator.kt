package com.norbula.mingxue.service.ai.grammar

import com.norbula.mingxue.models.ai.grammar.GeneratedWord

public interface GrammarGenerator {
    fun generateWords(amount: Int, topic: String): List<GeneratedWord>
    fun generateSentences(amount: Int, topic: String)
    fun isSameWordBasedOnContext(word: String, str1: String, str2: String): Boolean
    fun areWordsSameBasedOnContextBatch(comparisons: List<Triple<String, String, String>>): List<Boolean>
}