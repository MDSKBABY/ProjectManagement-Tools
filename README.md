# 轻量化项目管理工具

项目按照 [PLAN.md](PLAN.md) 分阶段实施。当前建设状态、验证结果和后续任务统一记录在 [PROGRESS.md](PROGRESS.md)，新会话应先阅读这两个文件。

## 环境要求

- Java 21
- Maven 3.9+
- Node.js 22+
- pnpm 10+
- Docker 与 Docker Compose

## 启动开发依赖

1. 将 `.env.example` 复制为 `.env`。
2. 将 `.env` 中的两个密码占位值替换为随机密码。
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
```
