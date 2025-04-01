package com.norbula.mingxue.service.ai.voice

import com.norbula.mingxue.models.ai.speech.SpeechWord
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service("speech_norbula")
class NorbulaVoiceGenerator: VoiceGenerator {
    private val logger = LoggerFactory.getLogger(NorbulaVoiceGenerator::class.java)

    private val webClient = WebClient.builder()
        .baseUrl("http://mingxue-tts-server:8081/api/v1")
        .defaultHeader("Content-Type", "application/json")
        .build()

    override fun generateTTSFiles(words: List<SpeechWord>) {
        val requestBody = TTSRequest(words = words)

        webClient.post()
            .uri("/process")
            .bodyValue(requestBody)
            .retrieve()
            .toBodilessEntity()  // Expect no response body
            .block()  // Wait for completion
    }
}