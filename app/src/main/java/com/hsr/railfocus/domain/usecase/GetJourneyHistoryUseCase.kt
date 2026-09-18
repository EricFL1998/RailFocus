package com.hsr.railfocus.domain.usecase

import com.hsr.railfocus.data.repository.JourneyRepository
import com.hsr.railfocus.domain.model.JourneyRecord
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetJourneyHistoryUseCase @Inject constructor(
    private val journeyRepository: JourneyRepository,
) {
    operator fun invoke(): Flow<List<JourneyRecord>> {
        return journeyRepository.getJourneyHistoryFlow()
    }
}
