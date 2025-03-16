package com.norbula.mingxue.modules.models

import jakarta.persistence.*

@Entity
@Table(name = "user_decks")
data class UserDeck(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val description: String,

    @Column(nullable = false)
    val isPublic: Boolean,
)
