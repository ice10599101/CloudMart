#!/usr/bin/env bash
# 下载最新 CI run 的诊断 artifact（build-log / failsafe-reports / surefire-reports），
# 解压到仓库根的 .ci-artifacts/ 供本地分析。
#
# 用法（token 只经环境变量，不落盘、不进对话）：
#   CI_TOKEN=github_pat_xxxx bash scripts/fetch-ci-artifacts.sh
# token 权限：Fine-grained → Repository access: CloudMart → Repository permissions →
#   Actions: Read-only。7 天有效期即可，用完在 GitHub 设置里撤销。
set -euo pipefail

REPO="ice10599101/CloudMart"
TOKEN="${CI_TOKEN:?请以 CI_TOKEN=... 形式传入只读 token}"
OUT="$(cd "$(dirname "$0")/.." && pwd)/.ci-artifacts"
mkdir -p "$OUT"

auth=(-H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json")

# 最新 run（也可传 RUN_ID 取历史 run）
RID="${RUN_ID:-$(curl -s "${auth[@]}" "https://api.github.com/repos/$REPO/actions/runs?per_page=1" \
  | python -c "import json,sys;print(json.load(sys.stdin)['workflow_runs'][0]['id'])")}"
echo "run: $RID"

# 列出该 run 的全部 artifact 并逐个下载解压
curl -s "${auth[@]}" "https://api.github.com/repos/$REPO/actions/runs/$RID/artifacts?per_page=20" \
  | python -c "
import json,sys
data = json.load(sys.stdin)
for a in data.get('artifacts', []):
    print(a['id'], a['name'])
" | while read -r AID NAME; do
  DEST="$OUT/$NAME"
  mkdir -p "$DEST"
  echo "下载 $NAME (artifact $AID) ..."
  curl -sL "${auth[@]}" -o "$DEST/$(echo "$NAME" | tr '/' '_').zip" \
    "https://api.github.com/repos/$REPO/actions/artifacts/$AID/zip"
  unzip -oq "$DEST/$(echo "$NAME" | tr '/' '_').zip" -d "$DEST" || echo "  解压失败（保留原始 zip）"
  rm -f "$DEST/$(echo "$NAME" | tr '/' '_').zip"
done

echo "完成。产物在 .ci-artifacts/ 下："
find "$OUT" -type f | head -20
