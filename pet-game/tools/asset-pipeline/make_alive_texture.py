#!/usr/bin/env python3
"""make_alive_texture.py — 把"生动脸"烘焙图安装为独立贴图资产（替代 GLB 内嵌拼接方案）。

背景（为什么改走独立贴图）：
  上一版 bake_alive.py 把重绘 PNG 用 prVw 私有区块补长后拼回 GLB 内嵌槽位。
  数据层自检通过（PNG 合法、奶油色），但该方案把"脸"和"模型容器"绑死：
  每次改脸都要重拼 GLB、重导入、且 prVw 区块对 Cocos 导入器的兼容性始终未在真机验证过。
  改为独立贴图资产后：GLB 保持原样（零导入风险），脸在运行时以 mainTexture 覆盖，
  改脸 = 换一张 PNG，资产链路与普通贴图完全一致。

流程：
  1) 解码 _probe/redraw_debug.png（bake_alive.py 的干净无填充产物）
  2) 自检：非白板（亮度 std 足够大）+ 五官采样点颜色符合预期
  3) 通过后复制为 assets/resources/textures/pet-cat-alive.png
     （不写 .meta —— 构建时 asset-db 会自动生成，eye_sprite.png 即先例）

用法（Blender 自带 python，含 numpy）：
  "D:/Program Files/Blender Foundation/Blender 5.2/5.2/python/bin/python.exe" make_alive_texture.py
"""
import os
import shutil
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bake_alive import png_decode, KNOWN_EYES  # noqa: E402

SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), '_probe', 'redraw_debug.png')
DEST = 'D:/Ide/IdeaProjects/CloudMart/pet-game/assets/resources/textures/pet-cat-alive.png'


def sample_gltf_uv(arr: np.ndarray, u: float, v: float) -> np.ndarray:
    """按 glTF uv（v 向上）取像素颜色。解码器 row0=PNG 顶=v=1，故 y=(1-v)·H。"""
    h, w = arr.shape[:2]
    return arr[int((1.0 - v) * h) % h, int(u * w) % w]


def main() -> None:
    w, h, ct, arr = png_decode(open(SRC, 'rb').read())
    mean = arr.reshape(-1, 3).mean(axis=0)
    lum = 0.299 * arr[:, :, 0] + 0.587 * arr[:, :, 1] + 0.114 * arr[:, :, 2]
    print(f'[alive] source {w}x{h} ct={ct} mean RGB={mean.round(3)} lum std={lum.std():.3f}')

    # 自检 1：非白板。白板 std≈0；正常奶油猫有毛发纹理，std 应 >0.05。
    assert lum.std() > 0.05, f'luminance std={lum.std():.3f} 太低，疑似白板'

    # 自检 2：双眼位置应为深色（琥珀虹膜+瞳孔叠加后明显暗于基色）。
    for i, (u, v) in enumerate(KNOWN_EYES):
        px = sample_gltf_uv(arr, u, v)
        pl = 0.299 * px[0] + 0.587 * px[1] + 0.114 * px[2]
        print(f'[alive] eye{i} uv=({u},{v}) rgb={px.round(3)} lum={pl:.3f}')
        assert pl < 0.55, f'eye{i} 亮度 {pl:.3f} 偏高，眼睛没有画在预期位置'

    # 自检 3：鼻子（双眼中点上方 1.25R）应为粉色（R 明显高于 B）。
    Ruv = min(0.090, (min(KNOWN_EYES[0][0], KNOWN_EYES[1][0]) - 0.005) / 1.06)
    nx = (KNOWN_EYES[0][0] + KNOWN_EYES[1][0]) / 2.0
    ny = min(KNOWN_EYES[0][1], KNOWN_EYES[1][1]) - Ruv * 1.25
    nose = sample_gltf_uv(arr, nx, ny)
    print(f'[alive] nose uv=({nx:.3f},{ny:.3f}) rgb={nose.round(3)}')
    assert nose[0] - nose[2] > 0.08, f'鼻子 rgb={nose.round(3)} 不够粉，粉色鼻没有落在预期位置'

    shutil.copyfile(SRC, DEST)
    print(f'[alive] installed -> {DEST} ({os.path.getsize(DEST)} bytes)')


if __name__ == '__main__':
    main()
