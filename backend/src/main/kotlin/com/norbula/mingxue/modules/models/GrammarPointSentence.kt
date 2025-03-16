package com.norbula.mingxue.modules.models

import jakarta.persistence.*

@Entity
@Table(name = "word_translations")
data class GrammarPointSentence(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @ManyToOne
    @JoinColumn(name = "grammar_point_id", nullable = false)
    val grammarPoint: GrammarPoint,

    @Column(nullable = false)
    val sentence: Sentence
)
