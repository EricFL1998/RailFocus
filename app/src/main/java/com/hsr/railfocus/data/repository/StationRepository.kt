package com.hsr.railfocus.data.repository

import com.hsr.railfocus.data.local.dataaccess.StationDataAccess
import com.hsr.railfocus.data.local.entity.toDomain
import com.hsr.railfocus.domain.model.Station
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StationRepository @Inject constructor(
    private val stationDataAccess: StationDataAccess,
) {
    suspend fun getAllStations(): List<Station> = withContext(Dispatchers.IO) {
        stationDataAccess.getAll().map { it.toDomain() }
    }

    suspend fun searchStations(query: String): List<Station> = withContext(Dispatchers.IO) {
        stationDataAccess.search(query).map { it.toDomain() }
    }
}
