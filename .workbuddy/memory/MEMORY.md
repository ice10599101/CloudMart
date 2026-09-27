# CloudMart 宠物模块 — 长期笔记

> 详细过程见服务端注入的工作记忆与 `pet-game/tools/` 内脚本注释；当日明细见同目录日期日志。

## 方向变更：猫退役 → 五只水果宠物（2026-09-28）
- 用户否决奶灰猫，猫模型已全量删除（`assets/resources/models/cat/`、`textures/pet-cat-alive.png`、
  `design/model/` 全部迭代源文件；git 历史可找回）。旧猫流水线 `tools/asset-pipeline/` 也已删除。
- **五果定稿：西瓜 / 火龙果 / 橘子 / 草莓 / 蓝莓**（葡萄落选、火龙果顶替）。
- **草莓打样已完成并进场**（2026-09-28，r2 按反馈改版）：`tools/fruit-pipeline/build_strawberry.py` 一条龙
  （建模 → 纯 Python PNG 贴图 → 4 骨绑定 → Idle/Happy/Blink → GLB → Cycles 预览）；
  r2 定稿特征：圆肚水滴轮廓 + **水滴形奶油籽点**（尖朝上）+ **软萌圆帽**（圆穹+蓬蓬球+短梗圆珠）+
  **ω 猫嘴**（贴面生成：每点按自身高度取轮廓半径+外凸，poly 曲线 bevel 成管再转 MESH）+ 大眼高光 + 腮红；
  GLB 在 `assets/resources/models/fruit/strawberry/`；PetGameRoot 加载链路已恢复（scale 1.5）。
- 5 个宠物坑位不变：mall-pet `species ENUM('CAT','DOG','RABBIT','FOX','PANDA')`，
  五果定稿后需一次枚举迁移（后端变更，先经用户确认）；`PetGameBridge.petState.species`（string）契约不动。
- 新流水线关键经验：贴图必须纯 Python(zlib) 写 PNG 再 `images.load`（Blender 生成式 pixels 写大图会黑）；
  pixels 是线性色接口；`from_pydata` 绕向反了 pet-toon 会渲成暗红（Cycles 看不出）；
  glTF scene 名决定主资源子路径（`目录/文件名/文件名` 三段式）；
  **贴面部件（嘴线/腮红）的半径必须按自身高度取轮廓值**，固定深度会被体表鼓形吞掉或悬空。
- 剩余四只水果按草莓流水线换参数复制；体型沿用现有标定（身高 ~1.4-1.5、地毯中央站位），
  机位/光照/接触阴影结构全复用；pet-toon 暗部色需按各果主色调 shadeColor。
- 保留未动：PetGameBridge 契约、PetRoomBuilder / PetEffects / pet-toon、
  PetGameTheme 的 SPECIES_* 调色数据与 resolvePalette（已无调用方，随水果加载器重写）。
- 构建链不变：`env -u NODE_OPTIONS` + node22 + cocos-cli build web-mobile，产物 → CloudMart-ui/public/pet-game；
  实拍用 `tools/shot.mjs`（python -m http.server 5199 --directory CloudMart-ui/public）。
