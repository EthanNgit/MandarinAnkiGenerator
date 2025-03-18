package com.norbula.mingxue.repository

import com.norbula.mingxue.modules.models.UserDeckWord
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface UserDeckWordRepository: CrudRepository<UserDeckWord, Int> {
}