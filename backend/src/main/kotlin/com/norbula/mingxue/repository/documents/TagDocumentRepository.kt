package com.norbula.mingxue.repository.documents

import com.norbula.mingxue.models.documents.TagDocument
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface TagDocumentRepository: ElasticsearchRepository<TagDocument, String> {
    fun findByName(tagName: String): Optional<TagDocument>
}