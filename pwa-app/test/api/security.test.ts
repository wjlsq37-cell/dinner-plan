import { describe, expect, it } from "vitest";
import { safeHttpsUrl } from "../../server/shared";

describe("direct proxy URL validation", () => {
  it("accepts public https URLs", () => { expect(safeHttpsUrl("https://api.deepseek.com/v1").hostname).toBe("api.deepseek.com"); });
  it.each(["http://example.com", "https://localhost/api", "https://printer.local/api", "https://service.internal/api", "https://127.0.0.1/api", "https://10.0.0.1/api", "https://192.168.1.5/api", "https://169.254.169.254/latest", "https://user:secret@example.com"])("blocks unsafe target %s", (value) => { expect(() => safeHttpsUrl(value)).toThrow(); });
  it("enforces provider allowlists", () => { expect(() => safeHttpsUrl("https://example.com", ["www.mxnzp.com"])).toThrow(); });
});

