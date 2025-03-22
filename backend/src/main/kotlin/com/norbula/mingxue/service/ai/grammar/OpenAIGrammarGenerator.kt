package com.norbula.mingxue.service.ai.grammar

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.norbula.mingxue.models.ai.grammar.GeneratedWord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

data class OpenAIResponse(
    val choices: List<Choice>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Choice(
    val message: Message
)

data class Message(
    val content: String // as json
)

@Service("grammar_openAi")
class OpenAIGrammarGenerator(
    @Value("\${openai.api.key}") private val openAiApiKey: String
): GrammarGenerator {
    private val logger = LoggerFactory.getLogger(OpenAIGrammarGenerator::class.java)

    private val webClient = WebClient.builder()
        .baseUrl("https://api.openai.com/v1/chat/completions")
        .defaultHeader("Authorization", "Bearer $openAiApiKey")
        .defaultHeader("Content-Type", "application/json")
        .build()

    override fun generateWords(amount: Int, topic: String): List<GeneratedWord> {
        val generateAmount = amount.coerceAtMost(20)

        val requestBody = mapOf(
            "model" to "gpt-4o-mini",
            "messages" to listOf(
                mapOf("role" to "system", "content" to "You only output JSON. Dont include any explanations or introductions."),
                mapOf("role" to "user", "content" to """
                    Generate $generateAmount Mandarin words about "$topic" in this exact JSON array format:
                    [
                        {
                            "simplifiedWord": "...",
                            "traditionalWord": "...",
                            "partOfSpeech": "<one of: Noun, Pronoun, Verb, Adjective, Adverb, Preposition, Conjunction, Determiner, Classifier, Particle, Interjection>",
                            "pinyin": "...",
                            "translation": "...",
                            "simpleSentence": "...",
                            "usageFrequency": "<one of: Frequent, Periodic, Infrequent>",
                            "tags": ["tag1", "tag2", "tag3"]  // Add tags here for searchability
                        }
                    ]
                """.trimIndent())
            ),
            "temperature" to 0.7
        )

        return webClient.post()
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(OpenAIResponse::class.java)
            .map { response ->
                val contentJson = response.choices.firstOrNull()?.message?.content ?: "[]"
                logger.debug("Raw GPT Response: {}", contentJson)  // Add this for debugging


                val jsonNode: JsonNode = jacksonObjectMapper().readTree(contentJson)

                jacksonObjectMapper().convertValue(jsonNode, object : TypeReference<List<GeneratedWord>>() {})
            }
            .onErrorResume { e ->
                logger.error("Error during word generation", e)
                Mono.just(emptyList())
            }
            .block()
            .orEmpty()
    }

    override fun generateSentences(amount: Int, topic: String) {
        TODO("Not yet implemented")
    }

    override fun isSameWordBasedOnContext(word: String, str1: String, str2: String): Boolean {
        val requestBody = mapOf(
            "model" to "gpt-4o-mini",
            "messages" to listOf(
                mapOf("role" to "system", "content" to "You only output true or false. Dont include any explanations or introductions."),
                mapOf("role" to "user", "content" to "$word is used the same in \"$str1\" and \"$str2\"?".trimIndent())
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
        val requestBody = mapOf(
            "model" to "gpt-4o-mini",
            "messages" to buildBatchMessages(comparisons)
        )

        val response = webClient.post()
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map::class.java)
            .block()

        val results = (response?.get("choices") as? List<Map<String, Any>>)
            ?.mapNotNull { it["message"] as? Map<*, *> }
            ?.mapNotNull { it["content"] as? String }
            ?.map { it.trim().lowercase() == "true" }
            ?: List(comparisons.size) { false }

        return results
    }

    private fun buildBatchMessages(comparisons: List<Triple<String, String, String>>): List<Map<String, String>> {
        val systemMessage = mapOf("role" to "system", "content" to "You only output true or false. Don't include explanations.")
        val userMessages = comparisons.map {
            mapOf("role" to "user", "content" to "${it.first} is used the same in \"${it.second}\" and \"${it.third}\"?")
        }

        return listOf(systemMessage) + userMessages
    }
}