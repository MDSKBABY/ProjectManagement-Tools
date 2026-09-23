# 第一期单机部署与运维

## 适用范围

本方案用 Docker Compose 在一台 Linux 主机上运行 PostgreSQL、Spring Boot 后端和 Nginx 前端。PostgreSQL 和后端不发布主机端口，只有前端入口可从主机访问。第一期应用尚未使用 Valkey，因此生产 Compose 不启动无用服务。

## 上线前准备

1. 安装 Docker Engine 和 Docker Compose v2，确保数据盘剩余空间充足。
2. 复制 `.env.example` 为 `.env`，仅在服务器本地编辑，不要提交。
3. 至少替换 `POSTGRES_PASSWORD`、`INITIAL_ADMIN_PASSWORD` 和 `SERVER_CREDENTIAL_MASTER_KEY`。主密钥可用 `openssl rand -base64 32` 生成，必须独立备份。
4. HTTPS 入口下保持 `SESSION_COOKIE_SECURE=true`。默认只绑定 `127.0.0.1:8080`，建议由主机 Caddy/Nginx 终止 TLS；仅在明确了防火墙时才把 `APP_BIND_ADDRESS` 改为 `0.0.0.0`。

## 构建与启动

```bash
docker compose --env-file .env -f deploy/docker-compose.prod.yml config
docker compose --env-file .env -f deploy/docker-compose.prod.yml build
docker compose --env-file .env -f deploy/docker-compose.prod.yml up -d
docker compose --env-file .env -f deploy/docker-compose.prod.yml ps
```

首次启动后打开 HTTPS 地址并用初始管理员登录。确认成功后，从 `.env` 删除三个 `INITIAL_ADMIN_*` 变量并重建后端容器，避免初始密码长期留在容器配置中。

```bash
docker compose --env-file .env -f deploy/docker-compose.prod.yml up -d --force-recreate backend
```

## 健康检查与日志

```bash
curl -fsS http://127.0.0.1:8080/healthz
docker compose --env-file .env -f deploy/docker-compose.prod.yml ps
docker compose --env-file .env -f deploy/docker-compose.prod.yml logs --tail=200 backend
```

`postgres`、`backend`、`frontend` 都应为 `healthy`。排障时不要把 `.env`、完整容器配置或凭据日志发送到公开渠道。

## 备份与恢复

备份必须同时包含：PostgreSQL 逻辑备份、`file_data` 文件卷、当前和历史服务器凭据主密钥。只备份数据库会丢失上传文件；丢失主密钥则无法解密已有服务器凭据。

创建数据库备份（命令会在当前目录生成文件）：

```bash
docker compose --env-file .env -f deploy/docker-compose.prod.yml exec -T postgres \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > project-management.dump
```

文件卷应由主机备份系统停机快照，或复制到受控备份仓。恢复必须先在隔离环境演练：启动空数据库，使用 `pg_restore --clean --if-exists`恢复数据库，恢复文件卷和对应密钥，再验证登录、文件下载和凭据解密。未经演练的备份不应视为可恢复。

## 升级与回滚

1. 升级前先完成上述备份，记录当前 Git 版本和镜像 ID。
2. 构建新镜像后先执行 `docker compose ... config`，再启动。Flyway 会在后端启动时执行前向迁移。
3. 应用回滚可重新构建已记录的 Git 版本；数据库不做手工逆迁移。如新版迁移与旧应用不兼容，只能在停机窗口从已演练的整体备份恢复。

## 第一期验收清单

- 从空 PostgreSQL 卷启动，Flyway V1～V9 全部成功。
- 完成“项目 → 成员 → 版本化文件/部署资产 → 服务器/环境指纹 → 方案 → 成功部署记录/基线 → 相似环境检索”。
- 使用不同权限账号复核项目隐藏、文件下载拒绝和凭据查看拒绝。
- 桌面与 390px 窄屏可完成导航，浏览器控制台无 error/warn。
- 备份恢复演练和 TLS 证书由实际生产主机的发布流程执行并留档。
