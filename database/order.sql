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
  `order_status` TINYINT DEFAULT 0 COMMENT '订单状态：0=待支付，1=已支付，2=待发货，3=已发货，4=已完成，5=已取消',
  `payment_status` TINYINT DEFAULT 0 COMMENT '支付状态：0=未支付，1=已支付',
  `payment_time` DATETIME COMMENT '支付时间',
  `shipping_time` DATETIME COMMENT '发货时间',
  `complete_time` DATETIME COMMENT '完成时间',
  `cancel_time` DATETIME COMMENT '取消时间',
  `cancel_reason` VARCHAR(255) COMMENT '取消原因',
  `remark` VARCHAR(500) COMMENT '订单备注',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  INDEX `idx_customer_id` (`customer_id`),
  INDEX `idx_shop_id` (`shop_id`),
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

-- ============================================
-- 插入测试数据
-- ============================================

-- 插入订单信息测试数据
-- 注意：customer_id=1 对应customer_info表中的第一条记录
-- shop_id=1 对应shop_info表中的第一条记录
-- address_id=1 对应customer_address表中的第一条记录
INSERT INTO `order_info` (`order_no`, `customer_id`, `shop_id`, `address_id`, `total_amount`, `product_amount`, `shipping_fee`, `order_status`, `payment_status`, `remark`) VALUES
('ORD20240307001', 1, 1, 1, 163.00, 163.00, 0.00, 1, 1, '请尽快发货'),
('ORD20240307002', 1, 1, 1, 35.00, 35.00, 0.00, 0, 0, NULL);

-- 插入订单项测试数据
-- 订单1包含2个商品
INSERT INTO `order_item` (`order_id`, `product_id`, `product_name`, `product_image`, `product_price`, `quantity`, `subtotal`) VALUES
(1, 1, '优质坚果礼盒', 'https://example.com/images/nut1.jpg', 128.00, 1, 128.00),
(1, 2, '有机果汁', 'https://example.com/images/juice1.jpg', 35.00, 1, 35.00);

-- 订单2包含1个商品
INSERT INTO `order_item` (`order_id`, `product_id`, `product_name`, `product_image`, `product_price`, `quantity`, `subtotal`) VALUES
(2, 2, '有机果汁', 'https://example.com/images/juice1.jpg', 35.00, 1, 35.00);



