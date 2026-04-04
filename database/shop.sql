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
