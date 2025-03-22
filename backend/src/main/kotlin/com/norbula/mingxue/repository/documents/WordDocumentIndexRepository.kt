package com.norbula.mingxue.repository.documents

import com.norbula.mingxue.models.documents.indexes.WordDocumentIndex
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface WordDocumentIndexRepository: CrudRepository<WordDocumentIndex, Int> {
}