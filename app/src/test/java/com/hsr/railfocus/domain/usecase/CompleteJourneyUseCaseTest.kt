package com.hsr.railfocus.domain.usecase

import com.hsr.railfocus.data.repository.JourneyRepository
import com.hsr.railfocus.domain.model.JourneyRecord
import com.hsr.railfocus.domain.model.JourneyStatus
import com.hsr.railfocus.domain.model.PathResult
import com.hsr.railfocus.domain.model.Station
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CompleteJourneyUseCaseTest {

    private lateinit var journeyRepository: JourneyRepository
    private lateinit var completeJourneyUseCase: CompleteJourneyUseCase

    @Before
    fun setup() {
        journeyRepository = mockk()
        completeJourneyUseCase = CompleteJourneyUseCase(journeyRepository)
    }

    private fun createDummyRecord(id: String, status: JourneyStatus, completedAt: Long? = null): JourneyRecord {
        val s1 = Station(id = "1", name = "南京南", displayName = "南京南", province = "江苏", city = "南京", lat = 31.97, lng = 118.80)
        val s2 = Station(id = "2", name = "北京南", displayName = "北京南", province = "北京", city = "北京", lat = 39.86, lng = 116.37)
        return JourneyRecord(
            id = id,
            startStation = s1,
            endStation = s2,
            plannedDurationMin = 210,
            actualDurationMin = 210,
            path = PathResult(listOf(s1, s2), 210, 1000.0, emptyList()),
            createdAt = 1000L,
            completedAt = completedAt,
            status = status,
        )
    }

    @Test
    fun testCompleteActiveJourney_succeedsAndReturnsUpdatedRecord() = runTest {
        val journeyId = "j_123"
        val active = createDummyRecord(journeyId, JourneyStatus.ACTIVE)
        val completed = createDummyRecord(journeyId, JourneyStatus.COMPLETED, 2000L)

        coEvery { journeyRepository.completeJourney(journeyId, 210, 0, null) } returns true
        coEvery { journeyRepository.getJourneyById(journeyId) } returns completed

        val result = completeJourneyUseCase(journeyId, 210, 0, null)
        assertNotNull(result)
        assertEquals(JourneyStatus.COMPLETED, result!!.status)
    }

    @Test
    fun testCompleteAlreadyCompletedJourney_returnsNullToPreventDoubleCounting() = runTest {
        val journeyId = "j_123"
        coEvery { journeyRepository.completeJourney(journeyId, 210, 0, null) } returns false

        val result = completeJourneyUseCase(journeyId, 210, 0, null)
        assertNull("已完成的旅程再次结算时必须返回 null，杜绝翻倍计入", result)
    }
}
