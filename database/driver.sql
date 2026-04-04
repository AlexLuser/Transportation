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
