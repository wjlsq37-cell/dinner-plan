import type { VercelRequest, VercelResponse } from "@vercel/node";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { DeveloperSettings } from "../src/types";

const settingsBase: DeveloperSettings = { enabled: true, aiProvider: "deepseek", aiBaseUrl: "https://api.deepseek.com", aiApiKey: "", aiModel: "deepseek-v4-flash", amapWebKey: "", recipeApiSource: "wanwei", recipeApiBaseUrl: "", recipeApiAppId: "", recipeApiSecret: "", wanweiRecipeAppKey: "", recipePageSize: 20, maxWaitSeconds: 180 };

const fetchJsonMock = vi.hoisted(() => vi.fn());
vi.mock("./_shared.js", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./_shared")>()),
  fetchJson: fetchJsonMock
}));

import handler from "./direct";

function responseMock() {
  const response = {
    status: vi.fn(), setHeader: vi.fn(), json: vi.fn()
  } as unknown as VercelResponse;
  vi.mocked(response.status).mockReturnValue(response);
  vi.mocked(response.setHeader).mockReturnValue(response);
  vi.mocked(response.json).mockReturnValue(response);
  return response;
}

function request(body: unknown) {
  return { method: "POST", headers: {}, body } as unknown as VercelRequest;
}

beforeEach(() => fetchJsonMock.mockReset());

describe("developer direct operations", () => {
  it("normalizes an AI cook response", async () => {
    fetchJsonMock.mockResolvedValue({ choices: [{ message: { content: JSON.stringify({ summary: "完成", mealPlans: [], recipes: [{ id: "r1", name: "番茄炒蛋" }] }) } }] });
    const response = responseMock();
    await handler(request({ operation: "cook", settings: { ...settingsBase, aiApiKey: "test-key" }, payload: { query: "家常菜", mode: "RECIPE_SINGLE", cookSource: "AI_GENERATED" } }), response);
    expect(response.status).toHaveBeenCalledWith(200);
    expect(response.json).toHaveBeenCalledWith(expect.objectContaining({ summary: "完成", totalMatches: 1 }));
  });

  it("maps Amap POIs using supplied browser coordinates", async () => {
    fetchJsonMock.mockResolvedValue({ pois: [{ id: "p1", name: "湖滨小馆", location: "120.16,30.25", distance: "72", biz_ext: { rating: "4.6", cost: "35" }, type: "餐饮服务;中餐厅" }] });
    const response = responseMock();
    await handler(request({ operation: "restaurant", settings: { ...settingsBase, amapWebKey: "test-key" }, payload: { query: "中餐", mode: "RESTAURANT", location: { latitude: 30.25, longitude: 120.16 } } }), response);
    expect(response.status).toHaveBeenCalledWith(200);
    expect(response.json).toHaveBeenCalledWith(expect.objectContaining({ restaurants: [expect.objectContaining({ name: "湖滨小馆", distance: "72m" })] }));
  });

  it("normalizes mxnzp search and detail responses", async () => {
    fetchJsonMock.mockResolvedValueOnce({ data: { list: [{ id: "42", name: "牛肉面", pic: "https://example.com/noodle.jpg" }] } });
    const settings = { ...settingsBase, recipeApiSource: "mxnzp" as const, recipeApiAppId: "app", recipeApiSecret: "secret" };
    const searchResponse = responseMock();
    await handler(request({ operation: "cook", settings, payload: { query: "牛肉面", mode: "RECIPE_SINGLE", cookSource: "DATABASE" } }), searchResponse);
    expect(searchResponse.json).toHaveBeenCalledWith(expect.objectContaining({ recipes: [expect.objectContaining({ id: "mxnzp_42", name: "牛肉面" })] }));

    fetchJsonMock.mockResolvedValueOnce({ data: { id: "42", name: "牛肉面", materialList: ["牛肉"], stepList: [{ text: "煮面", pic: "https://example.com/step.jpg" }] } });
    const detailResponse = responseMock();
    await handler(request({ operation: "recipe", settings, id: "mxnzp_42" }), detailResponse);
    expect(detailResponse.json).toHaveBeenCalledWith(expect.objectContaining({ id: "mxnzp_42", steps: ["煮面"] }));
  });
});
