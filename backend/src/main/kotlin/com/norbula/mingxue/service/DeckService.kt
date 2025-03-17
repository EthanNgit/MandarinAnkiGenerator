package com.norbula.mingxue.service

import com.norbula.mingxue.exceptions.UserDoesNotExist
import com.norbula.mingxue.repository.UserDeckRepository
import com.norbula.mingxue.repository.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import java.security.InvalidParameterException

class DeckService(
    @Autowired private val deckRepository: UserDeckRepository,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val genService: GenService
) {
    fun CreateDeck(authToken: String, deckName: String, deckTopic: String, publicDeck: Boolean = true) {
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
        val deckNameValid = deckRepository.existsByUserAndName(user, deckName)

        // check if min amount of words already exist

        // otherwise generate words

    }
}