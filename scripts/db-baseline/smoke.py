"""Exercise real local APIs after a fresh baseline; fixtures never enter baseline SQL."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import secrets
import subprocess
import urllib.error
import urllib.parse
import urllib.request
from run import load_local_env

ROOT = Path(__file__).resolve().parents[2]
BASE = "http://127.0.0.1:8090/api"
CONTAINER = "cloudmart-mysql"


def request(method, path, data=None, token=None, key=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if key:
        headers["X-Idempotency-Key"] = key
    payload = json.dumps(data).encode() if data is not None else None
    req = urllib.request.Request(BASE + path, payload, headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=45) as response:
            body = json.load(response)
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"{method} {path}: HTTP {error.code}: {error.read().decode()}") from None
    if body.get("success") is not True:
        raise RuntimeError(f"{method} {path}: {body}")
    return body["data"]


def mysql(sql):
    result = subprocess.run(["docker", "exec", "-i", "-e", "MYSQL_PWD=" + os.getenv("MYSQL_PASSWORD", "123456"),
                             CONTAINER, "mysql", "-h127.0.0.1", "-uroot", "--default-character-set=utf8mb4", "-N", "-B"],
                            input=sql.encode(), stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
    return result.stdout.decode().strip()


def main():
    global CONTAINER
    load_local_env()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--confirm-local-development", action="store_true", required=True)
    parser.add_argument("--refresh-search-only", action="store_true", help="Rebuild the product search alias from current DB without creating fixtures")
    parser.add_argument("--mysql-container", choices=["cloudmart-mysql", "cloudmart-c26-mysql"], default="cloudmart-mysql")
    args = parser.parse_args()
    CONTAINER = args.mysql_container
    run_id = "c26" + secrets.token_hex(5)
    admin_password = os.getenv("ADMIN_SMOKE_PASSWORD", "admin123")
    admin_name = os.getenv("ADMIN_SMOKE_ACCOUNT", "admin")
    admin = request("POST", "/auth/admin/login", {"account": admin_name, "password": admin_password})["accessToken"]
    if args.refresh_search_only:
        print(json.dumps(request("POST", "/admin/business/products/es/index/full-rebuild", token=admin), ensure_ascii=False, indent=2))
        return
    menus = request("GET", "/admin/menus/tree", token=admin)
    assert menus, "Empty permission menu tree"
    # A local test account is created only for this explicit smoke run, never in V1.
    username = "smoke-" + run_id
    mysql("INSERT INTO mall_user.users (username,password,nickname,status) "
          f"SELECT '{username}',password,'C26验收',1 FROM mall_admin.admin_user WHERE username='{admin_name.replace(chr(39), chr(39)*2)}' LIMIT 1;")
    user_id = int(mysql(f"SELECT id FROM mall_user.users WHERE username='{username}';"))
    user = request("POST", "/auth/login", {"account": username, "password": admin_password})["accessToken"]
    category = request("POST", "/admin/business/categories?" + urllib.parse.urlencode({"name": run_id, "parentId": 0}), token=admin)
    product = request("POST", "/admin/business/products", {
        "name": "C26验收商品", "categoryId": category["id"], "mainImage": "", "skus": [
            {"skuCode": run_id, "price": 10.00, "stock": 10, "attributes": "{}"}
        ]}, token=admin)
    sku = product["skus"][0]["id"]
    listed = request("GET", f"/product/products/{product['id']}", token=user)
    assert str(listed["id"]) == str(product["id"])
    quote = request("POST", "/order/quotes", {"items": [{"skuId": sku, "quantity": 1}]}, token=user)
    body = {"quoteId": quote["quoteId"], "expectedQuoteVersion": quote["version"],
            "receiverName": "C26验收", "receiverPhone": "13800000000", "receiverAddress": "开发环境验收地址"}
    order = request("POST", "/order/orders", body, user, run_id)
    replay = request("POST", "/order/orders", body, user, run_id)
    assert str(order["id"]) == str(replay["id"]), "Order idempotency failed"
    pet = request("POST", "/pet/create", {"name": "基线验收", "species": "ORANGE", "personality": "LIVELY", "gender": "MALE"}, token=user)
    quests = request("GET", "/pet/daily-quests", token=user)
    repeated = request("GET", "/pet/daily-quests", token=user)
    assert quests["setId"] and str(quests["setId"]) == str(repeated["setId"]), "Quest set generation not stable"
    sets = mysql(f"SELECT COUNT(*) FROM mall_pet.pet_daily_quest_set WHERE user_id={user_id};")
    assert sets == "1", "Duplicate daily quest set"
    report = {"run": run_id, "databaseContainer": CONTAINER, "time": dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).isoformat(),
              "permissionMenu": "PASS", "productCreationAndRead": "PASS", "quoteAndOrder": "PASS",
              "orderIdempotency": "PASS", "petAdoption": "PASS", "dailyQuestGenerationAndReplay": "PASS",
              "fixtureUserId": str(user_id), "fixtureProductId": str(product["id"]), "fixtureOrderId": str(order["id"]),
              "fixturePetId": str(pet["petId"]), "fixtureQuestSetId": str(quests["setId"])}
    output = ROOT / ".tmp-c26/smoke-report.json"
    output.parent.mkdir(exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    print("Smoke fixtures remain in this local development database; recreate after verification for a clean final environment.")


if __name__ == "__main__":
    main()
