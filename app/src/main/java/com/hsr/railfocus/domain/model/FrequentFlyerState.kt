package com.hsr.railfocus.domain.model

/**
 * 铁道常客俱乐部会员等级（模拟航空常客金银卡体系，具备定级里程与保级有效期）
 */
enum class MembershipTier(
    val title: String,
    val enTitle: String,
    val requiredMinutes: Int,    // 升级至此等级所需的累计有效专注里程（分钟）
    val validityDays: Int,       // 保级有效期（超期未专注则降级）
) {
    CLASSIC("经典会员", "CLASSIC", 0, Int.MAX_VALUE),
    SILVER("银卡会员", "SILVER", 1200, 30),         // 20小时(约2~3周持续专注)，保级30天
    GOLD("金卡会员", "GOLD", 4500, 60),             // 75小时(约1~2个月深度自律)，保级60天
    PLATINUM("白金卡会员", "PLATINUM", 12000, 120), // 200小时(近半年硬核沉淀)，保级120天
    DIAMOND("星空黑卡", "DIAMOND ELITE", 30000, 365); // 500小时(铁杆铁道常客)，保级365天(整年)

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

