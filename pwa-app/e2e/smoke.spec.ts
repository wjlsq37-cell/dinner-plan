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

test("cook flow keeps results but clears the search query after reopening", async ({ page }) => {
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
  await expect(input).toHaveValue("");
});

test("cook generation continues while another page is open", async ({ page }) => {
  await page.route("**/api/backend/recommend/cook", async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 900));
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ intent: "RECIPE_SINGLE", summary: "后台生成完成", mealPlans: [], recipes: [{ id: "background-result", name: "后台生成菜谱", cuisine: "家常菜", taste: [], tags: [], difficulty: "简单", cookTime: "20 分钟", servings: "2 人份", coverUrl: "", reason: "", ingredients: [], steps: [], tips: "" }], source: "DATABASE", totalMatches: 1 })
    });
  });
  await page.goto("/cook");
  await page.getByRole("button", { name: "单道菜" }).click();
  await page.getByPlaceholder(/两荤一素/).fill("后台生成");
  await page.getByRole("button", { name: "搜索菜谱" }).click();
  await expect(page.getByRole("heading", { name: "正在菜谱库里筛选" })).toBeVisible();
  await page.getByRole("link", { name: /收藏/ }).click();
  await expect(page.getByRole("heading", { name: "收藏" })).toBeVisible();
  await page.goBack();
  await expect(page.getByPlaceholder(/两荤一素/)).toHaveValue("");
  await expect(page.getByRole("heading", { name: "后台生成菜谱" })).toBeVisible();
  await expect(page.getByText("后台生成完成", { exact: true })).toBeVisible();
});

test("AI mode sends the AI source and does not present a recipe database fallback as generated", async ({ page }) => {
  let requestedSource = "";
  await page.route("**/api/backend/recommend/cook", async (route) => {
    requestedSource = (await route.request().postDataJSON()).cookSource;
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ intent: "RECIPE_SINGLE", summary: "已为你找到菜谱", mealPlans: [], recipes: [{ id: "mxnzp_1", name: "土豆牛肉", cuisine: "家常菜", taste: [], tags: [], difficulty: "简单", cookTime: "20 分钟", servings: "2 人份", coverUrl: "", reason: "", ingredients: [], steps: [], tips: "", source: "mxnzp" }], source: "AI_GENERATED", totalMatches: 1 })
    });
  });
  await page.goto("/cook");
  await page.getByRole("button", { name: "单道菜" }).click();
  await page.getByPlaceholder(/两荤一素/).fill("土豆牛肉");
  await page.getByLabel("AI 生成").click();
  await expect(page.getByText(/已切换到 AI 生成/)).toBeVisible();
  await page.getByRole("button", { name: "生成推荐" }).click();
  await expect(page.getByRole("alert")).toContainText("已阻止自动切换到菜谱库");
  expect(requestedSource).toBe("AI_GENERATED");
  await expect(page.getByText("已为你找到菜谱", { exact: true })).toHaveCount(0);
});

test("cook loading keeps the original animation, adds a timer and the whole result card opens", async ({ page }) => {
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
  await expect(page.getByRole("button", { name: "搜索菜谱" })).toHaveAttribute("aria-busy", "true");
  await expect(page.getByRole("button", { name: "搜索菜谱" })).toBeDisabled();
  await expect(page.getByRole("heading", { name: "正在菜谱库里筛选" })).toBeVisible();
  await expect(page.getByText(/已用时 00:/)).toBeVisible();
  await page.getByRole("heading", { name: "清炖牛肉" }).click();
  await expect(page).toHaveURL(/\/recipe\/result-1$/);
  await expect(page.getByRole("heading", { name: "菜谱详情" })).toBeVisible();
});

test("returning from detail keeps the result card and decoded image", async ({ page }) => {
  await page.route("https://img.test/dish.png", async (route) => route.fulfill({ status: 200, contentType: "image/png", body: Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2nWQAAAAASUVORK5CYII=", "base64") }));
  await page.route("**/api/backend/recipes/image-result", async (route) => route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({ id: "image-result", name: "带图菜谱", cuisine: "家常菜", taste: [], tags: [], difficulty: "简单", cookTime: "20 分钟", servings: "2 人份", coverUrl: "", reason: "适合晚餐", ingredients: [{ name: "鸡蛋", amount: "2 个" }], steps: ["完成"], tips: "趁热吃", source: "mxnzp", stepImageUrls: [] })
  }));
  await page.route("**/api/backend/recommend/cook", async (route) => route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({ intent: "RECIPE_SINGLE", summary: "已找到", mealPlans: [], recipes: [{ id: "image-result", name: "带图菜谱", cuisine: "家常菜", taste: [], tags: ["快手"], difficulty: "简单", cookTime: "20 分钟", servings: "2 人份", coverUrl: "https://img.test/dish.png", reason: "适合晚餐", ingredients: [], steps: ["完成"], tips: "趁热吃", source: "mxnzp" }], source: "DATABASE", totalMatches: 1 })
  }));
  await page.goto("/cook");
  await page.getByRole("button", { name: "单道菜" }).click();
  await page.getByPlaceholder(/两荤一素/).fill("带图菜谱");
  await page.getByRole("button", { name: "搜索菜谱" }).click();
  const card = page.getByRole("link", { name: "查看菜谱：带图菜谱" });
  await expect(card.locator("img")).toHaveClass(/image-loaded/);
  await card.click();
  await expect(page.locator(".image-hero img")).toHaveAttribute("src", "https://img.test/dish.png");
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

test("mobile form controls keep a non-zooming font size", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "mobile-chrome", "iOS auto-zoom prevention applies to the mobile layout");
  await page.goto("/nearby");
  await expect(page.getByPlaceholder(/菜系、预算/)).toHaveCSS("font-size", "16px");
  await page.getByRole("button", { name: "更改" }).click();
  await expect(page.getByPlaceholder("城市、商圈、地址或地标")).toHaveCSS("font-size", "16px");
  await page.goto("/settings/developer");
  await expect(page.getByLabel("AI Base URL")).toHaveCSS("font-size", "16px");
  await expect(page.getByLabel("AI 来源")).toHaveCSS("font-size", "16px");
});

test("developer mode never falls back to the default backend route", async ({ page }) => {
  const operations: string[] = [];
  let backendCalls = 0;
  await page.route("**/api/backend/**", async (route) => {
    backendCalls += 1;
    await route.fulfill({ status: 500, contentType: "application/json", body: JSON.stringify({ error: "unexpected_backend" }) });
  });
  await page.route("**/api/direct", async (route) => {
    const body = await route.request().postDataJSON();
    operations.push(body.operation);
    if (body.operation === "status") return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ proxyReachable: true, backendConfigured: false, message: "开发者直连通道可用。" }) });
    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ intent: "RECIPE_SINGLE", summary: "开发者模式生成完成", mealPlans: [], recipes: [], source: "AI_GENERATED", totalMatches: 0 }) });
  });
  await page.goto("/settings/developer");
  await page.getByLabel("开发者功能").check();
  await expect(page.getByText(/不会访问默认后端/)).toBeVisible();
  await page.getByLabel("AI API Key").fill("test-only-key");
  await page.getByRole("button", { name: "检测开发者直连配置" }).click();
  await expect(page.getByText(/开发者直连通道可用/)).toBeVisible();
  await page.getByRole("link", { name: /首页/ }).click();
  await page.getByRole("button", { name: /自己做/ }).click();
  await page.getByRole("button", { name: "单道菜" }).click();
  await page.getByLabel("AI 生成").click();
  await page.getByPlaceholder(/两荤一素/).fill("番茄炒蛋");
  await page.getByRole("button", { name: "生成推荐" }).click();
  await expect(page.getByText("开发者模式生成完成", { exact: true })).toBeVisible();
  expect(operations).toEqual(["status", "cook"]);
  expect(backendCalls).toBe(0);
});

test("nearby search keeps the original animation and shows a live timer", async ({ page }) => {
  await page.route("**/api/backend/recommend/restaurant", async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 350));
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ restaurants: [], locationUsed: { text: "上海人民广场" }, fallbackReason: "附近暂未找到符合条件的餐厅。" }) });
  });
  await page.goto("/nearby");
  await page.getByPlaceholder(/菜系、预算/).fill("火锅");
  await page.getByRole("button", { name: "搜索", exact: true }).click();
  await expect(page.getByRole("button", { name: "搜索", exact: true })).toHaveAttribute("aria-busy", "true");
  await expect(page.getByRole("button", { name: "搜索", exact: true })).toBeDisabled();
  await expect(page.getByRole("heading", { name: "正在搜索附近餐厅" })).toBeVisible();
  await expect(page.getByText(/已用时 00:/)).toBeVisible();
});

test("nearby search continues across navigation and never restores its query", async ({ page }) => {
  await page.route("**/api/backend/recommend/restaurant", async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 700));
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ restaurants: [{ id: "background-restaurant", source: "amap", name: "后台找到的餐厅", category: "日料", tags: [], address: "湖滨商圈", distance: "800m", rating: "4.6", price: "人均 ¥88", open: "营业中", phone: "", coverUrl: "", reason: "不在结果卡片展示" }], locationUsed: { text: "杭州湖滨" } }) });
  });
  await page.goto("/nearby");
  const input = page.getByPlaceholder(/菜系、预算/);
  await input.fill("日料人均 200");
  await page.getByRole("button", { name: "搜索", exact: true }).click();
  await page.getByRole("link", { name: /首页/ }).click();
  await page.getByRole("link", { name: /附近/ }).click();
  await expect(input).toHaveValue("");
  const restaurantCard = page.getByRole("link", { name: "查看餐厅：后台找到的餐厅" });
  await expect(restaurantCard).toBeVisible();
  await expect(restaurantCard).not.toContainText("不在结果卡片展示");
  const height = await restaurantCard.evaluate((element) => element.getBoundingClientRect().height);
  expect(height).toBeLessThanOrEqual(160);
});

test("secure browser location is sent to restaurant search", async ({ page, context }) => {
  const longAddress = "浙江省杭州市钱塘区白杨街道杭州绿灯物联网科技有限公司生态湿地公园";
  await context.grantPermissions(["geolocation"], { origin: "http://127.0.0.1:4173" });
  await context.setGeolocation({ latitude: 30.25, longitude: 120.16 });
  await page.route("**/api/location/reverse", async (route) => route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({ location: { latitude: 30.25, longitude: 120.16, text: longAddress } })
  }));
  await page.route("**/api/backend/recommend/restaurant", async (route) => route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({ restaurants: [], locationUsed: { latitude: 30.25, longitude: 120.16, text: longAddress }, fallbackReason: "附近暂未找到符合条件的餐厅。" })
  }));
  await page.goto("/nearby");
  await page.getByRole("button", { name: "定位", exact: true }).click();
  const locationText = page.locator(".current-location span");
  await expect(locationText).toHaveText(longAddress);
  await expect(locationText).toHaveCSS("text-overflow", "ellipsis");
  const header = await page.locator(".nearby-header").boundingBox();
  const changeButton = await page.getByRole("button", { name: "更改" }).boundingBox();
  const searchBox = await page.locator(".nearby-search").boundingBox();
  expect(header).not.toBeNull();
  expect(changeButton).not.toBeNull();
  expect(searchBox).not.toBeNull();
  expect(changeButton!.x + changeButton!.width).toBeLessThanOrEqual(header!.x + header!.width + 1);
  expect(searchBox!.x + searchBox!.width).toBeLessThanOrEqual(header!.x + header!.width + 1);
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
  expect(height).toBeLessThanOrEqual(50);
  await expect(page.locator(".bottom-nav a").first()).toHaveCSS("min-height", "44px");
});
