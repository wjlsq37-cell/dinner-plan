import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";

test("cook endpoint allows empty query for broad search", () => {
  const source = readFileSync(join(process.cwd(), "api/recommend/cook/index.ts"), "utf8");

  assert.match(source, /if \(!body\.query\?\.trim\(\) && !body\.broadSearch\)/);
});

test("restaurant endpoint allows empty query for broad search", () => {
  const source = readFileSync(join(process.cwd(), "api/recommend/restaurant.ts"), "utf8");

  assert.match(source, /if \(!body\.query\?\.trim\(\) && !body\.broadSearch\)/);
});
