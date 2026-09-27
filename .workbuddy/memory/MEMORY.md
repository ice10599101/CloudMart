# CloudMart 宠物模块 — 长期笔记

> 本项目所有微服务和docker服务全部完整运行在远程服务器，开发过程中需要测试直接连接远程服务器即可，本机不运行任何服务，本机只负责开发和更新代码。

## 方向变更：猫退役 → 五只水果宠物（2026-09-28）
- 用户否决奶灰猫，猫模型已全量删除（`assets/resources/models/cat/`、`textures/pet-cat-alive.png`、
  `design/model/` 全部迭代源文件；git 历史可找回）。旧猫流水线 `tools/asset-pipeline/` 也已删除。
- **五果定稿：西瓜 / 火龙果 / 橘子 / 草莓 / 蓝莓**（葡萄落选、火龙果顶替）。
- **五果全部进场**（2026-09-28）：草莓（build_strawberry.py 打样板）+ 橘子/西瓜/蓝莓/火龙果
  （build_fruits.py 批量）。家族规范：旋转曲面身体 + 贴图花纹（橘噪点/西瓜条纹/蓝莓果粉/火龙果品红）+
  3D 大眼高光 + ω 贴面猫嘴 + 腮红 + 软萌帽/鳍叶（零尖角）+ 4 骨绑定 + Idle/Happy/Blink。
  `PetGameRoot` 为五果加载器：FRUIT_SPECS 规格表 + SPECIES_SLOT 槽位映射
  （CAT→草莓/DOG→橘/RABBIT→西瓜/FOX→蓝莓/PANDA→火龙果，枚举迁移后直查水果码）+ `?species=` 预览。
- **species 枚举已迁移水果码**（V28 删除式迁移：旧物种宠物行 DELETE + 枚举一步收窄 +
  旧物种限定皮肤删除——开发期无用户数据，不做值迁移；Java 枚举/默认色表/9 处测试夹具同步；
  mall-pet 174 测试全绿；**V28 待服务重启落库**）。UI 侧 PetSpecies 联合类型/领养选项/emoji 表已同步，
  旧动物码在 emoji/果色映射里保留过渡兼容，V28 落库后自动走水果码直查。
  V28 曾漏网两处（2026-09-28 已修）：`CreatePetRequest` @Pattern 白名单、`AdoptWizard` 硬编码
  默认种类 'CAT'（已改 `SPECIES_OPTIONS[0].value` 跟随选项表）；createPet color fallback 已对齐
  defaultColorFor。**枚举收窄迁移必查：后端 @Pattern/@Schema 校验白名单 + 前端硬编码默认值。**
- **V29 全模块清零**：TRUNCATE 54 张用户/运行时数据表，保留 13 张配置/目录表（10 张 *_config +
  pet_achievement/pet_collection_entry 定义目录 + pet_config_version 版本快照）；comm 三方校验
  零漏网零误清；**新增配置表必须同步 V29 保留名单**。V28/V29 均待服务重启落库。
- **骨骼动画轴系**：pose 骨骼局部 Y = 沿骨轴（竖直），自转/扭摆绕局部 Y（rot=(0,deg,0)）；
  绕局部 X/Z 是翻跟头——五果 Happy 个性化（roll/jelly/hops/shimmy）已按此实现。
- **已知预存问题**：CloudMart-ui 的 vitest 单跑 pet.test.ts 报 mock 运行时缺失（git stash 实证与
  水果化改动无关）；待补 umi 兼容的 vitest alias/环境配置。
- **已知旋钮**：pet-toon 暖黄主光会把中等饱和度洗成 pastel（橘子贴图已压到 F26800 仍偏金黄）——
  要更准的果色需给 FRUIT_SPECS 加 per-fruit toon 覆写（furCtrl 高光/主光色）；五果统一粉彩感
  是屋里光感的家族特征，可整体保留。
- **顶饰规范**：帽/蒂/芽类顶饰必须自带穹顶盖住身体收口极点（旋转曲面顶极是尖点，直接架饰件
  必"分离+露尖"）；r4 定稿：西瓜 0.80 高圆肚、橘子穹蒂帽贴头。
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
