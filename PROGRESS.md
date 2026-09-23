# 项目建设进度

> 最后更新：2026-09-23
> 用途：这是跨会话接力文件。每完成一个已确认模块，都要同步更新本文件。
> 新会话开始顺序：先读 `AGENTS.md` → `PLAN.md` → `PROGRESS.md` → 与下一模块相关的代码。不得仅凭本文件跳过代码核对。

## 当前阶段

- 当前目标：第一期“项目基础与资料复用”。
- 当前进度：阶段 0 与第一期模块 1～10 已完成；第一期功能验收通过，生产 Linux 发布、TLS 与备份恢复演练仍需在目标服务器执行。
- 用户已授权按 `docs/phase-1-implementation-plan.md` 连续开发第一期全部模块，不再逐模块等待确认；前后端按接口依赖交错推进。
- 2026-09-20 已确认产品与架构方向：保留现有项目，参考 Plane 交互和 OpenProject 模型；详见 `docs/decisions/ADR-001-retain-current-stack-and-reference-plane-openproject.md`。
- 当前 Git 状态：项目文件尚未提交，工作区内容由用户持续确认；未经用户明确要求不要创建提交。

## 第一期模块与验收顺序

1. 项目管理后端最小闭环：创建、列表、详情、全量更新、归档、软删除、资源级权限和审计。已完成。
2. 项目成员管理：添加、移除和调整项目内角色。已完成。
3. 项目工作台前端：项目列表、创建、详情和成员维护。已完成。
4. 统一文件元数据与本地存储。已完成。
5. 文件分片上传、校验、版本管理和下载鉴权。已完成。
6. 部署资产归档。已完成。
7. 服务器档案与敏感凭据保护。已完成。
8. 环境指纹和部署方案。已完成。
9. 部署记录、成功部署基线和相似环境检索。已完成。
10. 审计查询、部署构建与第一期整体验收。已完成。

## 阶段 0 模块与验收顺序

每个模块必须独立开发、独立测试、记录结果并由用户确认后再进入下一模块。

1. 注释与开发规范：建立 `docs/development.md`，为安全和业务核心代码补充中文说明。
2. 后端工程与健康检查：独立验证应用构建、启动和健康接口。
3. 数据库迁移：独立验证 PostgreSQL、Flyway V1/V2 和基础数据。
4. 登录会话：独立验证 CSRF、登录、当前用户、Session 轮换和退出。
5. RBAC 与首位管理员：独立验证权限加载、密码哈希、初始化保护和审计。
6. 管理员用户管理：独立验证分页、创建、默认角色、状态修改、权限拒绝和审计。
7. 前端基础接入：实现并独立测试登录、当前用户和管理员用户管理基础页面。
8. 阶段 0 整体验收：运行全量测试，启动依赖、后端和前端，提供访问地址与测试说明。

## 已完成模块

1. 后端工程骨架
   - Java 21、Spring Boot 3.5.16、Maven。
   - 提供 Actuator 健康检查。
2. 本地开发基础设施
   - Docker Compose 提供 PostgreSQL 17.11 与 Valkey 9.1.2。
   - 服务仅绑定本机，密码通过 `.env` 注入。
3. 前端工程骨架
   - Vue 3、TypeScript、Vite、Element Plus、Vitest。
   - 已验证测试、生产构建及浏览器基础渲染。
4. 首版数据库结构
   - Flyway V1 创建用户、角色、权限、项目、项目成员和审计表。
   - MyBatis-Flex 实体与 Mapper 已接入 PostgreSQL 集成测试。
5. 用户与 RBAC 持久层
   - 支持活动用户查询、分配角色、授权权限、读取用户权限集合。
   - `passwordHash` 不会被 JSON 序列化。
6. Spring Security 认证基础
   - 使用 BCrypt cost 12 和统一的错误提示，避免泄露账号状态。
   - 角色转换为 `ROLE_*`，权限使用原始权限码。
7. JSON 会话认证 API
   - `GET /api/auth/csrf`
   - `POST /api/auth/login`
   - `GET /api/auth/me`
   - `POST /api/auth/logout`
   - 使用 HttpOnly、SameSite=Lax 的服务端会话，并在登录时轮换 Session ID 和 CSRF 令牌。
8. 基础角色权限与首位管理员安全初始化
   - Flyway V2 固定写入 5 个系统角色：`ADMIN`、`PROJECT_MANAGER`、`IMPLEMENTER`、`TESTER`、`VISITOR`。
   - 固定写入 8 个第一期基础权限，并建立默认角色权限关系。
   - 仅当用户表为空且 `INITIAL_ADMIN_USERNAME`、`INITIAL_ADMIN_PASSWORD` 同时配置时创建首位管理员。
   - 密码经现有 BCrypt 编码器哈希；不记录原始密码。
   - 已有用户时跳过初始化，不覆盖账号、不修改密码、不自动提权。
   - 初始化在事务中执行，并写入系统主体的 `INITIAL_ADMIN_CREATED` 审计记录。
9. 管理员用户管理 API 最小闭环
   - `GET /api/v1/admin/users`：按关键词和状态分页查询用户。
   - `POST /api/v1/admin/users`：创建普通用户，密码使用 BCrypt 哈希，默认分配 `VISITOR` 角色。
   - `PATCH /api/v1/admin/users/{id}/status`：启用或停用用户。
   - 三个接口都要求 `user:manage` 权限；写接口继续要求 CSRF 令牌。
   - 禁止管理员停用当前登录账号；只接受 `ACTIVE`、`DISABLED` 两种人工设置状态。
   - 用户响应不包含原始密码或密码哈希。
   - 创建和状态变更分别记录 `USER_CREATED`、`USER_STATUS_CHANGED` 审计事件。
10. 项目管理后端最小闭环
   - 新增 `GET/POST /api/v1/projects` 和 `GET/PUT/DELETE /api/v1/projects/{id}`。
   - 项目支持编码、名称、客户、状态、计划日期、标签和描述；创建者自动成为负责人和 `OWNER` 成员。
   - 普通用户只能查询参与项目，管理员可查询全部项目；修改和删除还要求管理员、负责人或项目内 `MANAGER` 身份。
   - 项目编码忽略大小写且不可重复，结束日期不得早于开始日期，标签会裁剪、去重并限制数量和长度。
   - 删除采用幂等软删除；已删除项目不可查询或更新。
   - 创建、更新、删除分别写入 `PROJECT_CREATED`、`PROJECT_UPDATED`、`PROJECT_DELETED` 审计记录。
   - Flyway V3 为项目补充客户和 JSONB 标签字段及标签索引；V1/V2 保持不变。
11. 项目成员管理 API
   - 新增成员分页查询、添加、项目角色调整和幂等移除接口。
   - 成员查询复用项目可见性；写操作同时要求 `project:manage_members` 功能权限及管理员、OWNER 或 MANAGER 项目身份。
   - 只允许添加活动用户；普通接口只能维护 `MANAGER`、`MEMBER`、`VIEWER`，始终保护 OWNER。
   - 添加、角色调整和移除分别记录 `PROJECT_MEMBER_ADDED`、`PROJECT_MEMBER_ROLE_CHANGED`、`PROJECT_MEMBER_REMOVED` 审计事件。
12. 项目工作台前端
   - 管理员可在项目工作台与用户管理间切换，普通项目用户默认进入项目工作台。
   - 支持项目分页、关键词与状态筛选、创建、详情、编辑和删除，并按登录用户权限隐藏不可用操作。
   - 支持项目成员查询、候选用户搜索、添加成员、调整角色和移除成员；OWNER 在界面与服务端均受保护。
   - 页面包含加载、错误、空数据和窄屏布局；实际浏览器端到端验收已覆盖项目创建、详情和 OWNER 成员展示。
13. 统一文件元数据与本地存储
   - Flyway V4 新增统一 `file_asset` 元数据、`file_link` 业务关联、版本与状态约束，以及 `file:read`、`file:write` 权限。
   - 项目文件支持分页和文件名搜索；预留元数据固定为 `RESERVED`，不会把尚无物理内容的文件标记为可下载。
   - 存储根目录由 `FILE_STORAGE_ROOT` 配置，存储键由服务端生成；原始文件名与磁盘路径严格分离，路径解析拒绝绝对路径和目录穿越。
   - 项目 OWNER、MANAGER、MEMBER 可在具有功能权限时写入文件，VIEWER 只读；外部用户无法通过文件接口确认项目存在。
   - 前端项目详情同步加入资料分页列表、搜索、状态展示和分片上传入口骨架。
14. 文件分片上传、校验、版本管理和下载鉴权
   - Flyway V5 新增 `file_upload_chunk`，记录分片编号、大小和 SHA-256；文件状态按 `RESERVED → UPLOADING → AVAILABLE/FAILED` 流转。
   - 后端支持固定大小分片的幂等写入、越界和大小校验、冲突检测、流式合并、完整文件 SHA-256 校验、原子发布、取消清理和授权下载。
   - 同一 `fileGroupId` 下自动生成不可覆盖的递增版本，版本列表按新到旧返回；项目外用户无法借助文件编号确认或下载内容。
   - 前端上传入口已接通真实文件选择、分片进度、单片最多三次重试、取消、下载、版本列表和上传新版本；单文件最大 2GB。
15. 部署资产归档
   - Flyway V6 新增不可变 `deployment_asset` 版本表、项目/类型/环境/风险索引和标签 GIN 索引，并加入 `deployment_asset:read`、`deployment_asset:write` 权限矩阵。
   - 支持安装包、脚本、手册、配置模板和依赖组件；每个资产版本关联同项目内一个 `AVAILABLE` 文件版本，不能关联未完成或其他项目文件。
   - 同一 `assetGroupId` 创建递增版本且无覆盖更新接口；脚本在请求层、业务层和数据库约束层都强制填写前置条件、执行方式和回滚说明。
   - 支持名称/版本、类型、操作系统、架构、目标环境、风险等级和标签组合筛选，详情和版本历史继续叠加项目资源级授权。
   - 前端项目工作台同步提供部署资产列表、组合筛选、详情、脚本说明、创建资产、创建新版本及版本历史弹窗，并适配窄屏布局。
16. 服务器档案与敏感凭据保护
   - Flyway V7 新增服务器档案与独立凭据表，加入环境、状态索引及 `server:*`、`server_credential:*` 权限矩阵。
   - 服务器支持分页筛选、创建、更新和软删除；删除服务器时同步物理销毁其凭据密文。
   - 凭据使用 AES-256-GCM、独立随机 nonce 和项目/服务器/密钥版本 AAD 加密；主密钥缺失或非 32 字节时拒绝服务。
   - 凭据保存和解密查看只向系统管理员或项目负责人开放，且叠加功能权限；查看响应禁止缓存，所有写入和查看都留审计记录。
   - 前端项目工作台提供服务器列表、筛选和编辑；凭据默认隐藏，只在明确操作后查看，关闭查看或编辑窗口后立即清理明文状态。
17. 环境指纹与部署方案
   - Flyway V8 新增环境指纹、部署方案和有序方案步骤表，并加入规范化筛选索引、唯一性约束及 `environment_fingerprint:*`、`deployment_solution:*` 权限矩阵。
   - 环境指纹支持名称、环境、操作系统、架构、数据库、中间件和标签组合筛选；名称在项目内忽略大小写唯一，删除仍被未删除方案引用的指纹时返回冲突。
   - 部署方案按聚合整体创建和更新，每个步骤必须指定顺序和同项目的精确部署资产版本；跨项目指纹或资产统一按不存在处理，避免泄露资源信息。
   - 方案包含适用场景、架构说明、前置条件、回滚步骤、风险提示及草稿/启用/归档状态，写入、更新和删除均记录审计事件。
   - 前端项目工作台提供环境指纹结构化筛选与编辑、部署方案列表/详情/编辑、动态步骤和资产版本选择，并按独立读写权限控制展示和操作。
18. 部署记录、成功基线与相似环境检索
   - Flyway V9 新增只追加的 `deployment_record`，固化服务器、环境指纹、部署方案及精确资产版本 JSONB 快照，并加入 `deployment_record:read/write` 权限矩阵。
   - 执行人由当前会话确定，前端不能代人记录；失败结果强制填写异常说明，仅成功记录可设为基线，创建和基线变更均写入审计。
   - 相似检索仅读取同项目最多 500 个成功基线候选，按操作系统、架构、运行时、数据库、环境类型、网络区域、中间件和标签计算 0～100 分，返回可解释的匹配项与差异项。
   - 前端项目工作台提供记录筛选、新增执行、历史快照详情、成功基线切换和相似环境经验检索，并按独立读写权限控制。
19. 审计查询、部署构建与第一期整体验收
   - 新增只读审计查询 API 与管理端页面，支持按操作人、动作、资源类型、结果和时间范围分页筛选，要求 `audit:read` 权限。
   - 工作台增加权限感知的全局导航与项目模块导航；无权限入口不会展示，项目模块只挂载当前页面。
   - 增加前后端多阶段 Dockerfile、Nginx 反向代理和安全响应头、生产 Docker Compose、环境变量模板及单机部署运维手册。
   - 新增第一期跨模块旅程测试，覆盖项目、成员、真实分片文件、部署资产、服务器、环境指纹、方案、部署记录、成功基线、相似检索与审计查询。
   - 使用空 PostgreSQL 实例验证 Flyway V1～V9、首位管理员初始化与重启保护；真实浏览器完成登录、项目创建、模块导航和审计留痕验收。

## 当前关键决策

- 实际认证方案是服务端 Session Cookie + CSRF；后续实现应保持该方案，除非用户明确批准迁移。
- 当前认证路径是 `/api/auth/*`；业务 API 的 `/api/v1` 规范将在开始业务接口时统一确认。
- 基础权限是“功能级权限”；项目接口还必须叠加项目成员/负责人等资源级校验，不能只检查 `project:*`。
- Flyway 历史迁移文件不可修改；后续数据库变化必须新增 V3、V4 等迁移。
- `.env` 被 Git 忽略，真实密码不得写入源码、README、`PROGRESS.md` 或提交历史。
- 保留 Vue 3 + Spring Boot + PostgreSQL 作为唯一业务底座；不直接 Fork 或嵌入 Plane/OpenProject。
- Plane 只作为工作区、项目导航、多视图和列表/详情交互参考，相关 UI 使用当前前端技术栈自主实现。
- OpenProject 只作为统一工作项、成员角色、工作流、关系和变更历史的领域模型参考；工作项从第二期落地。
- 部署资产、服务器凭据、部署方案和日报周报等专业业务保持独立模型，不与通用工作项强行合表。

## 最近验证结果

- 第一期 / 模块 10（审计查询、部署构建与第一期整体验收），2026-09-23：
  - `AuditLogApiTest`：2 passed；覆盖组合筛选、倒序分页、详情反序列化、无权限拒绝和非法时间范围。
  - `PhaseOneJourneyApiTest`：1 passed；在临时 PostgreSQL 中完成第一期跨模块核心链路，并确认 10 条所有者审计事件可查询。
  - 最终执行后端全量 `clean package`：79 passed，0 failed，0 errors，0 skipped，JAR 打包成功且确认不存在重复 class 条目；此前一次因 Docker Desktop/Testcontainers 资源压力人工终止的复跑不计为通过。
  - 前端执行 `pnpm test`：19 个测试文件共 51 passed；执行 `pnpm build`：类型检查和 Vite 生产构建成功。
  - `docker compose --env-file .env.example -f deploy/docker-compose.prod.yml config --quiet` 通过；Docker Desktop 镜像源切换为 `https://docker.m.daocloud.io` 后，生产 Compose 前后端镜像完整构建通过。后端镜像为 `sha256:c260187f54f...`（约 243 MB），前端镜像为 `sha256:deec998467bb...`（约 51 MB）；后端构建改用 BuildKit Maven 缓存，首次构建约 73 秒，复验命中缓存。
  - 使用空 PostgreSQL 启动最终 JAR，确认迁移到 V9、初始化管理员、重启不重复初始化；浏览器完成管理员登录、项目创建、八个项目模块导航及审计查询，控制台 error/warn 为 0。
  - 隔离验收数据库最终为 1 个用户、1 个项目、2 条审计记录；验收进程、临时数据库容器和测试凭据均已清理。

- 第一期 / 模块 9（部署记录、成功基线与相似环境检索），2026-09-23：
  - 按 RED → GREEN 新增 `DeploymentRecordApiTest`；覆盖执行快照、历史不可变、列表筛选、失败说明、成功基线、审计、相似环境排序、功能权限、项目成员和 CSRF，共 5 passed。
  - 执行 `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/project-management-m2 clean package -q`：后端 76 passed，0 failed，0 errors，0 skipped，JAR 打包成功。
  - 执行 `pnpm test`：17 个测试文件共 47 passed；执行 `pnpm build`：TypeScript 类型检查与 Vite 生产构建成功。
  - 待在模块 10 统一启动真实服务后执行跨模块核心链路和部署构建验收。

- 第一期 / 模块 8（环境指纹与部署方案），2026-09-22：
  - 按 RED → GREEN 新增 `DeploymentPlanningApiTest`；独立覆盖环境指纹 CRUD/规范化筛选、部署方案聚合 CRUD、精确资产版本、有序步骤、跨项目隐藏、功能权限、项目成员权限、CSRF、重复顺序、删除冲突和审计，共 4 passed。
  - 执行 `mvn -f backend/pom.xml clean package`：14 个测试套件共 71 passed，0 failed，0 errors，0 skipped，JAR 打包成功。
  - 执行 `pnpm typecheck && pnpm test && pnpm build`：类型检查通过，15 个测试文件共 41 passed，生产构建成功。
  - 使用真实 PostgreSQL 从 V7 升级到 V8；浏览器完成生产环境指纹创建、启用部署方案创建及“应用发布包 · 8.1.0”精确版本绑定，详情正确展示执行顺序、说明和参数。
  - 390px 窄屏无页面级横向溢出，浏览器控制台 error/warn 为 0；验收账号、项目、资产、指纹、方案、审计及临时进程均已清理。

- 第一期 / 模块 7（服务器档案与敏感凭据保护），2026-09-20：
  - 按 RED → GREEN 新增 `ServerInventoryApiTest` 和 `ServerCredentialCipherTest`；独立覆盖 CRUD、双层授权、CSRF、参数校验、密文落库、AAD 防错配、密钥配置失败关闭、审计及删除时销毁凭据，共 8 passed。
  - 执行 `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/project-management-m2 clean package -q`：14 个测试套件共 67 passed，0 failed，0 errors，0 skipped，JAR 打包成功。
  - 执行 `pnpm typecheck && pnpm test && pnpm build`：类型检查通过，12 个测试文件共 33 passed，生产构建成功。
  - 使用真实 PostgreSQL 从 V6 升级到 V7；浏览器完成服务器创建、凭据配置、默认脱敏、显式解密查看及关闭清理验收，控制台无错误。
  - 真实数据库已确认密文、12 字节 nonce 与无明文残留；创建、凭据变更和凭据查看审计均已生成，验收账号与业务数据最后已清理。

- 第一期 / 模块 6（部署资产归档），2026-09-20：
  - 按 RED → GREEN 新增 `DeploymentAssetApiTest`；初次执行按预期因 V6 表和部署资产路由不存在而失败。
  - 独立测试覆盖功能权限、CSRF、脚本必填说明、可用文件、跨项目文件拒绝、不可变版本、组合筛选、版本历史、审计和项目外隐藏，共 5 passed。
  - 执行 `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/project-management-m2 clean package -q`：59 passed，0 failed，0 errors，0 skipped，JAR 打包成功。
  - 执行 `pnpm typecheck && pnpm test && pnpm build`：类型检查通过，10 个测试文件共 26 passed，生产构建成功。
  - 使用真实 PostgreSQL 从 V5 升级到 V6；浏览器验证部署资产列表、Linux+标签组合筛选、脚本前置/执行/回滚说明、版本历史和创建新版本表单，控制台 error/warn 为 0。
  - 浏览器验收账号、项目、文件、文件关联和部署资产记录均已清理，未保留测试数据。

- 第一期 / 模块 5（分片上传、校验、版本与下载鉴权），2026-09-20：
  - 按 RED → GREEN 新增 `ProjectFileUploadApiTest`；初次执行按预期因分片和完成路由不存在而失败。
  - 独立测试覆盖幂等分片、越界和大小错误、冲突分片、SHA-256 不一致、失败内容不可下载、版本不可覆盖、项目外下载隐藏、取消上传和临时分片清理，共 5 passed。
  - 执行 `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/project-management-m2 clean package -q`：54 passed，0 failed，0 errors，0 skipped，JAR 打包成功。
  - 执行 `pnpm typecheck && pnpm test && pnpm build`：类型检查通过，8 个测试文件共 22 passed，生产构建成功。
  - 使用真实 PostgreSQL 从 V4 升级到 V5；真实文件完成元数据预留、分片写入、合并和下载，服务端哈希及下载哈希均与源文件一致。
  - 浏览器验证可用状态、下载入口和版本弹窗，控制台 error/warn 为 0；验收账号、项目、数据库记录和 `/tmp` 物理文件均已清理。

- 第一期 / 模块 4（统一文件元数据与本地存储），2026-09-20：
  - 按 RED → GREEN 新增 `ProjectFileMetadataApiTest` 和 `LocalFileStorageTest`；初次执行按预期因路由与数据表不存在而失败。
  - 独立测试覆盖功能权限、CSRF、项目可见性、可写角色、分页搜索、2GB 上限、SHA-256/文件名校验、服务端存储键、路径穿越拒绝和审计，共 5 passed。
  - 执行 `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/project-management-m2 clean package -q`：49 passed，0 failed，0 errors，0 skipped，JAR 打包成功。
  - 执行 `pnpm typecheck && pnpm test && pnpm build`：类型检查通过，8 个测试文件共 20 passed，生产构建成功。
  - 使用真实 PostgreSQL 从 V3 升级到 V4，并在浏览器验证项目资料空状态、搜索区和按权限显示的上传入口；最终控制台 error/warn 为 0。
  - 浏览器验收专用项目和账号已清理；物理文件目录使用 `/tmp` 验收路径，未污染仓库。

- 第一期 / 模块 3（项目工作台前端），2026-09-19：
  - 新增项目 API 客户端、项目工作台、项目编辑弹窗和项目成员管理组件，并补齐相应单元测试。
  - 执行 `pnpm typecheck && pnpm test && pnpm build`：类型检查通过，6 个测试文件共 17 passed，生产构建成功。
  - 使用真实 PostgreSQL、后端和 Vite 代理完成浏览器端到端验收：成功登录、创建项目、查看项目详情及 OWNER 成员。
  - 浏览器控制台检查发现并修复未注册 `v-loading` 指令告警；使用全新浏览器标签复验后 error/warn 均为 0。
  - 验收专用项目和账号已从本地数据库清理，未保留测试数据。

- 第一期 / 模块 2（项目成员管理），2026-09-19：
  - 按 RED → GREEN 实现 `ProjectMemberApiTest`；初次测试按预期因成员路由不存在而失败。
  - 独立测试覆盖功能权限、CSRF、项目可见性、成员搜索、活动账号限制、重复添加、OWNER 角色保护、角色调整、幂等移除和审计。
  - 执行 `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/project-management-m2 -Dtest=ProjectMemberApiTest test -q`：6 passed，0 failed，0 errors，0 skipped。
  - 执行 `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/project-management-m2 clean package -q`：44 passed，0 failed，0 errors，0 skipped，JAR 打包成功。
  - 本模块没有数据库结构变化，因此未新增或修改 Flyway 迁移。

- 第一期 / 模块 1（项目管理后端最小闭环），2026-09-19：
  - 按 RED → GREEN 流程新增 `ProjectAdministrationApiTest`，先验证项目路由尚不可用，再实现接口和资源级授权。
  - 项目独立测试覆盖认证、功能权限、CSRF、创建、OWNER 成员关系、成员可见范围、管理员全局可见、负责人/MANAGER 修改、归档、重复编码、日期和标签校验、幂等软删除及审计。
  - 执行 `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/project-management-m2 -Dtest=ProjectAdministrationApiTest test -q`：6 passed，0 failed，0 errors，0 skipped。
  - 执行 `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/project-management-m2 clean package -q`：38 passed，0 failed，0 errors，0 skipped，JAR 打包成功。
  - 当前项目没有配置 Java lint；本模块使用 Maven 编译、测试和打包作为后端验证。

- 阶段 0 / 模块 8（整体验收与启动），2026-09-19：
  - 后端执行 `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/project-management-m2 clean package -q`：32 passed，0 failed，0 errors，0 skipped，JAR 打包成功。
  - 前端执行 `pnpm typecheck && pnpm test && pnpm build`：类型检查通过，3 个测试文件共 9 passed，生产构建成功。
  - 启动专用 PostgreSQL 17.11 与 Valkey 9.1.2；二者均为 healthy，分别绑定本机 `55432`、`56379`，不影响已有项目容器。
  - 后端在 `http://127.0.0.1:8080` 运行，健康检查返回 `UP`；前端在 `http://127.0.0.1:5173` 运行并返回 HTTP 200。
  - 通过前端 `/api` 代理完成真实 CSRF 获取、管理员登录和用户分页查询；登录账号获得 ADMIN 角色，初始用户列表返回 1 条记录。
  - 按用户明确要求，本模块不代替用户操作浏览器；桌面/移动端视觉和人工交互由用户自行验收。
  - 首次初始化成功后已从本机 `.env` 删除一次性 `INITIAL_ADMIN_*` 变量；账号保留在数据库中。
- 阶段 0 / 模块 7（前端基础接入），2026-09-18：
  - 接入服务端 Session 登录、会话恢复、退出、CSRF 自动处理和统一 API 错误。
  - 实现管理员用户列表、关键词与状态筛选、创建用户、启用/停用用户，以及加载、错误和空状态。
  - 页面沿用现有蓝色内部管理系统风格，支持窄屏布局、键盘表单、语义标题、错误播报和减少动画偏好。
  - 为会话恢复、CSRF 轮换、敏感请求和关键交互补充中文维护注释。
  - 执行 `pnpm typecheck`：通过。
  - 执行 `pnpm test`：3 个测试文件、9 passed、0 failed。
  - 执行 `pnpm build`：通过；Vite 成功生成生产资源。
  - 真实后端联调、浏览器交互与响应式验收归入模块 8，避免用模拟数据代替整体验收。
- 阶段 0 / 模块 6（管理员用户管理），2026-09-18：
  - 新增独立测试 `UserAdministrationApiTest`，使用真实 PostgreSQL 并在每个测试后回滚数据。
  - 执行 `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/project-management-m2 -Dtest=UserAdministrationApiTest test -q`。
  - 结果：6 passed，0 failed，0 errors，0 skipped；现有管理员用户管理实现无需修复。
  - 已验证分页与筛选、认证和权限拒绝、参数校验、CSRF、创建用户、字段清理、密码哈希、默认 VISITOR 角色、用户名和邮箱冲突、密码字节限制、状态修改幂等、自我停用保护、非法状态、用户不存在及审计记录。
  - 为创建用户和状态修改请求 DTO 补充了中文用途与安全说明。
- 阶段 0 / 模块 5（RBAC 与首位管理员），2026-09-18：
  - 新增独立测试 `AccessControlBootstrapTest`；测试数据修改均在事务中回滚。
  - 执行 `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/project-management-m2 -Dtest=AccessControlBootstrapTest test -q`。
  - 结果：4 passed，0 failed，0 errors，0 skipped；现有 RBAC 和初始化实现无需修复。
  - 已验证 ADMIN 角色及 8 项权限加载、BCrypt cost 12、空库初始化、系统审计、重复执行保护、无配置安全跳过、半配置失败和非活动账号认证拒绝。
  - 为 `AuthenticatedUser` 补充了用途说明和认证后清除密码哈希的安全注释。
- 阶段 0 / 模块 4（登录会话），2026-09-18：
  - 新增独立测试 `AuthenticationSessionTest`，仅验证认证会话边界，不调用管理员用户管理接口。
  - 执行 `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/project-management-m2 -Dtest=AuthenticationSessionTest test -q`。
  - 结果：3 passed，0 failed，0 errors，0 skipped；现有认证实现无需修复。
  - 已验证 CSRF 令牌获取、正确和错误凭据登录、统一失败信息、输入校验、当前用户读取、Session ID 与 CSRF 令牌轮换、退出及退出后的访问拒绝。
- 阶段 0 / 模块 3（数据库迁移），2026-09-18：
  - 新增独立测试 `DatabaseMigrationTest`，使用临时 PostgreSQL 17.11 从空库执行迁移。
  - 执行 `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/project-management-m2 -Dtest=DatabaseMigrationTest test -q`。
  - 结果：2 passed，0 failed，0 errors，0 skipped。
  - 已验证 Flyway 按 V1、V2 顺序成功执行，8 张核心业务表存在，用户表只保存 `password_hash`，并准确写入 5 个系统角色、8 个基础权限及默认角色授权关系。
- 阶段 0 / 模块 2（后端工程与健康检查），2026-09-18：
  - 新增独立测试 `BackendHealthTest`。
  - 执行 `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/project-management-m2 -Dtest=BackendHealthTest test -q`。
  - 结果：1 passed，0 failed，0 errors，0 skipped。
  - 测试实际启动随机端口 Tomcat 和临时 PostgreSQL，通过 HTTP 验证健康状态为 `UP`，并验证匿名访问受保护路由返回统一 401 错误。
- 阶段 0 / 模块 1（注释与开发规范），2026-09-18：
  - 执行 `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/project-management-m2 package -q`。
  - 结果：16 passed，0 failed，0 errors，0 skipped；后端 JAR 打包成功。
  - 本模块只增加开发规范和维护性注释，未改变接口或业务行为。
- 2026-09-18：`mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/project-management-m2 package -q`
  - 实际执行 16 个后端集成测试。
  - 结果：16 passed，0 failed，0 errors，0 skipped。
  - 使用 Testcontainers 启动真实 PostgreSQL，除原有认证与初始化流程外，还验证了用户分页、权限拒绝、参数校验、创建、密码哈希、默认角色、重复账号冲突、状态变更、自我停用保护和审计记录。
- 本模块没有新增前端代码，因此未重复执行前端测试和构建。
- 当前项目没有配置 Java lint；最终使用 Maven `package` 作为编译、测试和打包验证。

## 当前文件入口

- 总体实施计划：`PLAN.md`
- 架构决策：`docs/decisions/ADR-001-retain-current-stack-and-reference-plane-openproject.md`
- 开发与注释规范：`docs/development.md`
- 启动和登录说明：`README.md`
- 环境变量模板：`.env.example`
- 数据库结构：`backend/src/main/resources/db/migration/V1__create_identity_project_and_audit_tables.sql`
- RBAC 种子：`backend/src/main/resources/db/migration/V2__seed_base_roles_and_permissions.sql`
- 项目字段迁移：`backend/src/main/resources/db/migration/V3__add_project_customer_and_tags.sql`
- 文件元数据迁移：`backend/src/main/resources/db/migration/V4__add_file_metadata_and_permissions.sql`
- 文件分片迁移：`backend/src/main/resources/db/migration/V5__add_file_upload_chunks.sql`
- 部署资产迁移：`backend/src/main/resources/db/migration/V6__add_deployment_assets.sql`
- 服务器与凭据迁移：`backend/src/main/resources/db/migration/V7__add_server_inventory_and_credentials.sql`
- 环境指纹与部署方案迁移：`backend/src/main/resources/db/migration/V8__add_environment_fingerprints_and_deployment_solutions.sql`
- 部署记录与成功基线迁移：`backend/src/main/resources/db/migration/V9__add_deployment_records_and_baselines.sql`
- 服务器业务与加密：`backend/src/main/java/com/company/projectmanagement/server/service/ServerInventoryService.java`、`backend/src/main/java/com/company/projectmanagement/server/security/ServerCredentialCipher.java`
- 环境指纹业务：`backend/src/main/java/com/company/projectmanagement/environment/service/EnvironmentFingerprintService.java`
- 部署方案业务：`backend/src/main/java/com/company/projectmanagement/solution/service/DeploymentSolutionService.java`
- 部署记录业务：`backend/src/main/java/com/company/projectmanagement/record/service/DeploymentRecordService.java`
- 文件元数据接口：`backend/src/main/java/com/company/projectmanagement/file/web/ProjectFileController.java`
- 本地文件存储边界：`backend/src/main/java/com/company/projectmanagement/file/storage/LocalFileStorage.java`
- 管理员初始化：`backend/src/main/java/com/company/projectmanagement/identity/bootstrap/InitialAdminBootstrap.java`
- 安全配置：`backend/src/main/java/com/company/projectmanagement/config/SecurityConfiguration.java`
- 后端集成测试：`backend/src/test/java/com/company/projectmanagement/ProjectManagementApplicationTest.java`
- 后端独立冒烟测试：`backend/src/test/java/com/company/projectmanagement/BackendHealthTest.java`
- 数据库迁移独立测试：`backend/src/test/java/com/company/projectmanagement/DatabaseMigrationTest.java`
- 登录会话独立测试：`backend/src/test/java/com/company/projectmanagement/AuthenticationSessionTest.java`
- RBAC 与初始化独立测试：`backend/src/test/java/com/company/projectmanagement/AccessControlBootstrapTest.java`
- 管理员用户管理独立测试：`backend/src/test/java/com/company/projectmanagement/UserAdministrationApiTest.java`
- 项目管理独立测试：`backend/src/test/java/com/company/projectmanagement/ProjectAdministrationApiTest.java`
- 项目成员独立测试：`backend/src/test/java/com/company/projectmanagement/ProjectMemberApiTest.java`
- 文件元数据独立测试：`backend/src/test/java/com/company/projectmanagement/ProjectFileMetadataApiTest.java`
- 文件上传独立测试：`backend/src/test/java/com/company/projectmanagement/ProjectFileUploadApiTest.java`
- 部署资产独立测试：`backend/src/test/java/com/company/projectmanagement/DeploymentAssetApiTest.java`
- 服务器与凭据独立测试：`backend/src/test/java/com/company/projectmanagement/ServerInventoryApiTest.java`、`backend/src/test/java/com/company/projectmanagement/ServerCredentialCipherTest.java`
- 环境指纹与部署方案独立测试：`backend/src/test/java/com/company/projectmanagement/DeploymentPlanningApiTest.java`
- 部署记录独立测试：`backend/src/test/java/com/company/projectmanagement/DeploymentRecordApiTest.java`
- 审计查询业务：`backend/src/main/java/com/company/projectmanagement/audit/service/AuditLogQueryService.java`
- 审计查询独立测试：`backend/src/test/java/com/company/projectmanagement/AuditLogApiTest.java`
- 第一期跨模块旅程测试：`backend/src/test/java/com/company/projectmanagement/PhaseOneJourneyApiTest.java`
- 生产部署编排：`deploy/docker-compose.prod.yml`
- 部署与运维手册：`docs/deployment.md`
- 前端统一 API 客户端：`frontend/src/api/client.ts`
- 前端认证接口：`frontend/src/api/auth.ts`
- 前端用户管理接口：`frontend/src/api/users.ts`
- 前端项目文件接口：`frontend/src/api/files.ts`
- 前端部署资产接口：`frontend/src/api/deployment-assets.ts`
- 前端服务器接口：`frontend/src/api/servers.ts`
- 前端环境指纹与部署方案接口：`frontend/src/api/environment-fingerprints.ts`、`frontend/src/api/deployment-solutions.ts`
- 前端部署记录接口：`frontend/src/api/deployment-records.ts`
- 前端审计查询接口：`frontend/src/api/audit-logs.ts`
- 登录组件：`frontend/src/components/LoginPanel.vue`
- 用户管理组件：`frontend/src/components/UserManagement.vue`
- 创建用户组件：`frontend/src/components/UserCreateDialog.vue`
- 项目资料组件：`frontend/src/components/ProjectFilePanel.vue`
- 部署资产组件：`frontend/src/components/DeploymentAssetPanel.vue`
- 服务器档案组件：`frontend/src/components/ServerInventoryPanel.vue`
- 环境指纹与部署方案组件：`frontend/src/components/EnvironmentFingerprintPanel.vue`、`frontend/src/components/DeploymentSolutionPanel.vue`
- 部署记录与相似检索组件：`frontend/src/components/DeploymentRecordPanel.vue`
- 审计查询组件：`frontend/src/components/AuditLogPanel.vue`
- 前端独立测试：`frontend/src/App.spec.ts`、`frontend/src/api/client.spec.ts`、`frontend/src/api/users.spec.ts`
- 用户管理入口：`backend/src/main/java/com/company/projectmanagement/identity/web/UserAdministrationController.java`
- 用户管理业务：`backend/src/main/java/com/company/projectmanagement/identity/service/UserAdministrationService.java`
- 项目管理入口：`backend/src/main/java/com/company/projectmanagement/project/web/ProjectController.java`
- 项目管理业务：`backend/src/main/java/com/company/projectmanagement/project/service/ProjectService.java`

## 下一步建议

在目标 Linux 主机完成生产容器组启动、HTTPS、备份恢复和回滚演练；随后进入第二期需求细化与模块规划。

## 已知风险与待办

- 登录接口尚未增加限流；生产发布前必须实现并验证。
- 尚无修改密码/重置密码能力；首次管理员密码只能通过初始化创建，初始化变量应在成功后删除。
- 项目、文件、部署资产、服务器、环境指纹、部署方案和部署记录 API 已叠加资源级授权。
- 相似检索为避免无界查询，当前只对最近 500 个成功基线候选计分；数据量显著增大后应引入 SQL 粗排或向量索引，不能直接去掉上限。
- 项目成员管理已实现，但负责人交接尚未开放；普通成员接口会保护 OWNER。
- 本地文件存储适合开发和单机部署；多实例生产环境需要在后续阶段切换为共享持久卷或对象存储。
- 部署资产当前采用不可变追加版本，没有删除或作废状态；若业务需要撤回错误版本，应后续增加显式状态与审计，不能物理覆盖历史。
- 服务器凭据主密钥必须由部署环境安全备份；当前记录了密钥版本但尚未实现多版本轮换和批量重加密，不能直接替换生产主密钥。
- 自动化测试存在 Mockito 动态加载 Java Agent 的未来 JDK 兼容警告，目前不影响测试结果，后续测试基础设施模块再处理。
- 生产 Compose 已完成静态校验和本机 arm64 前后端镜像构建；容器组尚未使用正式环境变量启动，目标 Linux 架构镜像构建、TLS、备份恢复、回滚及安全扫描仍需执行。
