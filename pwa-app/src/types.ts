export type RecommendMode = "RECIPE_COMBO" | "RECIPE_SINGLE";
export type CookSource = "DATABASE" | "AI_GENERATED";
export type SavedKind = "meal" | "recipe" | "restaurant";
export type RestaurantSort = "relevance" | "distance" | "rating";

export interface LocationValue {
  latitude?: number | null;
  longitude?: number | null;
  text?: string | null;
}

export interface UserPreference {
  tastes: string[];
  avoids: string[];
  defaultDistanceKm: number;
  defaultBudget: string;
  preferQuickRecipes: boolean;
  preferOpenRestaurants: boolean;
  restaurantResultLimit: number;
}

export interface DeveloperSettings {
  enabled: boolean;
  aiProvider: "deepseek" | "custom";
  aiBaseUrl: string;
  aiApiKey: string;
  aiModel: string;
  amapWebKey: string;
  recipeApiSource: "wanwei" | "mxnzp" | "custom";
  recipeApiBaseUrl: string;
  recipeApiAppId: string;
  recipeApiSecret: string;
  wanweiRecipeAppKey: string;
  recipePageSize: number;
  maxWaitSeconds: number;
}

export interface DishItem {
  course: string;
  name: string;
  note: string;
  badge: string;
  recipeId?: string | null;
}

export interface MealPlan {
  id: string;
  title: string;
  structure: string;
  cookTime: string;
  servings: string;
  coverUrl: string;
  tags: string[];
  reason: string;
  dishes: DishItem[];
  shoppingList: string[];
  timeline: string[];
}

export interface Recipe {
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
  ingredients: Array<{ name: string; amount: string }>;
  steps: string[];
  tips: string;
  ratingStars?: number | null;
  source?: string | null;
  stepImageUrls?: string[];
}

export interface Restaurant {
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

export interface RecommendationRequest {
  query: string;
  mode: RecommendMode | "RESTAURANT";
  location?: LocationValue | null;
  preferences?: Partial<UserPreference>;
  cookSource?: CookSource;
  requestId?: string | null;
  broadSearch?: boolean;
}

export interface CookRecommendationResponse {
  intent: RecommendMode;
  summary: string;
  mealPlans: MealPlan[];
  recipes: Recipe[];
  fallbackReason?: string | null;
  source: CookSource;
  totalMatches: number;
}

export interface RestaurantRecommendationResponse {
  restaurants: Restaurant[];
  locationUsed?: LocationValue | null;
  fallbackReason?: string | null;
}

export interface SavedRef {
  kind: SavedKind;
  id: string;
}

export interface PersistedState {
  version: 1;
  preferences: UserPreference;
  developerSettings: DeveloperSettings;
  location: LocationValue;
  saved: SavedRef[];
  history: SavedRef[];
  mealCache: MealPlan[];
  recipeCache: Recipe[];
  restaurantCache: Restaurant[];
  lastCookQuery: string;
  lastCookMode: RecommendMode;
  lastCookSource: CookSource;
  lastMealPlans: MealPlan[];
  lastRecipes: Recipe[];
  lastRestaurantQuery: string;
  lastRestaurants: Restaurant[];
}
