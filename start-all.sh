#!/usr/bin/env bash
# ==============================================================
#  Transportation - Start All Microservices (macOS / Linux)
#  功能对等于 start-all.cmd，适配 macOS 环境
# ==============================================================
set -euo pipefail

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# 项目根目录（脚本所在目录）
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# 服务定义：名称 | 端口 | 目录
# 端口与各服务 application.yml 中 server.port 一致
SERVICES=(
  "user-service      |8081|user-service"
  "auth-service      |8082|auth-service"
  "gateway-service   |8090|gateway-service"
  "customer-service  |8083|customer-service"
  "shop-service      |8084|shop-service"
  "order-service     |8085|order-service"
  "logistics-service |8087|logistics-service"
  "driver-service    |8086|driver-service"
)

# ==============================================================
#  函数定义
# ==============================================================

print_banner() {
  echo ""
  echo -e "${CYAN}======================================================${NC}"
  echo -e "${CYAN}  Transportation - Start All Microservices${NC}"
  echo -e "${CYAN}======================================================${NC}"
  echo ""
}

# 检查端口是否被占用
check_port() {
  local port=$1
  if lsof -i ":$port" -sTCP:LISTEN -t >/dev/null 2>&1; then
    return 0  # 端口已被占用
  else
    return 1  # 端口空闲
  fi
}

# 检查命令是否存在
require_cmd() {
  if ! command -v "$1" &>/dev/null; then
    echo -e "${RED}[ERROR] '$1' 未安装，请先安装后再运行。${NC}"
    if [ "$1" = "mvn" ]; then
      echo -e "  提示: brew install maven"
    elif [ "$1" = "java" ]; then
      echo -e "  提示: brew install openjdk@17"
    fi
    exit 1
  fi
}

# 检查 Nacos 是否运行
check_nacos() {
  echo -e "${YELLOW}[INFO]${NC} 检查 Nacos (localhost:8848) ..."
  if nc -z localhost 8848 2>/dev/null; then
    echo -e "${GREEN}[OK]${NC}   Nacos 正在运行。"
  else
    echo -e "${YELLOW}[WARN]${NC} Nacos 未在 localhost:8848 运行！"
    echo -e "  请先启动 Nacos: sh bin/startup.sh -m standalone"
    echo ""
    read -rp "是否继续？(y/N): " cont
    if [[ ! "$cont" =~ ^[Yy]$ ]]; then
      echo "已取消。"
      exit 0
    fi
  fi
}

# 等待服务就绪
wait_for_service() {
  local name=$1
  local port=$2
  local max_attempts=30
  local attempt=1

  while [ $attempt -le $max_attempts ]; do
    if nc -z localhost "$port" 2>/dev/null; then
      echo -e "  ${GREEN}✓${NC} $name (:$port) 已启动"
      return 0
    fi
    sleep 2
    attempt=$((attempt + 1))
  done
  echo -e "  ${YELLOW}⚠${NC} $name (:$port) 启动超时（可能仍在初始化中）"
  return 1
}

# ==============================================================
#  模式选择
# ==============================================================

MODE="${1:-}"

print_banner

# 检查前置依赖
require_cmd java
require_cmd mvn

# 检查 Java 版本
JAVA_VERSION=$(java -version 2>&1 | head -1 | grep -oE '[0-9]+' | head -1)
if [ "$JAVA_VERSION" -lt 17 ] 2>/dev/null; then
  echo -e "${YELLOW}[WARN]${NC} 当前 Java 版本可能不兼容，项目需要 JDK 17+"
fi

# ==============================================================
#  Docker Compose 模式
# ==============================================================
if [ "$MODE" = "docker" ] || [ "$MODE" = "-d" ]; then
  echo -e "${CYAN}[MODE]${NC} Docker Compose 模式"
  echo ""

  # 检查 Docker
  require_cmd docker

  # 检查 Docker 是否运行
  if ! docker info &>/dev/null; then
    echo -e "${RED}[ERROR]${NC} Docker 未运行，请先启动 Docker Desktop。"
    exit 1
  fi

  # 检查 OSM 文件
  if [ ! -f "$SCRIPT_DIR/shanghai-260310.osm.pbf" ]; then
    echo -e "${YELLOW}[WARN]${NC} 未找到 shanghai-260310.osm.pbf，logistics-service 路径规划将不可用"
  fi

  echo -e "${CYAN}[STEP 1]${NC} 构建并启动所有服务 ..."
  echo ""
  docker compose up --build -d

  echo ""
  echo -e "${CYAN}[STEP 2]${NC} 等待服务就绪 ..."
  echo ""

  # 等待基础设施
  echo -e "${BOLD}基础设施：${NC}"
  for item in "MySQL|3307" "Redis|6379" "RabbitMQ|5672" "Nacos|8848"; do
    IFS='|' read -r name port <<< "$item"
    wait_for_service "$name" "$port" || true
  done

  echo ""
  echo -e "${BOLD}微服务：${NC}"
  for svc in "${SERVICES[@]}"; do
    IFS='|' read -r name port dir <<< "$svc"
    wait_for_service "$name" "$port" || true
  done

  echo ""
  echo -e "${BOLD}前端：${NC}"
  wait_for_service "Frontend (Nginx)" "80" || true

  echo ""
  print_running_info
  exit 0
fi

# ==============================================================
#  本地 Maven 模式（默认）
# ==============================================================
echo -e "${CYAN}[MODE]${NC} 本地 Maven 模式"
echo ""

# 检查端口冲突
echo -e "${YELLOW}[INFO]${NC} 检查端口占用 ..."
PORT_CONFLICT=0
for svc in "${SERVICES[@]}"; do
  IFS='|' read -r name port dir <<< "$svc"
  if check_port "$port"; then
    PID=$(lsof -i ":$port" -sTCP:LISTEN -t 2>/dev/null | head -1)
    echo -e "  ${RED}✗${NC} $name (:$port) 端口已被占用 (PID: $PID)"
    PORT_CONFLICT=1
  fi
done

if [ $PORT_CONFLICT -eq 1 ]; then
  echo ""
  read -rp "存在端口冲突，是否继续？(y/N): " cont
  if [[ ! "$cont" =~ ^[Yy]$ ]]; then
    echo "已取消。"
    exit 0
  fi
fi

# 检查 Nacos
check_nacos

# 首先编译整个项目
echo ""
echo -e "${YELLOW}[STEP 1]${NC} 编译整个项目 (mvn package -DskipTests) ..."
echo ""
if mvn package -DskipTests -q 2>&1; then
  echo -e "${GREEN}[OK]${NC} 项目编译成功。"
else
  echo -e "${RED}[ERROR]${NC} 项目编译失败，请检查错误信息。"
  exit 1
fi

# 启动所有服务
echo ""
echo -e "${YELLOW}[STEP 2]${NC} 启动所有服务 ..."
echo ""

PIDS=()

for svc in "${SERVICES[@]}"; do
  IFS='|' read -r name port dir <<< "$svc"
  JAR_FILE=$(find "$SCRIPT_DIR/$dir/target" -maxdepth 1 -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" 2>/dev/null | head -1)

  if [ -z "$JAR_FILE" ]; then
    echo -e "  ${RED}✗${NC} $name 未找到 JAR 文件，跳过"
    continue
  fi

  echo -e "  ${CYAN}→${NC} 启动 $name (:$port) ..."
  java -jar "$JAR_FILE" \
    --server.port="$port" \
    > "/tmp/transport-${dir}.log" 2>&1 &
  PIDS+=($!)
  disown
done

# 等待服务启动
echo ""
echo -e "${YELLOW}[STEP 3]${NC} 等待服务就绪 (最多60秒/服务) ..."
echo ""

for svc in "${SERVICES[@]}"; do
  IFS='|' read -r name port dir <<< "$svc"
  wait_for_service "$name" "$port" || true
done

echo ""
print_running_info

# ==============================================================
#  通用信息输出
# ==============================================================
print_running_info() {
  echo -e "${CYAN}======================================================${NC}"
  echo -e "  ${BOLD}所有服务已启动！${NC}"
  echo ""
  echo -e "  user-service      -> ${GREEN}http://localhost:8081${NC}"
  echo -e "  auth-service      -> ${GREEN}http://localhost:8082${NC}"
  echo -e "  customer-service  -> ${GREEN}http://localhost:8083${NC}"
  echo -e "  shop-service      -> ${GREEN}http://localhost:8084${NC}"
  echo -e "  order-service     -> ${GREEN}http://localhost:8085${NC}"
  echo -e "  driver-service    -> ${GREEN}http://localhost:8086${NC}"
  echo -e "  logistics-service -> ${GREEN}http://localhost:8087${NC}"
  echo -e "  gateway-service   -> ${GREEN}http://localhost:8090${NC}  [网关]"
  echo ""
  echo -e "  Nacos 控制台      -> ${GREEN}http://localhost:8848/nacos${NC}"
  echo -e "  RabbitMQ 管理     -> ${GREEN}http://localhost:15672${NC}  (guest/guest)"
  echo ""
  if [ "$MODE" = "docker" ] || [ "$MODE" = "-d" ]; then
    echo -e "  前端 (Nginx)      -> ${GREEN}http://localhost:80${NC}"
    echo ""
    echo -e "  ${YELLOW}常用 Docker 命令：${NC}"
    echo -e "    查看日志:  docker compose logs -f <service-name>"
    echo -e "    停止所有:  docker compose down"
    echo -e "    重启某服务: docker compose restart <service-name>"
    echo -e "    查看状态:  docker compose ps"
  else
    echo -e "  ${YELLOW}日志文件位置：${NC}"
    echo -e "    /tmp/transport-<service-name>.log"
    echo ""
    echo -e "  ${YELLOW}停止所有服务：${NC}"
    echo -e "    ./stop-all.sh"
    echo -e "    或手动: kill \$(lsof -i :8081-8087 -i :8090 -t)"
  fi
  echo -e "${CYAN}======================================================${NC}"
  echo ""
}
