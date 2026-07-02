# 《吃点啥》Android 原生前端实现说明

## 实现范围

已在 `app/` 下创建 Android 原生界面工程，技术栈为 Kotlin + Jetpack Compose，应用名为“吃点啥”，包名为 `com.dinnerplan.chidian`。

当前已从纯前端 Mock 交互升级为“Android 本地数据层 + Ktor 后端代理”的混合结构。`prototype/` 中的 HTML 原型已保留，未覆盖。

## 已实现页面

- 首页：拆分为“自己做”和“附近吃”两个独立搜索区，互不混用。
- 自己做推荐：支持组合菜单与单道菜推荐切换。
- 组合菜单详情：展示菜品结构、人数、总耗时、推荐理由、采购清单、烹饪顺序，并支持收藏整套菜单。
- 菜谱详情：展示成品图、食材、步骤、技巧，并支持收藏菜谱。
- 附近餐厅：独立餐厅搜索入口，支持手动位置/地标输入，通过后端代理查询高德地图真实 POI。
- 餐厅详情：展示地址、电话、距离、评分、人均、营业状态、推荐理由、导航提示，并支持收藏餐厅。
- 收藏：聚合菜单、菜谱、餐厅收藏，支持全部、做饭、餐厅筛选。
- 设置：展示基础口味偏好、忌口、默认半径、预算、快手菜和营业中偏好，并可编辑后端地址。

## 关键交互

- 首页输入“两荤一素、一汤、主食、微辣”后，点击“生成做饭方案”进入组合菜单推荐。
- 首页“附近吃”搜索独立进入餐厅列表，不跳转到做饭推荐。
- 推荐页可在“组合菜单”和“单道菜”之间切换。
- 收藏按钮即时更新状态，并通过 DataStore 本地保存。
- 页面支持返回栈，详情页可返回上一级。
- 定位失败、AI 推荐失败、后端未启动、无真实餐厅结果以弹窗或空状态提示；餐厅数据不由 AI 编造。
- 底部导航支持：首页、附近、收藏、设置。

## 后端与数据层

新增模块：

- `shared/`：定义 Android 与 Ktor 共用的 DTO 和 API 响应类型。
- `server/`：Ktor + Netty 后端代理，负责 AI 解析、基础菜谱推荐、高德 POI 查询和健康检查。

服务端接口：

- `GET /health`
- `POST /api/recommend/cook`
- `POST /api/recommend/restaurant`
- `GET /api/recipes/{id}`
- `GET /api/meal-plans/{id}`

Android 仍保留 UI 使用的数据模型：

- `MealPlan`
- `DishItem`
- `Recipe`
- `Restaurant`
- `UserPreference`
- `SavedItem`
- `AppUiState`

Android 仓库接口已替换为 suspend 真实仓库：

- `AiRecommendationRepository`
- `RestaurantRepository`

做饭推荐会优先请求后端；AI 未配置或失败时降级为本地规则 + seed 菜谱库。餐厅推荐必须来自高德 POI；高德未配置、无结果或请求失败时返回空结果和原因，不生成假餐厅。

本地持久化：

- 收藏菜单 / 菜谱 / 餐厅 ID
- 餐厅收藏快照
- 浏览历史
- 口味偏好、忌口、默认半径、开关偏好
- 后端地址，默认 `http://10.0.2.2:8080`

## 服务端配置

复制 `.env.example` 为 `.env` 或 `server/.env`，填入：

```properties
AI_BASE_URL=https://api.openai.com/v1
AI_API_KEY=你的 OpenAI 兼容接口密钥
AI_MODEL=gpt-4o-mini
AMAP_WEB_KEY=你的高德 Web API Key
PORT=8080
```

`.env` 和 `server/.env` 已加入 `.gitignore`，不要提交真实密钥。

## 视觉方向

Compose 页面按现有 HTML 原型复刻为温暖美食风：

- 暖白背景与番茄红主色
- 圆角但不过度装饰的卡片
- 食物头图与餐厅图
- 底部导航、标签、筛选、详情卡片、弹窗等移动端常见控件
- 小屏下以纵向滚动为主，避免文字横向溢出

## 运行前置条件

当前命令环境未检测到全局 `java` 与 `gradle`，因此本次未在终端完成编译或 APK 生成。

建议运行方式：

1. 使用 Android Studio 打开项目根目录 `E:\project\dinner_plan`。
2. 确认安装 JDK 17，或使用 Android Studio 自带的 JBR 17。
3. 确认 Android SDK 包含 Android API 34 平台及对应 Build Tools。
4. 等待 Gradle 同步完成。
5. 先运行 `server` 模块，或在终端执行 `gradlew :server:run`。
6. 再运行 `app` 模块；Android 模拟器会通过 `http://10.0.2.2:8080` 访问电脑上的后端。

项目已配置 Gradle Wrapper 8.7，首次同步时会按 `gradle/wrapper/gradle-wrapper.properties` 下载对应 Gradle 版本。
