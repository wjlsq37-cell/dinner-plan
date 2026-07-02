package com.dinnerplan.chidian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dinnerplan.chidian.Screen
import com.dinnerplan.chidian.ui.theme.ChiDianColors
import com.dinnerplan.chidian.ui.theme.ChiDianGradients

enum class FoodBottomTab {
    Home,
    Nearby,
    Saved,
    Settings
}

fun foodBottomTabFor(screen: Screen): FoodBottomTab {
    return when (screen) {
        Screen.Home,
        Screen.CookRecommend,
        is Screen.MealPlanDetail,
        is Screen.RecipeDetail -> FoodBottomTab.Home
        Screen.NearbyRestaurant,
        is Screen.RestaurantDetail -> FoodBottomTab.Nearby
        Screen.Saved -> FoodBottomTab.Saved
        Screen.Settings,
        Screen.DeveloperSettings -> FoodBottomTab.Settings
    }
}

@Composable
fun FoodPage(
    selected: Screen,
    onHome: () -> Unit,
    onNearby: () -> Unit,
    onSaved: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        containerColor = ChiDianColors.Canvas,
        snackbarHost = {
            snackbarHostState?.let { SnackbarHost(it) }
        },
        bottomBar = {
            FoodBottomNavigation(
                selected = selected,
                onHome = onHome,
                onNearby = onNearby,
                onSaved = onSaved,
                onSettings = onSettings
            )
        },
        content = content
    )
}

@Composable
fun FoodTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = ChiDianColors.Ink)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ChiDianColors.Ink)
            Text(
                subtitle,
                color = ChiDianColors.Muted,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (actionIcon != null && onAction != null) {
            IconButton(onClick = onAction) {
                Icon(actionIcon, contentDescription = null, tint = ChiDianColors.Ink)
            }
        }
    }
}

@Composable
fun FoodScreenHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: Brush = ChiDianGradients.AppetiteHero,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(gradient, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 27.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (actionIcon != null && onAction != null) {
            IconButton(onClick = onAction) {
                Icon(actionIcon, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
fun FoodBottomNavigation(
    selected: Screen,
    onHome: () -> Unit,
    onNearby: () -> Unit,
    onSaved: () -> Unit,
    onSettings: () -> Unit
) {
    val selectedTab = foodBottomTabFor(selected)

    NavigationBar(containerColor = ChiDianColors.Surface, tonalElevation = 0.dp) {
        NavigationBarItem(
            selected = selectedTab == FoodBottomTab.Home,
            onClick = onHome,
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text("首页") }
        )
        NavigationBarItem(
            selected = selectedTab == FoodBottomTab.Nearby,
            onClick = onNearby,
            icon = { Icon(Icons.Filled.Map, contentDescription = null) },
            label = { Text("附近") }
        )
        NavigationBarItem(
            selected = selectedTab == FoodBottomTab.Saved,
            onClick = onSaved,
            icon = { Icon(Icons.Filled.FavoriteBorder, contentDescription = null) },
            label = { Text("收藏") }
        )
        NavigationBarItem(
            selected = selectedTab == FoodBottomTab.Settings,
            onClick = onSettings,
            icon = { Icon(Icons.Filled.Tune, contentDescription = null) },
            label = { Text("设置") }
        )
    }
}
