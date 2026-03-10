# Driver Service 测试数据

## 文件说明

### 运输员信息相关
- `json/driver_add_request.json` - 添加运输员信息请求
- `json/driver_update_request.json` - 更新运输员信息请求
- `json/driver_response_example.json` - 运输员信息响应示例

### 车辆信息相关
- `json/vehicle_add_request.json` - 添加车辆请求
- `json/vehicle_update_request.json` - 更新车辆请求
- `json/vehicle_response_example.json` - 车辆信息响应示例
- `json/vehicle_list_response_example.json` - 车辆列表响应示例

### 配送订单相关
- `json/delivery_pending_list_response_example.json` - 待接单订单列表响应示例
- `json/delivery_my_list_response_example.json` - 我的配送订单列表响应示例
- `json/delivery_response_example.json` - 配送详情响应示例
- `json/delivery_accept_request.json` - 接单请求
- `json/delivery_status_update_request.json` - 更新配送状态请求
- `json/delivery_cancel_request.json` - 取消配送请求

## 测试场景

### 场景1：运输员信息管理

#### 1.1 获取运输员信息
```bash
GET http://localhost:8083/api/drivers
Authorization: Bearer {driver_token}
```
Header: `userId: 5`, `roleCode: driver`
参考响应：`driver_response_example.json`

#### 1.2 添加运输员信息
```bash
POST http://localhost:8083/api/drivers
Authorization: Bearer {driver_token}
Content-Type: application/json
Header: userId: 5

使用文件: driver_add_request.json
```

#### 1.3 修改运输员信息
```bash
PUT http://localhost:8083/api/drivers
Authorization: Bearer {driver_token}
Content-Type: application/json
Header: userId: 5

使用文件: driver_update_request.json
```

### 场景2：车辆信息管理

#### 2.1 获取车辆列表
```bash
GET http://localhost:8083/api/drivers/vehicles
Authorization: Bearer {driver_token}
```
参考响应：`vehicle_list_response_example.json`

#### 2.2 添加车辆
```bash
POST http://localhost:8083/api/drivers/vehicles
Authorization: Bearer {driver_token}
Content-Type: application/json
Header: userId: 5

使用文件: vehicle_add_request.json
```

#### 2.3 修改车辆信息
```bash
PUT http://localhost:8083/api/drivers/vehicles
Authorization: Bearer {driver_token}
Content-Type: application/json
Header: userId: 5

使用文件: vehicle_update_request.json
```

#### 2.4 更新车辆状态
```bash
PUT http://localhost:8083/api/drivers/vehicles/1/status?status=1
Authorization: Bearer {driver_token}
Header: userId: 5
```

#### 2.5 删除车辆
```bash
DELETE http://localhost:8083/api/drivers/vehicles/1
Authorization: Bearer {driver_token}
Header: userId: 5
```

### 场景3：配送订单管理

#### 3.1 获取待接单订单列表（分页）
```bash
GET http://localhost:8083/api/drivers/deliveries/pending?current=1&size=10
Authorization: Bearer {driver_token}
```
参考响应：`delivery_pending_list_response_example.json`

#### 3.2 接单
```bash
POST http://localhost:8083/api/drivers/deliveries/1/accept
Authorization: Bearer {driver_token}
Content-Type: application/json
Header: userId: 5

使用文件: delivery_accept_request.json
```

#### 3.3 获取我的配送订单列表（分页）
```bash
GET http://localhost:8083/api/drivers/deliveries?current=1&size=10
Authorization: Bearer {driver_token}
Header: userId: 5
```

#### 3.4 获取我的配送订单列表（按状态筛选）
```bash
GET http://localhost:8083/api/drivers/deliveries?current=1&size=10&status=1
Authorization: Bearer {driver_token}
Header: userId: 5
```
说明：status=1表示已接单状态

#### 3.5 获取配送详情
```bash
GET http://localhost:8083/api/drivers/deliveries/1
Authorization: Bearer {driver_token}
Header: userId: 5
```
参考响应：`delivery_response_example.json`

#### 3.6 更新配送状态（取货/运输中）
```bash
PUT http://localhost:8083/api/drivers/deliveries/1/status
Authorization: Bearer {driver_token}
Content-Type: application/json
Header: userId: 5

使用文件: delivery_status_update_request.json (status=2)
```

#### 3.7 更新配送状态（已送达）
```bash
PUT http://localhost:8083/api/drivers/deliveries/1/status
Authorization: Bearer {driver_token}
Content-Type: application/json
Header: userId: 5

使用文件: delivery_status_update_request.json (status=3)
```

#### 3.8 取消配送
```bash
POST http://localhost:8083/api/drivers/deliveries/1/cancel
Authorization: Bearer {driver_token}
Content-Type: application/json
Header: userId: 5

使用文件: delivery_cancel_request.json
```

---

## 测试数据说明

### 数据库测试数据
- **Driver用户**：user_id=5, driver_id=1（李四）
- **车辆数据**：
  - 车辆1：小型货车（driver_id=1, license_plate=京A12345）
  - 车辆2：中型货车（driver_id=1, license_plate=京B67890）
- **配送数据**：
  - 当订单状态变为"已发货"时，系统应该在order_delivery表中创建一条记录（delivery_status=0 待接单）

### 注意事项
1. 所有需要Token的请求，都要在Header中添加：`Authorization: Bearer {token}`
2. 运输员相关接口需要 `userId` 和 `roleCode` 请求头
3. driver_id=1 对应数据库中的第一个运输员（李四）
4. 测试前请确保数据库已执行 `database/user.sql` 和 `database/driver.sql`
5. 登录Token需要从 `auth-service` 获取，参考 `../auth-service/README.md`
6. 接单时需要验证订单状态，只能接待接单的订单
7. 更新配送状态时需要验证权限，只能更新自己接的订单
8. 配送状态流转：待接单(0) → 已接单(1) → 运输中(2) → 已送达(3)

### 服务端口
- Gateway Service: `http://localhost:8083`（通过网关访问）
- Driver Service: `http://localhost:8086`（直接访问）

### 接口说明
- **运输员接口** (`/api/drivers`): 运输员信息管理
  - 需要登录（driver角色）
  - 可以查看、添加、修改自己的信息
  
- **车辆接口** (`/api/drivers/vehicles`): 车辆信息管理
  - 需要登录（driver角色）
  - 可以查看、添加、修改、删除自己的车辆
  
- **配送接口** (`/api/drivers/deliveries`): 订单配送管理
  - 需要登录（driver角色）
  - 可以查看待接单订单、接单、更新配送状态、取消配送

