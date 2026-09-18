package com.hsr.railfocus.util

/**
 * 省份名称格式化工具
 * 将数据库中的简称转换为全称。
 */
object ProvinceFormatter {
    private val MUNICIPALITIES = setOf("北京", "上海", "天津", "重庆")
    private val SARS = setOf("香港", "澳门")
    
    private val AUTONOMOUS_REGIONS = mapOf(
        "内蒙古" to "内蒙古自治区",
        "西藏" to "西藏自治区",
        "广西" to "广西壮族自治区",
        "宁夏" to "宁夏回族自治区",
        "新疆" to "新疆维吾尔自治区"
    )

    /**
     * 格式化省份名称
     * @param province 数据库中的省份字段
     * @param city 数据库中的城市字段（用于辅助，不进行硬编码映射）
     */
    fun format(province: String?, city: String? = null): String {
        val raw = province?.trim() ?: ""
        
        if (raw.isEmpty()) return ""
        
        // 如果已经是全称，直接返回
        if (raw.endsWith("省") || 
            raw.endsWith("市") || 
            raw.endsWith("自治区") || 
            raw.endsWith("特别行政区")) {
            return raw
        }

        // 处理简称并补全
        return when {
            MUNICIPALITIES.contains(raw) -> "${raw}市"
            AUTONOMOUS_REGIONS.containsKey(raw) -> AUTONOMOUS_REGIONS[raw]!!
            SARS.contains(raw) -> "${raw}特别行政区"
            else -> "${raw}省"
        }
    }
}
