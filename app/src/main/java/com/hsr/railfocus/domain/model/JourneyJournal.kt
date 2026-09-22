package com.hsr.railfocus.domain.model

import kotlinx.serialization.Serializable

/**
 * 旅行手账领域模型
 */
@Serializable
data class JourneyJournal(
    val id: String,
    val journeyId: String,
    val stationId: String,
    val stationName: String,
    val content: String,
    val imagePaths: List<String> = emptyList(),
    val audioPath: String? = null,
    val audioDurationSec: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

