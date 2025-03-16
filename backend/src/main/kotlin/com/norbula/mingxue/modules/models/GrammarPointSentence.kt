package com.norbula.mingxue.modules.models

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "grammar_point_sentences")
data class GrammarPointSentence(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @ManyToOne
    @JoinColumn(name = "grammar_point_id", nullable = false)
    val grammarPoint: GrammarPoint,

    @ManyToOne
    @JoinColumn(name = "sentence_id", nullable = false)
    val sentence: Sentence,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime? = null
)
