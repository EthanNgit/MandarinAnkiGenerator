package com.norbula.mingxue

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.runApplication

@SpringBootApplication
class MingxueApplication

fun main(args: Array<String>) {
	runApplication<MingxueApplication>(*args)
}
