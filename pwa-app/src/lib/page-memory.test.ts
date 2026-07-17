import { afterEach, describe, expect, it, vi } from "vitest";
import { clearPageMemory, readPageMemory, restorePageScroll, writePageMemory } from "./page-memory";

afterEach(() => { clearPageMemory(); vi.restoreAllMocks(); });

describe("page memory", () => {
  it("keeps view state and restores its scroll position", () => {
    const scrollTo = vi.spyOn(window, "scrollTo").mockImplementation(() => undefined);
    vi.spyOn(window, "requestAnimationFrame").mockImplementation((callback) => { callback(0); return 1; });
    writePageMemory("nearby", { scrollY: 420, hasRenderedResults: true, query: "火锅", sort: "distance" });
    expect(readPageMemory<{ scrollY: number; hasRenderedResults: boolean; query: string; sort: string }>("nearby")).toMatchObject({ query: "火锅", sort: "distance" });
    restorePageScroll("nearby");
    expect(scrollTo).toHaveBeenCalledWith({ top: 420, behavior: "auto" });
  });
});
