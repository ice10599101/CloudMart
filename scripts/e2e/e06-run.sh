#!/usr/bin/env bash
# E06 验收链：签到→Redis 故障→恢复（方案 E06）
# 用法（三个阶段，Redis 停启由你执行）：
#   ./e06-run.sh pre        # 故障前：打卡成功基线（幂等口径确认）
#   docker stop cloudmart-redis && ./e06-run.sh outage
#   docker start cloudmart-redis && ./e06-run.sh recovery
set -uo pipefail
PHASE="${1:-pre}"
WISH=2096306820871180290
DB="mysql -h 129.204.152.168 -P 8306 -uroot -p123456 --connect-timeout=5 -D mall_wish"

login() { curl -s --connect-timeout 4 -X POST http://129.204.152.168:9001/login   -H "Content-Type: application/json" -d '{"account":"10001","password":"123456"}'   | python -c "import json,sys; print(json.load(sys.stdin)['data']['accessToken'])"; }

db_checkin() { $DB -N -e "SELECT COUNT(*) FROM wish_checkin WHERE wish_id=$WISH AND user_id=8 AND checkin_date=CURDATE();" 2>/dev/null; }

case "$PHASE" in
  pre)
    TOKEN=$(login)
    echo "== E06-pre：正常路径打卡基线 =="
    curl -s --connect-timeout 6 -X POST "http://129.204.152.168:9022/wishes/$WISH/checkin"       -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"       -d '{"content":"E06 pre 基线"}' | python -c "
import json,sys
d=json.load(sys.stdin)
print('打卡:', d.get('success'), '| 星光:', (d.get('data') or {}).get('starlightCredited'), '| streak:', (d.get('data') or {}).get('currentStreak'))"
    echo "DB 今日行数（应 1）: $(db_checkin)"
    echo "-- 幂等：同日重复打卡应拒绝 --"
    curl -s --connect-timeout 6 -X POST "http://129.204.152.168:9022/wishes/$WISH/checkin"       -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"       -d '{"content":"E06 pre 重复"}' -o /dev/null -w "重复打卡: %{http_code}（应非 2xx）
"
    echo "DB 今日行数（应仍 1）: $(db_checkin)"
    echo "→ 请执行 docker stop cloudmart-redis，然后 $0 outage"
    ;;
  outage)
    TOKEN=$(login)
    echo "== E06-outage：Redis 已停 =="
    curl -s --connect-timeout 8 -X POST "http://129.204.152.168:9022/wishes/$WISH/checkin"       -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"       -d '{"content":"E06 outage 注入"}' -o /tmp/e06-outage.json -w "故障期打卡: %{http_code}
"
    head -c 200 /tmp/e06-outage.json; echo
    echo "DB 今日行数（应与 pre 相同=1，无脏数据）: $(db_checkin)"
    echo "→ 请执行 docker start cloudmart-redis，然后 $0 recovery"
    ;;
  recovery)
    TOKEN=$(login)
    echo "== E06-recovery：恢复后 =="
    echo "DB 今日行数（应仍=1，outage 期间无半截写入）: $(db_checkin)"
    echo "-- App 离线队列模拟：恢复后补发（应成功入账或幂等拒绝）--"
    curl -s --connect-timeout 8 -X POST "http://129.204.152.168:9022/wishes/$WISH/checkin"       -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"       -d '{"content":"E06 恢复补发"}' | head -c 200; echo
    echo "DB 今日行数（应仍=1，补发被同日幂等拒绝）: $(db_checkin)"
    echo "== 奖励恰好一次 =="
    $DB -N -e "SELECT starlight_granted, COUNT(*) FROM wish_checkin WHERE wish_id=$WISH AND user_id=8 AND checkin_date=CURDATE() GROUP BY starlight_granted;" 2>/dev/null
    ;;
  *) echo "用法: $0 pre|outage|recovery";;
esac
