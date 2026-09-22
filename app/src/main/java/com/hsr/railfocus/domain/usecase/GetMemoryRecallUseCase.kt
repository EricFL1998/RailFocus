package com.hsr.railfocus.domain.usecase

import com.hsr.railfocus.data.repository.JournalRepository
import com.hsr.railfocus.domain.model.MemoryRecall
import com.hsr.railfocus.domain.model.RecallType
import java.util.Calendar
import javax.inject.Inject

/**
 * “那年今日 · 车站旧忆”唤醒判定 UseCase
 *
 * 1. 刚好当天（整年同月同日）：展示当年完整手账图文；
 * 2. 未来 1~3 天内（即将迎来当年的纪念日）：表达提前抵站与未来相遇的期许；
 * 3. 其它历史情况（非当天）：表达对过去停留的错过与回忆。
 */
class GetMemoryRecallUseCase @Inject constructor(
    private val journalRepository: JournalRepository,
) {
    suspend operator fun invoke(
        stationId: String,
        currentJourneyId: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): MemoryRecall? {
        val allJournals = journalRepository.getByStationId(stationId)
        val candidates = allJournals.filter { it.journeyId != currentJourneyId }
        if (candidates.isEmpty()) return null

        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val nowYear = nowCal.get(Calendar.YEAR)
        val nowDayOfYear = nowCal.get(Calendar.DAY_OF_YEAR)

        // 1. 优先判定：刚好当天（整年同一天）
        for (journal in candidates) {
            val jCal = Calendar.getInstance().apply { timeInMillis = journal.createdAt }
            val jYear = jCal.get(Calendar.YEAR)
            val jMonth = jCal.get(Calendar.MONTH)
            val jDay = jCal.get(Calendar.DAY_OF_MONTH)
            val yearDiff = nowYear - jYear

            if (yearDiff >= 1) {
                val anniversaryCal = (nowCal.clone() as Calendar).apply {
                    set(Calendar.MONTH, jMonth)
                    set(Calendar.DAY_OF_MONTH, jDay)
                }
                val diffDays = anniversaryCal.get(Calendar.DAY_OF_YEAR) - nowDayOfYear

                if (diffDays == 0) {
                    return MemoryRecall(
                        type = RecallType.SAME_DAY_YEARS_AGO,
                        journal = journal,
                        yearsAgo = yearDiff,
                        daysAgo = ((nowMillis - journal.createdAt) / (1000L * 60 * 60 * 24)).toInt(),
                    )
                }
            }
        }

        // 2. 次优先判定：未来 1~3 天内即将迎来当年的纪念日
        for (journal in candidates) {
            val jCal = Calendar.getInstance().apply { timeInMillis = journal.createdAt }
            val jYear = jCal.get(Calendar.YEAR)
            val jMonth = jCal.get(Calendar.MONTH)
            val jDay = jCal.get(Calendar.DAY_OF_MONTH)
            val yearDiff = nowYear - jYear

            if (yearDiff >= 1) {
                val anniversaryCal = (nowCal.clone() as Calendar).apply {
                    set(Calendar.MONTH, jMonth)
                    set(Calendar.DAY_OF_MONTH, jDay)
                }
                val diffDays = anniversaryCal.get(Calendar.DAY_OF_YEAR) - nowDayOfYear

                if (diffDays in 1..3) {
                    val futureMessage = if (diffDays == 1) {
                        "明天，是你曾在这里停留的日子。提前抵达，期待相逢。"
                    } else {
                        "${diffDays}天后，是你曾在这里停留的日子。提前抵达，期待相逢。"
                    }
                    return MemoryRecall(
                        type = RecallType.UPCOMING_DAYS,
                        journal = journal,
                        yearsAgo = yearDiff,
                        daysUntil = diffDays,
                        message = futureMessage,
                    )
                }
            }
        }

        // 3. 非当天情况：取最近一次历史手账（至少 1 天前），表达对过去停留的错过与回忆
        val latestHistory = candidates.firstOrNull() ?: return null
        val daysAgo = ((nowMillis - latestHistory.createdAt) / (1000L * 60 * 60 * 24)).toInt()
        if (daysAgo < 1) {
            return null
        }

        val missedMessage = "${daysAgo}天前，你曾在这里停留。错过的风景，期待未来重逢。"
        return MemoryRecall(
            type = RecallType.REVISIT,
            journal = latestHistory,
            daysAgo = daysAgo,
            message = missedMessage,
        )
    }
}

