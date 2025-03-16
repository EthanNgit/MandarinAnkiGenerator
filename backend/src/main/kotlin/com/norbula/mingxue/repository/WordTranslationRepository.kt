package com.norbula.mingxue.repository

import com.norbula.mingxue.modules.models.WordContext
import com.norbula.mingxue.modules.models.WordTranslation
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface WordTranslationRepository: CrudRepository<WordTranslation, Int> {
}