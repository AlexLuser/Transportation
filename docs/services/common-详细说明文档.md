# Common 公共模块 — 完全初学者指南

> common 模块是整个系统的"基础设施层"，本身不是一个独立运行的服务，而是被所有其他微服务模块所依赖的**公共类库**。理解 common 模块，是理解整个系统的前提。

---

## 目录

1. [这个模块是做什么的？](#1-这个模块是做什么的)
2. [目录结构](#2-目录结构)
3. [实体类（entity）](#3-实体类entity)
4. [数据传输对象（dto）](#4-数据传输对象dto)
5. [统一响应体（result）](#5-统一响应体result)
6. [权限枚举（constant）](#6-权限枚举constant)
7. [JWT工具类（util）](#7-jwt工具类util)
8. [异常体系（exception）](#8-异常体系exception)
9. [高德地理编码（geo）](#9-高德地理编码geo)
10. [依赖关系与编译原理](#10-依赖关系与编译原理)
11. [关键技术深度解析](#11-关键技术深度解析)

---

## 1. 这个模块是做什么的？

### 1.1 为什么需要公共模块？

假设没有 common 模块，每个服务都要各自定义"用户类"、"统一响应体"、"JWT工具"……

```
auth-service 定义：class User { String username; }
order-service 定义：class User { String userName; }   ← 字段名写错了！
driver-service 定义：class User { String name; }      ← 又不一样！
```

这样会导致：
- **代码重复**：同样的类写了5遍
- **不一致**：各服务对"用户"的理解不同
- **难以维护**：改一个字段，要改5个地方

**common 模块的解决方案**：把所有服务都需要用的"公共代码"集中放在一个地方，所有服务都来引用它。就像一栋办公楼的"公共设施"（电梯、停车场、大堂），大家共用，不用每个租户各自建。

### 1.2 common 模块不能独立运行

common 模块没有 `main` 方法，没有端口，**无法单独启动**。它被打包成一个 `.jar` 文件，供其他服务在编译时引入：

```xml
<!-- 其他服务的 pom.xml 中 -->
<dependency>
    <groupId>com.fm.Transportation</groupId>
    <artifactId>common</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

---

## 2. 目录结构

```
common/
└── src/main/java/com/fm/common/
    ├── constant/
    │   └── PermissionEnum.java     ← 角色权限枚举（4种角色）
    ├── dto/
    │   ├── LoginRequestDTO.java    ← 登录请求数据
    │   ├── LoginResponseDTO.java   ← 登录响应数据
    │   ├── UserInfoDTO.java        ← 用户基本信息
    │   └── PageResult.java         ← 通用分页结果
    ├── entity/
    │   └── User.java               ← 用户数据库实体
    ├── exception/
    │   ├── BusinessException.java  ← 业务异常类
    │   └── GlobalExceptionHandler.java ← 全局异常处理器
    ├── geo/
    │   ├── GeoPoint.java                   ← 经纬度坐标值对象（Java record）
    │   ├── AmapProperties.java             ← 高德配置项（key、超时、开关）
    │   ├── AmapGeocodingConfiguration.java ← HTTP 客户端 Bean 注册
    │   └── AmapGeocodingService.java       ← 地理编码 HTTP 调用（Spring Bean）
    ├── result/
    │   ├── Result.java             ← 统一响应包装器
    │   └── ResultCode.java         ← 响应码枚举
    └── util/
        └── JwtUtil.java            ← JWT 令牌工具
```

---

## 3. 实体类（entity）

### 3.1 User.java

```java
@Data
@TableName("user")     // MyBatis Plus：这个类对应数据库的 user 表
public class User {
    @TableId(type = IdType.AUTO)  // 主键，数据库自增
    private Long id;

    private String username;    // 登录用户名，唯一

    private String secret;      // BCrypt 加密后的密码哈希值
                                // 字段名用 secret 而非 password，避免泄露含义

    private Integer permission; // 权限值：1=管理员, 2=顾客, 3=商户, 4=运输员
}
```

**对应的数据库表：**

```sql
CREATE TABLE `user` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `username`   VARCHAR(50)  NOT NULL UNIQUE,
  `secret`     VARCHAR(255) NOT NULL,
  `permission` INT          NOT NULL,
  PRIMARY KEY (`id`)
);
```

**`Long` vs `Integer` vs `int`：**

| 类型 | 说明 | 使用场景 |
|------|------|----------|
| `int` | Java基本类型，不能为null | 简单计数 |
| `Integer` | int的包装类，可以为null | 数据库可能为null的整数字段 |
| `Long` | 64位整数，可以为null | 数据库主键（数据量大时超过int范围） |

主键用 `Long` 而不是 `Integer`，是因为理论上数据量可能超过 21 亿（int的最大值），`Long` 的最大值约为 922 亿亿，足够用了。

---

## 4. 数据传输对象（dto）

### 什么是 DTO？

**DTO（Data Transfer Object）** 是专门用于在"层与层"或"服务与服务"之间传递数据的简单对象。

**类比**：网购的"快递单"，上面只写了必要信息（收件人、地址、商品），而不是把整个仓库信息都打印上去。

### 4.1 LoginRequestDTO — 登录请求

```java
@Data
public class LoginRequestDTO implements Serializable {
    private String username;  // 用户名
    private String password;  // 明文密码（仅在传输中，不会存入数据库）
}
```

**`Serializable` 接口解释**：

`Serializable` 是一个"标记接口"（没有任何方法），表示这个类的对象可以被序列化。

- **序列化**：将 Java 对象转换为字节流（比如 JSON 字符串）
- **反序列化**：将字节流还原为 Java 对象

网络传输时，数据必须是字节流格式。标记 `Serializable` 后，框架（如 Jackson）才能将对象转换为 JSON，再通过网络发送。

**请求示例（前端发来的 JSON）：**
```json
{"username": "张三", "password": "123456"}
```

### 4.2 LoginResponseDTO — 登录响应

```java
@Data
public class LoginResponseDTO {
    private String token;        // JWT Token，前端要保存的"通行证"
    private UserInfoDTO userInfo; // 用户信息（嵌套对象）
    private Long expiration;     // Token有效期（秒），值为7200
}
```

**响应示例（服务器返回的 JSON）：**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.abc",
  "userInfo": {
    "userId": 1,
    "username": "张三",
    "roleCode": "customer",
    "roleName": "顾客用户"
  },
  "expiration": 7200
}
```

### 4.3 UserInfoDTO — 用户信息

```java
@Data
public class UserInfoDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;     // 用户ID
    private String username; // 用户名
    private String roleCode; // 角色代码："admin"/"customer"/"shop"/"driver"
    private String roleName; // 角色名称："管理员"/"顾客用户"/"商户用户"/"运输员"
}
```

**`@Serial` 和 `serialVersionUID` 解释**：

```java
@Serial
private static final long serialVersionUID = 1L;
```

`serialVersionUID` 是序列化的"版本号"。当你向类中增加新字段时，JVM 用这个版本号判断序列化前后的类是否兼容：
- 版本号相同 → 兼容（可以反序列化旧数据）
- 版本号不同 → 抛出 `InvalidClassException`

`@Serial` 注解（Java 14+）告诉编译器：这个字段是序列化相关的，帮你做额外的正确性检查。

`1L` 中的 `L` 表示这是一个 `long` 类型字面量（区别于 `int` 类型的 `1`）。

### 4.4 PageResult — 通用分页结果

**为什么需要分页？**

假设数据库里有 10 万条订单，一次全返回：
- 数据库查询慢（全表扫描）
- 网络传输慢（数据量大）
- 前端渲染慢（DOM元素太多）

分页：每次只返回第 1 页的 20 条记录，用户翻页时再请求第 2 页。

```java
@Data
public class PageResult<T> {
    private Long current;   // 当前第几页（从1开始）
    private Long size;      // 每页几条
    private Long total;     // 总记录数（数据库里共有多少条）
    private Long pages;     // 总页数（自动计算）
    private List<T> records; // 当前页的数据列表

    public PageResult(Long current, Long size, Long total, List<T> records) {
        this.current = current;
        this.size = size;
        this.total = total;
        this.records = records;
        // 向上取整：10条记录，每页3条 → (10+3-1)/3 = 4页
        this.pages = (total + size - 1) / size;
    }

    // 空结果快捷构造（查询结果为0条时使用）
    public static <T> PageResult<T> empty(Long current, Long size) {
        return new PageResult<>(current, size, 0L, List.of());
    }
}
```

**`<T>` 泛型解释**：

`T` 是一个类型占位符，使用时替换为具体类型：
- `PageResult<Order>` → `records` 是 `List<Order>`（订单列表）
- `PageResult<Driver>` → `records` 是 `List<Driver>`（司机列表）

这样一个 `PageResult` 类就能服务于所有分页场景，不需要为每种数据写一个 `OrderPageResult`、`DriverPageResult`……

**分页响应示例：**
```json
{
  "current": 1,
  "size": 10,
  "total": 35,
  "pages": 4,
  "records": [
    {"id": 1, "orderNo": "ORD20240101120001"},
    {"id": 2, "orderNo": "ORD20240101120002"}
  ]
}
```

---

## 5. 统一响应体（result）

### 5.1 Result.java — 所有接口的响应格式

**为什么需要统一格式？**

没有统一格式时，不同接口返回不同的东西：
```
登录成功 → {"token": "abc", "user": {...}}
查询订单 → {"order": {...}, "status": "ok"}
删除商品 → true
报错了   → {"error": "出错了"}
```

前端需要为每个接口写不同的解析代码，非常麻烦。

有了统一格式：
```java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result<T> {
    private Integer code;      // 状态码：200成功，非200失败
    private String message;    // 描述："success"或错误原因
    private T data;            // 真正的数据（T是泛型占位符）
    private Long timestamp;    // 响应时间戳（毫秒）

    // 静态工厂方法（比直接new更清晰）
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data, System.currentTimeMillis());
    }
    public static Result<Void> success() {
        return new Result<>(200, "success", null, System.currentTimeMillis());
    }
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null, System.currentTimeMillis());
    }
}
```

**`@AllArgsConstructor` 和 `@NoArgsConstructor` 解释**：

- `@AllArgsConstructor`：生成包含所有字段的构造方法：
  ```java
  public Result(Integer code, String message, T data, Long timestamp) { ... }
  ```
- `@NoArgsConstructor`：生成无参构造方法：
  ```java
  public Result() { }
  ```

为什么两个都要？
- `@AllArgsConstructor`：内部代码用 `new Result<>(200, "success", data, ...)` 创建
- `@NoArgsConstructor`：Jackson（JSON库）反序列化时需要无参构造方法

**`Void` 类型解释**：

`Result<Void>` 表示 data 字段为 null（没有返回数据）。`Void` 是 Java 提供的"空类型"占位符，专门用于泛型场景表示"没有数据"。比登出接口（logout）就用 `Result<Void>`，因为登出不需要返回任何数据。

**统一格式的所有响应示例：**

```json
// 登录成功
{"code": 200, "message": "success", "data": {"token": "...", "userInfo": {...}}, "timestamp": 1700000000000}

// 登出成功（无data）
{"code": 200, "message": "success", "data": null, "timestamp": 1700000000000}

// 用户名密码错误
{"code": 4001, "message": "用户名或密码错误", "data": null, "timestamp": 1700000000000}

// 服务器错误
{"code": 500, "message": "系统内部错误，请稍后重试", "data": null, "timestamp": 1700000000000}
```

前端只需要统一判断 `code` 是否为 200，大大简化了处理逻辑。

### 5.2 ResultCode.java — 响应码枚举

```java
@Getter
@AllArgsConstructor
public enum ResultCode {
    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),
    UNAUTHORIZED(401, "未授权，请先登录"),      // HTTP 401：未认证
    FORBIDDEN(403, "无权限访问"),               // HTTP 403：已认证但无权限
    USERNAME_OR_PASSWORD_ERROR(4001, "用户名或密码错误"),  // 自定义业务码
    USER_DISABLED(4002, "用户已被禁用"),
    TOKEN_INVALID(4003, "Token无效或已过期");

    private final Integer code;
    private final String message;
}
```

**HTTP 标准状态码 vs 自定义业务码：**

| 状态码 | 来源 | 含义 |
|--------|------|------|
| 200 | HTTP标准 | 请求成功 |
| 401 | HTTP标准 | 未认证（没有Token或Token无效） |
| 403 | HTTP标准 | 已认证但无权限（比如顾客访问管理员接口） |
| 500 | HTTP标准 | 服务器内部错误 |
| 4001 | 自定义 | 用户名或密码错误（业务层面的错误） |
| 4002 | 自定义 | 账号被禁用 |
| 4003 | 自定义 | Token相关问题 |

自定义码（4001~4003）是在 HTTP 200（请求本身成功）的前提下，在响应体的 `code` 字段传递的业务错误码。

---

## 6. 权限枚举（constant）

### PermissionEnum.java

```java
public enum PermissionEnum {
    //           权限值  角色名      角色代码     登录后跳转
    ADMIN   (1, "管理员",   "admin",    "/admin/dashboard"),
    CUSTOMER(2, "顾客用户", "customer", "/customer/home"),
    SHOP    (3, "商户用户", "shop",     "/shop/home"),
    DRIVER  (4, "运输员",   "driver",   "/driver/orders");

    private final Integer permission;  // 存在数据库里的数字
    private final String roleName;     // 显示给用户看的中文名
    private final String roleCode;     // 存在JWT里、用于权限判断的英文代码
    private final String redirectPath; // 登录后前端跳转的路径

    // 根据数字找枚举（登录时用）
    public static PermissionEnum getByPermission(Integer permission) {
        for (PermissionEnum e : values()) {
            if (e.permission.equals(permission)) return e;
        }
        return null;  // 数据库里有无效的权限值时返回null
    }
}
```

**枚举的本质**：枚举中的每个项（ADMIN、CUSTOMER等）都是这个枚举类的一个**固定实例**，它们在程序启动时就被创建好，不会改变。

**使用场景：**

```java
// 登录时：从数据库拿到 permission=2，转为枚举
PermissionEnum role = PermissionEnum.getByPermission(2);
// role = PermissionEnum.CUSTOMER

// 取出各种信息
role.getRoleCode();     // "customer"
role.getRoleName();     // "顾客用户"
role.getRedirectPath(); // "/customer/home"

// 注入JWT
jwtUtil.generateToken(userId, username, role.getRoleCode());
```

**为什么不直接存"customer"字符串而是存数字1/2/3/4？**

数字在数据库中占用空间更小（INT 4字节 vs VARCHAR 20字节），索引效率更高，查询更快。

---

## 7. JWT工具类（util）

### JwtUtil.java 完整能力

```java
@Component  // 注册为Spring Bean，可以被@Autowired注入
public class JwtUtil {

    @Value("${jwt.secret:TransportationSystemSecretKeyForJWTTokenGeneration2024}")
    private String secret;  // 从配置文件读取签名密钥

    @Value("${jwt.expiration:7200}")
    private Long expiration; // 从配置文件读取过期时间（秒）
```

**`@Value("${key:defaultValue}")` 解释**：

- `${jwt.secret}` → 从 `application.yml` 中读取 `jwt.secret` 的值
- `:TransportationSystem...` → 冒号后是**默认值**，如果配置文件中没有这个配置项，就使用默认值
- 好处：开发环境用默认密钥，生产环境在配置文件中覆盖为更安全的密钥

**七个方法详解：**

```java
// ① 生成Token（登录时调用）
String generateToken(Long userId, String username, String roleCode)

// ② 解析Token，返回所有载荷数据（内部使用）
Claims getClaimsFromToken(String token)

// ③ 从Token中取用户ID
Long getUserIdFromToken(String token)

// ④ 从Token中取用户名
String getUsernameFromToken(String token)

// ⑤ 从Token中取角色代码
String getRoleCodeFromToken(String token)

// ⑥ 验证Token是否有效（网关调用）
boolean validateToken(String token)

// ⑦ 获取配置的过期时间（返回给前端）
Long getExpiration()
```

**`generateToken()` 内部流程图：**

```
输入：userId=1, username="张三", roleCode="customer"
       ↓
构建 claims Map:
  {"userId": 1, "username": "张三", "roleCode": "customer"}
       ↓
Jwts.builder()
  .setClaims(claims)      // 自定义载荷
  .setSubject("张三")     // 标准载荷 sub
  .setIssuedAt(now)       // 标准载荷 iat（签发时间）
  .setExpiration(now+7200s) // 标准载荷 exp（过期时间）
  .signWith(key, HS256)   // 用密钥签名
  .compact()              // 生成字符串
       ↓
输出：eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.SflKxwRJSM...
```

**`getClaimsFromToken()` 的 try-catch：**

```java
public Claims getClaimsFromToken(String token) {
    try {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    } catch (Exception e) {
        return null;  // Token无效、过期、格式错误时，返回null而不是崩溃
    }
}
```

`try-catch` 是 Java 的**异常处理**语法：
- `try` 块：尝试执行的代码（可能抛出异常）
- `catch(Exception e)`：捕获所有异常，执行处理逻辑
- 这里的处理逻辑是：返回 null，上层代码根据 null 判断 Token 无效

---

## 8. 异常体系（exception）

### 8.1 BusinessException — 业务异常

**什么是异常（Exception）？**

程序执行过程中发生的"意外情况"，比如：
- 用户输入了错误的密码
- 查询的记录不存在
- 网络连接超时

Java 用异常机制处理这些情况：当发现问题时，`throw` 一个异常对象，程序立即停止当前执行流，把控制权交给能处理这个异常的代码。

```java
@Getter
public class BusinessException extends RuntimeException {
    // RuntimeException：运行时异常，不需要在方法签名上声明
    // vs checked Exception：必须在方法上声明 throws 或 try-catch

    private final Integer code;  // 业务错误码（如4001）

    // 构造方式1：自定义错误码和消息
    public BusinessException(Integer code, String message) {
        super(message);  // 调用父类构造方法，设置异常消息
        this.code = code;
    }

    // 构造方式2：直接传入枚举（最常用）
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    // 构造方式3：传枚举+自定义消息（覆盖枚举中的默认消息）
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }
}
```

**使用示例：**
```java
// 方式1
throw new BusinessException(500, "密码格式不正确");

// 方式2（最简洁，推荐）
throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
// 等价于：throw new BusinessException(4001, "用户名或密码错误")

// 方式3
throw new BusinessException(ResultCode.FAIL, "收货地址不存在");
// 等价于：throw new BusinessException(500, "收货地址不存在")
```

**`RuntimeException` vs 普通 `Exception`：**

Java 的异常分两类：
- **Checked Exception（受检异常）**：方法必须声明 `throws` 或在调用处 `try-catch`，如 `IOException`
- **Unchecked Exception（运行时异常）**：不需要强制处理，如 `RuntimeException`

`BusinessException extends RuntimeException`：选择运行时异常，原因是业务异常可能在任何方法中抛出，如果强制 `throws`，每个方法签名都要写 `throws BusinessException`，非常冗余。

### 8.2 GlobalExceptionHandler — 全局异常处理器

```java
@Slf4j               // Lombok: 生成日志对象 private static final Logger log = ...
@RestControllerAdvice // AOP增强所有Controller，拦截异常
public class GlobalExceptionHandler {

    // 处理业务异常（优先级最高，精确匹配）
    @ExceptionHandler(BusinessException.class)
    public Result<Object> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        // {} 是占位符，SLF4J 的懒加载格式化（比字符串拼接高效）
        Result<Object> result = Result.error(e.getMessage());
        result.setCode(e.getCode());  // 用业务异常自带的code（如4001）
        return result;
    }

    // 处理参数校验异常
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Object> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        return Result.error(e.getMessage());  // 使用默认500码
    }

    // 兜底处理（捕获所有未预料的异常）
    @ExceptionHandler(Exception.class)
    public Result<Object> handleException(Exception e) {
        log.error("系统异常", e);  // error级别，输出完整堆栈信息
        return Result.error("系统内部错误，请稍后重试");
        // 不把内部错误详情暴露给用户（安全考虑）
    }
}
```

**`@RestControllerAdvice` 工作原理（AOP/面向切面）：**

Spring 在所有 Controller 方法的外面包了一层"切面"：

```
用户发请求
    ↓
Controller.someMethod() 被调用
    ↓
[AOP切面拦截异常]
    ├── 正常执行完毕 → 返回结果给用户
    └── 抛出异常 → GlobalExceptionHandler 捕获 → 返回统一格式的错误响应
```

没有这个全局处理器，未捕获的异常会让 Spring 返回默认的 HTML 错误页面，前端无法解析。

**日志级别说明（SLF4J + Logback）：**

| 级别 | 方法 | 使用场景 |
|------|------|----------|
| DEBUG | `log.debug()` | 开发调试信息 |
| INFO | `log.info()` | 正常操作记录 |
| WARN | `log.warn()` | 业务警告（参数错误等） |
| ERROR | `log.error()` | 系统错误，需要紧急处理 |

---

## 9. 高德地理编码（geo）

common 模块中新增了 `geo` 包，为需要「将中文地址文本转为经纬度坐标」的服务提供统一的地理编码能力，目前被 customer-service（收货地址）和 shop-service（仓库地址）共同使用。

### 9.1 GeoPoint.java — 经纬度值对象

```java
public record GeoPoint(double latitude, double longitude) {}
```

**`record` 类型（Java 16+）**：`record` 是专门用于"只存数据、不需要修改"的场景。编译器自动生成构造方法、`equals()`、`hashCode()`、`toString()`，无需 `@Data` 注解。

`GeoPoint` 只有两个字段：纬度（latitude，南北方向）和经度（longitude，东西方向）。高德地理编码返回结果后就封装成这个对象，传递给业务层写入实体。

### 9.2 AmapProperties.java — 配置绑定

```java
@ConfigurationProperties(prefix = "amap")
public class AmapProperties {
    private boolean enabled = true;       // 是否启用（Key 为空时自动无效）
    private String key = "";              // 高德 Web 服务 Key
    private int connectTimeoutMs = 5000;  // 连接超时（毫秒）
    private int readTimeoutMs = 8000;     // 读取超时（毫秒）
}
```

**`@ConfigurationProperties` 工作原理**：

Spring Boot 会把 `application.yml` 中以 `amap` 为前缀的配置项自动绑定到这个类的字段上：

```yaml
amap:
  enabled: true
  key: ${AMAP_KEY:}         # 从环境变量 AMAP_KEY 读取，不存在时为空串
  connect-timeout-ms: 5000
  read-timeout-ms: 8000
```

注意配置键是 `connect-timeout-ms`（短横线），而 Java 字段是 `connectTimeoutMs`（驼峰）——Spring Boot 的"Relaxed Binding"（宽松绑定）会自动处理这两种写法的映射。

**`${AMAP_KEY:}` 写法解释**：

- `${AMAP_KEY}` → 读取环境变量 `AMAP_KEY`，不存在时启动报错
- `${AMAP_KEY:}` → 冒号后是默认值（此处默认值为空串），不存在时使用默认值而不报错

生产环境中通过 `export AMAP_KEY=你的Key` 注入，避免 Key 出现在代码仓库里。Key 为空时，`AmapGeocodingService` 会直接跳过编码，什么都不做。

### 9.3 AmapGeocodingService.java — HTTP 调用核心

```java
@Service
public class AmapGeocodingService {

    private static final String GEOCODE_URL = "https://restapi.amap.com/v3/geocode/geo";

    // 失败时返回 Optional.empty()，不抛异常，不阻断业务
    public Optional<GeoPoint> geocode(String province, String city,
                                       String district, String detailAddress) {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getKey())) {
            return Optional.empty();  // Key 未配置，直接跳过
        }

        // 1. 拼完整地址："上海市上海市浦东新区陆家嘴环路1000号"
        String full = buildFullAddress(province, city, district, detailAddress);

        // 2. 构建请求 URI 并发送 GET 请求
        URI uri = UriComponentsBuilder.fromUriString(GEOCODE_URL)
                .queryParam("address", full)
                .queryParam("key", properties.getKey())
                .queryParam("city", city)
                .encode(StandardCharsets.UTF_8)
                .build().toUri();

        String body = restTemplate.getForObject(uri, String.class);

        // 3. 解析响应
        // 高德返回格式：{"status":"1","geocodes":[{"location":"121.5050,31.2356",...}]}
        // location 格式：经度,纬度（注意顺序！先经度后纬度）
        String location = objectMapper.readTree(body)
                .path("geocodes").get(0)
                .path("location").asText();

        String[] parts = location.split(",");          // ["121.5050", "31.2356"]
        double lng = Double.parseDouble(parts[0]);     // 经度（第一个）
        double lat = Double.parseDouble(parts[1]);     // 纬度（第二个）

        return Optional.of(new GeoPoint(lat, lng));
    }
}
```

**高德响应格式示例**：

```json
{
  "status": "1",
  "info": "OK",
  "geocodes": [
    {
      "formatted_address": "上海市浦东新区陆家嘴环路",
      "location": "121.5050,31.2356",
      "level": "门牌号"
    }
  ]
}
```

注意：高德的 `location` 字段格式是**经度在前，纬度在后**（`longitude,latitude`），与常见的"纬度,经度"顺序相反，解析时须特别注意。

**`Optional<GeoPoint>` 的设计意图**：

`Optional` 是 Java 8 引入的容器类型，表示"可能有值，可能没有"：
- `Optional.of(value)` → 有值（成功拿到坐标）
- `Optional.empty()` → 无值（编码失败或被跳过）

使用 `Optional` 而不是直接返回 `null`，好处是调用方被迫处理"没有坐标"的情况（编译期提醒），不会忘记 null 检查。语义上也更清晰：编码失败是"正常的无结果"，而不是异常。

**失败不阻断业务**：网络超时、高德配额用尽、地址无法识别等情况下，Service 直接 `log.warn()` 后返回 `Optional.empty()`。业务层检测到 empty 时，不填坐标、照常保存地址，物流路线规划遇到坐标为 `null` 时会降级处理。

### 9.4 AmapGeocodingConfiguration.java — Bean 注册

```java
@Configuration
@EnableConfigurationProperties(AmapProperties.class)  // 激活 @ConfigurationProperties
public class AmapGeocodingConfiguration {

    @Bean
    public RestTemplate amapRestTemplate(AmapProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        return new RestTemplate(factory);
    }
}
```

**为什么要单独定义一个 `amapRestTemplate` Bean？**

系统中可能存在多个 `RestTemplate` Bean（如物流服务也有自己的）。如果直接定义 `@Bean RestTemplate restTemplate()`，Spring 上下文中出现多个相同类型的 Bean 时会产生注入歧义（`NoUniqueBeanDefinitionException`）。

给这个 Bean 命名为 `amapRestTemplate`，然后在 `AmapGeocodingService` 中用 `@Qualifier("amapRestTemplate")` 精确注入，避免冲突：

```java
public AmapGeocodingService(
        AmapProperties properties,
        @Qualifier("amapRestTemplate") RestTemplate restTemplate,  // 精确指定
        ObjectMapper objectMapper) { ... }
```

**`@EnableConfigurationProperties(AmapProperties.class)` 的必要性**：

`@ConfigurationProperties` 只是声明"我要绑定配置"，不会自动注册 Bean。`@EnableConfigurationProperties` 在配置类上显式激活，才会让 Spring 把 `AmapProperties` 实例化并注入到 IoC 容器中。

---

## 10. 依赖关系与编译原理

### 10.1 Maven 多模块项目

整个 Transportation 项目是一个 **Maven 多模块项目（Multi-Module）**：

```
Transportation/（父项目，只有pom.xml）
├── common/         ← 无端口，打包成jar供其他模块用
├── auth-service/   ← 依赖 common
├── gateway-service/ ← 依赖 common
├── customer-service/ ← 依赖 common
├── order-service/  ← 依赖 common
├── driver-service/ ← 依赖 common
├── shop-service/   ← 依赖 common
└── logistics-service/ ← 依赖 common
```

**父项目 pom.xml 的作用**：
- 统一管理所有子模块的版本号（`<dependencyManagement>`）
- 声明所有子模块（`<modules>`）
- 统一 Java 版本（Java 17）

**构建顺序**：Maven 自动分析依赖关系，先编译 common，再编译依赖 common 的服务。

### 10.2 为什么 gateway 需要排除 web 依赖？

```xml
<!-- gateway-service 的 pom.xml 中 -->
<dependency>
    <groupId>com.fm.Transportation</groupId>
    <artifactId>common</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

原因：
- 普通服务（auth/order/driver等）用 **Spring MVC**（传统同步模型，基于Servlet）
- gateway-service 用 **Spring WebFlux**（响应式异步模型，基于Reactor）
- 这两套框架**不能共存**，引入了 `spring-cloud-starter-gateway`（基于WebFlux）就必须排除 `spring-boot-starter-web`（基于MVC）

---

## 11. 关键技术深度解析

### 11.1 JJWT 库（Java JWT）

common 模块的 pom.xml 中引入了三个 JJWT 相关依赖：

```xml
<!-- API接口（编译时需要） -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.11.5</version>
</dependency>

<!-- 实现类（运行时需要，scope=runtime表示编译时不需要） -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>

<!-- Jackson序列化支持（处理JWT的JSON部分） -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>
```

**`scope=runtime` 解释**：这个依赖只在程序运行时需要，编译时不需要。你的代码不直接调用 `jjwt-impl` 的类，只调用 `jjwt-api` 中定义的接口，运行时 JJWT 框架内部会自动找到实现类。

### 11.2 MyBatis Plus 注解的编译行为

```java
@TableName("user")          // MyBatis Plus 在运行时读取这个注解
@TableId(type = IdType.AUTO) // 告诉 MyBatis Plus 如何处理主键
```

这些注解不是在编译时生效的，而是在**运行时**由 MyBatis Plus 框架通过 Java 反射机制读取，用于自动生成 SQL 语句：

```java
// MyBatis Plus 反射读取注解，自动生成SQL：
// SELECT id, username, secret, permission FROM user WHERE id = ?
authMapper.selectById(1L);
```

common 模块的 pom.xml 只引入了 `mybatis-plus-annotation`（只有注解），而不是完整的 `mybatis-plus-boot-starter`（因为 common 本身不运行，不需要完整框架）。

### 11.3 Jackson 库

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

**Jackson** 是 Java 最流行的 JSON 处理库，`@RequestBody` 和 `@ResponseBody` 都靠它工作：

```
前端 JSON → Jackson.readValue() → Java 对象  （反序列化）
Java 对象 → Jackson.writeValueAsString() → JSON  （序列化）
```

---

## 附录：common 模块知识点速查表

| 文件 | 核心类/接口 | 关键知识点 |
|------|-------------|------------|
| `User.java` | `@TableName`, `@TableId` | MyBatis Plus实体注解、Long主键 |
| `LoginRequestDTO.java` | `Serializable` | 序列化接口、DTO概念 |
| `LoginResponseDTO.java` | 嵌套对象 | DTO组合、数据封装 |
| `UserInfoDTO.java` | `@Serial`, `serialVersionUID` | 序列化版本控制 |
| `PageResult.java` | 泛型`<T>`、向上取整 | 分页设计、泛型复用 |
| `Result.java` | 泛型、静态工厂方法、`Void` | 统一响应格式、静态方法 |
| `ResultCode.java` | 枚举含多字段 | 带参数的枚举、HTTP状态码规范 |
| `PermissionEnum.java` | 枚举、静态查找方法 | 角色权限设计、枚举遍历 |
| `JwtUtil.java` | `@Component`, `@Value` | Spring Bean、配置注入、JJWT库 |
| `BusinessException.java` | `extends RuntimeException` | 自定义异常、运行时异常 |
| `GlobalExceptionHandler.java` | `@RestControllerAdvice`, `@Slf4j` | AOP、全局异常处理、日志 |
| `GeoPoint.java` | `record` | Java record类型、只读值对象、经纬度封装 |
| `AmapProperties.java` | `@ConfigurationProperties` | 配置绑定、Relaxed Binding、环境变量注入 |
| `AmapGeocodingService.java` | `Optional<T>`、`RestTemplate` | 高德地理编码 HTTP 调用、失败不阻断业务 |
| `AmapGeocodingConfiguration.java` | `@EnableConfigurationProperties`、`@Qualifier` | 激活配置绑定、Bean 命名与精确注入 |
