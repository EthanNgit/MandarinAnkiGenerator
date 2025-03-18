package com.norbula.mingxue.controller

import com.norbula.mingxue.modules.models.UserDeck
import com.norbula.mingxue.modules.models.Word
import com.norbula.mingxue.modules.models.WordContext
import com.norbula.mingxue.modules.models.llm.GeneratedWord
import com.norbula.mingxue.service.DeckService
import com.norbula.mingxue.service.GenService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/v1/test")
class testController(
    @Autowired private val genService: GenService,
    @Autowired private val deckService: DeckService
) {
    private val logger = LoggerFactory.getLogger(testController::class.java)

    @PostMapping("/gen")
    fun createWords(
        @RequestParam amount: Int = 5,
        @RequestParam topic: String = "verbs"
    ): ResponseEntity<List<WordContext>> {
        logger.debug("createWords: method called")
        val generatedWords = genService.CreateWords(amount, topic)

        logger.debug("createWords: Generated $amount words about $topic with values ${generatedWords.map { it -> it.word.simplifiedWord }}")
        return ResponseEntity(generatedWords, HttpStatus.CREATED)
    }

    @PostMapping("/deck")
    fun createDeck(
        @RequestParam deckName: String = "Deck 1",
        @RequestParam deckTopic: String = "verbs"
    ): ResponseEntity<UserDeck> {
        val token = getAuthTokenFromJwt(SecurityContextHolder.getContext())

        logger.debug("createDeck: method called")
        val deck = deckService.CreateDeck(token, deckName, deckTopic)

        logger.debug("createDeck: Generated deck about $deckTopic")
        return ResponseEntity(deck, HttpStatus.CREATED)
    }


    private fun getAuthTokenFromJwt(securityContext: SecurityContext): String {
        val authentication: Authentication = securityContext.authentication
        val jwt: Jwt = authentication.principal as Jwt
        val token: String = jwt.subject

        return token
    }
}