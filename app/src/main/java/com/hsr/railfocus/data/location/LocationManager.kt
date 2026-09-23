package com.hsr.railfocus.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LocationManager - 管理GPS位置服务
 * 
 * 功能：
 * - 获取当前位置 (支持 GMS 和原生 Android 兜底)
 * - 计算距离
 */
@Singleton
class LocationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * 定位结果封装
     */
    data class LocationResult(
        val location: Location,
        val provider: String // "gms" 或 "native"
    )

    /**
     * 检查位置权限
     */
    fun hasLocationPermission(): Boolean {
        return ((ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED) ||
                (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED))
    }

    /**
     * 获取位置，允许指定偏好的提供商以跳过等待
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(preferredProvider: String = "auto"): LocationResult? {
        if (!hasLocationPermission()) {
            return null
        }

        // 1. 如果偏好是 native，直接尝试原生
        if (preferredProvider == "native") {
            getNativeLocation()?.let { return LocationResult(it, "native") }
        }

        // 2. 尝试使用 Google Play Services (GMS)，除非明确指定只用 native
        if (preferredProvider != "native") {
            val gmsLocation = try {
                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setDurationMillis(if (preferredProvider == "gms") 10000 else 4000)
                    .setMaxUpdateAgeMillis(60000)
                    .build()

                fusedLocationClient.getCurrentLocation(request, null).await()
            } catch (_: Exception) {
                null
            }
            if (gmsLocation != null) return LocationResult(gmsLocation, "gms")
        }

        // 3. 如果 GMS 失败且还没试过 native，尝试原生兜底
        if (preferredProvider != "native") {
            getNativeLocation()?.let { return LocationResult(it, "native") }
        }

        return null
    }

    /**
     * 原生 Android 定位兜底方案（包含单次短时监听重试，解决冷启动缓存为空问题）
     */
    @SuppressLint("MissingPermission")
    private suspend fun getNativeLocation(): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        
        val cached = try {
            val providers = lm.getProviders(true)
            var bestLocation: Location? = null
            
            for (provider in providers) {
                val lastKnown = lm.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || lastKnown.time > bestLocation.time) {
                    bestLocation = lastKnown
                }
            }
            bestLocation
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }

        if (cached != null) return cached

        return try {
            kotlinx.coroutines.withTimeoutOrNull(3000L) {
                kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                    val listener = object : android.location.LocationListener {
                        override fun onLocationChanged(loc: Location) {
                            try { lm.removeUpdates(this) } catch (_: Exception) {}
                            if (cont.isActive) cont.resumeWith(Result.success(loc))
                        }
                        override fun onProviderDisabled(provider: String) {}
                        override fun onProviderEnabled(provider: String) {}
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    }
                    cont.invokeOnCancellation {
                        try { lm.removeUpdates(listener) } catch (_: Exception) {}
                    }
                    try {
                        val provider = when {
                            lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ->
                                android.location.LocationManager.GPS_PROVIDER
                            lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) ->
                                android.location.LocationManager.NETWORK_PROVIDER
                            else -> lm.getProviders(true).firstOrNull()
                        }
                        if (provider != null) {
                            lm.requestSingleUpdate(provider, listener, android.os.Looper.getMainLooper())
                        } else {
                            if (cont.isActive) cont.resumeWith(Result.success(null))
                        }
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resumeWith(Result.success(null))
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 计算两个位置之间的距离（米）
     */
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
}
