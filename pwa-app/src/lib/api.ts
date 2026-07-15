import type { CookRecommendationResponse, DeveloperSettings, MealPlan, Recipe, RecommendationRequest, RestaurantRecommendationResponse } from "../types";

async function requestJson<T>(url: string, init?: RequestInit, signal?: AbortSignal): Promise<T> {
  const response = await fetch(url, { ...init, signal, headers: { "content-type": "application/json", ...(init?.headers ?? {}) } });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(typeof body.message === "string" ? body.message : `请求失败（${response.status}）`);
  return body as T;
}

export class ApiGateway {
  constructor(private settings: DeveloperSettings) {}

  cook(payload: RecommendationRequest, signal?: AbortSignal): Promise<CookRecommendationResponse> {
    return this.settings.enabled
      ? requestJson("/api/direct", { method: "POST", body: JSON.stringify({ operation: "cook", settings: this.settings, payload }) }, signal)
      : requestJson("/api/backend/recommend/cook", { method: "POST", body: JSON.stringify(payload) }, signal);
  }

  cancel(requestId: string): Promise<{ cancelled: boolean; message: string }> {
    if (this.settings.enabled) return Promise.resolve({ cancelled: true, message: "本机请求已取消。" });
    return requestJson("/api/backend/recommend/cook/cancel", { method: "POST", body: JSON.stringify({ requestId }) });
  }

  restaurants(payload: RecommendationRequest, signal?: AbortSignal): Promise<RestaurantRecommendationResponse> {
    return this.settings.enabled
      ? requestJson("/api/direct", { method: "POST", body: JSON.stringify({ operation: "restaurant", settings: this.settings, payload }) }, signal)
      : requestJson("/api/backend/recommend/restaurant", { method: "POST", body: JSON.stringify(payload) }, signal);
  }

  recipe(id: string, signal?: AbortSignal): Promise<Recipe> {
    return this.settings.enabled
      ? requestJson("/api/direct", { method: "POST", body: JSON.stringify({ operation: "recipe", settings: this.settings, id }) }, signal)
      : requestJson(`/api/backend/recipes/${encodeURIComponent(id)}`, undefined, signal);
  }

  mealPlan(id: string, signal?: AbortSignal): Promise<MealPlan> {
    return requestJson(`/api/backend/meal-plans/${encodeURIComponent(id)}`, undefined, signal);
  }
}
