package com.example.workout.data.exercise

import kotlinx.serialization.Serializable

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val category: String,
    val summary: String,
    val isTimed: Boolean,
    val defaultSeconds: Int? = null,
    val reps: String? = null,
    val steps: List<String> = emptyList(),
    val videoUrl: String? = null,
    val note: String? = null,
)
