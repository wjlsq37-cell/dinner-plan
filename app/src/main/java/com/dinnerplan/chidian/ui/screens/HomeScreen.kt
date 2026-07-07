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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dinnerplan.chidian.decisionGreetingForHour
import com.dinnerplan.chidian.decisionSubcopyForHour
import com.dinnerplan.chidian.Recipe
import com.dinnerplan.chidian.Restaurant
import com.dinnerplan.chidian.ui.components.FoodCard
import com.dinnerplan.chidian.ui.components.FoodChip
import com.dinnerplan.chidian.ui.components.StaggeredVisible
import com.dinnerplan.chidian.ui.components.pressScale
import com.dinnerplan.chidian.ui.theme.ChiDianColors
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    listState: LazyListState,
    onCookSearch: () -> Unit,
    onRestaurantSearch: () -> Unit,
    onDecision: () -> Unit,
    decisionRecipe: Recipe?,
    decisionRestaurant: Restaurant?,
    isDecisionLoading: Boolean,
    decisionGenerationId: Long,
    onSettings: () -> Unit,
    onRecipe: (String) -> Unit,
    onRestaurant: (String) -> Unit
) {
    LazyColumn(
        state = listState,
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
                DecisionCard(onDecision = onDecision, isDecisionLoading = isDecisionLoading)
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
                    decisionRecipe = decisionRecipe,
                    decisionRestaurant = decisionRestaurant,
                    isDecisionLoading = isDecisionLoading,
                    decisionGenerationId = decisionGenerationId,
                    onRecipe = { recipe -> onRecipe(recipe.id) },
                    onRestaurant = { restaurant -> onRestaurant(restaurant.id) }
                )
            }
        }
    }
}

@Composable
private fun DecisionCard(onDecision: () -> Unit, isDecisionLoading: Boolean) {
    val hour = remember { LocalTime.now().hour }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, ChiDianColors.Line)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = ChiDianColors.Mint.copy(alpha = 0.12f), shape = RoundedCornerShape(999.dp)) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = ChiDianColors.MintDark,
                            modifier = Modifier
                                .padding(7.dp)
                                .size(17.dp)
                        )
                    }
                    Text("今日决策", color = ChiDianColors.MintDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text("少纠结一点", color = ChiDianColors.Muted, fontSize = 12.sp)
            }
            Text(
                text = decisionGreetingForHour(hour),
                color = ChiDianColors.Ink,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 30.sp
            )
            Text(
                text = decisionSubcopyForHour(hour),
                color = ChiDianColors.Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Button(
                onClick = onDecision,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isDecisionLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ChiDianColors.MintDark,
                    contentColor = Color.White,
                    disabledContainerColor = ChiDianColors.MintDark.copy(alpha = 0.62f),
                    disabledContentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isDecisionLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(17.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (isDecisionLoading) "正在生成今日灵感..." else "帮我决定吃什么", fontWeight = FontWeight.Bold)
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
    decisionRecipe: Recipe?,
    decisionRestaurant: Restaurant?,
    isDecisionLoading: Boolean,
    decisionGenerationId: Long,
    onRecipe: (Recipe) -> Unit,
    onRestaurant: (Restaurant) -> Unit
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

        when {
            isDecisionLoading -> DecisionLoadingCard()
            decisionRecipe == null && decisionRestaurant == null -> DecisionEmptyCard()
            else -> {
                decisionRecipe?.let { recipe ->
                    AnimatedDecisionCard(animationKey = decisionGenerationId, delayMillis = 0) { borderColor, containerColor, shadowElevation ->
                        DecisionRecipeCard(
                            recipe = recipe,
                            onClick = { onRecipe(recipe) },
                            borderColor = borderColor,
                            containerColor = containerColor,
                            shadowElevation = shadowElevation
                        )
                    }
                }
                decisionRestaurant?.let { restaurant ->
                    AnimatedDecisionCard(animationKey = decisionGenerationId, delayMillis = 130) { borderColor, containerColor, shadowElevation ->
                        InspirationRestaurantCard(
                            restaurant = restaurant,
                            onClick = { onRestaurant(restaurant) },
                            borderColor = borderColor,
                            containerColor = containerColor,
                            shadowElevation = shadowElevation
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DecisionRecipeCard(
    recipe: Recipe,
    onClick: () -> Unit,
    borderColor: Color = ChiDianColors.Line,
    containerColor: Color = ChiDianColors.Surface,
    shadowElevation: Dp = 1.dp
) {
    InspirationCard(
        imageUrl = recipe.coverUrl,
        icon = Icons.Filled.Restaurant,
        title = recipe.name,
        subtitle = recipeMetaForDecision(recipe),
        tags = (recipe.taste + recipe.tags).filter { it.isNotBlank() }.take(3),
        tint = ChiDianColors.Tomato,
        borderColor = borderColor,
        containerColor = containerColor,
        shadowElevation = shadowElevation,
        onClick = onClick
    )
}

@Composable
private fun InspirationRestaurantCard(
    restaurant: Restaurant,
    onClick: () -> Unit,
    borderColor: Color = ChiDianColors.Line,
    containerColor: Color = ChiDianColors.Surface,
    shadowElevation: Dp = 1.dp
) {
    InspirationCard(
        imageUrl = restaurant.coverUrl,
        icon = Icons.Filled.Place,
        title = restaurant.name,
        subtitle = "${restaurant.distance} · ${restaurant.price} · ${restaurant.rating} 分",
        tags = restaurant.tags.take(3),
        tint = ChiDianColors.MintDark,
        borderColor = borderColor,
        containerColor = containerColor,
        shadowElevation = shadowElevation,
        onClick = onClick
    )
}

@Composable
private fun AnimatedDecisionCard(
    animationKey: Long,
    delayMillis: Int,
    content: @Composable (borderColor: Color, containerColor: Color, shadowElevation: Dp) -> Unit
) {
    val density = LocalDensity.current
    val alpha = remember { Animatable(1f) }
    val translationY = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val highlight = remember { Animatable(0f) }

    LaunchedEffect(animationKey) {
        if (animationKey <= 0L) {
            alpha.snapTo(1f)
            translationY.snapTo(0f)
            scale.snapTo(1f)
            highlight.snapTo(0f)
            return@LaunchedEffect
        }
        alpha.snapTo(0f)
        translationY.snapTo(with(density) { 16.dp.toPx() })
        scale.snapTo(0.98f)
        highlight.snapTo(1f)
        delay(delayMillis.toLong())
        launch { alpha.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) }
        launch { translationY.animateTo(0f, tween(320, easing = FastOutSlowInEasing)) }
        launch { scale.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
        delay(900)
        highlight.animateTo(0f, tween(250, easing = FastOutSlowInEasing))
    }

    val borderColor = lerp(ChiDianColors.Line, ChiDianColors.Orange.copy(alpha = 0.45f), highlight.value)
    val containerColor = ChiDianColors.Surface
    val shadowElevation = (1f + 2f * highlight.value).dp
    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha.value
            this.translationY = translationY.value
            scaleX = scale.value
            scaleY = scale.value
        }
    ) {
        content(borderColor, containerColor, shadowElevation)
    }
}

@Composable
private fun DecisionLoadingCard() {
    FoodCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = ChiDianColors.Mint.copy(alpha = 0.12f), shape = RoundedCornerShape(999.dp)) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = ChiDianColors.MintDark,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("正在生成今日灵感", color = ChiDianColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("给你搭配一道菜和一家附近餐厅", color = ChiDianColors.Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DecisionEmptyCard() {
    FoodCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = ChiDianColors.SurfaceWarm, shape = RoundedCornerShape(999.dp)) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = ChiDianColors.AiBlue,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("今天还没拍板", color = ChiDianColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("点上方按钮后，这里会出现一菜一店", color = ChiDianColors.Muted, fontSize = 12.sp)
            }
        }
    }
}

private fun recipeMetaForDecision(recipe: Recipe): String {
    return buildList {
        add(recipe.cuisine)
        add(recipe.cookTime)
        recipe.ratingStars?.let { add("${"%.1f".format(it)} 分") }
    }.filter { it.isNotBlank() }.joinToString(" · ")
}

@Composable
private fun InspirationCard(
    imageUrl: String,
    icon: ImageVector,
    title: String,
    subtitle: String,
    tags: List<String>,
    tint: Color,
    borderColor: Color = ChiDianColors.Line,
    containerColor: Color = ChiDianColors.Surface,
    shadowElevation: Dp = 1.dp,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(onClick),
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = shadowElevation,
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
}
