import { requireMethod, routeParam, sendJson, withApi } from "../../src/http.js";
import { recipeById } from "../../src/recommendation.js";

export default withApi(async (request, response, config) => {
  if (!requireMethod(request, response, "GET")) return;
  const id = routeParam(request, "/api/recipes/");
  const recipe = await recipeById(id, config);
  if (!recipe) {
    sendJson(response, 404, { error: "recipe_not_found", message: "暂时没有找到这道菜谱的详情。" });
    return;
  }
  sendJson(response, 200, recipe);
});
