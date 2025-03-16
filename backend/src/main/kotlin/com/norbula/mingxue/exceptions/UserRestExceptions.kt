package com.norbula.mingxue.exceptions

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import java.time.LocalDateTime

class UserAlreadyExistsException : RuntimeException()
class UserDoesNotExist : RuntimeException()

@RestControllerAdvice
class UserRestControllerExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException::class)
    fun handleUserAlreadyExistsException(e: UserAlreadyExistsException, request: WebRequest): ResponseEntity<Any> {
        val status = HttpStatus.CONFLICT
        val error = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = status.value(),
            error = "User already exists.",
            path = request.getDescription(false).substringAfter("uri=")
        )

        return ResponseEntity(error, status)
    }

    @ExceptionHandler(UserDoesNotExist::class)
    fun handleUserDoesNotExist(e: UserDoesNotExist, request: WebRequest): ResponseEntity<Any> {
        val status = HttpStatus.NOT_FOUND
        val error = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = status.value(),
            error = "User does not exist.",
            path = request.getDescription(false).substringAfter("uri=")
        )

        return ResponseEntity(error, status)
    }
}