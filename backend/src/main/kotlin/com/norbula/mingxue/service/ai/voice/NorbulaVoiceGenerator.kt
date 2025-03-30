package com.norbula.mingxue.service.ai.voice

import com.norbula.mingxue.service.ai.grammar.OpenAIGrammarGenerator
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.reactive.function.client.WebClient

class NorbulaVoiceGenerator: VoiceGenerator {
    private val logger = LoggerFactory.getLogger(NorbulaVoiceGenerator::class.java)

    private val webClient = WebClient.builder()
        .baseUrl("http://mingxue-tts-server:8081")
        .defaultHeader("Content-Type", "application/json")
        .build()

    override fun generateTTSFiles(words: List<String>): List<String> {
        val requestBody = mapOf("words" to words)

        return webClient.post()
            .uri("/process")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(object : ParameterizedTypeReference<List<String>>() {})
            .block()
            .orEmpty()
    }
}