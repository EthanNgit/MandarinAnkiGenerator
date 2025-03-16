package com.norbula.mingxue.modules.models

import jakarta.persistence.*

@Entity
@Table(name = "sentences")
data class Sentence(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @Column(nullable = false)
    val sentence: String,

    @Column(nullable = false)
    val pinyin: String,

    @Column(nullable = false)
    val translation: String,
)
