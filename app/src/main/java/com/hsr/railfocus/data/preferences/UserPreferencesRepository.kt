package com.hsr.railfocus.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import dagger.hilt.android.qualifiers.ApplicationContext
import com.hsr.railfocus.domain.model.FrequentFlyerState
import com.hsr.railfocus.domain.model.MembershipTier
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * 用户偏好存储
 * 
 * 使用 DataStore 持久化：
 * - 上次旅行终点的经纬度和车站信息
 * - 是否首次使用标记
 * - 上次选择的城市
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private object Keys {
        val LAST_LATITUDE = doublePreferencesKey("last_latitude")
        val LAST_LONGITUDE = doublePreferencesKey("last_longitude")
        val LAST_STATION_ID = stringPreferencesKey("last_station_id")
        val LAST_STATION_NAME = stringPreferencesKey("last_station_name")
        val LAST_CITY = stringPreferencesKey("last_city")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PREFERRED_LOCATION_PROVIDER = stringPreferencesKey("preferred_location_provider")
        val DAILY_GOAL_MIN = intPreferencesKey("daily_goal_min")
        val TODAY_FOCUS_MIN = intPreferencesKey("today_focus_min")
        val TODAY_DATE = stringPreferencesKey("today_focus_date")
        val FOCUS_STREAK = intPreferencesKey("focus_streak")
        val LAST_GOAL_DATE = stringPreferencesKey("last_goal_date")
        val AMBIENT_SOUND_ENABLED = booleanPreferencesKey("ambient_sound_enabled")
        val AMBIENT_SOUND_VOLUME = intPreferencesKey("ambient_sound_volume")
        val STATION_ANNOUNCEMENT_ENABLED = booleanPreferencesKey("station_announcement_enabled")
        val KEEP_SCREEN_ON_ENABLED = booleanPreferencesKey("keep_screen_on_enabled")
        val MEMBERSHIP_TIER = stringPreferencesKey("membership_tier")
        val TOTAL_LIFETIME_FOCUS_MIN = intPreferencesKey("total_lifetime_focus_min")
        val LAST_FOCUS_TIMESTAMP = longPreferencesKey("last_focus_timestamp")
    }

    /**
     * 专注旅程进行时是否保持屏幕常亮
     */
    val keepScreenOnEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.KEEP_SCREEN_ON_ENABLED] ?: true
    }

    /**
     * 设置专注常亮开关
     */
    suspend fun setKeepScreenOnEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.KEEP_SCREEN_ON_ENABLED] = enabled
        }
    }

    /**
     * 列车进出站时是否播放站台播报
     */
    val stationAnnouncementEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.STATION_ANNOUNCEMENT_ENABLED] ?: true
    }

    /**
     * 设置站台播报开关
     */
    suspend fun setStationAnnouncementEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.STATION_ANNOUNCEMENT_ENABLED] = enabled
        }
    }

    /**
     * 专注时是否播放车厢环境音（白噪音）
     */
    val ambientSoundEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.AMBIENT_SOUND_ENABLED] ?: true
    }

    /**
     * 设置专注环境音开关
     */
    suspend fun setAmbientSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.AMBIENT_SOUND_ENABLED] = enabled
        }
    }

    /**
     * 白噪音环境音量 (0..100)
     */
    val ambientSoundVolume: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[Keys.AMBIENT_SOUND_VOLUME] ?: 30
    }

    /**
     * 设置白噪音环境音量
     */
    suspend fun setAmbientSoundVolume(volume: Int) {
        val clamped = volume.coerceIn(0, 100)
        context.dataStore.edit { preferences ->
            preferences[Keys.AMBIENT_SOUND_VOLUME] = clamped
        }
    }

    /**
     * 铁道常客俱乐部会员状态（支持航司级定级里程与掉级机制）
     */
    val frequentFlyerState: Flow<FrequentFlyerState> = context.dataStore.data.map { preferences ->
        val totalMinutes = preferences[Keys.TOTAL_LIFETIME_FOCUS_MIN] ?: 0
        val lastTimestamp = preferences[Keys.LAST_FOCUS_TIMESTAMP] ?: 0L
        val rawTierName = preferences[Keys.MEMBERSHIP_TIER] ?: MembershipTier.CLASSIC.name
        val currentTier = try {
            MembershipTier.valueOf(rawTierName)
        } catch (_: Exception) {
            MembershipTier.CLASSIC
        }

        // 计算掉级状态：如果距离上次出行超过保级天数，逐级衰减
        val now = System.currentTimeMillis()
        var evaluatedTier = currentTier
        var daysUntilDowngrade = Int.MAX_VALUE
        var isWarning = false

        if (currentTier != MembershipTier.CLASSIC && lastTimestamp > 0L) {
            val elapsedDays = ((now - lastTimestamp) / (1000L * 3600 * 24)).toInt()
            val validity = currentTier.validityDays
            val remainingDays = validity - elapsedDays

            if (remainingDays <= 0) {
                // 超期降级
                evaluatedTier = currentTier.prevTier
                daysUntilDowngrade = evaluatedTier.validityDays
            } else {
                daysUntilDowngrade = remainingDays
                if (remainingDays <= 7) {
                    isWarning = true
                }
            }
        }

        FrequentFlyerState(
            tier = evaluatedTier,
            totalFocusMinutes = totalMinutes,
            daysUntilDowngrade = daysUntilDowngrade,
            isDowngradeWarning = isWarning,
        )
    }

    /**
     * 每日专注目标与连续打卡状态。
     * 跨天时当日的累计分钟会自动归零显示。
     */
    val dailyGoalState: Flow<DailyGoalState> = context.dataStore.data.map { preferences ->
        val today = LocalDate.now().toString()
        val todayMin = if (preferences[Keys.TODAY_DATE] == today) {
            preferences[Keys.TODAY_FOCUS_MIN] ?: 0
        } else {
            0
        }
        DailyGoalState(
            goalMin = preferences[Keys.DAILY_GOAL_MIN] ?: DEFAULT_DAILY_GOAL_MIN,
            todayFocusMin = todayMin,
            streakDays = preferences[Keys.FOCUS_STREAK] ?: 0,
        )
    }

    /**
     * 设置每日专注目标（分钟）
     */
    suspend fun setDailyGoal(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.DAILY_GOAL_MIN] = minutes
        }
    }

    /**
     * 记录一次已完成的专注时长（分钟）。
     * 只有计入后当日累计达到目标才更新连续天数：
     * 昨天达成过则 +1，否则重置为 1；当天已达成则不变。
     */
    suspend fun recordFocusMinutes(minutes: Int) {
        if (minutes <= 0) return
        val today = LocalDate.now()
        val todayStr = today.toString()
        context.dataStore.edit { preferences ->
            val storedDate = preferences[Keys.TODAY_DATE]
            val base = if (storedDate == todayStr) preferences[Keys.TODAY_FOCUS_MIN] ?: 0 else 0
            val newTotal = base + minutes
            preferences[Keys.TODAY_FOCUS_MIN] = newTotal
            preferences[Keys.TODAY_DATE] = todayStr

            // 累计常客里程与保级刷新
            val currentLifetime = preferences[Keys.TOTAL_LIFETIME_FOCUS_MIN] ?: 0
            val newLifetime = currentLifetime + minutes
            preferences[Keys.TOTAL_LIFETIME_FOCUS_MIN] = newLifetime
            preferences[Keys.LAST_FOCUS_TIMESTAMP] = System.currentTimeMillis()

            // 根据累计有效里程判定是否晋升更高等级
            val currentTier = try {
                MembershipTier.valueOf(preferences[Keys.MEMBERSHIP_TIER] ?: MembershipTier.CLASSIC.name)
            } catch (_: Exception) {
                MembershipTier.CLASSIC
            }
            val possibleTiers = MembershipTier.entries.filter { newLifetime >= it.requiredMinutes }
            val highestEligible = possibleTiers.maxByOrNull { it.requiredMinutes } ?: MembershipTier.CLASSIC
            if (highestEligible.ordinal > currentTier.ordinal) {
                preferences[Keys.MEMBERSHIP_TIER] = highestEligible.name
            }

            val goal = preferences[Keys.DAILY_GOAL_MIN] ?: DEFAULT_DAILY_GOAL_MIN
            if (newTotal >= goal) {
                val lastGoal = preferences[Keys.LAST_GOAL_DATE]
                if (lastGoal != todayStr) {
                    val yesterdayStr = today.minusDays(1).toString()
                    val current = preferences[Keys.FOCUS_STREAK] ?: 0
                    preferences[Keys.FOCUS_STREAK] = if (lastGoal == yesterdayStr) current + 1 else 1
                    preferences[Keys.LAST_GOAL_DATE] = todayStr
                }
            }
        }
    }

    /**
     * 上次保存的位置信息
     */
    val lastLocation: Flow<SavedLocation?> = context.dataStore.data.map { preferences ->
        val lat = preferences[Keys.LAST_LATITUDE]
        val lng = preferences[Keys.LAST_LONGITUDE]
        val stationId = preferences[Keys.LAST_STATION_ID]
        val stationName = preferences[Keys.LAST_STATION_NAME]
        val city = preferences[Keys.LAST_CITY]

        if (((lat != null) && (lng != null) && (stationId != null))) {
            SavedLocation(
                latitude = lat,
                longitude = lng,
                stationId = stationId,
                stationName = stationName ?: "",
                city = city ?: "",
            )
        } else {
            null
        }
    }

    /**
     * 保存当前位置
     */
    suspend fun saveLastLocation(location: SavedLocation) {
        context.dataStore.edit { preferences ->
            preferences[Keys.LAST_LATITUDE] = location.latitude
            preferences[Keys.LAST_LONGITUDE] = location.longitude
            preferences[Keys.LAST_STATION_ID] = location.stationId
            preferences[Keys.LAST_STATION_NAME] = location.stationName
            preferences[Keys.LAST_CITY] = location.city
        }
    }

    /**
     * 清除保存的位置信息（恢复为首次使用状态）
     */
    suspend fun clearLastLocation() {
        context.dataStore.edit { preferences ->
            preferences.remove(Keys.LAST_LATITUDE)
            preferences.remove(Keys.LAST_LONGITUDE)
            preferences.remove(Keys.LAST_STATION_ID)
            preferences.remove(Keys.LAST_STATION_NAME)
            preferences.remove(Keys.LAST_CITY)
        }
    }

    /**
     * 当前主题模式
     */
    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[Keys.THEME_MODE] ?: "auto"
    }

    /**
     * 设置主题模式
     */
    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.THEME_MODE] = mode
        }
    }

    /**
     * 偏好的定位提供商 ("gms", "native", "auto")
     */
    val preferredLocationProvider: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[Keys.PREFERRED_LOCATION_PROVIDER] ?: "auto"
    }

    /**
     * 设置偏好的定位提供商
     */
    suspend fun setPreferredLocationProvider(provider: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.PREFERRED_LOCATION_PROVIDER] = provider
        }
    }
}

/** 每日专注目标默认 45 分钟 */
const val DEFAULT_DAILY_GOAL_MIN = 45

/**
 * 每日专注目标状态
 */
data class DailyGoalState(
    val goalMin: Int,
    val todayFocusMin: Int,
    val streakDays: Int,
)

/**
 * 保存的位置信息
 */
data class SavedLocation(
    val latitude: Double,
    val longitude: Double,
    val stationId: String,
    val stationName: String,
    val city: String,
)
