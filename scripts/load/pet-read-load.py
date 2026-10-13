#!/usr/bin/env python3
"""PET-25/R30：宠物模块读路径压测脚本（部署环境执行，验证延迟基线）。

基线（方案 §10.4）：普通聚合读 p95 ≤ 500ms；并发模型在隔离环境约定。
R30 扩展：纳入协作 PVE 列表（N+1 修复点），输出 p50/p95/p99 与错误率，
支持 RESULT_CSV 落盘以便优化前后代表数据对比；只打只读端点，不产生业务状态变更。

用法：
  GATEWAY=http://... ACCOUNT=... PASSWORD=... REQUESTS=200 CONCURRENCY=10 \
    python scripts/load/pet-read-load.py
  RESULT_CSV=baseline-before.csv ...   # 额外输出每端点 CSV

免登录模式（凭据不经脚本；TOKEN 从浏览器/网关日志取，过期重取）：
  GATEWAY=http://... TOKEN=eyJ... python scripts/load/pet-read-load.py
"""
import csv
import json
import os
import statistics
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from typing import Optional

GATEWAY = os.environ.get("GATEWAY", "http://127.0.0.1:8090")
ACCOUNT = os.environ.get("ACCOUNT", "")
PASSWORD = os.environ.get("PASSWORD", "")
# 已签发的 JWT：提供时跳过账号密码登录（凭据不落命令行）
TOKEN = os.environ.get("TOKEN", "")
REQUESTS = int(os.environ.get("REQUESTS", "200"))
CONCURRENCY = int(os.environ.get("CONCURRENCY", "10"))
P95_BASELINE_MS = float(os.environ.get("P95_BASELINE_MS", "500"))
RESULT_CSV = os.environ.get("RESULT_CSV", "")

# R30：/pve/open、/pve/mine 是 PetPveServiceImpl 批量 enrich 改造的验证端点
PATHS = [
    "/api/pet/me",
    "/api/pet/bootstrap",
    "/api/pet/activity-center",
    "/api/pet/daily-quests",
    "/api/pet/events?status=CLAIMABLE",
    "/api/pet/wallet",
    "/api/pet/pve/bosses",
    "/api/pet/pve/open?page=1&size=50",
    "/api/pet/pve/mine?page=1&size=50",
]


def call(method: str, path: str, body: Optional[dict] = None,
         token: Optional[str] = None) -> tuple[int, float, str]:
    """返回 (HTTP状态码, 毫秒, 响应体文本)。网络层失败状态码记 0。"""
    req = urllib.request.Request(
        GATEWAY + path,
        data=json.dumps(body).encode() if body is not None else None,
        method=method,
    )
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    start = time.monotonic()
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            text = resp.read().decode("utf-8", errors="replace")
            return resp.status, (time.monotonic() - start) * 1000, text
    except urllib.error.HTTPError as e:
        text = e.read().decode("utf-8", errors="replace")
        return e.code, (time.monotonic() - start) * 1000, text
    except Exception as exc:
        return 0, (time.monotonic() - start) * 1000, f"NETWORK_ERROR: {exc}"


def login() -> str:
    """单次登录换取 JWT（压测专用账号，凭据仅经环境变量传入）。"""
    req = urllib.request.Request(
        GATEWAY + "/api/auth/login",
        data=json.dumps({"account": ACCOUNT, "password": PASSWORD}).encode(),
        method="POST",
    )
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            payload = json.loads(resp.read().decode())
    except urllib.error.HTTPError as exc:
        print(f"登录失败 status={exc.code}")
        sys.exit(1)
    except Exception as exc:
        print(f"登录请求失败: {exc}")
        sys.exit(1)
    token = (payload.get("data") or {}).get("accessToken")
    if not token:
        print("登录响应缺少 data.accessToken")
        sys.exit(1)
    return token


def percentile(sorted_samples: list[float], q: float) -> float:
    """最近秩百分位（q∈(0,1]），空列表返回 0。"""
    if not sorted_samples:
        return 0.0
    index = max(0, int(round(q * len(sorted_samples))) - 1)
    return sorted_samples[index]


def main() -> None:
    # 优先使用外部提供的已签发 token；没有才要求账号密码登录
    token = TOKEN or None
    if token:
        print("使用 TOKEN 免登录模式（不发送账号密码）")
    else:
        if not ACCOUNT or not PASSWORD:
            print("请设置 TOKEN（已签发 JWT）或 ACCOUNT/PASSWORD（压测专用账号）")
            sys.exit(2)
        token = login()
    rounds = max(1, REQUESTS // CONCURRENCY)
    print(f"开始压测：{rounds} 轮 × {len(PATHS)} 端点（并发 {CONCURRENCY}）= "
          f"{rounds * len(PATHS)} 请求")

    latencies: dict[str, list[float]] = {p: [] for p in PATHS}
    # 错误按性质分桶：4xx=业务态（如未领养宠物）、5xx=服务端错误、0=网络错误
    err_4xx: dict[str, int] = {p: 0 for p in PATHS}
    err_5xx: dict[str, int] = {p: 0 for p in PATHS}
    err_net: dict[str, int] = {p: 0 for p in PATHS}
    # 每个端点保留首个非 200 响应作为诊断样本（线程安全由 GIL 下赋值原子性保证）
    first_error_sample: dict[str, tuple[int, str]] = {}

    def worker(path: str) -> None:
        status, ms, body = call("GET", path, token=token)
        latencies[path].append(ms)
        if status == 200:
            return
        if status == 0:
            err_net[path] += 1
        elif 400 <= status < 500:
            err_4xx[path] += 1
        else:
            err_5xx[path] += 1
        if path not in first_error_sample:
            first_error_sample[path] = (status, body[:300])

    start_all = time.monotonic()
    with ThreadPoolExecutor(max_workers=CONCURRENCY) as pool:
        for _ in range(rounds):
            list(pool.map(worker, PATHS))
    wall = time.monotonic() - start_all

    total_calls = sum(len(v) for v in latencies.values())
    total_4xx = sum(err_4xx.values())
    total_5xx = sum(err_5xx.values())
    total_net = sum(err_net.values())
    total_hard_errors = total_5xx + total_net
    rows = []
    print(f"\n{'端点':<42}{'n':>5}{'p50':>9}{'p95':>9}{'p99':>9}{'max':>9}"
          f"{'4xx':>5}{'5xx':>5}{'net':>5}")
    all_p95 = []
    for path in PATHS:
        samples = sorted(latencies[path])
        if not samples:
            continue
        p50 = statistics.median(samples)
        p95 = percentile(samples, 0.95)
        p99 = percentile(samples, 0.99)
        mx = samples[-1]
        all_p95.append(p95)
        rows.append({
            "endpoint": path, "n": len(samples),
            "p50_ms": round(p50, 1), "p95_ms": round(p95, 1),
            "p99_ms": round(p99, 1), "max_ms": round(mx, 1),
            "err_4xx": err_4xx[path], "err_5xx": err_5xx[path],
            "err_network": err_net[path],
        })
        print(f"{path:<42}{len(samples):>5}{p50:>8.0f}ms{p95:>8.0f}ms"
              f"{p99:>8.0f}ms{mx:>8.0f}ms{err_4xx[path]:>5}{err_5xx[path]:>5}{err_net[path]:>5}")

    if first_error_sample:
        print("\n非 200 响应样本（每端点首个，用于区分业务态与系统故障）：")
        for path, (status, body) in first_error_sample.items():
            print(f"  [{status}] {path}\n        {body}")

    if RESULT_CSV:
        with open(RESULT_CSV, "w", newline="", encoding="utf-8") as fh:
            writer = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
            writer.writeheader()
            writer.writerows(rows)
        print(f"\nCSV 已写入：{RESULT_CSV}")

    worst_p95 = max(all_p95) if all_p95 else 0.0
    # 判定口径：p95 达标且无 5xx/网络错误即 PASS；4xx 多为压测账号业务态（如未领养宠物），
    # 单独提示不计入系统失败，但必须在结论中说明
    passed = worst_p95 <= P95_BASELINE_MS and total_hard_errors == 0
    verdict = "PASS" if passed else "FAIL"
    print(f"\n墙钟: {wall:.1f}s · 吞吐: {total_calls / wall:.0f} req/s · "
          f"非200: 4xx={total_4xx}(业务态) 5xx={total_5xx} net={total_net} · "
          f"p95 最差端点 {worst_p95:.0f}ms（基线 {P95_BASELINE_MS:.0f}ms）→ {verdict}")
    if total_4xx and total_hard_errors == 0:
        print("提示：存在 4xx 业务响应（见上方样本），通常是压测账号缺少前置数据"
              "（如未领养宠物），不属于服务故障；对比基线时这些端点的延迟仅供参考。")
    sys.exit(0 if passed else 1)


if __name__ == "__main__":
    main()
