#!/usr/bin/env python3
"""
logistics_history_test.sql 数据验证脚本
========================================
功能：
  1. 解析 SQL 文件，提取 logistics_track 轨迹点
  2. 验证坐标是否在上海地理范围内
  3. 验证相邻轨迹点间的速度一致性（Haversine 计算 vs speed 字段）
  4. 验证方位角（heading）与实际运动方向偏差
  5. 检测异常跳变（两点间距 > 5km 且时间间隔 < 2min）
  6. 【可选】验证坐标是否在 OSM 路网 100m 范围内（需 pip install pyosmium）

用法：
  python validate_logistics_tracks.py
  python validate_logistics_tracks.py --sql ../database/logistics_history_test.sql
  python validate_logistics_tracks.py --osm ../logistics-service/shanghai-260310.osm.pbf

依赖：
  标准库（无需安装）：re, math, sys, argparse, pathlib, collections
  可选（需 pip install pyosmium）：osmium — 验证坐标是否在路网附近
"""

import re
import math
import sys
import argparse
from pathlib import Path
from collections import defaultdict

# ─────────────────────────────────────────────────────────────────────────────
# 上海地理边界（含郊区缓冲）
# ─────────────────────────────────────────────────────────────────────────────
SHANGHAI_BOUNDS = {
    "lat_min": 30.65,  "lat_max": 31.88,
    "lon_min": 120.85, "lon_max": 122.20,
}

# 速度合理性参数
MAX_VEHICLE_SPEED_KMH  = 120.0   # 货车最高限速
MAX_SPEED_DEVIATION    = 0.50    # 允许与记录值偏差 50%（GPS 误差 + 非匀速）
MAX_JUMP_DIST_KM       = 5.0     # 两点间距离超过此值视为"跳变"
MAX_JUMP_TIME_MIN      = 2.0     # 跳变时间阈值（分钟）
MAX_HEADING_DIFF_DEG   = 90.0    # 允许方位角与实际方向偏差（°）


# ─────────────────────────────────────────────────────────────────────────────
# 地理计算工具
# ─────────────────────────────────────────────────────────────────────────────

def haversine_km(lat1, lon1, lat2, lon2) -> float:
    """球面两点距离（千米）"""
    R = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lon2 - lon1)
    a  = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def bearing_deg(lat1, lon1, lat2, lon2) -> float:
    """方位角（度，0=正北，顺时针）"""
    lat1, lon1, lat2, lon2 = map(math.radians, [lat1, lon1, lat2, lon2])
    y = math.sin(lon2 - lon1) * math.cos(lat2)
    x = math.cos(lat1) * math.sin(lat2) - math.sin(lat1) * math.cos(lat2) * math.cos(lon2 - lon1)
    return (math.degrees(math.atan2(y, x)) + 360) % 360


def heading_diff(h1, h2) -> float:
    """两个方位角之差（取最小角，0-180°）"""
    diff = abs(h1 - h2) % 360
    return min(diff, 360 - diff)


# ─────────────────────────────────────────────────────────────────────────────
# SQL 解析
# ─────────────────────────────────────────────────────────────────────────────

def parse_track_inserts(sql_text: str) -> dict[int, list[dict]]:
    """
    解析 SQL 中所有 INSERT INTO logistics_track 的 VALUES 行。
    返回 {route_id: [track_point, ...]} 按 route_id 分组，已按时间偏移排序。

    轨迹字段顺序（对应 SQL 列声明）：
      route_id, driver_id, latitude, longitude, altitude,
      speed, heading, accuracy, address, track_time
    """
    result: dict[int, list[dict]] = defaultdict(list)

    # 找所有 INSERT INTO `logistics_track` ... VALUES (...),(...),...;
    block_pattern = re.compile(
        r"INSERT\s+INTO\s+`?logistics_track`?[^;]*?VALUES\s*(.*?);",
        re.DOTALL | re.IGNORECASE,
    )
    # 匹配单行 VALUES 组：(val1, val2, ...)
    row_pattern = re.compile(r"\(([^)]+)\)")

    for block_match in block_pattern.finditer(sql_text):
        values_block = block_match.group(1)
        for row_match in row_pattern.finditer(values_block):
            raw = [v.strip().strip("'\"") for v in row_match.group(1).split(",")]
            if len(raw) < 9:
                continue
            try:
                point = {
                    "route_id":  int(raw[0]),
                    "driver_id": int(raw[1]),
                    "lat":       float(raw[2]),
                    "lon":       float(raw[3]),
                    "altitude":  float(raw[4]) if raw[4] not in ("NULL", "null") else None,
                    "speed":     float(raw[5]) if raw[5] not in ("NULL", "null") else None,
                    "heading":   float(raw[6]) if raw[6] not in ("NULL", "null") else None,
                    "accuracy":  float(raw[7]) if raw[7] not in ("NULL", "null") else None,
                    "address":   raw[8],
                    "time_expr": raw[9] if len(raw) > 9 else "unknown",
                    # 从 DATE_ADD(@rX, INTERVAL N MINUTE) 提取分钟偏移
                    "offset_min": _extract_offset(raw[9] if len(raw) > 9 else ""),
                }
                result[point["route_id"]].append(point)
            except (ValueError, IndexError) as e:
                print(f"  [WARN] 解析行失败: {row_match.group(1)[:80]}... ({e})")

    # 按时间偏移排序
    for route_id in result:
        result[route_id].sort(key=lambda p: p["offset_min"])

    return dict(result)


def _extract_offset(time_expr: str) -> float:
    """从 DATE_ADD(@rX, INTERVAL N MINUTE) 中提取 N"""
    m = re.search(r"INTERVAL\s+(\d+)\s+MINUTE", time_expr, re.IGNORECASE)
    return float(m.group(1)) if m else 0.0


# ─────────────────────────────────────────────────────────────────────────────
# 验证逻辑
# ─────────────────────────────────────────────────────────────────────────────

def validate_all(tracks_by_route: dict[int, list[dict]]) -> tuple[int, int]:
    """
    对所有路线执行全部验证。
    返回 (error_count, warning_count)
    """
    errors, warnings = 0, 0

    for route_id in sorted(tracks_by_route):
        points = tracks_by_route[route_id]
        print(f"\n{'─'*60}")
        print(f"  Route {route_id}  ({len(points)} 个轨迹点)")
        print(f"{'─'*60}")

        e, w = validate_route(route_id, points)
        errors   += e
        warnings += w

    return errors, warnings


def validate_route(route_id: int, points: list[dict]) -> tuple[int, int]:
    errors, warnings = 0, 0

    for i, pt in enumerate(points):
        tag = f"Route{route_id}[{i}] @+{int(pt['offset_min'])}min"

        # ── 1. 坐标范围 ────────────────────────────────────────────
        lat, lon = pt["lat"], pt["lon"]
        if not (SHANGHAI_BOUNDS["lat_min"] <= lat <= SHANGHAI_BOUNDS["lat_max"] and
                SHANGHAI_BOUNDS["lon_min"] <= lon <= SHANGHAI_BOUNDS["lon_max"]):
            print(f"  ✗ [ERROR] {tag}  坐标 ({lat},{lon}) 超出上海范围")
            errors += 1
        else:
            print(f"  ✓ [OK]   {tag}  坐标 ({lat:.4f},{lon:.4f})  ✓")

        # ── 2. 速度字段合理性 ───────────────────────────────────────
        spd = pt["speed"]
        if spd is not None and spd > MAX_VEHICLE_SPEED_KMH:
            print(f"    ✗ [ERROR] {tag}  speed={spd} 超过货车限速 {MAX_VEHICLE_SPEED_KMH}km/h")
            errors += 1

        # ── 3. 与上一点的一致性验证 ────────────────────────────────
        if i == 0:
            continue

        prev = points[i - 1]
        dist_km   = haversine_km(prev["lat"], prev["lon"], lat, lon)
        delta_min = pt["offset_min"] - prev["offset_min"]

        if delta_min <= 0:
            print(f"    ⚠ [WARN] {tag}  时间偏移未增加（delta={delta_min}min）")
            warnings += 1
            continue

        # 计算两点间平均速度（km/h）
        calc_speed = dist_km / delta_min * 60

        # 跳变检测（距离大但时间短）
        if dist_km > MAX_JUMP_DIST_KM and delta_min < MAX_JUMP_TIME_MIN:
            print(f"    ✗ [ERROR] {tag}  跳变! dist={dist_km:.2f}km, Δt={delta_min:.0f}min "
                  f"→ 隐含速度={calc_speed:.0f}km/h")
            errors += 1

        # 速度一致性（记录值 vs 计算值，使用段均速近似）
        if spd is not None and spd > 0 and calc_speed > 0:
            deviation = abs(calc_speed - spd) / max(spd, calc_speed)
            if deviation > MAX_SPEED_DEVIATION and calc_speed > 5:
                print(f"    ⚠ [WARN] {tag}  速度偏差较大: "
                      f"记录={spd:.0f}km/h  计算段均速={calc_speed:.0f}km/h  "
                      f"偏差={deviation*100:.0f}%")
                warnings += 1

        # 方位角一致性
        hdg = pt["heading"]
        if hdg is not None and dist_km > 0.05:    # 移动超过50m才校验方向
            actual_bearing = bearing_deg(prev["lat"], prev["lon"], lat, lon)
            diff = heading_diff(hdg, actual_bearing)
            if diff > MAX_HEADING_DIFF_DEG:
                print(f"    ⚠ [WARN] {tag}  方位角偏差: "
                      f"记录={hdg:.0f}°  实际={actual_bearing:.0f}°  差={diff:.0f}°")
                warnings += 1

    return errors, warnings


# ─────────────────────────────────────────────────────────────────────────────
# OSM 路网验证（可选，需 pyosmium）
# ─────────────────────────────────────────────────────────────────────────────

def validate_osm_proximity(tracks_by_route: dict[int, list[dict]],
                            osm_path: str,
                            max_dist_m: float = 100.0) -> None:
    """
    验证所有轨迹点是否在 OSM 路网 max_dist_m 米范围内。
    需要：pip install pyosmium
    """
    try:
        import osmium  # type: ignore
    except ImportError:
        print("\n[INFO] pyosmium 未安装，跳过 OSM 验证。")
        print("       安装命令: pip install pyosmium")
        return

    print(f"\n{'═'*60}")
    print(f"  读取 OSM 文件: {osm_path}")
    print(f"{'═'*60}")

    # ── 提取 OSM 路网节点 ──────────────────────────────────────────
    class RoadNodeCollector(osmium.SimpleHandler):
        def __init__(self):
            super().__init__()
            self.nodes: list[tuple[float, float]] = []  # (lat, lon)
            self.road_node_ids: set[int] = set()
            self.ways: list[list[int]] = []

        def way(self, w):
            # 仅收集机动车道路（highway=motorway/trunk/primary/secondary/tertiary/residential...）
            highway = w.tags.get("highway", "")
            if highway in {"motorway", "trunk", "primary", "secondary",
                           "tertiary", "residential", "unclassified", "service",
                           "motorway_link", "trunk_link", "primary_link"}:
                for node in w.nodes:
                    self.road_node_ids.add(node.ref)

        def node(self, n):
            if n.id in self.road_node_ids:
                self.nodes.append((n.location.lat, n.location.lon))

    handler = RoadNodeCollector()
    try:
        handler.apply_file(osm_path, locations=True)
    except Exception as e:
        print(f"  [ERROR] 读取 OSM 文件失败: {e}")
        return

    road_nodes = handler.nodes
    print(f"  共提取 {len(road_nodes):,} 个路网节点")

    if not road_nodes:
        print("  [WARN] 未提取到路网节点，请检查 OSM 文件路径")
        return

    # ── 对每个轨迹点查找最近路网节点 ──────────────────────────────
    errors = 0
    all_points = [(pt["lat"], pt["lon"], rid, i)
                  for rid, pts in tracks_by_route.items()
                  for i, pt in enumerate(pts)]

    print(f"  验证 {len(all_points)} 个轨迹点与路网的最近距离...")
    for lat, lon, route_id, idx in all_points:
        min_dist = min(
            haversine_km(lat, lon, nlat, nlon) * 1000  # 转米
            for nlat, nlon in road_nodes
        )
        if min_dist > max_dist_m:
            print(f"  ✗ [WARN] Route{route_id}[{idx}] ({lat},{lon}) "
                  f"距最近路网节点 {min_dist:.0f}m > {max_dist_m:.0f}m 阈值")
            errors += 1

    if errors == 0:
        print(f"  ✓ 所有 {len(all_points)} 个轨迹点均在路网 {max_dist_m}m 范围内")
    else:
        print(f"  共 {errors} 个轨迹点距路网较远（可能在小路或误差范围内）")


# ─────────────────────────────────────────────────────────────────────────────
# 统计报告
# ─────────────────────────────────────────────────────────────────────────────

def print_statistics(tracks_by_route: dict[int, list[dict]]) -> None:
    print(f"\n{'═'*60}")
    print("  数据统计摘要")
    print(f"{'═'*60}")

    total = sum(len(pts) for pts in tracks_by_route.values())
    print(f"  路线数量：{len(tracks_by_route)}")
    print(f"  轨迹点总数：{total}")

    # 各时段速度分布
    hour_speeds: dict[int, list[float]] = defaultdict(list)
    for route_id, pts in tracks_by_route.items():
        for pt in pts:
            hour = int(pt["offset_min"] // 60)  # 简单用偏移估算，实际小时由 @rX 决定
            if pt["speed"] is not None and pt["speed"] > 0:
                hour_speeds[hour].append(pt["speed"])

    # 慢速点（speed < 8）统计
    slow_points = [
        (pt["lat"], pt["lon"], pt["speed"], rid)
        for rid, pts in tracks_by_route.items()
        for pt in pts
        if pt["speed"] is not None and 0 < pt["speed"] < 8
    ]
    print(f"\n  低速点（speed<8km/h）共 {len(slow_points)} 个：")
    for lat, lon, spd, rid in slow_points:
        print(f"    Route{rid}  ({lat:.4f},{lon:.4f})  {spd:.0f}km/h")
        print(f"           → 网格键 ({round(lat,2)},{round(lon,2)}) 将触发 selectSlowZones")

    # 各路线速度摘要
    print("\n  各路线速度摘要：")
    for rid in sorted(tracks_by_route):
        pts = tracks_by_route[rid]
        speeds = [p["speed"] for p in pts if p["speed"] is not None and p["speed"] > 0]
        if speeds:
            print(f"    Route{rid}: min={min(speeds):.0f}  max={max(speeds):.0f}  "
                  f"avg={sum(speeds)/len(speeds):.1f} km/h  (n={len(speeds)})")


# ─────────────────────────────────────────────────────────────────────────────
# 主程序
# ─────────────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="验证 logistics_history_test.sql 中的轨迹数据合理性"
    )
    parser.add_argument(
        "--sql",
        default=str(Path(__file__).parent.parent / "database" / "logistics_history_test.sql"),
        help="SQL 文件路径（默认: ../database/logistics_history_test.sql）",
    )
    parser.add_argument(
        "--osm",
        default=None,
        help="OSM PBF 文件路径（可选，用于验证坐标是否在路网附近）",
    )
    parser.add_argument(
        "--osm-threshold",
        type=float,
        default=100.0,
        help="OSM 验证距离阈值（米，默认 100）",
    )
    args = parser.parse_args()

    sql_path = Path(args.sql)
    if not sql_path.exists():
        print(f"[ERROR] 找不到 SQL 文件: {sql_path}")
        sys.exit(1)

    print(f"{'═'*60}")
    print(f"  物流轨迹数据验证工具")
    print(f"  SQL: {sql_path}")
    print(f"{'═'*60}")

    sql_text = sql_path.read_text(encoding="utf-8")

    # ── 解析 ──────────────────────────────────────────────────────
    tracks_by_route = parse_track_inserts(sql_text)
    total_points = sum(len(v) for v in tracks_by_route.values())
    print(f"\n  解析完成：{len(tracks_by_route)} 条路线，{total_points} 个轨迹点\n")

    # ── 基础验证 ──────────────────────────────────────────────────
    errors, warnings = validate_all(tracks_by_route)

    # ── OSM 验证（可选）─────────────────────────────────────────
    if args.osm:
        osm_path = Path(args.osm)
        if osm_path.exists():
            validate_osm_proximity(tracks_by_route, str(osm_path), args.osm_threshold)
        else:
            print(f"\n[WARN] OSM 文件不存在: {osm_path}，跳过路网验证")

    # ── 统计摘要 ──────────────────────────────────────────────────
    print_statistics(tracks_by_route)

    # ── 最终结论 ──────────────────────────────────────────────────
    print(f"\n{'═'*60}")
    print(f"  验证结果：{errors} 个错误，{warnings} 个警告")
    if errors == 0 and warnings == 0:
        print("  ✅ 所有数据通过验证，可安全导入数据库")
    elif errors == 0:
        print("  ⚠️  无严重错误，警告可忽略（GPS 误差 / 非匀速导致）")
    else:
        print("  ❌ 存在严重错误，请修正后重新导入")
    print(f"{'═'*60}\n")

    sys.exit(0 if errors == 0 else 1)


if __name__ == "__main__":
    main()
