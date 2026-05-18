# Shop-Service 商户服务 — 完全初学者指南

> shop-service 是整个系统中功能最丰富的业务服务，负责管理商户信息、商品目录、仓库和库存，还向顾客提供商城浏览功能。理解它需要掌握"一对多"和"多对多"数据库关系。

---

## 目录

1. [这个模块是做什么的？](#1-这个模块是做什么的)
2. [目录结构](#2-目录结构)
3. [数据库设计与关系](#3-数据库设计与关系)
4. [实体类详解](#4-实体类详解)
5. [商户管理](#5-商户管理)
6. [商品管理](#6-商品管理)
7. [仓库管理](#7-仓库管理)
8. [库存管理：防超卖的原子操作](#8-库存管理防超卖的原子操作)
9. [商城接口（Mall）](#9-商城接口mall)
10. [关键技术深度解析](#10-关键技术深度解析)

---

## 1. 这个模块是做什么的？

### 1.1 商户服务的职责

```
商户端操作（shop角色）：
  管理商铺信息 → ShopController
  管理商品     → ProductController（上下架、价格、图片）
  管理仓库     → WarehouseController（仓库地址、经纬度）
  管理库存     → StockController（哪个仓库有多少件商品）

顾客端操作（customer角色）：
  浏览商城     → MallController（查看在售商品、商户信息）
  下单时用     → 内部接口（供order-service调用）
```

### 1.2 接口总览

| 控制器 | 前缀 | 主要功能 | 角色 |
|--------|------|----------|------|
| ShopController | `/api/shops` | 商铺增删改查 | shop/admin |
| ProductController | `/api/products` | 商品管理 | shop |
| WarehouseController | `/api/warehouses` | 仓库管理 | shop |
| StockController | `/api/stocks` | 库存管理 | shop |
| MallController | `/api/mall` | 商城浏览 | customer/shop |

---

## 2. 目录结构

```
shop-service/
├── pom.xml
└── src/main/
    ├── java/com/fm/shop/
    │   ├── ShopServiceApplication.java     ← 启动类（端口8085）
    │   ├── config/
    │   │   ├── SwaggerConfig.java          ← Swagger文档
    │   │   └── MyBatisPlusConfig.java      ← 分页插件（单页最多500条）
    │   ├── controller/
    │   │   ├── ShopController.java         ← 商铺接口
    │   │   ├── ProductController.java      ← 商品接口
    │   │   ├── WarehouseController.java    ← 仓库接口
    │   │   ├── StockController.java        ← 库存接口
    │   │   └── MallController.java         ← 商城浏览接口
    │   ├── entity/
    │   │   ├── Shop.java                   ← shop_info表实体
    │   │   ├── Product.java                ← product_info表实体
    │   │   ├── Warehouse.java              ← warehouse表实体
    │   │   └── WarehouseProduct.java       ← warehouse_product表实体（库存）
    │   ├── mapper/
    │   │   ├── ShopMapper.java
    │   │   ├── ProductMapper.java
    │   │   ├── WarehouseMapper.java
    │   │   └── WarehouseProductMapper.java ← 库存Mapper（含自定义SQL）
    │   └── service/
    │       ├── ShopService.java
    │       ├── ProductService.java
    │       ├── WarehouseService.java
    │       ├── StockService.java
    │       └── impl/
    │           ├── ShopServiceImpl.java
    │           ├── ProductServiceImpl.java
    │           ├── WarehouseServiceImpl.java
    │           └── StockServiceImpl.java
    └── resources/
        └── application.yml
```

---

## 3. 数据库设计与关系

### 3.1 四张表的关系

```
user 表（认证，属于common）
  │ userId（一个user对应一个shop）
  ↓
shop_info 表（商铺信息）
  │ shopId（一个shop有多个商品）        │ shopId（一个shop有多个仓库）
  ↓                                     ↓
product_info 表（商品）           warehouse 表（仓库）
                    ╲                   ╱
                     ╲                 ╱
                      warehouse_product 表
                      （哪个仓库有多少件哪个商品）
                      productId + warehouseId → stock（库存数量）
```

**"多对多"关系解释**：

- 一个仓库可以存放多种商品（仓库A有苹果100个、香蕉50个）
- 一种商品可以存放在多个仓库（苹果在仓库A有100个、在仓库B有200个）

这种关系叫**多对多（Many-to-Many）**，需要一张**中间表**（`warehouse_product`）来记录每个组合的数量。

### 3.2 SQL 表结构

```sql
CREATE TABLE `shop_info` (
  `id`           BIGINT NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT NOT NULL UNIQUE,  -- 关联user表
  `shop_name`    VARCHAR(100),
  `shop_desc`    TEXT,                    -- 商铺描述（可以很长）
  `shop_logo`    VARCHAR(500),            -- 店铺Logo URL
  `contact_phone` VARCHAR(20),
  `contact_email` VARCHAR(100),
  `province` / `city` / `district` / `detail_address` VARCHAR,  -- 商铺地址
  `latitude` / `longitude` DOUBLE,       -- 商铺经纬度
  `status`       INT DEFAULT 0,          -- 0待审核 1正常 2已关闭
  `create_time` / `update_time` DATETIME,
  PRIMARY KEY (`id`)
);

CREATE TABLE `product_info` (
  `id`           BIGINT NOT NULL AUTO_INCREMENT,
  `shop_id`      BIGINT NOT NULL,         -- 属于哪个商铺
  `product_name` VARCHAR(200) NOT NULL,
  `description`  TEXT,
  `price`        DECIMAL(10,2) NOT NULL,  -- 保留2位小数的精确金额
  `original_price` DECIMAL(10,2),        -- 原价（显示划线价）
  `category`     VARCHAR(50),            -- 商品分类
  `images`       JSON,                   -- 商品图片列表（JSON数组格式）
  `status`       INT DEFAULT 0,          -- 0待审核 1上架 2下架
  `sales_count`  INT DEFAULT 0,          -- 销量
  `create_time` / `update_time` DATETIME,
  PRIMARY KEY (`id`)
);

CREATE TABLE `warehouse` (
  `id`           BIGINT NOT NULL AUTO_INCREMENT,
  `shop_id`      BIGINT NOT NULL,
  `warehouse_name` VARCHAR(100),
  `province` / `city` / `district` / `detail_address` VARCHAR,
  `latitude` / `longitude` DOUBLE,       -- 仓库经纬度（物流路线起点！）
  `create_time` / `update_time` DATETIME,
  PRIMARY KEY (`id`)
);

CREATE TABLE `warehouse_product` (
  `id`           BIGINT NOT NULL AUTO_INCREMENT,
  `warehouse_id` BIGINT NOT NULL,
  `product_id`   BIGINT NOT NULL,
  `stock`        INT DEFAULT 0,          -- 库存数量
  `create_time` / `update_time` DATETIME,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_warehouse_product` (`warehouse_id`, `product_id`) -- 不重复
);
```

**`DECIMAL(10, 2)` 解释**：

金额不能用 `FLOAT` 或 `DOUBLE` 存储！

```java
// 浮点数的精度问题
System.out.println(0.1 + 0.2); // 输出：0.30000000000000004（不是0.3！）
```

`DECIMAL(10, 2)` 是精确的十进制数字：
- `10`：总共最多10位数字
- `2`：小数点后2位

Java 中对应 `BigDecimal` 类型（同样精确）：
```java
BigDecimal price = new BigDecimal("19.99");  // 注意：用字符串构造，不用double
```

---

## 4. 实体类详解

### 4.1 Product.java

```java
@Data
@TableName("product_info")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private String productName;
    private String description;
    private BigDecimal price;          // 精确金额
    private BigDecimal originalPrice;  // 原价（可为null，不打折时不填）
    private String category;
    private String images;   // 存储JSON数组字符串，如：["url1","url2","url3"]
    // 注意：images 在数据库是 JSON 类型，但 Java 中用 String 存储，
    // 需要自己解析（Jackson的 objectMapper.readValue）
    private Integer status;       // 0待审核 1上架 2下架
    private Integer salesCount;   // 销量（下单时自动增加）
    private Date createTime;
    private Date updateTime;
}
```

**为什么 images 用 JSON 字符串而不是单独的图片表？**

如果用单独的 `product_image` 表，查询商品时需要 JOIN 两张表，增加了复杂度。商品图片是与商品强绑定的数据，用 JSON 字符串存储更简单：

```java
// images 字段的值示例：
"[\"https://oss.example.com/img1.jpg\", \"https://oss.example.com/img2.jpg\"]"

// 解析时：
List<String> imageList = objectMapper.readValue(images, new TypeReference<List<String>>() {});
// imageList = ["https://oss.example.com/img1.jpg", "https://oss.example.com/img2.jpg"]
```

### 4.2 WarehouseProduct.java — 库存中间表实体

```java
@Data
@TableName("warehouse_product")
public class WarehouseProduct {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long warehouseId;  // 哪个仓库
    private Long productId;    // 哪个商品
    private Integer stock;     // 库存数量
    private Date createTime;
    private Date updateTime;
}
```

---

## 5. 商户管理

### 5.1 ShopServiceImpl 核心逻辑

```java
// 获取当前用户的商铺（通过 userId 查）
public Shop getShopByUserId(Long userId) {
    LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Shop::getUserId, userId);
    return shopMapper.selectOne(wrapper);
}

// 保存或更新商铺（用于商户注册/修改资料）
public Shop saveOrUpdateShop(Shop shop) {
    if (shop.getId() == null) {
        shop.setStatus(0); // 新商铺默认"待审核"状态
        shopMapper.insert(shop);
    } else {
        shopMapper.updateById(shop);
    }
    return shop;
}
```

**商铺状态流程**：

```
新商铺 status=0（待审核）
         ↓ 管理员审核通过
       status=1（正常营业）
         ↓ 管理员关闭
       status=2（已关闭）
```

### 5.2 供 order-service 调用的内部接口

```java
// ShopController 中的内部接口
@GetMapping("/internal/user/{userId}")
public Result<Shop> getShopByUserId(@PathVariable Long userId) {
    // order-service 创建订单时，通过 userId 找到商铺信息
    return Result.success(shopService.getShopByUserId(userId));
}
```

---

## 6. 商品管理

### 6.1 多维度分页排序

ProductServiceImpl 实现了灵活的多维度排序：

```java
public PageResult<Product> getProductsByShopId(
        Long shopId, Long current, Long size,
        String sortField, String sortOrder, Integer status) {

    Page<Product> page = new Page<>(current, size);
    LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Product::getShopId, shopId);

    // 按状态筛选（null表示不筛选）
    if (status != null) {
        wrapper.eq(Product::getStatus, status);
    }

    // 动态排序
    if ("price".equals(sortField)) {
        if ("asc".equals(sortOrder)) wrapper.orderByAsc(Product::getPrice);
        else wrapper.orderByDesc(Product::getPrice);
    } else if ("salesCount".equals(sortField)) {
        if ("asc".equals(sortOrder)) wrapper.orderByAsc(Product::getSalesCount);
        else wrapper.orderByDesc(Product::getSalesCount);
    } else {
        wrapper.orderByDesc(Product::getCreateTime); // 默认按创建时间降序
    }

    IPage<Product> result = productMapper.selectPage(page, wrapper);
    return new PageResult<>(result.getCurrent(), result.getSize(),
                            result.getTotal(), result.getRecords());
}
```

**`IPage<T>` 解释**：

`IPage<T>` 是 MyBatis Plus 的分页结果接口，包含：
- `getCurrent()`：当前页码
- `getSize()`：每页大小
- `getTotal()`：总记录数
- `getRecords()`：当前页数据列表

使用 `Page<T>` 对象作为参数传给 `selectPage()`，MyBatis Plus 会自动在 SQL 后加上 `LIMIT ? OFFSET ?`：

```sql
-- 生成的SQL（第2页，每页10条）：
SELECT * FROM product_info WHERE shop_id = ? ORDER BY create_time DESC LIMIT 10 OFFSET 10
```

### 6.2 商品状态管理

```java
// 更新商品状态（上架/下架）
public boolean updateProductStatus(Long productId, Integer status) {
    LambdaUpdateWrapper<Product> wrapper = new LambdaUpdateWrapper<>();
    wrapper.eq(Product::getId, productId)
           .set(Product::getStatus, status);
    return productMapper.update(null, wrapper) > 0;
}
```

商品状态含义：
- `0`：待审核（新商品刚上传，管理员未审核）
- `1`：上架（顾客可以看到并购买）
- `2`：下架（暂时不卖，顾客看不到）

---

## 7. 仓库管理

### 7.1 仓库的关键作用

仓库有两个核心字段：
1. **经纬度（latitude/longitude）**：作为物流路线规划的**起点**
2. **关联商品库存**：通过 `warehouse_product` 表记录每个仓库有多少件商品

### 7.2 WarehouseServiceImpl

```java
// 获取商铺下的所有仓库
public List<Warehouse> getWarehousesByShopId(Long shopId) {
    LambdaQueryWrapper<Warehouse> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Warehouse::getShopId, shopId)
           .orderByDesc(Warehouse::getCreateTime);
    return warehouseMapper.selectList(wrapper);
}

// 保存或更新仓库（含自动地理编码）
public Warehouse saveOrUpdateWarehouse(Warehouse warehouse) {
    // 更新场景下先查出旧记录，用于地理编码失败时保留旧坐标
    Warehouse existing = warehouse.getId() != null ? getWarehouseById(warehouse.getId()) : null;

    // 调用高德地理编码，自动填充 latitude/longitude
    applyGeocode(warehouse, existing);

    if (warehouse.getId() == null) {
        warehouseMapper.insert(warehouse);    // 新增仓库
    } else {
        warehouseMapper.updateById(warehouse); // 更新仓库
    }
    return warehouse;
}
```

### 7.3 applyGeocode — 自动填充经纬度

```java
private void applyGeocode(Warehouse incoming, Warehouse existing) {
    // 合并地址字段：优先用新传入的，否则用数据库中的旧值
    String p      = pick(incoming.getProvince(),      existing != null ? existing.getProvince()      : null);
    String c      = pick(incoming.getCity(),          existing != null ? existing.getCity()          : null);
    String d      = pick(incoming.getDistrict(),      existing != null ? existing.getDistrict()      : null);
    String detail = pick(incoming.getDetailAddress(), existing != null ? existing.getDetailAddress() : null);

    Optional<GeoPoint> geo = amapGeocodingService.geocode(p, c, d, detail);
    if (geo.isPresent()) {
        incoming.setLatitude(geo.get().latitude());
        incoming.setLongitude(geo.get().longitude());
    } else if (existing != null) {
        // 高德失败，更新场景下保留旧坐标
        if (incoming.getLatitude()  == null) incoming.setLatitude(existing.getLatitude());
        if (incoming.getLongitude() == null) incoming.setLongitude(existing.getLongitude());
    }
}

private static String pick(String incoming, String existing) {
    return incoming != null ? incoming : existing;
}
```

**逻辑与 customer-service 的收货地址完全对称**：新增仓库时若高德失败，坐标留 `null`；更新仓库时若失败，保留原坐标。两者共用 common 模块中的同一个 `AmapGeocodingService` Bean。

### 7.4 供 order-service 调用的内部接口

```java
// 供 order-service 在发货时调用，获取仓库地址和经纬度
@GetMapping("/internal/{warehouseId}")
public Result<Warehouse> getWarehouseById(@PathVariable Long warehouseId) {
    Warehouse warehouse = warehouseService.getWarehouseById(warehouseId);
    if (warehouse == null) return Result.error("仓库不存在");
    return Result.success(warehouse);
}
```

---

## 8. 库存管理：防超卖的原子操作

库存管理是 shop-service 中最关键、最需要仔细理解的部分。

### 8.1 什么是超卖？

```
仓库A有苹果5个

用户1查询：还剩5个 → 决定下单5个
用户2查询：还剩5个 → 决定下单5个（同时！）

用户1下单成功，库存变成0
用户2也下单成功，库存变成-5  ← 超卖！实际只有5个却卖出去10个
```

### 8.2 `WarehouseProductMapper` 的原子扣减

防止超卖的关键在于**原子操作**——把"检查库存"和"扣减库存"合并成一个不可分割的 SQL 操作：

```java
@Mapper
public interface WarehouseProductMapper extends BaseMapper<WarehouseProduct> {

    // 原子扣减库存（自定义SQL，不能用BaseMapper的通用方法）
    @Update("UPDATE warehouse_product " +
            "SET stock = stock - #{quantity} " +
            "WHERE warehouse_id = #{warehouseId} " +
            "AND product_id = #{productId} " +
            "AND stock >= #{quantity}")  // 关键：只在库存足够时才扣减
    int deductStock(@Param("warehouseId") Long warehouseId,
                    @Param("productId") Long productId,
                    @Param("quantity") Integer quantity);
}
```

**这条 SQL 为什么能防超卖？**

```sql
UPDATE warehouse_product
SET stock = stock - 5
WHERE warehouse_id = 1 AND product_id = 1 AND stock >= 5
```

- **数据库的原子性**：一条 UPDATE 语句在数据库层面是原子的，不会被其他操作打断
- **`stock >= quantity` 条件**：如果当前库存不足，WHERE 条件不满足，UPDATE 影响0行（不扣减）
- **返回影响行数**：返回 `0` 表示库存不足，返回 `1` 表示扣减成功

**流程对比：**

```
错误做法（会超卖）：
  1. SELECT stock FROM warehouse_product WHERE ... → stock=5
  2. 检查：5 >= 5，可以下单
  3. UPDATE ... SET stock=0             ← 用户1成功
  3. UPDATE ... SET stock=0             ← 用户2也成功（因为第1步也查到5）

正确做法（原子扣减）：
  1. UPDATE ... SET stock=stock-5 WHERE ... AND stock>=5 → 影响1行（用户1成功）
  2. UPDATE ... SET stock=stock-5 WHERE ... AND stock>=5 → 影响0行（用户2失败！）
```

### 8.3 StockServiceImpl 完整调用链

```java
@Service
public class StockServiceImpl implements StockService {

    @Autowired
    private WarehouseProductMapper warehouseProductMapper;

    // 查询商品在某仓库的库存
    public WarehouseProduct getStock(Long warehouseId, Long productId) {
        LambdaQueryWrapper<WarehouseProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WarehouseProduct::getWarehouseId, warehouseId)
               .eq(WarehouseProduct::getProductId, productId);
        return warehouseProductMapper.selectOne(wrapper);
    }

    // 查询商品在所有仓库的库存列表（order-service下单前查询用）
    public List<WarehouseProduct> getStockByProduct(Long productId) {
        LambdaQueryWrapper<WarehouseProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WarehouseProduct::getProductId, productId)
               .gt(WarehouseProduct::getStock, 0);  // 只返回有库存的仓库
        return warehouseProductMapper.selectList(wrapper);
    }

    // 原子扣减库存（order-service下单时调用）
    public boolean deductStock(Long warehouseId, Long productId, Integer quantity) {
        int affected = warehouseProductMapper.deductStock(warehouseId, productId, quantity);
        return affected > 0;  // 影响行数>0表示扣减成功
    }

    // 设置/更新库存（商户手动调整）
    public WarehouseProduct setStock(Long warehouseId, Long productId, Integer stock) {
        WarehouseProduct existing = getStock(warehouseId, productId);
        if (existing == null) {
            // 不存在则创建库存记录
            WarehouseProduct wp = new WarehouseProduct();
            wp.setWarehouseId(warehouseId);
            wp.setProductId(productId);
            wp.setStock(stock);
            warehouseProductMapper.insert(wp);
            return wp;
        } else {
            // 已存在则更新库存数量
            existing.setStock(stock);
            warehouseProductMapper.updateById(existing);
            return existing;
        }
    }
}
```

### 8.4 供 order-service 调用的内部库存接口

```java
// StockController 中的内部接口

// order-service 下单前查询商品库存分布
@GetMapping("/internal/product/{productId}")
public Result<List<WarehouseProduct>> getStockByProduct(@PathVariable Long productId) {
    return Result.success(stockService.getStockByProduct(productId));
}

// order-service 下单时原子扣减库存
@PostMapping("/internal/deduct")
public Result<Boolean> deductStock(
        @RequestParam Long warehouseId,
        @RequestParam Long productId,
        @RequestParam Integer quantity) {
    return Result.success(stockService.deductStock(warehouseId, productId, quantity));
}
```

---

## 9. 商城接口（Mall）

MallController 为顾客提供商城浏览功能，不同于商户管理接口，它：
1. **只返回上架商品**（status=1）
2. **包含商户信息**（商品旁边显示店铺名）
3. **支持关键词搜索和分类筛选**

### 9.1 MallController 主要接口

```java
@Tag(name = "商城浏览", description = "面向顾客的商城接口")
@RestController
@RequestMapping("/api/mall")
public class MallController {

    // 商城商品列表（含搜索、筛选、分页、排序）
    @GetMapping("/products")
    public Result<PageResult<Map<String, Object>>> getMallProducts(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size,
            @RequestParam(required = false) String keyword,   // 搜索关键词
            @RequestParam(required = false) String category,  // 商品分类
            @RequestParam(defaultValue = "createTime") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder,
            @RequestParam(required = false) Long shopId) {    // 指定商铺

        // 只查上架商品（status=1），组合商品和商铺信息返回
        return Result.success(mallService.getMallProducts(...));
    }

    // 商城商铺列表
    @GetMapping("/shops")
    public Result<PageResult<Shop>> getMallShops(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size) {
        // 只返回正常营业的商铺（status=1）
        return Result.success(mallService.getMallShops(current, size));
    }
}
```

**`@RequestParam(defaultValue = "1")` 解释**：

如果请求中没有传 `current` 参数，默认值为 `"1"`（字符串，Spring自动转换为Long）。

```
GET /api/mall/products → current=1, size=20（使用默认值）
GET /api/mall/products?current=3&size=10 → current=3, size=10
```

### 9.2 商城商品数据格式

商城展示时，每个商品需要同时包含商品信息和商铺信息（方便顾客直接看到卖家名字）：

```json
{
  "productId": 1,
  "productName": "新鲜苹果",
  "price": 5.99,
  "images": ["https://...img1.jpg"],
  "salesCount": 100,
  "shopId": 1,
  "shopName": "张三果园",    ← 商铺信息融合进来了
  "shopLogo": "https://...logo.jpg"
}
```

这是通过在 Service 中联合查询两张表，然后手动组装成 `Map<String, Object>` 实现的：

```java
// 伪代码（实际实现略有不同）
List<Product> products = productMapper.selectList(productWrapper);
for (Product product : products) {
    Map<String, Object> item = new HashMap<>();
    item.put("productId", product.getId());
    item.put("productName", product.getProductName());
    item.put("price", product.getPrice());

    // 查询对应商铺信息
    Shop shop = shopMapper.selectById(product.getShopId());
    item.put("shopName", shop.getShopName());
    item.put("shopLogo", shop.getShopLogo());

    result.add(item);
}
```

---

## 10. 关键技术深度解析

### 10.5 高德地理编码在仓库管理中的接入

仓库管理在 `saveOrUpdateWarehouse()` 内调用 `AmapGeocodingService`（来自 common 模块），配置项写在 `shop-service` 的 `application.yml` 中：

```yaml
amap:
  enabled: true
  key: ${AMAP_KEY:}        # 从环境变量读取，避免 Key 提交进代码仓库
  connect-timeout-ms: 5000
  read-timeout-ms: 8000
```

**为什么由 common 模块提供而不是 shop-service 自己写？**

customer-service 和 shop-service 的地理编码逻辑完全相同（拼地址 → HTTP 请求高德 → 解析 `location` 字段 → 写入实体）。把 `AmapGeocodingService` 放在 common 模块，两个服务都直接注入同一个 Bean，避免代码重复。

如果今后还有其他服务（如 driver-service）需要地理编码，同样只需引入 common 并配置 Key 即可，无需再实现 HTTP 调用逻辑。

### 10.1 MyBatisPlus 分页拦截器

```java
@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor paginationInterceptor =
            new PaginationInnerInterceptor(DbType.MYSQL);

        paginationInterceptor.setMaxLimit(500L);  // 单次最多返回500条
        paginationInterceptor.setOverflow(false); // 页码超出总页数时不返回第一页，而是返回空

        interceptor.addInnerInterceptor(paginationInterceptor);
        return interceptor;
    }
}
```

**拦截器（Interceptor）是什么？**

拦截器是 AOP 的一种实现，在 SQL 执行前后插入额外逻辑：

```
代码调用 mapper.selectPage(page, wrapper)
          ↓
MyBatis 准备 SQL：SELECT * FROM product_info WHERE ...
          ↓
【分页拦截器拦截】：
  检查 page 参数 → 自动追加 LIMIT 20 OFFSET 0
          ↓
最终执行：SELECT * FROM product_info WHERE ... LIMIT 20 OFFSET 0
```

**`setMaxLimit(500L)` 的安全意义**：

防止恶意请求传入超大 `size` 值（如 `size=100000`），导致一次查询返回海量数据，压垮数据库和服务器内存。

### 10.2 `@Param` 注解

```java
@Update("UPDATE warehouse_product " +
        "SET stock = stock - #{quantity} " +
        "WHERE warehouse_id = #{warehouseId} " +
        "AND product_id = #{productId} " +
        "AND stock >= #{quantity}")
int deductStock(@Param("warehouseId") Long warehouseId,
                @Param("productId") Long productId,
                @Param("quantity") Integer quantity);
```

`@Param("warehouseId")` 给参数起名字，MyBatis 在 SQL 中用 `#{warehouseId}` 引用。

如果不加 `@Param`，方法有多个参数时 MyBatis 不知道 `#{warehouseId}` 对应哪个参数（单个参数时可以省略）。

**`#{parameter}` vs `${parameter}`**：

| 写法 | 方式 | 安全性 |
|------|------|--------|
| `#{value}` | 预编译（Prepared Statement） | 安全，防SQL注入 |
| `${value}` | 字符串拼接 | 危险，可能SQL注入 |

永远使用 `#{}`，避免使用 `${}`（除非动态SQL需要，如动态表名/列名）。

**SQL 注入示例**：
```sql
-- 使用 ${username}（危险）
SELECT * FROM user WHERE username = '张三' OR '1'='1'
-- 恶意用户传入：  张三' OR '1'='1
-- 查询了所有用户！
```

### 10.3 `gt()` 方法解析

```java
wrapper.gt(WarehouseProduct::getStock, 0);  // WHERE stock > 0
```

MyBatis Plus 常用比较条件方法：

| 方法 | SQL | 含义 |
|------|-----|------|
| `eq(field, val)` | `field = val` | 等于 |
| `ne(field, val)` | `field != val` | 不等于 |
| `gt(field, val)` | `field > val` | 大于 |
| `ge(field, val)` | `field >= val` | 大于等于 |
| `lt(field, val)` | `field < val` | 小于 |
| `le(field, val)` | `field <= val` | 小于等于 |
| `like(field, val)` | `field LIKE %val%` | 模糊查询 |
| `in(field, list)` | `field IN (...)` | 包含 |
| `isNull(field)` | `field IS NULL` | 为空 |

### 10.4 UNIQUE KEY 的数据库保证

```sql
UNIQUE KEY `uk_warehouse_product` (`warehouse_id`, `product_id`)
```

**联合唯一索引**：`(warehouse_id, product_id)` 的组合必须唯一。

意思是：同一个仓库（warehouse_id=1）里，同一个商品（product_id=5）只能有一条库存记录，不会出现：
```
仓库1 + 商品5 → stock=100
仓库1 + 商品5 → stock=200  ← 重复！
```

这个约束由数据库层面保证，即使代码层面没有检查，数据库也会拒绝插入重复记录（抛出 `DuplicateKeyException`）。

---

## 附录：知识点速查表

| 知识点 | 说明 |
|--------|------|
| 多对多关系 | product + warehouse 通过 warehouse_product 中间表关联 |
| `DECIMAL(10,2)` | 精确金额类型，对应Java的`BigDecimal` |
| `BigDecimal` | Java精确十进制数，避免浮点数精度问题 |
| 原子扣减库存 | SQL中的`AND stock>=quantity`条件防超卖 |
| `@Update`注解 | MyBatis自定义UPDATE SQL |
| `@Param`注解 | 多参数时为MyBatis命名参数 |
| `#{}` vs `${}` | 预编译（安全）vs字符串拼接（危险） |
| 分页拦截器 | MyBatis Plus自动给SQL加LIMIT/OFFSET |
| `IPage<T>` | MyBatis Plus分页结果接口 |
| `Page<T>` | 分页参数对象（当前页、每页大小） |
| `gt()` | 大于条件，`wrapper.gt(field, val)` |
| 商品状态 | 0待审核/1上架/2下架，决定顾客是否可见 |
| JSON字段 | images列存JSON数组字符串，需要手动解析 |
| 联合唯一索引 | 防止同一仓库同一商品有多条库存记录 |
| 仓库地理编码自动填充 | saveOrUpdateWarehouse 时调用 AmapGeocodingService，自动补全经纬度 |
| 地理编码失败策略 | 失败时保留旧坐标（更新场景）或留 null（新增场景），仓库保存不受影响 |
| common 模块复用 | AmapGeocodingService 定义在 common，customer 与 shop 共享同一实现 |
