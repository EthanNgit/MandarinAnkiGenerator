package com.norbula.mingxue.controller

import com.norbula.mingxue.modules.models.Word
import com.norbula.mingxue.modules.models.llm.GeneratedWord
import com.norbula.mingxue.service.GenService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/v1/test")
class testController(
    @Autowired private val genService: GenService
) {
    private val logger = LoggerFactory.getLogger(testController::class.java)

    @PostMapping("/")
    fun createWords(
        @RequestParam amount: Int = 5,
        @RequestParam topic: String = "verbs"
    ): ResponseEntity<List<Word>> {
        logger.debug("method called")
        val generatedWords = genService.CreateWords(amount, topic)

        logger.debug("Generated $amount words about $topic with values ${generatedWords.map { it -> it.simplifiedWord }}")
        return ResponseEntity(generatedWords, HttpStatus.CREATED)
    }
}