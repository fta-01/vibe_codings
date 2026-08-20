package com.example.workout.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "records",
    indices = [Index(value = ["date"], unique = true)],
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val exercise: String,
    val durationMinutes: Int,
)
