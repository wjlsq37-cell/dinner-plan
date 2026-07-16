import type { VercelRequest, VercelResponse } from "@vercel/node";
import { assertPublicResolution, isBodyAllowed, json, safeHttpsUrl, safeMessage } from "../_shared.js";
import { backendPath } from "./route.js";

function upstreamFailure(response: VercelResponse, status: number, body: unknown) {
  const upstreamMessage = body && typeof body === "object" && typeof (body as { message?: unknown }).message === "string"
    ? String((body as { message: string }).message).slice(0, 160)
    : "";
  if (status === 400) return json(response, 400, { error: "invalid_request", message: upstreamMessage || "请求参数不完整。" });
  if (status === 401 || status === 403) return json(response, status, { error: "upstream_auth", message: "线上服务鉴权失败，请检查部署配置。" });
  if (status === 404) return json(response, 502, { error: "upstream_route_missing", message: "线上服务接口版本不匹配，请重新部署后端。" });
  if (status === 408 || status === 504) return json(response, 504, { error: "upstream_timeout", message: "线上服务响应超时，请稍后再试。" });
  return json(response, 502, { error: "upstream_error", message: "线上服务暂时不可用，请稍后再试。" });
}

export default async function handler(request: VercelRequest, response: VercelResponse) {
  if (!isBodyAllowed(request)) return json(response, 413, { error: "body_too_large", message: "请求内容过大。" });
  let pieces: string[];
  try {
    pieces = backendPath(request);
  } catch {
    return json(response, 404, { error: "proxy_route_missing", message: "当前操作不受代理支持。" });
  }

  try {
    const base = safeHttpsUrl(process.env.UPSTREAM_API_BASE_URL || "https://dinner-plan-amber.vercel.app");
    await assertPublicResolution(base);
    const upstream = new URL(`/api/${pieces.map((item) => encodeURIComponent(item)).join("/")}`, base);
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
    }).finally(() => clearTimeout(timeout));
    const text = await upstreamResponse.text();
    let body: unknown;
    try {
      body = JSON.parse(text);
    } catch {
      return json(response, 502, { error: "invalid_upstream_response", message: "线上服务返回了无效数据，请稍后再试。" });
    }
    if (!upstreamResponse.ok) return upstreamFailure(response, upstreamResponse.status, body);
    json(response, 200, body);
  } catch (error) {
    json(response, 502, { error: "upstream_error", message: safeMessage(error) });
  }
}
