# 本地启动与测试手册

本文用于开发人员在本机启动项目并开展功能测试。所有命令默认从项目根目录执行：

```text
/Users/xuke/Documents/ChatGPT/项目管理工具
```

项目提供两种启动方式：

- 日常功能测试：Docker 只运行 PostgreSQL 和 Valkey，后端、前端在本机运行。启动快，修改代码后便于调试，推荐优先使用。
- 完整容器测试：前后端和数据库都由生产 Compose 运行，用于检查镜像、Nginx 代理和部署配置。

两种方式不要同时启动，否则可能占用相同端口或连接到错误的数据库。

## 一、首次准备

### 1. 检查开发工具

```bash
java -version
mvn -version
node -v
pnpm -v
docker version
docker compose version
```

最低要求：Java 21、Maven 3.9、Node.js 22、pnpm 10，以及可正常运行的 Docker 和 Docker Compose v2。

### 2. 创建本机环境变量文件

`.env` 已被 Git 忽略，不能把它提交到仓库。

```bash
cp .env.example .env
openssl rand -base64 32
```

将第二条命令生成的值填入 `.env` 的 `SERVER_CREDENTIAL_MASTER_KEY`，并至少修改以下占位值：

```dotenv
POSTGRES_PASSWORD=请替换为随机数据库密码
VALKEY_PASSWORD=请替换为随机Valkey密码
SERVER_CREDENTIAL_MASTER_KEY=请填入刚生成的Base64值
INITIAL_ADMIN_USERNAME=admin
INITIAL_ADMIN_PASSWORD=请替换为唯一且不少于12个字符的密码
INITIAL_ADMIN_DISPLAY_NAME=系统管理员
```

不要在聊天、截图、日志或提交记录中展示 `.env` 的真实内容。

## 二、推荐方式：启动开发环境

需要三个终端窗口。第一次启动会下载镜像和项目依赖，耗时会比后续启动长。

### 终端一：启动 PostgreSQL 和 Valkey

```bash
docker compose --env-file .env -f deploy/docker-compose.dev.yml up -d postgres valkey
docker compose --env-file .env -f deploy/docker-compose.dev.yml ps
```

等到 `postgres` 和 `valkey` 均显示为 `healthy` 后，再启动后端。

查看依赖服务日志：

```bash
docker compose --env-file .env -f deploy/docker-compose.dev.yml logs --tail=100 postgres valkey
```

### 终端二：启动 Spring Boot 后端

先把 `.env` 导入当前终端，再启动后端：

```bash
set -a
source .env
set +a
mvn -f backend/pom.xml spring-boot:run
```

后端启动时会自动执行 Flyway V1～V18。看到 Spring Boot 启动完成后，另开终端检查：

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
```

预期响应包含：

```json
{"status":"UP"}
```

全新数据库会根据 `.env` 创建首位管理员。日志出现“首位管理员已安全初始化”后，可以从 `.env` 删除三个 `INITIAL_ADMIN_*` 配置；账号已经保存到数据库，后续重启不会丢失。

### 终端三：启动 Vue 前端

首次启动先安装锁定版本的依赖：

```bash
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

浏览器打开：

```text
http://127.0.0.1:5173
```

前端开发服务器会把 `/api` 请求代理到 `http://127.0.0.1:8080`，因此测试时只访问前端地址，不要直接通过后端地址登录。

### 可选：启用 Ollama 日报润色

不测试 AI 时保持 `.env` 中的 `OLLAMA_ENABLED=false`，日报保存、确认等功能仍可正常测试。

需要测试 AI 时，先启动 Ollama 并下载模型：

```bash
docker compose --env-file .env -f deploy/docker-compose.dev.yml --profile ai up -d ollama
docker compose --env-file .env -f deploy/docker-compose.dev.yml --profile ai exec ollama \
  ollama pull qwen2.5:3b
```

然后把 `.env` 改为：

```dotenv
OLLAMA_ENABLED=true
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen2.5:3b
```

修改后停止并重新启动后端，使配置生效。模型下载体积较大；如果暂时只做普通业务测试，可以跳过本步骤。

## 三、建议的手工测试顺序

登录后建议按以下顺序测试，前一步产生的数据可以被后续模块复用：

1. 使用首位管理员登录，确认首页能正常加载。
2. 创建普通用户，验证启用、停用和密码重置。
3. 创建项目并添加项目成员，检查 OWNER、MANAGER、MEMBER、VIEWER 的操作差异。
4. 创建任务和里程碑，测试状态流转、关系、看板、日历和个人提醒。
5. 上传文件，创建部署资产、服务器、环境指纹、部署方案和部署记录。
6. 创建并审核技术知识文章。
7. 新建会议或培训记录，再创建日报并关联工作项、会议和部署记录；确认后检查内容不能被覆盖。
8. 根据已确认日报生成周报，确认日报快照已经固化。
9. 新建厂商、接口联调记录和设计资料，检查正式提交时的附件或纪要校验。
10. 打开项目全周期页面，检查里程碑、部署、会议、日报和周报是否按日期聚合，并正确显示临期或延期状态。
11. 使用低权限账号复测项目隐藏、文件下载、服务器凭据和管理入口的拒绝行为。

遇到错误时，记录操作步骤、请求时间、页面提示和后端日志，但不要复制密码、Cookie、CSRF Token 或 `.env` 内容。

## 四、运行自动化测试

### 后端全量测试与打包

后端集成测试使用 Testcontainers 自动创建临时 PostgreSQL，因此 Docker 必须处于运行状态，不要求先启动开发数据库。

```bash
mvn -f backend/pom.xml clean package
```

当前基线应为 107 个测试通过，并生成：

```text
backend/target/project-management-0.0.1-SNAPSHOT.jar
```

### 前端测试、类型检查和生产构建

```bash
cd frontend
pnpm install --frozen-lockfile
pnpm test
pnpm typecheck
pnpm build
```

当前基线应为 30 个测试文件、73 个测试通过。生产构建存在主包超过 500 kB 的性能提示，但不影响构建成功。

## 五、完整容器测试

此方式用于验证生产镜像、Nginx 代理和空库迁移。开始前先停止第二节中的本机后端、前端和开发 Compose。

本机通过 HTTP 测试时，在 `.env` 临时增加：

```dotenv
SESSION_COOKIE_SECURE=false
OLLAMA_ENABLED=false
```

正式 HTTPS 环境必须把 `SESSION_COOKIE_SECURE` 恢复为 `true`。

不测试 AI 时，只构建并启动 PostgreSQL、后端和前端：

```bash
docker compose --env-file .env -f deploy/docker-compose.prod.yml config
docker compose --env-file .env -f deploy/docker-compose.prod.yml build backend frontend
docker compose --env-file .env -f deploy/docker-compose.prod.yml up -d postgres backend frontend
docker compose --env-file .env -f deploy/docker-compose.prod.yml ps
```

等到三个服务均显示为 `healthy`，打开：

```text
http://127.0.0.1:8080
```

检查前端和 API 代理：

```bash
curl -fsS http://127.0.0.1:8080/healthz
curl -fsS http://127.0.0.1:8080/api/auth/csrf
```

检查数据库迁移版本：

```bash
docker compose --env-file .env -f deploy/docker-compose.prod.yml exec -T postgres \
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1"'
```

预期最新版本为 `18`。

如需完整测试 Ollama，执行普通的 `up -d` 启动全部服务，再进入容器下载 `.env` 中配置的模型。详细生产配置见 [部署与运维手册](deployment.md)。

## 六、停止项目

本机运行的后端和前端分别在对应终端按 `Ctrl+C` 停止。

停止开发 Compose，但保留数据库和模型数据：

```bash
docker compose --env-file .env -f deploy/docker-compose.dev.yml down
```

停止完整容器，但保留数据：

```bash
docker compose --env-file .env -f deploy/docker-compose.prod.yml down
```

只有明确需要重新建立空数据库时才能添加 `-v`。该参数会永久删除本项目的数据库、上传文件和模型卷，执行前必须确认数据可以丢弃或已经备份。

## 七、常见问题

### 端口被占用

```bash
lsof -nP -iTCP:5432 -sTCP:LISTEN
lsof -nP -iTCP:8080 -sTCP:LISTEN
lsof -nP -iTCP:5173 -sTCP:LISTEN
```

修改 `.env` 中的 `POSTGRES_PORT` 只会改变 PostgreSQL 主机端口；修改后后端也会读取相同值。前端端口由 `frontend/vite.config.ts` 配置。

### 后端无法连接数据库

先运行 `docker compose ... ps`，确认 PostgreSQL 为 `healthy`；再核对当前后端终端是否执行过 `source .env`。不要把真实连接密码粘贴到公开日志中。

### 登录成功后仍回到登录页

- 开发模式只访问 `http://127.0.0.1:5173`。
- 完整容器的本机 HTTP 测试需要 `SESSION_COOKIE_SECURE=false`。
- 修改 `.env` 后需要重启后端容器或本机后端进程。

### 首位管理员没有重新创建

首位管理员只会在 `app_user` 表完全为空时创建。修改 `.env` 不会覆盖已有账号，这是密码保护设计。需要重置密码时使用管理员密码重置能力，不要直接修改数据库。

### 查看后端容器日志

```bash
docker compose --env-file .env -f deploy/docker-compose.prod.yml logs --tail=200 backend
```

如果 Docker Desktop 显示服务健康但宿主机端口不可访问，先检查 `docker compose ... ps` 的 `PORTS` 列，再重启 Docker Desktop 后重新创建容器。
