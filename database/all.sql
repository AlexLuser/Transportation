SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

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
('shop_bj', '$2a$10$.ckJuQWWC9dUh0hOa7v4LuxBa6PICggDyUUz7awFv4CM/rh7tQZ0a', 3),   -- 华北商户（密码：123456，MCMF 京仓发全国测试）
('driver', '$2a$10$.ckJuQWWC9dUh0hOa7v4LuxBa6PICggDyUUz7awFv4CM/rh7tQZ0a', 4);     -- 运输员（密码：123456）

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
(1, '张三', '13800138000', '上海市', '上海市', '静安区', '静安区南京西路688号', '200041', 0, 31.2289, 121.4490);

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
  `order_id` BIGINT DEFAULT NULL COMMENT '关联order_info表的id（干线路线为NULL）',
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
  -- Hub-and-Spoke 扩展字段
  `segment_type` TINYINT NOT NULL DEFAULT 0 COMMENT '配送段类型：0=完整单订单，1=干线（仓库→Hub），2=末端（Hub→客户）',
  `batch_id`     BIGINT  DEFAULT NULL COMMENT '所属批次ID',
  `hub_id`       BIGINT  DEFAULT NULL COMMENT '中转站ID（干线司机的目标Hub）',
  `route_id`     BIGINT  DEFAULT NULL COMMENT '关联物流路线ID（干线路线接单时绑定，末端/普通路线通过orderId查找）',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_order_id` (`order_id`),
  INDEX `idx_driver_id` (`driver_id`),
  INDEX `idx_delivery_status` (`delivery_status`),
  INDEX `idx_batch_id` (`batch_id`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单配送表';

-- ============================================================
-- 测试数据
-- user 表顺序：… shop_bj=id=5，driver=id=6。以下 driver_info.user_id 必须为 6。
-- ============================================================

-- 运输员信息（driver_id=1，即物流路线中绑定的运输员）
-- 注意：电话使用 13700137000，与 shop_info 的电话区分，避免混淆
INSERT INTO `driver_info`
  (`user_id`, `real_name`, `phone`, `email`, `id_card`, `gender`, `birthday`,
   `license_number`, `license_type`, `license_expire_date`, `status`)
VALUES
(6, '李四', '13700137000', 'lisi@example.com', '310101199001011234', 1, '1990-01-01',
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
  `affiliated_hub_id` BIGINT DEFAULT NULL COMMENT '归属全国城市级配送中心ID（关联 national_hub.id）',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0=禁用，1=启用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_warehouse_name` (`warehouse_name`),
  INDEX `idx_status` (`status`),
  INDEX `idx_affiliated_hub_id` (`affiliated_hub_id`)
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
-- user_id=3 → shop（优质食品店），user_id=4 → shop2（时尚服装店），user_id=5 → shop_bj（京北优品铺）
-- ============================================================

-- 商户信息（shop_id=1 为物流测试所用商户；shop_id=3 为华北仓配商户，须绑定 user.id=5 即 shop_bj）
INSERT INTO `shop_info` (`user_id`, `shop_name`, `shop_phone`, `shop_email`, `description`, `business_license`, `status`) VALUES
(3, '优质食品店', '021-55001234', 'shop1@example.com', '专业销售优质食品，品质保证', '91310000123456789X', 1),
(4, '时尚服装店', '021-55005678', 'shop2@example.com', '时尚潮流服装，款式新颖',     '91310000123456789Y', 1),
(5, '京北优品铺', '010-62001200', 'shopbj@example.com', '华北食品饮料仓配一体，北京仓发货、支持全国配送', '91110100MA00000123', 1);

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
 '["/images/nut_gift_box.png"]', 1, 26),
(1, 4, '有机果汁',       'SP002', '100%纯天然有机果汁，无添加',           35.00,  45.00,  '瓶', 0.5,
 '["/images/organic_juice.jpg"]', 1, 51),
(2, 5, '商务休闲衬衫',   'SP003', '高品质商务休闲衬衫，多色可选',         299.00, 399.00, '件', 0.3,
 '["/images/business_shirt.jpg"]', 1, 15),
(2, 6, '时尚连衣裙',     'SP004', '春季新款时尚连衣裙，优雅大方',         399.00, 499.00, '件', 0.4,
 '["/images/fashion_dress.jpg"]', 1, 8),
(3, 3, '进京坚果礼盒',   'SP101', '京仓发货坚果礼盒，与上海仓商品独立库存', 128.00, 158.00, '盒', 1.5,
 '["/images/beijing_nut_gift_box.jpg"]', 1, 0),
(3, 4, '进京有机果汁',   'SP102', '京仓发货有机果汁，便于沪京双向物流演示', 35.00,  45.00,  '瓶', 0.5,
 '["/images/beijing_organic_juice.jpg"]', 1, 0);

-- 仓库信息（上海三仓与本地 OSM 一致；北京仓为真实种子数据，归属 national_hub 北京配送中心 id=4，用于全国/MCMF 演示）
-- ★ warehouse_id=2（上海华东仓库）为物流测试主用仓库，lat=31.1985, longitude=121.5889
-- ★ warehouse_id=4（北京大兴履约中心）归属京北优品铺（shop_id=3），affiliated_hub_id=4
INSERT INTO `warehouse`
  (`warehouse_name`, `warehouse_phone`, `province`, `city`, `district`, `detail_address`, `postal_code`, `capacity`, `latitude`, `longitude`, `affiliated_hub_id`, `status`)
VALUES
('上海闵行分拨仓', '021-51001001', '上海市', '上海市', '闵行区',   '闵行区元江路426号物流园1号库', '201111', 10000, 31.0928, 121.4536, NULL, 1),
('上海华东仓库',   '021-87654321', '上海市', '上海市', '浦东新区', '浦东新区物流园区B区2号',     '200135',  8000, 31.1985, 121.5889, NULL, 1),  -- ★ 物流测试使用（Hub 由下方 UPDATE 写入）
('上海宝山分拨仓', '021-51001003', '上海市', '上海市', '宝山区',   '宝山区富锦路1500号物流园C库', '200444',  6000, 31.3988, 121.4312, NULL, 1),
('北京大兴履约中心', '010-62001201', '北京市', '北京市', '大兴区', '大兴区京南物流园8号库（京北优品铺）', '102600', 8000, 39.6522, 116.3414, 4, 1);

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
(3, 4, 10),   -- 时尚连衣裙，库存10
-- 仓库4（北京大兴履约中心，京北优品铺 shop_id=3 商品）
(4, 5, 800),  -- 进京坚果礼盒
(4, 6, 1200); -- 进京有机果汁（京→沪/MCMF 测试主用）

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
  `warehouse_id` BIGINT DEFAULT NULL COMMENT '发货仓库ID（商户选择的发货仓库，物流路线起点）',
  `origin_hub_id` BIGINT DEFAULT NULL COMMENT '发货城市Hub ID（发货时由 ShipmentRoutingService 分配）',
  `dest_hub_id` BIGINT DEFAULT NULL COMMENT '收货城市Hub ID（发货时由 ShipmentRoutingService 分配）',
  `flow_plan_id` BIGINT DEFAULT NULL COMMENT 'MCMF流量规划单ID（跨城订单分配到干线批次时写入）',
  `inter_city_batch_id` BIGINT DEFAULT NULL COMMENT '跨城干线批次ID（分配到干线批次时写入）',
  `planned_path` VARCHAR(500) DEFAULT NULL COMMENT '规划的完整Hub路径（JSON数组：[hubId1,hubId2,...]，用于多跳干线自动衔接）',
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
  INDEX `idx_origin_hub_id` (`origin_hub_id`),
  INDEX `idx_dest_hub_id` (`dest_hub_id`),
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
(1, 1, '优质坚果礼盒', '/images/nut_gift_box.png',   128.00, 1, 128.00),
(1, 2, '有机果汁',     '/images/organic_juice.jpg',  35.00, 1,  35.00);

-- order_id=2：有机果汁（待发货，可通过 POST /api/logistics/routes 创建路线）
INSERT INTO `order_item`
  (`order_id`, `product_id`, `product_name`, `product_image`, `product_price`, `quantity`, `subtotal`)
VALUES
(2, 2, '有机果汁', '/images/organic_juice.jpg', 35.00, 1, 35.00);

-- 若现有库仍为旧枚举（3=已完成、4=已取消），可执行下面一条迁移后再启动新代码：
-- UPDATE `order_info` SET `order_status` = `order_status` + 1 WHERE `order_status` >= 3;
-- ============================================================
-- 物流调度服务数据库表
-- 模块：路径规划
-- 包含：物流路线表、里程碑节点表、实时轨迹表
-- ============================================================

-- ============================================================
-- Hub-and-Spoke 物流扩展表
-- ============================================================

-- 按依赖顺序删除旧表（含新增表）
DROP TABLE IF EXISTS `logistics_batch_item`;
DROP TABLE IF EXISTS `logistics_batch`;
DROP TABLE IF EXISTS `logistics_hub`;
DROP TABLE IF EXISTS `logistics_node`;
DROP TABLE IF EXISTS `logistics_track`;
DROP TABLE IF EXISTS `logistics_route`;

-- ============================================================
-- 0-A. 物流中转站表
-- ============================================================
CREATE TABLE `logistics_hub` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '中转站ID',
  `name`         VARCHAR(100) NOT NULL COMMENT '中转站名称',
  `address`      VARCHAR(300) NOT NULL COMMENT '中转站地址',
  `latitude`     DOUBLE       NOT NULL COMMENT '纬度',
  `longitude`    DOUBLE       NOT NULL COMMENT '经度',
  `region`       VARCHAR(50)  DEFAULT NULL COMMENT '所属区域',
  `max_capacity` INT          DEFAULT 1000 COMMENT '最大日处理量',
  `current_load` INT          DEFAULT 0    COMMENT '当前待处理包裹数',
  `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0=正常，1=满载，2=关闭',
  `remark`       VARCHAR(300) DEFAULT NULL COMMENT '备注',
  `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_region` (`region`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流中转站表';

INSERT INTO `logistics_hub` (`name`, `address`, `latitude`, `longitude`, `region`, `max_capacity`, `status`) VALUES
('上海浦东分拨中心', '上海市浦东新区外环路6888号', 31.2180, 121.6200, '浦东', 2000, 0),
('上海虹桥分拨中心', '上海市闵行区申长路2600号',   31.1950, 121.3300, '虹桥', 1500, 0),
('上海松江分拨中心', '上海市松江区九亭镇九莘路',   31.0300, 121.2200, '松江', 1000, 0);

-- ============================================================
-- 0-B. 配送批次表
-- ============================================================
CREATE TABLE `logistics_batch` (
  `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '批次ID',
  `batch_no`        VARCHAR(30) NOT NULL UNIQUE COMMENT '批次编号（LB+时间戳+4位随机）',
  `warehouse_id`    BIGINT      NOT NULL COMMENT '发货仓库ID',
  `hub_id`          BIGINT      DEFAULT NULL COMMENT '中转站ID',
  `trunk_route_id`  BIGINT      DEFAULT NULL COMMENT '干线路线ID（segment_type=1）',
  `batch_status`    TINYINT     NOT NULL DEFAULT 0
                    COMMENT '批次状态：0=待出发，1=干线运输中，2=已到中转站，3=末端派送中，4=全部完成',
  `total_orders`    INT         NOT NULL DEFAULT 0 COMMENT '订单总数',
  `use_hub`         TINYINT     NOT NULL DEFAULT 1 COMMENT '是否经Hub中转：0=否，1=是',
  `vrp_algorithm`   VARCHAR(50) DEFAULT 'NEAREST_NEIGHBOR' COMMENT 'VRP算法名称',
  `total_distance`  DOUBLE      DEFAULT NULL COMMENT 'VRP规划总距离（米）',
  `remark`          VARCHAR(300) DEFAULT NULL,
  `create_time`     DATETIME    DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no`  (`batch_no`),
  INDEX `idx_warehouse_id`  (`warehouse_id`),
  INDEX `idx_hub_id`        (`hub_id`),
  INDEX `idx_batch_status`  (`batch_status`),
  INDEX `idx_create_time`   (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配送批次表';

-- ============================================================
-- 0-C. 批次订单明细表
-- ============================================================
CREATE TABLE `logistics_batch_item` (
  `id`             BIGINT  NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `batch_id`       BIGINT  NOT NULL COMMENT '所属批次ID',
  `order_id`       BIGINT  NOT NULL COMMENT '订单ID',
  `route_id`       BIGINT  DEFAULT NULL COMMENT '所属末端路线ID（单订单=单条route；多停靠=组route）',
  `visit_sequence` INT     NOT NULL DEFAULT 0 COMMENT '批次内全局VRP访问顺序（从1开始）',
  `stop_sequence`  INT     DEFAULT NULL COMMENT '在同组末端路线内的停靠顺序（从1开始，多停靠时有效）',
  `end_lat`        DOUBLE  DEFAULT NULL COMMENT '目的地纬度（快照，供地图展示）',
  `end_lng`        DOUBLE  DEFAULT NULL COMMENT '目的地经度（快照）',
  `end_address`    VARCHAR(300) DEFAULT NULL COMMENT '目的地地址（快照）',
  `receiver_name`  VARCHAR(50)  DEFAULT NULL COMMENT '收货人姓名（快照）',
  `receiver_phone` VARCHAR(20)  DEFAULT NULL COMMENT '收货人电话（快照）',
  `item_status`    TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0=待激活，1=末端派送中，2=已送达',
  `create_time`    DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_batch_id`  (`batch_id`),
  INDEX `idx_order_id`  (`order_id`),
  INDEX `idx_route_id`  (`route_id`),
  INDEX `idx_sequence`  (`batch_id`, `visit_sequence`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次订单明细表（每行=一个订单/一个末端停靠点）';

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
  -- 单订单路线填 orderId；多停靠末端路线（stop_count>1）为 NULL
  `order_id`              BIGINT       DEFAULT NULL COMMENT '关联订单ID（单订单路线；多停靠末端路线为NULL，停靠明细见 logistics_batch_item）',
  `delivery_id`           BIGINT       DEFAULT NULL COMMENT '关联配送记录ID（接单后绑定）',
  `driver_id`             BIGINT       DEFAULT NULL COMMENT '运输员ID（接单后绑定）',
  `warehouse_id`          BIGINT       DEFAULT NULL COMMENT '出发仓库ID',

  -- Hub-and-Spoke 扩展字段
  `batch_id`              BIGINT       DEFAULT NULL COMMENT '所属批次ID（null=单订单模式）',
  `segment_type`          TINYINT      NOT NULL DEFAULT 0
                          COMMENT '路线段类型：0=独立单订单，1=干线（仓库→Hub），2=末端（Hub→客户）',
  `hub_id`                BIGINT       DEFAULT NULL COMMENT '中转站ID（干线路线的终点Hub；末端路线的起点Hub）',

  -- 多停靠点末端路线分组信息（segment_type=2 时有效）
  `group_index`           TINYINT      DEFAULT NULL COMMENT '批次内末端分组编号（0,1,2...），同一组由一名司机负责',
  `stop_count`            INT          NOT NULL DEFAULT 1 COMMENT '本路线停靠点数量（1=单订单，>1=多停靠末端路线）',
  -- 多停靠点有序停靠列表（JSON数组）：[{seq,orderId,address,lat,lng,receiverName,receiverPhone}]
  `waypoints`             JSON         DEFAULT NULL COMMENT '多停靠点列表（stop_count>1时有效）',

  -- 出发地信息
  `start_address`         VARCHAR(300) NOT NULL COMMENT '出发地址（仓库/Hub地址快照）',
  `start_lat`             DOUBLE       DEFAULT NULL COMMENT '出发地纬度',
  `start_lng`             DOUBLE       DEFAULT NULL COMMENT '出发地经度',

  -- 目的地信息（单订单=收货地；多停靠末端=最后一个停靠点地址）
  `end_address`           VARCHAR(300) NOT NULL COMMENT '目的地址（单订单=收货地；多停靠=最后一停靠点）',
  `end_lat`               DOUBLE       DEFAULT NULL COMMENT '目的地纬度',
  `end_lng`               DOUBLE       DEFAULT NULL COMMENT '目的地经度',

  -- 实时位置（运输中持续更新）
  `current_lat`           DOUBLE       DEFAULT NULL COMMENT '当前位置纬度',
  `current_lng`           DOUBLE       DEFAULT NULL COMMENT '当前位置经度',
  `current_address`       VARCHAR(300) DEFAULT NULL COMMENT '当前位置描述',
  `last_track_time`       DATETIME     DEFAULT NULL COMMENT '最后一次位置更新时间',

  -- 状态机：-1=待激活，0=待出发，1=运输中，2=已送达，3=异常
  `route_status`          TINYINT      NOT NULL DEFAULT 0
                          COMMENT '路线状态：-1=待激活(末端路线)，0=待出发，1=运输中，2=已送达，3=异常',

  -- 时间预估
  `estimated_arrival_time` DATETIME   DEFAULT NULL COMMENT '预计到达时间',
  `actual_arrival_time`   DATETIME    DEFAULT NULL COMMENT '实际到达时间',

  -- 路线数据（GeoJSON）
  `planned_route`         LONGTEXT     DEFAULT NULL COMMENT '计划路线（GeoJSON LineString，由 GraphHopper 生成）',

  -- 收货人信息（单订单路线快照；多停靠末端路线 stop_count>1 时为NULL，详见 waypoints/batch_item）
  `receiver_name`         VARCHAR(50)  DEFAULT NULL COMMENT '收货人姓名（单订单路线；多停靠为NULL）',
  `receiver_phone`        VARCHAR(20)  DEFAULT NULL COMMENT '收货人电话（单订单路线；多停靠为NULL）',

  `remark`                VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_route_no`   (`route_no`),
  INDEX `idx_order_id`       (`order_id`),
  INDEX `idx_driver_id`      (`driver_id`),
  INDEX `idx_warehouse_id`   (`warehouse_id`),
  INDEX `idx_route_status`   (`route_status`),
  INDEX `idx_batch_id`       (`batch_id`),
  INDEX `idx_segment_type`   (`segment_type`),
  INDEX `idx_create_time`    (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流路线表（单订单/干线/多停靠末端路线共用）';


-- ============================================================
-- 2. 里程碑节点表
--    路线上的关键节点：出发点（type=0）、途经点（type=1）、目的地（type=2）
-- ============================================================
CREATE TABLE `logistics_node` (
  `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '节点ID',
  `route_id`              BIGINT       NOT NULL COMMENT '所属路线ID',
  `node_type`             TINYINT      NOT NULL COMMENT '节点类型：0=出发点，1=途经点，2=目的地，3=中转站（Hub）',
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
   `route_status`, `estimated_arrival_time`, `actual_arrival_time`,
   `planned_route`,
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
  -- 状态：运输中；预计45分钟后到达；actual_arrival_time=NULL（未送达）
  1, DATE_ADD(NOW(), INTERVAL 45 MINUTE), NULL,
  -- planned_route：GeoJSON LineString（浦东仓库→张杨路→世纪大道→陆家嘴，简化4段路线）
  '{"type":"LineString","coordinates":[[121.5889,31.1985],[121.5640,31.2100],[121.5350,31.2256],[121.5050,31.2356]]}',
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

-- 实时轨迹（route_id=1）
-- 本次运输（今日，当前时间附近）+ 历史轨迹（过去30天内不同时段，
-- 为 selectAvgSpeedByHour 提供当前小时及周边小时的基础样本量）
INSERT INTO `logistics_track`
  (`route_id`, `driver_id`, `latitude`, `longitude`,
   `altitude`, `speed`, `heading`, `accuracy`, `address`, `track_time`)
VALUES
-- ── 本次运输（当日实时轨迹，3条）──
-- 第1点：从仓库出发（speed=0，准备发车）
(1, 1, 31.1985, 121.5889, 6.0,  0.0, 340.0, 5.0,
 '上海市浦东新区物流园区B区2号',
 DATE_SUB(NOW(), INTERVAL 60 MINUTE)),
-- 第2点：途经张杨路
(1, 1, 31.2100, 121.5640, 6.2, 38.5, 332.0, 4.5,
 '上海市浦东新区张杨路附近',
 DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
-- 第3点：途经世纪大道（当前最新位置）
(1, 1, 31.2256, 121.5350, 6.5, 42.0, 320.0, 4.0,
 '上海市浦东新区世纪大道附近',
 DATE_SUB(NOW(), INTERVAL 15 MINUTE)),
-- ── 历史轨迹（过去30天内，覆盖不同小时，为 LLM 提供全天基础速度样本）──
-- 早高峰（07-08时）参考轨迹：浦东南路附近，速度偏低
(1, 1, 31.1950, 121.5150, 6.1, 12.0, 275.0, 5.0, '上海市浦东新区浦东南路',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL  3 DAY), '07:22:00')),
(1, 1, 31.1960, 121.5080, 6.1,  8.5, 268.0, 5.0, '上海市浦东南路商城路附近',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL  5 DAY), '08:10:00')),
(1, 1, 31.2002, 121.5100, 6.2, 14.0, 290.0, 4.5, '上海市延安路隧道出口',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL  7 DAY), '07:45:00')),
-- 上午平峰（09-11时）：速度较高
(1, 1, 31.2100, 121.5640, 6.2, 36.0, 332.0, 4.5, '上海市浦东新区张杨路',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL  2 DAY), '09:30:00')),
(1, 1, 31.2256, 121.5350, 6.5, 40.0, 320.0, 4.0, '上海市浦东新区世纪大道',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL  4 DAY), '10:15:00')),
(1, 1, 31.2300, 121.5200, 6.6, 38.0, 315.0, 4.0, '上海市浦东新区陆家嘴西路',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL  6 DAY), '11:00:00')),
-- 午间（12-14时）：平均速度
(1, 1, 31.2088, 121.4968, 6.4, 28.0, 310.0, 4.5, '上海市黄浦区西藏中路',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL  8 DAY), '12:30:00')),
(1, 1, 31.2192, 121.4885, 6.5, 25.0, 305.0, 4.5, '上海市黄浦区人民广场',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '13:20:00')),
-- 下午平峰（15-16时）：速度较高
(1, 1, 31.2356, 121.5050, 6.7, 42.0, 295.0, 4.0, '上海市浦东新区陆家嘴',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '15:10:00')),
(1, 1, 31.1985, 121.5889, 6.0, 39.0, 10.0,  4.0, '上海市浦东新区物流园区',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '16:00:00')),
-- 晚高峰（17-19时）：速度偏低
(1, 1, 31.1932, 121.5150, 6.1, 10.0, 275.0, 5.0, '上海市浦东南路（晚高峰）',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL  1 DAY), '17:30:00')),
(1, 1, 31.1960, 121.5280, 6.1,  6.5, 270.0, 5.5, '上海市延安路隧道浦东（拥堵）',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL  9 DAY), '18:05:00')),
(1, 1, 31.2040, 121.4900, 6.3, 15.0, 285.0, 5.0, '上海市延安中路',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '18:50:00')),
-- 夜间（20-23时）：畅通
(1, 1, 31.2195, 121.4540, 6.4, 44.0, 280.0, 4.0, '上海市曹杨路',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '20:20:00')),
(1, 1, 31.2358, 121.4200, 6.5, 46.0, 270.0, 4.0, '上海市普陀云岭东路',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '22:10:00'));

-- ================================================================
-- 调度池 dispatch_pool
-- 商户备货完成后，订单进入此表等待智能调度系统统一处理
-- status: 0=待调度 1=已调度（已分配批次）2=已取消
-- ================================================================
DROP TABLE IF EXISTS `dispatch_pool`;
CREATE TABLE `dispatch_pool` (
  `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id`              BIGINT       NOT NULL                COMMENT '订单ID',
  `shop_id`               BIGINT       NOT NULL                COMMENT '商铺ID',
  `warehouse_id`          BIGINT       NULL                    COMMENT '发货仓库ID（跨城到达Hub后为NULL）',
  `end_address`           VARCHAR(500) NOT NULL                COMMENT '收货地址全文',
  `end_lat`               DOUBLE       NULL                    COMMENT '收货地址纬度',
  `end_lng`               DOUBLE       NULL                    COMMENT '收货地址经度',
  `receiver_name`         VARCHAR(100) NOT NULL DEFAULT ''     COMMENT '收货人',
  `receiver_phone`        VARCHAR(30)  NOT NULL DEFAULT ''     COMMENT '收货电话',
  `remark`                VARCHAR(500) NULL                    COMMENT '订单备注（透传自订单，供急送检测）',
  `status`                TINYINT      NOT NULL DEFAULT 0      COMMENT '0=待调度 1=已调度 2=已取消',
  `batch_id`              BIGINT       NULL                    COMMENT '关联批次ID（调度后赋值）',
  -- MCMF 全国调度扩展字段
  `origin_hub_id`         BIGINT       NULL                    COMMENT '发货所在城市Hub ID',
  `dest_hub_id`           BIGINT       NULL                    COMMENT '收货所在城市Hub ID',
  `is_cross_city`         TINYINT      NOT NULL DEFAULT 0      COMMENT '是否跨城：0=同城 1=跨城',
  `dispatch_origin_type`  TINYINT      NOT NULL DEFAULT 0      COMMENT '调度起点类型：0=仓库 1=干线到达Hub',
  `dispatch_origin_lat`   DOUBLE       NULL                    COMMENT '实际调度起点纬度（type=1时为目标Hub坐标）',
  `dispatch_origin_lng`   DOUBLE       NULL                    COMMENT '实际调度起点经度',
  `dispatch_origin_addr`  VARCHAR(200) NULL                    COMMENT '实际调度起点地址',
  `enter_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入池时间',
  `dispatch_time`         DATETIME     NULL                    COMMENT '调度执行时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_status` (`status`),
  KEY `idx_warehouse_id` (`warehouse_id`),
  KEY `idx_dest_hub_id` (`dest_hub_id`),
  KEY `idx_is_cross_city` (`is_cross_city`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单调度池';

-- ============================================================
-- 修改 logistics_route 表：新增 inter_city_batch_id 字段支持跨城干线虚拟路线
-- segment_type 扩展：3=跨城干线（Hub→Hub，虚拟直线，不经GraphHopper）
-- ============================================================
ALTER TABLE `logistics_route`
  ADD COLUMN `inter_city_batch_id` BIGINT DEFAULT NULL
    COMMENT '关联跨城干线批次ID（segment_type=3时有值）'
  AFTER `hub_id`,
  ADD INDEX `idx_inter_city_batch_id` (`inter_city_batch_id`);

-- ============================================================
-- 全国物流网络表（MCMF 所需）
-- ============================================================

-- 全国多级中转站（国家枢纽/省级/城市配送中心）
DROP TABLE IF EXISTS `national_hub`;
CREATE TABLE `national_hub` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '全国Hub ID',
  `name`          VARCHAR(100) NOT NULL                COMMENT 'Hub名称',
  `hub_level`     TINYINT      NOT NULL DEFAULT 2      COMMENT '层级：0=全国枢纽，1=省级中心，2=城市配送中心',
  `province`      VARCHAR(50)  NOT NULL                COMMENT '所在省份',
  `city`          VARCHAR(50)  NOT NULL                COMMENT '所在城市',
  `latitude`      DOUBLE       NOT NULL                COMMENT '纬度',
  `longitude`     DOUBLE       NOT NULL                COMMENT '经度',
  `max_capacity`  INT          NOT NULL DEFAULT 5000   COMMENT '最大日处理量（件）',
  `current_load`  INT          NOT NULL DEFAULT 0      COMMENT '当前待处理件数',
  `status`        TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0=正常，1=满载，2=关闭',
  `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_hub_level` (`hub_level`),
  INDEX `idx_city` (`city`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全国物流中转站（MCMF网络节点）';

-- Hub间运输边（MCMF网络的边）
DROP TABLE IF EXISTS `hub_link`;
CREATE TABLE `hub_link` (
  `id`                 BIGINT         NOT NULL AUTO_INCREMENT COMMENT '边ID',
  `from_hub_id`        BIGINT         NOT NULL                COMMENT '起点Hub ID',
  `to_hub_id`          BIGINT         NOT NULL                COMMENT '终点Hub ID',
  `transport_mode`     VARCHAR(10)    NOT NULL DEFAULT 'ROAD' COMMENT '运输方式：ROAD/RAIL/AIR',
  `capacity_daily`     INT            NOT NULL DEFAULT 500    COMMENT '日最大运量（件）',
  `cost_per_unit`      DECIMAL(10,4)  NOT NULL DEFAULT 1.0000 COMMENT '今日有效费用（元/件，已含LLM校准倍率）',
  `base_cost_per_unit` DECIMAL(10,4)  NOT NULL DEFAULT 1.0000 COMMENT '基准费用（静态录入值，LLM校准前）',
  `distance_km`        DOUBLE         NOT NULL DEFAULT 0      COMMENT '里程（km）',
  `duration_hours`     DOUBLE         NOT NULL DEFAULT 0      COMMENT '运输时长（h）',
  `is_active`          TINYINT        NOT NULL DEFAULT 1      COMMENT '是否启用：0=停用，1=启用',
  `create_time`        DATETIME       DEFAULT CURRENT_TIMESTAMP,
  `update_time`        DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_from_hub_id` (`from_hub_id`),
  INDEX `idx_to_hub_id` (`to_hub_id`),
  INDEX `idx_is_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全国Hub间运输边（MCMF网络边）';

-- MCMF 流量规划单
DROP TABLE IF EXISTS `flow_plan_item`;
DROP TABLE IF EXISTS `flow_plan`;
CREATE TABLE `flow_plan` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '规划单ID',
  `plan_date`    DATE          NOT NULL                COMMENT '规划日期',
  `total_demand` INT           NOT NULL DEFAULT 0      COMMENT '总需运输件数',
  `total_cost`   DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT 'MCMF最优总费用（元）',
  `actual_flow`  INT           NOT NULL DEFAULT 0      COMMENT 'MCMF实际完成总流量（件，应等于总需运量则可行）',
  `feasible`     TINYINT       NOT NULL DEFAULT 1      COMMENT '1=完全满足图上传输需求 0=部分枢纽不可达',
  `status`       VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/OPTIMIZING/DONE/FAILED',
  `algorithm`    VARCHAR(50)   NOT NULL DEFAULT 'MCMF_SSP_SPFA' COMMENT '使用的算法',
  `llm_advice`   TEXT          DEFAULT NULL            COMMENT 'LLM风险分析与调度建议',
  `llm_enhanced` TINYINT       NOT NULL DEFAULT 0      COMMENT '是否使用LLM费用校准：0=否，1=是',
  `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_plan_date` (`plan_date`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCMF流量规划单';

-- MCMF 流量规划明细（每条边的分配流量）
CREATE TABLE `flow_plan_item` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `plan_id`      BIGINT        NOT NULL                COMMENT '所属规划单ID',
  `from_hub_id`  BIGINT        NOT NULL                COMMENT '起点Hub ID',
  `to_hub_id`    BIGINT        NOT NULL                COMMENT '终点Hub ID',
  `link_id`      BIGINT        NOT NULL                COMMENT '对应hub_link.id',
  `flow_amount`  INT           NOT NULL DEFAULT 0      COMMENT '分配流量（件）',
  `edge_cost`    DECIMAL(10,4) NOT NULL DEFAULT 0      COMMENT '该边单位费用',
  `total_cost`   DECIMAL(12,2) NOT NULL DEFAULT 0      COMMENT '该边总费用（flow_amount * edge_cost）',
  `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_plan_id` (`plan_id`),
  INDEX `idx_from_to` (`from_hub_id`, `to_hub_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCMF流量规划明细';

-- 跨城干线批次
DROP TABLE IF EXISTS `inter_city_batch`;
CREATE TABLE `inter_city_batch` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '批次ID',
  `batch_no`        VARCHAR(30)  NOT NULL UNIQUE        COMMENT '批次编号（IB+时间戳+4位随机）',
  `flow_plan_id`    BIGINT       NOT NULL                COMMENT '所属流量规划单ID',
  `from_hub_id`     BIGINT       NOT NULL                COMMENT '起点Hub ID',
  `to_hub_id`       BIGINT       NOT NULL                COMMENT '终点Hub ID',
  `transport_mode`  VARCHAR(10)  NOT NULL DEFAULT 'ROAD' COMMENT '运输方式：ROAD/RAIL/AIR',
  `planned_depart`  DATETIME     NULL                    COMMENT '计划发车时间',
  `actual_depart`   DATETIME     NULL                    COMMENT '实际发车时间',
  `actual_arrive`   DATETIME     NULL                    COMMENT '实际到达时间',
  `status`          VARCHAR(20)  NOT NULL DEFAULT 'CREATED' COMMENT '状态：CREATED/DEPARTED/ARRIVED/DISPATCHED',
  `item_count`      INT          NOT NULL DEFAULT 0      COMMENT '包含订单件数',
  `remark`          VARCHAR(300) NULL,
  `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  INDEX `idx_flow_plan_id` (`flow_plan_id`),
  INDEX `idx_from_to` (`from_hub_id`, `to_hub_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨城干线批次';

-- ============================================================
-- 全国Hub测试数据（与 TODO 附录示例一致）
-- ============================================================
INSERT INTO `national_hub` (`id`, `name`, `hub_level`, `province`, `city`, `latitude`, `longitude`, `max_capacity`, `current_load`, `status`) VALUES
(1, '武汉全国枢纽',  0, '湖北', '武汉', 30.5928, 114.3055, 10000, 0, 0),
(2, '郑州全国枢纽',  0, '河南', '郑州', 34.7466, 113.6253, 10000, 0, 0),
(3, '上海配送中心',  2, '上海', '上海', 31.2304, 121.4737,  5000, 0, 0),
(4, '北京配送中心',  2, '北京', '北京', 39.9042, 116.4074,  5000, 0, 0),
(5, '广州配送中心',  2, '广东', '广州', 23.1291, 113.2644,  5000, 0, 0),
(6, '成都配送中心',  2, '四川', '成都', 30.5723, 104.0665,  5000, 0, 0),
(7, '西安配送中心',  2, '陕西', '西安', 34.3416, 108.9398,  5000, 0, 0),
(8, '深圳配送中心',  2, '广东', '深圳', 22.5431, 114.0579,  5000, 0, 0),
(9, '南京配送中心',  2, '江苏', '南京', 32.0603, 118.7969,  5000, 0, 0),
-- 省级枢纽（hub_level=1）：衔接大区与全国枢纽，便于 MCMF 多路径分摊
(10, '沈阳省级枢纽', 1, '辽宁', '沈阳', 41.8057, 123.4315,  8000, 0, 0),
(11, '济南省级枢纽', 1, '山东', '济南', 36.6512, 117.1201,  8000, 0, 0),
(12, '杭州省级枢纽', 1, '浙江', '杭州', 30.2741, 120.1551,  8000, 0, 0),
(13, '重庆省级枢纽', 1, '重庆', '重庆', 29.5630, 106.5516,  8000, 0, 0),
-- 配套城市配送中心（hub_level=2）
(14, '大连配送中心', 2, '辽宁', '大连', 38.9140, 121.6147,  5000, 0, 0),
(15, '青岛配送中心', 2, '山东', '青岛', 36.0671, 120.3826,  5000, 0, 0),
(16, '宁波配送中心', 2, '浙江', '宁波', 29.8683, 121.5440,  5000, 0, 0),
(17, '长沙配送中心', 2, '湖南', '长沙', 28.2282, 112.9388,  5000, 0, 0);

-- Hub间运输边（id, from, to, mode, capacity, cost_per_unit, base_cost, dist_km, duration_h, is_active）
INSERT INTO `hub_link` (`id`,`from_hub_id`,`to_hub_id`,`transport_mode`,`capacity_daily`,`cost_per_unit`,`base_cost_per_unit`,`distance_km`,`duration_hours`,`is_active`) VALUES
( 1, 3, 1, 'ROAD', 500, 3.20, 3.20,  840, 10.0, 1), -- 上海→武汉
( 2, 3, 9, 'ROAD', 500, 0.90, 0.90,  290,  4.0, 1), -- 上海→南京
( 3, 3, 2, 'RAIL', 300, 2.80, 2.80,  800,  6.0, 1), -- 上海→郑州
( 4, 4, 1, 'ROAD', 300, 3.80, 3.80, 1100, 13.0, 1), -- 北京→武汉
( 5, 4, 2, 'RAIL', 400, 2.10, 2.10,  690,  6.0, 1), -- 北京→郑州
( 6, 5, 1, 'ROAD', 400, 3.50, 3.50, 1000, 12.0, 1), -- 广州→武汉
( 7, 5, 8, 'ROAD', 500, 0.60, 0.60,  140,  2.0, 1), -- 广州→深圳
( 8, 1, 6, 'ROAD', 400, 2.60, 2.60,  800, 10.0, 1), -- 武汉→成都
( 9, 1, 7, 'ROAD', 300, 2.40, 2.40,  680,  8.0, 1), -- 武汉→西安
(10, 2, 7, 'RAIL', 300, 1.80, 1.80,  500,  5.0, 1), -- 郑州→西安
(11, 2, 6, 'ROAD', 250, 2.20, 2.20, 1000, 12.0, 1), -- 郑州→成都
(12, 2, 9, 'RAIL', 300, 2.00, 2.00,  700,  6.0, 1), -- 郑州→南京
-- 以下入边补全：原先北京/上海/广州等仅有「发出」边，MCMF 无法把货流送进该城（沪→京、京→沪等 OD 在图上不可行，界面看不到京沪干线）
(13, 9, 3, 'ROAD', 400, 1.00, 1.00,  290,  4.0, 1), -- 南京→上海（京→沪/经宁入沪）
(14, 2, 4, 'RAIL', 350, 2.40, 2.40,  660,  7.0, 1), -- 郑州→北京（华北到达）
(15, 1, 4, 'ROAD', 300, 2.90, 2.90, 1050, 12.0, 1), -- 武汉→北京
(16, 2, 5, 'RAIL', 300, 2.70, 2.70, 1400, 12.0, 1), -- 郑州→广州（华南到达）
(17, 3, 4, 'RAIL', 200, 4.20, 4.20, 1200, 14.0, 1), -- 上海→北京（京沪直达干线，便于演示）
-- ── 省级枢纽 + 城市枢纽 衔接边（有向；对向成对给出，保证主要 OD 在图上可达）──
-- 华北 / 东北
(18, 4, 10, 'RAIL', 350, 3.10, 3.10,  700,  8.0, 1), -- 北京→沈阳
(19, 10, 4, 'RAIL', 350, 3.10, 3.10,  700,  8.0, 1), -- 沈阳→北京
(20, 10, 14, 'ROAD', 450, 0.48, 0.48,  380,  4.0, 1), -- 沈阳→大连
(21, 14, 10, 'ROAD', 450, 0.48, 0.48,  380,  4.0, 1), -- 大连→沈阳
(22, 14, 4, 'ROAD', 300, 2.95, 2.95,  800, 10.0, 1), -- 大连→北京（渤海湾经陆路示意）
(23, 4, 14, 'ROAD', 300, 2.95, 2.95,  800, 10.0, 1), -- 北京→大连
(24, 10, 11, 'RAIL', 280, 2.75, 2.75, 1050, 11.0, 1), -- 沈阳→济南（关内衔接）
(25, 11, 10, 'RAIL', 280, 2.75, 2.75, 1050, 11.0, 1), -- 济南→沈阳
-- 山东 / 中原
(26, 2, 11, 'RAIL', 400, 1.35, 1.35,  420,  5.0, 1), -- 郑州→济南
(27, 11, 2, 'RAIL', 400, 1.35, 1.35,  420,  5.0, 1), -- 济南→郑州
(28, 11, 15, 'ROAD', 500, 0.88, 0.88,  350,  4.0, 1), -- 济南→青岛
(29, 15, 11, 'ROAD', 500, 0.88, 0.88,  350,  4.0, 1), -- 青岛→济南
(30, 11, 9, 'RAIL', 350, 1.75, 1.75,  620,  7.0, 1), -- 济南→南京
(31, 9, 11, 'RAIL', 350, 1.75, 1.75,  620,  7.0, 1), -- 南京→济南
(32, 15, 3, 'ROAD', 350, 2.15, 2.15,  650,  8.0, 1), -- 青岛→上海
(33, 3, 15, 'ROAD', 350, 2.15, 2.15,  650,  8.0, 1), -- 上海→青岛
-- 华东（沪杭甬、宁杭）
(34, 3, 12, 'ROAD', 500, 0.52, 0.52,  180,  2.5, 1), -- 上海→杭州
(35, 12, 3, 'ROAD', 500, 0.52, 0.52,  180,  2.5, 1), -- 杭州→上海
(36, 12, 16, 'ROAD', 500, 0.58, 0.58,  155,  2.0, 1), -- 杭州→宁波
(37, 16, 12, 'ROAD', 500, 0.58, 0.58,  155,  2.0, 1), -- 宁波→杭州
(38, 3, 16, 'ROAD', 400, 0.62, 0.62,  215,  2.5, 1), -- 上海→宁波
(39, 16, 3, 'ROAD', 400, 0.62, 0.62,  215,  2.5, 1), -- 宁波→上海
(40, 12, 9, 'ROAD', 450, 1.05, 1.05,  230,  3.0, 1), -- 杭州→南京
(41, 9, 12, 'ROAD', 450, 1.05, 1.05,  230,  3.0, 1), -- 南京→杭州
(42, 12, 1, 'ROAD', 350, 2.45, 2.45,  750,  9.0, 1), -- 杭州→武汉
(43, 1, 12, 'ROAD', 350, 2.45, 2.45,  750,  9.0, 1), -- 武汉→杭州
(44, 12, 5, 'RAIL', 300, 3.15, 3.15, 1250, 12.0, 1), -- 杭州→广州
(45, 5, 12, 'RAIL', 300, 3.15, 3.15, 1250, 12.0, 1), -- 广州→杭州
-- 华中 / 华南（长沙衔接武汉、郑州、广州）
(46, 1, 17, 'ROAD', 450, 0.82, 0.82,  340,  4.0, 1), -- 武汉→长沙
(47, 17, 1, 'ROAD', 450, 0.82, 0.82,  340,  4.0, 1), -- 长沙→武汉
(48, 17, 5, 'ROAD', 400, 2.25, 2.25,  720,  9.0, 1), -- 长沙→广州
(49, 5, 17, 'ROAD', 400, 2.25, 2.25,  720,  9.0, 1), -- 广州→长沙
(50, 17, 2, 'RAIL', 350, 1.90, 1.90,  810,  9.0, 1), -- 长沙→郑州
(51, 2, 17, 'RAIL', 350, 1.90, 1.90,  810,  9.0, 1), -- 郑州→长沙
-- 西南（渝蓉、渝陕、渝鄂）
(52, 6, 13, 'ROAD', 500, 0.52, 0.52,  300,  4.0, 1), -- 成都→重庆
(53, 13, 6, 'ROAD', 500, 0.52, 0.52,  300,  4.0, 1), -- 重庆→成都
(54, 13, 7, 'ROAD', 350, 2.05, 2.05,  650,  8.0, 1), -- 重庆→西安
(55, 7, 13, 'ROAD', 350, 2.05, 2.05,  650,  8.0, 1), -- 西安→重庆
(56, 13, 1, 'ROAD', 350, 1.95, 1.95,  750,  9.0, 1), -- 重庆→武汉
(57, 1, 13, 'ROAD', 350, 1.95, 1.95,  750,  9.0, 1), -- 武汉→重庆
(58, 2, 13, 'RAIL', 300, 2.35, 2.35,  900, 10.0, 1), -- 郑州→重庆（西南陆路主轴）
(59, 13, 2, 'RAIL', 300, 2.35, 2.35,  900, 10.0, 1); -- 重庆→郑州

-- 上海的3个仓库归属上海配送中心（national_hub.id=3）
UPDATE `warehouse` SET `affiliated_hub_id` = 3 WHERE `city` = '上海市' OR `province` = '上海市';

-- ============================================================
-- 仓储管理扩展：共享仓、库位、入库/出库/盘点/调拨单
-- ============================================================

-- warehouse_product 增加 shop_id 字段，支持共享仓多租户隔离
-- 使用存储过程保证幂等（列不存在才执行 ALTER，避免重复运行报错）
DROP PROCEDURE IF EXISTS `add_shop_id_to_warehouse_product`;
DELIMITER $$
CREATE PROCEDURE `add_shop_id_to_warehouse_product`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'warehouse_product'
          AND COLUMN_NAME  = 'shop_id'
    ) THEN
        ALTER TABLE `warehouse_product`
            ADD COLUMN `shop_id` BIGINT DEFAULT NULL
                COMMENT '所属商家ID（关联shop_info.id），共享仓下区分不同商家库存'
                AFTER `product_id`,
            ADD INDEX `idx_wp_shop_id` (`shop_id`);
    END IF;
END$$
DELIMITER ;
CALL `add_shop_id_to_warehouse_product`();
DROP PROCEDURE IF EXISTS `add_shop_id_to_warehouse_product`;

-- 补填现有数据的 shop_id（根据 product_info.shop_id 回填）
UPDATE `warehouse_product` wp
    JOIN `product_info` pi ON wp.product_id = pi.id
SET wp.shop_id = pi.shop_id
WHERE wp.shop_id IS NULL;

-- ============================================================
-- 1. 仓库-商家关联表（多对多共享仓）
-- ============================================================
DROP TABLE IF EXISTS `warehouse_shop`;
CREATE TABLE `warehouse_shop` (
  `id`           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `warehouse_id` BIGINT   NOT NULL COMMENT '仓库ID（关联warehouse.id）',
  `shop_id`      BIGINT   NOT NULL COMMENT '商家ID（关联shop_info.id）',
  `role`         VARCHAR(20) NOT NULL DEFAULT 'TENANT' COMMENT '角色：OWNER=仓库所有者，TENANT=租用方',
  `status`       TINYINT  NOT NULL DEFAULT 1 COMMENT '状态：0=停用，1=启用',
  `create_time`  DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_warehouse_shop` (`warehouse_id`, `shop_id`),
  INDEX `idx_warehouse_id` (`warehouse_id`),
  INDEX `idx_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库-商家关联表（支持共享仓，多对多）';

-- 初始数据：根据现有库存关系，建立商家-仓库归属
-- 使用 INSERT IGNORE 保证幂等（重复执行时跳过已存在记录）
INSERT IGNORE INTO `warehouse_shop` (`warehouse_id`, `shop_id`, `role`) VALUES
(1, 1, 'OWNER'),   -- 上海闵行分拨仓 → 优质食品店（owner）
(2, 1, 'OWNER'),   -- 上海华东仓库 → 优质食品店（owner）
(2, 2, 'TENANT'),  -- 上海华东仓库 → 时尚服装店（共享租用）
(3, 1, 'TENANT'),  -- 上海宝山分拨仓 → 优质食品店（共享租用）
(3, 2, 'TENANT'),  -- 上海宝山分拨仓 → 时尚服装店（共享租用）
(4, 3, 'OWNER');   -- 北京大兴履约中心 → 京北优品铺（owner）

-- ============================================================
-- 2. 库位表
-- ============================================================
DROP TABLE IF EXISTS `warehouse_location`;
CREATE TABLE `warehouse_location` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '库位ID',
  `warehouse_id`  BIGINT      NOT NULL COMMENT '所属仓库ID',
  `zone_code`     VARCHAR(20) NOT NULL COMMENT '区域编码（如 A、B、冷链区）',
  `row_no`        VARCHAR(10) NOT NULL COMMENT '排号',
  `shelf_no`      VARCHAR(10) NOT NULL COMMENT '架号',
  `level_no`      VARCHAR(10) NOT NULL COMMENT '层号',
  `location_code` VARCHAR(50) NOT NULL COMMENT '库位编码（唯一，如 A-01-02-03）',
  `capacity`      INT         NOT NULL DEFAULT 100 COMMENT '容量（件）',
  `current_stock` INT         NOT NULL DEFAULT 0   COMMENT '当前占用量',
  `status`        TINYINT     NOT NULL DEFAULT 1    COMMENT '状态：0=禁用，1=正常，2=锁定（盘点中）',
  `create_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_location_code` (`warehouse_id`, `location_code`),
  INDEX `idx_warehouse_id` (`warehouse_id`),
  INDEX `idx_zone_code` (`warehouse_id`, `zone_code`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库位表';

-- 初始库位数据（每个仓库生成少量示例库位）
INSERT INTO `warehouse_location` (`warehouse_id`, `zone_code`, `row_no`, `shelf_no`, `level_no`, `location_code`, `capacity`, `current_stock`, `status`) VALUES
-- 仓库1（上海闵行分拨仓）
(1, 'A', '01', '01', '01', 'A-01-01-01', 100, 50,  1),
(1, 'A', '01', '01', '02', 'A-01-01-02', 100, 100, 1),
(1, 'A', '01', '02', '01', 'A-01-02-01', 100, 0,   1),
(1, 'B', '01', '01', '01', 'B-01-01-01', 200, 0,   1),
-- 仓库2（上海华东仓库）
(2, 'A', '01', '01', '01', 'A-01-01-01', 100, 49,  1),
(2, 'A', '01', '01', '02', 'A-01-01-02', 100, 99,  1),
(2, 'A', '01', '02', '01', 'A-01-02-01', 100, 30,  1),
(2, 'A', '01', '02', '02', 'A-01-02-02', 100, 20,  1),
(2, 'B', '01', '01', '01', 'B-01-01-01', 200, 0,   1),
(2, 'B', '01', '01', '02', 'B-01-01-02', 200, 0,   1),
-- 仓库3（上海宝山分拨仓）
(3, 'A', '01', '01', '01', 'A-01-01-01', 100, 100, 1),
(3, 'A', '01', '01', '02', 'A-01-01-02', 100, 10,  1),
(3, 'B', '01', '01', '01', 'B-01-01-01', 200, 0,   1),
-- 仓库4（北京大兴履约中心）
(4, 'A', '01', '01', '01', 'A-01-01-01', 500, 800,  1),
(4, 'A', '01', '01', '02', 'A-01-01-02', 500, 1200, 1),
(4, 'B', '01', '01', '01', 'B-01-01-01', 300, 0,    1);

-- ============================================================
-- 3. 入库单
-- ============================================================
DROP TABLE IF EXISTS `inbound_order_item`;
DROP TABLE IF EXISTS `inbound_order`;
CREATE TABLE `inbound_order` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '入库单ID',
  `order_no`      VARCHAR(30) NOT NULL UNIQUE COMMENT '入库单号（IB+时间戳+4位随机）',
  `warehouse_id`  BIGINT      NOT NULL COMMENT '目标仓库ID',
  `shop_id`       BIGINT      NOT NULL COMMENT '所属商家ID',
  `source_type`   VARCHAR(20) NOT NULL DEFAULT 'PURCHASE' COMMENT '来源类型：PURCHASE=采购，TRANSFER=调拨，RETURN=退货',
  `related_id`    BIGINT      DEFAULT NULL COMMENT '关联单据ID（调拨单/退货单）',
  `operator_id`   BIGINT      DEFAULT NULL COMMENT '操作人ID',
  `status`        VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING=待入库，PROCESSING=入库中，DONE=已完成，CANCELLED=已取消',
  `expected_time` DATETIME    DEFAULT NULL COMMENT '预计入库时间',
  `actual_time`   DATETIME    DEFAULT NULL COMMENT '实际完成时间',
  `remark`        VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  INDEX `idx_warehouse_id` (`warehouse_id`),
  INDEX `idx_shop_id` (`shop_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入库单';

CREATE TABLE `inbound_order_item` (
  `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `inbound_order_id` BIGINT      NOT NULL COMMENT '所属入库单ID',
  `product_id`       BIGINT      NOT NULL COMMENT '商品ID',
  `product_name`     VARCHAR(200) NOT NULL COMMENT '商品名称（快照）',
  `expected_qty`     INT         NOT NULL DEFAULT 0 COMMENT '预计数量',
  `actual_qty`       INT         NOT NULL DEFAULT 0 COMMENT '实际入库数量',
  `location_id`      BIGINT      DEFAULT NULL COMMENT '上架库位ID',
  `status`           VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING=待入库，DONE=已入库',
  `create_time`      DATETIME    DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_inbound_order_id` (`inbound_order_id`),
  INDEX `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入库单明细';

-- ============================================================
-- 4. 出库单
-- ============================================================
DROP TABLE IF EXISTS `outbound_order_item`;
DROP TABLE IF EXISTS `outbound_order`;
CREATE TABLE `outbound_order` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '出库单ID',
  `order_no`      VARCHAR(30) NOT NULL UNIQUE COMMENT '出库单号（OB+时间戳+4位随机）',
  `warehouse_id`  BIGINT      NOT NULL COMMENT '出库仓库ID',
  `shop_id`       BIGINT      NOT NULL COMMENT '所属商家ID',
  `dest_type`     VARCHAR(20) NOT NULL DEFAULT 'DELIVERY' COMMENT '目的类型：DELIVERY=配送出库，TRANSFER=调拨，RETURN=退货',
  `related_id`    BIGINT      DEFAULT NULL COMMENT '关联单据ID（订单/调拨单）',
  `operator_id`   BIGINT      DEFAULT NULL COMMENT '操作人ID',
  `status`        VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING=待出库，PROCESSING=出库中，DONE=已完成，CANCELLED=已取消',
  `expected_time` DATETIME    DEFAULT NULL COMMENT '预计出库时间',
  `actual_time`   DATETIME    DEFAULT NULL COMMENT '实际完成时间',
  `remark`        VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  INDEX `idx_warehouse_id` (`warehouse_id`),
  INDEX `idx_shop_id` (`shop_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出库单';

CREATE TABLE `outbound_order_item` (
  `id`                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `outbound_order_id` BIGINT      NOT NULL COMMENT '所属出库单ID',
  `product_id`        BIGINT      NOT NULL COMMENT '商品ID',
  `product_name`      VARCHAR(200) NOT NULL COMMENT '商品名称（快照）',
  `quantity`          INT         NOT NULL DEFAULT 0 COMMENT '出库数量',
  `location_id`       BIGINT      DEFAULT NULL COMMENT '拣货库位ID',
  `status`            VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING=待拣货，DONE=已出库',
  `create_time`       DATETIME    DEFAULT CURRENT_TIMESTAMP,
  `update_time`       DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_outbound_order_id` (`outbound_order_id`),
  INDEX `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出库单明细';

-- ============================================================
-- 5. 盘点单
-- ============================================================
DROP TABLE IF EXISTS `inventory_check_item`;
DROP TABLE IF EXISTS `inventory_check`;
CREATE TABLE `inventory_check` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '盘点单ID',
  `check_no`     VARCHAR(30) NOT NULL UNIQUE COMMENT '盘点单号（CC+时间戳+4位随机）',
  `warehouse_id` BIGINT      NOT NULL COMMENT '盘点仓库ID',
  `shop_id`      BIGINT      DEFAULT NULL COMMENT '商家ID（NULL=全仓盘点，指定则按商家范围盘）',
  `check_type`   VARCHAR(20) NOT NULL DEFAULT 'FULL' COMMENT '盘点类型：FULL=全盘，ZONE=分区盘，DYNAMIC=动态盘',
  `zone_code`    VARCHAR(20) DEFAULT NULL COMMENT '盘点区域（分区盘时有效）',
  `operator_id`  BIGINT      DEFAULT NULL COMMENT '操作人ID',
  `status`       VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING=待盘点，PROCESSING=盘点中，CONFIRMING=待确认，DONE=已完成',
  `start_time`   DATETIME    DEFAULT NULL COMMENT '开始时间',
  `end_time`     DATETIME    DEFAULT NULL COMMENT '完成时间',
  `remark`       VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time`  DATETIME    DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_check_no` (`check_no`),
  INDEX `idx_warehouse_id` (`warehouse_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盘点单';

CREATE TABLE `inventory_check_item` (
  `id`           BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `check_id`     BIGINT NOT NULL COMMENT '所属盘点单ID',
  `location_id`  BIGINT NOT NULL COMMENT '库位ID',
  `product_id`   BIGINT NOT NULL COMMENT '商品ID',
  `shop_id`      BIGINT DEFAULT NULL COMMENT '商家ID',
  `product_name` VARCHAR(200) NOT NULL COMMENT '商品名称（快照）',
  `system_qty`   INT    NOT NULL DEFAULT 0 COMMENT '系统账面数量',
  `actual_qty`   INT    DEFAULT NULL COMMENT '实盘数量（NULL=尚未盘点）',
  `difference`   INT    GENERATED ALWAYS AS (IFNULL(`actual_qty`, 0) - `system_qty`) STORED COMMENT '差异（实盘-系统）',
  `status`       VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING=待盘，COUNTED=已盘，DIFF=有差异',
  `create_time`  DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_check_id` (`check_id`),
  INDEX `idx_location_id` (`location_id`),
  INDEX `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盘点单明细';

-- ============================================================
-- 6. 调拨单
-- ============================================================
DROP TABLE IF EXISTS `transfer_order_item`;
DROP TABLE IF EXISTS `transfer_order`;
CREATE TABLE `transfer_order` (
  `id`                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '调拨单ID',
  `order_no`           VARCHAR(30) NOT NULL UNIQUE COMMENT '调拨单号（TR+时间戳+4位随机）',
  `shop_id`            BIGINT      NOT NULL COMMENT '发起商家ID',
  `src_warehouse_id`   BIGINT      NOT NULL COMMENT '调出仓库ID',
  `dst_warehouse_id`   BIGINT      NOT NULL COMMENT '调入仓库ID',
  `operator_id`        BIGINT      DEFAULT NULL COMMENT '操作人ID',
  `status`             VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING=待审批，APPROVED=已审批，IN_TRANSIT=在途，DONE=已完成，CANCELLED=已取消',
  `expected_time`      DATETIME    DEFAULT NULL COMMENT '预计完成时间',
  `actual_time`        DATETIME    DEFAULT NULL COMMENT '实际完成时间',
  `remark`             VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time`        DATETIME    DEFAULT CURRENT_TIMESTAMP,
  `update_time`        DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  INDEX `idx_shop_id` (`shop_id`),
  INDEX `idx_src_warehouse_id` (`src_warehouse_id`),
  INDEX `idx_dst_warehouse_id` (`dst_warehouse_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调拨单';

CREATE TABLE `transfer_order_item` (
  `id`                BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `transfer_order_id` BIGINT NOT NULL COMMENT '所属调拨单ID',
  `product_id`        BIGINT NOT NULL COMMENT '商品ID',
  `product_name`      VARCHAR(200) NOT NULL COMMENT '商品名称（快照）',
  `quantity`          INT    NOT NULL DEFAULT 0 COMMENT '调拨数量',
  `src_location_id`   BIGINT DEFAULT NULL COMMENT '调出库位ID',
  `dst_location_id`   BIGINT DEFAULT NULL COMMENT '调入库位ID',
  `status`            VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING=待出库，OUT=已出库，DONE=已入库',
  `create_time`       DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time`       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_transfer_order_id` (`transfer_order_id`),
  INDEX `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调拨单明细';

-- ============================================================
-- 7. Hub 作业记录表（logistics-service 使用）
-- ============================================================
DROP TABLE IF EXISTS `hub_sorting_record`;
DROP TABLE IF EXISTS `hub_inbound_record`;
DROP TABLE IF EXISTS `hub_outbound_record`;

CREATE TABLE `hub_inbound_record` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `hub_id`       BIGINT      NOT NULL COMMENT '配送中心ID（关联national_hub.id）',
  `order_id`     BIGINT      DEFAULT NULL COMMENT '订单ID（手动登记时可为空）',
  `waybill_no`   VARCHAR(50) DEFAULT NULL COMMENT '运单号',
  `source_type`  VARCHAR(20) NOT NULL DEFAULT 'COLLECTION' COMMENT '来源：COLLECTION=揽收，TRUNK_ARRIVE=干线到达',
  `from_hub_id`  BIGINT      DEFAULT NULL COMMENT '来源Hub ID（干线到达时有效）',
  `operator_id`  BIGINT      DEFAULT NULL COMMENT '操作人ID',
  `status`       VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING=待入库，DONE=已入库',
  `arrive_time`  DATETIME    DEFAULT NULL COMMENT '到达时间',
  `inbound_time` DATETIME    DEFAULT NULL COMMENT '入库确认时间',
  `create_time`  DATETIME    DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_hub_id` (`hub_id`),
  INDEX `idx_order_id` (`order_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Hub到货入库记录';

CREATE TABLE `hub_outbound_record` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `hub_id`        BIGINT      NOT NULL COMMENT '配送中心ID',
  `order_id`      BIGINT      DEFAULT NULL COMMENT '订单ID（手动登记时可为空）',
  `waybill_no`    VARCHAR(50) DEFAULT NULL COMMENT '运单号',
  `dest_type`     VARCHAR(20) NOT NULL DEFAULT 'TRUNK' COMMENT '目的类型：TRUNK=干线发车，LAST_MILE=末端下发',
  `dest_hub_id`   BIGINT      DEFAULT NULL COMMENT '目的Hub ID（干线时有效）',
  `batch_id`      BIGINT      DEFAULT NULL COMMENT '批次ID（inter_city_batch 或 logistics_batch）',
  `operator_id`   BIGINT      DEFAULT NULL COMMENT '操作人ID',
  `status`        VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING=待出库，DONE=已出库',
  `outbound_time` DATETIME    DEFAULT NULL COMMENT '出库时间',
  `create_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_hub_id` (`hub_id`),
  INDEX `idx_order_id` (`order_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Hub出库记录';

CREATE TABLE `hub_sorting_record` (
  `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `hub_id`           BIGINT      NOT NULL COMMENT '配送中心ID',
  `order_id`         BIGINT      DEFAULT NULL COMMENT '订单ID（手动登记时可为空）',
  `waybill_no`       VARCHAR(50) DEFAULT NULL COMMENT '运单号',
  `dest_hub_id`      BIGINT      DEFAULT NULL COMMENT '目的配送中心ID',
  `assigned_batch_id` BIGINT     DEFAULT NULL COMMENT '分配到的干线批次ID',
  `sort_result`      VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '分拣结果：PENDING=待分拣，ASSIGNED=已分配批次，DIRECT=直送末端',
  `operator_id`      BIGINT      DEFAULT NULL COMMENT '操作人ID',
  `sort_time`        DATETIME    DEFAULT NULL COMMENT '分拣时间',
  `create_time`      DATETIME    DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_hub_id` (`hub_id`),
  INDEX `idx_order_id` (`order_id`),
  INDEX `idx_dest_hub_id` (`dest_hub_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Hub分拣记录';
