package com.norbula.mingxue.repository

import com.norbula.mingxue.modules.models.Word
import com.norbula.mingxue.modules.models.WordContext
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface WordContextRepository: CrudRepository<WordContext, Int> {
    fun findByWord(word: Word): List<WordContext>
    fun findByWordIn(word: Collection<Word>): List<WordContext>
}