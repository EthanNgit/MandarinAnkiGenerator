package com.norbula.mingxue.modules.models

import jakarta.persistence.*

@Entity
@Table(name = "word_translations")
data class WordTranslation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @ManyToOne
    @JoinColumn(name = "context_id", nullable = false)
    val context: WordContext,

    @Column(nullable = false)
    val translation: String,

    @Column(nullable = false)
    val language: String, // could split this into its own table
)
