package com.norbula.mingxue.modules.models.llm

public interface GrammarGenerator {
    fun generateWords(amount: Int, topic: String): List<GeneratedWord>
    fun generateSentences(amount: Int, topic: String)
}