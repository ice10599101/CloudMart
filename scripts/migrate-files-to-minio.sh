#!/usr/bin/env bash
# =============================================================================
# P1-19：本地文件 → MinIO 存量迁移脚本
#
# 前置：服务器已安装 MinIO Client（mc），并已配置 alias：
#   mc alias set cloudmart http://127.0.0.1:9000 <ACCESS_KEY> <SECRET_KEY>
#
# 用法：
#   ./migrate-files-to-minio.sh [本地存储根目录]
#   默认根目录 ../files（mall-file file.storage-path 缺省值），内含
#   public/ private/（quarantine/ 为隔离域，不迁移）。
#
# 行为：
#   1. mc mirror --preserve 按 storageKey 镜像 public/ 与 private/ 到 bucket
#      （幂等：已迁移对象跳过，可反复执行直至差异为 0）；
#   2. 逐域统计本地与远端对象数并输出核对结果；
#   3. 不删除本地任何文件——mall-file 切到 minio 模式且核对通过后，
#      由运维手动归档本地目录（回滚 = 把 file.storage-mode 切回 local）。
# =============================================================================
set -euo pipefail

STORAGE_ROOT="${1:-$(dirname "$0")/../files}"
ALIAS="${MC_ALIAS:-cloudmart}"
BUCKET="${MINIO_BUCKET:-cloudmart-files}"

if ! command -v mc >/dev/null 2>&1; then
  echo "[migrate] 未找到 mc（MinIO Client）。安装：https://min.io/download" >&2
  exit 1
fi

if ! mc alias list "$ALIAS" >/dev/null 2>&1; then
  echo "[migrate] mc alias '$ALIAS' 不存在。先执行：mc alias set $ALIAS http://127.0.0.1:9000 <KEY> <SECRET>" >&2
  exit 1
fi

mc bucket add "$ALIAS/$BUCKET" --ignore-existing 2>/dev/null || mc mb --ignore-existing "$ALIAS/$BUCKET"

overall_fail=0
for domain in public private; do
  src_dir="$STORAGE_ROOT/$domain"
  if [[ ! -d "$src_dir" ]]; then
    echo "[migrate] 跳过 $domain（本地不存在：$src_dir）"
    continue
  fi
  echo "[migrate] 镜像 $domain → $ALIAS/$BUCKET/$domain ..."
  mc mirror --preserve --overwrite "$src_dir" "$ALIAS/$BUCKET/$domain"

  local_count=$(find "$src_dir" -type f | wc -l | tr -d ' ')
  remote_count=$(mc ls --recursive "$ALIAS/$BUCKET/$domain" | wc -l | tr -d ' ')
  echo "[migrate] 核对 $domain：本地 $local_count / 远端 $remote_count"
  if [[ "$local_count" != "$remote_count" ]]; then
    echo "[migrate] ⚠️ $domain 对象数不一致，请重跑（mc mirror 幂等）或人工核对" >&2
    overall_fail=1
  fi
done

if [[ "$overall_fail" -eq 0 ]]; then
  echo "[migrate] ✅ 迁移核对通过。切换步骤：mall-file 注入 FILE_STORAGE_MODE=minio 并重启 → 回滚即切回 local"
else
  echo "[migrate] ❌ 存在差异，暂不切换存储模式" >&2
  exit 1
fi
