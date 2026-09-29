package com.hsr.railfocus.domain.service

import com.hsr.railfocus.data.repository.WeatherRepository
import com.hsr.railfocus.domain.model.WeatherInfo
import com.hsr.railfocus.util.GeoGrid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 城市切换追踪器：回答"列车开到哪里算进入了另一个城市"。
 *
 * 判定模型（在线）：
 * 列车行进中持续上报 GPS 坐标，本追踪器把坐标映射到 0.1° 地理网格（约 11km）。
 * 只有跨越网格边界时才重新解析归属地——通过小米 geo 反向查询，
 * 由服务端权威行政区边界数据判定该坐标属于哪个区县、哪个地级市。
 * 当返回的地级市名与当前城市不一致时，即判定"进入了另一个城市"，
 * 触发点为跨网格后的第一次成功解析位置（误差在一个网格宽度以内，约 11km）。
 *
 * 设计取舍：
 * - 天气精度为区县级（geo 返回区县级气象站），跨县即换新站点数据；
 *   但"进入另一个城市"只按地级市名变化判定，避免市内跨县时误报。
 * - 网格粒度天然平滑了行政区边界附近的来回抖动：
 *   高铁穿过一个网格需要约两分钟，不会在边界上高频翻转。
 * - 弱网/无网时（高铁常见）解析失败，保持上一次城市与天气状态不变（粘性），
 *   等网络恢复后的下一次跨网格再判定，绝不把状态清空为"未知"。
 * - 旅程开始时调用 [reset] 清除上一段旅程的状态。
 */
@Singleton
class CityTransitionTracker @Inject constructor(
    private val weatherRepository: WeatherRepository,
) {
    companion object {
        /** 前瞻探测距离（公里）：约合高铁 2 分钟车程，即减速入站/边界预警距离 */
        const val LOOKAHEAD_DISTANCE_KM = 10.0
    }

    /** 一次"进入另一个城市"事件 */
    data class CityTransition(
        /** 离开的地级市名 */
        val fromCity: String,
        /** 进入的地级市名 */
        val toCity: String,
        /** 新城市的当前天气（切换判定同时拿到的，无需再请求） */
        val weather: WeatherInfo,
        /** 判定发生时的坐标 */
        val latitude: Double,
        val longitude: Double,
    )

    /** 当前所在网格键（上一次成功解析的网格） */
    private var lastGridKey: String? = null

    /** 当前判定的地级市名；null 表示尚未成功解析过 */
    var currentCity: String? = null
        private set

    /** 当前判定的区县名 */
    var currentArea: String? = null
        private set

    /** 当前地点的最新天气；null 表示尚未成功获取过 */
    var currentWeather: WeatherInfo? = null
        private set

    private val _currentCityFlow = MutableStateFlow<String?>(null)
    val currentCityFlow: StateFlow<String?> = _currentCityFlow.asStateFlow()

    private val _currentWeatherFlow = MutableStateFlow<WeatherInfo?>(null)
    val currentWeatherFlow: StateFlow<WeatherInfo?> = _currentWeatherFlow.asStateFlow()

    /** 记录已经针对其做过边界前瞻预取的网格，避免对同一个前方边界重复触发请求 */
    private val prefetchedAheadGrids = mutableSetOf<String>()

    /**
     * 前瞻检测：检测前方约 10km 处是否快要跨越城市或县的边界。
     * 若快要跨越且该新区域尚未预取，拉取并缓存前方新区域的天气。
     */
    suspend fun checkApproachingBoundary(
        currentLat: Double,
        currentLon: Double,
        aheadLat: Double,
        aheadLon: Double,
    ): WeatherInfo? {
        val curGrid = GeoGrid.keyOf(currentLat, currentLon)
        val aheadGrid = GeoGrid.keyOf(aheadLat, aheadLon)

        // 1. 前瞻点仍在当前网格，说明距离边界还远
        if (aheadGrid == curGrid) return null

        // 2. 该前方网格已针对本次跨界预取过，不重复请求
        if (prefetchedAheadGrids.contains(aheadGrid)) return null

        // 3. 确保当前基准位置的归属已建立
        if (currentCity == null) {
            weatherRepository.getWeather(currentLat, currentLon)?.let { curWeather ->
                currentCity = curWeather.cityName.ifBlank { null }
                currentArea = curWeather.areaName.ifBlank { null }
                currentWeather = curWeather
                _currentCityFlow.value = currentCity
                _currentWeatherFlow.value = curWeather
            }
        }

        // 4. 查询前瞻点所属区域
        val aheadWeather = weatherRepository.getWeather(aheadLat, aheadLon) ?: return null

        val isDifferentCity = !aheadWeather.cityName.isNullOrBlank() &&
                currentCity != null &&
                aheadWeather.cityName != currentCity

        val isDifferentArea = !aheadWeather.areaName.isNullOrBlank() &&
                currentArea != null &&
                aheadWeather.areaName != currentArea

        // 5. 探测到前方快要跨越市界或县界 -> 标记预取完成并返回天气
        if (isDifferentCity || isDifferentArea) {
            prefetchedAheadGrids.add(aheadGrid)
            return aheadWeather
        }

        return null
    }

    /**
     * 旅程位置更新。
     *
     * @return 若本次更新判定为"进入了另一个城市"，返回切换事件；否则返回 null。
     *         同一网格内的重复上报、网络失败、归属地未变化时均返回 null。
     */
    suspend fun onLocationUpdate(latitude: Double, longitude: Double): CityTransition? {
        // 网格去抖：网格没变就不做任何 IO（GPS 可能每几秒上报一次）
        val gridKey = GeoGrid.keyOf(latitude, longitude)
        if (gridKey == lastGridKey) return null

        // 跨网格：解析新位置的天气与归属（内部有 30 分钟/7 天缓存，成本可控）
        val weather = weatherRepository.getWeather(latitude, longitude)
            ?: return null // 网络失败且无缓存：保持旧状态（粘性），不更新 lastGridKey，下个点重试

        lastGridKey = gridKey
        val previousCity = currentCity
        currentWeather = weather
        _currentWeatherFlow.value = weather

        val newCity = weather.cityName
        val newArea = weather.areaName
        if (newCity.isNotBlank()) {
            currentCity = newCity
            _currentCityFlow.value = newCity
        }
        if (newArea.isNotBlank()) {
            currentArea = newArea
        }

        return if (previousCity != null && newCity.isNotBlank() && previousCity != newCity) {
            CityTransition(
                fromCity = previousCity,
                toCity = newCity,
                weather = weather,
                latitude = latitude,
                longitude = longitude,
            )
        } else {
            null
        }
    }

    /** 旅程开始/结束时调用，清除上一段旅程的状态 */
    fun reset() {
        lastGridKey = null
        currentCity = null
        currentArea = null
        currentWeather = null
        _currentCityFlow.value = null
        _currentWeatherFlow.value = null
        prefetchedAheadGrids.clear()
    }
}
