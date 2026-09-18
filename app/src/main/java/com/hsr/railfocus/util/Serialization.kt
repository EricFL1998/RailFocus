package com.hsr.railfocus.util

import kotlinx.serialization.json.Json

/**
 * 应用统一的 JSON 序列化配置。
 *
 * - [Json.ignoreUnknownKeys]：兼容旧版本（Gson 时代）写入的 JSON 中的多余字段。
 * - [Json.coerceInputValues]：字段缺失或非法时回退到属性默认值，而不是抛出异常。
 * - [Json.encodeDefaults]：写出默认值，保证新旧版本读取一致。
 */
val appJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}
