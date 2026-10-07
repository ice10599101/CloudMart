#!/usr/bin/env python3
"""PET-09/10/11/12/13/21/24 部署验证（V68–V71 + mall-admin V18 重启后执行）。

只读或构造性失败（不产生任何业务状态变更）：
  用户域（pet-verify-test 测试账号）：
    1.  V68 任务集：GET /daily-quests 返回 setId；GET /daily-quest-sets 列表非空；
        GET /daily-quest-sets/{setId} 详情可达
    2.  PET-12：GET /purchase-requests/{uuid} → 200 UNKNOWN；GET /purchase-orders 200
    3.  PET-13：GET /pets/{petId}/album 200（V71 列存在即迁移成功佐证）；
        POST /pets/{petId}/album/{bogus}/retry-binding → 业务拒绝信封（路由存在）
    4.  PET-11：GET /events?status=CLAIMABLE 200
    5.  PET-06：GET /activity-center 含 accountBusyActivity.minigame
  管理域（用户 token，仅验路由存在性，期望 401/403 而非裸 404）：
    6.  GET /admin/pet/seasons/1/settlement-jobs
    7.  PUT /admin/pet/configs/studies/999999999/enabled
"""
import json
import os
import sys
import urllib.request
import urllib.error

GATEWAY = os.environ.get("GATEWAY", "http://129.204.152.168:8090")
ACCOUNT = os.environ.get("ACCOUNT", "pet-verify-test@cloudmart.dev")
PASSWORD = os.environ.get("PASSWORD", "PetVerify@2026!")

results = []


def call(method, path, body=None, token=None):
    req = urllib.request.Request(GATEWAY + path,
                                 data=json.dumps(body).encode() if body is not None else None,
                                 method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}
    except Exception as e:
        return 0, {"error": {"message": str(e)}}


def check(name, ok, detail=""):
    results.append((name, ok))
    print(("PASS  " if ok else "FAIL  ") + name + ("  | " + detail if detail else ""))


def main():
    s, b = call("POST", "/api/auth/login", {"account": ACCOUNT, "password": PASSWORD})
    token = (b.get("data") or {}).get("accessToken")
    check("登录（pet-verify 测试账号）", s == 200 and bool(token), f"status={s}")
    if not token:
        sys.exit(1)

    # ---- PET-09/V68：任务集实体化 ----
    s, b = call("GET", "/api/pet/daily-quests", token=token)
    panel = b.get("data") or {}
    set_id = panel.get("setId")
    check("GET /daily-quests 200 且含 setId（V68 集生成）",
          s == 200 and bool(set_id), f"status={s} setId={set_id}")

    s, b = call("GET", "/api/pet/daily-quest-sets", token=token)
    sets = b.get("data") or []
    check("GET /daily-quest-sets 200 列表非空（集实体路由）",
          s == 200 and len(sets) > 0 and any(str(x.get("id")) == str(set_id) for x in sets),
          f"status={s} count={len(sets)}")

    if set_id:
        s, b = call("GET", f"/api/pet/daily-quest-sets/{set_id}", token=token)
        check("GET /daily-quest-sets/{setId} 详情可达", s == 200 and (b.get("data") or {}).get("setId") is not None,
              f"status={s}")
    else:
        check("GET /daily-quest-sets/{setId} 详情（跳过：无 setId）", False, "前置失败")

    # ---- PET-12：购买请求状态查询 ----
    probe_key = "deploy-verify-0001"
    s, b = call("GET", f"/api/pet/purchase-requests/{probe_key}", token=token)
    data = b.get("data") or {}
    check("GET /purchase-requests/{key} → 200 UNKNOWN（恢复入口）",
          s == 200 and data.get("status") == "UNKNOWN" and data.get("retryable") is True,
          f"status={s} resp={data.get('status')}")

    s, b = call("GET", "/api/pet/purchase-orders", token=token)
    check("GET /purchase-orders 200", s == 200, f"status={s}")

    # ---- PET-13/V71：相册列表 + 重试绑定路由 ----
    s, b = call("GET", "/api/pet/me", token=token)
    pet_id = ((b.get("data") or {}).get("pets") or [{}])[0].get("petId") if isinstance(b.get("data"), dict) else None
    if not pet_id and isinstance(b.get("data"), dict):
        pet_id = (b.get("data") or {}).get("petId")
    check("GET /me 取到 petId", bool(pet_id), f"petId={pet_id}")

    if pet_id:
        s, b = call("GET", f"/api/pet/pets/{pet_id}/album", token=token)
        check("GET /pets/{petId}/album 200（V71 列存在 = 迁移成功）", s == 200, f"status={s}")

        s, b = call("POST", f"/api/pet/pets/{pet_id}/album/999999999999/retry-binding", token=token)
        envelope_ok = s in (200, 400, 403, 404) and bool(b.get("error") or b.get("success") is not None)
        check("POST /album/{bogus}/retry-binding → 业务拒绝信封（路由+归属校验存在）",
              envelope_ok and s != 0, f"status={s} code={(b.get('error') or {}).get('code')}")

        # PET-19：GET 预览无副作用、POST visits 路由存在（bogus petId 构造失败，无副作用）
        # 自己家预览 → PET_VISIT_SELF 409（恰好证明 previewHome 路由与守卫在工作）
        s, b = call("GET", f"/api/pet/home/{pet_id}", token=token)
        check("GET /home/{own} → 409 PET_VISIT_SELF（预览路由+守卫存在）",
              s == 409 and (b.get("error") or {}).get("code") == "PET_VISIT_SELF",
              f"status={s} code={(b.get('error') or {}).get('code')}")
        # 他人不存在家 → PET_NOT_FOUND 信封（路由可达）
        s, b = call("GET", "/api/pet/home/999999999999", token=token)
        check("GET /home/{bogus} → 业务拒绝信封", s in (400, 403, 404, 409) and bool(b.get("error")),
              f"status={s} code={(b.get('error') or {}).get('code')}")
        s, b = call("POST", f"/api/pet/home/999999999999/visits", token=token)
        check("POST /home/{bogus}/visits → 业务拒绝信封（命令路由存在）",
              s in (200, 400, 403, 404) and bool(b.get("error") or b.get("success") is not None), f"status={s}")
    else:
        check("相册/家园验证（跳过：无 petId）", False, "前置失败")

    # ---- PET-11：事件状态过滤 ----
    s, b = call("GET", "/api/pet/events?status=CLAIMABLE", token=token)
    check("GET /events?status=CLAIMABLE 200", s == 200, f"status={s}")

    # ---- PET-06：活动中心 minigame 标志 ----
    s, b = call("GET", "/api/pet/activity-center", token=token)
    busy = ((b.get("data") or {}).get("accountBusyActivity") or {})
    check("GET /activity-center 200 含 minigame 标志（PET-06 口径）",
          s == 200 and "minigame" in busy, f"status={s} busyKeys={sorted(busy.keys())}")
    rec = (b.get("data") or {}).get("recoveries")
    check("GET /activity-center 含 recoveries（PET-28 恢复中心）",
          rec is not None and all(k in rec for k in ("bindingFailedAlbums", "unsettledMinigameRounds", "processingPurchases")),
          f"recoveries={rec}")

    # ---- PET-21/24：管理域路由存在性（用户 token 期望 401/403，而非裸 404） ----
    s, b = call("GET", "/api/admin/pet/seasons/999999/settlement-jobs", token=token)
    check("GET /admin/pet/seasons/{id}/settlement-jobs 路由存在（401/403）",
          s in (401, 403), f"status={s}")
    s, b = call("PUT", "/api/admin/pet/configs/studies/999999999/enabled?enabled=false", token=token)
    check("PUT /admin/pet/configs/studies/{id}/enabled 路由存在（401/403）",
          s in (401, 403), f"status={s}")
    s, b = call("POST", "/api/admin/pet/seasons/999999/settlement-jobs/999999999/retry", token=token)
    check("POST /admin/pet/seasons/{id}/settlement-jobs/{jobId}/retry 路由存在（401/403）",
          s in (401, 403), f"status={s}")

    failed = [name for name, ok in results if not ok]
    print(f"\n== 结果: {len(results) - len(failed)}/{len(results)} 通过 ==")
    if failed:
        print("失败项：")
        for name in failed:
            print("  -", name)
        sys.exit(1)


if __name__ == "__main__":
    main()
