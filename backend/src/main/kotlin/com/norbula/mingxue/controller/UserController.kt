package com.norbula.mingxue.controller

import com.norbula.mingxue.modules.models.UserDTO
import com.norbula.mingxue.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import org.springframework.security.oauth2.jwt.Jwt


@RestController
@RequestMapping("/api/v1/users")
class UserController(
    @Autowired private val userService: UserService,
) {
    private val logger = LoggerFactory.getLogger(UserController::class.java)

    @PostMapping("/me")
            /**
             * Post request to create a user, requires a user to be sent as body and
             * for uid in JWT to be the same as in the user body sent
             *
             * MIN ROLE: NONE
             **/
    fun createUserMe(@RequestBody user: UserDTO): ResponseEntity<UserDTO> {
        val token = getAuthTokenFromJwt(SecurityContextHolder.getContext())

        userService.setUserRole(token)
        val createdUser = userService.createUser(token, user)

        return ResponseEntity(createdUser, HttpStatus.CREATED)
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('ROLE_APP')")
            /**
             * Get request to get the user data of the sender (me)
             *
             * MIN ROLE: APP
             **/
    fun getUserMe(): ResponseEntity<UserDTO> {
        val token = getAuthTokenFromJwt(SecurityContextHolder.getContext())
        val user = userService.getUser(token)

        return ResponseEntity(user, HttpStatus.OK)
    }

    @DeleteMapping("/me")
    @PreAuthorize("hasRole('ROLE_APP')")
            /**
             * Delete request to delete user data of the sender
             *
             * MIN ROLE: APP
             **/
    fun deleteUserMe(): ResponseEntity<UserDTO> {
        val token = getAuthTokenFromJwt(SecurityContextHolder.getContext())
        userService.deleteUser(token)

        return ResponseEntity(HttpStatus.NO_CONTENT)
    }

    private fun getAuthTokenFromJwt(securityContext: SecurityContext): String {
        val authentication: Authentication = securityContext.authentication
        val jwt: Jwt = authentication.principal as Jwt
        val token: String = jwt.subject

        return token
    }
}