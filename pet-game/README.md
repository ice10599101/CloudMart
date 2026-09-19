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
