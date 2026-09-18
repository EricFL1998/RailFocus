package com.hsr.railfocus.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PathResult(
    val path: List<Station>,
    val totalDurationMin: Int,
    val totalDistanceKm: Double,
    val edges: List<PathEdge>,
) {
    @Serializable
    data class PathEdge(
        val from: Station,
        val to: Station,
        val durationMin: Int,
        val lineName: String,
        /** 该段距离 (km)。旧版本存储的 JSON 可能缺少该字段，此时为 0.0，调用方需回退到估算值。 */
        val distanceKm: Double = 0.0,
    )
}
