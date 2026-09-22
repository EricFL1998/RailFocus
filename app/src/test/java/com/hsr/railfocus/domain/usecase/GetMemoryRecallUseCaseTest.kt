package com.hsr.railfocus.domain.usecase

import com.hsr.railfocus.data.repository.JournalRepository
import com.hsr.railfocus.domain.model.JourneyJournal
import com.hsr.railfocus.domain.model.RecallType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class GetMemoryRecallUseCaseTest {

    private lateinit var journalRepository: JournalRepository
    private lateinit var useCase: GetMemoryRecallUseCase

    @Before
    fun setup() {
        journalRepository = mockk()
        useCase = GetMemoryRecallUseCase(journalRepository)
    }

    @Test
    fun testSameDayExact_matched() = runTest {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 22, 12, 0, 0)
        }
        val nowMillis = nowCal.timeInMillis

        // 1 年前的同一天（严格同月同日）
        val pastCal = Calendar.getInstance().apply {
            set(2025, Calendar.SEPTEMBER, 22, 10, 30, 0)
        }
        val pastJournal = JourneyJournal(
            id = "j1",
            journeyId = "old_journey",
            stationId = "南京南",
            stationName = "南京南",
            content = "去年的今天，我在南京南站专注。",
            createdAt = pastCal.timeInMillis,
            updatedAt = pastCal.timeInMillis
        )

        coEvery { journalRepository.getByStationId("南京南") } returns listOf(pastJournal)

        val result = useCase(stationId = "南京南", currentJourneyId = "current_journey", nowMillis = nowMillis)

        assertNotNull(result)
        assertEquals(RecallType.SAME_DAY_YEARS_AGO, result?.type)
        assertEquals(1, result?.yearsAgo)
    }

    @Test
    fun testUpcomingWithin3Days_matched() = runTest {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 22, 12, 0, 0)
        }
        val nowMillis = nowCal.timeInMillis

        // 1 年前的 9月24日（距离今天刚好还有 2 天）
        val pastCal = Calendar.getInstance().apply {
            set(2025, Calendar.SEPTEMBER, 24, 10, 0, 0)
        }
        val pastJournal = JourneyJournal(
            id = "j_future",
            journeyId = "old_journey_f",
            stationId = "北京南",
            stationName = "北京南",
            content = "去年的初秋，在北京南站准备出发。",
            createdAt = pastCal.timeInMillis,
            updatedAt = pastCal.timeInMillis
        )

        coEvery { journalRepository.getByStationId("北京南") } returns listOf(pastJournal)

        val result = useCase(stationId = "北京南", currentJourneyId = "current_journey", nowMillis = nowMillis)

        assertNotNull(result)
        assertEquals(RecallType.UPCOMING_DAYS, result?.type)
        assertEquals(2, result?.daysUntil)
        assertTrue(result?.message?.contains("2天后，是你曾在这里停留的日子") == true)
        assertTrue(result?.message?.contains("期待相逢") == true)
    }

    @Test
    fun testNotSameDay_fallbackToMissedAndFutureMessage() = runTest {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 22, 12, 0, 0)
        }
        val nowMillis = nowCal.timeInMillis

        // 60 天前（7月，不在未来3天内）
        val pastCal = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 24, 10, 0, 0)
        }
        val pastJournal = JourneyJournal(
            id = "j2",
            journeyId = "old_journey_2",
            stationId = "上海虹桥",
            stationName = "上海虹桥",
            content = "盛夏时节在此。",
            createdAt = pastCal.timeInMillis,
            updatedAt = pastCal.timeInMillis
        )

        coEvery { journalRepository.getByStationId("上海虹桥") } returns listOf(pastJournal)

        val result = useCase(stationId = "上海虹桥", currentJourneyId = "current_journey", nowMillis = nowMillis)

        assertNotNull(result)
        assertEquals(RecallType.REVISIT, result?.type)
        assertEquals(60, result?.daysAgo)
        assertTrue(result?.message?.contains("60天前，你曾在这里停留") == true)
        assertTrue(result?.message?.contains("期待未来重逢") == true)
    }

    @Test
    fun testTodayJournal_notTriggered() = runTest {
        val nowMillis = System.currentTimeMillis()
        val todayJournal = JourneyJournal(
            id = "j3",
            journeyId = "current_journey",
            stationId = "杭州东",
            stationName = "杭州东",
            content = "今天刚刚完成。",
            createdAt = nowMillis - 1000 * 60 * 10,
            updatedAt = nowMillis - 1000 * 60 * 10
        )

        coEvery { journalRepository.getByStationId("杭州东") } returns listOf(todayJournal)

        val result = useCase(stationId = "杭州东", currentJourneyId = "current_journey", nowMillis = nowMillis)

        assertNull(result)
    }
}

