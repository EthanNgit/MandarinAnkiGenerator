package com.norbula.mingxue.repository

import com.norbula.mingxue.models.Word
import com.norbula.mingxue.models.WordContext
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface WordContextRepository: CrudRepository<WordContext, Int> {
    fun findByWord(word: Word): List<WordContext>
    fun findByWordIn(word: Collection<Word>): List<WordContext>
}