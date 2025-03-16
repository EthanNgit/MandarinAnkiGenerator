package com.norbula.mingxue.repository

import com.norbula.mingxue.modules.models.User
import com.norbula.mingxue.modules.models.UserDeck
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface UserDeckRepository: CrudRepository<UserDeck, Int> {
    fun findByUser(user: User): List<UserDeck>
}