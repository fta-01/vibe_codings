package com.example.workout.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: RecordEntity): Long

    @Query("SELECT * FROM records WHERE date LIKE :monthPattern ORDER BY date")
    fun getRecordsForMonth(monthPattern: String): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE date = :date LIMIT 1")
    fun getTodayRecord(date: String): Flow<RecordEntity?>

    @Query("SELECT date FROM records ORDER BY date")
    fun getAllDates(): Flow<List<String>>
}
