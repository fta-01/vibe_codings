package com.example.workout.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun emptySetIsZero() {
        assertEquals(0, Streak.currentStreak(emptySet(), d(2026, 8, 18)))
    }

    @Test
    fun onlyTodayIsOne() {
        assertEquals(1, Streak.currentStreak(setOf(d(2026, 8, 18)), d(2026, 8, 18)))
    }

    @Test
    fun todayMissingButYesterdayPresent() {
        assertEquals(1, Streak.currentStreak(setOf(d(2026, 8, 17)), d(2026, 8, 18)))
    }

    @Test
    fun fiveConsecutiveDays() {
        val days = (14..18).map { d(2026, 8, it) }.toSet()
        assertEquals(5, Streak.currentStreak(days, d(2026, 8, 18)))
    }

    @Test
    fun gapBreaksStreak() {
        val days = setOf(d(2026, 8, 15), d(2026, 8, 16), d(2026, 8, 18))
        assertEquals(1, Streak.currentStreak(days, d(2026, 8, 18)))
    }

    @Test
    fun streakAcrossMonthBoundary() {
        val days = setOf(d(2026, 1, 31), d(2026, 2, 1), d(2026, 2, 2))
        assertEquals(3, Streak.currentStreak(days, d(2026, 2, 2)))
    }

    @Test
    fun streakAcrossYearBoundary() {
        val days = setOf(d(2025, 12, 31), d(2026, 1, 1))
        assertEquals(2, Streak.currentStreak(days, d(2026, 1, 1)))
    }

    @Test
    fun neitherTodayNorYesterdayIsZero() {
        assertEquals(0, Streak.currentStreak(setOf(d(2026, 8, 10)), d(2026, 8, 18)))
    }
}
