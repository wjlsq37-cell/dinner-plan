import "@testing-library/jest-dom/vitest";
import { beforeEach, vi } from "vitest";

beforeEach(() => {
  Object.defineProperty(window.navigator, "onLine", { configurable: true, value: true });
  vi.restoreAllMocks();
});
