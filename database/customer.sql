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
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_customer_id` (`customer_id`),
  INDEX `idx_is_default` (`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址表';

-- 插入测试数据
-- 注意：user_id=2 对应user表中的customer用户（根据user.sql中的插入顺序）
-- 如果user表中的customer用户id不是2，请根据实际情况修改

-- 插入顾客信息测试数据
INSERT INTO `customer_info` (`user_id`, `real_name`, `phone`, `email`, `gender`, `birthday`, `status`) VALUES
(2, '张三', '13800138000', 'zhangsan@example.com', 1, '1990-05-15', 1);  -- 关联user表中customer用户（user_id=2）

-- 插入收货地址测试数据
-- 注意：customer_id=1 对应上面插入的customer_info记录
INSERT INTO `customer_address` (`customer_id`, `receiver_name`, `receiver_phone`, `province`, `city`, `district`, `detail_address`, `postal_code`, `is_default`) VALUES
(1, '张三', '13800138000', '北京市', '北京市', '朝阳区', '朝阳区建国路88号SOHO现代城A座1001室', '100025', 1),  -- 默认地址
(1, '张三', '13800138001', '上海市', '上海市', '浦东新区', '浦东新区陆家嘴环路1000号', '200120', 0);  -- 非默认地址

