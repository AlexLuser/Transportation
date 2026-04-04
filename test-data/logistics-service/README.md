# Logistics Service 测试数据

## 服务信息

- **直连地址**：`http://localhost:8087`
- **网关地址**：`http://localhost:8083`（通过网关访问）
- **Swagger 文档**：`http://localhost:8087/swagger-ui/index.html`

## 鉴权说明

Logistics Service **不走 JWT**，在请求头中直接传入以下字段：

| Header     | 说明               | 示例值    |
|------------|--------------------|-----------|
| `userId`   | 当前用户ID（必填） | `1`       |
| `roleCode` | 角色码（部分接口） | `admin` / `driver` |

> 注意：轨迹上报接口中 `userId` 目前被直接当作 `driverId` 使用（待 B6 任务修正），上报时请传 `userId: 5`（对应 driver_info.id=1 的运输员李四的 user_id）。

---

## 文件说明

### 路线管理相关

| 文件 | 用途 |
|------|------|
| `json/create_route_request.json`         | 创建路线请求（orderId=2，触发 GraphHopper 规划） |
| `json/bind_driver_request.json`          | 绑定运输员请求 |
| `json/update_status_delivered_request.json` | 更新路线状态为已送达(2) |
| `json/update_status_exception_request.json` | 更新路线状态为异常(3) |
| `json/create_route_response_example.json`   | 创建路线成功响应示例 |
| `json/route_detail_response_example.json`   | 路线详情响应示例（含节点+轨迹） |
| `json/pending_routes_response_example.json` | 待出发路线列表响应示例 |

### 轨迹上报相关

| 文件 | 用途 |
|------|------|
| `json/location_update_request.json`      | 运输员上报 GPS 位置请求 |
| `json/track_latest_response_example.json`   | 最新轨迹点响应示例 |
| `json/track_history_response_example.json`  | 完整轨迹历史响应示例（3条，时间正序） |

---

## 测试场景

### 场景1：查询已有路线（使用 logistics.sql 测试数据）

#### 1.1 按物流单号查询（无需登录）
```
GET http://localhost:8087/api/logistics/routes/no/LR202603131200000001
```
预期：返回运输中的路线详情 + 2个节点 + 3条轨迹
参考响应：`route_detail_response_example.json`

#### 1.2 按路线ID查询
```
GET http://localhost:8087/api/logistics/routes/1
Header: userId: 2
```

#### 1.3 按订单ID查询
```
GET http://localhost:8087/api/logistics/routes/order/1
Header: userId: 2
```

#### 1.4 查询运输员的路线列表
```
GET http://localhost:8087/api/logistics/routes/driver/1
Header: userId: 5
```

#### 1.5 查询待出发路线列表（管理员）
```
GET http://localhost:8087/api/logistics/routes/pending
Header: userId: 1
        roleCode: admin
```
预期：执行 create_route_request 后，新路线（route_id=2）会出现在此列表
参考响应：`pending_routes_response_example.json`

---

### 场景2：创建路线（触发 GraphHopper 路径规划）★ 核心测试

```
POST http://localhost:8087/api/logistics/routes
Content-Type: application/json

使用文件: create_route_request.json
```

预期：
- 返回新路线，`plannedRoute` 字段包含 GraphHopper 计算出的 GeoJSON LineString 路径
- `estimatedArrivalTime` 根据行驶时长自动计算
- 数据库自动创建出发节点（type=0）和目的地节点（type=2）

参考响应：`create_route_response_example.json`

> ⚠️ 坐标须在上海范围内，否则 GraphHopper（shanghai OSM）无法规划路径

---

### 场景3：轨迹上报与实时追踪

#### 3.1 上报 GPS 位置（运输员）
```
POST http://localhost:8087/api/logistics/track/location
Header: userId: 5
        roleCode: driver
Content-Type: application/json

使用文件: location_update_request.json
```
预期：新增轨迹点，同时更新 `logistics_route` 表的 `current_lat`/`current_lng` 和 `last_track_time`

#### 3.2 查看最新位置
```
GET http://localhost:8087/api/logistics/track/1/latest
Header: userId: 2
```
参考响应：`track_latest_response_example.json`

#### 3.3 查看最近 N 条轨迹
```
GET http://localhost:8087/api/logistics/track/1/recent?limit=50
Header: userId: 2
```

#### 3.4 查看完整轨迹历史（时间正序，用于地图回放）
```
GET http://localhost:8087/api/logistics/track/1/history
Header: userId: 2
```
参考响应：`track_history_response_example.json`

---

### 场景4：绑定运输员并更新状态

#### 4.1 为新路线绑定运输员（模拟接单）
```
PUT http://localhost:8087/api/logistics/routes/2/bind
Content-Type: application/json

使用文件: bind_driver_request.json
```
预期：route_status 由 0(待出发) 变为 1(运输中)

#### 4.2 标记已送达
```
PUT http://localhost:8087/api/logistics/routes/1/status
Header: userId: 5
        roleCode: driver
Content-Type: application/json

使用文件: update_status_delivered_request.json
```
预期：route_status 变为 2，`actualArrivalTime` 自动记录当前时间

#### 4.3 标记运输异常
```
PUT http://localhost:8087/api/logistics/routes/1/status
Header: userId: 1
        roleCode: admin
Content-Type: application/json

使用文件: update_status_exception_request.json
```

---

### 推荐测试顺序

```
① GET  /routes/no/LR202603131200000001       验证已有数据可正常读取
② GET  /track/1/history                      验证3条历史轨迹正确
③ POST /track/location                       上报新GPS位置
④ GET  /track/1/latest                       确认当前位置已更新
⑤ POST /routes (create_route_request.json)  ★ 触发 GraphHopper 路径规划
⑥ GET  /routes/2                             确认 plannedRoute 有 GeoJSON 数据
⑦ GET  /routes/pending                       查看待出发路线列表
⑧ PUT  /routes/2/bind                        绑定运输员，状态变运输中
⑨ PUT  /routes/1/status (status=2)           标记送达，验证 actualArrivalTime
```

---

## 测试数据说明

### 数据库预置数据（logistics.sql）

| 表 | 数据 | 说明 |
|----|------|------|
| `logistics_route` | id=1，route_status=1 | 运输中，可直接查询 |
| `logistics_node`  | id=1(出发点，已到达)，id=2(目的地，未到达) | route_id=1 的2个节点 |
| `logistics_track` | id=1~3，共3条 | 从仓库→张杨路→世纪大道的轨迹 |

### 坐标范围（上海浦东，与 shanghai-260310.osm.pbf 匹配）

| 地点 | 纬度 | 经度 |
|------|------|------|
| 上海华东仓库（出发）  | 31.1985 | 121.5889 |
| 张杨路附近（途中）    | 31.2100 | 121.5640 |
| 世纪大道附近（当前）  | 31.2256 | 121.5350 |
| 陆家嘴环路（目的地）  | 31.2356 | 121.5050 |
| 静安区南京西路（备用）| 31.2289 | 121.4490 |

### 关键ID对照

| ID | 含义 |
|----|------|
| `userId: 1` | admin 管理员 |
| `userId: 2` | customer 顾客张三 |
| `userId: 5` | driver 运输员李四（driver_id=1） |
| `orderId: 1` | 待揽件订单（logistics_route 已存在，不可重复创建） |
| `orderId: 2` | 已支付待发货（用于测试创建路线） |
| `routeId: 1` | 测试路线（运输中） |
| `routeNo: LR202603131200000001` | 测试路线编号 |

### 注意事项

1. **幂等保护**：`orderId=1` 的路线已存在，对其调用 `POST /routes` 会直接返回现有路线，不会重复创建
2. **坐标范围**：创建路线时坐标必须在上海范围内，否则 GraphHopper 无法规划
3. **轨迹上报权限**：`roleCode` 必须为 `driver`，且 `userId` 会作为 `driverId` 写入轨迹记录
4. **状态更新权限**：仅 `admin` 或 `driver` 角色可调用状态变更接口
5. **数据库准备**：测试前请确保已执行 `database/logistics.sql`（依赖 user/driver/shop/order 数据）

