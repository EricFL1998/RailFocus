package com.hsr.railfocus.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import dagger.hilt.android.qualifiers.ApplicationContext
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
    }

    /**
     * 专注时是否播放车厢环境音
     */
    val ambientSoundEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.AMBIENT_SOUND_ENABLED] ?: false
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
