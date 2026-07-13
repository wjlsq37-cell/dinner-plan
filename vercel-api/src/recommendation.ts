import { generateAiCookRecommendation } from "./ai.js";
import { recommendRestaurantsFromAmap } from "./amap.js";
import type { VercelApiConfig } from "./config.js";
import { loadRecipeDetail, searchRecipesCascade } from "./recipeSources.js";
import type { CookRecommendationResponse, RecipeDto, RecommendationModeDto, RecommendationRequest, RestaurantRecommendationResponse } from "./types.js";

export async function recommendCook(
  request: RecommendationRequest,
  config: VercelApiConfig
): Promise<CookRecommendationResponse> {
  const mode = request.mode || "RECIPE_SINGLE";
  if ((request.cookSource ?? "DATABASE") === "AI_GENERATED") {
    const generated = await generateAiCookRecommendation(request, config);
    if (generated) return generated;
  }
  const search = await searchRecipesCascade({
    query: request.query,
    page: 1,
    pageSize: 50,
    config: config.recipe,
    broadSearch: request.broadSearch ?? false
  });
  return {
    intent: normalizeCookIntent(mode),
    summary: search.recipes.length > 0
      ? request.broadSearch
        ? `已为你随机扩大候选范围，找到 ${search.recipes.length} 道菜谱。`
        : `已为你找到 ${search.recipes.length} 道相关菜谱。`
      : "暂时没找到合适的结果，可以换个关键词或放宽条件再试。",
    mealPlans: [],
    recipes: search.recipes,
    fallbackReason: search.fallbackReason ?? null,
    source: request.cookSource ?? "DATABASE",
    totalMatches: search.totalMatches
  };
}

export async function recommendRestaurants(
  request: RecommendationRequest,
  config: VercelApiConfig
): Promise<RestaurantRecommendationResponse> {
  return recommendRestaurantsFromAmap({ ...request, mode: "RESTAURANT" }, config);
}

export async function recipeById(id: string, config: VercelApiConfig): Promise<RecipeDto | null> {
  return loadRecipeDetail({ id, config: config.recipe });
}

function normalizeCookIntent(mode: RecommendationModeDto): RecommendationModeDto {
  return mode === "RECIPE_COMBO" ? "RECIPE_COMBO" : "RECIPE_SINGLE";
}
