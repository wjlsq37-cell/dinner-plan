package com.dinnerplan.server

import com.dinnerplan.shared.DishItemDto
import com.dinnerplan.shared.IngredientDto
import com.dinnerplan.shared.MealPlanDto
import com.dinnerplan.shared.RecipeDto

class SeedRepository {
    val recipes: List<RecipeDto> = listOf(
        RecipeDto(
            id = "recipe_beef_tomato",
            name = "番茄肥牛汤",
            cuisine = "家常菜",
            taste = listOf("酸甜", "微辣", "暖胃"),
            tags = listOf("25 分钟", "新手友好", "少油"),
            difficulty = "简单",
            cookTime = "25 分钟",
            servings = "2 人份",
            coverUrl = "https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?auto=format&fit=crop&w=900&q=80",
            reason = "酸甜开胃，肥牛熟得快，适合想吃热乎但不想大动干戈的晚餐。",
            ingredients = listOf(
                IngredientDto("肥牛卷", "200g"),
                IngredientDto("番茄", "2 个"),
                IngredientDto("金针菇", "1 把"),
                IngredientDto("蒜末", "适量"),
                IngredientDto("番茄酱", "1 勺"),
                IngredientDto("盐", "适量")
            ),
            steps = listOf(
                "番茄去皮切块，金针菇洗净，肥牛提前焯水去浮沫。",
                "锅中少油炒香蒜末，加入番茄块和番茄酱，炒到出沙。",
                "加入热水煮开，放入金针菇煮 3 分钟，再放肥牛。",
                "加盐调味，喜欢微辣可加少量小米辣，出锅前撒葱花。"
            ),
            tips = "肥牛先焯水，汤会更清爽；番茄炒出沙后再加水，味道更浓。"
        ),
        RecipeDto(
            id = "recipe_chicken_pepper",
            name = "青椒鸡丁",
            cuisine = "川湘家常",
            taste = listOf("鲜香", "微辣"),
            tags = listOf("20 分钟", "下饭", "高蛋白"),
            difficulty = "简单",
            cookTime = "20 分钟",
            servings = "2 人份",
            coverUrl = "https://images.unsplash.com/photo-1563379926898-05f4575a45d8?auto=format&fit=crop&w=900&q=80",
            reason = "鸡肉和青椒都容易熟，微辣下饭，油量可控，适合工作日晚餐。",
            ingredients = listOf(
                IngredientDto("鸡胸肉", "250g"),
                IngredientDto("青椒", "2 个"),
                IngredientDto("生抽", "1 勺"),
                IngredientDto("淀粉", "半勺"),
                IngredientDto("蒜片", "适量"),
                IngredientDto("黑胡椒", "少许")
            ),
            steps = listOf(
                "鸡胸肉切丁，加生抽、黑胡椒和淀粉抓匀腌 8 分钟。",
                "青椒切块，锅中少油先炒鸡丁至变色。",
                "加入蒜片和青椒，大火翻炒 2 分钟。",
                "补少量盐和生抽，翻匀后立即出锅，保持青椒爽脆。"
            ),
            tips = "鸡丁不要炒太久，表面变白后再回锅翻炒，口感更嫩。"
        ),
        RecipeDto(
            id = "recipe_sour_spicy_potato",
            name = "酸辣土豆丝",
            cuisine = "家常菜",
            taste = listOf("酸辣", "爽脆"),
            tags = listOf("15 分钟", "低成本", "快手菜"),
            difficulty = "入门",
            cookTime = "15 分钟",
            servings = "2 人份",
            coverUrl = "https://images.unsplash.com/photo-1604908176997-125f25cc6f3d?auto=format&fit=crop&w=900&q=80",
            reason = "材料简单、出菜很快，酸辣味明显，特别适合没想法时兜底。",
            ingredients = listOf(
                IngredientDto("土豆", "2 个"),
                IngredientDto("干辣椒", "3 个"),
                IngredientDto("白醋", "1 勺"),
                IngredientDto("蒜末", "适量"),
                IngredientDto("盐", "适量"),
                IngredientDto("青椒", "半个")
            ),
            steps = listOf(
                "土豆切丝后用清水冲洗两遍，去掉表面淀粉。",
                "热锅少油炒香蒜末和干辣椒。",
                "下土豆丝大火快炒，沿锅边淋白醋。",
                "加盐调味，土豆丝断生后立刻出锅。"
            ),
            tips = "土豆丝冲水和大火快炒是爽脆的关键。"
        )
    )

    val mealPlans: List<MealPlanDto> = listOf(
        MealPlanDto(
            id = "meal_spicy_combo",
            title = "微辣两荤一素一汤晚餐",
            structure = "两荤一素 · 一汤 · 主食",
            cookTime = "约 45 分钟",
            servings = "2-3 人份",
            coverUrl = "https://images.unsplash.com/photo-1559847844-5315695dadae?auto=format&fit=crop&w=900&q=80",
            tags = listOf("两荤一素", "一汤", "主食", "微辣"),
            reason = "按做饭需求拆解为荤菜、素菜、汤和主食，整体微辣下饭，准备顺序也适合一人操作。",
            dishes = listOf(
                DishItemDto("荤菜", "青椒鸡丁", "微辣高蛋白，20 分钟", "Meat", "recipe_chicken_pepper"),
                DishItemDto("荤菜", "蒜香小炒肉", "下饭肉菜，少油快炒", "Meat"),
                DishItemDto("素菜", "酸辣土豆丝", "爽脆开胃，15 分钟", "Veg", "recipe_sour_spicy_potato"),
                DishItemDto("汤", "番茄豆腐汤", "清爽收尾，少油暖胃", "Soup"),
                DishItemDto("主食", "米饭 / 杂粮饭", "提前煮，搭微辣菜更稳", "Staple")
            ),
            shoppingList = listOf("鸡胸肉 250g", "五花肉 150g", "土豆 2 个", "青椒 3 个", "番茄 2 个", "豆腐 1 盒", "米饭 2-3 人份", "蒜、生抽、白醋、小米辣"),
            timeline = listOf(
                "先淘米煮饭，同时切配鸡肉、土豆丝、青椒和番茄。",
                "土豆丝泡水，鸡丁腌制 8 分钟，汤锅先煮番茄豆腐汤底。",
                "先炒酸辣土豆丝，再炒青椒鸡丁和小炒肉，最后给汤调味。",
                "所有热菜集中在最后 20 分钟出锅，口感更好。"
            )
        ),
        MealPlanDto(
            id = "meal_light_combo",
            title = "清淡快手一人晚餐",
            structure = "一荤一素 · 一汤 · 主食",
            cookTime = "约 30 分钟",
            servings = "1-2 人份",
            coverUrl = "https://images.unsplash.com/photo-1512058564366-18510be2db19?auto=format&fit=crop&w=900&q=80",
            tags = listOf("清淡", "快手", "一人食", "少油"),
            reason = "适合不想吃太油时使用，保留蛋白质、蔬菜、热汤和主食，整体负担更轻。",
            dishes = listOf(
                DishItemDto("荤菜", "番茄肥牛汤", "汤菜合一，25 分钟", "Meat", "recipe_beef_tomato"),
                DishItemDto("素菜", "清炒时蔬", "少油补蔬菜", "Veg"),
                DishItemDto("汤", "紫菜蛋花汤", "5 分钟补汤水", "Soup"),
                DishItemDto("主食", "米饭 / 面条", "按饱腹感选择", "Staple")
            ),
            shoppingList = listOf("肥牛卷 200g", "番茄 2 个", "青菜 1 把", "鸡蛋 2 个", "紫菜少许", "米饭或挂面", "盐、生抽、葱"),
            timeline = listOf(
                "先准备主食，再处理番茄、青菜和鸡蛋。",
                "番茄肥牛汤先煮，等待时清炒时蔬。",
                "最后 5 分钟冲紫菜蛋花汤，全部一起上桌。"
            )
        )
    )

    fun recipeById(id: String): RecipeDto? = recipes.firstOrNull { it.id == id }

    fun mealPlanById(id: String): MealPlanDto? = mealPlans.firstOrNull { it.id == id }
}
