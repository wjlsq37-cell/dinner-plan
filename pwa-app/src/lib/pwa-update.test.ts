import { describe, expect, it, vi } from "vitest";
import { dismissPwaUpdate, publishPwaUpdate, subscribePwaUpdate } from "./pwa-update";

describe("PWA update bridge", () => {
  it("delivers an update that became ready before the UI subscribed", async () => {
    const updater = vi.fn().mockResolvedValue(undefined);
    const listener = vi.fn();
    publishPwaUpdate(updater);
    const unsubscribe = subscribePwaUpdate(listener);
    await Promise.resolve();
    expect(listener).toHaveBeenCalledWith(updater);
    unsubscribe();
    dismissPwaUpdate();
  });

  it("stops showing a dismissed update", async () => {
    publishPwaUpdate(vi.fn().mockResolvedValue(undefined));
    dismissPwaUpdate();
    const listener = vi.fn();
    const unsubscribe = subscribePwaUpdate(listener);
    await Promise.resolve();
    expect(listener).not.toHaveBeenCalled();
    unsubscribe();
  });
});
