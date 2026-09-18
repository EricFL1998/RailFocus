package com.hsr.railfocus.data.graph

import com.hsr.railfocus.data.local.dataaccess.EdgeDataAccess
import com.hsr.railfocus.data.local.dataaccess.StationDataAccess
import com.hsr.railfocus.data.local.entity.EdgeEntity
import com.hsr.railfocus.data.local.entity.StationEntity
import com.hsr.railfocus.domain.model.journey.TrainSpeedModel
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 验证 RailGraph 使用真实高铁速度模型和途经站停站时间计算路径。
 */
class RailGraphTest {

    private val stationDataAccess: StationDataAccess = mockk()
    private val edgeDataAccess: EdgeDataAccess = mockk()
    private lateinit var railGraph: RailGraph

    @Before
    fun setup() {
        clearAllMocks()
        railGraph = RailGraph(stationDataAccess, edgeDataAccess)
    }

    @Test
    fun `最快路径 - 包含中间站停站时间`() = runTest {
        // A -> B -> C，A 是起点，B 是途经站，C 是终点
        val edges = listOf(
            EdgeEntity(fromStationId = "A", toStationId = "B", distanceKm = 100.0),
            EdgeEntity(fromStationId = "B", toStationId = "C", distanceKm = 50.0),
        )
        val stations = listOf(
            StationEntity(id = "A", name = "A", displayName = "A", province = "", city = "", lat = 0.0, lon = 0.0, isMajor = false, tier = 3),
            StationEntity(id = "B", name = "B", displayName = "B", province = "", city = "", lat = 0.0, lon = 0.0, isMajor = false, tier = 3),
            StationEntity(id = "C", name = "C", displayName = "C", province = "", city = "", lat = 0.0, lon = 0.0, isMajor = false, tier = 3),
        )

        coEvery { edgeDataAccess.getAllEdges() } returns edges
        coEvery { stationDataAccess.getStationsByIds(any()) } returns stations

        val result = railGraph.findFastestPath("A", "C")
        assertNotNull(result)

        val speedModel = TrainSpeedModel()
        val abTime = speedModel.computeTravelTimeMinutes(100f)
        val bcTime = speedModel.computeTravelTimeMinutes(50f)
        val expectedTotal = abTime + bcTime + RailGraph.INTERMEDIATE_DWELL_MIN

        assertEquals(expectedTotal, result!!.totalDurationMin)
        assertEquals(150.0, result.totalDistanceKm, 0.0)
    }

    @Test
    fun `直达路径 - 不包含停站时间`() = runTest {
        val edges = listOf(
            EdgeEntity(fromStationId = "A", toStationId = "B", distanceKm = 100.0),
        )
        val stations = listOf(
            StationEntity(id = "A", name = "A", displayName = "A", province = "", city = "", lat = 0.0, lon = 0.0, isMajor = false, tier = 3),
            StationEntity(id = "B", name = "B", displayName = "B", province = "", city = "", lat = 0.0, lon = 0.0, isMajor = false, tier = 3),
        )

        coEvery { edgeDataAccess.getAllEdges() } returns edges
        coEvery { stationDataAccess.getStationsByIds(any()) } returns stations

        val result = railGraph.findFastestPath("A", "B")
        assertNotNull(result)

        val speedModel = TrainSpeedModel()
        val expectedTime = speedModel.computeTravelTimeMinutes(100f)

        assertEquals(expectedTime, result!!.totalDurationMin)
    }

    @Test
    fun `可达站点 - 在合理时长内找到目的地`() = runTest {
        // A -> B 距离 100 km，真实运行时间约 22 分钟；给 30 分钟应可达。
        val edges = listOf(
            EdgeEntity(fromStationId = "A", toStationId = "B", distanceKm = 100.0),
        )
        val stations = listOf(
            StationEntity(id = "A", name = "A", displayName = "A", province = "", city = "", lat = 0.0, lon = 0.0, isMajor = false, tier = 3),
            StationEntity(id = "B", name = "B", displayName = "B", province = "", city = "", lat = 0.0, lon = 0.0, isMajor = false, tier = 3),
        )

        coEvery { edgeDataAccess.getAllEdges() } returns edges
        coEvery { stationDataAccess.getStationsByIds(any()) } returns stations

        val reachable = railGraph.findStationsByDuration("A", durationMin = 30, tolerance = 10)
        assertTrue("100km 在 30±10 分钟内应可达 B", reachable.any { it.id == "B" })
    }

    @Test
    fun `可达站点 - 时间不足时无目的地`() = runTest {
        // A -> B 距离 100 km，真实运行时间约 22 分钟；给 5 分钟应不可达。
        val edges = listOf(
            EdgeEntity(fromStationId = "A", toStationId = "B", distanceKm = 100.0),
        )
        val stations = listOf(
            StationEntity(id = "A", name = "A", displayName = "A", province = "", city = "", lat = 0.0, lon = 0.0, isMajor = false, tier = 3),
            StationEntity(id = "B", name = "B", displayName = "B", province = "", city = "", lat = 0.0, lon = 0.0, isMajor = false, tier = 3),
        )

        coEvery { edgeDataAccess.getAllEdges() } returns edges
        coEvery { stationDataAccess.getStationsByIds(any()) } returns stations

        val reachable = railGraph.findStationsByDuration("A", durationMin = 5, tolerance = 0)
        // 5 分钟时只有起点 A 自身在 [5,5] 范围内；B 需要约 22 分钟，不可达
        assertTrue("100km 在 5 分钟内不可达 B", reachable.none { it.id == "B" })
        assertTrue("起点 A 不应被作为目的地返回", reachable.none { it.id == "A" })
    }
}
