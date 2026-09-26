#!/usr/bin/env bash
# 图生 3D 原始资产 -> 可上线运行时模型。
# 顺序很重要（都是上次踩坑换来的）：减面 -> 缩放贴图 -> 格式 -> 切线 -> 修切线 -> 修材质。
# 注意 gltf-transform 的命令行参数与 v4 文档一致，别照抄别处的写法。
set -e

NODE="C:/Users/Administrator/.workbuddy/binaries/node/versions/22.22.2-3/node.exe"
CLI="C:/Users/Administrator/.workbuddy/binaries/node/workspace/node_modules/@gltf-transform/cli/bin/cli.js"
PY="C:/Users/Administrator/.workbuddy/binaries/python/versions/3.13.12/python.exe"

cd "$(dirname "$0")/../.."          # -> pet-game/
V=design/model/qqcat
SRC=$V/qqcat-raw.glb
A=$V/_a.glb
B=$V/_b.glb
C=$V/_c.glb
D=$V/_d.glb
OUT=$V/qqcat-prod.glb

echo "== 1/6 optimize（减面，贴图先不动）=="
# --texture-compress false 时 --texture-size 不生效，所以贴图缩放必须单独用 resize（上次的坑）
"$NODE" "$CLI" optimize "$SRC" "$A" \
  --compress false --simplify-ratio 0.045 --simplify-error 0.005 --texture-compress false

echo "== 2/6 resize 贴图到 1024 =="
"$NODE" "$CLI" resize "$A" "$B" --width 1024 --height 1024

echo "== 3/6 baseColor 转 JPEG（--formats 必须显式写 *，默认只转本来就是 JPEG 的）=="
"$NODE" "$CLI" jpeg "$B" "$C" --formats "*" --slots "baseColorTexture" --quality 90

echo "== 4/6 生成切线 =="
"$NODE" "$CLI" tangents "$C" "$D"

echo "== 5/6 修零长切线 + 去金属感 =="
"$PY" tools/asset-pipeline/fix_tangents.py "$D" "$OUT" 2>&1 | tail -5 || cp "$D" "$OUT"
"$PY" tools/asset-pipeline/material_fix.py "$OUT" "$OUT" 2>&1 | tail -8 || true

echo "== 6/6 校验 =="
"$PY" tools/asset-pipeline/glb_inspect.py "$OUT" 2>&1 | head -16
ls -la "$OUT" | awk '{printf "最终体积: %.2f MB\n", $5/1048576}'
