# CloudMart 社区宠物模块 — 长期项目笔记

> 更新：2026-09-25。详细过程见同目录 `2026-09-22.md`（工具用法）、`2026-09-24.md`（白猫定案 + 构建链排查）。

## 一、项目事实（已核实，勿凭猜测）
- 宠物模块 = `mall-pet`（Java 后端）+ `pet-game`（Cocos Creator **4.0.0** / `4.0.0-alpha.34`，3D）。
- **无编辑器，全 headless**：
  - Node 22 便携版 `pet-game/.tools/node22/node.exe`（已 gitignore）
  - CLI `pet-game/cocos-cli/dist/cli.js`（vendored，含 `packages/engine`，README 标注**勿修改**）
  - 构建：`"$NODE22" "$CLI" build --project <pet-game> --platform web-mobile --build-config build-config.json --no-interactive`
  - 产物 → `CloudMart-ui/public/pet-game/`
- 三端宿主嵌入，走 postMessage：浏览器 iframe / Expo webview / Taro web-view。
- 视觉验收：`pet-game/tools/shot.mjs`；URL 参数 `demo=1` / `shot=room|front|q34` / `plain=1` / `probe=1` / `probeMat=1`。
- MCP：`cocos start-mcp-server`。关键工具：`assets-import-asset`、`assets-refresh`、
  `assets-query-asset-info`（含子资源）、`assets-material-query/-save`、`scene-create-node-by-asset`、
  `scene-add-component`、`scene-set-component-property`、`scene-save`、`builder-build`。
- `tools/gen_assets.py` 固定场景与 3 个脚本的 .meta UUID（内含压缩 UUID 算法）。
  **只改脚本内容不用重跑；改脚本文件名必须重跑。**

## 二、基础设施底线（2026-09-25 修订）
用户要求重做整个宠物模块设计（宠物本体 / 家园 / 全部 UI），但以下属基础设施，
删了要重建且会打断三端：
1. **通信契约** `PetGameBridge.ts` ↔ `CloudMart-ui/src/components/PetStage/bridge.ts`
   （`ready`/`intent`/`petState`/`actionResult`/`battleRounds`/`chatBubble`）→ **契约冻结**。
2. **spine 缺陷根修（替代旧 index.html v1 桩）**：
   `build-templates/web-mobile/cocos-js/spine.asm-t8kGod18.js` 假分块（构建模板覆盖产物）。
   引擎基础 init 无条件加载 spine asm 且被 cc.game.init await，asm 实例化抛 embind/BindingError
   → programLib 全空 → 黑屏。假分块 = Proxy 哑库（ownKeys 供全部 spine 类名、
   then/catch/finally 必须返回 undefined、SpineWasmUtil 空实现）。重构建自动生效。
3. **effect 加载规则**：自定义 effect 必须放 `assets/resources/effects/`，否则静默退化成内置材质；
   连带 `gen_assets.py` 的 UUID 方案与验收链路。
4. **构建验收环境事实**：构建产物在 headless swiftshader 下**从不渲染**（9/19 原始构建同证），
   不是回归；headless 验收看 **cocos preview**（动态服务）+ `_probe/shot_edge.mjs`；
   用户真机浏览器才是构建产物的验收环境。

## 三、已定的任务与范围
- 我**接管宠物模块设计**：宠物本体 + 所有功能 UI。宠物**全 3D**，平台**锁定 Cocos 4**。
- 第一只宠物 = 我做的**奶灰猫**（琥珀金眼 + 干枯玫瑰藕粉针织围巾 + 浅金铃铛）。
- **范围（2026-09-23 用户修正）**：五项数值（生命/饱食/心情/精力/清洁）**全部上屏**；
  宠物模块**所有功能**都要出 UI；**Web 端 + 移动三端统一**（同一套令牌，只重排布局，断点 768px）。
- **交付节奏：设计稿先行** —— 先出可评审的画布设计稿，用户过审后再写 TS 代码。
- **旧代码只重做设计层**，基础设施保留。

## 四、设计现状（v2，2026-09-23 晚）
**旧 UI 已从代码真删**：`PetHud.ts` 删除；`PetGameTheme.ts` 的
`HUD` / `STATE_ROWS` / `ACTION_BUTTONS` / `NAV_BUTTONS` 删除（核实过只被 PetHud 用）；
`PetGameRoot.ts` 27 行 `this.hud*` 摘除，`buildUi()` → `buildOverlay()`。
**保留**：宠物本体配色（角色层）、`PetGameBridge` 契约、headless 构建链、effect 规则。
删完构建仍通过，场景 = 房间 + 猫 + 粒子 + 热区。

**v2 架构六条**（与旧版的结构差异，不是重排）：
① 删常驻动作栏 → 动作由状态驱动，需求卡最多 2 张，全健康时动作区消失（UI 会「消失」）
② 删 8 个平铺胶囊 → 收进右上角一个「生活」入口，分 4 组：照料 / 成长 / 玩耍 / 一起
③ 五项数值压成**顶部通栏状态带**，用 **10 格刻度计**代替进度条
④ 取消常驻气泡 → 右侧「它的话」列（带行动=需求卡 / 纯说话=台词卡，即 `chatBubble`）
⑤ 底部刻意留空，下半屏全给地板与影子
⑥ 跨端 = 组件不变只换排布（状态带 + 它的话两个组件，Web 横排 / 移动纵排）

**视觉**：暖木纸感织物卡片（纸底 `#FBF6EE` / 白 / 内嵌 `#F1E8DA`），
强调色取自宠物本体 —— 琥珀金 `#D9932F`（虹膜）+ 干枯玫瑰 `#C4959A`（围巾）。
五项语义色：生命 `#E06B5C` / 饱食 `#E79A3D` / 心情 `#D9799C` / 精力 `#74B183` / 清洁 `#6BA3D4`；
<25% 转 danger `#D45E52`。24 个矢量图标（禁 emoji）。字阶 Noto Sans SC + Inter tabular-nums。
**画布 Ardot `728925154478829`**（A 设计系统 / D Web 家园 / E 移动家园）；
导出图 `pet-game/design/screens/`。

## 五、新宠物（奶灰猫重做）—— 已绑骨 + 已 K 动画（v7，2026-09-23 晚）
用户拍板 **A：还是奶灰猫**，用 Blender 绑骨骼 + K 动画 + 重做材质。

**产物**：`design/model/pet-cat-rigged.glb`（**2.33MB**，原 2.21MB）
校验：1 skin / **10 骨骼**（Root·Hips·Spine·Chest·Neck·Head·双耳各 2）/
**Idle 4.00s + Happy 1.67s**（各 30 通道）/ primitive 含 `JOINTS_0`+`WEIGHTS_0` /
三张贴图原样保留 / **`extensionsUsed: None`** / 面数 11,994 不变。
绑定工程：`design/model/_work/cat_rigged.blend`。形变验证图：`design/model/_check/`。

**Blender 事实（硬约束，别再踩）**
- `D:\Program Files\Blender Foundation\Blender 5.2\blender.exe` = **5.2.2 LTS**，`--background` 可用
- **glTF 导入后是 Z 轴向上**（旋转写在节点四元数上）→ 必须先 `transform_apply`，否则所有测量错位
- **Blender 5 起 `action.fcurves` 不存在**（改成 layer/strip/**channelbag** 槽位动作结构）
- Principled BSDF 的**节点名随界面语言变**，只能按 `node.type` 取，不能按名字取
- EEVEE 在 headless 不保证可用 → 预览渲染用 **Cycles CPU**

**实测几何（`blender_probe.py` 反推，不是按比例猜）**
身高 0.9503 · **颈线 z=0.388** · 耳根 z≈0.79 · **脸朝 -Y** ·
**双眼 (-0.088,-0.243,0.610) / (0.140,-0.242,0.582)**，眼距 0.229（占脸宽 36%）
> 眼睛定位法：反照率贴图里唯一的高饱和暖色 = 琥珀金虹膜 → 按 UV 反查顶点 → 按 X 聚类。
> **阈值必须收紧**（r>0.55/r-b>0.28）并加几何高度约束，否则**浅金铃铛**会被当成虹膜。

**绑定三定律（这一轮最贵的教训）**
1. **Blender 骨热权重在这种网格上必然失败** —— 实测 **970 个连通分量、最大岛只 359 顶点**。
   图生 3D 是碎片化拓扑，骨热需要连通性；又不能 remesh（会毁掉烘焙 UV 与贴图）。
2. **"整岛刚性绑定"是错的**：碎片化网格上它会让每块碎片各自一个权重，一动就互相拉开 ——
   看到的裂缝是这个，不是过渡不平滑。**正解：逐点权重 + 邻接平滑，且邻接按空间距离建**
   （不能按共享边，否则平滑跨不过碎片缝）。参数：`NEIGH_CELL 0.022 / NEIGH_RADIUS 0.032`。
3. **纯距离场分不开"相邻但不同部位"**（围巾就贴在颈线下）。必须加**实测解剖高度门控**
   （Head `above 0.360→0.455`、耳 `above 0.735→0.800`，带宽 0.07~0.10 不能窄）
   + 显式**空间锁**（胡须 `z>0.46 & y<-0.17` → Head）+ **区域生长**（细长条必然跨框，只锁一半会被拉长）。

**明确放弃（写在脚本注释里，不是忘了）**：尾巴不绑（紧贴人身、几何几乎相交，距离权重必串味；
要摆动只能从资产侧留真实间隙或改用测地距离）· 不做眨眼（眼睛烘焙在贴图里，无独立眼皮几何，
要先反查眼球 3D 位再补眼皮）· 不做行走循环（碎片化网格做不了可靠四肢形变）。

**新增脚本**（`pet-game/tools/asset-pipeline/`）：`blender_probe.py` · `rig_cat.py` ·
`animate_cat.py` · `render_preview.py`

**生动脸定稿（2026-09-25，替代 GLB 拼接路线）**：
- `bake_alive.py` 的干净产物 → `make_alive_texture.py` 自检（非白板/双眼深色/粉鼻）→
  安装为 `assets/resources/textures/pet-cat-alive.png`（meta 构建时自动生成）。
- `PetGameRoot` 材质前置加载 `textures/pet-cat-alive/texture`，`configurePetMaterials`
  优先覆盖 mainTexture，失败回退 GLB 内嵌 albedo。**GLB 保持原版不拼接**（prVw 备份在
  `_probe/backups/` 弃用）。preview 探针证实三网格 albedoTex=pet-cat-alive ✓。
- preview 引擎形态两坑：`AmbientInfo.skyColorHDR` 只读（buildLighting 已加可写探测守卫）；
  **TS 改动必须重启 preview** 才生效。preview 下蒙皮网格不渲染（用户浏览器正常，?rawmat=1 可诊断）。

## 五之二、3D「生动化」（v6，已落地）
**根因不是造型精度，是全场没有「光的方向叙事」**：场景零光源、阴影关闭，
房间与角色共用同一个自研 `pet-toon`，而它的三点光是**固定常数** → 物体之间没有光的关系。
关键事实：`lightDir.y = 0.74` 时地板与正对镜头的墙亮度只差 3%（半兰伯特把一切压到 0.5~1.0），
明度带太窄 = 塑料感。已改动：主光改**来自窗户**、补光改**暖色地板反弹**、
暗部色冷紫灰→暖灰棕、房间调色板按**亮度阶梯**重排、补**接触阴影 + 墙脚 AO**、
地面"阳光"由硬边方板换**柔边光斑**、机位后退 12%。

**踩坑必记**：
- 地毯是逐层圆盘（顶面 y≈0.045~0.07）→ 放 y=0.009 的贴花会被**整个盖住**，一个都看不见。
- `kit.decal` 是**唯一**的柔边贴花手段（径向 alpha 写在 uv.x，由 `decalCtrl` 消费），
  只能贴平面；凸面要用 `kit.patch`。
- 改房间摆位时必须同步改 `buildContactShadows()` 里的坐标（两处硬编码对应）。
- 改 `pet-toon` 的参数要**同时改两个 technique**（opaque 与 transparent 各写一份属性表，
  引擎在 transparent 下不解析 YAML 锚点引用）。

## 六、资产位置约定
- **设计稿底图流程（已打通，无旧 UI 残留）**：
  `cocos build` → `python -m http.server 5199 --directory CloudMart-ui/public`
  → `node tools/shot.mjs --base ".../pet-game/index.html?demo=1" --shots room
  --width <设计宽> --height <设计高> --outdir ../../shots/v5`
  按设计尺寸 1:1 截图（dpr2），宽高比与画框一致 → 导入画布用 `scaleMode: FILL` 零变形。
  竖屏自动走 `portrait` 机位（见 `PetGameRoot.CAMERA_SHOT`）。
- 底图调色（Ardot fill.filters）：exposure −8 / contrast +6 / saturation −5 /
  temperature +6 / highlights −16 / shadows +4，把过曝的粉白房间拉回暖木调。
- 运行版模型：`pet-game/design/model/pet-cat-runtime.glb`
  （**暂不要放进 `assets/`** —— 会触发 asset-db 导入与构建，等绑骨方案落地再决定是否移到
  `assets/resources/models/`）
- 设计源：`pet-game/design/concept/`；导出图：`pet-game/design/screens/`
- 流水线脚本：`pet-game/tools/asset-pipeline/`；底图处理：`pet-game/tools/design/`
  （`band_fix2.py` 地板色剖面填充 / `crop_png.py` 纯 stdlib 裁切）
- 大体积中间产物（高模 45 MB / 原始 FBX 60 MB / 4K 贴图 85 MB / 已废弃的企鹅 160 MB）
  **不进 git**，放仓库外 `D:\Ide\IdeaProjects\CloudMart-3d-source\`

## 七、工程规范
项目根目录 `AGENTS.md` 是严格规范