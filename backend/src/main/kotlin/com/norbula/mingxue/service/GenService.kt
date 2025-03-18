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
import kotlin.system.measureTimeMillis

@Service
class GenService (
    @Autowired private val wordRepository: WordRepository,
    @Autowired private val wordContextRepository: WordContextRepository,
    @Autowired private val wordTranslationRepository: WordTranslationRepository,
    @Autowired private val sentenceRepository: SentenceRepository,
    @Autowired private val generators: Map<String, GrammarGenerator>
) {
    private val logger = LoggerFactory.getLogger(GenService::class.java)

    fun CreateWords(amount: Int, topic: String): List<WordContext> {
        // pick generation method
        val generator = generators["openAi"] ?: throw Error()

        // for batch db requests
        val newWords = mutableListOf<Word>()
        val newContexts = mutableListOf<WordContext>()
        val newTranslations = mutableListOf<WordTranslation>()
        val resultContexts = mutableListOf<WordContext>()

        // measure entire function time for performance
        var generationTimeElapsed = 0L
        val wholeTimeElapsed = measureTimeMillis {
            var generatedWords: List<GeneratedWord>

            // measure generation time bottle cap
            generationTimeElapsed = measureTimeMillis {
                generatedWords = generator.generateWords(amount, topic)
            }
            // generate words in form of `GeneratedWord`
            logger.debug("Generated $amount words in $generationTimeElapsed ms with results: ${generatedWords.map { it.simpleSentence }}")

            // create map of words that already existed out of the batch (map to its word)
            val simplifiedWords = generatedWords.map { it.simplifiedWord }
            val existingWords = wordRepository.findBySimplifiedWordIn(simplifiedWords).associateBy { it.simplifiedWord }

            // foreach word that exist in that map find their context (map to all of its contexts)
            val existingContextsByWord = wordContextRepository.findByWordIn(existingWords.values).groupBy { it.word }

            // prepare batch comparison requests (word, words context sentence, generated context sentence)
            val contextComparisons = mutableListOf<Triple<String, String, String>>()
            val generatedWordToContexts = mutableMapOf<GeneratedWord, List<WordContext>>()

            // for all generated create the word if it does not exist
            // while right after, if it has contexts associated to that word
            // add them to be evaluated for being the same context that is generated
            generatedWords.forEach { generatedWord ->
                val word = existingWords[generatedWord.simplifiedWord] ?: Word(
                    simplifiedWord = generatedWord.simplifiedWord,
                    traditionalWord = generatedWord.traditionalWord
                ).also { newWords.add(it) }

                val existingContexts = existingContextsByWord[word] ?: emptyList()
                generatedWordToContexts[generatedWord] = existingContexts

                existingContexts.forEach { context ->
                    contextComparisons.add(Triple(word.simplifiedWord, context.usageSentence, generatedWord.simpleSentence))
                }
            }

            // since words are done being altered, save them
            wordRepository.saveAll(newWords).also {
                logger.debug("Words saved to repository: ${newWords.map { it.simplifiedWord }}")
            }

            // based on the collected contexts for the words, compare if they are the same
            // it will return in same order a boolean list, so if the first word's contexts
            // do not match the first entry will be false, and vice versa
            var gptResults: List<Boolean>
            val genTimeElapsed2 = measureTimeMillis {
                gptResults = generator.areWordsSameBasedOnContextBatch(contextComparisons)
            }
            logger.debug("Checked contexts for similarity in $genTimeElapsed2 ms, with results: $gptResults")
            generationTimeElapsed += genTimeElapsed2

            // again for every generated word we can now prepare the contexts
            // start with the word belonging to it, then the get all contexts for the word
            // out of those if we already have the context add to the generation count (and to-save),
            // otherwise, create the context and add it to the to-save list
            // after that we save the contexts and reset the list for the next word
            // finally we find if there is a translation that exists for a context
            // if there is not then add it (putting it in the to-save list),
            // after that save them and reset the list for the next word
            val garbageTimeElapsed = measureTimeMillis {
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
                            resultContexts.add(matchedContext)
                            logger.debug("Context matched: ${context.usageSentence}, Updated generationCount: ${matchedContext.generationCount}")
                            break // we already found the context exists no need to check the rest...
                        }
                        index++
                    }

                    // only create a new context if no match was found
                    // but always update generation count at least
                    if (matchedContext == null) {
                        matchedContext = WordContext(
                            word = word,
                            pinyin = generatedWord.pinyin,
                            partOfSpeech = PartOfSpeech.valueOf(generatedWord.partOfSpeech),
                            usageSentence = generatedWord.simpleSentence,
                            generationCount = 1,
                            frequency = ContextFrequency.valueOf(generatedWord.usageFrequency)
                        ).also { newContexts.add(it); resultContexts.add(it) }
                        logger.debug("Created new context for word: ${word.simplifiedWord}, context: ${matchedContext.usageSentence}")
                    }

                    // save contexts in batch after all words have been processed
                    if (newContexts.isNotEmpty()) {
                        wordContextRepository.saveAll(newContexts).also {
                            logger.debug("Contexts saved to repository: ${newContexts.map { it.usageSentence }}")
                            newContexts.clear() // clear the list after saving to prevent re-saving
                        }
                    }

                    // check if translation exists for the context and add it to the new translations list,
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

                    // save translations in batch after all contexts are processed
                    if (newTranslations.isNotEmpty()) {
                        wordTranslationRepository.saveAll(newTranslations).also {
                            logger.debug("Translations saved to repository: ${newTranslations.map { it.translation }}")
                            newTranslations.clear() // clear the list after saving to prevent re-saving
                        }
                    }
                }
            }
            logger.debug("Took garbage section of algorithm $garbageTimeElapsed ms to complete")


            // todo: add tagging
        }
        logger.debug("Took entire function $wholeTimeElapsed ms to complete")
        logger.debug("Took entire function minus bottle cap ${wholeTimeElapsed - generationTimeElapsed} ms to complete")

        return resultContexts
    }
}