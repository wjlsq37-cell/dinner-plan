import type { IngredientDto, RecipeDto } from "./types.js";

export type RecipeSourceId = "mxnzp" | "wanwei";

export interface RecipeApiConfig {
  priority: RecipeSourceId[];
  mxnzpSearchUrl: string;
  mxnzpDetailUrl: string;
  mxnzpAppId: string;
  mxnzpAppSecret: string;
  wanweiBaseUrl: string;
  wanweiAppKey: string;
}

export interface RecipeFetchRequest {
  source: RecipeSourceId;
  url: string;
  method: "GET" | "POST";
  query?: Record<string, string | number>;
  form?: Record<string, string | number>;
}

export type RecipeFetcher = (request: RecipeFetchRequest) => Promise<unknown>;

export interface RecipeCascadeSearchInput {
  query: string;
  page: number;
  pageSize: number;
  config: RecipeApiConfig;
  broadSearch?: boolean;
  fetcher?: RecipeFetcher;
}

export interface RecipeCascadeSearchResult {
  recipes: RecipeDto[];
  totalMatches: number;
  fallbackReason?: string | null;
}

export interface RecipeDetailInput {
  id: string;
  config: RecipeApiConfig;
  fetcher?: RecipeFetcher;
}

export async function searchRecipesCascade(input: RecipeCascadeSearchInput): Promise<RecipeCascadeSearchResult> {
  const fetcher = input.fetcher ?? defaultRecipeFetcher;
  const priority = input.config.priority.length > 0 ? input.config.priority : ["mxnzp", "wanwei"];
  const failures: string[] = [];

  if (input.broadSearch) {
    const recipes: RecipeDto[] = [];
    for (const source of priority) {
      for (const term of broadRecipeSearchTerms) {
        try {
          const next = source === "mxnzp"
            ? await searchMxnzpRecipes({ ...input, query: term, broadSearch: false }, fetcher)
            : await searchWanweiRecipes({ ...input, query: term, broadSearch: false }, fetcher);
          recipes.push(...next);
          if (distinctRecipes(recipes).length >= input.pageSize) {
            const distinct = distinctRecipes(recipes).slice(0, input.pageSize);
            return {
              recipes: distinct,
              totalMatches: distinct.length,
              fallbackReason: null
            };
          }
        } catch (error) {
          failures.push(`${source}/${term}: ${error instanceof Error ? error.message : String(error)}`);
        }
      }
    }
    const distinct = distinctRecipes(recipes).slice(0, input.pageSize);
    if (distinct.length > 0) {
      return {
        recipes: distinct,
        totalMatches: distinct.length,
        fallbackReason: null
      };
    }
    return {
      recipes: [],
      totalMatches: 0,
      fallbackReason: failures.length > 0
        ? "菜谱服务暂时不可用，请稍后再试。"
        : "暂时没找到合适的结果，可以换个关键词或放宽条件再试。"
    };
  }

  for (const source of priority) {
    try {
      const recipes = source === "mxnzp"
        ? await searchMxnzpRecipes(input, fetcher)
        : await searchWanweiRecipes(input, fetcher);
      if (recipes.length > 0) {
        return {
          recipes,
          totalMatches: recipes.length,
          fallbackReason: null
        };
      }
    } catch (error) {
      failures.push(`${source}: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  return {
    recipes: [],
    totalMatches: 0,
    fallbackReason: failures.length > 0
      ? "菜谱服务暂时不可用，请稍后再试。"
      : "暂时没找到合适的结果，可以换个关键词或放宽条件再试。"
  };
}

const broadRecipeSearchTerms = ["家常菜", "鸡蛋", "豆腐", "鸡肉", "猪肉", "牛肉", "鱼", "青菜", "土豆", "茄子", "汤", "面", "饭"];

export async function loadRecipeDetail(input: RecipeDetailInput): Promise<RecipeDto | null> {
  if (!input.id.startsWith("mxnzp_")) return null;
  if (!isMxnzpConfigured(input.config, true)) return null;
  const rawId = input.id.replace(/^mxnzp_/, "");
  const payload = await (input.fetcher ?? defaultRecipeFetcher)({
    source: "mxnzp",
    url: input.config.mxnzpDetailUrl,
    method: "GET",
    query: {
      id: rawId,
      app_id: input.config.mxnzpAppId,
      app_secret: input.config.mxnzpAppSecret
    }
  });
  const recipe = parseMxnzpDetailResponse(payload);
  return recipe ? { ...recipe, id: `mxnzp_${rawId}` } : null;
}

async function searchMxnzpRecipes(input: RecipeCascadeSearchInput, fetcher: RecipeFetcher): Promise<RecipeDto[]> {
  if (!isMxnzpConfigured(input.config, false)) return [];
  const payload = await fetcher({
    source: "mxnzp",
    url: input.config.mxnzpSearchUrl,
    method: "GET",
    query: {
      keyword: input.query,
      page: input.page,
      app_id: input.config.mxnzpAppId,
      app_secret: input.config.mxnzpAppSecret
    }
  });
  return parseMxnzpSearchResponse(payload)
    .map((recipe) => ({ ...recipe, steps: recipe.steps.length > 0 ? recipe.steps : defaultSteps(recipe.name) }))
    .slice(0, input.pageSize);
}

async function searchWanweiRecipes(input: RecipeCascadeSearchInput, fetcher: RecipeFetcher): Promise<RecipeDto[]> {
  if (!input.config.wanweiBaseUrl || !input.config.wanweiAppKey) return [];
  const keywordPayload = await fetcher({
    source: "wanwei",
    url: `${input.config.wanweiBaseUrl.replace(/\/+$/, "")}/1164-4`,
    method: "POST",
    query: { appKey: input.config.wanweiAppKey },
    form: {
      keyword: input.query,
      keyWord: input.query,
      word: input.query,
      cpName: input.query,
      name: input.query,
      maxResults: input.pageSize,
      page: input.page
    }
  });
  const keywordRecipes = parseWanweiResponse(keywordPayload);
  if (keywordRecipes.length > 0) return keywordRecipes.slice(0, input.pageSize);

  const categoryPayload = await fetcher({
    source: "wanwei",
    url: `${input.config.wanweiBaseUrl.replace(/\/+$/, "")}/1164-1`,
    method: "POST",
    query: { appKey: input.config.wanweiAppKey },
    form: {
      type: inferWanweiCategory(input.query),
      cpName: input.query,
      maxResults: input.pageSize,
      page: input.page
    }
  });
  return distinctRecipes([...keywordRecipes, ...parseWanweiResponse(categoryPayload)]).slice(0, input.pageSize);
}

export async function defaultRecipeFetcher(request: RecipeFetchRequest): Promise<unknown> {
  const url = new URL(request.url);
  for (const [key, value] of Object.entries(request.query ?? {})) {
    url.searchParams.set(key, String(value));
  }
  const init: RequestInit = { method: request.method };
  if (request.method === "POST") {
    const body = new URLSearchParams();
    for (const [key, value] of Object.entries(request.form ?? {})) {
      if (String(value).trim()) body.set(key, String(value));
    }
    init.headers = { "content-type": "application/x-www-form-urlencoded" };
    init.body = body;
  }
  const response = await fetch(url, init);
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`${request.source} HTTP ${response.status}: ${text.slice(0, 160)}`);
  }
  return parseJson(text);
}

export function parseMxnzpSearchResponse(payload: unknown): RecipeDto[] {
  const root = asRecord(payload);
  const code = numberValue(root.code ?? root.status);
  if (code != null && ![0, 1, 200].includes(code)) return [];
  const body = asRecord(root.data ?? root.result ?? root);
  return recipeObjects(body).map((item) => toRecipe(item, "mxnzp", "mxnzp", "mxnzp"));
}

export function parseMxnzpDetailResponse(payload: unknown): RecipeDto | null {
  const root = asRecord(payload);
  const code = numberValue(root.code ?? root.status);
  if (code != null && ![0, 1, 200].includes(code)) return null;
  const body = asRecord(root.data ?? root.result ?? root);
  return toRecipe(body, "mxnzp", "mxnzp", "mxnzp");
}

export function parseWanweiResponse(payload: unknown): RecipeDto[] {
  const root = asRecord(payload);
  const code = numberValue(root.showapi_res_code);
  if (code != null && code !== 0) return [];
  const body = asRecord(root.showapi_res_body ?? root);
  const retCode = numberValue(body.ret_code);
  if (retCode != null && retCode !== 0) return [];
  return recipeObjects(body).map((item) => toRecipe(item, "wanwei", "showapi", "万维易源"));
}

function toRecipe(item: Record<string, unknown>, sourceName: string, idPrefix: string, defaultTag: string): RecipeDto {
  const name = firstText(item.name, item.cpName, item.title, item.dishName, item.recipeName) || "未命名菜谱";
  const rawId = firstText(item.id, item.menuId, item.recipeId, item.classid) || stableId(name);
  const ingredients = parseIngredients(item.ingredient ?? item.ingredients ?? item.yl ?? item.materials ?? item.material);
  const parsedSteps = parseSteps(item.instruction ?? item.instructions ?? item.steps ?? item.recipeInstructions ?? item.method ?? item.process);
  const stepTexts = parsedSteps.map((step) => step.text).filter(Boolean);
  const stepImages = parsedSteps.map((step) => step.image).filter(Boolean);
  const tips = firstText(item.tips, item.tip, item.remark, item.description, item.des);
  const cookTime = firstText(item.duration, item.time, item.cookTime) || "约20分钟";

  return {
    id: `${idPrefix}_${sanitizeId(rawId)}`,
    name,
    cuisine: firstText(item.type, item.typeV1, item.type_v1, item.category) || sourceName,
    taste: [],
    tags: [defaultTag, firstText(item.type, item.typeV2, item.type_v2)].filter(Boolean),
    difficulty: firstText(item.difficulty, item.hardLevel) || "家常",
    cookTime,
    servings: firstText(item.servings, item.people) || "2-3人份",
    coverUrl: firstText(item.cover, item.coverUrl, item.largeImg, item.smallImg, item.img, item.image, item.pic, item.photo, item.thumbnail),
    reason: `${sourceName} 菜谱结果，和你的搜索更接近。`,
    ingredients: ingredients.length > 0 ? ingredients : [{ name: name, amount: "适量" }],
    steps: stepTexts.length > 0 ? stepTexts : defaultSteps(name),
    tips,
    ratingStars: qualityRating(ingredients.length, stepTexts.length, Boolean(firstText(item.cover, item.largeImg, item.smallImg, item.img))),
    source: sourceName,
    stepImageUrls: stepImages
  };
}

function parseIngredients(value: unknown): IngredientDto[] {
  if (Array.isArray(value)) {
    return value.map((item) => {
      if (typeof item === "string") return { name: item.trim(), amount: "" };
      const obj = asRecord(item);
      return {
        name: firstText(obj.name, obj.ylName, obj.title, obj.ingredientName) || "",
        amount: firstText(obj.amount, obj.ylUnit, obj.unit, obj.weight) || ""
      };
    }).filter((item) => item.name);
  }
  if (typeof value === "string") {
    return value.split(/[、,，;；\n]/).map((name) => ({ name: name.trim(), amount: "" })).filter((item) => item.name);
  }
  return [];
}

function parseSteps(value: unknown): Array<{ text: string; image: string }> {
  if (Array.isArray(value)) {
    return value.map((item, index) => {
      if (typeof item === "string") return { text: item.trim(), image: "" };
      const obj = asRecord(item);
      const text = firstText(obj.text, obj.content, obj.desc, obj.description, obj.stepText, obj.explain) || "";
      const step = firstText(obj.step, obj.orderNum, obj.no) || String(index + 1);
      return {
        text: text.replace(/^第?\s*\d+\s*步[:：.]?\s*/, "").trim() || text.trim(),
        image: firstText(obj.url, obj.imgUrl, obj.image, obj.pic, obj.photo)
      };
    }).filter((step) => step.text);
  }
  if (typeof value === "string") {
    return value.split(/\n|。/).map((text) => ({ text: text.trim(), image: "" })).filter((step) => step.text);
  }
  return [];
}

function recipeObjects(body: Record<string, unknown>): Record<string, unknown>[] {
  const direct = arraysFrom(body, ["list", "data", "datas", "result", "results", "items", "recipes", "contentlist"]);
  if (direct.length > 0) return direct;
  const pagebean = asRecord(body.pagebean ?? body.pageBean ?? body.page);
  return arraysFrom(pagebean, ["contentlist", "list", "data", "datas", "items", "results"]);
}

function arraysFrom(obj: Record<string, unknown>, keys: string[]): Record<string, unknown>[] {
  for (const key of keys) {
    const value = obj[key];
    if (Array.isArray(value)) return value.map(asRecord).filter((item) => Object.keys(item).length > 0);
  }
  return [];
}

function defaultSteps(name: string): string[] {
  return [`准备${name}所需食材。`, "按家常做法处理并烹饪，出锅前尝味调整。"];
}

function inferWanweiCategory(query: string): string {
  if (/汤|羹/.test(query)) return "汤粥";
  if (/早餐|面|饭|粉|粥/.test(query)) return "主食";
  if (/甜|蛋糕|点心|下午茶/.test(query)) return "甜品";
  return "家常菜";
}

function distinctRecipes(recipes: RecipeDto[]): RecipeDto[] {
  const seen = new Set<string>();
  return recipes.filter((recipe) => {
    const key = recipe.id || recipe.name;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function isMxnzpConfigured(config: RecipeApiConfig, detail: boolean): boolean {
  return Boolean((detail ? config.mxnzpDetailUrl : config.mxnzpSearchUrl) && config.mxnzpAppId && config.mxnzpAppSecret);
}

function parseJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    throw new Error(`Invalid JSON: ${text.slice(0, 120)}`);
  }
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function firstText(...values: unknown[]): string {
  for (const value of values) {
    if (typeof value === "string" && value.trim()) return value.trim();
    if (typeof value === "number") return String(value);
  }
  return "";
}

function numberValue(value: unknown): number | null {
  if (typeof value === "number") return value;
  if (typeof value === "string") return Number.isFinite(Number(value)) ? Number(value) : null;
  return null;
}

function sanitizeId(value: string): string {
  return value.replace(/[^a-zA-Z0-9_-]/g, "_").replace(/_+/g, "_").slice(0, 80) || stableId(value);
}

function stableId(value: string): string {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = ((hash << 5) - hash + value.charCodeAt(index)) | 0;
  }
  return Math.abs(hash).toString(36);
}

function qualityRating(ingredientCount: number, stepCount: number, hasImage: boolean): number {
  const score = 3 + Math.min(0.8, ingredientCount * 0.12) + Math.min(0.8, stepCount * 0.16) + (hasImage ? 0.3 : 0);
  return Math.round(Math.min(5, score) * 10) / 10;
}
