package com.hsr.railfocus.util

import org.maplibre.android.geometry.LatLngBounds

/**
 * 中国地图可视范围与缓存范围。
 * 覆盖中国大陆、台湾、港澳以及少量周边缓冲区域。
 */
object ChinaBounds {
    val bounds: LatLngBounds = LatLngBounds.from(
        /* latNorth = */ 54.0,
        /* lonEast = */ 136.0,
        /* latSouth = */ 18.0,
        /* lonWest = */ 73.0,
    )
}
