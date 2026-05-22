#!/usr/bin/env python3
"""
benchmark.py — 智能物流系统性能基准测试
============================================
对三大核心接口进行并发性能测试，生成 CSV 数据、对比图表和测试报告。

测试项目：
  1. 缓存对比测试：仓库列表接口 GET /api/shop/{shopId}/warehouses
  2. 路径规划并发测试：路由规划接口 POST /api/logistics/route/plan
  3. 订单查询并发测试：订单列表接口 GET /api/orders?customerId={id}

用法：
  python scripts/benchmark/benchmark.py                    # 运行全部测试
  python scripts/benchmark/benchmark.py --test cache       # 只跑缓存对比
  python scripts/benchmark/benchmark.py --test routing     # 只跑路径规划
  python scripts/benchmark/benchmark.py --test order       # 只跑订单查询
  python scripts/benchmark/benchmark.py --skip-warmup     # 跳过预热（已预热过）
  python scripts/benchmark/benchmark.py --concurrency 1,5,10,20,50  # 自定义并发数

前置条件：
  - Docker Compose 全部服务已启动
  - 系统中有预置的测试数据（admin/driver/customer/merchant 账号，密码123456）
  - Python 3.9+ with requests, matplotlib

输出：
  - scripts/benchmark/data/*.csv          原始测试数据
  - scripts/benchmark/figures/*.png       对比图表
  - scripts/benchmark/data/report.json    汇总报告
"""

import argparse
import csv
import json
import os
import statistics
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from pathlib import Path
from typing import Optional

import requests

# ─── 配置 ───────────────────────────────────────────────────────────

BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data"
FIG_DIR = BASE_DIR / "figures"

GATEWAY = "http://localhost:8090"  # 网关地址

# 测试账号
USERS = {
    "admin":    {"username": "admin",     "password": "123456"},
    "customer": {"username": "sender",   "password": "123456"},
    "driver":   {"username": "driver",   "password": "123456"},
    "merchant": {"username": "merchant", "password": "123456"},
}

# 默认并发级别
DEFAULT_CONCURRENCY = [1, 5, 10, 20, 50]

# ─── 工具函数 ────────────────────────────────────────────────────────


def ensure_dirs():
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    FIG_DIR.mkdir(parents=True, exist_ok=True)


def login(role: str) -> Optional[str]:
    """登录并返回 token"""
    resp = requests.post(
        f"{GATEWAY}/api/auth/login",
        json=USERS[role],
        timeout=10,
    )
    if resp.status_code == 200:
        data = resp.json()
        payload = data.get("data", {})
        if payload is None:
            return None
        return payload.get("token")
    print(f"  ⚠ 登录失败 [{role}]: {resp.status_code} {resp.text[:120]}")
    return None


def login_all() -> dict:
    """登录所有角色，返回 {role: token}"""
    tokens = {}
    for role in USERS:
        token = login(role)
        if token:
            tokens[role] = token
            print(f"  ✓ {role} 登录成功")
        else:
            print(f"  ✗ {role} 登录失败")
    return tokens


def timed_request(method: str, url: str, headers: dict = None,
                  json_body: dict = None, timeout: int = 30) -> dict:
    """执行单次请求并返回 {status, elapsed_ms, error}"""
    start = time.perf_counter()
    try:
        if method.upper() == "GET":
            resp = requests.get(url, headers=headers, timeout=timeout)
        else:
            resp = requests.post(url, headers=headers, json=json_body, timeout=timeout)
        elapsed_ms = (time.perf_counter() - start) * 1000
        return {"status": resp.status_code, "elapsed_ms": elapsed_ms, "error": None}
    except Exception as e:
        elapsed_ms = (time.perf_counter() - start) * 1000
        return {"status": 0, "elapsed_ms": elapsed_ms, "error": str(e)}


def run_concurrent(fn, args_list, concurrency: int) -> list:
    """并发执行 fn(*args)，每个 args_list 元素调用一次"""
    results = []
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = {pool.submit(fn, *args): i for i, args in enumerate(args_list)}
        for future in as_completed(futures):
            results.append(future.result())
    return results


def save_csv(filename: str, rows: list, fieldnames: list):
    path = DATA_DIR / filename
    with open(path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    print(f"  → 已保存 {path}")


def stats_summary(values: list) -> dict:
    """统计摘要"""
    if not values:
        return {"count": 0, "mean": 0, "median": 0, "p95": 0, "p99": 0, "min": 0, "max": 0, "std": 0}
    sorted_v = sorted(values)
    n = len(sorted_v)
    return {
        "count": n,
        "mean": round(statistics.mean(sorted_v), 2),
        "median": round(statistics.median(sorted_v), 2),
        "p95": round(sorted_v[int(n * 0.95)] if n > 20 else sorted_v[-1], 2),
        "p99": round(sorted_v[int(n * 0.99)] if n > 100 else sorted_v[-1], 2),
        "min": round(min(sorted_v), 2),
        "max": round(max(sorted_v), 2),
        "std": round(statistics.stdev(sorted_v), 2) if n > 1 else 0,
    }


# ─── Redis 缓存清除 ────────────────────────────────────────────────────


def flush_redis_keys(*keys: str) -> bool:
    """通过 docker exec 在 Redis 中删除指定缓存 key"""
    for key in keys:
        try:
            result = subprocess.run(
                ["docker", "exec", "transport-redis", "redis-cli", "DEL", key],
                capture_output=True, text=True, timeout=5,
            )
            if result.returncode != 0:
                print(f"    ⚠ DEL {key} 失败: {result.stderr.strip()}")
                return False
        except Exception as e:
            print(f"    ⚠ DEL {key} 异常: {e}")
            return False
    return True


def flush_redis_pattern(pattern: str) -> bool:
    """通过 docker exec 在 Redis 中删除匹配 pattern 的所有 key"""
    try:
        # 先 SCAN 找出所有匹配的 key
        result = subprocess.run(
            ["docker", "exec", "transport-redis", "redis-cli", "--scan", "--pattern", pattern],
            capture_output=True, text=True, timeout=5,
        )
        keys = [k for k in result.stdout.strip().split("\n") if k.strip()]
        if keys:
            del_result = subprocess.run(
                ["docker", "exec", "transport-redis", "redis-cli", "DEL"] + keys,
                capture_output=True, text=True, timeout=5,
            )
            return del_result.returncode == 0
        return True
    except Exception as e:
        print(f"    ⚠ flush pattern {pattern} 异常: {e}")
        return False


# ─── 测试 1：缓存对比测试 ────────────────────────────────────────────


def test_cache(tokens: dict, n_requests: int = 50):
    """
    缓存前后对比测试（仓库列表 + 商城商品列表）。
    通过 docker exec redis-cli DEL 在每次冷请求前清除 Redis 缓存，
    确保每次冷请求都是真正的 Cache Miss。
    冷请求：flush Redis → 请求 → 记录耗时（每次 flush 后只发 1 个请求）
    热请求：不 flush → 连续发送 n_requests 个请求（首次填充后均为命中）
    """
    print("\n" + "=" * 60)
    print("测试 1：缓存前后对比测试")
    print("=" * 60)

    token = tokens.get("admin")
    if not token:
        print("  ✗ admin token 不可用，跳过")
        return None

    headers = {"Authorization": f"Bearer {token}"}

    # ── 测试的缓存接口 ──
    cache_tests = [
        {
            "name": "仓库列表",
            "url": f"{GATEWAY}/api/warehouses",
            "redis_keys": ["warehouseAll::all"],  # @Cacheable(value="warehouseAll", key="'all'")
        },
        {
            "name": "商城商品列表",
            "url": f"{GATEWAY}/api/products?current=1&size=10",
            "redis_pattern": "mallProducts*",  # @Cacheable(value="mallProducts", key=分页参数组合)
        },
    ]

    all_results = []
    all_reports = []

    for ct in cache_tests:
        name = ct["name"]
        url = ct["url"]
        print(f"\n  ── {name}接口: {url} ──")

        # 验证接口
        test_resp = timed_request("GET", url, headers=headers)
        if test_resp["status"] != 200:
            print(f"    ✗ 接口不可用: status={test_resp['status']}，跳过")
            continue
        print(f"    接口可用 (验证请求 {test_resp['elapsed_ms']:.0f}ms)")

        # ── 冷启动（缓存未命中）：每次请求前 flush Redis ──
        print(f"    冷启动测试：{n_requests} 次请求（每次前 flush Redis）...")
        cold_results = []
        for i in range(n_requests):
            # Flush Redis key
            if "redis_keys" in ct:
                flush_redis_keys(*ct["redis_keys"])
            elif "redis_pattern" in ct:
                flush_redis_pattern(ct["redis_pattern"])
            r = timed_request("GET", url, headers=headers)
            cold_results.append({
                "endpoint": name,
                "request_no": i + 1,
                "elapsed_ms": r["elapsed_ms"],
                "status": r["status"],
                "cache_hit": False,
            })

        # ── 热启动（缓存命中）：不 flush，首次填充后均为命中 ──
        print(f"    热启动测试：{n_requests} 次请求（缓存命中）...")
        warm_results = []
        for i in range(n_requests):
            r = timed_request("GET", url, headers=headers)
            warm_results.append({
                "endpoint": name,
                "request_no": i + 1,
                "elapsed_ms": r["elapsed_ms"],
                "status": r["status"],
                "cache_hit": True,
            })

        all_results.extend(cold_results)
        all_results.extend(warm_results)

        cold_times = [r["elapsed_ms"] for r in cold_results if r["status"] == 200]
        warm_times = [r["elapsed_ms"] for r in warm_results if r["status"] == 200]

        cold_stats = stats_summary(cold_times)
        warm_stats = stats_summary(warm_times)

        speedup = round(cold_stats["mean"] / warm_stats["mean"], 2) if warm_stats["mean"] > 0 else 0

        print(f"\n    缓存未命中: mean={cold_stats['mean']:.1f}ms, median={cold_stats['median']:.1f}ms, p95={cold_stats['p95']:.1f}ms, p99={cold_stats['p99']:.1f}ms")
        print(f"    缓存命中:   mean={warm_stats['mean']:.1f}ms, median={warm_stats['median']:.1f}ms, p95={warm_stats['p95']:.1f}ms, p99={warm_stats['p99']:.1f}ms")
        print(f"    性能提升:   {speedup:.1f}x")

        all_reports.append({
            "endpoint": name,
            "url": url,
            "n_requests": n_requests,
            "cold": cold_stats,
            "warm": warm_stats,
            "speedup": speedup,
        })

    # 保存 CSV
    if all_results:
        save_csv("cache_comparison.csv", all_results,
                 ["endpoint", "request_no", "elapsed_ms", "status", "cache_hit"])

    return {
        "test": "cache_comparison",
        "endpoints": all_reports,
        # 兼容：取第一个 endpoint 的数据作为主报告
        "url": all_reports[0]["url"] if all_reports else "",
        "n_requests": n_requests,
        "cold": all_reports[0]["cold"] if all_reports else {},
        "warm": all_reports[0]["warm"] if all_reports else {},
        "speedup": all_reports[0]["speedup"] if all_reports else 0,
    }


# ─── 测试 2：路径规划并发测试 ──────────────────────────────────────────


def test_routing(tokens: dict, concurrency_levels: list):
    """
    物流核心接口并发测试。
    测试 Hub 拓扑查询接口（GET /api/logistics/national-network/topology）
    和 Hub 列表查询接口（GET /api/logistics/national-network/hubs）的并发性能。
    使用 Python ThreadPoolExecutor 进行并发请求。
    """
    print("\n" + "=" * 60)
    print("测试 2：物流核心接口并发测试")
    print("=" * 60)

    token = tokens.get("admin")
    if not token:
        print("  ✗ admin token 不可用，跳过")
        return None

    headers = {"Authorization": f"Bearer {token}"}

    # 测试接口：Hub 列表查询（有 Redis 缓存，适合测并发吞吐）
    url = f"{GATEWAY}/api/logistics/national-network/hubs?level=2"

    # 验证接口
    test_resp = timed_request("GET", url, headers=headers)
    if test_resp["status"] != 200:
        print(f"  ⚠ Hub 列表接口不可用: status={test_resp['status']}")
        # 备用接口
        url = f"{GATEWAY}/api/logistics/national-network/topology"
        test_resp = timed_request("GET", url, headers=headers)
    if test_resp["status"] != 200:
        print(f"  ✗ 备用接口也不可用: status={test_resp['status']}，跳过")
        return None

    print(f"  接口验证成功 (单次耗时 {test_resp['elapsed_ms']:.0f}ms)")
    print(f"  测试URL: {url}")

    all_results = []
    summary = {}

    for c in concurrency_levels:
        n = max(c * 10, 50)  # 每个并发级别发 c*10 个请求
        args_list = [(url, headers)] * n
        print(f"\n  并发数 = {c}，发送 {n} 个请求...")
        results = run_concurrent(
            lambda u, hdr: timed_request("GET", u, headers=hdr),
            args_list,
            c,
        )
        for i, r in enumerate(results):
            all_results.append({
                "concurrency": c,
                "request_no": i + 1,
                "elapsed_ms": r["elapsed_ms"],
                "status": r["status"],
                "error": r["error"] or "",
            })

        times = [r["elapsed_ms"] for r in results if r["status"] == 200]
        s = stats_summary(times)
        success_rate = len(times) / len(results) * 100 if results else 0
        tps = len(times) / (sum(times) / 1000) if times else 0
        summary[str(c)] = {**s, "success_rate": round(success_rate, 1), "tps": round(tps, 1)}
        print(f"    mean={s['mean']:.1f}ms, p95={s['p95']:.1f}ms, TPS={tps:.1f}, 成功率={success_rate:.0f}%")

    save_csv("routing_concurrency.csv", all_results,
             ["concurrency", "request_no", "elapsed_ms", "status", "error"])

    return {
        "test": "logistics_concurrency",
        "url": url,
        "concurrency_levels": concurrency_levels,
        "summary": summary,
    }


def _test_routing_with_ab(token: str, concurrency_levels: list):
    """用 ab 工具测试物流接口（备用方案）"""
    print("\n  使用 Apache Bench (ab) 进行并发测试...")

    ab_url = f"{GATEWAY}/api/logistics/national-network/hubs?level=2"
    all_results = []
    summary = {}

    for c in concurrency_levels:
        n = max(c * 10, 50)
        cmd = [
            "ab", "-n", str(n), "-c", str(c),
            "-H", f"Authorization: Bearer {token}",
            "-s", "30",
            ab_url,
        ]
        print(f"\n  ab -n {n} -c {c} {ab_url}")
        try:
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
            output = result.stdout

            mean_ms = 0
            tps = 0
            fail_rate = 0
            for line in output.split("\n"):
                if "Time per request:" in line and "(mean)" in line:
                    mean_ms = float(line.split(":")[1].strip().split()[0])
                if "Requests per second:" in line:
                    tps = float(line.split(":")[1].strip().split()[0])
                if "Failed requests:" in line:
                    fail_count = int(line.split(":")[1].strip())
                    fail_rate = fail_count / n * 100

            print(f"    mean={mean_ms:.1f}ms, TPS={tps:.1f}, 失败率={fail_rate:.1f}%")
            all_results.append({
                "concurrency": c,
                "total_requests": n,
                "mean_ms": mean_ms,
                "tps": tps,
                "fail_rate": fail_rate,
            })
            summary[str(c)] = {"mean": mean_ms, "tps": tps, "fail_rate": fail_rate}

        except Exception as e:
            print(f"    ✗ ab 测试失败: {e}")
            summary[str(c)] = {"mean": 0, "tps": 0, "fail_rate": 100}

    save_csv("routing_concurrency.csv", all_results,
             ["concurrency", "total_requests", "mean_ms", "tps", "fail_rate"])

    return {
        "test": "logistics_concurrency_ab",
        "url": ab_url,
        "concurrency_levels": concurrency_levels,
        "summary": summary,
    }


# ─── 测试 3：订单查询并发测试 ──────────────────────────────────────────


def test_order(tokens: dict, concurrency_levels: list):
    """
    订单查询接口并发测试。
    GET /api/orders?current=1&size=10
    """
    print("\n" + "=" * 60)
    print("测试 3：订单查询接口并发测试")
    print("=" * 60)

    token = tokens.get("customer")
    if not token:
        print("  ✗ customer token 不可用，跳过")
        return None

    headers = {"Authorization": f"Bearer {token}"}
    url = f"{GATEWAY}/api/orders?current=1&size=10"

    # 验证接口
    test_resp = timed_request("GET", url, headers=headers)
    if test_resp["status"] != 200:
        print(f"  ✗ 订单查询接口返回 {test_resp['status']}，跳过")
        return None

    print(f"  接口验证成功 (单次耗时 {test_resp['elapsed_ms']:.0f}ms)")

    all_results = []
    summary = {}

    for c in concurrency_levels:
        n = max(c * 10, 50)  # 每个并发级别发 c*10 个请求
        args_list = [(url, headers)] * n
        print(f"\n  并发数 = {c}，发送 {n} 个请求...")
        results = run_concurrent(
            lambda url, hdr: timed_request("GET", url, headers=hdr),
            args_list,
            c,
        )
        for i, r in enumerate(results):
            all_results.append({
                "concurrency": c,
                "request_no": i + 1,
                "elapsed_ms": r["elapsed_ms"],
                "status": r["status"],
                "error": r["error"] or "",
            })

        times = [r["elapsed_ms"] for r in results if r["status"] == 200]
        s = stats_summary(times)
        success_rate = len(times) / len(results) * 100 if results else 0
        tps = len(times) / (sum(times) / 1000) if times else 0
        summary[str(c)] = {**s, "success_rate": round(success_rate, 1), "tps": round(tps, 1)}
        print(f"    mean={s['mean']:.1f}ms, p95={s['p95']:.1f}ms, TPS={tps:.1f}, 成功率={success_rate:.0f}%")

    save_csv("order_query_concurrency.csv", all_results,
             ["concurrency", "request_no", "elapsed_ms", "status", "error"])

    return {
        "test": "order_query_concurrency",
        "url": url,
        "concurrency_levels": concurrency_levels,
        "summary": summary,
    }


# ─── 图表生成 ─────────────────────────────────────────────────────────


def generate_charts():
    """根据 CSV 数据生成对比图表"""
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    plt.rcParams["font.sans-serif"] = ["Arial Unicode MS", "SimHei", "PingFang SC"]
    plt.rcParams["axes.unicode_minus"] = False

    # ── 缓存对比图
    cache_csv = DATA_DIR / "cache_comparison.csv"
    if cache_csv.exists():
        with open(cache_csv, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            rows = list(reader)

        endpoints = sorted(set(r["endpoint"] for r in rows)) if rows and "endpoint" in rows[0] else [""]

        # 每个端点一张图
        for ep in endpoints:
            if "endpoint" in rows[0]:
                ep_rows = [r for r in rows if r["endpoint"] == ep]
            else:
                ep_rows = rows

            cold = [float(r["elapsed_ms"]) for r in ep_rows if r["cache_hit"] == "False"]
            warm = [float(r["elapsed_ms"]) for r in ep_rows if r["cache_hit"] == "True"]

            fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5))

            # 左图：逐请求响应时间散点
            x_cold = range(1, len(cold) + 1)
            x_warm = range(1, len(warm) + 1)
            ax1.plot(x_cold, cold, "o-", label="Cache Miss", markersize=3, alpha=0.7, color="#e74c3c")
            ax1.plot(x_warm, warm, "s-", label="Cache Hit", markersize=3, alpha=0.7, color="#2ecc71")
            if cold:
                ax1.axhline(y=statistics.mean(cold), color="#e74c3c", linestyle="--", alpha=0.5,
                           label=f"Miss 均值: {statistics.mean(cold):.1f}ms")
            if warm:
                ax1.axhline(y=statistics.mean(warm), color="#2ecc71", linestyle="--", alpha=0.5,
                           label=f"Hit 均值: {statistics.mean(warm):.1f}ms")
            ax1.set_xlabel("Request No.")
            ax1.set_ylabel("Response Time (ms)")
            title_ep = f" ({ep})" if ep else ""
            ax1.set_title(f"Cache Miss vs Hit{title_ep}")
            ax1.legend(fontsize=8)
            ax1.grid(True, alpha=0.3)

            # 右图：箱线图对比
            box_data = []
            box_labels = []
            if cold:
                box_data.append(cold)
                box_labels.append(f"Cache Miss\n(n={len(cold)})")
            if warm:
                box_data.append(warm)
                box_labels.append(f"Cache Hit\n(n={len(warm)})")
            if box_data:
                bp = ax2.boxplot(box_data, labels=box_labels, patch_artist=True,
                                boxprops=dict(alpha=0.7),
                                medianprops=dict(color="black", linewidth=2))
                if len(bp["boxes"]) >= 1:
                    bp["boxes"][0].set_facecolor("#e74c3c")
                if len(bp["boxes"]) >= 2:
                    bp["boxes"][1].set_facecolor("#2ecc71")
            ax2.set_ylabel("Response Time (ms)")
            ax2.set_title(f"Response Time Distribution{title_ep}")
            ax2.grid(True, alpha=0.3, axis="y")

            fig.tight_layout()
            fname = f"cache_comparison_{ep.replace(' ', '_')}.png" if ep else "cache_comparison.png"
            fig.savefig(FIG_DIR / fname, dpi=150)
            plt.close(fig)
            print(f"  → 已保存 {FIG_DIR / fname}")

        # 综合对比柱状图
        if len(endpoints) > 1:
            fig, ax = plt.subplots(figsize=(10, 5))
            x_pos = list(range(len(endpoints)))
            bar_width = 0.35
            cold_means = []
            warm_means = []
            for ep in endpoints:
                ep_rows = [r for r in rows if r["endpoint"] == ep]
                cold_vals = [float(r["elapsed_ms"]) for r in ep_rows if r["cache_hit"] == "False"]
                warm_vals = [float(r["elapsed_ms"]) for r in ep_rows if r["cache_hit"] == "True"]
                cold_means.append(statistics.mean(cold_vals) if cold_vals else 0)
                warm_means.append(statistics.mean(warm_vals) if warm_vals else 0)

            bars1 = ax.bar([p - bar_width/2 for p in x_pos], cold_means, bar_width,
                          label="Cache Miss", color="#e74c3c", alpha=0.8)
            bars2 = ax.bar([p + bar_width/2 for p in x_pos], warm_means, bar_width,
                          label="Cache Hit", color="#2ecc71", alpha=0.8)

            # 在柱子上标注提速倍数
            for i, (cm, wm) in enumerate(zip(cold_means, warm_means)):
                if wm > 0:
                    speedup = cm / wm
                    ax.annotate(f"{speedup:.1f}x", (x_pos[i] + bar_width/2, wm),
                              ha="center", va="bottom", fontsize=10, fontweight="bold", color="#2ecc71")

            ax.set_xticks(x_pos)
            ax.set_xticklabels(endpoints)
            ax.set_ylabel("Mean Response Time (ms)")
            ax.set_title("Cache Performance Comparison Across Endpoints")
            ax.legend()
            ax.grid(True, alpha=0.3, axis="y")
            fig.tight_layout()
            fig.savefig(FIG_DIR / "cache_comparison_summary.png", dpi=150)
            plt.close(fig)
            print(f"  → 已保存 {FIG_DIR / 'cache_comparison_summary.png'}")

    # ── 路径规划并发图
    routing_csv = DATA_DIR / "routing_concurrency.csv"
    if routing_csv.exists():
        with open(routing_csv, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            rows = list(reader)

        # 检查是 ab 格式还是 Python 格式
        if "total_requests" in rows[0]:
            # ab 格式
            concs = [int(r["concurrency"]) for r in rows]
            means = [float(r["mean_ms"]) for r in rows]
            tps_list = [float(r["tps"]) for r in rows]

            fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12, 5))
            ax1.bar([str(c) for c in concs], means, color="#3498db")
            ax1.set_xlabel("Concurrency")
            ax1.set_ylabel("Mean Response Time (ms)")
            ax1.set_title("Route Planning: Mean RT vs Concurrency")
            ax1.grid(True, alpha=0.3, axis="y")

            ax2.bar([str(c) for c in concs], tps_list, color="#e67e22")
            ax2.set_xlabel("Concurrency")
            ax2.set_ylabel("TPS")
            ax2.set_title("Route Planning: TPS vs Concurrency")
            ax2.grid(True, alpha=0.3, axis="y")
        else:
            # Python 并发格式
            concs = sorted(set(int(r["concurrency"]) for r in rows))
            means = []
            p95s = []
            tps_list = []
            for c in concs:
                times = [float(r["elapsed_ms"]) for r in rows
                         if int(r["concurrency"]) == c and int(r["status"]) == 200]
                s = stats_summary(times)
                means.append(s["mean"])
                p95s.append(s["p95"])
                tps_list.append(len(times) / (sum(times) / 1000) if times else 0)

            fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12, 5))
            x_labels = [str(c) for c in concs]
            ax1.bar(x_labels, means, color="#3498db", alpha=0.8, label="Mean")
            ax1.plot(x_labels, p95s, "r^-", label="P95", markersize=8)
            ax1.set_xlabel("Concurrency")
            ax1.set_ylabel("Response Time (ms)")
            ax1.set_title("Route Planning: RT vs Concurrency")
            ax1.legend()
            ax1.grid(True, alpha=0.3, axis="y")

            ax2.bar(x_labels, tps_list, color="#e67e22")
            ax2.set_xlabel("Concurrency")
            ax2.set_ylabel("TPS")
            ax2.set_title("Route Planning: TPS vs Concurrency")
            ax2.grid(True, alpha=0.3, axis="y")

        fig.tight_layout()
        fig.savefig(FIG_DIR / "routing_benchmark.png", dpi=150)
        plt.close(fig)
        print(f"  → 已保存 {FIG_DIR / 'routing_benchmark.png'}")

    # ── 订单查询并发图
    order_csv = DATA_DIR / "order_query_concurrency.csv"
    if order_csv.exists():
        with open(order_csv, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            rows = list(reader)

        concs = sorted(set(int(r["concurrency"]) for r in rows))
        means = []
        p95s = []
        tps_list = []
        success_rates = []
        for c in concs:
            c_rows = [r for r in rows if int(r["concurrency"]) == c]
            times = [float(r["elapsed_ms"]) for r in c_rows if int(r["status"]) == 200]
            s = stats_summary(times)
            means.append(s["mean"])
            p95s.append(s["p95"])
            tps_list.append(len(times) / (sum(times) / 1000) if times else 0)
            success_rates.append(len(times) / len(c_rows) * 100 if c_rows else 0)

        fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12, 5))
        x_labels = [str(c) for c in concs]
        ax1.bar(x_labels, means, color="#9b59b6", alpha=0.8, label="Mean")
        ax1.plot(x_labels, p95s, "r^-", label="P95", markersize=8)
        ax1.set_xlabel("Concurrency")
        ax1.set_ylabel("Response Time (ms)")
        ax1.set_title("Order Query: RT vs Concurrency")
        ax1.legend()
        ax1.grid(True, alpha=0.3, axis="y")

        ax2_tps = ax2.bar(x_labels, tps_list, color="#1abc9c", alpha=0.8, label="TPS")
        ax2_sr = ax2.twinx()
        ax2_sr.plot(x_labels, success_rates, "rs--", label="Success Rate %", markersize=8)
        ax2.set_xlabel("Concurrency")
        ax2.set_ylabel("TPS", color="#1abc9c")
        ax2_sr.set_ylabel("Success Rate (%)", color="red")
        ax2.set_title("Order Query: TPS & Success Rate vs Concurrency")
        ax2.grid(True, alpha=0.3, axis="y")

        # 合并图例
        lines1, labels1 = ax2.get_legend_handles_labels()
        lines2, labels2 = ax2_sr.get_legend_handles_labels()
        ax2.legend(lines1 + lines2, labels1 + labels2, loc="upper left")

        fig.tight_layout()
        fig.savefig(FIG_DIR / "order_benchmark.png", dpi=150)
        plt.close(fig)
        print(f"  → 已保存 {FIG_DIR / 'order_benchmark.png'}")


# ─── 主流程 ──────────────────────────────────────────────────────────


def main():
    global GATEWAY
    parser = argparse.ArgumentParser(description="智能物流系统性能基准测试")
    parser.add_argument("--test", choices=["cache", "routing", "order", "all"], default="all",
                        help="选择测试项 (default: all)")
    parser.add_argument("--skip-warmup", action="store_true",
                        help="跳过 JVM 预热请求")
    parser.add_argument("--concurrency", default=None,
                        help="自定义并发级别，逗号分隔 (e.g. 1,5,10,20,50)")
    gateway_default = GATEWAY
    parser.add_argument("--gateway", default=gateway_default,
                        help=f"网关地址 (default: {gateway_default})")
    args = parser.parse_args()

    GATEWAY = args.gateway

    concurrency_levels = DEFAULT_CONCURRENCY
    if args.concurrency:
        concurrency_levels = [int(x) for x in args.concurrency.split(",")]

    ensure_dirs()

    print(f"{'=' * 60}")
    print(f"智能物流系统性能基准测试")
    print(f"时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"网关: {GATEWAY}")
    print(f"并发级别: {concurrency_levels}")
    print(f"{'=' * 60}")

    # 1. 登录
    print("\n[1/4] 登录各角色...")
    tokens = login_all()
    if not tokens:
        print("✗ 所有登录均失败，退出")
        sys.exit(1)

    # 2. 预热（JVM 预热）
    if not args.skip_warmup:
        print("\n[2/4] JVM 预热（发送 5 次请求）...")
        for role, token in tokens.items():
            if role == "merchant":
                timed_request("GET", f"{GATEWAY}/api/shop/my",
                              headers={"Authorization": f"Bearer {token}"})
            elif role == "customer":
                timed_request("GET", f"{GATEWAY}/api/orders?current=1&size=5",
                              headers={"Authorization": f"Bearer {token}"})
            elif role == "driver":
                timed_request("GET", f"{GATEWAY}/api/drivers/deliveries",
                              headers={"Authorization": f"Bearer {token}"})
        print("  ✓ 预热完成")
    else:
        print("\n[2/4] 跳过预热")

    # 3. 执行测试
    print("\n[3/4] 执行性能测试...")
    reports = []

    if args.test in ("cache", "all"):
        r = test_cache(tokens)
        if r:
            reports.append(r)

    if args.test in ("routing", "all"):
        r = test_routing(tokens, concurrency_levels)
        if r:
            reports.append(r)

    if args.test in ("order", "all"):
        r = test_order(tokens, concurrency_levels)
        if r:
            reports.append(r)

    # 4. 生成图表和报告
    print("\n[4/4] 生成图表和报告...")
    generate_charts()

    # 汇总报告
    report = {
        "timestamp": datetime.now().isoformat(),
        "gateway": GATEWAY,
        "concurrency_levels": concurrency_levels,
        "tests": reports,
    }

    report_path = DATA_DIR / "report.json"
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(f"  → 已保存 {report_path}")

    # 打印摘要
    print("\n" + "=" * 60)
    print("测试结果摘要")
    print("=" * 60)
    for t in reports:
        print(f"\n【{t['test']}】")
        if t["test"] == "cache_comparison":
            # 多端点模式
            if "endpoints" in t and t["endpoints"]:
                for ep in t["endpoints"]:
                    print(f"  [{ep['endpoint']}]")
                    print(f"    缓存未命中均值: {ep['cold']['mean']:.1f}ms (p95={ep['cold']['p95']:.1f}ms)")
                    print(f"    缓存命中均值:   {ep['warm']['mean']:.1f}ms (p95={ep['warm']['p95']:.1f}ms)")
                    print(f"    性能提升:       {ep['speedup']:.1f}x")
            else:
                print(f"  缓存未命中均值: {t['cold']['mean']:.1f}ms")
                print(f"  缓存命中均值:   {t['warm']['mean']:.1f}ms")
                print(f"  性能提升:       {t['speedup']:.1f}x")
        elif "summary" in t:
            for c, s in t["summary"].items():
                if "tps" in s:
                    print(f"  并发={c}: mean={s['mean']:.1f}ms, p95={s.get('p95', 'N/A')}ms, TPS={s['tps']:.1f}")
                else:
                    print(f"  并发={c}: mean={s['mean']:.1f}ms, TPS={s['tps']:.1f}")

    print(f"\n✓ 全部测试完成！数据在 {DATA_DIR}，图表在 {FIG_DIR}")


if __name__ == "__main__":
    main()
