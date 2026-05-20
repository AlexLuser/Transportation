-- ============================================================
-- 物流中转站（Hub）表
-- 实现 Hub-and-Spoke 枢纽辐射式物流模型
-- ============================================================

DROP TABLE IF EXISTS `logistics_hub`;

CREATE TABLE `logistics_hub` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '中转站ID',
  `name`         VARCHAR(100) NOT NULL COMMENT '中转站名称',
  `address`      VARCHAR(300) NOT NULL COMMENT '中转站地址',
  `latitude`     DOUBLE       NOT NULL COMMENT '纬度',
  `longitude`    DOUBLE       NOT NULL COMMENT '经度',
  `region`       VARCHAR(50)  DEFAULT NULL COMMENT '所属区域（如：浦东、虹桥、松江）',
  `max_capacity` INT          DEFAULT 1000 COMMENT '最大日处理包裹量',
  `current_load` INT          DEFAULT 0    COMMENT '当前待处理包裹数',
  `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0=正常，1=满载，2=关闭',
  `remark`       VARCHAR(300) DEFAULT NULL COMMENT '备注',
  `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_status`   (`status`),
  INDEX `idx_region`   (`region`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流中转站表';

-- 上海区域测试中转站（坐标均在 shanghai-260310.osm.pbf 覆盖范围内）
INSERT INTO `logistics_hub` (`name`, `address`, `latitude`, `longitude`, `region`, `max_capacity`, `status`) VALUES
('上海浦东分拨中心', '上海市浦东新区外环路6888号',  31.2180, 121.6200, '浦东',  2000, 0),
('上海虹桥分拨中心', '上海市闵行区申长路2600号',    31.1950, 121.3300, '虹桥',  1500, 0),
('上海松江分拨中心', '上海市松江区九亭镇九莘路',    31.0300, 121.2200, '松江',  1000, 0);
