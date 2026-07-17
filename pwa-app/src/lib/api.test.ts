import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiGateway } from "./api";
import { defaultState } from "./store";

afterEach(() => vi.unstubAllGlobals());

describe("ApiGateway error mapping", () => {
  it("maps an unreachable proxy", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("network")));
    await expect(new ApiGateway(defaultState.developerSettings).status()).rejects.toMatchObject({ kind: "proxy_unreachable" });
  });

  it("maps backend authentication failures", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ error: "unauthorized", message: "unauthorized" }), { status: 401, headers: { "content-type": "application/json" } })));
    await expect(new ApiGateway(defaultState.developerSettings).status()).rejects.toEqual(expect.objectContaining({ kind: "auth", status: 401 }));
  });

  it("uses the developer endpoint without exposing settings in the URL", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ intent: "RECIPE_SINGLE", summary: "ok", mealPlans: [], recipes: [], source: "AI_GENERATED", totalMatches: 0 }), { status: 200, headers: { "content-type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);
    await new ApiGateway({ ...defaultState.developerSettings, enabled: true, aiApiKey: "secret" }).cook({ query: "test", mode: "RECIPE_SINGLE", cookSource: "AI_GENERATED" });
    expect(fetchMock.mock.calls[0][0]).toBe("/api/direct");
    expect(String(fetchMock.mock.calls[0][0])).not.toContain("secret");
  });

  it("sends the AI source to the backend and rejects a silent recipe database fallback", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      intent: "RECIPE_SINGLE", summary: "已找到菜谱", mealPlans: [],
      recipes: [{ ...defaultState.recipeCache[0], id: "mxnzp_42", source: "mxnzp" }],
      source: "AI_GENERATED", totalMatches: 1
    }), { status: 200, headers: { "content-type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);
    const request = { query: "土豆牛肉", mode: "RECIPE_SINGLE" as const, cookSource: "AI_GENERATED" as const };
    await expect(new ApiGateway(defaultState.developerSettings).cook(request)).rejects.toMatchObject({ kind: "ai_unavailable" });
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toMatchObject({ cookSource: "AI_GENERATED" });
  });

  it("accepts an actual AI cook response", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
      intent: "RECIPE_SINGLE", summary: "AI 已生成", mealPlans: [],
      recipes: [{ ...defaultState.recipeCache[0], id: "ai_recipe_42", source: "ai_generated" }],
      source: "AI_GENERATED", totalMatches: 1
    }), { status: 200, headers: { "content-type": "application/json" } })));
    await expect(new ApiGateway(defaultState.developerSettings).cook({ query: "土豆牛肉", mode: "RECIPE_SINGLE", cookSource: "AI_GENERATED" })).resolves.toMatchObject({ summary: "AI 已生成" });
  });

  it("routes every developer operation through the fixed direct endpoint", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ restaurants: [] }), { status: 200, headers: { "content-type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);
    const gateway = new ApiGateway({ ...defaultState.developerSettings, enabled: true });
    await gateway.restaurants({ query: "面馆", mode: "RESTAURANT" });
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toMatchObject({ operation: "restaurant" });
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ ...defaultState.recipeCache[0] }), { status: 200, headers: { "content-type": "application/json" } }));
    await gateway.recipe("recipe-id");
    expect(JSON.parse(String(fetchMock.mock.calls[1][1]?.body))).toMatchObject({ operation: "recipe", id: "recipe-id" });
    expect(fetchMock.mock.calls.every((call) => call[0] === "/api/direct")).toBe(true);
  });

  it("uses the fixed same-origin reverse geocode endpoint", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ location: { latitude: 30.25, longitude: 120.16, text: "杭州市湖滨街道" } }), { status: 200, headers: { "content-type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);
    await new ApiGateway(defaultState.developerSettings).reverseGeocode({ latitude: 30.25, longitude: 120.16 });
    expect(fetchMock.mock.calls[0][0]).toBe("/api/location/reverse");
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toEqual({ latitude: 30.25, longitude: 120.16 });
  });

  it("reports a missing proxy route as a deployment problem", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ error: "proxy_route_missing", message: "missing" }), { status: 404, headers: { "content-type": "application/json" } })));
    await expect(new ApiGateway(defaultState.developerSettings).cook({ query: "牛肉", mode: "RECIPE_SINGLE" })).rejects.toMatchObject({ kind: "proxy_unreachable", status: 404 });
  });

  it("rejects successful responses with the wrong DTO shape", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ ok: true }), { status: 200, headers: { "content-type": "application/json" } })));
    await expect(new ApiGateway(defaultState.developerSettings).restaurants({ query: "面馆", mode: "RESTAURANT" })).rejects.toMatchObject({ kind: "invalid_response" });
  });
});
