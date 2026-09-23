package com.hsr.railfocus.domain.usecase

import com.hsr.railfocus.data.repository.JourneyRepository
import com.hsr.railfocus.domain.model.JourneyRecord
import javax.inject.Inject

/**
 * 完成旅程 UseCase
 * 
 * 记录旅程完成时间和实际时长，更新访问记录。原子排他，防止前后台同时结算导致时长翻倍。
 */
class CompleteJourneyUseCase @Inject constructor(
    private val journeyRepository: JourneyRepository,
) {
    /**
     * 完成旅程
     * 
     * @param journeyId 旅程ID
     * @param actualDurationMin 实际时长（分钟）
     * @return 成功完成则返回最新记录；若该旅程已被其他流程完成/取消则返回 null
     */
    suspend operator fun invoke(
        journeyId: String,
        actualDurationMin: Int,
        delayMinutes: Int = 0,
        earnedTier: String? = null,
    ): JourneyRecord? {
        return try {
            val success = journeyRepository.completeJourney(
                journeyId = journeyId,
                actualDurationMin = actualDurationMin,
                delayMinutes = delayMinutes,
                earnedTier = earnedTier,
            )
            if (success) {
                journeyRepository.getJourneyById(journeyId)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
