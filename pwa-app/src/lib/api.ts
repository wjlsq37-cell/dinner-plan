import type { ApiErrorKind, CookRecommendationResponse, DeveloperSettings, MealPlan, Recipe, RecommendationRequest, RestaurantRecommendationResponse, ServiceStatus } from "../types";

export class ApiError extends Error {
  constructor(public kind: ApiErrorKind, message: string, public status?: number) {
    super(message);
    this.name = "ApiError";
  }
}

async function requestJson<T>(url: string, init?: RequestInit, signal?: AbortSignal): Promise<T> {
  if (!navigator.onLine) throw new ApiError("offline", "当前处于离线状态，联网后再试一次。");
  let response: Response;
  try {
    response = await fetch(url, { ...init, signal, headers: { "content-type": "application/json", ...(init?.headers ?? {}) } });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") throw new ApiError("cancelled", "请求已取消，已保留上一次成功结果。");
    throw new ApiError("proxy_unreachable", "暂时无法连接服务，请检查网络或代理部署。");
  }
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const message = typeof body.message === "string" ? body.message : `请求失败（${response.status}）`;
    const code = typeof body.error === "string" ? body.error : "";
    const kind: ApiErrorKind = response.status === 401 || response.status === 403 ? "auth"
      : response.status === 408 || response.status === 504 ? "timeout"
      : response.status === 400 || code.includes("config") ? "config"
      : response.status === 502 || response.status === 503 ? "upstream" : "invalid_response";
    throw new ApiError(kind, message, response.status);
  }
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

  status(signal?: AbortSignal): Promise<ServiceStatus> {
    return requestJson("/api/status", { method: "GET" }, signal);
  }
}
