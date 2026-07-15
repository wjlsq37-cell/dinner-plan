import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  retries: 1,
  use: { baseURL: "http://127.0.0.1:4173", trace: "on-first-retry" },
  webServer: { command: "npm run build && npm run preview -- --host 127.0.0.1", port: 4173, reuseExistingServer: true },
  projects: [
    { name: "mobile-chrome", use: { ...devices["Pixel 7"], channel: "chrome" } },
    { name: "desktop-chrome", use: { ...devices["Desktop Chrome"], channel: "chrome" } }
  ]
});
