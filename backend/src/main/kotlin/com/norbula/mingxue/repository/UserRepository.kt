package com.norbula.mingxue.repository

import com.norbula.mingxue.modules.models.User
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Repository
interface UserRepository: CrudRepository<User, Int> {
    fun findByAuthToken(token: String) : Optional<User>
    fun findByEmail(email: String) : Optional<User>
    @Transactional
    fun deleteByAuthToken(token: String): Optional<User>
    fun existsByAuthToken(token: String): Boolean
}