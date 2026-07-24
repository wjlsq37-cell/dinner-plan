import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiGateway } from "./api";
import { defaultState } from "./store";

const broadRecipeTerms = ["家常菜", "鸡蛋", "豆腐", "鸡肉", "猪肉", "牛肉", "鱼", "青菜", "土豆", "茄子", "汤", "面", "饭"];
const broadRestaurantTerms = ["餐厅", "中餐", "小吃", "快餐", "面馆", "粉面", "火锅", "烧烤", "日料", "西餐", "甜品", "咖啡"];

afterEach(() => vi.unstubAllGlobals());

describe("developer direct recommendation parity", () => {
  it("uses the proxy broad recipe keyword set for home decisions", async () => {
    const keywords: string[] = [];
    const fetchMock = vi.fn().mockImplementation(async (input: URL | RequestInfo) => {
      const url = new URL(String(input));
      const keyword = url.searchParams.get("keyword") || "";
      keywords.push(keyword);
      return new Response(JSON.stringify({ data: { list: [{ id: keyword, name: `${keyword}推荐` }] } }), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);
    const gateway = new ApiGateway({
      ...defaultState.developerSettings,
      enabled: true,
      recipeApiSource: "mxnzp",
      recipeApiAppId: "app-id",
      recipeApiSecret: "app-secret"
    });

    const result = await gateway.cook({ query: "", mode: "RECIPE_SINGLE", cookSource: "DATABASE", broadSearch: true });

    expect(keywords).toEqual(broadRecipeTerms);
    expect(result.recipes).toHaveLength(broadRecipeTerms.length);
    expect(result.summary).toContain("扩大候选范围");
  });

  it("uses the proxy broad restaurant keyword set and merges all candidates", async () => {
    const keywords: string[] = [];
    const fetchMock = vi.fn().mockImplementation(async (input: URL | RequestInfo) => {
      const url = new URL(String(input));
      const keyword = url.searchParams.get("keywords") || "";
      keywords.push(keyword);
      const index = broadRestaurantTerms.indexOf(keyword);
      return new Response(JSON.stringify({
        status: "1",
        pois: [{
          id: keyword,
          name: `${keyword}店`,
          type: `${keyword};餐饮服务`,
          address: "测试路",
          location: "121.47,31.23",
          distance: String(200 + index),
          biz_ext: { rating: String(4 + index / 100) }
        }]
      }), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);
    const gateway = new ApiGateway({ ...defaultState.developerSettings, enabled: true, amapWebKey: "amap-key" });

    const result = await gateway.restaurants({
      query: "",
      mode: "RESTAURANT",
      broadSearch: true,
      location: { latitude: 31.23, longitude: 121.47 },
      preferences: { restaurantResultLimit: 50, defaultDistanceKm: 5 }
    });

    expect(keywords).toEqual(broadRestaurantTerms);
    expect(result.restaurants).toHaveLength(broadRestaurantTerms.length);
  });

  it("uses the proxy AI prompt shape and retries results that hit an avoid term", async () => {
    const responses = [
      { summary: "第一次", recipes: [{ name: "香菜拌饭", ingredients: [{ name: "香菜", amount: "一把" }] }], mealPlans: [] },
      { summary: "第二次", recipes: [{ name: "番茄炒蛋", ingredients: [{ name: "番茄", amount: "两个" }] }], mealPlans: [] }
    ];
    const fetchMock = vi.fn().mockImplementation(async () => new Response(JSON.stringify({
      choices: [{ message: { content: JSON.stringify(responses.shift()) } }]
    }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    const gateway = new ApiGateway({ ...defaultState.developerSettings, enabled: true, aiApiKey: "secret" });

    const result = await gateway.cook({
      query: "",
      mode: "RECIPE_SINGLE",
      cookSource: "AI_GENERATED",
      broadSearch: true,
      preferences: { tastes: ["清淡"], avoids: ["香菜"] }
    });

    expect(fetchMock).toHaveBeenCalledTimes(2);
    const firstBody = JSON.parse(String(fetchMock.mock.calls[0][1]?.body));
    const retryBody = JSON.parse(String(fetchMock.mock.calls[1][1]?.body));
    expect(firstBody.messages[1].content).toContain("用户需求：\n模式：RECIPE_SINGLE");
    expect(firstBody.messages[1].content).toContain("忌口：香菜");
    expect(firstBody.messages[1].content).toContain("偏好：清淡");
    expect(retryBody.messages[1].content).toContain("上一次结果命中了忌口：香菜");
    expect(result.recipes.map((recipe) => recipe.name)).toEqual(["番茄炒蛋"]);
  });
});
