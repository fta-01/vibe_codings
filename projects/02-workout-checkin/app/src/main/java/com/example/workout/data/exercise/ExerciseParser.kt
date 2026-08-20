package com.example.workout.data.exercise

import kotlinx.serialization.json.Json

object ExerciseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(rawJson: String): List<Exercise> =
        json.decodeFromString<List<Exercise>>(rawJson)
}
