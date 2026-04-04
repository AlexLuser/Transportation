# Gateway-Service 网关服务 — 完全初学者指南

> gateway-service 是整个系统的"大门"，所有来自前端的请求都必须经过它。它负责路由转发、JWT身份验证和权限控制，是系统安全的第一道防线。

---

## 目录

1. [这个模块是做什么的？](#1-这个模块是做什么的)
2. [技术栈：WebFlux 响应式编程](#2-技术栈webflux-响应式编程)
3. [目录结构](#3-目录结构)
4. [路由配置详解](#4-路由配置详解)
5. [JWT 全局过滤器详解](#5-jwt-全局过滤器详解)
6. [CORS 跨域配置](#6-cors-跨域配置)
7. [完整请求处理流程](#7-完整请求处理流程)
8. [权限控制矩阵](#8-权限控制矩阵)
9. [关键技术深度解析](#9-关键技术深度解析)

---

## 1. 这个模块是做什么的？

### 1.1 没有网关会怎样？

想象一下没有网关的情况：

```
前端（浏览器）          各个服务
      │
      ├──── 登录 ─────→ auth-service:8082
      │
      ├──── 下单 ─────→ order-service:8086
      │
      ├──── 查配送 ───→ driver-service:8088
      │
      └──── 查路线 ───→ logistics-service:8087
```

**问题**：
1. 前端要记住6个不同的端口号
2. 每个服务都要自己做JWT验证，代码重复
3. 任何人都可以直接访问这些服务，没有统一的安全控制

### 1.2 有了网关之后

```
前端（浏览器）              网关（8083）                后端服务群
      │                        │
      │  所有请求 → 8083        │  ① JWT验证
      │ ──────────────────────→ │  ② 权限判断       ──→ auth-service:8082
      │                        │  ③ 路由转发        ──→ order-service:8086
      │ ←────────────────────── │  ④ 注入用户信息    ──→ driver-service:8088
      │       返回结果          │                    ──→ logistics-service:8087
```

**好处**：
1. 前端只需要记住一个地址（`:8083`）
2. JWT验证集中在网关，各业务服务不需要重复验证
3. 网关统一控制哪个角色能访问哪个服务

### 1.3 网关的四大职责

| 职责 | 说明 |
|------|------|
| **路由（Routing）** | 根据URL前缀，把请求转发到对应的后端服务 |
| **身份验证（Authentication）** | 验证请求携带的JWT Token是否有效 |
| **权限控制（Authorization）** | 判断当前用户角色是否有权访问该路径 |
| **用户信息传递** | 把从Token中解析出的用户信息写入请求头，传给后端服务 |

---

## 2. 技术栈：WebFlux 响应式编程

### 2.1 为什么网关用 WebFlux 而不是 Spring MVC？

这是本模块最重要的技术差异，初学者常常困惑。

**Spring MVC（同步阻塞）**：

```
请求1 ──→ 线程1处理（阻塞等待数据库） ──→ 返回
请求2 ──→ 线程2处理（阻塞等待数据库） ──→ 返回
请求3 ──→ 线程3处理（阻塞等待数据库） ──→ 返回
...
请求100 → 等待线程池有空闲线程...（性能瓶颈）
```

每个请求占用一个线程，等待 I/O（数据库、网络）时线程被"锁住"无法处理其他请求。

**Spring WebFlux（异步非阻塞）**：

```
请求1 ──→ 注册回调（马上释放线程）
请求2 ──→ 注册回调（马上释放线程）
请求3 ──→ 注册回调（马上释放线程）
...
（I/O 完成时通知对应回调，一个线程处理成千上万个并发）
```

**Spring Cloud Gateway 基于 WebFlux**，因为网关需要处理大量并发请求，WebFlux 的异步模型效率更高。

### 2.2 WebFlux 的核心类型

```java
// Mono<T>：代表"将来会有0个或1个结果"的异步操作
Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain)
// 就像一个"未来的快递包裹"，现在下单，之后才送到

// Flux<T>：代表"将来会有0个或多个结果"的异步流
// （本模块未使用，但常见于WebFlux应用）
```

**`Mono<Void>` 解读**：

- `Mono`：只有一个值（或没有值）的异步容器
- `<Void>`：这个异步操作没有返回值（类似方法返回 `void`）
- 整个 `filter` 方法返回 `Mono<Void>` 而不是 `void`，因为这是异步操作

**链式调用（Chain API）**：

WebFlux 代码大量使用链式调用，与传统命令式代码不同：

```java
// 命令式（Spring MVC）
void process() {
    Result result = doStep1();
    Result result2 = doStep2(result);
    return result2;
}

// 声明式（WebFlux/Reactor）
Mono<Void> process() {
    return doStep1()
        .flatMap(result -> doStep2(result))  // 类似"成功了就继续做这个"
        .then();                              // 丢弃结果，返回 Mono<Void>
}
```

---

## 3. 目录结构

```
gateway-service/
├── pom.xml
└── src/main/
    ├── java/com/fm/gateway/
    │   ├── GatewayServiceApplication.java  ← 启动类（端口8083）
    │   ├── config/
    │   │   └── CorsConfig.java             ← CORS跨域配置
    │   └── filter/
    │       └── JwtAuthFilter.java          ← JWT验证全局过滤器（核心）
    └── resources/
        └── application.yml                 ← 路由规则配置（重要）
```

只有 4 个文件，但每个都非常关键。

---

## 4. 路由配置详解

### 4.1 application.yml 完整路由配置

```yaml
server:
  port: 8083  # 网关对外暴露的端口，前端访问这里

spring:
  application:
    name: gateway-service
  main:
    web-application-type: reactive  # 必须声明使用 WebFlux（响应式）

  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848  # 从Nacos获取各服务地址

    gateway:
      discovery:
        locator:
          enabled: true               # 启用Nacos服务自动发现
          lower-case-service-id: true # 服务名统一转小写

      routes:
        # ① 认证服务路由
        - id: auth-service            # 路由唯一标识（随意命名）
          uri: lb://auth-service      # lb://表示负载均衡，auth-service是Nacos服务名
          predicates:
            - Path=/api/auth/**       # 匹配 /api/auth/ 开头的所有路径
          filters:
            - StripPrefix=0           # 不截断前缀（保持原样转发）

        # ② 顾客服务路由
        - id: customer-service
          uri: lb://customer-service
          predicates:
            - Path=/api/customers/**
          filters:
            - StripPrefix=0

        # ③ 商户服务路由（一个服务对应多个路径前缀）
        - id: shop-service
          uri: lb://shop-service
          predicates:
            - Path=/api/shops/**,/api/products/**,/api/warehouses/**,/api/stocks/**,/api/mall/**
          filters:
            - StripPrefix=0

        # ④ 运输员服务路由
        - id: driver-service
          uri: lb://driver-service
          predicates:
            - Path=/api/drivers/**
          filters:
            - StripPrefix=0

        # ⑤ 订单服务路由
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/orders/**
          filters:
            - StripPrefix=0

        # ⑥ 物流服务路由
        - id: logistics-service
          uri: lb://logistics-service
          predicates:
            - Path=/api/logistics/**
          filters:
            - StripPrefix=0
```

### 4.2 路由工作原理

```
前端发请求：GET http://localhost:8083/api/orders/my

网关处理步骤：
1. 接收请求到 8083 端口
2. 匹配路由规则：/api/orders/** → 匹配到 order-service 路由
3. 向 Nacos 查询：order-service 在哪里？
4. Nacos 返回：localhost:8086
5. 转发请求到：GET http://localhost:8086/api/orders/my
6. 接收 order-service 的响应
7. 原样返回给前端
```

### 4.3 `lb://` 负载均衡

`lb://auth-service` 中的 `lb` 是 **Load Balance（负载均衡）** 的缩写。

**什么是负载均衡？**

如果一个服务启动了多个实例（比如3台服务器都运行了 order-service）：

```
Nacos 注册表：
  order-service → [192.168.1.1:8086, 192.168.1.2:8086, 192.168.1.3:8086]
```

网关请求 `lb://order-service` 时，Spring Cloud LoadBalancer 会自动选择其中一个实例（默认轮询算法），实现负载均衡。

---

## 5. JWT 全局过滤器详解

### 5.1 过滤器的作用

**过滤器（Filter）** 是一种拦截机制，在请求到达目标 Controller 之前（或响应返回前端之前）执行额外的逻辑。

```
前端请求 → 过滤器链 → Controller处理 → 过滤器链 → 返回响应
          (JwtAuthFilter)                  (可选后处理)
```

### 5.2 JwtAuthFilter 完整逻辑

```java
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {
    // GlobalFilter：Spring Cloud Gateway 的全局过滤器接口，对所有路由生效
    // Ordered：控制过滤器执行顺序（数字越小越先执行）

    @Autowired
    private JwtUtil jwtUtil;

    // 白名单：不需要验证Token的路径
    private static final List<String> WHITE_LIST = Arrays.asList(
        "/api/auth/login",    // 登录接口不需要Token（还没有Token）
        "/swagger-ui/**",     // Swagger文档页面
        "/v3/api-docs/**"     // Swagger API定义
    );
```

**白名单设计**：登录接口不能要求 Token（否则陷入"先有鸡还是先有蛋"的死循环），所以必须放入白名单直接放行。

### 5.3 核心 filter 方法（六步处理）

```java
@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    // ServerWebExchange：WebFlux中代表一次完整的请求-响应交换（包含request和response）
    // GatewayFilterChain：过滤器链，调用 chain.filter() 表示"放行，交给下一个过滤器或业务处理"

    ServerHttpRequest request = exchange.getRequest();
    String path = request.getURI().getPath();  // 例如："/api/orders/my"

    // ① 白名单检查：直接放行
    if (isWhiteList(path)) {
        return chain.filter(exchange);  // 不做任何检查，继续处理
    }

    // ② 获取 Token（从请求头中）
    String token = getToken(request);
    if (!StringUtils.hasText(token)) {  // StringUtils.hasText：判断不为null且不为空
        return unauthorized(exchange, "Token不能为空");  // 直接返回401
    }

    // ③ 验证 Token（调用 common 模块的 JwtUtil）
    if (!jwtUtil.validateToken(token)) {
        return unauthorized(exchange, "Token无效或已过期");  // 返回401
    }

    // ④ 解析 Token，提取用户信息
    Long userId = jwtUtil.getUserIdFromToken(token);
    String username = jwtUtil.getUsernameFromToken(token);
    String roleCode = jwtUtil.getRoleCodeFromToken(token);

    // ⑤ 权限控制：判断该角色是否能访问此路径
    if (!hasPermission(path, roleCode)) {
        return forbidden(exchange, "无权限访问该资源");  // 返回403
    }

    // ⑥ 把用户信息写入请求头，传给下游服务
    ServerHttpRequest.Builder builder = request.mutate();
    if (userId != null) builder.header("userId", userId.toString());
    if (StringUtils.hasText(username)) builder.header("username", username);
    if (StringUtils.hasText(roleCode)) builder.header("roleCode", roleCode);

    return chain.filter(exchange.mutate().request(builder.build()).build());
    // exchange.mutate()：创建一个新的不可变exchange（WebFlux中对象是不可变的）
    // builder.build()：生成添加了userId/username/roleCode头的新请求
}

@Override
public int getOrder() {
    return 0;  // 优先级0（最高），确保JWT验证在所有过滤器之前执行
}
```

### 5.4 getToken：从哪里取Token

```java
private String getToken(ServerHttpRequest request) {
    // 优先从 Authorization 头获取（标准方式）
    // 格式：Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
    String authHeader = request.getHeaders().getFirst("Authorization");
    if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
        return authHeader.substring(7);  // 截去 "Bearer " 前缀（7个字符）
    }

    // 备用：从 token 头直接获取（非标准但兼容旧版前端）
    return request.getHeaders().getFirst("token");
}
```

**`Authorization: Bearer xxx` 是国际标准**（RFC 6750），"Bearer"意为"持有者"，表示持有这个令牌的人有访问权限。

**`substring(7)` 解释**：
```
"Bearer eyJhbGci..."
 0123456
       ↑ 第7个字符开始是Token内容
```

### 5.5 isWhiteList：Ant 路径匹配

```java
private boolean isWhiteList(String path) {
    AntPathMatcher pathMatcher = new AntPathMatcher();
    return WHITE_LIST.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
}
```

**`AntPathMatcher` 的通配符**：

| 通配符 | 匹配说明 | 示例 |
|--------|----------|------|
| `?` | 匹配单个字符 | `/api/auth?` 匹配 `/api/auth1` |
| `*` | 匹配一层路径的任意字符 | `/api/*` 匹配 `/api/login`，但不匹配 `/api/a/b` |
| `**` | 匹配多层路径 | `/swagger-ui/**` 匹配 `/swagger-ui/index.html` 和 `/swagger-ui/a/b/c.js` |

**`stream().anyMatch()`**：

Java 8 的 Stream API，等价于"循环 WHITE_LIST，只要有一个匹配就返回true"：
```java
// 传统写法
for (String pattern : WHITE_LIST) {
    if (pathMatcher.match(pattern, path)) return true;
}
return false;

// Stream写法（更简洁）
WHITE_LIST.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
```

### 5.6 unauthorized/forbidden：返回错误响应

```java
private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
    ServerHttpResponse response = exchange.getResponse();

    // 设置HTTP状态码为401
    response.setStatusCode(HttpStatus.UNAUTHORIZED);

    // 设置响应内容类型为JSON（否则浏览器不知道如何解析）
    response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

    // 构建统一格式的错误响应体
    Result<Object> result = Result.error(message);
    result.setCode(ResultCode.UNAUTHORIZED.getCode());  // 401

    try {
        // 将 Result 对象序列化为 JSON 字符串
        String json = objectMapper.writeValueAsString(result);

        // 将 JSON 字符串转为字节数组，包装成 DataBuffer
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));

        // 写入响应（WebFlux方式，返回Mono）
        return response.writeWith(Mono.just(buffer));
    } catch (JsonProcessingException e) {
        return response.setComplete();  // JSON序列化失败时，直接关闭连接
    }
}
```

**为什么不直接用 `return Result.error(...)` ？**

因为 Gateway 用的是 WebFlux，不能直接返回对象（WebFlux 需要通过 `DataBuffer` 手动写入响应体）。这是 WebFlux 和 Spring MVC 最明显的区别之一。

**`DataBuffer`**：WebFlux 中的响应体容器，类似于 MVC 中的 `HttpServletResponse.getWriter().write()`。

---

## 6. CORS 跨域配置

### 6.1 什么是跨域（CORS）？

**同源策略（Same-Origin Policy）**：浏览器的安全策略，默认不允许从一个域名向另一个域名发送 Ajax 请求。

```
前端运行在：http://localhost:5173（Vue开发服务器）
API服务在：  http://localhost:8083（网关）

这是两个不同的"源"（端口不同）→ 跨域！
```

浏览器会阻止这种请求，抛出 CORS 错误。

**CORS（Cross-Origin Resource Sharing）** 是服务器告诉浏览器"允许这种跨域请求"的机制，通过在响应头中添加特定字段实现。

### 6.2 CorsConfig.java 解读

```java
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        // 注意：Gateway用 CorsWebFilter，不是 WebMvcConfigurer
        // 因为 Gateway 基于 WebFlux，不能用 MVC 的配置方式

        CorsConfiguration config = new CorsConfiguration();

        config.addAllowedOriginPattern("*");  // 允许任意来源
        // 注意：用 allowedOriginPattern 而不是 allowedOrigin("*")
        // 因为当 allowCredentials=true 时，不能用 * 作为 origin

        config.addAllowedHeader("*");   // 允许任意请求头（包括 Authorization）
        config.addAllowedMethod("*");   // 允许 GET/POST/PUT/DELETE 等所有方法
        config.setAllowCredentials(true); // 允许携带 Cookie（当前系统虽用JWT不用Cookie，但保留此配置）
        config.setMaxAge(3600L);  // 预检请求缓存1小时（减少OPTIONS请求次数）

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config); // 对所有路径生效

        return new CorsWebFilter(source);
    }
}
```

**CORS 预检请求（Preflight）**：

浏览器发送非简单请求（如 PUT、DELETE 或含自定义头的请求）时，会先发一个 `OPTIONS` 请求询问服务器"我可以这样发吗？"。

```
浏览器                                    网关
  │  OPTIONS /api/orders  (预检)           │
  │ ───────────────────────────────────→   │
  │                                        │
  │  200 OK + Access-Control-* 响应头      │
  │ ←───────────────────────────────────   │
  │
  │  PUT /api/orders/123/status  (实际请求)│
  │ ───────────────────────────────────→   │
```

`maxAge=3600` 表示浏览器可以把预检结果缓存1小时，避免每次请求都发预检。

---

## 7. 完整请求处理流程

### 7.1 正常登录请求（白名单放行）

```
前端 POST http://localhost:8083/api/auth/login
     {"username":"张三","password":"123456"}
             ↓
     网关接收请求（JwtAuthFilter）
             ↓
     isWhiteList("/api/auth/login") = true → 直接放行
             ↓
     Nacos查询 auth-service 地址 = localhost:8082
             ↓
     转发到 POST http://localhost:8082/api/auth/login
             ↓
     auth-service 验证密码，返回 {token:"eyJ...", userInfo:{...}}
             ↓
     网关原样返回给前端
```

### 7.2 正常业务请求（需要验证）

```
前端 GET http://localhost:8083/api/orders/my
     Headers: {Authorization: "Bearer eyJhbGci..."}
             ↓
     JwtAuthFilter 处理：
       ① 不在白名单
       ② getToken() 提取 "eyJhbGci..."
       ③ jwtUtil.validateToken() = true（Token有效）
       ④ 解析出 userId=1, username="张三", roleCode="customer"
       ⑤ hasPermission("/api/orders/my", "customer") = true
       ⑥ 添加请求头：{userId:"1", username:"张三", roleCode:"customer"}
             ↓
     转发到 GET http://localhost:8086/api/orders/my
     Headers: {userId:"1", username:"张三", roleCode:"customer"}
             ↓
     order-service 从请求头读取 userId，查询该顾客的订单
             ↓
     返回订单列表 → 网关 → 前端
```

### 7.3 未登录请求（拒绝访问）

```
前端 GET http://localhost:8083/api/orders/my
     没有携带 Authorization 头
             ↓
     JwtAuthFilter：
       ① 不在白名单
       ② getToken() 返回 null
       ③ 调用 unauthorized(exchange, "Token不能为空")
             ↓
     返回 HTTP 401
     {"code":401, "message":"Token不能为空", "data":null}
             ↓
     请求终止，不转发到任何服务
```

### 7.4 权限不足请求（顾客访问管理员接口）

```
前端 GET http://localhost:8083/api/drivers（列出所有司机，仅管理员权限）
     Headers: {Authorization: "Bearer eyJ...（顾客的Token）"}
             ↓
     JwtAuthFilter：
       ① 不在白名单
       ② Token 有效，解析出 roleCode="customer"
       ③ hasPermission("/api/drivers", "customer") = false
          （/api/drivers 要求 driver 或 admin 角色）
       ④ 调用 forbidden(exchange, "无权限访问该资源")
             ↓
     返回 HTTP 403
     {"code":403, "message":"无权限访问该资源", "data":null}
```

---

## 8. 权限控制矩阵

### hasPermission 方法逻辑

```java
private boolean hasPermission(String path, String roleCode) {
    if ("admin".equals(roleCode)) return true;  // 管理员无限制，直接放行

    if (path.startsWith("/api/customers"))  return "customer".equals(roleCode);
    if (path.startsWith("/api/shops"))      return "shop".equals(roleCode);
    if (path.startsWith("/api/products"))   return "shop".equals(roleCode) || "customer".equals(roleCode);
    if (path.startsWith("/api/mall"))       return "shop".equals(roleCode) || "customer".equals(roleCode) || "admin".equals(roleCode);
    if (path.startsWith("/api/warehouses")) return "shop".equals(roleCode);
    if (path.startsWith("/api/stocks"))     return "shop".equals(roleCode);
    if (path.startsWith("/api/drivers"))    return "driver".equals(roleCode) || "admin".equals(roleCode);
    if (path.startsWith("/api/logistics/routes"))   return true;  // 所有角色
    if (path.startsWith("/api/logistics/track"))    return true;  // 所有角色
    if (path.startsWith("/api/logistics/dispatch")) return "admin".equals(roleCode);

    return true;  // 未匹配的路径默认放行
}
```

### 权限矩阵（路径 × 角色）

| 路径前缀 | admin | customer | shop | driver |
|----------|-------|----------|------|--------|
| `/api/auth/**` | ✓ | ✓ | ✓ | ✓ | （白名单，所有人） |
| `/api/customers/**` | ✓ | ✓ | ✗ | ✗ |
| `/api/shops/**` | ✓ | ✗ | ✓ | ✗ |
| `/api/products/**` | ✓ | ✓ | ✓ | ✗ |
| `/api/mall/**` | ✓ | ✓ | ✓ | ✗ |
| `/api/warehouses/**` | ✓ | ✗ | ✓ | ✗ |
| `/api/stocks/**` | ✓ | ✗ | ✓ | ✗ |
| `/api/drivers/**` | ✓ | ✗ | ✗ | ✓ |
| `/api/orders/**` | ✓ | ✓ | ✓ | ✓ |
| `/api/logistics/routes` | ✓ | ✓ | ✓ | ✓ |
| `/api/logistics/track` | ✓ | ✓ | ✓ | ✓ |
| `/api/logistics/dispatch` | ✓ | ✗ | ✗ | ✗ |

---

## 9. 关键技术深度解析

### 9.1 全局过滤器 vs 路由过滤器

| 类型 | 接口 | 作用范围 | 配置位置 |
|------|------|----------|----------|
| 全局过滤器 | `GlobalFilter` | 所有路由 | Java代码 `@Component` |
| 路由过滤器 | `GatewayFilter` | 特定路由 | `application.yml` filters字段 |

`JwtAuthFilter` 实现 `GlobalFilter`，对所有请求生效（包括白名单判断）。

### 9.2 `StripPrefix=0` 的含义

如果配置 `StripPrefix=1`：
```
前端请求：/api/auth/login
网关转发：/login（去掉了第一层路径"/api"）
```

配置 `StripPrefix=0`：
```
前端请求：/api/auth/login
网关转发：/api/auth/login（原样转发，不去掉前缀）
```

本系统使用 `StripPrefix=0`，因为后端服务的接口路径也以 `/api/` 开头，前后保持一致。

### 9.3 Nacos 服务发现的工作原理

```
服务启动（auth-service）
    ↓
向 Nacos (localhost:8848) 注册：
  {"serviceName": "auth-service", "ip": "localhost", "port": 8082}
    ↓
网关请求 lb://auth-service 时
    ↓
Spring Cloud LoadBalancer 查询 Nacos
    ↓
Nacos 返回：[{ip: "localhost", port: 8082}]
    ↓
LoadBalancer 选择一个实例（如果有多个则轮询）
    ↓
转发请求到 http://localhost:8082
```

### 9.4 `exchange.mutate()` 为什么不能直接修改 request？

WebFlux 中的 `ServerHttpRequest` 是**不可变对象（Immutable）**，一旦创建就不能修改。这是 WebFlux 的设计原则（函数式编程风格）。

```java
// 错误（不可变对象，无法直接添加头）
request.getHeaders().add("userId", "1");  // 会抛出 UnsupportedOperationException

// 正确（创建新的 request 对象）
ServerHttpRequest newRequest = request.mutate()
    .header("userId", "1")
    .build();
```

`mutate()` 相当于"基于现有对象创建一个修改版的副本"，不改变原对象。

### 9.5 为什么 Gateway 不需要 @EnableDiscoveryClient？

实际上 Gateway 不需要显式加 `@EnableDiscoveryClient`，因为：
1. `spring-cloud-starter-alibaba-nacos-discovery` 依赖中已包含自动配置
2. Spring Boot 的自动配置机制会自动启用服务发现

但代码中加上它也没有问题（显式声明更清晰）。

---

## 附录：知识点速查表

| 知识点 | 说明 |
|--------|------|
| Spring Cloud Gateway | 微服务 API 网关，基于 WebFlux |
| WebFlux | Spring 响应式 Web 框架，基于 Reactor |
| `Mono<T>` | 代表 0 或 1 个异步结果 |
| `GlobalFilter` | 网关全局过滤器接口 |
| `Ordered` | 控制过滤器执行顺序 |
| `ServerWebExchange` | WebFlux 请求-响应交换对象 |
| `AntPathMatcher` | Ant 风格路径匹配（`*`, `**`） |
| `CORS` | 跨域资源共享，解决浏览器同源限制 |
| `CorsWebFilter` | WebFlux 的 CORS 过滤器 |
| `lb://` | 负载均衡协议前缀，结合 Nacos 使用 |
| `predicates` | 路由匹配条件（如 Path） |
| `StripPrefix` | 路由过滤器，控制是否截断 URL 前缀 |
| `Authorization: Bearer` | JWT 标准携带方式（RFC 6750） |
| `DataBuffer` | WebFlux 响应体数据容器 |
