package com.norbula.mingxue.modules.models

import jakarta.persistence.*

@Entity
@Table(name = "word_contexts")
data class WordContext(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @ManyToOne
    @JoinColumn(name = "word_id", nullable = false)
    val word: Word,

    @Column(nullable = false)
    val pinyin: String,

    @Column(nullable = false)
    val partOfSpeech: String, // could split this into table

    @Column(nullable = false)
    val usage: String,

    @Column(nullable = false)
    val generationCount: String,

    @Column(nullable = false)
    val frequency: String, // could be an enum
)
