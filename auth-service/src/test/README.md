# Auth Service 测试说明

## 测试文件结构

```
src/test/
├── java/
│   └── com/fm/auth/
│       ├── service/
│       │   └── AuthServiceTest.java      # AuthService 单元测试
│       └── controller/
│           └── AuthControllerTest.java   # AuthController 集成测试
└── resources/
    └── application-test.yml              # 测试环境配置
```

## 测试内容

### 1. AuthServiceTest（单元测试）

使用 Mockito 进行单元测试，测试以下场景：

- ✅ **成功登录**：用户名和密码正确，返回JWT Token和用户信息
- ✅ **用户名为空**：验证参数校验
- ✅ **密码为空**：验证参数校验
- ✅ **用户不存在**：验证业务异常处理
- ✅ **密码错误**：验证密码验证逻辑
- ✅ **权限配置错误**：验证权限枚举映射
- ✅ **不同角色登录**：测试不同权限角色的登录流程

### 2. AuthControllerTest（集成测试）

使用 Spring MockMvc 进行集成测试，测试以下场景：

- ✅ **登录接口成功**：验证HTTP请求和响应格式
- ✅ **参数为空**：验证异常处理

## 运行测试

### 使用 Maven 运行所有测试

```bash
cd auth-service
mvn test
```

### 使用 Maven 运行特定测试类

```bash
# 运行 AuthServiceTest
mvn test -Dtest=AuthServiceTest

# 运行 AuthControllerTest
mvn test -Dtest=AuthControllerTest
```

### 使用 IDE 运行

- **IntelliJ IDEA**: 右键点击测试类或测试方法，选择 "Run 'TestName'"
- **Eclipse**: 右键点击测试类，选择 "Run As" -> "JUnit Test"

## 测试依赖

测试使用以下依赖（已在 `pom.xml` 中配置）：

- `spring-boot-starter-test`: Spring Boot 测试框架
- `junit-jupiter`: JUnit 5 测试框架
- `mockito`: Mock 框架，用于模拟依赖

## 注意事项

1. **单元测试**（AuthServiceTest）：
   - 使用 Mockito 模拟 `AuthMapper` 和 `JwtUtil`
   - 不依赖数据库，运行速度快
   - 测试业务逻辑的正确性

2. **集成测试**（AuthControllerTest）：
   - 使用 `@WebMvcTest` 只加载 Web 层
   - Mock `AuthService`，测试 Controller 层
   - 验证 HTTP 请求和响应格式

3. **测试配置**：
   - `application-test.yml` 用于测试环境配置
   - 测试时禁用 Nacos 服务发现

## 扩展测试

如果需要添加更多测试场景，可以在相应的测试类中添加新的测试方法：

```java
@Test
void testYourNewScenario() {
    // 准备测试数据
    // 执行测试
    // 验证结果
}
```

