package com.hsr.railfocus.ui.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hsr.railfocus.domain.model.PathResult
import com.hsr.railfocus.domain.model.Station
import com.hsr.railfocus.domain.model.StationFactsProvider
import com.hsr.railfocus.domain.service.DestinationOption
import com.hsr.railfocus.domain.service.JourneyTimerService
import com.hsr.railfocus.domain.usecase.CancelJourneyUseCase
import com.hsr.railfocus.domain.usecase.CompleteJourneyUseCase
import com.hsr.railfocus.domain.usecase.StartJourneyUseCase
import android.content.Context
import android.content.Intent
import com.hsr.railfocus.service.FocusTimerService
import com.hsr.railfocus.data.repository.JourneyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 专注页面 ViewModel
 *
 * 负责：
 * 1. 从导航参数恢复目的地和路径信息
 * 2. 启动/暂停/恢复/停止专注计时器
 * 3. 收集计时器状态、进度、到站事件并更新 UI
 * 4. 完成或停止时持久化旅程记录
 */
@HiltViewModel
class FocusSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val timerService: JourneyTimerService,
    private val startJourneyUseCase: StartJourneyUseCase,
    private val completeJourneyUseCase: CompleteJourneyUseCase,
    private val cancelJourneyUseCase: CancelJourneyUseCase,
    private val journeyRepository: JourneyRepository,
    private val preferencesRepository: com.hsr.railfocus.data.preferences.UserPreferencesRepository,
) : ViewModel() {

    companion object {
        const val ARG_DESTINATION_JSON = "destination_json"
    }

    private val _uiState = MutableStateFlow(FocusSessionUiState())
    val uiState: StateFlow<FocusSessionUiState> = _uiState.asStateFlow()

    /** 防止完成/取消被重复处理（StateFlow 重放、多次点击等） */
    private var hasFinished: Boolean = false

    /** 上次写入检查点的剩余秒数 */
    private var lastCheckpointRemaining: Int = -1

    init {
        viewModelScope.launch {
            // 1. 优先恢复数据库中记录为 ACTIVE 的旅程（进程被杀后的恢复）
            val active = journeyRepository.getActiveJourney()
            if (active != null) {
                restoreFromRecord(active)
                return@launch
            }

            // 2. Recover from SavedStateHandle if present (for backward compatibility/deep links)
            val destination: DestinationOption? = savedStateHandle.get<String>(ARG_DESTINATION_JSON)
                ?.let { DestinationOption.fromJson(it) }

            if (destination != null) {
                startJourney(destination)
            } else if (timerService.state.value !is JourneyTimerService.TimerState.Idle) {
                // 3. Check if timer service is already running (e.g. returning to Home while session is active)
                recoverExistingSession()
            }
        }
        observeTimer()
    }

    /**
     * Start a new journey journey
     */
    fun startJourney(
        destination: DestinationOption,
        focusType: FocusType? = null,
        seatNumber: String? = null,
    ) {
        val path = PathResult(
            path = destination.pathStations,
            totalDurationMin = destination.travelTimeMinutes,
            totalDistanceKm = destination.distance,
            edges = destination.pathEdges,
        )

        val totalSeconds = destination.travelTimeMinutes * 60
        val carriageNumber = (1..16).random().toString()

        _uiState.update {
            it.copy(
                journeyId = null,
                isRestored = false,
                restoredDestinationJson = "",
                startStation = destination.pathStations.firstOrNull() ?: Station.DEFAULT,
                endStation = destination.station,
                path = path,
                remainingSeconds = totalSeconds,
                totalSeconds = totalSeconds,
                isCompleted = false,
                isStopped = false,
                focusType = focusType,
                seatNumber = seatNumber,
                carriageNumber = carriageNumber,
                error = null,
            )
        }
        hasFinished = false
        lastCheckpointRemaining = -1

        timerService.startWithProgress(path, totalSeconds, viewModelScope)

        // 立即以 ACTIVE 状态落库，作为进程被杀后的恢复依据
        viewModelScope.launch {
            try {
                val record = startJourneyUseCase(
                    startStation = destination.pathStations.firstOrNull() ?: Station.DEFAULT,
                    endStation = destination.station,
                    path = path,
                    plannedDurationMin = totalSeconds / 60,
                    actualDurationMin = 0,
                    status = com.hsr.railfocus.domain.model.JourneyStatus.ACTIVE,
                    focusType = focusType?.displayName,
                    seatNumber = seatNumber,
                    carriageNumber = carriageNumber,
                    completedAt = null,
                )
                _uiState.update { it.copy(journeyId = record.id) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * 从数据库记录恢复进行中的旅程。
     */
    private suspend fun restoreFromRecord(record: com.hsr.railfocus.domain.model.JourneyRecord) {
        val totalSeconds = record.plannedDurationMin * 60
        val remaining = (record.remainingSec ?: totalSeconds).coerceIn(0, totalSeconds)
        val destination = DestinationOption(
            station = record.endStation,
            travelTimeMinutes = record.plannedDurationMin,
            distance = record.path.totalDistanceKm,
            recommendationScore = 0.0,
            pathEdges = record.path.edges,
            pathStations = record.path.path,
            isVisited = false,
        )

        _uiState.update {
            it.copy(
                journeyId = record.id,
                isRestored = true,
                restoredDestinationJson = destination.toJson(),
                startStation = record.startStation,
                endStation = record.endStation,
                path = record.path,
                remainingSeconds = remaining,
                totalSeconds = totalSeconds,
                isCompleted = false,
                isStopped = false,
                isPaused = false,
                seatNumber = record.seatNumber,
                carriageNumber = record.carriageNumber,
                error = null,
            )
        }
        hasFinished = false
        lastCheckpointRemaining = remaining

        timerService.restoreProgress(record.path, totalSeconds, remaining, viewModelScope)
    }

    private fun recoverExistingSession() {
        val progress = timerService.progress.value ?: return
        val remaining = timerService.getRemainingSeconds()
        
        // We don't have the full DestinationOption here, but we can reconstruct enough for UI
        // In a real app, we might want to persist the destination JSON in the service or a DB
        _uiState.update {
            it.copy(
                startStation = progress.currentSegmentStartStation ?: Station.DEFAULT,
                endStation = progress.nextStation ?: Station.DEFAULT,
                path = null, // Path might be missing if we don't persist it
                remainingSeconds = remaining,
                totalSeconds = remaining, // Approximation
                overallProgress = progress.overallProgress,
                currentStation = progress.currentSegmentStartStation,
                nextStation = progress.nextStation,
                currentSpeed = progress.currentSpeed
            )
        }
    }

    private fun observeTimer() {
        timerService.state
            .onEach { state ->
                when (state) {
                    is JourneyTimerService.TimerState.Running -> {
                        _uiState.update {
                            it.copy(
                                remainingSeconds = state.remainingSeconds,
                                isPaused = false,
                                isCompleted = false,
                            )
                        }
                        checkpointRemaining(state.remainingSeconds)
                    }
                    is JourneyTimerService.TimerState.Paused -> {
                        _uiState.update { it.copy(isPaused = true) }
                        checkpointRemaining(_uiState.value.remainingSeconds)
                    }
                    is JourneyTimerService.TimerState.Completed -> {
                        _uiState.update { it.copy(isCompleted = true) }
                        loadStationFact()
                        finishJourney(com.hsr.railfocus.domain.model.JourneyStatus.COMPLETED)
                        saveLastLocation()
                        
                        // Stop the foreground service to dismiss notification
                        context.stopService(Intent(context, FocusTimerService::class.java))
                    }
                    else -> { /* Idle */ }
                }
            }
            .launchIn(viewModelScope)

        timerService.progress
            .onEach { progress ->
                if (progress != null) {
                    _uiState.update {
                        it.copy(
                            overallProgress = progress.overallProgress,
                            currentStation = progress.currentSegmentStartStation,
                            nextStation = progress.nextStation,
                            currentSpeed = progress.currentSpeed
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * 以节流的频率把剩余时间写入数据库检查点（约每 10 秒一次），
     * 避免每秒一次的数据库写入。
     */
    private fun checkpointRemaining(remainingSeconds: Int) {
        val journeyId = _uiState.value.journeyId ?: return
        if (lastCheckpointRemaining >= 0 && (lastCheckpointRemaining - remainingSeconds) < 10) return
        lastCheckpointRemaining = remainingSeconds
        viewModelScope.launch {
            try {
                journeyRepository.updateRemaining(journeyId, remainingSeconds)
            } catch (_: Exception) {
                // 检查点写入失败不影响计时
            }
        }
    }

    /**
     * 结束旅程：完成走 CompleteJourneyUseCase（更新 ACTIVE 记录），
     * 取消走 CancelJourneyUseCase；仅在无法找到 ACTIVE 记录时
     * 退化为插入一条终态记录，保证历史不丢。
     */
    private fun finishJourney(status: com.hsr.railfocus.domain.model.JourneyStatus) {
        if (hasFinished) return
        hasFinished = true
        val state = _uiState.value
        val path = state.path ?: return
        val actualDurationMin = ((state.totalSeconds - state.remainingSeconds) / 60).coerceAtLeast(0)

        viewModelScope.launch {
            try {
                val journeyId = state.journeyId
                if (journeyId != null) {
                    when (status) {
                        com.hsr.railfocus.domain.model.JourneyStatus.COMPLETED ->
                            completeJourneyUseCase(journeyId, actualDurationMin.coerceAtLeast(1))
                        else ->
                            cancelJourneyUseCase(journeyId, actualDurationMin)
                    }
                } else {
                    startJourneyUseCase(
                        startStation = state.startStation,
                        endStation = state.endStation,
                        path = path,
                        plannedDurationMin = state.totalSeconds / 60,
                        actualDurationMin = actualDurationMin,
                        status = status,
                        focusType = state.focusType?.displayName,
                        seatNumber = state.seatNumber,
                        carriageNumber = state.carriageNumber,
                        completedAt = System.currentTimeMillis(),
                    )
                }
                // 只有完成的旅程计入每日目标与连续打卡
                if (status == com.hsr.railfocus.domain.model.JourneyStatus.COMPLETED) {
                    preferencesRepository.recordFocusMinutes(actualDurationMin.coerceAtLeast(1))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private fun saveLastLocation() {
        val endStation = _uiState.value.endStation
        viewModelScope.launch {
            preferencesRepository.saveLastLocation(
                com.hsr.railfocus.data.preferences.SavedLocation(
                    latitude = endStation.lat,
                    longitude = endStation.lng,
                    stationId = endStation.id,
                    stationName = endStation.name,
                    city = endStation.city
                )
            )
        }
    }

    fun pause() {
        timerService.pause()
    }

    fun resume() {
        timerService.resume(viewModelScope)
    }

    fun stop() {
        if (_uiState.value.isStopped) return
        timerService.stop()
        _uiState.update { it.copy(isStopped = true) }
        finishJourney(com.hsr.railfocus.domain.model.JourneyStatus.CANCELLED)

        // Stop the foreground service to dismiss notification
        context.stopService(Intent(context, FocusTimerService::class.java))
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun loadStationFact() {
        try {
            StationFactsProvider.load(context)
            val fact = StationFactsProvider.randomFactForCity(_uiState.value.endStation.city)
            _uiState.update { it.copy(stationFact = fact) }
        } catch (_: Exception) {
            // ignore
        }
    }
}
