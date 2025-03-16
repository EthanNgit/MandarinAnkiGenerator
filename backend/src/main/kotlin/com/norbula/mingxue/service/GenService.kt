package com.norbula.mingxue.service

import com.norbula.mingxue.repository.SentenceRepository
import com.norbula.mingxue.repository.WordContextRepository
import com.norbula.mingxue.repository.WordRepository
import com.norbula.mingxue.repository.WordTranslationRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class GenService (
    @Autowired private val wordRepository: WordRepository,
    @Autowired private val wordContextRepository: WordContextRepository,
    @Autowired private val wordTranslationRepository: WordTranslationRepository,
    @Autowired private val sentenceRepository: SentenceRepository
){

}