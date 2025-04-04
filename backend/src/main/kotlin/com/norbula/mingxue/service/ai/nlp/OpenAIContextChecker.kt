package com.norbula.mingxue.service.ai.nlp.context

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.norbula.mingxue.service.ai.grammar.OpenAIGrammarGenerator
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service("context_openAi")
class OpenAIContextChecker(
    @Value("\${openai.api.key}") private val openAiApiKey: String
) : ContextChecker {
    private val logger = LoggerFactory.getLogger(OpenAIGrammarGenerator::class.java)

    private val webClient = WebClient.builder()
        .baseUrl("https://api.openai.com/v1/chat/completions")
        .defaultHeader("Authorization", "Bearer $openAiApiKey")
        .defaultHeader("Content-Type", "application/json")
        .build()


    override fun isSameWordBasedOnContext(word: String, str1: String, str2: String): Boolean {
        val requestBody = mapOf(
            "model" to "gpt-4o-mini",
            "messages" to listOf(
                mapOf("role" to "system", "content" to "You only output true or false. Dont include any explanations or introductions."),
                mapOf("role" to "user", "content" to "is \"$word\" the same word based on context in \"$str1\" and \"$str2\"?".trimIndent())
            )
        )

        return try {
            webClient.post()
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map::class.java)
                .map { response ->
                    val choices = response["choices"] as? List<Map<String, Any>> ?: return@map false
                    val message = choices.getOrNull(0)?.get("message") as? Map<*, *> ?: return@map false
                    val content = message["content"] as? String ?: return@map false

                    content.trim().lowercase() == "true"
                }
                .block() ?: false
        } catch (e: Exception) {
            println("Error calling OpenAI API: ${e.message}")
            false
        }
    }

    override fun areWordsSameBasedOnContextBatch(comparisons: List<Triple<String, String, String>>): List<Boolean> {
        if (comparisons.isEmpty()) return emptyList()

        val requestBody = mapOf(
            "model" to "gpt-4o-mini",
            "messages" to buildBatchMessages(comparisons)
        )

        val response = webClient.post()
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map::class.java)
            .block()

        logger.debug("Debug for context batching: $response")
        val results = (response?.get("choices") as? List<Map<String, Any>>)
            ?.mapNotNull { it["message"] as? Map<*, *> }
            ?.mapNotNull { it["content"] as? String }
            ?.map {
                try {
                    jacksonObjectMapper().readValue(it, List::class.java) as List<Boolean>
                } catch (e: Exception) {
                    logger.error("Error parsing response content", e)
                    listOf(false)  // Default to false in case of parsing error
                }
            }
            ?.flatten() // Flatten in case the list is nested
            ?: List(comparisons.size) { false }

        logger.debug("Inspected contexts of {} words with results {}", comparisons.size, results)
        return results
    }

    private fun buildBatchMessages(comparisons: List<Triple<String, String, String>>): List<Map<String, String>> {
        val systemMessage = mapOf("role" to "system", "content" to "You only output true or false values as a JSON array: [true, false, ...] in the same order given. Don't include explanations.")

        val userMessages = comparisons.mapIndexed { ind, comparison ->
            mapOf("role" to "user", "content" to """
            ${ind + 1}. Is the word "${comparison.first}" the same word based on context in:
            "${comparison.second}" and "${comparison.third}"?
        """.trimIndent())
        }

        return listOf(systemMessage) + userMessages
    }

}