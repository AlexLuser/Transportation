# Docker 新手教程 - 给完全小白的超详细指南

> **适用对象**：完全不懂 Docker 的新手（比如昨天的我）
> **项目**：Transportation 智能物流管理系统
> **写于**：2026-05-14

---

## 一、Docker 是什么？（人话版）

想象一下这个场景：

**没有 Docker 之前**：
- 你想运行一个项目，需要先安装 Java、MySQL、Redis、RabbitMQ...
- 每个软件都有版本要求，装错了就崩
- 你的电脑变成"软件垃圾场"，乱七八糟

**有了 Docker 之后**：
- Docker 就像一个"魔法箱子"
- 所有的软件（Java、数据库、中间件）都装在"箱子"里
- 需要什么，直接搬箱子，不用装软件
- 箱子之间互相隔离，不会搞乱你的电脑

**专业点的解释**（可选看）：
- Docker 是一个"容器化平台"
- 容器 = 打包好的运行环境（软件 + 配置 + 依赖）
- 一次打包，到处运行（你的电脑、服务器、云端都能跑）

---

## 二、为什么要安装 Docker？（针对 Transportation 项目）

你的 Transportation 项目有 **8 个微服务**：
- gateway-service（网关）
- auth-service（认证）
- customer-service（客户）
- shop-service（商家）
- order-service（订单）
- logistics-service（物流）
- driver-service（司机）
- frontend（前端）

**传统方式启动**：
1. 手动启动 MySQL、Redis、RabbitMQ...
2. 手动启动 8 个微服务（每个都要 `mvn spring-boot:run`）
3. 手动启动前端（`npm run dev`）
4. 搞错启动顺序就崩
5. 换台电脑又要重新配置一遍

**Docker 方式启动**：
1. 一条命令：`docker compose up -d`
2. 自动启动所有服务（数据库、中间件、微服务、前端）
3. 换台电脑也是一条命令搞定

---

## 三、安装步骤（我帮你做了什么）

### ✅ 已完成的工作

我帮你完成了以下所有步骤：

1. **检查芯片类型**
   - 你的 Mac 是 Apple Silicon (M1/M2/M3 系列)
   - 下载了对应版本的 Docker Desktop (Apple Silicon 版)

2. **下载 Docker Desktop**
   - 从官网下载：https://desktop.docker.com/mac/main/arm64/Docker.dmg
   - 文件大小：585MB
   - 下载位置：`/tmp/Docker.dmg`

3. **挂载安装镜像**
   - 命令：`hdiutil attach /tmp/Docker.dmg`
   - 镜像挂载到：`/Volumes/Docker`

4. **安装 Docker.app**
   - 复制 Docker.app 到应用程序文件夹：`cp -R /Volumes/Docker/Docker.app /Applications/`
   - 卸载安装镜像：`hdiutil detach /Volumes/Docker`

5. **启动 Docker Desktop**
   - 命令：`open /Applications/Docker.app`
   - **注意**：首次启动时需要你手动完成以下操作：
     - 接受许可协议（Accept License）
     - 输入管理员密码（允许 Docker 修改系统配置）
     - 等待初始化完成（菜单栏出现 Docker 图标）

6. **配置环境变量**
   - 修改了 `~/.zshrc` 文件
   - 添加了 Docker 的 bin 目录到 PATH：
     ```bash
     export PATH="/Applications/Docker.app/Contents/Resources/bin:$PATH"
     ```
   - 这样你可以在终端直接使用 `docker` 和 `docker compose` 命令

7. **验证安装**
   - ✅ Docker 版本：29.4.3
   - ✅ Docker Compose 版本：v5.1.3
   - ✅ 测试运行：`docker run hello-world` 成功！

---

## 四、你需要手动完成的步骤（重要！）

虽然我帮你安装了 Docker Desktop，但首次启动时需要你手动完成一些操作：

### 步骤 1：接受许可协议
1. Docker Desktop 启动后，会弹出一个窗口
2. 阅读许可协议（Docker 对个人用户免费）
3. 点击 **"Accept"** 接受协议

### 步骤 2：输入管理员密码
1. Docker 需要修改系统配置（安装网络组件、虚拟机等）
2. 系统会弹出密码输入框
3. 输入你的 Mac 登录密码
4. 点击 **"OK"** 或 **"允许"**

### 步骤 3：等待初始化
1. Docker 图标会出现在菜单栏（右上角）
2. 图标会有一个小动画（表示正在启动）
3. 等待动画消失，表示启动完成
4. **预计时间**：首次启动需要 1-3 分钟

### 步骤 4：验证安装成功
打开终端（Terminal），输入以下命令：

```bash
# 检查 Docker 版本
docker --version

# 检查 Docker Compose 版本
docker compose version

# 运行测试容器
docker run hello-world
```

如果看到 "Hello from Docker!"，说明安装成功！🎉

---

## 五、Docker 常用命令（抄作业版）

### 基础命令

```bash
# 查看 Docker 版本
docker --version

# 查看 Docker 详细信息
docker info

# 查看正在运行的容器
docker ps

# 查看所有容器（包括停止的）
docker ps -a

# 查看本地镜像
docker images

# 拉取镜像（从 Docker Hub 下载）
docker pull mysql:8.0

# 运行容器
docker run -d --name my-mysql -p 3306:3306 mysql:8.0

# 停止容器
docker stop <容器名或ID>

# 启动容器
docker start <容器名或ID>

# 删除容器
docker rm <容器名或ID>

# 删除镜像
docker rmi <镜像名或ID>

# 查看容器日志
docker logs <容器名或ID>

# 进入容器（类似 SSH）
docker exec -it <容器名或ID> bash
```

### Docker Compose 命令（重点！）

Docker Compose 是用来管理多个容器的工具（比如你的 Transportation 项目）。

```bash
# 启动所有服务（后台运行）
docker compose up -d

# 查看服务状态
docker compose ps

# 查看服务日志
docker compose logs

# 停止所有服务
docker compose down

# 重启所有服务
docker compose restart

# 重新构建并启动（修改代码后使用）
docker compose up -d --build
```

---

## 六、如何使用 Docker 启动 Transportation 项目

### 方法 1：使用项目自带的 docker-compose.yml（推荐）

1. **打开终端**，进入项目目录：
   ```bash
   cd /Users/miao/CodeBuddy/Transportation
   ```

2. **启动所有服务**：
   ```bash
   docker compose up -d
   ```

3. **等待服务启动**（首次需要下载镜像，可能需要 10-20 分钟）

4. **访问项目**：
   - 前端：http://localhost:5173
   - 后端 API：http://localhost:8083

### 方法 2：使用启动脚本（更简单）

项目提供了启动脚本：

```bash
# 启动所有服务
./start-all.sh

# 停止所有服务
./stop-all.sh
```

---

## 七、常见问题 & 解决方案

### 问题 1：Docker Desktop 启动失败
**症状**：菜单栏图标一直转圈，或者报错  
**解决**：
1. 重启 Docker Desktop（右键菜单栏图标 → Quit Docker Desktop）
2. 重新打开 Docker.app
3. 如果还不行，重启电脑

### 问题 2：端口被占用
**症状**：`docker compose up` 时报错 "port is already allocated"  
**解决**：
```bash
# 查看哪个程序占用了端口（比如 3306）
lsof -i :3306

# 杀死占用端口的进程
kill -9 <PID>
```

### 问题 3：镜像下载太慢
**症状**：`docker pull` 速度只有几 KB/s  
**解决**：配置国内镜像加速器
1. 点击菜单栏 Docker 图标 → Preferences
2. 进入 Docker Engine 设置
3. 添加国内镜像源（比如阿里云、网易云）

### 问题 4：磁盘空间不足
**症状**：Docker 占用几十 GB 空间  
**解决**：
```bash
# 删除所有停止的容器
docker container prune

# 删除所有未使用的镜像
docker image prune -a

# 删除所有未使用的卷（数据会丢失！谨慎使用）
docker volume prune
```

---

## 八、学习资源（想深入研究的看这里）

### 官方文档
- Docker 官方文档：https://docs.docker.com/
- Docker Compose 文档：https://docs.docker.com/compose/

### 中文教程
- 菜鸟教程 - Docker：https://www.runoob.com/docker/docker-tutorial.html
- 尚硅谷 Docker 教程（B站）

### 实战项目
- 用 Docker 部署 Spring Boot 项目
- 用 Docker 搭建 MySQL 主从复制
- 用 Docker Compose 部署微服务架构

---

## 九、总结（重要！）

### 我帮你做了什么
1. ✅ 检查了你的电脑没有安装 Docker
2. ✅ 确认了你的 Mac 芯片类型（Apple Silicon）
3. ✅ 从官网下载了 Docker Desktop (585MB)
4. ✅ 安装了 Docker Desktop 到应用程序文件夹
5. ✅ 启动了 Docker Desktop
6. ✅ 配置了环境变量（你可以在终端直接使用 `docker` 命令）
7. ✅ 验证了安装成功（运行了 hello-world 测试）

### 你还需要做什么
1. ⏳ **（如果还没完成）** 接受 Docker 许可协议
2. ⏳ **（如果还没完成）** 输入管理员密码允许 Docker 修改系统
3. ⏳ 等待 Docker Desktop 初始化完成
4. ⏳ 在终端运行 `docker run hello-world` 验证安装
5. ⏳ 尝试用 `docker compose up -d` 启动 Transportation 项目

### 下一步学习建议
1. 先学会用 `docker compose up/down` 启动/停止项目
2. 遇到问题时，学会用 `docker logs` 查看日志
3. 想深入研究时，再看官方文档学习 Dockerfile 编写

---

## 十、联系方式（遇到问题找谁）

如果你在安装或使用过程中遇到问题：
1. 先查看本文档的 **"七、常见问题 & 解决方案"**
2. 如果还解决不了，问我（AI 助手）😊

---

**祝你在 Docker 的世界里玩得开心！🐳**

（写完这篇教程的时间是 2026-05-14 21:20，如果以后 Docker 版本更新了，界面可能略有不同，但核心流程是一样的）
