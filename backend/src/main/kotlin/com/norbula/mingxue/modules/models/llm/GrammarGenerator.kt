package com.norbula.mingxue.modules.models.llm

import reactor.core.publisher.Mono

public interface GrammarGenerator {
    fun generateWords(amount: Int, topic: String): List<GeneratedWord>
    fun generateSentences(amount: Int, topic: String)
    fun isSameWordBasedOnContext(word: String, str1: String, str2: String): Boolean
    fun areWordsSameBasedOnContextBatch(comparisons: List<Triple<String, String, String>>): List<Boolean>
}