# Auth Service 测试数据

## 文件说明

- `json/login_request.json` - 登录请求示例（admin）
- `json/login_admin.json` - Admin角色登录请求
- `json/login_customer.json` - Customer角色登录请求
- `json/login_shop.json` - Shop角色登录请求
- `json/login_driver.json` - Driver角色登录请求
- `json/login_response_example.json` - 登录响应示例

## 使用方法

### 登录接口测试

```bash
POST http://localhost:8083/api/auth/login
Content-Type: application/json
```

**请求体示例**（使用 `login_customer.json`）：
```json
{
  "username": "customer",
  "password": "123456"
}
```

**响应示例**（参考 `login_response_example.json`）：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userInfo": {
      "userId": 2,
      "username": "customer",
      "roleCode": "customer",
      "roleName": "顾客用户"
    },
    "expiresIn": 7200
  },
  "timestamp": 1234567890
}
```

## 测试用户

| 用户名 | 密码 | 角色 | 权限值 |
|--------|------|------|--------|
| admin | 123456 | admin | 1 |
| customer | 123456 | customer | 2 |
| shop | 123456 | shop | 3 |
| driver | 123456 | driver | 4 |

## 注意事项

1. 所有测试用户的密码都是 `123456`
2. Token有效期为7200秒（2小时）
3. 登录成功后，保存返回的 `token` 用于后续API调用
4. 在Header中使用Token：`Authorization: Bearer {token}`

