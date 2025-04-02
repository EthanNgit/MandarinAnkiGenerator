package com.norbula.mingxue.service

import com.norbula.mingxue.models.*
import com.norbula.mingxue.models.ai.grammar.GeneratedSentence
import com.norbula.mingxue.models.enums.ContextFrequency
import com.norbula.mingxue.models.enums.PartOfSpeech
import com.norbula.mingxue.models.ai.grammar.GeneratedWord
import com.norbula.mingxue.models.ai.speech.SpeechWord
import com.norbula.mingxue.repository.*
import com.norbula.mingxue.service.ai.grammar.GrammarGenerator
import com.norbula.mingxue.service.ai.nlp.context.ContextChecker
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
    @Autowired private val generators: Map<String, GrammarGenerator>,
    @Autowired private val contextCheckers: Map<String, ContextChecker>
) {
    private val logger = LoggerFactory.getLogger(GenService::class.java)

    fun CreateWords(amount: Int, topic: String): List<WordContext> {
        val loggerContext = "Topic: $topic, Amount: $amount"
        logger.info("Starting word creation process. $loggerContext")

        var totalGenerationApiTime = 0L
        val resultContexts = mutableListOf<WordContext>()

        val totalExecutionTime = measureTimeMillis {
            // 1. use a generation
            val generator = getGenerator("generator_gemini")
            val contextChecker = getContextChecker("context_gemini")

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
            val allWordsMap = existingWordsMap + savedNewWords.associateBy { it.simplifiedWord }
            logger.debug("Saved ${savedNewWords.size} new words. $loggerContext")

            // 6. Check Context Similarity via API
            val (similarityResults, similarityCheckTime) = measure {
                checkContextSimilarityWithApi(contextChecker, contextComparisons)
            }
            totalGenerationApiTime += similarityCheckTime
            logger.debug("Checked context similarity via API in $similarityCheckTime ms. Results count: ${similarityResults.size}. $loggerContext")

            // 7. Process Contexts, Sentences, Translations (Build lists for batch saving)
            val processedEntitiesResult = processEntities(
                generatedWords,
                allWordsMap,
                generatedWordToExistingContextsMap,
                similarityResults
            )
            logger.debug(
                "Processed entities: ${processedEntitiesResult.contextsToSaveOrUpdate.size} contexts, " +
                        "${processedEntitiesResult.sentencesToSave.size} sentences."
            )

            // Add processed contexts to the final result list
            resultContexts.addAll(processedEntitiesResult.resultContexts)

            // 8. Save Sentences and Contexts (Contexts become persistent)
            saveProcessedEntities(processedEntitiesResult)
            logger.debug("Saved processed sentences and contexts to the database. $loggerContext")

            // 9. Process and Save Translations (Now safe to check existsByContext)
            processAndSaveTranslations(processedEntitiesResult.contextsForTranslationCheck)
            logger.debug("Processed and saved translations. $loggerContext")

            // 10. Generate TTS files for the contexts words
            generateTtsForContexts(resultContexts, false)

            // 11. Generate TTS files for the contexts sentences
            generateTtsForContexts(resultContexts, true)

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

    private fun getContextChecker(name: String): ContextChecker {
        return contextCheckers[name] ?: run {
            logger.error("Context checker '$name' not found!")
            throw IllegalArgumentException("Context checker '$name' not configured.")
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
        contextChecker: ContextChecker,
        contextComparisons: List<Triple<String, String, String>>
    ): List<Boolean> {
        return if (contextComparisons.isNotEmpty()) {
            contextChecker.areWordsSameBasedOnContextBatch(contextComparisons)
        } else {
            emptyList()
        }
    }

    private data class ProcessedEntitiesResult(
        val contextsToSaveOrUpdate: List<WordContext>,
        val sentencesToSave: List<Sentence>,
        val contextsForTranslationCheck: List<Pair<WordContext, GeneratedWord>>,
        val resultContexts: List<WordContext>
    )

    private fun processEntities(
        generatedWords: List<GeneratedWord>,
        allWordsMap: Map<String, Word>,
        generatedWordToExistingContextsMap: Map<GeneratedWord, List<WordContext>>,
        similarityResults: List<Boolean>
    ): ProcessedEntitiesResult {
        val contextsToSaveOrUpdate = mutableListOf<WordContext>()
        val sentencesToSave = mutableListOf<Sentence>()
        val contextsForTranslationCheck = mutableListOf<Pair<WordContext, GeneratedWord>>()
        val finalResultContexts = mutableListOf<WordContext>()

        var similarityCheckIndex = 0

        generatedWords.forEach { generatedWord ->
            val word = allWordsMap[generatedWord.simplifiedWord]
                ?: throw IllegalStateException("Word '${generatedWord.simplifiedWord}' not found in map after save.")

            val existingContexts = generatedWordToExistingContextsMap[generatedWord] ?: emptyList()
            var matchedContext: WordContext? = null

            for (context in existingContexts) {
                if (similarityCheckIndex >= similarityResults.size) {
                    logger.warn("Similarity results index out of bounds. Expected result for: ${context.usageSentence.simplifiedSentence}")
                    break
                }
                val isSimilar = similarityResults[similarityCheckIndex]
                similarityCheckIndex++

                if (isSimilar) {
                    matchedContext = context.copy(generationCount = context.generationCount + 1)
                    contextsToSaveOrUpdate.add(matchedContext)
                    finalResultContexts.add(matchedContext)
                    logger.trace("Context matched for word '${word.simplifiedWord}'. Existing context ID: ${context.id}, Sentence: ${context.usageSentence.simplifiedSentence}")
                    break
                }
            }

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
                    partOfSpeech = PartOfSpeech.tryValueOf(generatedWord.partOfSpeech),
                    usageSentence = newSentence,
                    generationCount = 1,
                    frequency = ContextFrequency.tryValueOf(generatedWord.usageFrequency.lowercase()) ?: ContextFrequency.infrequent
                ).also {
                    contextsToSaveOrUpdate.add(it)
                    finalResultContexts.add(it)
                }
                logger.trace("Creating new context for word '${word.simplifiedWord}'. Sentence: ${newSentence.simplifiedSentence}")
            }

            contextsForTranslationCheck.add(matchedContext to generatedWord)

        }

        return ProcessedEntitiesResult(
            contextsToSaveOrUpdate,
            sentencesToSave,
            contextsForTranslationCheck,
            finalResultContexts
        )
    }

    private fun saveProcessedEntities(entities: ProcessedEntitiesResult) {
        var savedSentences: List<Sentence> = emptyList()
        if (entities.sentencesToSave.isNotEmpty()) {
            savedSentences = sentenceRepository.saveAll(entities.sentencesToSave).toList()
            logger.debug("Saved ${savedSentences.size} sentences.")
        }

        var savedContexts: List<WordContext> = emptyList()
        if (entities.contextsToSaveOrUpdate.isNotEmpty()) {
            // Persist the contexts so they have non-null IDs.
            savedContexts = wordContextRepository.saveAll(entities.contextsToSaveOrUpdate).toList()
            logger.debug("Saved/Updated ${savedContexts.size} contexts.")
        }
    }

    private fun processAndSaveTranslations(contextsAndGenerations: List<Pair<WordContext, GeneratedWord>>) {
        if (contextsAndGenerations.isEmpty()) {
            logger.debug("No contexts provided for translation processing.")
        }

        val translationsToSave = mutableListOf<WordTranslation>()

        contextsAndGenerations.forEach { (context, generatedWord) ->
            try {
                if (!wordTranslationRepository.existsByContext(context)) {
                    WordTranslation(
                        context = context,
                        translation = generatedWord.translation
                    ).also {
                        translationsToSave.add(it)
                        logger.trace("Queueing new translation for context of sentence: ${context.usageSentence.simplifiedSentence}")
                    }
                }
            } catch (e: Exception) {
                logger.error("Error checking translation existence for context (ID might be null?): ${context.id}, Sentence: ${context.usageSentence.simplifiedSentence}. Error: ${e.message}")
            }
        }

        if (translationsToSave.isNotEmpty()) {
            val savedTranslations = wordTranslationRepository.saveAll(translationsToSave).toList()
            logger.debug("Saved ${savedTranslations.size} new translations.")
        } else {
            logger.debug("No new translations needed.")
        }
    }

    private fun generateTtsForContexts(contexts: List<WordContext>, sentences: Boolean) {
        val validContexts = contexts.filter { it.id != null }
        if (validContexts.size < contexts.size) {
            logger.warn("${contexts.size - validContexts.size} contexts had no ID and were skipped for TTS generation.")
        }

        val speechWords = validContexts.map { context ->
            SpeechWord(
                contextId = context.id!!,
                text = if (!sentences) context.word.simplifiedWord else context.usageSentence.simplifiedSentence,
                pronunciation = if (!sentences) context.pinyin else ""
            )
        }
        if (speechWords.isNotEmpty()) {
            try {
                if (!sentences) {
                    ttsService.generateTTSWordFiles(speechWords)
                } else {
                    ttsService.generateTTSSentenceFiles(speechWords)
                }
                logger.debug("TTS files generated for ${speechWords.size} contexts.")
            } catch (e: Exception) {
                logger.error("Failed to generate TTS files: ${e.message}", e)
            }
        } else {
            logger.debug("No contexts with valid IDs to generate TTS for.")
        }
    }

    private inline fun <T> measure(block: () -> T): Pair<T, Long> {
        var result: T? = null
        val time = measureTimeMillis {
            result = block()
        }
        return (result as T) to time
    }

    fun CreateSentencesForWord(word: String): List<Sentence> {
        val generator = generators["grammar_openAi"] ?: throw Error()

        val newSentences = mutableListOf<GeneratedSentence>()

        TODO()
    }

    fun CreateSentencesForGrammarConcept(conceptId: Int): List<GrammarPointSentence> {
        val concept = grammarPointRepository.findById(conceptId).orElseThrow { Error() } // TODO: add error for invalid concept

        val generator = generators["generator_openAi"] ?: throw Error()

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
