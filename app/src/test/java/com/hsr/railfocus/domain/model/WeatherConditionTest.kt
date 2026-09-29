package com.hsr.railfocus.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherConditionTest {

    @Test
    fun clearCloudyOvercast_mapping() {
        assertEquals(WeatherCondition.CLEAR, WeatherCondition.fromCnCode(0))
        assertEquals(WeatherCondition.CLOUDY, WeatherCondition.fromCnCode(1))
        assertEquals(WeatherCondition.OVERCAST, WeatherCondition.fromCnCode(2))
    }

    @Test
    fun rainCodes_mapping() {
        // 参考小米天气分组：3/7/19/21 小雨，8/22 中雨，9/23 大雨，10~12/24/25 暴雨
        assertEquals(WeatherCondition.LIGHT_RAIN, WeatherCondition.fromCnCode(3))
        assertEquals(WeatherCondition.LIGHT_RAIN, WeatherCondition.fromCnCode(7))
        assertEquals(WeatherCondition.LIGHT_RAIN, WeatherCondition.fromCnCode(21))
        assertEquals(WeatherCondition.MODERATE_RAIN, WeatherCondition.fromCnCode(8))
        assertEquals(WeatherCondition.MODERATE_RAIN, WeatherCondition.fromCnCode(22))
        assertEquals(WeatherCondition.HEAVY_RAIN, WeatherCondition.fromCnCode(9))
        assertEquals(WeatherCondition.STORM_RAIN, WeatherCondition.fromCnCode(10))
        assertEquals(WeatherCondition.STORM_RAIN, WeatherCondition.fromCnCode(11))
        assertEquals(WeatherCondition.STORM_RAIN, WeatherCondition.fromCnCode(25))
    }

    @Test
    fun thunderAndSleet_mapping() {
        assertEquals(WeatherCondition.THUNDER_SHOWER, WeatherCondition.fromCnCode(4))
        assertEquals(WeatherCondition.THUNDER_SHOWER, WeatherCondition.fromCnCode(5))
        // 6 为雨夹雪，应独立于雪
        assertEquals(WeatherCondition.SLEET, WeatherCondition.fromCnCode(6))
    }

    @Test
    fun snowCodes_mapping() {
        assertEquals(WeatherCondition.LIGHT_SNOW, WeatherCondition.fromCnCode(13))
        assertEquals(WeatherCondition.LIGHT_SNOW, WeatherCondition.fromCnCode(14))
        assertEquals(WeatherCondition.LIGHT_SNOW, WeatherCondition.fromCnCode(26))
        assertEquals(WeatherCondition.LIGHT_SNOW, WeatherCondition.fromCnCode(27))
        assertEquals(WeatherCondition.MODERATE_SNOW, WeatherCondition.fromCnCode(15))
        assertEquals(WeatherCondition.MODERATE_SNOW, WeatherCondition.fromCnCode(28))
        assertEquals(WeatherCondition.HEAVY_SNOW, WeatherCondition.fromCnCode(16))
        assertEquals(WeatherCondition.STORM_SNOW, WeatherCondition.fromCnCode(17))
    }

    @Test
    fun fogHazeDustSand_mapping() {
        assertEquals(WeatherCondition.LIGHT_FOG, WeatherCondition.fromCnCode(18))
        assertEquals(WeatherCondition.HEAVY_FOG, WeatherCondition.fromCnCode(32))
        assertEquals(WeatherCondition.MODERATE_FOG, WeatherCondition.fromCnCode(57))
        assertEquals(WeatherCondition.HEAVY_FOG, WeatherCondition.fromCnCode(58))
        assertEquals(WeatherCondition.DUST, WeatherCondition.fromCnCode(29))
        assertEquals(WeatherCondition.SAND, WeatherCondition.fromCnCode(20))
        assertEquals(WeatherCondition.SAND, WeatherCondition.fromCnCode(30))
        assertEquals(WeatherCondition.SAND, WeatherCondition.fromCnCode(31))
        assertEquals(WeatherCondition.LIGHT_HAZE, WeatherCondition.fromCnCode(53))
        assertEquals(WeatherCondition.MODERATE_HAZE, WeatherCondition.fromCnCode(54))
        assertEquals(WeatherCondition.HEAVY_HAZE, WeatherCondition.fromCnCode(55))
        assertEquals(WeatherCondition.HEAVY_HAZE, WeatherCondition.fromCnCode(56))
    }

    @Test
    fun unknownCode_mapping() {
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromCnCode(-1))
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromCnCode(99))
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromCnCode(52))
    }

    @Test
    fun precipitationFlag() {
        assertTrue(WeatherCondition.LIGHT_RAIN.isPrecipitation)
        assertTrue(WeatherCondition.STORM_RAIN.isPrecipitation)
        assertTrue(WeatherCondition.THUNDER_SHOWER.isPrecipitation)
        assertTrue(WeatherCondition.SLEET.isPrecipitation)
        assertTrue(WeatherCondition.LIGHT_SNOW.isPrecipitation)
        assertTrue(WeatherCondition.STORM_SNOW.isPrecipitation)
        assertFalse(WeatherCondition.CLEAR.isPrecipitation)
        assertFalse(WeatherCondition.CLOUDY.isPrecipitation)
        assertFalse(WeatherCondition.FOG.isPrecipitation)
        assertFalse(WeatherCondition.HAZE.isPrecipitation)
    }

    @Test
    fun weatherInfo_conditionAndSummary() {
        val info = WeatherInfo(
            locationKey = "weathercn:101180109",
            areaName = "中原",
            cityName = "郑州",
            weatherCode = 7,
            temperatureC = 21.4,
            fetchedAt = 1000L,
        )
        assertEquals(WeatherCondition.LIGHT_RAIN, info.condition)
        assertEquals("小雨", info.detailedLabel)
        assertEquals("小雨 21°", info.summary)

        val stormInfo = info.copy(weatherCode = 11)
        assertEquals(WeatherCondition.STORM_RAIN, stormInfo.condition)
        assertEquals("大暴雨", stormInfo.detailedLabel)
    }
}
