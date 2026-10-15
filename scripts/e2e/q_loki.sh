#!/usr/bin/env bash
# Loki 日志查询辅助：q_loki.sh <LogQL> <分钟回溯> [limit]
# 例：./scripts/e2e/q_loki.sh '{filename=~".*mall-admin.*"} |~ "Exception"' 30 50
set -uo pipefail
BASE="http://129.204.152.168:8100"
Q="${1:?缺少 LogQL}"
MIN="${2:-30}"
LIMIT="${3:-50}"

NOW=$(date +%s)
START=$((NOW - MIN * 60))
ENC=$(python -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" "$Q")

curl -s -m 60 "$BASE/loki/api/v1/query_range?query=$ENC&start=$((START * 1000000000))&end=$((NOW * 1000000000))&limit=$LIMIT&direction=backward" \
  | python -c "
import sys, json
raw = sys.stdin.read()
if not raw.strip():
    print('NO RESPONSE'); sys.exit(1)
try:
    d = json.loads(raw)
except json.JSONDecodeError:
    print('RAW:', raw[:400]); sys.exit(1)
if d.get('status') != 'success':
    print('LOKI ERROR:', d); sys.exit(1)
r = d['data']['result']
n = sum(len(s.get('values', [])) for s in r)
print('matched lines:', n)
for s in r:
    for ts, line in s.get('values', []):
        print(line[:400])
"
