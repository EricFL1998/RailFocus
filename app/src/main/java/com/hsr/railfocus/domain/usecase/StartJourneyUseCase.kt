package com.hsr.railfocus.domain.usecase

import com.hsr.railfocus.data.repository.JourneyRepository
import com.hsr.railfocus.domain.model.JourneyRecord
import com.hsr.railfocus.domain.model.PathResult
import com.hsr.railfocus.domain.model.Station
import java.util.UUID
import javax.inject.Inject

class StartJourneyUseCase @Inject constructor(
    private val journeyRepository: JourneyRepository,
) {
    suspend operator fun invoke(
        startStation: Station,
        endStation: Station,
        path: PathResult,
        plannedDurationMin: Int,
        actualDurationMin: Int,
        status: com.hsr.railfocus.domain.model.JourneyStatus = com.hsr.railfocus.domain.model.JourneyStatus.COMPLETED,
        focusType: String? = null,
        seatNumber: String? = null,
        carriageNumber: String? = null,
        completedAt: Long? = null,
        delayMinutes: Int = 0,
    ): JourneyRecord {
        val createdAt = System.currentTimeMillis()
        val actualCompletedAt = completedAt ?: if (status == com.hsr.railfocus.domain.model.JourneyStatus.COMPLETED) {
            // 兼容：如果未传完成时间，按实际专注时长推算
            createdAt + (actualDurationMin * 60 * 1000L)
        } else null
        val record = JourneyRecord(
            id = UUID.randomUUID().toString(),
            startStation = startStation,
            endStation = endStation,
            plannedDurationMin = plannedDurationMin,
            actualDurationMin = actualDurationMin,
            path = path,
            createdAt = createdAt,
            completedAt = actualCompletedAt,
            status = status,
            focusType = focusType,
            seatNumber = seatNumber,
            carriageNumber = carriageNumber,
            delayMinutes = delayMinutes,
        )
        journeyRepository.saveJourney(record)
        return record
    }
}
