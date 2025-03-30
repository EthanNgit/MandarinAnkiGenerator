package com.norbula.mingxue.service

import com.norbula.mingxue.models.*
import com.norbula.mingxue.models.ai.grammar.GeneratedSentence
import com.norbula.mingxue.models.enums.ContextFrequency
import com.norbula.mingxue.models.enums.PartOfSpeech
import com.norbula.mingxue.models.ai.grammar.GeneratedWord
import com.norbula.mingxue.repository.*
import com.norbula.mingxue.service.ai.grammar.GrammarGenerator
import com.norbula.mingxue.service.ai.voice.NorbulaVoiceGenerator
import com.norbula.mingxue.service.documents.WordTaggingService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import kotlin.system.measureTimeMillis

@Service
class GenService (
    @Autowired private val wordRepository: WordRepository,
    @Autowired private val wordContextRepository: WordContextRepository,
    @Autowired private val wordTranslationRepository: WordTranslationRepository,
    @Autowired private val grammarPointRepository: GrammarPointRepository,
    @Autowired private val grammarPointSentenceRepository: GrammarPointSentenceRepository,
    @Autowired private val sentenceRepository: SentenceRepository,
    @Autowired private val wordTaggingService: WordTaggingService,
    @Autowired private val ttsService: NorbulaVoiceGenerator,
    @Autowired private val generators: Map<String, GrammarGenerator>
) {
    private val logger = LoggerFactory.getLogger(GenService::class.java)

    fun CreateWords(amount: Int, topic: String): List<WordContext> {
        val loggerContext = "Topic: $topic, Amount: $amount"
        logger.info("Starting word creation process. $loggerContext")

        var totalGenerationApiTime = 0L
        val resultContexts = mutableListOf<WordContext>()

        val totalExecutionTime = measureTimeMillis {
            // 1. use a generation
            val generator = getGenerator("grammar_openAi")

            // 2. generate the words
            val (generatedWords, generationTime) = measure {
                generateWordsFromApi(generator, amount, topic)
            }
            totalGenerationApiTime += generationTime
            if (generatedWords.isEmpty()) {
                // no words were generated for some reason...
                return@measureTimeMillis
            }

            // 3. Fetch Existing Data
            val (existingWordsMap, existingContextsByWord) = fetchExistingData(generatedWords)
            logger.debug("Fetched existing data: ${existingWordsMap.size} words, ${existingContextsByWord.values.flatten().size} contexts. $loggerContext")

            // 4. Prepare Words and Context Comparisons
            val prepResult = prepareWordsAndComparisons(generatedWords, existingWordsMap, existingContextsByWord)
            val wordsToCreate = prepResult.wordsToCreate
            val contextComparisons = prepResult.contextComparisons
            val generatedWordToExistingContextsMap = prepResult.generatedWordToExistingContextsMap
            logger.debug("Prepared ${wordsToCreate.size} new words and ${contextComparisons.size} context comparisons. $loggerContext")

            // 5. Save New Words (if any)
            val savedNewWords = saveNewWords(wordsToCreate)
            val allWordsMap = existingWordsMap + savedNewWords.associateBy { it.simplifiedWord } // Combine existing and new
            logger.debug("Saved ${savedNewWords.size} new words. $loggerContext")

            // 6. Check Context Similarity via API
            val (similarityResults, similarityCheckTime) = measure {
                checkContextSimilarityWithApi(generator, contextComparisons)
            }
            totalGenerationApiTime += similarityCheckTime
            logger.debug("Checked context similarity via API in $similarityCheckTime ms. Results count: ${similarityResults.size}. $loggerContext")

            // 7. Process Contexts, Sentences, Translations (Build lists for batch saving)
            val processedEntitiesResult = processEntities( // <-- Use updated function
                generatedWords,
                allWordsMap,
                generatedWordToExistingContextsMap,
                similarityResults
            )
            logger.debug(
                "Processed entities: ${processedEntitiesResult.contextsToSaveOrUpdate.size} contexts, " +
                        "${processedEntitiesResult.sentencesToSave.size} sentences." // Log updated info
            )

            // Add processed contexts to the final result list
            resultContexts.addAll(processedEntitiesResult.resultContexts)

            // 8. Save Sentences and Contexts (Contexts become persistent)
            saveProcessedEntities(processedEntitiesResult) // <-- Saves sentences & contexts
            logger.debug("Saved processed sentences and contexts to the database. $loggerContext")

            // 9. Process and Save Translations (Now safe to check existsByContext)
            processAndSaveTranslations(processedEntitiesResult.contextsForTranslationCheck) // <-- New step
            logger.debug("Processed and saved translations. $loggerContext")

            // TODO: Add tagging logic here if needed

        } // End of totalExecutionTime measurement

        val databaseAndTime = totalExecutionTime - totalGenerationApiTime
        logger.info(
            "Word creation process completed in $totalExecutionTime ms. $loggerContext " +
                    "(API time: $totalGenerationApiTime ms, DB/Processing time: $databaseAndTime ms). " +
                    "Returning ${resultContexts.size} contexts."
        )

        return resultContexts
        }

    private fun getGenerator(name: String): GrammarGenerator {
        return generators[name] ?: run {
            logger.error("Generator '$name' not found!")
            throw IllegalArgumentException("Generator '$name' not configured.")
        }
    }

    private fun generateWordsFromApi(generator: GrammarGenerator, amount: Int, topic: String): List<GeneratedWord> {
        // In a real scenario, add error handling for the API call
        return generator.generateWords(amount, topic)
    }

    private data class ExistingData(
        val wordsMap: Map<String, Word>,
        val contextsByWord: Map<Word, List<WordContext>>
    )

    private fun fetchExistingData(generatedWords: List<GeneratedWord>): ExistingData {
        val simplifiedWords = generatedWords.map { it.simplifiedWord }.distinct()
        if (simplifiedWords.isEmpty()) {
            return ExistingData(emptyMap(), emptyMap())
        }
        val existingWords = wordRepository.findBySimplifiedWordIn(simplifiedWords)
        val existingWordsMap = existingWords.associateBy { it.simplifiedWord }
        val existingContextsByWord = if (existingWords.isNotEmpty()) {
            wordContextRepository.findByWordIn(existingWords).groupBy { it.word }
        } else {
            emptyMap()
        }
        return ExistingData(existingWordsMap, existingContextsByWord)
    }

    private data class PreparationResult(
        val wordsToCreate: List<Word>,
        val contextComparisons: List<Triple<String, String, String>>,
        val generatedWordToExistingContextsMap: Map<GeneratedWord, List<WordContext>>
    )

    private fun prepareWordsAndComparisons(
        generatedWords: List<GeneratedWord>,
        existingWordsMap: Map<String, Word>,
        existingContextsByWord: Map<Word, List<WordContext>>
    ): PreparationResult {
        val wordsToCreate = mutableListOf<Word>()
        val contextComparisons = mutableListOf<Triple<String, String, String>>()
        val generatedWordToExistingContextsMap = mutableMapOf<GeneratedWord, List<WordContext>>()

        generatedWords.forEach { generatedWord ->
            val existingWord = existingWordsMap[generatedWord.simplifiedWord]
            val word = existingWord ?: Word(
                simplifiedWord = generatedWord.simplifiedWord,
                traditionalWord = generatedWord.traditionalWord
                // Ensure ID is null/default for creation
            ).also { if (existingWord == null) wordsToCreate.add(it) }

            val existingContexts = if (existingWord != null) existingContextsByWord[existingWord] ?: emptyList() else emptyList()
            generatedWordToExistingContextsMap[generatedWord] = existingContexts

            existingContexts.forEach { context ->
                contextComparisons.add(
                    Triple(
                        word.simplifiedWord,
                        context.usageSentence.simplifiedSentence, // Assuming nested structure
                        generatedWord.simplifiedSentence
                    )
                )
            }
        }
        return PreparationResult(wordsToCreate, contextComparisons, generatedWordToExistingContextsMap)
    }

    private fun saveNewWords(wordsToCreate: List<Word>): List<Word> {
        if (wordsToCreate.isEmpty()) {
            logger.debug("No new words to save.")
            return emptyList()
        }

        val savedIterable: Iterable<Word> = wordRepository.saveAll(wordsToCreate)
        val savedWords: List<Word> = savedIterable.toList()

        logger.debug("Saved ${savedWords.size} new words: ${savedWords.map { it.simplifiedWord }}")

        return savedWords
    }

    private fun checkContextSimilarityWithApi(
        generator: GrammarGenerator,
        contextComparisons: List<Triple<String, String, String>>
    ): List<Boolean> {
        return if (contextComparisons.isNotEmpty()) {
            // Add error handling for API call
            generator.areWordsSameBasedOnContextBatch(contextComparisons)
        } else {
            emptyList()
        }
    }

    private data class ProcessedEntitiesResult(
        val contextsToSaveOrUpdate: List<WordContext>,
        val sentencesToSave: List<Sentence>,
        val contextsForTranslationCheck: List<Pair<WordContext, GeneratedWord>>, // Store context and its original generation data
        val resultContexts: List<WordContext>
    )

    private fun processEntities(
        generatedWords: List<GeneratedWord>,
        allWordsMap: Map<String, Word>, // Map containing both existing and newly saved words
        generatedWordToExistingContextsMap: Map<GeneratedWord, List<WordContext>>,
        similarityResults: List<Boolean>
    ): ProcessedEntitiesResult { // <-- Updated return type
        val contextsToSaveOrUpdate = mutableListOf<WordContext>()
        val sentencesToSave = mutableListOf<Sentence>()
        // REMOVED: val translationsToSave = mutableListOf<WordTranslation>()
        val contextsForTranslationCheck = mutableListOf<Pair<WordContext, GeneratedWord>>() // <-- New list
        val finalResultContexts = mutableListOf<WordContext>()

        var similarityCheckIndex = 0

        generatedWords.forEach { generatedWord ->
            val word = allWordsMap[generatedWord.simplifiedWord]
                ?: throw IllegalStateException("Word '${generatedWord.simplifiedWord}' not found in map after save.")

            val existingContexts = generatedWordToExistingContextsMap[generatedWord] ?: emptyList()
            var matchedContext: WordContext? = null // Will hold the final context object for this generated word

            // Check against existing contexts for this word
            // (Logic for finding/creating matchedContext remains the same as before)
            for (context in existingContexts) {
                if (similarityCheckIndex >= similarityResults.size) {
                    logger.warn("Similarity results index out of bounds. Expected result for: ${context.usageSentence.simplifiedSentence}")
                    break // Avoid IndexOutOfBoundsException
                }
                val isSimilar = similarityResults[similarityCheckIndex]
                similarityCheckIndex++ // Consume the result

                if (isSimilar) {
                    matchedContext = context.copy(generationCount = context.generationCount + 1)
                    contextsToSaveOrUpdate.add(matchedContext)
                    finalResultContexts.add(matchedContext)
                    logger.trace("Context matched for word '${word.simplifiedWord}'. Existing context ID: ${context.id}, Sentence: ${context.usageSentence.simplifiedSentence}")
                    break
                }
            }

            // If no existing context matched, create a new one
            if (matchedContext == null) {
                val newSentence = Sentence(
                    simplifiedSentence = generatedWord.simplifiedSentence,
                    traditionalSentence = generatedWord.traditionalSentence,
                    pinyin = generatedWord.sentencePinyin,
                    translation = generatedWord.sentenceTranslation
                ).also { sentencesToSave.add(it) }

                matchedContext = WordContext(
                    word = word,
                    pinyin = generatedWord.pinyin,
                    partOfSpeech = PartOfSpeech.valueOf(generatedWord.partOfSpeech.lowercase()),
                    usageSentence = newSentence,
                    generationCount = 1,
                    frequency = ContextFrequency.valueOf(generatedWord.usageFrequency.lowercase())
                ).also {
                    contextsToSaveOrUpdate.add(it)
                    finalResultContexts.add(it)
                }
                logger.trace("Creating new context for word '${word.simplifiedWord}'. Sentence: ${newSentence.simplifiedSentence}")
            }

            // --- Translation logic removed from here ---
            // Instead, store the context and generated word for later processing
            contextsForTranslationCheck.add(matchedContext to generatedWord) // <-- Add to new list

        } // End loop through generatedWords

        return ProcessedEntitiesResult( // <-- Use updated data class
            contextsToSaveOrUpdate,
            sentencesToSave,
            contextsForTranslationCheck, // <-- Pass the new list
            finalResultContexts
        )
    }

    private fun saveProcessedEntities(entities: ProcessedEntitiesResult) { // <-- Updated parameter type
        var savedSentences: List<Sentence> = emptyList()
        if (entities.sentencesToSave.isNotEmpty()) {
            savedSentences = sentenceRepository.saveAll(entities.sentencesToSave).toList() // Ensure List
            logger.debug("Saved ${savedSentences.size} sentences.")
        }

        var savedContexts: List<WordContext> = emptyList()
        if (entities.contextsToSaveOrUpdate.isNotEmpty()) {
            savedContexts = wordContextRepository.saveAll(entities.contextsToSaveOrUpdate).toList() // Ensure List
            logger.debug("Saved/Updated ${savedContexts.size} contexts.")
            // Optional: You might need to update the context objects in entities.contextsForTranslationCheck
            // with these saved instances if the object references don't automatically update.
            // This depends on how JPA manages object identity after saveAll. A safer approach
            // might be to map savedContexts by some key and retrieve the persistent instance later.
            // However, let's try without it first, as JPA often updates IDs in place.
        }
        // --- Translation saving removed ---
    }

    private fun processAndSaveTranslations(contextsAndGenerations: List<Pair<WordContext, GeneratedWord>>) {
        if (contextsAndGenerations.isEmpty()) {
            logger.debug("No contexts provided for translation processing.")
            return
        }

        val translationsToSave = mutableListOf<WordTranslation>()

        contextsAndGenerations.forEach { (context, generatedWord) ->
            // Now 'context' should be persistent (or at least have its ID set by JPA after saveAll)
            // If you still get errors, it might mean context object references weren't updated post-save,
            // requiring fetching them again or using a map based on IDs from the saved results.
            try {
                if (!wordTranslationRepository.existsByContext(context)) {
                    WordTranslation(
                        context = context, // Use the (now hopefully persistent) context
                        translation = generatedWord.translation
                    ).also {
                        translationsToSave.add(it)
                        logger.trace("Queueing new translation for context of sentence: ${context.usageSentence.simplifiedSentence}")
                    }
                }
            } catch (e: Exception) {
                // Log potential errors during existsByContext if context is still transient or ID is null
                logger.error("Error checking translation existence for context (ID might be null?): ${context.id}, Sentence: ${context.usageSentence.simplifiedSentence}. Error: ${e.message}")
                // Decide if you want to skip or handle differently
            }
        }

        if (translationsToSave.isNotEmpty()) {
            val savedTranslations = wordTranslationRepository.saveAll(translationsToSave).toList() // Ensure List
            logger.debug("Saved ${savedTranslations.size} new translations.")
        } else {
            logger.debug("No new translations needed.")
        }
    }

    // Helper to measure time and return result + time
    private inline fun <T> measure(block: () -> T): Pair<T, Long> {
        var result: T? = null
        val time = measureTimeMillis {
            result = block()
        }
        // Cast needed because Kotlin can't guarantee initialization otherwise inside lambda
        return (result as T) to time
    }

//    fun CreateWords(amount: Int, topic: String): List<WordContext> {
//        // pick generation method
//        val generator = generators["grammar_openAi"] ?: throw Error()
//
//        // for batch db requests
//        val newWords = mutableListOf<Word>()
//        val newContexts = mutableListOf<WordContext>()
//        val newSentences = mutableListOf<Sentence>()
//        val newTranslations = mutableListOf<WordTranslation>()
//        val resultContexts = mutableListOf<WordContext>()
//
//        // measure entire function time for performance
//        var generationTimeElapsed = 0L
//        val wholeTimeElapsed = measureTimeMillis {
//            var generatedWords: List<GeneratedWord>
//
//            // measure generation time bottle cap
//            generationTimeElapsed = measureTimeMillis {
//                generatedWords = generator.generateWords(amount, topic)
//            }
//            // generate words in form of `GeneratedWord`
//            logger.debug("Generated $amount words in $generationTimeElapsed ms with results: ${generatedWords.map { it.simplifiedSentence }}")
//
//            // create map of words that already existed out of the batch (map to its word)
//            val simplifiedWords = generatedWords.map { it.simplifiedWord }
//            val existingWords = wordRepository.findBySimplifiedWordIn(simplifiedWords).associateBy { it.simplifiedWord }
//
//            // foreach word that exist in that map find their context (map to all of its contexts)
//            val existingContextsByWord = wordContextRepository.findByWordIn(existingWords.values).groupBy { it.word }
//
//            // prepare batch comparison requests (word, words context sentence, generated context sentence)
//            val contextComparisons = mutableListOf<Triple<String, String, String>>()
//            val generatedWordToContexts = mutableMapOf<GeneratedWord, List<WordContext>>()
//
//            // for all generated create the word if it does not exist
//            // while right after, if it has contexts associated to that word
//            // add them to be evaluated for being the same context that is generated
//            generatedWords.forEach { generatedWord ->
//                val word = existingWords[generatedWord.simplifiedWord] ?: Word(
//                    simplifiedWord = generatedWord.simplifiedWord,
//                    traditionalWord = generatedWord.traditionalWord
//                ).also { newWords.add(it) }
//
//                val existingContexts = existingContextsByWord[word] ?: emptyList()
//                generatedWordToContexts[generatedWord] = existingContexts
//
//                existingContexts.forEach { context ->
//                    contextComparisons.add(Triple(word.simplifiedWord, context.usageSentence.simplifiedSentence, generatedWord.simplifiedSentence))
//                }
//            }
//
//            // since words are done being altered, save them
//            wordRepository.saveAll(newWords).also {
//                logger.debug("Words saved to repository: ${newWords.map { it.simplifiedWord }}")
//            }
//
//            // based on the collected contexts for the words, compare if they are the same
//            // it will return in same order a boolean list, so if the first word's contexts
//            // do not match the first entry will be false, and vice versa
//            var gptResults: List<Boolean> = emptyList()
//            val genTimeElapsed2 = measureTimeMillis {
//                if (contextComparisons.isNotEmpty()) {
//                    gptResults = generator.areWordsSameBasedOnContextBatch(contextComparisons)
//                }
//            }
//            logger.debug("Checked contexts for similarity in $genTimeElapsed2 ms, with results: $gptResults")
//            generationTimeElapsed += genTimeElapsed2
//
//            // again for every generated word we can now prepare the contexts
//            // start with the word belonging to it, then the get all contexts for the word
//            // out of those if we already have the context add to the generation count (and to-save),
//            // otherwise, create the context and add it to the to-save list
//            // after that we save the contexts and reset the list for the next word
//            // finally we find if there is a translation that exists for a context
//            // if there is not then add it (putting it in the to-save list),
//            // after that save them and reset the list for the next word
//            val garbageTimeElapsed = measureTimeMillis {
//                var index = 0
//                generatedWords.forEach { generatedWord ->
//                    val word = existingWords[generatedWord.simplifiedWord] ?: newWords.find { it.simplifiedWord == generatedWord.simplifiedWord }!!
//
//                    val existingContexts = generatedWordToContexts[generatedWord] ?: emptyList()
//
//                    var matchedContext: WordContext? = null
//                    for (context in existingContexts) {
//                        logger.debug("Checking context: ${context.usageSentence}, GPT result: ${gptResults.getOrNull(index)}")
//
//                        val valid = gptResults.getOrNull(index)
//                        if (valid != null && valid != false) {
//                            matchedContext = context.copy(generationCount = context.generationCount + 1)
//                            newContexts.add(matchedContext)
//                            resultContexts.add(matchedContext)
//                            logger.debug("Context matched: ${context.usageSentence}, Updated generationCount: ${matchedContext.generationCount}")
//                            break // we already found the context exists no need to check the rest...
//                        }
//                        index++
//                    }
//
//                    // only create a new context if no match was found
//                    // but always update generation count at least
//                    if (matchedContext == null) {
//                        val newSentence = Sentence(
//                            simplifiedSentence = generatedWord.simplifiedSentence,
//                            traditionalSentence = generatedWord.traditionalSentence,
//                            pinyin = generatedWord.sentencePinyin,
//                            translation = generatedWord.sentenceTranslation
//                        ).also { newSentences.add(it) }
//
//                        matchedContext = WordContext(
//                            word = word,
//                            pinyin = generatedWord.pinyin,
//                            partOfSpeech = PartOfSpeech.valueOf(generatedWord.partOfSpeech.lowercase()),
//                            usageSentence = newSentence,
//                            generationCount = 1,
//                            frequency = ContextFrequency.valueOf(generatedWord.usageFrequency.lowercase())
//                        ).also { newContexts.add(it); resultContexts.add(it) }
//                        logger.debug("Created new context for word: ${word.simplifiedWord}, context: ${matchedContext.usageSentence}")
//                    }
//
//                    if (newSentences.isNotEmpty()) {
//                        sentenceRepository.saveAll(newSentences).also {
//                            logger.debug("Sentences saved to repository: ${newSentences.map { it.simplifiedSentence }}")
//                            newSentences.clear() // clear the list after saving to prevent re-saving
//                        }
//                    }
//
//                    // save contexts in batch after all words have been processed
//                    if (newContexts.isNotEmpty()) {
//                        wordContextRepository.saveAll(newContexts).also {
//                            logger.debug("Contexts saved to repository: ${newContexts.map { it.usageSentence }}")
//                            newContexts.clear() // clear the list after saving to prevent re-saving
//                        }
//                    }
//
//                    // check if translation exists for the context and add it to the new translations list,
//                    if (!wordTranslationRepository.existsByContext(matchedContext)) {
//                        newTranslations.add(
//                            WordTranslation(
//                                context = matchedContext,
//                                translation = generatedWord.translation
//                            ).also {
//                                logger.debug("Translation added for context: ${matchedContext.usageSentence}, translation: ${generatedWord.translation}")
//                            }
//                        )
//                    }
//
//                    // save translations in batch after all contexts are processed
//                    if (newTranslations.isNotEmpty()) {
//                        wordTranslationRepository.saveAll(newTranslations).also {
//                            logger.debug("Translations saved to repository: ${newTranslations.map { it.translation }}")
//                            newTranslations.clear() // clear the list after saving to prevent re-saving
//                        }
//                    }
//                }
//            }
//            logger.debug("Took garbage section of algorithm $garbageTimeElapsed ms to complete")
//
//            // TODO: add tagging
//        }
//        logger.debug("Took entire function $wholeTimeElapsed ms to complete")
//        logger.debug("Took entire function minus bottle cap ${wholeTimeElapsed - generationTimeElapsed} ms to complete")
//
//        return resultContexts
//    }

    fun CreateSentencesForWord(word: String): List<Sentence> {
        val generator = generators["grammar_openAi"] ?: throw Error()

        val newSentences = mutableListOf<GeneratedSentence>()

        TODO()
    }

    fun CreateSentencesForGrammarConcept(conceptId: Int): List<GrammarPointSentence> {
        val concept = grammarPointRepository.findById(conceptId).orElseThrow { Error() } // TODO: add error for invalid concept

        val generator = generators["grammar_openAi"] ?: throw Error()

        val generatedSentences = generator.generateSentencesGrammarConcept(5, concept)

        val newSentences = mutableListOf<Sentence>()
        val newGrammarPointSentences = mutableListOf<GrammarPointSentence>()

        generatedSentences.forEach { generatedSentence ->
            val sentence = Sentence(
                simplifiedSentence = generatedSentence.simplifiedSentence,
                traditionalSentence = generatedSentence.traditionalSentence,
                pinyin = generatedSentence.pinyin,
                translation = generatedSentence.translation,
            )

            newSentences.add(sentence)
        }

        val sentences = sentenceRepository.saveAll(newSentences)

        sentences.forEach { sentence ->
            val grammarPointSentence = GrammarPointSentence(
                grammarPoint = concept,
                sentence = sentence
            )

            newGrammarPointSentences.add(grammarPointSentence)
        }

        val grammarPointSentences = grammarPointSentenceRepository.saveAll(newGrammarPointSentences)

        return grammarPointSentences.toList()
    }
}
