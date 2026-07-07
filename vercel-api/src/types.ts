export type RecommendationModeDto = "RECIPE_SINGLE" | "RECIPE_COMBO" | "RESTAURANT";
export type CookSourceDto = "DATABASE" | "AI_GENERATED";

export interface LocationDto {
  latitude?: number | null;
  longitude?: number | null;
  text?: string | null;
}

export interface UserPreferenceDto {
  tastes?: string[];
  avoids?: string[];
  defaultDistanceKm?: number;
  defaultBudget?: string;
  preferQuickRecipes?: boolean;
  preferOpenRestaurants?: boolean;
  restaurantResultLimit?: number;
}

export interface RecommendationRequest {
  query: string;
  mode: RecommendationModeDto;
  location?: LocationDto | null;
  preferences?: UserPreferenceDto;
  cookSource?: CookSourceDto;
  requestId?: string | null;
}

export interface IngredientDto {
  name: string;
  amount: string;
}

export interface DishItemDto {
  course: string;
  name: string;
  note: string;
  badge: string;
  recipeId?: string | null;
}

export interface RecipeDto {
  id: string;
  name: string;
  cuisine: string;
  taste: string[];
  tags: string[];
  difficulty: string;
  cookTime: string;
  servings: string;
  coverUrl: string;
  reason: string;
  ingredients: IngredientDto[];
  steps: string[];
  tips: string;
  ratingStars?: number | null;
  source?: string | null;
  stepImageUrls?: string[];
}

export interface MealPlanDto {
  id: string;
  title: string;
  structure: string;
  cookTime: string;
  servings: string;
  coverUrl: string;
  tags: string[];
  reason: string;
  dishes: DishItemDto[];
  shoppingList: string[];
  timeline: string[];
}

export interface RestaurantDto {
  id: string;
  source: string;
  name: string;
  category: string;
  tags: string[];
  address: string;
  distance: string;
  rating: string;
  price: string;
  open: string;
  phone: string;
  coverUrl: string;
  reason: string;
  latitude?: number | null;
  longitude?: number | null;
}

export interface CookRecommendationResponse {
  intent: RecommendationModeDto;
  summary: string;
  mealPlans: MealPlanDto[];
  recipes: RecipeDto[];
  fallbackReason?: string | null;
  source: CookSourceDto;
  totalMatches: number;
}

export interface RestaurantRecommendationResponse {
  restaurants: RestaurantDto[];
  locationUsed?: LocationDto | null;
  fallbackReason?: string | null;
}

export interface CancelRecommendationRequest {
  requestId: string;
}

export interface CancelRecommendationResponse {
  requestId: string;
  cancelled: boolean;
  message: string;
}
