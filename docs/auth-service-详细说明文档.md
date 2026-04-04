# Auth-Service 认证服务 — 完全初学者指南

> 本文档面向**完全没有后端开发经验**的读者，从零开始讲解 auth-service 模块的每一行代码背后的原理、用到的技术，以及数据如何在系统中流动。建议从头到尾按顺序阅读。

---

## 目录

1. [这个模块是做什么的？](#1-这个模块是做什么的)
2. [基础概念预备知识](#2-基础概念预备知识)
3. [技术栈全览](#3-技术栈全览)
4. [项目目录结构](#4-项目目录结构)
5. [数据库设计](#5-数据库设计)
6. [核心数据对象（DTO & 实体）](#6-核心数据对象dto--实体)
7. [完整登录流程详解](#7-完整登录流程详解)
8. [逐文件代码讲解](#8-逐文件代码讲解)
9. [关键技术深度解析](#9-关键技术深度解析)
10. [配置文件解读](#10-配置文件解读)
11. [异常处理机制](#11-异常处理机制)
12. [接口文档（Swagger）](#12-接口文档swagger)
13. [与其他服务的关系](#13-与其他服务的关系)
14. [常见问题 Q&A](#14-常见问题-qa)

---

## 1. 这个模块是做什么的？

### 1.1 现实场景类比

想象你去一家公司办公楼上班。门口有一个**门卫（auth-service）**，负责：

1. **验证身份**：你出示工牌（用户名+密码），门卫查看花名册（数据库）确认你是否是员工
2. **发放通行证**：确认身份后，门卫给你一张临时通行证（JWT Token），有效期2小时
3. **区分角色**：通行证上标注你是"管理员"、"普通员工"、"商户"还是"快递员"，不同角色可以进入不同区域

在这个系统中，auth-service 就是这个门卫，负责处理所有用户的**登录认证**。

### 1.2 系统角色

本系统共有 4 种角色：

| 权限值 | 角色代码 | 角色名称 | 登录后跳转页面 |
|--------|----------|----------|----------------|
| 1 | `admin` | 管理员 | `/admin/dashboard` |
| 2 | `customer` | 顾客用户 | `/customer/home` |
| 3 | `shop` | 商户用户 | `/shop/home` |
| 4 | `driver` | 运输员 | `/driver/orders` |

### 1.3 模块提供的接口

| HTTP 方法 | URL | 功能 |
|-----------|-----|------|
| POST | `/api/auth/login` | 用户登录，返回 JWT Token |
| POST | `/api/auth/logout` | 用户登出（前端删除 Token） |

---

## 2. 基础概念预备知识

在深入代码之前，我们需要了解几个基础概念。

### 2.1 什么是 HTTP 请求与响应？

**HTTP（超文本传输协议）** 是浏览器和服务器之间通信的语言。

```
用户点击"登录"按钮
       ↓
浏览器发送 HTTP 请求 ─────────────────────→ 服务器处理
       ↑                                         ↓
浏览器接收响应 ←────────────────────────── 服务器返回结果
```

**请求示例（发送给服务器的数据）：**
```
POST /api/auth/login
Content-Type: application/json

{
  "username": "张三",
  "password": "123456"
}
```

**响应示例（服务器返回的数据）：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "userInfo": {
      "userId": 1,
      "username": "张三",
      "roleCode": "customer",
      "roleName": "顾客用户"
    },
    "expiration": 7200
  },
  "timestamp": 1700000000000
}
```

### 2.2 什么是 JSON？

**JSON（JavaScript Object Notation）** 是一种数据格式，就像一种"通用语言"，让不同系统之间传递数据。

```json
{
  "name": "张三",         ← 字符串（用引号）
  "age": 25,              ← 数字（不用引号）
  "isStudent": false,     ← 布尔值（true/false）
  "courses": ["数学", "英语"],  ← 数组（用中括号）
  "address": {            ← 嵌套对象（用花括号）
    "city": "北京",
    "street": "长安街"
  }
}
```

### 2.3 什么是 RESTful API？

**RESTful** 是一种设计接口的规范，规定如何用 HTTP 方法表达不同操作：

| HTTP 方法 | 含义 | 举例 |
|-----------|------|------|
| GET | 查询/获取 | 获取用户信息 |
| POST | 创建/提交 | 登录、创建订单 |
| PUT | 更新（完整替换） | 更新用户资料 |
| DELETE | 删除 | 删除商品 |

本模块中，登录和登出都用 **POST**，因为它们是"提交动作"。

### 2.4 什么是微服务？

传统的应用把所有功能（登录、下单、配送等）塞进一个程序里，这叫**单体应用**。

**微服务**把每个功能拆成独立的小程序：
```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ auth-service │  │order-service│  │driver-service│
│  （认证服务）  │  │  （订单服务） │  │  （司机服务） │
└─────────────┘  └─────────────┘  └─────────────┘
       ↑                ↑                ↑
   端口: 8082       端口: 8081       端口: 8084
```

这样每个服务可以独立部署、独立扩展，互不影响。

---

## 3. 技术栈全览

auth-service 用到了以下技术，下面逐一解释：

### 3.1 Spring Boot — 服务框架

**类比**：Spring Boot 就像一套"精装修的公寓"，房子结构、水电煤气都帮你装好了，你只需要摆家具（写业务代码）。

传统 Java Web 开发需要配置大量 XML 文件、手动管理依赖版本，非常繁琐。Spring Boot 通过**自动配置**解决了这个问题：
- 你告诉它"我需要一个 Web 服务"，它自动配置好 Tomcat 服务器
- 你告诉它"我要连接 MySQL"，它自动配置好数据库连接池

**核心注解（注解是 Java 中以 `@` 开头的特殊标记）：**

| 注解 | 作用 |
|------|------|
| `@SpringBootApplication` | 标记这是 Spring Boot 应用的启动类 |
| `@RestController` | 标记这个类是 HTTP 接口控制器，返回 JSON |
| `@Service` | 标记这个类是业务逻辑层 |
| `@Autowired` | 自动注入依赖（不需要手动 `new` 对象） |

### 3.2 MyBatis Plus — 数据库操作框架

**类比**：如果数据库是一个仓库，MyBatis Plus 就是仓库管理系统，帮你自动生成"入库单"、"出库单"（SQL 语句）。

没有 MyBatis Plus 时，你需要手写 SQL：
```sql
SELECT * FROM user WHERE username = '张三';
```

有了 MyBatis Plus，你用 Java 代码就能操作数据库，不需要写 SQL：
```java
// 等价于上面的 SQL
LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(User::getUsername, "张三");
User user = authMapper.selectOne(wrapper);
```

### 3.3 JWT — 用户身份令牌

**JWT（JSON Web Token）** 是一种加密的字符串，用来携带用户身份信息。

**类比**：就像景区的门票，上面有你的信息、有效期、景区的防伪印章。服务器不需要保存"谁买了票"的记录，只需要验证门票上的印章是不是自己盖的。

JWT 的结构由三部分组成（用 `.` 分隔）：
```
eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoi5byg5LiJIiwicm9sZUNvZGUiOiJjdXN0b21lciJ9.abc123
│─────────────────────│ │─────────────────────────────────────────────│ │──────│
      Header（头部）                    Payload（载荷）                  Signature（签名）
   （说明加密算法）              （存放用户信息，Base64编码）            （防篡改的数字签名）
```

**解码 Payload 后得到（Base64 解码，不是加密，任何人都能看）：**
```json
{
  "userId": 1,
  "username": "张三",
  "roleCode": "customer",
  "sub": "张三",
  "iat": 1700000000,   ← 签发时间（Unix时间戳）
  "exp": 1700007200    ← 过期时间（iat + 7200秒）
}
```

**重要**：JWT 的 Payload **不加密**（只是编码），所以不要在里面放密码等敏感信息！

### 3.4 BCrypt — 密码加密算法

**为什么密码不能明文存储？**

如果数据库被黑客拿到，他们会立刻看到所有人的密码：
```
用户名: 张三, 密码: 123456   ← 非常危险！
```

**BCrypt 的解决方案**：把密码变成一串乱码（哈希值），且无法反推：
```
原始密码: 123456
BCrypt加密后: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
```

**BCrypt 的特点**：
1. **不可逆**：无法从哈希值推算出原始密码
2. **相同密码每次加密结果不同**：每次加密都会加入随机"盐"（Salt）
3. **验证方式**：`passwordEncoder.matches(输入的密码, 数据库中的哈希值)` → 返回 `true/false`

```java
// 加密示例（注册时使用）
String hash = passwordEncoder.encode("123456");
// 输出: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy

// 验证示例（登录时使用）
boolean ok = passwordEncoder.matches("123456", hash);
// 输出: true（密码正确）
boolean ok2 = passwordEncoder.matches("654321", hash);
// 输出: false（密码错误）
```

### 3.5 Nacos — 服务注册与发现中心

**类比**：Nacos 就像一家公司的**花名册**（或者说"114查号台"）。

当 auth-service 启动时，它向 Nacos 报到："我是 auth-service，我在 8082 端口"。

当其他服务（如 gateway-service）需要调用 auth-service 时，先问 Nacos："auth-service 在哪里？"，Nacos 回答："在 localhost:8082"。

这样即使 auth-service 换了 IP 地址，其他服务不需要修改代码，只需重新问 Nacos 就能找到。

### 3.6 Lombok — 减少样板代码

Java 写一个简单的数据类，需要大量重复代码：
```java
public class User {
    private String username;

    // 下面这些都是"样板代码"，有了Lombok的@Data注解就不用写了
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public boolean equals(Object o) { ... }
    public int hashCode() { ... }
    public String toString() { ... }
}
```

Lombok 的注解自动生成这些代码：

| 注解 | 作用 |
|------|------|
| `@Data` | 自动生成 getter、setter、equals、hashCode、toString |
| `@AllArgsConstructor` | 生成包含所有字段的构造方法 |
| `@NoArgsConstructor` | 生成无参构造方法 |
| `@Getter` | 只生成 getter 方法 |
| `@Slf4j` | 自动创建日志对象 `log` |

---

## 4. 项目目录结构

```
auth-service/
├── pom.xml                          ← Maven 依赖配置（相当于"购物清单"）
└── src/
    ├── main/
    │   ├── java/com/fm/auth/
    │   │   ├── AuthServiceApplication.java      ← 启动类（程序入口）
    │   │   ├── config/
    │   │   │   └── SwaggerConfig.java           ← API 文档配置
    │   │   ├── controller/
    │   │   │   └── AuthController.java          ← 接收 HTTP 请求（第一层）
    │   │   ├── mapper/
    │   │   │   └── AuthMapper.java              ← 数据库操作（最底层）
    │   │   └── service/
    │   │       ├── AuthService.java             ← 业务逻辑接口（定义做什么）
    │   │       └── impl/
    │   │           └── AuthServiceImpl.java     ← 业务逻辑实现（怎么做）
    │   └── resources/
    │       └── application.yml                  ← 配置文件（数据库、端口等）
    └── test/                                    ← 测试代码
```

### 分层架构图

```
HTTP 请求（来自前端）
       ↓
┌─────────────────────────────────────────────┐
│ Controller 层（AuthController）              │
│   负责：接收请求、参数提取、返回响应         │
│   类比：前台接待                             │
└─────────────────────┬───────────────────────┘
                      ↓ 调用
┌─────────────────────────────────────────────┐
│ Service 层（AuthServiceImpl）                │
│   负责：核心业务逻辑（验密、生成Token等）    │
│   类比：业务处理部门                         │
└─────────────────────┬───────────────────────┘
                      ↓ 调用
┌─────────────────────────────────────────────┐
│ Mapper 层（AuthMapper）                      │
│   负责：与数据库通信（查询、增删改）         │
│   类比：仓库管理员                           │
└─────────────────────┬───────────────────────┘
                      ↓ 查询
              MySQL 数据库
```

---

## 5. 数据库设计

### 5.1 User 表结构

auth-service 只访问一张表：**user 表**。

```sql
CREATE TABLE `user` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户唯一ID',
  `username`   VARCHAR(50)  NOT NULL UNIQUE         COMMENT '用户名（登录用）',
  `secret`     VARCHAR(255) NOT NULL                COMMENT '密码（BCrypt加密后的哈希值）',
  `permission` INT          NOT NULL                COMMENT '权限值：1管理员 2顾客 3商户 4司机',
  PRIMARY KEY (`id`)
);
```

### 5.2 示例数据

```
id | username | secret                                                | permission
---|----------|------------------------------------------------------|----------
 1 | admin    | $2a$10$abc...（"admin123"的BCrypt哈希）               | 1
 2 | customer1| $2a$10$xyz...（"pass123"的BCrypt哈希）                | 2
 3 | shop1    | $2a$10$def...（"shop456"的BCrypt哈希）                | 3
 4 | driver1  | $2a$10$ghi...（"drv789"的BCrypt哈希）                 | 4
```

**注意**：`secret` 字段存储的是**哈希值**，不是明文密码！即使数据库被盗，黑客也无法直接知道用户密码。

---

## 6. 核心数据对象（DTO & 实体）

### 什么是 DTO？

**DTO（Data Transfer Object，数据传输对象）** 是专门用于在层与层之间传递数据的简单 Java 类。

**类比**：快递包裹的"快递单"，上面写明了"寄件人、收件人、物品"，但不代表完整的订单信息。

### 6.1 LoginRequestDTO — 登录请求数据

```java
// 前端发来的登录数据（用户填写的表单内容）
public class LoginRequestDTO {
    private String username;  // 用户名
    private String password;  // 密码（明文）
}
```

**对应的 JSON（前端发送）：**
```json
{
  "username": "张三",
  "password": "123456"
}
```

### 6.2 LoginResponseDTO — 登录响应数据

```java
// 服务器返回给前端的数据
public class LoginResponseDTO {
    private String token;        // JWT Token（前端要保存这个！）
    private UserInfoDTO userInfo; // 用户基本信息
    private Long expiration;     // Token有效期（秒），值为7200
}
```

### 6.3 UserInfoDTO — 用户信息

```java
// 嵌套在 LoginResponseDTO 里的用户信息
public class UserInfoDTO {
    private Long userId;     // 用户ID，例如：1
    private String username; // 用户名，例如："张三"
    private String roleCode; // 角色代码，例如："customer"
    private String roleName; // 角色名称，例如："顾客用户"
}
```

### 6.4 User 实体类 — 数据库映射对象

```java
@TableName("user")  // 告诉MyBatis Plus，这个类对应数据库的"user"表
public class User {
    @TableId(type = IdType.AUTO)  // 主键，数据库自增
    private Long id;

    private String username;   // 对应数据库 username 列
    private String secret;     // 对应数据库 secret 列（哈希密码）
    private Integer permission; // 对应数据库 permission 列（权限值）
}
```

**实体类 vs DTO 的区别**：

| 对比项 | 实体类 (User) | DTO (LoginRequestDTO) |
|--------|---------------|-----------------------|
| 对应 | 数据库的一行记录 | 网络传输的数据包 |
| 包含 | 数据库所有字段（含敏感字段） | 只包含需要传递的字段 |
| 作用 | 与数据库交互 | 与外部（前端）交互 |

为什么要区分？假设 User 包含密码哈希，如果直接把 User 返回给前端，密码哈希也会被返回，这是安全漏洞。DTO 让我们精确控制"给前端看什么"。

### 6.5 Result — 统一响应包装器

所有接口的响应都用 `Result<T>` 包裹，形成统一格式：

```java
public class Result<T> {
    private Integer code;       // 状态码：200成功，500失败，4001用户名密码错误...
    private String message;     // 描述信息："success"或错误原因
    private T data;             // 真正的数据（T是泛型，代表任意类型）
    private Long timestamp;     // 响应时间戳（毫秒）
}
```

**泛型 `<T>` 的解释**：`T` 是一个占位符，使用时用具体类型替换：
- `Result<LoginResponseDTO>` → data 里放的是 `LoginResponseDTO`
- `Result<Void>` → data 为 null（登出接口没有返回数据）

**统一格式的好处**：前端只需要统一判断 `code` 是否为 200，不需要为每个接口写不同的解析逻辑。

---

## 7. 完整登录流程详解

### 7.1 数据流动图

```
前端（浏览器/App）                    auth-service                        MySQL数据库
      │                                    │                                    │
      │  POST /api/auth/login              │                                    │
      │  {"username":"张三","password":"123456"}                                │
      │ ─────────────────────────────────→ │                                    │
      │                                    │                                    │
      │                                    │  1. 参数校验（用户名、密码非空）   │
      │                                    │                                    │
      │                                    │  SELECT * FROM user                │
      │                                    │  WHERE username = '张三'           │
      │                                    │ ──────────────────────────────────→│
      │                                    │                                    │
      │                                    │  返回: User{id=1, secret="$2a$...",│
      │                                    │            permission=2}           │
      │                                    │ ←──────────────────────────────────│
      │                                    │                                    │
      │                                    │  2. BCrypt验证密码                 │
      │                                    │  matches("123456", "$2a$...") = true│
      │                                    │                                    │
      │                                    │  3. 查询PermissionEnum            │
      │                                    │  permission=2 → CUSTOMER角色       │
      │                                    │                                    │
      │                                    │  4. 生成JWT Token                  │
      │                                    │  包含: userId=1, roleCode="customer"│
      │                                    │                                    │
      │  HTTP 200 响应                     │                                    │
      │  {code:200, data:{token:"eyJ...", │                                    │
      │   userInfo:{...}, expiration:7200}}│                                    │
      │ ←───────────────────────────────── │                                    │
      │                                    │                                    │
      │ 前端保存 Token 到 localStorage     │                                    │
      │ 后续请求在 Header 中携带 Token     │                                    │
```

### 7.2 七步登录流程

#### 第一步：参数校验

```java
if (loginRequest.getUsername() == null || loginRequest.getUsername().trim().isEmpty()) {
    throw new BusinessException(ResultCode.FAIL.getCode(), "用户名不能为空");
}
if (loginRequest.getPassword() == null || loginRequest.getPassword().trim().isEmpty()) {
    throw new BusinessException(ResultCode.FAIL.getCode(), "密码不能为空");
}
```

- `null`：Java 中表示"什么都没有"，比如用户根本没传 username 字段
- `.trim()`：去掉字符串前后的空格（"  " 空格字符串也不算有效用户名）
- `.isEmpty()`：判断字符串是否为空字符串 `""`
- `throw`：抛出异常，中断程序执行，跳到全局异常处理器

#### 第二步：查询数据库

```java
LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(User::getUsername, loginRequest.getUsername());
User user = authMapper.selectOne(wrapper);
```

**LambdaQueryWrapper 详解：**

`LambdaQueryWrapper` 是 MyBatis Plus 提供的查询条件构建器，用 Java 的方式写 SQL 条件。

```java
// 等价的 SQL：
// SELECT * FROM user WHERE username = '张三'

LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();  // 创建条件构建器
wrapper.eq(User::getUsername, "张三");  // eq 代表 equals（等于）
// User::getUsername 是Java的"方法引用"，等价于 "username" 字段名
// 这样写的好处：如果字段名改了，Java编译器会报错（纯字符串写法不会）
```

**`authMapper.selectOne(wrapper)`**：

- `selectOne`：查询一条记录，如果有多条会报错，如果没有记录返回 `null`
- 其他常用方法：`selectList`（查多条）、`insert`（插入）、`update`（更新）

如果用户不存在（`user == null`），立即抛出错误：
```java
if (user == null) {
    throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
    // 错误码：4001，消息："用户名或密码错误"
}
```

**为什么用户不存在和密码错误返回同样的错误？**

安全考虑：如果分开返回"用户不存在"和"密码错误"，黑客可以通过穷举用户名来探测系统中存在哪些用户。统一返回"用户名或密码错误"，黑客无法区分。

#### 第三步：验证密码

```java
if (!passwordEncoder.matches(loginRequest.getPassword(), user.getSecret())) {
    throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
}
```

`passwordEncoder.matches(rawPassword, encodedPassword)` 的工作原理：

1. 从 `encodedPassword`（`$2a$10$N9qo8...`）中提取**盐值（Salt）**
2. 用同样的盐值对 `rawPassword`（"123456"）进行哈希运算
3. 比较两个哈希值是否相同

**为什么不能直接比较字符串？**

因为 BCrypt 每次加密同一个密码得到的结果不同（盐值随机），所以 `encode("123456")` ≠ `encode("123456")`，必须用 `matches()` 方法。

#### 第四步：获取角色信息

```java
PermissionEnum permissionEnum = PermissionEnum.getByPermission(user.getPermission());
if (permissionEnum == null) {
    throw new BusinessException(ResultCode.FAIL.getCode(), "用户权限配置错误");
}
```

`PermissionEnum` 是一个**枚举类（Enum）**，枚举是一组固定的常量集合：

```java
public enum PermissionEnum {
    ADMIN(1, "管理员", "admin", "/admin/dashboard"),
    CUSTOMER(2, "顾客用户", "customer", "/customer/home"),
    SHOP(3, "商户用户", "shop", "/shop/home"),
    DRIVER(4, "运输员", "driver", "/driver/orders");
    //  ↑权限值  ↑角色名    ↑角色代码  ↑登录后跳转路径
}
```

`getByPermission(2)` 的实现：遍历所有枚举值，找到 `permission == 2` 的那个，返回 `CUSTOMER`。

**为什么用枚举而不是直接用数字？**

```java
// 不用枚举：容易出错，数字没有含义
if (user.getPermission() == 2) { ... }

// 用枚举：代码自解释，不容易写错
if (permissionEnum == PermissionEnum.CUSTOMER) { ... }
```

#### 第五步：生成 JWT Token

```java
String token = jwtUtil.generateToken(
    user.getId(),              // userId: 1
    user.getUsername(),        // username: "张三"
    permissionEnum.getRoleCode() // roleCode: "customer"
);
```

`jwtUtil.generateToken()` 内部做了什么：

```java
public String generateToken(Long userId, String username, String roleCode) {
    // 1. 构建 Payload（载荷）
    Map<String, Object> claims = new HashMap<>();
    claims.put("userId", userId);
    claims.put("username", username);
    claims.put("roleCode", roleCode);

    // 2. 计算过期时间（当前时间 + 7200秒）
    Date expirationDate = new Date(System.currentTimeMillis() + 7200 * 1000);

    // 3. 用 JJWT 库构建 Token
    return Jwts.builder()
        .setClaims(claims)                           // 设置自定义载荷
        .setSubject(username)                        // 设置 sub（主题）
        .setIssuedAt(new Date())                     // 设置 iat（签发时间）
        .setExpiration(expirationDate)               // 设置 exp（过期时间）
        .signWith(getSigningKey(), SignatureAlgorithm.HS256)  // 用密钥签名
        .compact();                                  // 生成最终字符串
}
```

**HS256 签名算法**：使用 HMAC-SHA256 算法，结合密钥（配置文件中的 secret 字符串）对 Token 的 Header 和 Payload 进行计算，得到签名。任何对 Token 内容的篡改都会导致签名校验失败。

#### 第六步：构建用户信息对象

```java
UserInfoDTO userInfo = new UserInfoDTO();
userInfo.setUserId(user.getId());         // 1
userInfo.setUsername(user.getUsername()); // "张三"
userInfo.setRoleCode(permissionEnum.getRoleCode()); // "customer"
userInfo.setRoleName(permissionEnum.getRoleName()); // "顾客用户"
```

注意：这里没有把 `secret`（哈希密码）放进去——不把敏感信息暴露给前端。

#### 第七步：构建并返回响应

```java
LoginResponseDTO loginResponse = new LoginResponseDTO();
loginResponse.setToken(token);        // JWT Token
loginResponse.setUserInfo(userInfo);  // 用户信息
loginResponse.setExpiration(jwtUtil.getExpiration()); // 7200（秒）

return loginResponse;
// AuthController 会把它包装成 Result<LoginResponseDTO> 返回
```

**前端收到响应后应该做的事：**
1. 把 `token` 保存到 `localStorage`（浏览器本地存储）
2. 把 `userInfo` 保存到应用状态（Pinia/Vuex）
3. 根据 `roleCode` 跳转到对应页面（如 customer 跳转到 `/customer/home`）
4. 后续每次 HTTP 请求，在请求头中加入：`Authorization: Bearer eyJhbG...`

---

## 8. 逐文件代码讲解

### 8.1 AuthServiceApplication.java — 程序入口

```java
@SpringBootApplication          // ① 标记为Spring Boot应用
@EnableDiscoveryClient          // ② 开启Nacos服务注册与发现
@EnableFeignClients             // ③ 开启OpenFeign（服务间调用，当前模块暂未使用）
@MapperScan("com.fm.auth.mapper") // ④ 扫描Mapper接口（让Spring管理）
@ComponentScan(basePackages = {"com.fm.auth", "com.fm.common"})  // ⑤ 扫描组件范围
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
```

**注解详解：**

① `@SpringBootApplication`：三合一注解，等价于：
  - `@SpringBootConfiguration`：这是一个配置类
  - `@EnableAutoConfiguration`：启用自动配置（自动配置 Tomcat、数据库连接等）
  - `@ComponentScan`：扫描当前包下的所有组件（`@Service`、`@Controller` 等）

② `@EnableDiscoveryClient`：启动时向 Nacos 注册服务信息（`spring.application.name` + 端口）

④ `@MapperScan("com.fm.auth.mapper")`：告诉 Spring 去哪里找 Mapper 接口，Spring 会自动为接口创建实现类（代理对象）

⑤ `@ComponentScan`：告诉 Spring 不仅扫描 `com.fm.auth` 包，还要扫描 `com.fm.common` 包（因为 `GlobalExceptionHandler`、`JwtUtil` 等都在 common 模块）

### 8.2 AuthController.java — HTTP 接口控制器

```java
@Tag(name = "认证管理", description = "登录、登出等认证相关接口")
@RestController          // ① 标记为RESTful控制器，自动把返回值转为JSON
@RequestMapping("/api/auth")  // ② 所有接口的URL前缀
public class AuthController {

    @Autowired           // ③ 自动注入AuthService（不需要new AuthService()）
    private AuthService authService;

    @Operation(summary = "用户登录", description = "通过用户名和密码登录，返回JWT Token")
    @PostMapping("/login")   // ④ 处理 POST /api/auth/login 请求
    public Result<LoginResponseDTO> login(@RequestBody LoginRequestDTO loginRequest) {
        // ⑤ @RequestBody 把请求体的JSON自动转为Java对象
        LoginResponseDTO loginResponse = authService.login(loginRequest);
        return Result.success(loginResponse);  // ⑥ 包装成统一响应格式
    }

    @Operation(summary = "用户登出")
    @PostMapping("/logout")  // 处理 POST /api/auth/logout 请求
    public Result<Void> logout() {
        authService.logout();
        return Result.success();  // 返回 {code:200, message:"success", data:null}
    }
}
```

**`@RequestBody` 的工作原理**：

前端发来的请求体是 JSON 字符串：`{"username":"张三","password":"123456"}`

Spring 的 `Jackson` 库（内置 JSON 处理器）会自动把这个 JSON 转为 `LoginRequestDTO` 对象：
```
JSON字符串 → Jackson解析 → LoginRequestDTO{username="张三", password="123456"}
```

**控制器为什么这么"薄"？**

控制器只做两件事：接收请求、委托给 Service、返回结果。复杂的业务逻辑在 Service 层，这是"关注点分离"原则。好处是：
- 如果业务逻辑变了，只改 Service，Controller 不用动
- 测试时可以单独测试 Service（不需要启动 HTTP 服务器）

### 8.3 AuthService.java — 服务接口

```java
public interface AuthService {
    LoginResponseDTO login(LoginRequestDTO loginRequest);
    void logout();
}
```

**为什么要有接口（interface）？**

接口定义"做什么"，实现类定义"怎么做"。好处：

1. **面向接口编程**：Controller 只依赖 `AuthService` 接口，不关心具体实现
2. **方便替换**：如果将来要换一种实现方式，只需要写一个新的实现类，Controller 代码不变
3. **便于测试**：测试时可以用 Mock（假的实现）替换真实实现

```java
// AuthController 中的 @Autowired
@Autowired
private AuthService authService;  // 依赖的是接口，不是具体实现类
// Spring 会自动找到实现了AuthService接口的类（AuthServiceImpl）并注入
```

### 8.4 AuthServiceImpl.java — 核心业务逻辑

见第 7 节的详细讲解。这里补充几个技术点：

**`@Service` 注解**：告诉 Spring 这是一个业务逻辑类，Spring 会创建这个类的实例（Bean）并管理其生命周期。

**依赖注入（DI）与控制反转（IoC）**：

传统方式（手动创建对象）：
```java
// 问题：如果AuthMapper需要数据库连接，你得自己创建DataSource，非常麻烦
AuthMapper authMapper = new AuthMapper(dataSource);
AuthServiceImpl service = new AuthServiceImpl(authMapper, jwtUtil);
```

Spring 的方式（自动注入）：
```java
@Service
public class AuthServiceImpl {
    @Autowired
    private AuthMapper authMapper;  // Spring自动创建并注入
    @Autowired
    private JwtUtil jwtUtil;        // Spring自动创建并注入
}
```

**IoC（控制反转）**：对象的创建和管理权从"程序员"转交给"Spring 容器"，程序员只需声明"我需要什么"。

**`BCryptPasswordEncoder` 的实例化方式**：

注意到 `jwtUtil` 用 `@Autowired`，但 `passwordEncoder` 直接 `new`：
```java
private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
```

原因：`BCryptPasswordEncoder` 是一个无状态的工具类（没有需要注入的依赖），直接 `new` 更简单。当然，也可以把它配置成 Spring Bean，取决于设计偏好。

### 8.5 AuthMapper.java — 数据库 Mapper

```java
@Mapper
public interface AuthMapper extends BaseMapper<User> {
    // 这里什么都没有！
}
```

这个接口只有一行代码，但它继承了 `BaseMapper<User>`，从而拥有了大量方法：

```java
// BaseMapper<User> 提供的常用方法（不用写代码就有）：
authMapper.selectById(1L);                // SELECT * FROM user WHERE id = 1
authMapper.selectOne(wrapper);            // SELECT * FROM user WHERE ... LIMIT 1
authMapper.selectList(wrapper);           // SELECT * FROM user WHERE ...
authMapper.insert(user);                  // INSERT INTO user VALUES (...)
authMapper.updateById(user);              // UPDATE user SET ... WHERE id = ?
authMapper.deleteById(1L);               // DELETE FROM user WHERE id = 1
```

**`@Mapper` 注解**：告诉 MyBatis 这是一个 Mapper 接口，MyBatis 会在运行时动态生成实现类（通过 Java 的动态代理技术）。

**`BaseMapper<User>` 的泛型参数**：`<User>` 告诉 MyBatis Plus 这个 Mapper 操作的是 `User` 实体类对应的 `user` 表（通过 `@TableName` 注解关联）。

---

## 9. 关键技术深度解析

### 9.1 JWT 完整工作流程

**生成 Token（登录时）：**

```
用户信息
{userId:1, username:"张三", roleCode:"customer"}
         ↓
    JwtUtil.generateToken()
         ↓
Header: {"alg":"HS256","typ":"JWT"}
         ↓ Base64编码
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9

Payload: {"userId":1,"username":"张三","roleCode":"customer","sub":"张三","iat":1700000000,"exp":1700007200}
         ↓ Base64编码
eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoi5byg5LiJIiwicm9sZUNvZGUiOiJjdXN0b21lciIsInN1YiI6IuW8oOS4iSIsImlhdCI6MTcwMDAwMDAwMCwiZXhwIjoxNzAwMDA3MjAwfQ

Header.Payload → HMAC-SHA256(secret) → Signature
                                      ↓
                             SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c

最终Token = Header + "." + Payload + "." + Signature
```

**验证 Token（后续请求时，由 gateway 或其他服务处理）：**

```
收到Token: eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQi...SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
         ↓
用相同的secret重新计算签名
         ↓
比较计算出的签名 与 Token中的签名 是否一致
         ↓
检查 exp（过期时间）是否 > 当前时间
         ↓
如果都通过 → Token有效 → 解析出userId、roleCode
```

**为什么用 JWT 而不是 Session？**

| 比较项 | Session（传统方式） | JWT（无状态） |
|--------|---------------------|---------------|
| 存储位置 | 服务器内存/Redis | 不需要服务器存储 |
| 微服务兼容 | 需要共享Session，复杂 | 每个服务自己验证，简单 |
| 扩展性 | 服务器多了需要Session同步 | 天然支持分布式 |
| 安全注销 | 删除Session即可 | 需要黑名单（本系统简化处理） |

### 9.2 BCrypt 加密原理

BCrypt 的格式：`$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy`

```
$2a  $10  $N9qo8uLOickgx2ZMRZoMye  IjZAgcfl7p92ldGxad68LJZdL17lhWy
 ↑    ↑    ↑─────────────────────    ↑──────────────────────────────
版本  代价  Salt（22个字符，随机生成）   Hash（31个字符）
     因子
     （10代表2^10=1024次哈希迭代，
       代价越高越慢，越难暴力破解）
```

**BCrypt 验证过程：**

```java
passwordEncoder.matches("123456", "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy")

步骤1：从Hash字符串中提取 Salt = "N9qo8uLOickgx2ZMRZoMye"
步骤2：用 Salt 对 "123456" 进行2^10次哈希运算
步骤3：得到新的Hash值
步骤4：比较新Hash值 与 原Hash值（后31位）是否相同
步骤5：相同 → return true，不同 → return false
```

### 9.3 MyBatis Plus 的 LambdaQueryWrapper

**`LambdaQueryWrapper` vs 普通字符串条件：**

```java
// 方式1：普通字符串（容易写错字段名，编译不报错）
QueryWrapper<User> wrapper = new QueryWrapper<>();
wrapper.eq("username", "张三");  // 如果字段名改为"user_name"，这里会静默失败

// 方式2：Lambda表达式（类型安全，字段名改变时编译报错）
LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(User::getUsername, "张三");  // User::getUsername 引用的是Java方法，字段改名后编译报错
```

**`User::getUsername` 是什么（方法引用）？**

`::`是 Java 8 引入的**方法引用**语法，等价于 `user -> user.getUsername()`：

```java
// 这两种写法等价：
wrapper.eq(User::getUsername, "张三");
wrapper.eq(user -> user.getUsername(), "张三");
```

MyBatis Plus 通过分析 `User::getUsername` 这个方法引用，推断出对应的数据库列名是 `username`（或 `user_name`，根据驼峰转换规则）。

**常用查询方法：**

```java
LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

wrapper.eq(User::getUsername, "张三");     // WHERE username = '张三'
wrapper.ne(User::getPermission, 1);        // WHERE permission != 1
wrapper.gt(User::getId, 5L);              // WHERE id > 5
wrapper.like(User::getUsername, "张");    // WHERE username LIKE '%张%'
wrapper.in(User::getPermission, 2, 3, 4); // WHERE permission IN (2,3,4)
wrapper.orderByDesc(User::getId);         // ORDER BY id DESC
wrapper.last("LIMIT 10");                // 追加原始SQL
```

### 9.4 Spring 依赖注入（IoC/DI）详解

**Spring 容器的工作原理：**

```
程序启动
   ↓
Spring扫描所有 @Service、@Controller、@Repository、@Component 注解的类
   ↓
Spring创建这些类的实例（Bean）
   ↓
Spring处理 @Autowired，自动把对应的Bean注入进去

例如：
AuthServiceImpl 需要 AuthMapper（@Autowired）
AuthServiceImpl 需要 JwtUtil（@Autowired）

Spring 已经创建了 AuthMapper 实例（因为它有@Mapper，被Spring管理）
Spring 已经创建了 JwtUtil 实例（因为它有@Component）

Spring 把这两个实例"注入"到 AuthServiceImpl 中
```

**注入的三种方式（本项目用字段注入）：**

```java
// 方式1：字段注入（本项目使用，代码简洁）
@Autowired
private AuthMapper authMapper;

// 方式2：构造器注入（推荐，方便测试）
public AuthServiceImpl(AuthMapper authMapper, JwtUtil jwtUtil) {
    this.authMapper = authMapper;
    this.jwtUtil = jwtUtil;
}

// 方式3：setter注入（较少使用）
@Autowired
public void setAuthMapper(AuthMapper authMapper) {
    this.authMapper = authMapper;
}
```

### 9.5 枚举类（Enum）详解

```java
public enum PermissionEnum {
    // 每个枚举项都是这个类的一个实例
    ADMIN(1, "管理员", "admin", "/admin/dashboard"),
    CUSTOMER(2, "顾客用户", "customer", "/customer/home"),
    SHOP(3, "商户用户", "shop", "/shop/home"),
    DRIVER(4, "运输员", "driver", "/driver/orders");

    // 每个枚举项的属性
    private final Integer permission;
    private final String roleName;
    private final String roleCode;
    private final String redirectPath;

    // 枚举的构造方法（私有的，只能在枚举内部调用）
    PermissionEnum(Integer permission, String roleName, String roleCode, String redirectPath) {
        this.permission = permission;
        this.roleName = roleName;
        this.roleCode = roleCode;
        this.redirectPath = redirectPath;
    }
}
```

**使用示例：**

```java
PermissionEnum admin = PermissionEnum.ADMIN;
System.out.println(admin.getPermission()); // 1
System.out.println(admin.getRoleCode());   // "admin"
System.out.println(admin.getRoleName());   // "管理员"

// 遍历所有枚举值
for (PermissionEnum p : PermissionEnum.values()) {
    System.out.println(p.name() + ": " + p.getPermission());
}
// 输出：
// ADMIN: 1
// CUSTOMER: 2
// SHOP: 3
// DRIVER: 4

// 根据权限值查找枚举
PermissionEnum found = PermissionEnum.getByPermission(2);
// found = PermissionEnum.CUSTOMER
```

**枚举 vs 常量的区别：**

```java
// 不用枚举（常量方式，容易出错）
public static final int PERMISSION_ADMIN = 1;
public static final int PERMISSION_CUSTOMER = 2;
// 问题：只能存一个值，无法把"角色名"和"角色代码"绑定在一起

// 用枚举（把相关信息组合在一起）
PermissionEnum.CUSTOMER.getPermission() // 2
PermissionEnum.CUSTOMER.getRoleCode()   // "customer"
PermissionEnum.CUSTOMER.getRoleName()   // "顾客用户"
```

---

## 10. 配置文件解读

### application.yml 完整解析

```yaml
server:
  port: 8082              # auth-service 监听 8082 端口
                          # 系统中其他服务的端口：order=8081, customer=8083...

spring:
  application:
    name: auth-service    # 向 Nacos 注册时使用的服务名
                          # 其他服务通过这个名字找到 auth-service
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848  # Nacos 注册中心地址和端口

  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver  # MySQL JDBC 驱动类名
    url: jdbc:mysql://localhost:3306/transportation
         ?useUnicode=true          # 使用Unicode编码
         &characterEncoding=utf8   # 字符集为UTF-8（支持中文）
         &useSSL=false             # 不使用SSL（开发环境）
         &serverTimezone=Asia/Shanghai  # 服务器时区（避免时间差8小时）
    username: admin
    password: 12345678

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    # 数据库字段命名：user_name（下划线）
    # Java字段命名：username（驼峰）
    # 这个配置让MyBatis Plus自动转换，两者可以对应上

    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
    # 开发时打印 SQL 到控制台，方便调试
    # 生产环境应该关闭（设置为 org.apache.ibatis.logging.nologging.NoLoggingImpl）

  global-config:
    db-config:
      id-type: auto   # 主键生成策略：数据库自增（AUTO_INCREMENT）

jwt:
  secret: TransportationSystemSecretKeyForJWTTokenGeneration2024
  # JWT 签名密钥，长度至少32个字符（HS256要求）
  # 生产环境必须换成随机复杂的密钥，不能用这个！

  expiration: 7200    # Token 有效期，单位：秒，7200 秒 = 2 小时
```

### pom.xml 依赖解读

```xml
<!-- 父项目（继承公共配置和版本管理） -->
<parent>
    <groupId>com.fm</groupId>
    <artifactId>Transportation</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</parent>

<artifactId>auth-service</artifactId>

<dependencies>
    <!-- 内部公共模块：包含 User、DTO、JwtUtil、GlobalExceptionHandler 等 -->
    <dependency>
        <groupId>com.fm</groupId>
        <artifactId>common</artifactId>
    </dependency>

    <!-- Spring Boot Web：提供 @RestController、HTTP服务器（Tomcat）等 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- MyBatis Plus：ORM框架，简化数据库操作 -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-boot-starter</artifactId>
    </dependency>

    <!-- MySQL 数据库驱动 -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
    </dependency>

    <!-- Nacos 服务发现：让服务注册到 Nacos -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>

    <!-- OpenFeign：服务间 HTTP 调用（本模块暂未使用，启动类开启了） -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-openfeign</artifactId>
    </dependency>

    <!-- BCrypt 密码加密（来自 Spring Security，只用加密功能） -->
    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-crypto</artifactId>
    </dependency>

    <!-- SpringDoc：自动生成 Swagger API 文档 -->
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>2.2.0</version>
    </dependency>

    <!-- Lombok：减少样板代码（getter/setter等） -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>

    <!-- 测试框架 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## 11. 异常处理机制

### 11.1 异常的传播链

当登录发生错误时，异常是这样传播的：

```
AuthServiceImpl.login()
    ↓ throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR)
    ↓
（异常向上传播，不被捕获）
    ↓
AuthController.login()
    ↓
（异常继续向上传播）
    ↓
GlobalExceptionHandler.handleBusinessException()
    ↓
返回 Result{code:4001, message:"用户名或密码错误", data:null}
```

### 11.2 ResultCode 错误码

```java
public enum ResultCode {
    SUCCESS(200, "操作成功"),               // 正常
    FAIL(500, "操作失败"),                  // 通用错误
    UNAUTHORIZED(401, "未授权，请先登录"),  // 未登录访问需要登录的接口
    FORBIDDEN(403, "无权限访问"),           // 登录了但没有权限（如顾客访问管理员接口）
    USERNAME_OR_PASSWORD_ERROR(4001, "用户名或密码错误"),  // 登录失败
    USER_DISABLED(4002, "用户已被禁用"),    // 账号被禁用（当前未实现）
    TOKEN_INVALID(4003, "Token无效或已过期");  // Token问题（由网关返回）
}
```

**这些状态码是自定义的**（4001、4002、4003）：HTTP 标准只定义了 200、401、403、404、500 等，业务状态码是在这套标准之上的额外约定。

### 11.3 GlobalExceptionHandler

```java
@RestControllerAdvice  // 这是一个全局控制器增强，能拦截所有Controller的异常
@Slf4j                 // Lombok注解，自动创建 private Logger log = LoggerFactory.getLogger(...)
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)  // 只处理BusinessException类型的异常
    public Result<Object> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());  // 记录日志（warn级别，不是错误）
        // {} 是占位符，e.getMessage() 会替换它，避免字符串拼接（性能优化）

        Result<Object> result = Result.error(e.getMessage());  // 创建错误响应
        result.setCode(e.getCode());  // 使用BusinessException自带的错误码（如4001）
        return result;
    }

    @ExceptionHandler(Exception.class)  // 兜底：处理所有其他未被捕获的异常
    public Result<Object> handleException(Exception e) {
        log.error("系统异常", e);  // error级别，打印完整堆栈信息
        return Result.error("系统内部错误，请稍后重试");  // 不暴露内部错误详情给用户
    }
}
```

**`@RestControllerAdvice` 原理**：它是一个 AOP（面向切面编程）实现，Spring 在所有 Controller 方法外面套了一层"拦截器"，当方法抛出异常时，不是让程序崩溃，而是调用对应的 `@ExceptionHandler` 方法生成正常的 HTTP 响应。

---

## 12. 接口文档（Swagger）

### 12.1 什么是 Swagger？

Swagger 是一种 API 文档规范，可以自动生成一个网页，让开发者直接在网页上测试接口（不需要 Postman 等工具）。

### 12.2 访问 Swagger UI

auth-service 启动后，访问：
```
http://localhost:8082/swagger-ui/index.html
```

你会看到一个网页，上面列出了所有接口，可以直接点击"Try it out"按钮测试。

### 12.3 Swagger 注解

```java
@Tag(name = "认证管理", description = "登录、登出等认证相关接口")
// 对应Swagger文档中的"分组标签"，把相关接口归为一组

@Operation(summary = "用户登录", description = "通过用户名和密码登录，返回JWT Token")
// 描述单个接口的功能摘要和详情
```

### 12.4 SwaggerConfig.java

```java
@Configuration  // 标记为配置类（Spring会执行@Bean方法）
public class SwaggerConfig {

    @Bean  // 返回值会被Spring作为Bean管理（相当于注册到Spring容器）
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("物流管理系统 - 认证服务API文档")  // 文档标题
                .version("1.0.0")
                .description("认证服务接口文档"));         // 文档描述
    }
}
```

---

## 13. 与其他服务的关系

### 13.1 auth-service 在整体架构中的位置

```
                    前端（Vue.js）
                        │
                        ↓
              ┌────────────────────┐
              │   gateway-service  │  ← 网关（8080端口，流量入口）
              │   （请求路由+JWT验证）│
              └────┬──────────┬───┘
                   │          │
          ┌────────┘          └──────────┐
          ↓                             ↓
┌──────────────────┐         ┌──────────────────────┐
│   auth-service   │         │  其他业务服务          │
│   端口：8082     │         │  order、driver、shop等 │
│   功能：登录认证  │         │                      │
└──────────────────┘         └──────────────────────┘
          │
          ↓（查询用户）
     MySQL 数据库
     transportation.user 表
```

### 13.2 登录后的 Token 使用

```
① 用户登录（POST /api/auth/login）
        ↓
② auth-service 返回 Token = "eyJhbGci..."
        ↓
③ 前端保存 Token 到 localStorage
        ↓
④ 用户访问 /api/order/list（需要登录）
        ↓
⑤ 前端在请求头加上：Authorization: Bearer eyJhbGci...
        ↓
⑥ gateway-service 拦截请求，验证 Token（调用 JwtUtil.validateToken）
        ↓
⑦ Token 有效 → 放行，将 userId 和 roleCode 注入请求头
        ↓
⑧ order-service 从请求头读取用户信息，处理业务
```

### 13.3 为什么登出是"无状态"的？

```java
@Override
public void logout() {
    // 无状态JWT，token由前端清除，服务端无需额外操作
}
```

JWT 是**无状态**的——服务端不保存任何 Session 信息。所谓"登出"，只需要前端删除本地的 Token：

```javascript
// 前端 Vue.js 代码（登出逻辑）
function logout() {
    localStorage.removeItem('token');  // 删除本地Token
    router.push('/login');             // 跳转到登录页
}
```

**缺点**：如果 Token 被盗，在有效期内无法使其失效（需要 Token 黑名单机制，本系统未实现，因为有效期只有 2 小时，风险较低）。

---

## 14. 常见问题 Q&A

**Q：为什么用户名和密码错误时，错误信息一样（"用户名或密码错误"）？**

A：安全设计。如果分别返回"用户不存在"和"密码错误"，黑客可以通过穷举探测哪些用户名存在。统一错误信息让黑客无法区分。

---

**Q：JWT Token 可以伪造吗？**

A：不能（在计算上不可行）。签名是用密钥通过 HMAC-SHA256 算法生成的，没有密钥就无法生成有效签名。如果篡改了 Payload（如把 roleCode 改成"admin"），签名就会失效，服务端验证时会拒绝。

---

**Q：为什么密码要用 BCrypt 而不是 MD5？**

A：MD5 已经被破解，有"彩虹表"（预计算的哈希值对照表）可以快速反推。BCrypt 设计为慢速哈希（每次验证需要数百毫秒），大大增加了暴力破解的时间成本，且加盐防止彩虹表攻击。

---

**Q：Nacos 挂了，auth-service 还能用吗？**

A：auth-service 本身还能处理请求，因为它的服务在启动时已经完成注册。但其他服务可能无法找到 auth-service（服务发现失败），新启动的服务也无法注册。生产环境 Nacos 需要集群部署保证高可用。

---

**Q：`@Autowired` 和 `new` 有什么本质区别？**

A：`new` 是你自己创建对象，你要负责对象的生命周期和依赖管理。`@Autowired` 是让 Spring 容器创建和管理对象，Spring 负责在合适的时机创建、注入和销毁对象，还能提供单例、代理等高级特性。

---

**Q：Token 过期后怎么办？**

A：前端检测到响应 code 为 4003（Token 无效或已过期），自动跳转到登录页面，要求用户重新登录。更好的方案是实现"刷新 Token"机制（本系统未实现）。

---

**Q：`JJWT` 库是什么？**

A：`JJWT（Java JWT）`是一个开源的 Java 库，封装了 JWT 的生成、解析和验证逻辑。它在 `common` 模块的 `pom.xml` 中引入，被 auth-service 通过依赖 common 模块间接使用。

---

## 附录：核心知识点速查表

| 知识点 | 作用 | 在本模块的使用 |
|--------|------|----------------|
| Spring Boot | Web服务框架 | 整个服务的运行基础 |
| `@RestController` | 标记HTTP控制器 | `AuthController` |
| `@RequestBody` | JSON转Java对象 | 解析登录请求 |
| `@Service` | 标记业务逻辑类 | `AuthServiceImpl` |
| `@Autowired` | 依赖注入 | 注入AuthMapper、JwtUtil |
| `@Mapper` | 标记MyBatis Mapper | `AuthMapper` |
| `BaseMapper<T>` | 通用数据库操作 | 继承自动获得CRUD方法 |
| `LambdaQueryWrapper` | 类型安全的查询条件 | 按用户名查询User |
| `@TableName` | 映射数据库表 | User → user表 |
| `@TableId` | 主键配置 | id字段自增 |
| `@Data` (Lombok) | 生成getter/setter等 | 所有DTO和实体类 |
| `BCryptPasswordEncoder` | 密码哈希验证 | 登录时验证密码 |
| `JwtUtil` | JWT令牌工具类 | 生成和解析Token |
| `PermissionEnum` | 角色权限枚举 | 权限值→角色信息映射 |
| `Result<T>` | 统一响应格式 | 所有接口的返回值 |
| `BusinessException` | 业务异常 | 参数错误、认证失败时抛出 |
| `GlobalExceptionHandler` | 全局异常处理 | 捕获并格式化所有异常 |
| `ResultCode` | 错误码枚举 | 标准化的错误码和消息 |
| `@Value` | 读取配置文件 | 读取JWT密钥和过期时间 |
| Nacos | 服务注册与发现 | auth-service向Nacos注册 |
| `@EnableDiscoveryClient` | 开启服务发现 | 启动类注解 |
| Swagger/SpringDoc | API文档 | 自动生成接口文档页面 |
| `@Tag` / `@Operation` | Swagger注解 | 描述接口信息 |
