package com.norbula.mingxue.modules.models.llm

import reactor.core.publisher.Mono

public interface GrammarGenerator {
    fun generateWords(amount: Int, topic: String): Mono<List<GeneratedWord>>
    fun generateSentences(amount: Int, topic: String)
}