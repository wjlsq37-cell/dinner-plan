import { readJsonBody, requireMethod, sendJson, withApi } from "../../../src/http.js";
import { recommendCook } from "../../../src/recommendation.js";
import type { RecommendationRequest } from "../../../src/types.js";

export default withApi(async (request, response, config) => {
  if (!requireMethod(request, response, "POST")) return;
  const body = await readJsonBody<RecommendationRequest>(request);
  if (!body.query?.trim()) {
    sendJson(response, 400, { error: "query_required", message: "请输入想吃什么。" });
    return;
  }
  sendJson(response, 200, await recommendCook(body, config));
});
