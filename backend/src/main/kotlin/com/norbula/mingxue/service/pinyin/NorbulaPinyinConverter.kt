package com.norbula.mingxue.service.pinyin

import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service("pinyin_converter_norbula")
class NorbulaPinyinConverter : PinyinConverter {
    private val logger = LoggerFactory.getLogger(NorbulaPinyinConverter::class.java)

    private val webClient = WebClient.builder()
        .baseUrl("http://mingxue-pinyin-converter:8082/api/v1")
        .defaultHeader("Content-Type", "application/json")
        .build()

    private data class PinyinRequest(val pinyin: List<String>)

    override fun toMarked(pinyin: List<String>): List<String> {
        return webClient.post()
            .uri("/convert/mark")
            .header("Content-Type", "application/json")
            .bodyValue(PinyinRequest(pinyin))
            .retrieve()
            .bodyToMono(object : ParameterizedTypeReference<Map<String, List<String>>>() {})
            .block()
            ?.get("result")
            .orEmpty()
    }

    override fun toZhuyin(pinyin: List<String>): List<String>  {
        return webClient.post()
            .uri("/convert/zhuyin")
            .header("Content-Type", "application/json")
            .bodyValue(PinyinRequest(pinyin))
            .retrieve()
            .bodyToMono(object : ParameterizedTypeReference<Map<String, List<String>>>() {})
            .block()
            ?.get("result")
            .orEmpty()
    }
}