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

test("cook loading is prominent and the whole result card opens", async ({ page }) => {
  await page.route("**/api/backend/recommend/cook", async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 350));
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ intent: "RECIPE_SINGLE", summary: "已找到", mealPlans: [], recipes: [{ id: "result-1", name: "清炖牛肉", cuisine: "家常菜", taste: [], tags: ["清淡"], difficulty: "简单", cookTime: "40 分钟", servings: "2 人份", coverUrl: "", reason: "适合晚餐", ingredients: [], steps: ["炖煮"], tips: "小火慢炖" }], source: "DATABASE", totalMatches: 1 })
    });
  });
  await page.goto("/cook");
  await page.getByRole("button", { name: "单道菜" }).click();
  await page.getByPlaceholder(/两荤一素/).fill("牛肉");
  await page.getByRole("button", { name: "搜索菜谱" }).click();
  await expect(page.getByRole("button", { name: /正在搜索/ })).toHaveAttribute("aria-busy", "true");
  await expect(page.getByRole("heading", { name: "正在菜谱库里筛选" })).toBeVisible();
  await page.getByRole("heading", { name: "清炖牛肉" }).click();
  await expect(page).toHaveURL(/\/recipe\/result-1$/);
  await expect(page.getByRole("heading", { name: "菜谱详情" })).toBeVisible();
});

test("returning from detail keeps the result card and decoded image", async ({ page }) => {
  await page.route("https://img.test/dish.png", async (route) => route.fulfill({ status: 200, contentType: "image/png", body: Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2nWQAAAAASUVORK5CYII=", "base64") }));
  await page.route("**/api/backend/recommend/cook", async (route) => route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({ intent: "RECIPE_SINGLE", summary: "已找到", mealPlans: [], recipes: [{ id: "image-result", name: "带图菜谱", cuisine: "家常菜", taste: [], tags: ["快手"], difficulty: "简单", cookTime: "20 分钟", servings: "2 人份", coverUrl: "https://img.test/dish.png", reason: "适合晚餐", ingredients: [], steps: ["完成"], tips: "趁热吃", source: "seed" }], source: "DATABASE", totalMatches: 1 })
  }));
  await page.goto("/cook");
  await page.getByRole("button", { name: "单道菜" }).click();
  await page.getByPlaceholder(/两荤一素/).fill("带图菜谱");
  await page.getByRole("button", { name: "搜索菜谱" }).click();
  const card = page.getByRole("link", { name: "查看菜谱：带图菜谱" });
  await expect(card.locator("img")).toHaveClass(/image-loaded/);
  await card.click();
  await page.getByRole("button", { name: "返回" }).click();
  const restored = page.getByRole("link", { name: "查看菜谱：带图菜谱" });
  await expect(restored).not.toHaveClass(/card-enter/);
  await expect(restored.locator("img")).toHaveClass(/image-loaded/);
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

test("nearby search shows an obvious live loading state", async ({ page }) => {
  await page.route("**/api/backend/recommend/restaurant", async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 350));
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ restaurants: [], locationUsed: { text: "上海人民广场" }, fallbackReason: "附近暂未找到符合条件的餐厅。" }) });
  });
  await page.goto("/nearby");
  await page.getByPlaceholder(/菜系、预算/).fill("火锅");
  await page.getByRole("button", { name: "搜索", exact: true }).click();
  await expect(page.getByRole("button", { name: /搜索中/ })).toHaveAttribute("aria-busy", "true");
  await expect(page.getByRole("heading", { name: "正在搜索附近餐厅" })).toBeVisible();
  await expect(page.getByText(/已用时 00:/)).toBeVisible();
});

test("secure browser location is sent to restaurant search", async ({ page, context }) => {
  await context.grantPermissions(["geolocation"], { origin: "http://127.0.0.1:4173" });
  await context.setGeolocation({ latitude: 30.25, longitude: 120.16 });
  await page.route("**/api/location/reverse", async (route) => route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({ location: { latitude: 30.25, longitude: 120.16, text: "浙江省杭州市上城区湖滨街道" } })
  }));
  await page.route("**/api/backend/recommend/restaurant", async (route) => route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({ restaurants: [], locationUsed: { latitude: 30.25, longitude: 120.16, text: "浙江省杭州市上城区湖滨街道" }, fallbackReason: "附近暂未找到符合条件的餐厅。" })
  }));
  await page.goto("/nearby");
  await page.getByRole("button", { name: "定位", exact: true }).click();
  await expect(page.getByText("浙江省杭州市上城区湖滨街道", { exact: true })).toBeVisible();
});

test("map return resumes the existing PWA controls", async ({ page, context }) => {
  await context.route("https://uri.amap.com/**", async (route) => route.fulfill({ status: 200, contentType: "text/html", body: "<title>地图</title>" }));
  await page.goto("/restaurant/restaurant_hunan");
  const [popup] = await Promise.all([
    page.waitForEvent("popup"),
    page.getByRole("link", { name: "导航去这里" }).click()
  ]);
  await popup.close();
  await page.bringToFront();
  await page.evaluate(() => window.dispatchEvent(new PageTransitionEvent("pageshow", { persisted: true })));
  await page.getByRole("button", { name: "收藏", exact: true }).click();
  await expect(page.getByRole("button", { name: "已收藏" })).toBeVisible();
});

test("bottom navigation uses the compact content height", async ({ page }) => {
  await page.goto("/");
  const height = await page.locator(".bottom-nav").evaluate((element) => Number.parseFloat(getComputedStyle(element).height));
  expect(height).toBeLessThanOrEqual(58);
  await expect(page.locator(".bottom-nav a").first()).toHaveCSS("min-height", "44px");
});
