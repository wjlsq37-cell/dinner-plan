import assert from "node:assert/strict";
import test from "node:test";
import {
  loadRecipeDetail,
  searchRecipesCascade,
  type RecipeApiConfig,
  type RecipeFetchRequest
} from "../src/recipeSources.js";

const baseConfig: RecipeApiConfig = {
  priority: ["mxnzp", "wanwei"],
  mxnzpSearchUrl: "https://www.mxnzp.com/api/cookbook/search",
  mxnzpDetailUrl: "https://www.mxnzp.com/api/cookbook/details",
  mxnzpAppId: "mx-id",
  mxnzpAppSecret: "mx-secret",
  wanweiBaseUrl: "https://route.showapi.com",
  wanweiAppKey: "wanwei-key"
};

test("mxnzp results win and wanwei is not called", async () => {
  const calls: RecipeFetchRequest[] = [];
  const result = await searchRecipesCascade({
    query: "番茄炒蛋",
    page: 1,
    pageSize: 10,
    config: baseConfig,
    fetcher: async (request) => {
      calls.push(request);
      return {
        code: 1,
        data: {
          list: [
            {
              id: "37",
              name: "番茄炒蛋",
              cover: "https://img.example/tomato.jpg",
              ingredient: [{ name: "鸡蛋", amount: "2个" }]
            }
          ]
        }
      };
    }
  });

  assert.equal(result.recipes.length, 1);
  assert.equal(result.recipes[0].source, "mxnzp");
  assert.equal(result.recipes[0].coverUrl, "https://img.example/tomato.jpg");
  assert.equal(calls.length, 1);
  assert.equal(calls[0].source, "mxnzp");
  assert.deepEqual(Object.keys(calls[0].query ?? {}).sort(), ["app_id", "app_secret", "keyword", "page"]);
});

test("wanwei is called when mxnzp returns no recipes", async () => {
  const calls: RecipeFetchRequest[] = [];
  const result = await searchRecipesCascade({
    query: "茄子",
    page: 1,
    pageSize: 10,
    config: baseConfig,
    fetcher: async (request) => {
      calls.push(request);
      if (request.source === "mxnzp") {
        return { code: 1, data: { list: [] } };
      }
      return {
        showapi_res_code: 0,
        showapi_res_body: {
          pagebean: {
            contentlist: [
              {
                id: "w1",
                cpName: "红烧茄子",
                largeImg: "https://img.example/eggplant.jpg",
                yl: [{ ylName: "茄子", ylUnit: "1根" }],
                steps: [{ content: "茄子切条后煸炒。" }]
              }
            ]
          }
        }
      };
    }
  });

  assert.equal(result.recipes.length, 1);
  assert.equal(result.recipes[0].source, "wanwei");
  assert.equal(calls[0].source, "mxnzp");
  assert.equal(calls[1].source, "wanwei");
});

test("wanwei is called when mxnzp request fails", async () => {
  const calls: RecipeFetchRequest[] = [];
  const result = await searchRecipesCascade({
    query: "晚餐",
    page: 1,
    pageSize: 10,
    config: baseConfig,
    fetcher: async (request) => {
      calls.push(request);
      if (request.source === "mxnzp") {
        throw new Error("mxnzp unavailable");
      }
      return {
        showapi_res_code: 0,
        showapi_res_body: {
          datas: [
            {
              id: "w2",
              cpName: "清炒西兰花",
              smallImg: "https://img.example/broccoli.jpg"
            }
          ]
        }
      };
    }
  });

  assert.equal(result.recipes.length, 1);
  assert.equal(result.recipes[0].source, "wanwei");
  assert.equal(calls.length, 2);
});

test("friendly empty result is returned when every recipe source is empty", async () => {
  const result = await searchRecipesCascade({
    query: "不存在的菜",
    page: 1,
    pageSize: 10,
    config: baseConfig,
    fetcher: async () => ({ code: 1, data: { list: [] } })
  });

  assert.deepEqual(result.recipes, []);
  assert.equal(result.totalMatches, 0);
  assert.match(result.fallbackReason ?? "", /暂时没找到/);
});

test("mxnzp detail is loaded only for mxnzp recipe ids", async () => {
  const calls: RecipeFetchRequest[] = [];
  const detail = await loadRecipeDetail({
    id: "mxnzp_37",
    config: baseConfig,
    fetcher: async (request) => {
      calls.push(request);
      return {
        code: 1,
        data: {
          id: "37",
          name: "番茄炒蛋",
          tips: "趁热吃",
          duration: "10分钟",
          difficulty: "简单",
          ingredient: [{ name: "鸡蛋", amount: "2个" }],
          instruction: [
            { step: "1", text: "打散鸡蛋。", url: "https://img.example/step1.jpg" },
            { step: "2", text: "下锅翻炒。", url: "https://img.example/step2.jpg" }
          ]
        }
      };
    }
  });

  assert.equal(detail?.id, "mxnzp_37");
  assert.deepEqual(detail?.steps, ["打散鸡蛋。", "下锅翻炒。"]);
  assert.deepEqual(detail?.stepImageUrls, ["https://img.example/step1.jpg", "https://img.example/step2.jpg"]);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, baseConfig.mxnzpDetailUrl);

  const nonMxnzp = await loadRecipeDetail({
    id: "showapi_w1",
    config: baseConfig,
    fetcher: async () => {
      throw new Error("should not be called");
    }
  });
  assert.equal(nonMxnzp, null);
});
