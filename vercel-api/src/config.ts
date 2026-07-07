import type { RecipeApiConfig, RecipeSourceId } from "./recipeSources.js";

export interface VercelApiConfig {
  aiBaseUrl: string;
  aiApiKey: string;
  aiModel: string;
  amapWebKey: string;
  appApiToken: string;
  recipe: RecipeApiConfig;
}

export interface HealthResponse {
  ok: boolean;
  missingConfig: string[];
  aiConfigured: boolean;
  amapConfigured: boolean;
  recipeCorpusReady: boolean;
  recipeCorpusCount: number;
  recipeCorpusPath: string;
  recipeSources: {
    priority: RecipeSourceId[];
    mxnzpConfigured: boolean;
    wanweiConfigured: boolean;
  };
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): VercelApiConfig {
  return {
    aiBaseUrl: value(env, "AI_BASE_URL", "https://api.deepseek.com").replace(/\/+$/, ""),
    aiApiKey: value(env, "AI_API_KEY"),
    aiModel: value(env, "AI_MODEL", "deepseek-v4-flash"),
    amapWebKey: value(env, "AMAP_WEB_KEY"),
    appApiToken: value(env, "APP_API_TOKEN"),
    recipe: {
      priority: parseRecipePriority(value(env, "RECIPE_API_PRIORITY", "mxnzp,wanwei")),
      mxnzpSearchUrl: value(env, "MXNZP_RECIPE_SEARCH_URL", "https://www.mxnzp.com/api/cookbook/search"),
      mxnzpDetailUrl: value(env, "MXNZP_RECIPE_DETAIL_URL", "https://www.mxnzp.com/api/cookbook/details"),
      mxnzpAppId: value(env, "MXNZP_APP_ID"),
      mxnzpAppSecret: value(env, "MXNZP_APP_SECRET"),
      wanweiBaseUrl: value(env, "WANWEI_RECIPE_BASE_URL", "https://route.showapi.com"),
      wanweiAppKey: value(env, "WANWEI_APP_KEY")
    }
  };
}

export function buildHealthResponse(config: VercelApiConfig): HealthResponse {
  const aiConfigured = Boolean(config.aiBaseUrl && config.aiApiKey && config.aiModel);
  const amapConfigured = Boolean(config.amapWebKey);
  const mxnzpConfigured = Boolean(config.recipe.mxnzpSearchUrl && config.recipe.mxnzpAppId && config.recipe.mxnzpAppSecret);
  const wanweiConfigured = Boolean(config.recipe.wanweiBaseUrl && config.recipe.wanweiAppKey);
  const recipeConfigured = mxnzpConfigured || wanweiConfigured;
  const missingConfig: string[] = [];
  if (!aiConfigured) missingConfig.push("AI_BASE_URL/AI_API_KEY/AI_MODEL");
  if (!amapConfigured) missingConfig.push("AMAP_WEB_KEY");
  if (!recipeConfigured) missingConfig.push("MXNZP_* or WANWEI_*");

  return {
    ok: missingConfig.length === 0,
    missingConfig,
    aiConfigured,
    amapConfigured,
    recipeCorpusReady: recipeConfigured,
    recipeCorpusCount: 0,
    recipeCorpusPath: "recipe-api-cascade",
    recipeSources: {
      priority: config.recipe.priority,
      mxnzpConfigured,
      wanweiConfigured
    }
  };
}

function value(env: NodeJS.ProcessEnv, key: string, fallback = ""): string {
  return (env[key] ?? fallback).trim();
}

function parseRecipePriority(raw: string): RecipeSourceId[] {
  const parsed = raw
    .split(",")
    .map((item) => item.trim().toLowerCase())
    .filter((item): item is RecipeSourceId => item === "mxnzp" || item === "wanwei");
  return parsed.length > 0 ? Array.from(new Set(parsed)) : ["mxnzp", "wanwei"];
}
