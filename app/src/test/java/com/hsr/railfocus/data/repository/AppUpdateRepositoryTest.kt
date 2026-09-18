package com.hsr.railfocus.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryTest {

    private val repository = AppUpdateRepository()

    @Test
    fun remoteNewerPatchVersion_isNewer() {
        assertTrue(repository.isNewerVersion("1.1", "1.0"))
    }

    @Test
    fun remoteNewerMultiSegment_isNewer() {
        assertTrue(repository.isNewerVersion("1.0.1", "1.0"))
        assertTrue(repository.isNewerVersion("2.0", "1.9"))
        assertTrue(repository.isNewerVersion("1.10", "1.9"))
    }

    @Test
    fun leadingVPrefix_isIgnored() {
        assertTrue(repository.isNewerVersion("v1.1", "1.0"))
        assertTrue(repository.isNewerVersion("1.1", "v1.0"))
    }

    @Test
    fun sameVersion_isNotNewer() {
        assertFalse(repository.isNewerVersion("1.0", "1.0"))
        assertFalse(repository.isNewerVersion("1.0", "1.0.0"))
        assertFalse(repository.isNewerVersion("v1.0", "1.0"))
    }

    @Test
    fun remoteOlderVersion_isNotNewer() {
        assertFalse(repository.isNewerVersion("0.9", "1.0"))
        assertFalse(repository.isNewerVersion("1.0", "1.0.1"))
    }

    @Test
    fun unparseableVersion_isNotNewer() {
        assertFalse(repository.isNewerVersion("", "1.0"))
        assertFalse(repository.isNewerVersion("abc", "1.0"))
        assertFalse(repository.isNewerVersion("1.1", ""))
        assertFalse(repository.isNewerVersion("1.1", "abc"))
    }

    @Test
    fun prereleaseSuffix_isParsedAsBaseVersion() {
        // "1.1-beta" 解析为 1.1，仍应比 1.0 新；"1.0-beta" 不比 1.0 新
        assertTrue(repository.isNewerVersion("1.1-beta", "1.0"))
        assertFalse(repository.isNewerVersion("1.0-beta", "1.0"))
    }
}
