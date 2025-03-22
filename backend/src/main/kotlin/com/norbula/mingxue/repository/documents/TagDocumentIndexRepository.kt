package com.norbula.mingxue.repository.documents

import com.norbula.mingxue.models.documents.indexes.TagDocumentIndex
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface TagDocumentIndexRepository: CrudRepository<TagDocumentIndex, Int> {
}