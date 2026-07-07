import { buildHealthResponse } from "../src/config.js";
import { requireMethod, sendJson, withApi } from "../src/http.js";

export default withApi(async (request, response, config) => {
  if (!requireMethod(request, response, "GET")) return;
  sendJson(response, 200, buildHealthResponse(config));
});
