# Order-Service 订单服务 — 完全初学者指南

> order-service 是整个系统的"核心枢纽"，它调用了几乎所有其他服务，是业务流程最复杂的模块。一个订单从创建到完成，要经历多个状态和多个服务的协作。

---

## 目录

1. [这个模块是做什么的？](#1-这个模块是做什么的)
2. [目录结构](#2-目录结构)
3. [数据库设计](#3-数据库设计)
4. [订单生命周期（状态机）](#4-订单生命周期状态机)
5. [OpenFeign 跨服务调用](#5-openfeign-跨服务调用)
6. [创建订单：最复杂的业务流程](#6-创建订单最复杂的业务流程)
7. [发货：触发配送和物流的关键操作](#7-发货触发配送和物流的关键操作)
8. [其他订单操作](#8-其他订单操作)
9. [事务与 ServiceImpl 继承](#9-事务与-serviceimpl-继承)
10. [关键技术深度解析](#10-关键技术深度解析)

---

## 1. 这个模块是做什么的？

### 1.1 订单服务的核心角色

order-service 是整个电商+物流流程的"大脑"：

```
顾客下单 → 验证地址、商品、扣减库存 → 生成订单
          ↓
商户发货 → 创建配送任务（driver-service）+ 创建物流路线（logistics-service）
          ↓
运输员接单 → 状态更新为派送中
          ↓
送达 → 订单完成
```

### 1.2 接口总览

| 方法 | URL | 功能 | 角色 |
|------|-----|------|------|
| POST | `/api/orders` | 创建订单 | customer |
| GET | `/api/orders/my` | 我的订单列表 | customer / admin |
| GET | `/api/orders/{id}` | 订单详情 | customer/shop/admin |
| GET | `/api/orders/no/{orderNo}` | 按订单号精确查询 | admin |
| GET | `/api/orders/shop/{shopId}` | 商户订单列表 | shop / admin |
| GET | `/api/orders/admin/all` | 全部订单（分页） | admin |
| PUT | `/api/orders/{id}/status` | 更新订单状态，body：`orderStatus`；商户仅允许发货 `2` | shop/admin |
| PUT | `/api/orders/{id}/pay` | 支付订单 | customer |
| PUT | `/api/orders/{id}/cancel` | 取消订单 | customer / admin |
| DELETE | `/api/orders/{id}` | 删除订单（软删除） | customer |
| PUT | `/api/orders/{id}/reopen-pickup` | 订单从派送中回到待揽件 | admin（司机取消接单等场景） |

---

## 2. 目录结构

```
order-service/
├── pom.xml
└── src/main/
    ├── java/com/fm/order/
    │   ├── OrderServiceApplication.java  ← 启动类（端口8086）
    │   ├── config/
    │   │   ├── MyBatisPlusConfig.java    ← 分页插件
    │   │   └── SwaggerConfig.java
    │   ├── controller/
    │   │   └── OrderController.java     ← 所有订单HTTP接口（376行）
    │   ├── dto/
    │   │   ├── CreateOrderRequestDTO.java ← 创建订单请求数据
    │   │   └── OrderDetailDTO.java        ← 订单详情（订单+商品列表）
    │   ├── entity/
    │   │   ├── Order.java               ← order_info表实体
    │   │   └── OrderItem.java           ← order_item表实体
    │   ├── feign/
    │   │   ├── CustomerFeignClient.java  ← 调用customer-service
    │   │   ├── ShopFeignClient.java      ← 调用shop-service
    │   │   ├── DriverFeignClient.java    ← 调用driver-service
    │   │   └── LogisticsFeignClient.java ← 调用logistics-service
    │   ├── mapper/
    │   │   ├── OrderMapper.java
    │   │   └── OrderItemMapper.java
    │   └── service/
    │       ├── OrderService.java
    │       └── impl/
    │           └── OrderServiceImpl.java ← 核心业务逻辑（489行）
    └── resources/
        └── application.yml
```

---

## 3. 数据库设计

### 3.1 两张核心表

```sql
-- 订单主表
CREATE TABLE `order_info` (
  `id`               BIGINT NOT NULL AUTO_INCREMENT,
  `order_no`         VARCHAR(50) NOT NULL UNIQUE,  -- 如：ORD202401011234
  `customer_id`      BIGINT NOT NULL,   -- 关联customer_info.id
  `shop_id`          BIGINT NOT NULL,   -- 关联shop_info.id
  `address_id`       BIGINT NOT NULL,   -- 关联customer_address.id（收货地址快照的引用）
  `total_amount`     DECIMAL(10,2),     -- 总金额（商品+运费）
  `product_amount`   DECIMAL(10,2),     -- 商品金额
  `shipping_fee`     DECIMAL(10,2),     -- 运费（当前系统为0）
  `order_status`     INT,               -- 订单状态（见下文状态机）
  `payment_status`   INT,               -- 0未支付 1已支付
  `payment_time`     DATETIME,
  `shipping_time`    DATETIME,          -- 发货时间
  `complete_time`    DATETIME,          -- 完成时间
  `cancel_time`      DATETIME,
  `cancel_reason`    VARCHAR(500),
  `remark`           VARCHAR(500),      -- 顾客备注
  `warehouse_id`     BIGINT,            -- 发货仓库ID（物流路线起点）
  `customer_deleted` INT DEFAULT 0,     -- 0正常 1顾客软删除
  `create_time`      DATETIME,
  `update_time`      DATETIME,
  PRIMARY KEY (`id`)
);

-- 订单商品明细表
CREATE TABLE `order_item` (
  `id`            BIGINT NOT NULL AUTO_INCREMENT,
  `order_id`      BIGINT NOT NULL,      -- 关联order_info.id
  `product_id`    BIGINT NOT NULL,
  `product_name`  VARCHAR(200),         -- 商品名称快照（下单时复制，商品改名不影响历史订单）
  `product_image` VARCHAR(500),         -- 商品图片快照
  `product_price` DECIMAL(10,2),        -- 下单时的商品价格快照
  `quantity`      INT,
  `subtotal`      DECIMAL(10,2),        -- product_price * quantity
  `create_time`   DATETIME,
  `update_time`   DATETIME,
  PRIMARY KEY (`id`)
);
```

**快照（Snapshot）设计**：

`order_item` 中存储了 `product_name`、`product_image`、`product_price`，而不是通过 `product_id` 实时查询商品信息。

**原因**：商品信息可能变化（改名、改价、下架甚至删除），但已创建的订单不应受影响。

**类比**：你在餐厅点了一份牛肉面（订单创建），即使餐厅后来把牛肉面改名为"豪华牛肉面"并涨价，你的这份订单依然记录的是当时的名字和价格。

---

## 4. 订单生命周期（状态机）

### 4.1 订单状态定义

```
orderStatus 值：
  0 = 待支付（顾客下单但未付款，当前系统简化为下单即付）
  1 = 待发货（已支付，等待商户发货）
  2 = 待揽件（商户已发货，等待运输员接单）
  3 = 派送中（运输员已接单正在配送）
  4 = 已完成（运输员送达，订单关闭）
  5 = 已取消（取消了）
```

### 4.2 状态转换图

```
下单后直接进入 → 【1：待发货】（`createOrder` 内 `orderStatus=1`、`paymentStatus=1`，毕设简化）
                        ↓ 商户点"发货"，PUT body `orderStatus=2`
                  【2：待揽件】 ← ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐
                        ↓ 运输员接单                    │ 司机取消配送（driver-service调用）
                  【3：派送中】                          │
                        ↓ 运输员送达
                  【4：已完成】

          【1：待发货】或下单时 → 【5：已取消】（顾客取消）
          【2：待揽件】或更早  → 无法取消（商品已发出）
```

### 4.3 状态校验逻辑

每次状态变更前，系统会验证"当前状态是否允许变更到目标状态"：

```java
// 发货（商户调用，status=2）
if (!Integer.valueOf(1).equals(order.getOrderStatus())) {
    throw new BusinessException(..., "需为待发货:1，当前:" + order.getOrderStatus());
}

// 变为派送中（运输员接单时，由driver-service内部调用，status=3）
if (!Integer.valueOf(2).equals(order.getOrderStatus())) {
    throw new BusinessException(..., "需为待揽件:2，当前:" + order.getOrderStatus());
}

// 变为已完成（运输员送达时，由driver-service内部调用，status=4）
if (!Integer.valueOf(3).equals(order.getOrderStatus())) {
    throw new BusinessException(..., "需为派送中:3，当前:" + order.getOrderStatus());
}

// 取消（仅待支付=0或待发货=1时可取消）
if (order.getOrderStatus() != 0 && order.getOrderStatus() != 1) {
    throw new BusinessException(..., "当前状态不允许取消");
}
```

---

## 5. OpenFeign 跨服务调用

order-service 是本系统中调用其他服务最多的模块，使用 4 个 Feign 客户端。

### 5.1 什么是 Feign？

**OpenFeign** 让你用定义接口的方式来描述"我需要调用哪个服务的哪个接口"，不需要手写 `HttpURLConnection` 或 `RestTemplate` 等复杂代码。

```java
// 传统 RestTemplate 方式（繁琐）
String url = "http://customer-service/api/customers/address/" + addressId;
ResponseEntity<Result> response = restTemplate.getForEntity(url, Result.class);

// OpenFeign 方式（简洁，像调用本地方法）
Result<Map<String, Object>> result = customerFeignClient.getAddressById(addressId);
```

### 5.2 四个 Feign 客户端

**CustomerFeignClient — 调用顾客服务**

```java
@FeignClient(name = "customer-service", path = "/api/customers")
// name：Nacos 中注册的服务名
// path：所有方法的URL前缀
public interface CustomerFeignClient {

    // 按地址ID查地址详情（验证地址是否属于当前顾客 + 获取经纬度）
    @GetMapping("/address/{addressId}")
    Result<Map<String, Object>> getAddressById(@PathVariable Long addressId);

    // 按userId查顾客（将userId转换为customerId）
    @GetMapping("/internal/user/{userId}")
    Result<Map<String, Object>> getCustomerByUserId(@PathVariable Long userId);
}
```

**ShopFeignClient — 调用商铺服务**

```java
@FeignClient(name = "shop-service", path = "/api")
public interface ShopFeignClient {

    // 获取商品信息（验证商品存在、上架、属于该商户）
    @GetMapping("/products/{productId}/internal")
    Result<Map<String, Object>> getProduct(@PathVariable Long productId);

    // 查询商品在各仓库的库存分布
    @GetMapping("/stocks/internal/product/{productId}")
    Result<List<Map<String, Object>>> getStockByProduct(@PathVariable Long productId);

    // 原子扣减库存（防超卖的关键调用）
    @PostMapping("/stocks/internal/deduct")
    Result<Boolean> deductStock(@RequestParam Long warehouseId,
                                @RequestParam Long productId,
                                @RequestParam Integer quantity);

    // 获取仓库信息（用于物流路线的起点地址和经纬度）
    @GetMapping("/warehouses/internal/{warehouseId}")
    Result<Map<String, Object>> getWarehouseById(@PathVariable Long warehouseId);
}
```

**DriverFeignClient — 调用运输员服务**

```java
@FeignClient(name = "driver-service", path = "/api/drivers/deliveries")
public interface DriverFeignClient {

    // 发货时，创建配送任务（进入待接单大厅）
    @PostMapping("/create")
    Result<Map<String, Object>> createDelivery(
            @RequestParam Long orderId,
            @RequestParam String deliveryAddress,
            @RequestParam String receiverName,
            @RequestParam String receiverPhone);
}
```

**LogisticsFeignClient — 调用物流服务**

```java
@FeignClient(name = "logistics-service")
public interface LogisticsFeignClient {

    // 发货时，创建物流路线（规划从仓库到收货地址的路线）
    @PostMapping("/api/logistics/routes")
    Result<Map<String, Object>> createRoute(@RequestBody Map<String, Object> request);
}
```

### 5.3 服务间调用的依赖图

```
order-service
    ├── 下单时 →→ customer-service（验证地址归属）
    ├── 下单时 →→ shop-service（验证商品、扣减库存、获取仓库信息）
    ├── 发货时 →→ driver-service（创建配送任务）
    └── 发货时 →→ logistics-service（创建物流路线）
```

---

## 6. 创建订单：最复杂的业务流程

### 6.1 CreateOrderRequestDTO — 创建订单请求格式

```java
@Data
public class CreateOrderRequestDTO {
    private Long shopId;       // 向哪家商铺购买
    private Long addressId;    // 使用哪个收货地址
    private List<OrderItemDTO> items;  // 购买的商品列表
    private String remark;     // 备注

    @Data
    public static class OrderItemDTO {  // 内部类（静态成员类）
        private Long productId;   // 哪个商品
        private Integer quantity; // 买几件
    }
}
```

**请求JSON示例：**
```json
{
  "shopId": 1,
  "addressId": 3,
  "items": [
    {"productId": 10, "quantity": 2},
    {"productId": 11, "quantity": 1}
  ],
  "remark": "请尽快发货"
}
```

**内部静态类（Static Inner Class）解释**：

`OrderItemDTO` 定义在 `CreateOrderRequestDTO` 内部，引用时写 `CreateOrderRequestDTO.OrderItemDTO`。`static` 表示它不依赖外部类的实例（可以独立创建）。这样做的好处是把密切相关的类组织在一起，逻辑清晰。

### 6.2 七步创建订单详解

```java
@Transactional  // 整个创建过程在一个事务中
public OrderDetailDTO createOrder(Long customerId, CreateOrderRequestDTO request) {
```

**第一步：验证收货地址**

```java
// 通过 Feign 调用 customer-service 查询地址
Result<Map<String, Object>> addrResult = customerFeignClient.getAddressById(request.getAddressId());
if (addrResult.getCode() != 200 || addrResult.getData() == null) {
    throw new BusinessException(ResultCode.FAIL.getCode(), "收货地址不存在");
}

// 验证地址属于当前顾客
Map<String, Object> addrData = addrResult.getData();
Long addrCustomerId = Long.valueOf(addrData.get("customerId").toString());
if (!addrCustomerId.equals(customerId)) {
    throw new BusinessException(ResultCode.FAIL.getCode(), "收货地址不属于当前顾客");
}
```

**为什么要验证地址归属？**

防止恶意用户传入别人的 addressId，使用他人地址下单。

**第二步：逐一验证商品并扣减库存**

```java
BigDecimal productAmount = BigDecimal.ZERO;  // 商品总金额（从0开始累加）
List<OrderItem> orderItems = new ArrayList<>();
Long firstWarehouseId = null;  // 记录发货仓库

for (CreateOrderRequestDTO.OrderItemDTO itemDTO : request.getItems()) {

    // ① 获取商品信息（Feign调用shop-service）
    Result<Map<String, Object>> productResult = shopFeignClient.getProduct(itemDTO.getProductId());

    // ② 验证商品属于该商铺
    Long productShopId = Long.valueOf(productData.get("shopId").toString());
    if (!productShopId.equals(request.getShopId())) {
        throw new BusinessException(ResultCode.FAIL.getCode(), "商品不属于该商户");
    }

    // ③ 验证商品已上架
    Integer productStatus = Integer.valueOf(productData.get("status").toString());
    if (productStatus != 1) {
        throw new BusinessException(ResultCode.FAIL.getCode(), "商品已下架或待审核");
    }

    // ④ 查询库存分布（哪个仓库有货）
    Result<List<Map<String, Object>>> stockResult = shopFeignClient.getStockByProduct(itemDTO.getProductId());

    // ⑤ 找到有足够库存的仓库
    Long warehouseId = null;
    for (Map<String, Object> stock : stockList) {
        Integer availableStock = Integer.valueOf(stock.get("stock").toString());
        if (availableStock >= itemDTO.getQuantity()) {
            warehouseId = Long.valueOf(stock.get("warehouseId").toString());
            break;
        }
    }
    if (warehouseId == null) {
        throw new BusinessException(ResultCode.FAIL.getCode(), "商品库存不足");
    }

    // ⑥ 原子扣减库存（防超卖）
    Result<Boolean> deductResult = shopFeignClient.deductStock(warehouseId, itemDTO.getProductId(), itemDTO.getQuantity());
    if (!Boolean.TRUE.equals(deductResult.getData())) {
        throw new BusinessException(ResultCode.FAIL.getCode(), "库存扣减失败，可能库存已被其他订单占用");
    }

    // ⑦ 计算小计，创建订单项对象
    BigDecimal price = new BigDecimal(productData.get("price").toString());
    BigDecimal subtotal = price.multiply(new BigDecimal(itemDTO.getQuantity()));
    productAmount = productAmount.add(subtotal);

    OrderItem orderItem = new OrderItem();
    orderItem.setProductName(productData.get("productName").toString()); // 快照！
    orderItem.setProductPrice(price);  // 快照！
    orderItem.setQuantity(itemDTO.getQuantity());
    orderItem.setSubtotal(subtotal);
    orderItems.add(orderItem);
}
```

**第三步：生成订单号**

```java
private String generateOrderNo() {
    // 格式：ORD + 时间戳（精确到秒）+ 4位随机数
    String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    int random = (int) (Math.random() * 10000);
    return "ORD" + dateTime + String.format("%04d", random);
}
// 示例输出：ORD202401011200120034
```

**`String.format("%04d", random)` 解释**：

- `%d`：十进制整数
- `04`：最少4位，不足4位补0（如 `34` → `"0034"`）

**为什么需要4位随机数？**

时间戳精确到秒，同一秒内可能有多个用户下单，随机数确保订单号不重复（虽然极小概率下也可能重复，生产环境会用分布式ID生成器如 Snowflake）。

**第四步：创建订单记录**

```java
Order order = new Order();
order.setOrderNo(orderNo);
order.setCustomerId(customerId);
order.setShopId(request.getShopId());
order.setAddressId(request.getAddressId());
order.setProductAmount(productAmount);
order.setShippingFee(BigDecimal.ZERO); // 运费暂时为0（简化处理）
order.setTotalAmount(productAmount);   // 总金额 = 商品金额 + 运费
order.setWarehouseId(firstWarehouseId); // 发货仓库（物流路线起点）
order.setOrderStatus(1);   // 直接进入待发货（毕设简化：无真实支付流程）
order.setPaymentStatus(1); // 直接标记已支付
order.setPaymentTime(new Date());
orderMapper.insert(order); // 插入后，order.id 被自动填充
```

**第五步：创建订单商品明细**

```java
for (OrderItem orderItem : orderItems) {
    orderItem.setOrderId(order.getId()); // 设置关联的订单ID（第四步insert后才有id）
    orderItemMapper.insert(orderItem);
}
```

**注意顺序**：必须先 `insert(order)` 得到 `order.id`，再用 `order.id` 设置 `orderItem.orderId`，最后 `insert(orderItem)`。事务保证两个 insert 要么都成功，要么都回滚。

---

## 7. 发货：触发配送和物流的关键操作

当商户点击"发货"时，调用 `PUT /api/orders/{id}/status`，传入 `status=2`（待揽件）。

这是整个系统中"扇出调用"最多的操作：

```
updateOrderStatus(orderId, status=2)
    ├── 校验订单状态（必须为待发货=1）
    ├── 校验已支付
    ├── 调用 customer-service → 获取收货地址（收件人、电话、省市区）
    ├── 调用 driver-service → createDelivery（创建配送任务，进入待接单大厅）
    ├── 调用 shop-service → getWarehouseById（获取仓库地址和经纬度）
    └── 调用 logistics-service → createRoute（规划物流路线）
```

**代码中的错误处理策略**：

```java
try {
    Result<Map<String, Object>> deliveryResult = driverFeignClient.createDelivery(...);
    if (deliveryResult.getCode() != 200) {
        System.err.println("创建配送记录失败: " + deliveryResult.getMessage());
        // 不抛异常！只记录日志，不影响订单状态变更
    }
} catch (Exception e) {
    System.err.println("创建配送记录异常: " + e.getMessage());
    // 不回滚！即使配送服务挂了，订单发货依然成功
}
```

**为什么不因为配送/物流创建失败而回滚？**

这是**"最终一致性"**的设计思想，适合微服务场景：
- 如果因为物流服务暂时不可用就回滚整个发货操作，用户体验很差
- 更好的做法是：订单状态成功变为"待揽件"，配送记录和物流路线可以通过重试或人工补偿后续处理

与之对比，`@Transactional` 事务只保证本地数据库操作的原子性，跨服务的调用无法纳入同一个事务（这就是分布式事务问题）。

---

## 8. 其他订单操作

### 8.1 取消订单

```java
@Transactional
public boolean cancelOrder(Long orderId, String cancelReason) {
    Order order = orderMapper.selectById(orderId);

    // 只有待支付(0)和待发货(1)可以取消
    if (order.getOrderStatus() != 0 && order.getOrderStatus() != 1) {
        throw new BusinessException(ResultCode.FAIL.getCode(), "当前订单状态不允许取消");
    }

    LambdaUpdateWrapper<Order> wrapper = new LambdaUpdateWrapper<>();
    wrapper.eq(Order::getId, orderId)
           .set(Order::getOrderStatus, 5)         // 已取消
           .set(Order::getCancelTime, new Date())
           .set(Order::getCancelReason, cancelReason);

    return orderMapper.update(null, wrapper) > 0;
}
```

**为什么待揽件(2)之后不能取消？**

商品已经出库（库存已扣减、配送任务已创建），取消需要退货退款，逻辑复杂，本系统简化处理（只允许发货前取消）。

### 8.2 软删除订单

```java
@Transactional
public boolean deleteOrderByCustomer(Long orderId, Long customerId) {
    Order order = orderMapper.selectById(orderId);

    // 只能删除自己的订单
    if (!order.getCustomerId().equals(customerId)) {
        throw new BusinessException(ResultCode.FAIL.getCode(), "无权操作该订单");
    }

    // 只有已取消的订单可以"删除"（软删除）
    if (order.getOrderStatus() != 5) {
        throw new BusinessException(ResultCode.FAIL.getCode(), "只有已取消的订单可以删除");
    }

    // 软删除：只是标记，不真正删除
    LambdaUpdateWrapper<Order> wrapper = new LambdaUpdateWrapper<>();
    wrapper.eq(Order::getId, orderId)
           .set(Order::getCustomerDeleted, 1);  // 0→1，顾客端不再显示
    return orderMapper.update(null, wrapper) > 0;
}
```

**软删除的查询配合**：

查询"我的订单"时，会过滤掉软删除的记录：
```java
wrapper.ne(Order::getCustomerDeleted, 1);  // 不等于1（即只查customerDeleted=0的）
```

`ne(field, val)` = NOT EQUAL，对应 SQL `WHERE customer_deleted != 1`。

### 8.3 分页查询全部订单（管理员）

```java
public PageResult<Order> getAllOrders(Long current, Long size, Integer status) {
    Page<Order> page = new Page<>(current, size);
    LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();

    if (status != null) {
        wrapper.eq(Order::getOrderStatus, status); // 按状态筛选（可选）
    }
    wrapper.orderByDesc(Order::getCreateTime); // 最新订单排在前面

    IPage<Order> result = orderMapper.selectPage(page, wrapper);
    // orderMapper.selectPage 触发分页拦截器，自动加 LIMIT/OFFSET

    return new PageResult<>(result.getCurrent(), result.getSize(),
                            result.getTotal(), result.getRecords());
}
```

---

## 9. 事务与 ServiceImpl 继承

### 9.1 extends ServiceImpl

```java
@Service
public class OrderServiceImpl
        extends ServiceImpl<OrderMapper, Order>
        implements OrderService {
```

`extends ServiceImpl<OrderMapper, Order>` 是 MyBatis Plus 提供的服务基类，继承后自动拥有：

```java
// ServiceImpl 提供的方法（不用自己写）
this.save(entity);           // INSERT
this.updateById(entity);     // UPDATE by id
this.removeById(id);         // DELETE by id
this.getById(id);            // SELECT by id
this.list();                 // SELECT all
this.page(page, wrapper);    // SELECT with pagination
```

但本模块直接用 `orderMapper.xxx()` 方法，而不太多用 `ServiceImpl` 提供的方法，因为需要更精细的控制（如 `LambdaUpdateWrapper` 多字段更新）。

### 9.2 @Transactional 的注意事项

**自调用失效**：

```java
@Service
public class OrderServiceImpl {

    public void methodA() {
        this.methodB();  // 自调用！事务失效！
    }

    @Transactional
    public void methodB() {
        // 这里的事务不会生效！
    }
}
```

**原因**：`@Transactional` 是通过 Spring AOP 代理实现的，只有通过 Spring 注入的代理对象调用才能触发事务，`this.` 调用绕过了代理。

**解决方案**：把需要事务的方法提取到单独的 Service 中，或者注入自身的代理对象（不推荐）。本项目中 `createOrder` 等核心方法都是被 Controller 通过 Spring 注入的代理对象调用，不存在此问题。

---

## 10. 关键技术深度解析

### 10.1 `LocalDateTime` 和 `DateTimeFormatter`

```java
String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
```

**`LocalDateTime` vs `Date`**：

| 类 | 包 | 特点 |
|----|----|------|
| `Date` | `java.util` | 旧API，线程不安全，设计不好 |
| `LocalDateTime` | `java.time` | Java 8新API，不可变，线程安全，推荐使用 |

**时间格式字符**：

| 字符 | 含义 | 示例 |
|------|------|------|
| `yyyy` | 4位年份 | 2024 |
| `MM` | 2位月份 | 01 |
| `dd` | 2位日期 | 01 |
| `HH` | 2位小时（24h制） | 12 |
| `mm` | 2位分钟 | 00 |
| `ss` | 2位秒 | 34 |

### 10.2 Jackson 读取 JSON 数组

```java
// 商品图片字段是JSON数组字符串
String images = "[\"https://img1.jpg\", \"https://img2.jpg\"]";

// 用 Jackson 解析为 List<String>
List<String> imageList = objectMapper.readValue(
    images,
    new TypeReference<List<String>>() {}  // 泛型类型令牌
);
// imageList.get(0) = "https://img1.jpg"
```

**`TypeReference<List<String>>` 解释**：

由于 Java 的**类型擦除**（泛型在运行时会被擦除），`List<String>.class` 是非法的写法。`TypeReference<List<String>>` 是 Jackson 提供的解决方案：通过创建匿名子类来保留泛型类型信息（这是一种常见的 Java 泛型技巧）。

### 10.3 `Result.getCode() != 200` 的防御性编程

```java
Result<Map<String, Object>> addrResult = customerFeignClient.getAddressById(requestAddressId);
if (addrResult.getCode() != 200 || addrResult.getData() == null) {
    throw new BusinessException(...);
}
```

即使对方服务返回了 HTTP 200，也要检查业务状态码（`code` 字段）是否为 200。防御性编程原则：永远不要假设外部服务一定按预期返回。

### 10.4 `ServiceImpl<M, T>` 的泛型含义

```java
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order>
```

- `M`（第一个泛型）：Mapper 类型，即 `OrderMapper`
- `T`（第二个泛型）：实体类型，即 `Order`

`ServiceImpl` 通过这两个泛型参数知道用哪个 Mapper 和操作哪个表，自动生成对应的 SQL 操作。

---

## 附录：知识点速查表

| 知识点 | 说明 |
|--------|------|
| 订单状态机 | 0待支付→1待发货→2待揽件→3派送中→4已完成/5已取消 |
| 快照数据 | order_item存商品名/价格快照，历史订单不受商品变更影响 |
| OpenFeign | 声明式HTTP客户端，4个Feign客户端调用其他服务 |
| 原子扣减库存 | 调用shop-service的`deductStock`，SQL保证防超卖 |
| 订单号生成 | `ORD + yyyyMMddHHmmss + 4位随机数` |
| `BigDecimal` | 金融计算用精确十进制，避免浮点数精度问题 |
| 软删除 | `customerDeleted=1`标记，查询时`ne(customerDeleted, 1)` |
| `@Transactional` | 本地事务（多个insert/update要么全成功要么全回滚） |
| 最终一致性 | 调用其他服务失败只记日志，不回滚本地事务 |
| `ServiceImpl` | MyBatis Plus服务基类，继承获得CRUD方法 |
| `LocalDateTime` | Java 8新时间API，线程安全，比Date更好用 |
| `TypeReference` | Jackson解析泛型集合类型（如`List<String>`）时使用 |
| `String.format("%04d", n)` | 格式化整数，不足4位补0 |
| 多扇出调用 | 发货操作依次调用customer/driver/shop/logistics四个服务 |
