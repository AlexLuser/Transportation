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
