import { requireMethod, sendJson, withApi } from "../../src/http.js";

export default withApi(async (request, response) => {
  if (!requireMethod(request, response, "GET")) return;
  sendJson(response, 404, { error: "meal_plan_not_found", message: "暂时没有找到这套餐单的详情。" });
});
