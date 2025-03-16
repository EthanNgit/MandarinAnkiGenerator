package com.norbula.mingxue.modules.models

import jakarta.persistence.*

@Entity
@Table(name = "grammar_points")
data class GrammarPoint(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @ManyToOne
    @JoinColumn(name = "word_context_id", nullable = false)
    val context: WordContext,

    @Column(nullable = false)
    val description: String
)
