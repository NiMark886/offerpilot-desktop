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
