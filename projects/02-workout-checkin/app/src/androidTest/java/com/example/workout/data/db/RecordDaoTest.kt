package com.example.workout.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: RecordDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.recordDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertThenReadToday() = runBlocking {
        dao.insert(RecordEntity(date = "2026-08-18", exercise = "squat", durationMinutes = 15))
        val today = dao.getTodayRecord("2026-08-18").first()
        assertTrue(today != null)
        assertEquals("squat", today!!.exercise)
    }

    @Test
    fun duplicateDateIsRejected() = runBlocking {
        dao.insert(RecordEntity(date = "2026-08-18", exercise = "squat", durationMinutes = 15))
        var threw = false
        try {
            dao.insert(RecordEntity(date = "2026-08-18", exercise = "plank", durationMinutes = 2))
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            threw = true
        }
        assertTrue("重复日期应抛异常", threw)
    }

    @Test
    fun monthQueryFilters() = runBlocking {
        dao.insert(RecordEntity(date = "2026-08-18", exercise = "squat", durationMinutes = 15))
        dao.insert(RecordEntity(date = "2026-08-19", exercise = "plank", durationMinutes = 2))
        dao.insert(RecordEntity(date = "2026-07-31", exercise = "squat", durationMinutes = 10))
        val aug = dao.getRecordsForMonth("2026-08-%").first()
        assertEquals(2, aug.size)
    }
}
