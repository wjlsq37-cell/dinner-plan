import type { MealPlan, Recipe, Restaurant } from "../types";

export const seedRecipes: Recipe[] = [
  {
    id: "recipe_beef_tomato", name: "番茄炖牛腩", cuisine: "家常菜", taste: ["酸甜", "咸鲜"],
    tags: ["下饭菜", "暖胃"], difficulty: "中等", cookTime: "60 分钟", servings: "3 人份", coverUrl: "",
    reason: "酸甜开胃，适合不赶时间的晚餐。", ratingStars: 4.8, source: "seed",
    ingredients: [{ name: "牛腩", amount: "500g" }, { name: "番茄", amount: "3 个" }, { name: "洋葱", amount: "半个" }],
    steps: ["牛腩冷水下锅焯去血沫。", "番茄和洋葱炒出香味与汁水。", "加入牛腩和热水，小火炖至软烂。"],
    tips: "番茄分两次加入，汤汁层次更丰富。", stepImageUrls: []
  },
  {
    id: "recipe_chicken_pepper", name: "青椒鸡丁", cuisine: "川味家常", taste: ["微辣", "咸鲜"],
    tags: ["快手菜", "高蛋白"], difficulty: "简单", cookTime: "25 分钟", servings: "2 人份", coverUrl: "",
    reason: "微辣少油，半小时内可以完成。", ratingStars: 4.7, source: "seed",
    ingredients: [{ name: "鸡胸肉", amount: "300g" }, { name: "青椒", amount: "2 个" }],
    steps: ["鸡肉切丁，加少量盐和淀粉腌制。", "青椒切块，热锅快炒断生。", "鸡丁滑炒变色后与青椒合炒调味。"],
    tips: "鸡丁变白后即可调味，避免久炒发柴。", stepImageUrls: []
  },
  {
    id: "recipe_potato", name: "酸辣土豆丝", cuisine: "家常菜", taste: ["酸辣"], tags: ["快手菜", "素菜"],
    difficulty: "简单", cookTime: "15 分钟", servings: "2 人份", coverUrl: "", reason: "清爽脆口，适合平衡整桌荤菜。",
    ingredients: [{ name: "土豆", amount: "2 个" }, { name: "干辣椒", amount: "2 个" }],
    steps: ["土豆切丝后用清水洗去淀粉。", "热锅爆香辣椒，下土豆丝大火翻炒。", "沿锅边淋醋，调盐后出锅。"],
    tips: "全程大火快炒，口感更脆。", ratingStars: 4.6, source: "seed"
  }
];

export const seedMealPlans: MealPlan[] = [{
  id: "meal_spicy_combo", title: "微辣两荤一素一汤晚餐", structure: "两荤一素 · 一汤 · 主食", cookTime: "45 分钟",
  servings: "3 人份", coverUrl: "", tags: ["微辣", "晚餐", "荤素平衡"], reason: "结构完整、口味协调，食材能交叉复用。",
  dishes: [
    { course: "荤菜", name: "青椒鸡丁", note: "快炒先出锅", badge: "Meat", recipeId: "recipe_chicken_pepper" },
    { course: "荤菜", name: "番茄炖牛腩", note: "提前炖煮", badge: "Meat", recipeId: "recipe_beef_tomato" },
    { course: "素菜", name: "酸辣土豆丝", note: "最后快炒", badge: "Veg", recipeId: "recipe_potato" },
    { course: "汤", name: "菌菇豆腐汤", note: "清淡收尾", badge: "Soup" },
    { course: "主食", name: "米饭", note: "先煮上", badge: "Staple" }
  ],
  shoppingList: ["鸡胸肉 300g", "牛腩 500g", "番茄 3 个", "青椒 2 个", "土豆 2 个", "菌菇", "豆腐", "大米"],
  timeline: ["先淘米煮饭并开始炖牛腩", "统一完成蔬菜和肉类切配", "炖汤，同时腌制鸡丁", "依次快炒土豆丝和鸡丁", "最后调味装盘"]
}];

export const seedRestaurants: Restaurant[] = [
  { id: "restaurant_noodle", source: "seed", name: "巷口牛肉面", category: "面馆", tags: ["牛肉面", "单人友好"], address: "人民广场附近", distance: "850m", rating: "4.6", price: "人均 ¥28", open: "营业中", phone: "电话以地图为准", coverUrl: "", reason: "距离近、出餐快，适合一个人用餐。", latitude: 31.2304, longitude: 121.4737 },
  { id: "restaurant_hunan", source: "seed", name: "湘味小馆", category: "湘菜", tags: ["微辣", "家常菜"], address: "南京西路商圈", distance: "1.6km", rating: "4.7", price: "人均 ¥68", open: "营业状态以地图为准", phone: "电话以地图为准", coverUrl: "", reason: "口味匹配，适合朋友小聚。", latitude: 31.232, longitude: 121.46 }
];
