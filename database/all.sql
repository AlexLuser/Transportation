-- 顾客信息表
-- 用于存储顾客用户的详细信息
-- 关联user表，通过user_id关联

DROP TABLE IF EXISTS `customer_address`;
DROP TABLE IF EXISTS `customer_info`;

-- 创建顾客信息表
CREATE TABLE `customer_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '顾客ID',
  `user_id` BIGINT NOT NULL COMMENT '关联user表的id',
  `real_name` VARCHAR(50) COMMENT '真实姓名',
  `phone` VARCHAR(20) COMMENT '手机号',
  `email` VARCHAR(100) COMMENT '邮箱',
  `gender` TINYINT COMMENT '性别：0=未知，1=男，2=女',
  `birthday` DATE COMMENT '生日',
  `avatar` VARCHAR(255) COMMENT '头像URL',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0=禁用，1=启用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  INDEX `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='顾客信息表';

-- 创建收货地址表
CREATE TABLE `customer_address` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '地址ID',
  `customer_id` BIGINT NOT NULL COMMENT '关联customer_info表的id',
  `receiver_name` VARCHAR(50) NOT NULL COMMENT '收货人姓名',
  `receiver_phone` VARCHAR(20) NOT NULL COMMENT '收货人电话',
  `province` VARCHAR(50) COMMENT '省份',
  `city` VARCHAR(50) COMMENT '城市',
  `district` VARCHAR(50) COMMENT '区/县',
  `detail_address` VARCHAR(255) NOT NULL COMMENT '详细地址',
  `postal_code` VARCHAR(10) COMMENT '邮编',
  `is_default` TINYINT DEFAULT 0 COMMENT '是否默认地址：0=否，1=是',
  `latitude` DOUBLE DEFAULT NULL COMMENT '收货地址纬度（用于物流路线规划终点）',
  `longitude` DOUBLE DEFAULT NULL COMMENT '收货地址经度（用于物流路线规划终点）',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_customer_id` (`customer_id`),
  INDEX `idx_is_default` (`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址表';

-- ============================================================
-- 测试数据
-- user_id=2 对应 user.sql 中的 customer 用户（第2条记录）
-- ============================================================

-- 顾客信息
INSERT INTO `customer_info` (`user_id`, `real_name`, `phone`, `email`, `gender`, `birthday`, `status`) VALUES
(2, '张三', '13800138000', 'zhangsan@example.com', 1, '1990-05-15', 1);

-- 收货地址（均为上海，与 OSM 数据范围一致）
-- customer_id=1 对应上面插入的顾客记录
INSERT INTO `customer_address`
  (`customer_id`, `receiver_name`, `receiver_phone`, `province`, `city`, `district`, `detail_address`, `postal_code`, `is_default`, `latitude`, `longitude`)
VALUES
-- 默认地址：上海浦东（物流测试主用地址，order_id=1 的收货地址，坐标与 logistics.sql 一致）
(1, '张三', '13800138000', '上海市', '上海市', '浦东新区', '浦东新区陆家嘴环路1000号', '200120', 1, 31.2356, 121.5050),
-- 备用地址：上海静安
(1, '张三', '13800138001', '上海市', '上海市', '静安区', '静安区南京西路688号', '200041', 0, 31.2289, 121.4490);

-- 运输员服务数据库表
-- 包含运输员信息、车辆信息、订单配送等

-- 删除表（按依赖顺序）
DROP TABLE IF EXISTS `order_delivery`;
DROP TABLE IF EXISTS `vehicle_info`;
DROP TABLE IF EXISTS `driver_info`;

-- ============================================
-- 1. 运输员信息表
-- ============================================
CREATE TABLE `driver_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '运输员ID',
  `user_id` BIGINT NOT NULL COMMENT '关联user表的id（运输员用户）',
  `real_name` VARCHAR(50) NOT NULL COMMENT '真实姓名',
  `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
  `email` VARCHAR(100) COMMENT '邮箱',
  `id_card` VARCHAR(18) COMMENT '身份证号',
  `gender` TINYINT COMMENT '性别：0=未知，1=男，2=女',
  `birthday` DATE COMMENT '生日',
  `avatar` VARCHAR(255) COMMENT '头像URL',
  `license_number` VARCHAR(50) COMMENT '驾驶证号',
  `license_type` VARCHAR(10) COMMENT '驾驶证类型（C1/C2/B1/B2等）',
  `license_expire_date` DATE COMMENT '驾驶证到期日期',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0=禁用，1=启用，2=待审核',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  INDEX `idx_phone` (`phone`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运输员信息表';

-- ============================================
-- 2. 车辆信息表
-- ============================================
CREATE TABLE `vehicle_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '车辆ID',
  `driver_id` BIGINT NOT NULL COMMENT '关联driver_info表的id',
  `vehicle_type` VARCHAR(20) NOT NULL COMMENT '车辆类型：小型货车/中型货车/大型货车/厢式货车等',
  `vehicle_brand` VARCHAR(50) COMMENT '车辆品牌',
  `vehicle_model` VARCHAR(50) COMMENT '车辆型号',
  `license_plate` VARCHAR(20) NOT NULL COMMENT '车牌号',
  `load_capacity` DECIMAL(10, 2) COMMENT '载重（吨）',
  `volume_capacity` DECIMAL(10, 2) COMMENT '载货体积（立方米）',
  `vehicle_status` TINYINT DEFAULT 1 COMMENT '车辆状态：0=停用，1=可用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_driver_id` (`driver_id`),
  INDEX `idx_license_plate` (`license_plate`),
  INDEX `idx_vehicle_status` (`vehicle_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车辆信息表';

-- ============================================
-- 3. 订单配送表
-- ============================================
CREATE TABLE `order_delivery` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '配送ID',
  `order_id` BIGINT NOT NULL COMMENT '关联order_info表的id',
  `driver_id` BIGINT COMMENT '关联driver_info表的id（接单后才有）',
  `vehicle_id` BIGINT COMMENT '关联vehicle_info表的id（使用的车辆）',
  `delivery_status` TINYINT DEFAULT 0 COMMENT '配送状态：0=待接单，1=已接单，2=运输中，3=已送达，4=已取消',
  `accept_time` DATETIME COMMENT '接单时间',
  `pickup_time` DATETIME COMMENT '取货时间',
  `delivery_time` DATETIME COMMENT '送达时间',
  `cancel_time` DATETIME COMMENT '取消时间',
  `cancel_reason` VARCHAR(255) COMMENT '取消原因',
  `delivery_address` VARCHAR(500) COMMENT '配送地址（快照）',
  `receiver_name` VARCHAR(50) COMMENT '收货人姓名（快照）',
  `receiver_phone` VARCHAR(20) COMMENT '收货人电话（快照）',
  `remark` VARCHAR(500) COMMENT '配送备注',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  INDEX `idx_driver_id` (`driver_id`),
  INDEX `idx_delivery_status` (`delivery_status`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单配送表';

-- ============================================================
-- 测试数据
-- user_id=5 → user.sql 中的 driver 用户（第5条记录）
-- ============================================================

-- 运输员信息（driver_id=1，即物流路线中绑定的运输员）
-- 注意：电话使用 13700137000，与 shop_info 的电话区分，避免混淆
INSERT INTO `driver_info`
  (`user_id`, `real_name`, `phone`, `email`, `id_card`, `gender`, `birthday`,
   `license_number`, `license_type`, `license_expire_date`, `status`)
VALUES
(5, '李四', '13700137000', 'lisi@example.com', '310101199001011234', 1, '1990-01-01',
 'SH0001234567890', 'C1', '2030-12-31', 1);

-- 车辆信息（归属于 driver_id=1，驻扎上海，使用沪牌）
-- vehicle_id=1 为物流测试使用的车辆
INSERT INTO `vehicle_info`
  (`driver_id`, `vehicle_type`, `vehicle_brand`, `vehicle_model`, `license_plate`,
   `load_capacity`, `volume_capacity`, `vehicle_status`)
VALUES
(1, '小型货车', '东风', 'DF-100', '沪A12345', 2.5, 10.0, 1),  -- ★ 物流测试使用
(1, '中型货车', '解放', 'JF-200', '沪B67890', 5.0, 20.0, 0);  -- 当前停用

-- 订单配送记录（order_id=1 的配送，与 logistics.sql 中 delivery_id=1 对应）
-- ★ delivery_id=1，delivery_status=2（运输中），与 logistics_route 中的 delivery_id 保持一致
INSERT INTO `order_delivery`
  (`order_id`, `driver_id`, `vehicle_id`, `delivery_status`,
   `accept_time`, `pickup_time`,
   `delivery_address`, `receiver_name`, `receiver_phone`, `remark`)
VALUES
(
  1, 1, 1, 2,                                           -- 运输中
  DATE_SUB(NOW(), INTERVAL 90 MINUTE),                  -- 90分钟前接单
  DATE_SUB(NOW(), INTERVAL 60 MINUTE),                  -- 60分钟前从仓库取货
  '上海市浦东新区陆家嘴环路1000号', '张三', '13800138000',
  '客户要求放门口'
);

-- ============================================================
-- 物流调度服务数据库表
-- 模块：路径规划
-- 包含：物流路线表、里程碑节点表、实时轨迹表
-- ============================================================

-- 按依赖顺序删除旧表
DROP TABLE IF EXISTS `logistics_node`;
DROP TABLE IF EXISTS `logistics_track`;
DROP TABLE IF EXISTS `logistics_route`;


-- ============================================================
-- 1. 物流路线表
--    一个订单对应一条物流路线，记录从仓库到收货地址的全程信息
--    - 地址和坐标均保存快照（下单时冻结）
--    - planned_route 存储 GeoJSON LineString（GraphHopper 规划结果）
--    - route_status 驱动整个配送状态机
-- ============================================================
CREATE TABLE `logistics_route` (
  `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '路线ID（主键）',
  `route_no`              VARCHAR(30)  NOT NULL UNIQUE COMMENT '路线编号（LR+yyyyMMddHHmmss+4位随机）',
  `order_id`              BIGINT       NOT NULL UNIQUE COMMENT '关联订单ID（一个订单只能有一条路线）',
  `delivery_id`           BIGINT       DEFAULT NULL COMMENT '关联配送记录ID（接单后绑定）',
  `driver_id`             BIGINT       DEFAULT NULL COMMENT '运输员ID（接单后绑定）',
  `warehouse_id`          BIGINT       DEFAULT NULL COMMENT '出发仓库ID',

  -- 出发地信息（仓库）
  `start_address`         VARCHAR(300) NOT NULL COMMENT '出发地址（仓库地址快照）',
  `start_lat`             DOUBLE       DEFAULT NULL COMMENT '出发地纬度',
  `start_lng`             DOUBLE       DEFAULT NULL COMMENT '出发地经度',

  -- 目的地信息（收货地址）
  `end_address`           VARCHAR(300) NOT NULL COMMENT '目的地址（收货地址快照）',
  `end_lat`               DOUBLE       DEFAULT NULL COMMENT '目的地纬度',
  `end_lng`               DOUBLE       DEFAULT NULL COMMENT '目的地经度',

  -- 实时位置（运输中持续更新）
  `current_lat`           DOUBLE       DEFAULT NULL COMMENT '当前位置纬度',
  `current_lng`           DOUBLE       DEFAULT NULL COMMENT '当前位置经度',
  `current_address`       VARCHAR(300) DEFAULT NULL COMMENT '当前位置描述（逆地理编码结果）',
  `last_track_time`       DATETIME     DEFAULT NULL COMMENT '最后一次位置更新时间',

  -- 状态机：0=待出发，1=运输中，2=已送达，3=异常
  `route_status`          TINYINT      NOT NULL DEFAULT 0 COMMENT '路线状态：0=待出发，1=运输中，2=已送达，3=异常',

  -- 时间预估
  `estimated_arrival_time` DATETIME   DEFAULT NULL COMMENT '预计到达时间',
  `actual_arrival_time`   DATETIME    DEFAULT NULL COMMENT '实际到达时间',

  -- 路线数据（GeoJSON）
  `planned_route`         LONGTEXT     DEFAULT NULL COMMENT '计划路线（GeoJSON LineString，由 GraphHopper 生成）',

  -- 收货人信息快照
  `receiver_name`         VARCHAR(50)  DEFAULT NULL COMMENT '收货人姓名',
  `receiver_phone`        VARCHAR(20)  DEFAULT NULL COMMENT '收货人电话',
  `remark`                VARCHAR(500) DEFAULT NULL COMMENT '备注',

  `create_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_route_no`   (`route_no`),
  UNIQUE KEY `uk_order_id`   (`order_id`),
  INDEX `idx_driver_id`      (`driver_id`),
  INDEX `idx_warehouse_id`   (`warehouse_id`),
  INDEX `idx_route_status`   (`route_status`),
  INDEX `idx_create_time`    (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流路线表';


-- ============================================================
-- 2. 里程碑节点表
--    路线上的关键节点：出发点（type=0）、途经点（type=1）、目的地（type=2）
-- ============================================================
CREATE TABLE `logistics_node` (
  `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '节点ID',
  `route_id`              BIGINT       NOT NULL COMMENT '所属路线ID',
  `node_type`             TINYINT      NOT NULL COMMENT '节点类型：0=出发点，1=途经点，2=目的地',
  `node_name`             VARCHAR(100) NOT NULL COMMENT '节点名称',
  `node_address`          VARCHAR(300) DEFAULT NULL COMMENT '节点地址',
  `latitude`              DOUBLE       DEFAULT NULL COMMENT '节点纬度',
  `longitude`             DOUBLE       DEFAULT NULL COMMENT '节点经度',
  `sequence_no`           INT          NOT NULL DEFAULT 0 COMMENT '顺序号（0=出发点，99=目的地）',
  `planned_arrive_time`   DATETIME     DEFAULT NULL COMMENT '计划到达时间',
  `actual_arrive_time`    DATETIME     DEFAULT NULL COMMENT '实际到达时间',
  `node_status`           TINYINT      NOT NULL DEFAULT 0 COMMENT '节点状态：0=未到达，1=已到达，2=已跳过',
  `remark`                VARCHAR(300) DEFAULT NULL COMMENT '节点备注',
  `create_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_route_id`    (`route_id`),
  INDEX `idx_node_type`   (`node_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='里程碑节点表';


-- ============================================================
-- 3. 实时轨迹表
--    运输员 App 每15~30秒上报一次GPS，高频写入，ROW_FORMAT=COMPRESSED 压缩存储
-- ============================================================
CREATE TABLE `logistics_track` (
  `id`          BIGINT    NOT NULL AUTO_INCREMENT COMMENT '轨迹点ID',
  `route_id`    BIGINT    NOT NULL COMMENT '所属路线ID',
  `driver_id`   BIGINT    NOT NULL COMMENT '运输员ID',
  `latitude`    DOUBLE    NOT NULL COMMENT '纬度',
  `longitude`   DOUBLE    NOT NULL COMMENT '经度',
  `altitude`    DOUBLE    DEFAULT NULL COMMENT '海拔（米，可选）',
  `speed`       DOUBLE    DEFAULT NULL COMMENT '速度（km/h）',
  `heading`     DOUBLE    DEFAULT NULL COMMENT '方向角（0=正北，顺时针）',
  `accuracy`    DOUBLE    DEFAULT NULL COMMENT 'GPS精度（米，值越小越精确）',
  `address`     VARCHAR(300) DEFAULT NULL COMMENT '位置描述（逆地理编码结果，由客户端传入）',
  `track_time`  DATETIME  NOT NULL COMMENT 'GPS上报时间（客户端本地时间）',
  `create_time` DATETIME  DEFAULT CURRENT_TIMESTAMP COMMENT '服务端接收时间',
  PRIMARY KEY (`id`),
  INDEX `idx_route_id_time` (`route_id`, `track_time`),
  INDEX `idx_driver_id`     (`driver_id`),
  INDEX `idx_track_time`    (`track_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实时轨迹点表'
  ROW_FORMAT=COMPRESSED;


-- ============================================================
-- 测试数据说明
-- ----------------------------------------------------------------
-- 完整测试场景：
--   顾客张三（customer_id=1）在优质食品店（shop_id=1）下单
--   订单 ORD20260313000001（order_id=1）已支付并发货
--   从上海华东仓库（warehouse_id=2, 浦东新区物流园区B区2号）发出
--   配送至上海浦东新区陆家嘴环路1000号（address_id=1）
--   运输员李四（driver_id=1）驾驶 沪A12345（vehicle_id=1）正在运输中
--
-- 坐标范围（均在上海浦东区域，与 shanghai-260310.osm.pbf 匹配）：
--   上海华东仓库：         lat=31.1985, lng=121.5889
--   途中-张杨路附近：      lat=31.2100, lng=121.5640
--   途中-世纪大道附近：    lat=31.2256, lng=121.5350（当前位置）
--   目的地-陆家嘴环路：    lat=31.2356, lng=121.5050
--
-- 可用接口测试：
--   GET  /api/logistics/routes/1               查路线详情（routeId=1）
--   GET  /api/logistics/routes/order/1         按订单ID查询（orderId=1）
--   GET  /api/logistics/routes/no/LR202603131200000001  按物流单号查询
--   GET  /api/logistics/track/1/latest         查最新轨迹位置
--   GET  /api/logistics/track/1/history        查完整轨迹历史
--   POST /api/logistics/routes                 用 order_id=2 创建新路线（触发GraphHopper规划）
--   PUT  /api/logistics/routes/1/status        更新路线状态（需 admin/driver 角色）
-- ============================================================

-- 物流路线（route_id=1，运输中，与 order_delivery.id=1 对应）
INSERT INTO `logistics_route`
  (`route_no`, `order_id`, `delivery_id`, `driver_id`, `warehouse_id`,
   `start_address`,  `start_lat`, `start_lng`,
   `end_address`,    `end_lat`,   `end_lng`,
   `current_lat`,    `current_lng`, `current_address`, `last_track_time`,
   `route_status`, `estimated_arrival_time`,
   `receiver_name`, `receiver_phone`, `remark`, `create_time`)
VALUES
(
  'LR202603131200000001',
  1, 1, 1, 2,
  -- 出发地：上海华东仓库
  '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
  -- 目的地：张三收货地址
  '上海市浦东新区陆家嘴环路1000号',              31.2356, 121.5050,
  -- 当前位置：途中世纪大道附近（与最新轨迹点一致）
  31.2256, 121.5350, '上海市浦东新区世纪大道附近', DATE_SUB(NOW(), INTERVAL 15 MINUTE),
  -- 状态：运输中；预计45分钟后到达
  1, DATE_ADD(NOW(), INTERVAL 45 MINUTE),
  '张三', '13800138000', NULL,
  DATE_SUB(NOW(), INTERVAL 60 MINUTE)           -- 路线创建时间（1小时前）
);

-- 里程碑节点（route_id=1）
INSERT INTO `logistics_node`
  (`route_id`, `node_type`, `node_name`, `node_address`, `latitude`, `longitude`,
   `sequence_no`, `planned_arrive_time`, `actual_arrive_time`, `node_status`)
VALUES
-- 出发点：已离开仓库
(1, 0, '出发仓库',
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
  0,
  DATE_SUB(NOW(), INTERVAL 60 MINUTE),   -- 计划1小时前出发
  DATE_SUB(NOW(), INTERVAL 60 MINUTE),   -- 实际1小时前出发
  1),                                    -- 已到达（已出发）
-- 目的地：未到达
(1, 2, '收货地址',
 '上海市浦东新区陆家嘴环路1000号', 31.2356, 121.5050,
  99,
  DATE_ADD(NOW(), INTERVAL 45 MINUTE),   -- 预计45分钟后到达
  NULL,
  0);                                    -- 未到达

-- 实时轨迹（route_id=1，3条，模拟从仓库出发向陆家嘴行进中）
INSERT INTO `logistics_track`
  (`route_id`, `driver_id`, `latitude`, `longitude`, `speed`, `heading`, `accuracy`, `address`, `track_time`)
VALUES
-- 第1点：从仓库出发（speed=0，准备发车）
(1, 1, 31.1985, 121.5889,  0.0, 340.0, 5.0,
 '上海市浦东新区物流园区B区2号',
 DATE_SUB(NOW(), INTERVAL 60 MINUTE)),
-- 第2点：途经张杨路
(1, 1, 31.2100, 121.5640, 38.5, 332.0, 4.5,
 '上海市浦东新区张杨路附近',
 DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
-- 第3点：途经世纪大道（当前最新位置，与 logistics_route.current_lat/lng 一致）
(1, 1, 31.2256, 121.5350, 42.0, 320.0, 4.0,
 '上海市浦东新区世纪大道附近',
 DATE_SUB(NOW(), INTERVAL 15 MINUTE));

-- 订单服务数据库表
-- 包含订单信息、订单项等

-- 删除表（按依赖顺序）
DROP TABLE IF EXISTS `order_item`;
DROP TABLE IF EXISTS `order_info`;

-- ============================================
-- 1. 订单信息表
-- ============================================
CREATE TABLE `order_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no` VARCHAR(50) NOT NULL COMMENT '订单号（唯一）',
  `customer_id` BIGINT NOT NULL COMMENT '关联customer_info表的id（顾客）',
  `shop_id` BIGINT NOT NULL COMMENT '关联shop_info表的id（商户）',
  `address_id` BIGINT NOT NULL COMMENT '关联customer_address表的id（收货地址）',
  `total_amount` DECIMAL(10, 2) NOT NULL COMMENT '订单总金额（商品金额+运费）',
  `product_amount` DECIMAL(10, 2) NOT NULL COMMENT '商品总金额',
  `shipping_fee` DECIMAL(10, 2) DEFAULT 0.00 COMMENT '运费',
  `warehouse_id` BIGINT DEFAULT NULL COMMENT '发货仓库ID（下单时扣减库存的仓库，用于物流路线起点）',
  `order_status` TINYINT DEFAULT 0 COMMENT '订单状态：0=待支付，1=待发货，2=待揽件，3=派送中，4=已完成，5=已取消',
  `payment_status` TINYINT DEFAULT 0 COMMENT '支付状态：0=未支付，1=已支付',
  `payment_time` DATETIME COMMENT '支付时间',
  `shipping_time` DATETIME COMMENT '发货时间',
  `complete_time` DATETIME COMMENT '完成时间',
  `cancel_time` DATETIME COMMENT '取消时间',
  `cancel_reason` VARCHAR(255) COMMENT '取消原因',
  `remark` VARCHAR(500) COMMENT '订单备注',
  `customer_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '顾客软删除：0=正常，1=已隐藏（对顾客不可见，商户/管理员仍可见）',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  INDEX `idx_customer_id` (`customer_id`),
  INDEX `idx_shop_id` (`shop_id`),
  INDEX `idx_warehouse_id` (`warehouse_id`),
  INDEX `idx_order_status` (`order_status`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单信息表';

-- ============================================
-- 2. 订单项表（订单商品明细）
-- ============================================
CREATE TABLE `order_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单项ID',
  `order_id` BIGINT NOT NULL COMMENT '关联order_info表的id',
  `product_id` BIGINT NOT NULL COMMENT '关联product_info表的id',
  `product_name` VARCHAR(200) NOT NULL COMMENT '商品名称（下单时的快照）',
  `product_image` VARCHAR(255) COMMENT '商品图片（下单时的快照）',
  `product_price` DECIMAL(10, 2) NOT NULL COMMENT '商品单价（下单时的价格）',
  `quantity` INT NOT NULL COMMENT '购买数量',
  `subtotal` DECIMAL(10, 2) NOT NULL COMMENT '小计金额（单价*数量）',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_order_id` (`order_id`),
  INDEX `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单项表';

-- ============================================================
-- 测试数据
-- customer_id=1  → customer.sql 中的张三
-- shop_id=1      → shop.sql 中的优质食品店
-- address_id=1   → customer.sql 中的上海浦东默认地址
-- ============================================================

-- 订单信息测试数据
-- ★ order_id=1：派送中（driver.sql delivery_status=2 运输中，logistics.sql route_status=1 运输中，三表保持一致）
-- ★ order_id=2：已支付待发货（可用于测试"创建路线" POST 接口）
INSERT INTO `order_info`
  (`order_no`, `customer_id`, `shop_id`, `address_id`, `warehouse_id`,
   `total_amount`, `product_amount`, `shipping_fee`,
   `order_status`, `payment_status`, `payment_time`, `shipping_time`, `remark`,
   `customer_deleted`)
VALUES
(
  'ORD20260313000001', 1, 1, 1, 2,
  163.00, 163.00, 0.00,
  3, 1,                                              -- 派送中（order_status=3），已支付
  DATE_SUB(NOW(), INTERVAL 3 HOUR),                  -- 3小时前付款
  DATE_SUB(NOW(), INTERVAL 1 HOUR),                  -- 1小时前发货
  '请尽快配送',
  0                                                  -- 顾客未删除
),
(
  'ORD20260313000002', 1, 1, 1, 2,
  35.00, 35.00, 0.00,
  1, 1,                                              -- 待发货（order_status=1），已支付
  DATE_SUB(NOW(), INTERVAL 30 MINUTE),               -- 30分钟前付款
  NULL,
  NULL,
  0                                                  -- 顾客未删除
);

-- 订单项测试数据
-- order_id=1：坚果礼盒 + 有机果汁（均在上海华东仓库 warehouse_id=2 有库存）
INSERT INTO `order_item`
  (`order_id`, `product_id`, `product_name`, `product_image`, `product_price`, `quantity`, `subtotal`)
VALUES
(1, 1, '优质坚果礼盒', 'https://example.com/images/nut1.jpg',   128.00, 1, 128.00),
(1, 2, '有机果汁',     'https://example.com/images/juice1.jpg',  35.00, 1,  35.00);

-- order_id=2：有机果汁（待发货，可通过 POST /api/logistics/routes 创建路线）
INSERT INTO `order_item`
  (`order_id`, `product_id`, `product_name`, `product_image`, `product_price`, `quantity`, `subtotal`)
VALUES
(2, 2, '有机果汁', 'https://example.com/images/juice1.jpg', 35.00, 1, 35.00);

-- 若现有库仍为旧枚举（3=已完成、4=已取消），可执行下面一条迁移后再启动新代码：
-- UPDATE `order_info` SET `order_status` = `order_status` + 1 WHERE `order_status` >= 3;
-- 商户服务数据库表
-- 包含商户信息、商品信息、商品分类、仓库、库存等

-- 删除表（按依赖顺序）
DROP TABLE IF EXISTS `warehouse_product`;
DROP TABLE IF EXISTS `product_info`;
DROP TABLE IF EXISTS `warehouse`;
DROP TABLE IF EXISTS `product_category`;
DROP TABLE IF EXISTS `shop_info`;

-- ============================================
-- 1. 商户信息表
-- ============================================
CREATE TABLE `shop_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '商户ID',
  `user_id` BIGINT NOT NULL COMMENT '关联user表的id（商户用户）',
  `shop_name` VARCHAR(100) NOT NULL COMMENT '商户名称',
  `shop_phone` VARCHAR(20) NOT NULL COMMENT '商户联系电话',
  `shop_email` VARCHAR(100) COMMENT '商户邮箱',
  `description` TEXT COMMENT '商户描述',
  `logo` VARCHAR(255) COMMENT '商户Logo URL',
  `business_license` VARCHAR(100) COMMENT '营业执照号',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0=禁用，1=启用，2=待审核',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  INDEX `idx_shop_name` (`shop_name`),
  INDEX `idx_status` (`status`),
  INDEX `idx_phone` (`shop_phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户信息表';

-- ============================================
-- 2. 商品分类表
-- ============================================
CREATE TABLE `product_category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `category_name` VARCHAR(50) NOT NULL COMMENT '分类名称',
  `parent_id` BIGINT DEFAULT 0 COMMENT '父分类ID（0表示顶级分类）',
  `sort_order` INT DEFAULT 0 COMMENT '排序顺序',
  `description` VARCHAR(255) COMMENT '分类描述',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0=禁用，1=启用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_parent_id` (`parent_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

-- ============================================
-- 3. 商品信息表
-- ============================================
CREATE TABLE `product_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `shop_id` BIGINT NOT NULL COMMENT '关联shop_info表的id',
  `category_id` BIGINT COMMENT '关联product_category表的id（商品分类）',
  `product_name` VARCHAR(200) NOT NULL COMMENT '商品名称',
  `product_code` VARCHAR(50) COMMENT '商品编码（SKU）',
  `description` TEXT COMMENT '商品描述',
  `price` DECIMAL(10, 2) NOT NULL COMMENT '商品价格',
  `original_price` DECIMAL(10, 2) COMMENT '原价（用于显示折扣）',
  `unit` VARCHAR(20) DEFAULT '件' COMMENT '单位（件、箱、kg等）',
  `weight` DECIMAL(10, 2) COMMENT '重量（kg）',
  `images` TEXT COMMENT '商品图片（JSON数组格式，存储多个图片URL）',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0=下架，1=上架，2=待审核',
  `sales_count` INT DEFAULT 0 COMMENT '销量',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_shop_id` (`shop_id`),
  INDEX `idx_category_id` (`category_id`),
  INDEX `idx_product_name` (`product_name`),
  INDEX `idx_status` (`status`),
  INDEX `idx_product_code` (`product_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品信息表';

-- ============================================
-- 4. 仓库信息表
-- ============================================
CREATE TABLE `warehouse` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '仓库ID',
  `warehouse_name` VARCHAR(100) NOT NULL COMMENT '仓库名称',
  `warehouse_phone` VARCHAR(20) COMMENT '仓库联系电话',
  `province` VARCHAR(50) COMMENT '省份',
  `city` VARCHAR(50) COMMENT '城市',
  `district` VARCHAR(50) COMMENT '区/县',
  `detail_address` VARCHAR(255) NOT NULL COMMENT '详细地址',
  `postal_code` VARCHAR(10) COMMENT '邮编',
  `capacity` INT DEFAULT 0 COMMENT '仓库容量（单位：件，0表示无限制）',
  `latitude` DOUBLE DEFAULT NULL COMMENT '仓库纬度（用于物流路线规划）',
  `longitude` DOUBLE DEFAULT NULL COMMENT '仓库经度（用于物流路线规划）',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0=禁用，1=启用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_warehouse_name` (`warehouse_name`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库信息表';

-- ============================================
-- 5. 仓库商品关联表（多对多，含库存）
-- ============================================
CREATE TABLE `warehouse_product` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `warehouse_id` BIGINT NOT NULL COMMENT '关联warehouse表的id',
  `product_id` BIGINT NOT NULL COMMENT '关联product_info表的id',
  `stock` INT DEFAULT 0 COMMENT '该商品在该仓库的库存数量',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_warehouse_product` (`warehouse_id`, `product_id`),
  INDEX `idx_warehouse_id` (`warehouse_id`),
  INDEX `idx_product_id` (`product_id`),
  INDEX `idx_stock` (`stock`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库商品关联表';

-- ============================================================
-- 测试数据
-- user_id=3 → shop（优质食品店），user_id=4 → shop2（时尚服装店）
-- ============================================================

-- 商户信息（shop_id=1 为物流测试所用商户）
INSERT INTO `shop_info` (`user_id`, `shop_name`, `shop_phone`, `shop_email`, `description`, `business_license`, `status`) VALUES
(3, '优质食品店', '021-55001234', 'shop1@example.com', '专业销售优质食品，品质保证', '91310000123456789X', 1),
(4, '时尚服装店', '021-55005678', 'shop2@example.com', '时尚潮流服装，款式新颖',     '91310000123456789Y', 1);

-- 商品分类
INSERT INTO `product_category` (`category_name`, `parent_id`, `sort_order`, `description`, `status`) VALUES
('食品', 0, 1, '各类食品', 1),
('服装', 0, 2, '各类服装', 1),
('零食', 1, 1, '休闲零食',  1),
('饮料', 1, 2, '各类饮料',  1),
('男装', 2, 1, '男士服装',  1),
('女装', 2, 2, '女士服装',  1);

-- 商品信息（shop_id=1 的商品用于 order_id=1 的物流测试）
INSERT INTO `product_info`
  (`shop_id`, `category_id`, `product_name`, `product_code`, `description`, `price`, `original_price`, `unit`, `weight`, `images`, `status`, `sales_count`)
VALUES
(1, 3, '优质坚果礼盒',   'SP001', '精选优质坚果，包含核桃、杏仁、腰果等', 128.00, 158.00, '盒', 1.5,
 '["https://example.com/images/nut1.jpg","https://example.com/images/nut2.jpg"]', 1, 26),
(1, 4, '有机果汁',       'SP002', '100%纯天然有机果汁，无添加',           35.00,  45.00,  '瓶', 0.5,
 '["https://example.com/images/juice1.jpg"]',                                     1, 51),
(2, 5, '商务休闲衬衫',   'SP003', '高品质商务休闲衬衫，多色可选',         299.00, 399.00, '件', 0.3,
 '["https://example.com/images/shirt1.jpg","https://example.com/images/shirt2.jpg"]', 1, 15),
(2, 6, '时尚连衣裙',     'SP004', '春季新款时尚连衣裙，优雅大方',         399.00, 499.00, '件', 0.4,
 '["https://example.com/images/dress1.jpg"]',                                     1, 8);

-- 仓库信息（全部位于上海市，与本地 OSM/地图范围一致，避免外地坐标导致路径规划失败）
-- ★ warehouse_id=2（上海华东仓库）为物流测试主用仓库，lat=31.1985, longitude=121.5889
INSERT INTO `warehouse`
  (`warehouse_name`, `warehouse_phone`, `province`, `city`, `district`, `detail_address`, `postal_code`, `capacity`, `latitude`, `longitude`, `status`)
VALUES
('上海闵行分拨仓', '021-51001001', '上海市', '上海市', '闵行区',   '闵行区元江路426号物流园1号库', '201111', 10000, 31.0928, 121.4536, 1),
('上海华东仓库',   '021-87654321', '上海市', '上海市', '浦东新区', '浦东新区物流园区B区2号',     '200135',  8000, 31.1985, 121.5889, 1),  -- ★ 物流测试使用
('上海宝山分拨仓', '021-51001003', '上海市', '上海市', '宝山区',   '宝山区富锦路1500号物流园C库', '200444',  6000, 31.3988, 121.4312, 1);

-- 仓库商品关联（含库存）
-- 说明：product_id=1（坚果礼盒）和 product_id=2（有机果汁）均存入上海华东仓库（id=2）
--       以支持 order_id=1 从上海华东仓库发货的物流测试场景
INSERT INTO `warehouse_product` (`warehouse_id`, `product_id`, `stock`) VALUES
-- 仓库1（上海闵行分拨仓）
(1, 1, 50),   -- 优质坚果礼盒，库存50
(1, 2, 100),  -- 有机果汁，库存100
-- 仓库2（上海华东仓库）★ 物流测试主用
(2, 1, 49),   -- 优质坚果礼盒，库存49（order_id=1 发货后减1）
(2, 2, 99),   -- 有机果汁，库存99（order_id=1 发货后减1）
(2, 3, 30),   -- 商务休闲衬衫，库存30
(2, 4, 20),   -- 时尚连衣裙，库存20
-- 仓库3（上海宝山分拨仓）
(3, 2, 100),  -- 有机果汁，库存100
(3, 4, 10);   -- 时尚连衣裙，库存10

-- 用户表
-- 用于存储系统用户基本信息

DROP TABLE IF EXISTS `user`;

CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(100) NOT NULL COMMENT '用户名',
  `secret` VARCHAR(100) NOT NULL COMMENT '密码哈希值',
  `permission` INT NOT NULL COMMENT '权限Flag：1=管理员(admin)，2=顾客用户(customer)，3=商户用户(Shop)，4=运输员(Driver)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 插入测试数据
-- 注意：密码统一为 123456，已使用BCrypt加密
-- 实际使用时，密码需要通过BCrypt加密后再存储

INSERT INTO `user` (`username`, `secret`, `permission`) VALUES
('admin', '$2a$10$.ckJuQWWC9dUh0hOa7v4LuxBa6PICggDyUUz7awFv4CM/rh7tQZ0a', 1),      -- 管理员（密码：123456）
('customer', '$2a$10$.ckJuQWWC9dUh0hOa7v4LuxBa6PICggDyUUz7awFv4CM/rh7tQZ0a', 2),  -- 顾客用户（密码：123456）
('shop', '$2a$10$.ckJuQWWC9dUh0hOa7v4LuxBa6PICggDyUUz7awFv4CM/rh7tQZ0a', 3),      -- 商户用户（密码：123456）
('shop2', '$2a$10$.ckJuQWWC9dUh0hOa7v4LuxBa6PICggDyUUz7awFv4CM/rh7tQZ0a', 3),     -- 商户用户2（密码：123456）
('driver', '$2a$10$.ckJuQWWC9dUh0hOa7v4LuxBa6PICggDyUUz7awFv4CM/rh7tQZ0a', 4);     -- 运输员（密码：123456）

-- ============================================================
-- 物流历史完整测试数据（generate_history_data.py 生成）
-- 包含：30条历史路线 + 3名新用户 + 完整关联表数据
-- 执行前提：logistics.sql / customer.sql / driver.sql /
--           order.sql / shop.sql / user.sql 均已执行完毕
-- ============================================================

SET NAMES utf8mb4;

-- ──────────────────────────────────────────
-- 新用户（user_id 6-9，追加到已有 1-5 之后）
-- ──────────────────────────────────────────
INSERT INTO `user` (`id`, `username`, `secret`, `permission`) VALUES
(6, 'customer2', '$2a$10$.ckJuQWWC9dUh0hOa7v4LuxBa6PICggDyUUz7awFv4CM/rh7tQZ0a', 2),  -- 李明
(7, 'customer3', '$2a$10$.ckJuQWWC9dUh0hOa7v4LuxBa6PICggDyUUz7awFv4CM/rh7tQZ0a', 2),  -- 王芳
(8, 'driver2',   '$2a$10$.ckJuQWWC9dUh0hOa7v4LuxBa6PICggDyUUz7awFv4CM/rh7tQZ0a', 4),  -- 陈刚
(9, 'driver3',   '$2a$10$.ckJuQWWC9dUh0hOa7v4LuxBa6PICggDyUUz7awFv4CM/rh7tQZ0a', 4);  -- 赵磊

-- 顾客信息
INSERT INTO `customer_info`
  (`id`, `user_id`, `real_name`, `phone`, `email`, `gender`, `birthday`, `status`)
VALUES
(2, 6, '李明', '13900139001', 'liming@example.com',   1, '1988-08-20', 1),
(3, 7, '王芳', '13600136002', 'wangfang@example.com', 2, '1992-03-12', 1);

-- 收货地址（addr_id 3-12，追加到已有 1-2 之后）
INSERT INTO `customer_address`
  (`id`, `customer_id`, `receiver_name`, `receiver_phone`,
   `province`, `city`, `district`, `detail_address`, `postal_code`,
   `is_default`, `latitude`, `longitude`)
VALUES
(3, 1, '张三', '13800138000', '上海市', '上海市', '徐汇区', '上海市徐汇区龙华路668号', '200030', 0, 31.182, 121.438),
(4, 1, '张三', '13800138000', '上海市', '上海市', '杨浦区', '上海市杨浦区中山北二路800号', '200092', 0, 31.255, 121.505),
(5, 2, '李明', '13900139001', '上海市', '上海市', '普陀区', '上海市普陀区武威路200号', '200062', 0, 31.242, 121.405),
(6, 2, '李明', '13900139001', '上海市', '上海市', '黄浦区', '上海市黄浦区南京东路668号', '200001', 0, 31.2321, 121.48),
(7, 2, '李明', '13900139001', '上海市', '上海市', '长宁区', '上海市长宁区虹桥路1号', '200050', 0, 31.196, 121.346),
(8, 3, '王芳', '13600136002', '上海市', '上海市', '浦东新区', '上海市浦东新区张江高科技园区', '200120', 0, 31.2021, 121.6087),
(9, 3, '王芳', '13600136002', '上海市', '上海市', '浦东新区', '上海市浦东新区金桥出口加工区', '200120', 0, 31.206, 121.64),
(10, 3, '王芳', '13600136002', '上海市', '上海市', '静安区', '上海市静安区大宁路288号', '200040', 0, 31.228, 121.456),
(11, 2, '李明', '13900139001', '上海市', '上海市', '徐汇区', '上海市徐汇区龙华路668号', '200030', 0, 31.182, 121.438),
(12, 3, '王芳', '13600136002', '上海市', '上海市', '杨浦区', '上海市杨浦区中山北二路800号', '200092', 0, 31.255, 121.505);

-- 运输员信息（driver_id 2-3）
INSERT INTO `driver_info`
  (`id`, `user_id`, `real_name`, `phone`, `email`, `id_card`, `gender`, `birthday`,
   `license_number`, `license_type`, `license_expire_date`, `status`)
VALUES
(2, 8, '陈刚', '13900139008', 'chengang@example.com',  '310104198805051234', 1, '1988-05-05',
 'SH0002345678901', 'C1', '2031-06-30', 1),
(3, 9, '赵磊', '13600136009', 'zhaolei@example.com',   '310106199209091234', 1, '1992-09-09',
 'SH0003456789012', 'B2', '2029-12-31', 1);

-- 车辆信息（vehicle_id 3-4）
INSERT INTO `vehicle_info`
  (`id`, `driver_id`, `vehicle_type`, `vehicle_brand`, `vehicle_model`,
   `license_plate`, `load_capacity`, `volume_capacity`, `vehicle_status`)
VALUES
(3, 2, '小型货车', '福田', 'FT-150', '沪C11223', 2.0, 8.0,  1),
(4, 3, '中型货车', '庆铃', 'QL-300', '沪D44556', 4.0, 15.0, 1);

-- ──────────────────────────────────────────
-- 历史订单（order_id 3-32，全部已完成 status=4）
-- ──────────────────────────────────────────
INSERT INTO `order_info`
  (`id`, `order_no`, `customer_id`, `shop_id`, `address_id`, `warehouse_id`,
   `total_amount`, `product_amount`, `shipping_fee`,
   `order_status`, `payment_status`, `payment_time`, `shipping_time`, `complete_time`,
   `remark`, `customer_deleted`)
VALUES
(3, 'ORD_HIST_00000003', 1, 1, 3, 2, 138.00, 128.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '05:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '07:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '08:28:00'), NULL, 0),
(4, 'ORD_HIST_00000004', 1, 1, 4, 2, 45.00, 35.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '12:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:32:00'), NULL, 0),
(5, 'ORD_HIST_00000005', 2, 1, 7, 2, 45.00, 35.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '15:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '17:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '18:52:00'), NULL, 0),
(6, 'ORD_HIST_00000006', 2, 2, 5, 2, 309.00, 299.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '08:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:52:00'), NULL, 0),
(7, 'ORD_HIST_00000007', 3, 1, 8, 2, 138.00, 128.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 6 DAY), '10:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 6 DAY), '12:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 6 DAY), '12:22:00'), NULL, 0),
(8, 'ORD_HIST_00000008', 1, 2, 4, 2, 409.00, 399.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '13:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:32:00'), NULL, 0),
(9, 'ORD_HIST_00000009', 2, 1, 6, 2, 45.00, 35.00, 10.00, 5, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 8 DAY), '14:00:00'), NULL, NULL, NULL, 0),
(10, 'ORD_HIST_00000010', 3, 1, 9, 2, 138.00, 128.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 9 DAY), '07:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 9 DAY), '09:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 9 DAY), '10:00:00'), NULL, 0),
(11, 'ORD_HIST_00000011', 3, 2, 10, 2, 309.00, 299.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '15:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '17:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '18:13:00'), NULL, 0),
(12, 'ORD_HIST_00000012', 2, 1, 6, 2, 45.00, 35.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '11:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:38:00'), NULL, 0),
(13, 'ORD_HIST_00000013', 1, 2, 3, 2, 409.00, 399.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '07:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:43:00'), NULL, 0),
(14, 'ORD_HIST_00000014', 3, 1, 10, 2, 138.00, 128.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '06:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:57:00'), NULL, 0),
(15, 'ORD_HIST_00000015', 2, 2, 7, 2, 309.00, 299.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '16:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '18:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '19:18:00'), NULL, 0),
(16, 'ORD_HIST_00000016', 1, 1, 4, 2, 45.00, 35.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '13:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '15:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '16:05:00'), NULL, 0),
(17, 'ORD_HIST_00000017', 3, 2, 8, 2, 409.00, 399.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 16 DAY), '06:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 16 DAY), '08:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 16 DAY), '08:52:00'), NULL, 0),
(18, 'ORD_HIST_00000018', 2, 1, 11, 2, 138.00, 128.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '07:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:43:00'), NULL, 0),
(19, 'ORD_HIST_00000019', 1, 1, 2, 2, 45.00, 35.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '10:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '12:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '13:22:00'), NULL, 0),
(20, 'ORD_HIST_00000020', 2, 2, 6, 2, 309.00, 299.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '05:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:50:00'), NULL, 0),
(21, 'ORD_HIST_00000021', 3, 1, 12, 2, 45.00, 35.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:32:00'), NULL, 0),
(22, 'ORD_HIST_00000022', 3, 2, 8, 2, 409.00, 399.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 21 DAY), '11:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 21 DAY), '13:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 21 DAY), '13:53:00'), NULL, 0),
(23, 'ORD_HIST_00000023', 3, 1, 9, 2, 138.00, 128.00, 10.00, 5, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 22 DAY), '15:30:00'), NULL, NULL, NULL, 0),
(24, 'ORD_HIST_00000024', 3, 2, 10, 2, 309.00, 299.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '07:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:43:00'), NULL, 0),
(25, 'ORD_HIST_00000025', 2, 1, 5, 2, 45.00, 35.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:53:00'), NULL, 0),
(26, 'ORD_HIST_00000026', 1, 1, 1, 1, 138.00, 128.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '07:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '10:00:00'), NULL, 0),
(27, 'ORD_HIST_00000027', 1, 1, 3, 1, 138.00, 128.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '12:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '15:18:00'), NULL, 0),
(28, 'ORD_HIST_00000028', 3, 1, 8, 1, 45.00, 35.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '08:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '10:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '11:35:00'), NULL, 0),
(29, 'ORD_HIST_00000029', 3, 2, 12, 3, 409.00, 399.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '08:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:55:00'), NULL, 0),
(30, 'ORD_HIST_00000030', 2, 1, 7, 3, 45.00, 35.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '11:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '13:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '14:18:00'), NULL, 0),
(31, 'ORD_HIST_00000031', 1, 2, 4, 3, 409.00, 399.00, 10.00, 5, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 30 DAY), '15:00:00'), NULL, NULL, NULL, 0),
(32, 'ORD_HIST_00000032', 2, 1, 11, 3, 45.00, 35.00, 10.00, 4, 1, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '07:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '09:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '10:40:00'), NULL, 0);

-- 订单项（每订单1件商品）
INSERT INTO `order_item`
  (`order_id`, `product_id`, `product_name`, `product_image`, `product_price`, `quantity`, `subtotal`)
VALUES
(3, 1, '优质坚果礼盒', 'https://example.com/images/prod1.jpg', 128.00, 1, 128.00),
(4, 2, '有机果汁', 'https://example.com/images/prod2.jpg', 35.00, 1, 35.00),
(5, 2, '有机果汁', 'https://example.com/images/prod2.jpg', 35.00, 1, 35.00),
(6, 3, '商务休闲衬衫', 'https://example.com/images/prod3.jpg', 299.00, 1, 299.00),
(7, 1, '优质坚果礼盒', 'https://example.com/images/prod1.jpg', 128.00, 1, 128.00),
(8, 4, '时尚连衣裙', 'https://example.com/images/prod4.jpg', 399.00, 1, 399.00),
(9, 2, '有机果汁', 'https://example.com/images/prod2.jpg', 35.00, 1, 35.00),
(10, 1, '优质坚果礼盒', 'https://example.com/images/prod1.jpg', 128.00, 1, 128.00),
(11, 3, '商务休闲衬衫', 'https://example.com/images/prod3.jpg', 299.00, 1, 299.00),
(12, 2, '有机果汁', 'https://example.com/images/prod2.jpg', 35.00, 1, 35.00),
(13, 4, '时尚连衣裙', 'https://example.com/images/prod4.jpg', 399.00, 1, 399.00),
(14, 1, '优质坚果礼盒', 'https://example.com/images/prod1.jpg', 128.00, 1, 128.00),
(15, 3, '商务休闲衬衫', 'https://example.com/images/prod3.jpg', 299.00, 1, 299.00),
(16, 2, '有机果汁', 'https://example.com/images/prod2.jpg', 35.00, 1, 35.00),
(17, 4, '时尚连衣裙', 'https://example.com/images/prod4.jpg', 399.00, 1, 399.00),
(18, 1, '优质坚果礼盒', 'https://example.com/images/prod1.jpg', 128.00, 1, 128.00),
(19, 2, '有机果汁', 'https://example.com/images/prod2.jpg', 35.00, 1, 35.00),
(20, 3, '商务休闲衬衫', 'https://example.com/images/prod3.jpg', 299.00, 1, 299.00),
(21, 2, '有机果汁', 'https://example.com/images/prod2.jpg', 35.00, 1, 35.00),
(22, 4, '时尚连衣裙', 'https://example.com/images/prod4.jpg', 399.00, 1, 399.00),
(23, 1, '优质坚果礼盒', 'https://example.com/images/prod1.jpg', 128.00, 1, 128.00),
(24, 3, '商务休闲衬衫', 'https://example.com/images/prod3.jpg', 299.00, 1, 299.00),
(25, 2, '有机果汁', 'https://example.com/images/prod2.jpg', 35.00, 1, 35.00),
(26, 1, '优质坚果礼盒', 'https://example.com/images/prod1.jpg', 128.00, 1, 128.00),
(27, 1, '优质坚果礼盒', 'https://example.com/images/prod1.jpg', 128.00, 1, 128.00),
(28, 2, '有机果汁', 'https://example.com/images/prod2.jpg', 35.00, 1, 35.00),
(29, 4, '时尚连衣裙', 'https://example.com/images/prod4.jpg', 399.00, 1, 399.00),
(30, 2, '有机果汁', 'https://example.com/images/prod2.jpg', 35.00, 1, 35.00),
(31, 4, '时尚连衣裙', 'https://example.com/images/prod4.jpg', 399.00, 1, 399.00),
(32, 2, '有机果汁', 'https://example.com/images/prod2.jpg', 35.00, 1, 35.00);

-- 配送记录（delivery_id 2-31，全部已送达 delivery_status=3）
INSERT INTO `order_delivery`
  (`id`, `order_id`, `driver_id`, `vehicle_id`, `delivery_status`,
   `accept_time`, `pickup_time`, `delivery_time`,
   `delivery_address`, `receiver_name`, `receiver_phone`, `remark`)
VALUES
(2, 3, 1, 1, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '07:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '07:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '08:28:00'), '上海市徐汇区龙华路668号', '张三', '13800138000', NULL),
(3, 4, 1, 1, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '13:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:32:00'), '上海市杨浦区中山北二路800号', '张三', '13800138000', NULL),
(4, 5, 1, 1, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '17:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '17:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '18:52:00'), '上海市长宁区虹桥路1号', '李明', '13900139001', NULL),
(5, 6, 2, 3, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '09:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:52:00'), '上海市普陀区武威路200号', '李明', '13900139001', NULL),
(6, 7, 2, 3, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 6 DAY), '11:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 6 DAY), '12:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 6 DAY), '12:22:00'), '上海市浦东新区张江高科技园区', '王芳', '13600136002', NULL),
(7, 8, 1, 1, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:32:00'), '上海市杨浦区中山北二路800号', '张三', '13800138000', NULL),
(8, 9, 2, 3, 4, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 8 DAY), '15:30:00'), NULL, NULL, '上海市黄浦区南京东路668号', '李明', '13900139001', NULL),
(9, 10, 3, 4, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 9 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 9 DAY), '09:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 9 DAY), '10:00:00'), '上海市浦东新区金桥出口加工区', '王芳', '13600136002', NULL),
(10, 11, 3, 4, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '16:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '17:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '18:13:00'), '上海市静安区大宁路288号', '王芳', '13600136002', NULL),
(11, 12, 1, 1, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '12:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:38:00'), '上海市黄浦区南京东路668号', '李明', '13900139001', NULL),
(12, 13, 2, 3, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '08:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:43:00'), '上海市徐汇区龙华路668号', '张三', '13800138000', NULL),
(13, 14, 3, 4, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '07:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:57:00'), '上海市静安区大宁路288号', '王芳', '13600136002', NULL),
(14, 15, 1, 1, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '17:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '18:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '19:18:00'), '上海市长宁区虹桥路1号', '李明', '13900139001', NULL),
(15, 16, 2, 3, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '15:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '15:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '16:05:00'), '上海市杨浦区中山北二路800号', '张三', '13800138000', NULL),
(16, 17, 1, 1, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 16 DAY), '08:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 16 DAY), '08:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 16 DAY), '08:52:00'), '上海市浦东新区张江高科技园区', '王芳', '13600136002', NULL),
(17, 18, 3, 4, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '08:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:43:00'), '上海市徐汇区龙华路668号', '李明', '13900139001', NULL),
(18, 19, 2, 3, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '12:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '12:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '13:22:00'), '上海市静安区南京西路688号', '张三', '13800138000', NULL),
(19, 20, 1, 1, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '06:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:50:00'), '上海市黄浦区南京东路668号', '李明', '13900139001', NULL),
(20, 21, 3, 4, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '10:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:32:00'), '上海市杨浦区中山北二路800号', '王芳', '13600136002', NULL),
(21, 22, 2, 3, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 21 DAY), '13:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 21 DAY), '13:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 21 DAY), '13:53:00'), '上海市浦东新区张江高科技园区', '王芳', '13600136002', NULL),
(22, 23, 1, 1, 4, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 22 DAY), '17:00:00'), NULL, NULL, '上海市浦东新区金桥出口加工区', '王芳', '13600136002', NULL),
(23, 24, 2, 3, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '08:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:43:00'), '上海市静安区大宁路288号', '王芳', '13600136002', NULL),
(24, 25, 3, 4, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '15:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:53:00'), '上海市普陀区武威路200号', '李明', '13900139001', NULL),
(25, 26, 1, 1, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '08:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '10:00:00'), '上海市浦东新区陆家嘴环路1000号', '张三', '13800138000', NULL),
(26, 27, 2, 3, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '15:18:00'), '上海市徐汇区龙华路668号', '张三', '13800138000', NULL),
(27, 28, 3, 4, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '10:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '10:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '11:35:00'), '上海市浦东新区张江高科技园区', '王芳', '13600136002', NULL),
(28, 29, 3, 4, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '09:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:55:00'), '上海市杨浦区中山北二路800号', '王芳', '13600136002', NULL),
(29, 30, 2, 3, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '12:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '13:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '14:18:00'), '上海市长宁区虹桥路1号', '李明', '13900139001', NULL),
(30, 31, 3, 4, 4, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 30 DAY), '16:30:00'), NULL, NULL, '上海市杨浦区中山北二路800号', '张三', '13800138000', NULL),
(31, 32, 1, 1, 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '09:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '10:40:00'), '上海市徐汇区龙华路668号', '李明', '13900139001', NULL);

-- ──────────────────────────────────────────
-- 物流路线（route_id 2-31）
-- ──────────────────────────────────────────
INSERT INTO `logistics_route`
  (`id`, `route_no`, `order_id`, `delivery_id`, `driver_id`, `warehouse_id`,
   `start_address`, `start_lat`, `start_lng`,
   `end_address`, `end_lat`, `end_lng`,
   `current_lat`, `current_lng`, `current_address`, `last_track_time`,
   `route_status`, `estimated_arrival_time`, `actual_arrival_time`,
   `receiver_name`, `receiver_phone`, `planned_route`, `create_time`, `update_time`)
VALUES
(2, 'LR_HIST_0002', 3, 2, 1, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市徐汇区龙华路668号', 31.182, 121.438,
 31.1820, 121.4380, '上海市徐汇区龙华路668号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '08:26:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '08:10:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '08:28:00'),
 '张三', '13800138000', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '07:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '08:28:00')),
(3, 'LR_HIST_0003', 4, 3, 1, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市杨浦区中山北二路800号', 31.255, 121.505,
 31.2550, 121.5050, '上海市杨浦区中山北二路800号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:19:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:32:00'),
 '张三', '13800138000', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:32:00')),
(4, 'LR_HIST_0004', 5, 4, 1, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市长宁区虹桥路1号', 31.196, 121.346,
 31.1960, 121.3460, '上海市长宁区虹桥路1号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '19:31:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '18:22:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '18:52:00'),
 '李明', '13900139001', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '17:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '18:52:00')),
(5, 'LR_HIST_0005', 6, 5, 2, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市普陀区武威路200号', 31.242, 121.405,
 31.2420, 121.4050, '上海市普陀区武威路200号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:34:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:50:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:52:00'),
 '李明', '13900139001', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:52:00')),
(6, 'LR_HIST_0006', 7, 6, 2, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市浦东新区张江高科技园区', 31.2021, 121.6087,
 31.2021, 121.6087, '上海市浦东新区张江高科技园区', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 6 DAY), '12:06:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 6 DAY), '12:20:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 6 DAY), '12:22:00'),
 '王芳', '13600136002', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 6 DAY), '12:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 6 DAY), '12:22:00')),
(7, 'LR_HIST_0007', 8, 7, 1, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市杨浦区中山北二路800号', 31.255, 121.505,
 31.2550, 121.5050, '上海市杨浦区中山北二路800号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:19:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:32:00'),
 '张三', '13800138000', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:32:00')),
(8, 'LR_HIST_0008', 9, 8, 2, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市黄浦区南京东路668号', 31.2321, 121.48,
 31.1987, 121.5185, '上海市黄浦区南京东路668号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 8 DAY), '16:12:00'),
 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 8 DAY), '16:35:00'), NULL,
 '李明', '13900139001', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 8 DAY), '16:00:00'), NULL),
(9, 'LR_HIST_0009', 10, 9, 3, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市浦东新区金桥出口加工区', 31.206, 121.64,
 31.2060, 121.6400, '上海市浦东新区金桥出口加工区', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 9 DAY), '09:39:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 9 DAY), '09:55:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 9 DAY), '10:00:00'),
 '王芳', '13600136002', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 9 DAY), '09:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 9 DAY), '10:00:00')),
(10, 'LR_HIST_0010', 11, 10, 3, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市静安区大宁路288号', 31.228, 121.456,
 31.2280, 121.4560, '上海市静安区大宁路288号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '18:18:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '17:42:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '18:13:00'),
 '王芳', '13600136002', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '17:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '18:13:00')),
(11, 'LR_HIST_0011', 12, 11, 1, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市黄浦区南京东路668号', 31.2321, 121.48,
 31.2321, 121.4800, '上海市黄浦区南京东路668号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:23:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:35:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:38:00'),
 '李明', '13900139001', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:38:00')),
(12, 'LR_HIST_0012', 13, 12, 2, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市徐汇区龙华路668号', 31.182, 121.438,
 31.1820, 121.4380, '上海市徐汇区龙华路668号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:27:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:40:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:43:00'),
 '张三', '13800138000', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:43:00')),
(13, 'LR_HIST_0013', 14, 13, 3, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市静安区大宁路288号', 31.228, 121.456,
 31.2280, 121.4560, '上海市静安区大宁路288号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:56:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:42:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:57:00'),
 '王芳', '13600136002', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:57:00')),
(14, 'LR_HIST_0014', 15, 14, 1, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市长宁区虹桥路1号', 31.196, 121.346,
 31.1960, 121.3460, '上海市长宁区虹桥路1号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '20:01:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '18:52:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '19:18:00'),
 '李明', '13900139001', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '18:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '19:18:00')),
(15, 'LR_HIST_0015', 16, 15, 2, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市杨浦区中山北二路800号', 31.255, 121.505,
 31.2550, 121.5050, '上海市杨浦区中山北二路800号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '15:49:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '16:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '16:05:00'),
 '张三', '13800138000', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '15:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '16:05:00')),
(16, 'LR_HIST_0016', 17, 16, 1, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市浦东新区张江高科技园区', 31.2021, 121.6087,
 31.2021, 121.6087, '上海市浦东新区张江高科技园区', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 16 DAY), '08:39:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 16 DAY), '08:50:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 16 DAY), '08:52:00'),
 '王芳', '13600136002', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 16 DAY), '08:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 16 DAY), '08:52:00')),
(17, 'LR_HIST_0017', 18, 17, 3, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市徐汇区龙华路668号', 31.182, 121.438,
 31.1820, 121.4380, '上海市徐汇区龙华路668号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:27:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:40:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:43:00'),
 '李明', '13900139001', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:43:00')),
(18, 'LR_HIST_0018', 19, 18, 2, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市静安区南京西路688号', 31.2289, 121.449,
 31.2289, 121.4490, '上海市静安区南京西路688号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '13:12:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '13:10:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '13:22:00'),
 '张三', '13800138000', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '12:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '13:22:00')),
(19, 'LR_HIST_0019', 20, 19, 1, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市黄浦区南京东路668号', 31.2321, 121.48,
 31.2321, 121.4800, '上海市黄浦区南京东路668号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:49:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:35:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:50:00'),
 '李明', '13900139001', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:50:00')),
(20, 'LR_HIST_0020', 21, 20, 3, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市杨浦区中山北二路800号', 31.255, 121.505,
 31.2550, 121.5050, '上海市杨浦区中山北二路800号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:26:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:32:00'),
 '王芳', '13600136002', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:32:00')),
(21, 'LR_HIST_0021', 22, 21, 2, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市浦东新区张江高科技园区', 31.2021, 121.6087,
 31.2021, 121.6087, '上海市浦东新区张江高科技园区', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 21 DAY), '13:34:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 21 DAY), '13:50:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 21 DAY), '13:53:00'),
 '王芳', '13600136002', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 21 DAY), '13:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 21 DAY), '13:53:00')),
(22, 'LR_HIST_0022', 23, 22, 1, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市浦东新区金桥出口加工区', 31.206, 121.64,
 31.2022, 121.6225, '上海市浦东新区金桥出口加工区', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 22 DAY), '17:48:00'),
 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 22 DAY), '17:55:00'), NULL,
 '王芳', '13600136002', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 22 DAY), '17:30:00'), NULL),
(23, 'LR_HIST_0023', 24, 23, 2, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市静安区大宁路288号', 31.228, 121.456,
 31.2280, 121.4560, '上海市静安区大宁路288号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:27:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:42:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:43:00'),
 '王芳', '13600136002', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:43:00')),
(24, 'LR_HIST_0024', 25, 24, 3, 2,
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
 '上海市普陀区武威路200号', 31.242, 121.405,
 31.2420, 121.4050, '上海市普陀区武威路200号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:34:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:50:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:53:00'),
 '李明', '13900139001', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:53:00')),
(25, 'LR_HIST_0025', 26, 25, 1, 1,
 '上海市闵行区元江路426号物流园1号库（上海闵行分拨仓）', 31.0928, 121.4536,
 '上海市浦东新区陆家嘴环路1000号', 31.2356, 121.505,
 31.2356, 121.5050, '上海市浦东新区陆家嘴环路1000号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:30:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:55:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '10:00:00'),
 '张三', '13800138000', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '10:00:00')),
(26, 'LR_HIST_0026', 27, 26, 2, 1,
 '上海市闵行区元江路426号物流园1号库（上海闵行分拨仓）', 31.0928, 121.4536,
 '上海市徐汇区龙华路668号', 31.182, 121.438,
 31.1820, 121.4380, '上海市徐汇区龙华路668号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '14:49:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '15:15:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '15:18:00'),
 '张三', '13800138000', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '15:18:00')),
(27, 'LR_HIST_0027', 28, 27, 3, 1,
 '上海市闵行区元江路426号物流园1号库（上海闵行分拨仓）', 31.0928, 121.4536,
 '上海市浦东新区张江高科技园区', 31.2021, 121.6087,
 31.2021, 121.6087, '上海市浦东新区张江高科技园区', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '11:11:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '11:32:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '11:35:00'),
 '王芳', '13600136002', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '10:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '11:35:00')),
(28, 'LR_HIST_0028', 29, 28, 3, 3,
 '上海市宝山区富锦路1500号物流园C库（上海宝山分拨仓）', 31.3988, 121.4312,
 '上海市杨浦区中山北二路800号', 31.255, 121.505,
 31.2550, 121.5050, '上海市杨浦区中山北二路800号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:31:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:50:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:55:00'),
 '王芳', '13600136002', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:55:00')),
(29, 'LR_HIST_0029', 30, 29, 2, 3,
 '上海市宝山区富锦路1500号物流园C库（上海宝山分拨仓）', 31.3988, 121.4312,
 '上海市长宁区虹桥路1号', 31.196, 121.346,
 31.1960, 121.3460, '上海市长宁区虹桥路1号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '13:42:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '14:10:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '14:18:00'),
 '李明', '13900139001', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '13:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '14:18:00')),
(30, 'LR_HIST_0030', 31, 30, 3, 3,
 '上海市宝山区富锦路1500号物流园C库（上海宝山分拨仓）', 31.3988, 121.4312,
 '上海市杨浦区中山北二路800号', 31.255, 121.505,
 31.3108, 121.4725, '上海市杨浦区中山北二路800号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 30 DAY), '17:52:00'),
 3, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 30 DAY), '17:50:00'), NULL,
 '张三', '13800138000', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 30 DAY), '17:00:00'), NULL),
(31, 'LR_HIST_0031', 32, 31, 1, 3,
 '上海市宝山区富锦路1500号物流园C库（上海宝山分拨仓）', 31.3988, 121.4312,
 '上海市徐汇区龙华路668号', 31.182, 121.438,
 31.1820, 121.4380, '上海市徐汇区龙华路668号', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '10:12:00'),
 2, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '10:35:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '10:40:00'),
 '李明', '13900139001', NULL, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '09:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '10:40:00'));

-- 里程碑节点（每条路线出发点+目的地，共60个）
INSERT INTO `logistics_node`
  (`route_id`, `node_type`, `node_name`, `node_address`,
   `latitude`, `longitude`, `sequence_no`,
   `planned_arrive_time`, `actual_arrive_time`, `node_status`)
VALUES
(2, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '07:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '07:30:00'), 1),
(2, 2, '收货地址', '上海市徐汇区龙华路668号', 31.182, 121.438, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '08:10:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '08:28:00'), 1),
(3, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:00:00'), 1),
(3, 2, '收货地址', '上海市杨浦区中山北二路800号', 31.255, 121.505, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:32:00'), 1),
(4, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '17:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '17:30:00'), 1),
(4, 2, '收货地址', '上海市长宁区虹桥路1号', 31.196, 121.346, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '18:22:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '18:52:00'), 1),
(5, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:00:00'), 1),
(5, 2, '收货地址', '上海市普陀区武威路200号', 31.242, 121.405, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:50:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:52:00'), 1),
(6, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 6 DAY), '12:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 6 DAY), '12:00:00'), 1),
(6, 2, '收货地址', '上海市浦东新区张江高科技园区', 31.2021, 121.6087, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 6 DAY), '12:20:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 6 DAY), '12:22:00'), 1),
(7, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:00:00'), 1),
(7, 2, '收货地址', '上海市杨浦区中山北二路800号', 31.255, 121.505, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:32:00'), 1),
(8, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 8 DAY), '16:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 8 DAY), '16:00:00'), 1),
(8, 2, '收货地址', '上海市黄浦区南京东路668号', 31.2321, 121.48, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 8 DAY), '16:35:00'), NULL, 0),
(9, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 9 DAY), '09:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 9 DAY), '09:30:00'), 1),
(9, 2, '收货地址', '上海市浦东新区金桥出口加工区', 31.206, 121.64, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 9 DAY), '09:55:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 9 DAY), '10:00:00'), 1),
(10, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '17:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '17:00:00'), 1),
(10, 2, '收货地址', '上海市静安区大宁路288号', 31.228, 121.456, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '17:42:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '18:13:00'), 1),
(11, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:00:00'), 1),
(11, 2, '收货地址', '上海市黄浦区南京东路668号', 31.2321, 121.48, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:35:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:38:00'), 1),
(12, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:00:00'), 1),
(12, 2, '收货地址', '上海市徐汇区龙华路668号', 31.182, 121.438, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:40:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:43:00'), 1),
(13, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:00:00'), 1),
(13, 2, '收货地址', '上海市静安区大宁路288号', 31.228, 121.456, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:42:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:57:00'), 1),
(14, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '18:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '18:00:00'), 1),
(14, 2, '收货地址', '上海市长宁区虹桥路1号', 31.196, 121.346, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '18:52:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '19:18:00'), 1),
(15, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '15:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '15:30:00'), 1),
(15, 2, '收货地址', '上海市杨浦区中山北二路800号', 31.255, 121.505, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '16:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '16:05:00'), 1),
(16, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 16 DAY), '08:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 16 DAY), '08:30:00'), 1),
(16, 2, '收货地址', '上海市浦东新区张江高科技园区', 31.2021, 121.6087, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 16 DAY), '08:50:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 16 DAY), '08:52:00'), 1),
(17, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:00:00'), 1),
(17, 2, '收货地址', '上海市徐汇区龙华路668号', 31.182, 121.438, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:40:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:43:00'), 1),
(18, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '12:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '12:30:00'), 1),
(18, 2, '收货地址', '上海市静安区南京西路688号', 31.2289, 121.449, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '13:10:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '13:22:00'), 1),
(19, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:00:00'), 1),
(19, 2, '收货地址', '上海市黄浦区南京东路668号', 31.2321, 121.48, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:35:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:50:00'), 1),
(20, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:00:00'), 1),
(20, 2, '收货地址', '上海市杨浦区中山北二路800号', 31.255, 121.505, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:32:00'), 1),
(21, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 21 DAY), '13:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 21 DAY), '13:30:00'), 1),
(21, 2, '收货地址', '上海市浦东新区张江高科技园区', 31.2021, 121.6087, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 21 DAY), '13:50:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 21 DAY), '13:53:00'), 1),
(22, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 22 DAY), '17:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 22 DAY), '17:30:00'), 1),
(22, 2, '收货地址', '上海市浦东新区金桥出口加工区', 31.206, 121.64, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 22 DAY), '17:55:00'), NULL, 0),
(23, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:00:00'), 1),
(23, 2, '收货地址', '上海市静安区大宁路288号', 31.228, 121.456, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:42:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:43:00'), 1),
(24, 0, '出发仓库', '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:00:00'), 1),
(24, 2, '收货地址', '上海市普陀区武威路200号', 31.242, 121.405, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:50:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:53:00'), 1),
(25, 0, '出发仓库', '上海市闵行区元江路426号物流园1号库（上海闵行分拨仓）', 31.0928, 121.4536, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:00:00'), 1),
(25, 2, '收货地址', '上海市浦东新区陆家嘴环路1000号', 31.2356, 121.505, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:55:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '10:00:00'), 1),
(26, 0, '出发仓库', '上海市闵行区元江路426号物流园1号库（上海闵行分拨仓）', 31.0928, 121.4536, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '14:30:00'), 1),
(26, 2, '收货地址', '上海市徐汇区龙华路668号', 31.182, 121.438, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '15:15:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '15:18:00'), 1),
(27, 0, '出发仓库', '上海市闵行区元江路426号物流园1号库（上海闵行分拨仓）', 31.0928, 121.4536, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '10:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '10:30:00'), 1),
(27, 2, '收货地址', '上海市浦东新区张江高科技园区', 31.2021, 121.6087, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '11:32:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '11:35:00'), 1),
(28, 0, '出发仓库', '上海市宝山区富锦路1500号物流园C库（上海宝山分拨仓）', 31.3988, 121.4312, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:00:00'), 1),
(28, 2, '收货地址', '上海市杨浦区中山北二路800号', 31.255, 121.505, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:50:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:55:00'), 1),
(29, 0, '出发仓库', '上海市宝山区富锦路1500号物流园C库（上海宝山分拨仓）', 31.3988, 121.4312, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '13:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '13:00:00'), 1),
(29, 2, '收货地址', '上海市长宁区虹桥路1号', 31.196, 121.346, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '14:10:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '14:18:00'), 1),
(30, 0, '出发仓库', '上海市宝山区富锦路1500号物流园C库（上海宝山分拨仓）', 31.3988, 121.4312, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 30 DAY), '17:00:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 30 DAY), '17:00:00'), 1),
(30, 2, '收货地址', '上海市杨浦区中山北二路800号', 31.255, 121.505, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 30 DAY), '17:50:00'), NULL, 0),
(31, 0, '出发仓库', '上海市宝山区富锦路1500号物流园C库（上海宝山分拨仓）', 31.3988, 121.4312, 0, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '09:30:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '09:30:00'), 1),
(31, 2, '收货地址', '上海市徐汇区龙华路668号', 31.182, 121.438, 99, TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '10:35:00'), TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '10:40:00'), 1);

-- ──────────────────────────────────────────
-- 轨迹点（约 300 条，每条路线 8-14 点）
-- ──────────────────────────────────────────
INSERT INTO `logistics_track`
  (`route_id`, `driver_id`, `latitude`, `longitude`,
   `altitude`, `speed`, `heading`, `accuracy`, `address`, `track_time`)
VALUES
(2, 1, 31.1985, 121.5889, 6.4, 0.0, 265.4, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '07:30:00')),
(2, 1, 31.1978, 121.5794, 6.4, 20.0, 265.4, 4.5, '上海市合庆路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '07:35:00')),
(2, 1, 31.1972, 121.5700, 6.4, 20.0, 265.4, 4.5, '上海市高科西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '07:38:00')),
(2, 1, 31.1963, 121.5600, 6.4, 20.0, 264.3, 4.5, '上海市浦东南路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '07:40:00')),
(2, 1, 31.1955, 121.5500, 6.4, 20.0, 264.3, 4.5, '上海市浦东南路（慢速区）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '07:43:00')),
(2, 1, 31.1948, 121.5400, 6.4, 20.0, 265.7, 4.5, '上海市商城路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '07:46:00')),
(2, 1, 31.1942, 121.5300, 6.4, 20.0, 265.7, 4.5, '上海市南浦大桥', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '07:49:00')),
(2, 1, 31.1937, 121.5225, 6.4, 6.0, 265.5, 4.5, '上海市过桥中', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '07:52:00')),
(2, 1, 31.1932, 121.5150, 6.4, 6.0, 265.5, 4.5, '上海市徐汇宛平南路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '08:00:00')),
(2, 1, 31.1925, 121.5050, 6.4, 20.0, 265.3, 4.5, '上海市龙华西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '08:04:00')),
(2, 1, 31.1918, 121.4960, 6.4, 20.0, 265.2, 4.5, '上海市龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '08:07:00')),
(2, 1, 31.1912, 121.4870, 6.4, 20.0, 265.2, 4.5, '上海市龙华西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '08:09:00')),
(2, 1, 31.1903, 121.4790, 6.4, 20.0, 262.9, 4.5, '上海市龙华西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '08:11:00')),
(2, 1, 31.1895, 121.4710, 6.4, 20.0, 262.9, 4.5, '上海市龙华西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '08:14:00')),
(2, 1, 31.1882, 121.4634, 6.4, 20.0, 258.3, 4.5, '上海市龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '08:16:00')),
(2, 1, 31.1868, 121.4558, 6.4, 20.0, 258.3, 4.5, '上海市龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '08:18:00')),
(2, 1, 31.1842, 121.4453, 6.4, 20.0, 253.9, 4.5, '上海市龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '08:21:00')),
(2, 1, 31.1820, 121.4380, 6.4, 0.0, 250.6, 5.5, '上海市龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 2 DAY), '08:26:00')),
(3, 1, 31.1985, 121.5889, 6.4, 0.0, 319.8, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:00:00')),
(3, 1, 31.2065, 121.5810, 6.4, 38.0, 319.8, 4.5, '上海市东方路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:03:00')),
(3, 1, 31.2110, 121.5760, 6.4, 38.0, 316.5, 4.5, '上海市张杨路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:04:00')),
(3, 1, 31.2155, 121.5710, 6.4, 38.0, 316.5, 4.5, '上海市昌里路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:05:00')),
(3, 1, 31.2200, 121.5660, 6.4, 38.0, 316.5, 4.5, '上海市源深路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:06:00')),
(3, 1, 31.2245, 121.5610, 6.4, 38.0, 316.5, 4.5, '上海市崂山路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:08:00')),
(3, 1, 31.2290, 121.5560, 6.5, 38.0, 316.5, 4.5, '上海市杨浦大桥', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:09:00')),
(3, 1, 31.2335, 121.5510, 6.5, 38.0, 316.5, 4.5, '上海市杨浦区进入', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:10:00')),
(3, 1, 31.2380, 121.5449, 6.5, 38.0, 310.8, 4.5, '上海市杨浦（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:11:00')),
(3, 1, 31.2425, 121.5388, 6.5, 38.0, 310.8, 4.5, '上海市杨浦区进入', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:12:00')),
(3, 1, 31.2458, 121.5301, 6.5, 38.0, 294.4, 4.5, '上海市杨浦区进入', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:14:00')),
(3, 1, 31.2492, 121.5215, 6.5, 38.0, 294.4, 4.5, '上海市杨浦（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:15:00')),
(3, 1, 31.2535, 121.5118, 6.5, 38.0, 297.4, 4.5, '上海市杨浦（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:17:00')),
(3, 1, 31.2550, 121.5050, 6.5, 0.0, 284.5, 5.5, '上海市杨浦（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 3 DAY), '14:19:00')),
(4, 1, 31.1985, 121.5889, 6.4, 0.0, 264.0, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '17:30:00')),
(4, 1, 31.1976, 121.5794, 6.4, 14.0, 264.0, 4.5, '上海市向西', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '17:37:00')),
(4, 1, 31.1968, 121.5700, 6.4, 14.0, 264.0, 4.5, '上海市浦东南路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '17:41:00')),
(4, 1, 31.1958, 121.5595, 6.4, 14.0, 263.6, 4.5, '上海市浦东南路（慢速区）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '17:45:00')),
(4, 1, 31.1948, 121.5490, 6.4, 14.0, 263.6, 4.5, '上海市南浦大桥入口', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '17:50:00')),
(4, 1, 31.1939, 121.5380, 6.4, 14.0, 264.5, 4.5, '上海市南浦大桥', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '17:54:00')),
(4, 1, 31.1930, 121.5270, 6.4, 14.0, 264.5, 4.5, '上海市外环高速', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '17:59:00')),
(4, 1, 31.1924, 121.5175, 6.4, 5.0, 265.8, 5.5, '上海市外环（高速）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '18:04:00')),
(4, 1, 31.1918, 121.5080, 6.4, 5.0, 265.8, 5.5, '上海市外环（高速）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '18:15:00')),
(4, 1, 31.1905, 121.4977, 6.4, 14.0, 261.6, 4.5, '上海市长宁', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '18:22:00')),
(4, 1, 31.1892, 121.4875, 6.4, 14.0, 261.6, 4.5, '上海市虹桥路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '18:26:00')),
(4, 1, 31.1876, 121.4770, 6.4, 14.0, 259.9, 4.5, '上海市虹桥（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '18:30:00')),
(4, 1, 31.1860, 121.4665, 6.4, 14.0, 259.9, 4.5, '上海市虹桥路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '18:34:00')),
(4, 1, 31.1855, 121.4557, 6.4, 14.0, 266.9, 4.5, '上海市虹桥路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '18:39:00')),
(4, 1, 31.1850, 121.4450, 6.4, 14.0, 266.9, 4.5, '上海市虹桥路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '18:43:00')),
(4, 1, 31.1850, 121.4340, 6.4, 14.0, 270.0, 4.5, '上海市虹桥路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '18:48:00')),
(4, 1, 31.1850, 121.4230, 6.4, 14.0, 270.0, 4.5, '上海市虹桥路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '18:52:00')),
(4, 1, 31.1860, 121.4117, 6.4, 14.0, 275.9, 4.5, '上海市虹桥（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '18:57:00')),
(4, 1, 31.1870, 121.4005, 6.4, 14.0, 275.9, 4.5, '上海市虹桥（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '19:01:00')),
(4, 1, 31.1908, 121.3893, 6.4, 14.0, 291.3, 4.5, '上海市虹桥（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '19:06:00')),
(4, 1, 31.1945, 121.3780, 6.4, 14.0, 291.3, 4.5, '上海市虹桥（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '19:11:00')),
(4, 1, 31.1953, 121.3620, 6.4, 14.0, 273.1, 4.5, '上海市虹桥（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '19:18:00')),
(4, 1, 31.1960, 121.3460, 6.4, 0.0, 273.1, 5.5, '上海市虹桥（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 4 DAY), '19:31:00')),
(5, 2, 31.1985, 121.5889, 6.4, 0.0, 266.5, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:00:00')),
(5, 2, 31.1980, 121.5794, 6.4, 38.0, 266.5, 4.5, '上海市合庆路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:02:00')),
(5, 2, 31.1975, 121.5700, 6.4, 38.0, 266.5, 4.5, '上海市浦东南路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:04:00')),
(5, 2, 31.1970, 121.5600, 6.4, 38.0, 266.7, 4.5, '上海市延安路隧道浦东', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:05:00')),
(5, 2, 31.1965, 121.5500, 6.4, 38.0, 266.7, 4.5, '上海市延安路隧道出口', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:07:00')),
(5, 2, 31.1962, 121.5390, 6.4, 38.0, 268.5, 4.5, '上海市延安中路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:08:00')),
(5, 2, 31.1960, 121.5280, 6.4, 38.0, 268.5, 4.5, '上海市延安西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:10:00')),
(5, 2, 31.1969, 121.5180, 6.4, 20.0, 276.0, 4.5, '上海市曹杨路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:12:00')),
(5, 2, 31.1978, 121.5080, 6.4, 20.0, 276.0, 4.5, '上海市长寿路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:15:00')),
(5, 2, 31.2009, 121.4990, 6.4, 38.0, 291.9, 4.5, '上海市云岭东路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:17:00')),
(5, 2, 31.2040, 121.4900, 6.4, 38.0, 291.9, 4.5, '上海市普陀（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:18:00')),
(5, 2, 31.2078, 121.4809, 6.4, 38.0, 295.7, 4.5, '上海市云岭东路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:20:00')),
(5, 2, 31.2115, 121.4718, 6.4, 38.0, 295.7, 4.5, '上海市云岭东路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:21:00')),
(5, 2, 31.2155, 121.4629, 6.4, 38.0, 297.7, 4.5, '上海市云岭东路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:23:00')),
(5, 2, 31.2195, 121.4540, 6.4, 38.0, 297.7, 4.5, '上海市云岭东路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:24:00')),
(5, 2, 31.2236, 121.4452, 6.4, 38.0, 299.0, 4.5, '上海市云岭东路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:26:00')),
(5, 2, 31.2278, 121.4365, 6.5, 38.0, 299.0, 4.5, '上海市普陀（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:27:00')),
(5, 2, 31.2318, 121.4282, 6.5, 38.0, 299.6, 4.5, '上海市普陀（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:29:00')),
(5, 2, 31.2358, 121.4200, 6.5, 38.0, 299.6, 4.5, '上海市普陀（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:30:00')),
(5, 2, 31.2389, 121.4125, 6.5, 38.0, 295.8, 4.5, '上海市普陀（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:31:00')),
(5, 2, 31.2420, 121.4050, 6.5, 0.0, 295.8, 5.5, '上海市普陀（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 5 DAY), '10:34:00')),
(6, 2, 31.1985, 121.5889, 6.4, 0.0, 86.1, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 6 DAY), '12:00:00')),
(6, 2, 31.1990, 121.5975, 6.4, 28.0, 86.1, 4.5, '上海市外高桥大道', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 6 DAY), '12:03:00')),
(6, 2, 31.1996, 121.6058, 6.4, 28.0, 85.2, 4.5, '上海市外环路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 6 DAY), '12:05:00')),
(6, 2, 31.2006, 121.6095, 6.4, 28.0, 72.5, 4.5, '上海市申江路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 6 DAY), '12:06:00')),
(6, 2, 31.2016, 121.6098, 6.4, 28.0, 14.4, 4.5, '上海市科苑路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 6 DAY), '12:06:00')),
(6, 2, 31.2021, 121.6087, 6.4, 0.0, 298.0, 5.5, '上海市张江高科（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 6 DAY), '12:06:00')),
(7, 1, 31.1985, 121.5889, 6.4, 0.0, 319.8, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:00:00')),
(7, 1, 31.2065, 121.5810, 6.4, 38.0, 319.8, 4.5, '上海市东方路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:03:00')),
(7, 1, 31.2110, 121.5760, 6.4, 38.0, 316.5, 4.5, '上海市张杨路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:04:00')),
(7, 1, 31.2155, 121.5710, 6.4, 38.0, 316.5, 4.5, '上海市昌里路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:05:00')),
(7, 1, 31.2200, 121.5660, 6.4, 38.0, 316.5, 4.5, '上海市源深路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:06:00')),
(7, 1, 31.2245, 121.5610, 6.4, 38.0, 316.5, 4.5, '上海市崂山路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:08:00')),
(7, 1, 31.2290, 121.5560, 6.5, 38.0, 316.5, 4.5, '上海市杨浦大桥', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:09:00')),
(7, 1, 31.2335, 121.5510, 6.5, 38.0, 316.5, 4.5, '上海市杨浦区进入', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:10:00')),
(7, 1, 31.2380, 121.5449, 6.5, 38.0, 310.8, 4.5, '上海市杨浦（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:11:00')),
(7, 1, 31.2425, 121.5388, 6.5, 38.0, 310.8, 4.5, '上海市杨浦区进入', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:12:00')),
(7, 1, 31.2458, 121.5301, 6.5, 38.0, 294.4, 4.5, '上海市杨浦区进入', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:14:00')),
(7, 1, 31.2492, 121.5215, 6.5, 38.0, 294.4, 4.5, '上海市杨浦（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:15:00')),
(7, 1, 31.2535, 121.5118, 6.5, 38.0, 297.4, 4.5, '上海市杨浦（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:17:00')),
(7, 1, 31.2550, 121.5050, 6.5, 0.0, 284.5, 5.5, '上海市杨浦（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 7 DAY), '15:19:00')),
(8, 2, 31.1985, 121.5889, 6.4, 0.0, 268.2, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 8 DAY), '16:00:00')),
(8, 2, 31.1983, 121.5794, 6.4, 38.0, 268.2, 4.5, '上海市向西', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 8 DAY), '16:02:00')),
(8, 2, 31.1980, 121.5700, 6.4, 38.0, 268.2, 4.5, '上海市浦东南路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 8 DAY), '16:04:00')),
(8, 2, 31.1977, 121.5595, 6.4, 38.0, 268.4, 4.5, '上海市延安路隧道', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 8 DAY), '16:05:00')),
(8, 2, 31.1975, 121.5490, 6.4, 38.0, 268.4, 4.5, '上海市延安路隧道出口', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 8 DAY), '16:07:00')),
(8, 2, 31.1974, 121.5380, 6.4, 38.0, 269.1, 4.5, '上海市西藏中路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 8 DAY), '16:09:00')),
(8, 2, 31.1972, 121.5270, 6.4, 38.0, 269.1, 4.5, '上海市人民广场附近', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 8 DAY), '16:10:00')),
(9, 3, 31.1985, 121.5889, 6.4, 0.0, 85.7, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 9 DAY), '09:30:00')),
(9, 3, 31.1992, 121.5998, 6.4, 38.0, 85.7, 4.5, '上海市外环路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 9 DAY), '09:33:00')),
(9, 3, 31.2004, 121.6108, 6.4, 38.0, 82.7, 4.5, '上海市浦东大道金桥段', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 9 DAY), '09:34:00')),
(9, 3, 31.2022, 121.6225, 6.4, 38.0, 79.8, 4.5, '上海市金桥路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 9 DAY), '09:36:00')),
(9, 3, 31.2042, 121.6335, 6.4, 38.0, 78.0, 4.5, '上海市金桥工业区', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 9 DAY), '09:38:00')),
(9, 3, 31.2058, 121.6390, 6.4, 38.0, 71.2, 4.5, '上海市金桥加工区', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 9 DAY), '09:39:00')),
(9, 3, 31.2060, 121.6400, 6.4, 0.0, 76.8, 5.5, '上海市金桥（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 9 DAY), '09:39:00')),
(10, 3, 31.1985, 121.5889, 6.4, 0.0, 266.8, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '17:00:00')),
(10, 3, 31.1980, 121.5785, 6.4, 14.0, 266.8, 4.5, '上海市向西', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '17:08:00')),
(10, 3, 31.1975, 121.5680, 6.4, 14.0, 266.8, 4.5, '上海市浦东南路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '17:12:00')),
(10, 3, 31.1970, 121.5570, 6.4, 14.0, 267.0, 4.5, '上海市延安路隧道浦东', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '17:17:00')),
(10, 3, 31.1965, 121.5460, 6.4, 14.0, 267.0, 4.5, '上海市延安路隧道出口', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '17:21:00')),
(10, 3, 31.1961, 121.5350, 6.4, 14.0, 267.9, 4.5, '上海市延安中路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '17:26:00')),
(10, 3, 31.1958, 121.5240, 6.4, 5.0, 267.9, 5.5, '上海市延安西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '17:32:00')),
(10, 3, 31.1966, 121.5145, 6.4, 5.0, 276.0, 5.5, '上海市陕西南路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '17:43:00')),
(10, 3, 31.1975, 121.5050, 6.4, 14.0, 276.0, 4.5, '上海市大丰路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '17:49:00')),
(10, 3, 31.1996, 121.4959, 6.4, 14.0, 285.4, 4.5, '上海市共和新路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '17:53:00')),
(10, 3, 31.2018, 121.4868, 6.4, 14.0, 285.4, 4.5, '上海市静安大宁路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '17:57:00')),
(10, 3, 31.2043, 121.4789, 6.4, 14.0, 290.3, 4.5, '上海市共和新路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '18:00:00')),
(10, 3, 31.2068, 121.4710, 6.4, 14.0, 290.3, 4.5, '上海市共和新路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '18:04:00')),
(10, 3, 31.2118, 121.4618, 6.4, 14.0, 302.4, 4.5, '上海市共和新路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '18:08:00')),
(10, 3, 31.2178, 121.4592, 6.4, 14.0, 339.7, 4.5, '上海市静安大宁路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '18:11:00')),
(10, 3, 31.2238, 121.4572, 6.4, 14.0, 344.1, 4.5, '上海市静安大宁路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '18:14:00')),
(10, 3, 31.2280, 121.4560, 6.5, 0.0, 346.3, 5.5, '上海市静安大宁路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '18:18:00')),
(11, 1, 31.1985, 121.5889, 6.4, 0.0, 268.2, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:00:00')),
(11, 1, 31.1983, 121.5794, 6.4, 38.0, 268.2, 4.5, '上海市向西', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:02:00')),
(11, 1, 31.1980, 121.5700, 6.4, 38.0, 268.2, 4.5, '上海市浦东南路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:04:00')),
(11, 1, 31.1977, 121.5595, 6.4, 38.0, 268.4, 4.5, '上海市延安路隧道', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:05:00')),
(11, 1, 31.1975, 121.5490, 6.4, 38.0, 268.4, 4.5, '上海市延安路隧道出口', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:07:00')),
(11, 1, 31.1974, 121.5380, 6.4, 38.0, 269.1, 4.5, '上海市西藏中路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:09:00')),
(11, 1, 31.1972, 121.5270, 6.4, 38.0, 269.1, 4.5, '上海市人民广场附近', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:10:00')),
(11, 1, 31.1987, 121.5185, 6.4, 20.0, 281.7, 4.5, '上海市近南京东路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:12:00')),
(11, 1, 31.2002, 121.5100, 6.4, 20.0, 281.7, 4.5, '上海市黄浦南京东路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:14:00')),
(11, 1, 31.2045, 121.5034, 6.4, 38.0, 307.3, 4.5, '上海市近南京东路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:16:00')),
(11, 1, 31.2088, 121.4968, 6.4, 38.0, 307.3, 4.5, '上海市近南京东路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:17:00')),
(11, 1, 31.2140, 121.4926, 6.4, 38.0, 325.7, 4.5, '上海市近南京东路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:18:00')),
(11, 1, 31.2192, 121.4885, 6.4, 38.0, 325.7, 4.5, '上海市黄浦南京东路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:19:00')),
(11, 1, 31.2278, 121.4832, 6.5, 38.0, 332.2, 4.5, '上海市黄浦南京东路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:21:00')),
(11, 1, 31.2321, 121.4800, 6.5, 0.0, 327.5, 5.5, '上海市黄浦南京东路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '13:23:00')),
(12, 2, 31.1985, 121.5889, 6.4, 0.0, 265.4, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:00:00')),
(12, 2, 31.1978, 121.5794, 6.4, 38.0, 265.4, 4.5, '上海市合庆路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:02:00')),
(12, 2, 31.1972, 121.5700, 6.4, 38.0, 265.4, 4.5, '上海市高科西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:04:00')),
(12, 2, 31.1963, 121.5600, 6.4, 38.0, 264.3, 4.5, '上海市浦东南路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:05:00')),
(12, 2, 31.1955, 121.5500, 6.4, 38.0, 264.3, 4.5, '上海市浦东南路（慢速区）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:07:00')),
(12, 2, 31.1948, 121.5400, 6.4, 38.0, 265.7, 4.5, '上海市商城路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:08:00')),
(12, 2, 31.1942, 121.5300, 6.4, 38.0, 265.7, 4.5, '上海市南浦大桥', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:10:00')),
(12, 2, 31.1937, 121.5225, 6.4, 20.0, 265.5, 4.5, '上海市过桥中', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:11:00')),
(12, 2, 31.1932, 121.5150, 6.4, 20.0, 265.5, 4.5, '上海市徐汇宛平南路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:13:00')),
(12, 2, 31.1925, 121.5050, 6.4, 38.0, 265.3, 4.5, '上海市龙华西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:15:00')),
(12, 2, 31.1918, 121.4960, 6.4, 38.0, 265.2, 4.5, '上海市龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:17:00')),
(12, 2, 31.1912, 121.4870, 6.4, 38.0, 265.2, 4.5, '上海市龙华西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:18:00')),
(12, 2, 31.1903, 121.4790, 6.4, 38.0, 262.9, 4.5, '上海市龙华西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:19:00')),
(12, 2, 31.1895, 121.4710, 6.4, 38.0, 262.9, 4.5, '上海市龙华西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:21:00')),
(12, 2, 31.1882, 121.4634, 6.4, 38.0, 258.3, 4.5, '上海市龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:22:00')),
(12, 2, 31.1868, 121.4558, 6.4, 38.0, 258.3, 4.5, '上海市龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:23:00')),
(12, 2, 31.1842, 121.4453, 6.4, 38.0, 253.9, 4.5, '上海市龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:25:00')),
(12, 2, 31.1820, 121.4380, 6.4, 0.0, 250.6, 5.5, '上海市龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '09:27:00')),
(13, 3, 31.1985, 121.5889, 6.4, 0.0, 266.8, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:00:00')),
(13, 3, 31.1980, 121.5785, 6.4, 20.0, 266.8, 4.5, '上海市向西', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:05:00')),
(13, 3, 31.1975, 121.5680, 6.4, 20.0, 266.8, 4.5, '上海市浦东南路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:08:00')),
(13, 3, 31.1970, 121.5570, 6.4, 20.0, 267.0, 4.5, '上海市延安路隧道浦东', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:12:00')),
(13, 3, 31.1965, 121.5460, 6.4, 20.0, 267.0, 4.5, '上海市延安路隧道出口', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:15:00')),
(13, 3, 31.1961, 121.5350, 6.4, 20.0, 267.9, 4.5, '上海市延安中路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:18:00')),
(13, 3, 31.1958, 121.5240, 6.4, 6.0, 267.9, 4.5, '上海市延安西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:23:00')),
(13, 3, 31.1966, 121.5145, 6.4, 6.0, 276.0, 4.5, '上海市陕西南路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:32:00')),
(13, 3, 31.1975, 121.5050, 6.4, 20.0, 276.0, 4.5, '上海市大丰路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:36:00')),
(13, 3, 31.1996, 121.4959, 6.4, 20.0, 285.4, 4.5, '上海市共和新路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:39:00')),
(13, 3, 31.2018, 121.4868, 6.4, 20.0, 285.4, 4.5, '上海市静安大宁路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:41:00')),
(13, 3, 31.2043, 121.4789, 6.4, 20.0, 290.3, 4.5, '上海市共和新路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:44:00')),
(13, 3, 31.2068, 121.4710, 6.4, 20.0, 290.3, 4.5, '上海市共和新路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:46:00')),
(13, 3, 31.2118, 121.4618, 6.4, 20.0, 302.4, 4.5, '上海市共和新路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:49:00')),
(13, 3, 31.2178, 121.4592, 6.4, 20.0, 339.7, 4.5, '上海市静安大宁路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:51:00')),
(13, 3, 31.2238, 121.4572, 6.4, 20.0, 344.1, 4.5, '上海市静安大宁路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:54:00')),
(13, 3, 31.2280, 121.4560, 6.5, 0.0, 346.3, 5.5, '上海市静安大宁路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 13 DAY), '08:56:00')),
(14, 1, 31.1985, 121.5889, 6.4, 0.0, 264.0, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '18:00:00')),
(14, 1, 31.1976, 121.5794, 6.4, 14.0, 264.0, 4.5, '上海市向西', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '18:07:00')),
(14, 1, 31.1968, 121.5700, 6.4, 14.0, 264.0, 4.5, '上海市浦东南路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '18:11:00')),
(14, 1, 31.1958, 121.5595, 6.4, 14.0, 263.6, 4.5, '上海市浦东南路（慢速区）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '18:15:00')),
(14, 1, 31.1948, 121.5490, 6.4, 14.0, 263.6, 4.5, '上海市南浦大桥入口', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '18:20:00')),
(14, 1, 31.1939, 121.5380, 6.4, 14.0, 264.5, 4.5, '上海市南浦大桥', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '18:24:00')),
(14, 1, 31.1930, 121.5270, 6.4, 14.0, 264.5, 4.5, '上海市外环高速', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '18:29:00')),
(14, 1, 31.1924, 121.5175, 6.4, 5.0, 265.8, 5.5, '上海市外环（高速）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '18:34:00')),
(14, 1, 31.1918, 121.5080, 6.4, 5.0, 265.8, 5.5, '上海市外环（高速）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '18:45:00')),
(14, 1, 31.1905, 121.4977, 6.4, 14.0, 261.6, 4.5, '上海市长宁', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '18:52:00')),
(14, 1, 31.1892, 121.4875, 6.4, 14.0, 261.6, 4.5, '上海市虹桥路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '18:56:00')),
(14, 1, 31.1876, 121.4770, 6.4, 14.0, 259.9, 4.5, '上海市虹桥（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '19:00:00')),
(14, 1, 31.1860, 121.4665, 6.4, 14.0, 259.9, 4.5, '上海市虹桥路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '19:04:00')),
(14, 1, 31.1855, 121.4557, 6.4, 14.0, 266.9, 4.5, '上海市虹桥路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '19:09:00')),
(14, 1, 31.1850, 121.4450, 6.4, 14.0, 266.9, 4.5, '上海市虹桥路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '19:13:00')),
(14, 1, 31.1850, 121.4340, 6.4, 14.0, 270.0, 4.5, '上海市虹桥路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '19:18:00')),
(14, 1, 31.1850, 121.4230, 6.4, 14.0, 270.0, 4.5, '上海市虹桥路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '19:22:00')),
(14, 1, 31.1860, 121.4117, 6.4, 14.0, 275.9, 4.5, '上海市虹桥（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '19:27:00')),
(14, 1, 31.1870, 121.4005, 6.4, 14.0, 275.9, 4.5, '上海市虹桥（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '19:31:00')),
(14, 1, 31.1908, 121.3893, 6.4, 14.0, 291.3, 4.5, '上海市虹桥（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '19:36:00')),
(14, 1, 31.1945, 121.3780, 6.4, 14.0, 291.3, 4.5, '上海市虹桥（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '19:41:00')),
(14, 1, 31.1953, 121.3620, 6.4, 14.0, 273.1, 4.5, '上海市虹桥（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '19:48:00')),
(14, 1, 31.1960, 121.3460, 6.4, 0.0, 273.1, 5.5, '上海市虹桥（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '20:01:00')),
(15, 2, 31.1985, 121.5889, 6.4, 0.0, 319.8, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '15:30:00')),
(15, 2, 31.2065, 121.5810, 6.4, 38.0, 319.8, 4.5, '上海市东方路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '15:33:00')),
(15, 2, 31.2110, 121.5760, 6.4, 38.0, 316.5, 4.5, '上海市张杨路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '15:34:00')),
(15, 2, 31.2155, 121.5710, 6.4, 38.0, 316.5, 4.5, '上海市昌里路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '15:35:00')),
(15, 2, 31.2200, 121.5660, 6.4, 38.0, 316.5, 4.5, '上海市源深路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '15:36:00')),
(15, 2, 31.2245, 121.5610, 6.4, 38.0, 316.5, 4.5, '上海市崂山路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '15:38:00')),
(15, 2, 31.2290, 121.5560, 6.5, 38.0, 316.5, 4.5, '上海市杨浦大桥', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '15:39:00')),
(15, 2, 31.2335, 121.5510, 6.5, 38.0, 316.5, 4.5, '上海市杨浦区进入', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '15:40:00')),
(15, 2, 31.2380, 121.5449, 6.5, 38.0, 310.8, 4.5, '上海市杨浦（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '15:41:00')),
(15, 2, 31.2425, 121.5388, 6.5, 38.0, 310.8, 4.5, '上海市杨浦区进入', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '15:42:00')),
(15, 2, 31.2458, 121.5301, 6.5, 38.0, 294.4, 4.5, '上海市杨浦区进入', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '15:44:00')),
(15, 2, 31.2492, 121.5215, 6.5, 38.0, 294.4, 4.5, '上海市杨浦（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '15:45:00')),
(15, 2, 31.2535, 121.5118, 6.5, 38.0, 297.4, 4.5, '上海市杨浦（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '15:47:00')),
(15, 2, 31.2550, 121.5050, 6.5, 0.0, 284.5, 5.5, '上海市杨浦（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '15:49:00')),
(16, 1, 31.1985, 121.5889, 6.4, 0.0, 86.1, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 16 DAY), '08:30:00')),
(16, 1, 31.1990, 121.5975, 6.4, 20.0, 86.1, 4.5, '上海市外高桥大道', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 16 DAY), '08:34:00')),
(16, 1, 31.1996, 121.6058, 6.4, 20.0, 85.2, 4.5, '上海市外环路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 16 DAY), '08:37:00')),
(16, 1, 31.2006, 121.6095, 6.4, 20.0, 72.5, 4.5, '上海市申江路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 16 DAY), '08:38:00')),
(16, 1, 31.2016, 121.6098, 6.4, 20.0, 14.4, 4.5, '上海市科苑路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 16 DAY), '08:38:00')),
(16, 1, 31.2021, 121.6087, 6.4, 0.0, 298.0, 5.5, '上海市张江高科（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 16 DAY), '08:39:00')),
(17, 3, 31.1985, 121.5889, 6.4, 0.0, 265.4, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:00:00')),
(17, 3, 31.1978, 121.5794, 6.4, 38.0, 265.4, 4.5, '上海市合庆路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:02:00')),
(17, 3, 31.1972, 121.5700, 6.4, 38.0, 265.4, 4.5, '上海市高科西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:04:00')),
(17, 3, 31.1963, 121.5600, 6.4, 38.0, 264.3, 4.5, '上海市浦东南路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:05:00')),
(17, 3, 31.1955, 121.5500, 6.4, 38.0, 264.3, 4.5, '上海市浦东南路（慢速区）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:07:00')),
(17, 3, 31.1948, 121.5400, 6.4, 38.0, 265.7, 4.5, '上海市商城路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:08:00')),
(17, 3, 31.1942, 121.5300, 6.4, 38.0, 265.7, 4.5, '上海市南浦大桥', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:10:00')),
(17, 3, 31.1937, 121.5225, 6.4, 20.0, 265.5, 4.5, '上海市过桥中', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:11:00')),
(17, 3, 31.1932, 121.5150, 6.4, 20.0, 265.5, 4.5, '上海市徐汇宛平南路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:13:00')),
(17, 3, 31.1925, 121.5050, 6.4, 38.0, 265.3, 4.5, '上海市龙华西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:15:00')),
(17, 3, 31.1918, 121.4960, 6.4, 38.0, 265.2, 4.5, '上海市龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:17:00')),
(17, 3, 31.1912, 121.4870, 6.4, 38.0, 265.2, 4.5, '上海市龙华西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:18:00')),
(17, 3, 31.1903, 121.4790, 6.4, 38.0, 262.9, 4.5, '上海市龙华西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:19:00')),
(17, 3, 31.1895, 121.4710, 6.4, 38.0, 262.9, 4.5, '上海市龙华西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:21:00')),
(17, 3, 31.1882, 121.4634, 6.4, 38.0, 258.3, 4.5, '上海市龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:22:00')),
(17, 3, 31.1868, 121.4558, 6.4, 38.0, 258.3, 4.5, '上海市龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:23:00')),
(17, 3, 31.1842, 121.4453, 6.4, 38.0, 253.9, 4.5, '上海市龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:25:00')),
(17, 3, 31.1820, 121.4380, 6.4, 0.0, 250.6, 5.5, '上海市龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 17 DAY), '09:27:00')),
(18, 2, 31.1985, 121.5889, 6.4, 0.0, 267.4, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '12:30:00')),
(18, 2, 31.1981, 121.5785, 6.4, 28.0, 267.4, 4.5, '上海市合庆路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '12:34:00')),
(18, 2, 31.1977, 121.5680, 6.4, 28.0, 267.4, 4.5, '上海市浦东南路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '12:36:00')),
(18, 2, 31.1972, 121.5570, 6.4, 28.0, 267.0, 4.5, '上海市延安路隧道浦东', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '12:38:00')),
(18, 2, 31.1967, 121.5460, 6.4, 28.0, 267.0, 4.5, '上海市延安路隧道出口', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '12:40:00')),
(18, 2, 31.1964, 121.5350, 6.4, 28.0, 267.9, 4.5, '上海市延安中路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '12:43:00')),
(18, 2, 31.1960, 121.5240, 6.4, 10.0, 267.9, 4.5, '上海市延安西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '12:46:00')),
(18, 2, 31.1969, 121.5151, 6.4, 10.0, 276.7, 4.5, '上海市华山路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '12:51:00')),
(18, 2, 31.1978, 121.5062, 6.4, 10.0, 276.7, 4.5, '上海市近南京西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '12:56:00')),
(18, 2, 31.2002, 121.4972, 6.4, 28.0, 287.0, 4.5, '上海市静安南京西路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '12:59:00')),
(18, 2, 31.2025, 121.4882, 6.4, 28.0, 287.0, 4.5, '上海市近南京西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '13:01:00')),
(18, 2, 31.2063, 121.4802, 6.4, 28.0, 298.7, 4.5, '上海市近南京西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '13:03:00')),
(18, 2, 31.2100, 121.4722, 6.4, 28.0, 298.7, 4.5, '上海市近南京西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '13:05:00')),
(18, 2, 31.2140, 121.4659, 6.4, 28.0, 306.4, 4.5, '上海市近南京西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '13:06:00')),
(18, 2, 31.2180, 121.4595, 6.4, 28.0, 306.4, 4.5, '上海市静安南京西路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '13:08:00')),
(18, 2, 31.2216, 121.4548, 6.4, 28.0, 311.5, 4.5, '上海市静安南京西路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '13:09:00')),
(18, 2, 31.2252, 121.4500, 6.5, 28.0, 311.6, 4.5, '上海市静安南京西路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '13:10:00')),
(18, 2, 31.2289, 121.4490, 6.5, 0.0, 347.0, 5.5, '上海市静安南京西路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 18 DAY), '13:12:00')),
(19, 1, 31.1985, 121.5889, 6.4, 0.0, 268.2, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:00:00')),
(19, 1, 31.1983, 121.5794, 6.4, 20.0, 268.2, 4.5, '上海市向西', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:05:00')),
(19, 1, 31.1980, 121.5700, 6.4, 20.0, 268.2, 4.5, '上海市浦东南路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:08:00')),
(19, 1, 31.1977, 121.5595, 6.4, 20.0, 268.4, 4.5, '上海市延安路隧道', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:11:00')),
(19, 1, 31.1975, 121.5490, 6.4, 20.0, 268.4, 4.5, '上海市延安路隧道出口', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:14:00')),
(19, 1, 31.1974, 121.5380, 6.4, 20.0, 269.1, 4.5, '上海市西藏中路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:17:00')),
(19, 1, 31.1972, 121.5270, 6.4, 20.0, 269.1, 4.5, '上海市人民广场附近', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:20:00')),
(19, 1, 31.1987, 121.5185, 6.4, 6.0, 281.7, 4.5, '上海市近南京东路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:24:00')),
(19, 1, 31.2002, 121.5100, 6.4, 6.0, 281.7, 4.5, '上海市黄浦南京东路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:32:00')),
(19, 1, 31.2045, 121.5034, 6.4, 20.0, 307.3, 4.5, '上海市近南京东路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:36:00')),
(19, 1, 31.2088, 121.4968, 6.4, 20.0, 307.3, 4.5, '上海市近南京东路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:38:00')),
(19, 1, 31.2140, 121.4926, 6.4, 20.0, 325.7, 4.5, '上海市近南京东路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:40:00')),
(19, 1, 31.2192, 121.4885, 6.4, 20.0, 325.7, 4.5, '上海市黄浦南京东路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:42:00')),
(19, 1, 31.2278, 121.4832, 6.5, 20.0, 332.2, 4.5, '上海市黄浦南京东路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:45:00')),
(19, 1, 31.2321, 121.4800, 6.5, 0.0, 327.5, 5.5, '上海市黄浦南京东路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 19 DAY), '07:49:00')),
(20, 3, 31.1985, 121.5889, 6.4, 0.0, 319.8, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:00:00')),
(20, 3, 31.2065, 121.5810, 6.4, 28.0, 319.8, 4.5, '上海市东方路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:04:00')),
(20, 3, 31.2110, 121.5760, 6.4, 28.0, 316.5, 4.5, '上海市张杨路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:06:00')),
(20, 3, 31.2155, 121.5710, 6.4, 28.0, 316.5, 4.5, '上海市昌里路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:07:00')),
(20, 3, 31.2200, 121.5660, 6.4, 28.0, 316.5, 4.5, '上海市源深路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:09:00')),
(20, 3, 31.2245, 121.5610, 6.4, 28.0, 316.5, 4.5, '上海市崂山路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:10:00')),
(20, 3, 31.2290, 121.5560, 6.5, 28.0, 316.5, 4.5, '上海市杨浦大桥', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:12:00')),
(20, 3, 31.2335, 121.5510, 6.5, 28.0, 316.5, 4.5, '上海市杨浦区进入', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:13:00')),
(20, 3, 31.2380, 121.5449, 6.5, 28.0, 310.8, 4.5, '上海市杨浦（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:15:00')),
(20, 3, 31.2425, 121.5388, 6.5, 28.0, 310.8, 4.5, '上海市杨浦区进入', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:17:00')),
(20, 3, 31.2458, 121.5301, 6.5, 28.0, 294.4, 4.5, '上海市杨浦区进入', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:19:00')),
(20, 3, 31.2492, 121.5215, 6.5, 28.0, 294.4, 4.5, '上海市杨浦（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:21:00')),
(20, 3, 31.2535, 121.5118, 6.5, 28.0, 297.4, 4.5, '上海市杨浦（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:23:00')),
(20, 3, 31.2550, 121.5050, 6.5, 0.0, 284.5, 5.5, '上海市杨浦（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '11:26:00')),
(21, 2, 31.1985, 121.5889, 6.4, 0.0, 86.1, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 21 DAY), '13:30:00')),
(21, 2, 31.1990, 121.5975, 6.4, 38.0, 86.1, 4.5, '上海市外高桥大道', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 21 DAY), '13:32:00')),
(21, 2, 31.1996, 121.6058, 6.4, 38.0, 85.2, 4.5, '上海市外环路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 21 DAY), '13:33:00')),
(21, 2, 31.2006, 121.6095, 6.4, 38.0, 72.5, 4.5, '上海市申江路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 21 DAY), '13:34:00')),
(21, 2, 31.2016, 121.6098, 6.4, 38.0, 14.4, 4.5, '上海市科苑路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 21 DAY), '13:34:00')),
(21, 2, 31.2021, 121.6087, 6.4, 0.0, 298.0, 5.5, '上海市张江高科（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 21 DAY), '13:34:00')),
(22, 1, 31.1985, 121.5889, 6.4, 0.0, 85.7, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 22 DAY), '17:30:00')),
(22, 1, 31.1992, 121.5998, 6.4, 14.0, 85.7, 4.5, '上海市外环路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 22 DAY), '17:38:00')),
(22, 1, 31.2004, 121.6108, 6.4, 14.0, 82.7, 4.5, '上海市浦东大道金桥段', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 22 DAY), '17:43:00')),
(23, 2, 31.1985, 121.5889, 6.4, 0.0, 266.8, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:00:00')),
(23, 2, 31.1980, 121.5785, 6.4, 38.0, 266.8, 4.5, '上海市向西', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:03:00')),
(23, 2, 31.1975, 121.5680, 6.4, 38.0, 266.8, 4.5, '上海市浦东南路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:04:00')),
(23, 2, 31.1970, 121.5570, 6.4, 38.0, 267.0, 4.5, '上海市延安路隧道浦东', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:06:00')),
(23, 2, 31.1965, 121.5460, 6.4, 38.0, 267.0, 4.5, '上海市延安路隧道出口', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:08:00')),
(23, 2, 31.1961, 121.5350, 6.4, 38.0, 267.9, 4.5, '上海市延安中路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:09:00')),
(23, 2, 31.1958, 121.5240, 6.4, 20.0, 267.9, 4.5, '上海市延安西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:11:00')),
(23, 2, 31.1966, 121.5145, 6.4, 20.0, 276.0, 4.5, '上海市陕西南路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:14:00')),
(23, 2, 31.1975, 121.5050, 6.4, 38.0, 276.0, 4.5, '上海市大丰路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:16:00')),
(23, 2, 31.1996, 121.4959, 6.4, 38.0, 285.4, 4.5, '上海市共和新路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:17:00')),
(23, 2, 31.2018, 121.4868, 6.4, 38.0, 285.4, 4.5, '上海市静安大宁路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:19:00')),
(23, 2, 31.2043, 121.4789, 6.4, 38.0, 290.3, 4.5, '上海市共和新路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:20:00')),
(23, 2, 31.2068, 121.4710, 6.4, 38.0, 290.3, 4.5, '上海市共和新路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:21:00')),
(23, 2, 31.2118, 121.4618, 6.4, 38.0, 302.4, 4.5, '上海市共和新路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:23:00')),
(23, 2, 31.2178, 121.4592, 6.4, 38.0, 339.7, 4.5, '上海市静安大宁路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:24:00')),
(23, 2, 31.2238, 121.4572, 6.4, 38.0, 344.1, 4.5, '上海市静安大宁路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:25:00')),
(23, 2, 31.2280, 121.4560, 6.5, 0.0, 346.3, 5.5, '上海市静安大宁路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 23 DAY), '09:27:00')),
(24, 3, 31.1985, 121.5889, 6.4, 0.0, 266.5, 5.5, '上海市仓库出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:00:00')),
(24, 3, 31.1980, 121.5794, 6.4, 38.0, 266.5, 4.5, '上海市合庆路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:02:00')),
(24, 3, 31.1975, 121.5700, 6.4, 38.0, 266.5, 4.5, '上海市浦东南路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:04:00')),
(24, 3, 31.1970, 121.5600, 6.4, 38.0, 266.7, 4.5, '上海市延安路隧道浦东', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:05:00')),
(24, 3, 31.1965, 121.5500, 6.4, 38.0, 266.7, 4.5, '上海市延安路隧道出口', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:07:00')),
(24, 3, 31.1962, 121.5390, 6.4, 38.0, 268.5, 4.5, '上海市延安中路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:08:00')),
(24, 3, 31.1960, 121.5280, 6.4, 38.0, 268.5, 4.5, '上海市延安西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:10:00')),
(24, 3, 31.1969, 121.5180, 6.4, 20.0, 276.0, 4.5, '上海市曹杨路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:12:00')),
(24, 3, 31.1978, 121.5080, 6.4, 20.0, 276.0, 4.5, '上海市长寿路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:15:00')),
(24, 3, 31.2009, 121.4990, 6.4, 38.0, 291.9, 4.5, '上海市云岭东路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:17:00')),
(24, 3, 31.2040, 121.4900, 6.4, 38.0, 291.9, 4.5, '上海市普陀（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:18:00')),
(24, 3, 31.2078, 121.4809, 6.4, 38.0, 295.7, 4.5, '上海市云岭东路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:20:00')),
(24, 3, 31.2115, 121.4718, 6.4, 38.0, 295.7, 4.5, '上海市云岭东路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:21:00')),
(24, 3, 31.2155, 121.4629, 6.4, 38.0, 297.7, 4.5, '上海市云岭东路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:23:00')),
(24, 3, 31.2195, 121.4540, 6.4, 38.0, 297.7, 4.5, '上海市云岭东路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:24:00')),
(24, 3, 31.2236, 121.4452, 6.4, 38.0, 299.0, 4.5, '上海市云岭东路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:26:00')),
(24, 3, 31.2278, 121.4365, 6.5, 38.0, 299.0, 4.5, '上海市普陀（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:27:00')),
(24, 3, 31.2318, 121.4282, 6.5, 38.0, 299.6, 4.5, '上海市普陀（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:29:00')),
(24, 3, 31.2358, 121.4200, 6.5, 38.0, 299.6, 4.5, '上海市普陀（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:30:00')),
(24, 3, 31.2389, 121.4125, 6.5, 38.0, 295.8, 4.5, '上海市普陀（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:31:00')),
(24, 3, 31.2420, 121.4050, 6.5, 0.0, 295.8, 5.5, '上海市普陀（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 24 DAY), '16:34:00')),
(25, 1, 31.0928, 121.4536, 6.2, 0.0, 357.6, 5.5, '上海市闵行仓出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:00:00')),
(25, 1, 31.1042, 121.4530, 6.2, 38.0, 357.6, 4.5, '上海市元江路北段', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:03:00')),
(25, 1, 31.1155, 121.4525, 6.2, 38.0, 357.6, 4.5, '上海市中春路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:05:00')),
(25, 1, 31.1270, 121.4530, 6.3, 38.0, 2.1, 4.5, '上海市中环漕宝路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:08:00')),
(25, 1, 31.1385, 121.4535, 6.3, 38.0, 2.1, 4.5, '上海市华泾', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:10:00')),
(25, 1, 31.1498, 121.4572, 6.3, 38.0, 15.5, 4.5, '上海市卢浦大桥附近', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:12:00')),
(25, 1, 31.1610, 121.4608, 6.3, 38.0, 15.5, 4.5, '上海市进入黄浦', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:14:00')),
(25, 1, 31.1718, 121.4680, 6.3, 38.0, 29.8, 4.5, '上海市近陆家嘴', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:16:00')),
(25, 1, 31.1825, 121.4752, 6.4, 38.0, 29.8, 4.5, '上海市陆家嘴（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:18:00')),
(25, 1, 31.1928, 121.4832, 6.4, 38.0, 33.5, 4.5, '上海市近陆家嘴', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:20:00')),
(25, 1, 31.2032, 121.4912, 6.4, 38.0, 33.5, 4.5, '上海市近陆家嘴', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:22:00')),
(25, 1, 31.2090, 121.4959, 6.4, 38.0, 34.4, 4.5, '上海市近陆家嘴', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:24:00')),
(25, 1, 31.2148, 121.5005, 6.4, 38.0, 34.4, 4.5, '上海市陆家嘴（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:25:00')),
(25, 1, 31.2203, 121.5019, 6.4, 38.0, 11.9, 4.5, '上海市陆家嘴（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:26:00')),
(25, 1, 31.2258, 121.5032, 6.5, 38.0, 11.9, 4.5, '上海市陆家嘴（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:27:00')),
(25, 1, 31.2356, 121.5050, 6.5, 0.0, 8.9, 5.5, '上海市陆家嘴（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 25 DAY), '09:30:00')),
(26, 2, 31.0928, 121.4536, 6.2, 0.0, 348.0, 5.5, '上海市闵行仓出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '14:30:00')),
(26, 2, 31.1016, 121.4514, 6.2, 38.0, 348.0, 4.5, '上海市沪闵路北段', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '14:33:00')),
(26, 2, 31.1105, 121.4492, 6.2, 38.0, 348.0, 4.5, '上海市龙吴路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '14:34:00')),
(26, 2, 31.1217, 121.4472, 6.2, 38.0, 351.3, 4.5, '上海市徐汇区南段', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '14:36:00')),
(26, 2, 31.1330, 121.4452, 6.3, 38.0, 351.3, 4.5, '上海市龙华西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '14:38:00')),
(26, 2, 31.1444, 121.4442, 6.3, 38.0, 355.7, 4.5, '上海市徐汇龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '14:40:00')),
(26, 2, 31.1558, 121.4432, 6.3, 38.0, 355.7, 4.5, '上海市龙华西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '14:42:00')),
(26, 2, 31.1641, 121.4426, 6.3, 38.0, 356.5, 4.5, '上海市龙华西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '14:44:00')),
(26, 2, 31.1725, 121.4420, 6.3, 38.0, 356.5, 4.5, '上海市徐汇龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '14:45:00')),
(26, 2, 31.1820, 121.4380, 6.4, 0.0, 340.2, 5.5, '上海市徐汇龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 26 DAY), '14:49:00')),
(27, 3, 31.0928, 121.4536, 6.2, 0.0, 20.2, 5.5, '上海市闵行仓出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '10:30:00')),
(27, 3, 31.1067, 121.4596, 6.2, 38.0, 20.2, 4.5, '上海市中环北段', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '10:35:00')),
(27, 3, 31.1205, 121.4655, 6.2, 38.0, 20.2, 4.5, '上海市徐汇-黄浦界', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '10:37:00')),
(27, 3, 31.1307, 121.4707, 6.3, 38.0, 23.6, 4.5, '上海市进入黄浦', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '10:39:00')),
(27, 3, 31.1410, 121.4760, 6.3, 38.0, 23.6, 4.5, '上海市浦东南路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '10:41:00')),
(27, 3, 31.1512, 121.4812, 6.3, 38.0, 23.6, 4.5, '上海市向东', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '10:43:00')),
(27, 3, 31.1616, 121.4872, 6.3, 38.0, 26.2, 4.5, '上海市外环附近', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '10:45:00')),
(27, 3, 31.1721, 121.4932, 6.3, 38.0, 26.2, 4.5, '上海市张江方向', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '10:47:00')),
(27, 3, 31.1825, 121.4992, 6.4, 38.0, 26.2, 4.5, '上海市科苑路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '10:49:00')),
(27, 3, 31.1917, 121.5100, 6.4, 20.0, 45.0, 4.5, '上海市张江高科（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '10:52:00')),
(27, 3, 31.2010, 121.5208, 6.4, 20.0, 45.0, 4.5, '上海市科苑路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '10:57:00')),
(27, 3, 31.2005, 121.5354, 6.4, 38.0, 92.3, 4.5, '上海市科苑路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '11:00:00')),
(27, 3, 31.2000, 121.5500, 6.4, 38.0, 92.3, 4.5, '上海市科苑路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '11:02:00')),
(27, 3, 31.1999, 121.5654, 6.4, 38.0, 90.4, 4.5, '上海市科苑路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '11:04:00')),
(27, 3, 31.1998, 121.5808, 6.4, 38.0, 90.4, 4.5, '上海市张江高科（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '11:06:00')),
(27, 3, 31.2002, 121.5908, 6.4, 38.0, 87.7, 4.5, '上海市张江高科（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '11:08:00')),
(27, 3, 31.2005, 121.6008, 6.4, 38.0, 87.7, 4.5, '上海市张江高科（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '11:09:00')),
(27, 3, 31.2016, 121.6065, 6.4, 38.0, 77.3, 4.5, '上海市张江高科（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '11:10:00')),
(27, 3, 31.2021, 121.6087, 6.4, 0.0, 75.1, 5.5, '上海市张江高科（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 27 DAY), '11:11:00')),
(28, 3, 31.3988, 121.4312, 6.8, 0.0, 159.6, 5.5, '上海市宝山仓出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:00:00')),
(28, 3, 31.3847, 121.4374, 6.8, 38.0, 159.6, 4.5, '上海市逸仙路南段', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:05:00')),
(28, 3, 31.3705, 121.4435, 6.7, 38.0, 159.6, 4.5, '上海市共和新路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:07:00')),
(28, 3, 31.3557, 121.4496, 6.7, 38.0, 160.5, 4.5, '上海市内环沪太', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:10:00')),
(28, 3, 31.3408, 121.4558, 6.7, 38.0, 160.5, 4.5, '上海市中山北路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:13:00')),
(28, 3, 31.3308, 121.4614, 6.7, 38.0, 154.6, 4.5, '上海市近杨浦大桥浦西侧', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:15:00')),
(28, 3, 31.3208, 121.4669, 6.6, 38.0, 154.6, 4.5, '上海市杨浦（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:17:00')),
(28, 3, 31.3108, 121.4725, 6.6, 38.0, 154.6, 4.5, '上海市近杨浦大桥浦西侧', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:19:00')),
(28, 3, 31.3009, 121.4781, 6.6, 38.0, 154.3, 4.5, '上海市近杨浦大桥浦西侧', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:21:00')),
(28, 3, 31.2911, 121.4836, 6.6, 38.0, 154.3, 4.5, '上海市近杨浦大桥浦西侧', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:23:00')),
(28, 3, 31.2812, 121.4892, 6.6, 38.0, 154.3, 4.5, '上海市杨浦（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:25:00')),
(28, 3, 31.2710, 121.4940, 6.5, 38.0, 158.1, 4.5, '上海市杨浦（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:27:00')),
(28, 3, 31.2608, 121.4988, 6.5, 38.0, 158.1, 4.5, '上海市杨浦（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:28:00')),
(28, 3, 31.2550, 121.5050, 6.5, 0.0, 137.6, 5.5, '上海市杨浦（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 28 DAY), '10:31:00')),
(29, 2, 31.3988, 121.4312, 6.8, 0.0, 196.3, 5.5, '上海市宝山仓出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '13:00:00')),
(29, 2, 31.3847, 121.4263, 6.8, 38.0, 196.3, 4.5, '上海市向南', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '13:05:00')),
(29, 2, 31.3705, 121.4215, 6.7, 38.0, 196.3, 4.5, '上海市沪太路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '13:07:00')),
(29, 2, 31.3557, 121.4161, 6.7, 38.0, 197.1, 4.5, '上海市中环西段', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '13:10:00')),
(29, 2, 31.3408, 121.4108, 6.7, 38.0, 197.1, 4.5, '上海市继续南下', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '13:13:00')),
(29, 2, 31.3258, 121.4050, 6.7, 38.0, 198.3, 4.5, '上海市外环高速', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '13:15:00')),
(29, 2, 31.3108, 121.3992, 6.6, 38.0, 198.3, 4.5, '上海市长宁区', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '13:18:00')),
(29, 2, 31.2958, 121.3944, 6.6, 38.0, 195.4, 4.5, '上海市虹桥（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '13:21:00')),
(29, 2, 31.2808, 121.3895, 6.6, 38.0, 195.4, 4.5, '上海市长宁区', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '13:24:00')),
(29, 2, 31.2658, 121.3828, 6.5, 38.0, 200.8, 4.5, '上海市长宁区', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '13:27:00')),
(29, 2, 31.2508, 121.3762, 6.5, 38.0, 200.8, 4.5, '上海市长宁区', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '13:29:00')),
(29, 2, 31.2408, 121.3714, 6.5, 38.0, 202.3, 4.5, '上海市长宁区', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '13:31:00')),
(29, 2, 31.2308, 121.3666, 6.5, 38.0, 202.3, 4.5, '上海市虹桥（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '13:33:00')),
(29, 2, 31.2208, 121.3618, 6.4, 38.0, 202.3, 4.5, '上海市虹桥（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '13:35:00')),
(29, 2, 31.2084, 121.3539, 6.4, 38.0, 208.6, 4.5, '上海市虹桥（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '13:38:00')),
(29, 2, 31.1960, 121.3460, 6.4, 0.0, 208.6, 5.5, '上海市虹桥（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 29 DAY), '13:42:00')),
(30, 3, 31.3988, 121.4312, 6.8, 0.0, 159.6, 5.5, '上海市宝山仓出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 30 DAY), '17:00:00')),
(30, 3, 31.3847, 121.4374, 6.8, 14.0, 159.6, 4.5, '上海市逸仙路南段', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 30 DAY), '17:14:00')),
(30, 3, 31.3705, 121.4435, 6.7, 14.0, 159.6, 4.5, '上海市共和新路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 30 DAY), '17:21:00')),
(30, 3, 31.3557, 121.4496, 6.7, 14.0, 160.5, 4.5, '上海市内环沪太', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 30 DAY), '17:29:00')),
(30, 3, 31.3408, 121.4558, 6.7, 14.0, 160.5, 4.5, '上海市中山北路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 30 DAY), '17:36:00')),
(30, 3, 31.3308, 121.4614, 6.7, 14.0, 154.6, 4.5, '上海市近杨浦大桥浦西侧', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 30 DAY), '17:41:00')),
(30, 3, 31.3208, 121.4669, 6.6, 14.0, 154.6, 4.5, '上海市杨浦（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 30 DAY), '17:47:00')),
(31, 1, 31.3988, 121.4312, 6.8, 0.0, 184.8, 5.5, '上海市宝山仓出发', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '09:30:00')),
(31, 1, 31.3837, 121.4297, 6.8, 38.0, 184.8, 4.5, '上海市逸仙路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '09:35:00')),
(31, 1, 31.3685, 121.4282, 6.7, 38.0, 184.8, 4.5, '上海市共和新路南段', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '09:38:00')),
(31, 1, 31.3574, 121.4256, 6.7, 38.0, 191.2, 4.5, '上海市中山北路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '09:39:00')),
(31, 1, 31.3463, 121.4231, 6.7, 38.0, 191.2, 4.5, '上海市内环高架', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '09:41:00')),
(31, 1, 31.3352, 121.4205, 6.7, 38.0, 191.2, 4.5, '上海市中山西路', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '09:43:00')),
(31, 1, 31.3243, 121.4198, 6.6, 38.0, 183.0, 4.5, '上海市近徐汇', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '09:45:00')),
(31, 1, 31.3134, 121.4192, 6.6, 38.0, 183.0, 4.5, '上海市徐汇龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '09:47:00')),
(31, 1, 31.3025, 121.4185, 6.6, 38.0, 183.0, 4.5, '上海市近徐汇', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '09:49:00')),
(31, 1, 31.2912, 121.4202, 6.6, 38.0, 172.8, 4.5, '上海市近徐汇', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '09:51:00')),
(31, 1, 31.2798, 121.4218, 6.6, 38.0, 172.8, 4.5, '上海市近徐汇', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '09:53:00')),
(31, 1, 31.2685, 121.4235, 6.5, 38.0, 172.8, 4.5, '上海市近徐汇', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '09:55:00')),
(31, 1, 31.2574, 121.4263, 6.5, 38.0, 168.0, 4.5, '上海市近徐汇', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '09:57:00')),
(31, 1, 31.2463, 121.4290, 6.5, 38.0, 168.0, 4.5, '上海市近徐汇', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '09:59:00')),
(31, 1, 31.2352, 121.4318, 6.5, 38.0, 168.0, 4.5, '上海市徐汇龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '10:01:00')),
(31, 1, 31.2243, 121.4331, 6.4, 38.0, 174.0, 4.5, '上海市徐汇龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '10:03:00')),
(31, 1, 31.2134, 121.4345, 6.4, 38.0, 174.0, 4.5, '上海市徐汇龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '10:05:00')),
(31, 1, 31.2025, 121.4358, 6.4, 38.0, 174.0, 4.5, '上海市徐汇龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '10:07:00')),
(31, 1, 31.1923, 121.4369, 6.4, 38.0, 174.8, 4.5, '上海市徐汇龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '10:09:00')),
(31, 1, 31.1820, 121.4380, 6.4, 0.0, 174.8, 5.5, '上海市徐汇龙华路（到达）', TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 31 DAY), '10:12:00'));

-- ══════════════════════════════════════════════════════════
-- 数据统计
-- 新增轨迹点: 434 条（+ 已有 route_id=1 的 3 条 = 437 条总计）
-- 新增路线: 30 条（route_id 2-31）
-- 新增订单: 30 条（order_id 3-32）
-- 新增用户: 4 条（user_id 6-9）
-- ══════════════════════════════════════════════════════════