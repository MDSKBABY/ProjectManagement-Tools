# 项目建设进度

> 最后更新：2026-09-19
> 用途：这是跨会话接力文件。每完成一个已确认模块，都要同步更新本文件。
> 新会话开始顺序：先读 `AGENTS.md` → `PLAN.md` → `PROGRESS.md` → 与下一模块相关的代码。不得仅凭本文件跳过代码核对。

## 当前阶段

- 当前目标：阶段 0“可测试基础版本”。目标完成后启动本地服务并向用户提供访问地址。
- 总体计划：阶段 0 完成后进入第一期“项目基础与资料复用”。
- 当前进度：阶段 0 模块 1 至模块 8 已完成；本地服务正在运行，等待用户通过浏览器验收。
- 当前 Git 状态：项目文件尚未提交，工作区内容由用户持续确认；未经用户明确要求不要创建提交。

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

## 当前关键决策

- 实际认证方案是服务端 Session Cookie，不是 `PLAN.md` 早期草案中的 JWT；后续实现应保持 Session 方案，除非用户明确批准迁移。
- 当前认证路径是 `/api/auth/*`；业务 API 的 `/api/v1` 规范将在开始业务接口时统一确认。
- 基础权限是“功能级权限”；项目接口还必须叠加项目成员/负责人等资源级校验，不能只检查 `project:*`。
- Flyway 历史迁移文件不可修改；后续数据库变化必须新增 V3、V4 等迁移。
- `.env` 被 Git 忽略，真实密码不得写入源码、README、`PROGRESS.md` 或提交历史。

## 最近验证结果

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
- 开发与注释规范：`docs/development.md`
- 启动和登录说明：`README.md`
- 环境变量模板：`.env.example`
- 数据库结构：`backend/src/main/resources/db/migration/V1__create_identity_project_and_audit_tables.sql`
- RBAC 种子：`backend/src/main/resources/db/migration/V2__seed_base_roles_and_permissions.sql`
- 管理员初始化：`backend/src/main/java/com/company/projectmanagement/identity/bootstrap/InitialAdminBootstrap.java`
- 安全配置：`backend/src/main/java/com/company/projectmanagement/config/SecurityConfiguration.java`
- 后端集成测试：`backend/src/test/java/com/company/projectmanagement/ProjectManagementApplicationTest.java`
- 后端独立冒烟测试：`backend/src/test/java/com/company/projectmanagement/BackendHealthTest.java`
- 数据库迁移独立测试：`backend/src/test/java/com/company/projectmanagement/DatabaseMigrationTest.java`
- 登录会话独立测试：`backend/src/test/java/com/company/projectmanagement/AuthenticationSessionTest.java`
- RBAC 与初始化独立测试：`backend/src/test/java/com/company/projectmanagement/AccessControlBootstrapTest.java`
- 管理员用户管理独立测试：`backend/src/test/java/com/company/projectmanagement/UserAdministrationApiTest.java`
- 前端统一 API 客户端：`frontend/src/api/client.ts`
- 前端认证接口：`frontend/src/api/auth.ts`
- 前端用户管理接口：`frontend/src/api/users.ts`
- 登录组件：`frontend/src/components/LoginPanel.vue`
- 用户管理组件：`frontend/src/components/UserManagement.vue`
- 创建用户组件：`frontend/src/components/UserCreateDialog.vue`
- 前端独立测试：`frontend/src/App.spec.ts`、`frontend/src/api/client.spec.ts`、`frontend/src/api/users.spec.ts`
- 用户管理入口：`backend/src/main/java/com/company/projectmanagement/identity/web/UserAdministrationController.java`
- 用户管理业务：`backend/src/main/java/com/company/projectmanagement/identity/service/UserAdministrationService.java`

## 下一步建议

阶段 0 已完成。用户可在浏览器验收登录、用户筛选、创建用户、启用/停用和退出流程；如发现问题，应先记录复现步骤，再在阶段 0 范围内修复并回归测试。

浏览器验收通过后，下一阶段建议先规划第一期“项目基础与资料复用”，不要在未确认需求前直接增加角色分配或项目管理接口。

## 已知风险与待办

- 登录接口尚未增加限流；生产发布前必须实现并验证。
- 尚无修改密码/重置密码能力；首次管理员密码只能通过初始化创建，初始化变量应在成功后删除。
- `PROJECT_MANAGER`、`IMPLEMENTER` 等角色的资源级授权尚未落地，不能据此开放项目写接口。
- 自动化测试存在 Mockito 动态加载 Java Agent 的未来 JDK 兼容警告，目前不影响测试结果，后续测试基础设施模块再处理。
- 尚未执行生产 Linux Docker 部署演练、备份恢复演练或安全扫描。
