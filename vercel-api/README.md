# Dinner Plan Vercel API

This Vercel project hosts the production API used by the Android app when developer mode is off.

## Deploy

1. Set the Vercel Project Root to `vercel-api`.
2. Copy `.env.example` into Vercel Environment Variables.
3. Keep `RECIPE_API_PRIORITY=mxnzp,wanwei` unless you want to change the recipe source order.

The large local recipe corpus is intentionally not deployed. Online recipe search uses mxnzp first and falls back to Wanwei when mxnzp has no usable result.

## Local Checks

```bash
npm install
npm test
```
