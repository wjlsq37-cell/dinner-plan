# 吃点啥 PWA

独立于 Android 项目的 React + TypeScript PWA。手机端保持原 App 的页面、功能与操作逻辑，宽屏进行自适应排版。

## 本地运行

```bash
npm install
npm run dev
```

`npm run dev` 用于界面开发，并把默认后端请求代理到 `https://dinner-plan.vercel.app`。如需同时测试 `/api/status`、同源代理和开发者直连模式，请运行：

```bash
npm run dev:full
```

首次使用完整模式前需要运行一次 `npx vercel login`，并把当前目录链接到独立的 `dinner-plan-pwa` 项目。

手机通过电脑局域网 HTTP 地址只能预览界面。浏览器精确定位需要 localhost 或部署后的 HTTPS 地址。

## 验证

```bash
npm test
npm run build
npm run test:e2e
```

## 独立部署到 Vercel

1. 新建 Vercel 项目，将 Root Directory 设为 `pwa-app`。
   - 推荐项目名：`dinner-plan-pwa`
   - 不要把 `pwa-app` 链接到现有后端项目 `dinner-plan`
2. Framework Preset 选择 Vite，Build Command 使用 `npm run build`，Output Directory 使用 `dist`。
3. 设置 `UPSTREAM_API_BASE_URL=https://dinner-plan.vercel.app`。
4. 如果现有后端启用了 App Token，再设置仅服务端可见的 `UPSTREAM_APP_TOKEN`。

如果本地 `.vercel/project.json` 仍显示 `dinner-plan`，先在仓库根目录重新执行 `vercel link --repo`，把 `pwa-app` 目录关联到 `dinner-plan-pwa`。部署前先确认现有后端的 `/api/health` 正常。

开发者模式的 AI、高德和菜谱密钥只保存在当前浏览器 IndexedDB。代理不记录请求内容，并拒绝 HTTP、本机、内网、链路本地及云元数据地址。

## 离线边界

- 可离线启动应用，浏览和修改本地收藏、历史、设置与已缓存详情。
- 推荐、定位后的餐厅查询和详情补全需要网络。
- API 响应、密钥和用户设置不会进入 Service Worker 缓存。

## 手机验收

1. 使用 HTTPS 生产或预览地址打开网页。
2. Android Chrome 选择“添加到主屏幕”，iOS Safari 选择“共享 → 添加到主屏幕”。
3. 在独立窗口中测试定位、做饭推荐、附近搜索、收藏和刷新恢复。
4. 发布新版本后保持旧页面打开；检测到新 Service Worker 时会提示“发现新版本”，只有点击“更新”才刷新，新版不会清除 IndexedDB 数据。
