-- ============================================================
-- 配送批次表 & 批次明细表（多停靠末端路线版本）
-- 支持多订单合并、VRP路线规划、K-Means地理分组、Hub中转分段配送
-- ============================================================

-- 允许干线路线（segment_type=1）及多停靠末端路线（stop_count>1）的 order_id 为 NULL
ALTER TABLE `logistics_route`
  MODIFY COLUMN `order_id` BIGINT DEFAULT NULL
  COMMENT '关联订单ID（单订单路线必填；干线路线/多停靠末端路线为NULL）';

-- 添加多停靠末端路线所需字段（若尚未添加）
ALTER TABLE `logistics_route`
  ADD COLUMN IF NOT EXISTS `group_index` TINYINT DEFAULT NULL
    COMMENT '批次内末端分组编号（0,1,2...），同一组由一名司机负责',
  ADD COLUMN IF NOT EXISTS `stop_count` INT NOT NULL DEFAULT 1
    COMMENT '停靠点数量（1=单订单，>1=多停靠末端路线）',
  ADD COLUMN IF NOT EXISTS `waypoints` JSON DEFAULT NULL
    COMMENT '多停靠点有序列表：[{seq,orderId,address,lat,lng,receiverName,receiverPhone}]';

DROP TABLE IF EXISTS `logistics_batch_item`;
DROP TABLE IF EXISTS `logistics_batch`;

-- ============================================================
-- 1. 配送批次表
-- ============================================================
CREATE TABLE `logistics_batch` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '批次ID',
  `batch_no`        VARCHAR(30)  NOT NULL UNIQUE COMMENT '批次编号（LB+yyyyMMddHHmmss+4位随机）',
  `warehouse_id`    BIGINT       NOT NULL COMMENT '发货仓库ID',
  `hub_id`          BIGINT       DEFAULT NULL COMMENT '中转站ID（useHub=true时有值）',
  `trunk_route_id`  BIGINT       DEFAULT NULL COMMENT '干线路线ID（仓库→Hub，segment_type=1）',
  `batch_status`    TINYINT      NOT NULL DEFAULT 0
                    COMMENT '批次状态：0=待出发，1=干线运输中，2=已到中转站，3=末端派送中，4=全部完成',
  `total_orders`    INT          NOT NULL DEFAULT 0 COMMENT '批次内订单总数',
  `use_hub`         TINYINT      NOT NULL DEFAULT 1 COMMENT '是否经Hub中转：0=否（多点直送），1=是',
  `vrp_algorithm`   VARCHAR(50)  DEFAULT 'NEAREST_NEIGHBOR' COMMENT '使用的VRP算法',
  `total_distance`  DOUBLE       DEFAULT NULL COMMENT 'VRP规划总距离（米）',
  `remark`          VARCHAR(300) DEFAULT NULL COMMENT '备注',
  `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no`    (`batch_no`),
  INDEX `idx_warehouse_id`    (`warehouse_id`),
  INDEX `idx_hub_id`          (`hub_id`),
  INDEX `idx_batch_status`    (`batch_status`),
  INDEX `idx_create_time`     (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配送批次表';

-- ============================================================
-- 2. 批次订单明细表
--    每行 = 一个订单 = 末端路线的一个停靠点
--    多停靠末端路线：同组 item 共享同一个 route_id
-- ============================================================
CREATE TABLE `logistics_batch_item` (
  `id`             BIGINT  NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `batch_id`       BIGINT  NOT NULL COMMENT '所属批次ID',
  `order_id`       BIGINT  NOT NULL COMMENT '订单ID',
  `route_id`       BIGINT  DEFAULT NULL COMMENT '所属末端路线ID（单停靠=独立route；多停靠=同组共享route）',
  `visit_sequence` INT     NOT NULL DEFAULT 0 COMMENT '批次内全局VRP访问顺序（从1开始）',
  `stop_sequence`  INT     DEFAULT NULL COMMENT '组内停靠顺序（多停靠时有效，从1开始）',
  `end_lat`        DOUBLE  DEFAULT NULL COMMENT '目的地纬度（快照）',
  `end_lng`        DOUBLE  DEFAULT NULL COMMENT '目的地经度（快照）',
  `end_address`    VARCHAR(300) DEFAULT NULL COMMENT '目的地地址（快照）',
  `receiver_name`  VARCHAR(50)  DEFAULT NULL COMMENT '收货人姓名（快照）',
  `receiver_phone` VARCHAR(20)  DEFAULT NULL COMMENT '收货人电话（快照）',
  `item_status`    TINYINT NOT NULL DEFAULT 0
                   COMMENT '状态：0=待激活，1=末端派送中，2=已送达',
  `create_time`    DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_batch_id`   (`batch_id`),
  INDEX `idx_order_id`   (`order_id`),
  INDEX `idx_route_id`   (`route_id`),
  INDEX `idx_sequence`   (`batch_id`, `visit_sequence`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次订单明细表（每行=一个订单/末端停靠点）';
