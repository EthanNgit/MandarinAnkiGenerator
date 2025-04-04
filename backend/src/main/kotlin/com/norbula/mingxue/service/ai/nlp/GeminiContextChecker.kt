package com.norbula.mingxue.service.ai.nlp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.norbula.mingxue.service.ai.grammar.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service("context_gemini")
class GeminiContextChecker(
    @Value("\${gemini.api.key}") private val geminiApiKey: String,
) : ContextChecker {
    private val logger = LoggerFactory.getLogger(OpenAIGrammarGenerator::class.java)

    private val webClient = WebClient.builder()
        .baseUrl("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent")
        .defaultHeader("Content-Type", "application/json")
        .build()

    override fun isSameWordBasedOnContext(word: String, str1: String, str2: String): Boolean {
        val requestBody = GeminiRequest(
            contents = listOf(
                ContentItem(
                    role = "user",
                    parts = listOf(
                        PartItem(
                            text = """
                                You only output true or false. Dont include any explanations or introductions.
                                
                                "is \"$word\" the same word based on context in \"$str1\" and \"$str2\"?
                                """.trimIndent()
                        )
                    )
                )
            ),
            generationConfig = GenerationConfig(temperature = 0.0)
        )

        return try {
            webClient.post()
                .uri { uriBuilder ->
                    uriBuilder.queryParam("key", geminiApiKey).build()
                }
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(GeminiResponse::class.java)
                .map { response ->
                    val content = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                    content.trim().lowercase() == "true"
                }
                .block() ?: false
        } catch (e: Exception) {
            logger.error("Error calling Gemini API: ${e.message}", e)
            false
        }
    }

    override fun areWordsSameBasedOnContextBatch(comparisons: List<Triple<String, String, String>>): List<Boolean> {
        if (comparisons.isEmpty()) return emptyList()

        val requestBody = GeminiRequest(
            contents = buildBatchContents(comparisons),
            generationConfig = GenerationConfig(temperature = 0.0)
        )

        return try {
            val response = webClient.post()
                .uri { uriBuilder ->
                    uriBuilder.queryParam("key", geminiApiKey).build()
                }
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(GeminiResponse::class.java)
                .block()


            var responseText = response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            responseText = responseText.trim()
            responseText = responseText.removePrefix("```json").trim()
            responseText = responseText.removeSuffix("```").trim()
            logger.debug("Gemini response for batch context: $responseText")

            try {
                jacksonObjectMapper().readValue(responseText, List::class.java) as List<Boolean>
            } catch (e: Exception) {
                logger.error("Error parsing response content", e)
                List(comparisons.size) { false }
            }
        } catch (e: Exception) {
            logger.error("Error during batch context check", e)
            List(comparisons.size) { false }
        }
    }

    private fun buildBatchContents(comparisons: List<Triple<String, String, String>>): List<ContentItem> {
        val prompt = StringBuilder()
        prompt.append("You only output true or false values as a JSON array: [true, false, ...] in the same order given. Don't include explanations.\n\n")

        comparisons.forEachIndexed { index, comparison ->
            prompt.append("${index + 1}. Is the word \"${comparison.first}\" the same word based on context in:\n")
            prompt.append("\"${comparison.second}\" and \"${comparison.third}\"?\n\n")
        }

        return listOf(
            ContentItem(
                role = "user",
                parts = listOf(
                    PartItem(
                        text = prompt.toString().trim()
                    )
                )
            )
        )
    }

}