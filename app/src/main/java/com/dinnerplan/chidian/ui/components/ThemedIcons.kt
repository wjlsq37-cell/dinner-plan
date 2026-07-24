package com.dinnerplan.chidian.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack as RoundedArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.rounded.Add as RoundedAdd
import androidx.compose.material.icons.rounded.Apps as RoundedApps
import androidx.compose.material.icons.rounded.AutoAwesome as RoundedAutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight as RoundedChevronRight
import androidx.compose.material.icons.rounded.Close as RoundedClose
import androidx.compose.material.icons.rounded.CheckCircle as RoundedCheckCircle
import androidx.compose.material.icons.rounded.Delete as RoundedDelete
import androidx.compose.material.icons.rounded.ExpandLess as RoundedExpandLess
import androidx.compose.material.icons.rounded.ExpandMore as RoundedExpandMore
import androidx.compose.material.icons.rounded.Favorite as RoundedFavorite
import androidx.compose.material.icons.rounded.FavoriteBorder as RoundedFavoriteBorder
import androidx.compose.material.icons.rounded.Home as RoundedHome
import androidx.compose.material.icons.rounded.Info as RoundedInfo
import androidx.compose.material.icons.rounded.Key as RoundedKey
import androidx.compose.material.icons.rounded.Map as RoundedMap
import androidx.compose.material.icons.rounded.MyLocation as RoundedMyLocation
import androidx.compose.material.icons.rounded.Navigation as RoundedNavigation
import androidx.compose.material.icons.rounded.Phone as RoundedPhone
import androidx.compose.material.icons.rounded.Place as RoundedPlace
import androidx.compose.material.icons.rounded.Palette as RoundedPalette
import androidx.compose.material.icons.rounded.RadioButtonUnchecked as RoundedRadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh as RoundedRefresh
import androidx.compose.material.icons.rounded.Restaurant as RoundedRestaurant
import androidx.compose.material.icons.rounded.Save as RoundedSave
import androidx.compose.material.icons.rounded.Search as RoundedSearch
import androidx.compose.material.icons.rounded.Star as RoundedStar
import androidx.compose.material.icons.rounded.Tune as RoundedTune
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dinnerplan.chidian.ui.theme.ChiDianColors
import com.dinnerplan.chidian.ui.theme.ChiDianThemeValues

enum class AppIcon {
    Home,
    Nearby,
    Saved,
    Settings,
    Back,
    Check,
    Close,
    Add,
    Delete,
    ExpandLess,
    ExpandMore,
    Favorite,
    FavoriteBorder,
    Info,
    Key,
    Map,
    MyLocation,
    Navigation,
    Phone,
    Place,
    Refresh,
    Restaurant,
    Save,
    Search,
    Star,
    Tune,
    Palette,
    Launcher,
    ChevronRight,
    RadioUnchecked,
    AiDecide
}

@Composable
fun ThemedActionIcon(
    icon: AppIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    decorated: Boolean = true,
    iconSize: Dp = 20.dp,
    defaultTint: Color = Color.Unspecified
) {
    val vector = themedIconVector(icon)
    if (!ChiDianThemeValues.isGirlPink || !decorated) {
        val resolvedTint = if (defaultTint == Color.Unspecified) LocalContentColor.current else defaultTint
        Icon(
            imageVector = vector,
            contentDescription = contentDescription,
            modifier = modifier.size(iconSize),
            tint = if (selected && ChiDianThemeValues.isGirlPink) Color.White else resolvedTint
        )
        return
    }

    val container = if (selected) ChiDianColors.ActionPrimary else ChiDianColors.SurfaceWarm
    val content = if (selected) Color.White else ChiDianColors.ActionPrimary
    Surface(
        modifier = modifier.size(34.dp),
        shape = ChiDianThemeValues.iconShape,
        color = container,
        border = BorderStroke(
            1.dp,
            if (selected) ChiDianColors.ActionPrimary else ChiDianColors.BorderSubtle
        ),
        tonalElevation = 0.dp,
        shadowElevation = if (selected) 1.dp else 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = vector,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = content
            )
        }
    }
}

@Composable
fun themedIconVector(icon: AppIcon): ImageVector {
    val pink = ChiDianThemeValues.isGirlPink
    return when (icon) {
        AppIcon.Home -> if (pink) Icons.Rounded.RoundedHome else Icons.Filled.Home
        AppIcon.Nearby -> if (pink) Icons.Rounded.RoundedMap else Icons.Filled.Map
        AppIcon.Saved -> if (pink) Icons.Rounded.RoundedFavoriteBorder else Icons.Filled.FavoriteBorder
        AppIcon.Settings -> if (pink) Icons.Rounded.RoundedTune else Icons.Filled.Tune
        AppIcon.Back -> if (pink) Icons.AutoMirrored.Rounded.RoundedArrowBack else Icons.AutoMirrored.Filled.ArrowBack
        AppIcon.Check -> if (pink) Icons.Rounded.RoundedCheckCircle else Icons.Filled.CheckCircle
        AppIcon.Close -> if (pink) Icons.Rounded.RoundedClose else Icons.Filled.Close
        AppIcon.Add -> if (pink) Icons.Rounded.RoundedAdd else Icons.Filled.Add
        AppIcon.Delete -> if (pink) Icons.Rounded.RoundedDelete else Icons.Filled.Delete
        AppIcon.ExpandLess -> if (pink) Icons.Rounded.RoundedExpandLess else Icons.Filled.ExpandLess
        AppIcon.ExpandMore -> if (pink) Icons.Rounded.RoundedExpandMore else Icons.Filled.ExpandMore
        AppIcon.Favorite -> if (pink) Icons.Rounded.RoundedFavorite else Icons.Filled.Favorite
        AppIcon.FavoriteBorder -> if (pink) Icons.Rounded.RoundedFavoriteBorder else Icons.Filled.FavoriteBorder
        AppIcon.Info -> if (pink) Icons.Rounded.RoundedInfo else Icons.Filled.Info
        AppIcon.Key -> if (pink) Icons.Rounded.RoundedKey else Icons.Filled.Key
        AppIcon.Map -> if (pink) Icons.Rounded.RoundedMap else Icons.Filled.Map
        AppIcon.MyLocation -> if (pink) Icons.Rounded.RoundedMyLocation else Icons.Filled.MyLocation
        AppIcon.Navigation -> if (pink) Icons.Rounded.RoundedNavigation else Icons.Filled.Navigation
        AppIcon.Phone -> if (pink) Icons.Rounded.RoundedPhone else Icons.Filled.Phone
        AppIcon.Place -> if (pink) Icons.Rounded.RoundedPlace else Icons.Filled.Place
        AppIcon.Refresh -> if (pink) Icons.Rounded.RoundedRefresh else Icons.Filled.Refresh
        AppIcon.Restaurant -> if (pink) Icons.Rounded.RoundedRestaurant else Icons.Filled.Restaurant
        AppIcon.Save -> if (pink) Icons.Rounded.RoundedSave else Icons.Filled.Save
        AppIcon.Search -> if (pink) Icons.Rounded.RoundedSearch else Icons.Filled.Search
        AppIcon.Star -> if (pink) Icons.Rounded.RoundedStar else Icons.Filled.Star
        AppIcon.Tune -> if (pink) Icons.Rounded.RoundedTune else Icons.Filled.Tune
        AppIcon.Palette -> if (pink) Icons.Rounded.RoundedPalette else Icons.Filled.Palette
        AppIcon.Launcher -> if (pink) Icons.Rounded.RoundedApps else Icons.Filled.Apps
        AppIcon.ChevronRight -> if (pink) Icons.Rounded.RoundedChevronRight else Icons.Filled.ChevronRight
        AppIcon.RadioUnchecked -> if (pink) {
            Icons.Rounded.RoundedRadioButtonUnchecked
        } else {
            Icons.Filled.RadioButtonUnchecked
        }
        AppIcon.AiDecide -> if (pink) Icons.Rounded.RoundedAutoAwesome else Icons.Filled.AutoAwesome
    }
}
