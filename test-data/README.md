# 测试数据目录

本目录用于存放各服务的测试数据，按服务模块分类组织。

## 目录结构

```
test-data/
├── auth-service/          # 认证服务测试数据
│   ├── json/              # JSON测试数据
│   └── README.md          # 使用说明
├── customer-service/      # 顾客服务测试数据
│   ├── json/              # JSON测试数据
│   └── README.md          # 使用说明
├── user-service/          # 用户服务测试数据
│   ├── json/              # JSON测试数据（待添加）
│   └── README.md          # 使用说明
├── sql/                   # SQL测试数据（如有）
└── scripts/               # 测试脚本（如有）
```

## 各服务测试数据

### Auth Service（认证服务）
- **位置**：`auth-service/json/`
- **说明**：登录相关的请求和响应示例
- **详情**：查看 [auth-service/README.md](./auth-service/README.md)

### Customer Service（顾客服务）
- **位置**：`customer-service/json/`
- **说明**：顾客信息和地址管理的测试数据
- **详情**：查看 [customer-service/README.md](./customer-service/README.md)

### User Service（用户服务）
- **位置**：`user-service/json/`
- **说明**：用户服务相关的测试数据（待添加）
- **详情**：查看 [user-service/README.md](./user-service/README.md)

## 快速开始

### 1. 登录获取Token

```bash
POST http://localhost:8083/api/auth/login
Content-Type: application/json

使用文件: auth-service/json/login_customer.json
```

### 2. 测试Customer Service

```bash
GET http://localhost:8083/api/customers/1
Authorization: Bearer {token}

使用文件: customer-service/json/customer_response_example.json (参考)
```

## 注意事项

1. **Token获取**：所有需要认证的接口都需要先通过 `auth-service` 登录获取Token
2. **权限控制**：不同角色的Token具有不同的访问权限
3. **数据库准备**：测试前请确保数据库已执行相应的SQL文件（位于 `database/` 目录）
4. **服务启动**：测试前请确保相关服务已启动（Nacos、MySQL、各微服务）

## 测试流程

1. 启动基础设施（Nacos、MySQL）
2. 启动业务服务（auth-service、customer-service、gateway-service）
3. 使用 `auth-service` 的测试数据登录获取Token
4. 使用Token测试各服务的API接口
5. 验证权限控制是否正常工作

## 相关文档

- 数据库SQL文件：`database/`
- 各服务详细说明：各服务目录下的 `README.md`
