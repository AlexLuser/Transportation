-- =============================================================================
-- 本地调度种子数据 — 上海同城待调度订单（在 all.sql + logistics_history_test.sql 之后执行）
--
-- 目的：为 K-Means 末端聚类调度预览提供可视化演示数据
-- 内容：
--   1）新增上海各区收货地址（覆盖浦东、闵行、徐汇、杨浦、虹口、长宁、普陀、宝山）
--   2）约 25 笔上海同城待揽件订单（order_status=2）
--   3）对应调度池记录（status=0 待调度），is_cross_city=0
--
-- 依赖：all.sql 中的种子数据（customer_id=1, shop_id=1, warehouse_id=2）
-- 使用：可重复执行（幂等：删除 LOC-DISP-% 订单后重新插入）
-- =============================================================================

USE `transportation`;
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ---------------------------------------------------------------------------
-- 上海各区收货地址（customer_id=1 即种子「张三」）
-- 坐标取各区中心位置，在 OSM 上海市范围内，与 K-Means 聚类演示匹配
-- ---------------------------------------------------------------------------
INSERT INTO `customer_address`
  (`customer_id`, `receiver_name`, `receiver_phone`, `province`, `city`, `district`, `detail_address`, `postal_code`, `is_default`, `latitude`, `longitude`)
SELECT 1, '张三', '13800138000', '上海市', '上海市', '闵行区', '闵行区莘庄镇都市路500号', '201100', 0, 31.1118, 121.3817
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `customer_address` WHERE `customer_id` = 1 AND `district` = '闵行区' LIMIT 1);

INSERT INTO `customer_address`
  (`customer_id`, `receiver_name`, `receiver_phone`, `province`, `city`, `district`, `detail_address`, `postal_code`, `is_default`, `latitude`, `longitude`)
SELECT 1, '张三', '13800138000', '上海市', '上海市', '徐汇区', '徐汇区漕溪北路88号', '200030', 0, 31.1885, 121.4370
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `customer_address` WHERE `customer_id` = 1 AND `district` = '徐汇区' LIMIT 1);

INSERT INTO `customer_address`
  (`customer_id`, `receiver_name`, `receiver_phone`, `province`, `city`, `district`, `detail_address`, `postal_code`, `is_default`, `latitude`, `longitude`)
SELECT 1, '张三', '13800138000', '上海市', '上海市', '杨浦区', '杨浦区控江路1500号', '200090', 0, 31.2680, 121.5255
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `customer_address` WHERE `customer_id` = 1 AND `district` = '杨浦区' LIMIT 1);

INSERT INTO `customer_address`
  (`customer_id`, `receiver_name`, `receiver_phone`, `province`, `city`, `district`, `detail_address`, `postal_code`, `is_default`, `latitude`, `longitude`)
SELECT 1, '张三', '13800138000', '上海市', '上海市', '虹口区', '虹口区四川北路1888号', '200080', 0, 31.2647, 121.4727
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `customer_address` WHERE `customer_id` = 1 AND `district` = '虹口区' LIMIT 1);

INSERT INTO `customer_address`
  (`customer_id`, `receiver_name`, `receiver_phone`, `province`, `city`, `district`, `detail_address`, `postal_code`, `is_default`, `latitude`, `longitude`)
SELECT 1, '张三', '13800138000', '上海市', '上海市', '长宁区', '长宁区中山公园万航渡路100号', '200050', 0, 31.2199, 121.4176
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `customer_address` WHERE `customer_id` = 1 AND `district` = '长宁区' LIMIT 1);

INSERT INTO `customer_address`
  (`customer_id`, `receiver_name`, `receiver_phone`, `province`, `city`, `district`, `detail_address`, `postal_code`, `is_default`, `latitude`, `longitude`)
SELECT 1, '张三', '13800138000', '上海市', '上海市', '普陀区', '普陀区真如镇桃浦路220号', '200333', 0, 31.2497, 121.3970
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `customer_address` WHERE `customer_id` = 1 AND `district` = '普陀区' LIMIT 1);

INSERT INTO `customer_address`
  (`customer_id`, `receiver_name`, `receiver_phone`, `province`, `city`, `district`, `detail_address`, `postal_code`, `is_default`, `latitude`, `longitude`)
SELECT 1, '张三', '13800138000', '上海市', '上海市', '宝山区', '宝山区牡丹江路1288号', '201900', 0, 31.3977, 121.4897
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `customer_address` WHERE `customer_id` = 1 AND `district` = '宝山区' LIMIT 1);

INSERT INTO `customer_address`
  (`customer_id`, `receiver_name`, `receiver_phone`, `province`, `city`, `district`, `detail_address`, `postal_code`, `is_default`, `latitude`, `longitude`)
SELECT 1, '张三', '13800138000', '上海市', '上海市', '松江区', '松江区中山二路66号', '201600', 0, 31.0327, 121.2246
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `customer_address` WHERE `customer_id` = 1 AND `district` = '松江区' LIMIT 1);

-- ---------------------------------------------------------------------------
-- 获取地址 ID
-- ---------------------------------------------------------------------------
SET @addr_pudong   = (SELECT `id` FROM `customer_address` WHERE `customer_id` = 1 AND `district` = '浦东新区' AND `is_default` = 1 LIMIT 1);
SET @addr_jingan   = (SELECT `id` FROM `customer_address` WHERE `customer_id` = 1 AND `district` = '静安区' LIMIT 1);
SET @addr_minhang   = (SELECT `id` FROM `customer_address` WHERE `customer_id` = 1 AND `district` = '闵行区' LIMIT 1);
SET @addr_xuhui    = (SELECT `id` FROM `customer_address` WHERE `customer_id` = 1 AND `district` = '徐汇区' LIMIT 1);
SET @addr_yangpu   = (SELECT `id` FROM `customer_address` WHERE `customer_id` = 1 AND `district` = '杨浦区' LIMIT 1);
SET @addr_hongkou  = (SELECT `id` FROM `customer_address` WHERE `customer_id` = 1 AND `district` = '虹口区' LIMIT 1);
SET @addr_changning = (SELECT `id` FROM `customer_address` WHERE `customer_id` = 1 AND `district` = '长宁区' LIMIT 1);
SET @addr_putuo    = (SELECT `id` FROM `customer_address` WHERE `customer_id` = 1 AND `district` = '普陀区' LIMIT 1);
SET @addr_baoshan  = (SELECT `id` FROM `customer_address` WHERE `customer_id` = 1 AND `district` = '宝山区' LIMIT 1);
SET @addr_songjiang = (SELECT `id` FROM `customer_address` WHERE `customer_id` = 1 AND `district` = '松江区' LIMIT 1);

-- 固定种子：上海优质食品店 shop_id=1, 上海华东仓 warehouse_id=2
SET @wh_shanghai = 2;
SET @shop_shanghai = 1;

-- ---------------------------------------------------------------------------
-- 清理旧本地调度测试数据
-- ---------------------------------------------------------------------------
DELETE dp FROM `dispatch_pool` dp
  INNER JOIN `order_info` o ON dp.`order_id` = o.`id`
  WHERE o.`order_no` LIKE 'LOC-DISP-%';
DELETE oi FROM `order_item` oi
  INNER JOIN `order_info` o ON oi.`order_id` = o.`id`
  WHERE o.`order_no` LIKE 'LOC-DISP-%';
DELETE FROM `order_info` WHERE `order_no` LIKE 'LOC-DISP-%';

-- ---------------------------------------------------------------------------
-- 批量插入上海同城订单
-- 分布特征：浦东5单 + 闵行4单 + 徐汇3单 + 杨浦3单 + 虹口2单 + 长宁2单 + 普陀2单 + 宝山2单 + 松江2单
-- 确保K-Means聚类能形成3-5个明显的地理簇
-- ---------------------------------------------------------------------------
SET @ldtid = DATE_FORMAT(NOW(), '%Y%m%d%H%i%s');

DROP TEMPORARY TABLE IF EXISTS `_local_orders`;
CREATE TEMPORARY TABLE `_local_orders` (
  `seq` INT NOT NULL PRIMARY KEY,
  `address_id` BIGINT NOT NULL,
  `remark` VARCHAR(120) NOT NULL
);

-- 浦东 5 单（聚类核心区1）
INSERT INTO `_local_orders` (`seq`, `address_id`, `remark`) VALUES
(1,  @addr_pudong,   '同城配送 浦东#1'),
(2,  @addr_pudong,   '同城配送 浦东#2'),
(3,  @addr_pudong,   '同城配送 浦东#3'),
(4,  @addr_pudong,   '同城配送 浦东#4'),
(5,  @addr_pudong,   '同城配送 浦东#5');

-- 闵行 4 单（聚类核心区2 - 西南）
INSERT INTO `_local_orders` (`seq`, `address_id`, `remark`) VALUES
(6,  @addr_minhang,  '同城配送 闵行#1'),
(7,  @addr_minhang,  '同城配送 闵行#2'),
(8,  @addr_minhang,  '同城配送 闵行#3'),
(9,  @addr_minhang,  '同城配送 闵行#4');

-- 徐汇 3 单（聚类区2附近 - 中心偏南）
INSERT INTO `_local_orders` (`seq`, `address_id`, `remark`) VALUES
(10, @addr_xuhui,   '同城配送 徐汇#1'),
(11, @addr_xuhui,   '同城配送 徐汇#2'),
(12, @addr_xuhui,   '同城配送 徐汇#3');

-- 杨浦 3 单（聚类核心区3 - 东北）
INSERT INTO `_local_orders` (`seq`, `address_id`, `remark`) VALUES
(13, @addr_yangpu,  '同城配送 杨浦#1'),
(14, @addr_yangpu,  '同城配送 杨浦#2'),
(15, @addr_yangpu,  '同城配送 杨浦#3');

-- 虹口 2 单（东北附近）
INSERT INTO `_local_orders` (`seq`, `address_id`, `remark`) VALUES
(16, @addr_hongkou, '同城配送 虹口#1'),
(17, @addr_hongkou, '同城配送 虹口#2');

-- 长宁 2 单（西侧中心）
INSERT INTO `_local_orders` (`seq`, `address_id`, `remark`) VALUES
(18, @addr_changning, '同城配送 长宁#1'),
(19, @addr_changning, '同城配送 长宁#2');

-- 普陀 2 单（西北）
INSERT INTO `_local_orders` (`seq`, `address_id`, `remark`) VALUES
(20, @addr_putuo,   '同城配送 普陀#1'),
(21, @addr_putuo,   '同城配送 普陀#2');

-- 宝山 2 单（远北）
INSERT INTO `_local_orders` (`seq`, `address_id`, `remark`) VALUES
(22, @addr_baoshan, '同城配送 宝山#1'),
(23, @addr_baoshan, '同城配送 宝山#2');

-- 松江 2 单（远西南）
INSERT INTO `_local_orders` (`seq`, `address_id`, `remark`) VALUES
(24, @addr_songjiang, '同城配送 松江#1'),
(25, @addr_songjiang, '同城配送 松江#2');

-- ---------------------------------------------------------------------------
-- 插入订单（order_status=2 待揽件，payment_status=1 已支付）
-- origin_hub_id / dest_hub_id 使用上海本地 Hub（浦东分拨中心 id=1）
-- ---------------------------------------------------------------------------
INSERT INTO `order_info`
  (`order_no`, `customer_id`, `shop_id`, `address_id`, `warehouse_id`,
   `origin_hub_id`, `dest_hub_id`, `flow_plan_id`, `inter_city_batch_id`,
   `total_amount`, `product_amount`, `shipping_fee`,
   `order_status`, `payment_status`, `payment_time`, `shipping_time`,
   `remark`, `customer_deleted`, `create_time`)
SELECT
  CONCAT('LOC-DISP-', @ldtid, '-', LPAD(m.`seq`, 3, '0')),
  1,
  @shop_shanghai,
  m.`address_id`,
  @wh_shanghai,
  1, 1,   -- 同城：origin_hub_id=dest_hub_id=1 (浦东分拨中心)
  NULL, NULL,
  28.00 + (m.`seq` % 5) * 5.00,   -- 28~48元，模拟不同商品金额
  25.00 + (m.`seq` % 5) * 5.00,
  3.00,                            -- 固定运费3元
  2,   -- 待揽件
  1,   -- 已支付
  NOW(), NOW(),
  m.`remark`,
  0,
  NOW()
FROM `_local_orders` m;

DROP TEMPORARY TABLE IF EXISTS `_local_orders`;

-- ---------------------------------------------------------------------------
-- 初始化调度池：将所有同城测试订单写入 dispatch_pool
-- status=0 待调度，is_cross_city=0 同城
-- dispatch_origin_type=0 从仓库出发
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
  0,   -- 待调度
  o.`origin_hub_id`,
  o.`dest_hub_id`,
  0,   -- 同城
  0    -- 从仓库出发
FROM `order_info` o
JOIN `customer_address` a ON o.`address_id` = a.`id`
WHERE o.`order_no` LIKE CONCAT('LOC-DISP-', @ldtid, '-%') COLLATE utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 订单项（每单1件，使用优质食品商品 id=2 有机蔬菜）
-- ---------------------------------------------------------------------------
INSERT INTO `order_item` (`order_id`, `product_id`, `product_name`, `product_image`, `product_price`, `quantity`, `subtotal`)
SELECT
  o.`id`,
  2,
  '有机蔬菜',
  'https://example.com/images/veg1.jpg',
  p.`price`,
  1,
  p.`price`
FROM `order_info` o
JOIN `product_info` p ON p.`id` = 2
WHERE o.`order_no` LIKE CONCAT('LOC-DISP-', @ldtid, '-%') COLLATE utf8mb4_unicode_ci;

SELECT CONCAT(
  'Local Dispatch: inserted ',
  (SELECT COUNT(*) FROM `order_info` WHERE `order_no` LIKE CONCAT('LOC-DISP-', @ldtid, '-%') COLLATE utf8mb4_unicode_ci),
  ' orders, ',
  (SELECT COUNT(*) FROM `dispatch_pool` dp
     INNER JOIN `order_info` o ON dp.`order_id` = o.`id`
     WHERE o.`order_no` LIKE CONCAT('LOC-DISP-', @ldtid, '-%') COLLATE utf8mb4_unicode_ci),
  ' dispatch_pool entries (status=0, is_cross_city=0).',
  ' Covers: 浦东×5, 闵行×4, 徐汇×3, 杨浦×3, 虹口×2, 长宁×2, 普陀×2, 宝山×2, 松江×2'
) AS message;
