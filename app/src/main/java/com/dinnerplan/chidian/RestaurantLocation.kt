package com.dinnerplan.chidian

import com.dinnerplan.shared.LocationDto
import java.util.Locale

internal fun restaurantLocationForSearch(
    locationText: String,
    latitude: Double?,
    longitude: Double?
): LocationDto {
    return if (latitude != null && longitude != null) {
        LocationDto(latitude = latitude, longitude = longitude, text = locationText.ifBlank { coordinateDisplayLabel(latitude, longitude) })
    } else {
        LocationDto(text = locationText)
    }
}

internal fun coordinateDisplayLabel(latitude: Double, longitude: Double): String {
    return "经纬度：${String.format(Locale.US, "%.6f", latitude)}, ${String.format(Locale.US, "%.6f", longitude)}"
}

internal fun isLikelyAmapSearchCoordinate(latitude: Double, longitude: Double): Boolean {
    return latitude in 18.0..54.0 && longitude in 73.0..136.0
}

internal fun unsupportedAmapLocationMessage(latitude: Double, longitude: Double): String {
    logInternalIssue("Unsupported Amap search coordinate", coordinateDisplayLabel(latitude, longitude))
    return "暂时无法获取当前位置，你也可以手动输入地点。"
}
