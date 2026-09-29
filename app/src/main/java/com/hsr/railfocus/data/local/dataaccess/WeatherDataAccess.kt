package com.hsr.railfocus.data.local.dataaccess

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hsr.railfocus.data.local.entity.WeatherCacheEntity

/**
 * 天气与归属地缓存数据访问接口
 */
@Dao
interface WeatherDataAccess {

    @Query("SELECT * FROM weather_cache WHERE gridKey = :gridKey")
    suspend fun getByGrid(gridKey: String): WeatherCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WeatherCacheEntity)

    @Query("DELETE FROM weather_cache")
    suspend fun deleteAll()
}
