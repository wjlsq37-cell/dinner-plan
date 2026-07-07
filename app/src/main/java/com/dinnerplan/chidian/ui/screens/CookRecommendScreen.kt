package com.dinnerplan.chidian.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dinnerplan.chidian.AppUiState
import com.dinnerplan.chidian.CookSourceMode
import com.dinnerplan.chidian.DishBadge
import com.dinnerplan.chidian.DishItem
import com.dinnerplan.chidian.MealPlan
import com.dinnerplan.chidian.Recipe
import com.dinnerplan.chidian.RecommendMode
import com.dinnerplan.chidian.appendQueryTerm
import com.dinnerplan.chidian.formatElapsedTime
import com.dinnerplan.chidian.friendlyReason
import com.dinnerplan.chidian.friendlyStatusMessage
import com.dinnerplan.chidian.recipeMetaLine
import com.dinnerplan.chidian.UserMessageContext
import com.dinnerplan.chidian.UserReasonContext
import com.dinnerplan.chidian.ui.components.EmptyFoodState
import com.dinnerplan.chidian.ui.components.FoodCard
import com.dinnerplan.chidian.ui.components.FoodChip
import com.dinnerplan.chidian.ui.components.FoodModeChip
import com.dinnerplan.chidian.ui.components.FoodScreenHeader
import com.dinnerplan.chidian.ui.components.FoodSearchPanel
import com.dinnerplan.chidian.ui.components.FoodSegmentedButtons
import com.dinnerplan.chidian.ui.components.ShimmerLine
import com.dinnerplan.chidian.ui.components.StaggeredVisible
import com.dinnerplan.chidian.ui.components.StatusCard
import com.dinnerplan.chidian.ui.theme.ChiDianColors

@Composable
fun CookRecommendScreen(
    state: AppUiState,
    listState: LazyListState,
    mealPlans: List<MealPlan>,
    recipes: List<Recipe>,
    onStateChange: (AppUiState) -> Unit,
    onBack: () -> Unit,
    onMeal: (String) -> Unit,
    onRecipe: (String) -> Unit,
    onToggleMeal: (String) -> Unit,
    onToggleRecipe: (String) -> Unit,
    onSearch: () -> Unit,
    onCancelSearch: () -> Unit,
    onReroll: () -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StaggeredVisible(index = 0) {
                FoodScreenHeader(
                    title = "在家做点啥",
                    subtitle = "按菜谱库或 AI 生成，推荐单道菜和组合菜单",
                    onBack = onBack,
                    actionIcon = Icons.Filled.Refresh,
                    onAction = onReroll
                )
            }
        }

        item {
            StaggeredVisible(index = 1) {
                FoodSearchPanel(
                    icon = Icons.Filled.Restaurant,
                    title = "自己做",
                    note = "支持单道菜和组合菜单",
                    value = state.cookQuery,
                    onValueChange = { onStateChange(state.copy(cookQuery = it)) },
                    buttonIcon = if (state.cookSourceMode == CookSourceMode.Database) {
                        Icons.Filled.Search
                    } else {
                        Icons.Filled.AutoAwesome
                    },
                    onSubmit = onSearch,
                    chips = listOf("两荤一素", "一汤", "主食", "微辣", "30 分钟"),
                    onChipClick = { tag ->
                        onStateChange(state.copy(cookQuery = appendQueryTerm(state.cookQuery, tag)))
                    }
                )
            }
        }

        item {
            StaggeredVisible(index = 2) {
                FoodCard {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FoodModeChip(
                            text = "数据库",
                            selected = state.cookSourceMode == CookSourceMode.Database,
                            onClick = { onStateChange(state.copy(cookSourceMode = CookSourceMode.Database)) }
                        )
                        FoodModeChip(
                            text = "AI 生成",
                            selected = state.cookSourceMode == CookSourceMode.AiGenerated,
                            onClick = { onStateChange(state.copy(cookSourceMode = CookSourceMode.AiGenerated)) }
                        )
                    }
                }
            }
        }

        state.cookFallbackReason?.let { reason ->
            item {
                StaggeredVisible(index = 4) {
                    StatusCard(
                        icon = Icons.Filled.Info,
                        title = "推荐状态",
                        message = friendlyStatusMessage(reason, UserMessageContext.Recipe)
                    )
                }
            }
        }

        item {
            StaggeredVisible(index = 5) {
                FoodSegmentedButtons(
                    first = "组合菜单",
                    second = "单道菜",
                    firstSelected = state.recommendMode == RecommendMode.MealPlan,
                    onFirst = { onStateChange(state.copy(recommendMode = RecommendMode.MealPlan)) },
                    onSecond = { onStateChange(state.copy(recommendMode = RecommendMode.SingleRecipe)) }
                )
            }
        }

        if (state.isCookLoading) {
            item {
                StaggeredVisible(index = 6) {
                    CookLoadingStatus(
                        state = state,
                        onCancelSearch = onCancelSearch
                    )
                }
            }
        } else if (state.recommendMode == RecommendMode.MealPlan) {
            if (mealPlans.isEmpty()) {
                item {
                    StaggeredVisible(index = 6) {
                        EmptyFoodState(
                            icon = Icons.Filled.Info,
                            title = "暂时没有组合菜单",
                            message = "可以换一个做饭需求，或放宽条件再试一次。",
                            actionText = "重新生成",
                            onAction = onSearch
                        )
                    }
                }
            }
            itemsIndexed(mealPlans, key = { _, plan -> plan.id }) { index, plan ->
                StaggeredVisible(index = index) {
                    CookMealPlanCard(
                        plan = plan,
                        isSaved = plan.id in state.savedMealIds,
                        onClick = { onMeal(plan.id) },
                        onToggleSave = { onToggleMeal(plan.id) }
                    )
                }
            }
        } else {
            if (recipes.isEmpty()) {
                item {
                    StaggeredVisible(index = 6) {
                        EmptyFoodState(
                            icon = Icons.Filled.Info,
                            title = "暂时没有单道菜",
                            message = "可以换一个菜名、口味或食材重新试试。",
                            actionText = "重新生成",
                            onAction = onSearch
                        )
                    }
                }
            }
            itemsIndexed(recipes, key = { _, recipe -> recipe.id }) { index, recipe ->
                StaggeredVisible(index = index) {
                    CookRecipeCard(
                        recipe = recipe,
                        isSaved = recipe.id in state.savedRecipeIds,
                        onClick = { onRecipe(recipe.id) },
                        onToggleSave = { onToggleRecipe(recipe.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CookLoadingStatus(
    state: AppUiState,
    onCancelSearch: () -> Unit
) {
    if (state.cookSourceMode == CookSourceMode.AiGenerated) {
        val aiTitle = "AI 正在生成这一桌饭"
        val aiMessage = "已用时 ${formatElapsedTime(state.cookElapsedSeconds)}，可以随时取消并保留上一次成功结果。"

        FoodCard {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Surface(color = ChiDianColors.AiBlue.copy(alpha = 0.1f), shape = RoundedCornerShape(999.dp)) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = ChiDianColors.AiBlue,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(aiTitle, color = ChiDianColors.Ink, fontWeight = FontWeight.Bold)
                    Text(aiMessage, color = ChiDianColors.Muted, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
            ShimmerLine(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
            )
            Button(
                onClick = onCancelSearch,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ChiDianColors.SurfaceWarm,
                    contentColor = ChiDianColors.TomatoDark
                ),
                border = BorderStroke(1.dp, ChiDianColors.Line),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("取消制作")
            }
        }
    } else {
        StatusCard(
            icon = Icons.Filled.Search,
            title = "正在搜索菜谱库",
            message = "正在为你查找更合适的做饭灵感。"
        )
    }
}

@Composable
private fun CookMealPlanCard(
    plan: MealPlan,
    isSaved: Boolean,
    onClick: () -> Unit,
    onToggleSave: () -> Unit
) {
    FoodCard(modifier = Modifier.clickable(onClick = onClick)) {
        if (plan.coverUrl.isNotBlank()) {
            CookCardCover(imageUrl = plan.coverUrl, description = plan.title)
        }
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                text = plan.title,
                color = ChiDianColors.Ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${plan.structure} · ${plan.cookTime} · ${plan.servings}",
                color = ChiDianColors.Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            FoodTagRow(plan.tags)
            Text(
                text = friendlyReason(plan.reason, UserReasonContext.MealPlan),
                color = ChiDianColors.Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        DishList(plan.dishes)
        ActionRow(
            primaryText = "查看整套菜单",
            isSaved = isSaved,
            onPrimary = onClick,
            onSave = onToggleSave
        )
    }
}

@Composable
private fun CookRecipeCard(
    recipe: Recipe,
    isSaved: Boolean,
    onClick: () -> Unit,
    onToggleSave: () -> Unit
) {
    FoodCard(modifier = Modifier.clickable(onClick = onClick)) {
        if (recipe.coverUrl.isNotBlank()) {
            CookCardCover(imageUrl = recipe.coverUrl, description = recipe.name)
        }
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                text = recipe.name,
                color = ChiDianColors.Ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = recipeMetaLine(recipe),
                color = ChiDianColors.Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            FoodTagRow(recipe.tags)
            Text(
                text = friendlyReason(recipe.reason, UserReasonContext.Recipe),
                color = ChiDianColors.Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        ActionRow(
            primaryText = "看做法",
            isSaved = isSaved,
            onPrimary = onClick,
            onSave = onToggleSave
        )
    }
}

@Composable
private fun CookCardCover(imageUrl: String, description: String) {
    AsyncImage(
        model = imageUrl,
        contentDescription = description,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(8.dp))
    )
}

@Composable
private fun DishList(dishes: List<DishItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ChiDianColors.SurfaceWarm)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        dishes.forEach { dish ->
            DishLine(dish)
        }
    }
}

@Composable
private fun DishLine(dish: DishItem) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        FoodChip(text = dish.badge.label, selected = true, green = dish.badge == DishBadge.Veg)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "${dish.course} · ${dish.name}",
                color = ChiDianColors.Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = dish.note,
                color = ChiDianColors.Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ActionRow(
    primaryText: String,
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
            Text(primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

@Composable
private fun FoodTagRow(tags: List<String>, green: Boolean = false) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        tags.filter { it.isNotBlank() }.forEach { tag ->
            FoodChip(text = tag, green = green)
        }
    }
}

private val DishBadge.label: String
    get() = when (this) {
        DishBadge.Meat -> "荤"
        DishBadge.Veg -> "素"
        DishBadge.Soup -> "汤"
        DishBadge.Staple -> "主食"
    }
