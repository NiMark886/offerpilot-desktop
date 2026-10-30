# OfferPilot P0 Week 6 实施计划：一键部署、20 并发压测与演示物料

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 完成 OfferPilot P0 Week 6 收尾：Docker Compose 一键部署、20 并发完整链路压测、3 分钟演示物料与文档。

**Architecture:** 前端 Nginx 镜像同时托管 Vue SPA 并反向代理 `/api` 到后端；后端 Maven 多阶段构建为 JRE 镜像；Compose 默认只启动 `backend + frontend`，后端通过 `host.docker.internal` 连接本机 MySQL（`root/1234`，库 `offerpilot`）；Redis/PostgreSQL/MinIO 放入 `infra` profile。压测脚本用 Python 线程池模拟 20 个独立用户跑完整链路，输出 JSON 与 Markdown 报告。

**Tech Stack:** Java 21、Spring Boot 3.5、Maven、Vue3 + Vite、Nginx、Docker Compose、Python 3 + requests。

**分支:** `feat/p0-week6-deploy`（已从 `feat/p0-week5b-admin-quota` 创建）

**执行环境变更（2026-10-27）：** 本机 Docker Desktop 无法启动（WSL2/服务权限未就绪，当前会话无法提权）。W6 运行验收改为本地模式：Maven + 本地 MySQL（`root/1234`，库 `offerpilot`）+ Vite。Dockerfile、Compose 与脚本仍交付并做静态/配置校验；`docker build`、`docker compose up` 与 Nginx 运行时验证标记为环境阻塞，待 Docker 可用后补验。

---

## 文件结构

| 文件 | 责任 |
|------|------|
| `backend/Dockerfile` | 后端多阶段构建与运行镜像 |
| `backend/.dockerignore` | 排除构建产物与本地数据 |
| `frontend/Dockerfile` | 前端构建与 Nginx 运行镜像 |
| `frontend/.dockerignore` | 排除 node_modules 与 dist |
| `frontend/nginx.conf` | SPA history fallback、`/api` 反代、SSE 配置 |
| `docker-compose.yml` | MySQL/backend/frontend 编排与 infra profile |
| `.env.example` | Compose 与本地开发环境变量样例 |
| `scripts/docker-up.ps1` / `.sh` | 一键启动、生成 `.env`、等待健康 |
| `scripts/docker-down.ps1` / `.sh` | 停止服务，可选清空卷 |
| `scripts/load_test.py` | 20 并发完整链路压测与报告生成 |
| `docs/reports/load-test-20u.json` | 压测原始指标 |
| `docs/reports/load-test-20u.md` | 压测可读报告 |
| `docs/demo/3-minute-demo.md` | 3 分钟演示脚本 |
| `docs/demo/product-intro.html` | 单文件产品介绍页 |
| `docs/demo/resume-project.md` | STAR 简历项目描述（含实测数据） |
| `README.md` | 部署、压测、演示入口 |

---

## Task 1: 后端 Docker 镜像

**Files:**
- Create: `backend/Dockerfile`
- Create: `backend/.dockerignore`

- [x] **Step 1: 创建 `.dockerignore`**

Create `backend/.dockerignore`:
```text
target
data
.idea
*.iml
.git
```

- [x] **Step 2: 创建 `Dockerfile`**

Create `backend/Dockerfile`:
```dockerfile
# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S offerpilot && adduser -S offerpilot -G offerpilot \
    && mkdir -p /app/data/uploads \
    && chown -R offerpilot:offerpilot /app
COPY --from=build /workspace/target/*.jar /app/app.jar
USER offerpilot
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [ ] **Step 3: 构建镜像**（DOCKER_UNAVAILABLE：Docker daemon 不可用，待环境恢复后补验）

Run:
```bash
cd /e/work
docker build -t offerpilot-backend:test backend
```
Expected: 构建成功，最后输出 `naming to docker.io/library/offerpilot-backend:test`。

> 若 Docker daemon 不可用：跳过 Step 3–4，记录 `DOCKER_UNAVAILABLE`，继续 Step 5 提交 Dockerfile；运行验证待环境恢复后补做。

- [ ] **Step 4: 用 H2 启动镜像并验证健康**（DOCKER_UNAVAILABLE：待环境恢复后补验）

Run:
```bash
docker rm -f offerpilot-backend-test 2>/dev/null || true
docker run -d --name offerpilot-backend-test -p 18080:8080 \
  -e JWT_SECRET="$(openssl rand -base64 64 | tr -d '\r\n')" \
  offerpilot-backend:test
for i in $(seq 1 60); do
  if curl -sf http://localhost:18080/actuator/health >/dev/null; then break; fi
  sleep 2
done
curl -s http://localhost:18080/actuator/health
docker rm -f offerpilot-backend-test
```
Expected: 输出包含 `"status":"UP"`，且容器可正常删除。

- [x] **Step 5: 提交**

```bash
cd /e/work
git add backend/Dockerfile backend/.dockerignore
git commit -m "build(backend): add multi-stage Docker image"
```

---

## Task 2: 前端 Docker 镜像与 Nginx

**Files:**
- Create: `frontend/Dockerfile`
- Create: `frontend/.dockerignore`
- Create: `frontend/nginx.conf`

- [x] **Step 1: 创建 `.dockerignore`**

Create `frontend/.dockerignore`:
```text
node_modules
dist
.git
.idea
*.log
```

- [x] **Step 2: 创建 `nginx.conf`**

Create `frontend/nginx.conf`:
```nginx
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    gzip on;
    gzip_min_length 1024;
    gzip_types text/plain text/css application/json application/javascript application/xml image/svg+xml;

    location /api/ {
        proxy_pass http://backend:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header Connection "";
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 120s;
        proxy_send_timeout 120s;
    }

    location /assets/ {
        expires 30d;
        add_header Cache-Control "public, immutable";
        try_files $uri =404;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

- [x] **Step 3: 创建 `Dockerfile`**

Create `frontend/Dockerfile`:
```dockerfile
# syntax=docker/dockerfile:1
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=5 \
  CMD wget -qO- http://localhost/ >/dev/null || exit 1
```

- [ ] **Step 4: 构建镜像**（DOCKER_UNAVAILABLE：Docker daemon 不可用，待环境恢复后补验）

Run:
```bash
cd /e/work
docker build -t offerpilot-frontend:test frontend
```
Expected: 构建成功。

> 若 Docker daemon 不可用：跳过 Step 4–5，记录 `DOCKER_UNAVAILABLE`，继续 Step 6 提交前端镜像文件；运行验证待环境恢复后补做。

- [ ] **Step 5: 验证静态页面与 history fallback**（DOCKER_UNAVAILABLE：待环境恢复后补验）

Run:
```bash
docker rm -f offerpilot-frontend-test 2>/dev/null || true
docker run -d --name offerpilot-frontend-test -p 18081:80 offerpilot-frontend:test
sleep 3
curl -s http://localhost:18081/ | grep -q '<div id="app"></div>' && echo "ROOT_OK"
curl -s http://localhost:18081/history | grep -q '<div id="app"></div>' && echo "FALLBACK_OK"
docker rm -f offerpilot-frontend-test
```
Expected: 输出 `ROOT_OK` 与 `FALLBACK_OK`。

- [x] **Step 6: 提交**

```bash
cd /e/work
git add frontend/Dockerfile frontend/.dockerignore frontend/nginx.conf
git commit -m "build(frontend): add Nginx image with API proxy and SPA fallback"
```

---

## Task 3: Compose 编排与环境变量

**Files:**
- Modify: `docker-compose.yml`
- Modify: `.env.example`

- [x] **Step 1: 重写 `docker-compose.yml`**

Replace `docker-compose.yml` with:
```yaml
name: offerpilot

services:
  backend:
    build:
      context: ./backend
    container_name: offerpilot-backend
    environment:
      SPRING_PROFILES_ACTIVE: mysql
      MYSQL_HOST: ${DOCKER_MYSQL_HOST:-host.docker.internal}
      MYSQL_PORT: "3306"
      MYSQL_DATABASE: ${MYSQL_DATABASE:-offerpilot}
      MYSQL_USER: ${MYSQL_USER:-root}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:-1234}
      JWT_SECRET: ${JWT_SECRET:?JWT_SECRET is required}
      APP_AI_ENABLED: ${APP_AI_ENABLED:-false}
      DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY:-}
      OFFERPILOT_UPLOAD_DIR: /app/data/uploads
    extra_hosts:
      - "host.docker.internal:host-gateway"
    volumes:
      - upload-data:/app/data/uploads
    restart: unless-stopped

  frontend:
    build:
      context: ./frontend
    container_name: offerpilot-frontend
    ports:
      - "${OFFERPILOT_HTTP_PORT:-8088}:80"
    depends_on:
      backend:
        condition: service_healthy
    restart: unless-stopped

  redis:
    image: redis:7.4-alpine
    container_name: offerpilot-redis
    profiles: ["infra"]
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data

  postgres:
    image: pgvector/pgvector:pg16
    container_name: offerpilot-postgres
    profiles: ["infra"]
    environment:
      POSTGRES_DB: offerpilot
      POSTGRES_USER: offerpilot
      POSTGRES_PASSWORD: offerpilot
      TZ: Asia/Shanghai
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data

  minio:
    image: minio/minio:latest
    container_name: offerpilot-minio
    profiles: ["infra"]
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: offerpilot
      MINIO_ROOT_PASSWORD: offerpilot123
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio-data:/data

volumes:
  upload-data:
  redis-data:
  postgres-data:
  minio-data:
```

- [x] **Step 2: 更新 `.env.example`**

Replace `.env.example` with:
```dotenv
# Compose
OFFERPILOT_HTTP_PORT=8088

# Backend (local MySQL)
JWT_SECRET=replace-with-a-base64-encoded-64-byte-secret
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_DATABASE=offerpilot
MYSQL_USER=root
MYSQL_PASSWORD=1234
# Docker 容器内访问宿主 MySQL 的地址；Docker Desktop 默认 host.docker.internal
DOCKER_MYSQL_HOST=host.docker.internal

# AI providers (optional; default uses deterministic mock)
APP_AI_ENABLED=false
DEEPSEEK_API_KEY=
```

- [x] **Step 3: 校验 Compose 配置**

Run:
```bash
cd /e/work
docker compose --env-file .env.example config >/dev/null && echo "COMPOSE_OK"
```
Expected: 输出 `COMPOSE_OK`。

- [x] **Step 4: 校验默认服务与 infra profile**

Run:
```bash
cd /e/work
docker compose --env-file .env.example config --services
docker compose --env-file .env.example --profile infra config --services
```
Expected:
- 第一条输出 `backend`、`frontend`
- 第二条额外输出 `redis`、`postgres`、`minio`

- [x] **Step 5: 提交**

```bash
cd /e/work
git add docker-compose.yml .env.example
git commit -m "build(compose): orchestrate backend, frontend and mysql"
```

---

## Task 4: 一键启动与停止脚本

**Files:**
- Create: `scripts/docker-up.ps1`
- Create: `scripts/docker-up.sh`
- Create: `scripts/docker-down.ps1`
- Create: `scripts/docker-down.sh`

- [x] **Step 1: 创建 PowerShell 启动脚本**

Create `scripts/docker-up.ps1`:
```powershell
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "未找到 docker，请先安装 Docker Desktop"
    exit 1
}

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker daemon 未运行，请先启动 Docker Desktop"
    exit 1
}

$mysql = New-Object System.Net.Sockets.TcpClient
try {
    $mysql.Connect("127.0.0.1", 3306)
} catch {
    Write-Error "本地 MySQL 未启动或 3306 端口不可用，请先启动 MySQL 服务"
    exit 1
} finally {
    $mysql.Dispose()
}

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    $bytes = New-Object byte[] 64
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    $secret = [Convert]::ToBase64String($bytes)
    (Get-Content ".env") -replace '^JWT_SECRET=.*$', "JWT_SECRET=$secret" |
        Set-Content ".env" -Encoding ascii
    Write-Host "已生成 .env（含随机 JWT_SECRET）"
}

docker compose up -d --build
if ($LASTEXITCODE -ne 0) {
    Write-Error "docker compose 启动失败"
    exit 1
}

$port = (Select-String -Path ".env" -Pattern '^OFFERPILOT_HTTP_PORT=(.*)$').Matches[0].Groups[1].Value.Trim()
if (-not $port) { $port = "8088" }

Write-Host "等待后端健康检查..."
$backendHealthy = $false
for ($i = 0; $i -lt 60; $i++) {
    $status = docker inspect --format "{{.State.Health.Status}}" offerpilot-backend 2>$null
    if ($status -eq "healthy") {
        $backendHealthy = $true
        break
    }
    Start-Sleep -Seconds 2
}
if (-not $backendHealthy) {
    Write-Error "后端未在 120 秒内健康，请执行 docker compose logs backend 查看日志"
    exit 1
}

Write-Host "等待前端页面..."
for ($i = 0; $i -lt 30; $i++) {
    try {
        Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:$port/" -TimeoutSec 3 | Out-Null
        Write-Host "OfferPilot 已启动：http://localhost:$port"
        exit 0
    } catch {
        Start-Sleep -Seconds 2
    }
}

Write-Error "前端页面未在 60 秒内可访问，请执行 docker compose logs frontend 查看日志"
exit 1
```

- [x] **Step 2: 创建 Bash 启动脚本**

Create `scripts/docker-up.sh`:
```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if ! command -v docker >/dev/null 2>&1; then
  echo "未找到 docker，请先安装 Docker Desktop" >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon 未运行，请先启动 Docker Desktop" >&2
  exit 1
fi

if ! (echo > /dev/tcp/127.0.0.1/3306) >/dev/null 2>&1; then
  echo "本地 MySQL 未启动或 3306 端口不可用，请先启动 MySQL 服务" >&2
  exit 1
fi

if [ ! -f .env ]; then
  cp .env.example .env
  if command -v openssl >/dev/null 2>&1; then
    secret="$(openssl rand -base64 64 | tr -d '\r\n')"
  else
    secret="$(head -c 64 /dev/urandom | base64 | tr -d '\r\n')"
  fi
  sed -i.bak "s|^JWT_SECRET=.*|JWT_SECRET=${secret}|" .env
  rm -f .env.bak
  echo "已生成 .env（含随机 JWT_SECRET）"
fi

docker compose up -d --build

port="$(grep -E '^OFFERPILOT_HTTP_PORT=' .env | tail -1 | cut -d= -f2 | tr -d '\r')"
port="${port:-8088}"

echo "等待后端健康检查..."
for _ in $(seq 1 60); do
  status="$(docker inspect --format '{{.State.Health.Status}}' offerpilot-backend 2>/dev/null || true)"
  if [ "$status" = "healthy" ]; then
    if curl -sf "http://localhost:${port}/" >/dev/null; then
      echo "OfferPilot 已启动：http://localhost:${port}"
      exit 0
    fi
  fi
  sleep 2
done

echo "服务未在 120 秒内就绪，请执行 docker compose logs backend frontend 查看日志" >&2
exit 1
```

- [x] **Step 3: 创建停止脚本**

Create `scripts/docker-down.ps1`:
```powershell
param([switch]$Volumes)

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if ($Volumes) {
    docker compose down -v
} else {
    docker compose down
}
```

Create `scripts/docker-down.sh`:
```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [ "${1:-}" = "-v" ] || [ "${1:-}" = "--volumes" ]; then
  docker compose down -v
else
  docker compose down
fi
```

- [x] **Step 4: 语法校验**

Run:
```bash
cd /e/work
bash -n scripts/docker-up.sh scripts/docker-down.sh && echo "BASH_OK"
powershell.exe -NoProfile -Command "\$errors = \$null; [System.Management.Automation.Language.Parser]::ParseFile('E:\work\scripts\docker-up.ps1', [ref]\$null, [ref]\$errors) > \$null; if (\$errors) { \$errors; exit 1 } else { 'PS_UP_OK' }"
powershell.exe -NoProfile -Command "\$errors = \$null; [System.Management.Automation.Language.Parser]::ParseFile('E:\work\scripts\docker-down.ps1', [ref]\$null, [ref]\$errors) > \$null; if (\$errors) { \$errors; exit 1 } else { 'PS_DOWN_OK' }"
```
Expected: `BASH_OK`、`PS_UP_OK`、`PS_DOWN_OK`。

- [x] **Step 5: 提交**

```bash
cd /e/work
git add scripts/docker-up.ps1 scripts/docker-up.sh scripts/docker-down.ps1 scripts/docker-down.sh
git commit -m "build(scripts): add one-click docker up and down scripts"
```

---

## Task 5: 20 并发压测脚本

**Files:**
- Create: `scripts/load_test.py`

- [x] **Step 1: 创建压测脚本**

Create `scripts/load_test.py`:
```python
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""OfferPilot 完整链路压测。

每个虚拟用户独立执行：
注册 -> 上传简历 -> 创建面试 -> 开始 -> SSE 答题 -> 结束 -> 生成报告

默认针对 Mock AI，报告中不包含真实 LLM 延迟。
"""

import argparse
import concurrent.futures
import io
import json
import os
import platform
import statistics
import threading
import time
import uuid
import zipfile
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path

import requests

RESUME_LINES = [
    "压测用户",
    "Java 后端开发工程师，3 年经验",
    "技能：Java, Spring Boot, MySQL, Redis, 微服务",
    "项目：OfferPilot AI 面试平台，负责后端架构与 RAG 检索。",
]


def build_resume_docx(index: int) -> bytes:
    content_types = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
        '<Default Extension="xml" ContentType="application/xml"/>'
        '<Override PartName="/word/document.xml" '
        'ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
        '</Types>'
    )
    rels = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        '<Relationship Id="rId1" '
        'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" '
        'Target="word/document.xml"/></Relationships>'
    )
    lines = [f"{RESUME_LINES[0]} {index}"] + RESUME_LINES[1:]
    paragraphs = "".join(
        f"<w:p><w:r><w:t>{line}</w:t></w:r></w:p>" for line in lines
    )
    document = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
        f"<w:body>{paragraphs}</w:body></w:document>"
    )
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("[Content_Types].xml", content_types)
        archive.writestr("_rels/.rels", rels)
        archive.writestr("word/document.xml", document)
    return buffer.getvalue()


@dataclass
class StepResult:
    name: str
    seconds: float
    ok: bool
    error: str = ""


@dataclass
class UserResult:
    user_index: int
    seconds: float
    ok: bool
    error: str = ""
    steps: list[StepResult] = field(default_factory=list)


def api_data(response: requests.Response):
    try:
        body = response.json()
    except ValueError as exc:
        raise RuntimeError(
            f"non-json response status={response.status_code} body={response.text[:200]}"
        ) from exc
    if response.status_code >= 400 or body.get("code") != 0:
        raise RuntimeError(
            f"status={response.status_code} code={body.get('code')} message={body.get('message')}"
        )
    return body["data"]


class VirtualUser:
    def __init__(self, index: int, base_url: str, run_id: str):
        self.index = index
        self.base_url = base_url.rstrip("/")
        self.run_id = run_id
        self.session = requests.Session()
        self.steps: list[StepResult] = []

    def run(self) -> UserResult:
        started = time.perf_counter()
        try:
            token = self.step("register", self._register)
            self.session.headers.update({"Authorization": f"Bearer {token}"})
            resume_id = self.step("resume_upload", self._upload_resume)
            job_id = self.step("job_templates", self._job_templates)
            session_id = self.step("interview_create", lambda: self._create_interview(job_id, resume_id))
            question_id = self.step("interview_start", lambda: self._start_interview(session_id))
            self.step("answer_sse", lambda: self._answer(session_id, question_id))
            self.step("interview_finish", lambda: self._finish(session_id))
            self.step("report_generate", lambda: self._report(session_id))
            return UserResult(self.index, time.perf_counter() - started, True, steps=self.steps)
        except Exception as exc:  # noqa: BLE001 - 压测需要记录任意失败
            return UserResult(
                self.index, time.perf_counter() - started, False, str(exc), self.steps
            )

    def step(self, name: str, action):
        started = time.perf_counter()
        try:
            value = action()
            self.steps.append(StepResult(name, time.perf_counter() - started, True))
            return value
        except Exception as exc:  # noqa: BLE001
            self.steps.append(StepResult(name, time.perf_counter() - started, False, str(exc)))
            raise

    def _register(self) -> str:
        email = f"load-{self.run_id}-{self.index}@example.com"
        response = self.session.post(
            f"{self.base_url}/api/auth/register",
            json={"email": email, "password": "password123", "nickname": f"LoadUser{self.index}"},
            timeout=30,
        )
        return api_data(response)["accessToken"]

    def _upload_resume(self) -> int:
        files = {
            "file": (
                f"load-{self.index}.docx",
                build_resume_docx(self.index),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            )
        }
        response = self.session.post(
            f"{self.base_url}/api/resumes", files=files, timeout=60
        )
        return api_data(response)["id"]

    def _job_templates(self) -> int:
        response = self.session.get(f"{self.base_url}/api/job-templates", timeout=30)
        return api_data(response)[0]["id"]

    def _create_interview(self, job_id: int, resume_id: int) -> int:
        response = self.session.post(
            f"{self.base_url}/api/interviews",
            json={
                "jobTemplateId": job_id,
                "resumeId": resume_id,
                "difficulty": "STANDARD",
                "durationMinutes": 30,
                "persona": "STANDARD",
                "mode": "INTERVIEW",
            },
            timeout=120,
        )
        return api_data(response)["id"]

    def _start_interview(self, session_id: int) -> int:
        response = self.session.post(
            f"{self.base_url}/api/interviews/{session_id}/start", timeout=30
        )
        return api_data(response)["currentQuestionId"]

    def _answer(self, session_id: int, question_id: int) -> None:
        with self.session.post(
            f"{self.base_url}/api/interviews/{session_id}/answers",
            json={
                "questionId": question_id,
                "content": "HashMap 底层是数组、链表和红黑树，扩容时容量翻倍并重新分布元素。",
            },
            headers={"Accept": "text/event-stream"},
            stream=True,
            timeout=120,
        ) as response:
            if response.status_code >= 400:
                raise RuntimeError(f"SSE status={response.status_code}")
            chunks: list[str] = []
            for chunk in response.iter_content(chunk_size=1024, decode_unicode=True):
                if chunk:
                    chunks.append(chunk if isinstance(chunk, str) else chunk.decode("utf-8", "ignore"))
            body = "".join(chunks)
            if "event:done" not in body.replace(" ", ""):
                raise RuntimeError("SSE stream did not emit done event")

    def _finish(self, session_id: int) -> None:
        response = self.session.post(
            f"{self.base_url}/api/interviews/{session_id}/finish", timeout=30
        )
        api_data(response)

    def _report(self, session_id: int) -> None:
        response = self.session.post(
            f"{self.base_url}/api/interviews/{session_id}/report", timeout=120
        )
        api_data(response)


def percentile(values: list[float], ratio: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, int(round(ratio * (len(ordered) - 1))))
    return round(ordered[index], 3)


def summarize(results: list[UserResult], wall_seconds: float, base_url: str) -> dict:
    step_names = sorted({step.name for result in results for step in result.steps})
    steps: dict[str, dict] = {}
    for name in step_names:
        items = [step for result in results for step in result.steps if step.name == name]
        latencies = [step.seconds for step in items]
        steps[name] = {
            "count": len(items),
            "success": sum(1 for step in items if step.ok),
            "p50Seconds": round(statistics.median(latencies), 3) if latencies else 0.0,
            "p95Seconds": percentile(latencies, 0.95),
            "p99Seconds": percentile(latencies, 0.99),
        }
    success_users = sum(1 for result in results if result.ok)
    users = len(results)
    return {
        "generatedAt": datetime.now().astimezone().isoformat(timespec="seconds"),
        "baseUrl": base_url,
        "users": users,
        "wallSeconds": round(wall_seconds, 3),
        "successUsers": success_users,
        "failedUsers": users - success_users,
        "successRate": round(success_users * 100.0 / users, 2) if users else 0.0,
        "throughputUsersPerSecond": round(success_users / wall_seconds, 3) if wall_seconds else 0.0,
        "environment": {
            "platform": platform.platform(),
            "python": platform.python_version(),
            "cpuCount": os.cpu_count(),
        },
        "steps": steps,
        "errors": [
            {"user": result.user_index, "error": result.error}
            for result in results
            if not result.ok
        ],
    }


def write_markdown(report: dict, path: Path, label: str) -> None:
    lines = [
        f"# OfferPilot 压测报告（{label}）",
        "",
        f"- 生成时间：{report['generatedAt']}",
        f"- 目标地址：`{report['baseUrl']}`",
        f"- 并发用户：{report['users']}",
        f"- 总耗时：{report['wallSeconds']} 秒",
        f"- 成功/失败：{report['successUsers']} / {report['failedUsers']}",
        f"- 成功率：{report['successRate']}%",
        f"- 吞吐：{report['throughputUsersPerSecond']} 用户/秒",
        f"- 环境：{report['environment']['platform']}，Python {report['environment']['python']}，"
        f"CPU {report['environment']['cpuCount']} 核",
        "",
        "> 本压测默认 `APP_AI_ENABLED=false`，使用确定性 Mock 蓝图与评估，"
        "结果不包含真实 LLM 延迟。",
        "",
        "## 分步骤延迟",
        "",
        "| 步骤 | 次数 | 成功 | P50 (s) | P95 (s) | P99 (s) |",
        "|------|------|------|---------|---------|---------|",
    ]
    for name, step in report["steps"].items():
        lines.append(
            f"| {name} | {step['count']} | {step['success']} | "
            f"{step['p50Seconds']} | {step['p95Seconds']} | {step['p99Seconds']} |"
        )
    lines.extend(["", "## 错误", ""])
    if report["errors"]:
        for error in report["errors"]:
            lines.append(f"- 用户 {error['user']}：{error['error']}")
    else:
        lines.append("- 无")
    lines.extend(["", "## 结论与建议", ""])
    if report["failedUsers"] == 0:
        lines.append("- 完整链路（注册、简历解析、蓝图生成、SSE 答题、报告）在本次并发下全部成功。")
    else:
        lines.append(f"- 本次有 {report['failedUsers']} 个用户失败，错误明细见上表。")
    lines.extend(
        [
            "- 若后续启用真实 LLM，答题与报告阶段延迟会显著上升，应单独压测 AI 调用链路。",
            "- 生产环境建议继续观察 MySQL 连接池、SSE 连接数与 Nginx `proxy_read_timeout`。",
            "",
        ]
    )
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="OfferPilot load test")
    parser.add_argument("--users", type=int, default=20)
    parser.add_argument("--base-url", default="http://localhost:8088")
    parser.add_argument("--output", default="docs/reports")
    parser.add_argument("--run-id", default=None)
    args = parser.parse_args()

    run_id = args.run_id or uuid.uuid4().hex[:10]
    users = [VirtualUser(i, args.base_url, run_id) for i in range(1, args.users + 1)]
    barrier = threading.Barrier(len(users))

    def run_one(user: VirtualUser) -> UserResult:
        barrier.wait()
        return user.run()

    started = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=len(users)) as pool:
        results = list(pool.map(run_one, users))
    wall_seconds = time.perf_counter() - started

    report = summarize(results, wall_seconds, args.base_url)
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)
    label = f"{args.users} 并发"
    json_path = output_dir / f"load-test-{args.users}u.json"
    md_path = output_dir / f"load-test-{args.users}u.md"
    json_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    write_markdown(report, md_path, label)

    print(json.dumps({
        "users": report["users"],
        "successRate": report["successRate"],
        "wallSeconds": report["wallSeconds"],
        "throughputUsersPerSecond": report["throughputUsersPerSecond"],
        "json": str(json_path),
        "markdown": str(md_path),
    }, ensure_ascii=False, indent=2))
    return 0 if report["failedUsers"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
```

- [x] **Step 2: 语法检查**

Run:
```bash
cd /e/work
python -m py_compile scripts/load_test.py && echo "PY_OK"
```
Expected: `PY_OK`。

- [x] **Step 3: 对本地后端做 2 用户冒烟**

前置：`http://localhost:8080/actuator/health` 返回 UP（本地 `mvn spring-boot:run` 或已有实例）。

Run:
```bash
cd /e/work
mkdir -p target/loadtest-smoke
python scripts/load_test.py --users 2 --base-url http://localhost:8080 \
  --output target/loadtest-smoke
```
Expected: JSON 输出 `"successRate": 100.0`，退出码 0，生成 `load-test-2u.json` 与 `load-test-2u.md`。

- [x] **Step 4: 提交**

```bash
cd /e/work
git add scripts/load_test.py
git commit -m "test(load): add 20-user end-to-end load test script"
```

---

## Task 6: 演示脚本与产品介绍页

**Files:**
- Create: `docs/demo/3-minute-demo.md`
- Create: `docs/demo/product-intro.html`

- [x] **Step 1: 创建 3 分钟演示脚本**

Create `docs/demo/3-minute-demo.md`:
```markdown
# OfferPilot 3 分钟演示脚本

> 前置：执行 `scripts/docker-up.ps1` 或 `bash scripts/docker-up.sh`，打开 `http://localhost:8088`。
> 演示账号：`admin@offerpilot.local / admin123`（管理员）；普通用户可现场注册。

| 时间 | 动作 | 话术 |
|------|------|------|
| 0:00–0:20 | 打开首页，展示岗位卡片 | “OfferPilot 是面向 Java/Java+AI 求职者的 AI 模拟面试教练，先选一个目标岗位。” |
| 0:20–0:40 | 点击“Java 后端开发（校招）→ 开始面试” | “创建面试时可以选择模式、难度、时长和面试官风格。” |
| 0:40–1:10 | 上传一份 PDF/DOCX 简历，展示解析出的技能与项目 | “简历会被解析成结构化画像，用于生成个性化题目，而不是通用题库。” |
| 1:10–1:30 | 点击“生成面试蓝图”，展示题目列表 | “AI 结合岗位 JD、简历和题库生成 6–10 道题，包含基础、项目和场景题。” |
| 1:30–2:10 | 进入面试间，回答第一题，展示流式回复与追问 | “回答会以 SSE 流式返回；面试官会根据回答继续追问，也可以请求提示或跳过。” |
| 2:10–2:35 | 结束面试，打开五维报告与成长页 | “报告从准确性、深度、项目表现、表达逻辑、岗位匹配度五个维度评分，并给出缺失要点。” |
| 2:35–3:00 | 用管理员登录，进入“题库管理”，展示搜索与新增 | “管理员可以维护题库；普通用户每天 3 场额度，管理员不限。” |

## 备用话术

- 如果使用 Mock AI：“当前为演示模式，使用确定性 Mock 生成器，保证无网络也能完整走通；配置 DeepSeek Key 后切换真实 AI。”
- 如果时间不足：跳过成长页，直接展示报告五维分数与薄弱点推荐。
- 如果现场网络较差：提前执行 `scripts/docker-up`，所有服务本地运行，不依赖外网。
```

- [x] **Step 2: 创建产品介绍页**

Create `docs/demo/product-intro.html`:
```html
<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>OfferPilot · AI 模拟面试教练</title>
  <style>
    :root { color-scheme: light; --navy: #17375e; --blue: #2e75b6; --muted: #6b7a8c; --bg: #f5f8fc; }
    * { box-sizing: border-box; }
    body { margin: 0; font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif; color: var(--navy); background: var(--bg); }
    .wrap { max-width: 1080px; margin: 0 auto; padding: 48px 24px 72px; }
    .hero { display: grid; grid-template-columns: 1.2fr 0.8fr; gap: 40px; align-items: center; padding: 48px; border-radius: 24px; background: linear-gradient(135deg, #17375e, #2e75b6); color: #fff; }
    .eyebrow { margin: 0 0 12px; font-size: 12px; letter-spacing: 0.18em; opacity: 0.8; }
    h1 { margin: 0 0 16px; font-size: clamp(32px, 4vw, 48px); line-height: 1.15; }
    .hero p { margin: 0; color: rgba(255,255,255,0.86); line-height: 1.8; }
    .badge { display: inline-block; margin-top: 20px; padding: 8px 14px; border: 1px solid rgba(255,255,255,0.4); border-radius: 999px; font-size: 13px; }
    .mock { padding: 20px; border-radius: 18px; background: rgba(255,255,255,0.12); border: 1px solid rgba(255,255,255,0.24); }
    .mock-row { height: 12px; margin: 10px 0; border-radius: 6px; background: rgba(255,255,255,0.28); }
    .mock-row.short { width: 62%; }
    .mock-row.blue { background: rgba(255,255,255,0.65); }
    section { margin-top: 48px; }
    h2 { font-size: 24px; margin: 0 0 20px; }
    .grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18px; }
    .card { padding: 22px; border-radius: 16px; background: #fff; border: 1px solid #e4ecf5; box-shadow: 0 8px 24px rgba(23,55,94,0.05); }
    .card h3 { margin: 0 0 10px; font-size: 17px; }
    .card p { margin: 0; color: var(--muted); font-size: 14px; line-height: 1.7; }
    .flow { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; counter-reset: step; }
    .flow div { padding: 18px 14px; border-radius: 14px; background: #fff; border: 1px solid #e4ecf5; font-size: 14px; text-align: center; }
    .flow div::before { counter-increment: step; content: counter(step); display: block; width: 28px; height: 28px; margin: 0 auto 10px; line-height: 28px; border-radius: 50%; background: var(--blue); color: #fff; font-weight: 700; }
    .tech { display: flex; flex-wrap: wrap; gap: 10px; }
    .tech span { padding: 8px 14px; border-radius: 999px; background: #fff; border: 1px solid #d8e5f2; color: var(--blue); font-size: 13px; }
    footer { margin-top: 56px; color: var(--muted); font-size: 13px; text-align: center; }
    @media (max-width: 820px) { .hero { grid-template-columns: 1fr; padding: 32px; } .grid { grid-template-columns: 1fr; } .flow { grid-template-columns: 1fr 1fr; } }
  </style>
</head>
<body>
  <main class="wrap">
    <section class="hero">
      <div>
        <p class="eyebrow">OFFERPILOT · AI INTERVIEW COACH</p>
        <h1>把一次真实的技术面试，装进浏览器。</h1>
        <p>面向 Java 后端与 Java+AI 求职者的垂直 AI 模拟面试教练。选岗位、传简历、多轮追问、五维评分、薄弱点复练，一场面试完整闭环。</p>
        <span class="badge">P0 已交付 · 本地 Docker 一键启动</span>
      </div>
      <div class="mock" aria-hidden="true">
        <div class="mock-row blue"></div>
        <div class="mock-row"></div>
        <div class="mock-row short"></div>
        <div class="mock-row"></div>
        <div class="mock-row blue"></div>
        <div class="mock-row short"></div>
      </div>
    </section>

    <section>
      <h2>核心能力</h2>
      <div class="grid">
        <article class="card"><h3>个性化蓝图</h3><p>结合岗位 JD、简历技能与题库生成 6–10 道题，覆盖基础、项目与场景追问。</p></article>
        <article class="card"><h3>多轮追问</h3><p>回答后实时评估并追问，支持提示与跳过，SSE 流式输出模拟真实对话节奏。</p></article>
        <article class="card"><h3>五维报告</h3><p>技术准确性、技术深度、项目表现、表达逻辑、岗位匹配度评分，逐题复盘缺失要点。</p></article>
        <article class="card"><h3>成长记录</h3><p>历史场次、平均分、最高分、分数趋势与五维均值，薄弱点推荐可练题目。</p></article>
        <article class="card"><h3>练习模式</h3><p>每题回答后立即返回正确性、得分、点评与缺失要点，适合高频刷题。</p></article>
        <article class="card"><h3>运营后台</h3><p>管理员维护题库、搜索与上下架；普通用户每日 3 场额度，管理员不限。</p></article>
      </div>
    </section>

    <section>
      <h2>使用流程</h2>
      <div class="flow">
        <div>选择岗位</div>
        <div>上传简历</div>
        <div>生成蓝图</div>
        <div>多轮面试</div>
        <div>报告与复练</div>
      </div>
    </section>

    <section>
      <h2>技术栈</h2>
      <div class="tech">
        <span>Java 21</span><span>Spring Boot 3.5</span><span>Spring AI</span>
        <span>MyBatis-Plus</span><span>Flyway</span><span>MySQL</span>
        <span>Vue 3</span><span>TypeScript</span><span>Element Plus</span>
        <span>Docker Compose</span><span>Nginx</span><span>DeepSeek</span>
      </div>
    </section>

    <footer>OfferPilot · 本地演示版 · 数据与简历仅保存在本机 Docker 卷中</footer>
  </main>
</body>
</html>
```

- [x] **Step 3: 验证产品介绍页**

Run:
```bash
cd /e/work
grep -q "OfferPilot" docs/demo/product-intro.html && echo "INTRO_HTML_OK"
python - <<'PY'
from html.parser import HTMLParser
from pathlib import Path
class Parser(HTMLParser):
    pass
Parser().feed(Path("docs/demo/product-intro.html").read_text(encoding="utf-8"))
print("HTML_PARSE_OK")
PY
```
Expected: `INTRO_HTML_OK`、`HTML_PARSE_OK`。

- [x] **Step 4: 提交**

```bash
cd /e/work
git add docs/demo/3-minute-demo.md docs/demo/product-intro.html
git commit -m "docs(demo): add 3-minute demo script and product intro page"
```

---

## Task 7: 本地全栈联调、浏览器验收与 20 并发压测

> Docker daemon 不可用，本节改用本地运行：Maven + 本地 MySQL + Vite。Nginx/Docker 运行时验证延期。

**Files:**
- Create: `docs/reports/load-test-20u.json`
- Create: `docs/reports/load-test-20u.md`

- [x] **Step 1: 确认本地 MySQL 与数据库**

Run:
```bash
mysql -uroot -p1234 -h127.0.0.1 -e "CREATE DATABASE IF NOT EXISTS offerpilot CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; SELECT 1;" && echo "MYSQL_OK"
```
Expected: `MYSQL_OK`。

- [x] **Step 2: 以 MySQL profile 重启后端**

停止旧的 8080 实例（仅限本项目开发进程），再启动：
```bash
PID=$(netstat -ano | grep ":8080 .*LISTEN" | awk '{print $5}' | head -1)
if [ -n "$PID" ]; then taskkill //F //PID "$PID" || true; fi
sleep 2
cd /e/work/backend
JWT_SECRET="$(openssl rand -base64 64 | tr -d '\r\n')" \
SPRING_PROFILES_ACTIVE=mysql \
MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 MYSQL_DATABASE=offerpilot \
MYSQL_USER=root MYSQL_PASSWORD=1234 \
nohup mvn spring-boot:run > /tmp/offerpilot-mysql.log 2>&1 &
for i in $(seq 1 60); do
  if curl -sf http://localhost:8080/actuator/health >/dev/null; then echo "BACKEND_UP"; break; fi
  sleep 2
done
curl -s http://localhost:8080/actuator/health
```
Expected: 输出包含 `"status":"UP"`。

- [x] **Step 3: 确认 Flyway V9 已应用到本地 MySQL**

Run:
```bash
mysql -uroot -p1234 -h127.0.0.1 offerpilot -e "SELECT version,description,success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 2;"
```
Expected: 输出包含 `9 admin and quota 1`。

- [x] **Step 4: 确认前端与 Vite 代理**

若 5173 未运行：
```bash
cd /e/work/frontend
nohup npm run dev -- --host 127.0.0.1 > /tmp/offerpilot-vite.log 2>&1 &
sleep 5
```
Run:
```bash
curl -s -o /dev/null -w "frontend=%{http_code}\n" http://127.0.0.1:5173/
curl -s -o /dev/null -w "api_via_vite=%{http_code}\n" http://127.0.0.1:5173/api/job-templates
```
Expected: 两个状态码均为 `200`。

- [x] **Step 5: 浏览器验收**（API 级验收已完成；本机无 Playwright，浏览器视觉验收待人工）

打开 `http://127.0.0.1:5173`，完成：

1. 管理员登录：`admin@offerpilot.local / admin123`。
2. 首页出现“题库管理”，进入后分页、搜索、新增、编辑、删除各一次。
3. 退出登录，注册一个新普通用户。
4. 上传任意 DOCX/PDF 简历（或使用演示用简历），创建面试。
5. 进入面试间回答一题，确认 SSE 流式输出与追问正常。
6. 结束面试，生成五维报告。
7. 返回首页，打开创建面试弹窗，确认“今日剩余 X 场”显示正常。

Expected: 上述 7 步全部成功，无控制台报错。

- [x] **Step 6: 1 用户冒烟压测**

Run:
```bash
cd /e/work
mkdir -p target/loadtest-local-smoke
python scripts/load_test.py --users 1 --base-url http://localhost:8080 \
  --output target/loadtest-local-smoke
```
Expected: `"successRate": 100.0`。

- [x] **Step 7: 执行 20 并发压测**

Run:
```bash
cd /e/work
python scripts/load_test.py --users 20 --base-url http://localhost:8080 --output docs/reports
```
Expected: 退出码 0，`"successRate": 100.0`，生成 `docs/reports/load-test-20u.json` 与 `.md`。

- [x] **Step 8: 检查报告内容**

Run:
```bash
cd /e/work
python - <<'PY'
import json
report = json.load(open("docs/reports/load-test-20u.json", encoding="utf-8"))
assert report["users"] == 20
assert report["successUsers"] == 20
assert report["steps"]["answer_sse"]["p95Seconds"] > 0
print("REPORT_OK", report["successRate"], report["wallSeconds"], report["steps"]["answer_sse"])
PY
grep -q "不包含真实 LLM 延迟" docs/reports/load-test-20u.md && echo "NOTE_OK"
```
Expected: `REPORT_OK 100.0 ...` 与 `NOTE_OK`。

- [x] **Step 9: 提交压测报告**

```bash
cd /e/work
git add docs/reports/load-test-20u.json docs/reports/load-test-20u.md
git commit -m "test(load): add 20-concurrency end-to-end report"
```

---

## Task 8: 简历项目描述与 README

**Files:**
- Create: `docs/demo/resume-project.md`
- Modify: `README.md`

- [x] **Step 1: 从实测 JSON 生成简历项目描述**

Run:
```bash
cd /e/work
python - <<'PY'
import json
from pathlib import Path

m = json.loads(Path("docs/reports/load-test-20u.json").read_text(encoding="utf-8"))
answer_p95 = m["steps"]["answer_sse"]["p95Seconds"]
create_p95 = m["steps"]["interview_create"]["p95Seconds"]
content = f"""# 简历项目描述：OfferPilot AI 模拟面试教练

## 项目简介

面向 Java 后端 / Java+AI 求职者的垂直 AI 模拟面试平台，覆盖“选岗位 → 传简历 → 生成面试蓝图 → 多轮追问 → 五维评分报告 → 薄弱点复练”的完整闭环。

## 技术栈

Java 21、Spring Boot 3.5、Spring AI、MyBatis-Plus、Flyway、MySQL、Vue 3、TypeScript、Element Plus、Docker Compose、Nginx。

## 个人职责与亮点（STAR）

- **Situation：** 求职者缺少低成本、可重复的真实技术面试练习，通用 AI 对话无法稳定覆盖岗位匹配、追问与结构化评估。
- **Task：** 独立完成从需求、后端、前端到部署的 MVP，支撑一场 6–10 题的完整模拟面试与五维报告。
- **Action：**
  - 设计岗位 JD + 简历画像 + 题库标签的蓝图生成流程，支持基础、项目、场景题与动态追问。
  - 实现 SSE 流式面试对话、提示/跳过/断点恢复与练习模式逐题评估。
  - 设计五维评分（准确性、深度、项目表现、表达逻辑、岗位匹配）与薄弱点推荐。
  - 实现管理员题库 CRUD 与普通用户每日 3 场额度控制。
  - 使用 Docker Compose + Nginx 完成一键部署，Nginx 同时托管 SPA 与反代 API。
- **Result：**
  - 后端 48 个自动化测试全部通过，前端构建通过。
  - 20 并发完整链路压测成功率 {m['successRate']:.0f}%，总耗时 {m['wallSeconds']:.1f}s，吞吐 {m['throughputUsersPerSecond']:.2f} 用户/秒。
  - 创建面试 P95 {create_p95:.2f}s，SSE 答题 P95 {answer_p95:.2f}s（Mock AI 环境）。
  - 支持本地一条命令启动完整栈，演示流程 3 分钟内可复现。

## 可直接粘贴到简历的版本

- 独立开发 AI 模拟面试平台，打通岗位/简历解析、面试蓝图、SSE 多轮追问、五维报告与薄弱点推荐闭环。
- 基于 Spring Boot 3.5 + Spring AI + MyBatis-Plus + MySQL 构建后端，Vue3 + TypeScript 构建前端。
- 实现管理员题库后台与每日额度；Docker Compose + Nginx 一键部署，20 并发完整链路压测成功率 {m['successRate']:.0f}%。
"""
Path("docs/demo/resume-project.md").write_text(content, encoding="utf-8")
print("RESUME_OK", m["successRate"], m["wallSeconds"])
PY
```
Expected: `RESUME_OK 100.0 ...`。

- [x] **Step 2: 更新 README**

在 `README.md` 的“当前进度”改为：
```markdown
当前进度：P0 已闭环（Week 6 完成：Docker Compose 一键部署、20 并发压测、演示物料）。

下一步：P1（向量检索、真实 AI 深度评测、支付与增长）。
```

在“快速开始”之前加入：
```markdown
## 本地运行（当前已验证）

前置条件：本机 MySQL 已启动，存在 `offerpilot` 数据库，账号默认 `root/1234`。

后端：

```bash
cd backend
SPRING_PROFILES_ACTIVE=mysql MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 \
MYSQL_DATABASE=offerpilot MYSQL_USER=root MYSQL_PASSWORD=1234 \
mvn spring-boot:run
```

前端：

```bash
cd frontend
npm run dev
```

访问 http://localhost:5173。

## Docker 一键启动（需 Docker Desktop/WSL2）

Windows PowerShell：
```powershell
.\scripts\docker-up.ps1
```

Git Bash / macOS / Linux：
```bash
bash scripts/docker-up.sh
```

访问 http://localhost:8088。首次启动会自动生成 `.env`（含随机 JWT_SECRET）并构建镜像。

前置条件：本机 MySQL 已启动，存在 `offerpilot` 数据库，`.env` 中默认使用 `root/1234`。如本机账号密码不同，请修改 `.env` 的 `MYSQL_USER`、`MYSQL_PASSWORD`、`MYSQL_DATABASE`。容器内通过 `host.docker.internal:3306` 访问宿主 MySQL。

> 本次 W6 验收因本机 Docker Desktop/WSL2 未就绪，使用“本地运行”路径完成；Docker 容器启动验证待环境恢复后补做。

常用命令：

```bash
docker compose logs -f backend   # 后端日志
docker compose ps                # 服务状态
bash scripts/docker-down.sh      # 停止
bash scripts/docker-down.sh -v   # 停止并清空上传文件
```

可选基础设施（当前业务未使用）：

```bash
docker compose --profile infra up -d
```
```

在“测试”之后加入：
```markdown
## 压测

```bash
python scripts/load_test.py --users 20 --base-url http://localhost:8080
```

若使用 Docker 栈，将 `--base-url` 改为 `http://localhost:8088`。

报告输出到 `docs/reports/load-test-20u.json` 与 `docs/reports/load-test-20u.md`。默认 Mock AI，不产生 DeepSeek 费用。

## 演示物料

- `docs/demo/3-minute-demo.md`：3 分钟演示脚本
- `docs/demo/product-intro.html`：产品介绍页（浏览器直接打开）
- `docs/demo/resume-project.md`：简历项目描述（含实测数据）
```

- [x] **Step 3: 检查 README 关键段落**

Run:
```bash
cd /e/work
grep -q "Docker 一键启动" README.md && echo "README_DOCKER_OK"
grep -q "压测" README.md && echo "README_LOAD_OK"
grep -q "演示物料" README.md && echo "README_DEMO_OK"
```
Expected: 三个 `OK`。

- [x] **Step 4: 提交**

```bash
cd /e/work
git add docs/demo/resume-project.md README.md
git commit -m "docs: add resume project description and deployment guide"
```

---

## Task 9: 最终验证与收尾

**Files:**
- Modify: `docs/superpowers/plans/2026-10-27-offerpilot-w6-deploy-loadtest-demo.md`（勾选完成项）

- [x] **Step 1: Compose 配置校验**

Run:
```bash
cd /e/work
docker compose --env-file .env.example config --quiet && echo "COMPOSE_FINAL_OK"
```
Expected: `COMPOSE_FINAL_OK`。

- [x] **Step 2: 后端全量测试**

Run:
```bash
cd /e/work
mvn -f backend/pom.xml test -q
```
Expected: 48 个测试通过，退出码 0。

- [x] **Step 3: 前端构建**

Run:
```bash
cd /e/work/frontend
npm run build
```
Expected: 构建成功。

- [x] **Step 4: 检查报告与演示文件**

Run:
```bash
cd /e/work
test -s docs/reports/load-test-20u.json && echo "JSON_OK"
test -s docs/reports/load-test-20u.md && echo "MD_OK"
test -s docs/demo/3-minute-demo.md && echo "DEMO_OK"
test -s docs/demo/product-intro.html && echo "INTRO_OK"
test -s docs/demo/resume-project.md && echo "RESUME_DOC_OK"
```
Expected: 五个 `OK`。

- [x] **Step 5: 更新计划勾选并提交**

把本计划中已完成步骤的 `- [x]` 改为 `- [x]`，然后：
```bash
cd /e/work
git add docs/superpowers/plans/2026-10-27-offerpilot-w6-deploy-loadtest-demo.md
git commit -m "docs: mark P0 week 6 complete"
git status --short
```
Expected: `git status` 无未提交文件。

---

## 自检结果

- **Spec coverage:** 部署架构（Task 1–4）、20 并发压测（Task 5、Task 7）、演示物料（Task 6、Task 8）、验收与错误处理（Task 4、Task 7、Task 9）均有对应任务。
- **Placeholder scan:** 无 TBD/TODO；简历中的压测数据由 Task 8 从实测 JSON 生成，不手填。
- **Type consistency:** 压测 JSON 字段 `successRate`、`wallSeconds`、`throughputUsersPerSecond`、`steps.answer_sse.p95Seconds`、`steps.interview_create.p95Seconds` 在 Task 5 生成、Task 7 校验、Task 8 消费，命名一致。
- **默认可用性:** 默认 Mock AI、仅暴露 8088、`.env` 自动生成、MySQL/上传卷持久化，干净环境可一键启动。
