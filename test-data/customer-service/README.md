# Customer Service 测试数据

## 文件说明

### 顾客信息相关
- `json/customer_add_request.json` - 添加顾客信息请求
- `json/customer_update_request.json` - 更新顾客信息请求
- `json/customer_response_example.json` - 顾客信息响应示例

### 地址相关
- `json/address_add_request.json` - 添加地址请求
- `json/address_update_request.json` - 更新地址请求
- `json/address_response_example.json` - 单个地址响应示例
- `json/address_list_response_example.json` - 地址列表响应示例

## 测试场景

### 场景1：权限控制测试

#### 1.1 Customer角色访问（应该成功 - 200）
```bash
GET http://localhost:8083/api/customers/1
Authorization: Bearer {customer_token}
```

#### 1.2 Shop角色访问（应该失败 - 403）
```bash
GET http://localhost:8083/api/customers/1
Authorization: Bearer {shop_token}
```
预期响应：
```json
{
  "code": 403,
  "message": "无权限访问该资源",
  "data": null,
  "timestamp": ...
}
```

#### 1.3 Admin角色访问（应该成功 - 200）
```bash
GET http://localhost:8083/api/customers/1
Authorization: Bearer {admin_token}
```

#### 1.4 无Token访问（应该失败 - 401）
```bash
GET http://localhost:8083/api/customers/1
```
预期响应：
```json
{
  "code": 401,
  "message": "Token不能为空",
  "data": null,
  "timestamp": ...
}
```

---

### 场景2：顾客信息管理

#### 2.1 获取个人信息
```bash
GET http://localhost:8083/api/customers/1
Authorization: Bearer {customer_token}
```
参考响应：`customer_response_example.json`

#### 2.2 添加个人信息
```bash
POST http://localhost:8083/api/customers
Authorization: Bearer {customer_token}
Content-Type: application/json

使用文件: customer_add_request.json
```

#### 2.3 修改个人信息
```bash
PUT http://localhost:8083/api/customers
Authorization: Bearer {customer_token}
Content-Type: application/json

使用文件: customer_update_request.json
```

#### 2.4 删除个人信息
```bash
DELETE http://localhost:8083/api/customers/1
Authorization: Bearer {customer_token}
```

---

### 场景3：地址管理

#### 3.1 获取个人地址列表
```bash
GET http://localhost:8083/api/customers/1/address
Authorization: Bearer {customer_token}
```
参考响应：`address_list_response_example.json`

#### 3.2 添加个人地址
```bash
POST http://localhost:8083/api/customers/address
Authorization: Bearer {customer_token}
Content-Type: application/json

使用文件: address_add_request.json
```

#### 3.3 修改个人地址
```bash
PUT http://localhost:8083/api/customers/address
Authorization: Bearer {customer_token}
Content-Type: application/json

使用文件: address_update_request.json
```

#### 3.4 删除个人地址
```bash
DELETE http://localhost:8083/api/customers/address/1
Authorization: Bearer {customer_token}
```

---

## 测试数据说明

### 数据库测试数据
- **Customer用户**：user_id=2, customer_id=1
- **地址数据**：customer_id=1 有2条地址记录（1条默认，1条非默认）

### 注意事项
1. 所有需要Token的请求，都要在Header中添加：`Authorization: Bearer {token}`
2. customer_id=1 对应数据库中的测试数据
3. 测试前请确保数据库已执行 `database/user.sql` 和 `database/customer.sql`
4. 登录Token需要从 `auth-service` 获取，参考 `../auth-service/README.md`

