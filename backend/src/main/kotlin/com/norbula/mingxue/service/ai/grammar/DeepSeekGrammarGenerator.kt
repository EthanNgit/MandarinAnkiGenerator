package com.norbula.mingxue.service.ai.grammar

import com.norbula.mingxue.models.ai.grammar.GeneratedWord
import org.springframework.stereotype.Service

@Service("grammar_deepSeek")
class DeepSeekGrammarGenerator: GrammarGenerator {
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