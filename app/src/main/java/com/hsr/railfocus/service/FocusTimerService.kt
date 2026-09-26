package com.hsr.railfocus.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.media.MediaPlayer
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.PowerManager
import com.hsr.railfocus.MainActivity
import com.hsr.railfocus.R
import com.hsr.railfocus.data.preferences.UserPreferencesRepository
import com.hsr.railfocus.domain.model.Station
import com.hsr.railfocus.ui.widget.FocusTimerWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.hsr.railfocus.domain.service.DestinationOption
import com.hsr.railfocus.domain.service.JourneyTimerService
import com.hsr.railfocus.domain.usecase.CompleteJourneyUseCase
import com.hsr.railfocus.data.repository.JourneyRepository
import androidx.annotation.RequiresApi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 专注计时前台服务 — 标准官方 Chronometer 版
 */
@AndroidEntryPoint
class FocusTimerService : Service() {

    companion object {
        const val CHANNEL_ID = "focus_timer"
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_ID_COMPLETED = 1002

        const val ACTION_START = "com.hsr.railfocus.ACTION_START_FOCUS"
        const val ACTION_PAUSE = "com.hsr.railfocus.ACTION_PAUSE_FOCUS"
        const val ACTION_RESUME = "com.hsr.railfocus.ACTION_RESUME_FOCUS"
        const val ACTION_STOP = "com.hsr.railfocus.ACTION_STOP_FOCUS"

        /** 应用是否在前台；前台完成旅程时不再弹"已到达"通知，由完成卡片直接呈现 */
        @Volatile
        var isAppInForeground = false

        const val EXTRA_DESTINATION_JSON = "destination_json"

        private const val AMBIENT_NORMAL_VOLUME = 0.3f
        private const val AMBIENT_DUCK_VOLUME = 0.1f
        private var currentAmbientVolumeFraction = 0.3f

        fun createStartIntent(context: Context, destinationJson: String): Intent {
            return Intent(context, FocusTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DESTINATION_JSON, destinationJson)
            }
        }
    }

    @Inject
    lateinit var timerService: JourneyTimerService

    @Inject
    lateinit var completeJourneyUseCase: CompleteJourneyUseCase

    @Inject
    lateinit var journeyRepository: JourneyRepository

    @Inject
    lateinit var preferencesRepository: UserPreferencesRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observationJobs: List<kotlinx.coroutines.Job> = emptyList()
    private var wakeLock: PowerManager.WakeLock? = null

    private var destinationJson: String? = null
    private var startStationName: String = ""
    private var endStationName: String = ""
    private var pathStations: List<Station> = emptyList()
    private var totalSeconds: Int = 0

    // 绝对结束时间戳，由系统 UI 托管倒计时
    private var absoluteEndTimeMillis: Long = 0

    // 到站播报文案，优先于常规线路文本展示
    private var arrivalAnnouncement: String? = null
    private var announcementClearJob: Job? = null

    // 车厢环境音播放器
    private var ambientPlayer: MediaPlayer? = null

    // 站台播报播放器（进出站广播音）
    private var announcementPlayer: MediaPlayer? = null

    private var audioManager: android.media.AudioManager? = null
    private var audioFocusRequest: android.media.AudioFocusRequest? = null

    private fun requestAudioFocus(): Boolean {
        if (audioManager == null) {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        }
        val am = audioManager ?: return false
        val playbackAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val focusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(playbackAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    android.media.AudioManager.AUDIOFOCUS_LOSS -> pauseAmbience()
                    android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseAmbience()
                    android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        ambientPlayer?.setVolume(AMBIENT_DUCK_VOLUME, AMBIENT_DUCK_VOLUME)
                    }
                    android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                        if (announcementPlayer == null) {
                            ambientPlayer?.setVolume(currentAmbientVolumeFraction, currentAmbientVolumeFraction)
                            resumeAmbience()
                        }
                    }
                }
            }
            .build()
        audioFocusRequest = focusRequest
        return am.requestAudioFocus(focusRequest) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { req ->
            audioManager?.abandonAudioFocusRequest(req)
            audioFocusRequest = null
        }
    }

    // 站台播报开关（由偏好流量持续同步，开关后无需重启旅程即生效）
    @Volatile
    private var stationAnnouncementEnabled = false

    private var preferenceJob: Job? = null

    private var lastWidgetUpdateMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> {
                timerService.pause()
                pauseAmbience()
                updateNotification()
            }
            ACTION_RESUME -> handleResume()
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val json = intent.getStringExtra(EXTRA_DESTINATION_JSON) ?: return
        if (json == destinationJson) {
            // 同一个旅程重复启动（如页面与服务各自发起），无需重建通知与观察器
            return
        }
        destinationJson = json

        val destination = DestinationOption.fromJson(json) ?: return
        val path = com.hsr.railfocus.domain.model.PathResult(
            path = destination.pathStations,
            totalDurationMin = destination.travelTimeMinutes,
            totalDistanceKm = destination.distance,
            edges = destination.pathEdges,
        )
        totalSeconds = destination.travelTimeMinutes * 60

        startStationName = destination.pathStations.firstOrNull()?.name ?: getString(R.string.notif_start_fallback)
        endStationName = destination.station.name
        pathStations = destination.pathStations

        // 初始化结束时间
        absoluteEndTimeMillis = System.currentTimeMillis() + (totalSeconds * 1000L)

        createNotificationChannel()

        val notification = buildNotification()
        
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {}

        acquireWakeLock(totalSeconds)

        if (timerService.state.value is JourneyTimerService.TimerState.Idle) {
            timerService.startWithProgress(path, totalSeconds, serviceScope)
        } else {
            timerService.bindScope(serviceScope)
        }
        
        serviceScope.launch {
            if (preferencesRepository.ambientSoundEnabled.first()) {
                startAmbience()
            }
        }

        observeStationAnnouncementPreference()
        observeTimer()
    }

    private fun handleResume() {
        // 进程存活：直接恢复暂停中的计时
        if (timerService.state.value is JourneyTimerService.TimerState.Paused) {
            val remaining = timerService.getRemainingSeconds()
            absoluteEndTimeMillis = System.currentTimeMillis() + (remaining * 1000L)
            timerService.resume(serviceScope)
            resumeAmbience()
            updateNotification()
            return
        }
        // 进程被杀后从通知恢复：计时器单例已随进程重建为 Idle，resume() 是 no-op，
        // 必须从数据库检查点恢复旅程，否则 ACTIVE 旅程将永远无人完成
        if (timerService.state.value !is JourneyTimerService.TimerState.Idle) return
        serviceScope.launch {
            val active = journeyRepository.getActiveJourney() ?: run {
                stopSelf()
                return@launch
            }
            val total = active.plannedDurationMin * 60
            val remaining = (active.remainingSec ?: total).coerceIn(0, total)
            val destination = DestinationOption(
                station = active.endStation,
                travelTimeMinutes = active.plannedDurationMin,
                distance = active.path.totalDistanceKm,
                recommendationScore = 0.0,
                pathEdges = active.path.edges,
                pathStations = active.path.path,
                isVisited = false,
            )
            destinationJson = destination.toJson()
            totalSeconds = total
            startStationName = active.startStation.name
            endStationName = active.endStation.name
            pathStations = active.path.path
            absoluteEndTimeMillis = System.currentTimeMillis() + remaining * 1000L

            createNotificationChannel()
            val notification = buildNotification()
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (_: Exception) {}

            acquireWakeLock(remaining)

            timerService.restoreProgress(active.path, total, remaining, serviceScope)

            if (preferencesRepository.ambientSoundEnabled.first()) {
                startAmbience()
            }
            observeStationAnnouncementPreference()
            observeTimer()
            updateNotification()
        }
    }

   private fun observeStationAnnouncementPreference() {
       preferenceJob?.cancel()
       preferenceJob = serviceScope.launch {
           // 旅程开始即视为从始发站发车
           if (preferencesRepository.stationAnnouncementEnabled.first()) {
               stationAnnouncementEnabled = true
               playStationAnnouncement(R.raw.train_departure_announcement)
           }
           // 持续同步开关，旅程中途切换立即生效
            launch {
                preferencesRepository.stationAnnouncementEnabled.collect { enabled ->
                    stationAnnouncementEnabled = enabled
                }
            }
            launch {
                preferencesRepository.ambientSoundVolume.collect { vol ->
                    currentAmbientVolumeFraction = (vol / 100f).coerceIn(0f, 1f)
                    if (announcementPlayer == null) {
                        ambientPlayer?.setVolume(currentAmbientVolumeFraction, currentAmbientVolumeFraction)
                    }
                }
            }
            launch {
                preferencesRepository.ambientSoundEnabled.collect { enabled ->
                    if (!enabled) {
                        pauseAmbience()
                    } else if (timerService.state.value is JourneyTimerService.TimerState.Running) {
                        resumeAmbience()
                    }
                }
            }
       }
   }

    /**
     * 播放站台广播音。
     *
     * 播报期间压低车厢环境音避免互相掩盖，播放结束恢复音量。
     * [onComplete] 在广播音自然播放完成后回调（被打断时不回调）。
     */
    private fun playStationAnnouncement(soundRes: Int, onComplete: (() -> Unit)? = null) {
        if (!stationAnnouncementEnabled) return
        requestAudioFocus()
        runCatching {
            announcementPlayer?.let { player ->
                player.setOnCompletionListener(null)
                runCatching { player.stop() }
                runCatching { player.release() }
            }
            ambientPlayer?.setVolume(AMBIENT_DUCK_VOLUME, AMBIENT_DUCK_VOLUME)
            val player = MediaPlayer.create(this, soundRes)
            if (player != null) {
               player.setOnCompletionListener {
                   announcementPlayer = null
                   runCatching { it.release() }
                    ambientPlayer?.setVolume(currentAmbientVolumeFraction, currentAmbientVolumeFraction)
                   onComplete?.invoke()
               }
               player.start()
               announcementPlayer = player
           } else {
               // 创建失败时恢复环境音音量
                ambientPlayer?.setVolume(currentAmbientVolumeFraction, currentAmbientVolumeFraction)
           }
        }
    }

   private fun startAmbience() {
       if (ambientPlayer != null) return
       runCatching {
           ambientPlayer = MediaPlayer.create(this, R.raw.hsr_whitenoise)?.apply {
               isLooping = true
                setVolume(currentAmbientVolumeFraction, currentAmbientVolumeFraction)
               start()
           }
       }
   }

    private fun pauseAmbience() {
        runCatching { ambientPlayer?.takeIf { it.isPlaying }?.pause() }
    }

    private fun resumeAmbience() {
        if (timerService.state.value !is JourneyTimerService.TimerState.Running) return
        serviceScope.launch {
            if (preferencesRepository.ambientSoundEnabled.first()) {
                if (ambientPlayer == null) {
                    startAmbience()
                } else {
                    runCatching { ambientPlayer?.takeIf { !it.isPlaying }?.start() }
                }
            }
        }
    }

    private fun observeTimer() {
        // 先取消旧的观察器，避免多次 ACTION_START 累积重复收集
        observationJobs.forEach { it.cancel() }

        val stateJob = timerService.state
            .onEach { state ->
                when (state) {
                    is JourneyTimerService.TimerState.Running -> {
                        resumeAmbience()
                        updateNotification()
                    }
                    is JourneyTimerService.TimerState.Paused -> {
                        pauseAmbience()
                        updateNotification()
                    }
                    is JourneyTimerService.TimerState.Completed -> {
                        pauseAmbience()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        serviceScope.launch {
                            try {
                                completeActiveJourneyInBackground()
                                showCompletionNotification()
                            } finally {
                                stopSelf()
                            }
                        }
                    }
                    else -> {}
                }
            }
            .launchIn(serviceScope)

        val progressJob = timerService.progress
            .onEach { progress ->
                if (progress != null) {
                    updateNotification()
                    val remaining = timerService.getRemainingSeconds()
                    updateWidgetThrottled(remaining)
                }
            }
            .launchIn(serviceScope)

        val upcomingJob = timerService.upcomingArrival
            .onEach { station ->
                announce(getString(R.string.notif_upcoming_station, station.name), vibrate = false)
            }
            .launchIn(serviceScope)

       val arrivalJob = timerService.stationArrival
           .onEach { station ->
              announce(getString(R.string.notif_arrived_station, station.name), vibrate = true)
                // 进站停车广播；发车广播由停靠结束事件触发
                playStationAnnouncement(R.raw.train_arrival_announcement)
            }
            .launchIn(serviceScope)

        val departureJob = timerService.stationDeparture
            .onEach {
                playStationAnnouncement(R.raw.train_departure_announcement)
            }
            .launchIn(serviceScope)

        observationJobs = listOf(stateJob, progressJob, upcomingJob, arrivalJob, departureJob)
    }

    private fun announce(text: String, vibrate: Boolean) {
        arrivalAnnouncement = text
        if (vibrate) vibrateOnce()
        updateNotification()

        announcementClearJob?.cancel()
        announcementClearJob = serviceScope.launch {
            delay(8_000)
            arrivalAnnouncement = null
            updateNotification()
        }
    }

    private fun vibrateOnce() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(
                VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (_: Exception) {}
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    /** 小组件刷新节流：每 5 秒最多一次，避免频繁跨进程刷新，同时同步 remainingSec 到数据库 */
    private fun updateWidgetThrottled(remainingSeconds: Int) {
        val now = System.currentTimeMillis()
        if (now - lastWidgetUpdateMs < 5_000) return
        lastWidgetUpdateMs = now
        serviceScope.launch {
            runCatching {
                val active = journeyRepository.getActiveJourney()
                if (active != null) {
                    journeyRepository.updateRemaining(active.id, remainingSeconds)
                }
            }
            runCatching {
                val manager = GlanceAppWidgetManager(this@FocusTimerService)
                manager.getGlanceIds(FocusTimerWidget::class.java).forEach { id ->
                    FocusTimerWidget().update(this@FocusTimerService, id)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setSound(null, null)
                enableVibration(false)
            }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val progress = timerService.getCurrentProgress()
        val isPaused = timerService.state.value is JourneyTimerService.TimerState.Paused
        val overallProgress = progress?.overallProgress ?: 0f
        val currentSegmentIndex = progress?.currentSegmentIndex ?: 0

        return if (Build.VERSION.SDK_INT >= 36) {
            buildProgressStyleNotification(isPaused, currentSegmentIndex, overallProgress)
        } else {
            buildFallbackNotification(isPaused, currentSegmentIndex)
        }
    }

    /**
     * 生成通知标题与正文。
     * 精确控制文案层次，确保终点站名与中间站名在标题和正文间互不重复。
     */
    private fun buildNotificationContent(
        isPaused: Boolean,
        currentSegmentIndex: Int
    ): Pair<String, String> {
        val progress = timerService.getCurrentProgress()
        val nextStationName = progress?.nextStation?.name ?: currentStationLabel(currentSegmentIndex)
        val isDwelling = progress?.isDwelling == true
        val speedInt = progress?.currentSpeed?.toInt() ?: 0

        val titleText: String
        val contentText: String

        when {
            isPaused -> {
                titleText = getString(R.string.notif_paused)
                contentText = "$startStationName → $endStationName"
            }
            isDwelling -> {
                val arrivedStation = progress?.currentSegmentStartStation?.name ?: startStationName
                titleText = "已到站：$arrivedStation"
                contentText = if (nextStationName.isNotEmpty() && nextStationName != arrivedStation) {
                    "列车停靠中 · 下一站：$nextStationName"
                } else {
                    "列车停靠中 · $startStationName → $endStationName"
                }
            }
            nextStationName == endStationName -> {
                // 终点站前最后一区间（或单段直达）：终点站仅在标题出现，正文不再重复终点站名
                titleText = "前往 $endStationName"
                val fromStation = progress?.currentSegmentStartStation?.name ?: startStationName
                contentText = if (arrivalAnnouncement != null) {
                    arrivalAnnouncement!!
                } else if (speedInt > 0) {
                    "$fromStation 出发 · 时速 $speedInt km/h"
                } else {
                    "$fromStation 出发 · 准点行驶中"
                }
            }
            else -> {
                // 正在前往中途站：标题展示前方到站，正文展示全程线路与时速
                titleText = "前方到站：$nextStationName"
                contentText = if (arrivalAnnouncement != null) {
                    arrivalAnnouncement!!
                } else if (speedInt > 0) {
                    "$startStationName → $endStationName · $speedInt km/h"
                } else {
                    "$startStationName → $endStationName"
                }
            }
        }

        return titleText to contentText
    }

    @RequiresApi(36)
    private fun buildProgressStyleNotification(
        isPaused: Boolean,
        currentSegmentIndex: Int,
        overallProgress: Float
    ): Notification {
        val remaining = timerService.getRemainingSeconds()
        val minutes = remaining / 60
        val seconds = remaining % 60
        val timeLabel = "%02d:%02d".format(minutes, seconds)

        val (titleText, contentText) = buildNotificationContent(isPaused, currentSegmentIndex)
        val progressInt = (overallProgress * 1000).toInt().coerceIn(0, 1000)

        // 启用完整 Android 16 Live Activity 进度条：高亮当前已跑路线、设置移动列车追踪图标、标记沿途车站
        val progressStyle = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(progressInt)
            .setProgressTrackerIcon(Icon.createWithResource(this, R.drawable.ic_bullet_train))

        // 为中途经由车站生成沿途站点进度标记 (Points)，使进度条真正呈现铁路站点分布
        if (pathStations.size > 2) {
            val totalSeg = pathStations.size - 1
            for (i in 1 until pathStations.size - 1) {
                val pointPos = ((i.toFloat() / totalSeg.toFloat()) * 1000).toInt()
                progressStyle.addProgressPoint(
                    Notification.ProgressStyle.Point(pointPos)
                )
            }
        }

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(titleText)
            .setContentText(contentText)
            // 子文本纯净展示应用/旅程名，倒计时由系统 setWhen/setUsesChronometer 在顶部优雅展示，不产生双重时间
            .setSubText(getString(R.string.notif_subtext))
            .setSmallIcon(R.drawable.ic_bullet_train)
            .setStyle(progressStyle)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(createContentPendingIntent())
            .addAction(createNotificationAction(if (isPaused) ACTION_RESUME else ACTION_PAUSE))
            .addAction(createNotificationAction(ACTION_STOP))
            .setWhen(absoluteEndTimeMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(!isPaused)
            .setShortCriticalText(if (isPaused) getString(R.string.notif_paused) else timeLabel)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)

        builder.extras = android.os.Bundle().apply {
            putBoolean("android.requestPromotedOngoing", true)
        }
        return builder.build()
    }

    private fun currentStationLabel(currentSegmentIndex: Int): String {
        if (pathStations.isEmpty()) return ""
        val nextIndex = (currentSegmentIndex + 1).coerceAtMost(pathStations.size - 1)
        return pathStations[nextIndex].name
    }

    private fun buildFallbackNotification(isPaused: Boolean, currentSegmentIndex: Int): Notification {
        val remaining = timerService.getRemainingSeconds()
        val progress = timerService.getCurrentProgress()
        val (titleText, contentText) = buildNotificationContent(isPaused, currentSegmentIndex)

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setSubText(getString(R.string.notif_subtext))
            .setSmallIcon(R.drawable.ic_bullet_train)
            .setOngoing(true)
            .setProgress(1000, ((progress?.overallProgress ?: 0f) * 1000).toInt(), false)
            .setContentIntent(createContentPendingIntent())
            .addAction(createNotificationAction(if (isPaused) ACTION_RESUME else ACTION_PAUSE))
            .addAction(createNotificationAction(ACTION_STOP))
            .build()
    }

    private fun createNotificationAction(action: String): Notification.Action {
        val (iconRes, title) = when (action) {
            ACTION_PAUSE -> android.R.drawable.ic_media_pause to getString(R.string.notif_action_pause)
            ACTION_RESUME -> android.R.drawable.ic_media_play to getString(R.string.notif_action_resume)
            ACTION_STOP -> android.R.drawable.ic_menu_close_clear_cancel to getString(R.string.notif_action_stop)
            else -> R.drawable.ic_bullet_train to action
        }
        val intent = Intent(this, FocusTimerService::class.java).apply { this.action = action }
        return Notification.Action.Builder(
            Icon.createWithResource(this, iconRes),
            title,
            PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        ).build()
    }

    private fun createContentPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private suspend fun completeActiveJourneyInBackground() {
        try {
            val active = journeyRepository.getActiveJourney() ?: return
            val durationMin = active.plannedDurationMin
            val delayMin = timerService.delayMinutes
            val tier = preferencesRepository.frequentFlyerState.first().tier.name
            val completed = completeJourneyUseCase(active.id, durationMin.coerceAtLeast(1), delayMin, tier)
            if (completed != null) {
                preferencesRepository.recordFocusMinutes(durationMin.coerceAtLeast(1))
                pathStations.lastOrNull()?.let { endStation ->
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
        } catch (_: Exception) {}
    }

    private fun showCompletionNotification() {
        // 应用在前台时会直接展示完成卡片，无需再弹通知
        if (isAppInForeground) return
        try {
            val manager = getSystemService(NotificationManager::class.java)
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                NOTIFICATION_ID_COMPLETED,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_completed_title, endStationName))
                .setContentText(getString(R.string.notif_completed_content))
                .setSmallIcon(R.drawable.ic_bullet_train)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            manager.notify(NOTIFICATION_ID_COMPLETED, notification)
        } catch (_: Exception) {}
    }

    private fun acquireWakeLock(timeoutSeconds: Int) {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RailFocus::FocusTimerWakeLock").apply { setReferenceCounted(false); acquire((timeoutSeconds * 1000L) + 60_000L) }
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        // 旅程结束/取消后异步刷新小组件，避免主线程 runBlocking 阻塞产生 ANR
        val appContext = applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {
                val manager = GlanceAppWidgetManager(appContext)
                manager.getGlanceIds(FocusTimerWidget::class.java).forEach { id ->
                    FocusTimerWidget().update(appContext, id)
                }
            }
        }
        abandonAudioFocus()
        runCatching { ambientPlayer?.stop() }
        ambientPlayer?.release()
        ambientPlayer = null
        runCatching { announcementPlayer?.stop() }
        announcementPlayer?.release()
        announcementPlayer = null
        preferenceJob?.cancel()
        announcementClearJob?.cancel()
        observationJobs.forEach { it.cancel() }
        timerService.stop()
        serviceScope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
    }
}
