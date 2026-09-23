package com.hsr.railfocus.data.local.dataaccess

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hsr.railfocus.data.local.entity.VisitedStationRecordEntity

@Dao
interface VisitedStationDataAccess {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: VisitedStationRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<VisitedStationRecordEntity>)

    @Query("SELECT DISTINCT stationId FROM visited_station_records")
    suspend fun getAllVisitedStationIds(): List<String>

    @Query("DELETE FROM visited_station_records")
    suspend fun deleteAll()
}
