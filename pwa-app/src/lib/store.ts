import { openDB } from "idb";
import { seedMealPlans, seedRecipes, seedRestaurants } from "../data/seed";
import type { PersistedState } from "../types";

const DB_NAME = "chidian-pwa";
const STORE_NAME = "state";

export const defaultState: PersistedState = {
  version: 1,
  preferences: { tastes: ["微辣", "清淡"], avoids: ["香菜", "海鲜"], defaultDistanceKm: 5, defaultBudget: "medium", preferQuickRecipes: true, preferOpenRestaurants: true, restaurantResultLimit: 50 },
  developerSettings: { enabled: false, aiProvider: "deepseek", aiBaseUrl: "https://api.deepseek.com", aiApiKey: "", aiModel: "deepseek-v4-flash", amapWebKey: "", recipeApiSource: "wanwei", recipeApiBaseUrl: "", recipeApiAppId: "", recipeApiSecret: "", wanweiRecipeAppKey: "", recipePageSize: 20, maxWaitSeconds: 180 },
  location: { text: "上海人民广场" },
  saved: [{ kind: "meal", id: "meal_spicy_combo" }, { kind: "recipe", id: "recipe_beef_tomato" }, { kind: "restaurant", id: "restaurant_noodle" }],
  history: [{ kind: "meal", id: "meal_spicy_combo" }, { kind: "recipe", id: "recipe_chicken_pepper" }, { kind: "restaurant", id: "restaurant_hunan" }],
  mealCache: seedMealPlans,
  recipeCache: seedRecipes,
  restaurantCache: seedRestaurants,
  lastCookQuery: "",
  lastCookMode: "RECIPE_COMBO",
  lastCookSource: "DATABASE",
  lastMealPlans: [],
  lastRecipes: [],
  lastRestaurantQuery: "",
  lastRestaurants: []
};

async function db() {
  return openDB(DB_NAME, 1, { upgrade(database) { if (!database.objectStoreNames.contains(STORE_NAME)) database.createObjectStore(STORE_NAME); } });
}

export async function loadState(): Promise<PersistedState> {
  try {
    const saved = await (await db()).get(STORE_NAME, "app");
    if (!saved || saved.version !== 1) return structuredClone(defaultState);
    return { ...structuredClone(defaultState), ...saved, preferences: { ...defaultState.preferences, ...saved.preferences }, developerSettings: { ...defaultState.developerSettings, ...saved.developerSettings } };
  } catch {
    return structuredClone(defaultState);
  }
}

export async function saveState(state: PersistedState): Promise<void> {
  try { await (await db()).put(STORE_NAME, state, "app"); } catch { /* Private browsing may deny IndexedDB. */ }
}
