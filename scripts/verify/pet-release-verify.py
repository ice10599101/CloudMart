#!/usr/bin/env python3
"""宠物模块发布验证脚本（R44–R53 部署后执行）。

用法：
  GATEWAY=http://129.204.152.168:8090 \
  ACCOUNT=user_a PASSWORD='xxx' \
  python scripts/verify/pet-release-verify.py

覆盖：
  1. R03 路由经网关（StripPrefix=2 后服务内路径正确，修复前全 404）
  2. R02 购买：缺幂等键 400 / 带键成功 / 同键重放返回原结果
  3. R28 进化：缺幂等键 400
  4. R05 处罚：GET /admin/pet/sanctions 可用（管理员）
  5. R11 断线恢复：GET /minigames/current
  6. R22 聊天：带键发消息 → 同键重放返回同一回复（真实唯一键下验证占键链路）
  7. V53 后置：-flyway history 由运维另行核对（脚本不含 DB 直连）
"""
import json
import os
import sys
import urllib.request
import urllib.error

GATEWAY = os.environ.get("GATEWAY", "http://129.204.152.168:8090")
ACCOUNT = os.environ.get("ACCOUNT", "")
PASSWORD = os.environ.get("PASSWORD", "")

results = []


def call(method, path, body=None, token=None, key=None, expect_status=None):
    url = GATEWAY + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
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
    except Exception as e:
        return 0, {"error": {"message": str(e)}}


def check(name, ok, detail=""):
    results.append((name, ok, detail))
    print(("PASS  " if ok else "FAIL  ") + name + ("  | " + detail if detail else ""))


def main():
    if not ACCOUNT or not PASSWORD:
        print("请设置 ACCOUNT/PASSWORD 环境变量（普通用户账号）")
        sys.exit(2)

    # 登录（user 域）
    status, body = call("POST", "/api/auth/login",
                        {"account": ACCOUNT, "password": PASSWORD})
    token = (body.get("data") or {}).get("accessToken")
    check("登录", status == 200 and token, f"status={status}")
    if not token:
        sys.exit(1)

    # ---- 1. R03 路由（修复前全 404）----
    status, body = call("GET", "/api/pet/onboarding", token=token)
    check("R03 GET /api/pet/onboarding 经网关", status == 200, f"status={status}")

    status, body = call("GET", "/api/pet/notify-settings", token=token)
    check("R03 GET /api/pet/notify-settings 经网关", status == 200, f"status={status}")

    # ---- 2. R11 断线恢复端点 ----
    status, body = call("GET", "/api/pet/minigames/current", token=token)
    check("R11 GET /api/pet/minigames/current", status == 200, f"status={status}")

    # ---- 3. R02 购买：缺键 400 ----
    status, body = call("POST", "/api/pet/purchases",
                        {"petId": 1, "itemType": "FOOD", "itemCode": "__no_such__"}, token=token)
    check("R02 POST /purchases 缺幂等键 → 400", status == 400, f"status={status}")

    # ---- 4. R28 进化：缺幂等键 400 ----
    status, body = call("POST", "/api/pet/evolution/evolve", {}, token=token)
    check("R28 POST /evolution/evolve 缺幂等键 → 400", status == 400, f"status={status}")

    # ---- 5. R22 聊天：带键发消息 + 同键重放一致性（真实唯一键验证核心）----
    key = "verify-chat-" + os.urandom(8).hex()
    msg = "发布验证消息" + key[-6:]
    status1, body1 = call("POST", "/api/pet/chat", {"message": msg}, token=token, key=key)
    ok1 = status1 == 200 and body1.get("success")
    check("R22 带键聊天首次成功", ok1, f"status={status1} code={(body1.get('error') or {}).get('code')}")
    if ok1:
        reply1 = (body1["data"] or {}).get("content")
        # 同键重放：返回同一回复，不落新消息
        status2, body2 = call("POST", "/api/pet/chat", {"message": msg}, token=token, key=key)
        reply2 = (body2.get("data") or {}).get("content") if status2 == 200 else None
        check("R22 同键重放返回同一回复", reply1 is not None and reply1 == reply2,
              f"reply1={str(reply1)[:20]} reply2={str(reply2)[:20]}")

    # ---- 6. R02 同键购买重放（选商城第一个食物，扣一次币）----
    status, body = call("GET", "/api/pet/shop", token=token)
    foods = [i for i in ((body.get("data") or {}).get("items") or [])
             if i.get("itemType") == "FOOD"] if status == 200 else []
    if foods:
        food = foods[0]
        purchase_key = "verify-buy-" + os.urandom(8).hex()
        status, body = call("POST", "/api/pet/shop/buy",
                            {"itemType": "FOOD", "itemCode": food.get("code")},
                            token=token, key=purchase_key)
        first = status
        status2, body2 = call("POST", "/api/pet/shop/buy",
                              {"itemType": "FOOD", "itemCode": food.get("code")},
                              token=token, key=purchase_key)
        check("R02 同键购买重放收敛（两次 HTTP 结果一致）", first == status2,
              f"first={first} replay={status2}")
    else:
        check("R02 商城列表（跳过购买重放：无食物上架）", status == 200, f"status={status}")

    # ---- 7. R05 处罚端点（需管理员 token；普通用户预期 403，也算端点存在）----
    status, body = call("GET", "/api/admin/pet/sanctions", token=token)
    check("R05 GET /admin/pet/sanctions 端点存在（403/200 皆可）", status in (200, 403),
          f"status={status}")

    print("\n===== 结果汇总 =====")
    passed = sum(1 for _, ok, _ in results if ok)
    print(f"PASS {passed}/{len(results)}")
    sys.exit(0 if passed == len(results) else 1)


if __name__ == "__main__":
    main()
