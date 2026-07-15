import type { VercelRequest, VercelResponse } from "@vercel/node";
import { lookup } from "node:dns/promises";

export const MAX_BODY_BYTES = 64 * 1024;

export function json(response: VercelResponse, status: number, body: unknown) {
  response.status(status).setHeader("cache-control", "no-store").json(body);
}

export function safeMessage(error: unknown): string {
  if (error instanceof Error && error.name === "AbortError") return "请求超时，请稍后再试。";
  return "上游服务暂时不可用，请稍后再试。";
}

export function requirePost(request: VercelRequest, response: VercelResponse): boolean {
  if (request.method === "POST") return true;
  json(response, 405, { error: "method_not_allowed", message: "请求方式不支持。" });
  return false;
}

export function isBodyAllowed(request: VercelRequest): boolean {
  const length = Number(request.headers["content-length"] ?? 0);
  return !Number.isFinite(length) || length <= MAX_BODY_BYTES;
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

export function safeHttpsUrl(value: string, allowedHosts?: string[]): URL {
  const url = new URL(value);
  if (url.protocol !== "https:" || url.username || url.password || isBlockedHost(url.hostname)) throw new Error("unsafe_url");
  if (allowedHosts && !allowedHosts.includes(url.hostname.toLowerCase())) throw new Error("host_not_allowed");
  return url;
}

export async function assertPublicResolution(url: URL): Promise<void> {
  if (isBlockedHost(url.hostname)) throw new Error("unsafe_url");
  const addresses = await lookup(url.hostname, { all: true, verbatim: true });
  if (!addresses.length || addresses.some((entry) => isBlockedHost(entry.address))) throw new Error("unsafe_resolution");
}

export async function fetchJson(url: URL | string, init: RequestInit = {}, timeoutSeconds = 60): Promise<any> {
  const target = typeof url === "string" ? safeHttpsUrl(url) : url;
  await assertPublicResolution(target);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), Math.min(Math.max(timeoutSeconds, 10), 300) * 1000);
  try {
    const response = await fetch(target, { ...init, signal: controller.signal, redirect: "error" });
    const text = await response.text();
    if (!response.ok) throw new Error(`upstream_${response.status}`);
    return JSON.parse(text);
  } finally { clearTimeout(timeout); }
}
