package com.norbula.mingxue.repository

import com.norbula.mingxue.models.Word
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface WordRepository: CrudRepository<Word, Int> {
    fun findBySimplifiedWord(simplifiedWord: String): Optional<Word>
    fun findBySimplifiedWordIn(simplifiedWords: List<String>): List<Word>
}