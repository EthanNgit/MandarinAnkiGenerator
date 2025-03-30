package com.norbula.mingxue.service.ai.grammar

import com.norbula.mingxue.models.GrammarPoint
import com.norbula.mingxue.models.ai.grammar.GeneratedSentence
import com.norbula.mingxue.models.ai.grammar.GeneratedWord

interface GrammarGenerator {
    fun generateWords(amount: Int, topic: String): List<GeneratedWord>
    fun generateSentencesGrammarConcept(amount: Int, concept: GrammarPoint): List<GeneratedSentence>
    fun generateSentencesUsingWord(amount: Int, word: String): List<GeneratedSentence>
    fun isSameWordBasedOnContext(word: String, str1: String, str2: String): Boolean
    fun areWordsSameBasedOnContextBatch(comparisons: List<Triple<String, String, String>>): List<Boolean>
}