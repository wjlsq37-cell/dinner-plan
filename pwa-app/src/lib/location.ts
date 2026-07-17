import type { LocationErrorKind, LocationValue } from "../types";

export class LocationRequestError extends Error {
  constructor(public kind: LocationErrorKind, message: string) {
    super(message);
    this.name = "LocationRequestError";
  }
}

export const locationErrorMessage = (kind: LocationErrorKind) => ({
  insecure_context: "当前页面不是安全连接，手机定位需要使用 HTTPS 地址。",
  unsupported: "当前浏览器不支持定位，请手动输入城市、商圈或地标。",
  permission_denied: "定位权限未开启，你仍然可以手动输入地点搜索。",
  unavailable: "系统暂时无法获取位置，请确认手机定位服务已开启。",
  timeout: "获取位置超时，请重试或手动输入地点。",
  unknown: "暂时无法获取位置，请手动输入地点。"
} satisfies Record<LocationErrorKind, string>)[kind];

export async function requestBrowserLocation(): Promise<LocationValue> {
  if (!window.isSecureContext) throw new LocationRequestError("insecure_context", locationErrorMessage("insecure_context"));
  if (!navigator.geolocation) throw new LocationRequestError("unsupported", locationErrorMessage("unsupported"));
  if (navigator.permissions?.query) {
    try {
      const permission = await navigator.permissions.query({ name: "geolocation" });
      if (permission.state === "denied") throw new LocationRequestError("permission_denied", locationErrorMessage("permission_denied"));
    } catch (error) {
      if (error instanceof LocationRequestError) throw error;
    }
  }
  return new Promise((resolve, reject) => navigator.geolocation.getCurrentPosition(
    (position) => resolve({ latitude: position.coords.latitude, longitude: position.coords.longitude, text: "" }),
    (error) => {
      const kind: LocationErrorKind = error.code === 1 ? "permission_denied" : error.code === 2 ? "unavailable" : error.code === 3 ? "timeout" : "unknown";
      reject(new LocationRequestError(kind, locationErrorMessage(kind)));
    },
    { enableHighAccuracy: true, timeout: 12_000, maximumAge: 300_000 }
  ));
}
