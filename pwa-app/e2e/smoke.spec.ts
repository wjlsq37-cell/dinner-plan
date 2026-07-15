import { expect, test } from "@playwright/test";

test("home exposes both core journeys and bottom navigation", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "吃点啥" })).toBeVisible();
  await expect(page.getByRole("button", { name: /今晚自己做点啥/ })).toBeVisible();
  await expect(page.getByRole("button", { name: /看看周围有什么/ })).toBeVisible();
  await page.getByRole("link", { name: /收藏/ }).click();
  await expect(page.getByRole("heading", { name: "我的收藏" })).toBeVisible();
});

test("cook flow preserves query state across refresh", async ({ page }) => {
  await page.goto("/cook");
  const input = page.getByPlaceholder(/两荤一素/);
  await input.fill("清淡快手菜");
  await page.reload();
  await expect(page.getByRole("heading", { name: "在家做点啥" })).toBeVisible();
});

test("manual restaurant location remains available without geolocation", async ({ page, context }) => {
  await context.grantPermissions([], { origin: "http://127.0.0.1:4173" });
  await page.goto("/nearby");
  const input = page.getByPlaceholder(/城市、商圈/);
  await input.fill("杭州西湖");
  await expect(input).toHaveValue("杭州西湖");
});
