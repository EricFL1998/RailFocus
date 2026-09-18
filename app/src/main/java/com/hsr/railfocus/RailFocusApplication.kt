package com.hsr.railfocus

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre

@HiltAndroidApp
class RailFocusApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize MapLibre with no API key (using free tile servers)
        MapLibre.getInstance(this)
    }
}
