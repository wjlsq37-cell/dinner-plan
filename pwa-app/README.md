# 吃点啥 PWA

独立于 Android 项目的 React + TypeScript PWA。手机端保持原 App 的页面、功能与操作逻辑，宽屏进行自适应排版。

## 本地运行

```bash
npm install
npm run dev
```

Vite 开发服务器会把默认后端请求代理到 `https://dinner-plan.vercel.app`。如需在本地测试 Vercel Serverless API 与开发者直连模式，请使用 Vercel CLI 启动项目。

## 验证

```bash
npm test
npm run build
npm run test:e2e
```

## 独立部署到 Vercel

1. 新建 Vercel 项目，将 Root Directory 设为 `pwa-app`。
2. Framework Preset 选择 Vite，Build Command 使用 `npm run build`，Output Directory 使用 `dist`。
3. 设置 `UPSTREAM_API_BASE_URL=https://dinner-plan.vercel.app`。
4. 如果现有后端启用了 App Token，再设置仅服务端可见的 `UPSTREAM_APP_TOKEN`。

开发者模式的 AI、高德和菜谱密钥只保存在当前浏览器 IndexedDB。代理不记录请求内容，并拒绝 HTTP、本机、内网、链路本地及云元数据地址。

## 离线边界

- 可离线启动应用，浏览和修改本地收藏、历史、设置与已缓存详情。
- 推荐、定位后的餐厅查询和详情补全需要网络。
- API 响应、密钥和用户设置不会进入 Service Worker 缓存。
