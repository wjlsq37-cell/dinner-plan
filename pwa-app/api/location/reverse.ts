import type { VercelRequest, VercelResponse } from "@vercel/node";
import type { ReverseGeocodeRequest } from "../../src/types.js";
import { fetchJson, isBodyAllowed, json, requirePost } from "../../server/shared.js";

function validCoordinate(latitude: number, longitude: number): boolean {
  return Number.isFinite(latitude) && Number.isFinite(longitude) && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
}

function text(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function addressFromPayload(payload: Record<string, any>): string {
  const regeocode = payload.regeocode && typeof payload.regeocode === "object" ? payload.regeocode : {};
  const formatted = text(regeocode.formatted_address);
  if (formatted) return formatted;
  const component = regeocode.addressComponent && typeof regeocode.addressComponent === "object" ? regeocode.addressComponent : {};
  const street = component.streetNumber && typeof component.streetNumber === "object" ? component.streetNumber : {};
  return [component.province, component.city, component.district, component.township, street.street, street.number].map(text).filter(Boolean).join("");
}

export default async function handler(request: VercelRequest, response: VercelResponse) {
  if (!requirePost(request, response)) return;
  if (!isBodyAllowed(request)) return json(response, 413, { error: "body_too_large", message: "请求内容过大。" });

  const body = (request.body || {}) as Partial<ReverseGeocodeRequest>;
  if (typeof body.latitude !== "number" || typeof body.longitude !== "number" || !validCoordinate(body.latitude, body.longitude)) return json(response, 400, { error: "invalid_location", message: "定位坐标无效。" });
  const { latitude, longitude } = body;

  const key = process.env.AMAP_WEB_KEY?.trim();
  if (!key) return json(response, 503, { error: "location_service_unconfigured", message: "地址解析服务尚未配置。" });

  try {
    const endpoint = new URL("https://restapi.amap.com/v3/geocode/regeo");
    endpoint.searchParams.set("key", key);
    endpoint.searchParams.set("location", `${longitude},${latitude}`);
    endpoint.searchParams.set("extensions", "base");
    endpoint.searchParams.set("radius", "100");
    const payload = await fetchJson(endpoint, {}, 12);
    const address = payload?.status === "1" ? addressFromPayload(payload) : "";
    if (!address) return json(response, 502, { error: "address_unavailable", message: "暂时无法解析当前位置名称。" });
    json(response, 200, { location: { latitude, longitude, text: address } });
  } catch {
    json(response, 502, { error: "address_unavailable", message: "暂时无法解析当前位置名称。" });
  }
}
