package com.hsr.railfocus.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "visited_station_records",
    indices = [Index(value = ["stationId"], unique = false)],
)
data class VisitedStationRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val stationId: String,
    val journeyId: String,
    val visitedAt: Long = System.currentTimeMillis(),
)
