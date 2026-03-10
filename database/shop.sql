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
-- 3. 商品信息表（不包含库存，库存在仓库商品关联表中）
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
  `capacity` INT DEFAULT 0 COMMENT '仓库容量（单位：件/箱等，0表示无限制）',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0=禁用，1=启用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_warehouse_name` (`warehouse_name`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库信息表';

-- ============================================
-- 5. 仓库商品关联表（多对多关系，包含库存数量）
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

-- ============================================
-- 插入测试数据
-- ============================================

-- 插入商户信息测试数据
-- 注意：user_id=3 对应user表中的shop用户，user_id=4 对应user表中的shop2用户（根据user.sql中的插入顺序）
INSERT INTO `shop_info` (`user_id`, `shop_name`, `shop_phone`, `shop_email`, `description`, `business_license`, `status`) VALUES
(3, '优质食品店', '13900139000', 'shop1@example.com', '专业销售优质食品，品质保证', '91110000123456789X', 1),
(4, '时尚服装店', '13900139001', 'shop2@example.com', '时尚潮流服装，款式新颖', '91310000123456789Y', 1);

-- 插入商品分类测试数据
INSERT INTO `product_category` (`category_name`, `parent_id`, `sort_order`, `description`, `status`) VALUES
('食品', 0, 1, '各类食品', 1),
('服装', 0, 2, '各类服装', 1),
('零食', 1, 1, '休闲零食', 1),
('饮料', 1, 2, '各类饮料', 1),
('男装', 2, 1, '男士服装', 1),
('女装', 2, 2, '女士服装', 1);

-- 插入商品信息测试数据
-- 注意：shop_id=1 对应上面插入的第一个商户，shop_id=2 对应第二个商户
INSERT INTO `product_info` (`shop_id`, `category_id`, `product_name`, `product_code`, `description`, `price`, `original_price`, `unit`, `weight`, `images`, `status`, `sales_count`) VALUES
(1, 3, '优质坚果礼盒', 'SP001', '精选优质坚果，包含核桃、杏仁、腰果等', 128.00, 158.00, '盒', 1.5, '["https://example.com/images/nut1.jpg", "https://example.com/images/nut2.jpg"]', 1, 25),
(1, 4, '有机果汁', 'SP002', '100%纯天然有机果汁，无添加', 35.00, 45.00, '瓶', 0.5, '["https://example.com/images/juice1.jpg"]', 1, 50),
(2, 5, '商务休闲衬衫', 'SP003', '高品质商务休闲衬衫，多色可选', 299.00, 399.00, '件', 0.3, '["https://example.com/images/shirt1.jpg", "https://example.com/images/shirt2.jpg"]', 1, 15),
(2, 6, '时尚连衣裙', 'SP004', '春季新款时尚连衣裙，优雅大方', 399.00, 499.00, '件', 0.4, '["https://example.com/images/dress1.jpg"]', 1, 8);

-- 插入仓库信息测试数据
INSERT INTO `warehouse` (`warehouse_name`, `warehouse_phone`, `province`, `city`, `district`, `detail_address`, `postal_code`, `capacity`, `status`) VALUES
('北京中央仓库', '010-12345678', '北京市', '北京市', '朝阳区', '朝阳区物流园区A区1号', '100020', 10000, 1),
('上海华东仓库', '021-87654321', '上海市', '上海市', '浦东新区', '浦东新区物流园区B区2号', '200135', 8000, 1),
('广州华南仓库', '020-11223344', '广东省', '广州市', '白云区', '白云区物流园区C区3号', '510440', 6000, 1);

-- 插入仓库商品关联测试数据（体现多对多关系）
-- 商品1（优质坚果礼盒）存放在仓库1和仓库2
-- 商品2（有机果汁）存放在仓库1和仓库3
-- 商品3（商务休闲衬衫）存放在仓库2
-- 商品4（时尚连衣裙）存放在仓库2和仓库3
INSERT INTO `warehouse_product` (`warehouse_id`, `product_id`, `stock`) VALUES
-- 仓库1（北京中央仓库）的商品
(1, 1, 50),   -- 优质坚果礼盒，库存50
(1, 2, 100),  -- 有机果汁，库存100
-- 仓库2（上海华东仓库）的商品
(2, 1, 50),   -- 优质坚果礼盒，库存50（同一商品在不同仓库）
(2, 3, 30),   -- 商务休闲衬衫，库存30
(2, 4, 20),   -- 时尚连衣裙，库存20
-- 仓库3（广州华南仓库）的商品
(3, 2, 100),  -- 有机果汁，库存100（同一商品在不同仓库）
(3, 4, 10);   -- 时尚连衣裙，库存10（同一商品在不同仓库）

