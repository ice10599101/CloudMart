#!/usr/bin/env bash
# E08 验收补验（方案 14 节）：API 驱动补跑此前未验的 A 项。
# 覆盖：A04 磁力/种子、A07 旧批次、A11 跨软件攻击、A14 快照对比、A17 并发收藏/点赞、
#       A19 反馈重复与修复、A24 评分并发、A26 榜单下架实时过滤、A41 查看条件变更、
#       A42 隐藏信息、A43 联系入口停用、A48 scope 边界、A51 举报处置→BAN 闭环。
# 说明性跳过：A20/A25（MQ 容错语义由单测覆盖，live 中断演练需停 RocketMQ）、A29（真机）、
#             A22/A44（需专用受限管理员账号）、A45（需停依赖服务）。
# 副作用：开场软删上一轮「E08 验收工具」，创建新一轮同名软件并保留（演示数据）；
#         结束态 ACTIVE/REGISTERED；
#         运行窗口内经 Nacos 热更新临时关闭 submit/feedback/report 三个日级限流
#         （0=不限，避免反复执行被配额挡住；分钟级限流保持不变），退出自动还原。
set -uo pipefail

GW=http://129.204.152.168:8090
AUTH=http://129.204.152.168:9001
NACOS=http://129.204.152.168:8848
DATA_ID=mall-software.yml
GROUP=DEFAULT_GROUP
SWHOST=http://129.204.152.168:9026
DB="mysql -h 129.204.152.168 -P 8306 -uroot -p123456 --connect-timeout=5"
RUN=$(date +%s)

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
code_of() { jq_get "print(d.get('error',{}).get('code',''))"; }
ok_of() { jq_get "print(str(d.get('success')).lower())"; }

echo "=== 0. 登录 ==="
ADMIN_TOKEN=$(curl -s -m 15 -X POST "$GW/api/auth/admin/login" -H "Content-Type: application/json" \
  -d '{"account":"admin","password":"admin123"}' | jq_get "print(d['data']['accessToken'] if d.get('success') else '')")
U1=$(curl -s -m 15 -X POST "$AUTH/login" -H "Content-Type: application/json" \
  -d '{"account":"e07link@test.dev","password":"E07pass!234"}' | jq_get "print(d['data']['accessToken'] if d.get('success') else '')")
U2=$(curl -s -m 15 -X POST "$AUTH/login" -H "Content-Type: application/json" \
  -d '{"account":"e07user2@test.dev","password":"E07pass!234"}' | jq_get "print(d['data']['accessToken'] if d.get('success') else '')")
AH="Authorization: Bearer $ADMIN_TOKEN"; U1H="Authorization: Bearer $U1"; U2H="Authorization: Bearer $U2"
[ -n "$ADMIN_TOKEN" ] && [ -n "$U1" ] && [ -n "$U2" ] || { echo "登录失败"; exit 1; }
echo "  PASS  三令牌就绪"

# ---- Nacos：运行窗口内关闭三个日级限流（0=不限），退出还原（与 e07-switches 同模式）----
NTOK=$(curl -s -m 8 -X POST "$NACOS/nacos/v1/auth/login" -d "username=nacos&password=nacos" \
  | jq_get "print(d.get('accessToken',''))")
[ -n "$NTOK" ] || { echo "Nacos 登录失败"; exit 1; }
nacos_get() {
  curl -s -m 8 "$NACOS/nacos/v3/client/cs/config?dataId=$DATA_ID&groupName=$GROUP&accessToken=$NTOK" \
    | jq_get "print(d['data']['content'])"
}
nacos_set() {
  local enc
  enc=$(printf '%s' "$1" | python -c "import sys,urllib.parse;print(urllib.parse.quote(sys.stdin.read(), safe=''))")
  curl -s -m 8 -X POST "$NACOS/nacos/v3/admin/cs/config?accessToken=$NTOK" \
    -d "dataId=$DATA_ID&groupName=$GROUP&type=yaml&content=$enc" > /dev/null
}
ORIGINAL_CONFIG=$(nacos_get)
[ -n "$ORIGINAL_CONFIG" ] || { echo "读取原配置失败"; exit 1; }
restore_config() { nacos_set "$ORIGINAL_CONFIG"; echo "（已还原 Nacos 配置）"; sleep 3; }
trap restore_config EXIT INT TERM
LIMITS_OFF=$(printf '%s' "$ORIGINAL_CONFIG" | python -c "
import sys, re
text = sys.stdin.read()
for key in ('publish-submissions-per-user-day', 'feedback-per-user-day', 'report-per-user-day'):
    pat = re.compile(r'^(\s*%s\s*:\s*)(\S+)\s*\$' % re.escape(key), re.M)
    if pat.search(text):
        text = pat.sub(lambda m: m.group(1) + '0', text, count=1)
    elif re.search(r'^software:\s*\$', text, re.M):
        # chr(10) 而非 '\n'：Git Bash 对嵌入脚本的 \n 有转义层风险（实测被改写为 /n 产出损坏 YAML）
        text = re.sub(r'^(software:\s*\$)', lambda m: m.group(1) + chr(10) + '  ' + key + ': 0',
                      text, count=1, flags=re.M)
    else:
        text = text.rstrip() + chr(10) + 'software:' + chr(10) + '  ' + key + ': 0' + chr(10)
print(text)
")
nacos_set "$LIMITS_OFF"
sleep 3
echo "  PASS  日级限流已临时关闭（退出自动还原）"

post_admin() { curl -s -m 20 -X POST "$GW/api/admin/software$1" -H "$AH" -H "Content-Type: application/json" -H "Idempotency-Key: $3" -d "$2"; }
get_admin() { curl -s -m 20 "$GW/api/admin/software$1" -H "$AH"; }
slock() { get_admin "/items?keyword=E08+%E9%AA%8C%E6%94%B6&page=1&size=10" | jq_get "print(next((str(r['lockVersion']) for r in d['data']['records'] if str(r['softwareId'])=='$1'), ''))"; }
slifecycle() { get_admin "/items?keyword=E08+%E9%AA%8C%E6%94%B6&page=1&size=10" | jq_get "print(next((r['lifecycle'] for r in d['data']['records'] if str(r['softwareId'])=='$1'), ''))"; }
spublished() { get_admin "/items?keyword=E08+%E9%AA%8C%E6%94%B6&page=1&size=10" | jq_get "print(next((str(r['publishedRevisionId']) for r in d['data']['records'] if str(r['softwareId'])=='$1'), ''))"; }
bust_rank_cache() { python -c "import socket; keys=['software:rank:access:v1','software:rank:favorite:v1','software:rank:rating:v1']; blk=lambda x:'$'+str(len(x)); parts=['*'+str(1+len(keys)),blk('DEL'),'DEL']; [parts.extend([blk(k),k]) for k in keys]; payload=(chr(13)+chr(10)).join(parts)+(chr(13)+chr(10)); s=socket.create_connection(('129.204.152.168',8379),timeout=5); s.sendall(payload.encode()); s.recv(64); s.close()" > /dev/null 2>&1; }
refresh_src() { curl -s -m 10 "$GW/api/software/$SID/releases" | jq_get "print(next((str(s['sourceId']) for r in d['data']['records'] for s in (r.get('sources') or []) if s.get('sourceType')=='NETDISK'), 'STALE'))"; }

echo "=== 0.1 清理上一轮 E08 软件 ==="
for OLD in $(get_admin "/items?keyword=E08+%E9%AA%8C%E6%94%B6&page=1&size=10" | jq_get "print(' '.join(str(r['softwareId']) for r in d['data']['records']))"); do
  LC=$(slifecycle "$OLD")
  if [ "$LC" = "BANNED" ]; then
    OLV=$(slock "$OLD")
    post_admin "/items/$OLD/unban" "{\"reason\":\"E08 清理\",\"expectedVersion\":$OLV}" "e08-clean-unban-$OLD" > /dev/null
  fi
  OLV=$(slock "$OLD")
  DEL=$(curl -s -m 15 -X DELETE "$GW/api/software/$OLD?expectedVersion=$OLV" -H "$U1H")
  echo "  清理 SW=$OLD ($LC): $(echo "$DEL" | ok_of)"
done

echo "=== A04 磁力/种子外链 ==="
CREATE=$(curl -s -m 15 -X POST "$GW/api/software" -H "$U1H" -H "Idempotency-Key: e08-create-$RUN" -H "Content-Type: application/json" -d '{}')
SID=$(echo "$CREATE" | jq_get "print(d['data']['softwareId'])")
RID=$(echo "$CREATE" | jq_get "print(d['data']['workingRevisionId'])")
LV=$(echo "$CREATE" | jq_get "print(d['data']['lockVersion'])")
[ -n "$SID" ] || { echo "创建失败"; exit 1; }
echo "  PASS  创建 SW=$SID"

BAD=$(curl -s -m 15 -X PUT "$GW/api/software/$SID/revisions/$RID?expectedVersion=$LV" -H "$U1H" -H "Content-Type: application/json" -d '{
  "name":"E08 验收工具","summary":"A04-A51 验收用","categoryId":"1",
  "descriptionHtml":"<p>E08 验收。</p>","licenseType":"FREE","attributionType":"ORIGINAL","accessMode":"REGISTERED",
  "releases":[{"versionName":"1.0.0","channel":"STABLE","recommended":true,
    "sources":[{"sourceType":"MAGNET","platform":"WINDOWS","arch":"X64","label":"坏磁力",
      "url":"magnet:?xt=urn:btih:abc&dn=x"}]}]}')
check "A04 非法磁力被拒" "false" "$(echo "$BAD" | ok_of)"

SAVE=$(curl -s -m 15 -X PUT "$GW/api/software/$SID/revisions/$RID?expectedVersion=$LV" -H "$U1H" -H "Content-Type: application/json" -d '{
  "name":"E08 验收工具","summary":"A04-A51 验收用","categoryId":"1",
  "descriptionHtml":"<p>E08 验收。</p>","licenseType":"FREE","attributionType":"ORIGINAL","accessMode":"REGISTERED",
  "releases":[{"versionName":"1.0.0","channel":"STABLE","recommended":true,"changelog":"首个版本",
    "sources":[
      {"sourceType":"NETDISK","platform":"WINDOWS","arch":"X64","label":"E08 网盘",
       "url":"https://pan.baidu.com/s/1e08abc?pwd=e08x","extractionCode":"e08secret","sizeLabel":"9MB"},
      {"sourceType":"MAGNET","platform":"CROSS_PLATFORM","arch":"X64","label":"E08 磁力",
       "url":"magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=e08test"},
      {"sourceType":"TORRENT_URL","platform":"CROSS_PLATFORM","arch":"X64","label":"E08 种子",
       "url":"https://example.com/e08/file.torrent"}]}]}')
check "A04 三类来源保存成功" "true" "$(echo "$SAVE" | ok_of)"
LV2=$(echo "$SAVE" | jq_get "print(d['data']['lockVersion'])")

SUB=$(curl -s -m 15 -X POST "$GW/api/software/$SID/revisions/$RID/submit?expectedVersion=$LV2" -H "$U1H" -H "Idempotency-Key: e08-submit1-$RUN")
check "提交审核" "true" "$(echo "$SUB" | ok_of)"
SL=$(slock "$SID")
DEC=$(post_admin "/revisions/$RID/decision" "{\"softwareId\":$SID,\"submitSeq\":1,\"expectedVersion\":$SL,\"approve\":true,\"reason\":\"E08 验收\"}" "e08-dec1-$RUN")
check "审核通过（首发）" "APPROVED" "$(echo "$DEC" | jq_get "print(d['data']['decision'])")"

SRCIDS=$(curl -s -m 10 "$GW/api/software/$SID/releases" | jq_get "
rs=d['data']['records'][0]['sources']
m=next((str(s['sourceId']) for s in rs if s['sourceType']=='MAGNET'),'')
t=next((str(s['sourceId']) for s in rs if s['sourceType']=='TORRENT_URL'),'')
n=next((str(s['sourceId']) for s in rs if s['sourceType']=='NETDISK'),'')
print(f'{m} {t} {n}')")
MAGNET_ID=$(echo $SRCIDS | awk '{print $1}'); TORRENT_ID=$(echo $SRCIDS | awk '{print $2}'); NETDISK_ID=$(echo $SRCIDS | awk '{print $3}')
RM=$(curl -s -m 10 -X POST "$GW/api/software/$SID/sources/$MAGNET_ID/resolve" -H "$U2H")
check "A04 磁力 resolve 返回 magnet 链接" "true" "$(echo "$RM" | jq_get "print(str((d['data']['url'] or '').startswith('magnet:?')).lower())")"
RT=$(curl -s -m 10 -X POST "$GW/api/software/$SID/sources/$TORRENT_ID/resolve" -H "$U2H")
check "A04 种子 resolve 返回 https 链接" "true" "$(echo "$RT" | jq_get "print(str((d['data']['url'] or '').startswith('https://')).lower())")"

echo "=== A11 跨软件 ID 攻击 ==="
SW1SRC=$(curl -s -m 10 "$GW/api/software/1/releases" | jq_get "print(d['data']['records'][0]['sources'][0]['sourceId'])")
R11=$(curl -s -m 10 -o /dev/null -w "%{http_code}" -X POST "$GW/api/software/$SID/sources/$SW1SRC/resolve" -H "$U2H")
check "A11 跨软件 resolve 拒绝(404/409)" "true" "$(echo "$R11" | grep -qE '^(404|409)$' && echo true || echo false)"
C1=$(curl -s -m 10 "$GW/api/software/1/comments?page=1&size=1" | jq_get "print(d['data']['records'][0]['id'])")
R112=$(curl -s -m 10 -o /dev/null -w "%{http_code}" -X PUT "$GW/api/software/$SID/comments/$C1/like" -H "$U2H")
check "A11 跨软件点赞拒绝(404)" "404" "$R112"
R113=$(curl -s -m 10 -o /dev/null -w "%{http_code}" -X POST "$GW/api/software/$SID/comments" -H "$U2H" -H "Idempotency-Key: e08-xreply-$RUN" -H "Content-Type: application/json" -d "{\"content\":\"跨软件回复\",\"replyToCommentId\":$C1}")
check "A11 跨软件回复拒绝(400/404)" "true" "$(echo "$R113" | grep -qE '^(400|404)$' && echo true || echo false)"

echo "=== A42 隐藏信息（游客视角） ==="
RAW=$(curl -s -m 10 "$GW/api/software/$SID")
check "A42 详情无 magnet 链接" "0" "$(echo "$RAW" | grep -c 'magnet:' || true)"
check "A42 详情无提取码值" "0" "$(echo "$RAW" | grep -c 'e08secret' || true)"
check "A42 详情无 url 字段" "0" "$(echo "$RAW" | jq_get "print(int('url' in d['data']))")"
RAWREL=$(curl -s -m 10 "$GW/api/software/$SID/releases")
check "A42 releases 无真实链接" "0" "$(echo "$RAWREL" | grep -c 'pan.baidu\|magnet:' || true)"

echo "=== A07 旧批次防覆盖 + A14 快照对比 ==="
WR=$(curl -s -m 15 -X POST "$GW/api/software/$SID/working-revision" -H "$U1H" -H "Idempotency-Key: e08-wr2-$RUN")
RID3=$(echo "$WR" | jq_get "print(d['data']['revisionId'])")
LV3=$(echo "$WR" | jq_get "print(d['data']['lockVersion'])")
S1=$(curl -s -m 15 -X PUT "$GW/api/software/$SID/revisions/$RID3?expectedVersion=$LV3" -H "$U1H" -H "Content-Type: application/json" -d '{
  "name":"E08 验收工具","summary":"A04-A51 验收用","categoryId":"1",
  "descriptionHtml":"<p>E08 验收 v2。</p>","licenseType":"FREE","attributionType":"ORIGINAL","accessMode":"REGISTERED",
  "releases":[{"versionName":"1.1.0","channel":"STABLE","recommended":true,"changelog":"第二版",
    "sources":[{"sourceType":"NETDISK","platform":"WINDOWS","arch":"X64","label":"E08 网盘改名",
      "url":"https://pan.baidu.com/s/1e08abc?pwd=e08x","extractionCode":"e08secret","sizeLabel":"9MB"}]}]}')
check "第二版保存" "true" "$(echo "$S1" | ok_of)"
LV4=$(echo "$S1" | jq_get "print(d['data']['lockVersion'])")
SUB1=$(curl -s -m 15 -X POST "$GW/api/software/$SID/revisions/$RID3/submit?expectedVersion=$LV4" -H "$U1H" -H "Idempotency-Key: e08-submit2a-$RUN")
LV4S=$(echo "$SUB1" | jq_get "print(d['data']['lockVersion'])")
WD=$(curl -s -m 15 -X POST "$GW/api/software/$SID/revisions/$RID3/withdraw?expectedVersion=$LV4S" -H "$U1H")
check "seq1 撤回（回 DRAFT 才能重提）" "true" "$(echo "$WD" | ok_of)"
LV4W=$(echo "$WD" | jq_get "print(d['data']['lockVersion'])")
S2=$(curl -s -m 15 -X PUT "$GW/api/software/$SID/revisions/$RID3?expectedVersion=$LV4W" -H "$U1H" -H "Content-Type: application/json" -d '{
  "name":"E08 验收工具","summary":"A04-A51 验收用","categoryId":"1",
  "descriptionHtml":"<p>E08 验收 v3。</p>","licenseType":"FREE","attributionType":"ORIGINAL","accessMode":"REGISTERED",
  "releases":[{"versionName":"1.1.0","channel":"STABLE","recommended":true,"changelog":"第三版",
    "sources":[{"sourceType":"NETDISK","platform":"WINDOWS","arch":"X64","label":"E08 网盘再改",
      "url":"https://pan.baidu.com/s/1e08abc?pwd=e08x","extractionCode":"e08secret","sizeLabel":"9MB"}]}]}')
check "撤回后再次保存" "true" "$(echo "$S2" | ok_of)"
LV5=$(echo "$S2" | jq_get "print(d['data']['lockVersion'])")
SUB2=$(curl -s -m 15 -X POST "$GW/api/software/$SID/revisions/$RID3/submit?expectedVersion=$LV5" -H "$U1H" -H "Idempotency-Key: e08-submit2b-$RUN")
check "seq2 提交" "true" "$(echo "$SUB2" | ok_of)"
SL=$(slock "$SID")
RD=$(get_admin "/revisions/$RID3")
check "A14 审核详情含待审与线上快照" "true" "$(echo "$RD" | jq_get "print(str(d['data'].get('target') is not None and d['data'].get('published') is not None).lower())")"
check "A14 来源标签变化可辨" "true" "$(echo "$RD" | jq_get "
t=d['data']['target'] or {}; p=d['data']['published'] or {}
ts=[s.get('label') for r in (t.get('releases') or []) for s in (r.get('sources') or [])]
ps=[s.get('label') for r in (p.get('releases') or []) for s in (r.get('sources') or [])]
print(str(bool(ts) and bool(ps) and ts!=ps).lower())")"
STALE_HTTP=$(curl -s -m 20 -o /tmp/e08_stale.json -w "%{http_code}" -X POST "$GW/api/admin/software/revisions/$RID3/decision" -H "$AH" -H "Content-Type: application/json" -H "Idempotency-Key: e08-dec-stale-$RUN" -d "{\"softwareId\":$SID,\"submitSeq\":1,\"expectedVersion\":$SL,\"approve\":true,\"reason\":\"E08 旧批次\"}")
check "A07 旧批次(submitSeq=1)被拒 409" "409" "$STALE_HTTP"
check "A07 错误码 SOFTWARE_SUBMIT_STALE" "SOFTWARE_SUBMIT_STALE" "$(code_of < /tmp/e08_stale.json)"
DEC2=$(post_admin "/revisions/$RID3/decision" "{\"softwareId\":$SID,\"submitSeq\":2,\"expectedVersion\":$SL,\"approve\":true,\"reason\":\"E08 新批次\"}" "e08-dec2-$RUN")
check "A07 新批次(submitSeq=2)通过" "APPROVED" "$(echo "$DEC2" | jq_get "print(d['data']['decision'])")"

NETDISK_ID=$(refresh_src)
echo "=== A26 榜单下架实时过滤 ==="
curl -s -m 10 -X POST "$GW/api/software/$SID/sources/$NETDISK_ID/resolve" -H "$U2H" > /dev/null
bust_rank_cache
INR=$(curl -s -m 10 "$GW/api/software/rankings?type=access&page=1&size=50" | jq_get "print(str(any(str(x['id'])=='$SID' for x in d['data']['records'])).lower())")
check "获取榜包含 SW" "true" "$INR"
SL=$(slock "$SID")
UNL=$(curl -s -m 15 -X POST "$GW/api/software/$SID/unlist?expectedVersion=$SL" -H "$U1H")
check "作者下架" "true" "$(echo "$UNL" | ok_of)"
INR2=$(curl -s -m 10 "$GW/api/software/rankings?type=access&page=1&size=50" | jq_get "print(str(any(str(x['id'])=='$SID' for x in d['data']['records'])).lower())")
check "A26 下架后榜单立即剔除（穿透缓存）" "false" "$INR2"
SL=$(slock "$SID")
REL=$(curl -s -m 15 -X POST "$GW/api/software/$SID/relist?expectedVersion=$SL" -H "$U1H")
check "重新上架" "true" "$(echo "$REL" | ok_of)"
bust_rank_cache
INR3=$(curl -s -m 10 "$GW/api/software/rankings?type=access&page=1&size=50" | jq_get "print(str(any(str(x['id'])=='$SID' for x in d['data']['records'])).lower())")
check "A26 重新上架后回榜" "true" "$INR3"

echo "=== A43 联系入口 + A41 查看条件变更 + A38 授权 ==="
CT=$(post_admin "/access-contacts" "{\"name\":\"E08客服-$RUN\",\"contactType\":\"HTTPS_URL\",\"contactValue\":\"https://example.com/e08\",\"enabled\":true,\"sortOrder\":10,\"reason\":\"E08 联系入口\"}" "e08-ct-$RUN")
check "A43 联系入口创建" "true" "$(echo "$CT" | ok_of)"
CID_C=$(get_admin "/access-contacts" | jq_get "print(next((str(c['id']) for c in d['data'] if c.get('name')=='E08客服-$RUN'),''))")
WR2=$(curl -s -m 15 -X POST "$GW/api/software/$SID/working-revision" -H "$U1H" -H "Idempotency-Key: e08-wr3-$RUN")
RID4=$(echo "$WR2" | jq_get "print(d['data']['revisionId'])")
LV6=$(echo "$WR2" | jq_get "print(d['data']['lockVersion'])")
PAID=$(curl -s -m 15 -X PUT "$GW/api/software/$SID/revisions/$RID4?expectedVersion=$LV6" -H "$U1H" -H "Content-Type: application/json" -d "{
  \"name\":\"E08 验收工具\",\"summary\":\"A04-A51 验收用\",\"categoryId\":\"1\",
  \"descriptionHtml\":\"<p>E08 验收 v4。</p>\",\"licenseType\":\"FREE\",\"attributionType\":\"ORIGINAL\",\"accessMode\":\"ADMIN_PAID\",\"accessContactId\":$CID_C,
  \"releases\":[{\"versionName\":\"1.1.0\",\"channel\":\"STABLE\",\"recommended\":true,\"changelog\":\"改查看条件\",
    \"sources\":[{\"sourceType\":\"NETDISK\",\"platform\":\"WINDOWS\",\"arch\":\"X64\",\"label\":\"E08 网盘\",
      \"url\":\"https://pan.baidu.com/s/1e08abc?pwd=e08x\",\"extractionCode\":\"e08secret\",\"sizeLabel\":\"9MB\"}]}]}")
check "A41 改为 ADMIN_PAID（携带联系入口）" "true" "$(echo "$PAID" | ok_of)"
LV7=$(echo "$PAID" | jq_get "print(d['data']['lockVersion'])")
curl -s -m 15 -X POST "$GW/api/software/$SID/revisions/$RID4/submit?expectedVersion=$LV7" -H "$U1H" -H "Idempotency-Key: e08-submit3-$RUN" > /dev/null
SL=$(slock "$SID")
DEC3=$(post_admin "/revisions/$RID4/decision" "{\"softwareId\":$SID,\"submitSeq\":1,\"expectedVersion\":$SL,\"approve\":true,\"reason\":\"E08 改条件\"}" "e08-dec3-$RUN")
check "A41 查看条件变更经审核生效" "APPROVED" "$(echo "$DEC3" | jq_get "print(d['data']['decision'])")"
NETDISK_ID=$(refresh_src)
R41=$(curl -s -m 10 -o /tmp/e08_41.json -w "%{http_code}" -X POST "$GW/api/software/$SID/sources/$NETDISK_ID/resolve" -H "$U2H")
check "A41 未授权 resolve 403" "403" "$R41"
check "A41 错误码 SOFTWARE_PAYMENT_ACCESS_REQUIRED" "SOFTWARE_PAYMENT_ACCESS_REQUIRED" "$(code_of < /tmp/e08_41.json)"
AS=$(curl -s -m 10 "$GW/api/software/$SID/access-state" -H "$U2H")
check "A43 付款模式展示联系入口" "true" "$(echo "$AS" | jq_get "print(str(any(c.get('name')=='E08客服-$RUN' for c in (d['data'].get('contacts') or []))).lower())")"
CTOFF=$(post_admin "/access-contacts" "{\"id\":$CID_C,\"name\":\"E08客服-$RUN\",\"contactType\":\"HTTPS_URL\",\"contactValue\":\"https://example.com/e08\",\"enabled\":false,\"sortOrder\":10,\"reason\":\"E08 停用\"}" "e08-ctoff-$RUN")
check "A43 停用成功" "true" "$(echo "$CTOFF" | ok_of)"
AS2=$(curl -s -m 10 "$GW/api/software/$SID/access-state" -H "$U2H")
check "A43 停用后联系入口消失" "false" "$(echo "$AS2" | jq_get "print(str(any(c.get('name')=='E08客服-$RUN' for c in (d['data'].get('contacts') or []))).lower())")"
GR=$(post_admin "/$SID/access-grants" '{"userId":2,"expiresAt":null,"confirmationNote":"E08 线下确认","externalReference":"REF-E08","reason":"E08 授权"}' "e08-grant-$RUN")
check "A38 人工授权" "true" "$(echo "$GR" | ok_of)"
R38=$(curl -s -m 10 -o /dev/null -w "%{http_code}" -X POST "$GW/api/software/$SID/sources/$NETDISK_ID/resolve" -H "$U2H")
check "A38 授权后 resolve 200" "200" "$R38"
GID=$(get_admin "/$SID/access-grants" | jq_get "print(next((str(g['grantId']) for g in d['data'] if str(g.get('userId'))=='2'),''))")
[ -n "$GID" ] || { echo "  FAIL  A38 授权行未返回 grantId"; FAIL=$((FAIL + 1)); }
RVK=$(curl -s -m 15 -X POST "$GW/api/admin/software/$SID/access-grants/2/revoke" -H "$AH" -H "Content-Type: application/json" -H "Idempotency-Key: e08-revoke-$RUN" -d '{"reason":"E08 撤销"}')
check "A38 撤销成功" "true" "$(echo "$RVK" | ok_of)"
R38B=$(curl -s -m 10 -o /dev/null -w "%{http_code}" -X POST "$GW/api/software/$SID/sources/$NETDISK_ID/resolve" -H "$U2H")
check "A38 撤销后 resolve 403" "403" "$R38B"

echo "=== A19 失效反馈重复与修复 ==="
NETDISK_ID=$(refresh_src)
FB1=$(curl -s -m 10 -X POST "$GW/api/software/$SID/sources/$NETDISK_ID/feedback" -H "$U2H" -H "Idempotency-Key: e08-fb1-$RUN" -H "Content-Type: application/json" -d '{"reasonCode":"LINK_INVALID","description":"E08 反馈测试"}')
check "A19 反馈创建" "true" "$(echo "$FB1" | ok_of)"
FBID=$(echo "$FB1" | jq_get "print(d['data']['feedbackId'])")
FB2=$(curl -s -m 10 -X POST "$GW/api/software/$SID/sources/$NETDISK_ID/feedback" -H "$U2H" -H "Idempotency-Key: e08-fb2-$RUN" -H "Content-Type: application/json" -d '{"reasonCode":"LINK_INVALID","description":"E08 重复反馈"}')
check "A19 同人同指纹去重（返回同一反馈）" "$FBID" "$(echo "$FB2" | jq_get "print(d['data']['feedbackId'])")"
PUBREV=$(spublished "$SID")
MARK=$(post_admin "/link-feedbacks/$FBID/mark" "{\"status\":\"RESOLVED\",\"resolvedRevisionId\":$PUBREV,\"note\":\"E08 已修复\"}" "e08-mark-$RUN")
check "A19 标记 RESOLVED（携带修复版本）" "true" "$(echo "$MARK" | ok_of)"
FB3=$(curl -s -m 10 -X POST "$GW/api/software/$SID/sources/$NETDISK_ID/feedback" -H "$U2H" -H "Idempotency-Key: e08-fb3-$RUN" -H "Content-Type: application/json" -d '{"reasonCode":"LINK_INVALID","description":"E08 修复后再反馈"}')
check "A19 修复后可再次反馈" "true" "$(echo "$FB3" | ok_of)"

echo "=== A24 评分并发一致性 ==="
for i in 1 2 3 4 5 6 7 8 9 10; do
  curl -s -m 10 -X PUT "$GW/api/software/$SID/rating" -H "$U2H" -H "Content-Type: application/json" -d '{"score":4}' -o /dev/null &
done
wait
RC=$(curl -s -m 10 "$GW/api/software/$SID" | jq_get "print(d['data']['ratingCount'])")
check "A24 并发评分后计数不超 1" "1" "$RC"
curl -s -m 10 -X DELETE "$GW/api/software/$SID/rating" -H "$U2H" -o /dev/null
RATING_ROW=$($DB -N -e "SELECT COUNT(*) FROM mall_software.software_rating WHERE software_id=$SID;" 2>/dev/null | tr -d ' ')
check "A24 撤销后无残留评分行" "0" "$RATING_ROW"

echo "=== A17 并发收藏/点赞 ==="
for i in $(seq 1 20); do
  curl -s -m 10 -X PUT "$GW/api/software/$SID/favorite" -H "$U2H" -o /dev/null &
  curl -s -m 10 -X DELETE "$GW/api/software/$SID/favorite" -H "$U2H" -o /dev/null &
done
wait
FC=$(curl -s -m 10 "$GW/api/software/$SID" | jq_get "print(d['data']['favoriteCount'])")
FROWS=$($DB -N -e "SELECT COUNT(*) FROM mall_software.software_favorite WHERE software_id=$SID;" 2>/dev/null | tr -d ' ')
check "A17 并发收藏后计数=关系行数" "$FROWS" "$FC"
curl -s -m 10 -X POST "$GW/api/software/$SID/comments" -H "$U2H" -H "Idempotency-Key: e08-cmt-$RUN" -H "Content-Type: application/json" -d '{"content":"E08 并发点赞目标评论"}' > /tmp/e08_cmt.json
CID2=$(cat /tmp/e08_cmt.json | jq_get "print(d['data']['id'])")
for i in $(seq 1 30); do curl -s -m 10 -X PUT "$GW/api/software/$SID/comments/$CID2/like" -H "$U2H" -o /dev/null & done
wait
LC=$($DB -N -e "SELECT like_count FROM mall_software.software_comment WHERE id=$CID2;" 2>/dev/null | tr -d ' ')
check "A17 30 并发点赞幂等(=1)" "1" "$LC"

echo "=== A51 举报处置→BAN 闭环 ==="
RP=$(curl -s -m 10 -X POST "$GW/api/software/$SID/reports" -H "$U2H" -H "Idempotency-Key: e08-rpt-$RUN" -H "Content-Type: application/json" -d '{"targetType":"SOFTWARE","reason":"OTHER","description":"E08 举报闭环"}')
RPID=$(echo "$RP" | jq_get "print(d['data']['reportId'])")
check "A51 举报创建" "true" "$(echo "$RP" | ok_of)"
HD=$(post_admin "/reports/$RPID/handle" "{\"status\":\"RESOLVED\",\"action\":\"BAN_SOFTWARE\",\"note\":\"E08 处置封禁\"}" "e08-handle-$RUN")
check "A51 处置 RESOLVED+BAN_SOFTWARE" "true" "$(echo "$HD" | ok_of)"
check "A51 处置后 BANNED" "BANNED" "$(slifecycle "$SID")"
GDET=$(curl -s -m 10 -o /dev/null -w "%{http_code}" "$GW/api/software/$SID")
check "A51 封禁后公开详情 404" "404" "$GDET"
SL=$(slock "$SID")
post_admin "/items/$SID/unban" "{\"reason\":\"E08 核验恢复\",\"expectedVersion\":$SL}" "e08-unban-$RUN" > /dev/null
check "A51 解除封禁→UNLISTED" "UNLISTED" "$(slifecycle "$SID")"
SL=$(slock "$SID")
RL=$(curl -s -m 15 -X POST "$GW/api/software/$SID/relist?expectedVersion=$SL" -H "$U1H")
check "A51 作者重新上架→ACTIVE" "true" "$(echo "$RL" | ok_of)"

echo "=== A48 内部/管理面 scope 边界（直连 9026） ==="
I1=$(curl -s -m 8 -o /dev/null -w "%{http_code}" -X POST "$SWHOST/internal/account-erasure")
check "A48 无令牌调内部擦除被拒" "true" "$(echo "$I1" | grep -qE '^(401|403)$' && echo true || echo false)"
I2=$(curl -s -m 8 -o /dev/null -w "%{http_code}" "$SWHOST/admin/software/items")
check "A48 无服务令牌直连管理面被拒" "true" "$(echo "$I2" | grep -qE '^(401|403)$' && echo true || echo false)"

echo "=== 说明性跳过 ==="
echo "  SKIP  A20/A25 MQ 中断与广播崩溃（容错语义由单测覆盖；live 演练需停 RocketMQ）"
echo "  SKIP  A29 三端外链复制降级（需真机/模拟器目验）"
echo "  SKIP  A22/A44 受限管理员权限负例（需专用角色账号；拦截器有单测）"
echo "  SKIP  A45 依赖故障与失败统计（需停社区/用户服务）"

echo "=== 收尾：恢复 REGISTERED 查看条件 ==="
WR3=$(curl -s -m 15 -X POST "$GW/api/software/$SID/working-revision" -H "$U1H" -H "Idempotency-Key: e08-wr4-$RUN")
RID5=$(echo "$WR3" | jq_get "print(d['data']['revisionId'])")
LV8=$(echo "$WR3" | jq_get "print(d['data']['lockVersion'])")
curl -s -m 15 -X PUT "$GW/api/software/$SID/revisions/$RID5?expectedVersion=$LV8" -H "$U1H" -H "Content-Type: application/json" -d '{
  "name":"E08 验收工具","summary":"A04-A51 验收用","categoryId":"1",
  "descriptionHtml":"<p>E08 验收（已恢复 REGISTERED）。</p>","licenseType":"FREE","attributionType":"ORIGINAL","accessMode":"REGISTERED",
  "releases":[{"versionName":"1.1.0","channel":"STABLE","recommended":true,"changelog":"恢复公开获取",
    "sources":[{"sourceType":"NETDISK","platform":"WINDOWS","arch":"X64","label":"E08 网盘",
      "url":"https://pan.baidu.com/s/1e08abc?pwd=e08x","extractionCode":"e08secret","sizeLabel":"9MB"}]}]}' > /tmp/e08_save5.json
LV9=$(cat /tmp/e08_save5.json | jq_get "print(d['data']['lockVersion'])")
curl -s -m 15 -X POST "$GW/api/software/$SID/revisions/$RID5/submit?expectedVersion=$LV9" -H "$U1H" -H "Idempotency-Key: e08-submit4-$RUN" > /dev/null
SL=$(slock "$SID")
DEC4=$(post_admin "/revisions/$RID5/decision" "{\"softwareId\":$SID,\"submitSeq\":1,\"expectedVersion\":$SL,\"approve\":true,\"reason\":\"E08 恢复\"}" "e08-dec4-$RUN")
check "收尾恢复 REGISTERED" "APPROVED" "$(echo "$DEC4" | jq_get "print(d['data']['decision'])")"

echo "=== 汇总 ==="
echo "PASS=$PASS FAIL=$FAIL"
