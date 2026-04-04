# Transportation 项目业务跑通改造任务拆分表

## 1. 文档目标

本文档用于把当前 `Transportation` 项目从“原型级微服务代码”改造成“可以跑通主要业务链路的业务系统”。

重点目标：

1. 打通跨服务主链路：登录 -> 顾客下单 -> 商户发货 -> 司机接单 -> 物流追踪 -> 配送完成。
2. 统一跨服务数据模型：`userId`、`customerId`、`shopId`、`driverId`、`warehouseId`、经纬度字段、状态字段。
3. 修复服务间调用不一致问题，确保 Feign 调用参数、返回值、权限头、数据库字段一致。
4. 增加最小可验证能力，确保每一阶段都能联调、回归、继续开发。

建议执行方式：

- 严格按阶段推进，不要并行大面积改。
- 每完成一个阶段就做一次联调验证。
- 优先改“主链路必须项”，暂不追求后台管理、AI、调度大屏等扩展功能。

---

## 2. 改造总原则

### 2.1 ID 统一原则

网关注入的 `userId` 只能表示 `user.id`，不能直接当作业务域主键使用。

统一约定：

- `userId` = `user.id`
- `customerId` = `customer_info.id`
- `shopId` = `shop_info.id`
- `driverId` = `driver_info.id`
- `deliveryId` = `order_delivery.id`
- `routeId` = `logistics_route.id`

所有业务服务内部必须先做“用户身份 -> 业务主体”的转换，再进行业务操作。

### 2.2 经度纬度命名统一原则

全项目统一使用：

- `latitude`
- `longitude`

不再混用：

- `lng`
- `lon`
- `startLng`
- `startLon`

### 2.3 状态机统一原则

订单、配送、物流路线的状态必须分别定义，且跨服务有明确映射关系。

建议统一如下：

#### 订单状态 `order_status`

- `0` 待支付
- `1` 待发货
- `2` 已发货
- `3` 已完成
- `4` 已取消

#### 支付状态 `payment_status`

- `0` 未支付
- `1` 已支付

#### 配送状态 `delivery_status`

- `0` 待接单
- `1` 已接单
- `2` 运输中
- `3` 已送达
- `4` 已取消

#### 物流路线状态 `route_status`

- `0` 待出发
- `1` 运输中
- `2` 已送达
- `3` 异常
- `4` 已取消

---

## 3. 阶段总览

### 阶段 P0-A: 建模统一

目标：先统一数据库、实体、DTO、Feign 字段，避免“代码能写，系统跑不通”。

### 阶段 P0-B: 打通主链路

目标：完成下单、发货、接单、轨迹上报、完成配送的业务闭环。

### 阶段 P1: 增强一致性

目标：补库存回滚、状态补偿、权限校验、幂等控制。

### 阶段 P2: 工程化补强

目标：补测试、补文档、清理前端测试壳、形成可回归开发基线。

---

## 4. 可执行改造任务拆分

## 阶段 P0-A: 建模统一

### 任务 A1: 统一订单表、仓库表、地址表结构

优先级：最高

目标：

- 数据库字段与实体类一致
- 物流规划所需字段完整

涉及文件：

- `database/order.sql`
- `database/shop.sql`
- `database/customer.sql`
- `order-service/src/main/java/com/fm/order/entity/Order.java`
- `shop-service/src/main/java/com/fm/shop/entity/Warehouse.java`
- `customer-service/src/main/java/com/fm/customer/entity/Address.java`

具体改动：

1. 在 `order_info` 增加 `warehouse_id` 字段。
2. 在 `warehouse` 增加 `latitude`、`longitude` 字段。
3. 在 `customer_address` 增加 `latitude`、`longitude` 字段。
4. 更新测试 SQL，确保示例数据带坐标。
5. 核对实体字段名与数据库下划线映射是否一致。

完成标准：

- 重新初始化数据库后，实体查询与插入无字段缺失。
- 仓库和收货地址都能返回坐标。

联调验证：

1. 查询仓库详情能返回 `latitude/longitude`
2. 查询地址详情能返回 `latitude/longitude`
3. 创建订单后数据库存在 `warehouse_id`

---

### 任务 A2: 统一经纬度命名

优先级：最高

目标：

- 消除 `lon/lng/longitude` 混用

涉及文件：

- `logistics-service/src/main/java/com/fm/logistics/dto/CreateRouteRequestDTO.java`
- `logistics-service/src/main/java/com/fm/logistics/entity/LogisticsRoute.java`
- `logistics-service/src/main/java/com/fm/logistics/entity/LogisticsNode.java`
- `logistics-service/src/main/java/com/fm/logistics/service/impl/LogisticsRouteServiceImpl.java`
- `order-service/src/main/java/com/fm/order/service/impl/OrderServiceImpl.java`
- `order-service/src/main/java/com/fm/order/feign/LogisticsFeignClient.java`
- `test-data/logistics-service/json/create_route_request.json`
- `test-data/logistics-service/README.md`

具体改动：

1. DTO 和实体统一成 `startLatitude/startLongitude/endLatitude/endLongitude` 或最少统一成 `startLat/startLongitude/endLat/endLongitude`。
2. Feign 请求体字段名统一。
3. 删除 `startLng/startLon/endLng/endLon` 混用写法。
4. 测试 JSON 和 README 一并改掉。

建议：

- 若改动较大，建议一步到位统一为：
  - `startLatitude`
  - `startLongitude`
  - `endLatitude`
  - `endLongitude`
- 若想控制改动量，则统一为：
  - `startLat`
  - `startLongitude`
  - `endLat`
  - `endLongitude`

完成标准：

- `order-service` 发给 `logistics-service` 的字段能被正确反序列化
- 创建路线时不再丢经度

---

### 任务 A3: 统一订单状态机定义

优先级：最高

涉及文件：

- `database/order.sql`
- `order-service/src/main/java/com/fm/order/entity/Order.java`
- `order-service/src/main/java/com/fm/order/controller/OrderController.java`
- `order-service/src/main/java/com/fm/order/service/impl/OrderServiceImpl.java`
- `driver-service/src/main/java/com/fm/driver/feign/OrderFeignClient.java`
- `frontend-test/src/api/definitions.js`
- `test-data` 下与订单相关 README / JSON

具体改动：

1. 确认订单状态最终枚举。
2. 更新代码注释、README、前端测试定义。
3. `payOrder()` 改为：
   - `payment_status = 1`
   - `order_status = 1`
4. 商户发货改为把订单从 `1` 更新到 `2`。
5. 配送完成回写订单状态改为 `3`。
6. 取消订单改为 `4`。

完成标准：

- 订单状态在数据库、后端、前端测试页含义一致。

---

### 任务 A4: 补最小内部查询接口，统一“userId -> 业务ID”转换

优先级：最高

目标：

- 任何服务都不能直接把 `userId` 当业务主键

涉及文件：

- `customer-service/src/main/java/com/fm/customer/controller/CustomerController.java`
- `customer-service/src/main/java/com/fm/customer/service/CustomerService.java`
- `customer-service/src/main/java/com/fm/customer/service/impl/CustomerServiceImpl.java`
- `shop-service/src/main/java/com/fm/shop/controller/ShopController.java`
- `shop-service/src/main/java/com/fm/shop/service/ShopService.java`
- `shop-service/src/main/java/com/fm/shop/service/impl/ShopServiceImpl.java`
- `driver-service/src/main/java/com/fm/driver/controller/DriverController.java`
- `driver-service/src/main/java/com/fm/driver/service/DriverService.java`
- `driver-service/src/main/java/com/fm/driver/service/impl/DriverServiceImpl.java`
- `order-service/src/main/java/com/fm/order/feign/CustomerFeignClient.java`
- `order-service/src/main/java/com/fm/order/feign/ShopFeignClient.java`

建议新增内部接口：

- `GET /api/customers/internal/user/{userId}`
- `GET /api/shops/internal/user/{userId}`
- `GET /api/drivers/internal/user/{userId}`

具体改动：

1. 在顾客、商户、司机服务增加内部查询接口。
2. 这些接口返回业务主体信息或最小 DTO。
3. `order-service` 创建订单、查单、商户看单时，先通过内部接口转换 ID。

完成标准：

- `order-service` 不再直接把 `userId` 写进 `order.customerId/order.shopId`

---

## 阶段 P0-B: 打通主链路

### 任务 B1: 重写订单创建链路

优先级：最高

目标：

- 顾客可以真实下单
- 商品库存正确扣减
- 订单数据正确归属顾客和商户

涉及文件：

- `order-service/src/main/java/com/fm/order/controller/OrderController.java`
- `order-service/src/main/java/com/fm/order/service/impl/OrderServiceImpl.java`
- `order-service/src/main/java/com/fm/order/dto/CreateOrderRequestDTO.java`
- `order-service/src/main/java/com/fm/order/feign/CustomerFeignClient.java`
- `order-service/src/main/java/com/fm/order/feign/ShopFeignClient.java`
- `shop-service/src/main/java/com/fm/shop/controller/StockController.java`

具体改动：

1. `createOrder()` 使用 `userId` 先查顾客业务 ID。
2. 地址校验改为基于 `customerId` 校验，不基于 `userId` 假定。
3. 商品校验时确认商品属于 `request.shopId`。
4. 扣库存时记录实际扣减仓库。
5. `order_item` 中建议补充 `warehouse_id`，便于后续取消订单回补库存。
6. `CreateOrderRequestDTO` 移除无效或未使用字段，避免前后定义不一致。

完成标准：

- 顾客能成功下单
- 订单、订单项、库存数据正确变化

联调验证：

1. 顾客登录
2. 调 `POST /api/orders`
3. 数据库中新增订单与订单项
4. 对应仓库库存减少

---

### 任务 B2: 重写订单查询与权限校验

优先级：高

目标：

- 顾客只能看自己的订单
- 商户只能看自己店铺订单
- 管理员可看全部

涉及文件：

- `order-service/src/main/java/com/fm/order/controller/OrderController.java`
- `order-service/src/main/java/com/fm/order/service/impl/OrderServiceImpl.java`

具体改动：

1. `GET /api/orders/{id}` 中：
   - 顾客角色先转换为 `customerId`
   - 商户角色先转换为 `shopId`
2. `GET /api/orders/my` 按 `customerId` 查订单，不按 `userId`
3. `GET /api/orders/shop/{shopId}` 中校验当前登录用户对应的 `shopId`

完成标准：

- 顾客、商户、管理员访问范围正确

---

### 任务 B3: 重写订单发货链路

优先级：最高

目标：

- 商户发货后自动创建配送单和物流路线
- 状态流转正确

涉及文件：

- `order-service/src/main/java/com/fm/order/controller/OrderController.java`
- `order-service/src/main/java/com/fm/order/service/impl/OrderServiceImpl.java`
- `order-service/src/main/java/com/fm/order/feign/DriverFeignClient.java`
- `order-service/src/main/java/com/fm/order/feign/LogisticsFeignClient.java`
- `driver-service/src/main/java/com/fm/driver/controller/DeliveryController.java`
- `driver-service/src/main/java/com/fm/driver/service/impl/DeliveryServiceImpl.java`
- `logistics-service/src/main/java/com/fm/logistics/controller/LogisticsRouteController.java`

具体改动：

1. 商户发货前校验：
   - 当前用户是订单所属商户
   - 订单已支付
   - 当前状态为待发货
2. 更新订单状态为已发货时：
   - 创建配送单
   - 创建物流路线
3. 创建物流路线请求必须带：
   - `orderId`
   - `warehouseId`
   - 仓库地址和坐标
   - 收货地址和坐标
   - 收货人信息
4. 创建失败策略要明确：
   - 推荐：配送单和路线任一失败则整体回滚发货

完成标准：

- 商户发货后数据库新增 `order_delivery` 和 `logistics_route`
- 订单状态更新为已发货

---

### 任务 B4: 打通司机接单 -> 物流绑定

优先级：最高

目标：

- 司机接单后，配送单和物流路线都能绑定司机

涉及文件：

- `driver-service/src/main/java/com/fm/driver/feign/LogisticsFeignClient.java` 新增
- `driver-service/src/main/java/com/fm/driver/service/impl/DeliveryServiceImpl.java`
- `driver-service/src/main/java/com/fm/driver/DriverServiceApplication.java`
- `logistics-service/src/main/java/com/fm/logistics/controller/LogisticsRouteController.java`
- `logistics-service/src/main/java/com/fm/logistics/service/impl/LogisticsRouteServiceImpl.java`

具体改动：

1. 在 `driver-service` 新增调用物流服务的 Feign 客户端。
2. `acceptDelivery()` 成功后：
   - 根据 `orderId` 查对应 `routeId`
   - 调物流服务 `PUT /api/logistics/routes/{routeId}/bind`
3. 绑定时写入：
   - `driverId`
   - `deliveryId`
   - 路线状态改为运输中

完成标准：

- 接单后 `order_delivery.driver_id` 和 `logistics_route.driver_id` 一致
- `logistics_route.delivery_id` 被正确写入

---

### 任务 B5: 打通配送状态 -> 订单状态 -> 路线状态

优先级：最高

目标：

- 司机更新配送状态时，订单和路线保持一致

涉及文件：

- `driver-service/src/main/java/com/fm/driver/service/impl/DeliveryServiceImpl.java`
- `driver-service/src/main/java/com/fm/driver/feign/OrderFeignClient.java`
- `driver-service/src/main/java/com/fm/driver/feign/LogisticsFeignClient.java`
- `logistics-service/src/main/java/com/fm/logistics/service/impl/LogisticsRouteServiceImpl.java`

具体改动：

1. 配送状态更新为 `2 运输中` 时：
   - 同步路线状态为 `1 运输中`
2. 配送状态更新为 `3 已送达` 时：
   - 同步路线状态为 `2 已送达`
   - 同步订单状态为 `3 已完成`
3. 配送状态更新为 `4 已取消` 时：
   - 同步路线状态为 `4 已取消` 或 `3 异常`
   - 是否允许订单回退，按业务规则确定

完成标准：

- 配送单、路线、订单状态可联动

---

### 任务 B6: 修正轨迹上报中的司机身份语义

优先级：高

背景：

当前 `logistics-service` 的轨迹上报里把网关传入的 `userId` 直接当 `driverId` 用，这和主键定义不一致。

涉及文件：

- `logistics-service/src/main/java/com/fm/logistics/controller/LogisticsTrackController.java`
- `logistics-service/src/main/java/com/fm/logistics/service/impl/LogisticsTrackServiceImpl.java`
- `driver-service` 新增内部司机查询接口或 `logistics-service` 新增 Feign 客户端

具体改动：

1. `userId` 进入物流服务后，先转换为 `driverId`
2. `updateLocation(driverId, dto)` 使用业务司机主键
3. 校验该司机是否已经绑定到该路线

完成标准：

- 轨迹表写入的 `driver_id` 是 `driver_info.id`，不是 `user.id`

---

## 阶段 P1: 一致性与补偿

### 任务 C1: 实现取消订单库存回补

优先级：高

涉及文件：

- `order-service/src/main/java/com/fm/order/service/impl/OrderServiceImpl.java`
- `order-service/src/main/java/com/fm/order/entity/OrderItem.java`
- `shop-service` 新增库存回补接口
- `order-service` 新增 `ShopFeignClient` 回补方法

具体改动：

1. 在订单项中记录实际出库仓库。
2. 对“未发货订单取消”执行库存回补。
3. 已发货订单取消需禁止或走特殊逆向流程。

完成标准：

- 取消未发货订单后，库存恢复

---

### 任务 C2: 增加幂等与前置状态校验

优先级：高

涉及文件：

- `order-service/src/main/java/com/fm/order/service/impl/OrderServiceImpl.java`
- `driver-service/src/main/java/com/fm/driver/service/impl/DeliveryServiceImpl.java`
- `logistics-service/src/main/java/com/fm/logistics/service/impl/LogisticsRouteServiceImpl.java`

具体改动：

1. 下单幂等按业务决定，可暂不做。
2. 发货幂等：
   - 已有配送单 / 路线时不重复创建
3. 接单幂等：
   - 非待接单状态禁止重复接单
4. 送达幂等：
   - 已送达禁止再次更新
5. 取消幂等：
   - 已取消禁止重复取消

完成标准：

- 重复调用接口不会把系统状态打乱

---

### 任务 C3: 统一错误处理与事务边界

优先级：高

涉及文件：

- `common/src/main/java/com/fm/common/exception/GlobalExceptionHandler.java`
- `common/src/main/java/com/fm/common/result/Result.java`
- `logistics-service/src/main/java/com/fm/logistics/LogisticsServiceApplication.java`
- 所有服务的服务实现类

具体改动：

1. 确保所有服务都扫描 `common`。
2. 对关键编排接口明确事务边界。
3. 对 Feign 调用失败场景统一抛业务异常，不要只 `System.err.println`。

完成标准：

- 主链路失败时返回明确错误，不出现“接口成功但数据不完整”

---

## 阶段 P2: 文档、测试、前端最小闭环

### 任务 D1: 清理 `frontend-test`

优先级：中

目标：

- 让测试页只展示真实存在的接口

涉及文件：

- `frontend-test/src/api/definitions.js`

具体改动：

1. 删除未实现接口：
   - `/api/drivers/list`
   - `/api/logistics/dispatch/**`
   - `/api/logistics/ai/**`
   - `/api/logistics/routes/{routeId}/plan-route`
2. 修正请求体字段名与后端 DTO 一致。
3. 修正订单状态文案。

完成标准：

- 测试页中的接口都能真实请求

---

### 任务 D2: 补最小联调测试

优先级：高

建议优先新增测试模块：

- `order-service`
- `driver-service`
- `logistics-service`

建议测试场景：

1. 顾客下单成功
2. 商户发货成功并创建配送单、路线
3. 司机接单后路线完成绑定
4. 司机送达后订单完成
5. 取消未发货订单后库存回补

完成标准：

- 至少覆盖主链路 5 个核心场景

---

### 任务 D3: 同步修正文档和测试数据

优先级：中

涉及文件：

- `test-data/README.md`
- `test-data/customer-service/README.md`
- `test-data/shop-service/README.md`
- `test-data/driver-service/README.md`
- `test-data/logistics-service/README.md`
- 所有相关 JSON 示例文件

具体改动：

1. 更新状态值说明
2. 更新字段命名
3. 更新接口路径和调用前置条件
4. 删除未实现功能描述

完成标准：

- 文档与代码行为一致

---

## 5. 推荐开发顺序

建议按下面顺序提交代码：

1. 数据库字段统一
2. 实体 / DTO / Feign 字段统一
3. 内部 ID 转换接口
4. 订单创建链路改造
5. 订单发货链路改造
6. 司机接单与物流绑定
7. 配送完成联动订单和路线
8. 取消订单补偿
9. 测试与文档修正

---

## 6. 每阶段完成后的验收清单

### 验收 1: 下单

- 顾客登录成功
- 顾客资料存在
- 地址存在且属于当前顾客
- 商品存在且属于指定商户
- 库存足够
- 下单成功
- 库存减少
- 订单归属正确

### 验收 2: 发货

- 商户只能发自己的订单
- 订单必须已支付
- 发货后订单状态变为已发货
- 自动创建配送单
- 自动创建物流路线

### 验收 3: 接单

- 司机能看到待接单配送
- 接单成功后配送单绑定司机和车辆
- 物流路线同步绑定司机和配送单

### 验收 4: 轨迹

- 司机可上报当前位置
- 轨迹表新增记录
- 路线表当前位置同步更新

### 验收 5: 完成

- 司机送达后配送状态正确
- 物流路线状态正确
- 订单状态正确

---

## 7. 当前最小可跑通业务闭环定义

满足以下 6 条即可认为项目已从“原型”升级为“可跑通业务系统”：

1. 顾客可登录并创建订单
2. 商户可查看并发货自己的订单
3. 发货后自动生成配送单与物流路线
4. 司机可接单并绑定路线
5. 司机可上报轨迹并查询物流进度
6. 司机确认送达后订单自动完成

---

## 8. 本文档建议维护方式

建议你在执行时给每个任务补以下字段：

- 负责人
- 开始日期
- 完成日期
- 当前状态
- 备注

可直接把每个任务改成：

- `未开始`
- `进行中`
- `已完成`
- `阻塞`

---

## 9. 建议下一步

如果按“尽快跑通系统”来推进，建议立刻开始以下 3 个任务：

1. 任务 A1: 数据库字段统一
2. 任务 A4: 内部 ID 转换接口
3. 任务 B1: 订单创建链路重写

这 3 个任务做完后，项目的主链路问题会一下子清晰很多，后续发货、接单、物流绑定也能顺着推进。










