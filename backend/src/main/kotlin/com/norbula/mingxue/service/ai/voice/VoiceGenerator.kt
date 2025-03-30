package com.norbula.mingxue.service.ai.voice

interface VoiceGenerator {
    fun generateTTSFiles(words: List<String>): List<String>
}