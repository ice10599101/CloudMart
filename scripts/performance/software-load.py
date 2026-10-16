#!/usr/bin/env python3
"""软件模块缩比压测（手册 14.4：1 万软件/10 万评论/100 并发 → 缩比执行并如实记录）。

缩比口径：5000 软件（ACTIVE+已审快照）/ 20000 评论（集中 50 个软件）/ 50 并发。
场景：列表 / 详情 / 评论分页 / 获取链接（认证）/ 榜单。
阶段：清残留（按 LOADTEST 指纹）→ 批量灌数 → 预热 → 压测 → 汇总分位数 → 清理。

安全边界：种子软件行以「灌数前 software 表 MAX(id)」为基线标记；清理只删
name LIKE 'LOADTEST%' 的 revision 及其关联行、content LIKE 'LOADTEST%' 的评论、
published_revision_id 指向这些 revision 的 software——不触碰任何真实数据。

运行前提：远程 MySQL 8306 / 网关 8090 / 用户服务 9001 可达；
压测窗口内经 Nacos 临时置 0 五项限流，退出还原（无论成败）。
输出：docs/software/load-result.json + 控制台摘要。
全量口径基线：列表 p95 ≤ 500ms，评论 p95 ≤ 800ms。
"""
import atexit
import json
import re
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from urllib.parse import quote

GW = "http://129.204.152.168:8090"
AUTH = "http://129.204.152.168:9001"
MYSQL = ["mysql", "-h", "129.204.152.168", "-P", "8306", "-uroot", "-p123456",
         "--connect-timeout=5", "--default-character-set=utf8mb4"]
NACOS = "http://129.204.152.168:8848"
SEED_SOFTWARE = 5000
SEED_COMMENTS = 20000
COMMENT_HOSTS = 50
CONCURRENCY = 50
DURATION_S = 25
TAG = "LOADTEST"

results = {}
_baseline = 0          # 灌数前 software MAX(id)，本轮种子全部 > 该值
_orig_config = None


def run_mysql(sql: str) -> None:
    # SQL 走 stdin（-e 参数在 Windows 有 32K 上限，5000 行 VALUES 会超）
    p = subprocess.run(MYSQL + ["-N"], input=sql, capture_output=True, text=True,
                       timeout=180)
    if p.returncode != 0:
        raise RuntimeError(f"mysql failed: {p.stderr.strip()[:300]}")


def sql_value(sql: str) -> str:
    p = subprocess.run(MYSQL + ["-N", "-e", sql], capture_output=True, text=True,
                       timeout=60)
    return p.stdout.strip()


def cleanup() -> None:
    """按指纹清理全部种子数据（幂等；绝不触碰真实数据）。"""
    try:
        run_mysql(f"""
            USE mall_software;
            DELETE c FROM software_comment c WHERE c.content LIKE '{TAG}%';
            DELETE src FROM software_source src
              JOIN software_release rel ON src.release_id = rel.id
              JOIN software_revision r ON rel.revision_id = r.id
              WHERE r.name LIKE '{TAG}%';
            DELETE rel FROM software_release rel
              JOIN software_revision r ON rel.revision_id = r.id
              WHERE r.name LIKE '{TAG}%';
            DELETE s FROM software s
              JOIN software_revision r ON r.id = s.published_revision_id
              WHERE r.name LIKE '{TAG}%';
            DELETE FROM software_revision WHERE name LIKE '{TAG}%';
        """)
        print("已清理种子数据", file=sys.stderr)
    except Exception as exc:
        print(f"清理失败（需手工检查 LOADTEST% 残留）: {exc}", file=sys.stderr)


def nacos_api(path: str, data=None):
    req = urllib.request.Request(NACOS + path,
                                 data=data.encode() if data else None,
                                 method="POST" if data else "GET")
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.load(resp)


def disable_rate_limits() -> str:
    tok = nacos_api("/nacos/v1/auth/login", "username=nacos&password=nacos")["accessToken"]
    orig = nacos_api(f"/nacos/v3/client/cs/config?dataId=mall-software.yml"
                     f"&groupName=DEFAULT_GROUP&accessToken={tok}")["data"]["content"]
    text = orig
    for key in ("resolve-per-user-minute", "comment-per-user-minute",
                "publish-submissions-per-user-day", "feedback-per-user-day",
                "report-per-user-day"):
        pat = re.compile(r"^(\s*%s\s*:\s*)(\S+)\s*$" % re.escape(key), re.M)
        if pat.search(text):
            text = pat.sub(lambda m: m.group(1) + "0", text, count=1)
        elif re.search(r"^software:\s*$", text, re.M):
            text = re.sub(r"^(software:\s*$)",
                          lambda m: m.group(1) + "\n  " + key + ": 0",
                          text, count=1, flags=re.M)
        else:
            text = text.rstrip() + "\nsoftware:\n  " + key + ": 0\n"
    req = urllib.request.Request(
        f"{NACOS}/nacos/v3/admin/cs/config?accessToken={tok}",
        data=f"dataId=mall-software.yml&groupName=DEFAULT_GROUP&type=yaml"
             f"&content={quote(text, safe='')}".encode(), method="POST")
    urllib.request.urlopen(req, timeout=10).read()
    time.sleep(4)
    return orig


def restore_rate_limits(orig: str) -> None:
    tok = nacos_api("/nacos/v1/auth/login", "username=nacos&password=nacos")["accessToken"]
    req = urllib.request.Request(
        f"{NACOS}/nacos/v3/admin/cs/config?accessToken={tok}",
        data=f"dataId=mall-software.yml&groupName=DEFAULT_GROUP&type=yaml"
             f"&content={quote(orig, safe='')}".encode(), method="POST")
    urllib.request.urlopen(req, timeout=10).read()
    time.sleep(3)
    print("已还原 Nacos 限流配置", file=sys.stderr)


def seed() -> None:
    """种子一律以 id > baseline 标记新行（AUTO_INCREMENT 计数器可能远高于
    MAX(id)——用 BETWEEN baseline+1..N 会在计数器错位时全部空转，实测踩坑）。
    真实数据 id 必然 ≤ baseline，故 `id > baseline` 精确圈定本轮种子。"""
    global _baseline
    _baseline = int(sql_value("SELECT COALESCE(MAX(id), 0) FROM mall_software.software"))
    owner = sql_value("SELECT id FROM mall_user.users WHERE deleted_at IS NULL "
                      "AND email LIKE '%test.dev%' LIMIT 1") or "1"
    print(f"灌数：{SEED_SOFTWARE} 软件 / {SEED_COMMENTS} 评论（基线 id={_baseline}）",
          file=sys.stderr)
    vals = ",".join(f"({owner}, 'ACTIVE', 0, 0, 1, 1)" for _ in range(SEED_SOFTWARE))
    run_mysql(
        "INSERT INTO mall_software.software (owner_id, lifecycle, "
        "published_revision_id, working_revision_id, lock_version, publication_seq) "
        f"VALUES {vals}")
    run_mysql(f"""
        INSERT INTO mall_software.software_revision
            (software_id, editor_id, revision_no, review_status, submit_seq,
             lock_version, name, summary, category_id, description_html,
             description_text, license_type, attribution_type, access_mode)
        SELECT s.id, s.owner_id, 1, 'APPROVED', 1, 1,
               CONCAT('{TAG} 工具 ', s.id), '压测种子简介（长度足够）', 1,
               '<p>压测</p>', '压测', 'FREE', 'ORIGINAL', 'REGISTERED'
        FROM mall_software.software s WHERE s.id > {_baseline};
        UPDATE mall_software.software s
        JOIN mall_software.software_revision r ON r.software_id = s.id
        SET s.published_revision_id = r.id, s.working_revision_id = NULL
        WHERE s.id > {_baseline} AND r.revision_no = 1;
        INSERT INTO mall_software.software_release
            (revision_id, release_key, version_name, channel, release_order, recommended, changelog)
        SELECT r.id, UUID(), '1.0.0', 'STABLE', 1, 1, '压测'
        FROM mall_software.software_revision r
        WHERE r.name LIKE '{TAG}%' AND r.software_id > {_baseline};
        INSERT INTO mall_software.software_source
            (release_id, source_key, source_type, provider, platform, arch, label,
             url, url_hash, content_fingerprint)
        SELECT rel.id, UUID(), 'NETDISK', 'BAIDU', 'WINDOWS', 'X64', '压测网盘',
               'https://pan.baidu.com/s/loadtest',
               SHA2(CONCAT('https://pan.baidu.com/s/loadtest', rel.id), 256),
               SHA2(CONCAT('https://pan.baidu.com/s/loadtest', rel.id), 256)
        FROM mall_software.software_release rel
        JOIN mall_software.software_revision r ON rel.revision_id = r.id
        WHERE r.name LIKE '{TAG}%' AND r.software_id > {_baseline};""")
    per = SEED_COMMENTS // COMMENT_HOSTS
    hosts = sql_value(f"SELECT GROUP_CONCAT(id) FROM (SELECT id FROM mall_software.software "
                      f"WHERE id > {_baseline} ORDER BY id LIMIT {COMMENT_HOSTS}) t")
    for sw in hosts.split(","):
        run_mysql(f"""
            INSERT INTO mall_software.software_comment
                (software_id, user_id, content, status, like_count, lock_version)
            SELECT {sw}, s.owner_id, CONCAT('{TAG} 评论 ', n.n), 'VISIBLE', 0, 0
            FROM mall_software.software s,
                 (SELECT a.N + b.N*10 + c.N*100 + 1 AS n
                  FROM (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
                        UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
                       (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
                        UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b,
                       (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
                        UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c) n
            WHERE s.id = {sw} AND n.n <= {per};
        """)
    total_sw = sql_value(f"SELECT COUNT(*) FROM mall_software.software_revision "
                         f"WHERE name LIKE '{TAG}%'")
    total_cm = sql_value("SELECT COUNT(*) FROM mall_software.software_comment "
                         f"WHERE content LIKE '{TAG}%'")
    assert int(total_sw) == SEED_SOFTWARE, f"种子 revision 数不符: {total_sw}"
    assert int(total_cm) == SEED_COMMENTS, f"种子评论数不符: {total_cm}"
    print(f"灌数完成：软件 {total_sw}，评论 {total_cm}", file=sys.stderr)


def load_test(name: str, fn) -> dict:
    latencies = []
    errors = []
    lock = threading.Lock()
    deadline = time.perf_counter() + DURATION_S

    def worker():
        local, local_err = [], []
        while time.perf_counter() < deadline:
            dt, ok, err = fn()
            local.append(dt)
            if not ok:
                local_err.append(err or "unknown")
        with lock:
            latencies.extend(local)
            errors.extend(local_err)

    threads = [threading.Thread(target=worker) for _ in range(CONCURRENCY)]
    t0 = time.perf_counter()
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    wall = time.perf_counter() - t0
    latencies.sort()
    n = len(latencies)

    def pct(p):
        return latencies[min(int(n * p), n - 1)] * 1000

    return {
        "name": name,
        "requests": n,
        "rps": round(n / wall, 1),
        "p50_ms": round(pct(0.50), 1),
        "p95_ms": round(pct(0.95), 1),
        "p99_ms": round(pct(0.99), 1),
        "max_ms": round(latencies[-1] * 1000, 1),
        "errors": len(errors),
        "error_rate": f"{len(errors) / n * 100:.2f}%" if n else "n/a",
        "error_samples": sorted(set(errors))[:3],
    }


# 线程内持久连接（每线程一条）：避免每请求新建连接导致 Windows 临时端口耗尽
# （WinError 10048），连接复用也让延迟测量只反映服务端处理 + 稳态网络
_local = threading.local()


def _conn():
    conn = getattr(_local, "conn", None)
    if conn is None:
        from urllib.parse import urlparse
        u = urlparse(GW)
        import http.client
        conn = http.client.HTTPConnection(u.hostname, u.port, timeout=20)
        _local.conn = conn
    return conn


def _request(method: str, path: str, headers=None, body=None):
    for attempt in (0, 1):  # 持久连接偶发被服务端关闭：重试一次
        t0 = time.perf_counter()
        try:
            conn = _conn()
            conn.request(method, path, body=body,
                         headers={"Content-Type": "application/json", **(headers or {})})
            resp = conn.getresponse()
            resp.read()
            ok = 200 <= resp.status < 300
            return time.perf_counter() - t0, ok, None if ok else f"HTTP {resp.status}"
        except Exception as exc:
            try:
                _local.conn.close()
            except Exception:
                pass
            _local.conn = None
            if attempt == 1:
                return time.perf_counter() - t0, 0, f"{type(exc).__name__}: {exc}"[:80]


def get_json(url: str, headers=None):
    path = url[len(GW):]
    return _request("GET", path, headers=headers)


def post_json(url: str, headers=None):
    path = url[len(GW):]
    return _request("POST", path, headers=headers, body="{}")


def login() -> str:
    req = urllib.request.Request(
        AUTH + "/login",
        data=json.dumps({"account": "e07link@test.dev",
                         "password": "E07pass!234"}).encode(),
        headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.load(resp)["data"]["accessToken"]


def main() -> None:
    global _orig_config
    atexit.register(cleanup)
    try:
        cleanup()
        seed()
        _orig_config = disable_rate_limits()
        auth = {"Authorization": f"Bearer {login()}"}

        for url in (f"{GW}/api/software?page=1&size=20", f"{GW}/api/software/1",
                    f"{GW}/api/software/1/comments?page=1&size=20",
                    f"{GW}/api/software/rankings?type=access"):
            get_json(url)

        print(f"开始压测：{CONCURRENCY} 并发 × {DURATION_S}s / 场景", file=sys.stderr)
        results = {
            "list": load_test(
                "列表 GET /api/software?size=20",
                lambda: get_json(f"{GW}/api/software?page=1&size=20")),
            "detail": load_test(
                "详情 GET /api/software/1",
                lambda: get_json(f"{GW}/api/software/1")),
            "comments": load_test(
                "评论分页 GET /api/software/1/comments?size=20",
                lambda: get_json(f"{GW}/api/software/1/comments?page=1&size=20")),
            "rankings": load_test(
                "榜单 GET /api/software/rankings?type=access",
                lambda: get_json(f"{GW}/api/software/rankings?type=access&page=1&size=20")),
        }
        first_sw = sql_value(f"SELECT MIN(id) FROM mall_software.software WHERE id > {_baseline}")
        src_id = sql_value(f"""
            SELECT src.id FROM mall_software.software_source src
              JOIN mall_software.software_release rel ON src.release_id = rel.id
              JOIN mall_software.software_revision r ON rel.revision_id = r.id
              WHERE r.name LIKE '{TAG}%' AND r.software_id = {first_sw}
              LIMIT 1""")
        if src_id and first_sw:
            results["resolve"] = load_test(
                "获取链接 POST resolve（认证）",
                lambda: post_json(f"{GW}/api/software/{first_sw}/sources/{src_id}/resolve",
                                  auth))

        with open("docs/software/load-result.json", "w", encoding="utf-8") as fh:
            json.dump(results, fh, ensure_ascii=False, indent=2)

        print("\n===== 压测结果（缩比：5000 软件 / 20000 评论 / "
              f"{CONCURRENCY} 并发 × {DURATION_S}s）=====")
        for r in results.values():
            print(f"{r['name']}\n"
                  f"  请求 {r['requests']} | RPS {r['rps']} | "
                  f"p50 {r['p50_ms']}ms | p95 {r['p95_ms']}ms | p99 {r['p99_ms']}ms | "
                  f"max {r['max_ms']}ms | 错误 {r['errors']} ({r['error_rate']}) "
                  f"{r['error_samples'] if r['errors'] else ''}")
    finally:
        if _orig_config is not None:
            restore_rate_limits(_orig_config)
        cleanup()


if __name__ == "__main__":
    main()
