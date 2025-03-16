package com.norbula.mingxue.service

import org.springframework.beans.factory.annotation.Autowired

class DeckService(
    @Autowired private val genService: GenService
) {

}