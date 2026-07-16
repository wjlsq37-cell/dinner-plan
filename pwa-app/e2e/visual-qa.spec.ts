import { expect, test } from "@playwright/test";

test("capture reference states", async ({ page, context }, testInfo) => {
  const errors: string[] = [];
  page.on("console", (message) => { if (message.type() === "error") errors.push(message.text()); });
  page.on("pageerror", (error) => errors.push(error.message));
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "吃点啥" })).toBeVisible();
  await page.waitForTimeout(550);
  await page.screenshot({ path: testInfo.outputPath("home.png"), animations: "disabled" });

  await page.goto("/settings");
  await expect(page.getByRole("heading", { name: "偏好设置" })).toBeVisible();
  await page.waitForTimeout(550);
  await page.screenshot({ path: testInfo.outputPath("settings.png"), animations: "disabled" });

  await page.goto("/saved");
  await expect(page.getByRole("heading", { name: "收藏" })).toBeVisible();
  await page.waitForTimeout(550);
  await page.screenshot({ path: testInfo.outputPath("saved.png"), animations: "disabled" });

  await context.grantPermissions(["geolocation"], { origin: "http://127.0.0.1:4173" });
  await context.setGeolocation({ latitude: 30.25, longitude: 120.16 });
  await page.route("**/api/backend/recommend/restaurant", async (route) => route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({
      restaurants: [
        { id: "qa-1", source: "amap", name: "老娘舅（杭州湖滨店）", category: "公司企业", tags: ["快餐"], address: "湖滨商圈", distance: "72m", rating: "4.5", price: "人均 ¥30.00", open: "营业中", phone: "", coverUrl: "", reason: "这家店和你的搜索更接近，距离很近。" },
        { id: "qa-2", source: "amap", name: "稻状元（湖滨中心店）", category: "餐饮服务", tags: ["中餐"], address: "海聚中心", distance: "74m", rating: "4.4", price: "人均 ¥28.00", open: "营业中", phone: "", coverUrl: "", reason: "这家店和你的搜索更接近，距离很近。" }
      ],
      locationUsed: { latitude: 30.25, longitude: 120.16, text: "杭州湖滨中心" }
    })
  }));
  await page.goto("/nearby");
  await page.getByRole("button", { name: "定位", exact: true }).click();
  await expect(page.getByText("老娘舅（杭州湖滨店）", { exact: true })).toBeVisible();
  await page.waitForTimeout(550);
  await page.screenshot({ path: testInfo.outputPath("nearby.png"), animations: "disabled" });
  expect(errors).toEqual([]);
});
