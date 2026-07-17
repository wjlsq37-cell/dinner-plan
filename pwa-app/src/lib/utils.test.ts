import { describe, expect, it } from "vitest";
import { addHistory, mergeRecipeRecords, parseDistanceMeters, sortRestaurants, toggleSaved, toggleString } from "./utils";
import type { Recipe, Restaurant, SavedRef } from "../types";

const restaurant = (id: string, distance: string, rating: string): Restaurant => ({ id, source: "test", name: id, category: "餐饮", tags: [], address: "", distance, rating, price: "", open: "", phone: "", coverUrl: "", reason: "" });

describe("restaurant helpers", () => {
  it("normalizes kilometer and meter distances", () => { expect(parseDistanceMeters("1.5km")).toBe(1500); expect(parseDistanceMeters("850m")).toBe(850); });
  it("sorts by distance and rating without mutating source", () => { const source = [restaurant("a", "2km", "4.8"), restaurant("b", "600m", "4.2")]; expect(sortRestaurants(source, "distance").map((item) => item.id)).toEqual(["b", "a"]); expect(sortRestaurants(source, "rating").map((item) => item.id)).toEqual(["a", "b"]); expect(source[0].id).toBe("a"); });
});

describe("local history and saves", () => {
  it("puts new saves first and toggles them off", () => { const ref: SavedRef = { kind: "recipe", id: "r1" }; expect(toggleSaved([], ref)).toEqual([ref]); expect(toggleSaved([ref], ref)).toEqual([]); });
  it("deduplicates history and keeps the latest ten", () => { const refs: SavedRef[] = Array.from({ length: 10 }, (_, index) => ({ kind: "recipe", id: String(index) })); const next = addHistory(refs, { kind: "recipe", id: "9" }); expect(next).toHaveLength(10); expect(next[0].id).toBe("9"); expect(new Set(next.map((item) => item.id)).size).toBe(10); });
});

describe("preference selection", () => {
  it("selects and deselects the same preference", () => {
    expect(toggleString(["微辣"], "清淡")).toEqual(["微辣", "清淡"]);
    expect(toggleString(["微辣", "清淡"], "清淡")).toEqual(["微辣"]);
  });
  it("does not mutate the original list", () => {
    const source = ["香菜", "海鲜"];
    toggleString(source, "香菜");
    expect(source).toEqual(["香菜", "海鲜"]);
  });
});

describe("recipe cache merging", () => {
  it("keeps the card cover and rich fields when detail data omits them", () => {
    const card = { id: "r1", name: "土豆牛肉", cuisine: "家常", taste: [], tags: ["mxnzp"], difficulty: "简单", cookTime: "20 分钟", servings: "2 人份", coverUrl: "https://img.test/card.jpg", reason: "", ingredients: [{ name: "牛肉", amount: "适量" }], steps: ["炖煮"], tips: "小火", stepImageUrls: ["https://img.test/step.jpg"] } satisfies Recipe;
    const detail = { ...card, coverUrl: "", tags: [], ingredients: [], steps: [], tips: "", stepImageUrls: [] };
    expect(mergeRecipeRecords([card], [detail])[0]).toMatchObject({ coverUrl: card.coverUrl, tags: card.tags, ingredients: card.ingredients, steps: card.steps, tips: card.tips, stepImageUrls: card.stepImageUrls });
  });
  it("keeps cached rich fields when an upstream detail omits optional arrays at runtime", () => {
    const card = { id: "r2", name: "土豆炖牛肉", cuisine: "家常", taste: ["咸鲜"], tags: ["mxnzp"], difficulty: "简单", cookTime: "30 分钟", servings: "2 人份", coverUrl: "https://img.test/card-2.jpg", reason: "", ingredients: [{ name: "土豆", amount: "2 个" }], steps: ["炖煮"], tips: "小火" } satisfies Recipe;
    const incomplete = { id: "r2", name: "土豆炖牛肉", coverUrl: "" } as Recipe;
    expect(mergeRecipeRecords([card], [incomplete])[0]).toMatchObject({ coverUrl: card.coverUrl, taste: card.taste, tags: card.tags, ingredients: card.ingredients, steps: card.steps });
  });
});
