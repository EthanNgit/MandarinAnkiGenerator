package com.norbula.mingxue.service.ai.nlp.tagging

import com.google.auth.oauth2.GoogleCredentials
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.io.FileInputStream

@Service("tagging_google")
class GoogleTagGenerator(
    @Value("\${google.service.account.key}") private val googleCredentialPath: String
): TagGenerator {
    private val logger = LoggerFactory.getLogger(GoogleTagGenerator::class.java)
    private val webClient = WebClient.builder()
        .baseUrl("https://language.googleapis.com/v2")
        .build()

    override fun getTags(content: String): List<String> {
        val categories = getContentCategories(content)
        val tags = breakContentCategories(categories)

        return tags
    }

    private fun getContentCategories(content: String): List<String> {
        val tags = mutableListOf<String>()

        if (content.isBlank()) {
            logger.warn("Content is empty or blank, skipping NLP processing")
            return tags
        }

        try {
            val requestBody = mapOf(
                "document" to mapOf(
                    "type" to "PLAIN_TEXT",
                    "content" to content
                )
            )

            val response = webClient.post()
                .uri("/documents:classifyText")
                .headers { headers ->
                    headers.setBearerAuth(getAccessToken())
                }
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .block()

            logger.debug("NLP service received categories: $response")

            val categories = response?.get("categories") as? List<Map<String, Any>>

            if (!categories.isNullOrEmpty()) {
                tags.addAll(categories.mapNotNull { it["name"] as? String })
            }
        } catch (e: Exception) {
            logger.error("Error calling Google NLP API", e)
        }

        return tags
    }

    private fun breakContentCategories(categories: List<String>): List<String> {
        // potential name: /Arts & Entertainment/Comics & Animation/Anime & Manga
        // to lower, split based on slashes, replace & with or, remove duplicates
        // edge case: /Arts & Entertainment/Comics & Animation/Other
        // ignore "other"
        val formattedTags = mutableListOf<String>()

        categories.forEach { category ->
            val tags = category.lowercase()
                .trim()
                .replace("&", "or")
                .split('/')
                .filter { it.isNotEmpty() && it[0].isLetter() && it != "other" }

            formattedTags.addAll(tags)
        }

        return formattedTags.distinct()
    }

    private fun getAccessToken(): String {
        val credentials = GoogleCredentials.fromStream(FileInputStream(googleCredentialPath))
            .createScoped(listOf("https://www.googleapis.com/auth/cloud-language"))
        credentials.refreshIfExpired()
        return credentials.accessToken.tokenValue
    }
}