import assert from "node:assert/strict";
import test from "node:test";
import { buildHealthResponse, type VercelApiConfig } from "../src/config.js";

test("health reports configuration without leaking secrets", () => {
  const config: VercelApiConfig = {
    aiBaseUrl: "https://api.deepseek.com",
    aiApiKey: "secret-ai-key",
    aiModel: "deepseek-v4-flash",
    amapWebKey: "secret-amap-key",
    appApiToken: "secret-app-token",
    recipe: {
      priority: ["mxnzp", "wanwei"],
      mxnzpSearchUrl: "https://www.mxnzp.com/api/cookbook/search",
      mxnzpDetailUrl: "https://www.mxnzp.com/api/cookbook/details",
      mxnzpAppId: "secret-mx-id",
      mxnzpAppSecret: "secret-mx-secret",
      wanweiBaseUrl: "https://route.showapi.com",
      wanweiAppKey: "secret-wanwei-key"
    }
  };

  const health = buildHealthResponse(config);
  const serialized = JSON.stringify(health);

  assert.equal(health.ok, true);
  assert.equal(health.aiConfigured, true);
  assert.equal(health.amapConfigured, true);
  assert.equal(health.recipeSources.mxnzpConfigured, true);
  assert.equal(health.recipeSources.wanweiConfigured, true);
  assert.equal(serialized.includes("secret"), false);
});
