#!/usr/bin/env bash
# E07 小答软件 P0 联调链（方案 14 节 A01-A44 用户侧）
# 用法：./e07-software.sh phase1|phase2
# phase1: 游客浏览(A01)/游客resolve拦截(A02)/资格预置(DB)/资格状态/创建幂等(A09)/保存冲突(A08)/URL攻击(A12)/提交(A05)
# phase2: 审核通过后执行——公开可见(A05/A06)/resolve放行(A03)/日去重计数(A18)/评论/点赞/收藏(A15/A16/A17)
set -uo pipefail
GW=http://129.204.152.168:8090
AUTH=http://129.204.152.168:9001
DB="mysql -h 129.204.152.168 -P 8306 -uroot -p123456 --connect-timeout=5"
UID_VAL=8

EMAIL="e07link@test.dev"
PASS="E07pass!234"
UID_VAL=1

login() { curl -s --connect-timeout 4 -X POST $AUTH/login -H "Content-Type: application/json" \
  -d "{\"account\":\"$EMAIL\",\"password\":\"$PASS\"}" \
  | python -c "import json,sys; print(json.load(sys.stdin)['data']['accessToken'])"; }

TOKEN=$(login)
if [ -z "$TOKEN" ]; then echo "登录失败"; exit 1; fi
AUTHH="Authorization: Bearer $TOKEN"
echo "测试账号: $EMAIL"

phase1() {
  echo "== A01 游客浏览（应空列表/仅已审） =="
  curl -s -m 8 "$GW/api/software?page=1&size=10" | python -c "import json,sys; d=json.load(sys.stdin); print('success:',d['success'],'| total:',d['data']['total'])"

  echo "== A02 游客 resolve（应 401） =="
  curl -s -m 8 -X POST "$GW/api/software/1/sources/1/resolve" -o /tmp/a02.json -w "HTTP %{http_code}\n"
  head -c 120 /tmp/a02.json; echo

  echo "== 预置：DB 开通 user8 发布资格（联调预置，正式入口走 mall-admin） =="
  $DB -N -e "INSERT INTO mall_software.software_publisher_permission (user_id,can_publish,updated_by,reason,lock_version,created_at,updated_at) VALUES ((SELECT u.id FROM (SELECT id FROM mall_user.users WHERE email='e07link@test.dev') u),1,1000,'E07 联调预置',0,NOW(3),NOW(3)) ON DUPLICATE KEY UPDATE can_publish=1, updated_at=NOW(3);" 2>/dev/null && echo "资格已开通"

  echo "== publisher-status（应 canPublish=true） =="
  curl -s -m 8 "$GW/api/software/publisher-status" -H "$AUTHH" | python -c "import json,sys; d=json.load(sys.stdin)['data']; print('canPublish:',d['canPublish'],'| publishEnabled:',d['publishEnabled'])"

  echo "== A09 创建软件（幂等键 e07-create-1） =="
  CREATE=$(curl -s -m 8 -X POST "$GW/api/software" -H "$AUTHH" -H "Idempotency-Key: e07-create-1" -H "Content-Type: application/json" -d '{}')
  echo "$CREATE" | python -c "import json,sys; d=json.load(sys.stdin)['data']; print('softwareId:',d['softwareId'],'| revisionId:',d['workingRevisionId'],'| lockVersion:',d['lockVersion'])"
  SID=$(echo "$CREATE" | python -c "import json,sys; print(json.load(sys.stdin)['data']['softwareId'])")
  RID=$(echo "$CREATE" | python -c "import json,sys; print(json.load(sys.stdin)['data']['workingRevisionId'])")
  LV=$(echo "$CREATE" | python -c "import json,sys; print(json.load(sys.stdin)['data']['lockVersion'])")

  echo "-- A09 同键同参重放（应返回同一 softwareId=$SID） --"
  curl -s -m 8 -X POST "$GW/api/software" -H "$AUTHH" -H "Idempotency-Key: e07-create-1" -H "Content-Type: application/json" -d '{}' \
    | python -c "import json,sys; d=json.load(sys.stdin)['data']; print('replay softwareId:',d['softwareId'],'（应 $SID）')"
  echo "-- A09 同键异体（应 409） --"
  curl -s -m 8 -X POST "$GW/api/software" -H "$AUTHH" -H "Idempotency-Key: e07-create-1" -H "Content-Type: application/json" -d '{"x":1}' -o /tmp/a09.json -w "HTTP %{http_code}\n"
  head -c 120 /tmp/a09.json; echo

  echo "== A12 URL 攻击保存被拒（evilbaidu.com） =="
  curl -s -m 8 -X PUT "$GW/api/software/$SID/revisions/$RID?expectedVersion=$LV" -H "$AUTHH" -H "Content-Type: application/json" -d '{
    "name":"E07 联调效率工具","summary":"用于验证小答软件发布链路的示例工具","categoryId":"1",
    "descriptionHtml":"<p>联调测试用软件介绍。</p><script>alert(1)</script>",
    "licenseType":"FREE","attributionType":"ORIGINAL","accessMode":"REGISTERED",
    "releases":[{"versionName":"1.0.0","channel":"STABLE","recommended":true,
      "sources":[{"sourceType":"NETDISK","platform":"WINDOWS","arch":"X64","label":"Win64",
        "url":"https://evilbaidu.com/s/1","extractionCode":"abc"}]}]}' -o /tmp/a12.json -w "HTTP %{http_code}\n"
  head -c 160 /tmp/a12.json; echo

  echo "== 正常保存草稿（A13 script 应被剥离） =="
  SAVE=$(curl -s -m 8 -X PUT "$GW/api/software/$SID/revisions/$RID?expectedVersion=$LV" -H "$AUTHH" -H "Content-Type: application/json" -d '{
    "name":"E07 联调效率工具","summary":"用于验证小答软件发布链路的示例工具","categoryId":"1",
    "descriptionHtml":"<p>联调测试用软件介绍。</p><script>alert(1)</script>",
    "licenseType":"FREE","attributionType":"ORIGINAL","accessMode":"REGISTERED",
    "tagIds":["1"],
    "releases":[{"versionName":"1.0.0","channel":"STABLE","recommended":true,"changelog":"首个版本",
      "sources":[{"sourceType":"NETDISK","platform":"WINDOWS","arch":"X64","label":"Win64 网盘",
        "url":"https://pan.baidu.com/s/1e07Test?pwd=e07a","extractionCode":"a1b2","sizeLabel":"12MB"}]}]}')
  echo "$SAVE" | python -c "
import json,sys
d=json.load(sys.stdin)
data=d['data']
print('success:',d['success'],'| newLockVersion:',data['lockVersion'],'| releases:',len(data['releases']))
src=data['releases'][0]['sources'][0]
print('sourceUrl(编辑器可见):',src['url'][:40])
import re
print('descriptionHtml 含 script:', '<script' in (data.get('descriptionHtml') or ''))"
  LV2=$(echo "$SAVE" | python -c "import json,sys; print(json.load(sys.stdin)['data']['lockVersion'])")

  echo "== A08 并发保存冲突（expectedVersion 过期应 409） =="
  curl -s -m 8 -X PUT "$GW/api/software/$SID/revisions/$RID?expectedVersion=$LV" -H "$AUTHH" -H "Content-Type: application/json" -d '{
    "name":"E07 过期页面保存","summary":"这个保存应该因为版本冲突失败","accessMode":"REGISTERED"}' -o /tmp/a08.json -w "HTTP %{http_code}\n"
  head -c 140 /tmp/a08.json; echo

  echo "== A05 提交审核（应 PENDING，submitSeq=1） =="
  curl -s -m 8 -X POST "$GW/api/software/$SID/revisions/$RID/submit?expectedVersion=$LV2" -H "$AUTHH" -H "Idempotency-Key: e07-submit-1" -o /tmp/submit.json -w "HTTP %{http_code}\n"
  head -c 200 /tmp/submit.json; echo

  echo "== A05 提交后仍不可见（草稿/待审不泄漏） =="
  curl -s -m 8 "$GW/api/software/$SID" -o /tmp/a05.json -w "HTTP %{http_code}\n"
  head -c 120 /tmp/a05.json; echo

  echo "== mine tab=pending（应包含 $SID） =="
  curl -s -m 8 "$GW/api/software/mine?tab=pending" -H "$AUTHH" | python -c "
import json,sys
d=json.load(sys.stdin)['data']
rows=d['records']
print('pending 数:',len(rows),'| 首行:',rows[0]['name'] if rows else '-','| status:',rows[0]['status'] if rows else '-')"

  echo "SID=$SID RID=$RID" > /tmp/e07-ids.env
  echo "== phase1 完成。审核需要管理员：POST /api/admin/software/revisions/$RID/decision =="
}

phase2() {
  source /tmp/e07-ids.env 2>/dev/null || { echo "先跑 phase1"; exit 1; }
  TOKEN=$(login); AUTHH="Authorization: Bearer $TOKEN"
  echo "== A05/A06 审核通过后公开可见 =="
  curl -s -m 8 "$GW/api/software/$SID" | python -c "
import json,sys
d=json.load(sys.stdin)
data=d['data']
print('success:',d['success'],'| name:',data['name'],'| commentCount:',data['commentCount'],'| isFavorited:',data['isFavorited'])"
  echo "-- A13 公开详情无 script/无链接字段 --"
  curl -s -m 8 "$GW/api/software/$SID" | python -c "
import json,sys
raw=sys.stdin.read()
print('含 script:', '<script' in raw, '| 含 extract/archivePassword 字段:', 'archivePassword' in raw, '| 含提取码值 a1b2:', 'a1b2' in raw)"
  echo "-- releases 来源摘要无 URL --"
  curl -s -m 8 "$GW/api/software/$SID/releases" | python -c "
import json,sys
d=json.load(sys.stdin)['data']
s=d['records'][0]['sources'][0]
print('availability:',s['availability'],'| 含 url 字段:', 'url' in s, '| label:',s['label'])"

  echo "== A03 登录 resolve（REGISTERED 放行，返回真实链接+提取码） =="
  SRCID=$($DB -N -e "SELECT id FROM mall_software.software_source ORDER BY id DESC LIMIT 1;" 2>/dev/null)
  curl -s -m 8 -X POST "$GW/api/software/$SID/sources/$SRCID/resolve" -H "$AUTHH" | python -c "
import json,sys
d=json.load(sys.stdin)['data']
print('url:',d['url'],'| extractionCode:',d['extractionCode'],'| availability:',d['availability'])"

  echo "== A18 同日重复 resolve 计数不变 =="
  C1=$($DB -N -e "SELECT link_access_count FROM mall_software.software WHERE id=$SID;" 2>/dev/null)
  curl -s -m 8 -X POST "$GW/api/software/$SID/sources/$SRCID/resolve" -H "$AUTHH" -o /dev/null
  C2=$($DB -N -e "SELECT link_access_count FROM mall_software.software WHERE id=$SID;" 2>/dev/null)
  echo "count 前=$C1 后=$C2（应相等）"

  echo "== A17 收藏/点赞幂等 =="
  curl -s -m 8 -X PUT "$GW/api/software/$SID/favorite" -H "$AUTHH" -o /dev/null -w "收藏1: %{http_code} "
  curl -s -m 8 -X PUT "$GW/api/software/$SID/favorite" -H "$AUTHH" -o /dev/null -w "收藏2(幂等): %{http_code}\n"
  F=$($DB -N -e "SELECT favorite_count FROM mall_software.software WHERE id=$SID;" 2>/dev/null)
  echo "favorite_count=$F（应 1）"

  echo "== A15 发根评论（直发 VISIBLE） =="
  curl -s -m 8 -X POST "$GW/api/software/$SID/comments" -H "$AUTHH" -H "Idempotency-Key: e07-cmt-1" \
    -H "Content-Type: application/json" -d '{"content":"E07 联调评论：工具很好用"}' \
    | python -c "import json,sys; d=json.load(sys.stdin)['data']; print('commentId:',d['id'],'| status:',d['status'])"
  CC=$($DB -N -e "SELECT comment_count FROM mall_software.software WHERE id=$SID;" 2>/dev/null)
  echo "comment_count=$CC（应 1）"

  echo "== A16 敏感词评论（应被社区机审拦截/转人工） =="
  curl -s -m 8 -X POST "$GW/api/software/$SID/comments" -H "$AUTHH" -H "Idempotency-Key: e07-cmt-2" \
    -H "Content-Type: application/json" -d '{"content":"这是一个垃圾软件，骗子"}' -o /tmp/a16.json -w "HTTP %{http_code}\n"
  head -c 200 /tmp/a16.json; echo

  echo "== 评论列表（公开视角） =="
  curl -s -m 8 "$GW/api/software/$SID/comments" | python -c "
import json,sys
d=json.load(sys.stdin)['data']
print('roots:',len(d['records']),'| 首楼 status:',d['records'][0]['status'] if d['records'] else '-')"

  echo "== A16/A17 点赞幂等 =="
  CID=$($DB -N -e "SELECT id FROM mall_software.software_comment WHERE software_id=$SID AND status='VISIBLE' ORDER BY id DESC LIMIT 1;" 2>/dev/null)
  curl -s -m 8 -X PUT "$GW/api/software/$SID/comments/$CID/like" -H "$AUTHH" -o /dev/null
  curl -s -m 8 -X PUT "$GW/api/software/$SID/comments/$CID/like" -H "$AUTHH" -o /dev/null
  L=$($DB -N -e "SELECT like_count FROM mall_software.software_comment WHERE id=$CID;" 2>/dev/null)
  echo "like_count=$L（应 1）"

  echo "== 取消收藏幂等（4.5） =="
  curl -s -m 8 -X DELETE "$GW/api/software/$SID/favorite" -H "$AUTHH" -o /dev/null -w "取消: %{http_code}\n"
  F2=$($DB -N -e "SELECT favorite_count FROM mall_software.software WHERE id=$SID;" 2>/dev/null)
  echo "favorite_count=$F2（应 0）"
}

case "${1:-phase1}" in
  phase1) phase1 ;;
  phase2) phase2 ;;
  *) echo "用法: $0 phase1|phase2" ;;
esac
