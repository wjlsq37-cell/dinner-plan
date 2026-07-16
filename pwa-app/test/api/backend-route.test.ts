import { describe, expect, it } from "vitest";
import { backendPath } from "../../server/backend-route";

describe("backend proxy route", () => {
  it("reads a Vercel splat string", () => {
    expect(backendPath({ url: "/api/backend/[...]", method: "POST", query: { path: "recommend/cook" } })).toEqual(["recommend", "cook"]);
  });

  it("accepts array-style catch-all parameters", () => {
    expect(backendPath({ url: "/api/backend/[...]", method: "POST", query: { path: ["recommend", "restaurant"] } })).toEqual(["recommend", "restaurant"]);
  });

  it("can recover the original request path", () => {
    expect(backendPath({ url: "/api/backend/recipes/mxnzp_123", method: "GET", query: {} })).toEqual(["recipes", "mxnzp_123"]);
  });

  it("rejects arbitrary upstream paths and wrong methods", () => {
    expect(() => backendPath({ url: "/api/backend/[...]", method: "GET", query: { path: "admin/users" } })).toThrow("unsupported_backend_operation");
    expect(() => backendPath({ url: "/api/backend/[...]", method: "GET", query: { path: "recommend/cook" } })).toThrow("unsupported_backend_operation");
  });
});

