-- =============================================================================
-- MCMF 全国流量规划 — 专用测试数据（在 all.sql 初始化完成后执行）
--
-- 内容概要：
--   1）多城市收货地址（经纬度贴近 national_hub，与 ShipmentRouting 最近 Hub 一致）
--   2）依赖 all.sql 种子：上海仓 warehouse_id=2 / shop_id=1；北京仓 warehouse_id=4 / shop_id=3（京北优品铺）
--   3）约 68 笔跨城待揽件单（当日 create_time），京→沪单使用进京有机果汁 product_id=6
--
-- 使用：mysql -h127.0.0.1 -P3307 -uadmin -p transportation < database/mcmf_test_data.sql
--       可重复执行：删除 MCMF-TEST-% 订单；地址采用 NOT EXISTS 幂等插入。
-- =============================================================================

USE `transportation`;
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ---------------------------------------------------------------------------
-- 收货地址（customer_info.id=1 即种子「张三」；与后续手工下单共用同一顾客体系）
-- 坐标取各城市中心附近，与 all.sql 中 national_hub 一致，便于与真实 assignHubs 对照
-- ---------------------------------------------------------------------------
INSERT INTO `customer_address`
  (`customer_id`, `receiver_name`, `receiver_phone`, `province`, `city`, `district`, `detail_address`, `postal_code`, `is_default`, `latitude`, `longitude`)
SELECT 1, '张三', '13800138000', '北京市', '北京市', '朝阳区', '朝阳区建国路88号', '100025', 0, 39.9042, 116.4074
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `customer_address` WHERE `customer_id` = 1 AND `city` = '北京市' LIMIT 1);

INSERT INTO `customer_address`
  (`customer_id`, `receiver_name`, `receiver_phone`, `province`, `city`, `district`, `detail_address`, `postal_code`, `is_default`, `latitude`, `longitude`)
SELECT 1, '张三', '13800138000', '广东省', '广州市', '天河区', '天河区体育西路101号', '510620', 0, 23.1291, 113.2644
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `customer_address` WHERE `customer_id` = 1 AND `city` = '广州市' LIMIT 1);

INSERT INTO `customer_address`
  (`customer_id`, `receiver_name`, `receiver_phone`, `province`, `city`, `district`, `detail_address`, `postal_code`, `is_default`, `latitude`, `longitude`)
SELECT 1, '张三', '13800138000', '江苏省', '南京市', '玄武区', '玄武区珠江路1号', '210008', 0, 32.0603, 118.7969
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `customer_address` WHERE `customer_id` = 1 AND `city` = '南京市' LIMIT 1);

INSERT INTO `customer_address`
  (`customer_id`, `receiver_name`, `receiver_phone`, `province`, `city`, `district`, `detail_address`, `postal_code`, `is_default`, `latitude`, `longitude`)
SELECT 1, '张三', '13800138000', '四川省', '成都市', '锦江区', '锦江区红星路三段99号', '610021', 0, 30.5723, 104.0665
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `customer_address` WHERE `customer_id` = 1 AND `city` = '成都市' LIMIT 1);

INSERT INTO `customer_address`
  (`customer_id`, `receiver_name`, `receiver_phone`, `province`, `city`, `district`, `detail_address`, `postal_code`, `is_default`, `latitude`, `longitude`)
SELECT 1, '张三', '13800138000', '陕西省', '西安市', '雁塔区', '雁塔区高新路52号', '710075', 0, 34.3416, 108.9398
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `customer_address` WHERE `customer_id` = 1 AND `city` = '西安市' LIMIT 1);

INSERT INTO `customer_address`
  (`customer_id`, `receiver_name`, `receiver_phone`, `province`, `city`, `district`, `detail_address`, `postal_code`, `is_default`, `latitude`, `longitude`)
SELECT 1, '张三', '13800138000', '广东省', '深圳市', '福田区', '福田区深南大道1006号', '518033', 0, 22.5431, 114.0579
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `customer_address` WHERE `customer_id` = 1 AND `city` = '深圳市' LIMIT 1);

SET @addr_shanghai  = (SELECT `id` FROM `customer_address` WHERE `customer_id` = 1 AND `city` = '上海市' AND `district` = '浦东新区' LIMIT 1);
SET @addr_beijing   = (SELECT `id` FROM `customer_address` WHERE `customer_id` = 1 AND `city` = '北京市' LIMIT 1);
SET @addr_guangzhou = (SELECT `id` FROM `customer_address` WHERE `customer_id` = 1 AND `city` = '广州市' LIMIT 1);
SET @addr_nanjing   = (SELECT `id` FROM `customer_address` WHERE `customer_id` = 1 AND `city` = '南京市' LIMIT 1);
SET @addr_chengdu   = (SELECT `id` FROM `customer_address` WHERE `customer_id` = 1 AND `city` = '成都市' LIMIT 1);
SET @addr_xian      = (SELECT `id` FROM `customer_address` WHERE `customer_id` = 1 AND `city` = '西安市' LIMIT 1);
SET @addr_shenzhen  = (SELECT `id` FROM `customer_address` WHERE `customer_id` = 1 AND `city` = '深圳市' LIMIT 1);

-- 与 all.sql 固定种子一致：上海华东仓、北京大兴履约中心、京北优品铺
SET @wh_shanghai    = 2;
SET @wh_beijing     = 4;
SET @shop_shanghai  = 1;
SET @shop_beijing   = 3;

-- ---------------------------------------------------------------------------
-- 清理旧测试订单及关联的作业记录
-- ---------------------------------------------------------------------------
-- Hub 作业记录（先于 order_info 删除，避免残留孤立记录）
DELETE hir FROM `hub_inbound_record` hir
  INNER JOIN `order_info` o ON hir.`order_id` = o.`id`
  WHERE o.`order_no` LIKE 'MCMF-TEST-%';
DELETE hsr FROM `hub_sorting_record` hsr
  INNER JOIN `order_info` o ON hsr.`order_id` = o.`id`
  WHERE o.`order_no` LIKE 'MCMF-TEST-%';
DELETE hor FROM `hub_outbound_record` hor
  INNER JOIN `order_info` o ON hor.`order_id` = o.`id`
  WHERE o.`order_no` LIKE 'MCMF-TEST-%';
-- 调度池
DELETE dp FROM `dispatch_pool` dp
  INNER JOIN `order_info` o ON dp.`order_id` = o.`id`
  WHERE o.`order_no` LIKE 'MCMF-TEST-%';
DELETE oi FROM `order_item` oi
  INNER JOIN `order_info` o ON oi.`order_id` = o.`id`
  WHERE o.`order_no` LIKE 'MCMF-TEST-%';
DELETE FROM `order_info` WHERE `order_no` LIKE 'MCMF-TEST-%';

-- ---------------------------------------------------------------------------
-- 批量插入：序号 1～N 对应不同 OD，与 hub_link 网络（上海/北京/广州/武汉/郑州…）形成可分摊流量
-- 规则：
--   上海 Hub(3) 发出：shop_id=1、warehouse_id=2，与优质食品店种子一致
--   北京 Hub(4) 发出：shop_id=3、warehouse_id=4（京北优品铺 / 北京大兴履约中心），收货上海浦东
-- ---------------------------------------------------------------------------
SET @tid = DATE_FORMAT(NOW(), '%Y%m%d%H%i%s');

DROP TEMPORARY TABLE IF EXISTS `_mcmf_orders`;
CREATE TEMPORARY TABLE `_mcmf_orders` (
  `seq` INT NOT NULL PRIMARY KEY,
  `address_id` BIGINT NOT NULL,
  `warehouse_id` BIGINT NOT NULL,
  `origin_hub_id` BIGINT NOT NULL,
  `dest_hub_id` BIGINT NOT NULL,
  `shop_id` BIGINT NOT NULL,
  `remark` VARCHAR(120) NOT NULL
);

-- 1～18：沪(3)→京(4)
INSERT INTO `_mcmf_orders` (`seq`, `address_id`, `warehouse_id`, `origin_hub_id`, `dest_hub_id`, `shop_id`, `remark`)
SELECT n.n, @addr_beijing, @wh_shanghai, 3, 4, @shop_shanghai, CONCAT('MCMF批量 沪→京 #', n.n)
FROM (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
      UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15 UNION SELECT 16 UNION SELECT 17 UNION SELECT 18) n;

-- 19～32：沪→穗(5)
INSERT INTO `_mcmf_orders` (`seq`, `address_id`, `warehouse_id`, `origin_hub_id`, `dest_hub_id`, `shop_id`, `remark`)
SELECT 18 + n.n, @addr_guangzhou, @wh_shanghai, 3, 5, @shop_shanghai, CONCAT('MCMF批量 沪→穗 #', n.n)
FROM (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
      UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14) n;

-- 33～44：沪→宁(9)
INSERT INTO `_mcmf_orders` (`seq`, `address_id`, `warehouse_id`, `origin_hub_id`, `dest_hub_id`, `shop_id`, `remark`)
SELECT 32 + n.n, @addr_nanjing, @wh_shanghai, 3, 9, @shop_shanghai, CONCAT('MCMF批量 沪→宁 #', n.n)
FROM (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
      UNION SELECT 11 UNION SELECT 12) n;

-- 45～52：沪→蓉(6)
INSERT INTO `_mcmf_orders` (`seq`, `address_id`, `warehouse_id`, `origin_hub_id`, `dest_hub_id`, `shop_id`, `remark`)
SELECT 44 + n.n, @addr_chengdu, @wh_shanghai, 3, 6, @shop_shanghai, CONCAT('MCMF批量 沪→蓉 #', n.n)
FROM (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8) n;

-- 53～58：沪→深(8)（经全国网可与广州/武汉等边组合）
INSERT INTO `_mcmf_orders` (`seq`, `address_id`, `warehouse_id`, `origin_hub_id`, `dest_hub_id`, `shop_id`, `remark`)
SELECT 52 + n.n, @addr_shenzhen, @wh_shanghai, 3, 8, @shop_shanghai, CONCAT('MCMF批量 沪→深 #', n.n)
FROM (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6) n;

-- 59～63：京(4)→沪(3)，京北优品铺 + 北京大兴履约中心
INSERT INTO `_mcmf_orders` (`seq`, `address_id`, `warehouse_id`, `origin_hub_id`, `dest_hub_id`, `shop_id`, `remark`)
SELECT 58 + n.n, @addr_shanghai, @wh_beijing, 4, 3, @shop_beijing, CONCAT('MCMF批量 京→沪 #', n.n)
FROM (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) n;

-- 64～68：沪→西安(7)（利用武汉/郑州—西安边）
INSERT INTO `_mcmf_orders` (`seq`, `address_id`, `warehouse_id`, `origin_hub_id`, `dest_hub_id`, `shop_id`, `remark`)
SELECT 63 + n.n, @addr_xian, @wh_shanghai, 3, 7, @shop_shanghai, CONCAT('MCMF批量 沪→陕 #', n.n)
FROM (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) n;

INSERT INTO `order_info`
  (`order_no`, `customer_id`, `shop_id`, `address_id`, `warehouse_id`,
   `origin_hub_id`, `dest_hub_id`, `flow_plan_id`, `inter_city_batch_id`,
   `total_amount`, `product_amount`, `shipping_fee`,
   `order_status`, `payment_status`, `payment_time`, `shipping_time`,
   `remark`, `customer_deleted`, `create_time`)
SELECT
  CONCAT('MCMF-TEST-', @tid, '-', LPAD(m.`seq`, 3, '0')),
  1,
  m.`shop_id`,
  m.`address_id`,
  m.`warehouse_id`,
  m.`origin_hub_id`,
  m.`dest_hub_id`,
  NULL,
  NULL,
  35.00,
  35.00,
  0.00,
  2,
  1,
  NOW(),
  NOW(),
  m.`remark`,
  0,
  NOW()
FROM `_mcmf_orders` m;

DROP TEMPORARY TABLE IF EXISTS `_mcmf_orders`;

-- ---------------------------------------------------------------------------
-- 初始化调度池：将所有测试订单写入 dispatch_pool（status=0 揽收/待调度）
-- 此时 hub_inbound_record / hub_sorting_record / hub_outbound_record 均为空，
-- 对应"商家已出库，司机揽收中，尚未到达配送中心"的状态，便于逐步演示完整流程。
-- ---------------------------------------------------------------------------
INSERT INTO `dispatch_pool`
  (`order_id`, `shop_id`, `warehouse_id`,
   `end_address`, `end_lat`, `end_lng`,
   `receiver_name`, `receiver_phone`,
   `remark`, `status`,
   `origin_hub_id`, `dest_hub_id`, `is_cross_city`,
   `dispatch_origin_type`)
SELECT
  o.`id`,
  o.`shop_id`,
  o.`warehouse_id`,
  CONCAT(a.`province`, a.`city`, a.`district`, a.`detail_address`),
  a.`latitude`,
  a.`longitude`,
  a.`receiver_name`,
  a.`receiver_phone`,
  o.`remark`,
  0,
  o.`origin_hub_id`,
  o.`dest_hub_id`,
  1,
  0
FROM `order_info` o
JOIN `customer_address` a ON o.`address_id` = a.`id`
WHERE o.`order_no` LIKE CONCAT('MCMF-TEST-', @tid, '-%') COLLATE utf8mb4_unicode_ci;

INSERT INTO `order_item` (`order_id`, `product_id`, `product_name`, `product_image`, `product_price`, `quantity`, `subtotal`)
SELECT
  o.`id`,
  p.`id`,
  p.`product_name`,
  IF(p.`id` = 6, 'https://example.com/images/juice1.jpg', 'https://example.com/images/juice1.jpg'),
  p.`price`,
  1,
  p.`price`
FROM `order_info` o
JOIN `product_info` p ON p.`id` = IF(o.`shop_id` = @shop_beijing, 6, 2)
WHERE o.`order_no` LIKE CONCAT('MCMF-TEST-', @tid, '-%') COLLATE utf8mb4_unicode_ci;

SELECT CONCAT(
  'MCMF: inserted ',
  (SELECT COUNT(*) FROM `order_info` WHERE `order_no` LIKE CONCAT('MCMF-TEST-', @tid, '-%') COLLATE utf8mb4_unicode_ci),
  ' orders, ',
  (SELECT COUNT(*) FROM `dispatch_pool` dp
     INNER JOIN `order_info` o ON dp.`order_id` = o.`id`
     WHERE o.`order_no` LIKE CONCAT('MCMF-TEST-', @tid, '-%') COLLATE utf8mb4_unicode_ci),
  ' dispatch_pool entries (status=0 揽收中).',
  ' Shanghai shop=', @shop_shanghai, ' WH=', @wh_shanghai,
  '; Beijing shop=', @shop_beijing, ' WH=', @wh_beijing, ' (进京有机果汁 id=6).',
  ' hub_inbound/sorting/outbound = 0 (确认到仓前初始状态)'
) AS message;

-- =============================================================================
-- 昨日边流量快照（仅 flow_plan + flow_plan_item，供 LLM 费用校准等读取历史负载）
--
-- 库表能力：flow_plan_item 按 plan_id 归属某日规划，可长期存放；与 order_info、
-- inter_city_batch 无强制外键，只要不把这些订单/批次指到本 plan_id，即不影响业务。
--
-- 隔离策略：
--   * algorithm = 'LLM_CALIB_MOCK_HISTORY' — 仅本段脚本写入，重复执行先删旧再插
--   * plan_date = CURDATE() - 1 日 — 「加载最新规划」走 ORDER BY plan_date DESC 时，
--     只要当日已有一次真实 DONE 规划，前台仍显示今日结果，不会误显示本快照
--   * 不插入 inter_city_batch、不更新 order_info.flow_plan_id
--
-- Java 侧可查询示例（需在 Service 中自行实现）：
--   SELECT link_id, flow_amount, edge_cost, ... FROM flow_plan_item
--   WHERE plan_id = (SELECT id FROM flow_plan WHERE algorithm='LLM_CALIB_MOCK_HISTORY'
--                    AND plan_date = DATE_SUB(CURDATE(),INTERVAL 1) LIMIT 1);
-- =============================================================================

DELETE fi FROM `flow_plan_item` fi
  INNER JOIN `flow_plan` fp ON fi.`plan_id` = fp.`id`
  WHERE fp.`algorithm` = 'LLM_CALIB_MOCK_HISTORY';
DELETE FROM `flow_plan` WHERE `algorithm` = 'LLM_CALIB_MOCK_HISTORY';

INSERT INTO `flow_plan` (
  `plan_date`, `total_demand`, `total_cost`, `actual_flow`, `feasible`,
  `status`, `algorithm`, `llm_advice`, `llm_enhanced`
) VALUES (
  DATE_SUB(CURDATE(), INTERVAL 1 DAY),
  520,
  4430.80,
  520,
  1,
  'DONE',
  'LLM_CALIB_MOCK_HISTORY',
  NULL,
  0
);

SET @mcmf_mock_hist_plan_id = LAST_INSERT_ID();

-- 模拟「昨日」若干边流量：含高负载（接近 capacity）、中负载、低负载，便于 prompt 里写负载率
-- edge_cost 与 all.sql 中 hub_link.base_cost_per_unit 一致；total_cost = flow_amount * edge_cost
INSERT INTO `flow_plan_item` (`plan_id`, `from_hub_id`, `to_hub_id`, `link_id`, `flow_amount`, `edge_cost`, `total_cost`) VALUES
(@mcmf_mock_hist_plan_id, 3, 4, 17, 185, 4.2000, 777.00),   -- 京沪 RAIL 日容 200，负载 ~92%
(@mcmf_mock_hist_plan_id, 3, 2,  3, 265, 2.8000, 742.00),   -- 沪→郑 RAIL 300，~88%
(@mcmf_mock_hist_plan_id, 3, 1,  1, 320, 3.2000, 1024.00), -- 沪→汉 ROAD 500
(@mcmf_mock_hist_plan_id, 12, 5, 44,  95, 3.1500, 299.25), -- 杭→穗 RAIL 300
(@mcmf_mock_hist_plan_id, 5, 1,  6, 110, 3.5000, 385.00),  -- 穗→汉
(@mcmf_mock_hist_plan_id, 2, 4, 14, 140, 2.4000, 336.00),   -- 郑→京
(@mcmf_mock_hist_plan_id, 2, 13, 58,  88, 2.3500, 206.80),  -- 郑→渝
(@mcmf_mock_hist_plan_id, 3, 12, 34, 210, 0.5200, 109.20),  -- 沪→杭 高频
(@mcmf_mock_hist_plan_id, 5, 8,  7,  28, 0.6000,  16.80),  -- 穗→深 低流量
(@mcmf_mock_hist_plan_id, 2, 7, 10, 195, 1.8000, 351.00),  -- 郑→西
(@mcmf_mock_hist_plan_id, 12, 1, 42,  75, 2.4500, 183.75); -- 杭→汉

SELECT CONCAT(
  'LLM mock history: flow_plan.id=', @mcmf_mock_hist_plan_id,
  ' plan_date=', DATE_SUB(CURDATE(), INTERVAL 1 DAY),
  ' items=', (SELECT COUNT(*) FROM `flow_plan_item` WHERE `plan_id` = @mcmf_mock_hist_plan_id),
  ' (algorithm=LLM_CALIB_MOCK_HISTORY; does not attach orders/batches).'
) AS message;
