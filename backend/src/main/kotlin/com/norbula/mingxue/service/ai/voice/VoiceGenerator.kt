package com.norbula.mingxue.service.ai.voice

import com.norbula.mingxue.models.ai.grammar.GeneratedWord
import com.norbula.mingxue.models.ai.speech.SpeechWord

data class TTSRequest(
    val engine: String = "azure",
    val gender: String = "female",
    val words: List<SpeechWord>
)

interface VoiceGenerator {
    fun generateTTSFiles(words: List<SpeechWord>)
}