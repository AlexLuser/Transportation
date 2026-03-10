# JWT认证与权限控制说明

## 一、Token的生成过程

### 1. 用户登录流程

```
用户请求登录
    ↓
POST /api/auth/login
{
  "username": "customer",
  "password": "123456"
}
    ↓
AuthService验证用户名和密码
    ↓
验证通过后，生成JWT Token
    ↓
返回Token给前端
{
  "code": 200,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userInfo": {
      "userId": 2,
      "username": "customer",
      "roleCode": "customer",
      "roleName": "顾客用户"
    },
    "expiration": 7200
  }
}
```

### 2. Token的生成代码

**位置**: `auth-service/src/main/java/com/fm/auth/service/impl/AuthServiceImpl.java`

```java
// 生成JWT Token
String token = jwtUtil.generateToken(
    user.getId(),        // 用户ID
    user.getUsername(),  // 用户名
    permissionEnum.getRoleCode()  // 角色编码（admin/customer/shop/driver）
);
```

### 3. Token的结构（JWT格式）

JWT Token由三部分组成，用 `.` 分隔：

```
Header.Payload.Signature
```

**示例Token**:
```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjIsInVzZXJuYW1lIjoiY3VzdG9tZXIiLCJyb2xlQ29kZSI6ImN1c3RvbWVyIiwic3ViIjoiY3VzdG9tZXIiLCJpYXQiOjE3MDQwNjcyMDAsImV4cCI6MTcwNDA3NDQwMH0.xxxxx
```

#### 3.1 Header（头部）
```json
{
  "alg": "HS256",  // 签名算法
  "typ": "JWT"     // Token类型
}
```

#### 3.2 Payload（载荷）- **这里包含用户信息**
```json
{
  "userId": 2,                    // 用户ID
  "username": "customer",          // 用户名
  "roleCode": "customer",          // 角色编码
  "sub": "customer",              // 主题（用户名）
  "iat": 1704067200,              // 签发时间（时间戳）
  "exp": 1704074400               // 过期时间（时间戳）
}
```

#### 3.3 Signature（签名）
```
HMACSHA256(
  base64UrlEncode(header) + "." + base64UrlEncode(payload),
  secret
)
```

**重要**: Token是**可解码**的，任何人都可以看到Payload中的信息（Base64解码），但**不能伪造**（因为需要密钥签名）。

---

## 二、Token中可以看到什么信息？

### ✅ 可以看到的信息（无需密钥）

任何人都可以通过Base64解码看到Token的Payload部分，包括：

1. **userId** - 用户ID
2. **username** - 用户名
3. **roleCode** - 角色编码（admin/customer/shop/driver）
4. **iat** - 签发时间
5. **exp** - 过期时间

### ❌ 不能看到的信息

1. **密码** - Token中不包含密码
2. **敏感信息** - 不包含其他敏感数据

### 🔒 安全性保证

虽然Token内容可见，但：
- **不能伪造**: 没有密钥无法生成有效签名
- **不能篡改**: 修改Payload会导致签名验证失败
- **有时效性**: Token有过期时间（默认2小时）

---

## 三、网关如何验证Token？

### 1. 网关过滤器流程

**位置**: `gateway-service/src/main/java/com/fm/gateway/filter/JwtAuthFilter.java`

```
请求到达网关
    ↓
检查是否在白名单（如 /api/auth/login）
    ↓
从请求头获取Token
    Authorization: Bearer {token}
    ↓
验证Token是否有效（格式、签名、过期时间）
    ↓
从Token中提取用户信息
    - userId
    - username
    - roleCode
    ↓
检查用户是否有权限访问该路径
    ↓
将用户信息添加到请求头，转发给下游服务
    Header: userId=2, username=customer, roleCode=customer
    ↓
下游服务可以通过请求头获取用户信息
```

### 2. 详细验证步骤

#### 步骤1: 检查白名单
```java
// 白名单路径不需要Token验证
private static final List<String> WHITE_LIST = Arrays.asList(
    "/api/auth/login",      // 登录接口
    "/swagger-ui/**",       // Swagger文档
    "/v3/api-docs/**"       // Swagger API文档
);
```

#### 步骤2: 获取Token
```java
// 支持两种方式：
// 1. Authorization: Bearer {token}
// 2. token: {token}
String authHeader = request.getHeaders().getFirst("Authorization");
if (authHeader != null && authHeader.startsWith("Bearer ")) {
    return authHeader.substring(7);  // 去掉 "Bearer " 前缀
}
```

#### 步骤3: 验证Token
```java
// 使用JwtUtil验证Token
if (!jwtUtil.validateToken(token)) {
    return unauthorized(exchange, "Token无效或已过期");
}
```

**验证内容**:
- Token格式是否正确
- 签名是否有效（使用密钥验证）
- 是否过期（检查exp字段）

#### 步骤4: 提取用户信息
```java
Long userId = jwtUtil.getUserIdFromToken(token);
String username = jwtUtil.getUsernameFromToken(token);
String roleCode = jwtUtil.getRoleCodeFromToken(token);
```

#### 步骤5: 权限控制
```java
// 根据路径和角色判断是否有权限
if (!hasPermission(path, roleCode)) {
    return forbidden(exchange, "无权限访问该资源");
}
```

#### 步骤6: 转发给下游服务
```java
// 将用户信息添加到请求头
ServerHttpRequest.Builder builder = request.mutate();
builder.header("userId", userId.toString());
builder.header("username", username);
builder.header("roleCode", roleCode);

// 转发请求
return chain.filter(exchange.mutate().request(modifiedRequest).build());
```

---

## 四、权限控制规则

### 1. 权限控制逻辑

**位置**: `gateway-service/src/main/java/com/fm/gateway/filter/JwtAuthFilter.java` 的 `hasPermission()` 方法

```java
private boolean hasPermission(String path, String roleCode) {
    // 管理员可以访问所有资源
    if ("admin".equals(roleCode)) {
        return true;
    }
    
    // 顾客服务：只允许customer角色访问
    if (path.startsWith("/api/customers")) {
        return "customer".equals(roleCode);
    }
    
    // 商户服务：只允许shop角色访问
    if (path.startsWith("/api/shops")) {
        return "shop".equals(roleCode);
    }
    
    // 商品服务：shop和customer都可以访问
    if (path.startsWith("/api/products")) {
        return "shop".equals(roleCode) || "customer".equals(roleCode);
    }
    
    // 商城服务：所有登录用户都可以访问
    if (path.startsWith("/api/mall")) {
        return "shop".equals(roleCode) || "customer".equals(roleCode) || "admin".equals(roleCode);
    }
    
    // 仓库服务：只允许shop角色访问
    if (path.startsWith("/api/warehouses")) {
        return "shop".equals(roleCode);
    }
    
    // 库存服务：只允许shop角色访问
    if (path.startsWith("/api/stocks")) {
        return "shop".equals(roleCode);
    }
    
    // 默认允许访问
    return true;
}
```

### 2. 权限矩阵

| 路径 | admin | customer | shop | driver |
|------|-------|----------|------|--------|
| `/api/auth/login` | ✅ | ✅ | ✅ | ✅ |
| `/api/customers/**` | ✅ | ✅ | ❌ | ❌ |
| `/api/shops/**` | ✅ | ❌ | ✅ | ❌ |
| `/api/products/**` | ✅ | ✅ | ✅ | ❌ |
| `/api/mall/**` | ✅ | ✅ | ✅ | ❌ |
| `/api/warehouses/**` | ✅ | ❌ | ✅ | ❌ |
| `/api/stocks/**` | ✅ | ❌ | ✅ | ❌ |

---

## 五、Token的使用示例

### 1. 前端如何使用Token

```javascript
// 1. 登录获取Token
const loginResponse = await fetch('http://localhost:8083/api/auth/login', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    username: 'customer',
    password: '123456'
  })
});

const { data } = await loginResponse.json();
const token = data.token;  // 保存Token

// 2. 使用Token访问受保护的接口
const productsResponse = await fetch('http://localhost:8083/api/mall/products', {
  method: 'GET',
  headers: {
    'Authorization': `Bearer ${token}`  // 在请求头中携带Token
  }
});
```

### 2. 后端服务如何获取用户信息

**下游服务**（如shop-service）可以通过请求头获取用户信息：

```java
@GetMapping("/products")
public Result<List<Product>> getProducts(
    @RequestHeader(value = "userId", required = false) String userIdHeader,
    @RequestHeader(value = "roleCode", required = false) String roleCode) {
    
    // 网关已经验证过Token，这里可以直接使用用户信息
    if (userIdHeader != null) {
        Long userId = Long.parseLong(userIdHeader);
        // 根据userId进行业务逻辑处理
    }
    
    // ...
}
```

---

## 六、Token的安全性

### 1. 安全措施

✅ **已实现的安全措施**:
- Token有过期时间（默认2小时）
- 使用HMAC-SHA256签名，防止伪造
- 网关统一验证，避免重复验证
- 基于角色的权限控制（RBAC）

⚠️ **需要注意的安全问题**:
- Token存储在客户端（浏览器localStorage/sessionStorage），可能被XSS攻击窃取
- Token在HTTP请求中传输，可能被中间人攻击（建议使用HTTPS）
- Token一旦泄露，在过期前都可以使用（无法主动撤销）

### 2. 建议的安全实践

1. **使用HTTPS**: 生产环境必须使用HTTPS传输
2. **设置合理的过期时间**: 根据业务需求调整（当前2小时）
3. **Token刷新机制**: 实现refresh token机制，缩短access token过期时间
4. **Token黑名单**: 实现Token黑名单机制，支持主动撤销Token
5. **敏感操作二次验证**: 对于敏感操作（如支付、修改密码），要求二次验证

---

## 七、调试Token

### 1. 在线工具解码Token

可以使用在线JWT解码工具查看Token内容：
- https://jwt.io/
- https://www.jsonwebtoken.io/

**注意**: 这些工具只能查看Token内容，不能伪造Token（因为需要密钥签名）

### 2. 查看Token中的信息

将Token粘贴到 https://jwt.io/ 的"Encoded"框中，可以看到：
- **Header**: 签名算法和类型
- **Payload**: 用户信息（userId, username, roleCode等）
- **Verify Signature**: 需要密钥才能验证签名

---

## 八、总结

### Token的特点

1. **可解码**: Token内容（Payload）是Base64编码，任何人都可以解码查看
2. **不可伪造**: 没有密钥无法生成有效签名
3. **不可篡改**: 修改Token内容会导致签名验证失败
4. **有时效性**: Token有过期时间，过期后需要重新登录

### 网关的作用

1. **统一认证**: 所有请求在网关层统一验证Token
2. **权限控制**: 根据路径和角色判断是否有权限访问
3. **信息传递**: 将用户信息添加到请求头，传递给下游服务
4. **安全防护**: 无效Token或权限不足的请求在网关层就被拦截

### 权限控制流程

```
用户请求 → 网关验证Token → 检查权限 → 转发给下游服务 → 返回结果
           ↓ 失败
        返回401/403
```

