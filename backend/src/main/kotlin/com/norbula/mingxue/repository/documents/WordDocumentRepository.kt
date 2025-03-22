package com.norbula.mingxue.repository.documents

import com.norbula.mingxue.models.documents.WordDocument
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.stereotype.Repository

@Repository
interface WordDocumentRepository: ElasticsearchRepository<WordDocument, String> {
}