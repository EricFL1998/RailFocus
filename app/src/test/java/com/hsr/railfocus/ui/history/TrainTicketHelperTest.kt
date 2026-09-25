package com.hsr.railfocus.ui.history

import com.hsr.railfocus.domain.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainTicketHelperTest {

    @Test
    fun `determineTrainSeries assigns correct series by duration and type`() {
        val (cSeries, cPrefix, cSpeed) = TrainTicketHelper.determineTrainSeries(25)
        assertEquals(TrainSeries.C_SERIES, cSeries)
        assertEquals("C", cPrefix)
        assertEquals(200, cSpeed)

        val (dSeries, dPrefix, dSpeed) = TrainTicketHelper.determineTrainSeries(45)
        assertEquals(TrainSeries.D_SERIES, dSeries)
        assertEquals("D", dPrefix)
        assertEquals(250, dSpeed)

        val (gSeries, gPrefix, gSpeed) = TrainTicketHelper.determineTrainSeries(90)
        assertEquals(TrainSeries.G_SERIES, gSeries)
        assertEquals("G", gPrefix)
        assertEquals(350, gSpeed)

        val (sleeperSeries, sleeperPrefix, sleeperSpeed) = TrainTicketHelper.determineTrainSeries(90, focusType = "夜行卧铺")
        assertEquals(TrainSeries.SLEEPER, sleeperSeries)
        assertEquals("D", sleeperPrefix)
        assertEquals(250, sleeperSpeed)
    }

    @Test
    fun `synthesizeTrainNumber is consistent and starts with prefix`() {
        val start = Station.DEFAULT
        val end = Station.DEFAULT.copy(id = "SHANGHAI_HONGQIAO")
        val trainNum1 = TrainTicketHelper.synthesizeTrainNumber("G", start, end, 60)
        val trainNum2 = TrainTicketHelper.synthesizeTrainNumber("G", start, end, 60)
        assertEquals(trainNum1, trainNum2)
        assertTrue(trainNum1.startsWith("G"))
    }
}

