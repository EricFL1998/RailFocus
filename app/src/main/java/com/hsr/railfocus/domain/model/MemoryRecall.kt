package com.hsr.railfocus.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RecallType {
    SAME_DAY_YEARS_AGO, // 刚好当天（严格同月同日，整年）：展示手账图文
    UPCOMING_DAYS,      // 未来 1~3 天内即将到期：表达对未来的期待与提前抵达的重逢
    REVISIT             // 过去/非当天：表达对过去的停留与错过的感慨
}

data class MemoryRecall(
    val type: RecallType,
    val journal: JourneyJournal,
    val yearsAgo: Int = 0,
    val daysAgo: Int = 0,
    val daysUntil: Int = 0,
    val message: String = "",
    val formattedDateTime: String = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault()).format(Date(journal.createdAt)),
    val formattedDate: String = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(Date(journal.createdAt)),
)

