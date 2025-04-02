package com.norbula.mingxue.models.enums

enum class ContextFrequency {
    frequent, periodic, infrequent;

    companion object {
        fun tryValueOf(value: String): ContextFrequency? {
            return kotlin.runCatching { ContextFrequency.valueOf(value.lowercase().trim()) }.getOrNull()
        }
    }
}