import { readJsonBody, requireMethod, sendJson, withApi } from "../../src/http.js";
import { recommendRestaurants } from "../../src/recommendation.js";
import type { RecommendationRequest } from "../../src/types.js";

export default withApi(async (request, response, config) => {
  if (!requireMethod(request, response, "POST")) return;
  const body = await readJsonBody<RecommendationRequest>(request);
  if (!body.query?.trim()) {
    sendJson(response, 400, { error: "query_required", message: "请输入想找的餐厅。" });
    return;
  }
  sendJson(response, 200, await recommendRestaurants({ ...body, mode: "RESTAURANT" }, config));
});
