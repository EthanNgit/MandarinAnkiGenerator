package com.norbula.mingxue.repository

import com.norbula.mingxue.models.Sentence
import com.norbula.mingxue.models.WordContext
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface SentenceRepository: CrudRepository<Sentence, Int> {
}