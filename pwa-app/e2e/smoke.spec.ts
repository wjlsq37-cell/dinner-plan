import { expect, test } from "@playwright/test";

test("home matches the four primary journeys", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "吃点啥" })).toBeVisible();
  await expect(page.getByRole("button", { name: /自己做/ })).toBeVisible();
  await expect(page.getByRole("button", { name: /附近吃/ })).toBeVisible();
  await expect(page.getByRole("navigation", { name: "主导航" })).toBeVisible();
  await page.getByRole("link", { name: /收藏/ }).click();
  await expect(page.getByRole("heading", { name: "收藏" })).toBeVisible();
});

test("cook flow persists the last successful query", async ({ page }) => {
  await page.route("**/api/backend/recommend/cook", async (route) => route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({ intent: "RECIPE_SINGLE", summary: "已筛选", mealPlans: [], recipes: [], source: "DATABASE", totalMatches: 0 })
  }));
  await page.goto("/cook");
  await page.getByRole("button", { name: "单道菜" }).click();
  const input = page.getByPlaceholder(/两荤一素/);
  await input.fill("清淡快手菜");
  await page.getByRole("button", { name: "搜索菜谱" }).click();
  await expect(page.getByText("已筛选", { exact: true })).toBeVisible();
  await page.reload();
  await expect(input).toHaveValue("清淡快手菜");
});

test("preference chips select, deselect and persist", async ({ page }) => {
  await page.goto("/settings");
  const chip = page.getByRole("button", { name: "清淡", exact: true });
  await expect(chip).toHaveAttribute("aria-pressed", "true");
  await chip.click();
  await expect(chip).toHaveAttribute("aria-pressed", "false");
  await page.reload();
  await expect(chip).toHaveAttribute("aria-pressed", "false");
  await chip.click();
  await expect(chip).toHaveAttribute("aria-pressed", "true");
});

test("manual restaurant location remains available after denial", async ({ page, context }) => {
  await context.clearPermissions();
  await page.goto("/nearby");
  await page.getByRole("button", { name: "更改" }).click();
  const input = page.getByPlaceholder("城市、商圈、地址或地标");
  await input.fill("杭州西湖");
  await expect(input).toHaveValue("杭州西湖");
});

test("secure browser location is sent to restaurant search", async ({ page, context }) => {
  await context.grantPermissions(["geolocation"], { origin: "http://127.0.0.1:4173" });
  await context.setGeolocation({ latitude: 30.25, longitude: 120.16 });
  await page.route("**/api/backend/recommend/restaurant", async (route) => route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({ restaurants: [], locationUsed: { latitude: 30.25, longitude: 120.16, text: "当前位置" }, fallbackReason: "附近暂未找到符合条件的餐厅。" })
  }));
  await page.goto("/nearby");
  await page.getByRole("button", { name: "定位", exact: true }).click();
  await expect(page.getByText("当前位置", { exact: true })).toBeVisible();
});
