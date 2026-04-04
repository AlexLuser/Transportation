#!/usr/bin/env python3
"""
generate_history_data.py — 物流历史测试数据生成器
==========================================================
原理：
  1. 预定义 15 条上海真实道路走廊（关键路口坐标序列）
  2. 在相邻路口间按距离插入中间 GPS 点（最大段距 1.2km）
  3. 根据出发时段分配合理速度（早/晚高峰 vs 平峰）
  4. 由速度+距离反算每点时间偏移
  5. 生成全部关联表的 INSERT SQL，保证数据库完整性

覆盖范围：
  · 30 条历史路线（route_id 2-31），跨越 31 天
  · 3 名顾客 × 12 个收货地址
  · 3 名运输员 × 4 辆车
  · 3 个仓库起点（浦东华东 / 闵行 / 宝山）
  · 坐标全部在 shanghai-260310.osm.pbf 覆盖范围内

用法：
  python scripts/generate_history_data.py
  python scripts/generate_history_data.py --out database/logistics_history_test.sql

【数据量】完全可以生成更多 SQL 行，常用手段：
  --max-seg-km 0.6        缩小相邻插值最大段长（默认 1.2），每条走廊的 logistics_track 点明显变多
  --samples-per-hour 80   与 --boost-hours 配合，追加更多轨迹行（见下）
  --slow-points 10        每个慢速格点追加更多行

可选「追加轨迹」块（默认开启，仅增加 logistics_track 行数，不改订单/路线主数据行数）：
  --no-enrich              关闭追加轨迹
  --boost-hours 9,10,11    高均速样本所在小时
  --slow-zone-hour 8       慢速样本所在小时（7 天窗口内 peakHour）

说明：RouteHistory 按小时聚合样本量；样本过少时 LLM 置信度易为 LOW。
"""

import math
import random
import argparse
from pathlib import Path
from typing import List, Tuple, Optional, Sequence

# 可复现
random.seed(42)

# ═══════════════════════════════════════════════════════════════
# 1.  地理工具函数
# ═══════════════════════════════════════════════════════════════

def haversine_km(p1, p2) -> float:
    """两点球面距离（千米）"""
    R = 6371.0
    lat1, lon1 = math.radians(p1[0]), math.radians(p1[1])
    lat2, lon2 = math.radians(p2[0]), math.radians(p2[1])
    dlat, dlon = lat2 - lat1, lon2 - lon1
    a = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))

def bearing(p1, p2) -> float:
    """方位角（度，0=正北，顺时针）"""
    lat1, lon1 = math.radians(p1[0]), math.radians(p1[1])
    lat2, lon2 = math.radians(p2[0]), math.radians(p2[1])
    y = math.sin(lon2 - lon1) * math.cos(lat2)
    x = math.cos(lat1) * math.sin(lat2) - math.sin(lat1) * math.cos(lat2) * math.cos(lon2 - lon1)
    return (math.degrees(math.atan2(y, x)) + 360) % 360

def lerp(p1, p2, t) -> tuple:
    return (p1[0] + (p2[0] - p1[0]) * t, p1[1] + (p2[1] - p1[1]) * t)

# ═══════════════════════════════════════════════════════════════
# 2.  已知拥堵网格（ROUND(lat,2) × ROUND(lon,2)）
#     被标记为慢速区的坐标在高峰期会分配低速值
#     同一网格出现 ≥3 次且均速<8 → 触发 selectSlowZones
# ═══════════════════════════════════════════════════════════════
SLOW_GRIDS = {
    (31.19, 121.51),   # 浦东南路/商城路  ← Route 2,4,10,14,19 经过
    (31.19, 121.52),   # 浦东南路上段     ← Route 4,10,14 经过
    (31.20, 121.52),   # 延安路隧道浦东侧 ← Route 5,13,18,24 经过
    (31.20, 121.51),   # 延安中路附近     ← Route 5,18 经过
    (31.28, 121.49),   # 共和新路/内环北  ← Route 28,30 (W3→杨浦) 经过
    (31.26, 121.49),   # 中山北路附近     ← Route 28,30 (W3→杨浦) 经过
}

# 高峰时段（早高峰 07-09，晚高峰 17-19）
# 这些小时的增强轨迹使用偏低速度，使 speedRatio 降到 0.7 以下，触发 LLM 绕路决策
PEAK_HOURS = frozenset({7, 8, 17, 18, 19})

def is_slow_grid(lat, lon) -> bool:
    return (round(lat, 2), round(lon, 2)) in SLOW_GRIDS

def assign_speed(lat: float, lon: float, hour: int,
                 is_first: bool = False, is_last: bool = False) -> float:
    """根据位置和时间分配速度（km/h）"""
    if is_first or is_last:
        return 0.0
    slow = is_slow_grid(lat, lon)
    if 7 <= hour < 9:     # 早高峰
        return 6.0 if slow else 20.0
    elif 11 <= hour < 13: # 午高峰
        return 10.0 if slow else 28.0
    elif 17 <= hour < 20: # 晚高峰
        return 5.0 if slow else 14.0
    else:                  # 平峰
        return 20.0 if slow else 38.0

# ═══════════════════════════════════════════════════════════════
# 3.  轨迹点生成
#     输入：关键路口坐标列表 + 出发小时
#     输出：[(lat, lon, speed, heading, offset_min), ...]
# ═══════════════════════════════════════════════════════════════

def generate_track_points(
    waypoints: List[Tuple[float, float]],
    hour: int,
    max_seg_km: float = 1.2,
    speed_override: Optional[List[Optional[float]]] = None,
) -> List[Tuple[float, float, float, float, float]]:
    """
    生成完整轨迹序列，在相邻路口间自动插入中间点。
    返回: [(lat, lon, speed_kmh, heading_deg, offset_min)]
    """
    points: List[Tuple[float, float]] = []       # (lat, lon)
    speed_tags: List[Optional[float]] = []        # None → 自动分配

    n = len(waypoints)
    for i in range(n):
        p_cur = waypoints[i]
        spd_override = speed_override[i] if speed_override else None

        if i == 0:
            points.append(p_cur)
            speed_tags.append(0.0)         # 出发，速度=0
            continue

        p_prev = waypoints[i - 1]
        dist = haversine_km(p_prev, p_cur)

        if dist > max_seg_km:
            # 插入中间点
            steps = max(2, int(dist / max_seg_km))
            for s in range(1, steps):
                t = s / steps
                ip = lerp(p_prev, p_cur, t)
                points.append(ip)
                speed_tags.append(None)    # 自动分配

        points.append(p_cur)
        speed_tags.append(0.0 if i == n - 1 else spd_override)

    # 计算方位角和时间偏移
    result = []
    total_min = 0.0
    for i, (p, st) in enumerate(zip(points, speed_tags)):
        is_first = (i == 0)
        is_last  = (i == len(points) - 1)
        spd = st if st is not None else assign_speed(p[0], p[1], hour, is_first, is_last)

        if i == 0:
            hdg = bearing(points[0], points[1]) if len(points) > 1 else 0.0
        else:
            hdg = bearing(points[i - 1], p)
            dist = haversine_km(points[i - 1], p)
            # 用前后速度均值估算行驶时间
            prev_spd = result[-1][2]
            avg = (prev_spd + spd) / 2.0 if (prev_spd + spd) > 0 else 20.0
            total_min += (dist / avg) * 60.0

        result.append((p[0], p[1], spd, hdg, total_min))

    return result

# ═══════════════════════════════════════════════════════════════
# 4.  上海道路走廊定义（15 条）
#     命名规则：<起点仓库>_<目的地缩写>
#     起点仓库：W2=华东仓库(浦东), W1=闵行分拨仓, W3=宝山分拨仓
# ═══════════════════════════════════════════════════════════════
# 各走廊：关键路口坐标（纬度, 经度）
# 按实际上海道路走向排列，每个点对应一个可识别的路口或地标

CORRIDORS = {
    # ── W2（华东仓库, 浦东） 出发的走廊 ──────────────────────────

    # 西南向：浦东南路 → 南浦大桥 → 徐汇龙华路（~14km）
    'W2_XUHUI': [
        (31.1985, 121.5889),  # 华东仓库
        (31.1972, 121.5700),  # 合庆路
        (31.1955, 121.5500),  # 高科西路
        (31.1942, 121.5300),  # 浦东南路中段
        (31.1932, 121.5150),  # 浦东南路（慢速区）
        (31.1925, 121.5050),  # 商城路（严重拥堵）
        (31.1912, 121.4870),  # 南浦大桥入口
        (31.1895, 121.4710),  # 南浦大桥（过江）
        (31.1868, 121.4558),  # 进入浦西徐汇
        (31.1842, 121.4453),  # 龙华西路
        (31.1820, 121.4380),  # 到达：徐汇龙华路668号
    ],

    # 北向：张杨路 → 杨浦大桥方向 → 杨浦区（~8km）
    'W2_YANGPU': [
        (31.1985, 121.5889),  # 华东仓库
        (31.2065, 121.5810),  # 东方路
        (31.2155, 121.5710),  # 张杨路
        (31.2245, 121.5610),  # 昌里路
        (31.2335, 121.5510),  # 源深路
        (31.2425, 121.5388),  # 近杨浦大桥
        (31.2492, 121.5215),  # 杨浦大桥
        (31.2535, 121.5118),  # 杨浦区进入
        (31.2550, 121.5050),  # 到达：杨浦中山北二路
    ],

    # 西向（远）：南浦大桥 → S20外环 → 虹桥（~22km）
    'W2_HONGQIAO': [
        (31.1985, 121.5889),  # 华东仓库
        (31.1968, 121.5700),  # 向西
        (31.1948, 121.5490),  # 浦东南路
        (31.1930, 121.5270),  # 浦东南路（慢速区）
        (31.1918, 121.5080),  # 近南浦大桥（严重拥堵）
        (31.1892, 121.4875),  # 南浦大桥过桥
        (31.1860, 121.4665),  # 外环高速入口
        (31.1850, 121.4450),  # S20外环（高速）
        (31.1850, 121.4230),  # S20外环（高速）
        (31.1870, 121.4005),  # 长宁区
        (31.1945, 121.3780),  # 虹桥路附近
        (31.1960, 121.3460),  # 到达：虹桥路1号
    ],

    # 西北向：延安路隧道 → 曹杨路 → 普陀武威路（~16km）
    'W2_PUTUO': [
        (31.1985, 121.5889),  # 华东仓库
        (31.1975, 121.5700),  # 合庆路
        (31.1965, 121.5500),  # 浦东南路（略堵）
        (31.1960, 121.5280),  # 延安路隧道浦东（慢速区）
        (31.1978, 121.5080),  # 延安路隧道出口（黄浦）
        (31.2040, 121.4900),  # 延安中路
        (31.2115, 121.4718),  # 延安西路
        (31.2195, 121.4540),  # 曹杨路
        (31.2278, 121.4365),  # 普陀长寿路
        (31.2358, 121.4200),  # 云岭东路
        (31.2420, 121.4050),  # 到达：普陀武威路200号
    ],

    # 东向（短）：外环路 → 张江高科（~4km）
    'W2_ZHANGJIANG': [
        (31.1985, 121.5889),  # 华东仓库
        (31.1990, 121.5975),  # 外高桥大道
        (31.1996, 121.6058),  # 外环路
        (31.2006, 121.6095),  # 申江路
        (31.2016, 121.6098),  # 科苑路
        (31.2021, 121.6087),  # 到达：张江高科
    ],

    # 东向（中）：外环路 → 金桥出口加工区（~6km）
    'W2_JINQIAO': [
        (31.1985, 121.5889),  # 华东仓库
        (31.1992, 121.5998),  # 外环路
        (31.2004, 121.6108),  # 浦东大道（金桥段）
        (31.2022, 121.6225),  # 金桥路
        (31.2042, 121.6335),  # 金桥工业区
        (31.2058, 121.6390),  # 金桥出口加工区
        (31.2060, 121.6400),  # 到达：金桥
    ],

    # 西北（中）：延安路隧道 → 静安大宁路（~13km）
    'W2_JING_AN_DN': [
        (31.1985, 121.5889),  # 华东仓库
        (31.1975, 121.5680),  # 向西
        (31.1965, 121.5460),  # 浦东南路
        (31.1958, 121.5240),  # 延安路隧道浦东（慢速区）
        (31.1975, 121.5050),  # 延安路隧道出口
        (31.2018, 121.4868),  # 延安中路（进入静安）
        (31.2068, 121.4710),  # 延安西路
        (31.2118, 121.4618),  # 静安陕西南路
        (31.2178, 121.4592),  # 大丰路
        (31.2238, 121.4572),  # 共和新路
        (31.2280, 121.4560),  # 到达：静安大宁路288号
    ],

    # 西北（短）：延安路隧道 → 静安南京西路（~11km）
    'W2_JING_AN_NJX': [
        (31.1985, 121.5889),  # 华东仓库
        (31.1977, 121.5680),  # 合庆路
        (31.1967, 121.5460),  # 浦东南路
        (31.1960, 121.5240),  # 延安路隧道浦东（慢速区）
        (31.1978, 121.5062),  # 延安路隧道出口
        (31.2025, 121.4882),  # 延安中路
        (31.2100, 121.4722),  # 延安西路
        (31.2180, 121.4595),  # 华山路
        (31.2252, 121.4500),  # 近南京西路
        (31.2289, 121.4490),  # 到达：静安南京西路688号
    ],

    # 西北向：延安路隧道 → 黄浦南京东路（~10km）
    'W2_HUANGPU': [
        (31.1985, 121.5889),  # 华东仓库
        (31.1980, 121.5700),  # 向西
        (31.1975, 121.5490),  # 浦东南路
        (31.1972, 121.5270),  # 延安路隧道入口（慢速区）
        (31.2002, 121.5100),  # 延安路隧道出口（黄浦）
        (31.2088, 121.4968),  # 西藏中路
        (31.2192, 121.4885),  # 人民广场附近
        (31.2278, 121.4832),  # 近南京东路
        (31.2321, 121.4800),  # 到达：黄浦南京东路
    ],

    # ── W1（闵行分拨仓） 出发的走廊 ─────────────────────────────

    # 东北向：中环 → 浦东陆家嘴（~18km）
    'W1_LUJIAZUI': [
        (31.0928, 121.4536),  # 闵行分拨仓
        (31.1155, 121.4525),  # 元江路北段
        (31.1385, 121.4535),  # 中春路
        (31.1610, 121.4608),  # 中环漕宝路
        (31.1825, 121.4752),  # 徐汇华泾
        (31.2032, 121.4912),  # 卢浦大桥附近
        (31.2148, 121.5005),  # 进入黄浦
        (31.2258, 121.5032),  # 近陆家嘴
        (31.2356, 121.5050),  # 到达：陆家嘴环路1000号
    ],

    # 北向：沪闵路 → 徐汇龙华路（~10km）
    'W1_XUHUI': [
        (31.0928, 121.4536),  # 闵行分拨仓
        (31.1105, 121.4492),  # 沪闵路北段
        (31.1330, 121.4452),  # 龙吴路
        (31.1558, 121.4432),  # 徐汇区南段
        (31.1725, 121.4420),  # 龙华西路
        (31.1820, 121.4380),  # 到达：徐汇龙华路668号
    ],

    # 东北向（远）：中环 → 内环 → 浦东张江（~35km）
    'W1_ZHANGJIANG': [
        (31.0928, 121.4536),  # 闵行分拨仓
        (31.1205, 121.4655),  # 中环北段
        (31.1512, 121.4812),  # 徐汇-黄浦界
        (31.1825, 121.4992),  # 进入黄浦
        (31.2010, 121.5208),  # 浦东南路
        (31.2000, 121.5500),  # 向东
        (31.1998, 121.5808),  # 外环附近
        (31.2005, 121.6008),  # 张江方向
        (31.2016, 121.6065),  # 科苑路
        (31.2021, 121.6087),  # 到达：张江高科
    ],

    # ── W3（宝山分拨仓） 出发的走廊 ─────────────────────────────

    # 南向：逸仙路 → 内环 → 杨浦区（~20km）
    'W3_YANGPU': [
        (31.3988, 121.4312),  # 宝山分拨仓
        (31.3705, 121.4435),  # 逸仙路南段
        (31.3408, 121.4558),  # 共和新路
        (31.3108, 121.4725),  # 内环沪太
        (31.2812, 121.4892),  # 中山北路
        (31.2608, 121.4988),  # 近杨浦大桥浦西侧
        (31.2550, 121.5050),  # 到达：杨浦中山北二路
    ],

    # 西南向（远）：S20外环 → 虹桥（~35km）
    'W3_HONGQIAO': [
        (31.3988, 121.4312),  # 宝山分拨仓
        (31.3705, 121.4215),  # 向南
        (31.3408, 121.4108),  # 沪太路
        (31.3108, 121.3992),  # 中环西段
        (31.2808, 121.3895),  # 继续南下
        (31.2508, 121.3762),  # 外环高速
        (31.2208, 121.3618),  # 长宁区
        (31.1960, 121.3460),  # 到达：虹桥路1号
    ],

    # 南向（远）：逸仙路 → 内环 → 徐汇（~32km）
    'W3_XUHUI': [
        (31.3988, 121.4312),  # 宝山分拨仓
        (31.3685, 121.4282),  # 逸仙路
        (31.3352, 121.4205),  # 共和新路南段
        (31.3025, 121.4185),  # 中山北路
        (31.2685, 121.4235),  # 内环高架
        (31.2352, 121.4318),  # 中山西路
        (31.2025, 121.4358),  # 近徐汇区
        (31.1820, 121.4380),  # 到达：徐汇龙华路668号
    ],
}

# ═══════════════════════════════════════════════════════════════
# 5.  路线规划表
#     每行：(route_id, order_id, corridor, dep_hour, dep_min,
#             days_ago, driver_id, status, exp_min, act_min,
#             customer_id, addr_id, shop_id, product_id, wh_id)
#     status: 2=已送达, 3=异常
#     act_min: -1 = 异常未到达
# ═══════════════════════════════════════════════════════════════
ROUTE_PLANS = [
    #  rid  oid  corridor          h  m  day drv st  exp act  cust addr shop prod  wh
    (  2,   3, 'W2_XUHUI',        7, 30,  2,  1,  2, 40,  58,   1,   3,   1,   1,  2),
    (  3,   4, 'W2_YANGPU',      14,  0,  3,  1,  2, 30,  32,   1,   4,   1,   2,  2),
    (  4,   5, 'W2_HONGQIAO',    17, 30,  4,  1,  2, 52,  82,   2,   7,   1,   2,  2),
    (  5,   6, 'W2_PUTUO',       10,  0,  5,  2,  2, 50,  52,   2,   5,   2,   3,  2),
    (  6,   7, 'W2_ZHANGJIANG',  12,  0,  6,  2,  2, 20,  22,   3,   8,   1,   1,  2),
    (  7,   8, 'W2_YANGPU',      15,  0,  7,  1,  2, 30,  32,   1,   4,   2,   4,  2),
    (  8,   9, 'W2_HUANGPU',     16,  0,  8,  2,  3, 35,  -1,   2,   6,   1,   2,  2),  # 异常
    (  9,  10, 'W2_JINQIAO',      9, 30,  9,  3,  2, 25,  30,   3,   9,   1,   1,  2),
    ( 10,  11, 'W2_JING_AN_DN',  17,  0, 10,  3,  2, 42,  73,   3,  10,   2,   3,  2),
    ( 11,  12, 'W2_HUANGPU',     13,  0, 11,  1,  2, 35,  38,   2,   6,   1,   2,  2),
    ( 12,  13, 'W2_XUHUI',        9,  0, 12,  2,  2, 40,  43,   1,   3,   2,   4,  2),
    ( 13,  14, 'W2_JING_AN_DN',   8,  0, 13,  3,  2, 42,  57,   3,  10,   1,   1,  2),
    ( 14,  15, 'W2_HONGQIAO',    18,  0, 14,  1,  2, 52,  78,   2,   7,   2,   3,  2),
    ( 15,  16, 'W2_YANGPU',      15, 30, 15,  2,  2, 30,  35,   1,   4,   1,   2,  2),
    ( 16,  17, 'W2_ZHANGJIANG',   8, 30, 16,  1,  2, 20,  22,   3,   8,   2,   4,  2),
    ( 17,  18, 'W2_XUHUI',        9,  0, 17,  3,  2, 40,  43,   2,  11,   1,   1,  2),
    ( 18,  19, 'W2_JING_AN_NJX', 12, 30, 18,  2,  2, 40,  52,   1,   2,   1,   2,  2),
    ( 19,  20, 'W2_HUANGPU',      7,  0, 19,  1,  2, 35,  50,   2,   6,   2,   3,  2),
    ( 20,  21, 'W2_YANGPU',      11,  0, 20,  3,  2, 30,  32,   3,  12,   1,   2,  2),
    ( 21,  22, 'W2_ZHANGJIANG',  13, 30, 21,  2,  2, 20,  23,   3,   8,   2,   4,  2),
    ( 22,  23, 'W2_JINQIAO',     17, 30, 22,  1,  3, 25,  -1,   3,   9,   1,   1,  2),  # 异常
    ( 23,  24, 'W2_JING_AN_DN',   9,  0, 23,  2,  2, 42,  43,   3,  10,   2,   3,  2),
    ( 24,  25, 'W2_PUTUO',       16,  0, 24,  3,  2, 50,  53,   2,   5,   1,   2,  2),
    ( 25,  26, 'W1_LUJIAZUI',     9,  0, 25,  1,  2, 55,  60,   1,   1,   1,   1,  1),
    ( 26,  27, 'W1_XUHUI',       14, 30, 26,  2,  2, 45,  48,   1,   3,   1,   1,  1),  # W1仅有prod1,2 → 坚果礼盒
    ( 27,  28, 'W1_ZHANGJIANG',  10, 30, 27,  3,  2, 62,  65,   3,   8,   1,   2,  1),  # W1 → 有机果汁
    ( 28,  29, 'W3_YANGPU',      10,  0, 28,  3,  2, 50,  55,   3,  12,   2,   4,  3),  # W3仅有prod2,4 → 连衣裙
    ( 29,  30, 'W3_HONGQIAO',    13,  0, 29,  2,  2, 70,  78,   2,   7,   1,   2,  3),  # W3 → 有机果汁
    ( 30,  31, 'W3_YANGPU',      17,  0, 30,  3,  3, 50,  -1,   1,   4,   2,   4,  3),  # 异常 W3 → 连衣裙
    ( 31,  32, 'W3_XUHUI',        9, 30, 31,  1,  2, 65,  70,   2,  11,   1,   2,  3),  # W3 → 有机果汁
]

# ═══════════════════════════════════════════════════════════════
# 6.  静态参考数据（与现有数据库对齐）
# ═══════════════════════════════════════════════════════════════

# 仓库坐标
WAREHOUSE_COORDS = {
    1: (31.0928, 121.4536, '上海市闵行区元江路426号物流园1号库（上海闵行分拨仓）'),
    2: (31.1985, 121.5889, '上海市浦东新区物流园区B区2号（上海华东仓库）'),
    3: (31.3988, 121.4312, '上海市宝山区富锦路1500号物流园C库（上海宝山分拨仓）'),
}

# 收货地址（addr_id → label, lat, lon, customer_id）
# addr_id 1,2 已在 customer.sql 存在
ADDRESS_INFO = {
    1:  ('上海市浦东新区陆家嘴环路1000号', 31.2356, 121.5050, 1),
    2:  ('上海市静安区南京西路688号',       31.2289, 121.4490, 1),
    3:  ('上海市徐汇区龙华路668号',         31.1820, 121.4380, 1),
    4:  ('上海市杨浦区中山北二路800号',     31.2550, 121.5050, 1),
    5:  ('上海市普陀区武威路200号',         31.2420, 121.4050, 2),
    6:  ('上海市黄浦区南京东路668号',       31.2321, 121.4800, 2),
    7:  ('上海市长宁区虹桥路1号',           31.1960, 121.3460, 2),
    8:  ('上海市浦东新区张江高科技园区',   31.2021, 121.6087, 3),
    9:  ('上海市浦东新区金桥出口加工区',   31.2060, 121.6400, 3),
    10: ('上海市静安区大宁路288号',         31.2280, 121.4560, 3),
    11: ('上海市徐汇区龙华路668号',         31.1820, 121.4380, 2),  # 李明的徐汇地址
    12: ('上海市杨浦区中山北二路800号',     31.2550, 121.5050, 3),  # 王芳的杨浦地址
}

# 商品价格
PRODUCT_PRICE = {1: 128.00, 2: 35.00, 3: 299.00, 4: 399.00}

# 运输员/顾客/仓库名称（用于 SQL 注释和地址字段）
DRIVER_INFO = {
    1: ('李四', '13700137000'),
    2: ('陈刚', '13900139008'),
    3: ('赵磊', '13600136009'),
}

CUSTOMER_INFO = {
    1: ('张三', '13800138000'),
    2: ('李明', '13900139001'),
    3: ('王芳', '13600136002'),
}

PRODUCT_NAME = {1: '优质坚果礼盒', 2: '有机果汁', 3: '商务休闲衬衫', 4: '时尚连衣裙'}
SHOP_NAMES   = {1: '优质食品店', 2: '时尚服装店'}

# ═══════════════════════════════════════════════════════════════
# 7.  SQL 生成工具
# ═══════════════════════════════════════════════════════════════

def q(s: str) -> str:
    """引号转义"""
    return s.replace("'", "\\'")

def time_expr(days_ago: int, hour: int, minute: int, offset_min: float = 0) -> str:
    """生成 MySQL TIMESTAMP 表达式（相对当前日期）"""
    base_h = hour + int(minute + offset_min) // 60
    base_m = (minute + int(offset_min)) % 60
    frac_m = int(offset_min) % 1   # 忽略秒级差异
    return (f"TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL {days_ago} DAY), "
            f"'{base_h:02d}:{base_m:02d}:00')")

def dep_time(days_ago, hour, minute):
    """出发时间表达式"""
    return time_expr(days_ago, hour, minute, 0)

def arr_time(days_ago, hour, minute, offset_min):
    """到达时间表达式（出发时间 + offset_min 分钟）"""
    total = hour * 60 + minute + int(offset_min)
    h = total // 60
    m = total % 60
    return f"TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL {days_ago} DAY), '{h:02d}:{m:02d}:00')"


def ts_expr(days_ago: int, hour: int, minute: int) -> str:
    """单日固定时刻 TIMESTAMP（用于增强轨迹）"""
    return (f"TIMESTAMP(DATE_SUB(DATE(NOW()), INTERVAL {days_ago} DAY), "
            f"'{hour:02d}:{minute:02d}:00')")


def route_id_to_driver() -> dict:
    return {p[0]: p[6] for p in ROUTE_PLANS}


def pick_non_slow_point() -> Tuple[float, float]:
    """在上海市域内取一点，尽量避开 SLOW_GRIDS（用于高均速样本）"""
    for _ in range(80):
        lat = random.uniform(31.12, 31.28)
        lon = random.uniform(121.38, 121.62)
        if not is_slow_grid(lat, lon):
            return round(lat, 4), round(lon, 4)
    return 31.20, 121.48


def generate_enrichment_track_rows(
    boost_hours: Sequence[int],
    samples_per_boost_hour: int,
    slow_zone_hours: Sequence[int],
    slow_points_per_grid: int,
) -> List[str]:
    """
    追加 logistics_track 值行（不含 INSERT 头）：

    1) boost_hours（覆盖全 24 小时）：
       - 高峰时段（PEAK_HOURS：07-09、17-19）使用中低速 15-28 km/h，
         使 speedRatio 降至 0.7 以下，触发 LLM 绕路决策；
       - 平峰时段使用高速 35-45 km/h，使 speedRatio 保持 >1.0（畅通）。
       - 数据分散在 30 天内，确保每小时 sampleCount > 10（MEDIUM 以上置信度）。

    2) 慢速热点格点（slow_zone_hours 为多个高峰小时列表，默认 7,8,17,18）：
       - 每个慢速网格 × 7 天 × 每格条数 × 每个高峰时段，
         均速 <8 km/h，出现次数 ≥3，触发 selectSlowZones 返回结果；
       - peakHour 由 MAX(track_time) 决定，多时段数据使热点信息更真实。
    """
    rows: List[str] = []
    r2d = route_id_to_driver()
    route_ids = list(range(2, 32))  # 与 ROUTE_PLANS 一致

    # ── 全时段增强样本（高峰低速 / 平峰高速）────────────────────────────
    for bh in boost_hours:
        is_peak = bh in PEAK_HOURS
        for i in range(samples_per_boost_hour):
            day = i % 30
            minute = (i * 13 + bh * 3) % 56  # 分散在 0–55 分
            lat, lon = pick_non_slow_point()
            # 高峰时段用中低速，体现拥堵；平峰用高速体现畅通
            if is_peak:
                spd = round(random.uniform(15.0, 28.0), 1)
                label = f'[增强-高峰 {bh:02d}时]'
            else:
                spd = round(random.uniform(35.0, 45.0), 1)
                label = f'[增强-畅通 {bh:02d}时]'
            hdg = round(random.uniform(0, 359), 1)
            rid = route_ids[i % len(route_ids)]
            drv = r2d[rid]
            alt = round(6.0 + (lat - 31.0) * 2, 1)
            acc = 4.5
            tt = ts_expr(day, bh, minute)
            rows.append(
                f"({rid}, {drv}, {lat:.4f}, {lon:.4f}, {alt:.1f}, {spd:.1f}, {hdg:.1f}, {acc:.1f}, "
                f"'上海市{label}', {tt})"
            )

    # ── 慢速热点格点（多个高峰时段，过去 7 天 × 每格多条，均速 <8）──────
    for slow_hour in slow_zone_hours:
        for grid_lat, grid_lon in SLOW_GRIDS:
            for day in range(7):
                for k in range(slow_points_per_grid):
                    lat = round(grid_lat + 0.001 * (k % 3), 4)
                    lon = round(grid_lon + 0.001 * ((k + day) % 3), 4)
                    minute = min(55, 5 + k * 8 + day * 3)
                    spd = round(random.uniform(4.5, 7.8), 1)
                    hdg = round(random.uniform(0, 359), 1)
                    idx = day * slow_points_per_grid + k
                    rid = route_ids[idx % len(route_ids)]
                    drv = r2d[rid]
                    alt = round(6.0 + (lat - 31.0) * 2, 1)
                    acc = 6.0
                    tt = ts_expr(day, slow_hour, minute)
                    rows.append(
                        f"({rid}, {drv}, {lat:.4f}, {lon:.4f}, {alt:.1f}, {spd:.1f}, {hdg:.1f}, {acc:.1f}, "
                        f"'上海市[增强-慢速格 {grid_lat:.2f},{grid_lon:.2f}]', {tt})"
                    )

    return rows


# ═══════════════════════════════════════════════════════════════
# 8.  完整 SQL 生成
# ═══════════════════════════════════════════════════════════════

BCR = '$2a$10$.ckJuQWWC9dUh0hOa7v4LuxBa6PICggDyUUz7awFv4CM/rh7tQZ0a'  # 123456

def generate_sql(
    enrich: bool = True,
    boost_hours: Tuple[int, ...] = tuple(range(24)),   # 全 24 小时均有增强样本
    samples_per_boost_hour: int = 50,                   # 每小时 50 条 → 共 1200 条增强轨迹
    slow_zone_hours: Tuple[int, ...] = (7, 8, 17, 18), # 早晚高峰均生成慢速热点
    slow_points_per_grid: int = 20,                     # 与 argparse 默认值对齐（原函数默认为5，已修正）
    max_seg_km: float = 1.2,
) -> str:
    lines = []

    def L(s=''):
        lines.append(s)

    L('-- ============================================================')
    L('-- 物流历史完整测试数据（generate_history_data.py 生成）')
    L('-- 包含：30条历史路线 + 3名新用户 + 完整关联表数据')
    L('-- 执行前提：logistics.sql / customer.sql / driver.sql /');
    L('--           order.sql / shop.sql / user.sql 均已执行完毕')
    L('-- ============================================================')
    L()
    L('SET NAMES utf8mb4;')
    L()

    # ── 1. user ──────────────────────────────────────────────────
    L('-- ──────────────────────────────────────────')
    L('-- 新用户（user_id 6-9，追加到已有 1-5 之后）')
    L('-- ──────────────────────────────────────────')
    L('INSERT INTO `user` (`id`, `username`, `secret`, `permission`) VALUES')
    L(f"(6, 'customer2', '{BCR}', 2),  -- 李明")
    L(f"(7, 'customer3', '{BCR}', 2),  -- 王芳")
    L(f"(8, 'driver2',   '{BCR}', 4),  -- 陈刚")
    L(f"(9, 'driver3',   '{BCR}', 4);  -- 赵磊")
    L()

    # ── 2. customer_info ─────────────────────────────────────────
    L('-- 顾客信息')
    L('INSERT INTO `customer_info`')
    L('  (`id`, `user_id`, `real_name`, `phone`, `email`, `gender`, `birthday`, `status`)')
    L('VALUES')
    L("(2, 6, '李明', '13900139001', 'liming@example.com',   1, '1988-08-20', 1),")
    L("(3, 7, '王芳', '13600136002', 'wangfang@example.com', 2, '1992-03-12', 1);")
    L()

    # ── 3. customer_address ──────────────────────────────────────
    L('-- 收货地址（addr_id 3-12，追加到已有 1-2 之后）')
    L('INSERT INTO `customer_address`')
    L('  (`id`, `customer_id`, `receiver_name`, `receiver_phone`,')
    L('   `province`, `city`, `district`, `detail_address`, `postal_code`,')
    L('   `is_default`, `latitude`, `longitude`)')
    L('VALUES')
    addr_rows = []
    for aid in range(3, 13):
        addr, lat, lon, cid = ADDRESS_INFO[aid]
        cname, cphone = CUSTOMER_INFO[cid]
        # 解析区县
        parts = addr.split('区')
        district = parts[0].split('市')[-1] + '区' if '区' in addr else '浦东新区'
        # postal codes by district
        postal_map = {
            '徐汇区': '200030', '杨浦区': '200092', '普陀区': '200062',
            '黄浦区': '200001', '长宁区': '200050', '浦东新区': '200120',
            '静安区': '200040',
        }
        postal = postal_map.get(district, '200000')
        addr_rows.append(
            f"({aid}, {cid}, '{q(cname)}', '{cphone}', "
            f"'上海市', '上海市', '{district}', '{q(addr)}', '{postal}', "
            f"0, {lat}, {lon})"
        )
    L(',\n'.join(addr_rows) + ';')
    L()

    # ── 4. driver_info ───────────────────────────────────────────
    L('-- 运输员信息（driver_id 2-3）')
    L('INSERT INTO `driver_info`')
    L('  (`id`, `user_id`, `real_name`, `phone`, `email`, `id_card`, `gender`, `birthday`,')
    L('   `license_number`, `license_type`, `license_expire_date`, `status`)')
    L('VALUES')
    L("(2, 8, '陈刚', '13900139008', 'chengang@example.com',  '310104198805051234', 1, '1988-05-05',")
    L(" 'SH0002345678901', 'C1', '2031-06-30', 1),")
    L("(3, 9, '赵磊', '13600136009', 'zhaolei@example.com',   '310106199209091234', 1, '1992-09-09',")
    L(" 'SH0003456789012', 'B2', '2029-12-31', 1);")
    L()

    # ── 5. vehicle_info ──────────────────────────────────────────
    L('-- 车辆信息（vehicle_id 3-4）')
    L('INSERT INTO `vehicle_info`')
    L('  (`id`, `driver_id`, `vehicle_type`, `vehicle_brand`, `vehicle_model`,')
    L('   `license_plate`, `load_capacity`, `volume_capacity`, `vehicle_status`)')
    L('VALUES')
    L("(3, 2, '小型货车', '福田', 'FT-150', '沪C11223', 2.0, 8.0,  1),")
    L("(4, 3, '中型货车', '庆铃', 'QL-300', '沪D44556', 4.0, 15.0, 1);")
    L()

    # ── 6. order_info (30 条, order_id=3..32) ────────────────────
    L('-- ──────────────────────────────────────────')
    L('-- 历史订单（order_id 3-32，全部已完成 status=4）')
    L('-- ──────────────────────────────────────────')
    L('INSERT INTO `order_info`')
    L('  (`id`, `order_no`, `customer_id`, `shop_id`, `address_id`, `warehouse_id`,')
    L('   `total_amount`, `product_amount`, `shipping_fee`,')
    L('   `order_status`, `payment_status`, `payment_time`, `shipping_time`, `complete_time`,')
    L('   `remark`, `customer_deleted`)')
    L('VALUES')
    order_rows = []
    for plan in ROUTE_PLANS:
        rid, oid, corr, h, m, day, drv, st, exp, act, cid, aid, shopid, prodid, whid = plan
        price = PRODUCT_PRICE[prodid]
        total = price + 10.00
        order_no = f'ORD_HIST_{oid:08d}'
        pay_time  = arr_time(day, h, m, -120)   # 支付时间=出发前2小时
        ship_time = dep_time(day, h, m)          # 出发时间=发货时间

        if st == 2:                              # 已送达 → order_status=4（已完成）
            order_status   = 4
            complete_time  = arr_time(day, h, m, act)
        else:                                    # 异常   → order_status=5（已取消）
            order_status   = 5
            ship_time      = 'NULL'              # 未实际揽件，发货时间置 NULL
            complete_time  = 'NULL'

        row = (f"({oid}, '{order_no}', {cid}, {shopid}, {aid}, {whid}, "
               f"{total:.2f}, {price:.2f}, 10.00, "
               f"{order_status}, 1, {pay_time}, {ship_time}, "
               f"{complete_time}, NULL, 0)")
        order_rows.append(row)
    L(',\n'.join(order_rows) + ';')
    L()

    # ── 7. order_item ────────────────────────────────────────────
    L('-- 订单项（每订单1件商品）')
    L('INSERT INTO `order_item`')
    L('  (`order_id`, `product_id`, `product_name`, `product_image`, `product_price`, `quantity`, `subtotal`)')
    L('VALUES')
    item_rows = []
    for plan in ROUTE_PLANS:
        rid, oid, corr, h, m, day, drv, st, exp, act, cid, aid, shopid, prodid, whid = plan
        price = PRODUCT_PRICE[prodid]
        pname = PRODUCT_NAME[prodid]
        img = f'https://example.com/images/prod{prodid}.jpg'
        item_rows.append(
            f"({oid}, {prodid}, '{q(pname)}', '{img}', {price:.2f}, 1, {price:.2f})"
        )
    L(',\n'.join(item_rows) + ';')
    L()

    # ── 8. order_delivery ────────────────────────────────────────
    L('-- 配送记录（delivery_id 2-31，全部已送达 delivery_status=3）')
    L('INSERT INTO `order_delivery`')
    L('  (`id`, `order_id`, `driver_id`, `vehicle_id`, `delivery_status`,')
    L('   `accept_time`, `pickup_time`, `delivery_time`,')
    L('   `delivery_address`, `receiver_name`, `receiver_phone`, `remark`)')
    L('VALUES')
    delivery_rows = []
    veh_map = {1: 1, 2: 3, 3: 4}   # driver→vehicle
    for idx, plan in enumerate(ROUTE_PLANS):
        rid, oid, corr, h, m, day, drv, st, exp, act, cid, aid, shopid, prodid, whid = plan
        deliv_id = rid   # delivery_id = route_id
        veh_id = veh_map[drv]
        accept = arr_time(day, h, m, -30)   # 出发前30分钟接单
        addr_str, alat, alon, acid = ADDRESS_INFO[aid]
        cname, cphone = CUSTOMER_INFO[cid]

        if st == 2:                          # 正常送达
            d_st       = 3                   # 已送达
            pickup     = dep_time(day, h, m)
            deliv_time = arr_time(day, h, m, act)
        else:                                # 异常路线：接单后取消
            d_st       = 4                   # 已取消
            pickup     = 'NULL'              # 未实际取货
            deliv_time = 'NULL'

        delivery_rows.append(
            f"({deliv_id}, {oid}, {drv}, {veh_id}, {d_st}, "
            f"{accept}, {pickup}, {deliv_time}, "
            f"'{q(addr_str)}', '{q(cname)}', '{cphone}', NULL)"
        )
    L(',\n'.join(delivery_rows) + ';')
    L()

    # ── 9. logistics_route ───────────────────────────────────────
    L('-- ──────────────────────────────────────────')
    L('-- 物流路线（route_id 2-31）')
    L('-- ──────────────────────────────────────────')
    L('INSERT INTO `logistics_route`')
    L('  (`id`, `route_no`, `order_id`, `delivery_id`, `driver_id`, `warehouse_id`,')
    L('   `start_address`, `start_lat`, `start_lng`,')
    L('   `end_address`, `end_lat`, `end_lng`,')
    L('   `current_lat`, `current_lng`, `current_address`, `last_track_time`,')
    L('   `route_status`, `estimated_arrival_time`, `actual_arrival_time`,')
    L('   `receiver_name`, `receiver_phone`, `planned_route`, `create_time`, `update_time`)')
    L('VALUES')
    route_rows = []
    for plan in ROUTE_PLANS:
        rid, oid, corr, h, m, day, drv, st, exp, act, cid, aid, shopid, prodid, whid = plan
        wlat, wlon, waddr = WAREHOUSE_COORDS[whid]
        addr_str, alat, alon, acid = ADDRESS_INFO[aid]
        waypoints = CORRIDORS[corr]
        tracks = generate_track_points(waypoints, h, max_seg_km=max_seg_km)
        last_trk = tracks[-1] if st == 2 else tracks[min(len(tracks)//2, len(tracks)-1)]
        done_min = act if act > 0 else -1
        last_trk_time = arr_time(day, h, m, last_trk[4]) if done_min > 0 else arr_time(day, h, m, last_trk[4])
        cur_lat, cur_lon = last_trk[0], last_trk[1]
        cur_addr = addr_str + '（已到达）' if st == 2 else f'{last_trk[3]:.0f}°方向运输中'
        est_time = arr_time(day, h, m, exp)
        act_time = arr_time(day, h, m, act) if act > 0 else 'NULL'
        cname, cphone = CUSTOMER_INFO[cid]
        route_no = f'LR_HIST_{rid:04d}'
        start_time = dep_time(day, h, m)
        end_time = act_time
        route_rows.append(
            f"({rid}, '{route_no}', {oid}, {rid}, {drv}, {whid},\n"
            f" '{q(waddr)}', {wlat}, {wlon},\n"
            f" '{q(addr_str)}', {alat}, {alon},\n"
            f" {cur_lat:.4f}, {cur_lon:.4f}, '{q(addr_str)}', {last_trk_time},\n"
            f" {st}, {est_time}, {act_time},\n"
            f" '{q(cname)}', '{cphone}', NULL, {start_time}, {end_time})"
        )
    L(',\n'.join(route_rows) + ';')
    L()

    # ── 10. logistics_node ───────────────────────────────────────
    L('-- 里程碑节点（每条路线出发点+目的地，共60个）')
    L('INSERT INTO `logistics_node`')
    L('  (`route_id`, `node_type`, `node_name`, `node_address`,')
    L('   `latitude`, `longitude`, `sequence_no`,')
    L('   `planned_arrive_time`, `actual_arrive_time`, `node_status`)')
    L('VALUES')
    node_rows = []
    for plan in ROUTE_PLANS:
        rid, oid, corr, h, m, day, drv, st, exp, act, cid, aid, shopid, prodid, whid = plan
        wlat, wlon, waddr = WAREHOUSE_COORDS[whid]
        addr_str, alat, alon, acid = ADDRESS_INFO[aid]
        dep = dep_time(day, h, m)
        est_arr = arr_time(day, h, m, exp)
        act_arr = arr_time(day, h, m, act) if act > 0 else 'NULL'
        # 出发节点（已到达）
        node_rows.append(
            f"({rid}, 0, '出发仓库', '{q(waddr)}', {wlat}, {wlon}, 0, {dep}, {dep}, 1)"
        )
        # 目的地节点
        n_st = 1 if st == 2 else 0
        node_rows.append(
            f"({rid}, 2, '收货地址', '{q(addr_str)}', {alat}, {alon}, 99, {est_arr}, {act_arr}, {n_st})"
        )
    L(',\n'.join(node_rows) + ';')
    L()

    # ── 11. logistics_track ──────────────────────────────────────
    L('-- ──────────────────────────────────────────')
    L('-- 轨迹点（主数据约 300 条；若启用 enrich 则追加统计增强样本）')
    L('-- ──────────────────────────────────────────')
    L('INSERT INTO `logistics_track`')
    L('  (`route_id`, `driver_id`, `latitude`, `longitude`,')
    L('   `altitude`, `speed`, `heading`, `accuracy`, `address`, `track_time`)')
    L('VALUES')

    all_tracks = []
    ADDR_HINT = {
        'W2_XUHUI':       ['仓库出发','合庆路','高科西路','浦东南路','浦东南路（慢速区）','商城路','南浦大桥','过桥中','徐汇宛平南路','龙华西路','龙华路（到达）'],
        'W2_YANGPU':      ['仓库出发','东方路','张杨路','昌里路','源深路','崂山路','杨浦大桥','杨浦区进入','杨浦（到达）'],
        'W2_HONGQIAO':    ['仓库出发','向西','浦东南路','浦东南路（慢速区）','南浦大桥入口','南浦大桥','外环高速','外环（高速）','外环（高速）','长宁','虹桥路','虹桥（到达）'],
        'W2_PUTUO':       ['仓库出发','合庆路','浦东南路','延安路隧道浦东','延安路隧道出口','延安中路','延安西路','曹杨路','长寿路','云岭东路','普陀（到达）'],
        'W2_ZHANGJIANG':  ['仓库出发','外高桥大道','外环路','申江路','科苑路','张江高科（到达）'],
        'W2_JINQIAO':     ['仓库出发','外环路','浦东大道金桥段','金桥路','金桥工业区','金桥加工区','金桥（到达）'],
        'W2_JING_AN_DN':  ['仓库出发','向西','浦东南路','延安路隧道浦东','延安路隧道出口','延安中路','延安西路','陕西南路','大丰路','共和新路','静安大宁路（到达）'],
        'W2_JING_AN_NJX': ['仓库出发','合庆路','浦东南路','延安路隧道浦东','延安路隧道出口','延安中路','延安西路','华山路','近南京西路','静安南京西路（到达）'],
        'W2_HUANGPU':     ['仓库出发','向西','浦东南路','延安路隧道','延安路隧道出口','西藏中路','人民广场附近','近南京东路','黄浦南京东路（到达）'],
        'W1_LUJIAZUI':    ['闵行仓出发','元江路北段','中春路','中环漕宝路','华泾','卢浦大桥附近','进入黄浦','近陆家嘴','陆家嘴（到达）'],
        'W1_XUHUI':       ['闵行仓出发','沪闵路北段','龙吴路','徐汇区南段','龙华西路','徐汇龙华路（到达）'],
        'W1_ZHANGJIANG':  ['闵行仓出发','中环北段','徐汇-黄浦界','进入黄浦','浦东南路','向东','外环附近','张江方向','科苑路','张江高科（到达）'],
        'W3_YANGPU':      ['宝山仓出发','逸仙路南段','共和新路','内环沪太','中山北路','近杨浦大桥浦西侧','杨浦（到达）'],
        'W3_HONGQIAO':    ['宝山仓出发','向南','沪太路','中环西段','继续南下','外环高速','长宁区','虹桥（到达）'],
        'W3_XUHUI':       ['宝山仓出发','逸仙路','共和新路南段','中山北路','内环高架','中山西路','近徐汇','徐汇龙华路（到达）'],
    }

    for plan in ROUTE_PLANS:
        rid, oid, corr, h, m, day, drv, st, exp, act, cid, aid, shopid, prodid, whid = plan
        waypoints = CORRIDORS[corr]
        tracks = generate_track_points(waypoints, h, max_seg_km=max_seg_km)
        hints = ADDR_HINT.get(corr, [])

        # 对于异常路线，截断到中间位置
        if st == 3:
            cutoff = max(3, len(tracks) // 2)
            tracks = tracks[:cutoff]

        n_hints = len(hints)
        n_tracks = len(tracks)
        for i, (lat, lon, spd, hdg, offset) in enumerate(tracks):
            if n_hints == 0:
                addr_label = f'途经{i+1}'
            elif i < n_hints:
                addr_label = hints[i]
            else:
                # 将超出 hint 范围的插值点映射到最近的 hint（最后一段均摊）
                ratio = (i - n_hints + 1) / max(1, n_tracks - n_hints + 1)
                mapped = min(n_hints - 1, n_hints - 2 + int(ratio + 0.5))
                addr_label = hints[mapped]
            track_time = arr_time(day, h, m, offset)
            alt = 6.0 + (lat - 31.0) * 2   # 简单高程估算
            acc = 4.5 if spd > 5 else 5.5
            all_tracks.append(
                f"({rid}, {drv}, {lat:.4f}, {lon:.4f}, {alt:.1f}, {spd:.1f}, {hdg:.1f}, {acc:.1f}, "
                f"'上海市{q(addr_label)}', {track_time})"
            )

    if enrich:
        extra = generate_enrichment_track_rows(
            boost_hours, samples_per_boost_hour, slow_zone_hours, slow_points_per_grid
        )
        all_tracks.extend(extra)

    L(',\n'.join(all_tracks) + ';')
    L()
    n_boost   = len(boost_hours) * samples_per_boost_hour if enrich else 0
    n_slow    = len(slow_zone_hours) * len(SLOW_GRIDS) * 7 * slow_points_per_grid if enrich else 0
    n_organic = len(all_tracks) - n_boost - n_slow
    L('-- ══════════════════════════════════════════════════════════')
    L('-- 数据统计')
    L(f'-- 新增轨迹点: {len(all_tracks)} 条')
    L(f'--   主数据（有机轨迹）: {n_organic} 条')
    L(f'--   增强样本（全时段 boost）: {n_boost} 条，每小时约 {samples_per_boost_hour} 条')
    L(f'--   慢速热点（{len(slow_zone_hours)} 个时段 × {len(SLOW_GRIDS)} 个格点）: {n_slow} 条')
    L(f'-- 新增路线: 30 条（route_id 2-31）')
    L(f'-- 新增订单: 30 条（order_id 3-32）')
    L(f'-- 新增用户: 4 条（user_id 6-9）')
    L(f'-- 高峰时段 boost 速度: 15-28 km/h（小时 {sorted(PEAK_HOURS)}）')
    L(f'-- 平峰时段 boost 速度: 35-45 km/h（其余小时）')
    L('-- ══════════════════════════════════════════════════════════')

    return '\n'.join(lines)


# ═══════════════════════════════════════════════════════════════
# 9.  主程序
# ═══════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(description='生成物流历史测试数据 SQL')
    parser.add_argument(
        '--out', default=None,
        help='输出文件路径（默认: database/logistics_history_test.sql）'
    )
    parser.add_argument(
        '--enrich', dest='enrich', action='store_true', default=True,
        help='追加统计增强轨迹（默认开启）',
    )
    parser.add_argument(
        '--no-enrich', dest='enrich', action='store_false',
        help='关闭统计增强',
    )
    parser.add_argument(
        '--boost-hours', default=','.join(str(h) for h in range(24)),
        help='需要增强样本的小时，逗号分隔（默认：0-23 全覆盖）',
    )
    parser.add_argument(
        '--samples-per-hour', type=int, default=50,
        help='每个 boost 小时生成的轨迹条数（默认 50，高峰时段用中低速，平峰用高速）',
    )
    parser.add_argument(
        '--slow-zone-hours', default='7,8,17,18',
        help='慢速网格数据覆盖的小时（逗号分隔，默认早晚高峰 7,8,17,18）',
    )
    parser.add_argument(
        '--slow-points', type=int, default=20,
        help='每个慢速格点在过去 7 天内生成的低速样本数（默认 20）',
    )
    parser.add_argument(
        '--max-seg-km', type=float, default=1.2,
        help='走廊插值最大段长（千米），越小则每条路线主轨迹点越多（默认 1.2，可试 0.8/0.5）',
    )
    args = parser.parse_args()

    if not (0.2 <= args.max_seg_km <= 2.0):
        raise SystemExit('--max-seg-km 建议在 0.2～2.0 之间')

    boost_hours = tuple(int(x.strip()) for x in args.boost_hours.split(',') if x.strip())
    if not boost_hours:
        boost_hours = tuple(range(24))
    for h in boost_hours:
        if h < 0 or h > 23:
            raise SystemExit(f'无效小时: {h}')

    slow_zone_hours = tuple(int(x.strip()) for x in args.slow_zone_hours.split(',') if x.strip())
    if not slow_zone_hours:
        slow_zone_hours = (7, 8, 17, 18)
    for h in slow_zone_hours:
        if h < 0 or h > 23:
            raise SystemExit(f'无效慢速时段小时: {h}')

    out_path = Path(args.out) if args.out else (
        Path(__file__).parent.parent / 'database' / 'logistics_history_test.sql'
    )

    print('正在生成轨迹坐标...')
    sql = generate_sql(
        enrich=args.enrich,
        boost_hours=boost_hours,
        samples_per_boost_hour=args.samples_per_hour,
        slow_zone_hours=slow_zone_hours,
        slow_points_per_grid=args.slow_points,
        max_seg_km=args.max_seg_km,
    )

    out_path.write_text(sql, encoding='utf-8')
    print(f'[OK] 已写入 {out_path}')
    print(f'   max_seg_km={args.max_seg_km}  统计增强: {"开启" if args.enrich else "关闭"}')
    print(f'   boost_hours={boost_hours}（共 {len(boost_hours)} 个小时 × {args.samples_per_hour} 条）')
    print(f'   slow_zone_hours={slow_zone_hours}  slow_points_per_grid={args.slow_points}')
    print(f'   高峰时段（低速 boost）= {sorted(PEAK_HOURS)}')

    # 统计轨迹点
    pts = 0
    route_count = len(ROUTE_PLANS)
    for plan in ROUTE_PLANS:
        rid, oid, corr, h, m, day, drv, st, exp, act, cid, aid, shopid, prodid, whid = plan
        wps = CORRIDORS[corr]
        tks = generate_track_points(wps, h, max_seg_km=args.max_seg_km)
        if st == 3:
            pts += max(3, len(tks) // 2)
        else:
            pts += len(tks)
    boost_pts = len(boost_hours) * args.samples_per_hour if args.enrich else 0
    slow_pts  = len(slow_zone_hours) * len(SLOW_GRIDS) * 7 * args.slow_points if args.enrich else 0
    print(f'   路线数: {route_count}  走廊数: {len(CORRIDORS)}')
    print(f'   有机轨迹: {pts} 条  boost: {boost_pts} 条  慢速热点: {slow_pts} 条  合计: {pts+boost_pts+slow_pts+3} 条')


if __name__ == '__main__':
    main()
