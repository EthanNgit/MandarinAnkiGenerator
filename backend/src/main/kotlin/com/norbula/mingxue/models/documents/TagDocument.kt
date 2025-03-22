package com.norbula.mingxue.models.documents

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document

@Document(indexName = "tags_index")
data class TagDocument(
    @Id
    val id: Int? = null,
    val name: String,
    val embedding: List<Float>
)