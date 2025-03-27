package com.norbula.mingxue.models

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "sentences")
data class Sentence(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @Column(name = "simplified_sentence", nullable = false)
    val simplifiedSentence: String,

    @Column(name = "traditional_sentence", nullable = false)
    val traditionalSentence: String,

    @Column(nullable = false)
    val pinyin: String,

    @Column(nullable = false)
    val translation: String,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime? = null
)
