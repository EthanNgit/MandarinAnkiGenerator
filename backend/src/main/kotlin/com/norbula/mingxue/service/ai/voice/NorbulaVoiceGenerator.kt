package com.norbula.mingxue.service.ai.voice

import com.norbula.mingxue.models.ai.speech.SpeechWord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service("speech_norbula")
class NorbulaVoiceGenerator: VoiceGenerator {
    private val logger = LoggerFactory.getLogger(NorbulaVoiceGenerator::class.java)

    private val webClient = WebClient.builder()
        .baseUrl("http://mingxue-tts-server:8081/api/v1")
        .defaultHeader("Content-Type", "application/json")
        .build()

    override fun generateTTSWordFiles(words: List<SpeechWord>) {
        val requestBody = TTSRequest(words = words)

        webClient.post()
            .uri("/process/word")
            .bodyValue(requestBody)
            .retrieve()
            .toBodilessEntity()
            .block()
    }

    override fun generateTTSSentenceFiles(words: List<SpeechWord>) {
        val requestBody = TTSRequest(words = words)

        webClient.post()
            .uri("/process/sentence")
            .bodyValue(requestBody)
            .retrieve()
            .toBodilessEntity()
            .block()
    }

    override fun getTTSFile(id: Int,  sentence: Boolean): ByteArray? {
        val path = "/get/$id/${if (!sentence) "word" else "sentence"}"
        return try {
            val response = webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(ByteArray::class.java)
                .block()

            response

        } catch (e: Exception) {
            logger.error("Error fetching audio for ID $id", e)
            null
        }
    }
}