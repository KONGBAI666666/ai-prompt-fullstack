# AI 提示词管理平台（全栈版）

前后端分离的 AI 提示词管理平台。后端提供 22 个 RESTful 接口，前端使用 Vue 3 单页应用调用。

## 项目架构

```
┌─────────────────────── 浏览器 ───────────────────────┐
│                                                      │
│   Vue 3 页面（LoginView / HomeView / MainLayout）     │
│                        │                             │
│                 api 模块（user.js 等）                 │
│                        │                             │
│           Axios 封装（request.js 拦截器）              │
│         请求自动携带 Authorization: token              │
└────────────────────────┼─────────────────────────────┘
                         │  HTTP + JSON
                         │  （开发期经 Vite 代理 /api → 8080）
┌────────────────────────┼─────────────────────────────┐
│                 Spring Boot 后端                      │
│                        │                             │
│      Sa-Token 拦截器（校验 token / 权限 RBAC）          │
│                        │                             │
│      Controller（22 个 REST 接口，统一返回 Result）      │
│                        │                             │
│            Service（业务逻辑 / 事务）                   │
│                        │                             │
│         MyBatis-Plus（Mapper / 分页插件）              │
└────────────────────────┼─────────────────────────────┘
                         │
                    MySQL 8.0
        （user / prompt / category / favorite / history）
```

一次典型请求（以登录为例）：

```
点击登录 → LoginView.vue → user.js → request.js → POST /api/user/login
        → UserController → UserService（BCrypt 校验密码）→ Sa-Token 生成 token
        → 返回 {token, user} → 前端存入 localStorage → 后续请求自动带 token
```

## 已实现功能

| 模块 | 状态 |
| --- | --- |
| 用户注册 / 登录 / 登出（Sa-Token 认证） | ✅ |
| RBAC 权限控制（USER / ADMIN） | ✅ |
| Prompt 管理 22 个 REST 接口 + Swagger 文档 | ✅ |
| 前端登录页（token 保存 + 路由守卫） | ✅ |
| Prompt 列表（搜索 / 分类筛选 / 分页 / 收藏 / 一键复制） | ✅ |
| Prompt 新增 / 详情 / 编辑 / 删除（CRUD 闭环，本人或管理员可删） | ✅ |
| 个人中心（我的信息 / 我的 Prompt / 收藏 / 使用记录） | ✅ |
| 管理员后台（前端页面，接口已就绪） | 🚧 规划中 |

## 项目结构

```
ai-prompt-fullstack
├── backend/     Spring Boot 3.4.1 后端（Java 21 + MyBatis-Plus + Sa-Token + MySQL 8.0）
└── frontend/    Vue 3 前端（Vite + Vue Router + Pinia + Axios）
```

## 启动方式

### 后端（先启动）

1. MySQL 执行 `backend/src/main/resources/sql/init.sql` 初始化数据库
2. 修改 `backend/src/main/resources/application-dev.yml` 中的数据库密码
3. 启动 `AiPromptApplication`，后端运行在 `http://localhost:8080/api`
4. 接口文档：`http://localhost:8080/api/swagger-ui.html`

### 前端

```bash
cd frontend
npm install
npm run dev
```

前端运行在 `http://localhost:5173`，开发期通过 Vite 代理把 `/api` 请求转发到后端 8080 端口。

## 测试账号

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 管理员 | admin | admin123 |
| 普通用户 | test | 123456 |

## 说明

- 后端接口详情见 `backend/README.md`
- Sa-Token 的 token-name 为 `Authorization`，前端登录后需在请求头携带该字段
