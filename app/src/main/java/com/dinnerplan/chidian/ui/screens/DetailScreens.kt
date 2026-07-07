package com.dinnerplan.chidian.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dinnerplan.chidian.DishBadge
import com.dinnerplan.chidian.DishItem
import com.dinnerplan.chidian.MealPlan
import com.dinnerplan.chidian.Recipe
import com.dinnerplan.chidian.Restaurant
import com.dinnerplan.chidian.friendlyReason
import com.dinnerplan.chidian.recipeMetaLine
import com.dinnerplan.chidian.UserReasonContext
import com.dinnerplan.chidian.ui.components.FoodCard
import com.dinnerplan.chidian.ui.components.FoodChip
import com.dinnerplan.chidian.ui.components.FoodInfoTile
import com.dinnerplan.chidian.ui.components.FoodTopBar
import com.dinnerplan.chidian.ui.components.StaggeredVisible
import com.dinnerplan.chidian.ui.theme.ChiDianColors
import com.dinnerplan.chidian.ui.theme.ChiDianGradients

@Composable
fun MealPlanDetailScreen(
    plan: MealPlan,
    isSaved: Boolean,
    onBack: () -> Unit,
    onToggleSave: (String) -> Unit,
    onRecipe: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StaggeredVisible(index = 0) {
                FoodTopBar(
                    title = "组合菜单",
                    subtitle = "${plan.structure} · ${plan.cookTime} · ${plan.servings}",
                    onBack = onBack,
                    actionIcon = if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    onAction = { onToggleSave(plan.id) }
                )
            }
        }
        item {
            StaggeredVisible(index = 1) {
                DetailHero(
                    title = plan.title,
                    subtitle = friendlyReason(plan.reason, UserReasonContext.MealPlan),
                    imageUrl = plan.coverUrl,
                    chips = plan.tags,
                    green = false
                )
            }
        }
        item {
            StaggeredVisible(index = 2) {
                DetailMetrics(
                    listOf(
                        "结构" to plan.structure,
                        "时间" to plan.cookTime,
                        "份量" to plan.servings,
                        "菜品" to "${plan.dishes.size} 道"
                    )
                )
            }
        }
        item {
            StaggeredVisible(index = 3) {
                DetailSection(icon = Icons.Filled.Restaurant, title = "这一桌怎么搭") {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        plan.dishes.forEach { dish ->
                            DishDetailLine(dish = dish, onRecipe = onRecipe)
                        }
                    }
                }
            }
        }
        item {
            StaggeredVisible(index = 4) {
                DetailSection(icon = Icons.Filled.ShoppingCart, title = "统一采购清单") {
                    DetailTagGrid(plan.shoppingList)
                }
            }
        }
        item {
            StaggeredVisible(index = 5) {
                DetailSection(icon = Icons.Filled.Route, title = "建议烹饪顺序") {
                    DetailStepList(plan.timeline)
                }
            }
        }
        item {
            StaggeredVisible(index = 6) {
                SaveButton(
                    text = if (isSaved) "已收藏整套菜单" else "收藏整套菜单",
                    isSaved = isSaved,
                    onClick = { onToggleSave(plan.id) }
                )
            }
        }
    }
}

@Composable
fun RecipeDetailScreen(
    recipe: Recipe,
    isSaved: Boolean,
    onBack: () -> Unit,
    onToggleSave: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StaggeredVisible(index = 0) {
                FoodTopBar(
                    title = "菜谱详情",
                    subtitle = recipeMetaLine(recipe),
                    onBack = onBack,
                    actionIcon = if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    onAction = { onToggleSave(recipe.id) }
                )
            }
        }
        item {
            StaggeredVisible(index = 1) {
                DetailHero(
                    title = recipe.name,
                    subtitle = friendlyReason(recipe.reason, UserReasonContext.Recipe),
                    imageUrl = recipe.coverUrl,
                    chips = recipe.tags,
                    green = false
                )
            }
        }
        item {
            StaggeredVisible(index = 2) {
                DetailMetrics(
                    buildList {
                        add("星级" to recipe.ratingStars?.let { "%.1f 星".format(it) }.orEmpty().ifBlank { "未评分" })
                        add("来源" to recipe.source.orEmpty().ifBlank { "本地菜谱" })
                        add("时间" to recipe.cookTime)
                        add("难度" to recipe.difficulty)
                        add("份量" to recipe.servings)
                        add("口味" to recipe.taste.joinToString("、").ifBlank { "未标注" })
                    }
                )
            }
        }
        item {
            StaggeredVisible(index = 3) {
                DetailSection(icon = Icons.Filled.LocalDining, title = "食材和调料") {
                    DetailTagGrid(recipe.ingredients.map { "${it.first} ${it.second}".trim() })
                }
            }
        }
        item {
            StaggeredVisible(index = 4) {
                DetailSection(icon = Icons.Filled.Route, title = "步骤") {
                    DetailStepList(recipe.steps, recipe.stepImageUrls)
                }
            }
        }
        if (recipe.tips.isNotBlank()) {
            item {
                StaggeredVisible(index = 5) {
                    DetailSection(icon = Icons.Filled.Star, title = "小贴士") {
                        Text(recipe.tips, color = ChiDianColors.Muted, lineHeight = 20.sp)
                    }
                }
            }
        }
        item {
            StaggeredVisible(index = 6) {
                SaveButton(
                    text = if (isSaved) "已收藏菜谱" else "收藏菜谱",
                    isSaved = isSaved,
                    onClick = { onToggleSave(recipe.id) }
                )
            }
        }
    }
}

@Composable
fun RestaurantDetailScreen(
    restaurant: Restaurant,
    isSaved: Boolean,
    onBack: () -> Unit,
    onToggleSave: (String) -> Unit,
    onNavigate: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StaggeredVisible(index = 0) {
                FoodTopBar(
                    title = "餐厅详情",
                    subtitle = "${restaurant.category} · ${restaurant.distance} · ${restaurant.open}",
                    onBack = onBack,
                    actionIcon = if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    onAction = { onToggleSave(restaurant.id) }
                )
            }
        }
        item {
            StaggeredVisible(index = 1) {
                DetailHero(
                    title = restaurant.name,
                    subtitle = friendlyReason(restaurant.reason, UserReasonContext.Restaurant),
                    imageUrl = restaurant.coverUrl,
                    chips = restaurant.tags,
                    green = true
                )
            }
        }
        item {
            StaggeredVisible(index = 2) {
                DetailMetrics(
                    listOf(
                        "距离" to restaurant.distance,
                        "评分" to "${restaurant.rating} 分",
                        "预算" to restaurant.price,
                        "状态" to restaurant.open
                    )
                )
            }
        }
        item {
            StaggeredVisible(index = 3) {
                DetailSection(icon = Icons.Filled.LocationOn, title = "地址和电话") {
                    Text(restaurant.address.ifBlank { "暂无地址" }, color = ChiDianColors.Muted, lineHeight = 20.sp)
                    if (restaurant.phone.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Phone, contentDescription = null, tint = ChiDianColors.MintDark, modifier = Modifier.size(16.dp))
                            Text(restaurant.phone, color = ChiDianColors.Muted)
                        }
                    }
                }
            }
        }
        item {
            StaggeredVisible(index = 4) {
                Button(
                    onClick = onNavigate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ChiDianColors.MintDark),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("导航去这里")
                }
            }
        }
        item {
            StaggeredVisible(index = 5) {
                SaveButton(
                    text = if (isSaved) "已收藏餐厅" else "收藏餐厅",
                    isSaved = isSaved,
                    onClick = { onToggleSave(restaurant.id) }
                )
            }
        }
    }
}

@Composable
private fun DetailHero(
    title: String,
    subtitle: String,
    imageUrl: String,
    chips: List<String>,
    green: Boolean
) {
    val gradient = if (green) {
        Brush.linearGradient(listOf(ChiDianColors.MintDark, ChiDianColors.Mint))
    } else {
        ChiDianGradients.AppetiteHero
    }

    FoodCard {
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(158.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .background(gradient, RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Filled.Restaurant,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 20.dp)
                        .size(48.dp)
                )
            }
        }
        Text(title, color = ChiDianColors.Ink, fontSize = 23.sp, fontWeight = FontWeight.Black, lineHeight = 28.sp)
        if (subtitle.isNotBlank()) {
            Text(subtitle, color = ChiDianColors.Muted, fontSize = 13.sp, lineHeight = 20.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        DetailChipRow(chips = chips, green = green)
    }
}

@Composable
private fun DetailMetrics(values: List<Pair<String, String>>) {
    FoodCard {
        values.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (label, value) ->
                    FoodInfoTile(label = label, value = value.ifBlank { "未标注" }, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DetailSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    FoodCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = ChiDianColors.SurfaceWarm, shape = RoundedCornerShape(999.dp)) {
                Icon(icon, contentDescription = null, tint = ChiDianColors.Tomato, modifier = Modifier.padding(7.dp).size(17.dp))
            }
            Text(title, color = ChiDianColors.Ink, fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun DishDetailLine(dish: DishItem, onRecipe: (String) -> Unit) {
    val recipeId = dish.recipeId
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (recipeId != null) Modifier.clickable { onRecipe(recipeId) } else Modifier),
        color = ChiDianColors.SurfaceWarm,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ChiDianColors.Line)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = ChiDianColors.Tomato,
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = dishBadgeLabel(dish.badge),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(dish.name, color = ChiDianColors.Ink, fontWeight = FontWeight.Bold)
                Text(dish.note, color = ChiDianColors.Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            if (dish.recipeId != null) {
                Text("做法", color = ChiDianColors.TomatoDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DetailTagGrid(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.filter { it.isNotBlank() }.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = ChiDianColors.SurfaceWarm,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ChiDianColors.Line)
                    ) {
                        Text(
                            text = item,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            color = ChiDianColors.Ink,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DetailStepList(steps: List<String>, stepImageUrls: List<String> = emptyList()) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.filter { it.isNotBlank() }.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Surface(color = ChiDianColors.Tomato, shape = RoundedCornerShape(999.dp)) {
                    Text(
                        text = "${index + 1}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(step, color = ChiDianColors.Muted, lineHeight = 20.sp)
                    stepImageUrls.getOrNull(index)?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = step,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(142.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailChipRow(chips: List<String>, green: Boolean) {
    val visibleChips = chips.filter { it.isNotBlank() }
    if (visibleChips.isEmpty()) return

    Row(
        modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        visibleChips.forEach { tag ->
            FoodChip(text = tag, green = green)
        }
    }
}

@Composable
private fun SaveButton(text: String, isSaved: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSaved) ChiDianColors.Tomato else ChiDianColors.SurfaceWarm,
            contentColor = if (isSaved) Color.White else ChiDianColors.Ink
        ),
        border = if (isSaved) null else BorderStroke(1.dp, ChiDianColors.Line),
        shape = RoundedCornerShape(8.dp)
    ) {
        Icon(if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun dishBadgeLabel(badge: DishBadge): String {
    return when (badge) {
        DishBadge.Meat -> "荤菜"
        DishBadge.Veg -> "素菜"
        DishBadge.Soup -> "汤"
        DishBadge.Staple -> "主食"
    }
}
