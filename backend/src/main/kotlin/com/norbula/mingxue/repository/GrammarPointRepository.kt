package com.norbula.mingxue.repository

import com.norbula.mingxue.models.GrammarPoint
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface GrammarPointRepository: CrudRepository<GrammarPoint, Int> {
}