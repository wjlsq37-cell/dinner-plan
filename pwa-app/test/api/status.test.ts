import type { VercelRequest, VercelResponse } from "@vercel/node";
import { afterEach, describe, expect, it, vi } from "vitest";
import handler from "../../api/status";

function responseMock() {
  const response = { status: vi.fn(), setHeader: vi.fn(), json: vi.fn() } as unknown as VercelResponse;
  vi.mocked(response.status).mockReturnValue(response);
  vi.mocked(response.setHeader).mockReturnValue(response);
  vi.mocked(response.json).mockReturnValue(response);
  return response;
}

afterEach(() => {
  delete process.env.UPSTREAM_API_BASE_URL;
});

describe("status function", () => {
  it("returns the stable status DTO for unsupported methods", async () => {
    const response = responseMock();
    await handler({ method: "POST" } as VercelRequest, response);
    expect(response.status).toHaveBeenCalledWith(405);
    expect(response.json).toHaveBeenCalledWith({ proxyReachable: false, backendConfigured: false, message: "请求方式不支持。" });
  });

  it("rejects an unsafe upstream without leaking its value", async () => {
    process.env.UPSTREAM_API_BASE_URL = "https://127.0.0.1/private";
    const response = responseMock();
    await handler({ method: "GET" } as VercelRequest, response);
    expect(response.status).toHaveBeenCalledWith(200);
    expect(response.json).toHaveBeenCalledWith({ proxyReachable: false, backendConfigured: false, message: "线上服务配置无效。" });
    expect(JSON.stringify(vi.mocked(response.json).mock.calls)).not.toContain("127.0.0.1");
  });
});
