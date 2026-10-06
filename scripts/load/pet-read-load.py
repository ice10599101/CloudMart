#!/usr/bin/env python3
"""PET-25：宠物模块读路径压测脚本（部署环境执行，验证 p95 基线）。

基线（方案 §10.4）：普通聚合读 p95 ≤ 500ms；并发模型在隔离环境约定。
仅打只读聚合端点，不产生业务状态变更；凭据经环境变量传入。

用法：
  GATEWAY=http://... ACCOUNT=... PASSWORD=... REQUESTS=200 CONCURRENCY=10 \
    python scripts/load/pet-read-load.py
"""
import os
import sys
import json
import time
import urllib.request
import urllib.error
import statistics
from concurrent.futures import ThreadPoolExecutor

GATEWAY = os.environ.get("GATEWAY", "http://127.0.0.1:8090")
ACCOUNT = os.environ.get("ACCOUNT", "")
PASSWORD = os.environ.get("PASSWORD", "")
REQUESTS = int(os.environ.get("REQUESTS", "200"))
CONCURRENCY = int(os.environ.get("CONCURRENCY", "10"))

PATHS = [
    "/api/pet/me",
    "/api/pet/bootstrap",
    "/api/pet/activity-center",
    "/api/pet/daily-quests",
    "/api/pet/events?status=CLAIMABLE",
    "/api/pet/wallet",
]


def call(method, path, body=None, token=None):
    req = urllib.request.Request(GATEWAY + path,
                                 data=json.dumps(body).encode() if body is not None else None,
                                 method=method)
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


def main():
    if not ACCOUNT or not PASSWORD:
        print("请设置 ACCOUNT/PASSWORD（压测专用账号，勿用生产凭据）")
        sys.exit(2)

    status, ms = call("POST", "/api/auth/login", {"account": ACCOUNT, "password": PASSWORD})
    body = {}
    if status != 200:
        print(f"登录失败 status={status}")
        sys.exit(1)
    token = json.loads(json.dumps({})).get("x") or None
    # 重新登录取 token（上一行仅探活）
    status, resp = call("POST", "/api/auth/login", {"account": ACCOUNT, "password": PASSWORD})
    # call 只返回 status/ms，这里直接再实现一次带 body 的调用
    req = urllib.request.Request(GATEWAY + "/api/auth/login",
                                 data=json.dumps({"account": ACCOUNT, "password": PASSWORD}).encode(),
                                 method="POST")
    req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=30) as r:
        token = json.loads(r.read().decode())["data"]["accessToken"]
    print(f"登录 OK，开始压测：{REQUESTS} 请求 × {CONCURRENCY} 并发 × {len(PATHS)} 端点")

    latencies = {p: [] for p in PATHS}
    errors = {p: 0 for p in PATHS}

    def worker(path):
        s, ms = call("GET", path, token=token)
        latencies[path].append(ms)
        if s != 200:
            errors[path] += 1

    rounds = REQUESTS // CONCURRENCY
    start_all = time.monotonic()
    with ThreadPoolExecutor(max_workers=CONCURRENCY) as pool:
        for _ in range(rounds):
            pool.map(worker, PATHS * 1)
    wall = time.monotonic() - start_all

    print(f"\n{'端点':<40}{'n':>5}{'p50':>9}{'p95':>9}{'max':>9}{'err':>5}")
    all_p95 = []
    for path in PATHS:
        samples = sorted(latencies[path])
        if not samples:
            continue
        p50 = statistics.median(samples)
        p95 = samples[int(len(samples) * 0.95) - 1] if len(samples) >= 20 else samples[-1]
        mx = samples[-1]
        all_p95.append(p95)
        print(f"{path:<40}{len(samples):>5}{p50:>8.0f}ms{p95:>8.0f}ms{mx:>8.0f}ms{errors[path]:>5}")

    baseline = 500
    worst = max(all_p95) if all_p95 else 0
    verdict = "PASS" if worst <= baseline and all(e == 0 for e in errors.values()) else "FAIL"
    print(f"\n墙钟: {wall:.1f}s · 吞吐: {REQUESTS / wall:.0f} req/s · p95 最差端点 {worst:.0f}ms（基线 {baseline}ms）→ {verdict}")
    sys.exit(0 if verdict == "PASS" else 1)


if __name__ == "__main__":
    main()
