#!/usr/bin/env bash
# E07 phase3：管理端真实联调（A31-A40 资格/授权/联系入口/幂等契约）
# 幂等键契约：统一走 HTTP 头 Idempotency-Key（手册 6.14/7.5，与前端及其它服务一致），
# mall-admin 通过 SoftwareFeignConfiguration 透传给 mall-software。
# 管理端分页参数名是 size（不是 pageSize），这是既有约定。
set -uo pipefail

BASE="http://129.204.152.168:8090"
DB="mysql -h 129.204.152.168 -P 8306 -uroot -p123456 --connect-timeout=5"

PASS=0
FAIL=0

check() {
  local name="$1" expect="$2" actual="$3"
  if [ "$expect" = "$actual" ]; then
    echo "  PASS  $name"
    PASS=$((PASS + 1))
  else
    echo "  FAIL  $name (expect=$expect actual=$actual)"
    FAIL=$((FAIL + 1))
  fi
}

# jq 助手：表达式写在 $1 里，输入为 stdin
jq_get() { python -c "import sys,json;d=json.load(sys.stdin);$1" 2>/dev/null; }

echo "=== 0. 管理员登录 ==="
ADMIN_TOKEN=$(curl -s -m 15 -X POST "$BASE/api/auth/admin/login" \
  -H "Content-Type: application/json" \
  -d '{"account":"admin","password":"admin123"}' \
  | jq_get "print(d['data']['accessToken'] if d.get('success') else '')")
if [ -z "${ADMIN_TOKEN:-}" ]; then
  echo "  FAIL  管理员登录失败，终止"; exit 1
fi
echo "  PASS  admin 登录成功 (token ${#ADMIN_TOKEN} 字符)"

AH="Authorization: Bearer $ADMIN_TOKEN"
SW=1

# post <路径> <JSON> <Idempotency-Key>
post() {
  curl -s -m 20 -X POST "$BASE/api/admin/software$1" \
    -H "$AH" -H "Content-Type: application/json" \
    -H "Idempotency-Key: $3" \
    -d "$2"
}
getp() { curl -s -m 20 "$BASE/api/admin/software$1" -H "$AH"; }
ok() { echo "$1" | jq_get "print(str(d.get('success')).lower())"; }

echo "=== A31 发布资格：关闭 userId=1 ==="
K1="perm-off-$(date +%s)"
R=$(post "/publisher-permissions/1/toggle" '{"enable":false,"reason":"E07 phase3 关闭资格测试"}' "$K1")
check "返回 success" "true" "$(ok "$R")"
check "data 为 true（变更生效）" "true" "$(echo "$R" | jq_get "print(str(d.get('data')).lower())")"

echo "=== A32 同键同体重放 → 幂等吸收 ==="
R2=$(post "/publisher-permissions/1/toggle" '{"enable":false,"reason":"E07 phase3 关闭资格测试"}' "$K1")
check "同键同体重放被幂等吸收" "true" "$(ok "$R2")"

echo "=== A33 重新开通 userId=1 ==="
K2="perm-on-$(date +%s)"
R3=$(post "/publisher-permissions/1/toggle" '{"enable":true,"reason":"E07 phase3 恢复资格"}' "$K2")
check "恢复资格 data=true" "true" "$(echo "$R3" | jq_get "print(str(d.get('data')).lower())")"

echo "=== A34 付款授权：给 userId=1 开通 softwareId=1 ==="
K3="grant-$(date +%s)"
G=$(post "/$SW/access-grants" \
  '{"userId":1,"expiresAt":"2027-12-31T23:59:59","confirmationNote":"线下确认","externalReference":"REF-E07-001","reason":"E07 授权开通"}' "$K3")
check "授权返回 success" "true" "$(ok "$G")"
GRANTS=$(getp "/$SW/access-grants")
check "重查授权列表含 userId=1 且 ACTIVE" "ACTIVE" "$(echo "$GRANTS" | jq_get "
try:
    print(next(x.get('status','?') for x in d['data'] if str(x.get('userId'))=='1'))
except StopIteration:
    print('?')
")"

echo "=== A35 同一授权行重开（延期 + 更新同 Row） ==="
K4="grant2-$(date +%s)"
G2=$(post "/$SW/access-grants" \
  '{"userId":1,"expiresAt":null,"confirmationNote":"改为长期","externalReference":"REF-E07-002","reason":"E07 授权延期"}' "$K4")
check "重开返回 success" "true" "$(ok "$G2")"
check "同 Row 未新增重复(=1)" "1" "$($DB -N -e "SELECT COUNT(*) FROM mall_software.software_access_grant WHERE software_id=$SW AND user_id=1" 2>/dev/null | tr -d ' ')"

echo "=== A36 撤销授权 + 幂等 ==="
K5="revoke-$(date +%s)"
RV=$(post "/$SW/access-grants/1/revoke" '{"reason":"E07 撤销测试"}' "$K5")
check "撤销 success" "true" "$(ok "$RV")"
RV2=$(post "/$SW/access-grants/1/revoke" '{"reason":"E07 撤销测试"}' "$K5")
check "同键同体重复撤销仍 success" "true" "$(ok "$RV2")"

echo "=== A37 联系入口：新增 + HTTPS 校验 ==="
K6="contact-$(date +%s)"
CT=$(post "/access-contacts" \
  '{"name":"E07客服","contactType":"HTTPS_URL","contactValue":"https://example.com/support","enabled":true,"sortOrder":10,"reason":"E07 联系入口"}' "$K6")
check "新增 success" "true" "$(ok "$CT")"
CTBAD=$(post "/access-contacts" \
  '{"name":"危险协议","contactType":"HTTPS_URL","contactValue":"javascript:alert(1)","enabled":true,"reason":"应被拒绝"}' "contact-bad-$(date +%s)")
check "非 HTTPS 被拒" "false" "$(ok "$CTBAD")"

echo "=== A38 联系入口列表可见 ==="
CL=$(getp "/access-contacts")
check "列表含 E07客服" "true" "$(echo "$CL" | jq_get "print(str(any(x.get('name')=='E07客服' for x in d['data'])).lower())")"

echo "=== A39 缺少 Idempotency-Key → 400（契约强制） ==="
NOKEY=$(curl -s -m 20 -X POST "$BASE/api/admin/software/publisher-permissions/1/toggle" \
  -H "$AH" -H "Content-Type: application/json" \
  -d '{"enable":true,"reason":"无幂等键"}')
check "无键被拒" "false" "$(ok "$NOKEY")"
check "错误码 SOFTWARE_OPERATION_INVALID" "SOFTWARE_OPERATION_INVALID" "$(echo "$NOKEY" | jq_get "print(d['error']['code'])")"

echo "=== 汇总 ==="
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
