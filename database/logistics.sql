-- 物流调度服务数据库表
-- 包含物流路线、实时轨迹、里程碑节点

-- 删除表（按依赖顺序）
DROP TABLE IF EXISTS `logistics_node`;
DROP TABLE IF EXISTS `logistics_track`;
DROP TABLE IF EXISTS `logistics_route`;

-- ============================================
-- 1. 物流路线表（一条订单对应一条路线）
-- ============================================
CREATE TABLE `logistics_route` (
  `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '路线ID',
  `route_no`              VARCHAR(30)  NOT NULL UNIQUE COMMENT '路线编号（LR+时间戳+4位随机，对外展示）',
  `order_id`              BIGINT       NOT NULL UNIQUE COMMENT '关联 order_info 表的 id（一订单一路线）',
  `delivery_id`           BIGINT       DEFAULT NULL COMMENT '关联 order_delivery 表的 id（接单后绑定）',
  `driver_id`             BIGINT       DEFAULT NULL COMMENT '关联运输员 ID（接单后绑定）',
  `warehouse_id`          BIGINT       DEFAULT NULL COMMENT '出发仓库 ID',
  `start_address`         VARCHAR(300) NOT NULL COMMENT '出发地址（仓库地址，快照）',
  `start_lat`             DOUBLE       DEFAULT NULL COMMENT '出发地纬度',
  `start_lng`             DOUBLE       DEFAULT NULL COMMENT '出发地经度',
  `end_address`           VARCHAR(300) NOT NULL COMMENT '目的地地址（收货地址，快照）',
  `end_lat`               DOUBLE       DEFAULT NULL COMMENT '目的地纬度',
  `end_lng`               DOUBLE       DEFAULT NULL COMMENT '目的地经度',
  `current_lat`           DOUBLE       DEFAULT NULL COMMENT '当前位置纬度（实时更新）',
  `current_lng`           DOUBLE       DEFAULT NULL COMMENT '当前位置经度（实时更新）',
  `current_address`       VARCHAR(300) DEFAULT NULL COMMENT '当前位置描述（逆地理编码，实时更新）',
  `last_track_time`       DATETIME     DEFAULT NULL COMMENT '最后位置更新时间',
  `route_status`          TINYINT      DEFAULT 0 COMMENT '路线状态：0=待出发，1=运输中，2=已送达，3=异常',
  `planned_distance`      DOUBLE       DEFAULT NULL COMMENT '计划距离（km）',
  `actual_distance`       DOUBLE       DEFAULT NULL COMMENT '实际距离（km，送达后统计）',
  `planned_duration`      INT          DEFAULT NULL COMMENT '计划时长（分钟）',
  `estimated_arrival_time` DATETIME    DEFAULT NULL COMMENT '预计到达时间（动态更新）',
  `actual_arrival_time`   DATETIME     DEFAULT NULL COMMENT '实际到达时间',
  `planned_route`         LONGTEXT     DEFAULT NULL COMMENT '计划路线（GeoJSON LineString）',
  `ai_suggested_route`    LONGTEXT     DEFAULT NULL COMMENT 'AI建议路线（GeoJSON，预留大模型接口）',
  `ai_analysis`           TEXT         DEFAULT NULL COMMENT 'AI分析结果（JSON，预留大模型接口）',
  `receiver_name`         VARCHAR(50)  DEFAULT NULL COMMENT '收货人姓名（快照）',
  `receiver_phone`        VARCHAR(20)  DEFAULT NULL COMMENT '收货人电话（快照）',
  `remark`                VARCHAR(500) DEFAULT NULL COMMENT '路线备注',
  `create_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_route_no`   (`route_no`),
  UNIQUE KEY `uk_order_id`   (`order_id`),
  INDEX `idx_driver_id`      (`driver_id`),
  INDEX `idx_warehouse_id`   (`warehouse_id`),
  INDEX `idx_route_status`   (`route_status`),
  INDEX `idx_create_time`    (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流路线表';

-- ============================================
-- 2. 实时轨迹点表（高频写入，按路线分区存储）
-- ============================================
CREATE TABLE `logistics_track` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '轨迹点ID',
  `route_id`    BIGINT   NOT NULL COMMENT '关联 logistics_route 表的 id',
  `driver_id`   BIGINT   NOT NULL COMMENT '运输员ID',
  `latitude`    DOUBLE   NOT NULL COMMENT '纬度',
  `longitude`   DOUBLE   NOT NULL COMMENT '经度',
  `altitude`    DOUBLE   DEFAULT NULL COMMENT '海拔（米）',
  `speed`       DOUBLE   DEFAULT NULL COMMENT '速度（km/h）',
  `heading`     DOUBLE   DEFAULT NULL COMMENT '方向角（0-360度，0=正北）',
  `accuracy`    DOUBLE   DEFAULT NULL COMMENT 'GPS精度（米）',
  `address`     VARCHAR(300) DEFAULT NULL COMMENT '当前位置描述（前端逆地理编码传入）',
  `track_time`  DATETIME NOT NULL COMMENT '位置上报时间（客户端时间）',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_route_id_time` (`route_id`, `track_time`),
  INDEX `idx_driver_id`     (`driver_id`),
  INDEX `idx_track_time`    (`track_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实时轨迹点表'
  ROW_FORMAT=COMPRESSED;   -- 压缩存储，节省空间（轨迹数据量大）

-- ============================================
-- 3. 里程碑节点表（出发点、途经点、目的地）
-- ============================================
CREATE TABLE `logistics_node` (
  `id`                  BIGINT   NOT NULL AUTO_INCREMENT COMMENT '节点ID',
  `route_id`            BIGINT   NOT NULL COMMENT '关联 logistics_route 表的 id',
  `node_type`           TINYINT  NOT NULL COMMENT '节点类型：0=出发点，1=途经点，2=目的地',
  `node_name`           VARCHAR(100) NOT NULL COMMENT '节点名称',
  `node_address`        VARCHAR(300) DEFAULT NULL COMMENT '节点地址',
  `latitude`            DOUBLE   DEFAULT NULL COMMENT '节点纬度',
  `longitude`           DOUBLE   DEFAULT NULL COMMENT '节点经度',
  `sequence_no`         INT      DEFAULT 0 COMMENT '顺序号（升序排列）',
  `planned_arrive_time` DATETIME DEFAULT NULL COMMENT '计划到达时间',
  `actual_arrive_time`  DATETIME DEFAULT NULL COMMENT '实际到达时间',
  `node_status`         TINYINT  DEFAULT 0 COMMENT '节点状态：0=未到达，1=已到达，2=已跳过',
  `remark`              VARCHAR(300) DEFAULT NULL COMMENT '节点备注',
  `create_time`         DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_route_id`  (`route_id`),
  INDEX `idx_node_type` (`node_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流里程碑节点表';

-- ============================================
-- 更新 warehouse 表：增加经纬度字段（用于物流路线起点精确定位）
-- ============================================
ALTER TABLE `warehouse`
  ADD COLUMN `latitude`  DOUBLE DEFAULT NULL COMMENT '仓库纬度（用于物流路线规划）' AFTER `status`,
  ADD COLUMN `longitude` DOUBLE DEFAULT NULL COMMENT '仓库经度（用于物流路线规划）' AFTER `latitude`;

-- 更新测试数据：为已有仓库补充模拟经纬度
UPDATE `warehouse` SET `latitude` = 39.9042,  `longitude` = 116.4074 WHERE `id` = 1;  -- 北京中央仓库
UPDATE `warehouse` SET `latitude` = 31.2304,  `longitude` = 121.4737 WHERE `id` = 2;  -- 上海华东仓库
UPDATE `warehouse` SET `latitude` = 23.1291,  `longitude` = 113.2644 WHERE `id` = 3;  -- 广州华南仓库

-- ============================================
-- 更新 order_info 表：增加 warehouse_id 字段（记录发货仓库）
-- ============================================
ALTER TABLE `order_info`
  ADD COLUMN `warehouse_id` BIGINT DEFAULT NULL COMMENT '发货仓库ID（下单时扣减库存的仓库，物流路线起点）'
  AFTER `remark`;

-- ============================================
-- 插入测试数据（模拟一条已在运输中的物流路线）
-- ============================================
INSERT INTO `logistics_route`
  (`route_no`, `order_id`, `start_address`, `start_lat`, `start_lng`,
   `end_address`, `end_lat`, `end_lng`, `receiver_name`, `receiver_phone`,
   `route_status`, `create_time`)
VALUES
  ('LR20260310000001', 1,
   '北京市朝阳区物流园区A区1号', 39.9042, 116.4074,
   '北京市海淀区中关村大街1号', 39.9827, 116.3033,
   '张三', '13800138000',
   1, NOW());

