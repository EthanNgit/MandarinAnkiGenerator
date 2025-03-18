package com.norbula.mingxue.service

import com.norbula.mingxue.exceptions.UserDoesNotExist
import com.norbula.mingxue.modules.models.UserDeck
import com.norbula.mingxue.modules.models.UserDeckWord
import com.norbula.mingxue.repository.UserDeckRepository
import com.norbula.mingxue.repository.UserDeckWordRepository
import com.norbula.mingxue.repository.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.security.InvalidParameterException

@Service
class DeckService(
    @Autowired private val deckRepository: UserDeckRepository,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val userDeckWordRepository: UserDeckWordRepository,
    @Autowired private val genService: GenService
) {
    fun CreateDeck(authToken: String, deckName: String, deckTopic: String, publicDeck: Boolean = true): UserDeck {
        // validate parameters before query
        if (deckName.length > 100) {
            throw InvalidParameterException() // todo: add exception
        }
        if (deckTopic.length > 50) {
            throw InvalidParameterException() // todo: add exception
        }

        // get user with authToken
        val user = userRepository.findByAuthToken(authToken).orElseThrow { UserDoesNotExist() }

        // get name and desc/topic and if public
        val deckNameUsed = deckRepository.existsByUserAndName(user, deckName)
        if (deckNameUsed) {
            throw InvalidParameterException() // todo: add exception
        }

        // check if min amount of words already exist
        // todo: add prechecking for words to deck service

        // otherwise generate words
        val words = genService.CreateWords(5, deckTopic)

        val toCreateDeck = UserDeck(
            user = user,
            name = deckName,
            description = deckTopic,
            isPublic = publicDeck
        )

        val deck = deckRepository.save(toCreateDeck)
        val toSaveDeckWords: MutableList<UserDeckWord> = mutableListOf()
        words.forEach { word ->
            toSaveDeckWords.add(UserDeckWord(
                deck = deck,
                wordContext = word
            ))
        }

        val deckWords = userDeckWordRepository.saveAll(toSaveDeckWords)

        return deck
    }
}