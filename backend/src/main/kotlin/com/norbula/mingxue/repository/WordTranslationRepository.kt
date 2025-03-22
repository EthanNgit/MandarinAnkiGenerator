package com.norbula.mingxue.repository

import com.norbula.mingxue.models.WordContext
import com.norbula.mingxue.models.WordTranslation
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface WordTranslationRepository: CrudRepository<WordTranslation, Int> {
    fun findByContext(context: WordContext): Optional<WordTranslation>
    fun existsByContext(context: WordContext): Boolean
}