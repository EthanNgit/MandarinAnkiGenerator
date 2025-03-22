package com.norbula.mingxue.service.documents


import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct

@Service
class ElasticsearchIndexService(private val elasticsearchClient: ElasticsearchClient) {

    private val logger = LoggerFactory.getLogger(ElasticsearchIndexService::class.java)

    private val indexName = "words_index"

    @PostConstruct
    fun initializeIndex() {
        try {
            // Check if the index exists
            val indexExists = elasticsearchClient.indices().exists { it.index(indexName) }.value()
            if (!indexExists) {
                createIndex()
            } else {
                logger.info("Elasticsearch index '$indexName' already exists.")
            }
        } catch (e: Exception) {
            logger.error("Error checking or creating Elasticsearch index: ${e.message}", e)
        }
    }

    private fun createIndex() {
        val createIndexRequest = CreateIndexRequest.Builder()
            .index(indexName)
            .mappings { mapping ->
                mapping
                    .properties("id") { it.integer { integer -> integer } } // Ensure integer builder is returned
                    .properties("simplifiedWord") {
                        it.text { text ->
                            text.fields("keyword") { k -> k.keyword { keyword -> keyword } }
                        }
                    }
                    .properties("traditionalWord") {
                        it.text { text ->
                            text.fields("keyword") { k -> k.keyword { keyword -> keyword } }
                        }
                    }
                    .properties("pinyin") {
                        it.text { text -> text } // Return text builder
                    }
                    .properties("partOfSpeech") {
                        it.text { text ->
                            text.fields("keyword") { k -> k.keyword { keyword -> keyword } }
                        }
                    }
                    .properties("translation") {
                        it.text { text ->
                            text.fields("keyword") { k -> k.keyword { keyword -> keyword } }
                        }
                    }
                    .properties("tagIds") {
                        it.integer { integer -> integer } // Ensure integer builder is returned
                    }
                    .properties("embedding") {
                        it.denseVector { vector -> vector.dims(1536) } // Ensure vector builder is returned
                    }
            }
            .settings { settings ->
                settings.index { index ->
                    index.numberOfShards("1").numberOfReplicas("1")
                }
            }
            .build()

        val response = elasticsearchClient.indices().create(createIndexRequest)
        if (response.acknowledged()) {
            logger.info("Elasticsearch index '$indexName' created successfully.")
        } else {
            logger.error("Failed to create Elasticsearch index '$indexName'.")
        }
    }

}
