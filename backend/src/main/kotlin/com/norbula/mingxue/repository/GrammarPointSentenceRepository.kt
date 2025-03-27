package com.norbula.mingxue.repository

import com.norbula.mingxue.models.GrammarPoint
import com.norbula.mingxue.models.GrammarPointSentence
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface GrammarPointSentenceRepository: CrudRepository<GrammarPointSentence, Int> {
}