package com.factory.flowcoach.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterDao {

    @Insert
    suspend fun insert(entry: WaterEntry): Long

    @Delete
    suspend fun delete(entry: WaterEntry)

    @Query("SELECT * FROM water_entries WHERE dateKey = :dateKey ORDER BY timestampEpochMillis DESC")
    fun entriesForDate(dateKey: String): Flow<List<WaterEntry>>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_entries WHERE dateKey = :dateKey")
    fun totalForDate(dateKey: String): Flow<Int>

    @Query(
        """
        SELECT dateKey, SUM(amountMl) AS totalMl
        FROM water_entries
        WHERE dateKey >= :fromDateKey
        GROUP BY dateKey
        ORDER BY dateKey DESC
        """
    )
    fun dailyTotalsSince(fromDateKey: String): Flow<List<DailyTotal>>

    @Query(
        """
        SELECT dateKey, SUM(amountMl) AS totalMl
        FROM water_entries
        GROUP BY dateKey
        ORDER BY dateKey DESC
        """
    )
    fun allDailyTotals(): Flow<List<DailyTotal>>

    @Query("DELETE FROM water_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
