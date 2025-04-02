package com.norbula.mingxue.service.ai.grammar

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.norbula.mingxue.models.GrammarPoint
import com.norbula.mingxue.models.ai.grammar.GeneratedSentence
import com.norbula.mingxue.models.ai.grammar.GeneratedWord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

// DeepSeek response structure
data class DeepSeekResponse(
    val choices: List<DeepSeekChoice>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DeepSeekChoice(
    val message: DeepSeekMessage
)

data class DeepSeekMessage(
    val content: String // as json
)

@Service("generator_deepSeek")
class DeepSeekGrammarGenerator(
    @Value("\${deepseek.api.key}") private val deepSeekApiKey: String,
    @Value("\${norbula.mingxue.default.generative.max.wordlist}") private val maxWordListSize: Int,
    @Value("\${norbula.mingxue.default.generative.max.worddetail}") private val maxWordDetailChunkSize: Int
): GrammarGenerator {
    private val logger = LoggerFactory.getLogger(DeepSeekGrammarGenerator::class.java)

    private val webClient = WebClient.builder()
        .baseUrl("https://api.deepseek.com/v1/chat/completions")
        .defaultHeader("Authorization", "Bearer $deepSeekApiKey")
        .defaultHeader("Content-Type", "application/json")
        .build()

    override fun generateWords(amount: Int, topic: String): List<GeneratedWord> {
        val words = generateWordList(amount, topic)
        if (words.isEmpty()) return emptyList()

        val batches = words.chunked(maxWordDetailChunkSize)

        val requests = batches.map { batch ->
            logger.debug("THE ARRAY IS ${batch.joinToString(", ", "[", "]")}")
            val requestBody = mapOf(
                "model" to "deepseek-chat",  // Use appropriate DeepSeek model
                "messages" to listOf(
                    mapOf("role" to "system", "content" to "You only output JSON. Dont include any explanations or introductions."),
                    mapOf("role" to "user", "content" to """
                    Fill in the details of the given Mandarin words in the context of "$topic" in this exact JSON array format:
                    [
                        {
                            "simplifiedWord": "...",
                            "traditionalWord": "...",
                            "partOfSpeech": "<one of: Noun, Pronoun, Verb, Adjective, Adverb, Preposition, Conjunction, Determiner, Classifier, Particle, Interjection>",
                            "pinyin": "...", // use numbered pinyin such as 桌子 -> zhuo1zi3. ALWAYS make sure to use 5 for no tone/neutral tone such as 模糊 -> mo3hu5 or 的 -> de5. The pinyin must be correct double check.
                            "translation": "...",
                            "simplifiedSentence": "...", // simple sentence using word
                            "traditionalSentence": "...", // same sentence but traditional
                            "sentencePinyin": "...", // pinyin of the same sentence, use numbered pinyin such as 桌子 -> zhuo1zi3, the pinyin must be correct double check, make sure to use 5 for no tone/neutral tone such as 模糊 -> mo3hu5 or 的 -> de5
                            "sentenceTranslation": "..." // translation of the sentence
                            "usageFrequency": "<one of: Frequent, Periodic, Infrequent>",
                            "tags": ["tag1", "tag2", "tag3"]  // Add tags here for searchability
                        }
                    ].
                    Words: ${batch.joinToString(", ", "[", "]")}
                """.trimIndent())
                ),
                "temperature" to 0.9
            )

            webClient.post()
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(DeepSeekResponse::class.java)
                .map { response ->
                    val contentJson = response.choices.firstOrNull()?.message?.content ?: "[]"
                    logger.debug("Raw DeepSeek Response: {}", contentJson)  // Add this for debugging

                    val jsonNode: JsonNode = jacksonObjectMapper().readTree(contentJson)

                    jacksonObjectMapper().convertValue(jsonNode, object : TypeReference<List<GeneratedWord>>() {})
                }
                .onErrorResume { e ->
                    logger.error("Error during word generation", e)
                    Mono.just(emptyList())
                }
        }

        return Flux.merge(requests)
            .collectList()
            .block()
            ?.flatten()
            ?: emptyList()
    }

    private fun generateWordList(amount: Int, topic: String): List<String> {
        val generateAmount = amount.coerceAtMost(maxWordListSize)
        val objectMapper = jacksonObjectMapper()

        val requestBody = mapOf(
            "model" to "deepseek-chat",  // Use appropriate DeepSeek model
            "messages" to listOf(
                mapOf("role" to "system", "content" to "You only output JSON. Dont include any explanations or introductions."),
                mapOf("role" to "user", "content" to """
                    Generate exactly $generateAmount Unique Mandarin words about "$topic" in this exact JSON array format:
                [
                    "word1",
                    "word2",
                    "word3",
                    ...
                ]
                """.trimIndent())
            ),
            "temperature" to 0.7
        )

        return webClient.post()
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(DeepSeekResponse::class.java)
            .map { response ->
                val contentJson = response.choices.firstOrNull()?.message?.content ?: "[]"
                logger.debug("Raw DeepSeek Response: {}", contentJson)

                val jsonNode: JsonNode = objectMapper.readTree(contentJson)
                objectMapper.convertValue(jsonNode, object : TypeReference<List<String>>() {}).distinct()
            }
            .onErrorResume { e ->
                logger.error("Error during word list generation", e)
                Mono.just(emptyList())
            }
            .block()
            .orEmpty()
    }

    override fun generateSentencesGrammarConcept(amount: Int, concept: GrammarPoint): List<GeneratedSentence> {
        TODO("Not yet implemented")
    }

    override fun generateSentencesUsingWord(amount: Int, word: String): List<GeneratedSentence> {
        TODO("Not yet implemented")
    }
}