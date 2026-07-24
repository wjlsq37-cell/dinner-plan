import type { CookRecommendationResponse, DeveloperSettings, DishItem, MealPlan, Recipe, RecommendationRequest, Restaurant, RestaurantRecommendationResponse, ReverseGeocodeRequest, ReverseGeocodeResponse, ServiceStatus } from "../types";
import { ApiError } from "./api-error";

const BROAD_RECIPE_SEARCH_TERMS = ["家常菜", "鸡蛋", "豆腐", "鸡肉", "猪肉", "牛肉", "鱼", "青菜", "土豆", "茄子", "汤", "面", "饭"];
const BROAD_RESTAURANT_SEARCH_TERMS = ["餐厅", "中餐", "小吃", "快餐", "面馆", "粉面", "火锅", "烧烤", "日料", "西餐", "甜品", "咖啡"];

interface RestaurantKeywordPlan {
  keywords: string[];
  mustMatch: string[];
  preferMatch: string[];
  negativeMatch: string[];
}

interface RestaurantCandidate {
  id: string;
  name: string;
  type: string;
  address: string;
  distance: number;
  rating: string;
  cost: string;
  businessArea: string;
  tags: string[];
  description: string;
  coverUrl: string;
  latitude: number | null;
  longitude: number | null;
  phone: string;
}

function isRecord(value: unknown): value is Record<string, any> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function stripCodeFence(value: string): string {
  return value.trim().replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/, "");
}

function stringArray(value: unknown): string[] {
  if (Array.isArray(value)) return value.map(String).map((item) => item.trim()).filter(Boolean);
  if (typeof value === "string") return value.split(/[,，、\n]/).map((item) => item.trim()).filter(Boolean);
  return [];
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

async function requestAiJson(settings: DeveloperSettings, system: string, user: string, temperature: number, signal?: AbortSignal): Promise<any> {
  if (!settings.aiApiKey || !settings.aiModel) throw new ApiError("config", "请填写 AI API Key 和模型名称。");
  const result = await externalJson(aiEndpoint(settings), {
    method: "POST", headers: { "content-type": "application/json", authorization: `Bearer ${settings.aiApiKey}` },
    body: JSON.stringify({ model: settings.aiModel, temperature, response_format: { type: "json_object" }, messages: [
      { role: "system", content: system },
      { role: "user", content: user }
    ] })
  }, settings, signal);
  const content = result?.choices?.[0]?.message?.content;
  if (typeof content !== "string") throw new ApiError("invalid_response", "AI 没有返回可解析的内容。");
  try { return JSON.parse(stripCodeFence(content)); } catch { throw new ApiError("invalid_response", "AI 返回内容不是有效 JSON。"); }
}

async function requestAiCookGeneration(settings: DeveloperSettings, payload: RecommendationRequest, violatedTerms: string[] = [], signal?: AbortSignal): Promise<CookRecommendationResponse> {
  const avoids = (payload.preferences?.avoids ?? []).filter(Boolean).join("、") || "无";
  const tastes = (payload.preferences?.tastes ?? []).filter(Boolean).join("、") || "无";
  const system = [
    "你是家庭菜谱生成助手。只输出 JSON，不要解释。",
    "JSON 字段必须匹配 CookRecommendationResponse：intent、summary、mealPlans、recipes、source、totalMatches。",
    "必须避开用户忌口食材，并尽量符合用户偏好。",
    "recipes 中每道菜必须包含 name、cuisine、taste、tags、difficulty、cookTime、servings、coverUrl、reason、ingredients、steps、tips。"
  ].join("\n");
  const retryNote = violatedTerms.length > 0 ? `上一次结果命中了忌口：${violatedTerms.join("、")}。这次必须完全避开。` : "";
  const user = [
    `用户需求：${payload.query}`,
    `模式：${payload.mode}`,
    `忌口：${avoids}`,
    `偏好：${tastes}`,
    retryNote
  ].filter(Boolean).join("\n");
  const parsed = await requestAiJson(settings, system, user, 0.6, signal);
  const recipeValues = Array.isArray(parsed?.recipes) ? parsed.recipes : parsed?.recipe ? [parsed.recipe] : payload.mode === "RECIPE_SINGLE" && (parsed?.name || parsed?.title) ? [parsed] : [];
  const mealValues = Array.isArray(parsed?.mealPlans) ? parsed.mealPlans : parsed?.mealPlan ? [parsed.mealPlan] : payload.mode === "RECIPE_COMBO" && (parsed?.title || parsed?.name) ? [parsed] : [];
  const recipes = recipeValues.map((item: any, index: number) => normalizeRecipe(item, index, "ai_generated"));
  const mealPlans = mealValues.map(normalizeMealPlan);
  if (!recipes.length && !mealPlans.length) throw new ApiError("invalid_response", "AI 没有返回可展示的菜谱或菜单。");
  return { intent: payload.mode === "RECIPE_SINGLE" ? "RECIPE_SINGLE" : "RECIPE_COMBO", summary: String(parsed?.summary || "已根据你的需求生成推荐。"), mealPlans, recipes, source: "AI_GENERATED", totalMatches: recipes.length + mealPlans.length };
}

function expandAvoidTerms(avoids: string[]): string[] {
  const terms = avoids.flatMap((item) => item.split(/[\s,，、/;；]+/)).map((item) => item.trim()).filter(Boolean);
  const expanded = [...terms];
  if (terms.some((term) => term.includes("牛羊"))) expanded.push("牛肉", "羊肉", "牛", "羊");
  if (terms.includes("海鲜")) expanded.push("鱼", "虾", "蟹", "贝", "海鲜");
  return Array.from(new Set(expanded));
}

function recipeText(recipe: Recipe): string {
  return [
    recipe.name,
    recipe.reason,
    recipe.tips,
    recipe.ingredients.map((item) => `${item.name}${item.amount}`).join(" "),
    recipe.steps.join(" ")
  ].join(" ");
}

function violatedAvoidTerms(response: CookRecommendationResponse, avoids: string[]): string[] {
  const serialized = JSON.stringify(response);
  return expandAvoidTerms(avoids).filter((term) => serialized.includes(term));
}

function filterAvoidingTerms(response: CookRecommendationResponse, avoids: string[]): CookRecommendationResponse {
  const terms = expandAvoidTerms(avoids);
  if (terms.length === 0) return response;
  const recipes = response.recipes.filter((recipe) => !terms.some((term) => recipeText(recipe).includes(term)));
  const mealPlans = response.mealPlans.filter((plan) => !terms.some((term) => JSON.stringify(plan).includes(term)));
  return { ...response, recipes, mealPlans, totalMatches: recipes.length + mealPlans.length };
}

async function aiCook(settings: DeveloperSettings, payload: RecommendationRequest, signal?: AbortSignal): Promise<CookRecommendationResponse> {
  const first = await requestAiCookGeneration(settings, payload, [], signal);
  const violated = violatedAvoidTerms(first, payload.preferences?.avoids ?? []);
  let generation = first;
  if (violated.length > 0) {
    try {
      generation = await requestAiCookGeneration(settings, payload, violated, signal);
    } catch {
      generation = first;
    }
  }
  const filtered = filterAvoidingTerms(generation, payload.preferences?.avoids ?? []);
  if (!filtered.recipes.length && !filtered.mealPlans.length) throw new ApiError("invalid_response", "AI 推荐命中了忌口，暂时没有可展示结果。");
  return filtered;
}

async function searchRecipeSource(settings: DeveloperSettings, query: string, limit: number, signal?: AbortSignal): Promise<Recipe[]> {
  if (settings.recipeApiSource === "mxnzp") {
    if (!settings.recipeApiAppId || !settings.recipeApiSecret) throw new ApiError("config", "请填写 mxnzp app_id 和 app_secret。");
    const endpoint = safeExternalUrl(settings.recipeApiBaseUrl || "https://www.mxnzp.com/api/cookbook/search", ["www.mxnzp.com"]);
    endpoint.searchParams.set("keyword", query); endpoint.searchParams.set("page", "1"); endpoint.searchParams.set("app_id", settings.recipeApiAppId); endpoint.searchParams.set("app_secret", settings.recipeApiSecret);
    const result = await externalJson(endpoint, { method: "GET" }, settings, signal);
    const list = result?.data?.list || result?.data?.records || result?.data || [];
    return Array.isArray(list) ? list.slice(0, limit).map((item: any, index: number) => normalizeRecipe({ ...item, id: `mxnzp_${item.id || index}` }, index, "mxnzp")) : [];
  }
  if (!settings.recipeApiAppId || !(settings.wanweiRecipeAppKey || settings.recipeApiSecret)) throw new ApiError("config", "请填写菜谱接口 AppID 和密钥。");
  const source = settings.recipeApiSource;
  const base = safeExternalUrl(settings.recipeApiBaseUrl || "https://route.showapi.com", source === "wanwei" ? ["route.showapi.com"] : undefined);
  const endpoint = new URL("/1164-1", base); endpoint.searchParams.set("showapi_appid", settings.recipeApiAppId); endpoint.searchParams.set("showapi_sign", settings.wanweiRecipeAppKey || settings.recipeApiSecret); endpoint.searchParams.set("keyword", query); endpoint.searchParams.set("page", "1");
  const result = await externalJson(endpoint, { method: "GET" }, settings, signal);
  const body = result?.showapi_res_body || result?.data || result;
  const list = body?.data || body?.list || body?.pagebean?.contentlist || [];
  return Array.isArray(list) ? list.slice(0, limit).map((item: any, index: number) => normalizeRecipe(item, index, source)) : [];
}

function distinctRecipes(recipes: Recipe[]): Recipe[] {
  const seen = new Set<string>();
  return recipes.filter((recipe) => {
    const key = recipe.id || recipe.name;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

async function recipeSearch(settings: DeveloperSettings, payload: RecommendationRequest, signal?: AbortSignal): Promise<CookRecommendationResponse> {
  const intent = payload.mode === "RECIPE_COMBO" ? "RECIPE_COMBO" : "RECIPE_SINGLE";
  if (!payload.broadSearch) {
    const recipes = await searchRecipeSource(settings, payload.query || "家常菜", settings.recipePageSize, signal);
    const sourceName = settings.recipeApiSource === "mxnzp" ? "mxnzp" : settings.recipeApiSource === "wanwei" ? "万维" : "自定义";
    return { intent, summary: `已从${sourceName}菜谱源筛选结果。`, mealPlans: [], recipes, source: "DATABASE", totalMatches: recipes.length };
  }

  const recipes: Recipe[] = [];
  let failures = 0;
  for (const term of BROAD_RECIPE_SEARCH_TERMS) {
    try {
      recipes.push(...await searchRecipeSource(settings, term, 50, signal));
      if (distinctRecipes(recipes).length >= 50) break;
    } catch {
      failures += 1;
    }
  }
  const distinct = distinctRecipes(recipes).slice(0, 50);
  return {
    intent,
    summary: distinct.length ? `已为你随机扩大候选范围，找到 ${distinct.length} 道菜谱。` : "暂时没找到合适的结果，可以换个关键词或放宽条件再试。",
    mealPlans: [],
    recipes: distinct,
    fallbackReason: distinct.length ? null : failures ? "菜谱服务暂时不可用，请稍后再试。" : "暂时没找到合适的结果，可以换个关键词或放宽条件再试。",
    source: "DATABASE",
    totalMatches: distinct.length
  };
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

function fallbackRestaurantKeywordPlan(query: string): RestaurantKeywordPlan {
  if (/日料|日本|寿司|放题|自助/.test(query)) {
    return {
      keywords: ["日料自助", "日本料理", "日式放题", "寿司", "自助餐"],
      mustMatch: query.includes("自助") || query.includes("放题") ? ["自助", "放题"] : [],
      preferMatch: ["日料", "日本料理", "寿司", "刺身", "日式"],
      negativeMatch: []
    };
  }
  if (/娃|孩子|儿童|亲子|家庭/.test(query)) {
    return {
      keywords: ["亲子餐厅", "儿童友好", "商场餐饮", "家庭餐厅", "清淡餐厅"],
      mustMatch: [],
      preferMatch: ["亲子", "儿童", "家庭", "商场", "清淡"],
      negativeMatch: ["酒吧", "夜店"]
    };
  }
  if (/清淡|不辣|少油|舒服/.test(query)) {
    return {
      keywords: ["清淡餐厅", "粥", "汤", "家常菜", "轻食"],
      mustMatch: [],
      preferMatch: ["清淡", "粥", "汤", "轻食", "家常"],
      negativeMatch: ["麻辣", "烧烤", "火锅"]
    };
  }
  const clean = query.trim() || "餐厅";
  return { keywords: [clean, "餐厅"], mustMatch: [], preferMatch: [clean], negativeMatch: [] };
}

async function restaurantKeywordPlan(settings: DeveloperSettings, payload: RecommendationRequest, signal?: AbortSignal): Promise<RestaurantKeywordPlan> {
  if (payload.broadSearch) {
    return { keywords: BROAD_RESTAURANT_SEARCH_TERMS, mustMatch: [], preferMatch: BROAD_RESTAURANT_SEARCH_TERMS, negativeMatch: [] };
  }
  if (!settings.aiApiKey || !settings.aiModel) return fallbackRestaurantKeywordPlan(payload.query);
  const system = [
    "你是餐厅搜索关键词解析助手。只输出 JSON，不要解释。",
    "字段必须是 summary、keywords、mustMatch、preferMatch、negativeMatch、searchStrategy。",
    "keywords 是要分别用于地图搜索的关键词，不要把所有词拼成一个长关键词。",
    "searchStrategy 固定输出 separate。"
  ].join("\n");
  try {
    const parsed = await requestAiJson(settings, system, `用户附近吃需求：${payload.query}`, 0.2, signal);
    const keywords = stringArray(parsed?.keywords).filter(Boolean);
    return {
      keywords: keywords.length ? keywords.slice(0, 12) : ["餐厅"],
      mustMatch: stringArray(parsed?.mustMatch),
      preferMatch: stringArray(parsed?.preferMatch),
      negativeMatch: stringArray(parsed?.negativeMatch)
    };
  } catch {
    return fallbackRestaurantKeywordPlan(payload.query);
  }
}

function restaurantCandidate(raw: any): RestaurantCandidate | null {
  const name = String(raw?.name || "").trim();
  if (!name) return null;
  const biz = isRecord(raw?.biz_ext) ? raw.biz_ext : {};
  const photos = Array.isArray(raw?.photos) ? raw.photos : [];
  const [longitude, latitude] = String(raw?.location || "").split(",").map(Number);
  const type = String(raw?.type || "餐饮");
  return {
    id: String(raw?.id || ""),
    name,
    type,
    address: String(raw?.address || ""),
    distance: Number(String(raw?.distance || "")) || 999999,
    rating: String(biz.rating || "暂无"),
    cost: String(biz.cost || ""),
    businessArea: String(raw?.business_area || ""),
    tags: type.split(";").filter(Boolean).slice(0, 3),
    description: String(raw?.tag || ""),
    coverUrl: String(photos[0]?.url || ""),
    latitude: Number.isFinite(latitude) ? latitude : null,
    longitude: Number.isFinite(longitude) ? longitude : null,
    phone: String(raw?.tel || "")
  };
}

function restaurantScore(candidate: RestaurantCandidate, query: string, plan: RestaurantKeywordPlan): number {
  const text = [candidate.name, candidate.type, candidate.address, candidate.businessArea, candidate.tags.join(" "), candidate.description].join(" ");
  let value = 0;
  for (const term of plan.mustMatch) if (term && text.includes(term)) value += 40;
  for (const term of plan.preferMatch) if (term && text.includes(term)) value += 16;
  for (const term of plan.negativeMatch) if (term && text.includes(term)) value -= 30;
  for (const term of query.split(/[\s,，、/]+/)) if (term && text.includes(term)) value += 8;
  const rating = Number(candidate.rating);
  if (Number.isFinite(rating)) value += rating * 3;
  value += Math.max(0, 10 - candidate.distance / 1000);
  return value;
}

function parseRadiusMeters(query: string, defaultKm: number): number {
  const km = query.match(/(\d+(?:\.\d+)?)\s*km/i)?.[1] ?? query.match(/(\d+(?:\.\d+)?)\s*公里/)?.[1];
  if (km) return Math.round(Number(km) * 1000);
  const meters = query.match(/(\d+)\s*m/i)?.[1] ?? query.match(/(\d+)\s*米/)?.[1];
  if (meters) return Number(meters);
  return Math.max(1, Math.min(10, defaultKm)) * 1000;
}

function restaurantDto(candidate: RestaurantCandidate): Restaurant {
  return {
    id: `amap_${candidate.id || `${candidate.name}_${candidate.address}`}`,
    source: "amap",
    name: candidate.name,
    category: candidate.type.split(";")[0] || "餐饮",
    tags: candidate.tags,
    address: candidate.address,
    distance: candidate.distance === 999999 ? "" : `${candidate.distance}m`,
    rating: candidate.rating,
    price: candidate.cost ? `人均 ¥${candidate.cost}` : "人均暂无",
    open: "营业状态以门店为准",
    phone: candidate.phone,
    coverUrl: candidate.coverUrl,
    reason: "这家店和你的搜索更接近，距离也比较合适。",
    latitude: candidate.latitude,
    longitude: candidate.longitude
  };
}

export async function developerRestaurants(settings: DeveloperSettings, payload: RecommendationRequest, signal?: AbortSignal): Promise<RestaurantRecommendationResponse> {
  if (!settings.amapWebKey) throw new ApiError("config", "请填写高德 Web Key。");
  let latitude = coordinate(payload.location?.latitude); let longitude = coordinate(payload.location?.longitude);
  const locationText = payload.location?.text?.trim() || "上海人民广场";
  if (latitude == null || longitude == null) {
    const geocode = safeExternalUrl("https://restapi.amap.com/v3/geocode/geo", ["restapi.amap.com"]); geocode.searchParams.set("key", settings.amapWebKey); geocode.searchParams.set("address", locationText);
    const geo = await externalJson(geocode, { method: "GET" }, settings, signal); const parts = String(geo?.geocodes?.[0]?.location || "").split(","); longitude = coordinate(parts[0]); latitude = coordinate(parts[1]);
  }
  if (latitude == null || longitude == null) throw new ApiError("invalid_response", "高德没有返回有效位置。");
  const plan = await restaurantKeywordPlan(settings, payload, signal);
  const radius = Math.max(500, Math.min(10000, parseRadiusMeters(payload.query, payload.preferences?.defaultDistanceKm ?? 5)));
  const merged = new Map<string, RestaurantCandidate>();
  for (const keyword of plan.keywords.filter(Boolean)) {
    const around = safeExternalUrl("https://restapi.amap.com/v3/place/around", ["restapi.amap.com"]);
    around.searchParams.set("key", settings.amapWebKey);
    around.searchParams.set("location", `${longitude},${latitude}`);
    around.searchParams.set("keywords", keyword);
    around.searchParams.set("types", "050000");
    around.searchParams.set("radius", String(radius));
    around.searchParams.set("offset", "20");
    around.searchParams.set("page", "1");
    around.searchParams.set("extensions", "all");
    const result = await externalJson(around, { method: "GET" }, settings, signal);
    if (result?.status != null && String(result.status) !== "1") continue;
    for (const raw of Array.isArray(result?.pois) ? result.pois : []) {
      const candidate = restaurantCandidate(raw);
      if (!candidate) continue;
      const key = candidate.id || `${candidate.name}|${candidate.address}`;
      if (!merged.has(key)) merged.set(key, candidate);
    }
  }
  const limit = Math.max(1, Math.min(50, payload.preferences?.restaurantResultLimit ?? 50));
  const restaurants = [...merged.values()]
    .sort((left, right) => restaurantScore(right, payload.query, plan) - restaurantScore(left, payload.query, plan) || left.distance - right.distance)
    .slice(0, limit)
    .map(restaurantDto);
  return { restaurants, locationUsed: { latitude, longitude, text: locationText }, fallbackReason: restaurants.length ? null : "附近餐厅数据暂时加载失败，请稍后再试。" };
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
