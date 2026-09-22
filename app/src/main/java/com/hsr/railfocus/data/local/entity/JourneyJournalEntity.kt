package com.hsr.railfocus.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "journey_journals",
    foreignKeys = [
        ForeignKey(
            entity = JourneyRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["journeyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["journeyId"], unique = true),
        Index(value = ["stationId"]),
        Index(value = ["createdAt"])
    ]
)
data class JourneyJournalEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val journeyId: String,
    val stationId: String,
    val stationName: String,
    val content: String,
    val imagePathsJson: String = "[]",
    val audioPath: String? = null,
    val audioDurationSec: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

