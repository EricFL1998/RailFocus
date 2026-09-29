package com.hsr.railfocus.domain.service

import com.hsr.railfocus.data.preferences.UserPreferencesRepository
import com.hsr.railfocus.domain.model.WeatherCondition
import com.hsr.railfocus.domain.model.WeatherInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局活动天气管理器。
 *
 * 统一协调当前 App 界面应该生效的天气效果：
 * - 旅程进行中：使用列车沿途实时探测并跨城更新的天气 [journeyWeather]；
 * - 旅程未开始/已结束（首页）：使用当前所在车站的天气 [homeWeather]；
 * - 遵从设置开关：若用户在设置中关闭了“天气显示”开关，则清空天气，不触发任何动效。
 */
@Singleton
class WeatherManager @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _homeWeather = MutableStateFlow<WeatherInfo?>(null)
    val homeWeather: StateFlow<WeatherInfo?> = _homeWeather.asStateFlow()

    private val _journeyWeather = MutableStateFlow<WeatherInfo?>(null)
    val journeyWeather: StateFlow<WeatherInfo?> = _journeyWeather.asStateFlow()

    private val _isJourneyActive = MutableStateFlow(false)
    val isJourneyActive: StateFlow<Boolean> = _isJourneyActive.asStateFlow()

    /**
     * 当前生效的天气信息
     */
    val activeWeather: StateFlow<WeatherInfo?> = combine(
        _homeWeather,
        _journeyWeather,
        _isJourneyActive,
        preferencesRepository.weatherDisplayEnabled,
    ) { home, journey, journeyActive, enabled ->
        if (!enabled) null
        else if (journeyActive && journey != null) journey
        else home
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    /**
     * 当前界面生效的天气现象（驱动粒子动效）
     */
    val activeCondition: StateFlow<WeatherCondition?> = combine(
        activeWeather,
        preferencesRepository.weatherDisplayEnabled,
    ) { weather, enabled ->
        if (!enabled) null else weather?.condition
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    fun setHomeWeather(weather: WeatherInfo?) {
        _homeWeather.value = weather
    }

    fun setJourneyWeather(weather: WeatherInfo?) {
        _journeyWeather.value = weather
    }

    fun setJourneyActive(active: Boolean) {
        _isJourneyActive.value = active
        if (!active) {
            _journeyWeather.value = null
        }
    }
}
