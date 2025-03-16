package com.norbula.mingxue.modules.models

import jakarta.persistence.*

@Entity
@Table(name = "words")
data class Word(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @Column(name = "simplified_word", unique = true)
    val simplifiedWord: String,

    @Column(name = "traditional_word")
    val traditionalWord: String,
)
