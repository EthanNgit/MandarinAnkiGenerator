package com.norbula.mingxue.service.documents

import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.json.JsonData
import com.norbula.mingxue.models.WordDTO
import com.norbula.mingxue.models.ai.grammar.GeneratedWord
import com.norbula.mingxue.models.documents.TagDocument
import com.norbula.mingxue.models.documents.WordDocument
import com.norbula.mingxue.models.documents.indexes.TagDocumentIndex
import com.norbula.mingxue.models.documents.indexes.WordDocumentIndex
import com.norbula.mingxue.repository.documents.TagDocumentIndexRepository
import com.norbula.mingxue.repository.documents.WordDocumentIndexRepository
import com.norbula.mingxue.service.ai.nlp.TagGenerator
import com.norbula.mingxue.service.ai.vector.EmbeddingGenerator
import com.norbula.mingxue.repository.documents.TagDocumentRepository
import com.norbula.mingxue.repository.documents.WordDocumentRepository
import org.elasticsearch.script.Script
import org.elasticsearch.search.SearchHit
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.ElasticsearchOperations

import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrNull

@Service
class WordTaggingService (
    @Autowired private val wordDocumentRepository: WordDocumentRepository,
    @Autowired private val tagDocumentRepository: TagDocumentRepository,
    @Autowired private val tagGenerators: Map<String, TagGenerator>,
    @Autowired private val embeddingGenerators: Map<String, EmbeddingGenerator>,
    @Autowired private val tagDocumentIndexRepository: TagDocumentIndexRepository,
    @Autowired private val wordDocumentIndexRepository: WordDocumentIndexRepository,
    @Autowired private val elasticsearchOperations: ElasticsearchOperations
) {
    private val logger = LoggerFactory.getLogger(WordTaggingService::class.java)

    fun searchWords(queryText: String, partOfSpeech: String? = null): List<WordDTO> {
        val embeddingGenerator = embeddingGenerators["embedding_openAi"] ?: throw Error()
        val queryEmbedding = embeddingGenerator.getEmbedding(queryText)
        logger.info("Generated embedding for query: $queryText")

        val tagGenerator = tagGenerators["tagging_google"] ?: throw Error()
        val tagNames: List<String> = tagGenerator.getTags(queryText)
        logger.info("Tag names for query: $tagNames")

        val tagIds = tagNames.mapNotNull { tagName ->
            tagDocumentRepository.findByName(tagName).getOrNull()?.id
        }
        logger.info("Tag IDs for query: $tagIds")

        val query = NativeQuery.builder()
            .withQuery { q ->
                q.bool { b ->
                    // Fuzzy matching for text fields
                    b.must { must ->
                        must.bool { ib ->
                            ib.should { s -> s.match { m -> m.field("simplifiedWord").query(queryText).fuzziness("AUTO") } }
                            .should { s -> s.match { m -> m.field("traditionalWord").query(queryText).fuzziness("AUTO") } }
                            .should { s -> s.match { m -> m.field("translation").query(queryText).fuzziness("AUTO") } }
                            .should { s -> s.match { m -> m.field("pinyin").query(queryText).fuzziness("AUTO") } }
                            .minimumShouldMatch("1")
                        }
                    }

                    if (!partOfSpeech.isNullOrBlank()) {
                        b.filter { f -> f.term { t -> t.field("partOfSpeech.keyword").caseInsensitive(true).value(partOfSpeech) } }
                    }

                    b.should { s ->
                        s.scriptScore { ss ->
                            ss.query { q -> q.matchAll { it } }
                            ss.script { sb ->
                                sb.source(
                                    """
                                    if (doc['embedding'].size() == 0) return 0; 
                                    return cosineSimilarity(params.queryVector, 'embedding') + 1.0;
                                    """.trimIndent()
                                )
                                    .lang("painless")
                                    .params(mapOf("queryVector" to JsonData.of(queryEmbedding)))  // Fix: Convert to JsonData
                            }
                            ss.boost(1.5f)
                        }
                    }

                    b.minimumShouldMatch("1")
                }
            }
            .build()

        println("Generated Query: ${query.query}")

        val searchHits = elasticsearchOperations.search(query, WordDocument::class.java)

        println("Total Hits: ${searchHits.totalHits}")
        println("Search Response: ${searchHits.searchHits}")

        return searchHits.searchHits.map { it.content.toDTO() }
    }

    fun processGeneratedWord(generatedWord: GeneratedWord) {
        val tagGenerator = tagGenerators["tagging_google"] ?: throw Error() // todo: add error for tagGenerator does not exist
        val embeddingGenerator = embeddingGenerators["embedding_openAi"] ?: throw Error() // todo: add error for embeddingGenerator does not exist

        // generate tags for a word + use pre generated tags
        val generatedTags = generatedWord.tags.map { it.lowercase() }
        val tags = tagGenerator.getTags(generatedWord.simplifiedWord) + generatedTags

        logger.debug("Planning to add the tags for ${generatedWord.simplifiedWord}, $generatedTags")

        val existingTagIds = mutableListOf<Int>()
        val newTagNames = mutableListOf<String>()

        // check if tags already exist
        tags.forEach{ tag ->
            tagDocumentRepository.findByName(tag).ifPresentOrElse(
                { existingTagIds.add(it.id!!) },
                { newTagNames.add(tag) }
            )
        }

        // add tag documents
        if (newTagNames.isNotEmpty()) {
            val newEmbeddings = embeddingGenerator.getBatchEmbedding(newTagNames)
            newTagNames.forEachIndexed { index, tag ->
                val tagIndex = tagDocumentIndexRepository.save(TagDocumentIndex())
                val tagDoc = TagDocument(id = tagIndex.id, name = tag, embedding = newEmbeddings[index])
                tagDocumentRepository.save(tagDoc)
                existingTagIds.add(tagIndex.id)
            }
        }

        logger.debug("Saved the tags for ${generatedWord.simplifiedWord}")


        // add word document
        val wordEmbedding = embeddingGenerator.getEmbedding(generatedWord.simplifiedWord)
        val newWordId = wordDocumentIndexRepository.save(WordDocumentIndex())
        val wordDoc = WordDocument(
            id = newWordId.id,
            simplifiedWord = generatedWord.simplifiedWord,
            traditionalWord = generatedWord.traditionalWord,
            pinyin = generatedWord.pinyin,
            partOfSpeech = generatedWord.partOfSpeech,
            translation = generatedWord.translation,
            tagIds = existingTagIds,
            embedding = wordEmbedding
        )

        val savedDoc = wordDocumentRepository.save(wordDoc)

        logger.debug("Tagged the doc for ${savedDoc.simplifiedWord}: $savedDoc")
    }
}