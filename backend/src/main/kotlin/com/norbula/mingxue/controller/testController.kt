package com.norbula.mingxue.controller

import com.norbula.mingxue.models.UserDeck
import com.norbula.mingxue.models.WordContext
import com.norbula.mingxue.models.WordDTO
import com.norbula.mingxue.models.enums.PinyinType
import com.norbula.mingxue.service.AnkiService
import com.norbula.mingxue.service.DeckService
import com.norbula.mingxue.service.GenService
import com.norbula.mingxue.service.documents.WordTaggingService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.io.File
import java.nio.file.Files

@RestController
@RequestMapping("/api/v1/test")
class testController(
    @Autowired private val genService: GenService,
    @Autowired private val deckService: DeckService,
    @Autowired private val wordTaggingService: WordTaggingService,
    @Autowired private val ankiService: AnkiService
) {
    private val logger = LoggerFactory.getLogger(testController::class.java)

    @PostMapping("/gen")
    fun createWords(
        @RequestParam amount: Int = 5,
        @RequestParam topic: String = "verbs"
    ): ResponseEntity<List<WordContext>> {
        logger.debug("createWords: method called")
        val generatedWords = genService.CreateWords(amount, topic)

        logger.debug(
            "createWords: Generated {} words about {} with values {}",
            amount,
            topic,
            generatedWords.map { it.word.simplifiedWord })
        return ResponseEntity(generatedWords, HttpStatus.CREATED)
    }

    @GetMapping("/find")
    fun findWords(
        @RequestParam query: String = "",
        @RequestParam pos: String = ""
    ): ResponseEntity<List<WordDTO>> {
        logger.debug("findWords: method called")
        val foundWords = wordTaggingService.searchWords(query, pos)

        return ResponseEntity(foundWords, HttpStatus.OK)
    }

    data class CreateDeckRequest(
        val deckName: String = "Deck 1",
        val deckTopic: String = "verbs",
        val deckSize: Int = 5,
    )

    @PostMapping("/deck")
    fun createDeck(
        @RequestBody body: CreateDeckRequest
    ): ResponseEntity<UserDeck> {
        val token = getAuthTokenFromJwt(SecurityContextHolder.getContext())

        logger.debug("createDeck: method called deck")
        val deck = deckService.createDeck(token, body.deckName, body.deckTopic, body.deckSize)

        logger.debug("createDeck: Generated deck about ${body.deckTopic}")
        return ResponseEntity(deck, HttpStatus.CREATED)
    }

    data class ExportDeckRequest(
        val deckName: String = "Deck 1",
        val pinyinType: PinyinType = PinyinType.marked,
    )

    @PostMapping("/export")
    fun exportDeck(@RequestBody deck: ExportDeckRequest): ResponseEntity<Resource> {
        val token = getAuthTokenFromJwt(SecurityContextHolder.getContext())

        val tempDir = Files.createTempDirectory("anki_export").toFile()

        // Create the Anki deck with audio
        val apkgFile = ankiService.createAnkiDeckWithAudio(
            userToken = token,
            outputDir = tempDir,
            deckName = deck.deckName,
            pinyinType = deck.pinyinType
        )

        // Return the .apkg file as a downloadable resource
        val resource = FileSystemResource(apkgFile)
        val headers = HttpHeaders().apply {
            add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${apkgFile.name}\"")
        }
        return ResponseEntity.ok()
            .headers(headers)
            .contentLength(apkgFile.length())
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(resource)
    }

    private fun getAuthTokenFromJwt(securityContext: SecurityContext): String {
        val authentication: Authentication = securityContext.authentication
        val jwt: Jwt = authentication.principal as Jwt
        val token: String = jwt.subject

        return token
    }
}