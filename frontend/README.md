# AI Prompt 管理系统 - 前端

AI 提示词管理平台的前端：Prompt 浏览/搜索/发布、收藏、使用记录、个人中心与管理员后台，支持明暗双主题。

> Vue 3 单页应用 · 与 `../backend` 的 Spring Boot 后端配套使用

## 一、技术栈

| 类别 | 技术 | 版本 |
|---|---|---|
| 框架 | Vue 3（Composition API + `<script setup>`） | 3.5 |
| 构建 | Vite | 8.x |
| 路由 | Vue Router | 5.x |
| UI 组件库 | Element Plus（含暗黑模式、图标库） | 2.14 |
| HTTP | Axios（统一拦截器） | 1.x |

## 二、项目结构

```
src/
├── api/            # 按模块封装的接口：user / prompt / category / favorite / history / admin
│   └── request.js  # axios 实例：自动携带 token、拆 Result、统一错误提示、401 踢回登录
├── views/          # 页面：Home / Login / PromptCreate(新增编辑复用) / PromptDetail / Profile / Admin
├── layout/         # MainLayout：登录后的顶部导航 + 内容区
├── composables/    # useTheme（明暗主题）、usePromptActions（收藏/复制公共逻辑）
├── utils/          # auth（token/用户信息存取）、format（时间格式化）
├── router/         # 路由 + 全局守卫（未登录拦截、admin 角色检查）
└── assets/         # 全局样式（main.css / theme.css 主题变量）
```

## 三、启动步骤

前置条件：后端已启动（默认 `http://localhost:8080`，见 `../backend/README.md`）。

```bash
npm install
npm run dev
```

开发服务器地址：**http://localhost:5173**（Vite 已配置代理，页面里的 `/api` 请求自动转发到后端 8080，无需处理跨域）。

登录账号见后端 README（初始数据由 `../backend/src/main/resources/sql/init.sql` 写入）。

## 四、构建

```bash
npm run build
```

产物输出到 `dist/`，可直接部署到任意静态服务器（部署时需将 `/api` 反向代理到后端）。
