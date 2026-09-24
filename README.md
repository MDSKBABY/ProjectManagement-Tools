# 轻量化项目管理工具

项目按照 [PLAN.md](PLAN.md) 分阶段实施。当前建设状态、验证结果和后续任务统一记录在 [PROGRESS.md](PROGRESS.md)，新会话应先阅读这两个文件。

## 架构方向

系统保留 Vue 3 + Spring Boot + PostgreSQL 技术栈，以 Plane 作为工作区和项目交互参考，以 OpenProject Work Package 作为统一工作项模型参考。不直接合并两者源码或运行第二套项目管理后端。

决策背景、取舍与实施边界见 [ADR-001](docs/decisions/ADR-001-retain-current-stack-and-reference-plane-openproject.md)。

## 环境要求

- Java 21
- Maven 3.9+
- Node.js 22+
- pnpm 10+
- Docker 与 Docker Compose

## 启动开发依赖

1. 将 `.env.example` 复制为 `.env`。
2. 将 `.env` 中的数据库、Valkey 密码占位值替换为随机密码，并按下文生成服务器凭据加密主密钥。
3. 在项目根目录运行：

```bash
docker compose --env-file .env -f deploy/docker-compose.dev.yml up -d
```

查看服务状态：

```bash
docker compose --env-file .env -f deploy/docker-compose.dev.yml ps
```

停止服务但保留数据：

```bash
docker compose --env-file .env -f deploy/docker-compose.dev.yml down
```

## 运行后端测试

后端集成测试会通过 Testcontainers 自动启动临时 PostgreSQL，因此需要先启动 Docker。

```bash
mvn -f backend/pom.xml test
```

本地启动后端前，请将 `.env` 中的变量导入当前终端：

```bash
set -a
source .env
set +a
mvn -f backend/pom.xml spring-boot:run
```

应用启动时会自动执行尚未应用的 Flyway 迁移。后端健康检查地址为 `http://localhost:8080/actuator/health`。

## 初始化首位管理员

首次启动前，在不会提交到 Git 的 `.env` 中设置：

```dotenv
INITIAL_ADMIN_USERNAME=admin
INITIAL_ADMIN_PASSWORD=请替换为唯一且足够强的密码
INITIAL_ADMIN_DISPLAY_NAME=系统管理员
```

安全规则：

- 用户名只能包含字母、数字、点、下划线和连字符，长度为 3-64 位。
- 密码至少 12 个字符，UTF-8 编码后不超过 72 字节；数据库只保存 BCrypt 哈希。
- 两个必填变量必须同时提供，否则应用拒绝启动。
- 只有 `app_user` 完全为空时才会创建管理员；已有任何用户时不会覆盖密码或自动提权。
- 初始化成功会写入 `INITIAL_ADMIN_CREATED` 审计记录，动作主体为系统。

首次成功启动后，从 `.env` 删除三个 `INITIAL_ADMIN_*` 变量并妥善保存密码。然后可按下方认证流程使用该账号登录。

## 认证接口

认证采用 HttpOnly Cookie 服务端会话。前端登录顺序：

1. `GET /api/auth/csrf` 获取 CSRF 令牌。
2. 将令牌放入返回的 `headerName` 请求头，调用 `POST /api/auth/login`。
3. 登录成功后再次调用 `GET /api/auth/csrf`，刷新已作废的旧令牌。
4. `GET /api/auth/me` 查询当前用户。
5. 使用新 CSRF 请求头调用 `POST /api/auth/logout`。退出后，下次登录前需重新获取令牌。

登录请求体：

```json
{
  "username": "your-username",
  "password": "your-password"
}
```

生产环境必须通过 HTTPS 访问，并设置 `SESSION_COOKIE_SECURE=true`。

### 登录限流与密码管理

登录失败默认按“后端看到的来源地址 + 规范化用户名”计数：15 分钟内达到 5 次失败后限制 15 分钟，响应为 `429 LOGIN_RATE_LIMITED` 并包含 `Retry-After` 响应头。可通过以下环境变量调整：

```dotenv
LOGIN_RATE_LIMIT_MAX_FAILURES=5
LOGIN_RATE_LIMIT_WINDOW_SECONDS=900
LOGIN_RATE_LIMIT_BLOCK_SECONDS=900
LOGIN_RATE_LIMIT_MAX_ENTRIES=10000
```

当前登录用户可修改自己的密码：

```http
PATCH /api/auth/password
Content-Type: application/json

{
  "currentPassword": "当前密码",
  "newPassword": "至少 12 个字符的新密码"
}
```

拥有 `user:manage` 权限的管理员可重置其他用户的密码：

```http
PATCH /api/v1/admin/users/{id}/password
Content-Type: application/json

{
  "newPassword": "至少 12 个字符的临时密码"
}
```

两种操作都会使目标用户的所有现有会话失效，分别写入 `PASSWORD_CHANGED` 和 `PASSWORD_RESET` 审计记录。管理员不能通过重置接口绕过旧密码校验来修改自己的密码。

## 管理员用户管理 API

以下接口必须先登录，并且当前账号拥有 `user:manage` 权限。POST 和 PATCH 请求还必须携带当前会话的 CSRF 请求头。

分页查询用户：

```http
GET /api/v1/admin/users?page=1&pageSize=20&keyword=张三&status=ACTIVE
```

创建普通用户（创建后自动分配最低权限的 `VISITOR` 角色）：

```http
POST /api/v1/admin/users
Content-Type: application/json

{
  "username": "zhangsan",
  "initialPassword": "请使用至少12个字符的初始密码",
  "displayName": "张三",
  "email": "zhangsan@example.com",
  "mobile": "+86 13800000000"
}
```

启用或停用用户：

```http
PATCH /api/v1/admin/users/123/status
Content-Type: application/json

{
  "status": "DISABLED"
}
```

状态接口只接受 `ACTIVE` 或 `DISABLED`，并禁止管理员停用当前登录账号。密码及密码哈希不会出现在任何用户响应中；创建和状态变更均写入审计日志。

## 项目管理 API

项目接口使用 `/api/v1/projects`，必须登录并具有对应的 `project:*` 功能权限。除管理员外，查询结果还会按项目成员关系过滤；修改和删除需要当前用户是项目负责人或项目内 `MANAGER`。

```http
GET    /api/v1/projects?page=1&pageSize=20&keyword=平台&status=ACTIVE
POST   /api/v1/projects
GET    /api/v1/projects/{id}
PUT    /api/v1/projects/{id}
DELETE /api/v1/projects/{id}
```

创建项目示例：

```json
{
  "code": "PM-001",
  "name": "内部项目管理平台",
  "customerName": "示例客户",
  "status": "PLANNING",
  "startDate": "2026-09-20",
  "endDate": "2026-12-31",
  "tags": ["平台", "重点"],
  "description": "建设内部项目管理能力"
}
```

创建者会自动成为项目负责人和 `OWNER` 成员。项目编码创建后不可修改；`PUT` 需要提交名称、状态等全部可编辑字段。删除采用软删除且可安全重试，创建、更新和删除都会写入审计日志。写请求必须携带当前 Session 对应的 CSRF 请求头。

### 项目成员 API

成员查询要求 `project:read` 权限，并且当前用户必须能查看该项目。成员写接口要求 `project:manage_members` 权限，同时当前用户必须是系统管理员、项目负责人或项目内 `MANAGER`。

```http
GET    /api/v1/projects/{projectId}/members?page=1&pageSize=20&keyword=张
GET    /api/v1/projects/{projectId}/members/candidates?page=1&pageSize=20&keyword=李
POST   /api/v1/projects/{projectId}/members
PUT    /api/v1/projects/{projectId}/members/{userId}
DELETE /api/v1/projects/{projectId}/members/{userId}
```

添加成员请求示例：

```json
{
  "userId": 123,
  "role": "MEMBER"
}
```

可维护的普通角色为 `MANAGER`、`MEMBER`、`VIEWER`。`OWNER` 只能由项目创建和后续专门的负责人交接流程维护，普通成员接口不能授予、修改或移除 OWNER。只能添加正常启用的用户；重复添加返回冲突，重复移除普通成员可安全重试。添加、角色调整和移除均写入审计日志。

### 统一工作项 API

统一工作项首批支持任务 `TASK` 和里程碑 `MILESTONE`。查询需要 `work_item:read`，创建、修改和状态流转需要 `work_item:write`，删除需要 `work_item:delete`；同时还会检查项目成员与项目内角色。

```http
GET    /api/v1/projects/{projectId}/work-items?page=1&pageSize=20
POST   /api/v1/projects/{projectId}/work-items
GET    /api/v1/projects/{projectId}/work-items/{id}
PATCH  /api/v1/projects/{projectId}/work-items/{id}
DELETE /api/v1/projects/{projectId}/work-items/{id}
POST   /api/v1/projects/{projectId}/work-items/{id}/status-transitions
GET    /api/v1/projects/{projectId}/work-items/{id}/status-history
```

列表支持 `keyword`、`type`、`status`、`priority`、`assigneeId`、`plannedFrom` 和 `plannedTo` 组合筛选。创建示例：

```json
{
  "type": "TASK",
  "title": "准备现场部署",
  "description": "核对服务器与安装包",
  "priority": "HIGH",
  "assigneeId": 123,
  "plannedStartDate": "2026-10-01",
  "plannedEndDate": "2026-10-03"
}
```

工作项初始状态为 `TODO`，可流转到 `IN_PROGRESS`、`DONE` 或 `CANCELED`。状态变更必须使用独立接口，普通 PATCH 不能绕过状态机：

```json
{
  "status": "IN_PROGRESS",
  "comment": "已开始执行"
}
```

项目 OWNER、MANAGER 和系统管理员可完整维护；被指派的普通成员可编辑内容和流转状态，但不能改变类型或负责人。VIEWER 只读且不能被指派。删除采用软删除，状态历史和审计日志会保留。

工作项关系为有向关系，列表可用 `workItemId` 和 `type` 筛选：

```http
GET    /api/v1/projects/{projectId}/work-item-relations?page=1&pageSize=20
POST   /api/v1/projects/{projectId}/work-item-relations
DELETE /api/v1/projects/{projectId}/work-item-relations/{relationId}
```

```json
{
  "sourceWorkItemId": 10,
  "targetWorkItemId": 11,
  "type": "PARENT_CHILD"
}
```

`PARENT_CHILD` 表示父项指向子项，`PRECEDES` 表示前置项指向后续项，`BLOCKS` 表示阻塞项指向被阻塞项。父子图与依赖图分别保持无环；`PRECEDES` 和 `BLOCKS` 共享同一依赖图，同方向不能重复表达。关系不能跨项目或指向自身，删除可安全重试并保留审计记录。项目成员可查看，只有 OWNER、MANAGER 和系统管理员可维护。

个人提醒只对创建它的当前用户可见，支持按工作项、状态和提醒时间筛选：

```http
GET    /api/v1/projects/{projectId}/work-item-reminders?page=1&pageSize=20
POST   /api/v1/projects/{projectId}/work-item-reminders
POST   /api/v1/projects/{projectId}/work-item-reminders/{reminderId}/dismiss
DELETE /api/v1/projects/{projectId}/work-item-reminders/{reminderId}
```

```json
{
  "workItemId": 10,
  "remindAt": "2026-10-01T09:00:00+08:00",
  "message": "检查现场部署清单"
}
```

提醒时间必须晚于当前时间。关闭和删除均可安全重试，只有第一次操作会写入审计；当前切片提供站内提醒管理，不包含邮件、短信或操作系统推送。

前端在“项目工作台 → 工作项”中提供：

- 按关键词、类型、状态、优先级和负责人筛选的工作项列表。
- 工作项详情、计划/实际周期、创建、编辑和软删除。
- 受控状态流转、备注和按时间排列的状态历史。
- 父子、前置、阻塞关系的有向创建、查看和移除。
- 按四种状态分栏的看板，可从卡片进入详情或发起状态流转。
- 按计划周期铺展的月历，支持切换月份并从日期进入工作项详情。
- 仅本人可见的提醒列表，以及提醒创建、关闭和删除。

页面会根据功能权限、项目角色和当前负责人隐藏不可用操作，服务端仍会对每次请求重新授权。

### 项目文件与版本 API

文件接口同时检查 `file:*` 功能权限与项目成员关系。文件名只用于展示，磁盘存储键由服务端随机生成；开发环境的受控目录通过 `FILE_STORAGE_ROOT` 配置。

```http
GET    /api/v1/projects/{projectId}/files?page=1&pageSize=20&keyword=手册
POST   /api/v1/projects/{projectId}/files/metadata
PUT    /api/v1/projects/{projectId}/files/{fileId}/chunks/{chunkIndex}
POST   /api/v1/projects/{projectId}/files/{fileId}/complete
DELETE /api/v1/projects/{projectId}/files/{fileId}/upload
GET    /api/v1/projects/{projectId}/files/groups/{fileGroupId}/versions
GET    /api/v1/projects/{projectId}/files/{fileId}/download
```

元数据预留请求示例：

```json
{
  "originalName": "部署手册.pdf",
  "mediaType": "application/pdf",
  "sizeBytes": 1024,
  "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
}
```

元数据创建后状态为 `RESERVED`，响应会返回服务端分片大小和总分片数。客户端按编号上传二进制分片，全部成功后调用完成接口；服务端以流式方式合并、校验 SHA-256，再原子发布为 `AVAILABLE`。重复上传内容一致的分片可安全重试，冲突分片会被拒绝；取消上传会清理临时分片并把未完成资产标记为失败。

上传新版本时在元数据请求中传入已有文件的 `fileGroupId`，服务端自动生成递增且不可覆盖的版本号。版本列表按版本号倒序返回。下载每次都会重新校验当前用户的功能权限和项目成员关系，不能通过已知文件编号绕过项目权限。单文件上限为 2GB，原始文件名不能包含路径分隔符；分片大小可通过 `FILE_CHUNK_SIZE_BYTES` 配置，默认 5MB。

### 部署资产 API

部署资产用于把已上传完成的文件归档为可复用的安装包、脚本、手册、配置模板或依赖组件。读取要求 `deployment_asset:read`，创建版本要求 `deployment_asset:write`，同时继续检查项目成员关系。

```http
GET  /api/v1/projects/{projectId}/deployment-assets
GET  /api/v1/projects/{projectId}/deployment-assets/{assetId}
POST /api/v1/projects/{projectId}/deployment-assets
GET  /api/v1/projects/{projectId}/deployment-assets/groups/{assetGroupId}/versions
```

列表支持 `keyword`、`assetType`、`operatingSystem`、`architecture`、`environment`、`riskLevel` 和 `tag` 组合筛选。创建脚本资产示例：

```json
{
  "name": "生产自动部署脚本",
  "assetType": "SCRIPT",
  "versionLabel": "1.0.0",
  "fileAssetId": 123,
  "operatingSystem": "Linux",
  "architecture": "amd64",
  "environment": "PRODUCTION",
  "riskLevel": "HIGH",
  "tags": ["核心", "自动化"],
  "prerequisites": "安装 Java 21 并完成数据库备份",
  "executionInstructions": "使用实施账号执行 ./deploy.sh",
  "rollbackInstructions": "执行 ./rollback.sh 恢复上一版本"
}
```

关联文件必须属于同一项目且状态为 `AVAILABLE`。脚本必须同时填写前置条件、执行方式和回滚说明，服务端和数据库都会校验。部署资产没有覆盖更新接口；创建新版本时传入原资产的 `assetGroupId`，系统自动生成递增的内部版本并保留全部历史。

### 服务器档案与敏感凭据 API

服务器档案读取要求 `server:read`，维护要求 `server:write`，并继续检查项目成员关系。凭据使用独立接口：只有项目负责人或系统管理员能够保存和解密，同时还必须具有对应的 `server_credential:*` 功能权限。

```http
GET    /api/v1/projects/{projectId}/servers
POST   /api/v1/projects/{projectId}/servers
GET    /api/v1/projects/{projectId}/servers/{serverId}
PUT    /api/v1/projects/{projectId}/servers/{serverId}
DELETE /api/v1/projects/{projectId}/servers/{serverId}
PUT    /api/v1/projects/{projectId}/servers/{serverId}/credential
GET    /api/v1/projects/{projectId}/servers/{serverId}/credential
```

服务器普通响应只返回 `credentialConfigured`，不会返回凭据明文、密文或加密随机数。显式查看接口返回 `Cache-Control: no-store`，每次成功查看和凭据修改都会写入审计日志，审计内容不包含用户名或密码。

凭据使用 AES-256-GCM、每次独立随机 nonce，并绑定项目、服务器和密钥版本。启动前必须在不会提交到 Git 的 `.env` 中设置：

```dotenv
SERVER_CREDENTIAL_MASTER_KEY=请替换为32字节随机值的Base64
SERVER_CREDENTIAL_KEY_VERSION=v1
```

可以运行 `openssl rand -base64 32` 生成主密钥。主密钥丢失后已有凭据无法恢复；更换密钥版本前必须设计旧密钥保留或批量重加密流程，不能直接覆盖生产环境变量。

### 环境指纹与部署方案 API

环境指纹用于保存可检索的部署环境基线，部署方案则把一个环境指纹与一组有序的精确资产版本组合起来。读取与维护分别要求 `environment_fingerprint:read/write` 和 `deployment_solution:read/write`，所有接口还会检查项目可见性或可写成员身份。

```http
GET    /api/v1/projects/{projectId}/environment-fingerprints
POST   /api/v1/projects/{projectId}/environment-fingerprints
GET    /api/v1/projects/{projectId}/environment-fingerprints/{fingerprintId}
PUT    /api/v1/projects/{projectId}/environment-fingerprints/{fingerprintId}
DELETE /api/v1/projects/{projectId}/environment-fingerprints/{fingerprintId}

GET    /api/v1/projects/{projectId}/deployment-solutions
POST   /api/v1/projects/{projectId}/deployment-solutions
GET    /api/v1/projects/{projectId}/deployment-solutions/{solutionId}
PUT    /api/v1/projects/{projectId}/deployment-solutions/{solutionId}
DELETE /api/v1/projects/{projectId}/deployment-solutions/{solutionId}
```

环境指纹列表支持 `keyword`、`environment`、`operatingSystem`、`architecture`、`databaseName`、`middleware` 和 `tag` 组合筛选。项目内名称忽略大小写唯一；仍被未删除方案引用的指纹不能删除。

部署方案按完整聚合保存：请求包含方案名称、适用场景、环境指纹、架构说明、前置条件、回滚步骤、风险提示、状态和至少一个步骤。每个步骤必须包含唯一的正整数顺序、标题、执行说明及 `assetId`；该编号必须指向同一项目内的一个具体部署资产版本。跨项目指纹或资产不会被接受，更新方案时会以本次请求中的步骤集合整体替换旧步骤。

### 部署记录、成功基线与相似环境检索 API

部署记录是只追加的执行历史。创建时会由服务端记录当前登录人，并固化服务器、环境指纹、部署方案和精确资产版本快照；后续修改源数据不会改写历史。服务器凭据不会进入快照。

```http
GET  /api/v1/projects/{projectId}/deployment-records
POST /api/v1/projects/{projectId}/deployment-records
GET  /api/v1/projects/{projectId}/deployment-records/{recordId}
PUT  /api/v1/projects/{projectId}/deployment-records/{recordId}/baseline
GET  /api/v1/projects/{projectId}/deployment-records/similar?fingerprintId={fingerprintId}&limit=10
```

失败记录必须填写异常说明，只有成功记录可标记为基线。相似检索只在同项目的成功基线中运行，按操作系统、架构、运行时、数据库、环境类型、网络区域、中间件和标签给出 0～100 分，同时返回匹配项与差异项。读取要求 `deployment_record:read`，创建和维护基线要求 `deployment_record:write`，并继续检查项目成员身份。

### 审计查询 API

审计查询为只读管理员能力，要求当前用户具有 `audit:read` 权限。结果按创建时间和编号倒序返回，可组合筛选操作人、动作、资源类型、执行结果和时间范围。

```http
GET /api/v1/admin/audit-logs?page=1&pageSize=20&actorId=1&action=PROJECT_CREATED&resourceType=PROJECT&outcome=SUCCESS&createdFrom=2026-09-01T00:00:00Z&createdTo=2026-09-30T23:59:59Z
```

时间范围必须满足开始时间不晚于结束时间。响应中的 `details` 是审计事件写入时保存的结构化 JSON；查询接口不会修改或补写审计记录。

## 运行前端

首次运行先安装依赖：

```bash
cd frontend
pnpm install
```

启动开发服务器：

```bash
pnpm dev
```

前端默认地址为 `http://localhost:5173`。运行测试和生产构建：

```bash
pnpm test
pnpm build
```

开发服务器会把 `/api` 请求代理到本机 `http://127.0.0.1:8080`，因此浏览器只需访问前端地址，不需要单独处理跨域或 Cookie。

## Docker 单机部署

生产配置会多阶段构建前后端，对外只发布 Nginx 入口：

```bash
docker compose --env-file .env -f deploy/docker-compose.prod.yml config
docker compose --env-file .env -f deploy/docker-compose.prod.yml build
docker compose --env-file .env -f deploy/docker-compose.prod.yml up -d
```

生产环境变量、HTTPS、首位管理员收尾、健康检查、备份恢复和回滚步骤见 [部署与运维手册](docs/deployment.md)。

## 本机验收账号配置

如数据库是全新的，在首次启动后端前临时向被 Git 忽略的 `.env` 加入：

```dotenv
INITIAL_ADMIN_USERNAME=admin
INITIAL_ADMIN_PASSWORD=请替换为唯一且不少于12个字符的临时密码
INITIAL_ADMIN_DISPLAY_NAME=系统管理员
```

启动日志出现“首位管理员已安全初始化”后，从 `.env` 删除这三个变量。管理员已经保存在数据库中，后续重启无需再次设置；若要重新初始化，必须先明确清理本项目数据库卷，不能依靠修改环境变量覆盖已有账号。

如果本机默认端口已被其他项目占用，可在 `.env` 中设置其他端口，例如：

```dotenv
POSTGRES_PORT=55432
VALKEY_PORT=56379
FILE_STORAGE_ROOT=./data/files
FILE_CHUNK_SIZE_BYTES=5242880
```
