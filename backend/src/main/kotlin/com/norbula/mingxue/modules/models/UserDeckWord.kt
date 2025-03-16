package com.norbula.mingxue.modules.models

import jakarta.persistence.*

@Entity
@Table(name = "word_translations")
data class UserDeckWord(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @ManyToOne
    @JoinColumn(name = "deck_id", nullable = false)
    val deck: UserDeck,

    @ManyToOne
    @JoinColumn(name = "word_id", nullable = false)
    val wordContext: WordContext,
)
