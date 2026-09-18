package com.hsr.railfocus.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 邻接边实体 - 对应预置数据库中的 edges 表
 * 包含站点间的邻接关系，用于路径规划
 */
@Entity(
    tableName = "edges",
    indices = [
        Index(value = ["from_id"], name = "idx_edges_from_id"),
        Index(value = ["to_id"], name = "idx_edges_to_id"),
        Index(value = ["from_id", "to_id"], name = "idx_edges_from_to"),
    ],
)
data class EdgeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "from_id")
    val fromStationId: String,

    @ColumnInfo(name = "from_name")
    val fromName: String? = null,

    @ColumnInfo(name = "to_id")
    val toStationId: String,

    @ColumnInfo(name = "to_name")
    val toName: String? = null,

    @ColumnInfo(name = "line")
    val lineName: String? = null,

    @ColumnInfo(name = "distance_km")
    val distanceKm: Double? = null,

    @ColumnInfo(name = "source")
    val source: String? = null,
)
