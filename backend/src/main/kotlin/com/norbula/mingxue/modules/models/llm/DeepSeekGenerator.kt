package com.norbula.mingxue.modules.models.llm

import reactor.core.publisher.Mono

class DeepSeekGenerator: GrammarGenerator {
    override fun generateWords(amount: Int, topic: String): Mono<List<GeneratedWord>> {
        TODO("Not yet implemented")
    }

    override fun generateSentences(amount: Int, topic: String) {
        TODO("Not yet implemented")
    }
}