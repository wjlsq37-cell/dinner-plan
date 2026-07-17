import type { ApiErrorKind, CookRecommendationResponse, DeveloperSettings, MealPlan, Recipe, RecommendationRequest, RestaurantRecommendationResponse, ReverseGeocodeRequest, ReverseGeocodeResponse, ServiceStatus } from "../types";

export class ApiError extends Error {
  constructor(public kind: ApiErrorKind, message: string, public status?: number) {
    super(message);
    this.name = "ApiError";
  }
}

type Validator<T> = (body: unknown) => body is T;

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

const isCookResponse: Validator<CookRecommendationResponse> = (body): body is CookRecommendationResponse =>
  isRecord(body) && Array.isArray(body.mealPlans) && Array.isArray(body.recipes) && typeof body.summary === "string";
const isRestaurantResponse: Validator<RestaurantRecommendationResponse> = (body): body is RestaurantRecommendationResponse =>
  isRecord(body) && Array.isArray(body.restaurants);
const isRecipe: Validator<Recipe> = (body): body is Recipe => isRecord(body) && typeof body.id === "string" && typeof body.name === "string";
const isMealPlan: Validator<MealPlan> = (body): body is MealPlan => isRecord(body) && typeof body.id === "string" && typeof body.title === "string";
const isServiceStatus: Validator<ServiceStatus> = (body): body is ServiceStatus =>
  isRecord(body) && typeof body.proxyReachable === "boolean" && typeof body.backendConfigured === "boolean" && typeof body.message === "string";
const isReverseGeocodeResponse: Validator<ReverseGeocodeResponse> = (body): body is ReverseGeocodeResponse => {
  const location = isRecord(body) && isRecord(body.location) ? body.location : null;
  return Boolean(location) && typeof location?.latitude === "number" && typeof location.longitude === "number" && typeof location.text === "string";
};

function isDatabaseFallback(response: CookRecommendationResponse): boolean {
  if (response.source !== "AI_GENERATED") return true;
  return response.recipes.some((recipe) => {
    const source = String(recipe.source || "").toLowerCase();
    return /mxnzp|wanwei|database|recipe[_-]?api/.test(source) || /^(mxnzp|wanwei)_/i.test(recipe.id);
  });
}

async function requestJson<T>(url: string, init?: RequestInit, signal?: AbortSignal, validate?: Validator<T>): Promise<T> {
  if (!navigator.onLine) throw new ApiError("offline", "当前处于离线状态，联网后再试一次。");
  let response: Response;
  try {
    response = await fetch(url, { ...init, signal, headers: { "content-type": "application/json", ...(init?.headers ?? {}) } });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") throw new ApiError("cancelled", "请求已取消，已保留上一次成功结果。");
    throw new ApiError("proxy_unreachable", "暂时无法连接服务，请检查网络或代理部署。");
  }
  let body: unknown;
  try {
    body = JSON.parse(await response.text());
  } catch {
    throw new ApiError("invalid_response", "服务返回了无效数据。", response.status);
  }
  if (!response.ok) {
    const message = isRecord(body) && typeof body.message === "string" ? body.message : `请求失败（${response.status}）`;
    const code = isRecord(body) && typeof body.error === "string" ? body.error : "";
    const kind: ApiErrorKind = response.status === 401 || response.status === 403 ? "auth"
      : response.status === 408 || response.status === 504 ? "timeout"
      : code === "proxy_route_missing" || response.status === 404 ? "proxy_unreachable"
      : response.status === 400 || code.includes("config") ? "config"
      : response.status >= 500 ? "upstream" : "invalid_response";
    throw new ApiError(kind, message, response.status);
  }
  if (validate && !validate(body)) throw new ApiError("invalid_response", "服务返回的数据结构不完整。", response.status);
  return body as T;
}

export class ApiGateway {
  constructor(private settings: DeveloperSettings) {}

  async cook(payload: RecommendationRequest, signal?: AbortSignal): Promise<CookRecommendationResponse> {
    const result = await (this.settings.enabled
      ? requestJson("/api/direct", { method: "POST", body: JSON.stringify({ operation: "cook", settings: this.settings, payload }) }, signal, isCookResponse)
      : requestJson("/api/backend/recommend/cook", { method: "POST", body: JSON.stringify(payload) }, signal, isCookResponse));
    if (payload.cookSource === "AI_GENERATED" && isDatabaseFallback(result)) {
      throw new ApiError("ai_unavailable", "AI 生成未返回有效结果，已阻止自动切换到菜谱库。");
    }
    return result;
  }

  cancel(requestId: string): Promise<{ cancelled: boolean; message: string }> {
    if (this.settings.enabled) return Promise.resolve({ cancelled: true, message: "本机请求已取消。" });
    return requestJson("/api/backend/recommend/cook/cancel", { method: "POST", body: JSON.stringify({ requestId }) }, undefined, (body): body is { cancelled: boolean; message: string } => isRecord(body) && typeof body.cancelled === "boolean" && typeof body.message === "string");
  }

  restaurants(payload: RecommendationRequest, signal?: AbortSignal): Promise<RestaurantRecommendationResponse> {
    return this.settings.enabled
      ? requestJson("/api/direct", { method: "POST", body: JSON.stringify({ operation: "restaurant", settings: this.settings, payload }) }, signal, isRestaurantResponse)
      : requestJson("/api/backend/recommend/restaurant", { method: "POST", body: JSON.stringify(payload) }, signal, isRestaurantResponse);
  }

  reverseGeocode(payload: ReverseGeocodeRequest, signal?: AbortSignal): Promise<ReverseGeocodeResponse> {
    return requestJson("/api/location/reverse", { method: "POST", body: JSON.stringify(payload) }, signal, isReverseGeocodeResponse);
  }

  recipe(id: string, signal?: AbortSignal): Promise<Recipe> {
    return this.settings.enabled
      ? requestJson("/api/direct", { method: "POST", body: JSON.stringify({ operation: "recipe", settings: this.settings, id }) }, signal, isRecipe)
      : requestJson(`/api/backend/recipes/${encodeURIComponent(id)}`, undefined, signal, isRecipe);
  }

  mealPlan(id: string, signal?: AbortSignal): Promise<MealPlan> {
    return requestJson(`/api/backend/meal-plans/${encodeURIComponent(id)}`, undefined, signal, isMealPlan);
  }

  status(signal?: AbortSignal): Promise<ServiceStatus> {
    return requestJson("/api/status", { method: "GET" }, signal, isServiceStatus);
  }
}
