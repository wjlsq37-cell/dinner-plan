package com.dinnerplan.chidian.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dinnerplan.chidian.AppUiState
import com.dinnerplan.chidian.DeveloperSettings
import com.dinnerplan.chidian.MealPlan
import com.dinnerplan.chidian.MockData
import com.dinnerplan.chidian.PreferenceTarget
import com.dinnerplan.chidian.Recipe
import com.dinnerplan.chidian.Restaurant
import com.dinnerplan.chidian.SavedFilter
import com.dinnerplan.chidian.SavedItem
import com.dinnerplan.chidian.WANWEI_RECIPE_BASE_URL
import com.dinnerplan.chidian.appendUnique
import com.dinnerplan.chidian.toggleList
import com.dinnerplan.chidian.ui.components.EmptyFoodState
import com.dinnerplan.chidian.ui.components.FoodCard
import com.dinnerplan.chidian.ui.components.FoodChip
import com.dinnerplan.chidian.ui.components.FoodInfoTile
import com.dinnerplan.chidian.ui.components.FoodTopBar
import com.dinnerplan.chidian.ui.components.StaggeredVisible
import com.dinnerplan.chidian.ui.theme.ChiDianColors

@Composable
fun SavedScreen(
    state: AppUiState,
    onStateChange: (AppUiState) -> Unit,
    onBack: () -> Unit,
    onMeal: (String) -> Unit,
    onRecipe: (String) -> Unit,
    onRestaurant: (String) -> Unit,
    onInfo: () -> Unit
) {
    val savedItems = savedItemsFor(state)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StaggeredVisible(index = 0) {
                FoodTopBar(
                    title = "收藏",
                    subtitle = "组合菜单、菜谱和餐厅都在这里",
                    onBack = onBack,
                    actionIcon = Icons.Filled.Info,
                    onAction = onInfo
                )
            }
        }
        item {
            StaggeredVisible(index = 1) {
                SavedFilterTabs(
                    selected = state.savedFilter,
                    onSelect = { filter -> onStateChange(state.copy(savedFilter = filter)) }
                )
            }
        }
        if (savedItems.isEmpty()) {
            item {
                StaggeredVisible(index = 2) {
                    EmptyFoodState(
                        icon = Icons.Filled.FavoriteBorder,
                        title = "还没有收藏",
                        message = "看到喜欢的组合菜单、菜谱或餐厅，点收藏就会出现在这里。",
                        actionText = "回首页",
                        onAction = onBack
                    )
                }
            }
        } else {
            items(savedItems, key = { "${it::class.simpleName}:${it.id}" }) { item ->
                StaggeredVisible(index = 2) {
                    SavedResultCard(item, state, onMeal, onRecipe, onRestaurant)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    state: AppUiState,
    onStateChange: (AppUiState) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDeveloperSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StaggeredVisible(index = 0) {
                FoodTopBar(
                    title = "偏好设置",
                    subtitle = "口味、忌口和搜索习惯都保存在本机",
                    onBack = onBack,
                    actionIcon = Icons.Filled.Save,
                    onAction = onSave
                )
            }
        }
        item {
            StaggeredVisible(index = 1) {
                DeveloperEntryCard(
                    enabled = state.developerSettings.enabled,
                    onClick = onDeveloperSettings
                )
            }
        }
        item {
            StaggeredVisible(index = 2) {
                PreferenceCard(
                    title = "常用口味",
                    selected = state.preferences.tastes,
                    options = state.tasteOptions,
                    isEditable = true,
                    isEditing = state.preferenceEditTarget == PreferenceTarget.Taste,
                    isAdding = state.preferenceAddTarget == PreferenceTarget.Taste,
                    addText = state.tasteDraft,
                    onToggle = { taste ->
                        onStateChange(state.copy(preferences = state.preferences.copy(tastes = toggleList(state.preferences.tastes, taste))))
                    },
                    onEditMode = {
                        onStateChange(
                            state.copy(
                                preferenceEditTarget = if (state.preferenceEditTarget == PreferenceTarget.Taste) null else PreferenceTarget.Taste,
                                preferenceAddTarget = null
                            )
                        )
                    },
                    onAddMode = {
                        onStateChange(
                            state.copy(
                                preferenceAddTarget = if (state.preferenceAddTarget == PreferenceTarget.Taste) null else PreferenceTarget.Taste,
                                preferenceEditTarget = null
                            )
                        )
                    },
                    onAddTextChange = { text -> onStateChange(state.copy(tasteDraft = text)) },
                    onCreate = {
                        val value = state.tasteDraft.trim()
                        if (value.isNotBlank()) {
                            onStateChange(
                                state.copy(
                                    tasteOptions = appendUnique(state.tasteOptions, value),
                                    preferences = state.preferences.copy(tastes = appendUnique(state.preferences.tastes, value)),
                                    tasteDraft = "",
                                    preferenceAddTarget = null
                                )
                            )
                        }
                    },
                    onDelete = { taste ->
                        onStateChange(
                            state.copy(
                                tasteOptions = state.tasteOptions - taste,
                                preferences = state.preferences.copy(tastes = state.preferences.tastes - taste)
                            )
                        )
                    }
                )
            }
        }
        item {
            StaggeredVisible(index = 3) {
                PreferenceCard(
                    title = "忌口",
                    selected = state.preferences.avoids,
                    options = state.avoidOptions,
                    isEditable = true,
                    isEditing = state.preferenceEditTarget == PreferenceTarget.Avoid,
                    isAdding = state.preferenceAddTarget == PreferenceTarget.Avoid,
                    addText = state.avoidDraft,
                    onToggle = { avoid ->
                        onStateChange(state.copy(preferences = state.preferences.copy(avoids = toggleList(state.preferences.avoids, avoid))))
                    },
                    onEditMode = {
                        onStateChange(
                            state.copy(
                                preferenceEditTarget = if (state.preferenceEditTarget == PreferenceTarget.Avoid) null else PreferenceTarget.Avoid,
                                preferenceAddTarget = null
                            )
                        )
                    },
                    onAddMode = {
                        onStateChange(
                            state.copy(
                                preferenceAddTarget = if (state.preferenceAddTarget == PreferenceTarget.Avoid) null else PreferenceTarget.Avoid,
                                preferenceEditTarget = null
                            )
                        )
                    },
                    onAddTextChange = { text -> onStateChange(state.copy(avoidDraft = text)) },
                    onCreate = {
                        val value = state.avoidDraft.trim()
                        if (value.isNotBlank()) {
                            onStateChange(
                                state.copy(
                                    avoidOptions = appendUnique(state.avoidOptions, value),
                                    preferences = state.preferences.copy(avoids = appendUnique(state.preferences.avoids, value)),
                                    avoidDraft = "",
                                    preferenceAddTarget = null
                                )
                            )
                        }
                    },
                    onDelete = { avoid ->
                        onStateChange(
                            state.copy(
                                avoidOptions = state.avoidOptions - avoid,
                                preferences = state.preferences.copy(avoids = state.preferences.avoids - avoid)
                            )
                        )
                    }
                )
            }
        }
        item {
            StaggeredVisible(index = 4) {
                PreferenceCard(
                    title = "默认搜索半径",
                    selected = if (state.preferences.defaultDistance.isBlank()) emptyList() else listOf(state.preferences.defaultDistance),
                    options = listOf("1km", "3km", "5km", "10km"),
                    onToggle = { distance ->
                        onStateChange(
                            state.copy(
                                preferences = state.preferences.copy(
                                    defaultDistance = if (state.preferences.defaultDistance == distance) "" else distance
                                )
                            )
                        )
                    }
                )
            }
        }
        item {
            StaggeredVisible(index = 5) {
                FoodCard {
                    PreferenceToggleRow(
                        title = "优先推荐快手菜",
                        checked = state.preferences.preferQuickRecipes,
                        onToggle = {
                            onStateChange(state.copy(preferences = state.preferences.copy(preferQuickRecipes = !state.preferences.preferQuickRecipes)))
                        }
                    )
                    PreferenceToggleRow(
                        title = "只看营业中餐厅",
                        checked = state.preferences.preferOpenRestaurants,
                        onToggle = {
                            onStateChange(state.copy(preferences = state.preferences.copy(preferOpenRestaurants = !state.preferences.preferOpenRestaurants)))
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun DeveloperSettingsScreen(
    backendBaseUrl: String,
    settings: DeveloperSettings,
    onBackendBaseUrlChange: (String) -> Unit,
    onSettingsChange: (DeveloperSettings) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val controlsEnabled = settings.enabled

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StaggeredVisible(index = 0) {
                FoodTopBar(
                    title = "开发者模式",
                    subtitle = if (settings.enabled) "手机 App 直连 AI、高德和万维易源" else "当前通过本地后端代理",
                    onBack = onBack,
                    actionIcon = Icons.Filled.Save,
                    onAction = onSave
                )
            }
        }
        item {
            StaggeredVisible(index = 1) {
                FoodCard {
                    PreferenceToggleRow(
                        title = "开发者功能",
                        checked = settings.enabled,
                        onToggle = { onSettingsChange(settings.copy(enabled = !settings.enabled)) }
                    )
                    Text(
                        text = if (settings.enabled) "已启用直连调试链路。" else "后端代理模式仍可编辑后端地址。",
                        color = ChiDianColors.Muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
        item {
            StaggeredVisible(index = 2) {
                FoodCard {
                    SectionTitle(icon = Icons.Filled.Settings, title = "后端地址")
                    Text("开发者功能关闭时，App 会通过这个地址访问本地后端。", color = ChiDianColors.Muted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = backendBaseUrl,
                        onValueChange = onBackendBaseUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        label = { Text("Backend Base URL") }
                    )
                }
            }
        }
        item {
            StaggeredVisible(index = 3) {
                DeveloperConfigCard(title = "AI 设置", enabled = controlsEnabled, icon = Icons.Filled.Key) {
                    OutlinedTextField(
                        value = settings.aiBaseUrl,
                        onValueChange = { onSettingsChange(settings.copy(aiBaseUrl = it)) },
                        enabled = controlsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        label = { Text("AI Base URL") }
                    )
                    OutlinedTextField(
                        value = settings.aiApiKey,
                        onValueChange = { onSettingsChange(settings.copy(aiApiKey = it)) },
                        enabled = controlsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        label = { Text("AI API Key") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = settings.aiModel,
                        onValueChange = { onSettingsChange(settings.copy(aiModel = it)) },
                        enabled = controlsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        label = { Text("AI Model") }
                    )
                    ClearTextButton("清空 AI Key", controlsEnabled) {
                        onSettingsChange(settings.copy(aiApiKey = ""))
                    }
                }
            }
        }
        item {
            StaggeredVisible(index = 4) {
                DeveloperConfigCard(title = "地图 API 设置", enabled = controlsEnabled, icon = Icons.Filled.Map) {
                    OutlinedTextField(
                        value = settings.amapWebKey,
                        onValueChange = { onSettingsChange(settings.copy(amapWebKey = it)) },
                        enabled = controlsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        label = { Text("高德 Web Key") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    ClearTextButton("清空高德 Key", controlsEnabled) {
                        onSettingsChange(settings.copy(amapWebKey = ""))
                    }
                }
            }
        }
        item {
            StaggeredVisible(index = 5) {
                DeveloperConfigCard(title = "菜谱 API 设置", enabled = controlsEnabled, icon = Icons.Filled.Restaurant) {
                    Text("接口地址：$WANWEI_RECIPE_BASE_URL", color = ChiDianColors.Muted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = settings.wanweiRecipeAppKey,
                        onValueChange = { onSettingsChange(settings.copy(wanweiRecipeAppKey = it)) },
                        enabled = controlsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        label = { Text("万维易源 AppKey") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = settings.safePageSize.toString(),
                        onValueChange = { text: String ->
                            val next = text.filter { it.isDigit() }.toIntOrNull()
                            if (next != null) {
                                onSettingsChange(settings.copy(wanweiRecipePageSize = next.coerceIn(1, 50)))
                            }
                        },
                        enabled = controlsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        label = { Text("每页数量（1-50）") }
                    )
                    ClearTextButton("清空万维 AppKey", controlsEnabled) {
                        onSettingsChange(settings.copy(wanweiRecipeAppKey = ""))
                    }
                }
            }
        }
        item {
            StaggeredVisible(index = 6) {
                DeveloperConfigCard(title = "最大等待时长", enabled = controlsEnabled, icon = Icons.Filled.Search) {
                    OutlinedTextField(
                        value = settings.safeMaxWaitSeconds.toString(),
                        onValueChange = { text: String ->
                            val next = text.filter { it.isDigit() }.toIntOrNull()
                            if (next != null) {
                                onSettingsChange(settings.copy(maxWaitSeconds = next.coerceIn(10, 300)))
                            }
                        },
                        enabled = controlsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        label = { Text("秒（10-300）") }
                    )
                    FoodInfoTile(label = "当前上限", value = "${settings.safeMaxWaitSeconds} 秒")
                }
            }
        }
    }
}

@Composable
private fun DeveloperEntryCard(enabled: Boolean, onClick: () -> Unit) {
    FoodCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = ChiDianColors.SurfaceWarm, shape = RoundedCornerShape(999.dp)) {
                Icon(Icons.Filled.Tune, contentDescription = null, tint = ChiDianColors.Tomato, modifier = Modifier.padding(8.dp).size(20.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("开发者模式", color = ChiDianColors.Ink, fontWeight = FontWeight.Black)
                Text(
                    if (enabled) "已开启：App 将直连 AI、高德和万维易源"
                    else "已关闭：继续通过本地后端代理",
                    color = ChiDianColors.Muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
            FoodChip(if (enabled) "开启" else "关闭", selected = enabled)
        }
    }
}

@Composable
private fun SavedFilterTabs(selected: SavedFilter, onSelect: (SavedFilter) -> Unit) {
    FoodCard {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            SavedFilterButton("全部", selected == SavedFilter.All, { onSelect(SavedFilter.All) }, Modifier.weight(1f))
            SavedFilterButton("餐厅", selected == SavedFilter.Restaurant, { onSelect(SavedFilter.Restaurant) }, Modifier.weight(1f))
            SavedFilterButton("自己做", selected == SavedFilter.Cook, { onSelect(SavedFilter.Cook) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SavedFilterButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) ChiDianColors.Tomato else ChiDianColors.SurfaceWarm,
        shape = RoundedCornerShape(8.dp),
        border = if (selected) null else BorderStroke(1.dp, ChiDianColors.Line)
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            color = if (selected) Color.White else ChiDianColors.Muted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SavedResultCard(
    item: SavedItem,
    state: AppUiState,
    onMeal: (String) -> Unit,
    onRecipe: (String) -> Unit,
    onRestaurant: (String) -> Unit
) {
    when (item) {
        is SavedItem.Meal -> findSavedMealPlan(item.id, state)?.let { plan ->
            SavedMiniCard(
                imageUrl = plan.coverUrl,
                title = plan.title,
                subtitle = "${plan.structure} · ${plan.cookTime}",
                tags = plan.tags.take(3),
                onClick = { onMeal(plan.id) }
            )
        }
        is SavedItem.RecipeItem -> findSavedRecipe(item.id, state)?.let { recipe ->
            SavedMiniCard(
                imageUrl = recipe.coverUrl,
                title = recipe.name,
                subtitle = "${recipe.cookTime} · ${recipe.difficulty}",
                tags = recipe.tags.take(3),
                onClick = { onRecipe(recipe.id) }
            )
        }
        is SavedItem.RestaurantItem -> findSavedRestaurant(item.id, state)?.let { restaurant ->
            SavedMiniCard(
                imageUrl = restaurant.coverUrl,
                title = restaurant.name,
                subtitle = "${restaurant.distance} · ${restaurant.price}",
                tags = restaurant.tags.take(3),
                green = true,
                onClick = { onRestaurant(restaurant.id) }
            )
        }
    }
}

@Composable
private fun SavedMiniCard(
    imageUrl: String,
    title: String,
    subtitle: String,
    tags: List<String>,
    green: Boolean = false,
    onClick: () -> Unit
) {
    FoodCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SavedThumb(imageUrl = imageUrl, description = title, green = green)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = ChiDianColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = ChiDianColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    tags.filter { it.isNotBlank() }.forEach { tag ->
                        FoodChip(text = tag, green = green)
                    }
                }
            }
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = if (green) ChiDianColors.MintDark else ChiDianColors.Tomato)
        }
    }
}

@Composable
private fun SavedThumb(imageUrl: String, description: String, green: Boolean) {
    if (imageUrl.isNotBlank()) {
        AsyncImage(
            model = imageUrl,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(82.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        Surface(
            modifier = Modifier.size(82.dp),
            color = if (green) ChiDianColors.Mint.copy(alpha = 0.18f) else ChiDianColors.SurfaceWarm,
            shape = RoundedCornerShape(8.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Restaurant, contentDescription = null, tint = if (green) ChiDianColors.MintDark else ChiDianColors.Tomato)
            }
        }
    }
}

@Composable
private fun PreferenceCard(
    title: String,
    selected: List<String>,
    options: List<String>,
    isEditable: Boolean = false,
    isEditing: Boolean = false,
    isAdding: Boolean = false,
    addText: String = "",
    onToggle: (String) -> Unit,
    onEditMode: () -> Unit = {},
    onAddMode: () -> Unit = {},
    onAddTextChange: (String) -> Unit = {},
    onCreate: () -> Unit = {},
    onDelete: (String) -> Unit = {}
) {
    FoodCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = ChiDianColors.Ink, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            if (isEditable) {
                IconButton(onClick = onAddMode) {
                    Icon(Icons.Filled.Add, contentDescription = "新增", tint = ChiDianColors.Tomato)
                }
                IconButton(onClick = onEditMode) {
                    Icon(Icons.Filled.Delete, contentDescription = "编辑", tint = if (isEditing) ChiDianColors.Tomato else ChiDianColors.Muted)
                }
            }
        }
        PreferenceTagRow(
            options = options,
            selected = selected.toSet(),
            isEditing = isEditing,
            onToggle = onToggle,
            onDelete = onDelete
        )
        if (isAdding) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = addText,
                    onValueChange = onAddTextChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    label = { Text("新增") }
                )
                Button(
                    onClick = onCreate,
                    colors = ButtonDefaults.buttonColors(containerColor = ChiDianColors.Tomato),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("加入")
                }
            }
        }
    }
}

@Composable
private fun PreferenceTagRow(
    options: List<String>,
    selected: Set<String>,
    isEditing: Boolean,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        options.forEach { option ->
            val isSelected = option in selected
            Surface(
                modifier = Modifier.clickable {
                    if (isEditing) onDelete(option) else onToggle(option)
                },
                color = if (isSelected) ChiDianColors.Tomato else ChiDianColors.SurfaceWarm,
                shape = RoundedCornerShape(999.dp),
                border = if (isSelected) null else BorderStroke(1.dp, ChiDianColors.Line)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = option,
                        color = if (isSelected) Color.White else ChiDianColors.Muted,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isEditing) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = if (isSelected) Color.White else ChiDianColors.Muted, modifier = Modifier.size(13.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferenceToggleRow(title: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = ChiDianColors.Ink, fontWeight = FontWeight.Bold)
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun DeveloperConfigCard(
    title: String,
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    FoodCard(modifier = Modifier.alpha(if (enabled) 1f else 0.56f)) {
        SectionTitle(icon = icon, title = title, enabled = enabled)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun SectionTitle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    enabled: Boolean = true
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = ChiDianColors.SurfaceWarm, shape = RoundedCornerShape(999.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) ChiDianColors.Tomato else ChiDianColors.Muted,
                modifier = Modifier.padding(7.dp).size(17.dp)
            )
        }
        Text(title, color = if (enabled) ChiDianColors.Ink else ChiDianColors.Muted, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ClearTextButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(text, color = if (enabled) ChiDianColors.TomatoDark else ChiDianColors.Muted)
    }
}

private fun savedItemsFor(state: AppUiState): List<SavedItem> {
    return buildList {
        state.savedMealIds.mapNotNull { id -> findSavedMealPlan(id, state) }.forEach { add(SavedItem.Meal(it.id)) }
        state.savedRecipeIds.mapNotNull { id -> findSavedRecipe(id, state) }.forEach { add(SavedItem.RecipeItem(it.id)) }
        state.savedRestaurantIds.mapNotNull { id -> findSavedRestaurant(id, state) }.forEach { add(SavedItem.RestaurantItem(it.id)) }
    }.filter { item ->
        when (state.savedFilter) {
            SavedFilter.All -> true
            SavedFilter.Cook -> item is SavedItem.Meal || item is SavedItem.RecipeItem
            SavedFilter.Restaurant -> item is SavedItem.RestaurantItem
        }
    }
}

private fun findSavedMealPlan(id: String, state: AppUiState): MealPlan? {
    return (state.mealPlans + MockData.mealPlans).firstOrNull { it.id == id }
}

private fun findSavedRecipe(id: String, state: AppUiState): Recipe? {
    return (state.recipes + state.recipeCache + MockData.recipes)
        .distinctBy { it.id }
        .firstOrNull { it.id == id }
}

private fun findSavedRestaurant(id: String, state: AppUiState): Restaurant? {
    return (state.restaurants + state.restaurantCache + MockData.restaurants)
        .distinctBy { it.id }
        .firstOrNull { it.id == id }
}
