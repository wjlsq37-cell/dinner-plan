import { broadRestaurantKeywordPlan, parseRestaurantKeywordPlan, type RestaurantKeywordPlan } from "./ai.js";
import type { VercelApiConfig } from "./config.js";
import type { LocationDto, RecommendationRequest, RestaurantDto, RestaurantRecommendationResponse } from "./types.js";

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
  photoTitles: string[];
  coverUrl: string;
  latitude: number | null;
  longitude: number | null;
  phone: string;
}

export async function recommendRestaurantsFromAmap(
  request: RecommendationRequest,
  config: VercelApiConfig
): Promise<RestaurantRecommendationResponse> {
  if (!config.amapWebKey) {
    return { restaurants: [], locationUsed: request.location ?? null, fallbackReason: "相关服务还没配置好，请到设置中检查后再试。" };
  }
  const location = await resolveLocation(request.location ?? null, config);
  if (!location?.latitude || !location.longitude) {
    return { restaurants: [], locationUsed: request.location ?? null, fallbackReason: "暂时无法获取当前位置，你也可以手动输入地点。" };
  }
  const plan = request.broadSearch ? broadRestaurantKeywordPlan() : await parseRestaurantKeywordPlan(request.query, config);
  const radiusMeters = parseRadiusMeters(request.query, request.preferences?.defaultDistanceKm ?? 5);
  const candidates = await searchCandidates(plan, location, radiusMeters, config).catch(() => []);
  if (candidates.length === 0) {
    return {
      restaurants: [],
      locationUsed: location,
      fallbackReason: "附近餐厅数据暂时加载失败，请稍后再试。"
    };
  }
  const limit = Math.max(1, Math.min(50, request.preferences?.restaurantResultLimit ?? 50));
  return {
    restaurants: rankCandidates(request.query, plan, candidates).slice(0, limit).map(toRestaurantDto),
    locationUsed: location,
    fallbackReason: null
  };
}

async function searchCandidates(
  plan: RestaurantKeywordPlan,
  location: LocationDto,
  radiusMeters: number,
  config: VercelApiConfig
): Promise<RestaurantCandidate[]> {
  const merged = new Map<string, RestaurantCandidate>();
  for (const keyword of plan.keywords.filter(Boolean)) {
    const url = new URL("https://restapi.amap.com/v3/place/around");
    url.searchParams.set("key", config.amapWebKey);
    url.searchParams.set("location", `${location.longitude},${location.latitude}`);
    url.searchParams.set("keywords", keyword);
    url.searchParams.set("types", "050000");
    url.searchParams.set("radius", String(Math.max(500, Math.min(10000, radiusMeters))));
    url.searchParams.set("offset", "20");
    url.searchParams.set("page", "1");
    url.searchParams.set("extensions", "all");
    const payload = await fetchJson(url);
    if (payload.status !== "1" || !Array.isArray(payload.pois)) continue;
    for (const poi of payload.pois.map(record)) {
      const candidate = toCandidate(poi);
      if (!candidate) continue;
      const key = candidate.id || `${candidate.name}|${candidate.address}`;
      if (!merged.has(key)) merged.set(key, candidate);
    }
  }
  return [...merged.values()];
}

async function resolveLocation(location: LocationDto | null, config: VercelApiConfig): Promise<LocationDto | null> {
  if (location?.latitude != null && location.longitude != null) return location;
  const text = location?.text?.trim() || "上海人民广场";
  const url = new URL("https://restapi.amap.com/v3/geocode/geo");
  url.searchParams.set("key", config.amapWebKey);
  url.searchParams.set("address", text);
  const payload = await fetchJson(url).catch(() => null);
  const first = Array.isArray(payload?.geocodes) ? record(payload.geocodes[0]) : {};
  const parts = String(first.location ?? "").split(",");
  const longitude = Number(parts[0]);
  const latitude = Number(parts[1]);
  return Number.isFinite(latitude) && Number.isFinite(longitude) ? { latitude, longitude, text } : null;
}

function rankCandidates(query: string, plan: RestaurantKeywordPlan, candidates: RestaurantCandidate[]): RestaurantCandidate[] {
  return [...candidates].sort((left, right) => score(right, query, plan) - score(left, query, plan) || left.distance - right.distance);
}

function score(candidate: RestaurantCandidate, query: string, plan: RestaurantKeywordPlan): number {
  const textValue = [candidate.name, candidate.type, candidate.address, candidate.businessArea, candidate.tags.join(" "), candidate.description].join(" ");
  let value = 0;
  for (const term of plan.mustMatch) if (term && textValue.includes(term)) value += 40;
  for (const term of plan.preferMatch) if (term && textValue.includes(term)) value += 16;
  for (const term of plan.negativeMatch) if (term && textValue.includes(term)) value -= 30;
  for (const term of query.split(/[\s,，、/]+/)) if (term && textValue.includes(term)) value += 8;
  const rating = Number(candidate.rating);
  if (Number.isFinite(rating)) value += rating * 3;
  value += Math.max(0, 10 - candidate.distance / 1000);
  return value;
}

function toCandidate(poi: Record<string, unknown>): RestaurantCandidate | null {
  const name = text(poi.name);
  if (!name) return null;
  const biz = record(poi.biz_ext);
  const photos = array(poi.photos).map(record);
  const location = text(poi.location).split(",");
  const longitude = Number(location[0]);
  const latitude = Number(location[1]);
  const type = text(poi.type) || "餐饮";
  return {
    id: text(poi.id),
    name,
    type,
    address: text(poi.address),
    distance: Number(text(poi.distance)) || 999999,
    rating: text(biz.rating) || "暂无",
    cost: text(biz.cost),
    businessArea: text(poi.business_area),
    tags: type.split(";").filter(Boolean).slice(0, 3),
    description: text(poi.tag),
    photoTitles: photos.map((photo) => text(photo.title)).filter(Boolean),
    coverUrl: text(photos[0]?.url),
    latitude: Number.isFinite(latitude) ? latitude : null,
    longitude: Number.isFinite(longitude) ? longitude : null,
    phone: text(poi.tel)
  };
}

function toRestaurantDto(candidate: RestaurantCandidate): RestaurantDto {
  return {
    id: `amap_${candidate.id || stableId(candidate.name + candidate.address)}`,
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

function parseRadiusMeters(query: string, defaultKm: number): number {
  const km = query.match(/(\d+(?:\.\d+)?)\s*km/i)?.[1] ?? query.match(/(\d+(?:\.\d+)?)\s*公里/)?.[1];
  if (km) return Math.round(Number(km) * 1000);
  const meters = query.match(/(\d+)\s*m/i)?.[1] ?? query.match(/(\d+)\s*米/)?.[1];
  if (meters) return Number(meters);
  return Math.max(1, Math.min(10, defaultKm)) * 1000;
}

async function fetchJson(url: URL): Promise<Record<string, unknown>> {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`Amap HTTP ${response.status}`);
  return record(await response.json());
}

function record(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function array(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function text(value: unknown): string {
  if (typeof value === "string") return value.trim();
  if (typeof value === "number") return String(value);
  return "";
}

function stableId(value: string): string {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = ((hash << 5) - hash + value.charCodeAt(index)) | 0;
  }
  return Math.abs(hash).toString(36);
}
