package com.norbula.mingxue.service.ai.vector

import com.norbula.mingxue.service.ai.grammar.OpenAIGrammarGenerator
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service("embedding_openAi")
class OpenAIEmbeddingGenerator (
    @Value("\${openai.api.key}") private val openAiApiKey: String
): EmbeddingGenerator {
    private val logger = LoggerFactory.getLogger(OpenAIGrammarGenerator::class.java)

    private val webClient = WebClient.builder()
        .baseUrl("https://api.openai.com/v1/embeddings")
        .defaultHeader("Authorization", "Bearer $openAiApiKey")
        .defaultHeader("Content-Type", "application/json")
        .build()

    override fun getEmbedding(word: String): List<Float> {
        val requestBody = mapOf(
            "model" to "text-embedding-3-small",
            "input" to word
        )

        return try {
            val response = webClient.post()
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(OpenAIEmbeddingResponse::class.java)
                .block() // Blocking call to wait for the response

            response?.data?.firstOrNull()?.embedding ?: emptyList()
        } catch (e: Exception) {
            logger.error("Error getting embedding for word: $word", e)
            emptyList()
        }
    }

    override fun getBatchEmbedding(words: List<String>): List<List<Float>> {
        if (words.isEmpty()) return emptyList()

        val requestBody = mapOf(
            "model" to "text-embedding-3-small",
            "input" to words
        )

        return try {
            val response = webClient.post()
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(OpenAIEmbeddingResponse::class.java)
                .block() // Blocking call to wait for the response

            response?.data?.map { it.embedding } ?: emptyList()
        } catch (e: Exception) {
            logger.error("Error getting batch embeddings for words: $words", e)
            emptyList()
        }
    }

    // Data class for parsing OpenAI response
    private data class OpenAIEmbeddingResponse(
        val data: List<EmbeddingData>
    )

    private data class EmbeddingData(
        val embedding: List<Float>
    )
}