package com.norbula.decks

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DecksApplication

fun main(args: Array<String>) {
	runApplication<DecksApplication>(*args)
}
