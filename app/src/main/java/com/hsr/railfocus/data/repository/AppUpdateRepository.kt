package com.hsr.railfocus.data.repository

import com.hsr.railfocus.util.appJson
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * 应用更新检查器
 *
 * 通过 GitHub Releases API 查询最新发布版本，与当前安装版本比较。
 * 发布 tag 约定为 "1.0" 或 "v1.0" 形式，忽略前导 v/V。
 */
@Singleton
class AppUpdateRepository @Inject constructor() {

    /**
     * 检查是否有新版本可用。
     *
     * @param currentVersion 当前应用版本名（如 "1.0"）
     */
    suspend fun checkForUpdate(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "RailFocus-Android")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                connection.disconnect()
                return@withContext UpdateCheckResult.NoRelease
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return@withContext UpdateCheckResult.Failure("HTTP $responseCode")
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val release = appJson.decodeFromString<GitHubReleaseDto>(responseBody)
            val remoteVersion = release.tag_name.trim().removePrefix("v").removePrefix("V")
            if (remoteVersion.isEmpty() || !isNewerVersion(remoteVersion, currentVersion)) {
                UpdateCheckResult.UpToDate
            } else {
                UpdateCheckResult.UpdateAvailable(
                    AppUpdateInfo(
                        version = remoteVersion,
                        releaseNotes = release.body.trim(),
                        releaseUrl = release.html_url,
                        downloadUrl = release.assets
                            .firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                            ?.browser_download_url,
                    )
                )
            }
        } catch (e: Exception) {
            UpdateCheckResult.Failure(e.message)
        }
    }

    /**
     * 远程版本是否比当前版本新。按数字段逐段比较（如 1.10 > 1.9）。
     * 任一方无法解析时保守返回 false（不提示更新）。
     */
    internal fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = parseVersion(remote) ?: return false
        val currentParts = parseVersion(current) ?: return false
        val size = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until size) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }

    /**
     * 把 "1.2.3" / "v1.2" / "1.0-beta" 之类的版本号解析为数字段列表。
     * 遇到第一个非数字段（如 -beta）即截断；完全无法解析返回 null。
     */
    private fun parseVersion(version: String): List<Int>? {
        val cleaned = version.trim().removePrefix("v").removePrefix("V")
        if (cleaned.isEmpty()) return null
        val numbers = mutableListOf<Int>()
        for (part in cleaned.split('.', '-', '_')) {
            if (part.isEmpty()) continue
            val digits = part.takeWhile { it.isDigit() }
            if (digits.isEmpty()) break
            numbers.add(digits.toIntOrNull() ?: return null)
        }
        return numbers.ifEmpty { null }
    }

    @Serializable
    private data class GitHubReleaseDto(
        val tag_name: String = "",
        val body: String = "",
        val html_url: String = "",
        val assets: List<Asset> = emptyList(),
    ) {
        @Serializable
        data class Asset(
            val name: String = "",
            val browser_download_url: String = "",
        )
    }

    private companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/EricFL1998/RailFocus/releases/latest"
    }
}

/**
 * 最新版本信息。
 */
data class AppUpdateInfo(
    val version: String,
    val releaseNotes: String,
    val releaseUrl: String,
    /** 发布中第一个 APK 资产的直链；没有则为 null，回退到 release 页面。 */
    val downloadUrl: String?,
)

/**
 * 更新检查结果。
 */
sealed interface UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult
    object UpToDate : UpdateCheckResult
    object NoRelease : UpdateCheckResult
    data class Failure(val message: String?) : UpdateCheckResult
}
