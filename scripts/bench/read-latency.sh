#!/usr/bin/env bash
# E03 性能基线采集脚本：读 API p95 延迟（工程验收基线：预发数据 100 并发读 p95<500ms、常规写 p95<1s）
#
# 用法（在能直连网关的预发/压测环境执行，勿打生产）：
#   GATEWAY=http://<gateway>:8090 TOKEN=<user-jwt> ./scripts/bench/read-latency.sh [path] [total] [concurrency]
# 示例：
#   GATEWAY=http://129.204.152.168:8090 TOKEN=eyJ... ./scripts/bench/read-latency.sh /api/product/products/10 200 20
#
# 输出：样本数、p50/p90/p95/p99/max（毫秒）。结果按 E03 规范记录进 docs/benchmarks/。

set -euo pipefail

GATEWAY="${GATEWAY:?请设置 GATEWAY 环境变量}"
TOKEN="${TOKEN:?请设置 TOKEN（用户 JWT）}"
PATH_URL="${1:-/api/product/products/1}"
TOTAL="${2:-200}"
CONC="${3:-20}"

OUT_DIR="$(mktemp -d)"
echo "target=$GATEWAY$PATH_URL total=$TOTAL concurrency=$CONC"

t0=$(date +%s)
seq 1 "$TOTAL" | xargs -P "$CONC" -I{} bash -c '
  line=$(curl -s -o /dev/null -w "%{time_total}" --max-time 10 \
    -H "Authorization: Bearer '"$TOKEN"'" "'"$GATEWAY""$PATH_URL"'")
  echo "$line"
' > "$OUT_DIR/times.txt"
t1=$(date +%s)

# 毫秒化并排序
sort -n "$OUT_DIR/times.txt" | awk -v total="$TOTAL" '
  { ms[NR] = $1 * 1000 }
  END {
    if (NR == 0) { print "no samples"; exit 1 }
    printf "samples=%d wall=%ds\n", NR, systime() - 0
    printf "p50=%.0fms p90=%.0fms p95=%.0fms p99=%.0fms max=%.0fms\n",
      ms[int(NR*0.50)], ms[int(NR*0.90)], ms[int(NR*0.95)], ms[int(NR*0.99)], ms[NR]
  }'

echo "耗时：$((t1 - t0))s（原始样本：$OUT_DIR/times.txt）"
echo "记录规范：结果连同数据量（商品/SKU 行数）、机器规格、执行日期写入 docs/benchmarks/，"
echo "不得空报达标（E03）；未达 p95<500ms 时先查索引与连接池再调优。"
