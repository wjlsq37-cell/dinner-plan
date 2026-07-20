import type { VercelRequest, VercelResponse } from "@vercel/node";
import { lookup } from "node:dns/promises";

const DEFAULT_UPSTREAM = "https://dinner-plan-amber.vercel.app";

function json(response: VercelResponse, status: number, body: unknown) {
  response
    .status(status)
    .setHeader("cache-control", "no-store")
    .setHeader("x-content-type-options", "nosniff")
    .json(body);
}

function isBlockedHost(hostname: string): boolean {
  const host = hostname.toLowerCase().replace(/^\[|\]$/g, "");
  if (host === "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal") || host === "0.0.0.0" || host === "::1") return true;
  if (/^127\./.test(host) || /^10\./.test(host) || /^169\.254\./.test(host) || /^192\.168\./.test(host)) return true;
  const match172 = host.match(/^172\.(\d+)\./);
  if (match172 && Number(match172[1]) >= 16 && Number(match172[1]) <= 31) return true;
  if (/^(fc|fd|fe8|fe9|fea|feb)/i.test(host.replaceAll(":", ""))) return true;
  return ["metadata.google.internal", "metadata.aws.internal", "100.100.100.200"].includes(host);
}

function upstreamUrl(): URL {
  const url = new URL(process.env.UPSTREAM_API_BASE_URL || DEFAULT_UPSTREAM);
  if (url.protocol !== "https:" || url.username || url.password || isBlockedHost(url.hostname)) throw new Error("unsafe_upstream");
  return url;
}

async function assertPublicResolution(url: URL): Promise<void> {
  const addresses = await lookup(url.hostname, { all: true, verbatim: true });
  if (!addresses.length || addresses.some((entry) => isBlockedHost(entry.address))) throw new Error("unsafe_resolution");
}

export default async function handler(request: VercelRequest, response: VercelResponse) {
  if (request.method !== "GET") return json(response, 405, { proxyReachable: false, backendConfigured: false, message: "请求方式不支持。" });
  try {
    const base = upstreamUrl();
    await assertPublicResolution(base);
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 10_000);
    const headers: Record<string, string> = { accept: "application/json" };
    if (process.env.UPSTREAM_APP_TOKEN) headers["x-app-token"] = process.env.UPSTREAM_APP_TOKEN;
    const upstream = await fetch(new URL("/api/health", base), { headers, signal: controller.signal, redirect: "error" }).finally(() => clearTimeout(timer));
    const health = await upstream.json().catch(() => null) as { ok?: unknown } | null;
    const configured = upstream.ok && health?.ok === true;
    json(response, 200, {
      proxyReachable: upstream.ok,
      backendConfigured: configured,
      message: !upstream.ok
        ? upstream.status === 401 || upstream.status === 403 ? "服务鉴权失败。" : "线上服务暂时不可用。"
        : configured ? "线上服务连接正常。" : "线上服务已连接，但推荐服务配置不完整。"
    });
  } catch (error) {
    const message = error instanceof Error && error.name === "AbortError"
      ? "线上服务连接超时。"
      : error instanceof Error && (error.message === "unsafe_upstream" || error.message === "unsafe_resolution")
        ? "线上服务配置无效。"
        : "暂时无法连接线上服务。";
    json(response, 200, { proxyReachable: false, backendConfigured: false, message });
  }
}
