import type { VercelApiConfig } from "./config.js";
import type { CookRecommendationResponse, MealPlanDto, RecipeDto, RecommendationModeDto, RecommendationRequest, UserPreferenceDto } from "./types.js";

export interface RestaurantKeywordPlan {
  summary: string;
  keywords: string[];
  mustMatch: string[];
  preferMatch: string[];
  negativeMatch: string[];
  searchStrategy: "separate";
}

export function broadRestaurantKeywordPlan(): RestaurantKeywordPlan {
  const terms = ["餐厅", "中餐", "小吃", "快餐", "面馆", "粉面", "火锅", "烧烤", "日料", "西餐", "甜品", "咖啡"];
  return {
    summary: "随机扩大附近餐厅候选范围",
    keywords: terms,
    mustMatch: [],
    preferMatch: terms,
    negativeMatch: [],
    searchStrategy: "separate"
  };
}

interface ChatMessage {
  role: "system" | "user";
  content: string;
}

export async function generateAiCookRecommendation(
  request: RecommendationRequest,
  config: VercelApiConfig
): Promise<CookRecommendationResponse | null> {
  if (!isAiConfigured(config)) return null;
  const preferences = request.preferences ?? {};
  const first = await requestCookGeneration(request.query, request.mode, preferences, config);
  if (!first) return null;
  const violated = violatedAvoidTerms(first, preferences.avoids ?? []);
  const generation = violated.length > 0
    ? (await requestCookGeneration(request.query, request.mode, preferences, config, violated)) ?? first
    : first;
  const filtered = filterAvoidingTerms(generation, preferences.avoids ?? []);
  if (filtered.recipes.length === 0 && filtered.mealPlans.length === 0) return null;
  return {
    intent: filtered.intent,
    summary: filtered.summary || "已根据你的输入生成做饭建议。",
    mealPlans: filtered.mealPlans,
    recipes: filtered.recipes,
    fallbackReason: null,
    source: "AI_GENERATED",
    totalMatches: filtered.recipes.length + filtered.mealPlans.length
  };
}

export async function parseRestaurantKeywordPlan(query: string, config: VercelApiConfig): Promise<RestaurantKeywordPlan> {
  if (!isAiConfigured(config)) return fallbackRestaurantKeywordPlan(query);
  const system = [
    "你是餐厅搜索关键词解析助手。只输出 JSON，不要解释。",
    "字段必须是 summary、keywords、mustMatch、preferMatch、negativeMatch、searchStrategy。",
    "keywords 是要分别用于地图搜索的关键词，不要把所有词拼成一个长关键词。",
    "searchStrategy 固定输出 separate。"
  ].join("\n");
  const user = `用户附近吃需求：${query}`;
  const content = await requestChatJson(system, user, config, 0.2).catch(() => null);
  if (!content) return fallbackRestaurantKeywordPlan(query);
  try {
    return normalizeKeywordPlan(JSON.parse(extractJsonObject(content)));
  } catch {
    return fallbackRestaurantKeywordPlan(query);
  }
}

export function fallbackRestaurantKeywordPlan(query: string): RestaurantKeywordPlan {
  if (/日料|日本|寿司|放题|自助/.test(query)) {
    return {
      summary: "用户想找日料自助餐厅",
      keywords: ["日料自助", "日本料理", "日式放题", "寿司", "自助餐"],
      mustMatch: query.includes("自助") || query.includes("放题") ? ["自助", "放题"] : [],
      preferMatch: ["日料", "日本料理", "寿司", "刺身", "日式"],
      negativeMatch: [],
      searchStrategy: "separate"
    };
  }
  if (/娃|孩子|儿童|亲子|家庭/.test(query)) {
    return {
      summary: "用户想找适合带孩子吃饭的餐厅",
      keywords: ["亲子餐厅", "儿童友好", "商场餐饮", "家庭餐厅", "清淡餐厅"],
      mustMatch: [],
      preferMatch: ["亲子", "儿童", "家庭", "商场", "清淡"],
      negativeMatch: ["酒吧", "夜店"],
      searchStrategy: "separate"
    };
  }
  if (/清淡|不辣|少油|舒服/.test(query)) {
    return {
      summary: "用户想找口味清淡的附近餐厅",
      keywords: ["清淡餐厅", "粥", "汤", "家常菜", "轻食"],
      mustMatch: [],
      preferMatch: ["清淡", "粥", "汤", "轻食", "家常"],
      negativeMatch: ["麻辣", "烧烤", "火锅"],
      searchStrategy: "separate"
    };
  }
  const clean = query.trim() || "餐厅";
  return {
    summary: `用户想找${clean}`,
    keywords: [clean, "餐厅"],
    mustMatch: [],
    preferMatch: [clean],
    negativeMatch: [],
    searchStrategy: "separate"
  };
}

async function requestCookGeneration(
  query: string,
  mode: RecommendationModeDto,
  preferences: UserPreferenceDto,
  config: VercelApiConfig,
  violatedTerms: string[] = []
): Promise<CookRecommendationResponse | null> {
  const avoids = (preferences.avoids ?? []).filter(Boolean).join("、") || "无";
  const tastes = (preferences.tastes ?? []).filter(Boolean).join("、") || "无";
  const system = [
    "你是家庭菜谱生成助手。只输出 JSON，不要解释。",
    "JSON 字段必须匹配 CookRecommendationResponse：intent、summary、mealPlans、recipes、source、totalMatches。",
    "必须避开用户忌口食材，并尽量符合用户偏好。",
    "recipes 中每道菜必须包含 name、cuisine、taste、tags、difficulty、cookTime、servings、coverUrl、reason、ingredients、steps、tips。"
  ].join("\n");
  const retryNote = violatedTerms.length > 0 ? `上一次结果命中了忌口：${violatedTerms.join("、")}。这次必须完全避开。` : "";
  const user = [
    `用户需求：${query}`,
    `模式：${mode}`,
    `忌口：${avoids}`,
    `偏好：${tastes}`,
    retryNote
  ].filter(Boolean).join("\n");
  const content = await requestChatJson(system, user, config, 0.6).catch(() => null);
  if (!content) return null;
  try {
    return normalizeCookResponse(JSON.parse(extractJsonObject(content)), mode);
  } catch {
    return null;
  }
}

async function requestChatJson(system: string, user: string, config: VercelApiConfig, temperature: number): Promise<string> {
  const response = await fetch(chatCompletionsUrl(config.aiBaseUrl), {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${config.aiApiKey}`
    },
    body: JSON.stringify({
      model: config.aiModel,
      messages: [
        { role: "system", content: system },
        { role: "user", content: user }
      ] satisfies ChatMessage[],
      temperature,
      response_format: { type: "json_object" }
    })
  });
  if (!response.ok) throw new Error(`AI HTTP ${response.status}`);
  const payload = await response.json() as { choices?: Array<{ message?: { content?: string } }> };
  return payload.choices?.[0]?.message?.content ?? "";
}

function normalizeCookResponse(raw: unknown, mode: RecommendationModeDto): CookRecommendationResponse {
  const obj = record(raw);
  const recipes = array(obj.recipes).map(normalizeRecipe).filter((recipe): recipe is RecipeDto => Boolean(recipe));
  const mealPlans = array(obj.mealPlans).map(normalizeMealPlan).filter((plan): plan is MealPlanDto => Boolean(plan));
  return {
    intent: text(obj.intent) as RecommendationModeDto || mode,
    summary: text(obj.summary),
    mealPlans,
    recipes,
    fallbackReason: null,
    source: "AI_GENERATED",
    totalMatches: recipes.length + mealPlans.length
  };
}

function normalizeRecipe(raw: unknown): RecipeDto | null {
  const obj = record(raw);
  const name = text(obj.name);
  if (!name) return null;
  const ingredients = array(obj.ingredients).map((item) => {
    const ingredient = record(item);
    return { name: text(ingredient.name), amount: text(ingredient.amount) };
  }).filter((item) => item.name);
  return {
    id: text(obj.id) || `ai_recipe_${stableId(name)}`,
    name,
    cuisine: text(obj.cuisine) || "家常菜",
    taste: stringArray(obj.taste),
    tags: stringArray(obj.tags),
    difficulty: text(obj.difficulty) || "家常",
    cookTime: text(obj.cookTime) || "约20分钟",
    servings: text(obj.servings) || "2-3人份",
    coverUrl: text(obj.coverUrl),
    reason: text(obj.reason) || "根据你的需求生成。",
    ingredients: ingredients.length > 0 ? ingredients : [{ name, amount: "适量" }],
    steps: stringArray(obj.steps),
    tips: text(obj.tips),
    ratingStars: typeof obj.ratingStars === "number" ? obj.ratingStars : null,
    source: "ai_generated",
    stepImageUrls: stringArray(obj.stepImageUrls)
  };
}

function normalizeMealPlan(raw: unknown): MealPlanDto | null {
  const obj = record(raw);
  const title = text(obj.title);
  if (!title) return null;
  return {
    id: text(obj.id) || `ai_meal_${stableId(title)}`,
    title,
    structure: text(obj.structure) || "家常菜单",
    cookTime: text(obj.cookTime) || "约45分钟",
    servings: text(obj.servings) || "2-3人份",
    coverUrl: text(obj.coverUrl),
    tags: stringArray(obj.tags),
    reason: text(obj.reason),
    dishes: array(obj.dishes).map((item) => {
      const dish = record(item);
      return {
        course: text(dish.course),
        name: text(dish.name),
        note: text(dish.note),
        badge: text(dish.badge) || "Veg",
        recipeId: text(dish.recipeId) || null
      };
    }).filter((dish) => dish.name),
    shoppingList: stringArray(obj.shoppingList),
    timeline: stringArray(obj.timeline)
  };
}

function filterAvoidingTerms(response: CookRecommendationResponse, avoids: string[]): CookRecommendationResponse {
  const terms = expandAvoidTerms(avoids);
  if (terms.length === 0) return response;
  const recipes = response.recipes.filter((recipe) => !containsAny(recipeText(recipe), terms));
  const mealPlans = response.mealPlans.filter((plan) => !containsAny(JSON.stringify(plan), terms));
  return {
    ...response,
    recipes,
    mealPlans,
    totalMatches: recipes.length + mealPlans.length
  };
}

function violatedAvoidTerms(response: CookRecommendationResponse, avoids: string[]): string[] {
  const terms = expandAvoidTerms(avoids);
  const payload = JSON.stringify(response);
  return terms.filter((term) => payload.includes(term));
}

function expandAvoidTerms(avoids: string[]): string[] {
  const terms = avoids.flatMap((item) => item.split(/[\s,，、/;；]+/)).map((item) => item.trim()).filter(Boolean);
  const expanded = [...terms];
  if (terms.some((term) => term.includes("牛羊"))) expanded.push("牛肉", "羊肉", "牛", "羊");
  if (terms.includes("海鲜")) expanded.push("鱼", "虾", "蟹", "贝", "海鲜");
  return Array.from(new Set(expanded));
}

function normalizeKeywordPlan(raw: unknown): RestaurantKeywordPlan {
  const obj = record(raw);
  const keywords = stringArray(obj.keywords).filter(Boolean);
  return {
    summary: text(obj.summary),
    keywords: keywords.length > 0 ? keywords.slice(0, 12) : ["餐厅"],
    mustMatch: stringArray(obj.mustMatch),
    preferMatch: stringArray(obj.preferMatch),
    negativeMatch: stringArray(obj.negativeMatch),
    searchStrategy: "separate"
  };
}

function isAiConfigured(config: VercelApiConfig): boolean {
  return Boolean(config.aiBaseUrl && config.aiApiKey && config.aiModel);
}

function chatCompletionsUrl(baseUrl: string): string {
  const normalized = baseUrl.replace(/\/+$/, "");
  return normalized.endsWith("/chat/completions") ? normalized : `${normalized}/chat/completions`;
}

function extractJsonObject(content: string): string {
  const start = content.indexOf("{");
  const end = content.lastIndexOf("}");
  if (start < 0 || end < start) throw new Error("No JSON object found");
  return content.slice(start, end + 1);
}

function recipeText(recipe: RecipeDto): string {
  return [
    recipe.name,
    recipe.reason,
    recipe.tips,
    recipe.ingredients.map((item) => `${item.name}${item.amount}`).join(" "),
    recipe.steps.join(" ")
  ].join(" ");
}

function containsAny(value: string, terms: string[]): boolean {
  return terms.some((term) => value.includes(term));
}

function record(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function array(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function stringArray(value: unknown): string[] {
  if (Array.isArray(value)) return value.map(text).filter(Boolean);
  if (typeof value === "string") return value.split(/[,，、\n]/).map((item) => item.trim()).filter(Boolean);
  return [];
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
