# CloudMart 宠物模块 — 长期笔记

> 详细过程见服务端注入的工作记忆与 `pet-game/tools/` 内脚本注释；当日明细见同目录日期日志。

## 方向变更：猫退役 → 五只水果宠物（2026-09-28 定向，待用户圈定五果）
- 用户否决奶灰猫，猫模型已全量删除（`assets/resources/models/cat/`、`textures/pet-cat-alive.png`、
  `design/model/` 全部迭代源文件；git 历史可找回）。`PetGameRoot.ts` 猫加载链路已摘除，
  场景暂为无角色；链路骨架（加载 → pet-toon 材质替换 → 剪辑挂载 → 状态驱动节奏）待水果进场原位恢复。
- 5 个宠物坑位不变：mall-pet `species ENUM('CAT','DOG','RABBIT','FOX','PANDA')`，
  五果定稿后需一次枚举迁移（后端变更，先经用户确认）；`PetGameBridge.petState.species`（string）契约不动。
- 五果主推：草莓（元气撒娇）/ 橘子（滚动吃货，剥皮=外套）/ 西瓜（躺平大师，条纹睡衣）/ 葡萄（果冻串）/ 蓝莓（小不点）；
  备选：菠萝（外硬内软）/ 樱桃（一茎双果双人组）/ 火龙果（火焰鳞片戏精）。
- 设计钩子：五果主色（红/橙/绿/紫/蓝）对齐状态带五项语义色（#E06B5C/#E79A3D/#74B183 + 紫蓝补位），
  可做"守护果"包装层玩法；五官规格 + 贴身短肢统一家族感；干枯玫瑰围巾可转成家族信物配件。
- 生产优势：水果为闭合简单曲面（猫是 970 碎片网格），绑定/权重稳定；动态靠滚动、弹跳、
  squash & stretch、果冻次级抖动 + 叶/蒂/皮小骨骼；体型沿用现有标定（身高 ~1.5、地毯中央站位），
  机位/光照/接触阴影结构全复用。
- 保留未动：PetGameBridge 契约、PetRoomBuilder / PetEffects / pet-toon、tools/asset-pipeline/ 脚本（去留待定）、
  PetGameTheme 的 SPECIES_* 调色数据与 resolvePalette（已无调用方，随水果加载器重写）。
- 构建链不变：`env -u NODE_OPTIONS` + node22 + cocos-cli build web-mobile，产物 → CloudMart-ui/public/pet-game。
