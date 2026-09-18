package com.hsr.railfocus.data.local.dataaccess

import androidx.room.Dao
import androidx.room.Query
import com.hsr.railfocus.data.local.entity.StationEntity

@Dao
interface StationDataAccess {
    @Query("SELECT * FROM stations ORDER BY tier ASC, name ASC")
    suspend fun getAll(): List<StationEntity>

    @Query("SELECT * FROM stations WHERE id = :id")
    suspend fun getById(id: String): StationEntity?

    @Query("SELECT * FROM stations WHERE name LIKE '%' || :query || '%' OR display_name LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<StationEntity>

    @Query("SELECT * FROM stations WHERE id IN (:ids)")
    suspend fun getStationsByIds(ids: List<String>): List<StationEntity>
}
