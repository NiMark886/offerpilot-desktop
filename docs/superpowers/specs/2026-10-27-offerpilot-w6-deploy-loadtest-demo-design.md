# OfferPilot P0 Week 6 设计：一键部署、20 并发压测与演示物料

- 日期：2026-10-27
- 分支：`feat/p0-week6-deploy`
- 状态：设计已确认，实施中
- 前置：P0 Week 5 已完成（题库、五维报告、成长历史、练习模式、管理员题库 CRUD、每日额度）
- 执行说明（2026-10-27）：本机 Docker Desktop 无法启动（WSL2/服务权限未就绪）。W6 运行验收改用本地模式（Maven + 本地 MySQL `root/1234` + Vite）；Dockerfile/Compose/脚本仍交付并做静态校验，容器运行验证待环境恢复后补做。

## 1. 目标

按总体方案 W6 验收口径完成 P0 收尾：

1. 本地 Docker Compose 一键部署前端、后端与 MySQL。
2. 20 并发完整链路压测，产出可复现报告。
3. 产出 3 分钟演示脚本、产品介绍页、简历项目描述。
4. 不扩展业务功能，不引入向量检索与支付。

## 2. 部署架构

```text
浏览器
  │  http://localhost:8088
  ▼
frontend（Nginx）
  ├─ /             → Vue SPA 静态文件（history fallback）
  └─ /api/*        → proxy_pass http://backend:8080（SSE 关闭缓冲）
                      │
                      ▼
                  backend（Spring Boot，容器内 8080）
                      ├─ host.docker.internal:3306 → 本地 MySQL（offerpilot）
                      └─ upload-data 卷（简历文件）
```

设计要点：

- 使用本机已安装的 MySQL：`127.0.0.1:3306`，账号 `root`，密码 `1234`，数据库 `offerpilot`；容器内通过 `host.docker.internal` 访问。
- Compose 默认只启动 `backend + frontend`；不再启动 MySQL 容器。
- 仅暴露 `frontend` 宿主端口，默认 `8088`，通过 `OFFERPILOT_HTTP_PORT` 覆盖；避免与本地 8080/3306 冲突。
- `backend` actuator 健康后再启动 `frontend`。
- Redis、PostgreSQL、MinIO 保留在 `infra` profile，默认不启动；当前业务未使用它们。
- 上传文件使用命名卷持久化；MySQL 数据由本地实例持久化。
- AI 默认关闭（Mock 生成器），设置 `APP_AI_ENABLED=true` 与 `DEEPSEEK_API_KEY` 后启用真实 AI。

## 3. 容器与文件

### 3.1 后端镜像

`backend/Dockerfile`：

- 构建阶段：`maven:3.9-eclipse-temurin-21`，执行 `mvn -DskipTests package`。
- 运行阶段：`eclipse-temurin:21-jre-alpine`，复制可执行 jar 到 `/app/app.jar`。
- 环境：`SPRING_PROFILES_ACTIVE=mysql`、MySQL 连接变量、`JWT_SECRET`、`APP_AI_ENABLED`、`OFFERPILOT_UPLOAD_DIR=/app/data/uploads`。
- 健康检查：`wget -qO- http://localhost:8080/actuator/health | grep UP`。
- 数据卷：`upload-data:/app/data/uploads`。

### 3.2 前端镜像

`frontend/Dockerfile`：

- 构建阶段：`node:22-alpine`，`npm ci` + `npm run build`。
- 运行阶段：`nginx:1.27-alpine`，复制 `dist` 与 `nginx.conf`。

`frontend/nginx.conf`：

- `try_files $uri $uri/ /index.html` 支持 Vue Router history 模式。
- `/api/` 反代到 `backend:8080`。
- SSE 必需配置：`proxy_buffering off`、`proxy_cache off`、`proxy_read_timeout 120s`、`proxy_http_version 1.1`、`Connection ""`。
- 静态资源 `gzip on` 与长期缓存头。

### 3.3 Compose

`docker-compose.yml` 保留现有 `name: offerpilot`，调整为：

| 服务 | 说明 |
|------|------|
| `backend` | 由 `backend/Dockerfile` 构建，连接本地 MySQL（`host.docker.internal:3306`） |
| `frontend` | 由 `frontend/Dockerfile` 构建，依赖 backend healthy，暴露 `${OFFERPILOT_HTTP_PORT:-8088}:80` |
| `redis` / `postgres` / `minio` | `profiles: ["infra"]`，默认不启动 |

本地 MySQL 前置条件：MySQL 服务已启动，存在 `offerpilot` 数据库，`root/1234` 可登录（可通过 `.env` 覆盖）。

### 3.4 一键脚本与环境变量

- `scripts/docker-up.ps1`、`scripts/docker-up.sh`：检查 Docker daemon；若 `.env` 不存在则从 `.env.example` 生成并替换随机 `JWT_SECRET`；执行 `docker compose up -d --build`；等待健康并打印访问地址。
- `scripts/docker-down.ps1`、`scripts/docker-down.sh`：`docker compose down`；`-v` 参数用于清空数据卷。
- `.env.example`：补充 `OFFERPILOT_HTTP_PORT`、`JWT_SECRET`、`APP_AI_ENABLED`、`DEEPSEEK_API_KEY`、MySQL 变量。
- `README.md`：增加一键启动、访问地址、日志、数据重置、常见问题。

## 4. 20 并发压测

新增 `scripts/load_test.py`：

- 依赖：Python 3 + `requests`（当前环境已具备）。
- 参数：`--users 20`、`--base-url http://localhost:8088`、`--output docs/reports`。
- 每个虚拟用户独立执行完整链路：
  1. 注册唯一账号。
  2. 生成最小 DOCX 简历并上传。
  3. 获取岗位模板并创建面试。
  4. 开始面试。
  5. 提交首题回答，读取 SSE 流直到 `done`。
  6. 结束面试。
  7. 生成五维报告。
- 指标：各步骤耗时、总耗时、成功率、P50/P95/P99、吞吐（场/秒）、错误分布。
- 输出：
  - `docs/reports/load-test-20u.json`
  - `docs/reports/load-test-20u.md`（环境、并发配置、结果、瓶颈、调优建议）
- 压测默认 Mock AI，报告中明确“不含真实 LLM 延迟”。
- 若实测出现连接池排队，只记录并给出 Hikari 调优建议，不在 W6 改造业务范围。

## 5. 演示物料

- `docs/demo/3-minute-demo.md`：精确到点击与话术的 3 分钟演示流程，覆盖登录、选岗、上传简历、蓝图、追问/提示、报告、成长、管理员题库与额度。
- `docs/demo/product-intro.html`：单文件、内联 CSS、无外部依赖的产品介绍页，可直接双击打开。
- `docs/demo/resume-project.md`：STAR 结构项目描述，包含技术栈、个人职责、测试与压测数据、部署方式。
- 压测报告中的真实数字回填到简历描述，禁止编造指标。

## 6. 验收标准

1. 本地 MySQL 已启动；执行一键脚本或 `docker compose up -d --build` 后，backend/frontend 均 healthy，Flyway 迁移成功。
2. `http://localhost:8088` 可打开 SPA；管理员登录、创建面试、SSE 答题、生成报告正常。
3. Nginx 反代 SSE 不缓冲、不截断。
4. `python scripts/load_test.py --users 20` 成功完成，20 个虚拟用户全部成功（100%），生成 JSON 与 Markdown 报告。
5. 后端 48 个测试与前端构建继续通过。
6. 按 `3-minute-demo.md` 在容器环境 3 分钟内完整复现。

## 7. 错误处理与运维

- 启动脚本先检测 Docker daemon；未启动时给出“请先启动 Docker Desktop”提示。
- 启动脚本检测本机 3306 端口；本地 MySQL 未启动时给出明确提示。
- 宿主端口占用时提示修改 `OFFERPILOT_HTTP_PORT`。
- `.env` 缺失时自动生成，`JWT_SECRET` 使用随机值；生产部署必须替换。
- 本地 MySQL 连接失败或 Flyway 迁移失败时，提示查看 `docker compose logs backend` 与 MySQL 账号/库名配置。
- 数据重置：`scripts/docker-down.* -v` 或 `docker compose down -v`。
- 真实 AI 调用失败时沿用现有 Mock 回退逻辑，不阻塞面试创建。

## 8. 不做范围

- 云服务器、域名、HTTPS、ICP 备案。
- 支付、会员、Redis 分布式限流。
- pgvector / bge-m3 向量检索（保持关键词检索）。
- MinIO 文件存储迁移（继续使用本地卷）。
- 业务功能与额度规则变更。

## 9. 风险

- 部署依赖本机 MySQL 服务；MySQL 未启动或密码不符时容器会不健康，README 说明排查方式。
- 首次拉取镜像受网络影响，可能较慢；README 说明重试方式。
- 压测指标随机器配置波动；报告记录本机环境，结论仅对本环境负责。
- Nginx 与 SSE 的组合需实际浏览器验证，不能只依赖 curl。
- Docker Desktop 资源不足可能导致容器不健康；报告中记录资源与解决方式。
