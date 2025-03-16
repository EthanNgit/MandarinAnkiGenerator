package com.norbula.mingxue.modules.models

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @Column(nullable = false)
    val email: String,

    @Column(name = "auth_token", updatable = false, nullable = false, unique = true)
    val authToken: String, // Authentication provider auth token

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime? = null
) {
    fun toDTO(): UserDTO = UserDTO(
        email = this.email,
        authToken = this.authToken
    )
}

data class UserDTO(
    val email: String,
    val authToken: String
)
