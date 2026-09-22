package com.hsr.railfocus.domain.model

import androidx.annotation.StringRes
import com.hsr.railfocus.R

/**
 * 铁道常客俱乐部会员等级（模拟航空常客金银卡体系，具备定级里程与保级有效期）
 */
enum class MembershipTier(
    @StringRes val titleRes: Int,
    val enTitle: String,
    val requiredMinutes: Int,    // 升级至此等级所需的累计有效专注里程（分钟）
    val validityDays: Int,       // 保级有效期（超期未专注则降级）
) {
    CLASSIC(R.string.club_tier_classic, "CLASSIC", 0, Int.MAX_VALUE),
    SILVER(R.string.club_tier_silver, "SILVER", 1200, 30),
    GOLD(R.string.club_tier_gold, "GOLD", 4500, 60),
    PLATINUM(R.string.club_tier_platinum, "PLATINUM", 12000, 120),
    DIAMOND(R.string.club_tier_diamond, "DIAMOND ELITE", 30000, 365);

    val nextTier: MembershipTier?
        get() = when (this) {
            CLASSIC -> SILVER
            SILVER -> GOLD
            GOLD -> PLATINUM
            PLATINUM -> DIAMOND
            DIAMOND -> null
        }

    val prevTier: MembershipTier
        get() = when (this) {
            DIAMOND -> PLATINUM
            PLATINUM -> GOLD
            GOLD -> SILVER
            SILVER -> CLASSIC
            CLASSIC -> CLASSIC
        }
}

/**
 * 常客卡完整状态
 */
data class FrequentFlyerState(
    val tier: MembershipTier = MembershipTier.CLASSIC,
    val totalFocusMinutes: Int = 0,
    val daysUntilDowngrade: Int = Int.MAX_VALUE,
    val isDowngradeWarning: Boolean = false,
)

