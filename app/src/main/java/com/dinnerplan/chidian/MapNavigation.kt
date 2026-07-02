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
    val amapUri: String?,
    val geoUri: String,
    val browserUri: String
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
            amapUri = "androidamap://route/plan/?sourceApplication=吃点啥&dlat=$lat&dlon=$lng&dname=$encodedName&dev=0&t=0",
            geoUri = "geo:$lat,$lng?q=$lat,$lng($encodedName)",
            browserUri = "https://uri.amap.com/marker?position=$lng,$lat&name=$encodedName"
        )
    } else {
        val query = listOf(request.address, request.name)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
        val encodedQuery = urlEncode(query)
        MapNavigationTargets(
            amapUri = null,
            geoUri = "geo:0,0?q=$encodedQuery",
            browserUri = "https://uri.amap.com/search?keyword=$encodedQuery"
        )
    }
}

internal fun openMapNavigation(context: Context, request: RestaurantNavigationRequest): MapNavigationResult {
    if ((request.latitude == null || request.longitude == null) && request.address.isBlank()) {
        return MapNavigationResult.MissingDestination
    }
    val targets = buildNavigationTargets(request)
    val attempts = buildList {
        targets.amapUri?.let { add(Intent(Intent.ACTION_VIEW, Uri.parse(it)).setPackage("com.autonavi.minimap")) }
        add(Intent(Intent.ACTION_VIEW, Uri.parse(targets.geoUri)))
        add(Intent(Intent.ACTION_VIEW, Uri.parse(targets.browserUri)))
    }
    attempts.forEach { intent ->
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) != null) {
            return try {
                context.startActivity(intent)
                MapNavigationResult.Opened
            } catch (error: ActivityNotFoundException) {
                MapNavigationResult.Failed(error.message ?: "没有可用地图应用。")
            }
        }
    }
    return MapNavigationResult.Failed("没有找到可用的地图应用或浏览器。")
}

private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
