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
    return "当前定位 ${coordinateDisplayLabel(latitude, longitude)} 不在高德大陆 POI 常规覆盖范围内。模拟器请在 Extended Controls > Location 中设置中国大陆坐标，例如杭州 30.256000, 120.205000 或上海 31.230400, 121.473700。"
}
