import type { IncomingMessage, ServerResponse } from "node:http";
import { loadConfig, type VercelApiConfig } from "./config.js";

export type ApiHandler = (request: IncomingMessage, response: ServerResponse, config: VercelApiConfig) => Promise<void>;

export function withApi(handler: ApiHandler): (request: IncomingMessage, response: ServerResponse) => Promise<void> {
  return async (request, response) => {
    const config = loadConfig();
    if (!isAuthorized(request, config)) {
      sendJson(response, 401, { error: "unauthorized", message: "请求未通过验证。" });
      return;
    }
    try {
      await handler(request, response, config);
    } catch (error) {
      console.error("Vercel API request failed", error);
      sendJson(response, 500, { error: "backend_error", message: "服务暂时不可用，请稍后再试。" });
    }
  };
}

export async function readJsonBody<T>(request: IncomingMessage): Promise<T> {
  const chunks: Buffer[] = [];
  for await (const chunk of request) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }
  const text = Buffer.concat(chunks).toString("utf8").trim();
  return (text ? JSON.parse(text) : {}) as T;
}

export function requireMethod(request: IncomingMessage, response: ServerResponse, method: string): boolean {
  if (request.method === method) return true;
  sendJson(response, 405, { error: "method_not_allowed", message: "请求方式不支持。" });
  return false;
}

export function sendJson(response: ServerResponse, status: number, body: unknown): void {
  response.statusCode = status;
  response.setHeader("content-type", "application/json; charset=utf-8");
  response.end(JSON.stringify(body));
}

export function routeParam(request: IncomingMessage, prefix: string): string {
  const pathname = new URL(request.url ?? "/", "https://local.invalid").pathname;
  return decodeURIComponent(pathname.replace(prefix, "").replace(/^\/+/, ""));
}

function isAuthorized(request: IncomingMessage, config: VercelApiConfig): boolean {
  if (!config.appApiToken) return true;
  return request.headers["x-app-token"] === config.appApiToken;
}
