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

