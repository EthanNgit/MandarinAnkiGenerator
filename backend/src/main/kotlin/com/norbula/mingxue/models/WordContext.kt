package com.norbula.mingxue.models

import com.norbula.mingxue.models.enums.ContextFrequency
import com.norbula.mingxue.models.enums.PartOfSpeech
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

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

    @Column(name = "part_of_speech", nullable = false)
    @Enumerated(EnumType.STRING)
    val partOfSpeech: PartOfSpeech,

    @ManyToOne
    @JoinColumn(name = "sentence_id", nullable = false)
    val usageSentence: Sentence,

    @Column(name = "generation_count", nullable = false)
    val generationCount: Int,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val frequency: ContextFrequency = ContextFrequency.infrequent,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime? = null
)
