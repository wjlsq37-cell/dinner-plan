import { describe, expect, it, vi } from "vitest";
import { requestBrowserLocation } from "./location";

function secure(value: boolean) {
  Object.defineProperty(window, "isSecureContext", { configurable: true, value });
}

describe("browser location", () => {
  it("rejects insecure pages with a clear reason", async () => {
    secure(false);
    await expect(requestBrowserLocation()).rejects.toMatchObject({ kind: "insecure_context" });
  });

  it("maps permission denial", async () => {
    secure(true);
    Object.defineProperty(navigator, "permissions", { configurable: true, value: { query: vi.fn().mockResolvedValue({ state: "prompt" }) } });
    Object.defineProperty(navigator, "geolocation", { configurable: true, value: { getCurrentPosition: (_success: PositionCallback, failure: PositionErrorCallback) => failure({ code: 1 } as GeolocationPositionError) } });
    await expect(requestBrowserLocation()).rejects.toEqual(expect.objectContaining({ kind: "permission_denied" }));
  });

  it("returns coordinates when permission succeeds", async () => {
    secure(true);
    Object.defineProperty(navigator, "permissions", { configurable: true, value: { query: vi.fn().mockResolvedValue({ state: "granted" }) } });
    Object.defineProperty(navigator, "geolocation", { configurable: true, value: { getCurrentPosition: (success: PositionCallback) => success({ coords: { latitude: 30.25, longitude: 120.16 } } as GeolocationPosition) } });
    await expect(requestBrowserLocation()).resolves.toEqual({ latitude: 30.25, longitude: 120.16, text: "当前位置" });
  });
});
