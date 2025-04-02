package com.norbula.mingxue.models.enums

enum class PartOfSpeech {
    noun, pronoun, verb, adjective, adverb, preposition, conjunction, determiner, classifier, particle, interjection;

    companion object {
        fun tryValueOf(value: String): PartOfSpeech? {
            return kotlin.runCatching { PartOfSpeech.valueOf(value.lowercase().trim()) }.getOrNull()
        }
    }
}