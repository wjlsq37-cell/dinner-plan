package com.dinnerplan.chidian

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.Geocoder
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

internal sealed interface DeviceLocationResult {
    data class Found(
        val latitude: Double,
        val longitude: Double,
        val source: String
    ) : DeviceLocationResult

    data class Unavailable(
        val message: String
    ) : DeviceLocationResult
}

@SuppressLint("MissingPermission")
internal suspend fun getCurrentDeviceLocation(context: Context): DeviceLocationResult {
    if (!hasLocationPermission(context)) {
        return DeviceLocationResult.Unavailable("没有定位权限，请允许定位后重试。")
    }
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return DeviceLocationResult.Unavailable("系统定位服务暂不可用。")
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter { provider -> runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false) }
    if (providers.isEmpty()) {
        return DeviceLocationResult.Unavailable("手机定位服务未开启，请先打开系统定位。")
    }

    val lastKnown = providers
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull(Location::getTime)
    if (lastKnown != null) {
        return lastKnown.toDeviceLocationResult()
    }

    val fresh = withTimeoutOrNull(10_000) {
        suspendCancellableCoroutine<Location?> { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                    runCatching { manager.removeUpdates(this) }
                }
            }
            providers.forEach { provider ->
                runCatching { manager.requestSingleUpdate(provider, listener, Looper.getMainLooper()) }
            }
            continuation.invokeOnCancellation {
                runCatching { manager.removeUpdates(listener) }
            }
        }
    }

    return fresh?.toDeviceLocationResult()
        ?: DeviceLocationResult.Unavailable("定位超时，请稍后重试或手动输入位置。")
}

private fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

private fun Location.toDeviceLocationResult(): DeviceLocationResult.Found {
    return DeviceLocationResult.Found(
        latitude = latitude,
        longitude = longitude,
        source = provider.orEmpty().ifBlank { "系统定位" }
    )
}

internal suspend fun resolveDeviceLocationLabel(
    context: Context,
    latitude: Double,
    longitude: Double
): String {
    return withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            val address = Geocoder(context, Locale.getDefault())
                .getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
                ?: return@runCatching ""
            address.getAddressLine(0).orEmpty().ifBlank {
                listOf(address.locality, address.subLocality, address.thoroughfare, address.featureName)
                    .mapNotNull { it?.takeIf(String::isNotBlank) }
                    .distinct()
                    .joinToString(" ")
            }
        }.getOrDefault("").ifBlank {
            coordinateDisplayLabel(latitude, longitude)
        }
    }
}
