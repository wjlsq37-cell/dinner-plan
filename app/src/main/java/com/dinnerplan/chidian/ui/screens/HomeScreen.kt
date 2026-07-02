package com.dinnerplan.chidian.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dinnerplan.chidian.MealPlan
import com.dinnerplan.chidian.MockData
import com.dinnerplan.chidian.Restaurant
import com.dinnerplan.chidian.ui.components.AppetiteHeroCard
import com.dinnerplan.chidian.ui.components.FoodCard
import com.dinnerplan.chidian.ui.components.FoodChip
import com.dinnerplan.chidian.ui.components.StaggeredVisible
import com.dinnerplan.chidian.ui.components.pressScale
import com.dinnerplan.chidian.ui.theme.ChiDianColors

@Composable
fun HomeScreen(
    onCookSearch: () -> Unit,
    onRestaurantSearch: () -> Unit,
    onSettings: () -> Unit,
    onMeal: (String) -> Unit,
    onRestaurant: (String) -> Unit
) {
    val meal = MockData.mealPlans.first()
    val restaurant = MockData.restaurants.first()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            StaggeredVisible(index = 0) {
                HomeHeader(onSettings = onSettings)
            }
        }

        item {
            StaggeredVisible(index = 1) {
                AppetiteHeroCard(
                    title = "今天适合吃点热的",
                    subtitle = "从做饭到附近觅食，先把今天的胃口定下来。",
                    note = "晚餐灵感已就绪"
                )
            }
        }

        item {
            StaggeredVisible(index = 3) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionPanel(
                        modifier = Modifier
                            .weight(1f)
                            .height(166.dp),
                        icon = Icons.Filled.Restaurant,
                        title = "自己做",
                        description = "菜谱和成套晚餐",
                        tint = ChiDianColors.Tomato,
                        onClick = onCookSearch
                    )
                    ActionPanel(
                        modifier = Modifier
                            .weight(1f)
                            .height(166.dp),
                        icon = Icons.Filled.Place,
                        title = "附近吃",
                        description = "按位置找顺路好店",
                        tint = ChiDianColors.MintDark,
                        onClick = onRestaurantSearch
                    )
                }
            }
        }

        item {
            StaggeredVisible(index = 4) {
                TodayInspiration(
                    meal = meal,
                    restaurant = restaurant,
                    onMeal = { onMeal(meal.id) },
                    onRestaurant = { onRestaurant(restaurant.id) }
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "吃点啥",
                color = ChiDianColors.Ink,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "食欲在前，AI 在背后发力",
                color = ChiDianColors.Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "设置", tint = ChiDianColors.Ink)
        }
    }
}

@Composable
private fun ActionPanel(
    icon: ImageVector,
    title: String,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FoodCard(modifier = modifier.pressScale(onClick)) {
        Surface(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = tint.copy(alpha = 0.12f),
            shape = RoundedCornerShape(999.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .padding(10.dp)
                    .size(24.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            color = ChiDianColors.Ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = description,
            modifier = Modifier.fillMaxWidth(),
            color = ChiDianColors.Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TodayInspiration(
    meal: MealPlan,
    restaurant: Restaurant,
    onMeal: () -> Unit,
    onRestaurant: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("今日灵感", color = ChiDianColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("一个下厨方案，一个附近选择", color = ChiDianColors.Muted, fontSize = 12.sp)
            }
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = ChiDianColors.AiBlue, modifier = Modifier.size(20.dp))
        }

        InspirationMealCard(meal = meal, onClick = onMeal)
        InspirationRestaurantCard(restaurant = restaurant, onClick = onRestaurant)
    }
}

@Composable
private fun InspirationMealCard(meal: MealPlan, onClick: () -> Unit) {
    InspirationCard(
        imageUrl = meal.coverUrl,
        icon = Icons.Filled.Restaurant,
        title = meal.title,
        subtitle = "${meal.structure} · ${meal.cookTime}",
        tags = meal.tags.take(3),
        tint = ChiDianColors.Tomato,
        onClick = onClick
    )
}

@Composable
private fun InspirationRestaurantCard(restaurant: Restaurant, onClick: () -> Unit) {
    InspirationCard(
        imageUrl = restaurant.coverUrl,
        icon = Icons.Filled.Place,
        title = restaurant.name,
        subtitle = "${restaurant.distance} · ${restaurant.price} · ${restaurant.rating} 分",
        tags = restaurant.tags.take(3),
        tint = ChiDianColors.MintDark,
        onClick = onClick
    )
}

@Composable
private fun InspirationCard(
    imageUrl: String,
    icon: ImageVector,
    title: String,
    subtitle: String,
    tags: List<String>,
    tint: Color,
    onClick: () -> Unit
) {
    FoodCard(modifier = Modifier.pressScale(onClick)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(82.dp)
                    .background(ChiDianColors.SurfaceWarm, RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                    color = Color.White.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, ChiDianColors.Line)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(16.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = title,
                    color = ChiDianColors.Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = ChiDianColors.Muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    tags.forEach { tag ->
                        FoodChip(text = tag, green = tint == ChiDianColors.MintDark)
                    }
                }
            }
        }
    }
}
