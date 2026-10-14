#!/usr/bin/env python3
"""宠物模块统一验收脚本（C27：合并 pet-contract/pet-release/pet24-deploy 三套脚本）。

在 C26 基线或其后增量部署/重启后执行。三套检查按套件分组，默认全跑：

  python scripts/verify/pet-verify.py                 # = --suite all
  python scripts/verify/pet-verify.py --suite deploy  # PET-09~24 部署后只读/构造性验证
  python scripts/verify/pet-verify.py --suite contract
  python scripts/verify/pet-verify.py --suite release

环境变量：
  GATEWAY   默认 http://129.204.152.168:8090
  ACCOUNT   默认 pet-verify-test@cloudmart.dev（release 套件要求显式提供真实账号）
  PASSWORD  默认 PetVerify@2026!
  ADMIN_ACCOUNT / ADMIN_PASSWORD 默认 admin / admin123

退出码：0 全部通过；1 有失败；2 参数/前置错误。
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request

GATEWAY = os.environ.get("GATEWAY", "http://129.204.152.168:8090")
ACCOUNT = os.environ.get("ACCOUNT", "pet-verify-test@cloudmart.dev")
PASSWORD = os.environ.get("PASSWORD", "PetVerify@2026!")
ADMIN_ACCOUNT = os.environ.get("ADMIN_ACCOUNT", "admin")
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "admin123")

results = []


def call(method, path, body=None, token=None, key=None):
    req = urllib.request.Request(
        GATEWAY + path,
        data=json.dumps(body).encode() if body is not None else None,
        method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    if key:
        req.add_header("Idempotency-Key", key)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}
    except Exception as e:  # 网络层不可达
        return 0, {"error": {"message": str(e)}}


def check(name, ok, detail=""):
    results.append((name, ok))
    print(("PASS  " if ok else "FAIL  ") + name + ("  | " + detail if detail else ""))


def login(path, account, password):
    s, b = call("POST", path, {"account": account, "password": password})
    return (b.get("data") or {}).get("accessToken"), s


def err_code(b):
    return (b.get("error") or {}).get("code")


# =====================================================================
# 套件一：deploy —— PET-09/10/11/12/13/21/24 部署验证（只读或构造性失败）
# =====================================================================
def suite_deploy(token):
    print("\n########## 套件：deploy（PET-09~24 部署验证） ##########")

    # PET-09 任务集实体化
    s, b = call("GET", "/api/pet/daily-quests", token=token)
    panel = b.get("data") or {}
    set_id = panel.get("setId")
    check("GET /daily-quests 200 且含 setId（任务集生成）",
          s == 200 and bool(set_id), f"status={s} setId={set_id}")

    s, b = call("GET", "/api/pet/daily-quest-sets", token=token)
    sets = b.get("data") or []
    check("GET /daily-quest-sets 200 列表非空且含当日集",
          s == 200 and len(sets) > 0 and any(str(x.get("id")) == str(set_id) for x in sets),
          f"status={s} count={len(sets)}")

    if set_id:
        s, b = call("GET", f"/api/pet/daily-quest-sets/{set_id}", token=token)
        check("GET /daily-quest-sets/{setId} 详情可达",
              s == 200 and (b.get("data") or {}).get("setId") is not None, f"status={s}")
    else:
        check("GET /daily-quest-sets/{setId} 详情（跳过：无 setId）", False, "前置失败")

    # PET-12 购买恢复入口
    s, b = call("GET", "/api/pet/purchase-requests/deploy-verify-0001", token=token)
    data = b.get("data") or {}
    check("GET /purchase-requests/{key} → 200 UNKNOWN（恢复入口）",
          s == 200 and data.get("status") == "UNKNOWN" and data.get("retryable") is True,
          f"status={s} resp={data.get('status')}")
    s, b = call("GET", "/api/pet/purchase-orders", token=token)
    check("GET /purchase-orders 200", s == 200, f"status={s}")

    # PET-13 相册 + 家园路由
    s, b = call("GET", "/api/pet/me", token=token)
    me = b.get("data") or {}
    pet_id = ((me.get("pets") or [{}])[0]).get("petId") if isinstance(me, dict) else None
    if not pet_id and isinstance(me, dict):
        pet_id = me.get("petId")
    check("GET /me 取到 petId", bool(pet_id), f"petId={pet_id}")

    if pet_id:
        s, b = call("GET", f"/api/pet/pets/{pet_id}/album", token=token)
        check("GET /pets/{petId}/album 200（任务集/相册列存在）", s == 200, f"status={s}")
        s, b = call("POST", f"/api/pet/pets/{pet_id}/album/999999999999/retry-binding", token=token)
        check("POST /album/{bogus}/retry-binding → 业务拒绝信封（路由存在）",
              s in (200, 400, 403, 404) and (bool(b.get("error")) or b.get("success") is not None),
              f"status={s} code={err_code(b)}")
        s, b = call("GET", f"/api/pet/home/{pet_id}", token=token)
        check("GET /home/{own} → 409 PET_VISIT_SELF（预览路由+守卫存在）",
              s == 409 and err_code(b) == "PET_VISIT_SELF", f"status={s} code={err_code(b)}")
        s, b = call("GET", "/api/pet/home/999999999999", token=token)
        check("GET /home/{bogus} → 业务拒绝信封", s in (400, 403, 404, 409) and bool(b.get("error")),
              f"status={s} code={err_code(b)}")
        s, b = call("POST", "/api/pet/home/999999999999/visits", token=token)
        check("POST /home/{bogus}/visits → 业务拒绝信封（命令路由存在）",
              s in (200, 400, 403, 404) and (bool(b.get("error")) or b.get("success") is not None),
              f"status={s}")
    else:
        check("相册/家园验证（跳过：无 petId）", False, "前置失败")

    # PET-11 事件状态过滤
    s, b = call("GET", "/api/pet/events?status=CLAIMABLE", token=token)
    check("GET /events?status=CLAIMABLE 200", s == 200, f"status={s}")

    # PET-06 活动中心聚合
    s, b = call("GET", "/api/pet/activity-center", token=token)
    busy = ((b.get("data") or {}).get("accountBusyActivity") or {})
    check("GET /activity-center 200 含 minigame 标志", s == 200 and "minigame" in busy,
          f"status={s} busyKeys={sorted(busy.keys())}")
    rec = (b.get("data") or {}).get("recoveries")
    check("GET /activity-center 含 recoveries（PET-28 恢复中心）",
          rec is not None and all(k in rec for k in ("bindingFailedAlbums", "unsettledMinigameRounds", "processingPurchases")),
          f"recoveries={rec}")

    # PET-21/24 管理域路由存在性（用户 token 期望 401/403，而非裸 404）
    s, _ = call("GET", "/api/admin/pet/seasons/999999/settlement-jobs", token=token)
    check("GET /admin/pet/seasons/{id}/settlement-jobs 路由存在（401/403）", s in (401, 403), f"status={s}")
    s, _ = call("PUT", "/api/admin/pet/configs/studies/999999999/enabled?enabled=false", token=token)
    check("PUT /admin/pet/configs/studies/{id}/enabled 路由存在（401/403）", s in (401, 403), f"status={s}")
    s, _ = call("POST", "/api/admin/pet/seasons/999999/settlement-jobs/999999999/retry", token=token)
    check("POST /admin/pet/seasons/{id}/settlement-jobs/{jobId}/retry 路由存在（401/403）",
          s in (401, 403), f"status={s}")


# =====================================================================
# 套件二：contract —— 批次 A–C2 契约
# =====================================================================
def suite_contract(token):
    print("\n########## 套件：contract（A–C2 契约验证） ##########")

    s, b = call("GET", "/api/pet/bootstrap", token=token)
    data = b.get("data") or {}
    check("GET /bootstrap 200 且含聚合键", s == 200 and all(
        k in data for k in ("serverNow", "businessDate", "capabilities", "quotas", "actionAvailability")),
        f"status={s} keys={sorted(data.keys())[:8]}")

    s, b = call("GET", "/api/pet/activity-center", token=token)
    data = b.get("data") or {}
    check("GET /activity-center 200 且含聚合键", s == 200 and all(
        k in data for k in ("accountBusyActivity", "dailySetSummary", "eventSummary", "cooperationSummary")),
        f"status={s}")

    s, b = call("GET", "/api/pet/reports/mine", token=token)
    check("GET /reports/mine 200", s == 200 and b.get("success") is not None, f"status={s}")

    s, b = call("POST", "/api/pet/reports", {"targetType": "NICKNAME", "targetId": 999999999,
                                             "reason": "验证举报", "description": "x" * 1001}, token=token)
    check("POST /reports description>1000 → 400", s == 400, f"status={s} code={err_code(b)}")
    s, b = call("POST", "/api/pet/reports", {"targetType": "WALL_MESSAGE", "targetId": 999999999,
                                             "reason": "验证举报"}, token=token)
    check("POST /reports 目标不存在 → 400", s == 400, f"status={s} code={err_code(b)}")

    s, b = call("GET", "/api/pet/chat/requests/verify-" + os.urandom(6).hex(), token=token)
    d = b.get("data") or {}
    check("GET /chat/requests/{key} → UNKNOWN/canRetry", s == 200 and d.get("status") == "UNKNOWN"
          and d.get("canRetry") is True, f"status={s} data={d}")

    s, _ = call("POST", "/api/pet/activities/claim-batch", {"activityIds": []}, token=token)
    check("POST /activities/claim-batch 空列表 → 400", s == 400, f"status={s}")
    s, b = call("POST", "/api/pet/activities/claim-batch", {"activityIds": [999999999]}, token=token)
    d = (b.get("data") or [{}])[0] if isinstance(b.get("data"), list) else {}
    check("POST /activities/claim-batch 未知 ID → FAILED 且不中断",
          s == 200 and d.get("status") == "FAILED", f"status={s} item={d}")

    s, _ = call("GET", "/api/pet/events?status=CLAIMABLE", token=token)
    check("GET /events?status=CLAIMABLE 200", s == 200, f"status={s}")
    s, _ = call("GET", "/api/pet/events?status=BOGUS", token=token)
    check("GET /events?status=BOGUS → 400", s == 400, f"status={s}")
    s, b = call("POST", "/api/pet/event-occurrences/999999999/claim", token=token)
    check("POST /event-occurrences/{id}/claim 路由存在（404 期次不存在）", s == 404,
          f"status={s} code={err_code(b)}")

    # C14：setId 必须为数字实体 ID——日期串不再回退当日集
    s, b = call("GET", "/api/pet/daily-quests", token=token)
    set_id = ((b.get("data") or {}).get("setId")) if s == 200 else None
    check("GET /daily-quests 取 setId", s == 200 and bool(set_id), f"setId={set_id}")
    s, _ = call("POST", "/api/pet/daily-quest-sets/2000-01-01/claim-all", token=token)
    check("POST /daily-quest-sets/{日期串}/claim-all → 400（C14 删除日期回退）", s == 400, f"status={s}")
    if set_id:
        s, b = call("POST", f"/api/pet/daily-quest-sets/{set_id}/claim-all", token=token)
        check("POST /daily-quest-sets/{setId}/claim-all 200（results 逐项）",
              s == 200 and isinstance((b.get("data") or {}).get("results"), list),
              f"status={s} results={len((b.get('data') or {}).get('results') or [])}")

    # 管理域：举报清理 + 对账 diffs
    admin, s = login("/api/auth/admin/login", ADMIN_ACCOUNT, ADMIN_PASSWORD)
    check("管理员登录", s == 200 and bool(admin), f"status={s}")
    if admin:
        s, b = call("GET", "/api/admin/pet/reports", token=admin)
        reports = b.get("data") or []
        pending = [r for r in reports if isinstance(reports, list) and r.get("status") == "PENDING"]
        if pending:
            rid = pending[0].get("id")
            s, _ = call("POST", f"/api/admin/pet/reports/{rid}/resolve",
                        {"action": "DISMISSED", "reason": "验证清理"}, token=admin)
            check("admin 驳回 PENDING 举报（清理验证数据）", s == 200, f"id={rid} status={s}")
        else:
            check("admin 举报队列（无 PENDING，跳过驳回）", True)
        s, b = call("GET", "/api/admin/pet/wallet/reconciliations?page=1&size=5", token=admin)
        runs = b.get("data") if isinstance(b.get("data"), dict) else {}
        run_list = runs.get("records") if isinstance(runs, dict) else None
        if run_list:
            run_id = run_list[0].get("id")
            s, _ = call("GET", f"/api/admin/pet/wallet/reconciliations/{run_id}/diffs", token=admin)
            check("GET /reconciliations/{runId}/diffs 200", s == 200, f"runId={run_id} status={s}")
        else:
            check("GET /reconciliations/{runId}/diffs（无对账批次，跳过）", True)


# =====================================================================
# 套件三：release —— R44–R53 发布契约（需真实普通用户账号）
# =====================================================================
def suite_release(token):
    print("\n########## 套件：release（R44–R53 发布验证） ##########")

    s, _ = call("GET", "/api/pet/onboarding", token=token)
    check("R03 GET /api/pet/onboarding 经网关", s == 200, f"status={s}")
    s, _ = call("GET", "/api/pet/notify-settings", token=token)
    check("R03 GET /api/pet/notify-settings 经网关", s == 200, f"status={s}")
    s, _ = call("GET", "/api/pet/minigames/current", token=token)
    check("R11 GET /api/pet/minigames/current", s == 200, f"status={s}")

    # C13：统一购买入口——缺幂等键 400（合法体，专门验证键门禁而非 Bean 校验）
    s, b = call("POST", "/api/pet/purchases",
                {"itemType": "FOOD", "itemCode": "__no_such__"}, token=token)
    check("C13 POST /purchases 缺幂等键 → 400", s == 400, f"status={s} code={err_code(b)}")

    # C17：进化强制 expectedFromStage——空体 400
    s, _ = call("POST", "/api/pet/evolution/evolve", {}, token=token)
    check("C17 POST /evolution/evolve 缺 expectedFromStage → 400", s == 400, f"status={s}")

    # R22 带键聊天 + 同键重放一致性
    key = "verify-chat-" + os.urandom(8).hex()
    msg = "发布验证消息" + key[-6:]
    s1, body1 = call("POST", "/api/pet/chat", {"message": msg}, token=token, key=key)
    ok1 = s1 == 200 and body1.get("success")
    check("R22 带键聊天首次成功", ok1, f"status={s1} code={err_code(body1)}")
    if ok1:
        reply1 = (body1.get("data") or {}).get("content")
        s2, body2 = call("POST", "/api/pet/chat", {"message": msg}, token=token, key=key)
        reply2 = (body2.get("data") or {}).get("content") if s2 == 200 else None
        check("R22 同键重放返回同一回复", reply1 is not None and reply1 == reply2,
              f"reply1={str(reply1)[:20]} reply2={str(reply2)[:20]}")

    # C13 同键购买重放收敛（两次 HTTP 结果一致，业务成功/拒绝皆可）
    s, body = call("GET", "/api/pet/shop", token=token)
    foods = [i for i in ((body.get("data") or {}).get("items") or [])
             if i.get("itemType") == "FOOD"] if s == 200 else []
    if foods:
        food_code = foods[0].get("code")
        purchase_key = "verify-buy-" + os.urandom(8).hex()
        payload = {"itemType": "FOOD", "itemCode": food_code}
        first, _ = call("POST", "/api/pet/purchases", payload, token=token, key=purchase_key)
        second, _ = call("POST", "/api/pet/purchases", payload, token=token, key=purchase_key)
        check("C13 同键购买重放收敛（两次 HTTP 结果一致）", first == second,
              f"first={first} replay={second}")
    else:
        check("C13 商城列表（跳过购买重放：无食物上架）", s == 200, f"status={s}")

    # R05 处罚端点（普通用户预期 403，200/403 皆证明端点存在）
    s, _ = call("GET", "/api/admin/pet/sanctions", token=token)
    check("R05 GET /admin/pet/sanctions 端点存在（403/200 皆可）", s in (200, 403), f"status={s}")


SUITES = {"deploy": suite_deploy, "contract": suite_contract, "release": suite_release}


def main():
    parser = argparse.ArgumentParser(description="宠物模块统一验收（C27）")
    parser.add_argument("--suite", choices=["all", "deploy", "contract", "release"], default="all")
    args = parser.parse_args()

    token, status = login("/api/auth/login", ACCOUNT, PASSWORD)
    check("登录", status == 200 and bool(token), f"status={status}")
    if not token:
        print("登录失败，终止", file=sys.stderr)
        sys.exit(1)

    names = list(SUITES.keys()) if args.suite == "all" else [args.suite]
    for name in names:
        SUITES[name](token)

    failed = [name for name, ok in results if not ok]
    print(f"\n===== 结果汇总：{len(results) - len(failed)}/{len(results)} 通过 =====")
    if failed:
        print("失败项：")
        for name in failed:
            print("  -", name)
        sys.exit(1)


if __name__ == "__main__":
    main()
