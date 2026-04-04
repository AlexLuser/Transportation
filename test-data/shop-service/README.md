# Shop Service 测试数据

## 文件说明

### 商户信息相关
- `json/shop_add_request.json` - 添加商户信息请求
- `json/shop_update_request.json` - 更新商户信息请求
- `json/shop_response_example.json` - 商户信息响应示例

### 商品相关
- `json/product_add_request.json` - 添加商品请求
- `json/product_update_request.json` - 更新商品请求
- `json/product_response_example.json` - 单个商品响应示例
- `json/product_list_response_example.json` - 商品列表响应示例

### 商城相关
- `json/mall_products_response_example.json` - 商城商品列表响应示例（旧版，无分页）
- `json/mall_products_page_response_example.json` - 商城商品列表分页响应示例

### 仓库相关
- `json/warehouse_response_example.json` - 单个仓库响应示例
- `json/warehouse_list_response_example.json` - 仓库列表响应示例

### 库存相关
- `json/stock_update_request.json` - 更新库存请求
- `json/stock_response_example.json` - 库存响应示例
- `json/stock_list_by_product_example.json` - 按商品查询库存列表响应示例
- `json/stock_list_by_warehouse_example.json` - 按仓库查询库存列表响应示例

## 测试场景

### 场景1：权限控制测试

#### 1.1 Shop角色访问（应该成功 - 200）
```bash
GET http://localhost:8085/api/shops/1
Authorization: Bearer {shop_token}
```
Header: `userId: 3`, `roleCode: shop`

#### 1.2 Customer角色访问（应该失败 - 403）
```bash
GET http://localhost:8085/api/shops/1
Authorization: Bearer {customer_token}
```
Header: `userId: 2`, `roleCode: customer`
预期响应：
```json
{
  "code": 403,
  "message": "无权访问其他商户的信息",
  "data": null,
  "timestamp": ...
}
```

#### 1.3 Admin角色访问（应该成功 - 200）
```bash
GET http://localhost:8085/api/shops/1
Authorization: Bearer {admin_token}
```
Header: `userId: 1`, `roleCode: admin`

#### 1.4 无Token访问（应该失败 - 401）
```bash
GET http://localhost:8085/api/shops/1
```
预期响应：
```json
{
  "code": 401,
  "message": "Token不能为空",
  "data": null,
  "timestamp": ...
}
```

---

### 场景2：商户信息管理

#### 2.1 获取商户信息
```bash
GET http://localhost:8085/api/shops/1
Authorization: Bearer {shop_token}
```
Header: `userId: 3`, `roleCode: shop`
参考响应：`shop_response_example.json`

#### 2.2 添加商户信息
```bash
POST http://localhost:8085/api/shops
Authorization: Bearer {shop_token}
Content-Type: application/json
Header: userId: 3

使用文件: shop_add_request.json
```

#### 2.3 修改商户信息
```bash
PUT http://localhost:8085/api/shops
Authorization: Bearer {shop_token}
Content-Type: application/json
Header: userId: 3

使用文件: shop_update_request.json
```

#### 2.4 删除商户信息
```bash
DELETE http://localhost:8085/api/shops/1
Authorization: Bearer {shop_token}
Header: userId: 3, roleCode: shop
```

---

### 场景3：商品管理

#### 3.1 获取商品详情（无需登录）
```bash
GET http://localhost:8085/api/products/1
```
参考响应：`product_response_example.json`

#### 3.2 获取商品列表（分页，按商户ID）
```bash
GET http://localhost:8083/api/products?shopId=1&current=1&size=10
Authorization: Bearer {shop_token}
```
说明：获取指定商户的商品列表，第1页，每页10条

#### 3.3 获取当前商户的商品列表（自动识别，分页）
```bash
GET http://localhost:8083/api/products?current=1&size=10
Authorization: Bearer {shop_token}
Header: userId: 3
```
说明：自动识别当前登录商户，返回分页商品列表

#### 3.4 获取商品列表（带排序）
```bash
GET http://localhost:8083/api/products?shopId=1&current=1&size=10&sortField=price&sortOrder=asc
Authorization: Bearer {shop_token}
```
说明：按价格升序排序

#### 3.5 搜索商品（分页）
```bash
GET http://localhost:8083/api/products/search?keyword=坚果&categoryId=3&shopId=1&current=1&size=10
Authorization: Bearer {shop_token}
```
说明：搜索商品，支持分页

#### 3.6 搜索商品（分页+排序）
```bash
GET http://localhost:8083/api/products/search?keyword=坚果&categoryId=3&shopId=1&current=1&size=10&sortField=salesCount&sortOrder=desc
Authorization: Bearer {shop_token}
```
说明：搜索商品，按销量降序排序

#### 3.5 添加商品
```bash
POST http://localhost:8085/api/products
Authorization: Bearer {shop_token}
Content-Type: application/json
Header: userId: 3

使用文件: product_add_request.json
```

#### 3.6 修改商品
```bash
PUT http://localhost:8085/api/products
Authorization: Bearer {shop_token}
Content-Type: application/json
Header: userId: 3

使用文件: product_update_request.json
```

#### 3.7 删除商品
```bash
DELETE http://localhost:8085/api/products/1
Authorization: Bearer {shop_token}
Header: userId: 3
```

---

### 场景4：仓库管理

#### 4.1 获取仓库列表（无需登录）
```bash
GET http://localhost:8085/api/warehouses
```
参考响应：`warehouse_list_response_example.json`

#### 4.2 获取仓库详情
```bash
GET http://localhost:8085/api/warehouses/1
```
参考响应：`warehouse_response_example.json`

#### 4.3 修改仓库容量
```bash
PUT http://localhost:8085/api/warehouses/1/capacity?capacity=12000
Authorization: Bearer {shop_token}
Header: userId: 3
```

---

### 场景5：库存管理

#### 5.1 获取商品库存（所有仓库）
```bash
GET http://localhost:8085/api/stocks/product/1
```
参考响应：`stock_list_by_product_example.json`

#### 5.2 获取仓库库存（所有商品）
```bash
GET http://localhost:8085/api/stocks/warehouse/1
```
参考响应：`stock_list_by_warehouse_example.json`

#### 5.3 修改商品库存
```bash
PUT http://localhost:8085/api/stocks
Authorization: Bearer {shop_token}
Content-Type: application/json
Header: userId: 3

使用文件: stock_update_request.json
```
参考响应：`stock_response_example.json`

---

### 场景6：商城商品浏览（C端用户）

#### 6.1 获取商城商品列表（分页，默认按创建时间倒序）
```bash
GET http://localhost:8083/api/mall/products?current=1&size=10
Authorization: Bearer {customer_token}
```
Header: `userId: 2`, `roleCode: customer`
说明：返回第1页，每页10条记录，按创建时间倒序排列

#### 6.2 按价格升序排序
```bash
GET http://localhost:8083/api/mall/products?current=1&size=10&sortField=price&sortOrder=asc
Authorization: Bearer {customer_token}
```
说明：按价格从低到高排序

#### 6.3 按销量降序排序
```bash
GET http://localhost:8083/api/mall/products?current=1&size=10&sortField=salesCount&sortOrder=desc
Authorization: Bearer {customer_token}
```
说明：按销量从高到低排序

#### 6.4 按分类筛选商品
```bash
GET http://localhost:8083/api/mall/products?current=1&size=10&categoryId=3
Authorization: Bearer {customer_token}
```
说明：只返回指定分类的上架商品

#### 6.5 搜索商品
```bash
GET http://localhost:8083/api/mall/products?current=1&size=10&keyword=坚果
Authorization: Bearer {customer_token}
```
说明：根据关键词搜索商品名称

#### 6.6 组合筛选（分类+关键词+排序）
```bash
GET http://localhost:8083/api/mall/products?current=1&size=10&categoryId=3&keyword=坚果&sortField=price&sortOrder=asc
Authorization: Bearer {customer_token}
```
说明：同时按分类和关键词筛选，并按价格升序排序

#### 6.7 分页参数说明
- `current`: 当前页码（从1开始，默认1）
- `size`: 每页大小（默认10）
- `categoryId`: 分类ID（可选）
- `keyword`: 关键词（可选，搜索商品名称）
- `sortField`: 排序字段（可选：`price`-价格, `salesCount`-销量, `createTime`-创建时间，默认为`createTime`）
- `sortOrder`: 排序方向（可选：`asc`-升序, `desc`-降序，默认为`desc`）

---

## 测试数据说明

### 数据库测试数据
- **Shop用户**：user_id=3, shop_id=1（优质食品店）
- **商品数据**：
  - 商品1：优质坚果礼盒（shop_id=1, category_id=3）
  - 商品2：有机果汁（shop_id=1, category_id=4）
  - 商品3：商务休闲衬衫（shop_id=2, category_id=5）
  - 商品4：时尚连衣裙（shop_id=2, category_id=6）
- **仓库数据**（均位于上海市，便于本地上海地图/OSM 路径规划）：
  - 仓库1：上海闵行分拨仓
  - 仓库2：上海华东仓库
  - 仓库3：上海宝山分拨仓
- **库存数据**：
  - 商品1在仓库1和仓库2都有库存
  - 商品2在仓库1和仓库3都有库存
  - 商品3在仓库2有库存
  - 商品4在仓库2和仓库3都有库存

### 注意事项
1. 所有需要Token的请求，都要在Header中添加：`Authorization: Bearer {token}`
2. 商户相关接口需要 `userId` 和 `roleCode` 请求头
3. shop_id=1 对应数据库中的第一个商户（优质食品店）
4. 测试前请确保数据库已执行 `database/user.sql` 和 `database/shop.sql`
5. 登录Token需要从 `auth-service` 获取，参考 `../auth-service/README.md`
6. 商品图片字段使用JSON数组格式的字符串，例如：`"[\"url1\", \"url2\"]"`
7. 库存管理接口需要验证商品是否属于当前商户，只有商品所属商户才能修改库存

### 服务端口
- Gateway Service: `http://localhost:8083`（通过网关访问）
- Shop Service: `http://localhost:8085`（直接访问）

### 接口说明
- **商城接口** (`/api/mall/**`): 面向C端用户，用于浏览所有上架商品
  - 需要登录（customer、shop、admin角色都可以访问）
  - 只返回上架商品（status=1）
  - 支持分页、排序、按分类筛选和关键词搜索
  - 排序字段：`price`（价格）、`salesCount`（销量）、`createTime`（创建时间）
  - 排序方向：`asc`（升序）、`desc`（降序）
  
- **商品管理接口** (`/api/products/**`): 面向商户，用于管理自己的商品
  - 需要登录（shop角色）
  - 可以查看、添加、修改、删除自己的商品
  - 支持分页、排序、按分类筛选和关键词搜索


