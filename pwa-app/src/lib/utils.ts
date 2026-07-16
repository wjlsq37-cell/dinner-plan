import type { Restaurant, RestaurantSort, SavedRef } from "../types";
import { ApiError } from "./api";

export function parseDistanceMeters(value: string): number | null {
  const number = Number(value.match(/\d+(?:\.\d+)?/)?.[0]);
  if (!Number.isFinite(number)) return null;
  return /km|公里/i.test(value) ? number * 1000 : number;
}

export function parseRating(value: string): number | null {
  const number = Number(value.match(/\d+(?:\.\d+)?/)?.[0]);
  return Number.isFinite(number) ? number : null;
}

export function sortRestaurants(items: Restaurant[], mode: RestaurantSort): Restaurant[] {
  if (mode === "distance") return [...items].sort((a, b) => (parseDistanceMeters(a.distance) ?? Infinity) - (parseDistanceMeters(b.distance) ?? Infinity));
  if (mode === "rating") return [...items].sort((a, b) => (parseRating(b.rating) ?? -1) - (parseRating(a.rating) ?? -1));
  return items;
}

export function toggleSaved(items: SavedRef[], next: SavedRef): SavedRef[] {
  const exists = items.some((item) => item.kind === next.kind && item.id === next.id);
  return exists ? items.filter((item) => item.kind !== next.kind || item.id !== next.id) : [next, ...items];
}

export function addHistory(items: SavedRef[], next: SavedRef): SavedRef[] {
  return [next, ...items.filter((item) => item.kind !== next.kind || item.id !== next.id)].slice(0, 10);
}

export function toggleString(items: string[], value: string): string[] {
  return items.includes(value) ? items.filter((item) => item !== value) : [...items, value];
}

export function formatElapsed(seconds: number): string {
  return `${String(Math.floor(Math.max(0, seconds) / 60)).padStart(2, "0")}:${String(Math.max(0, seconds) % 60).padStart(2, "0")}`;
}

export function appendQuery(source: string, value: string): string {
  if (!source.trim()) return value;
  return source.includes(value) ? source : `${source.trim()}，${value}`;
}

export function friendlyError(error: unknown): string {
  if (!navigator.onLine) return "当前处于离线状态，联网后再试一次。";
  if (error instanceof ApiError) {
    const messages = {
      offline: "当前处于离线状态，联网后再试一次。",
      proxy_unreachable: "暂时无法连接服务，请检查网络或代理部署。",
      auth: "服务鉴权失败，请检查代理访问令牌。",
      config: "服务配置不完整，请检查开发者设置。",
      timeout: "请求等待超时，请稍后重试。",
      upstream: "上游服务暂时不可用，请稍后再试。",
      invalid_response: "服务返回了无法识别的结果，请稍后再试。",
      cancelled: "请求已取消，已保留上一次成功结果。"
    } as const;
    return messages[error.kind];
  }
  if (error instanceof DOMException && error.name === "AbortError") return "请求已取消，已保留上一次成功结果。";
  const message = error instanceof Error ? error.message : "服务暂时不可用，请稍后再试。";
  return message.length > 160 ? "服务暂时不可用，请稍后再试。" : message;
}
