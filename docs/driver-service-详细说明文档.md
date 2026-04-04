# Driver-Service 运输员服务 — 完全初学者指南

> driver-service 负责管理运输员的个人信息、车辆信息，以及核心的配送任务管理。它是整个物流链条的"执行层"，通过双向调用与 order-service 和 logistics-service 紧密协作。

---

## 目录

1. [这个模块是做什么的？](#1-这个模块是做什么的)
2. [目录结构](#2-目录结构)
3. [数据库设计](#3-数据库设计)
4. [实体类详解](#4-实体类详解)
5. [运输员信息管理](#5-运输员信息管理)
6. [车辆管理](#6-车辆管理)
7. [配送任务管理：核心业务](#7-配送任务管理核心业务)
8. [服务间协作的完整流程](#8-服务间协作的完整流程)
9. [关键技术深度解析](#9-关键技术深度解析)

---

## 1. 这个模块是做什么的？

### 1.1 运输员的业务流程

```
商户发货（order-service）
    ↓ 创建配送记录（调用driver-service内部接口）
    ↓
配送记录进入"待接单大厅"（deliveryStatus=0）
    ↓ 运输员浏览待接单列表，选择接单
    ↓
接单成功（deliveryStatus=1）→ 同步订单状态为"派送中"
                             → 绑定司机到物流路线
    ↓ 运输员出发运货
    ↓
更新为运输中（deliveryStatus=2）→ 同步物流路线状态
    ↓ 货物到达
    ↓
确认送达（deliveryStatus=3）→ 同步订单状态为"已完成"
                            → 同步物流路线状态为"已送达"
```

### 1.2 接口总览

| 控制器 | 前缀 | 功能 | 角色 |
|--------|------|------|------|
| DriverController | `/api/drivers` | 运输员信息管理 | driver/admin |
| VehicleController | `/api/drivers/vehicles` | 车辆管理 | driver |
| DeliveryController | `/api/drivers/deliveries` | 配送任务管理 | driver/admin |

---

## 2. 目录结构

```
driver-service/
├── pom.xml
└── src/main/
    ├── java/com/fm/driver/
    │   ├── DriverServiceApplication.java   ← 启动类（端口8088）
    │   ├── config/
    │   │   ├── MyBatisPlusConfig.java      ← 分页插件
    │   │   └── SwaggerConfig.java
    │   ├── controller/
    │   │   ├── DriverController.java       ← 运输员信息接口
    │   │   ├── VehicleController.java      ← 车辆接口
    │   │   └── DeliveryController.java     ← 配送任务接口（最复杂）
    │   ├── entity/
    │   │   ├── Driver.java                 ← driver_info表实体
    │   │   ├── Vehicle.java                ← vehicle表实体
    │   │   └── OrderDelivery.java          ← order_delivery表实体
    │   ├── feign/
    │   │   ├── OrderFeignClient.java       ← 调用order-service
    │   │   └── LogisticsFeignClient.java   ← 调用logistics-service
    │   ├── mapper/
    │   │   ├── DriverMapper.java
    │   │   ├── VehicleMapper.java
    │   │   └── OrderDeliveryMapper.java
    │   └── service/
    │       ├── DriverService.java
    │       ├── VehicleService.java
    │       ├── DeliveryService.java
    │       └── impl/
    │           ├── DriverServiceImpl.java
    │           ├── VehicleServiceImpl.java
    │           └── DeliveryServiceImpl.java ← 核心逻辑（350行）
    └── resources/
        └── application.yml（端口8088）
```

---

## 3. 数据库设计

### 3.1 三张核心表

```sql
-- 运输员信息表
CREATE TABLE `driver_info` (
  `id`                   BIGINT NOT NULL AUTO_INCREMENT,
  `user_id`              BIGINT NOT NULL UNIQUE,   -- 关联user表
  `real_name`            VARCHAR(50),
  `phone`                VARCHAR(20),
  `email`                VARCHAR(100),
  `id_card`              VARCHAR(20),              -- 身份证号（实名认证用）
  `gender`               INT,                     -- 0未知 1男 2女
  `birthday`             DATETIME,
  `avatar`               VARCHAR(500),
  `license_number`       VARCHAR(50),              -- 驾驶证号
  `license_type`         VARCHAR(20),              -- 驾驶证类型（A/B/C等）
  `license_expire_date`  DATETIME,                 -- 驾驶证有效期
  `status`               INT DEFAULT 2,           -- 0禁用 1启用 2待审核
  `create_time`          DATETIME,
  `update_time`          DATETIME,
  PRIMARY KEY (`id`)
);

-- 车辆信息表
CREATE TABLE `vehicle` (
  `id`               BIGINT NOT NULL AUTO_INCREMENT,
  `driver_id`        BIGINT NOT NULL,   -- 属于哪个运输员
  `vehicle_type`     VARCHAR(50),       -- 车辆类型：货车/面包车/轿车
  `vehicle_brand`    VARCHAR(50),       -- 品牌：大众/丰田
  `vehicle_model`    VARCHAR(50),       -- 型号：T5/Hiace
  `license_plate`    VARCHAR(20),       -- 车牌号：粤B12345
  `load_capacity`    DECIMAL(10,2),     -- 最大载重（吨）
  `volume_capacity`  DECIMAL(10,2),     -- 容积（立方米）
  `vehicle_status`   INT DEFAULT 1,     -- 0停用 1可用
  `create_time`      DATETIME,
  `update_time`      DATETIME,
  PRIMARY KEY (`id`)
);

-- 配送任务表
CREATE TABLE `order_delivery` (
  `id`               BIGINT NOT NULL AUTO_INCREMENT,
  `order_id`         BIGINT NOT NULL UNIQUE,  -- 一个订单对应一个配送记录
  `driver_id`        BIGINT,                  -- 接单后填入（接单前为NULL）
  `vehicle_id`       BIGINT,                  -- 接单后填入
  `delivery_status`  INT DEFAULT 0,           -- 0待接单 1已接单 2运输中 3已送达 4已取消
  `accept_time`      DATETIME,                -- 接单时间
  `pickup_time`      DATETIME,                -- 取货时间（运输中）
  `delivery_time`    DATETIME,                -- 送达时间
  `cancel_time`      DATETIME,
  `cancel_reason`    VARCHAR(500),
  `delivery_address` VARCHAR(500),            -- 收货地址（快照）
  `receiver_name`    VARCHAR(50),             -- 收货人姓名（快照）
  `receiver_phone`   VARCHAR(20),             -- 收货人电话（快照）
  `remark`           VARCHAR(500),
  `create_time`      DATETIME,
  `update_time`      DATETIME,
  PRIMARY KEY (`id`)
);
```

### 3.2 配送状态机

```
【0：待接单】← ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐
     ↓ 运输员接单                              │ 司机取消（重置回待接单）
【1：已接单】                                  │
     ↓ 运输员出发                             │
【2：运输中】──────────────────────────────── ┘
     ↓ 货物送达
【3：已送达】
```

**注意**：司机取消后，配送记录的 `driverId/vehicleId` 字段被清空，重新回到待接单状态（`deliveryStatus=0`），其他司机可以重新接单。

---

## 4. 实体类详解

### 4.1 Driver.java

```java
@Data
@TableName("driver_info")
public class Driver {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;           // 关联user表
    private String realName;
    private String phone;
    private String email;
    private String idCard;         // 身份证号
    private Integer gender;
    private Date birthday;
    private String avatar;
    private String licenseNumber;  // 驾驶证号码
    private String licenseType;    // 驾驶证类型（A/B/C1/C2等）
    private Date licenseExpireDate; // 驾驶证有效期
    private Integer status;        // 0禁用 1启用 2待审核（新运输员先待审核）
    private Date createTime;
    private Date updateTime;
}
```

**驾驶证信息的重要性**：真实的物流系统需要验证驾驶员的资质，`licenseExpireDate` 驾驶证有效期到期后应自动禁止接单（本系统未实现此业务规则，留作扩展）。

### 4.2 OrderDelivery.java

```java
@Data
@TableName("order_delivery")
public class OrderDelivery {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;        // 关联的订单
    private Long driverId;       // 接单的运输员（接单前NULL）
    private Long vehicleId;      // 使用的车辆（接单前NULL）
    private Integer deliveryStatus; // 配送状态
    private Date acceptTime;
    private Date pickupTime;
    private Date deliveryTime;
    private Date cancelTime;
    private String cancelReason;
    private String deliveryAddress; // 收货地址（字符串拼接后的完整地址）
    private String receiverName;
    private String receiverPhone;
    private String remark;
    private Date createTime;
    private Date updateTime;
}
```

**`driverId` 和 `vehicleId` 为什么允许为 NULL？**

刚创建时（`deliveryStatus=0`，待接单），还没有运输员接单，不知道是谁会来接，所以这两个字段是空的（`NULL`）。接单后才填入。

在 Java 中，`Long` 类型（包装类）可以为 `null`，而 `long`（基本类型）不能为 `null`。这就是为什么用 `Long` 而不是 `long`。

---

## 5. 运输员信息管理

### 5.1 DriverController 关键接口

```java
@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    // 获取自己的运输员信息（从JWT中获取userId，再查driver）
    @GetMapping
    public Result<Driver> getMyDriver(
            @RequestHeader(value = "userId", required = false) String userIdHeader) {
        if (!StringUtils.hasText(userIdHeader))
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        Driver driver = driverService.getDriverByUserId(Long.parseLong(userIdHeader));
        if (driver == null) return Result.error("运输员信息不存在，请先完善信息");
        return Result.success(driver);
    }

    // 管理员查看指定运输员
    @GetMapping("/{id}")
    public Result<Driver> getDriverById(
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @PathVariable Long id) {
        if (!"admin".equals(roleCode))
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问");
        return Result.success(driverService.getDriverById(id));
    }

    // 内部接口：order-service 等调用，按 userId 查 driver
    @GetMapping("/internal/user/{userId}")
    public Result<Driver> getDriverByUserId(@PathVariable Long userId) {
        return Result.success(driverService.getDriverByUserId(userId));
    }

    // 内部接口：按 driverId 查 driver（logistics-service上报轨迹时用）
    @GetMapping("/internal/{driverId}")
    public Result<Driver> getDriverById_internal(@PathVariable Long driverId) {
        return Result.success(driverService.getDriverById(driverId));
    }
}
```

### 5.2 DriverServiceImpl

```java
@Override
public Driver getDriverByUserId(Long userId) {
    LambdaQueryWrapper<Driver> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Driver::getUserId, userId);
    return driverMapper.selectOne(wrapper);
}

@Override
public Driver saveOrUpdateDriver(Driver driver) {
    if (driver.getId() == null) {
        driver.setStatus(2);  // 新注册运输员默认"待审核"
        driverMapper.insert(driver);
    } else {
        driverMapper.updateById(driver);
    }
    return driver;
}
```

---

## 6. 车辆管理

### 6.1 VehicleController 接口

```java
// 获取我的车辆列表
@GetMapping("/vehicles")
public Result<List<Vehicle>> getMyVehicles(
        @RequestHeader("userId") String userIdHeader) {
    Driver driver = driverService.getDriverByUserId(Long.parseLong(userIdHeader));
    return Result.success(vehicleService.getVehiclesByDriverId(driver.getId()));
}

// 添加车辆（运输员自己添加，无需审核）
@PostMapping("/vehicles")
public Result<Vehicle> addVehicle(
        @RequestHeader("userId") String userIdHeader,
        @RequestBody Vehicle vehicle) {
    Driver driver = driverService.getDriverByUserId(Long.parseLong(userIdHeader));
    vehicle.setDriverId(driver.getId());  // 服务端设置，防止伪造
    return Result.success(vehicleService.addVehicle(vehicle));
}

// 更新车辆状态（启用/停用）
@PutMapping("/vehicles/{id}/status")
public Result<Boolean> updateVehicleStatus(
        @PathVariable Long id,
        @RequestParam Integer status) {
    return Result.success(vehicleService.updateVehicleStatus(id, status));
}
```

### 6.2 VehicleServiceImpl

```java
// 按运输员ID查询车辆列表
public List<Vehicle> getVehiclesByDriverId(Long driverId) {
    LambdaQueryWrapper<Vehicle> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Vehicle::getDriverId, driverId)
           .orderByDesc(Vehicle::getCreateTime);
    return vehicleMapper.selectList(wrapper);
}

// 删除车辆（真删除，因为车辆没有历史关联问题）
public boolean deleteVehicle(Long vehicleId) {
    return vehicleMapper.deleteById(vehicleId) > 0;
}
```

---

## 7. 配送任务管理：核心业务

这是 driver-service 中最复杂的部分，涉及多服务联动。

### 7.1 创建配送记录（内部接口，order-service 调用）

```java
@Override
public OrderDelivery createDelivery(Long orderId, String deliveryAddress,
                                     String receiverName, String receiverPhone) {
    // 幂等处理：如果已存在就不重复创建
    OrderDelivery existing = getDeliveryByOrderId(orderId);
    if (existing != null) {
        return existing;  // 直接返回已有记录
    }

    OrderDelivery delivery = new OrderDelivery();
    delivery.setOrderId(orderId);
    delivery.setDeliveryStatus(0);  // 初始：待接单
    delivery.setDeliveryAddress(deliveryAddress);
    delivery.setReceiverName(receiverName);
    delivery.setReceiverPhone(receiverPhone);
    // driverId / vehicleId 暂时为 null
    deliveryMapper.insert(delivery);
    return delivery;
}
```

**幂等性（Idempotency）**：

幂等是指一个操作执行多次和执行一次的结果相同。

如果 order-service 因为网络问题重试了两次 `createDelivery` 请求，幂等设计确保第二次调用不会创建重复的配送记录：

```
第一次调用：查不到 → 创建并返回
第二次调用：查到了 → 直接返回已有记录（不重复创建）
```

### 7.2 接单（acceptDelivery）— B4 关键节点

```java
@Override
public OrderDelivery acceptDelivery(Long deliveryId, Long driverId, Long vehicleId) {

    // ① 查询配送记录
    OrderDelivery delivery = deliveryMapper.selectById(deliveryId);
    if (delivery.getDeliveryStatus() != 0) {
        throw new BusinessException(ResultCode.FAIL.getCode(), "该订单已被接单或状态不正确");
    }

    // ② 更新配送记录（绑定运输员和车辆）
    delivery.setDriverId(driverId);
    delivery.setVehicleId(vehicleId);
    delivery.setDeliveryStatus(1);  // 已接单
    delivery.setAcceptTime(new Date());
    deliveryMapper.updateById(delivery);

    // ③ 同步订单状态 → 派送中(3)
    try {
        Map<String, Integer> body = new HashMap<>();
        body.put("orderStatus", 3);
        orderFeignClient.updateOrderStatus(delivery.getOrderId(), body, "system", "admin");
        // 注意：传入 roleCode="admin"，因为只有admin有权更新订单状态
        // 内部服务间调用时用"admin"权限绕过普通用户限制
    } catch (Exception e) {
        System.err.println("接单后同步订单状态失败：" + e.getMessage());
        // 不抛出异常，最终一致性设计
    }

    // ④ B4：绑定物流路线（将driverId/deliveryId写入logistics_route）
    try {
        Long routeId = getRouteIdByOrderId(delivery.getOrderId());
        if (routeId != null) {
            Map<String, Long> bindBody = new HashMap<>();
            bindBody.put("driverId", driverId);
            bindBody.put("deliveryId", deliveryId);
            logisticsFeignClient.bindDriver(routeId, bindBody);
        }
    } catch (Exception e) {
        System.err.println("B4：绑定物流路线失败：" + e.getMessage());
        // 不回滚接单，仅记录日志
    }

    return delivery;
}
```

**为什么调用 order-service 时传 `roleCode="admin"`？**

`OrderController.updateOrderStatus` 接口有权限校验，只有 `admin` 角色才能调用。driver-service 作为内部服务，在调用时伪装成 admin 角色。这是微服务内部调用的常见做法（生产环境应使用服务账号或内部认证机制）。

**`getRouteIdByOrderId()` 辅助方法**：

```java
private Long getRouteIdByOrderId(Long orderId) {
    try {
        Result<Map<String, Object>> result = logisticsFeignClient.getRouteByOrderId(orderId, "system");
        if (result.getCode() == 200 && result.getData() != null) {
            // 返回的是 RouteDetailDTO，包含 route 子对象
            Map<String, Object> routeDetailMap = result.getData();
            Object routeObj = routeDetailMap.get("route");       // 取 "route" 键
            if (routeObj instanceof Map) {
                Map<String, Object> routeMap = (Map<String, Object>) routeObj;
                Object idObj = routeMap.get("id");               // 取 route 的 "id"
                if (idObj != null) {
                    return Long.valueOf(idObj.toString());
                }
            }
        }
    } catch (Exception e) {
        System.err.println("查询物流路线失败：" + e.getMessage());
    }
    return null;  // 找不到时返回null，调用方跳过绑定
}
```

**多层 Map 嵌套解析**：

logistics-service 返回的 `RouteDetailDTO` 被 Feign 序列化为 JSON，然后反序列化为 `Map<String, Object>` 接收（因为 driver-service 没有定义 `RouteDetailDTO` 类）。所以需要手动多层取值：

```json
{
  "code": 200,
  "data": {
    "route": {                 ← routeDetailMap.get("route")
      "id": 5,                 ← routeMap.get("id")
      "orderId": 100
    },
    "nodes": [...]
  }
}
```

### 7.3 更新配送状态（updateDeliveryStatus）— B5 关键节点

```java
@Override
public OrderDelivery updateDeliveryStatus(Long deliveryId, Long driverId,
                                           Integer status, String remark) {

    OrderDelivery delivery = deliveryMapper.selectById(deliveryId);

    // 权限验证：只有接单的司机才能更新
    if (!delivery.getDriverId().equals(driverId)) {
        throw new BusinessException(ResultCode.FORBIDDEN);
    }

    if (status == 2) {  // 运输中（司机出发）
        if (delivery.getDeliveryStatus() != 1) {
            throw new BusinessException(..., "只能从已接单状态更新为运输中");
        }
        delivery.setPickupTime(new Date());

        // B5：同步物流路线状态 → 运输中(1)
        syncRouteStatus(delivery.getOrderId(), 1);

    } else if (status == 3) {  // 已送达
        if (delivery.getDeliveryStatus() != 2) {
            throw new BusinessException(..., "只能从运输中状态更新为已送达");
        }
        delivery.setDeliveryTime(new Date());

        // B5：同步物流路线状态 → 已送达(2)
        syncRouteStatus(delivery.getOrderId(), 2);

        // B5：同步订单状态 → 已完成(4)
        try {
            Map<String, Integer> body = new HashMap<>();
            body.put("orderStatus", 4);
            orderFeignClient.updateOrderStatus(delivery.getOrderId(), body, "system", "admin");
        } catch (Exception e) {
            System.err.println("B5：更新订单状态失败：" + e.getMessage());
        }
    }

    delivery.setDeliveryStatus(status);
    if (StringUtils.hasText(remark)) delivery.setRemark(remark);
    deliveryMapper.updateById(delivery);
    return delivery;
}
```

**`syncRouteStatus` 辅助方法**：

```java
private void syncRouteStatus(Long orderId, int routeStatus) {
    try {
        Long routeId = getRouteIdByOrderId(orderId);
        if (routeId != null) {
            Map<String, Integer> statusBody = new HashMap<>();
            statusBody.put("status", routeStatus);
            logisticsFeignClient.updateRouteStatus(routeId, statusBody, "system", "driver");
        }
    } catch (Exception e) {
        System.err.println("B5：同步路线状态失败（orderId=" + orderId + "）：" + e.getMessage());
    }
}
```

**物流路线状态码**：

| 值 | 含义 |
|----|------|
| 0 | 待运输（已创建，等待接单） |
| 1 | 运输中 |
| 2 | 已送达 |
| 3 | 运输异常（司机取消时） |

### 7.4 取消配送（cancelDelivery）

```java
@Override
public OrderDelivery cancelDelivery(Long deliveryId, Long driverId, String cancelReason) {

    OrderDelivery delivery = deliveryMapper.selectById(deliveryId);
    if (!delivery.getDriverId().equals(driverId)) {
        throw new BusinessException(ResultCode.FORBIDDEN);
    }
    // 只能取消已接单(1)或运输中(2)的配送
    if (delivery.getDeliveryStatus() != 1 && delivery.getDeliveryStatus() != 2) {
        throw new BusinessException(..., "只能取消已接单或运输中的订单");
    }

    Long orderId = delivery.getOrderId();

    // 重置配送记录（回到待接单状态）
    delivery.setDeliveryStatus(0);   // 回到待接单大厅
    delivery.setDriverId(null);      // 清空运输员
    delivery.setVehicleId(null);     // 清空车辆
    delivery.setAcceptTime(null);    // 清空接单时间
    delivery.setPickupTime(null);
    delivery.setDeliveryTime(null);
    delivery.setCancelTime(null);
    delivery.setCancelReason(null);
    // 在备注中记录取消原因（方便追溯）
    String note = "【司机取消配送】" + (StringUtils.hasText(cancelReason) ? cancelReason : "");
    delivery.setRemark(note);

    // B5：同步路线状态 → 运输异常(3)
    syncRouteStatus(orderId, 3);

    deliveryMapper.updateById(delivery);

    // 让订单回到"待揽件"状态（其他司机可以重新接单）
    try {
        orderFeignClient.reopenOrderToPendingPickup(orderId, "system", "admin");
    } catch (Exception e) {
        System.err.println("取消配送后同步订单状态失败：" + e.getMessage());
    }

    return delivery;
}
```

**`StringUtils.hasText()` 解释**：

Spring 提供的工具方法，等价于：
```java
str != null && !str.trim().isEmpty()
```

比 Java 原生的 `str != null && !str.isEmpty()` 更严格（额外去掉空格）。

---

## 8. 服务间协作的完整流程

### 8.1 整体协作图

```
1. 商户发货（order-service）
   └── 调用 driver-service.createDelivery()
       → 创建 order_delivery 记录（status=0，待接单）

2. 运输员接单（driver-service.acceptDelivery()）
   ├── 更新 order_delivery（status=1，绑定driverId/vehicleId）
   ├── 调用 order-service.updateOrderStatus(3) → 订单变"派送中"
   └── 调用 logistics-service.bindDriver() → 物流路线绑定司机

3. 运输员出发（driver-service.updateDeliveryStatus(2)）
   ├── 更新 order_delivery（status=2，记录pickupTime）
   └── 调用 logistics-service.updateRouteStatus(1) → 路线变"运输中"

4. 运输员送达（driver-service.updateDeliveryStatus(3)）
   ├── 更新 order_delivery（status=3，记录deliveryTime）
   ├── 调用 order-service.updateOrderStatus(4) → 订单变"已完成"
   └── 调用 logistics-service.updateRouteStatus(2) → 路线变"已送达"

5. 司机取消配送（driver-service.cancelDelivery()）
   ├── 重置 order_delivery（status=0，清空driverId/vehicleId）
   ├── 调用 logistics-service.updateRouteStatus(3) → 路线变"运输异常"
   └── 调用 order-service.reopenOrderToPendingPickup() → 订单回"待揽件"
```

### 8.2 Feign 客户端定义

**OrderFeignClient（调用订单服务）**：

```java
@FeignClient(name = "order-service", path = "/api/orders")
public interface OrderFeignClient {

    // 更新订单状态（内部调用，传admin权限）
    @PutMapping("/{orderId}/status")
    Result<Boolean> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestBody Map<String, Integer> body,
            @RequestHeader("userId") String userId,    // 必须传，否则Controller报401
            @RequestHeader("roleCode") String roleCode); // 传"admin"绕过权限校验

    // 让订单回到待揽件（司机取消时调用）
    @PutMapping("/{orderId}/reopen-pickup")
    Result<Boolean> reopenOrderToPendingPickup(
            @PathVariable Long orderId,
            @RequestHeader("userId") String userId,
            @RequestHeader("roleCode") String roleCode);
}
```

**LogisticsFeignClient（调用物流服务）**：

```java
@FeignClient(name = "logistics-service")
public interface LogisticsFeignClient {

    // 按订单ID查询物流路线（接单时查routeId）
    @GetMapping("/api/logistics/routes/order/{orderId}")
    Result<Map<String, Object>> getRouteByOrderId(
            @PathVariable Long orderId,
            @RequestHeader("userId") String userId);

    // 绑定司机到物流路线（接单后执行）
    @PutMapping("/api/logistics/routes/{routeId}/bind")
    Result<Map<String, Object>> bindDriver(
            @PathVariable Long routeId,
            @RequestBody Map<String, Long> body);  // {"driverId":1, "deliveryId":2}

    // 更新物流路线状态（出发/送达/异常）
    @PutMapping("/api/logistics/routes/{routeId}/status")
    Result<Map<String, Object>> updateRouteStatus(
            @PathVariable Long routeId,
            @RequestBody Map<String, Integer> body,  // {"status":1}
            @RequestHeader("userId") String userId,
            @RequestHeader("roleCode") String roleCode);
}
```

---

## 9. 关键技术深度解析

### 9.1 多字段条件查询和动态排序

DeliveryServiceImpl 中的 `getMyDeliveries` 支持按状态筛选和多字段排序：

```java
public PageResult<OrderDelivery> getMyDeliveries(
        Long driverId, Long current, Long size,
        Integer status, String sortField, String sortOrder) {

    Page<OrderDelivery> page = new Page<>(current, size);
    LambdaQueryWrapper<OrderDelivery> wrapper = new LambdaQueryWrapper<>();

    wrapper.eq(OrderDelivery::getDriverId, driverId);

    // 动态条件：status 可以不传（不传则查全部状态）
    if (status != null) {
        wrapper.eq(OrderDelivery::getDeliveryStatus, status);
    }

    applySort(wrapper, sortField, sortOrder);  // 动态排序

    IPage<OrderDelivery> pageResult = deliveryMapper.selectPage(page, wrapper);
    return new PageResult<>(...);
}
```

**`applySort()` 动态排序实现**：

```java
private void applySort(LambdaQueryWrapper<OrderDelivery> wrapper,
                       String sortField, String sortOrder) {
    if (!StringUtils.hasText(sortField)) sortField = "createTime";
    if (!StringUtils.hasText(sortOrder)) sortOrder = "desc";
    boolean isAsc = "asc".equalsIgnoreCase(sortOrder);

    switch (sortField.toLowerCase()) {  // toLowerCase 忽略大小写
        case "accepttime":
        case "accept_time":
            if (isAsc) wrapper.orderByAsc(OrderDelivery::getAcceptTime);
            else       wrapper.orderByDesc(OrderDelivery::getAcceptTime);
            break;
        case "deliverytime":
        case "delivery_time":
            if (isAsc) wrapper.orderByAsc(OrderDelivery::getDeliveryTime);
            else       wrapper.orderByDesc(OrderDelivery::getDeliveryTime);
            break;
        default:  // createTime 为默认
            if (isAsc) wrapper.orderByAsc(OrderDelivery::getCreateTime);
            else       wrapper.orderByDesc(OrderDelivery::getCreateTime);
    }
}
```

**`switch` 语句解释**：

等价于多个 `if-else if` 链，但更清晰：

```java
// switch 写法
switch (sortField.toLowerCase()) {
    case "accepttime": ... break;
    case "deliverytime": ... break;
    default: ...  // 其他情况
}

// 等价的 if-else 写法
if ("accepttime".equals(sortField.toLowerCase())) { ... }
else if ("deliverytime".equals(sortField.toLowerCase())) { ... }
else { ... }
```

### 9.2 `in()` 方法查询多个状态

```java
// 查询"进行中"的配送（已接单=1 或 运输中=2）
wrapper.in(OrderDelivery::getDeliveryStatus, 1, 2);
// SQL：WHERE delivery_status IN (1, 2)
```

`in()` 方法支持传入多个值，比写两个 `or` 条件更简洁：

```java
// 等价但更繁琐的写法
wrapper.and(w -> w.eq(OrderDelivery::getDeliveryStatus, 1)
                  .or()
                  .eq(OrderDelivery::getDeliveryStatus, 2));
```

### 9.3 `feign.client.config` 超时配置

```yaml
# application.yml
feign:
  client:
    config:
      default:          # "default" 表示所有Feign客户端的默认配置
        connectTimeout: 5000   # 连接超时：5秒（TCP握手最长等待时间）
        readTimeout: 5000      # 读取超时：5秒（等待响应最长时间）
```

**超时的重要性**：

如果不设置超时，当某个服务卡住时，Feign 调用会无限等待，导致线程被占用，最终整个服务因线程耗尽而宕机（级联故障）。

设置合理的超时值（如5秒）后，超时的服务调用会抛出异常，本服务可以捕获并做降级处理。

---

## 附录：知识点速查表

| 知识点 | 说明 |
|--------|------|
| 配送状态机 | 0待接单→1已接单→2运输中→3已送达（取消回到0） |
| 幂等设计 | createDelivery先查再建，防止重复创建 |
| 服务间调用内部权限 | 调用其他服务时传roleCode="admin"绕过普通权限校验 |
| B4节点 | 接单后绑定物流路线（bindDriver） |
| B5节点 | 状态变更后同步物流路线和订单状态 |
| `in()` | 查询多个状态值（WHERE field IN (1,2)） |
| 动态排序 | `applySort()`方法根据参数选择orderByAsc/orderByDesc |
| `switch-case` | 多分支条件判断，比if-else链更清晰 |
| Feign超时配置 | connectTimeout/readTimeout防止级联故障 |
| `StringUtils.hasText()` | null、""、"  "都返回false的字符串非空检查 |
| 多层Map解析 | 跨服务返回Map时需要多层get()提取数据 |
| `@RequestHeader` in Feign | Feign调用时添加请求头（如userId/roleCode） |
