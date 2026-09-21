package com.hsr.railfocus.domain.usecase

import com.hsr.railfocus.data.repository.JourneyRepository
import com.hsr.railfocus.domain.model.JourneyRecord
import javax.inject.Inject

/**
 * 完成旅程 UseCase
 * 
 * 记录旅程完成时间和实际时长，更新访问记录
 */
class CompleteJourneyUseCase @Inject constructor(
    private val journeyRepository: JourneyRepository,
) {
    /**
     * 完成旅程
     * 
     * @param journeyId 旅程ID
     * @param actualDurationMin 实际时长（分钟）
     * @return 完成的旅程记录，如果失败则返回null
     */
    suspend operator fun invoke(
        journeyId: String,
        actualDurationMin: Int,
        delayMinutes: Int = 0,
    ): JourneyRecord? {
        return try {
            // 1. 获取旅程记录
            val journey = journeyRepository.getJourneyById(journeyId)
                ?: return null
            
            // 2. 验证状态（已完成的旅程不能再完成）
            if (journey.completedAt != null) {
                return null
            }
            
            // 3. 完成旅程
            journeyRepository.completeJourney(journeyId, actualDurationMin, delayMinutes)
            
            // 4. 返回更新后的记录
            journeyRepository.getJourneyById(journeyId)
        } catch (_: Exception) {
            null
        }
    }
}
