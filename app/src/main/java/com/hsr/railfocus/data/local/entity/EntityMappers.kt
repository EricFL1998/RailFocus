package com.hsr.railfocus.data.local.entity

import com.hsr.railfocus.domain.model.JourneyRecord
import com.hsr.railfocus.domain.model.PathResult
import com.hsr.railfocus.domain.model.Station
import com.hsr.railfocus.util.ProvinceFormatter

fun StationEntity.toDomain(): Station {
    return Station(
        id = id,
        name = name,
        displayName = displayName ?: name,
        province = ProvinceFormatter.format(province, city),
        city = city ?: "",
        lat = lat ?: 0.0,
        lng = lon ?: 0.0,
        isMajor = isMajor ?: false,
        tier = tier ?: 3,
    )
}

fun StationEntity.toDomainModel(): Station {
    return toDomain()
}

fun JourneyRecordEntity.toDomain(
    startStation: Station,
    endStation: Station,
    path: PathResult,
): JourneyRecord {
    return JourneyRecord(
        id = id,
        startStation = startStation,
        endStation = endStation,
        plannedDurationMin = plannedDurationMin,
        actualDurationMin = actualDurationMin,
        path = path,
        createdAt = createdAt,
        completedAt = completedAt,
        remainingSec = remainingSec,
        status = try {
            com.hsr.railfocus.domain.model.JourneyStatus.valueOf(status)
        } catch (_: Exception) {
            if (completedAt != null) com.hsr.railfocus.domain.model.JourneyStatus.COMPLETED
            else com.hsr.railfocus.domain.model.JourneyStatus.CANCELLED
        },
        focusType = focusType,
        seatNumber = seatNumber,
        carriageNumber = carriageNumber,
        delayMinutes = delayMinutes,
        earnedTier = earnedTier,
    )
}

fun JourneyRecord.toEntity(pathJson: String): JourneyRecordEntity {
    return JourneyRecordEntity(
        id = id,
        startStationId = startStation.id,
        endStationId = endStation.id,
        plannedDurationMin = plannedDurationMin,
        actualDurationMin = actualDurationMin,
        pathJson = pathJson,
        createdAt = createdAt,
        completedAt = completedAt,
        remainingSec = remainingSec,
        status = status.name,
        focusType = focusType,
        seatNumber = seatNumber,
        carriageNumber = carriageNumber,
        delayMinutes = delayMinutes,
        earnedTier = earnedTier,
    )
}
