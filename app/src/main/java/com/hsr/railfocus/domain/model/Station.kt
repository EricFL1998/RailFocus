package com.hsr.railfocus.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Station(
    val id: String = "",
    val name: String = "",
    val displayName: String = "",
    val province: String = "",
    val city: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val isMajor: Boolean = false,
    val tier: Int = 3,
) {
    companion object {
        val DEFAULT = Station(
            id = "南京南",
            name = "南京南",
            displayName = "南京南站",
            province = "江苏省",
            city = "南京",
            lat = 31.9728,
            lng = 118.8047,
            isMajor = true,
            tier = 2
        )
    }
}
