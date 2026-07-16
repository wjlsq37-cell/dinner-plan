import type { VercelRequest, VercelResponse } from "@vercel/node";
import { assertPublicResolution, json, safeHttpsUrl } from "./_shared.js";

export default async function handler(request: VercelRequest, response: VercelResponse) {
  if (request.method !== "GET") return json(response, 405, { proxyReachable: false, backendConfigured: false, message: "请求方式不支持。" });
  try {
    const base = safeHttpsUrl(process.env.UPSTREAM_API_BASE_URL || "https://dinner-plan.vercel.app");
    await assertPublicResolution(base);
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 10_000);
    const headers: Record<string, string> = { accept: "application/json" };
    if (process.env.UPSTREAM_APP_TOKEN) headers["x-app-token"] = process.env.UPSTREAM_APP_TOKEN;
    const upstream = await fetch(new URL("/api/health", base), { headers, signal: controller.signal, redirect: "error" }).finally(() => clearTimeout(timer));
    json(response, 200, {
      proxyReachable: upstream.ok,
      backendConfigured: upstream.ok,
      message: upstream.ok ? "线上服务连接正常。" : upstream.status === 401 || upstream.status === 403 ? "服务鉴权失败。" : "线上服务暂时不可用。"
    });
  } catch {
    json(response, 200, { proxyReachable: false, backendConfigured: false, message: "暂时无法连接线上服务。" });
  }
}
