import type { Restaurant, RestaurantSort, SavedRef } from "../types";

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

export function formatElapsed(seconds: number): string {
  return `${String(Math.floor(Math.max(0, seconds) / 60)).padStart(2, "0")}:${String(Math.max(0, seconds) % 60).padStart(2, "0")}`;
}

export function appendQuery(source: string, value: string): string {
  if (!source.trim()) return value;
  return source.includes(value) ? source : `${source.trim()}，${value}`;
}

export function friendlyError(error: unknown): string {
  if (!navigator.onLine) return "当前处于离线状态，联网后再试一次。";
  if (error instanceof DOMException && error.name === "AbortError") return "请求已取消，已保留上一次成功结果。";
  const message = error instanceof Error ? error.message : "服务暂时不可用，请稍后再试。";
  return message.length > 160 ? "服务暂时不可用，请稍后再试。" : message;
}
