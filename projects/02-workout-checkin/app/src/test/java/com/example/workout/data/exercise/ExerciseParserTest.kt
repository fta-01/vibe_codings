package com.example.workout.data.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseParserTest {

    @Test
    fun parsesListOfExercises() {
        val json = """
            [
              {
                "id": "squat",
                "name": "深蹲",
                "category": "力量 · 下肢",
                "summary": "练大腿与臀部",
                "isTimed": false,
                "steps": ["步骤一", "步骤二"],
                "videoUrl": "https://example.com/v"
              }
            ]
        """.trimIndent()
        val list = ExerciseParser.parse(json)
        assertEquals(1, list.size)
        assertEquals("squat", list[0].id)
        assertEquals(2, list[0].steps.size)
        assertEquals("https://example.com/v", list[0].videoUrl)
    }

    @Test
    fun timedExerciseHasDefaultSeconds() {
        val json = """
            [
              {
                "id": "plank",
                "name": "平板支撑",
                "category": "核心",
                "summary": "核心训练",
                "isTimed": true,
                "defaultSeconds": 60,
                "steps": []
              }
            ]
        """.trimIndent()
        val list = ExerciseParser.parse(json)
        assertEquals(60, list[0].defaultSeconds)
    }

    @Test
    fun realDataParsesWithAllExercises() {
        val raw = javaClass.classLoader
            ?.getResourceAsStream("exercises.json")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw AssertionError("exercises.json 不在测试资源中")
        val list = ExerciseParser.parse(raw)
        assertTrue("至少 20 种运动，实际 ${list.size}", list.size >= 20)
        val ids = list.map { it.id }.toSet()
        assertEquals("id 必须唯一", list.size, ids.size)
        list.filter { it.isTimed }.forEach { ex ->
            assertTrue("计时型 ${ex.name} 必须有 defaultSeconds", ex.defaultSeconds != null)
        }
    }
}
