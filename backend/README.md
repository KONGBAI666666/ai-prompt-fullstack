# AI Prompt 管理系统

AI 提示词（Prompt）管理系统：实现 Prompt 的创建、分类、搜索、收藏、复用和使用记录管理，含 USER / ADMIN 两种角色的权限控制。

> 本科课程设计项目 · Spring Boot 单体后端

## 一、技术栈

| 类别 | 技术 | 版本 |
|---|---|---|
| 语言 / 框架 | Java + Spring Boot | 21 / 3.4.1 |
| ORM | MyBatis-Plus（含分页插件） | 3.5.17 |
| 认证鉴权 | Sa-Token | 1.45.0 |
| 密码加密 | spring-security-crypto（BCrypt） | - |
| 接口文档 | springdoc-openapi（Swagger UI） | 2.7.0 |
| 参数校验 | spring-boot-starter-validation | - |
| 数据库 | MySQL | 8.x |
| 工具库 | Lombok | - |
| 构建 | Maven | - |

## 二、项目结构

```
src/main/java/com/spring/aiprompt/
├── AiPromptApplication.java        # 启动类
├── common/Result.java              # 统一返回体 {code, message, data}
├── controller/                     # 6 个控制器：用户/Prompt/分类/收藏/使用记录/管理员
├── service/ + service/impl/        # 业务层（含 StpInterfaceImpl 角色数据源）
├── mapper/                         # MyBatis-Plus BaseMapper
├── entity/                         # User, Prompt, Category, Favorite, PromptHistory
├── dto/                            # 入参 + jakarta.validation 校验注解
├── vo/                             # 出参：UserVO(无密码), LoginVO, PromptVO, HistoryVO
├── config/                         # MybatisPlus / SaToken / Cors / Swagger / Password
├── exception/                      # BusinessException + GlobalExceptionHandler
└── handler/MyMetaObjectHandler.java# create_time/update_time 自动填充

src/main/resources/
├── application.yml                 # 激活 dev 环境
├── application-dev.yml             # 端口、数据源、Sa-Token 配置
└── sql/init.sql                    # 建库建表 + 初始数据
```

## 三、启动步骤

### 1. 初始化数据库

本地安装 MySQL 8.x，用 Navicat（或命令行）执行一次 `src/main/resources/sql/init.sql`：

- 自动创建数据库 `ai_prompt_db` 和 5 张表（DROP 重建，可重复执行）
- 写入初始数据：10 个默认分类、12 条示例 Prompt、12 个账号（admin / test 可用于登录，其余为演示用户）

### 2. 修改数据库连接（如有需要）

编辑 `src/main/resources/application-dev.yml` 中的 `spring.datasource.username / password` 为你本机 MySQL 的账号密码。

### 3. 启动应用

方式一（IDEA）：运行 `AiPromptApplication` 的 main 方法。

方式二（命令行）：

```bash
mvn spring-boot:run
```

看到日志 `AI Prompt 管理系统启动成功` 即启动完成。服务地址：`http://localhost:8080/api`

### 4. 内置账号

| 账号 | 密码 | 角色 |
|---|---|---|
| admin | admin123 | ADMIN（管理员） |
| test | 123456 | USER（普通用户） |

## 四、接口调试（Swagger）

浏览器打开：**http://localhost:8080/api/swagger-ui.html**

除注册/登录外，所有接口都需要登录后才能调用，步骤：

1. 展开 `POST /user/login` → 点 **Try it out** → 输入 `{"username":"admin","password":"admin123"}` → **Execute**
2. 从响应中复制 `data.token` 的值
3. 点页面右上角 **Authorize** 按钮，粘贴 token（不加 Bearer 前缀），点 Authorize → Close
4. 之后所有请求自动携带 token；换账号调试时先 Logout 再重新 Authorize

## 五、接口清单（共 23 个，前缀 /api）

### 用户 /user

| 接口 | 方法 | 权限 | 说明 |
|---|---|---|---|
| /user/register | POST | 公开 | 注册（用户名查重，BCrypt 加密入库） |
| /user/login | POST | 公开 | 登录，返回 token + 用户信息 |
| /user/info | GET | 登录 | 当前用户信息（不含密码） |
| /user/logout | POST | 登录 | 退出登录 |

### Prompt /prompt（核心）

| 接口 | 方法 | 权限 | 说明 |
|---|---|---|---|
| /prompt | POST | 登录 | 创建 Prompt |
| /prompt/list | GET | 登录 | 分页列表，支持 keyword（匹配标题/描述）+ categoryId 筛选 |
| /prompt/my | GET | 登录 | 我的 Prompt 分页列表 |
| /prompt/{id} | GET | 登录 | 详情（浏览数原子 +1） |
| /prompt/{id}/edit | GET | 本人 | 编辑页回填数据（不计浏览数） |
| /prompt/{id} | PUT | 本人 | 修改（非本人返回 403） |
| /prompt/{id} | DELETE | 本人或 ADMIN | 物理删除 + 级联清理收藏/使用记录 |

### 分类 /category

| 接口 | 方法 | 权限 | 说明 |
|---|---|---|---|
| /category/list | GET | 登录 | 分类列表 |
| /category | POST | ADMIN | 新增分类（名称查重） |
| /category/{id} | DELETE | ADMIN | 删除（分类下仍有 Prompt 时拒绝） |

### 收藏 /favorite

| 接口 | 方法 | 权限 | 说明 |
|---|---|---|---|
| /favorite/{promptId} | POST | 登录 | 收藏（防重复 + 收藏数原子 +1） |
| /favorite/{promptId} | DELETE | 登录 | 取消收藏（收藏数原子 −1） |
| /favorite/list | GET | 登录 | 我的收藏分页列表 |

### 使用记录 /history

| 接口 | 方法 | 权限 | 说明 |
|---|---|---|---|
| /history/{promptId} | POST | 登录 | 记录一次使用（前端"复制 Prompt"时调用） |
| /history/list | GET | 登录 | 我的使用记录分页（含 Prompt 标题） |

### 管理员 /admin（需 ADMIN 角色）

| 接口 | 方法 | 说明 |
|---|---|---|
| /admin/user/list | GET | 用户分页列表 |
| /admin/user/{id}/status | PUT | 启用/禁用用户（禁用后立即踢下线） |
| /admin/prompt/list | GET | 所有 Prompt 分页（支持关键词） |
| /admin/stats | GET | 统计：用户数 / Prompt 数 / 收藏数 / 今日新增 |

## 六、统一返回与错误码

所有接口返回统一结构：

```json
{ "code": 200, "message": "success", "data": { } }
```

| code | 场景 |
|---|---|
| 200 | 成功 |
| 400 | 业务错误 / 参数校验失败（返回中文提示） |
| 401 | 未登录 / token 失效 |
| 403 | 无权限（改删他人 Prompt、普通用户调管理员接口） |
| 500 | 未预期系统异常（日志记录堆栈，不向前端返回） |

## 七、核心设计要点（答辩可讲）

1. **认证鉴权双层设计**：Sa-Token 全局过滤器负责"白名单外一律登录"（未登录返回 JSON 401 而非 HTML 错误页）；SaInterceptor + `@SaCheckRole("ADMIN")` 注解负责角色鉴权，角色由 `StpInterfaceImpl` 实时查库，改角色即刻生效。
2. **密码安全**：BCrypt 单向加密存储（自动加盐）；登录时"用户不存在"与"密码错误"统一提示，防止用户名枚举；所有出参 VO 不含密码字段。
3. **越权防护**：修改 Prompt 仅限本人；删除 Prompt 限本人或管理员，Service 层用 `Objects.equals` 比对归属，非法操作返回 403。
4. **计数原子性**：浏览数/收藏数用 `UPDATE ... SET x = x + 1` 数据库原子更新（取消收藏用 `GREATEST(x-1, 0)` 防负数），避免"查出再改回"的并发覆盖问题。
5. **并发防重**：收藏采用"代码查重 + 数据库联合唯一索引"双保险，并发冲突时捕获 `DuplicateKeyException` 转为友好业务提示。
6. **事务与级联**：删除 Prompt 在 `@Transactional` 中同步清理收藏、使用记录，保证数据一致性。
7. **查询性能**：列表接口批量查询分类名/作者名/收藏状态（`selectBatchIds` + Map 组装），避免 N+1 查询。
8. **统一异常体系**：Service 层只抛 `BusinessException`，`GlobalExceptionHandler` 统一转为 Result JSON，Controller 无 try-catch 样板代码。

## 八、二期规划（当前版本明确排除）

Redis 缓存、AI 接口调用（Prompt 效果测试）、Prompt 评分、公开分享、Elasticsearch 全文检索。
