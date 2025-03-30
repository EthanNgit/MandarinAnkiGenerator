package com.norbula.mingxue.service.pinyin

import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.reactive.function.client.WebClient

class NorbulaPinyinConverter : PinyinConverter {
    private val logger = LoggerFactory.getLogger(NorbulaPinyinConverter::class.java)

    private val webClient = WebClient.builder()
        .baseUrl("http://mingxue-pinying-converter:8082/api/v1")
        .defaultHeader("Content-Type", "application/json")
        .build()

    override fun toMarked(pinyin: List<String>): String {
        return webClient.post()
            .uri("/convert/mark")
            .bodyValue(pinyin)
            .retrieve()
            .bodyToMono(object : ParameterizedTypeReference<String>() {})
            .block()
            .orEmpty()
    }

    override fun toZhuyin(pinyin: List<String>): String {
        return webClient.post()
            .uri("/convert/zhuyin")
            .bodyValue(pinyin)
            .retrieve()
            .bodyToMono(object : ParameterizedTypeReference<String>() {})
            .block()
            .orEmpty()
    }
}