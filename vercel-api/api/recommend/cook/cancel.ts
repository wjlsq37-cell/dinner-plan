import { readJsonBody, requireMethod, sendJson, withApi } from "../../../src/http.js";
import type { CancelRecommendationRequest, CancelRecommendationResponse } from "../../../src/types.js";

export default withApi(async (request, response) => {
  if (!requireMethod(request, response, "POST")) return;
  const body = await readJsonBody<CancelRecommendationRequest>(request);
  const requestId = body.requestId?.trim() ?? "";
  const payload: CancelRecommendationResponse = {
    requestId,
    cancelled: false,
    message: "已收到取消请求。"
  };
  sendJson(response, 200, payload);
});
