-- 为 driver 端"待处理"列表补充配送任务数据
-- 插入 order_delivery 记录（delivery_status=0 待接单，segment_type=2 末端配送）
-- 数据来源：dispatch_pool 中状态为0（待调度）的记录

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

INSERT INTO order_delivery (order_id, delivery_status, delivery_address, receiver_name, receiver_phone, segment_type, batch_id, hub_id, route_id, remark, create_time)
SELECT 
    dp.order_id,
    0 AS delivery_status,
    dp.end_address AS delivery_address,
    dp.receiver_name,
    dp.receiver_phone,
    2 AS segment_type,
    NULL AS batch_id,
    dp.dest_hub_id AS hub_id,
    NULL AS route_id,
    CONCAT('调度池ID:', dp.id) AS remark,
    NOW() AS create_time
FROM dispatch_pool dp
WHERE dp.status = 0
AND NOT EXISTS (
    SELECT 1 FROM order_delivery od WHERE od.order_id = dp.order_id
)
LIMIT 10;

-- 再补充几条没有对应 order 的纯配送任务（干线/末端直送）
INSERT INTO order_delivery (delivery_status, delivery_address, receiver_name, receiver_phone, segment_type, batch_id, hub_id, route_id, remark, create_time)
VALUES
(0, '上海市浦东新区张江高科技园区科苑路88号', '李明', '13812345678', 2, NULL, 1, NULL, '模拟末端配送任务-张江', NOW()),
(0, '上海市徐汇区漕河泾开发区宜山路900号', '王芳', '13987654321', 2, NULL, 1, NULL, '模拟末端配送任务-漕河泾', NOW()),
(0, '上海市静安区南京西路1266号恒隆广场', '张伟', '13711112222', 2, NULL, 1, NULL, '模拟末端配送任务-静安', NOW()),
(0, '上海市黄浦区外滩中山东一路1号', '刘洋', '13633334444', 2, NULL, 1, NULL, '模拟末端配送任务-外滩', NOW()),
(0, '上海市杨浦区五角场万达广场', '陈静', '13555556666', 2, NULL, 1, NULL, '模拟末端配送任务-五角场', NOW())
ON DUPLICATE KEY UPDATE create_time = NOW();
