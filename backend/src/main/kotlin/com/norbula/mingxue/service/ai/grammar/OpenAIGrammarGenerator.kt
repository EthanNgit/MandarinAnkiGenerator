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
    @Value("\${openai.api.key}") private val openAiApiKey: String,
    @Value("\${norbula.mingxue.default.generative.max.wordlist}") private val maxWordListSize: Int,
    @Value("\${norbula.mingxue.default.generative.max.worddetail}") private val maxWordDetailChunkSize: Int
): GrammarGenerator {
    private val logger = LoggerFactory.getLogger(OpenAIGrammarGenerator::class.java)

    private val webClient = WebClient.builder()
        .baseUrl("https://api.openai.com/v1/chat/completions")
        .defaultHeader("Authorization", "Bearer $openAiApiKey")
        .defaultHeader("Content-Type", "application/json")
        .build()

    override fun generateWords(amount: Int, topic: String): List<GeneratedWord> {
        val words = generateWordList(amount, topic)
        if (words.isEmpty()) return emptyList()

        val batches = words.chunked(maxWordDetailChunkSize)

        val requests = batches.map { batch ->
            logger.debug("THE ARRAY IS ${batch.joinToString(", ", "[", "]")}")
            val requestBody = mapOf(
                "model" to "gpt-4o-mini",
                "messages" to listOf(
                    mapOf("role" to "system", "content" to "You only output JSON. Dont include any explanations or introductions."),
                    mapOf("role" to "user", "content" to """
                    Fill in the details of the given Mandarin words in the context of "$topic" in this exact JSON array format:
                    [
                        {
                            "simplifiedWord": "...",
                            "traditionalWord": "...",
                            "partOfSpeech": "<one of: Noun, Pronoun, Verb, Adjective, Adverb, Preposition, Conjunction, Determiner, Classifier, Particle, Interjection>",
                            "pinyin": "...", // use numbered pinyin such as 桌子 -> zhuo1zi3, the pinyin must be correct double check (omit tone 5 such as 椅子 -> yi3zi)
                            "translation": "...",
                            "simplifiedSentence": "...", // simple sentence using word
                            "traditionalSentence": "...", // same sentence but traditional
                            "sentencePinyin": "...", // pinyin of the same sentence, use numbered pinyin such as 桌子 -> zhuo1zi3, the pinyin must be correct double check (omit tone 5 such as 椅子 -> yi3zi)
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
            "model" to "gpt-4o-mini",
            "messages" to listOf(
                mapOf("role" to "system", "content" to "You only output JSON. Dont include any explanations or introductions."),
                mapOf("role" to "user", "content" to """
                    Generate exactly $generateAmount Unique Mandarin words about "$topic" in this exact JSON array format:
                [
                    \"word1\",
                    \"word2\",
                    \"word3\",
                    ...
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
                logger.debug("Raw GPT Response: {}", contentJson)

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

    override fun generateSentencesUsingWord(amount: Int, word: String): List<GeneratedSentence> {
        val requestBody = mapOf(
            "model" to "gpt-4o-mini",
            "messages" to listOf(
                mapOf("role" to "system", "content" to "You only output JSON. Dont include any explanations or introductions."),
                mapOf("role" to "user", "content" to """
                    Generate $amount unique sentences using mandarin. Each sentence must include or use the word "$word" in some way. Use this exact JSON array format:
                    [
                        {
                            "simplifiedSentence": "...",
                            "traditionalSentence": "...",
                            "pinyin": "...", // use numbered pinyin such as 桌子 -> zhuo1zi3, the pinyin must be correct double check  (omit tone 5 such as 椅子 -> yi3zi)
                            "translation": "..."
                        }
                    ]
                """.trimIndent()),
            "temperature" to 0.9
            )
        )

        return try {
            webClient.post()
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(OpenAIResponse::class.java)
                .map { response ->
                    val contentJson = response.choices.firstOrNull()?.message?.content ?: "[]"
                    logger.debug("Raw GPT Response: {}", contentJson)  // Add this for debugging

                    val jsonNode: JsonNode = jacksonObjectMapper().readTree(contentJson)

                    jacksonObjectMapper().convertValue(jsonNode, object : TypeReference<List<GeneratedSentence>>() {})
                }
                .block() ?: emptyList()
        } catch (e: Exception) {
            println("Error calling OpenAI API: ${e.message}")
            emptyList()
        }
    }

    override fun generateSentencesGrammarConcept(amount: Int, concept: GrammarPoint): List<GeneratedSentence> {
        val requestBody = mapOf(
            "model" to "gpt-4o-mini",
            "messages" to listOf(
                mapOf("role" to "system", "content" to "You only output JSON. Dont include any explanations or introductions."),
                mapOf("role" to "user", "content" to """
                    Generate $amount unique but simple sentences using mandarin. Each sentence must use the grammar concept "${concept.context.word.simplifiedWord} as used in the sentence ${concept.context.usageSentence}". Use this exact JSON array format:
                    [
                        {
                            "simplifiedSentence": "...",
                            "traditionalSentence": "...",
                            "pinyin": "...", // use numbered pinyin such as 桌子 -> zhuo1zi3, the pinyin must be correct double check  (omit tone 5 such as 椅子 -> yi3zi)
                            "translation": "..."
                        }
                    ]
                """.trimIndent()),
                "temperature" to 0.9
            )
        )

        return try {
            webClient.post()
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(OpenAIResponse::class.java)
                .map { response ->
                    val contentJson = response.choices.firstOrNull()?.message?.content ?: "[]"
                    logger.debug("Raw GPT Response: {}", contentJson)  // Add this for debugging

                    val jsonNode: JsonNode = jacksonObjectMapper().readTree(contentJson)

                    jacksonObjectMapper().convertValue(jsonNode, object : TypeReference<List<GeneratedSentence>>() {})
                }
                .block() ?: emptyList()
        } catch (e: Exception) {
            println("Error calling OpenAI API: ${e.message}")
            emptyList()
        }
    }

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