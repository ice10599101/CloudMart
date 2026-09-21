# pet-game — CloudMart 社区宠物 Cocos 3D 场景

Cocos Creator **4.0.0**（`4.0.0-alpha.34`）**3D** 工程。构建产物（web-mobile）由三端宿主嵌入：

| 宿主 | 嵌入方式 | 桥通道 |
|---|---|---|
| CloudMart-ui (Web) | iframe 加载 `/pet-game/index.html` | `window.postMessage` 双向 |
| cloudmart-app (Expo) | react-native-webview | `injectJavaScript` → `window.__petHostMessage`；回程 `ReactNativeWebView.postMessage` |
| cloudmart-mobile (Taro) | H5: iframe / 微信小程序: web-view | 同 Web；weapp 实时意图走 `wx.miniProgram.navigateTo` |

## 3D 场景说明

- **场景为 3D**：透视主相机（DEFAULT 层）+ UI 正交相机（UI_2D 层，DEPTH_ONLY）；
- **宠物模型零外部资产**：由 `PetModelBuilder` / `PetCatBuilder` 用手写连续曲面
  （`PetMeshFactory` 的 lathe 旋转体 / sweep 扫掠管 / 椭球 / 球面片）程序化生成，
  材质统一走工程内自定义 effect `pet-toon`；
- **场景文件最小化**：`assets/scenes/PetHome.scene` 仅含两个相机 + Canvas + Root 挂点，
  由 `tools/gen_assets.py` 生成；全部内容在 `PetGameRoot.start()` 程序化构建。
  若编辑器小版本反序列化失败：新建 3D 场景 `PetHome`，在 Canvas 下建 `Root` 节点挂
  `PetGameRoot` 组件，并手动添加正交 UI 相机（可见层 UI_2D、ClearFlag DEPTH_ONLY）
  与透视主相机（可见层 DEFAULT、ClearFlag SOLID_COLOR）即可。

## 构建（cocos-cli，headless 无需编辑器）

工具链已就位：`cocos-cli/`（含 `packages/engine`，**勿修改**）；便携 Node 22 在
`pet-game/.tools/node22/`（gl 原生模块仅提供到 ABI 127/Node 22 的预编译）。

```bash
NODE22="D:/Ide/IdeaProjects/CloudMart/pet-game/.tools/node22/node.exe"
CLI="D:/Ide/IdeaProjects/CloudMart/pet-game/cocos-cli/dist/cli.js"
"$NODE22" "$CLI" build \
  --project "D:/Ide/IdeaProjects/CloudMart/pet-game" \
  --platform web-mobile \
  --build-config "D:/Ide/IdeaProjects/CloudMart/pet-game/build-config.json" \
  --no-interactive
```

产物直接输出到 `CloudMart-ui/public/pet-game/`（`build-config.json` 指定
buildPath/outputName），App/小程序通过同一 URL 访问该静态目录。

> 场景/脚本 UUID 由 `tools/gen_assets.py` 固定（场景按压缩 UUID 引用 PetGameRoot 组件）；
> 改动脚本文件名需同步重跑生成器。

## 自定义材质加载（重要，改 effect 前必读）

1. **着色器必须放在 `assets/resources/effects/`**：`EffectAsset.get(name)` 只能查到
   **已加载**的资产，放在普通目录下（原 `assets/effects/`）不会被自动加载，材质会静默
   退化成引擎内置材质 —— 表现为"改了 effect 完全没反应、描边/绒光/烘焙腮红全部消失"。
   `PetGameRoot.start()` 用 `resources.load('effects/pet-toon', EffectAsset, cb)` 加载，
   加载完成（或失败）后才构建场景（`buildScene`），并由 `PetBuilderKit.setToonEffect()` 注入。
2. **技术索引要按"最终选中的 effect"判断**（`PetBuilderKit.attach`）：如果按"解析结果是否
   为空"判断，一旦出现"解析失败但兜底拿到 pet-toon"的组合，`technique` 索引会越界，
   `Material.initialize` 静默失败并退回内置材质。
3. **依赖透明混合的部件（脚底软影、房间光点/阳光片）走引擎内置材质**（`PartStyle.plain`）：
   本构建链上自定义 effect 的 `transparent` 技术在部分路径下会丢失材质属性。
4. **改了 effect 却不生效时**：删除 `temp/asset-db/effect/effect.bin` 再构建
   （该插件在 bin 存在且版本匹配时会跳过重编译）。

## 视觉验收（截图与机位）

```bash
# 1) 起静态服务器
python -m http.server 5199 --directory CloudMart-ui/public
# 2) 抓图（房间 3/4 机位 / 正面近景 / 左前 3/4 近景 / 互动动画帧）
node tools/shot.mjs --base "http://127.0.0.1:5199/pet-game/index.html?demo=1&accessory=none&species=CAT" \
     --shots room,front,q34,action-feed --outdir ../../shots/final
```

调试用的 URL 参数（仅用于验收，不影响宿主接入）：

| 参数 | 作用 |
|---|---|
| `demo=1` | 无宿主时展示演示宠物（可叠加 `species` / `color` / `accessory`） |
| `shot=room\|front\|q34` | 切换验收机位（默认 room：轻微俯视 3/4 视角） |
| `plain=1` | 去掉房间与 HUD、纯色背景：只让角色靠轮廓/比例/五官自证 |
| `probe=1` | 打印角色在画面中的像素高度与占比（构图验收） |
| `probeMat=1` | 逐个打印宠内部件的 pass 数、effect 名与主色（材质验收） |

## 已知构建缺陷（重要）

当前 cocos-cli 工具链产出的 web-mobile 构建存在两处**既有缺陷**（与业务代码无关，基线版本同样复现）：

1. **spine 打桩模块中断启动**：产物中的 spine asm.js 桩在初始化时抛出
   embind `BindingError: Cannot register public name '' twice`，异常沿
   `game.onPostInfrastructureInitDelegate` 中断 `cc.game.init`，场景永不加载（黑屏）。
   **已通过 `build-templates/web-mobile/index.html`（自定义构建模板）注入 SystemJS
   拦截补丁绕过**：本工程不使用 spine，导入 spine 运行时模块时返回空实现。
2. **内置 effect 缺少编译产物**：部分内置程序（splash/profiler/skybox 等）缺失，运行时
   报 `program: ... not found` 与 `The asset ... is invalid`；**本工程的自定义 effect
   （pet-toon）与其依赖的 legacy chunk 不在缺失范围内** —— 宠物与房间全部走 pet-toon，
   依赖透明混合的少量部件走 `builtin-unlit`（见上文"自定义材质加载"第 3 条，实测可用）。
   修复需要 Creator 编辑器对内置资源执行 Refresh/Reimport（headless 构建链不产出），
   工具链升级后重测。

Web 宿主（CloudMart-ui）**只运行 Cocos 通路**：`PetStage/index.tsx` 直接挂载
`CocosStage`（iframe + postMessage），历史上一条 Three.js 原生兜底舞台
（`PetStage/native/`）已整体移除。

## 通信契约（唯一协议，三端一致）

- 游戏 → 宿主：`ready` / `intent`（feed/play/clean/rest/openWork/openStudy/openBottle/openBattle/openChat/openAchievements）/ `petTapped`
- 宿主 → 游戏：`init` / `petState` / `actionResult` / `battleRounds` / `chatBubble`
- 详见 `assets/scripts/PetGameBridge.ts`；宿主侧实现在
  `CloudMart-ui/src/components/PetStage/bridge.ts`

## 目录

```
assets/
├── resources/effects/pet-toon.effect   # 自定义卡通材质（必须放 resources，才能被加载）
├── scenes/PetHome.scene        # 最小 3D 场景（tools/gen_assets.py 生成）
└── scripts/
    ├── PetGameBridge.ts        # 通信协议与环境探测（web/app/weapp）
    ├── PetGameRoot.ts          # 主组件：资源加载 + 3D 宠物 + 相机机位 + 2D UI 叠层
    ├── PetGameTheme.ts         # 设计令牌：物种色板 / HUD 配色 / 情绪
    ├── PetBuilderKit.ts        # 建模工具：材质工厂（pet-toon / 内置兜底）、软贴花、描边
    ├── PetMeshFactory.ts       # 手写网格原语：lathe / sweep / 椭球 / 球面片 / 轮廓插值
    ├── PetCatBuilder.ts        # 猫的造型（v5 造型语言：轮廓优先、五官贴合曲面）
    ├── PetModelBuilder.ts      # 造型分派 + 其余物种（沿用 v4 实现，待按同一语言迁移）
    ├── PetRoomBuilder.ts       # 房间场景（地板/墙/窗/家具/地毯/光点）
    ├── PetAnimations.ts        # 3D tween 动画工厂（待机/互动/升级/受击）
    ├── PetEffects.ts / PetHud.ts  # 2D 粒子与 HUD
    └── ...
tools/
├── gen_assets.py               # 生成 .meta 与最小场景（含 resources 目录 meta）
└── shot.mjs                    # 验收截图工具（多机位 + 动画帧 + 控制台/探针采集）
```
