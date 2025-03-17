package com.norbula.mingxue.service

import com.norbula.mingxue.modules.models.Word
import com.norbula.mingxue.modules.models.WordContext
import com.norbula.mingxue.modules.models.WordTranslation
import com.norbula.mingxue.modules.models.enums.ContextFrequency
import com.norbula.mingxue.modules.models.enums.PartOfSpeech
import com.norbula.mingxue.modules.models.llm.GeneratedWord
import com.norbula.mingxue.modules.models.llm.GrammarGenerator
import com.norbula.mingxue.repository.SentenceRepository
import com.norbula.mingxue.repository.WordContextRepository
import com.norbula.mingxue.repository.WordRepository
import com.norbula.mingxue.repository.WordTranslationRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class GenService (
    @Autowired private val wordRepository: WordRepository,
    @Autowired private val wordContextRepository: WordContextRepository,
    @Autowired private val wordTranslationRepository: WordTranslationRepository,
    @Autowired private val sentenceRepository: SentenceRepository,
    @Autowired private val generators: Map<String, GrammarGenerator>
) {
    private val logger = LoggerFactory.getLogger(GenService::class.java)

    fun CreateWords(amount: Int, topic: String): List<Word> {
        val generator = generators["openAi"] ?: throw Error()

        val generatedWords = generator.generateWords(amount, topic)
        logger.debug("Generated words in service: ${generatedWords.map { it.simpleSentence }}")

        val simplifiedWords = generatedWords.map { it.simplifiedWord }
        val existingWords = wordRepository.findBySimplifiedWordIn(simplifiedWords).associateBy { it.simplifiedWord }

        val newWords = mutableListOf<Word>()
        val newContexts = mutableListOf<WordContext>()
        val newTranslations = mutableListOf<WordTranslation>()

        // Fetch all existing contexts at once
        val existingContextsByWord = wordContextRepository.findByWordIn(existingWords.values).groupBy { it.word }

        // Prepare batch GPT comparison requests
        val contextComparisons = mutableListOf<Triple<String, String, String>>()
        val generatedWordToContexts = mutableMapOf<GeneratedWord, List<WordContext>>()

        generatedWords.forEach { generatedWord ->
            val word = existingWords[generatedWord.simplifiedWord] ?: Word(
                simplifiedWord = generatedWord.simplifiedWord,
                traditionalWord = generatedWord.traditionalWord
            ).also { newWords.add(it) }

            val existingContexts = existingContextsByWord[word] ?: emptyList()
            generatedWordToContexts[generatedWord] = existingContexts

            existingContexts.forEach { context ->
                contextComparisons.add(Triple(word.simplifiedWord, generatedWord.simpleSentence, context.usageSentence))
            }
        }

        // Save all new words in batch first
        wordRepository.saveAll(newWords).also {
            logger.debug("Words saved to repository: ${newWords.map { it.simplifiedWord }}")
        }

        // Call GPT once for all comparisons
        val gptResults = generator.areWordsSameBasedOnContextBatch(contextComparisons)
        logger.debug("Checked if words relate to context on batch $gptResults")

        var index = 0
        generatedWords.forEach { generatedWord ->
            val word = existingWords[generatedWord.simplifiedWord] ?: newWords.find { it.simplifiedWord == generatedWord.simplifiedWord }!!

            val existingContexts = generatedWordToContexts[generatedWord] ?: emptyList()

            var matchedContext: WordContext? = null
            for (context in existingContexts) {
                logger.debug("Checking context: ${context.usageSentence}, GPT result: ${gptResults[index]}")
                if (gptResults[index]) {
                    matchedContext = context.copy(generationCount = context.generationCount + 1)
                    newContexts.add(matchedContext)
                    logger.debug("Context matched: ${context.usageSentence}, Updated generationCount: ${matchedContext!!.generationCount}")
                    break
                }
                index++
            }

            // Only create a new context if no match was found
            if (matchedContext == null) {
                matchedContext = WordContext(
                    word = word,
                    pinyin = generatedWord.pinyin,
                    partOfSpeech = PartOfSpeech.valueOf(generatedWord.partOfSpeech),
                    usageSentence = generatedWord.simpleSentence,
                    generationCount = 1,
                    frequency = ContextFrequency.valueOf(generatedWord.usageFrequency)
                ).also { newContexts.add(it) }
                logger.debug("Created new context for word: ${word.simplifiedWord}, context: ${matchedContext.usageSentence}")
            }

            // Save contexts in batch after all words have been processed
            if (newContexts.isNotEmpty()) {
                wordContextRepository.saveAll(newContexts).also {
                    logger.debug("Contexts saved to repository: ${newContexts.map { it.usageSentence }}")
                    newContexts.clear() // Clear the list after saving to prevent re-saving
                }
            }

            // Check if translation exists, and save new translations in batch if necessary
            if (!wordTranslationRepository.existsByContext(matchedContext)) {
                newTranslations.add(
                    WordTranslation(
                        context = matchedContext,
                        translation = generatedWord.translation
                    ).also {
                        logger.debug("Translation added for context: ${matchedContext.usageSentence}, translation: ${generatedWord.translation}")
                    }
                )
            }

            // Save translations in batch after all contexts are processed
            if (newTranslations.isNotEmpty()) {
                wordTranslationRepository.saveAll(newTranslations).also {
                    logger.debug("Translations saved to repository: ${newTranslations.map { it.translation }}")
                    newTranslations.clear() // Clear the list after saving to prevent re-saving
                }
            }
        }

        // todo: add tagging

        return newWords
    }
}