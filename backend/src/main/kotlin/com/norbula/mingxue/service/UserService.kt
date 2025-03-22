package com.norbula.mingxue.service

import com.google.firebase.auth.FirebaseAuth
import com.norbula.mingxue.exceptions.UserAlreadyExistsException
import com.norbula.mingxue.exceptions.UserDoesNotExist
import com.norbula.mingxue.models.User
import com.norbula.mingxue.models.UserDTO
import com.norbula.mingxue.models.enums.Role
import com.norbula.mingxue.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class UserService(
    @Autowired private val userRepository: UserRepository,
) {
    private val logger = LoggerFactory.getLogger(UserService::class.java)

    fun createUser(authToken: String, user: UserDTO): UserDTO {
        val storedUser = userRepository.findByAuthToken(authToken).orElse(null)

        if (storedUser != null) {
            throw UserAlreadyExistsException()
        }

        val newUser = User(
            email = user.email,
            authToken = authToken,
        )

        val savedUser = userRepository.save(newUser)
        setCustomClaims(savedUser.authToken, Role.APP)

        return savedUser.toDTO()
    }

    fun getUser(id: Int): UserDTO {
        return userRepository.findById(id).orElseThrow { UserDoesNotExist() }.toDTO()
    }

    fun getUser(token: String): UserDTO {
        return userRepository.findByAuthToken(token).orElseThrow { UserDoesNotExist() }.toDTO()
    }

    fun deleteUser(id: Int) {
        val user = userRepository.findById(id).orElseThrow { UserDoesNotExist() }

        delete(user)
    }

    fun deleteUser(token: String) {
        val user = userRepository.findByAuthToken(token).orElseThrow { UserDoesNotExist() }

        delete(user)
    }

    fun searchUser(token: String? = null, email: String? = null): List<UserDTO> {
        return when {
            token != null && email != null -> userRepository.findByAuthToken(token).map { listOf(it.toDTO()) }.orElse(emptyList())
            token != null -> userRepository.findByAuthToken(token).map { listOf(it.toDTO()) }.orElse(emptyList())
            email != null -> userRepository.findByEmail(email).map { listOf(it.toDTO()) }.orElse(emptyList())
            else -> emptyList()
        }
    }

    fun setUserRole(authToken: String) {
        setCustomClaims(authToken, Role.APP)
    }

    private fun setCustomClaims(authToken: String, role: Role) {
        val claims = mapOf("role" to role.name)
        FirebaseAuth.getInstance().setCustomUserClaims(authToken, claims)
    }


    private fun delete(user: User) {
        userRepository.deleteByAuthToken(user.authToken)

        FirebaseAuth.getInstance().deleteUser(user.authToken)
    }

}