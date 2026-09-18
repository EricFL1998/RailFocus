package com.hsr.railfocus.domain.model

import com.hsr.railfocus.util.appJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PathResult 序列化测试
 *
 * 验证 kotlinx.serialization 的往返一致性，
 * 以及与应用历史上 Gson 写入的旧版 JSON 的向后兼容性。
 */
class PathResultSerializationTest {

    private val stationA = Station(
        id = "nanjingnan",
        name = "南京南",
        displayName = "南京南站",
        province = "江苏省",
        city = "南京",
        lat = 31.9728,
        lng = 118.8047,
        isMajor = true,
        tier = 2,
    )

    private val stationB = Station(
        id = "zhenjiangnan",
        name = "镇江南",
        displayName = "镇江南站",
        province = "江苏省",
        city = "镇江",
        lat = 32.2083,
        lng = 119.4345,
        isMajor = false,
        tier = 3,
    )

    private fun samplePath(): PathResult {
        val edge = PathResult.PathEdge(
            from = stationA,
            to = stationB,
            durationMin = 21,
            lineName = "",
            distanceKm = 74.0,
        )
        return PathResult(
            path = listOf(stationA, stationB),
            totalDurationMin = 21,
            totalDistanceKm = 74.0,
            edges = listOf(edge),
        )
    }

    @Test
    fun roundTrip_preservesAllFields() {
        val path = samplePath()
        val restored = appJson.decodeFromString<PathResult>(appJson.encodeToString(path))
        assertEquals(path, restored)
    }

    @Test
    fun legacyGsonJson_withoutDistanceKm_defaultsToZero() {
        // 旧版本 Gson 写出的 JSON：PathEdge 没有 distanceKm 字段
        val legacyJson = """
            {
              "path": [
                {"id":"nanjingnan","name":"南京南","displayName":"南京南站","province":"江苏省","city":"南京","lat":31.9728,"lng":118.8047,"isMajor":true,"tier":2},
                {"id":"zhenjiangnan","name":"镇江南","displayName":"镇江南站","province":"江苏省","city":"镇江","lat":32.2083,"lng":119.4345,"isMajor":false,"tier":3}
              ],
              "totalDurationMin": 21,
              "totalDistanceKm": 74.0,
              "edges": [
                {"from":{"id":"nanjingnan","name":"南京南","displayName":"南京南站","province":"江苏省","city":"南京","lat":31.9728,"lng":118.8047,"isMajor":true,"tier":2},
                 "to":{"id":"zhenjiangnan","name":"镇江南","displayName":"镇江南站","province":"江苏省","city":"镇江","lat":32.2083,"lng":119.4345,"isMajor":false,"tier":3},
                 "durationMin":21,"lineName":""}
              ]
            }
        """.trimIndent()

        val path = appJson.decodeFromString<PathResult>(legacyJson)

        assertEquals(2, path.path.size)
        assertEquals(74.0, path.totalDistanceKm, 0.0001)
        assertEquals(1, path.edges.size)
        assertEquals(0.0, path.edges[0].distanceKm, 0.0001)
        // 其余字段完整还原
        assertEquals("南京南", path.edges[0].from.name)
        assertEquals(21, path.edges[0].durationMin)
    }

    @Test
    fun unknownKeys_areIgnored() {
        val jsonWithExtra = """
            {
              "path": [],
              "totalDurationMin": 0,
              "totalDistanceKm": 0.0,
              "edges": [],
              "futureField": {"anything": true}
            }
        """.trimIndent()

        val path = appJson.decodeFromString<PathResult>(jsonWithExtra)
        assertTrue(path.path.isEmpty())
    }
}
