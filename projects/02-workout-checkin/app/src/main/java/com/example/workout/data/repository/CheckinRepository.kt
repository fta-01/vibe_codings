package com.example.workout.data.repository

import android.content.Context
import com.example.workout.R
import com.example.workout.data.db.RecordDao
import com.example.workout.data.db.RecordEntity
import com.example.workout.data.exercise.Exercise
import com.example.workout.data.exercise.ExerciseParser
import kotlinx.coroutines.flow.Flow

class CheckinRepository(
    private val dao: RecordDao,
    private val context: Context,
) {
    suspend fun checkIn(date: String, exercise: String, durationMinutes: Int): Boolean {
        return try {
            dao.insert(RecordEntity(date = date, exercise = exercise, durationMinutes = durationMinutes))
            true
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            false
        }
    }

    fun recordsForMonth(month: String): Flow<List<RecordEntity>> =
        dao.getRecordsForMonth("$month-%")

    fun todayRecord(date: String): Flow<RecordEntity?> =
        dao.getTodayRecord(date)

    fun allDates(): Flow<List<String>> =
        dao.getAllDates()

    suspend fun loadExercises(): List<Exercise> {
        val raw = context.resources.openRawResource(R.raw.exercises)
            .bufferedReader()
            .use { it.readText() }
        return ExerciseParser.parse(raw)
    }
}
