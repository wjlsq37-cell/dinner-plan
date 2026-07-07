package com.dinnerplan.chidian

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

internal data class RestaurantNavigationRequest(
    val name: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?
)

internal data class MapNavigationTargets(
    val amapUri: String
)

internal sealed interface MapNavigationResult {
    data object Opened : MapNavigationResult
    data object MissingDestination : MapNavigationResult
    data class Failed(val message: String) : MapNavigationResult
}

internal fun buildNavigationTargets(request: RestaurantNavigationRequest): MapNavigationTargets {
    val encodedName = urlEncode(request.name.ifBlank { "目的地" })
    val hasCoordinate = request.latitude != null && request.longitude != null
    return if (hasCoordinate) {
        val lat = request.latitude!!
        val lng = request.longitude!!
        MapNavigationTargets(
            amapUri = "androidamap://route/plan/?sourceApplication=吃点啥&dlat=$lat&dlon=$lng&dname=$encodedName&dev=0&t=0"
        )
    } else {
        val query = listOf(request.address, request.name)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
        MapNavigationTargets(
            amapUri = "androidamap://poi?sourceApplication=吃点啥&keywords=${urlEncode(query)}&dev=0"
        )
    }
}

internal fun openMapNavigation(context: Context, request: RestaurantNavigationRequest): MapNavigationResult {
    if ((request.latitude == null || request.longitude == null) && request.address.isBlank() && request.name.isBlank()) {
        return MapNavigationResult.MissingDestination
    }
    val targets = buildNavigationTargets(request)
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targets.amapUri))
        .setPackage("com.autonavi.minimap")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent.resolveActivity(context.packageManager) == null) {
        return MapNavigationResult.Failed("未安装或无法打开高德地图。")
    }
    return try {
        context.startActivity(intent)
        MapNavigationResult.Opened
    } catch (error: ActivityNotFoundException) {
        logInternalIssue("Amap navigation ActivityNotFoundException", error.message, error)
        MapNavigationResult.Failed("未安装或无法打开高德地图。")
    }
}

private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
