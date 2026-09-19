#!/usr/bin/env python3
"""生成 pet-game 工程的 .meta 与最小 3D 场景 JSON（确定性、可重跑）。

背景：pet-game 是 Cocos Creator 4（4.0.0-alpha.34）**3D** 工程，本环境以
cocos-cli（pet-game/cocos-cli）headless 构建，无编辑器 GUI，故由脚本固定
资源 UUID（场景引用脚本组件依赖 meta 中的 UUID 一致）。

场景保持最小 3D 结构（3D 透视相机 + UI 正交相机 Canvas + Root 挂点），
全部内容由 PetGameRoot.start() 程序化构建：
  - 3D 宠物模型（primitives capsule/sphere/cone 拼装）+ 平行光 + 地面
  - 2D UI 叠层（状态条/按钮/气泡/对战面板）
由此把手工 JSON 场景的面积降到最小。

枚举值依据引擎源码（pet-game/cocos-cli/packages/engine）：
  Layers: UI_2D=1<<25=33554432, DEFAULT=1<<30=1073741824
  Camera ClearFlag: SOLID_COLOR=ClearFlagBit.ALL=7, DEPTH_ONLY=DEPTH|STENCIL=6
  CameraProjection: ORTHO=0, PERSPECTIVE=1
用法：python tools/gen_assets.py
"""
import io
import json
import math
import os

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

def read_meta_uuid(meta_path: str, fallback: str) -> str:
    """优先读取 asset-db 重写后的 meta uuid（Cocos 4 首次构建会重写 3.x 版式 meta 并
    重新分配 uuid，此后保持稳定）；meta 不存在时回退到 fallback。"""
    try:
        meta = json.loads(io.open(meta_path, encoding='utf-8').read())
        uuid = meta.get('uuid')
        if uuid:
            return uuid
    except Exception:
        pass
    return fallback

IDS = {
    'scene': read_meta_uuid(os.path.join(HERE, 'assets', 'scenes', 'PetHome.scene.meta'),
                            '7acc2b2c-4772-4e38-8bcb-29682fc42a0a'),
    'root_script': read_meta_uuid(os.path.join(HERE, 'assets', 'scripts', 'PetGameRoot.ts.meta'),
                                  'de28b229-6641-4583-a481-86f5040a13f7'),
    'bridge_script': read_meta_uuid(os.path.join(HERE, 'assets', 'scripts', 'PetGameBridge.ts.meta'),
                                    '743bd67a-5b61-46fb-a87c-2a54826671bf'),
    'anim_script': read_meta_uuid(os.path.join(HERE, 'assets', 'scripts', 'PetAnimations.ts.meta'),
                                  '0757ad7d-00a9-415a-be79-9038924a4c2d'),
}

LAYER_UI_2D = 1 << 25
LAYER_DEFAULT = 1 << 30
CLEAR_SOLID_COLOR = 7   # ClearFlagBit.ALL
CLEAR_DEPTH_ONLY = 6    # DEPTH | STENCIL
PROJ_ORTHO = 0
PROJ_PERSPECTIVE = 1


BASE64_KEYS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'

def compress(u: str) -> str:
    """Cocos compressed UUID（compressUUID(uuid, min=false) 形式，场景 __type__ 引用用）：

    前 5 个 hex 字符字面保留，其后每 3 个 hex 字符编码为 2 个 base64 字符
    （6-bit 打包：lhs=(a<<2)|b>>2, rhs=((b&3)<<4)|c）。
    依据：cocos-cli dist/core/builder/worker/builder/utils/index.js 的
    compressUUID(uuid, false)，并与构建产物 _RF.push 注册 id 实测对齐。
    （min=true 变体保留前 2 位，对应运行时 decodeUuid，勿用于场景引用。）
    """
    h = u.replace('-', '')
    if len(h) != 32:
        raise ValueError('uuid hex length must be 32')
    out = h[:5]
    for i in range(5, 32, 3):
        a, b, c = int(h[i], 16), int(h[i + 1], 16), int(h[i + 2], 16)
        lhs = (a << 2) | (b >> 2)
        rhs = ((b & 3) << 4) | c
        out += BASE64_KEYS[lhs] + BASE64_KEYS[rhs]
    return out


def quat_from_euler(rx: float, ry: float, rz: float) -> dict:
    """欧拉角(度) → cc.Quat 序列化块（ZYX 顺序近似单轴旋转场景足够）。"""
    crx, cx = math.cos(math.radians(rx / 2)), math.sin(math.radians(rx / 2))
    cry, cy = math.cos(math.radians(ry / 2)), math.sin(math.radians(ry / 2))
    crz, cz = math.cos(math.radians(rz / 2)), math.sin(math.radians(rz / 2))
    # 单轴俯仰为主，用 X 分量欧拉即可（相机微俯视）
    return {"__type__": "cc.Quat", "x": cx, "y": cy, "z": cz, "w": crx * cry * crz + cx * cy * cz}


def node(name, children, components, layer=LAYER_DEFAULT, pos=(0, 0, 0), euler=(0, 0, 0)):
    """cc.Node 序列化块（4.0 格式）。"""
    return {
        "__type__": "cc.Node",
        "_name": name,
        "_objFlags": 0,
        "__editorExtras__": {},
        "_parent": None,  # 由拼装阶段回填
        "_children": [{"__id__": i} for i in children],
        "_active": True,
        "_components": [{"__id__": i} for i in components],
        "_prefab": None,
        "_lpos": {"__type__": "cc.Vec3", "x": pos[0], "y": pos[1], "z": pos[2]},
        "_lrot": quat_from_euler(*euler),
        "_lscale": {"__type__": "cc.Vec3", "x": 1, "y": 1, "z": 1},
        "_mobility": 0,
        "_layer": layer,
        "_euler": {"__type__": "cc.Vec3", "x": euler[0], "y": euler[1], "z": euler[2]},
        "_id": "",
    }


def uitransform(node_id, w=0, h=0):
    return {
        "__type__": "cc.UITransform",
        "_name": "", "_objFlags": 0, "__editorExtras__": {},
        "node": {"__id__": node_id}, "_enabled": True, "__prefab": None,
        "_contentSize": {"__type__": "cc.Size", "width": w, "height": h},
        "_anchorPoint": {"__type__": "cc.Vec2", "x": 0.5, "y": 0.5},
        "_id": "",
    }


def camera_component(node_id, projection, clear_flags, visibility, priority,
                     fov=45.0, ortho_height=320.0, color=(24, 34, 58, 255)):
    return {
        "__type__": "cc.Camera",
        "_name": "", "_objFlags": 0, "__editorExtras__": {},
        "node": {"__id__": node_id}, "_enabled": True, "__prefab": None,
        "_projection": projection,
        "_priority": priority,
        "_fov": fov,
        "_fovAxis": 0,
        "_orthoHeight": ortho_height,
        "_near": 1.0,
        "_far": 2000.0,
        "_color": {"__type__": "cc.Color", "r": color[0], "g": color[1], "b": color[2], "a": color[3]},
        "_depth": 1,
        "_stencil": 0,
        "_clearFlags": clear_flags,
        "_rect": {"__type__": "cc.Rect", "x": 0, "y": 0, "width": 1, "height": 1},
        "_aperture": 2,
        "_shutter": 2,
        "_iso": 0,
        "_screenScale": 1.0,
        "_visibility": visibility,
        "_targetTexture": None,
        "_postProcess": None,
        "_usePostProcess": False,
        "_id": "",
    }


def build_scene(root_comp_uuid: str) -> str:
    """最小 3D 场景：Scene + 3D 透视相机 + UI Canvas(正交相机) + Root 挂点。"""
    objs = [None] * 21

    objs[0] = {
        "__type__": "cc.SceneAsset",
        "_name": "PetHome",
        "_objFlags": 0, "__editorExtras__": {}, "_native": "",
        "scene": {"__id__": 1},
    }

    # SceneGlobals（2..9）
    objs[2] = {
        "__type__": "cc.SceneGlobals",
        "ambient": {"__id__": 3}, "shadows": {"__id__": 4}, "_skybox": {"__id__": 5},
        "fog": {"__id__": 6}, "octree": {"__id__": 7}, "postSettings": {"__id__": 8},
        "lightMapInfo": {"__id__": 9},
    }
    objs[3] = {
        "__type__": "cc.AmbientInfo",
        "_skyColorHDR": {"__type__": "cc.Vec4", "x": 0.2, "y": 0.5, "z": 0.8, "w": 0.520833125},
        "_skyColor": {"__type__": "cc.Vec4", "x": 0.2, "y": 0.5, "z": 0.8, "w": 0.520833125},
        "_skyIllumHDR": 20000.0, "_skyIllum": 20000.0,
        "_groundAlbedoHDR": {"__type__": "cc.Vec4", "x": 0.2, "y": 0.2, "z": 0.2, "w": 1.0},
        "_groundAlbedo": {"__type__": "cc.Vec4", "x": 0.2, "y": 0.2, "z": 0.2, "w": 1.0},
        "_skyColorLDR": {"__type__": "cc.Vec4", "x": 0.452599, "y": 0.607679, "z": 0.755699, "w": 0.0},
        "_groundAlbedoLDR": {"__type__": "cc.Vec4", "x": 0.618555, "y": 0.578874, "z": 0.544804, "w": 0.0},
    }
    objs[4] = {
        "__type__": "cc.ShadowsInfo", "_enabled": False, "_type": 0,
        "_planeBias": 1.0, "_normalBias": 0.5, "_shadowDistance": 100.0,
        "_maxReceived": 4, "_quality": 2, "_blendFactor": 0.6,
    }
    objs[5] = {
        "__type__": "cc.SkyboxInfo", "_envLightingType": -1,
        "_envmapHDR": None, "_envmap": None, "_envmapLDR": None,
        "_diffuseMapHDR": None, "_diffuseMapLDR": None,
        "_enabled": False, "_useHDR": True, "_editableMaterial": None,
        "_reflectionHDR": None, "_reflectionLDR": None, "_rotationAngle": 0.0,
    }
    objs[6] = {
        "__type__": "cc.FogInfo", "_type": 0,
        "_fogColor": {"__type__": "cc.Color", "r": 200, "g": 200, "b": 200, "a": 255},
        "_enabled": False, "_fogDensity": 0.3, "_fogStart": 0.5, "_fogEnd": 300.0,
        "_fogAtten": 5.0, "_fogTop": 1.5, "_fogRange": 1.2, "_accurate": False,
    }
    objs[7] = {
        "__type__": "cc.OctreeInfo", "_enabled": False,
        "_minPos": {"__type__": "cc.Vec3", "x": -1024.0, "y": -1024.0, "z": -1024.0},
        "_maxPos": {"__type__": "cc.Vec3", "x": 1024.0, "y": 1024.0, "z": 1024.0},
        "_depth": 8,
    }
    objs[8] = {"__type__": "cc.PostSettingsInfo", "_toneMappingType": 0}
    objs[9] = {"__type__": "cc.LightMapInfo", "_supported": False}

    # Scene（1）
    objs[1] = {
        "__type__": "cc.Scene",
        "_name": "PetHome",
        "_objFlags": 0, "__editorExtras__": {},
        "_parent": None,
        "_children": [{"__id__": 10}, {"__id__": 14}, {"__id__": 16}],
        "_active": True, "_components": [], "_prefab": None,
        "_lpos": {"__type__": "cc.Vec3", "x": 0, "y": 0, "z": 0},
        "_lrot": {"__type__": "cc.Quat", "x": 0, "y": 0, "z": 0, "w": 1},
        "_lscale": {"__type__": "cc.Vec3", "x": 1, "y": 1, "z": 1},
        "_mobility": 0, "_layer": LAYER_DEFAULT,
        "_euler": {"__type__": "cc.Vec3", "x": 0, "y": 0, "z": 0},
        "autoReleaseAssets": False,
        "_globals": {"__id__": 2},
        "_id": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
    }

    # Canvas（10..13，UI_2D 层）
    # Canvas._children 必须显式包含 Root（18）：CC 反序列化以父节点 _children 为准
    objs[10] = node("Canvas", [18], [11, 12, 13], layer=LAYER_UI_2D)
    objs[10]["_parent"] = {"__id__": 1}
    objs[11] = uitransform(10, 960, 640)
    objs[12] = {
        "__type__": "cc.Canvas",
        "_name": "", "_objFlags": 0, "__editorExtras__": {},
        "node": {"__id__": 10}, "_enabled": True, "__prefab": None,
        "_cameraComponent": {"__id__": 15},
        "_alignCanvasWithScreen": True,
        "_id": "canvas-comp",
    }
    objs[13] = {
        "__type__": "cc.Widget",
        "_name": "", "_objFlags": 0, "__editorExtras__": {},
        "node": {"__id__": 10}, "_enabled": True, "__prefab": None,
        "_alignFlags": 45, "_target": None,
        "_left": 0.0, "_right": 0.0, "_top": 0.0, "_bottom": 0.0,
        "_horizontalCenter": 0.0, "_verticalCenter": 0.0,
        "_isAbsLeft": True, "_isAbsRight": True, "_isAbsTop": True, "_isAbsBottom": True,
        "_isAbsHorizontalCenter": True, "_isAbsVerticalCenter": True,
        "_originalWidth": 0.0, "_originalHeight": 0.0,
        "_alignMode": 2, "_lockFlags": 0,
        "_id": "canvas-widget",
    }

    # UI 相机（14..15，正交 + DEPTH_ONLY + UI_2D 可见，priority 高于 3D 相机）
    objs[14] = node("UICamera", [], [15], layer=LAYER_DEFAULT, pos=(0, 0, 1000))
    objs[14]["_parent"] = {"__id__": 1}
    objs[15] = camera_component(14, PROJ_ORTHO, CLEAR_DEPTH_ONLY, LAYER_UI_2D,
                                LAYER_DEFAULT, ortho_height=320.0)

    # 3D 主相机（16..17，透视 + SOLID_COLOR + DEFAULT 可见）
    objs[16] = node("Main3DCamera", [], [17], layer=LAYER_DEFAULT,
                    pos=(0, 2.6, 7.2), euler=(-14, 0, 0))
    objs[16]["_parent"] = {"__id__": 1}
    objs[17] = camera_component(16, PROJ_PERSPECTIVE, CLEAR_SOLID_COLOR, LAYER_DEFAULT, 0,
                                fov=45.0, color=(26, 40, 66, 255))

    # Root（18..20，Canvas 子节点，PetGameRoot 挂点；全部内容脚本程序化构建）
    objs[18] = node("Root", [], [19, 20], layer=LAYER_UI_2D)
    objs[18]["_parent"] = {"__id__": 10}
    objs[19] = uitransform(18, 960, 640)
    objs[20] = {
        "__type__": root_comp_uuid,
        "_name": "", "_objFlags": 0, "__editorExtras__": {},
        "node": {"__id__": 18}, "_enabled": True, "__prefab": None,
        "_id": "petgameroot-comp",
    }

    return json.dumps(objs, ensure_ascii=False, indent=2) + "\n"


def script_meta(uuid: str) -> str:
    return json.dumps({
        "ver": "1.2.0", "importer": "typescript", "imported": True,
        "uuid": uuid, "files": [], "subMetas": {}, "userData": {},
    }, ensure_ascii=False, indent=2) + "\n"


def scene_meta(uuid: str) -> str:
    return json.dumps({
        "ver": "1.1.50", "importer": "scene", "imported": True,
        "uuid": uuid, "files": [".json"], "subMetas": {}, "userData": {},
    }, ensure_ascii=False, indent=2) + "\n"


def folder_meta() -> str:
    return json.dumps({
        "ver": "1.2.0", "importer": "directory", "imported": True,
        "uuid": "00000000-0000-0000-0000-000000000000",
        "files": [], "subMetas": {}, "userData": {},
    }, ensure_ascii=False, indent=2) + "\n"


def main() -> None:
    root_comp = compress(IDS['root_script'])

    io.open(os.path.join(HERE, 'assets', 'scenes', 'PetHome.scene.meta'), 'w', encoding='utf-8',
            newline='\n').write(scene_meta(IDS['scene']))
    io.open(os.path.join(HERE, 'assets', 'scenes', 'PetHome.scene'), 'w', encoding='utf-8',
            newline='\n').write(build_scene(root_comp))

    for key, fname in [
        ('root_script', 'scripts/PetGameRoot.ts'),
        ('bridge_script', 'scripts/PetGameBridge.ts'),
        ('anim_script', 'scripts/PetAnimations.ts'),
    ]:
        path = os.path.join(HERE, 'assets', fname)
        io.open(path + '.meta', 'w', encoding='utf-8', newline='\n').write(script_meta(IDS[key]))

    for folder in ['assets', 'assets/scenes', 'assets/scripts']:
        io.open(os.path.join(HERE, folder, '.meta'), 'w', encoding='utf-8', newline='\n') \
            .write(folder_meta())

    print('PetGameRoot __type__ =', root_comp)
    print('3D scene generated')


if __name__ == '__main__':
    main()
