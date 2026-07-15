import type { VercelRequest, VercelResponse } from "@vercel/node";
import { assertPublicResolution, isBodyAllowed, json, safeHttpsUrl, safeMessage } from "../_shared.js";

export default async function handler(request: VercelRequest, response: VercelResponse) {
  if (!isBodyAllowed(request)) return json(response, 413, { error: "body_too_large", message: "请求内容过大。" });
  try {
    const base = safeHttpsUrl(process.env.UPSTREAM_API_BASE_URL || "https://dinner-plan.vercel.app");
    await assertPublicResolution(base);
    const pieces = Array.isArray(request.query.path) ? request.query.path : [request.query.path].filter(Boolean);
    const upstream = new URL(`/api/${pieces.map((item) => encodeURIComponent(String(item))).join("/")}`, base);
    const headers: Record<string, string> = { accept: "application/json" };
    if (request.method !== "GET" && request.method !== "HEAD") headers["content-type"] = "application/json";
    if (process.env.UPSTREAM_APP_TOKEN) headers["x-app-token"] = process.env.UPSTREAM_APP_TOKEN;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 185_000);
    const upstreamResponse = await fetch(upstream, {
      method: request.method,
      headers,
      body: request.method === "GET" || request.method === "HEAD" ? undefined : JSON.stringify(request.body ?? {}),
      signal: controller.signal,
      redirect: "error"
    });
    clearTimeout(timeout);
    const text = await upstreamResponse.text();
    response.status(upstreamResponse.status).setHeader("content-type", "application/json; charset=utf-8").setHeader("cache-control", "no-store").send(text);
  } catch (error) {
    json(response, 502, { error: "upstream_error", message: safeMessage(error) });
  }
}
