#!/usr/bin/env bash
# E07 功能开关验收（方案 11.2/A30）：开关必须服务端生效，关闭后旧前端也无法绕过，
# 后台处置与作者受控草稿入口保留；验证后自动还原（也可热更新，无需重启）。
#
# 前置：新版 mall-software 已部署（含 resolve/总开关/P1 订阅与榜单开关）。
# 用法：./e07-switches.sh          # 会临时改写 Nacos mall-software.yml，退出时还原
# 环境：SETTLE=4 可调大等待热刷新时长（秒）。
set -uo pipefail

GW=http://129.204.152.168:8090
AUTH=http://129.204.152.168:9001
NACOS=http://129.204.152.168:8848
DATA_ID=mall-software.yml
GROUP=DEFAULT_GROUP
SETTLE=${SETTLE:-3}

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
jq_get() { python -c "import sys,json;d=json.load(sys.stdin);$1" 2>/dev/null; }

echo "=== 0. 登录（管理员/用户1/用户2）==="
ADMIN_TOKEN=$(curl -s -m 15 -X POST "$GW/api/auth/admin/login" -H "Content-Type: application/json" \
  -d '{"account":"admin","password":"admin123"}' | jq_get "print(d['data']['accessToken'] if d.get('success') else '')")
U1=$(curl -s -m 15 -X POST "$AUTH/login" -H "Content-Type: application/json" \
  -d '{"account":"e07link@test.dev","password":"E07pass!234"}' | jq_get "print(d['data']['accessToken'] if d.get('success') else '')")
U2=$(curl -s -m 15 -X POST "$AUTH/login" -H "Content-Type: application/json" \
  -d '{"account":"e07user2@test.dev","password":"E07pass!234"}' | jq_get "print(d['data']['accessToken'] if d.get('success') else '')")
AH="Authorization: Bearer $ADMIN_TOKEN"
U1H="Authorization: Bearer $U1"
U2H="Authorization: Bearer $U2"
[ -n "$ADMIN_TOKEN" ] && [ -n "$U1" ] && [ -n "$U2" ] || { echo "登录失败，终止"; exit 1; }
echo "  PASS  三个令牌获取成功"

NTOK=$(curl -s -m 8 -X POST "$NACOS/nacos/v1/auth/login" -d "username=nacos&password=nacos" \
  | jq_get "print(d.get('accessToken',''))")
[ -n "$NTOK" ] || { echo "Nacos 登录失败，终止"; exit 1; }

nacos_get() {
  curl -s -m 8 "$NACOS/nacos/v3/client/cs/config?dataId=$DATA_ID&groupName=$GROUP&accessToken=$NTOK" \
    | jq_get "print(d['data']['content'])"
}
nacos_set() {
  # content 经 python urlencode 后内联提交（Git Bash curl 的 content@- 读 stdin 不可靠）
  local enc
  enc=$(printf '%s' "$1" | python -c "import sys,urllib.parse;print(urllib.parse.quote(sys.stdin.read(), safe=''))")
  curl -s -m 8 -X POST "$NACOS/nacos/v3/admin/cs/config?accessToken=$NTOK" \
    -d "dataId=$DATA_ID&groupName=$GROUP&type=yaml&content=$enc" > /dev/null
}
ORIGINAL=$(nacos_get)
[ -n "$ORIGINAL" ] || { echo "读取原配置失败，终止"; exit 1; }
restore() { nacos_set "$ORIGINAL"; echo "（已还原 Nacos 配置并等待生效）"; sleep "$SETTLE"; }
trap restore EXIT INT TERM

apply() { # apply <key> <value>：整段重写后热刷新
  local key="$1" val="$2" updated
  updated=$(printf '%s' "$(nacos_get)" | python -c "
import sys, re
key, val = sys.argv[1], sys.argv[2]
text = sys.stdin.read()
pat = re.compile(r'^(\s*%s\s*:\s*)(\S+)\s*\$' % re.escape(key), re.M)
if pat.search(text):
    text = pat.sub(lambda m: m.group(1) + val, text, count=1)
elif re.search(r'^software:\s*\$', text, re.M):
    text = re.sub(r'^(software:\s*\$)', lambda m: m.group(1) + '\n  ' + key + ': ' + val,
                  text, count=1, flags=re.M)
else:
    text = text.rstrip() + '\nsoftware:\n  ' + key + ': ' + val + '\n'
print(text)
" "$key" "$val")
  nacos_set "$updated"
  sleep "$SETTLE"
}

# 探针：分别返回 HTTP 状态 + 业务码
probe() { # probe <method> <url> [token_header]
  local method="$1" url="$2" auth="${3:-}"
  local args=(-s -m 10 -o /tmp/e07_sw.json -w "%{http_code}" -X "$method" "$url")
  [ -n "$auth" ] && args+=(-H "$auth")
  local http; http=$(curl "${args[@]}")
  echo "$http $(cat /tmp/e07_sw.json | jq_get "print(d.get('error',{}).get('code',''))" )"
}
probe_ok() { # probe_ok <method> <url> [auth] → true/false（success 字段）
  local method="$1" url="$2" auth="${3:-}"
  local args=(-s -m 10 -X "$method" "$url")
  [ -n "$auth" ] && args+=(-H "$auth")
  curl "${args[@]}" | jq_get "print(str(d.get('success')).lower())"
}
get_json() { # get_json <url> [auth]
  local url="$1" auth="${2:-}"
  local args=(-s -m 10 "$url")
  [ -n "$auth" ] && args+=(-H "$auth")
  curl "${args[@]}"
}
SOURCE_ID=$(get_json "$GW/api/software/1/releases" | jq_get "print(d['data']['records'][0]['sources'][0]['sourceId'])")
[ -n "$SOURCE_ID" ] || { echo "取 sourceId 失败，终止"; exit 1; }

echo "=== 基线（开关全开状态）==="
check "公共列表可用" "true" "$(probe_ok GET "$GW/api/software?page=1&size=3")"
check "榜单可用" "true" "$(probe_ok GET "$GW/api/software/rankings?type=favorite&page=1&size=3")"

echo "=== A30：resolve-enabled=false ==="
apply resolve-enabled false
R=$(probe POST "$GW/api/software/1/sources/$SOURCE_ID/resolve" "$U1H")
check "resolve 服务端拒绝(403/SOFTWARE_RESOLVE_DISABLED)" "403 SOFTWARE_RESOLVE_DISABLED" "$R"
check "后台仍可处置（管理端评论列表）" "true" "$(probe_ok GET "$GW/api/admin/software/1/comments?page=1&size=5" "$AH")"
apply resolve-enabled true
check "resolve 恢复可用" "true" "$(probe_ok POST "$GW/api/software/1/sources/$SOURCE_ID/resolve" "$U1H")"

echo "=== P1 榜单开关 rankings-enabled ==="
apply rankings-enabled false
R=$(probe GET "$GW/api/software/rankings?type=access&page=1&size=3")
check "榜单关闭后拒绝(403/SOFTWARE_RANKINGS_DISABLED)" "403 SOFTWARE_RANKINGS_DISABLED" "$R"
apply rankings-enabled true
check "榜单恢复可用" "true" "$(probe_ok GET "$GW/api/software/rankings?type=access&page=1&size=3")"

echo "=== P1 订阅开关 subscription-enabled ==="
apply subscription-enabled false
R=$(probe PUT "$GW/api/software/1/subscription" "$U2H")
check "订阅关闭后拒绝(403/SOFTWARE_SUBSCRIPTION_DISABLED)" "403 SOFTWARE_SUBSCRIPTION_DISABLED" "$R"
apply subscription-enabled true
check "订阅恢复可用（幂等）" "true" "$(probe_ok PUT "$GW/api/software/1/subscription" "$U2H")"

echo "=== 总开关 enabled（公共读写关闭，受控入口保留）==="
apply enabled false
R=$(probe GET "$GW/api/software?page=1&size=3")
check "公共列表关闭后拒绝(403/SOFTWARE_DISABLED)" "403 SOFTWARE_DISABLED" "$R"
R=$(probe GET "$GW/api/software/1")
check "公开详情关闭后拒绝" "403 SOFTWARE_DISABLED" "$R"
check "作者编辑器仍可读取（受控入口）" "true" "$(probe_ok GET "$GW/api/software/1/editor" "$U1H")"
check "后台仍可处置（管理端目录列表）" "true" "$(probe_ok GET "$GW/api/admin/software/items?keyword=E07&page=1&size=1" "$AH")"
apply enabled true
check "公共列表恢复可用" "true" "$(probe_ok GET "$GW/api/software?page=1&size=3")"

echo "=== 汇总 ==="
echo "PASS=$PASS FAIL=$FAIL"
