import type { CookRecommendationResponse, DeveloperSettings, DishItem, MealPlan, Recipe, RecommendationRequest, Restaurant, RestaurantRecommendationResponse, ReverseGeocodeRequest, ReverseGeocodeResponse, ServiceStatus } from "../types";
import { ApiError } from "./api-error";

function isRecord(value: unknown): value is Record<string, any> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function stripCodeFence(value: string): string {
  return value.trim().replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/, "");
}

function safeExternalUrl(value: string, allowedHosts?: string[]): URL {
  let url: URL;
  try { url = new URL(value); } catch { throw new ApiError("config", "开发者接口地址无效。"); }
  if (url.protocol !== "https:" || url.username || url.password) throw new ApiError("config", "开发者接口必须使用不含凭据的 HTTPS 地址。");
  const host = url.hostname.toLowerCase().replace(/\.$/, "");
  const blockedName = host === "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal") || host === "0.0.0.0" || host === "::1";
  const ipv4 = host.match(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/)?.slice(1).map(Number);
  const blockedIpv4 = ipv4 && (ipv4.some((part) => part > 255) || ipv4[0] === 10 || ipv4[0] === 127 || ipv4[0] === 0 || (ipv4[0] === 169 && ipv4[1] === 254) || (ipv4[0] === 172 && ipv4[1] >= 16 && ipv4[1] <= 31) || (ipv4[0] === 192 && ipv4[1] === 168));
  if (blockedName || blockedIpv4 || (allowedHosts && !allowedHosts.includes(host))) throw new ApiError("config", "开发者接口地址不在允许范围内。");
  return url;
}

async function externalJson(url: URL, init: RequestInit, settings: DeveloperSettings, signal?: AbortSignal): Promise<any> {
  if (!navigator.onLine) throw new ApiError("offline", "当前处于离线状态。");
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort("timeout"), Math.min(300, Math.max(10, settings.maxWaitSeconds)) * 1000);
  const abort = () => controller.abort("cancelled");
  signal?.addEventListener("abort", abort, { once: true });
  try {
    const response = await fetch(url, { ...init, mode: "cors", credentials: "omit", cache: "no-store", signal: controller.signal });
    const raw = await response.text();
    let body: any;
    try { body = JSON.parse(raw); } catch { throw new ApiError("invalid_response", "第三方服务没有返回 JSON。", response.status); }
    if (!response.ok) {
      const message = String(body?.message || body?.error?.message || body?.error || `第三方请求失败（${response.status}）`);
      throw new ApiError(response.status === 401 || response.status === 403 ? "auth" : response.status === 408 || response.status === 504 ? "timeout" : response.status === 400 ? "config" : "upstream", message, response.status);
    }
    return body;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (signal?.aborted) throw new ApiError("cancelled", "请求已取消。");
    if (controller.signal.aborted) throw new ApiError("timeout", "第三方服务响应超时。");
    throw new ApiError("cors", "浏览器无法直连第三方接口，请确认该服务允许网页跨域访问。");
  } finally {
    window.clearTimeout(timeout);
    signal?.removeEventListener("abort", abort);
  }
}

function normalizeRecipe(raw: any, index: number, source = "developer"): Recipe {
  const ingredients = Array.isArray(raw?.ingredients) ? raw.ingredients : Array.isArray(raw?.materialList) ? raw.materialList : [];
  const steps = Array.isArray(raw?.steps) ? raw.steps : Array.isArray(raw?.stepList) ? raw.stepList : [];
  return {
    id: String(raw?.id || `developer_recipe_${Date.now()}_${index}`),
    name: String(raw?.name || raw?.title || "未命名菜谱"),
    cuisine: String(raw?.cuisine || raw?.category || "家常菜"),
    taste: Array.isArray(raw?.taste) ? raw.taste.map(String) : [],
    tags: Array.isArray(raw?.tags) ? raw.tags.map(String) : [],
    difficulty: String(raw?.difficulty || "适中"),
    cookTime: String(raw?.cookTime || raw?.time || "时间以实际为准"),
    servings: String(raw?.servings || "2 人份"),
    coverUrl: String(raw?.coverUrl || raw?.pic || ""),
    reason: String(raw?.reason || "符合你的搜索需求。"),
    ingredients: ingredients.map((item: any) => typeof item === "string" ? { name: item, amount: "适量" } : { name: String(item?.name || item?.material || "食材"), amount: String(item?.amount || item?.quantity || "适量") }),
    steps: steps.map((step: any) => String(step?.text || step?.content || step?.description || step)),
    tips: String(raw?.tips || "按个人口味调整调味。"),
    ratingStars: Number(raw?.ratingStars) || undefined,
    source: String(raw?.source || source),
    stepImageUrls: Array.isArray(raw?.stepImageUrls) ? raw.stepImageUrls.map(String) : steps.map((step: any) => String(step?.pic || step?.image || "")).filter(Boolean)
  };
}

function normalizeMealPlan(raw: any, index: number): MealPlan {
  const dishes: DishItem[] = (Array.isArray(raw?.dishes) ? raw.dishes : []).map((dish: any, dishIndex: number) => ({
    course: String(dish?.course || `菜品 ${dishIndex + 1}`), name: String(dish?.name || dish?.title || "未命名菜品"),
    note: String(dish?.note || "按计划准备"), badge: String(dish?.badge || "推荐"), recipeId: dish?.recipeId ? String(dish.recipeId) : undefined
  }));
  return {
    id: String(raw?.id || `developer_meal_${Date.now()}_${index}`), title: String(raw?.title || raw?.name || "今日组合菜单"),
    structure: String(raw?.structure || dishes.map((dish) => dish.course).join(" · ") || "组合菜单"), cookTime: String(raw?.cookTime || raw?.time || "约 60 分钟"),
    servings: String(raw?.servings || "2–3 人份"), coverUrl: String(raw?.coverUrl || ""), tags: Array.isArray(raw?.tags) ? raw.tags.map(String) : [],
    reason: String(raw?.reason || "根据你的需求搭配。"), dishes, shoppingList: Array.isArray(raw?.shoppingList) ? raw.shoppingList.map(String) : [], timeline: Array.isArray(raw?.timeline) ? raw.timeline.map(String) : []
  };
}

function aiEndpoint(settings: DeveloperSettings): URL {
  const base = safeExternalUrl(settings.aiBaseUrl || "https://api.deepseek.com");
  const path = base.pathname.replace(/\/$/, "");
  base.pathname = `${path.endsWith("/v1") ? path : `${path}/v1`}/chat/completions`.replace(/\/+/g, "/");
  return base;
}

async function aiCook(settings: DeveloperSettings, payload: RecommendationRequest, signal?: AbortSignal): Promise<CookRecommendationResponse> {
  if (!settings.aiApiKey || !settings.aiModel) throw new ApiError("config", "请填写 AI API Key 和模型名称。");
  const result = await externalJson(aiEndpoint(settings), {
    method: "POST", headers: { "content-type": "application/json", authorization: `Bearer ${settings.aiApiKey}` },
    body: JSON.stringify({ model: settings.aiModel, temperature: 0.6, response_format: { type: "json_object" }, messages: [
      { role: "system", content: "你是菜谱推荐助手。只返回 JSON 对象。单菜模式返回 summary 和 recipes 数组；组合菜单模式返回 summary 和 mealPlans 数组。recipes 每项包含 name,cuisine,taste,tags,difficulty,cookTime,servings,reason,ingredients,steps,tips；mealPlans 每项包含 title,structure,cookTime,servings,tags,reason,dishes,shoppingList,timeline。至少返回一条可展示内容。" },
      { role: "user", content: `需求：${payload.query || "请随机推荐"}\n模式：${payload.mode}\n偏好：${JSON.stringify(payload.preferences || {})}` }
    ] })
  }, settings, signal);
  const content = result?.choices?.[0]?.message?.content;
  if (typeof content !== "string") throw new ApiError("invalid_response", "AI 没有返回可解析的内容。");
  let parsed: any;
  try { parsed = JSON.parse(stripCodeFence(content)); } catch { throw new ApiError("invalid_response", "AI 返回内容不是有效 JSON。"); }
  const recipeValues = Array.isArray(parsed?.recipes) ? parsed.recipes : parsed?.recipe ? [parsed.recipe] : payload.mode === "RECIPE_SINGLE" && (parsed?.name || parsed?.title) ? [parsed] : [];
  const mealValues = Array.isArray(parsed?.mealPlans) ? parsed.mealPlans : parsed?.mealPlan ? [parsed.mealPlan] : payload.mode === "RECIPE_COMBO" && (parsed?.title || parsed?.name) ? [parsed] : [];
  const recipes = recipeValues.map((item: any, index: number) => normalizeRecipe(item, index, "ai_generated"));
  const mealPlans = mealValues.map(normalizeMealPlan);
  if (!recipes.length && !mealPlans.length) throw new ApiError("invalid_response", "AI 没有返回可展示的菜谱或菜单。");
  return { intent: payload.mode === "RECIPE_SINGLE" ? "RECIPE_SINGLE" : "RECIPE_COMBO", summary: String(parsed?.summary || "已根据你的需求生成推荐。"), mealPlans, recipes, source: "AI_GENERATED", totalMatches: recipes.length + mealPlans.length };
}

async function recipeSearch(settings: DeveloperSettings, payload: RecommendationRequest, signal?: AbortSignal): Promise<CookRecommendationResponse> {
  if (settings.recipeApiSource === "mxnzp") {
    if (!settings.recipeApiAppId || !settings.recipeApiSecret) throw new ApiError("config", "请填写 mxnzp app_id 和 app_secret。");
    const endpoint = safeExternalUrl(settings.recipeApiBaseUrl || "https://www.mxnzp.com/api/cookbook/search", ["www.mxnzp.com"]);
    endpoint.searchParams.set("keyword", payload.query || "家常菜"); endpoint.searchParams.set("page", "1"); endpoint.searchParams.set("app_id", settings.recipeApiAppId); endpoint.searchParams.set("app_secret", settings.recipeApiSecret);
    const result = await externalJson(endpoint, { method: "GET" }, settings, signal);
    const list = result?.data?.list || result?.data?.records || result?.data || [];
    const recipes = Array.isArray(list) ? list.slice(0, settings.recipePageSize).map((item: any, index: number) => normalizeRecipe({ ...item, id: `mxnzp_${item.id || index}` }, index, "mxnzp")) : [];
    return { intent: payload.mode === "RECIPE_COMBO" ? "RECIPE_COMBO" : "RECIPE_SINGLE", summary: "已从 mxnzp 菜谱库筛选结果。", mealPlans: [], recipes, source: "DATABASE", totalMatches: recipes.length };
  }
  if (!settings.recipeApiAppId || !(settings.wanweiRecipeAppKey || settings.recipeApiSecret)) throw new ApiError("config", "请填写菜谱接口 AppID 和密钥。");
  const source = settings.recipeApiSource;
  const base = safeExternalUrl(settings.recipeApiBaseUrl || "https://route.showapi.com", source === "wanwei" ? ["route.showapi.com"] : undefined);
  const endpoint = new URL("/1164-1", base); endpoint.searchParams.set("showapi_appid", settings.recipeApiAppId); endpoint.searchParams.set("showapi_sign", settings.wanweiRecipeAppKey || settings.recipeApiSecret); endpoint.searchParams.set("keyword", payload.query || "家常菜"); endpoint.searchParams.set("page", "1");
  const result = await externalJson(endpoint, { method: "GET" }, settings, signal);
  const body = result?.showapi_res_body || result?.data || result;
  const list = body?.data || body?.list || body?.pagebean?.contentlist || [];
  const recipes = Array.isArray(list) ? list.slice(0, settings.recipePageSize).map((item: any, index: number) => normalizeRecipe(item, index, source)) : [];
  return { intent: payload.mode === "RECIPE_COMBO" ? "RECIPE_COMBO" : "RECIPE_SINGLE", summary: `已从${source === "wanwei" ? "万维" : "自定义"}菜谱源筛选结果。`, mealPlans: [], recipes, source: "DATABASE", totalMatches: recipes.length };
}

export function developerCook(settings: DeveloperSettings, payload: RecommendationRequest, signal?: AbortSignal): Promise<CookRecommendationResponse> {
  return payload.cookSource === "AI_GENERATED" ? aiCook(settings, payload, signal) : recipeSearch(settings, payload, signal);
}

export async function developerRecipe(settings: DeveloperSettings, id: string, signal?: AbortSignal): Promise<Recipe> {
  if (!id.startsWith("mxnzp_")) throw new ApiError("config", "当前开发者菜谱源不支持远程详情，请使用已缓存内容。");
  if (!settings.recipeApiAppId || !settings.recipeApiSecret) throw new ApiError("config", "请填写 mxnzp app_id 和 app_secret。");
  const endpoint = safeExternalUrl("https://www.mxnzp.com/api/cookbook/details", ["www.mxnzp.com"]);
  endpoint.searchParams.set("id", id.replace(/^mxnzp_/, "")); endpoint.searchParams.set("app_id", settings.recipeApiAppId); endpoint.searchParams.set("app_secret", settings.recipeApiSecret);
  const result = await externalJson(endpoint, { method: "GET" }, settings, signal);
  if (!result?.data) throw new ApiError("invalid_response", "菜谱详情不存在。");
  return normalizeRecipe({ ...result.data, id }, 0, "mxnzp");
}

function coordinate(value: unknown): number | undefined {
  const number = Number(value); return Number.isFinite(number) ? number : undefined;
}

export async function developerRestaurants(settings: DeveloperSettings, payload: RecommendationRequest, signal?: AbortSignal): Promise<RestaurantRecommendationResponse> {
  if (!settings.amapWebKey) throw new ApiError("config", "请填写高德 Web Key。");
  let latitude = coordinate(payload.location?.latitude); let longitude = coordinate(payload.location?.longitude);
  if (latitude == null || longitude == null) {
    const geocode = safeExternalUrl("https://restapi.amap.com/v3/geocode/geo", ["restapi.amap.com"]); geocode.searchParams.set("key", settings.amapWebKey); geocode.searchParams.set("address", payload.location?.text || "");
    const geo = await externalJson(geocode, { method: "GET" }, settings, signal); const parts = String(geo?.geocodes?.[0]?.location || "").split(","); longitude = coordinate(parts[0]); latitude = coordinate(parts[1]);
  }
  if (latitude == null || longitude == null) throw new ApiError("invalid_response", "高德没有返回有效位置。");
  const around = safeExternalUrl("https://restapi.amap.com/v3/place/around", ["restapi.amap.com"]); around.searchParams.set("key", settings.amapWebKey); around.searchParams.set("location", `${longitude},${latitude}`); around.searchParams.set("keywords", payload.query || "美食"); around.searchParams.set("types", "050000"); around.searchParams.set("radius", String(Math.min(10000, Math.max(500, Number(payload.preferences?.defaultDistanceKm || 5) * 1000)))); around.searchParams.set("offset", "20"); around.searchParams.set("extensions", "all");
  const result = await externalJson(around, { method: "GET" }, settings, signal);
  const restaurants: Restaurant[] = (Array.isArray(result?.pois) ? result.pois : []).slice(0, Number(payload.preferences?.restaurantResultLimit || 50)).map((poi: any) => {
    const [lng, lat] = String(poi.location || "").split(",").map(Number); const biz = poi.biz_ext || {}; const photo = Array.isArray(poi.photos) ? poi.photos[0]?.url : "";
    return { id: `amap_${poi.id}`, source: "amap", name: String(poi.name || "餐厅"), category: String(poi.type || "餐饮").split(";")[0], tags: [String(poi.type || "餐饮").split(";")[0]].filter(Boolean), address: String(poi.address || "地址以地图为准"), distance: poi.distance ? `${poi.distance}m` : "距离未知", rating: String(biz.rating || "暂无"), price: biz.cost ? `人均 ¥${biz.cost}` : "人均暂无", open: "营业状态以地图为准", phone: String(poi.tel || "电话暂无"), coverUrl: String(photo || ""), reason: "与搜索需求接近。", latitude: lat, longitude: lng };
  });
  return { restaurants, locationUsed: { latitude, longitude, text: payload.location?.text }, fallbackReason: restaurants.length ? null : "附近暂未找到符合条件的餐厅。" };
}

function addressFromAmap(payload: any): string {
  const regeocode = isRecord(payload?.regeocode) ? payload.regeocode : {};
  if (typeof regeocode.formatted_address === "string" && regeocode.formatted_address.trim()) return regeocode.formatted_address.trim();
  const component = isRecord(regeocode.addressComponent) ? regeocode.addressComponent : {};
  return [component.province, component.city, component.district, component.township].filter((value) => typeof value === "string").join("");
}

export async function developerReverseGeocode(settings: DeveloperSettings, payload: ReverseGeocodeRequest, signal?: AbortSignal): Promise<ReverseGeocodeResponse> {
  if (!settings.amapWebKey) throw new ApiError("config", "请填写高德 Web Key。");
  if (!Number.isFinite(payload.latitude) || !Number.isFinite(payload.longitude) || Math.abs(payload.latitude) > 90 || Math.abs(payload.longitude) > 180) throw new ApiError("config", "定位坐标无效。");
  const endpoint = safeExternalUrl("https://restapi.amap.com/v3/geocode/regeo", ["restapi.amap.com"]); endpoint.searchParams.set("key", settings.amapWebKey); endpoint.searchParams.set("location", `${payload.longitude},${payload.latitude}`); endpoint.searchParams.set("extensions", "base"); endpoint.searchParams.set("radius", "100");
  const result = await externalJson(endpoint, { method: "GET" }, settings, signal); const text = addressFromAmap(result);
  if (!text) throw new ApiError("invalid_response", "高德没有返回地址名称。");
  return { location: { latitude: payload.latitude, longitude: payload.longitude, text } };
}

export function developerStatus(settings: DeveloperSettings): ServiceStatus {
  const missing: string[] = [];
  if (!settings.aiBaseUrl || !settings.aiApiKey || !settings.aiModel) missing.push("AI 配置");
  if (!settings.amapWebKey) missing.push("高德 Web Key");
  if (settings.recipeApiSource === "mxnzp" && (!settings.recipeApiAppId || !settings.recipeApiSecret)) missing.push("mxnzp 密钥");
  if (settings.recipeApiSource === "wanwei" && (!settings.recipeApiAppId || !settings.wanweiRecipeAppKey)) missing.push("万维易源密钥");
  if (settings.recipeApiSource === "custom" && (!settings.recipeApiBaseUrl || !settings.recipeApiAppId || !settings.recipeApiSecret)) missing.push("自定义菜谱配置");
  return { proxyReachable: true, backendConfigured: missing.length === 0, message: missing.length ? `浏览器直连已启用，尚缺少：${missing.join("、")}。` : "浏览器直连已启用，配置完整；请求不会经过 Vercel。" };
}
