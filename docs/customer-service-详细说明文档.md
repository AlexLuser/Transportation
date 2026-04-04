# Customer-Service 顾客服务 — 完全初学者指南

> customer-service 负责管理顾客的个人信息和收货地址，是整个系统中最贴近"买家"角色的服务。本文档将带你逐行理解其中每一行代码的含义。

---

## 目录

1. [这个模块是做什么的？](#1-这个模块是做什么的)
2. [目录结构](#2-目录结构)
3. [数据库设计](#3-数据库设计)
4. [实体类详解](#4-实体类详解)
5. [顾客信息接口详解](#5-顾客信息接口详解)
6. [收货地址接口详解](#6-收货地址接口详解)
7. [核心业务逻辑：地址管理](#7-核心业务逻辑地址管理)
8. [跨服务调用：内部接口](#8-跨服务调用内部接口)
9. [关键技术深度解析](#9-关键技术深度解析)

---

## 1. 这个模块是做什么的？

### 1.1 顾客服务的职责

顾客下单购物时需要：
1. **个人信息**：真实姓名、联系方式（顾客资料）
2. **收货地址**：省市区、详细地址、收货人、邮编（可以有多个地址，一个默认）

customer-service 就是管理这两块数据的服务。

### 1.2 顾客（customer）和用户（user）的区别

**新手常见困惑**：系统里既有 `user` 表，又有 `customer_info` 表，这是重复了吗？

```
user 表：
  id=1, username="zhangsan", secret="$2a$...", permission=2
           ↑登录用                  ↑密码哈希        ↑角色

customer_info 表：
  id=1, userId=1, realName="张三", phone="13800138000", email="..."
                   ↑ 关联到user表    ↑真实姓名           ↑联系方式
```

**设计原因**：
- `user` 表是**认证信息**：用户名、密码、角色——这些由 auth-service 管理
- `customer_info` 表是**业务信息**：真实姓名、手机号、头像——这些由 customer-service 管理
- 分开存储实现了**关注点分离**：认证和业务逻辑互不影响

**关联关系**：`customer_info.userId` 外键指向 `user.id`，一个用户对应一个顾客资料。

### 1.3 提供的接口

| 方法 | URL | 功能 | 谁能用 |
|------|-----|------|--------|
| GET | `/api/customers/{userId}` | 获取顾客资料 | 顾客本人/管理员 |
| POST | `/api/customers` | 创建顾客资料 | 顾客 |
| PUT | `/api/customers` | 修改顾客资料 | 顾客 |
| DELETE | `/api/customers/{userId}` | 删除顾客资料 | 顾客本人/管理员 |
| GET | `/api/customers/internal/user/{userId}` | **内部接口**：按userId查询 | 其他服务 |
| GET | `/api/customers/{userId}/address` | 获取地址列表 | 顾客 |
| POST | `/api/customers/address` | 添加收货地址 | 顾客 |
| PUT | `/api/customers/address` | 修改收货地址 | 顾客 |
| DELETE | `/api/customers/address/{id}` | 删除收货地址 | 顾客 |
| GET | `/api/customers/address/{addressId}` | **内部接口**：按ID查地址 | 其他服务 |

---

## 2. 目录结构

```
customer-service/
├── pom.xml
└── src/main/
    ├── java/com/fm/customer/
    │   ├── CustomerServiceApplication.java   ← 启动类（端口8084）
    │   ├── config/
    │   │   └── SwaggerConfig.java            ← API文档配置
    │   ├── controller/
    │   │   ├── CustomerController.java       ← 顾客信息HTTP接口
    │   │   └── AddressController.java        ← 收货地址HTTP接口
    │   ├── entity/
    │   │   ├── Customer.java                 ← customer_info表实体
    │   │   └── Address.java                  ← customer_address表实体
    │   ├── mapper/
    │   │   ├── CustomerMapper.java           ← 顾客数据库操作
    │   │   └── AddressMapper.java            ← 地址数据库操作
    │   └── service/
    │       ├── CustomerService.java          ← 顾客业务接口
    │       ├── AddressService.java           ← 地址业务接口
    │       └── impl/
    │           ├── CustomerServiceImpl.java  ← 顾客业务实现
    │           └── AddressServiceImpl.java   ← 地址业务实现
    └── resources/
        └── application.yml                   ← 端口8084，连MySQL
```

---

## 3. 数据库设计

### 3.1 customer_info 表

```sql
CREATE TABLE `customer_info` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL UNIQUE,  -- 一个user对应一个顾客资料
  `real_name`   VARCHAR(50),                   -- 真实姓名
  `phone`       VARCHAR(20),                   -- 手机号
  `email`       VARCHAR(100),                  -- 邮箱
  `gender`      INT,                           -- 0未知 1男 2女
  `birthday`    DATETIME,                      -- 生日
  `avatar`      VARCHAR(500),                  -- 头像URL（存储在OSS/文件服务器上的链接）
  `status`      INT DEFAULT 1,                 -- 0禁用 1启用
  `create_time` DATETIME,
  `update_time` DATETIME,
  PRIMARY KEY (`id`)
);
```

### 3.2 customer_address 表

```sql
CREATE TABLE `customer_address` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `customer_id`    BIGINT       NOT NULL,    -- 关联customer_info.id
  `receiver_name`  VARCHAR(50),              -- 收货人姓名
  `receiver_phone` VARCHAR(20),              -- 收货人电话
  `province`       VARCHAR(50),              -- 省份
  `city`           VARCHAR(50),              -- 城市
  `district`       VARCHAR(50),              -- 区/县
  `detail_address` VARCHAR(200),             -- 详细地址（街道门牌）
  `postal_code`    VARCHAR(10),              -- 邮编
  `is_default`     INT DEFAULT 0,            -- 0非默认 1默认地址
  `latitude`       DOUBLE,                   -- 纬度（物流路线规划用）
  `longitude`      DOUBLE,                   -- 经度（物流路线规划用）
  `create_time`    DATETIME,
  `update_time`    DATETIME,
  PRIMARY KEY (`id`)
);
```

**地址中为什么存经纬度（latitude/longitude）？**

物流路线规划需要知道从仓库（出发地）到收货地址（目的地）的精确地理坐标。中文地址（"北京市朝阳区建国路1号"）无法直接用于地图路线计算，需要经纬度（如 `latitude=39.9, longitude=116.4`）。

经纬度在创建或修改地址时由**后端**自动调用高德 Web 服务地理编码接口填入——用户只需要填写省市区和详细地址，坐标的获取与写入完全在服务端完成，前端无需关心。若高德接口调用失败（如 Key 未配置、网络超时、地址无法识别），坐标字段保持 `null`，地址仍然正常保存，物流路线规划在坐标缺失时会降级处理。

---

## 4. 实体类详解

### 4.1 Customer.java

```java
@Data
@TableName("customer_info")  // 注意：类名是Customer，表名是customer_info（不一样！）
public class Customer {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;       // 关联 user.id，通过这个找到对应的登录账号
    private String realName;   // 对应数据库 real_name 列
                               // MyBatis Plus 自动驼峰转换：realName → real_name
    private String phone;
    private String email;
    private Integer gender;    // 0=未知, 1=男, 2=女
    private Date birthday;     // java.util.Date，对应 DATETIME 类型
    private String avatar;     // 头像URL字符串
    private Integer status;    // 0=禁用, 1=启用
    private Date createTime;   // 对应 create_time 列（驼峰转换）
    private Date updateTime;
}
```

**驼峰命名转换（camelCase to snake_case）**：

Java 习惯用驼峰命名（`realName`），数据库习惯用下划线（`real_name`）。

配置文件中的 `map-underscore-to-camel-case: true` 让 MyBatis Plus 自动转换：

```
Java字段         数据库列
realName    ←→   real_name
createTime  ←→   create_time
userId      ←→   user_id
isDefault   ←→   is_default
```

**`@TableName("customer_info")` 的必要性**：

如果不加，MyBatis Plus 会默认把 `Customer` 类映射到 `customer` 表。但实际表名是 `customer_info`，所以必须显式指定。

### 4.2 Address.java

```java
@Data
@TableName("customer_address")
public class Address {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;      // 关联 customer_info.id（不是userId！）
    private String receiverName;  // 收货人，可以和顾客本人不同
    private String receiverPhone;
    private String province;      // "广东省"
    private String city;          // "深圳市"
    private String district;      // "南山区"
    private String detailAddress; // "科技园南区XX栋"
    private String postalCode;    // "518000"
    private Integer isDefault;    // 0=普通地址, 1=默认地址
    private Double latitude;      // 纬度（如: 22.5431）
    private Double longitude;     // 经度（如: 113.9353）
    private Date createTime;
    private Date updateTime;
}
```

**`customerId` vs `userId`**：

```
userId（user表的主键）
   ↓
customerId（customer_info表的主键，通过userId查到）
   ↓
Address.customerId（收货地址属于某个customer）
```

这是三层关联。地址直接关联 customerId（顾客资料的ID），而不是 userId（用户账号的ID），因为地址是顾客业务层面的数据，与认证层无关。

---

## 5. 顾客信息接口详解

### 5.1 CustomerController 代码解析

```java
@Tag(name = "顾客管理")
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    // ① 获取顾客个人信息
    @GetMapping("/{userId}")
    public Result<Customer> getCustomer(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            // ↑ 从HTTP请求头读取 userId（由网关注入的）
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            // ↑ 从HTTP请求头读取 roleCode（由网关注入的）
            @PathVariable Long userId) {
            // ↑ 从URL路径读取（/api/customers/1 中的 1）

        // 安全校验：必须登录
        if (!StringUtils.hasText(userIdHeader)) 
            throw new BusinessException(ResultCode.UNAUTHORIZED);

        // 权限校验：只能查自己的（管理员可以查任意）
        if (!"admin".equals(roleCode) && !userId.equals(Long.parseLong(userIdHeader)))
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他用户的信息");

        Customer customer = customerService.getCustomerByUserId(userId);
        if (customer == null) return Result.error("个人信息不存在");
        return Result.success(customer);
    }
```

**注解解释**：

| 注解 | 作用 | 示例 |
|------|------|------|
| `@RequestHeader("userId")` | 从请求头读取 | `Headers: {userId: "1"}` |
| `@PathVariable` | 从URL路径读取 | `/api/customers/1` 中的 `1` |
| `@RequestBody` | 从请求体读取（JSON） | `{"realName": "张三"}` |
| `@RequestParam` | 从查询参数读取 | `/api/customers?page=1` 中的 `1` |

**`required = false` 的含义**：

```java
@RequestHeader(value = "userId", required = false) String userIdHeader
```

`required = false` 表示这个请求头是可选的——如果没有这个头，不会自动报错，而是 `userIdHeader = null`。后续代码再手动判断 null 并抛出 `UNAUTHORIZED`。

为什么不用 `required = true`（默认值）？因为 `required = true` 时，Spring 会直接返回 400 Bad Request，格式不是我们统一的 JSON 格式，前端解析困难。`required = false` 让我们自己控制错误响应格式。

**权限校验逻辑**：

```java
// 顾客只能查自己（userId == userIdHeader中的值）
// 管理员可以查任何人（roleCode == "admin"）
if (!"admin".equals(roleCode) && !userId.equals(Long.parseLong(userIdHeader)))
    throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他用户的信息");
```

`Long.parseLong(userIdHeader)` ：请求头中的 userId 是字符串类型（请求头只能是字符串），需要转为 Long 才能与 `@PathVariable Long userId` 比较。

### 5.2 CustomerServiceImpl 代码解析

```java
@Service
public class CustomerServiceImpl implements CustomerService {

    @Autowired
    private CustomerMapper customerMapper;

    // 按 userId 查询顾客（登录后用 userId 找到顾客资料）
    @Override
    public Customer getCustomerByUserId(Long userId) {
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Customer::getUserId, userId);
        return customerMapper.selectOne(wrapper);
        // 因为 user_id 字段有 UNIQUE 约束，selectOne 不会返回多行
    }

    // 保存或更新顾客信息
    @Override
    public Customer saveOrUpdateCustomer(Customer customer) {
        if (customer.getId() == null) {
            customerMapper.insert(customer);   // 没有id → 新增
        } else {
            customerMapper.updateById(customer); // 有id → 更新
        }
        return customer;
        // insert/updateById 之后，customer对象的id字段会被MyBatis Plus自动填充
    }

    // 更新顾客状态（管理员禁用/启用账号）
    @Override
    public boolean updateCustomerStatus(Long customerId, Integer status) {
        LambdaUpdateWrapper<Customer> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Customer::getId, customerId)  // WHERE id = customerId
               .set(Customer::getStatus, status); // SET status = status
        return customerMapper.update(null, wrapper) > 0;
        // 返回影响行数 > 0 表示更新成功
    }
}
```

**`LambdaUpdateWrapper` vs `LambdaQueryWrapper`**：

| 类 | 用途 | 对应SQL |
|----|------|---------|
| `LambdaQueryWrapper` | 构建查询条件（SELECT的WHERE） | `SELECT * FROM customer WHERE ...` |
| `LambdaUpdateWrapper` | 构建更新条件（UPDATE的WHERE + SET） | `UPDATE customer SET ... WHERE ...` |

```java
// LambdaUpdateWrapper 示例
LambdaUpdateWrapper<Customer> wrapper = new LambdaUpdateWrapper<>();
wrapper
    .eq(Customer::getId, 1L)       // WHERE id = 1
    .set(Customer::getStatus, 0);  // SET status = 0
customerMapper.update(null, wrapper);
// 等价SQL：UPDATE customer_info SET status = 0 WHERE id = 1
```

---

## 6. 收货地址接口详解

### 6.1 AddressController 关键接口

**添加地址（POST /api/customers/address）**：

```java
@PostMapping("/address")
public Result<Address> addAddress(
        @RequestHeader(value = "userId", required = false) String userIdHeader,
        @RequestBody Address address) {

    if (!StringUtils.hasText(userIdHeader)) 
        throw new BusinessException(ResultCode.UNAUTHORIZED);

    Long userId = Long.parseLong(userIdHeader);

    // 先用 userId 找到 customerId
    Customer customer = customerService.getCustomerByUserId(userId);
    if (customer == null)
        throw new BusinessException(ResultCode.FAIL.getCode(), "请先完善个人信息");

    // 设置 customerId（防止前端伪造其他人的customerId）
    address.setCustomerId(customer.getId());

    return Result.success(addressService.addAddress(address));
}
```

**为什么要服务端设置 customerId？**

如果允许前端传 customerId，恶意用户可以传别人的 customerId，给别人添加地址。服务端从 JWT 中（通过 userId 头）获取 userId，再查数据库得到 customerId，确保地址属于当前登录用户。

**设置默认地址（PUT /api/customers/{customerId}/address/{addressId}/default）**：

```java
@PutMapping("/{customerId}/address/{addressId}/default")
public Result<Boolean> setDefaultAddress(
        @RequestHeader(value = "userId", required = false) String userIdHeader,
        @PathVariable Long customerId,
        @PathVariable Long addressId) {

    // 权限：只能设置自己的默认地址
    Customer customer = customerService.getCustomerByUserId(Long.parseLong(userIdHeader));
    if (!customer.getId().equals(customerId))
        throw new BusinessException(ResultCode.FORBIDDEN, "无权操作");

    return Result.success(addressService.setDefaultAddress(customerId, addressId));
}
```

---

## 7. 核心业务逻辑：地址管理

地址管理中最复杂的是**"默认地址"的维护逻辑**，涉及事务和多步数据库操作。

### 7.1 @Transactional 事务注解

**什么是事务？**

事务是数据库的一组操作，要么全部成功，要么全部回滚（撤销）。

```
银行转账示例：
  账户A扣款100元  ── 成功
  账户B增款100元  ── 失败（网络中断）

没有事务：A的钱少了，B的钱没增加，钱凭空消失！
有了事务：A扣款成功后B增款失败 → 整个操作回滚，A的钱恢复
```

`@Transactional` 注解让 Spring 自动管理事务：
- 方法开始时 → 开启事务
- 方法正常结束 → 提交事务（所有操作生效）
- 方法抛出异常 → 回滚事务（所有操作撤销）

### 7.2 addAddress 事务流程

```java
@Override
@Transactional  // 以下所有数据库操作在一个事务中
public Address addAddress(Address address) {

    // 步骤1：检查是否是第一个地址
    List<Address> existingAddresses = getAddressesByCustomerId(address.getCustomerId());
    if (existingAddresses.isEmpty()) {
        address.setIsDefault(1);  // 第一个地址自动设为默认
    }

    // 步骤2：插入新地址
    addressMapper.insert(address);  // 执行后 address.id 被自动填充

    // 步骤3：如果新地址是默认地址，更新其他地址为非默认
    if (address.getIsDefault() != null && address.getIsDefault() == 1) {
        setDefaultAddress(address.getCustomerId(), address.getId());
        address.setIsDefault(1);
    }

    return address;
}
```

**为什么步骤1和3都要处理默认地址？**

- 步骤1：如果顾客没有任何地址，新增的自动成为默认（无需额外处理）
- 步骤3：如果顾客已有地址，且新地址明确要设为默认，需要把其他地址的默认取消

### 7.3 setDefaultAddress 的原子操作

```java
@Override
@Transactional
public boolean setDefaultAddress(Long customerId, Long addressId) {

    // 第一步：把该顾客所有地址设为非默认
    LambdaUpdateWrapper<Address> clearWrapper = new LambdaUpdateWrapper<>();
    clearWrapper.eq(Address::getCustomerId, customerId)
                .set(Address::getIsDefault, 0);
    addressMapper.update(null, clearWrapper);
    // SQL：UPDATE customer_address SET is_default=0 WHERE customer_id=?

    // 第二步：把指定地址设为默认
    LambdaUpdateWrapper<Address> setWrapper = new LambdaUpdateWrapper<>();
    setWrapper.eq(Address::getId, addressId)
              .set(Address::getIsDefault, 1);
    return addressMapper.update(null, setWrapper) > 0;
    // SQL：UPDATE customer_address SET is_default=1 WHERE id=?
}
```

**两步操作为什么需要事务？**

如果第一步成功（所有地址都变成非默认），但第二步失败了（比如 addressId 不存在），结果是顾客没有任何默认地址。有了 `@Transactional`，第二步失败时，第一步也会回滚，保持数据一致性。

### 7.5 applyGeocode — 自动填充经纬度

```java
private void applyGeocode(Address incoming, Address existing) {
    // 合并地址字段：优先用新传入的，否则用数据库中的旧值（处理局部更新）
    String p      = pick(incoming.getProvince(),      existing != null ? existing.getProvince()      : null);
    String c      = pick(incoming.getCity(),          existing != null ? existing.getCity()          : null);
    String d      = pick(incoming.getDistrict(),      existing != null ? existing.getDistrict()      : null);
    String detail = pick(incoming.getDetailAddress(), existing != null ? existing.getDetailAddress() : null);

    Optional<GeoPoint> geo = amapGeocodingService.geocode(p, c, d, detail);
    if (geo.isPresent()) {
        // 高德返回坐标，以最新解析结果为准（覆盖旧坐标）
        incoming.setLatitude(geo.get().latitude());
        incoming.setLongitude(geo.get().longitude());
    } else if (existing != null) {
        // 高德调用失败，更新场景下保留旧坐标（避免改成 null）
        if (incoming.getLatitude()  == null) incoming.setLatitude(existing.getLatitude());
        if (incoming.getLongitude() == null) incoming.setLongitude(existing.getLongitude());
    }
    // 新增场景下高德失败：坐标保持 null，地址照常保存
}

private static String pick(String incoming, String existing) {
    return incoming != null ? incoming : existing;
}
```

**调用时机**：

```
addAddress()    → applyGeocode(incoming=address, existing=null)
                  ↑ 新增场景，existing 为 null，失败时坐标留 null

updateAddress() → applyGeocode(incoming=address, existing=existingAddress)
                  ↑ 更新场景，existing 是数据库中的旧记录，失败时保留旧坐标
```

**`pick()` 辅助方法解释**：

假设用户只修改了 `detailAddress`，没有传 `province`（值为 null），`pick()` 会用数据库里已有的 `province`，从而拼出完整地址再请求高德，避免因字段不完整导致地理编码匹配失败。

### 7.4 deleteAddress 的默认地址转移

```java
@Override
@Transactional
public boolean deleteAddress(Long addressId) {
    Address address = getAddressById(addressId);
    if (address == null) return false;

    boolean deleted = addressMapper.deleteById(addressId) > 0;

    // 如果删除的是默认地址，自动把下一个地址设为默认
    if (deleted && address.getIsDefault() != null && address.getIsDefault() == 1) {
        List<Address> remaining = getAddressesByCustomerId(address.getCustomerId());
        // remaining 已按 is_default desc, create_time desc 排序
        if (!remaining.isEmpty()) {
            LambdaUpdateWrapper<Address> setWrapper = new LambdaUpdateWrapper<>();
            setWrapper.eq(Address::getId, remaining.get(0).getId())
                      .set(Address::getIsDefault, 1);
            addressMapper.update(null, setWrapper);
            // 把剩余地址中的第一个设为默认
        }
    }

    return deleted;
}
```

**`getAddressesByCustomerId` 的排序**：

```java
LambdaQueryWrapper<Address> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(Address::getCustomerId, customerId)
       .orderByDesc(Address::getIsDefault)    // 默认地址排在前面
       .orderByDesc(Address::getCreateTime);  // 同等条件下，新创建的排前面
return addressMapper.selectList(wrapper);
```

排序逻辑：默认地址（`is_default=1`）排最前面，如果多个地址都不是默认，最新创建的排前面。这样删除默认地址后，`remaining.get(0)` 总能取到"最合适"的下一个默认地址。

---

## 8. 跨服务调用：内部接口

### 8.1 什么是内部接口？

内部接口是为**其他后端服务调用**设计的接口，不是给前端用的。

```
order-service 创建订单时，需要：
  1. 验证收货地址是否属于该顾客 → 调用 customer-service
  2. 获取地址的经纬度（用于物流路线规划） → 调用 customer-service
```

### 8.2 内部接口示例

```java
// 供 order-service 调用，通过 userId 查询顾客资料
@GetMapping("/internal/user/{userId}")
public Result<Customer> getCustomerByUserId(@PathVariable Long userId) {
    Customer customer = customerService.getCustomerByUserId(userId);
    if (customer == null) return Result.error("顾客信息不存在");
    return Result.success(customer);
}

// 供 order-service 调用，通过 addressId 查询地址详情（含经纬度）
@GetMapping("/address/{addressId}")
public Result<Address> getAddressById(@PathVariable Long addressId) {
    Address address = addressService.getAddressById(addressId);
    if (address == null) return Result.error("地址不存在");
    return Result.success(address);
}
```

**内部接口为什么没有权限校验？**

因为这些接口只在内网服务间调用，不会暴露给前端用户。网关的路由配置中，`/api/customers/internal/**` 理论上应配置为不通过网关直接访问（或通过网关的IP白名单限制）。

### 8.3 order-service 如何调用

order-service 通过 `CustomerFeignClient` 接口调用 customer-service：

```java
// order-service/src/main/java/com/fm/order/feign/CustomerFeignClient.java
@FeignClient(name = "customer-service", path = "/api/customers")
public interface CustomerFeignClient {

    // 调用 customer-service 的内部接口
    @GetMapping("/address/{addressId}")
    Result<Map<String, Object>> getAddressById(@PathVariable Long addressId);

    @GetMapping("/internal/user/{userId}")
    Result<Map<String, Object>> getCustomerByUserId(@PathVariable Long userId);
}
```

**OpenFeign 是什么？**

OpenFeign 是一个声明式 HTTP 客户端。你只需要定义接口（标注要调用哪个服务的哪个接口），OpenFeign 在运行时自动生成实现类（使用动态代理），帮你发送 HTTP 请求。

```java
// 使用 Feign 调用（看起来像调用本地方法）
Result<Map<String, Object>> result = customerFeignClient.getAddressById(1L);

// Feign 实际上帮你发了这个 HTTP 请求：
// GET http://customer-service/api/customers/address/1
// （customer-service 的地址通过 Nacos 解析）
```

---

## 9. 关键技术深度解析

### 9.3 高德地理编码在地址管理中的接入

收货地址的地理编码调用发生在 `AddressServiceImpl` 中，配置项写在 `customer-service` 的 `application.yml` 里：

```yaml
amap:
  enabled: true
  key: ${AMAP_KEY:}        # 从环境变量 AMAP_KEY 读取，不存在时为空串
  connect-timeout-ms: 5000
  read-timeout-ms: 8000
```

**`${AMAP_KEY:}` 含义**：冒号后为默认值，此处默认值为空串。Key 为空时，`AmapGeocodingService` 会直接返回 `Optional.empty()`，什么都不做，不会抛出异常。开发人员拿到高德 Key 后，通过环境变量 `export AMAP_KEY=你的key` 注入即可，无需修改代码。

**`AmapGeocodingService` 在哪里定义？**

这个 Bean 定义在 `common` 模块的 `com.fm.common.geo` 包中。`customer-service` 的启动类上有 `@ComponentScan(basePackages = {"com.fm.customer", "com.fm.common"})`，所以 Spring 会自动扫描到 common 模块中的 `AmapGeocodingService` 并注册为 Bean，`AddressServiceImpl` 直接用 `@Autowired` 注入即可，无需额外配置。

### 9.1 `Map<String, Object>` vs 具体 DTO

在 order-service 的 Feign 接口中，返回类型是 `Result<Map<String, Object>>` 而不是 `Result<Address>`：

```java
// order-service 这样声明：
Result<Map<String, Object>> getAddressById(@PathVariable Long addressId);

// 为什么不用 Result<Address>？
// 因为 Address 类在 customer-service 中定义
// 如果 order-service 也引入 Address 类，会产生耦合
// 用 Map<String, Object> 更灵活，代价是需要手动取值
```

**使用 Map 取值的代码**：

```java
Map<String, Object> addrData = result.getData();
String receiverName = addrData.get("receiverName").toString();
Double latitude = Double.valueOf(addrData.get("latitude").toString());
```

**更好的做法**：把 Address 的关键字段放在 common 模块（作为共享 DTO），各服务都能用。本系统未这样做，取了折中方案用 Map。

### 9.2 软删除 vs 硬删除

本模块的 `deleteCustomer` 是**硬删除**（真正从数据库删除记录）：
```java
return customerMapper.deleteById(customerId) > 0;
// SQL：DELETE FROM customer_info WHERE id = ?
```

而 order-service 中订单的删除是**软删除**（只标记deleted标志）：
```java
wrapper.set(Order::getCustomerDeleted, 1);  // 只是标记为已删除
```

**什么时候用硬删除，什么时候用软删除？**

- **软删除**：数据有价值、可能需要恢复、有关联外键（订单记录需要留存）
- **硬删除**：数据确实不再需要、没有严格的外键约束（顾客资料删除后重建即可）

### 9.3 MyBatis Plus 分页拦截器

customer-service 中没有配置分页拦截器，因为顾客信息和地址数量通常不多，直接 `selectList` 全量返回即可。

如果数据量大，可以仿照 order-service 添加：

```java
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    return interceptor;
}
```

然后在 Service 中使用 `Page<T>`：
```java
Page<Address> page = new Page<>(current, size);
IPage<Address> result = addressMapper.selectPage(page, wrapper);
```

---

## 附录：知识点速查表

| 知识点 | 说明 |
|--------|------|
| 顾客与用户分离 | user表（认证）+ customer_info表（业务），通过userId关联 |
| `@TableName` | 当类名与表名不一致时，显式指定表名 |
| `@RequestHeader` | 从HTTP请求头中读取值（网关注入的userId/roleCode） |
| `@PathVariable` | 从URL路径中读取参数（`/api/customers/{userId}`） |
| `@RequestBody` | 从请求体JSON中读取参数（POST/PUT时使用） |
| `required=false` | 参数可选，避免Spring自动报400，改为自定义错误响应 |
| `LambdaUpdateWrapper` | 构建UPDATE语句的WHERE和SET条件 |
| `@Transactional` | 声明式事务，多个数据库操作原子执行 |
| 驼峰转换 | `map-underscore-to-camel-case:true`，Java驼峰←→数据库下划线 |
| 软删除 vs 硬删除 | 软删除用标志位，硬删除从数据库移除 |
| OpenFeign | 声明式HTTP客户端，简化微服务间调用 |
| 内部接口 | 后端服务间调用的接口，不面向前端 |
| 经纬度坐标 | 收货地址存latitude/longitude供物流路线规划使用 |
| 默认地址逻辑 | 删除默认地址时自动转移到下一个，仅一个地址时自动设为默认 |
| 高德地理编码自动填充 | addAddress/updateAddress 时调用 AmapGeocodingService 自动获取坐标，失败不阻断保存 |
| `Optional<T>` | 高德返回"可能有结果"时使用，比返回 null 更安全、语义更清晰 |
| `pick()` 辅助方法 | 局部更新时合并新旧字段，确保拼出完整地址后再请求地理编码 |
