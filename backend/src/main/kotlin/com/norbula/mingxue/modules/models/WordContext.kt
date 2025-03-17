package com.norbula.mingxue.modules.models

import com.norbula.mingxue.modules.models.enums.ContextFrequency
import com.norbula.mingxue.modules.models.enums.PartOfSpeech
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

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val partOfSpeech: PartOfSpeech,

    @Column(nullable = false)
    val usageSentence: String,

    @Column(nullable = false)
    val generationCount: Int,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val frequency: ContextFrequency = ContextFrequency.Infrequent,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime? = null
)
