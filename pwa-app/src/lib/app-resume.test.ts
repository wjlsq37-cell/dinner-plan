import { afterEach, describe, expect, it, vi } from "vitest";
import { markExternalNavigation, resumeExternalNavigation } from "./app-resume";

afterEach(() => {
  delete document.documentElement.dataset.externalNavigation;
  document.body.replaceChildren();
  vi.restoreAllMocks();
});

describe("external navigation resume", () => {
  it("clears the pending state and restores focus", () => {
    vi.spyOn(window, "requestAnimationFrame").mockImplementation((callback) => { callback(0); return 1; });
    const link = document.createElement("a"); link.href = "https://example.com"; document.body.append(link);
    markExternalNavigation(link);
    expect(document.documentElement.dataset.externalNavigation).toBe("pending");
    resumeExternalNavigation();
    expect(document.documentElement.dataset.externalNavigation).toBeUndefined();
    expect(document.activeElement).toBe(link);
  });
});
