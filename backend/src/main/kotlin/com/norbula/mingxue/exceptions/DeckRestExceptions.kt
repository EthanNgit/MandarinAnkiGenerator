package com.norbula.mingxue.exceptions

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import java.time.LocalDateTime

class DeckNameAlreadyExistsException : RuntimeException()
class DeckDoesNotExistException : RuntimeException()
class InvalidDeckTopicException : RuntimeException()
class InvalidDeckNameException : RuntimeException()

@RestControllerAdvice
class DeckRestControllerExceptionHandler {

    @ExceptionHandler(DeckNameAlreadyExistsException::class)
    fun handleDeckNameAlreadyExistsException(e: DeckNameAlreadyExistsException, request: WebRequest): ResponseEntity<Any> {
        val status = HttpStatus.CONFLICT
        val error = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = status.value(),
            error = "Deck name already exists.",
            path = request.getDescription(false).substringAfter("uri=")
        )

        return ResponseEntity(error, status)
    }

    @ExceptionHandler(DeckDoesNotExistException::class)
    fun handleDeckDoesNotExist(e: DeckDoesNotExistException, request: WebRequest): ResponseEntity<Any> {
        val status = HttpStatus.NOT_FOUND
        val error = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = status.value(),
            error = "Deck does not exist.",
            path = request.getDescription(false).substringAfter("uri=")
        )

        return ResponseEntity(error, status)
    }

    @ExceptionHandler(InvalidDeckTopicException::class)
    fun handleInvalidDeckTopic(e: InvalidDeckTopicException, request: WebRequest): ResponseEntity<Any> {
        val status = HttpStatus.BAD_REQUEST
        val error = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = status.value(),
            error = "Invalid deck topic.",
            path = request.getDescription(false).substringAfter("uri=")
        )

        return ResponseEntity(error, status)
    }

    @ExceptionHandler(InvalidDeckNameException::class)
    fun handleInvalidDeckName(e: InvalidDeckNameException, request: WebRequest): ResponseEntity<Any> {
        val status = HttpStatus.BAD_REQUEST
        val error = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = status.value(),
            error = "Invalid deck name.",
            path = request.getDescription(false).substringAfter("uri=")
        )

        return ResponseEntity(error, status)
    }
}