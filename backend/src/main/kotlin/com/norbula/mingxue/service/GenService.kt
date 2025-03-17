package com.norbula.mingxue.service

import com.norbula.mingxue.modules.models.llm.GeneratedWord
import com.norbula.mingxue.modules.models.llm.GrammarGenerator
import com.norbula.mingxue.repository.SentenceRepository
import com.norbula.mingxue.repository.WordContextRepository
import com.norbula.mingxue.repository.WordRepository
import com.norbula.mingxue.repository.WordTranslationRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class GenService (
    @Autowired private val wordRepository: WordRepository,
    @Autowired private val wordContextRepository: WordContextRepository,
    @Autowired private val wordTranslationRepository: WordTranslationRepository,
    @Autowired private val sentenceRepository: SentenceRepository,
    @Autowired private val generators: Map<String, GrammarGenerator>
){
    private val logger = LoggerFactory.getLogger(GenService::class.java)

    fun CreateWords(amount: Int, topic: String): Mono<List<GeneratedWord>>? {
        val res = generators["openAi"]?.generateWords(amount, topic)
        logger.debug("Output from generation = {}", res);

        return res
    }
}