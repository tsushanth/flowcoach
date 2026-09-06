package com.factory.flowcoach.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric-backed counterpart to the instrumented DAO test, so Room persistence is also
 * exercised by `./gradlew test` without requiring a connected device.
 */
@RunWith(RobolectricTestRunner::class)
class WaterDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: WaterDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.waterDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndReadEntriesForDate() = runTest {
        dao.insert(WaterEntry(amountMl = 250, timestampEpochMillis = 1000, dateKey = "2026-09-06"))
        dao.insert(WaterEntry(amountMl = 500, timestampEpochMillis = 2000, dateKey = "2026-09-06"))
        dao.insert(WaterEntry(amountMl = 300, timestampEpochMillis = 3000, dateKey = "2026-09-05"))

        val entries = dao.entriesForDate("2026-09-06").first()

        assertEquals(2, entries.size)
        assertEquals(500, entries[0].amountMl)
        assertEquals(250, entries[1].amountMl)
    }

    @Test
    fun totalForDateSumsAmounts() = runTest {
        dao.insert(WaterEntry(amountMl = 250, timestampEpochMillis = 1000, dateKey = "2026-09-06"))
        dao.insert(WaterEntry(amountMl = 500, timestampEpochMillis = 2000, dateKey = "2026-09-06"))

        val total = dao.totalForDate("2026-09-06").first()

        assertEquals(750, total)
    }

    @Test
    fun totalForDateIsZeroWhenNoEntriesExist() = runTest {
        val total = dao.totalForDate("2026-09-06").first()

        assertEquals(0, total)
    }

    @Test
    fun deleteRemovesEntry() = runTest {
        dao.insert(WaterEntry(amountMl = 250, timestampEpochMillis = 1000, dateKey = "2026-09-06"))
        val entry = dao.entriesForDate("2026-09-06").first().first()

        dao.delete(entry)

        assertTrue(dao.entriesForDate("2026-09-06").first().isEmpty())
    }

    @Test
    fun deleteByIdRemovesEntry() = runTest {
        val id = dao.insert(WaterEntry(amountMl = 250, timestampEpochMillis = 1000, dateKey = "2026-09-06"))

        dao.deleteById(id)

        assertTrue(dao.entriesForDate("2026-09-06").first().isEmpty())
    }

    @Test
    fun dailyTotalsSinceGroupsByDateWithinRange() = runTest {
        dao.insert(WaterEntry(amountMl = 100, timestampEpochMillis = 1000, dateKey = "2026-09-01"))
        dao.insert(WaterEntry(amountMl = 200, timestampEpochMillis = 2000, dateKey = "2026-09-05"))
        dao.insert(WaterEntry(amountMl = 300, timestampEpochMillis = 3000, dateKey = "2026-09-05"))
        dao.insert(WaterEntry(amountMl = 400, timestampEpochMillis = 4000, dateKey = "2026-09-06"))

        val totals = dao.dailyTotalsSince("2026-09-05").first()

        assertEquals(2, totals.size)
        assertEquals("2026-09-06", totals[0].dateKey)
        assertEquals(400, totals[0].totalMl)
        assertEquals("2026-09-05", totals[1].dateKey)
        assertEquals(500, totals[1].totalMl)
    }

    @Test
    fun allDailyTotalsIncludesEveryDate() = runTest {
        dao.insert(WaterEntry(amountMl = 100, timestampEpochMillis = 1000, dateKey = "2026-08-01"))
        dao.insert(WaterEntry(amountMl = 400, timestampEpochMillis = 4000, dateKey = "2026-09-06"))

        val totals = dao.allDailyTotals().first()

        assertEquals(2, totals.size)
    }
}
