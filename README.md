# 吃点啥（Dinner Plan）

一款帮助用户决定“今天吃什么”的智能餐饮助手，提供 Android 原生应用和可安装的 PWA。它可以根据口味、忌口和位置生成菜谱、组合晚餐及附近餐厅推荐，并在手机本地保存收藏、历史和偏好设置。

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://github.com/wjlsq37-cell/dinner-plan/tree/main/app)
[![PWA](https://img.shields.io/badge/PWA-online-5A0FC8?logo=pwa&logoColor=white)](https://dinner-plan-pwa.vercel.app)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![React](https://img.shields.io/badge/React-TypeScript-3178C6?logo=react&logoColor=white)](https://react.dev/)

## 在线体验

- PWA：[https://dinner-plan-pwa.vercel.app](https://dinner-plan-pwa.vercel.app)
- Android：从 `main` 分支使用 Android Studio 运行或构建 APK。

## 核心功能

- **今日决策**：一键生成菜谱与附近餐厅灵感，减少选择成本。
- **AI 推荐**：支持单道菜和组合晚餐，根据口味、忌口及搜索条件返回结果。
- **附近餐厅**：读取设备位置或手动位置，按距离和营业状态筛选餐厅。
- **详情与导航**：查看菜谱、组合菜单和餐厅详情，并可调用地图导航。
- **收藏与历史**：在本机保存菜谱、菜单、餐厅和浏览记录。
- **偏好设置**：管理常用口味、忌口、搜索半径和营业状态筛选。
- **双端体验**：Android 原生应用与 PWA 共用相同的产品逻辑和接口语义。

## Android 应用

Android 版本采用 Kotlin 和 Jetpack Compose 构建，面向 Android 8.0（API 26）及以上设备。

主要特性：

- Material 3 原生界面和响应式手机布局。
- “默认主题 / 少女粉”主题切换及本地持久化。
- “经典图标 / 元气厨师”启动器图标切换。
- 冷启动品牌画面、主题化按钮图标和状态栏适配。
- DataStore 保存主题、收藏、历史、口味、位置及开发者配置。
- 支持线上代理模式和可选的开发者直连模式。

技术栈：

- Kotlin
- Jetpack Compose / Material 3
- Ktor Client
- Kotlinx Serialization
- Coil
- Android DataStore

### 运行 Android

要求：

- Android Studio
- JDK 17
- Android SDK 34

```powershell
git checkout main
.\gradlew.bat :app:assembleDebug
```

调试 APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

运行 Android 单元测试：

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

## PWA

PWA 版本使用 React、TypeScript 和 Vite 构建，可通过浏览器访问，也可添加到手机主屏幕。

主要特性：

- 面向手机的四栏导航和触控布局。
- AI 菜谱、组合晚餐及附近餐厅推荐。
- 浏览器定位、位置恢复和搜索状态保留。
- 收藏、历史、偏好及页面状态本地保存。
- Service Worker 更新机制和可安装体验。
- 支持 Vercel 同源代理及浏览器开发者直连模式。

> PWA 可以缓存应用外壳和静态资源；AI 推荐、定位解析和餐厅数据仍需要网络连接。

技术栈：

- React
- TypeScript
- Vite
- Vitest
- Playwright
- Vercel Functions

### 运行 PWA

要求：

- Node.js 22
- npm

```bash
git checkout main
cd pwa-app
npm install
npm run dev
```

生产构建与检查：

```bash
npm run check
```

端到端测试：

```bash
npm run test:e2e
```

### PWA 环境变量

复制 `pwa-app/.env.example` 并根据部署环境填写：

| 变量 | 用途 |
|---|---|
| `UPSTREAM_API_BASE_URL` | 上游后端地址 |
| `UPSTREAM_APP_TOKEN` | 上游应用令牌，可选 |
| `AMAP_WEB_KEY` | 高德 Web 服务 Key，可选 |

不要把真实 API Key、Token 或其他密钥提交到 Git。

## 仓库结构

```text
dinner-plan/
├─ app/          Android 原生应用
├─ shared/       Android 与服务端共享的 Kotlin DTO
├─ server/       Kotlin/Ktor 后端
├─ vercel-api/   Vercel API 入口
├─ pwa-app/      React PWA
└─ prototype/    早期产品原型
```

## 分支说明

| 分支 | 用途 |
|---|---|
| `main` | Android、PWA、共享代码与稳定整合版本 |
| `codex/restaurant-cook-split` | Android 应用开发与验收 |
| `codex/pwa-app` | PWA 开发、测试与 Vercel 部署 |

Android 与 PWA 可以在独立分支开发，完成验收后合并到 `main`。`main` 始终保留可同时查看和构建两端代码的完整版本。

## 数据与隐私

- 收藏、历史、主题和偏好默认保存在用户设备或浏览器本地。
- 位置权限仅用于附近餐厅搜索。
- AI、地图和第三方菜谱服务可能接收完成推荐所必需的查询信息。
- 开发者模式中的密钥应仅保存在受控设备和部署环境中。

## 版本

- Android：`0.1.0`
- PWA：`0.1.0`
