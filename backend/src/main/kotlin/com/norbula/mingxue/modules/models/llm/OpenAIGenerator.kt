package com.norbula.mingxue.modules.models.llm

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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

@Service("openAi")
class OpenAIGenerator(
    @Value("\${openai.api.key}") private val openAiApiKey: String
): GrammarGenerator {
    private val webClient = WebClient.builder()
        .baseUrl("https://api.openai.com/v1/chat/completions")
        .defaultHeader("Authorization", "Bearer $openAiApiKey")
        .defaultHeader("Content-Type", "application/json")
        .build()

    override fun generateWords(amount: Int, topic: String): Mono<List<GeneratedWord>> {
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
                            "usageFrequency": "<one of: Frequent, Periodic, Infrequent>"
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

                val jsonNode: JsonNode = jacksonObjectMapper().readTree(contentJson)

                jacksonObjectMapper().convertValue(jsonNode, object : TypeReference<List<GeneratedWord>>() {})
            }
            .onErrorReturn(emptyList())
    }

    override fun generateSentences(amount: Int, topic: String) {
        TODO("Not yet implemented")
    }
}