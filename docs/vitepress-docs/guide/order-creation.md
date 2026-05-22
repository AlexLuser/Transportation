---
title: 顾客下单 - 冒险开始
---

# 🛒 序幕：顾客下单，冒险开始！

## 📱 B2C 平台下单：我在网上买了东西

**场景**：你打开 APP，浏览商品，看中了一盒"优质坚果礼盒"，点击"立即下单"...

### 前端交互流程

<MermaidDiagram :code="`sequenceDiagram
    participant U as 用户
    participant F as 前端页面
    participant A as API网关
    participant O as 订单服务
    participant C as 客户服务
    participant S as 商户服务
    participant MQ as 消息队列
    U->>F: 点击立即下单
    F->>A: POST /api/orders
    A->>O: createOrder()
    O->>C: resolveCustomerId(userId)
    C-->>O: customerId
    O->>S: 校验商品库存
    S-->>O: 库存充足
    O->>O: 生成订单号
    O->>MQ: StockDeductMessage
    O-->>F: 订单创建成功
    F->>F: 跳转订单列表
`" />

### 代码详解

**前端页面**：`Customer/CreateOrder.vue` → 点击"提交订单"按钮

```javascript
// 你点击的那一瞬间，前端悄悄做了这些：
submitOrder()
  → POST /api/orders  { shopId, addressId, items, remark }
    → OrderController.createOrder()  // 后端接单啦
      → 检查：你真的是顾客吗？（roleCode 必须是 "customer"）
      → resolveCustomerId(userId)  // 把 userId 翻译成 customerId（Feign 调 customer-service）
      → orderService.createOrder(customerId, request)
        → 校验收货地址是不是你的
        → 校验商品信息和库存（Feign 调 shop-service）
        → 生成订单号："ORD" + 时间戳 + 4位随机  // 比如 ORD2026052012345678
        → 订单状态 = 1(待发货)，支付状态 = 1(已支付)  // 毕设简化：下单即支付
        → MQ 异步扣减库存: StockDeductMessage → ROUTING_STOCK_DEDUCT
  → 页面跳转到 '/sender/home/orders'（你可以在订单列表看到它了）
```

**订单诞生了！** 它现在状态是"待发货"，等着商户来发货。

## 📦 C2C 个人寄件：我要寄个包裹

**场景**：你有东西要寄给朋友，点击"个人寄件"...

### 前端交互流程

<MermaidDiagram :code="`sequenceDiagram
    participant U as 用户
    participant F as 寄件页面
    participant A as API网关
    participant O as 订单服务
    participant L as 物流服务
    U->>F: 填写寄件信息
    F->>A: POST /api/orders/personal-shipment
    A->>O: createPersonalShipment()
    O->>O: 计算运费
    O->>L: assignHubs()
    L-->>O: originHubId, destHubId
    O->>O: 订单状态 = 0(待支付)
    O-->>F: 返回订单信息
    U->>F: 点击支付
    F->>A: PUT /api/orders/id/pay
    A->>O: payOrder()
    O->>L: AddToPoolMessage
    L->>L: 写入 dispatch_pool
    O-->>F: 支付成功
`" />

### 代码详解

**前端页面**：`Customer/CreateShipment.vue` → 三步骤向导（取件地址 → 收件地址 → 货物信息）

```javascript
// 寄件三步走：
submitShipment()
  → POST /api/orders/personal-shipment  { senderAddressId, deliveryAddressId, cargoName, weight, ... }
    → OrderController.createPersonalShipment()
      → 检查：你真的是顾客吗？
      → resolveCustomerId(userId)
      → orderService.createPersonalShipment(customerId, request)
        → 查取件地址（要有坐标哦，不然 Hub 分配会降级）
        → 查收件地址
        → 运费计算：基础 12 元 + 超出 1kg 部分 2 元/kg
        → 订单状态 = 0(待支付)，支付状态 = 0(未支付)
        → 创建 OrderItem（货物名 + 申报价值）
  → 跳转到订单列表

// 然后你需要支付：
payOrder(orderId)
  → PUT /api/orders/{id}/pay
    → OrderServiceImpl.payOrder()
      → 个人寄件分支 (orderType=1):
        → logisticsFeignClient.assignHubs()  // 🎯 重点！分配 Hub
        → orderStatus = 2(待揽件)  // 跳过商户发货（因为是个人寄件嘛）
        → MQ: AddToPoolMessage → ROUTING_ADD_TO_POOL  // 入调度池
        → dispatchOriginType = 2(个人取件/上门揽收)
```

## 🎯 关键差异

| 特性 | B2C 下单 | C2C 寄件 |
|------|-----------|-----------|
| **状态流转** | 0→1（待支付→待发货） | 0→2（待支付→待揽件） |
| **是否需要商户** | ✅ 需要商户发货 | ❌ 系统自动分配 Hub |
| **运费计算** | 商品价格 | 重量计费 |
| **支付后** | 等待商户发货 | 入调度池，等待揽收 |

## 📊 订单状态机

<MermaidDiagram :code="`stateDiagram-v2
    [*] --> 待支付: 创建订单
    待支付 --> 待发货: 支付B2C
    待支付 --> 待揽件: 支付C2C
    待发货 --> 待揽件: 商户发货
    待揽件 --> 运输中: 揽收入库
    运输中 --> 待签收: 到达目的地
    待签收 --> 已完成: 签收
    待支付 --> 已取消: 取消订单
    待发货 --> 已取消: 取消订单
`" />

## 🎯 下一步

- 想了解商户怎么发货？前往 [商户发货](/guide/merchant-ship)
- 想了解 Hub 中转站？前往 [中转站流水线](/guide/hub-operations)
