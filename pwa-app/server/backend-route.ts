import type { VercelRequest } from "@vercel/node";

type BackendMethod = "GET" | "POST";

const FIXED_OPERATIONS: ReadonlyArray<{ method: BackendMethod; pattern: RegExp }> = [
  { method: "POST", pattern: /^recommend\/cook$/ },
  { method: "POST", pattern: /^recommend\/cook\/cancel$/ },
  { method: "POST", pattern: /^recommend\/restaurant$/ },
  { method: "GET", pattern: /^recipes\/[^/]+$/ },
  { method: "GET", pattern: /^meal-plans\/[^/]+$/ }
];

export function backendPath(request: Pick<VercelRequest, "url" | "method" | "query">): string[] {
  const pathname = new URL(request.url || "/", "https://local.invalid").pathname;
  const fromRequestUrl = pathname.startsWith("/api/backend/")
    ? pathname.slice("/api/backend/".length)
    : "";
  const rawQuery = request.query.path;
  const fromQuery = Array.isArray(rawQuery) ? rawQuery.join("/") : String(rawQuery || "");
  const rawPath = fromRequestUrl && !fromRequestUrl.includes("[...]") ? fromRequestUrl : fromQuery;
  const pieces = rawPath.split("/").filter(Boolean).map((piece) => decodeURIComponent(piece));
  const normalized = pieces.join("/");
  const method = String(request.method || "GET").toUpperCase() as BackendMethod;

  if (!FIXED_OPERATIONS.some((operation) => operation.method === method && operation.pattern.test(normalized))) {
    throw new Error("unsupported_backend_operation");
  }
  return pieces;
}

