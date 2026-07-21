import type { VercelRequest, VercelResponse } from "@vercel/node";
import type { DeveloperSettings, RecommendationRequest, Recipe, Restaurant, ReverseGeocodeRequest } from "../src/types.js";
import { fetchJson, isBodyAllowed, json, requirePost, safeHttpsUrl, safeMessage } from "../server/shared.js";

type DirectOperation = "cook" | "restaurant" | "recipe" | "reverseGeocode" | "status";
type DirectBody = { operation?: DirectOperation; settings?: DeveloperSettings; payload?: RecommendationRequest | ReverseGeocodeRequest; id?: string };

function stripCodeFence(value: string) {
  return value.trim().replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/, "");
}

function normalizeRecipe(raw: any, index: number): Recipe {
  return {
    id: String(raw.id || `direct_recipe_${Date.now()}_${index}`), name: String(raw.name || raw.title || "未命名菜谱"),
    cuisine: String(raw.cuisine || raw.category || "家常菜"), taste: Array.isArray(raw.taste) ? raw.taste.map(String) : [],
    tags: Array.isArray(raw.tags) ? raw.tags.map(String) : [], difficulty: String(raw.difficulty || "适中"), cookTime: String(raw.cookTime || raw.time || "时间以实际为准"),
    servings: String(raw.servings || "2 人份"), coverUrl: String(raw.coverUrl || raw.pic || ""), reason: String(raw.reason || "符合你的搜索需求。"),
    ingredients: Array.isArray(raw.ingredients) ? raw.ingredients.map((item: any) => typeof item === "string" ? { name: item, amount: "适量" } : { name: String(item.name || "食材"), amount: String(item.amount || "适量") }) : [],
    steps: Array.isArray(raw.steps) ? raw.steps.map((step: any) => String(step.text || step)) : [], tips: String(raw.tips || "按个人口味调整调味。"),
    ratingStars: Number(raw.ratingStars) || undefined, source: String(raw.source || "ai"), stepImageUrls: Array.isArray(raw.stepImageUrls) ? raw.stepImageUrls.map(String) : []
  };
}

function directStatus(settings: DeveloperSettings) {
  const missing: string[] = [];
  if (!settings.aiBaseUrl || !settings.aiApiKey || !settings.aiModel) missing.push("AI 配置");
  if (!settings.amapWebKey) missing.push("高德 Web Key");
  if (settings.recipeApiSource === "mxnzp" && (!settings.recipeApiAppId || !settings.recipeApiSecret)) missing.push("mxnzp 密钥");
  if (settings.recipeApiSource === "wanwei" && (!settings.recipeApiAppId || !settings.wanweiRecipeAppKey)) missing.push("万维易源密钥");
  if (settings.recipeApiSource === "custom" && (!settings.recipeApiBaseUrl || !settings.recipeApiAppId || !settings.recipeApiSecret)) missing.push("自定义菜谱配置");
  return { proxyReachable: true, backendConfigured: missing.length === 0, message: missing.length ? `开发者直连通道可用，尚缺少：${missing.join("、")}。` : "开发者直连通道和第三方配置均可用。" };
}

function validCoordinate(latitude: number, longitude: number): boolean {
  return Number.isFinite(latitude) && Number.isFinite(longitude) && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
}

function addressText(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function addressFromPayload(payload: Record<string, any>): string {
  const regeocode = payload.regeocode && typeof payload.regeocode === "object" ? payload.regeocode : {};
  const formatted = addressText(regeocode.formatted_address);
  if (formatted) return formatted;
  const component = regeocode.addressComponent && typeof regeocode.addressComponent === "object" ? regeocode.addressComponent : {};
  const street = component.streetNumber && typeof component.streetNumber === "object" ? component.streetNumber : {};
  return [component.province, component.city, component.district, component.township, street.street, street.number].map(addressText).filter(Boolean).join("");
}

async function reverseGeocode(settings: DeveloperSettings, payload: ReverseGeocodeRequest) {
  if (!settings.amapWebKey) throw new Error("missing_amap_key");
  if (!validCoordinate(payload.latitude, payload.longitude)) throw new Error("invalid_location");
  const endpoint = new URL("https://restapi.amap.com/v3/geocode/regeo");
  endpoint.searchParams.set("key", settings.amapWebKey);
  endpoint.searchParams.set("location", `${payload.longitude},${payload.latitude}`);
  endpoint.searchParams.set("extensions", "base");
  endpoint.searchParams.set("radius", "100");
  const result = await fetchJson(endpoint, {}, settings.maxWaitSeconds);
  const address = result?.status === "1" ? addressFromPayload(result) : "";
  if (!address) throw new Error("address_unavailable");
  return { location: { latitude: payload.latitude, longitude: payload.longitude, text: address } };
}

async function aiCook(settings: DeveloperSettings, payload: RecommendationRequest) {
  if (!settings.aiApiKey || !settings.aiModel) throw new Error("missing_ai_config");
  const base = safeHttpsUrl(settings.aiBaseUrl || "https://api.deepseek.com");
  const endpoint = new URL("v1/chat/completions", `${base.toString().replace(/\/$/, "")}/`);
  const result = await fetchJson(endpoint, {
    method: "POST", headers: { "content-type": "application/json", authorization: `Bearer ${settings.aiApiKey}` },
    body: JSON.stringify({ model: settings.aiModel, temperature: 0.6, response_format: { type: "json_object" }, messages: [
      { role: "system", content: "你是菜谱推荐助手。只返回 JSON，字段为 summary, mealPlans, recipes。菜谱字段需兼容 id,name,cuisine,taste,tags,difficulty,cookTime,servings,coverUrl,reason,ingredients,steps,tips。组合菜单字段需兼容 id,title,structure,cookTime,servings,coverUrl,tags,reason,dishes,shoppingList,timeline。不得编造真实餐厅。" },
      { role: "user", content: `需求：${payload.query || "请随机推荐"}\n模式：${payload.mode}\n偏好：${JSON.stringify(payload.preferences || {})}` }
    ] })
  }, settings.maxWaitSeconds);
  const content = result?.choices?.[0]?.message?.content;
  if (typeof content !== "string") throw new Error("invalid_ai_response");
  const parsed = JSON.parse(stripCodeFence(content));
  const recipes = Array.isArray(parsed.recipes) ? parsed.recipes.map(normalizeRecipe) : [];
  return { intent: payload.mode === "RECIPE_SINGLE" ? "RECIPE_SINGLE" : "RECIPE_COMBO", summary: String(parsed.summary || "已根据你的需求生成推荐。"), mealPlans: Array.isArray(parsed.mealPlans) ? parsed.mealPlans : [], recipes, source: "AI_GENERATED", totalMatches: recipes.length + (parsed.mealPlans?.length || 0) };
}

async function recipeSearch(settings: DeveloperSettings, payload: RecommendationRequest) {
  const source = settings.recipeApiSource;
  if (source === "mxnzp") {
    const endpoint = safeHttpsUrl(settings.recipeApiBaseUrl || "https://www.mxnzp.com/api/cookbook/search", ["www.mxnzp.com"]);
    endpoint.searchParams.set("keyword", payload.query || "家常菜"); endpoint.searchParams.set("page", "1"); endpoint.searchParams.set("app_id", settings.recipeApiAppId); endpoint.searchParams.set("app_secret", settings.recipeApiSecret);
    const result = await fetchJson(endpoint, {}, settings.maxWaitSeconds);
    const list = result?.data?.list || result?.data || [];
    const recipes = Array.isArray(list) ? list.slice(0, settings.recipePageSize).map((item: any, index: number) => normalizeRecipe({ ...item, id: `mxnzp_${item.id || index}`, name: item.name || item.title, coverUrl: item.pic, source: "mxnzp" }, index)) : [];
    return { intent: payload.mode, summary: "已从 mxnzp 菜谱库筛选结果。", mealPlans: [], recipes, source: "DATABASE", totalMatches: recipes.length };
  }
  const base = safeHttpsUrl(settings.recipeApiBaseUrl || "https://route.showapi.com", source === "wanwei" ? ["route.showapi.com"] : undefined);
  const endpoint = new URL("/1164-1", base); endpoint.searchParams.set("showapi_appid", settings.recipeApiAppId); endpoint.searchParams.set("showapi_sign", settings.wanweiRecipeAppKey || settings.recipeApiSecret); endpoint.searchParams.set("keyword", payload.query || "家常菜"); endpoint.searchParams.set("page", "1");
  const result = await fetchJson(endpoint, {}, settings.maxWaitSeconds);
  const list = result?.showapi_res_body?.data || result?.showapi_res_body?.list || [];
  const recipes = Array.isArray(list) ? list.slice(0, settings.recipePageSize).map((item: any, index: number) => normalizeRecipe({ ...item, source: source }, index)) : [];
  return { intent: payload.mode, summary: `已从${source === "wanwei" ? "万维" : "自定义"}菜谱源筛选结果。`, mealPlans: [], recipes, source: "DATABASE", totalMatches: recipes.length };
}

async function recipeDetail(settings: DeveloperSettings, id: string) {
  if (!id.startsWith("mxnzp_")) throw new Error("detail_not_supported");
  const endpoint = safeHttpsUrl("https://www.mxnzp.com/api/cookbook/details", ["www.mxnzp.com"]);
  endpoint.searchParams.set("id", id.replace(/^mxnzp_/, "")); endpoint.searchParams.set("app_id", settings.recipeApiAppId); endpoint.searchParams.set("app_secret", settings.recipeApiSecret);
  const result = await fetchJson(endpoint, {}, settings.maxWaitSeconds); const data = result?.data;
  if (!data) throw new Error("detail_not_found");
  return normalizeRecipe({ ...data, id, name: data.name || data.title, coverUrl: data.pic, source: "mxnzp", ingredients: data.materialList || data.ingredients, steps: data.stepList || data.steps, stepImageUrls: Array.isArray(data.stepList) ? data.stepList.map((step: any) => String(step.pic || step.image || "")) : [] }, 0);
}

async function restaurantSearch(settings: DeveloperSettings, payload: RecommendationRequest) {
  if (!settings.amapWebKey) throw new Error("missing_amap_key");
  let latitude = payload.location?.latitude; let longitude = payload.location?.longitude;
  if (latitude == null || longitude == null) {
    const geocode = new URL("https://restapi.amap.com/v3/geocode/geo"); geocode.searchParams.set("key", settings.amapWebKey); geocode.searchParams.set("address", payload.location?.text || "");
    const geo = await fetchJson(geocode, {}, settings.maxWaitSeconds); const parts = String(geo?.geocodes?.[0]?.location || "").split(","); longitude = Number(parts[0]); latitude = Number(parts[1]);
  }
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) throw new Error("location_unavailable");
  const around = new URL("https://restapi.amap.com/v3/place/around"); around.searchParams.set("key", settings.amapWebKey); around.searchParams.set("location", `${longitude},${latitude}`); around.searchParams.set("keywords", payload.query || "美食"); around.searchParams.set("types", "050000"); around.searchParams.set("radius", String(Math.min(10000, Math.max(500, Number(payload.preferences?.defaultDistanceKm || 5) * 1000)))); around.searchParams.set("offset", "20"); around.searchParams.set("extensions", "all");
  const result = await fetchJson(around, {}, settings.maxWaitSeconds);
  const restaurants: Restaurant[] = (Array.isArray(result?.pois) ? result.pois : []).slice(0, Number(payload.preferences?.restaurantResultLimit || 50)).map((poi: any) => {
    const [lng, lat] = String(poi.location || "").split(",").map(Number); const biz = poi.biz_ext || {}; const photo = Array.isArray(poi.photos) ? poi.photos[0]?.url : "";
    return { id: `amap_${poi.id}`, source: "amap", name: String(poi.name || "餐厅"), category: String(poi.type || "餐饮").split(";")[0], tags: [String(poi.type || "餐饮").split(";")[0]].filter(Boolean), address: String(poi.address || "地址以地图为准"), distance: poi.distance ? `${poi.distance}m` : "距离未知", rating: String(biz.rating || "暂无"), price: biz.cost ? `人均 ¥${biz.cost}` : "人均暂无", open: "营业状态以地图为准", phone: String(poi.tel || "电话暂无"), coverUrl: String(photo || ""), reason: "与搜索需求接近，距离也比较合适。", latitude: lat, longitude: lng };
  });
  return { restaurants, locationUsed: { latitude, longitude, text: payload.location?.text }, fallbackReason: restaurants.length ? null : "附近暂未找到符合条件的餐厅。" };
}

export default async function handler(request: VercelRequest, response: VercelResponse) {
  if (!requirePost(request, response)) return;
  if (!isBodyAllowed(request)) return json(response, 413, { error: "body_too_large", message: "请求内容过大。" });
  const body = request.body as DirectBody;
  const allowedOperations: DirectOperation[] = ["cook", "restaurant", "recipe", "reverseGeocode", "status"];
  const needsPayload = body.operation === "cook" || body.operation === "restaurant" || body.operation === "reverseGeocode";
  if (!body.settings || !body.operation || !allowedOperations.includes(body.operation) || (needsPayload && !body.payload) || (body.operation === "recipe" && !body.id)) return json(response, 400, { error: "invalid_request", message: "请求参数不完整。" });
  try {
    const result = body.operation === "status" ? directStatus(body.settings)
      : body.operation === "recipe" ? await recipeDetail(body.settings, body.id!)
      : body.operation === "reverseGeocode" ? await reverseGeocode(body.settings, body.payload as ReverseGeocodeRequest)
      : body.operation === "restaurant" ? await restaurantSearch(body.settings, body.payload as RecommendationRequest)
      : (body.payload as RecommendationRequest).cookSource === "AI_GENERATED" ? await aiCook(body.settings, body.payload as RecommendationRequest)
      : await recipeSearch(body.settings, body.payload as RecommendationRequest);
    json(response, 200, result);
  } catch (error) { json(response, 502, { error: "direct_error", message: error instanceof Error && error.message.startsWith("missing_") ? "开发者模式配置不完整，请检查密钥。" : safeMessage(error) }); }
}
