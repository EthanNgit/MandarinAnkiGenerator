package com.norbula.mingxue.service.ai.voice

import com.norbula.mingxue.models.ai.grammar.GeneratedWord
import com.norbula.mingxue.models.ai.speech.SpeechWord

data class TTSRequest(
    val engine: String = "google",
    val gender: String = "any",
    val words: List<SpeechWord>
)

interface VoiceGenerator {
    fun generateTTSFiles(words: List<SpeechWord>): List<String>
}