# Native Android UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the 《吃点啥》 Android UI with bright appetite-first colors from direction A and light-future native Compose motion from direction C, without changing backend, AI, Amap, Wanwei, or persistence behavior.

**Architecture:** Keep the current `ChiDianApp` state flow and repository interfaces intact, then extract UI models/helpers, theme, motion primitives, cards, controls, and screens into focused files. Each task must leave the app compiling so visual changes can be checked on emulator or device.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Coil, DataStore, Ktor clients, Android LocationManager and Intent-based map navigation.

---

## Current Constraints

- Project root: `E:\project\dinner_plan`
- Current main UI file: `app/src/main/java/com/dinnerplan/chidian/MainActivity.kt`
- The workspace currently has no `.git` directory, so commit steps are replaced by build checkpoints. If a Git repository is initialized later, commit after each task.
- Keep AGP/Kotlin/Gradle/JDK versions unchanged.
- Use `apply_patch` for manual edits.
- Verification commands on this machine should set JDK 17 first:

```powershell
$jdkHome = 'C:\Users\admin\.jdks\ms-17.0.19'
$env:JAVA_HOME = $jdkHome
$env:Path = "$jdkHome\bin;$env:Path"
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

## Target File Structure

- Create: `app/src/main/java/com/dinnerplan/chidian/ui/theme/ChiDianTheme.kt`
  - Owns color tokens, gradients, Material color scheme, and shape constants.
- Create: `app/src/main/java/com/dinnerplan/chidian/ui/components/FoodMotion.kt`
  - Owns reusable motion primitives: press scale, shimmer, radar pulse, stagger helper, animated visibility wrapper.
- Create: `app/src/main/java/com/dinnerplan/chidian/ui/components/FoodScaffold.kt`
  - Owns app-level background, bottom navigation, top bars, page containers.
- Create: `app/src/main/java/com/dinnerplan/chidian/ui/components/FoodCards.kt`
  - Owns hero cards, result cards, detail blocks, empty/status cards, info tiles.
- Create: `app/src/main/java/com/dinnerplan/chidian/ui/components/FoodControls.kt`
  - Owns search panels, chips, segmented controls, toggles, buttons.
- Create: `app/src/main/java/com/dinnerplan/chidian/ui/screens/HomeScreen.kt`
- Create: `app/src/main/java/com/dinnerplan/chidian/ui/screens/CookRecommendScreen.kt`
- Create: `app/src/main/java/com/dinnerplan/chidian/ui/screens/NearbyRestaurantScreen.kt`
- Create: `app/src/main/java/com/dinnerplan/chidian/ui/screens/DetailScreens.kt`
- Create: `app/src/main/java/com/dinnerplan/chidian/ui/screens/SavedSettingsScreens.kt`
- Modify: `app/src/main/java/com/dinnerplan/chidian/MainActivity.kt`
  - Keep app state, repository wiring, persistence, navigation, and business helpers.
  - Remove extracted Composable definitions after their replacements compile.
- Create/modify tests under `app/src/test/kotlin/com/dinnerplan/chidian/`
  - Add small pure JVM tests for visibility, palette constants, and deterministic motion helpers.

---

### Task 1: Expose UI Models and Pure Helpers for Split Files

**Files:**
- Modify: `app/src/main/java/com/dinnerplan/chidian/MainActivity.kt`
- Create: `app/src/test/kotlin/com/dinnerplan/chidian/UiSurfaceAccessTest.kt`

- [ ] **Step 1: Write the failing visibility test**

Create `app/src/test/kotlin/com/dinnerplan/chidian/UiSurfaceAccessTest.kt`:

```kotlin
package com.dinnerplan.chidian

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiSurfaceAccessTest {
    @Test
    fun uiModelsCanBeUsedOutsideMainActivityFile() {
        val recipe = Recipe(
            id = "recipe_test",
            name = "番茄炒蛋",
            cuisine = "家常菜",
            taste = listOf("酸甜"),
            tags = listOf("快手"),
            difficulty = "简单",
            cookTime = "15 分钟",
            servings = "2 人份",
            coverUrl = "",
            reason = "测试用菜谱",
            ingredients = listOf("鸡蛋" to "2个"),
            steps = listOf("炒鸡蛋", "炒番茄"),
            tips = "先炒鸡蛋。",
            ratingStars = 4.6,
            source = "测试"
        )
        val state = AppUiState(recipes = listOf(recipe), recommendMode = RecommendMode.SingleRecipe)

        assertEquals("番茄炒蛋", state.recipes.single().name)
        assertEquals("4.6 星 · 家常菜 · 15 分钟 · 简单", recipeMetaLine(recipe))
    }

    @Test
    fun restaurantSortingHelperRemainsStable() {
        val near = MockData.restaurants.first().copy(id = "near", distance = "300m", rating = "4.1")
        val far = MockData.restaurants.first().copy(id = "far", distance = "1.2km", rating = "4.9")

        assertEquals(listOf("near", "far"), sortRestaurants(listOf(far, near), RestaurantSortMode.Distance).map { it.id })
        assertEquals(listOf("far", "near"), sortRestaurants(listOf(near, far), RestaurantSortMode.Rating).map { it.id })
        assertTrue(sortModeLabel(RestaurantSortMode.Relevance).contains("相关度"))
    }
}
```

- [ ] **Step 2: Run the new test and verify it fails because types are private**

Run:

```powershell
$jdkHome = 'C:\Users\admin\.jdks\ms-17.0.19'
$env:JAVA_HOME = $jdkHome
$env:Path = "$jdkHome\bin;$env:Path"
.\gradlew.bat :app:testDebugUnitTest --tests com.dinnerplan.chidian.UiSurfaceAccessTest
```

Expected: compilation fails with unresolved or inaccessible references for `Recipe`, `AppUiState`, `MockData`, or helper functions.

- [ ] **Step 3: Remove file-private visibility from UI surface types and pure helpers**

In `MainActivity.kt`, remove the `private` modifier from these declarations:

```kotlin
sealed interface Screen
enum class RecommendMode
enum class SavedFilter
enum class RestaurantSortMode
enum class CookSourceMode
enum class PreferenceTarget
data class DishItem
enum class DishBadge
data class MealPlan
data class Recipe
data class Restaurant
data class UserPreference
sealed interface SavedItem
data class DialogState
data class AppUiState
data class CookResult
data class CancelCookResult
data class RestaurantResult
object MockData
fun toggleSet(source: Set<String>, id: String): Set<String>
fun toggleList(source: List<String>, value: String): List<String>
fun appendUnique(source: List<String>, value: String): List<String>
fun sortRestaurants(restaurants: List<Restaurant>, mode: RestaurantSortMode): List<Restaurant>
fun sortModeLabel(mode: RestaurantSortMode): String
fun formatElapsedTime(totalSeconds: Int): String
fun recipeMetaLine(recipe: Recipe): String
fun parseDistanceMeters(distance: String): Double?
fun parseRatingScore(rating: String): Double?
fun appendQueryTerm(source: String, value: String): String
fun cookSummary(query: String): String
fun cookTags(query: String): List<String>
```

Keep repositories, persistence keys, DataStore extension, and backend/direct repository classes private unless a later task proves a new file needs them.

- [ ] **Step 4: Verify test passes**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.dinnerplan.chidian.UiSurfaceAccessTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Checkpoint**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: all app unit tests pass.

---

### Task 2: Add the New Appetite-First Theme

**Files:**
- Create: `app/src/main/java/com/dinnerplan/chidian/ui/theme/ChiDianTheme.kt`
- Modify: `app/src/main/java/com/dinnerplan/chidian/MainActivity.kt`
- Create: `app/src/test/kotlin/com/dinnerplan/chidian/ChiDianPaletteTest.kt`

- [ ] **Step 1: Write palette tests**

Create `app/src/test/kotlin/com/dinnerplan/chidian/ChiDianPaletteTest.kt`:

```kotlin
package com.dinnerplan.chidian

import com.dinnerplan.chidian.ui.theme.ChiDianColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChiDianPaletteTest {
    @Test
    fun paletteUsesBrightAppetiteBase() {
        assertEquals(0xFFFFFBF4, ChiDianColors.Canvas.value.toLong() and 0xFFFFFFFF)
        assertEquals(0xFFFF4F3E, ChiDianColors.Tomato.value.toLong() and 0xFFFFFFFF)
        assertEquals(0xFF35D07F, ChiDianColors.Mint.value.toLong() and 0xFFFFFFFF)
    }

    @Test
    fun aiAccentIsOnlyAnAccentNotTheMainPalette() {
        val mainColors = listOf(ChiDianColors.Tomato, ChiDianColors.Sun, ChiDianColors.Mint)
        assertTrue(ChiDianColors.AiBlue !in mainColors)
    }
}
```

- [ ] **Step 2: Run palette test and verify it fails before theme exists**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.dinnerplan.chidian.ChiDianPaletteTest
```

Expected: compilation fails because `ChiDianColors` does not exist.

- [ ] **Step 3: Create `ChiDianTheme.kt`**

Create `app/src/main/java/com/dinnerplan/chidian/ui/theme/ChiDianTheme.kt`:

```kotlin
package com.dinnerplan.chidian.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object ChiDianColors {
    val Canvas = Color(0xFFFFFBF4)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceWarm = Color(0xFFFFF6E8)
    val Line = Color(0xFFFFDCC7)
    val Tomato = Color(0xFFFF4F3E)
    val TomatoDark = Color(0xFFC8372B)
    val Sun = Color(0xFFFFC857)
    val Orange = Color(0xFFFF8A3D)
    val Mint = Color(0xFF35D07F)
    val MintDark = Color(0xFF1F8F5A)
    val AiBlue = Color(0xFF426BFF)
    val AiCyan = Color(0xFF18D9C5)
    val Ink = Color(0xFF201A16)
    val Muted = Color(0xFF746A61)
    val SoftMuted = Color(0xFF9E9188)
}

object ChiDianGradients {
    val AppetiteHero: Brush
        get() = Brush.linearGradient(
            listOf(ChiDianColors.Tomato, ChiDianColors.Orange, ChiDianColors.Sun)
        )

    val NearbyHero: Brush
        get() = Brush.linearGradient(
            listOf(ChiDianColors.Mint, ChiDianColors.AiCyan, ChiDianColors.Sun)
        )

    val AiGlow: Brush
        get() = Brush.linearGradient(
            listOf(ChiDianColors.AiBlue, ChiDianColors.AiCyan, ChiDianColors.Tomato)
        )
}

val ChiDianShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

private val ChiDianLightScheme = lightColorScheme(
    primary = ChiDianColors.Tomato,
    onPrimary = Color.White,
    secondary = ChiDianColors.Mint,
    onSecondary = Color.White,
    background = ChiDianColors.Canvas,
    surface = ChiDianColors.Surface,
    onSurface = ChiDianColors.Ink,
    outline = ChiDianColors.Line
)

@Composable
fun ChiDianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChiDianLightScheme,
        shapes = ChiDianShapes,
        content = content
    )
}
```

- [ ] **Step 4: Wire the new theme in `MainActivity.kt`**

Add import:

```kotlin
import com.dinnerplan.chidian.ui.theme.ChiDianColors
import com.dinnerplan.chidian.ui.theme.ChiDianTheme
```

Replace:

```kotlin
MaterialTheme(colorScheme = AppColors) {
    Scaffold(
        containerColor = WarmBackground,
```

with:

```kotlin
ChiDianTheme {
    Scaffold(
        containerColor = ChiDianColors.Canvas,
```

Replace the root Box background:

```kotlin
.background(WarmBackground)
```

with:

```kotlin
.background(ChiDianColors.Canvas)
```

Keep the old color constants temporarily because existing components still use them. They will be removed after component migration.

- [ ] **Step 5: Verify theme tests and app build**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.dinnerplan.chidian.ChiDianPaletteTest
.\gradlew.bat :app:assembleDebug
```

Expected: both commands succeed.

---

### Task 3: Add Reusable Compose Motion Primitives

**Files:**
- Create: `app/src/main/java/com/dinnerplan/chidian/ui/components/FoodMotion.kt`
- Create: `app/src/test/kotlin/com/dinnerplan/chidian/FoodMotionTest.kt`

- [ ] **Step 1: Write deterministic motion helper test**

Create `app/src/test/kotlin/com/dinnerplan/chidian/FoodMotionTest.kt`:

```kotlin
package com.dinnerplan.chidian

import com.dinnerplan.chidian.ui.components.staggerDelayMillis
import kotlin.test.Test
import kotlin.test.assertEquals

class FoodMotionTest {
    @Test
    fun staggerDelayIsBoundedForLongLists() {
        assertEquals(0, staggerDelayMillis(0))
        assertEquals(55, staggerDelayMillis(1))
        assertEquals(220, staggerDelayMillis(4))
        assertEquals(220, staggerDelayMillis(20))
    }
}
```

- [ ] **Step 2: Run motion test and verify it fails before helper exists**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.dinnerplan.chidian.FoodMotionTest
```

Expected: compilation fails because `staggerDelayMillis` does not exist.

- [ ] **Step 3: Create `FoodMotion.kt`**

Create `app/src/main/java/com/dinnerplan/chidian/ui/components/FoodMotion.kt`:

```kotlin
package com.dinnerplan.chidian.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.dinnerplan.chidian.ui.theme.ChiDianColors
import com.dinnerplan.chidian.ui.theme.ChiDianGradients

fun staggerDelayMillis(index: Int): Int = (index.coerceIn(0, 4) * 55)

@Composable
fun Modifier.pressScale(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    return scale(if (pressed) 0.97f else 1f)
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

@Composable
fun StaggeredVisible(index: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(220, delayMillis = staggerDelayMillis(index))) +
            slideInVertically(
                animationSpec = tween(260, delayMillis = staggerDelayMillis(index), easing = FastOutSlowInEasing),
                initialOffsetY = { it / 5 }
            ),
        exit = fadeOut(animationSpec = tween(120))
    ) {
        content()
    }
}

@Composable
fun ShimmerLine(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "shimmerOffset"
    )
    Box(
        modifier
            .height(5.dp)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        ChiDianColors.Tomato.copy(alpha = 0.24f),
                        ChiDianColors.Sun,
                        ChiDianColors.AiCyan.copy(alpha = 0.55f)
                    ),
                    start = androidx.compose.ui.geometry.Offset(offset * 600f, 0f),
                    end = androidx.compose.ui.geometry.Offset((offset + 1f) * 600f, 0f)
                )
            )
    )
}

@Composable
fun RadarPulse(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "radar")
    val pulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.28f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "radarPulse"
    )
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(pulse)
                .alpha((1.3f - pulse).coerceIn(0f, 0.45f))
                .background(ChiDianGradients.NearbyHero, CircleShape)
        )
        content()
    }
}

@Composable
fun FloatingGlow(modifier: Modifier = Modifier, color: Color = ChiDianColors.Sun) {
    val transition = rememberInfiniteTransition(label = "floatingGlow")
    val drift by transition.animateFloat(
        initialValue = -14f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowDrift"
    )
    Box(
        modifier
            .graphicsLayer {
                translationX = drift
                translationY = -drift / 2f
            }
            .background(color.copy(alpha = 0.16f), CircleShape)
    )
}
```

- [ ] **Step 4: Verify motion helper test passes**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.dinnerplan.chidian.FoodMotionTest
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 4: Create New Shared UI Components

**Files:**
- Create: `app/src/main/java/com/dinnerplan/chidian/ui/components/FoodScaffold.kt`
- Create: `app/src/main/java/com/dinnerplan/chidian/ui/components/FoodCards.kt`
- Create: `app/src/main/java/com/dinnerplan/chidian/ui/components/FoodControls.kt`
- Modify: `app/src/main/java/com/dinnerplan/chidian/MainActivity.kt`

- [ ] **Step 1: Create `FoodScaffold.kt` with new top and bottom navigation components**

Use this code as the starting point:

```kotlin
package com.dinnerplan.chidian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dinnerplan.chidian.Screen
import com.dinnerplan.chidian.ui.theme.ChiDianColors

@Composable
fun FoodPage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ChiDianColors.Canvas)
    ) {
        content()
    }
}

@Composable
fun FoodTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    actionIcon: ImageVector,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = ChiDianColors.Ink)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Black, color = ChiDianColors.Ink)
            Text(subtitle, color = ChiDianColors.Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onAction) {
            Surface(color = ChiDianColors.SurfaceWarm, shape = RoundedCornerShape(14.dp)) {
                Icon(actionIcon, contentDescription = null, tint = ChiDianColors.Ink, modifier = Modifier.padding(9.dp).size(20.dp))
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
    NavigationBar(containerColor = ChiDianColors.Surface.copy(alpha = 0.96f)) {
        NavigationBarItem(selected = selected is Screen.Home, onClick = onHome, icon = { Icon(Icons.Filled.Home, null) }, label = { Text("首页") })
        NavigationBarItem(selected = selected is Screen.NearbyRestaurant, onClick = onNearby, icon = { Icon(Icons.Filled.Map, null) }, label = { Text("附近") })
        NavigationBarItem(selected = selected is Screen.Saved, onClick = onSaved, icon = { Icon(Icons.Filled.Favorite, null) }, label = { Text("收藏") })
        NavigationBarItem(selected = selected is Screen.Settings || selected is Screen.DeveloperSettings, onClick = onSettings, icon = { Icon(Icons.Filled.Tune, null) }, label = { Text("设置") })
    }
}
```

- [ ] **Step 2: Create `FoodCards.kt` with modern cards**

Include these components first: `FoodCard`, `AppetiteHeroCard`, `StatusCard`, `EmptyFoodState`, `FoodInfoTile`.

```kotlin
package com.dinnerplan.chidian.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dinnerplan.chidian.ui.theme.ChiDianColors
import com.dinnerplan.chidian.ui.theme.ChiDianGradients

@Composable
fun FoodCard(modifier: Modifier = Modifier, content: @Composable Column.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ChiDianColors.Surface,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, ChiDianColors.Line.copy(alpha = 0.55f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun AppetiteHeroCard(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(ChiDianGradients.AppetiteHero)
            .padding(18.dp)
    ) {
        FloatingGlow(Modifier.size(130.dp).align(Alignment.TopEnd), ChiDianColors.Sun)
        Column(modifier = Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp)
        }
    }
}

@Composable
fun StatusCard(title: String, message: String, isAi: Boolean = false) {
    FoodCard {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = if (isAi) ChiDianColors.AiBlue else ChiDianColors.Tomato)
            Column {
                Text(title, color = ChiDianColors.Ink, fontWeight = FontWeight.Bold)
                Text(message, color = ChiDianColors.Muted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
        if (isAi) ShimmerLine(Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)))
    }
}

@Composable
fun EmptyFoodState(title: String, subtitle: String, action: String, onAction: () -> Unit) {
    FoodCard {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = ChiDianColors.Tomato, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(title, fontWeight = FontWeight.Black, color = ChiDianColors.Ink, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(subtitle, color = ChiDianColors.Muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Button(onClick = onAction, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ChiDianColors.Tomato)) {
            Text(action)
        }
    }
}

@Composable
fun FoodInfoTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = ChiDianColors.SurfaceWarm, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = ChiDianColors.Muted, fontSize = 11.sp)
            Spacer(Modifier.height(3.dp))
            Text(value, color = ChiDianColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
```

- [ ] **Step 3: Create `FoodControls.kt` with chips and segmented buttons**

Include `FoodChip`, `FoodSegmentedButtons`, `FoodSearchPanel`, `FoodModeChip`.

- [ ] **Step 4: Replace old scaffold components gradually**

In `MainActivity.kt`:

- Replace `MaterialTheme` wrapper with `ChiDianTheme` from Task 2 if not already done.
- Replace `BottomNavigation(...)` call with `FoodBottomNavigation(...)`.
- Replace `TopBar(...)` calls with `FoodTopBar(...)` one screen at a time.
- Keep old functions until all call sites are migrated, then remove unused functions.

- [ ] **Step 5: Verify compile**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 5: Redesign Home Screen

**Files:**
- Create: `app/src/main/java/com/dinnerplan/chidian/ui/screens/HomeScreen.kt`
- Modify: `app/src/main/java/com/dinnerplan/chidian/MainActivity.kt`

- [ ] **Step 1: Move HomeScreen to the new file**

Create `HomeScreen.kt` in package `com.dinnerplan.chidian.ui.screens`. Import `MealPlan`, `Restaurant`, `MockData`, `Screen` only as needed.

- [ ] **Step 2: Replace current home layout with “today control center”**

The new HomeScreen must render:

- Product title: `吃点啥`
- Subtitle: `食欲在前，AI 在背后发力`
- Hero: title `今天适合吃点热的`
- Capability row: `菜谱库`, `AI 生成`, `高德附近`
- Two action panels: `自己做` and `附近吃`
- Today inspiration section using `MockData.mealPlans.first()` and `MockData.restaurants.first()`

Use `AppetiteHeroCard`, `FoodCard`, `FoodChip`, `pressScale`, and `StaggeredVisible`.

- [ ] **Step 3: Update `MainActivity.kt` import and remove old HomeScreen after compile**

Add:

```kotlin
import com.dinnerplan.chidian.ui.screens.HomeScreen
```

If Kotlin reports ambiguous `HomeScreen`, delete the old `private fun HomeScreen` block from `MainActivity.kt`.

- [ ] **Step 4: Verify build**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Manual visual check**

Open the debug APK in emulator or phone. The first screen should be bright, food-forward, and not dominated by old beige cards.

---

### Task 6: Redesign Cook Recommendation Screen

**Files:**
- Create: `app/src/main/java/com/dinnerplan/chidian/ui/screens/CookRecommendScreen.kt`
- Modify: `app/src/main/java/com/dinnerplan/chidian/MainActivity.kt`

- [ ] **Step 1: Move `CookRecommendScreen` and `CookLoadingCard`**

Move these Composables into `CookRecommendScreen.kt`:

- `CookRecommendScreen`
- `CookLoadingCard`

Expose any helper components it needs through `FoodCards.kt` and `FoodControls.kt` instead of copying old styling.

- [ ] **Step 2: Add mode-specific loading**

For `CookSourceMode.AiGenerated`, render:

```kotlin
StatusCard(
    title = if (state.developerSettings.enabled) "AI 正在直连生成这一桌饭" else "AI 正在通过后端生成这一桌饭",
    message = "已用时 ${formatElapsedTime(state.cookElapsedSeconds)}，可以随时取消并保留上一次成功结果。",
    isAi = true
)
```

For `CookSourceMode.Database`, render:

```kotlin
StatusCard(
    title = "正在搜索菜谱库",
    message = "会优先匹配菜名、食材和标签，并按星级排序。",
    isAi = false
)
```

- [ ] **Step 3: Upgrade result list**

Wrap meal plan and recipe list items with:

```kotlin
StaggeredVisible(index = index) {
    MealPlanCard(...)
}
```

and:

```kotlin
StaggeredVisible(index = index) {
    RecipeCard(...)
}
```

Use `itemsIndexed` instead of `items`.

- [ ] **Step 4: Verify behavior**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Manual checks:

- Database mode shows database loading language.
- AI mode shows AI loading language and shimmer.
- Cancel button still cancels AI generation.
- Fallback status still displays real error text.

---

### Task 7: Redesign Nearby Restaurants Screen

**Files:**
- Create: `app/src/main/java/com/dinnerplan/chidian/ui/screens/NearbyRestaurantScreen.kt`
- Modify: `app/src/main/java/com/dinnerplan/chidian/MainActivity.kt`

- [ ] **Step 1: Move `NearbyRestaurantScreen`**

Move `NearbyRestaurantScreen` into the new file and import `RestaurantSortMode`, `AppUiState`, `Restaurant`, `sortRestaurants`, `toggleSet`, and UI components.

- [ ] **Step 2: Add native location radar affordance**

Replace the plain location top action with a radar button:

```kotlin
RadarPulse(modifier = Modifier.size(46.dp)) {
    IconButton(onClick = onLocateIssue, modifier = Modifier.align(Alignment.Center)) {
        Icon(Icons.Filled.MyLocation, contentDescription = "定位", tint = ChiDianColors.MintDark)
    }
}
```

- [ ] **Step 3: Upgrade map data state**

When `state.restaurantFallbackReason != null`, show:

```kotlin
StatusCard(
    title = "地图数据状态",
    message = reason,
    isAi = false
)
```

- [ ] **Step 4: Add staggered list rendering**

Use `itemsIndexed(sortedRestaurants)` and wrap each `RestaurantCard` in `StaggeredVisible(index)`.

- [ ] **Step 5: Verify behavior**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Manual checks:

- Location button still requests permission and triggers real location.
- Search still calls backend or direct Amap based on developer mode.
- Sorting by relevance, distance, and rating still changes visible order.
- No-results state still has a retry button.

---

### Task 8: Redesign Detail, Saved, Settings, and Developer Screens

**Files:**
- Create: `app/src/main/java/com/dinnerplan/chidian/ui/screens/DetailScreens.kt`
- Create: `app/src/main/java/com/dinnerplan/chidian/ui/screens/SavedSettingsScreens.kt`
- Modify: `app/src/main/java/com/dinnerplan/chidian/MainActivity.kt`

- [ ] **Step 1: Move detail screens**

Move these functions into `DetailScreens.kt`:

- `MealPlanDetailScreen`
- `RecipeDetailScreen`
- `RestaurantDetailScreen`
- `DetailBlock`
- `TagGrid`
- `StepList`
- `DishLine`
- `FoodInfoTile` usage replaces old `InfoTile`

- [ ] **Step 2: Use large hero + cleaner sections**

Each detail screen must keep:

- Back button.
- Favorite button.
- Existing content and actions.
- Restaurant map navigation callback.

Replace thick old cards with `FoodCard` sections and `AppetiteHeroCard` or image hero depending on available cover URL.

- [ ] **Step 3: Move saved and settings screens**

Move these functions into `SavedSettingsScreens.kt`:

- `SavedScreen`
- `SettingsScreen`
- `DeveloperSettingsScreen`
- `PreferenceCard`
- `PreferenceTagRow`
- `ToggleRow`

Keep developer mode behavior unchanged:

- Backend Base URL remains editable regardless of `settings.enabled`.
- AI/Amap/Wanwei/max wait controls are disabled when `settings.enabled == false`.

- [ ] **Step 4: Verify behavior**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Manual checks:

- Saved items still open detail pages.
- Developer mode switch still persists.
- Disabled fields are visibly disabled.
- Navigation button still opens map intents.

---

### Task 9: Remove Old UI Code and Final Verification

**Files:**
- Modify: `app/src/main/java/com/dinnerplan/chidian/MainActivity.kt`
- Modify: any new UI files created in previous tasks
- Modify: `docs/吃点啥-Android前端实现说明.md`

- [ ] **Step 1: Remove unused old UI constants and functions**

After migration, remove these from `MainActivity.kt` if no longer referenced:

```kotlin
private val WarmBackground
private val WarmSurface
private val WarmLine
private val Tomato
private val TomatoDark
private val Leaf
private val Ink
private val Muted
private val AppColors
private fun BottomNavigation(...)
private fun TopBar(...)
private fun WarmCard(...)
private fun HeroCard(...)
private fun PromptCard(...)
private fun TagRow(...)
private fun QuickTagRow(...)
private fun QuickTagChip(...)
private fun SelectableTagRow(...)
private fun TagChip(...)
private fun ActionCard(...)
private fun SectionHeader(...)
private fun AiSummaryCard(...)
private fun SegmentedButtons(...)
private fun SegmentButton(...)
private fun MealPlanCard(...)
private fun RecipeCard(...)
private fun RestaurantCard(...)
private fun SaveRow(...)
private fun MiniCard(...)
private fun SavedMiniCard(...)
private fun ThumbImage(...)
private fun EmptyState(...)
private fun ModeChip(...)
```

If a function is still referenced, either migrate the call site or move the function into the correct new component file.

- [ ] **Step 2: Update Android frontend docs**

In `docs/吃点啥-Android前端实现说明.md`, replace the old visual direction section with:

```markdown
## 视觉方向

当前 Android 原生界面采用“明亮食欲 + 轻未来 AI”的方向：

- A 方案配色：暖白背景、番茄红主动作、阳光橙/黄食欲高光、薄荷绿定位和附近状态。
- C 方案动效：搜索展开、AI 流光、定位雷达、结果错峰入场、收藏弹性反馈。
- 页面结构从原型卡片堆叠升级为首页控制台、做饭/附近专用工作区和大图详情页。
- 动效使用 Jetpack Compose 原生状态驱动，并结合触觉反馈体现原生 Android 优势。
```

- [ ] **Step 3: Run full verification**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Produce final APK path**

Confirm:

```powershell
Get-Item app\build\outputs\apk\debug\app-debug.apk | Select-Object FullName,Length,LastWriteTime
```

Expected: file exists at `E:\project\dinner_plan\app\build\outputs\apk\debug\app-debug.apk`.

- [ ] **Step 5: Manual smoke test**

On emulator or real phone:

- Open Home.
- Tap “自己做”, switch 数据库/AI生成, run a search.
- Tap “附近”, use manual location and locate button.
- Change restaurant sorting among 相关度、距离、评分.
- Open recipe, meal plan, and restaurant details.
- Toggle favorites.
- Open Settings and Developer Settings.

Expected: UI is bright and modern, animations are visible but not distracting, and all previous business behavior still works.

---

## Self-Review Against Spec

- Visual system covered by Tasks 2, 4, and 5.
- C-style motion covered by Tasks 3, 6, and 7.
- Home redesign covered by Task 5.
- Cook page redesign covered by Task 6.
- Nearby page redesign covered by Task 7.
- Detail, saved, settings redesign covered by Task 8.
- Code structure split covered by Tasks 1, 4, 5, 6, 7, and 8.
- Error and empty states preserved through `StatusCard` and existing fallback fields in Tasks 6 and 7.
- Build verification covered by each task and final Task 9.
