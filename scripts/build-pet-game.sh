#!/usr/bin/env bash
# =============================================================================
# pet-game Cocos 构建脚本（P0-3 方案 A 第 4 步）
#
# 用途：把 Cocos 工程构建为 web-mobile 产物并落到 Web 宿主静态目录
#       （CloudMart-ui/public/pet-game，构建配置 buildPath 已指向该目录）。
#       产物供两处消费：
#         - Web：CloudMart-ui PetStage/CocosStage 同源 iframe（/pet-game）
#         - Expo：react-native-webview 加载 EXPO_PUBLIC_PET_GAME_URL 指向的同一产物
#       小程序保留原生页面，不消费该产物（方案 A 分端接入决策）。
#
# 依赖：本仓库内置于 pet-game/cocos-cli 的构建 CLI（dist/cli.js）。
#       首次使用需在 pet-game/cocos-cli 下 npm install && npm run build。
#
# 用法：scripts/build-pet-game.sh [--skip-build]
#       --skip-build  跳过 Cocos 构建，仅把 pet-game/build/web-mobile 拷贝到宿主目录
#
# 注意：产物更新后需手动递增 CloudMart-ui/src/components/PetStage/CocosStage.tsx
#       中 PET_GAME_FRAME_URL 的版本戳（稳定版本号，禁止随机戳——见该文件注释）。
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PET_GAME_DIR="$ROOT/pet-game"
BUILD_OUTPUT="$PET_GAME_DIR/build/web-mobile"
HOST_TARGET="$ROOT/CloudMart-ui/public/pet-game"

SKIP_BUILD=false
[[ "${1:-}" == "--skip-build" ]] && SKIP_BUILD=true

if [[ "$SKIP_BUILD" == false ]]; then
  CLI_ENTRY="$PET_GAME_DIR/cocos-cli/dist/cli.js"
  if [[ ! -f "$CLI_ENTRY" ]]; then
    echo "[pet-game] CLI 未构建：请先执行 cd pet-game/cocos-cli && npm install && npm run build" >&2
    exit 1
  fi
  echo "[pet-game] 开始 Cocos web-mobile 构建（buildPath 取 build-config.json → CloudMart-ui/public）"
  (cd "$PET_GAME_DIR" && node cocos-cli/dist/cli.js build --project . --platform web-mobile)
fi

if [[ ! -f "$BUILD_OUTPUT/index.html" ]]; then
  echo "[pet-game] 构建产物缺失：$BUILD_OUTPUT/index.html 不存在" >&2
  exit 1
fi

mkdir -p "$(dirname "$HOST_TARGET")"
rm -rf "$HOST_TARGET"
cp -R "$BUILD_OUTPUT" "$HOST_TARGET"

FILES_COUNT=$(find "$HOST_TARGET" -type f | wc -l)
echo "[pet-game] 产物已就位：$HOST_TARGET（$FILES_COUNT 个文件）"
echo "[pet-game] 提醒：如场景/资源有变更，请同步递增 CocosStage.tsx 中 PET_GAME_FRAME_URL 的 ?v= 版本戳"
