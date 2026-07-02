package com.dinnerplan.chidian.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dinnerplan.chidian.AppUiState
import com.dinnerplan.chidian.Restaurant
import com.dinnerplan.chidian.RestaurantSortMode
import com.dinnerplan.chidian.sortModeLabel
import com.dinnerplan.chidian.sortRestaurants
import com.dinnerplan.chidian.ui.components.EmptyFoodState
import com.dinnerplan.chidian.ui.components.FoodCard
import com.dinnerplan.chidian.ui.components.FoodChip
import com.dinnerplan.chidian.ui.components.FoodScreenHeader
import com.dinnerplan.chidian.ui.components.ShimmerLine
import com.dinnerplan.chidian.ui.components.StaggeredVisible
import com.dinnerplan.chidian.ui.components.StatusCard
import com.dinnerplan.chidian.ui.theme.ChiDianColors
import com.dinnerplan.chidian.ui.theme.ChiDianGradients

@Composable
fun NearbyRestaurantScreen(
    state: AppUiState,
    restaurants: List<Restaurant>,
    onStateChange: (AppUiState) -> Unit,
    onBack: () -> Unit,
    onRestaurant: (String) -> Unit,
    onToggleRestaurant: (String) -> Unit,
    onLocateIssue: () -> Unit,
    onSearch: () -> Unit
) {
    var showEmpty by remember { mutableStateOf(false) }
    val sortedRestaurants = sortRestaurants(restaurants, state.restaurantSortMode)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StaggeredVisible(index = 0) {
                FoodScreenHeader(
                    title = "附近吃点啥",
                    subtitle = "按距离、位置和搜索条件扫描周边餐厅",
                    onBack = onBack,
                    gradient = ChiDianGradients.NearbyHero,
                    actionIcon = Icons.Filled.MyLocation,
                    onAction = onLocateIssue
                )
            }
        }

        item {
            StaggeredVisible(index = 1) {
                NearbySearchCard(
                    query = state.restaurantQuery,
                    onQueryChange = { onStateChange(state.copy(restaurantQuery = it)) },
                    onSearch = {
                        showEmpty = false
                        onSearch()
                    }
                )
            }
        }

        item {
            StaggeredVisible(index = 2) {
                NearbyLocationCard(
                    state = state,
                    onStateChange = onStateChange,
                    onLocateIssue = onLocateIssue
                )
            }
        }

        state.restaurantFallbackReason?.let { reason ->
            item {
                StaggeredVisible(index = 3) {
                    StatusCard(
                        icon = Icons.Filled.Info,
                        title = "地图数据状态",
                        message = reason
                    )
                }
            }
        }

        if (state.isRestaurantLoading) {
            item {
                StaggeredVisible(index = 4) {
                    NearbyLoadingCard(developerEnabled = state.developerSettings.enabled)
                }
            }
        } else if (showEmpty || restaurants.isEmpty()) {
            item {
                StaggeredVisible(index = 4) {
                    EmptyFoodState(
                        icon = Icons.Filled.Info,
                        title = "附近暂未找到符合条件的真实餐厅",
                        message = "换一个地标、扩大范围到 10km，或放宽口味和预算条件再试一次。",
                        actionText = "重新搜索",
                        onAction = {
                            showEmpty = false
                            onSearch()
                        }
                    )
                }
            }
        } else {
            item {
                StaggeredVisible(index = 4) {
                    NearbySortCard(
                        selected = state.restaurantSortMode,
                        onSelect = { sortMode -> onStateChange(state.copy(restaurantSortMode = sortMode)) }
                    )
                }
            }
            itemsIndexed(sortedRestaurants, key = { _, restaurant -> restaurant.id }) { index, restaurant ->
                StaggeredVisible(index = index) {
                    NearbyRestaurantCard(
                        restaurant = restaurant,
                        isSaved = restaurant.id in state.savedRestaurantIds,
                        onClick = { onRestaurant(restaurant.id) },
                        onToggleSave = { onToggleRestaurant(restaurant.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NearbySearchCard(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    FoodCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Surface(color = ChiDianColors.Mint.copy(alpha = 0.14f), shape = RoundedCornerShape(999.dp)) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = ChiDianColors.MintDark,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("搜索附近餐厅", color = ChiDianColors.Ink, fontWeight = FontWeight.Bold)
                Text("可输入菜系、预算、用餐场景或距离", color = ChiDianColors.Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            shape = RoundedCornerShape(8.dp),
            label = { Text("想吃什么 / 预算 / 距离") }
        )
        Button(
            onClick = onSearch,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ChiDianColors.MintDark),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("搜索周边餐厅")
        }
    }
}

@Composable
private fun NearbyLocationCard(
    state: AppUiState,
    onStateChange: (AppUiState) -> Unit,
    onLocateIssue: () -> Unit
) {
    val queryLocation = state.lastRestaurantLocation.ifBlank { state.locationText }

    FoodCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = ChiDianColors.SurfaceWarm, shape = RoundedCornerShape(999.dp)) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    tint = ChiDianColors.Tomato,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("查询位置：$queryLocation", color = ChiDianColors.Ink, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("可用原生定位，也可以输入城市、商圈或地标", color = ChiDianColors.Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            TextButton(onClick = onLocateIssue) {
                Text("定位", color = ChiDianColors.MintDark)
            }
        }
        OutlinedTextField(
            value = state.locationText,
            onValueChange = {
                onStateChange(
                    state.copy(
                        locationText = it,
                        currentLatitude = null,
                        currentLongitude = null,
                        locationSource = ""
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            label = { Text("手动位置 / 城市商圈 / 地标") }
        )
        Text(
            text = "已按“${state.restaurantQuery}”搜索餐厅",
            color = ChiDianColors.Muted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NearbyLoadingCard(developerEnabled: Boolean) {
    FoodCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Surface(color = ChiDianColors.Mint.copy(alpha = 0.14f), shape = RoundedCornerShape(999.dp)) {
                Icon(
                    imageVector = Icons.Filled.Restaurant,
                    contentDescription = null,
                    tint = ChiDianColors.MintDark,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = if (developerEnabled) "正在直连高德周边餐厅..." else "正在请求高德周边餐厅...",
                    color = ChiDianColors.Ink,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "餐厅名称、地址、距离等只取自地图 POI，不用 AI 编造。",
                    color = ChiDianColors.Muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
        ShimmerLine(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
        )
    }
}

@Composable
private fun NearbySortCard(selected: RestaurantSortMode, onSelect: (RestaurantSortMode) -> Unit) {
    FoodCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = ChiDianColors.MintDark, modifier = Modifier.size(18.dp))
            Text("排序方式", color = ChiDianColors.Ink, fontWeight = FontWeight.Bold)
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            RestaurantSortMode.entries.forEach { mode ->
                FoodChip(
                    text = sortModeLabel(mode),
                    selected = selected == mode,
                    green = true,
                    modifier = Modifier.clickable { onSelect(mode) }
                )
            }
        }
    }
}

@Composable
private fun NearbyRestaurantCard(
    restaurant: Restaurant,
    isSaved: Boolean,
    onClick: () -> Unit,
    onToggleSave: () -> Unit
) {
    FoodCard(modifier = Modifier.clickable(onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = restaurant.name,
                        color = ChiDianColors.Ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = restaurant.category,
                        color = ChiDianColors.Muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FoodChip(text = restaurant.distance, selected = true, green = true)
            }
            NearbyRestaurantMeta(restaurant)
            NearbyTagRow(restaurant.tags)
            Text(
                text = restaurant.address.ifBlank { restaurant.reason },
                color = ChiDianColors.Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (restaurant.reason.isNotBlank() && restaurant.address.isNotBlank()) {
                Text(
                    text = restaurant.reason,
                    color = ChiDianColors.Muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        NearbyActionRow(
            isSaved = isSaved,
            onPrimary = onClick,
            onSave = onToggleSave
        )
    }
}

@Composable
private fun NearbyRestaurantMeta(restaurant: Restaurant) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        NearbyMetaPill(
            icon = Icons.Filled.Star,
            text = "评分 ${restaurant.rating}",
            modifier = Modifier.weight(1f)
        )
        NearbyMetaPill(
            icon = Icons.Filled.Restaurant,
            text = restaurant.price,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NearbyMetaPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = ChiDianColors.SurfaceWarm,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ChiDianColors.Line)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = ChiDianColors.Tomato, modifier = Modifier.size(15.dp))
            Text(text, color = ChiDianColors.Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NearbyTagRow(tags: List<String>) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        tags.filter { it.isNotBlank() }.forEach { tag ->
            FoodChip(text = tag)
        }
    }
}

@Composable
private fun NearbyActionRow(
    isSaved: Boolean,
    onPrimary: () -> Unit,
    onSave: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onPrimary,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = ChiDianColors.SurfaceWarm,
                contentColor = ChiDianColors.TomatoDark
            ),
            border = BorderStroke(1.dp, ChiDianColors.Line),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("查看详情", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSaved) ChiDianColors.Tomato else Color.White,
                contentColor = if (isSaved) Color.White else ChiDianColors.Ink
            ),
            border = if (isSaved) null else BorderStroke(1.dp, ChiDianColors.Line),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                imageVector = if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(if (isSaved) "已收藏" else "收藏", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
