# pet-game — CloudMart 社区宠物 Cocos 3D 场景

Cocos Creator **4.0.0**（`4.0.0-alpha.34`）**3D** 工程。构建产物（web-mobile）由三端宿主嵌入：

| 宿主 | 嵌入方式 | 桥通道 |
|---|---|---|
| CloudMart-ui (Web) | iframe 加载 `/pet-game/index.html` | `window.postMessage` 双向 |
| cloudmart-app (Expo) | react-native-webview | `injectJavaScript` → `window.__petHostMessage`；回程 `ReactNativeWebView.postMessage` |
| cloudmart-mobile (Taro) | H5: iframe / 微信小程序: web-view | 同 Web；weapp 实时意图走 `wx.miniProgram.navigateTo` |

## 3D 场景说明

- **场景为 3D**：透视主相机（DEFAULT 层）+ UI 正交相机（UI_2D 层，DEPTH_ONLY）；
- **宠物模型零外部资产**：`PetGameRoot.buildPet3D()` 用引擎原语（capsule 身体 / sphere
  头与眼 / cone 耳朵）按种类配色拼装，平行光 + 地面 + 装饰摆件；二期替换 GLB/骨骼模型
  只改 `buildPet3D` 与 `SPECIES_COLORS`，桥与 UI 零改动；
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

## 已知构建缺陷（重要）

当前 cocos-cli 工具链产出的 web-mobile 构建存在两处**既有缺陷**（与业务代码无关，基线版本同样复现）：

1. **spine 打桩模块中断启动**：产物中的 spine asm.js 桩在初始化时抛出
   embind `BindingError: Cannot register public name '' twice`，异常沿
   `game.onPostInfrastructureInitDelegate` 中断 `cc.game.init`，场景永不加载（黑屏）。
   **已通过 `build-templates/web-mobile/index.html`（自定义构建模板）注入 SystemJS
   拦截补丁绕过**：本工程不使用 spine，导入 spine 运行时模块时返回空实现。
2. **内置 effect 缺少编译产物**：`src/effect.bin` 仅包含工程内自定义 effect（pet-toon），
   内置 unlit/splash/profiler/skybox 等程序缺失，运行时报
   `program: builtin-unlit|... not found` 与 `The asset ... is invalid`。
   修复需要 Creator 编辑器对内置资源执行 Refresh/Reimport（headless 构建链不产出），
   工具链升级后重测。

在缺陷 2 修复前，**Web 宿主默认使用 `CloudMart-ui/src/components/PetStage/native/`
（Three.js 原生舞台）**，`PetStage/index.tsx` 的 `STAGE_ENGINE` 常量控制通路切换；
缺陷修复后切回 `'cocos'` 即可复用本工程场景。

## 三端移植指引（原生舞台）

原生舞台与宿主解耦，桥协议（`PetGameBridge.ts`）三端不变。移植 `native/` 目录时：
- Web（CloudMart-ui）：已接入，零依赖差异（three 已在 package.json）；
- Expo（cloudmart-app）：用 `expo-gl` + `THREE.WebGLRenderer({ canvas: expo-gl view })`
  承载 `stageEngine.ts`（引擎不感知 DOM；HUD 需替换为 RN 组件）；
- Taro H5：可直接复用（同 Web）；微信小程序端 three 依赖 WebGL 适配层，暂维持 web-view
  加载 `/pet-game/` 的通路。

## 通信契约（唯一协议，三端一致）

- 游戏 → 宿主：`ready` / `intent`（feed/play/clean/rest/openWork/openStudy/openBottle/openBattle/openChat/openAchievements）/ `petTapped`
- 宿主 → 游戏：`init` / `petState` / `actionResult` / `battleRounds` / `chatBubble`
- 详见 `assets/scripts/PetGameBridge.ts`；宿主侧实现在
  `CloudMart-ui/src/components/PetStage/bridge.ts`

## 目录

```
assets/
├── scenes/PetHome.scene        # 最小 3D 场景（tools/gen_assets.py 生成）
└── scripts/
    ├── PetGameBridge.ts        # 通信协议与环境探测（web/app/weapp）
    ├── PetGameRoot.ts          # 主组件：3D 宠物 + 平行光/地面 + 2D UI 叠层
    └── PetAnimations.ts        # 3D tween 动画工厂（待机/互动/升级/受击）
```
