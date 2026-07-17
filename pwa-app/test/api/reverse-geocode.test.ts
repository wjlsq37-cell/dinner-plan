import type { VercelRequest, VercelResponse } from "@vercel/node";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const fetchJsonMock = vi.hoisted(() => vi.fn());
vi.mock("../../server/shared.js", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../server/shared")>()),
  fetchJson: fetchJsonMock
}));

import handler from "../../api/location/reverse";

function responseMock() {
  const response = { status: vi.fn(), setHeader: vi.fn(), json: vi.fn() } as unknown as VercelResponse;
  vi.mocked(response.status).mockReturnValue(response);
  vi.mocked(response.setHeader).mockReturnValue(response);
  vi.mocked(response.json).mockReturnValue(response);
  return response;
}

function request(body: unknown) {
  return { method: "POST", headers: {}, body } as unknown as VercelRequest;
}

beforeEach(() => {
  fetchJsonMock.mockReset();
  process.env.AMAP_WEB_KEY = "server-only-test-key";
});

afterEach(() => { delete process.env.AMAP_WEB_KEY; });

describe("reverse geocode function", () => {
  it("returns a formatted address without exposing the key", async () => {
    fetchJsonMock.mockResolvedValue({ status: "1", regeocode: { formatted_address: "浙江省杭州市上城区湖滨街道" } });
    const response = responseMock();
    await handler(request({ latitude: 30.25, longitude: 120.16 }), response);
    expect(response.status).toHaveBeenCalledWith(200);
    expect(response.json).toHaveBeenCalledWith({ location: { latitude: 30.25, longitude: 120.16, text: "浙江省杭州市上城区湖滨街道" } });
    expect(JSON.stringify(vi.mocked(response.json).mock.calls)).not.toContain("server-only-test-key");
  });

  it("rejects invalid coordinates before calling Amap", async () => {
    const response = responseMock();
    await handler(request({ latitude: 120, longitude: 300 }), response);
    expect(response.status).toHaveBeenCalledWith(400);
    expect(fetchJsonMock).not.toHaveBeenCalled();
  });

  it("reports a missing server key safely", async () => {
    delete process.env.AMAP_WEB_KEY;
    const response = responseMock();
    await handler(request({ latitude: 30.25, longitude: 120.16 }), response);
    expect(response.status).toHaveBeenCalledWith(503);
    expect(response.json).toHaveBeenCalledWith(expect.objectContaining({ error: "location_service_unconfigured" }));
  });

  it("does not leak upstream failures", async () => {
    fetchJsonMock.mockRejectedValue(new Error("secret upstream failure"));
    const response = responseMock();
    await handler(request({ latitude: 30.25, longitude: 120.16 }), response);
    expect(response.status).toHaveBeenCalledWith(502);
    expect(JSON.stringify(vi.mocked(response.json).mock.calls)).not.toContain("secret upstream failure");
  });
});
