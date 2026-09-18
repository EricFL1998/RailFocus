package com.hsr.railfocus.domain.usecase

import com.hsr.railfocus.data.repository.JourneyRepository
import javax.inject.Inject

/**
 * 取消旅程 UseCase
 * 
 * 取消正在进行或暂停的旅程，不记录到访问历史
 */
class CancelJourneyUseCase @Inject constructor(
    private val journeyRepository: JourneyRepository,
) {
    /**
     * 取消旅程
     * 
     * @param journeyId 旅程ID
     * @param actualDurationMin 取消时已专注的分钟数
     * @return true表示成功，false表示失败
     */
    suspend operator fun invoke(journeyId: String, actualDurationMin: Int = 0): Boolean {
        return try {
            // 1. 获取旅程
            val journey = journeyRepository.getJourneyById(journeyId)
                ?: return false
            
            // 2. 验证状态（已完成的旅程不能取消）
            if (journey.completedAt != null) {
                return false
            }
            
            // 3. 取消旅程
            journeyRepository.cancelJourney(journeyId, actualDurationMin)
            true
        } catch (_: Exception) {
            false
        }
    }
}
