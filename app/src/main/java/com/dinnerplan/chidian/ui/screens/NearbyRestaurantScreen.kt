package com.dinnerplan.chidian.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dinnerplan.chidian.AppUiState
import com.dinnerplan.chidian.Restaurant
import com.dinnerplan.chidian.RestaurantSortMode
import com.dinnerplan.chidian.friendlyReason
import com.dinnerplan.chidian.friendlyStatusMessage
import com.dinnerplan.chidian.sortModeLabel
import com.dinnerplan.chidian.sortRestaurants
import com.dinnerplan.chidian.UserMessageContext
import com.dinnerplan.chidian.UserReasonContext
import com.dinnerplan.chidian.ui.components.EmptyFoodState
import com.dinnerplan.chidian.ui.components.ShimmerLine
import com.dinnerplan.chidian.ui.components.StaggeredVisible
import com.dinnerplan.chidian.ui.components.StatusCard
import com.dinnerplan.chidian.ui.components.pressScale
import com.dinnerplan.chidian.ui.theme.ChiDianColors
import kotlinx.coroutines.launch

@Composable
fun NearbyRestaurantScreen(
    state: AppUiState,
    listState: LazyListState,
    restaurants: List<Restaurant>,
    onStateChange: (AppUiState) -> Unit,
    onBack: () -> Unit,
    onRestaurant: (String) -> Unit,
    onToggleRestaurant: (String) -> Unit,
    onLocateIssue: () -> Unit,
    onManualLocationSearch: (String) -> Unit,
    onSearch: () -> Unit
) {
    var showEmpty by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sortedRestaurants = sortRestaurants(restaurants, state.restaurantSortMode)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StaggeredVisible(index = 0) {
                NearbyHeroPanel(
                    state = state,
                    onQueryChange = { onStateChange(state.copy(restaurantQuery = it)) },
                    onLocateIssue = onLocateIssue,
                    onManualLocationSearch = {
                        showEmpty = false
                        onManualLocationSearch(it)
                    },
                    onSearch = {
                        showEmpty = false
                        onSearch()
                    }
                )
            }
        }

        state.restaurantFallbackReason?.let { reason ->
            item {
                StaggeredVisible(index = 3) {
                    StatusCard(
                        icon = Icons.Filled.Info,
                        title = "附近餐厅状态",
                        message = friendlyStatusMessage(reason, UserMessageContext.Restaurant)
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
                        title = "附近暂时没找到合适餐厅",
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
                    NearbySortRow(
                        selected = state.restaurantSortMode,
                        onSelect = { sortMode ->
                            onStateChange(state.copy(restaurantSortMode = sortMode))
                            scope.launch { listState.animateScrollToItem(0) }
                        }
                    )
                }
            }
            item {
                StaggeredVisible(index = 5) {
                    NearbyNoticeCard()
                }
            }
            itemsIndexed(sortedRestaurants, key = { _, restaurant -> restaurant.id }) { index, restaurant ->
                StaggeredVisible(index = index + 6) {
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
private fun NearbyHeroPanel(
    state: AppUiState,
    onQueryChange: (String) -> Unit,
    onLocateIssue: () -> Unit,
    onManualLocationSearch: (String) -> Unit,
    onSearch: () -> Unit
) {
    val locationLabel = state.lastRestaurantLocation
        .ifBlank { state.locationText }
        .ifBlank { "当前位置附近" }
    var isEditingLocation by remember { mutableStateOf(false) }
    var locationDraft by remember(locationLabel) { mutableStateOf(locationLabel) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val submitLocation = {
        val nextLocation = locationDraft.trim()
        if (nextLocation.isNotBlank()) {
            keyboardController?.hide()
            focusManager.clearFocus()
            isEditingLocation = false
            onManualLocationSearch(nextLocation)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ChiDianColors.BorderSubtle),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "附近吃点啥",
                        color = ChiDianColors.Ink,
                        fontSize = 29.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(Icons.Filled.Place, contentDescription = null, tint = ChiDianColors.LocationAccent, modifier = Modifier.size(18.dp))
                        if (isEditingLocation) {
                            ManualLocationField(
                                value = locationDraft,
                                onValueChange = { locationDraft = it },
                                onSubmit = submitLocation,
                                onCancel = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    locationDraft = locationLabel
                                    isEditingLocation = false
                                },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Text(
                                text = locationLabel,
                                modifier = Modifier.weight(1f, fill = false),
                                color = ChiDianColors.Ink,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(onClick = { isEditingLocation = true }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                Text("更改", color = ChiDianColors.LocationAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                IconButton(onClick = onLocateIssue) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "定位", tint = ChiDianColors.LocationAccent)
                }
            }

            NearbySearchSurface(
                query = state.restaurantQuery,
                onQueryChange = onQueryChange,
                onSearch = onSearch
            )
        }
    }
}

@Composable
private fun NearbySearchSurface(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val submitSearch = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onSearch()
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ChiDianColors.BorderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = ChiDianColors.LocationAccent, modifier = Modifier.size(22.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(
                    color = ChiDianColors.Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                cursorBrush = SolidColor(ChiDianColors.LocationAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isBlank()) {
                            Text(
                                text = "可输入菜系、预算、用餐场景或距离",
                                color = ChiDianColors.Muted,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "清空", tint = ChiDianColors.Muted, modifier = Modifier.size(18.dp))
                }
            }
            Button(
                onClick = submitSearch,
                colors = ButtonDefaults.buttonColors(containerColor = ChiDianColors.ActionPrimary, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text("搜索", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ManualLocationField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = ChiDianColors.LocationAccentSoft,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ChiDianColors.LocationAccent.copy(alpha = 0.20f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(
                    color = ChiDianColors.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                cursorBrush = SolidColor(ChiDianColors.LocationAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text("输入地址或商圈", color = ChiDianColors.Muted, fontSize = 13.sp)
                        }
                        innerTextField()
                    }
                }
            )
            TextButton(onClick = onSubmit, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                Text("确定", color = ChiDianColors.LocationAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "取消", tint = ChiDianColors.Muted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun NearbyLoadingCard(developerEnabled: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ChiDianColors.BorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Surface(color = ChiDianColors.LocationAccentSoft, shape = RoundedCornerShape(999.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Restaurant,
                        contentDescription = null,
                        tint = ChiDianColors.LocationAccent,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "正在搜索附近餐厅...",
                        color = ChiDianColors.Ink,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (developerEnabled) "正在按关键词分批搜索并整理结果。" else "正在整理附近餐厅信息，请稍等一下。",
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
}

@Composable
private fun NearbySortRow(selected: RestaurantSortMode, onSelect: (RestaurantSortMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RestaurantSortMode.entries.forEach { mode ->
            val active = selected == mode
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clickable { onSelect(mode) },
                color = if (active) ChiDianColors.ActionPrimary else Color.White,
                contentColor = if (active) Color.White else ChiDianColors.Ink,
                shape = RoundedCornerShape(999.dp),
                border = if (active) null else BorderStroke(1.dp, ChiDianColors.BorderSubtle),
                shadowElevation = if (active) 2.dp else 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = when (mode) {
                            RestaurantSortMode.Relevance -> Icons.Filled.Favorite
                            RestaurantSortMode.Distance -> Icons.Filled.Place
                            RestaurantSortMode.Rating -> Icons.Filled.Star
                        },
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(sortModeLabel(mode), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun NearbyNoticeCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ChiDianColors.LocationAccentSoft,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ChiDianColors.BorderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = ChiDianColors.LocationAccent, modifier = Modifier.size(18.dp))
            Text(
                text = "根据搜索内容、距离和评分为你整理附近餐厅",
                color = ChiDianColors.Muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(onClick),
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ChiDianColors.BorderSubtle),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                NearbyRestaurantImage(restaurant)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = restaurant.name,
                            modifier = Modifier.weight(1f),
                            color = ChiDianColors.Ink,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 22.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        NearbyDistancePill(restaurant.distance)
                    }
                    NearbyRestaurantMeta(restaurant)
                    if (restaurant.reason.isNotBlank()) {
                        NearbyReasonPill(friendlyReason(restaurant.reason, UserReasonContext.Restaurant))
                    }
                }
            }
            NearbyRestaurantActions(
                isSaved = isSaved,
                onPrimary = onClick,
                onSave = onToggleSave
            )
        }
    }
}

@Composable
private fun NearbyRestaurantImage(restaurant: Restaurant) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(width = 100.dp, height = 92.dp)
            .clip(shape)
            .background(ChiDianColors.SurfaceSubtle, shape),
        contentAlignment = Alignment.Center
    ) {
        if (restaurant.coverUrl.isNotBlank()) {
            AsyncImage(
                model = restaurant.coverUrl,
                contentDescription = restaurant.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Restaurant,
                contentDescription = null,
                tint = ChiDianColors.LocationAccent,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun NearbyDistancePill(distance: String) {
    Surface(
        color = ChiDianColors.LocationAccentSoft,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = distance.ifBlank { "附近" },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = ChiDianColors.LocationAccent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NearbyRestaurantMeta(restaurant: Restaurant) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = ChiDianColors.ActionPrimary, modifier = Modifier.size(15.dp))
        Text(
            text = restaurant.rating.ifBlank { "暂无评分" },
            color = ChiDianColors.ActionPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text("|", color = ChiDianColors.BorderSubtle, fontSize = 13.sp)
        Text(
            text = restaurant.price.ifBlank { "人均可问店" },
            color = ChiDianColors.Muted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text("|", color = ChiDianColors.BorderSubtle, fontSize = 13.sp)
        Text(
            text = restaurant.category.ifBlank { "餐厅" },
            color = ChiDianColors.Muted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NearbyReasonPill(reason: String) {
    Surface(
        color = ChiDianColors.LocationAccentSoft,
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = ChiDianColors.LocationAccent, modifier = Modifier.size(14.dp))
            Text(
                text = reason,
                color = ChiDianColors.LocationAccent,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NearbyRestaurantActions(
    isSaved: Boolean,
    onPrimary: () -> Unit,
    onSave: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clickable(onClick = onPrimary),
            color = ChiDianColors.ActionPrimarySoft,
            shape = RoundedCornerShape(999.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = ChiDianColors.ActionPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("查看详情", color = ChiDianColors.ActionPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clickable(onClick = onSave),
            color = if (isSaved) ChiDianColors.ActionPrimarySoft else Color.White,
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, if (isSaved) ChiDianColors.ActionPrimary.copy(alpha = 0.24f) else ChiDianColors.BorderSubtle)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isSaved) ChiDianColors.ActionPrimary else ChiDianColors.Ink,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isSaved) "已收藏" else "收藏",
                    color = if (isSaved) ChiDianColors.ActionPrimary else ChiDianColors.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
