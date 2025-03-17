package com.norbula.mingxue.repository

import com.norbula.mingxue.modules.models.User
import com.norbula.mingxue.modules.models.UserDeck
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserDeckRepository: CrudRepository<UserDeck, Int> {
    fun findByUser(user: User): List<UserDeck>
    fun findByUserAndName(user: User, name: String): Optional<UserDeck>
    fun existsByUserAndName(user: User, name: String): Boolean
}