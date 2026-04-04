-- ============================================================
-- 物流调度服务数据库表
-- 模块：路径规划
-- 包含：物流路线表、里程碑节点表、实时轨迹表
-- ============================================================

-- 按依赖顺序删除旧表
DROP TABLE IF EXISTS `logistics_node`;
DROP TABLE IF EXISTS `logistics_track`;
DROP TABLE IF EXISTS `logistics_route`;


-- ============================================================
-- 1. 物流路线表
--    一个订单对应一条物流路线，记录从仓库到收货地址的全程信息
--    - 地址和坐标均保存快照（下单时冻结）
--    - planned_route 存储 GeoJSON LineString（GraphHopper 规划结果）
--    - route_status 驱动整个配送状态机
-- ============================================================
CREATE TABLE `logistics_route` (
  `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '路线ID（主键）',
  `route_no`              VARCHAR(30)  NOT NULL UNIQUE COMMENT '路线编号（LR+yyyyMMddHHmmss+4位随机）',
  `order_id`              BIGINT       NOT NULL UNIQUE COMMENT '关联订单ID（一个订单只能有一条路线）',
  `delivery_id`           BIGINT       DEFAULT NULL COMMENT '关联配送记录ID（接单后绑定）',
  `driver_id`             BIGINT       DEFAULT NULL COMMENT '运输员ID（接单后绑定）',
  `warehouse_id`          BIGINT       DEFAULT NULL COMMENT '出发仓库ID',

  -- 出发地信息（仓库）
  `start_address`         VARCHAR(300) NOT NULL COMMENT '出发地址（仓库地址快照）',
  `start_lat`             DOUBLE       DEFAULT NULL COMMENT '出发地纬度',
  `start_lng`             DOUBLE       DEFAULT NULL COMMENT '出发地经度',

  -- 目的地信息（收货地址）
  `end_address`           VARCHAR(300) NOT NULL COMMENT '目的地址（收货地址快照）',
  `end_lat`               DOUBLE       DEFAULT NULL COMMENT '目的地纬度',
  `end_lng`               DOUBLE       DEFAULT NULL COMMENT '目的地经度',

  -- 实时位置（运输中持续更新）
  `current_lat`           DOUBLE       DEFAULT NULL COMMENT '当前位置纬度',
  `current_lng`           DOUBLE       DEFAULT NULL COMMENT '当前位置经度',
  `current_address`       VARCHAR(300) DEFAULT NULL COMMENT '当前位置描述（逆地理编码结果）',
  `last_track_time`       DATETIME     DEFAULT NULL COMMENT '最后一次位置更新时间',

  -- 状态机：0=待出发，1=运输中，2=已送达，3=异常
  `route_status`          TINYINT      NOT NULL DEFAULT 0 COMMENT '路线状态：0=待出发，1=运输中，2=已送达，3=异常',

  -- 时间预估
  `estimated_arrival_time` DATETIME   DEFAULT NULL COMMENT '预计到达时间',
  `actual_arrival_time`   DATETIME    DEFAULT NULL COMMENT '实际到达时间',

  -- 路线数据（GeoJSON）
  `planned_route`         LONGTEXT     DEFAULT NULL COMMENT '计划路线（GeoJSON LineString，由 GraphHopper 生成）',

  -- 收货人信息快照
  `receiver_name`         VARCHAR(50)  DEFAULT NULL COMMENT '收货人姓名',
  `receiver_phone`        VARCHAR(20)  DEFAULT NULL COMMENT '收货人电话',
  `remark`                VARCHAR(500) DEFAULT NULL COMMENT '备注',

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


-- ============================================================
-- 2. 里程碑节点表
--    路线上的关键节点：出发点（type=0）、途经点（type=1）、目的地（type=2）
-- ============================================================
CREATE TABLE `logistics_node` (
  `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '节点ID',
  `route_id`              BIGINT       NOT NULL COMMENT '所属路线ID',
  `node_type`             TINYINT      NOT NULL COMMENT '节点类型：0=出发点，1=途经点，2=目的地',
  `node_name`             VARCHAR(100) NOT NULL COMMENT '节点名称',
  `node_address`          VARCHAR(300) DEFAULT NULL COMMENT '节点地址',
  `latitude`              DOUBLE       DEFAULT NULL COMMENT '节点纬度',
  `longitude`             DOUBLE       DEFAULT NULL COMMENT '节点经度',
  `sequence_no`           INT          NOT NULL DEFAULT 0 COMMENT '顺序号（0=出发点，99=目的地）',
  `planned_arrive_time`   DATETIME     DEFAULT NULL COMMENT '计划到达时间',
  `actual_arrive_time`    DATETIME     DEFAULT NULL COMMENT '实际到达时间',
  `node_status`           TINYINT      NOT NULL DEFAULT 0 COMMENT '节点状态：0=未到达，1=已到达，2=已跳过',
  `remark`                VARCHAR(300) DEFAULT NULL COMMENT '节点备注',
  `create_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_route_id`    (`route_id`),
  INDEX `idx_node_type`   (`node_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='里程碑节点表';


-- ============================================================
-- 3. 实时轨迹表
--    运输员 App 每15~30秒上报一次GPS，高频写入，ROW_FORMAT=COMPRESSED 压缩存储
-- ============================================================
CREATE TABLE `logistics_track` (
  `id`          BIGINT    NOT NULL AUTO_INCREMENT COMMENT '轨迹点ID',
  `route_id`    BIGINT    NOT NULL COMMENT '所属路线ID',
  `driver_id`   BIGINT    NOT NULL COMMENT '运输员ID',
  `latitude`    DOUBLE    NOT NULL COMMENT '纬度',
  `longitude`   DOUBLE    NOT NULL COMMENT '经度',
  `altitude`    DOUBLE    DEFAULT NULL COMMENT '海拔（米，可选）',
  `speed`       DOUBLE    DEFAULT NULL COMMENT '速度（km/h）',
  `heading`     DOUBLE    DEFAULT NULL COMMENT '方向角（0=正北，顺时针）',
  `accuracy`    DOUBLE    DEFAULT NULL COMMENT 'GPS精度（米，值越小越精确）',
  `address`     VARCHAR(300) DEFAULT NULL COMMENT '位置描述（逆地理编码结果，由客户端传入）',
  `track_time`  DATETIME  NOT NULL COMMENT 'GPS上报时间（客户端本地时间）',
  `create_time` DATETIME  DEFAULT CURRENT_TIMESTAMP COMMENT '服务端接收时间',
  PRIMARY KEY (`id`),
  INDEX `idx_route_id_time` (`route_id`, `track_time`),
  INDEX `idx_driver_id`     (`driver_id`),
  INDEX `idx_track_time`    (`track_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实时轨迹点表'
  ROW_FORMAT=COMPRESSED;


-- ============================================================
-- 测试数据说明
-- ----------------------------------------------------------------
-- 完整测试场景：
--   顾客张三（customer_id=1）在优质食品店（shop_id=1）下单
--   订单 ORD20260313000001（order_id=1）已支付并发货
--   从上海华东仓库（warehouse_id=2, 浦东新区物流园区B区2号）发出
--   配送至上海浦东新区陆家嘴环路1000号（address_id=1）
--   运输员李四（driver_id=1）驾驶 沪A12345（vehicle_id=1）正在运输中
--
-- 坐标范围（均在上海浦东区域，与 shanghai-260310.osm.pbf 匹配）：
--   上海华东仓库：         lat=31.1985, lng=121.5889
--   途中-张杨路附近：      lat=31.2100, lng=121.5640
--   途中-世纪大道附近：    lat=31.2256, lng=121.5350（当前位置）
--   目的地-陆家嘴环路：    lat=31.2356, lng=121.5050
--
-- 可用接口测试：
--   GET  /api/logistics/routes/1               查路线详情（routeId=1）
--   GET  /api/logistics/routes/order/1         按订单ID查询（orderId=1）
--   GET  /api/logistics/routes/no/LR202603131200000001  按物流单号查询
--   GET  /api/logistics/track/1/latest         查最新轨迹位置
--   GET  /api/logistics/track/1/history        查完整轨迹历史
--   POST /api/logistics/routes                 用 order_id=2 创建新路线（触发GraphHopper规划）
--   PUT  /api/logistics/routes/1/status        更新路线状态（需 admin/driver 角色）
-- ============================================================

-- 物流路线（route_id=1，运输中，与 order_delivery.id=1 对应）
INSERT INTO `logistics_route`
  (`route_no`, `order_id`, `delivery_id`, `driver_id`, `warehouse_id`,
   `start_address`,  `start_lat`, `start_lng`,
   `end_address`,    `end_lat`,   `end_lng`,
   `current_lat`,    `current_lng`, `current_address`, `last_track_time`,
   `route_status`, `estimated_arrival_time`, `actual_arrival_time`,
   `planned_route`,
   `receiver_name`, `receiver_phone`, `remark`, `create_time`)
VALUES
(
  'LR202603131200000001',
  1, 1, 1, 2,
  -- 出发地：上海华东仓库
  '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
  -- 目的地：张三收货地址
  '上海市浦东新区陆家嘴环路1000号',              31.2356, 121.5050,
  -- 当前位置：途中世纪大道附近（与最新轨迹点一致）
  31.2256, 121.5350, '上海市浦东新区世纪大道附近', DATE_SUB(NOW(), INTERVAL 15 MINUTE),
  -- 状态：运输中；预计45分钟后到达；actual_arrival_time=NULL（未送达）
  1, DATE_ADD(NOW(), INTERVAL 45 MINUTE), NULL,
  -- planned_route：GeoJSON LineString（浦东仓库→张杨路→世纪大道→陆家嘴，简化4段路线）
  '{"type":"LineString","coordinates":[[121.5889,31.1985],[121.5640,31.2100],[121.5350,31.2256],[121.5050,31.2356]]}',
  '张三', '13800138000', NULL,
  DATE_SUB(NOW(), INTERVAL 60 MINUTE)           -- 路线创建时间（1小时前）
);

-- 里程碑节点（route_id=1）
INSERT INTO `logistics_node`
  (`route_id`, `node_type`, `node_name`, `node_address`, `latitude`, `longitude`,
   `sequence_no`, `planned_arrive_time`, `actual_arrive_time`, `node_status`)
VALUES
-- 出发点：已离开仓库
(1, 0, '出发仓库',
 '上海市浦东新区物流园区B区2号（上海华东仓库）', 31.1985, 121.5889,
  0,
  DATE_SUB(NOW(), INTERVAL 60 MINUTE),   -- 计划1小时前出发
  DATE_SUB(NOW(), INTERVAL 60 MINUTE),   -- 实际1小时前出发
  1),                                    -- 已到达（已出发）
-- 目的地：未到达
(1, 2, '收货地址',
 '上海市浦东新区陆家嘴环路1000号', 31.2356, 121.5050,
  99,
  DATE_ADD(NOW(), INTERVAL 45 MINUTE),   -- 预计45分钟后到达
  NULL,
  0);                                    -- 未到达

-- 实时轨迹（route_id=1）
-- 本次运输（今日，当前时间附近）+ 历史轨迹（过去30天内不同时段，
-- 为 selectAvgSpeedByHour 提供当前小时及周边小时的基础样本量）
INSERT INTO `logistics_track`
  (`route_id`, `driver_id`, `latitude`, `longitude`,
   `altitude`, `speed`, `heading`, `accuracy`, `address`, `track_time`)
VALUES
-- ── 本次运输（当日实时轨迹，3条）──
-- 第1点：从仓库出发（speed=0，准备发车）
(1, 1, 31.1985, 121.5889, 6.0,  0.0, 340.0, 5.0,
 '上海市浦东新区物流园区B区2号',
 DATE_SUB(NOW(), INTERVAL 60 MINUTE)),
-- 第2点：途经张杨路
(1, 1, 31.2100, 121.5640, 6.2, 38.5, 332.0, 4.5,
 '上海市浦东新区张杨路附近',
 DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
-- 第3点：途经世纪大道（当前最新位置）
(1, 1, 31.2256, 121.5350, 6.5, 42.0, 320.0, 4.0,
 '上海市浦东新区世纪大道附近',
 DATE_SUB(NOW(), INTERVAL 15 MINUTE)),
-- ── 历史轨迹（过去30天内，覆盖不同小时，为 LLM 提供全天基础速度样本）──
-- 早高峰（07-08时）参考轨迹：浦东南路附近，速度偏低
(1, 1, 31.1950, 121.5150, 6.1, 12.0, 275.0, 5.0, '上海市浦东新区浦东南路',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL  3 DAY), '07:22:00')),
(1, 1, 31.1960, 121.5080, 6.1,  8.5, 268.0, 5.0, '上海市浦东南路商城路附近',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL  5 DAY), '08:10:00')),
(1, 1, 31.2002, 121.5100, 6.2, 14.0, 290.0, 4.5, '上海市延安路隧道出口',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL  7 DAY), '07:45:00')),
-- 上午平峰（09-11时）：速度较高
(1, 1, 31.2100, 121.5640, 6.2, 36.0, 332.0, 4.5, '上海市浦东新区张杨路',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL  2 DAY), '09:30:00')),
(1, 1, 31.2256, 121.5350, 6.5, 40.0, 320.0, 4.0, '上海市浦东新区世纪大道',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL  4 DAY), '10:15:00')),
(1, 1, 31.2300, 121.5200, 6.6, 38.0, 315.0, 4.0, '上海市浦东新区陆家嘴西路',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL  6 DAY), '11:00:00')),
-- 午间（12-14时）：平均速度
(1, 1, 31.2088, 121.4968, 6.4, 28.0, 310.0, 4.5, '上海市黄浦区西藏中路',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL  8 DAY), '12:30:00')),
(1, 1, 31.2192, 121.4885, 6.5, 25.0, 305.0, 4.5, '上海市黄浦区人民广场',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 10 DAY), '13:20:00')),
-- 下午平峰（15-16时）：速度较高
(1, 1, 31.2356, 121.5050, 6.7, 42.0, 295.0, 4.0, '上海市浦东新区陆家嘴',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 12 DAY), '15:10:00')),
(1, 1, 31.1985, 121.5889, 6.0, 39.0, 10.0,  4.0, '上海市浦东新区物流园区',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 14 DAY), '16:00:00')),
-- 晚高峰（17-19时）：速度偏低
(1, 1, 31.1932, 121.5150, 6.1, 10.0, 275.0, 5.0, '上海市浦东南路（晚高峰）',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL  1 DAY), '17:30:00')),
(1, 1, 31.1960, 121.5280, 6.1,  6.5, 270.0, 5.5, '上海市延安路隧道浦东（拥堵）',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL  9 DAY), '18:05:00')),
(1, 1, 31.2040, 121.4900, 6.3, 15.0, 285.0, 5.0, '上海市延安中路',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 11 DAY), '18:50:00')),
-- 夜间（20-23时）：畅通
(1, 1, 31.2195, 121.4540, 6.4, 44.0, 280.0, 4.0, '上海市曹杨路',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 15 DAY), '20:20:00')),
(1, 1, 31.2358, 121.4200, 6.5, 46.0, 270.0, 4.0, '上海市普陀云岭东路',
 TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL 20 DAY), '22:10:00'));
