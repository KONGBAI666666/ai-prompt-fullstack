# AGENTS.md — AI 编程助手项目交接文档

> 本文档写给接手本项目的 AI 编程助手（及新人类开发者）。读完这一份即可开始工作，无需其他背景。

## 1. 项目是什么

**AI 提示词管理平台**：前后端分离的提示词（Prompt）收藏/分享社区 + 管理后台，带 AI 在线试运行功能。个人全栈项目，用于课程设计与求职作品集（Java 后端方向）。

- **后端**：Spring Boot 3.4.1 · Java 21 · MyBatis-Plus 3.5.17 · Sa-Token 1.45.0 · MySQL 8.0 · Redis 3.2+ · SpringDoc(Swagger)
- **前端**：Vue 3.5 · Vite · Vue Router · Pinia（暂未深度使用） · Axios · Element Plus
- **当前版本**：`v1.4-redis-ai`（29 个 REST 接口，其中 1 个 SSE 流式接口；9 张表；17 个权限点）

## 2. 目录结构

```
后端+前端/                  ← 源码仓库根（有 GitHub remote，见 §6）
├── AGENTS.md              ← 本文档
├── README.md              ← 项目说明书（架构图 / 功能表 / 启动方式 / 截图）
├── backend/               ← Spring Boot 后端
│   └── src/main/java/com/spring/aiprompt/
│       ├── controller/    ← 8 个 Controller（user/prompt/category/favorite/history/rbac/admin/ai）
│       ├── service/       ← 业务层。CountService（浏览计数）、AiService（大模型）、CaptchaService（验证码）是亮点文件
│       ├── service/impl/  ← 实现层。PromptServiceImpl / CategoryServiceImpl 含缓存与计数改造
│       ├── config/        ← SaTokenConfig（两层防线）、RedisConfig、AiProperties、CorsConfig、SwaggerConfig
│       ├── entity|dto|vo|mapper/
│       └── exception/     ← GlobalExceptionHandler（权限不足统一转 403）
│   └── src/main/resources/
│       ├── application.yml / application-dev.yml   ← ⚠️ 见 §5 特殊说明
│       └── sql/init.sql   ← 建库脚本（可重复执行：9 表 + 20 索引 + 2 视图 + 1 触发器 + 1 存储过程 + 种子数据）
├── frontend/
│   └── src/
│       ├── api/           ← axios 封装 request.js + 各模块 api；ai.js 是 fetch 流式（不走 axios）
│       ├── views/         ← 页面。PromptDetailView.vue 含 AI 试运行面板
│       ├── utils/auth.js  ← localStorage token 管理（key: ai_prompt_token / ai_prompt_user）
│       └── assets/theme.css ← 明暗双主题 CSS 变量（明绿/暗紫）
└── docs/screenshots/      ← README 用截图
```

## 3. 环境与启动

| 项 | 值 |
|---|---|
| MySQL | `localhost:3306`，库名 `ai_prompt_db`，账号 `root`（密码在本地配置文件里，见 §5） |
| Redis | `localhost:6379`，无密码，db 0（必须先启动，验证码/AI/计数都依赖它） |
| 后端 | 端口 8080，context-path `/api`。IDEA 跑 `AiPromptApplication`，或 `mvn spring-boot:run` |
| 前端 | `cd frontend && npm run dev` → `http://localhost:5173`，Vite 把 `/api` 代理到 8080 |
| Swagger | `http://localhost:8080/api/swagger-ui.html` |
| 测试账号 | `admin / admin123`（管理员）、`test / 123456`（普通用户） |

首次建库：MySQL 里执行 `backend/src/main/resources/sql/init.sql`（脚本自带 DROP，可重复执行）。

## 4. ⚠️ application-dev.yml 的 skip-worktree 保护（重要，勿踩坑）

`backend/src/main/resources/application-dev.yml` 被 git 设置了 **skip-worktree**（本地改动不进版本库），用于保护本地真实数据库密码不被提交：

- 仓库版本：`password: your_password`（占位符）+ 完整的 redis/ai 配置段
- 本地版本：真实密码 + 可能有本地调试改动
- **正常改代码永远不要碰这个文件；确实要更新仓库模板时**，按此流程：
  1. `git update-index --no-skip-worktree <file>` 解除保护
  2. 把密码临时改回 `your_password` → `git add` → 恢复真实密码
  3. `git update-index --skip-worktree <file>` 重新上保护
- 查看 flags：`git ls-files -v | findstr application-dev`（S=skip-worktree，H=正常）

## 5. AI 试运行配置（当前未启用）

详情页「AI 试运行」面板默认降级（点开始生成提示"未配置 API Key"）。启用方式——改 `application-dev.yml`（本地改，不会提交）：

```yaml
ai:
  enabled: true                                # 改 true 开放功能
  base-url: https://open.bigmodel.cn/api/paas/v4   # 智谱 GLM（OpenAI 兼容端点）
  api-key: "你的智谱APIKey"                     # bigmodel.cn 控制台申请
  model: glm-5.3-flash                         # 按你开通的模型填
  daily-limit: 20                              # 每用户每日次数（Redis 限流）
  timeout-seconds: 60
```

`base-url` 支持三种填法（AiService.buildChatUrl 自动识别）：填到版本目录（`.../v1`、`.../v4`）、只填主机（自动补 `/v1/chat/completions`）、或填完整端点。DeepSeek / 智谱 GLM / 通义（compatible-mode）均已兼容。

## 6. Git 仓库结构（两个仓库，别搞混）

1. **源码仓库**：`后端+前端/` 目录，remote = `https://github.com/KONGBAI666666/ai-prompt-fullstack`，master 分支。正常开发在这里。
2. **文档仓库**：外层 `AI prompt项目（有前端）（T）/` 目录，**纯本地无 remote**，存放课设报告/简历/答辩文档（`项目文档/` 目录）。改这些文档后在其目录内单独 commit，不要 push。

提交规范：约定式提交，`feat:` / `fix:` / `docs:` / `chore:` / `refactor:` 前缀 + 中文描述。

## 7. 架构要点（改代码前必读）

### 鉴权：Sa-Token 两层防线（config/SaTokenConfig.java）
- 第一层 `SaServletFilter`：白名单（`/user/register`、`/user/login`、`/user/captcha`、swagger、`/error`）之外全部要求登录，未登录返回 401 JSON
- 第二层 `SaInterceptor`：让 `@SaCheckRole` / `@SaCheckPermission` 注解生效，权限数据实时查库（后台改权限即时生效）
- 新增接口默认会被第一层拦住，**除非加白名单，否则无需自己处理"未登录"**

### token 传递
- header 名为 `Authorization`（sa-token.token-name），值为登录返回的 token 字符串（无 Bearer 前缀）
- 前端 axios 统一走 `request.js` 拦截器自动携带；**任何绕开 axios 用原生 fetch 的地方必须手动加 header**（ai.js 是现成范例）

### Redis key 清单（全部 StringRedisTemplate）
| key 模式 | 用途 | TTL |
|---|---|---|
| `captcha:{uuid}` | 图形验证码，Lua 脚本原子取出即删（一次性防重放） | 5 分钟 |
| `prompt:view:delta:{id}` | 浏览数未落库增量，INCR 累加 | 2 天（兜底） |
| `prompt:view:active` | 有增量的 prompt id 集合（Set），落库任务扫它 | 无 |
| `category:list:all` | 分类列表缓存（空值也缓存防穿透），增删时主动失效 | 10 分钟 |
| `ai:quota:{userId}:{yyyy-MM-dd}` | AI 试运行每日限流计数 | 2 天 |

浏览计数链路：详情页 → `CountService.incrView`（INCR）→ 读时"DB 基准值 + delta"合并返回 → `@Scheduled` 30 秒落库（GET → UPDATE → DECRBY 三步防丢，**不要改成 GET→DEL，会丢计数**）。

### AI 试运行 SSE 协议
- 接口：`POST /api/ai/run`，body `{promptId, input}`，响应 `text/event-stream`
- 每个事件 data 为单行 JSON：`{"t":"c","v":"增量文本"}` / `{"t":"done"}` / `{"t":"error","v":"原因"}`
- 后端生成跑在 JDK21 虚拟线程里；前端 fetch + ReadableStream 手动解析（Axios 拿不到流式、EventSource 不能带鉴权头，别"优化"回 axios）
- 提示词正文按 promptId 从库里取，**不信任前端传的内容**；生成成功会自动记使用历史

## 8. 代码规范（本项目既有风格，请保持一致）

- **注释**：中文 Javadoc，讲"为什么"多于"是什么"，类注释常含演进故事/踩坑背景。改动旧文件时保持原有注释风格
- **分层**：Controller 薄（参数校验 + 调 Service）；业务在 Service；DTO 入参 / VO 出参分离（VO 天然不含 password）
- **写操作**：禁止用整个实体 updateById 覆盖计数字段（历史踩坑，见 §9），用定向字段更新或 setSql 原子操作
- **并发**：唯一键兜底 + DuplicateKeyException 翻译成友好提示（收藏接口是范例）
- **不信任前端**：权限判断后端 Service 再校验一次，前端按钮显示只是体验层

## 9. 已知坑（前人踩过，别再踩）

1. **fetch 不带 token**：绕开 axios 的请求必须手动加 `Authorization: getToken()`，否则 401"请先登录"
2. **Vue Router 相同路径导航会被 abort**：重复 navigate 到当前 URL 不会重新发请求（自动化测试时浏览数"不变"就是这个伪问题，真实用户路径没问题）
3. **PowerShell 给 curl 传 JSON 会吞双引号**：写临时文件用 `-d "@file"` 传
4. **@Scheduled 不跑**：确认启动类有 `@EnableScheduling`
5. **init.sql 中文乱码**：MySQL 命令行执行加 `--default-character-set=utf8mb4`，或用 Navicat 等 GUI 跑
6. **8080 端口占用**：起后端前确认旧实例已停（netstat 查 PID）
7. **本机 Redis 是 3.2**：不支持 GETDEL 等新命令，原子操作用 Lua 脚本（CaptchaService 有范例）

## 10. 怎么验证改动（E2E 流程）

```
1. 起后端（8080）+ 前端（5173）+ Redis
2. curl 登录拿 token：
   GET  http://localhost:8080/api/user/captcha        → {id, image(base64)}
   redis-cli GET "captcha:{id}"                       → 4 位验证码（开发期可直接读）
   POST http://localhost:8080/api/user/login          → {token, user}（body: username/password/captchaId/captchaCode）
3. 带上 Authorization: {token} 调目标接口验证
4. 浏览器打开 http://localhost:5173 走真实用户路径（列表→点卡片进详情），确认 UI 正常
```

## 11. 待办 / 可选方向（接手后可做的事）

- [ ] 配置智谱 GLM 的 api-key，启用 AI 试运行真实生成（§5）
- [ ] （可选）AI 试运行失败重试 / 生成历史回显
- [ ] （可选）Pinia 实际接管登录态（目前 localStorage 直用）
- [ ] 不要主动升级依赖大版本（Spring Boot 3.4.x / Sa-Token 1.45 是课设报告的定稿口径，升级会让文档失真）
