import assert from "node:assert/strict";
import test from "node:test";
import { recommendRestaurantsFromAmap } from "../src/amap.js";
import type { VercelApiConfig } from "../src/config.js";

const baseConfig: VercelApiConfig = {
  aiBaseUrl: "",
  aiApiKey: "",
  aiModel: "",
  amapWebKey: "amap-key",
  appApiToken: "",
  recipe: {
    priority: ["mxnzp", "wanwei"],
    mxnzpSearchUrl: "",
    mxnzpDetailUrl: "",
    mxnzpAppId: "",
    mxnzpAppSecret: "",
    wanweiBaseUrl: "",
    wanweiAppKey: ""
  }
};

test("broad restaurant search fans out and deduplicates Amap candidates", async () => {
  const originalFetch = globalThis.fetch;
  const keywords: string[] = [];

  globalThis.fetch = (async (input) => {
    const url = new URL(String(input));
    const keyword = url.searchParams.get("keywords") ?? "";
    keywords.push(keyword);
    const id = keyword === "餐厅" || keyword === "中餐" ? "same" : `id_${keywords.length}`;
    return {
      ok: true,
      json: async () => ({
        status: "1",
        pois: [
          {
            id,
            name: `${keyword || "附近"}推荐店`,
            type: "餐饮服务;中餐厅",
            address: `湖滨路 ${keywords.length} 号`,
            distance: String(100 + keywords.length),
            location: "120.1,30.2",
            biz_ext: { rating: "4.6", cost: "58" }
          }
        ]
      })
    } as Response;
  }) as typeof fetch;

  try {
    const result = await recommendRestaurantsFromAmap(
      {
        query: "",
        mode: "RESTAURANT",
        broadSearch: true,
        location: { latitude: 30.2, longitude: 120.1, text: "湖滨" },
        preferences: { restaurantResultLimit: 4 }
      },
      baseConfig
    );

    assert.ok(keywords.length > 1);
    assert.ok(keywords.includes("餐厅"));
    assert.ok(keywords.includes("中餐"));
    assert.equal(new Set(result.restaurants.map((restaurant) => restaurant.id)).size, result.restaurants.length);
    assert.equal(result.restaurants.length, 4);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
