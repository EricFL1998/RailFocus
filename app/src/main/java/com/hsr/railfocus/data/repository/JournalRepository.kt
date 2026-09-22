package com.hsr.railfocus.data.repository

import com.hsr.railfocus.data.local.dataaccess.JournalDataAccess
import com.hsr.railfocus.data.local.entity.JourneyJournalEntity
import com.hsr.railfocus.domain.model.JourneyJournal
import com.hsr.railfocus.util.appJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalRepository @Inject constructor(
    private val journalDataAccess: JournalDataAccess,
) {
    suspend fun saveJournal(
        journeyId: String,
        stationId: String,
        stationName: String,
        content: String,
        imagePaths: List<String> = emptyList(),
        audioPath: String? = null,
        audioDurationSec: Int = 0,
    ): JourneyJournal {
        val existing = journalDataAccess.getByJourneyId(journeyId)
        val now = System.currentTimeMillis()
        val entity = if (existing != null) {
            val updated = existing.copy(
                content = content,
                imagePathsJson = appJson.encodeToString(imagePaths),
                audioPath = audioPath ?: existing.audioPath,
                audioDurationSec = if (audioDurationSec > 0) audioDurationSec else existing.audioDurationSec,
                updatedAt = now,
            )
            journalDataAccess.update(updated)
            updated
        } else {
            val newEntity = JourneyJournalEntity(
                id = UUID.randomUUID().toString(),
                journeyId = journeyId,
                stationId = stationId,
                stationName = stationName,
                content = content,
                imagePathsJson = appJson.encodeToString(imagePaths),
                audioPath = audioPath,
                audioDurationSec = audioDurationSec,
                createdAt = now,
                updatedAt = now,
            )
            journalDataAccess.insert(newEntity)
            newEntity
        }
        return entity.toDomain()
    }

    suspend fun getByJourneyId(journeyId: String): JourneyJournal? {
        return journalDataAccess.getByJourneyId(journeyId)?.toDomain()
    }

    fun getByJourneyIdFlow(journeyId: String): Flow<JourneyJournal?> {
        return journalDataAccess.getByJourneyIdFlow(journeyId).map { it?.toDomain() }
    }

    suspend fun getByStationId(stationId: String): List<JourneyJournal> {
        return journalDataAccess.getByStationId(stationId).map { it.toDomain() }
    }

    fun getAllFlow(): Flow<List<JourneyJournal>> {
        return journalDataAccess.getAllFlow().map { list -> list.map { it.toDomain() } }
    }

    suspend fun deleteById(id: String) {
        journalDataAccess.deleteById(id)
    }

    private fun JourneyJournalEntity.toDomain(): JourneyJournal {
        val paths: List<String> = try {
            appJson.decodeFromString(imagePathsJson)
        } catch (_: Exception) {
            emptyList()
        }
        return JourneyJournal(
            id = id,
            journeyId = journeyId,
            stationId = stationId,
            stationName = stationName,
            content = content,
            imagePaths = paths,
            audioPath = audioPath,
            audioDurationSec = audioDurationSec,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}

