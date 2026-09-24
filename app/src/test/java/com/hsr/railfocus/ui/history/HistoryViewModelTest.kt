package com.hsr.railfocus.ui.history

import android.content.Context
import com.hsr.railfocus.R
import com.hsr.railfocus.domain.model.JourneyRecord
import com.hsr.railfocus.domain.model.PathResult
import com.hsr.railfocus.domain.model.Station
import com.hsr.railfocus.domain.usecase.GetJourneyHistoryUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var getJourneyHistoryUseCase: GetJourneyHistoryUseCase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty history shows empty state`() = runTest {
        getJourneyHistoryUseCase = mockk()
        every { getJourneyHistoryUseCase.invoke() } returns flowOf(emptyList())

        val viewModel = HistoryViewModel(createContext(), getJourneyHistoryUseCase, mockk(relaxed = true))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isEmpty)
        assertEquals(0, state.tickets.size)
    }

    @Test
    fun `completed journey produces one train ticket`() = runTest {
        val record = createCompletedJourney()
        getJourneyHistoryUseCase = mockk()
        every { getJourneyHistoryUseCase.invoke() } returns flowOf(listOf(record))

        val viewModel = HistoryViewModel(createContext(), getJourneyHistoryUseCase, mockk(relaxed = true))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isEmpty)
        assertEquals(1, state.tickets.size)

        val ticket = state.tickets.first()
        assertEquals(record, ticket.record)
        assertEquals(120, ticket.plannedMinutes)
        assertEquals(120, ticket.focusMinutes)
        assertEquals(2, ticket.stationCount)
        assertTrue(ticket.isCompleted)
        assertEquals("已完成", ticket.completionStatus)
        assertEquals("专注达成", ticket.focusState)
        assertTrue(ticket.trainNumber.startsWith("G"))
    }

    @Test
    fun `cancelled journeys show as unfinished tickets`() = runTest {
        val cancelled = createCompletedJourney().copy(
            status = com.hsr.railfocus.domain.model.JourneyStatus.CANCELLED
        )
        getJourneyHistoryUseCase = mockk()
        every { getJourneyHistoryUseCase.invoke() } returns flowOf(listOf(cancelled))

        val viewModel = HistoryViewModel(createContext(), getJourneyHistoryUseCase, mockk(relaxed = true))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isEmpty)
        assertEquals(1, state.tickets.size)

       val ticket = state.tickets.first()
       assertFalse(ticket.isCompleted)
        assertEquals("已退票", ticket.completionStatus)
       assertEquals("专注未达成", ticket.focusState)
   }

    @Test
    fun `active journeys are filtered out`() = runTest {
        val active = createCompletedJourney().copy(
            completedAt = null,
            status = com.hsr.railfocus.domain.model.JourneyStatus.ACTIVE
        )
        getJourneyHistoryUseCase = mockk()
        every { getJourneyHistoryUseCase.invoke() } returns flowOf(listOf(active))

        val viewModel = HistoryViewModel(createContext(), getJourneyHistoryUseCase, mockk(relaxed = true))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isEmpty)
        assertEquals(0, state.tickets.size)
    }

    @Test
    fun `history updates when flow emits new records`() = runTest {
        val flow = MutableStateFlow<List<JourneyRecord>>(emptyList())
        getJourneyHistoryUseCase = mockk()
        every { getJourneyHistoryUseCase.invoke() } returns flow

        val viewModel = HistoryViewModel(createContext(), getJourneyHistoryUseCase, mockk(relaxed = true))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isEmpty)

        flow.value = listOf(createCompletedJourney())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isEmpty)
        assertEquals(1, state.tickets.size)
    }

    private fun createContext(): Context {
        val context = mockk<Context>()
        every { context.getString(R.string.ticket_status_completed) } returns "已完成"
        every { context.getString(R.string.history_status_cancelled) } returns "已退票"
        every { context.getString(R.string.ticket_focus_achieved) } returns "专注达成"
        every { context.getString(R.string.ticket_focus_missed) } returns "专注未完成"
        every { context.getString(R.string.ticket_focus_unachieved) } returns "专注未达成"
        return context
    }

    private fun createCompletedJourney(): JourneyRecord {
        val start = Station.DEFAULT
        val end = Station.DEFAULT.copy(
            id = "北京南",
            name = "北京南",
            displayName = "北京南站",
            city = "北京",
        )
        return JourneyRecord(
            id = "test-id",
            startStation = start,
            endStation = end,
            plannedDurationMin = 120,
            actualDurationMin = 120,
            path = PathResult(
                path = listOf(start, end),
                totalDurationMin = 120,
                totalDistanceKm = 1000.0,
                edges = emptyList(),
            ),
            createdAt = System.currentTimeMillis(),
            completedAt = System.currentTimeMillis(),
        )
    }
}

