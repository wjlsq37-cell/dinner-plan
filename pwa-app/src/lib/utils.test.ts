import { describe, expect, it } from "vitest";
import { addHistory, parseDistanceMeters, sortRestaurants, toggleSaved } from "./utils";
import type { Restaurant, SavedRef } from "../types";

const restaurant = (id: string, distance: string, rating: string): Restaurant => ({ id, source: "test", name: id, category: "餐饮", tags: [], address: "", distance, rating, price: "", open: "", phone: "", coverUrl: "", reason: "" });

describe("restaurant helpers", () => {
  it("normalizes kilometer and meter distances", () => { expect(parseDistanceMeters("1.5km")).toBe(1500); expect(parseDistanceMeters("850m")).toBe(850); });
  it("sorts by distance and rating without mutating source", () => { const source = [restaurant("a", "2km", "4.8"), restaurant("b", "600m", "4.2")]; expect(sortRestaurants(source, "distance").map((item) => item.id)).toEqual(["b", "a"]); expect(sortRestaurants(source, "rating").map((item) => item.id)).toEqual(["a", "b"]); expect(source[0].id).toBe("a"); });
});

describe("local history and saves", () => {
  it("puts new saves first and toggles them off", () => { const ref: SavedRef = { kind: "recipe", id: "r1" }; expect(toggleSaved([], ref)).toEqual([ref]); expect(toggleSaved([ref], ref)).toEqual([]); });
  it("deduplicates history and keeps the latest ten", () => { const refs: SavedRef[] = Array.from({ length: 10 }, (_, index) => ({ kind: "recipe", id: String(index) })); const next = addHistory(refs, { kind: "recipe", id: "9" }); expect(next).toHaveLength(10); expect(next[0].id).toBe("9"); expect(new Set(next.map((item) => item.id)).size).toBe(10); });
});
