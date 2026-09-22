package com.hsr.railfocus.domain.service

import android.os.SystemClock
import com.hsr.railfocus.domain.model.PathResult
import com.hsr.railfocus.domain.model.Station
import com.hsr.railfocus.domain.model.journey.JourneyProgress
import com.hsr.railfocus.domain.model.journey.JourneyProgressTracker
import com.hsr.railfocus.domain.model.journey.StationArrivalDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 旅程计时服务
 * 
 * 增强版：集成进度追踪、速度模型和到站检测。
 * 基于 timeProvider() 进行绝对时间测量，
 * 确保在锁屏休眠（Doze mode）期间时间流逝不受主线程挂起影响。
 */
@Singleton
class JourneyTimerService @Inject constructor(
    private val progressTracker: JourneyProgressTracker,
    private val arrivalDetector: StationArrivalDetector
) {

    sealed class TimerState {
        data object Idle : TimerState()
        data class Running(val remainingSeconds: Int, val totalSeconds: Int) : TimerState()
        data object Paused : TimerState()
        data object Completed : TimerState()
    }

    private var timerJob: Job? = null
    
    // 计时器状态
    private val _state = MutableStateFlow<TimerState>(TimerState.Idle)
    val state: StateFlow<TimerState> = _state.asStateFlow()

    // 旅程进度（包含位置、速度等）
    private val _progress = MutableStateFlow<JourneyProgress?>(null)
    val progress: StateFlow<JourneyProgress?> = _progress.asStateFlow()
    
    // 站点到达事件
    private val _stationArrival = MutableSharedFlow<Station>()
    val stationArrival: SharedFlow<Station> = _stationArrival.asSharedFlow()
    
    // 站点发车事件（停靠结束、驶离站台）
    private val _stationDeparture = MutableSharedFlow<Station>()
    val stationDeparture: SharedFlow<Station> = _stationDeparture.asSharedFlow()

    // 即将到达事件（提前触发动画）
    private val _upcomingArrival = MutableSharedFlow<Station>()
    val upcomingArrival: SharedFlow<Station> = _upcomingArrival.asSharedFlow()

    private var savedRemainingSeconds: Int = 0
    private var totalSessionSeconds: Int = 0
    private var currentPath: PathResult? = null

    // 时间源函数，默认使用 timeProvider() 包含休眠时间，单元测试可注入
    var timeProvider: () -> Long = { SystemClock.elapsedRealtime() }

    private var startRealtimeMillis: Long = 0L
    private var pauseStartRealtimeMillis: Long = 0L
    private var totalPauseRealtimeMillis: Long = 0L

    /** 当前旅程累计晚点秒数（暂停时间） */
    val delaySeconds: Int
        get() {
            val currentPause = if (_state.value is TimerState.Paused && pauseStartRealtimeMillis > 0L) {
                timeProvider() - pauseStartRealtimeMillis
            } else 0L
            return ((totalPauseRealtimeMillis + currentPause) / 1000L).toInt()
        }

    /** 当前旅程累计晚点分钟数 */
    val delayMinutes: Int
        get() = (delaySeconds + 59) / 60

    /**
     * 启动计时器（基础版本，无进度追踪）
     */
    fun start(durationSeconds: Int, scope: CoroutineScope) {
        startBasic(remainingSeconds = durationSeconds, totalSeconds = durationSeconds, scope = scope)
    }

    /**
     * 启动基础计时器，可分别指定剩余时间与总时间，
     * 以便暂停恢复后保持原始的进度百分比。
     */
    private fun startBasic(remainingSeconds: Int, totalSeconds: Int, scope: CoroutineScope) {
        timerJob?.cancel()
        totalSessionSeconds = totalSeconds
        savedRemainingSeconds = remainingSeconds
        val elapsedSeconds = (totalSeconds - remainingSeconds).coerceAtLeast(0)
        startRealtimeMillis = timeProvider() - (elapsedSeconds * 1000L)
        pauseStartRealtimeMillis = 0L
        totalPauseRealtimeMillis = 0L

        _state.value = TimerState.Running(remainingSeconds, totalSeconds)
        _progress.value = null
        currentPath = null

        timerJob = scope.launch {
            while (isActive) {
                val now = timeProvider()
                val realElapsed = ((now - startRealtimeMillis - totalPauseRealtimeMillis) / 1000L).toInt().coerceAtLeast(0)
                val remaining = (totalSessionSeconds - realElapsed).coerceAtLeast(0)
                savedRemainingSeconds = remaining
                _state.value = TimerState.Running(remaining, totalSessionSeconds)
                if (remaining <= 0) break
                delay(1000L)
            }
            if (isActive) {
                _state.value = TimerState.Completed
            }
        }
    }
    
    /**
     * 启动计时器（增强版本，带进度追踪）
     * 
     * @param path 旅程路径
     * @param durationSeconds 总时长（秒）
     * @param scope 协程作用域
     */
    fun startWithProgress(
        path: PathResult,
        durationSeconds: Int,
        scope: CoroutineScope
    ) {
        timerJob?.cancel()
        totalSessionSeconds = durationSeconds
        savedRemainingSeconds = durationSeconds
        currentPath = path
        startRealtimeMillis = timeProvider()
        pauseStartRealtimeMillis = 0L
        totalPauseRealtimeMillis = 0L
        
        // 重置到站检测器
        arrivalDetector.reset()
        
        // 标记起始站点为已到达
        path.path.firstOrNull()?.let { startStation ->
            arrivalDetector.markAsArrived(startStation.id)
        }

        _state.value = TimerState.Running(durationSeconds, totalSessionSeconds)
        // 立即渲染初始位置，避免第一秒内进度为空
        _progress.value = progressTracker.calculateProgress(
            path = path,
            elapsedSeconds = 0,
            totalSeconds = totalSessionSeconds,
        )

        timerJob = runProgressLoop(path, scope)
    }

    fun pause() {
        // 空闲状态下暂停没有意义，避免之后 resume() 启动一个 0 秒计时器
        if (_state.value is TimerState.Idle) return
        if (pauseStartRealtimeMillis == 0L) {
            pauseStartRealtimeMillis = timeProvider()
        }
        timerJob?.cancel()
        _state.value = TimerState.Paused
    }

    fun resume(scope: CoroutineScope) {
        val current = _state.value
        if (current is TimerState.Paused) {
            if (pauseStartRealtimeMillis > 0L) {
                totalPauseRealtimeMillis += (timeProvider() - pauseStartRealtimeMillis)
                pauseStartRealtimeMillis = 0L
            }
            val path = currentPath
            if (path != null) {
                // 使用原始总时长和剩余时间继续运行，保持进度连续性
                resumeWithProgress(path, savedRemainingSeconds, totalSessionSeconds, scope)
            } else {
                // 否则使用基础版本，保留原始总时长
                startBasic(savedRemainingSeconds, totalSessionSeconds, scope)
            }
        }
    }

    private fun resumeWithProgress(
        path: PathResult,
        remainingSeconds: Int,
        totalSeconds: Int,
        scope: CoroutineScope
    ) {
        timerJob?.cancel()
        _state.value = TimerState.Running(remainingSeconds, totalSeconds)
        timerJob = runProgressLoop(path, scope)
    }

    /**
     * 将正在运行的计时器接管并绑定到更长生命周期的作用域（如前台服务的 serviceScope），
     * 确保 Activity 销毁后前台服务依然能精准驱动倒计时并在到站时触发完成。
     */
    fun bindScope(newScope: CoroutineScope) {
        val path = currentPath
        if (_state.value is TimerState.Running && path != null) {
            timerJob?.cancel()
            timerJob = runProgressLoop(path, newScope)
        }
    }

    /**
     * 统一的进度计时循环，使用 timeProvider() 测量真实时间。
     */
    private fun runProgressLoop(
        path: PathResult,
        scope: CoroutineScope,
    ): Job = scope.launch {
        val totalSeconds = totalSessionSeconds

        while (isActive) {
            val now = timeProvider()
            val realElapsed = ((now - startRealtimeMillis - totalPauseRealtimeMillis) / 1000L).toInt().coerceAtLeast(0)
            val remaining = (totalSeconds - realElapsed).coerceAtLeast(0)
            savedRemainingSeconds = remaining

            // 计算已经过的时间：使用真实已流逝秒数
            val elapsedSeconds = (totalSeconds - remaining).coerceIn(0, totalSeconds)

            // 更新计时器状态
            _state.value = TimerState.Running(remaining, totalSeconds)

            // 计算并更新进度
            val previousProgress = _progress.value
            val currentProgress = progressTracker.calculateProgress(
                path = path,
                elapsedSeconds = elapsedSeconds,
                totalSeconds = totalSeconds
            )
            _progress.value = currentProgress

            // 检测停靠结束（发车）：上一秒还在停靠，本秒开始行驶
            if (previousProgress != null &&
                previousProgress.isDwelling &&
                !currentProgress.isDwelling
            ) {
                previousProgress.currentSegmentStartStation?.let { _stationDeparture.emit(it) }
            }

            // 检测站点到达
            val arrivedStation = arrivalDetector.checkArrival(currentProgress)
            if (arrivedStation != null) {
                _stationArrival.emit(arrivedStation)
            }

            // 检测即将到达
            val upcomingStation = arrivalDetector.checkUpcomingArrival(currentProgress)
            if (upcomingStation != null) {
                _upcomingArrival.emit(upcomingStation)
            }

            if (remaining <= 0) {
                break
            }

            delay(1000L)
        }

        if (isActive) {
            _state.value = TimerState.Completed

            // 完成时的最终进度
            val finalProgress = progressTracker.calculateProgress(
                path = path,
                elapsedSeconds = totalSeconds,
                totalSeconds = totalSeconds
            )
            _progress.value = finalProgress
        }
    }

    fun stop() {
        timerJob?.cancel()
        pauseStartRealtimeMillis = 0L
        totalPauseRealtimeMillis = 0L
        startRealtimeMillis = 0L
        _state.value = TimerState.Idle
        _progress.value = null
        currentPath = null
        arrivalDetector.reset()
    }

    /**
     * 从持久化检查点恢复旅程。
     *
     * 与 [startWithProgress] 不同：总时长与剩余时间独立指定，
     * 已路过的站点不会对齐重放到达事件。
     *
     * @param path 旅程路径
     * @param totalSeconds 原始总时长（秒）
     * @param remainingSeconds 检查点记录的剩余时间（秒）
     * @param scope 协程作用域
     */
    fun restoreProgress(
        path: PathResult,
        totalSeconds: Int,
        remainingSeconds: Int,
        scope: CoroutineScope,
    ) {
        timerJob?.cancel()
        pauseStartRealtimeMillis = 0L
        totalPauseRealtimeMillis = 0L
        totalSessionSeconds = totalSeconds
        savedRemainingSeconds = remainingSeconds
        currentPath = path

        val clampedRemaining = remainingSeconds.coerceIn(0, totalSeconds)
        val elapsedSeconds = totalSeconds - clampedRemaining
        startRealtimeMillis = timeProvider() - (elapsedSeconds * 1000L)

        arrivalDetector.reset()
        path.path.firstOrNull()?.let { arrivalDetector.markAsArrived(it.id) }

        val initialProgress = progressTracker.calculateProgress(
            path = path,
            elapsedSeconds = elapsedSeconds,
            totalSeconds = totalSeconds,
        )
        arrivalDetector.syncToProgress(initialProgress)

        _state.value = if (clampedRemaining > 0) {
            TimerState.Running(clampedRemaining, totalSeconds)
        } else {
            TimerState.Completed
        }
        _progress.value = initialProgress

        if (clampedRemaining > 0) {
            timerJob = runProgressLoop(path, scope)
        }
    }
    
    /**
     * 获取当前进度（如果有）
     */
    fun getCurrentProgress(): JourneyProgress? {
        return _progress.value
    }
    
    /**
     * 获取剩余时间（秒）
     */
    fun getRemainingSeconds(): Int {
        return savedRemainingSeconds
    }

    /**
     * 获取当前路径信息（用于完成恢复）
     */
    fun getCurrentPath(): PathResult? {
        return currentPath
    }
}

