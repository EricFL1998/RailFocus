package com.hsr.railfocus.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stations",
    indices = [
        Index(value = ["name"], name = "idx_stations_name"),
        Index(value = ["province"], name = "idx_stations_province"),
    ],
)
data class StationEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "display_name")
    val displayName: String?,
    val province: String?,
    val city: String?,
    val lat: Double?,
    val lon: Double?,
    @ColumnInfo(name = "is_major", defaultValue = "0")
    val isMajor: Boolean?,
    @ColumnInfo(defaultValue = "3")
    val tier: Int?,
)
