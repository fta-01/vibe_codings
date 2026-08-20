package com.example.workout.domain

import java.time.LocalDate

object Streak {
    fun currentStreak(checkedDates: Set<LocalDate>, today: LocalDate): Int {
        val start = when {
            today in checkedDates -> today
            today.minusDays(1) in checkedDates -> today.minusDays(1)
            else -> return 0
        }
        var streak = 0
        var cursor = start
        while (cursor in checkedDates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
