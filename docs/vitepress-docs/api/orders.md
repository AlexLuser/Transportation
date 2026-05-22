---
title: 订单服务 API
---

# 🔌 订单服务 API

Base URL: `http://localhost:8085/api/orders`

## 📦 接口列表

| Method | URI | 说明 | 需要认证 |
|--------|-----|------|----------|
| POST | `/` | B2C 下单 | ✅ |
| POST | `/personal-shipment` | C2C 个人寄件 | ✅ |
| PUT | `/{id}/pay` | 支付订单 | ✅ |
| POST | `/{id}/ship` | 商户发货 | ✅ (商户) |
| GET | `/{id}` | 查询订单详情 | ✅ |
| GET | `/list` | 查询订单列表 | ✅ |

## 📝 接口详情

### POST /

**功能**：B2C 下单

**请求体**：
```json
{
  "shopId": 1,
  "addressId": 100,
  "items": [
    {
      "productId": 1,
      "quantity": 2
    }
  ],
  "remark": "请尽快发货"
}
```

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 123,
    "orderNo": "ORD2026052012345678",
    "status": 1,
    "paymentStatus": 1,
    "totalAmount": 199.99
  }
}
```

### POST /personal-shipment

**功能**：C2C 个人寄件

**请求体**：
```json
{
  "senderAddressId": 100,
  "deliveryAddressId": 101,
  "cargoName": "文件资料",
  "weight": 1.5,
  "declaredValue": 100.0
}
```

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 124,
    "orderNo": "ORD2026052012345679",
    "status": 0,
    "paymentStatus": 0,
    "totalAmount": 13.0
  }
}
```

### PUT /{id}/pay

**功能**：支付订单

**路径参数**：
- `id`: 订单 ID

**响应**：
```json
{
  "code": 200,
  "message": "支付成功",
  "data": {
    "id": 123,
    "status": 2,
    "paymentStatus": 1
  }
}
```

### POST /{id}/ship

**功能**：商户发货

**路径参数**：
- `id`: 订单 ID

**请求体**：
```json
{
  "warehouseId": 1
}
```

**响应**：
```json
{
  "code": 200,
  "message": "发货成功",
  "data": {
    "id": 123,
    "status": 2,
    "originHubId": 1,
    "destHubId": 2
  }
}
```

## 📊 订单状态枚举

| 状态码 | 说明 | 触发条件 |
|--------|------|----------|
| 0 | 待支付 | 下单后未支付 |
| 1 | 待发货 | 已支付，等待商户发货 |
| 2 | 待揽件 | 已发货，等待运输员取件 |
| 3 | 运输中 | 已揽件，正在运输 |
| 4 | 已完成 | 已签收 |
| 5 | 已取消 | 用户取消 |
| 6 | 待签收 | 已到达目的地，等待签收 |

## 🧪 测试示例

### cURL

```bash
# 下单
curl -X POST http://localhost:8085/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{
    "shopId": 1,
    "addressId": 100,
    "items": [{"productId": 1, "quantity": 1}]
  }'

# 支付
curl -X PUT http://localhost:8085/api/orders/123/pay \
  -H "Authorization: Bearer {token}"

# 发货（商户）
curl -X POST http://localhost:8085/api/orders/123/ship \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{"warehouseId": 1}'
```

## 🎯 下一步

- 想了解 Hub 作业 API？前往 [Hub 作业 API](/api/hub)
- 想了解调度服务 API？前往 [调度服务 API](/api/dispatch)
