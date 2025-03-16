package com.norbula.mingxue.modules.models.llm

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.reactive.function.client.WebClient

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

class OpenAIGenerator(
    @Value("\${openai.api.key}") private val openAiApiKey: String
): GrammarGenerator {
    private val webClient = WebClient.builder()
        .baseUrl("https://api.openai.com/v1/chat/completions")
        .defaultHeader("Authorization", "Bearer $openAiApiKey")
        .defaultHeader("Content-Type", "application/json")
        .build()

    override fun generateWords(amount: Int, topic: String): List<GeneratedWord> {
        TODO("Not yet implemented")
    }

    override fun generateSentences(amount: Int, topic: String) {
        TODO("Not yet implemented")
    }
}