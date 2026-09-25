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
    @Volatile
    private var cachedStations: List<Station>? = null

    suspend fun getAllStations(): List<Station> = withContext(Dispatchers.IO) {
        cachedStations ?: stationDataAccess.getAll().map { it.toDomain() }.also { cachedStations = it }
    }

    suspend fun searchStations(query: String): List<Station> = withContext(Dispatchers.IO) {
        stationDataAccess.search(query).map { it.toDomain() }
    }
}
