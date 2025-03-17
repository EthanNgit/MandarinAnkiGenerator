package com.norbula.mingxue.modules.models.llm

import reactor.core.publisher.Mono

class DeepSeekGenerator: GrammarGenerator {
    override fun generateWords(amount: Int, topic: String): List<GeneratedWord> {
        TODO("Not yet implemented")
    }

    override fun generateSentences(amount: Int, topic: String) {
        TODO("Not yet implemented")
    }

    override fun isSameWordBasedOnContext(word: String, str1: String, str2: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun areWordsSameBasedOnContextBatch(comparisons: List<Triple<String, String, String>>): List<Boolean> {
        TODO("Not yet implemented")
    }
}