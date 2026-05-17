#!/usr/bin/env bash
# ==============================================================
#  Transportation - Stop All Microservices (macOS / Linux)
# ==============================================================
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

MODE="${1:-}"

if [ "$MODE" = "docker" ] || [ "$MODE" = "-d" ]; then
  echo -e "${CYAN}[MODE]${NC} 停止 Docker Compose 服务 ..."
  docker compose down
  echo -e "${GREEN}[OK]${NC} 所有 Docker 容器已停止。"
  exit 0
fi

echo -e "${YELLOW}[INFO]${NC} 停止所有本地微服务 ..."

# 需要停止的端口
PORTS=(8081 8082 8083 8084 8085 8086 8087 8090)

for port in "${PORTS[@]}"; do
  PID=$(lsof -i ":$port" -sTCP:LISTEN -t 2>/dev/null || true)
  if [ -n "$PID" ]; then
    echo -e "  ${CYAN}→${NC} 停止端口 :$port 上的进程 (PID: $PID)"
    kill $PID 2>/dev/null || true
  fi
done

sleep 2

# 检查是否还有残留进程
for port in "${PORTS[@]}"; do
  PID=$(lsof -i ":$port" -sTCP:LISTEN -t 2>/dev/null || true)
  if [ -n "$PID" ]; then
    echo -e "  ${YELLOW}⚠${NC} 端口 :$port 进程未响应，强制终止 ..."
    kill -9 $PID 2>/dev/null || true
  fi
done

# 清理日志文件
rm -f /tmp/transport-*.log 2>/dev/null || true

echo -e "${GREEN}[OK]${NC} 所有服务已停止。"
