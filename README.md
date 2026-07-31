# AI 提示词管理平台（全栈版）

前后端分离的 AI 提示词管理平台。后端提供 22 个 RESTful 接口，前端使用 Vue 3 单页应用调用。

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
