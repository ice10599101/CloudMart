#!/usr/bin/env python3
"""PET-25/R30：宠物模块读路径压测脚本（部署环境执行，验证延迟基线）。

基线（方案 §10.4）：普通聚合读 p95 ≤ 500ms；并发模型在隔离环境约定。
R30 扩展：纳入协作 PVE 列表（N+1 修复点），输出 p50/p95/p99 与错误率，
支持 RESULT_CSV 落盘以便优化前后代表数据对比；只打只读端点，不产生业务状态变更。

用法：
  GATEWAY=http://... ACCOUNT=... PASSWORD=... REQUESTS=200 CONCURRENCY=10 \
    python scripts/load/pet-read-load.py
  RESULT_CSV=baseline-before.csv ...   # 额外输出每端点 CSV
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
         token: Optional[str] = None) -> tuple[int, float]:
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
            resp.read()
            return resp.status, (time.monotonic() - start) * 1000
    except urllib.error.HTTPError as e:
        e.read()
        return e.code, (time.monotonic() - start) * 1000
    except Exception:
        return 0, (time.monotonic() - start) * 1000


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
    if not ACCOUNT or not PASSWORD:
        print("请设置 ACCOUNT/PASSWORD（压测专用账号，勿用生产凭据）")
        sys.exit(2)

    token = login()
    rounds = max(1, REQUESTS // CONCURRENCY)
    print(f"登录 OK：{rounds} 轮 × {len(PATHS)} 端点（并发 {CONCURRENCY}）= "
          f"{rounds * len(PATHS)} 请求")

    latencies: dict[str, list[float]] = {p: [] for p in PATHS}
    errors: dict[str, int] = {p: 0 for p in PATHS}

    def worker(path: str) -> None:
        status, ms = call("GET", path, token=token)
        latencies[path].append(ms)
        if status != 200:
            errors[path] += 1

    start_all = time.monotonic()
    with ThreadPoolExecutor(max_workers=CONCURRENCY) as pool:
        for _ in range(rounds):
            list(pool.map(worker, PATHS))
    wall = time.monotonic() - start_all

    total_calls = sum(len(v) for v in latencies.values())
    total_errors = sum(errors.values())
    rows = []
    print(f"\n{'端点':<42}{'n':>5}{'p50':>9}{'p95':>9}{'p99':>9}{'max':>9}"
          f"{'err':>5}{'err%':>7}")
    all_p95 = []
    for path in PATHS:
        samples = sorted(latencies[path])
        if not samples:
            continue
        p50 = statistics.median(samples)
        p95 = percentile(samples, 0.95)
        p99 = percentile(samples, 0.99)
        mx = samples[-1]
        err_rate = errors[path] / len(samples) * 100
        all_p95.append(p95)
        rows.append({
            "endpoint": path, "n": len(samples),
            "p50_ms": round(p50, 1), "p95_ms": round(p95, 1),
            "p99_ms": round(p99, 1), "max_ms": round(mx, 1),
            "errors": errors[path], "error_rate_pct": round(err_rate, 2),
        })
        print(f"{path:<42}{len(samples):>5}{p50:>8.0f}ms{p95:>8.0f}ms"
              f"{p99:>8.0f}ms{mx:>8.0f}ms{errors[path]:>5}{err_rate:>6.1f}%")

    if RESULT_CSV:
        with open(RESULT_CSV, "w", newline="", encoding="utf-8") as fh:
            writer = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
            writer.writeheader()
            writer.writerows(rows)
        print(f"\nCSV 已写入：{RESULT_CSV}")

    worst_p95 = max(all_p95) if all_p95 else 0.0
    passed = worst_p95 <= P95_BASELINE_MS and total_errors == 0
    verdict = "PASS" if passed else "FAIL"
    print(f"\n墙钟: {wall:.1f}s · 吞吐: {total_calls / wall:.0f} req/s · "
          f"错误 {total_errors}/{total_calls} · p95 最差端点 {worst_p95:.0f}ms"
          f"（基线 {P95_BASELINE_MS:.0f}ms）→ {verdict}")
    sys.exit(0 if passed else 1)


if __name__ == "__main__":
    main()
