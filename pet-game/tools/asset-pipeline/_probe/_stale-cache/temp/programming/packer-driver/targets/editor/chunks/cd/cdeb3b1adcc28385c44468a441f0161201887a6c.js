System.register(["__unresolved_0", "cc", "__unresolved_1", "__unresolved_2", "__unresolved_3", "__unresolved_4"], function (_export, _context) {
  "use strict";

  var _reporterNs, _cclegacy, __checkObsolete__, __checkObsoleteInNamespace__, _decorator, AnimationClip, Camera, Color, Component, DirectionalLight, director, EffectAsset, instantiate, Layers, Material, Node, Prefab, SkeletalAnimation, Texture2D, UITransform, Vec3, Vec4, resources, screen, PetGameBridge, PetBuilderKit, PetEffects, buildRoom, _dec, _class, _crd, ccclass, PET_MODEL_PATH, PET_MODEL_DIR, PET_ALIVE_TEX, PET_CLIPS, PET_MODEL_SCALE, PET_HEIGHT, PET_POS, CAMERA_SHOT, PLAIN_BG, HEAD_OFFSET, PetGameRoot;

  function _defineProperty(e, r, t) { return (r = _toPropertyKey(r)) in e ? Object.defineProperty(e, r, { value: t, enumerable: !0, configurable: !0, writable: !0 }) : e[r] = t, e; }

  function _toPropertyKey(t) { var i = _toPrimitive(t, "string"); return "symbol" == typeof i ? i : i + ""; }

  function _toPrimitive(t, r) { if ("object" != typeof t || !t) return t; var e = t[Symbol.toPrimitive]; if (void 0 !== e) { var i = e.call(t, r || "default"); if ("object" != typeof i) return i; throw new TypeError("@@toPrimitive must return a primitive value."); } return ("string" === r ? String : Number)(t); }

  function _reportPossibleCrUseOfPetGameBridge(extras) {
    _reporterNs.report("PetGameBridge", "./PetGameBridge", _context.meta, extras);
  }

  function _reportPossibleCrUseOfPetBuilderKit(extras) {
    _reporterNs.report("PetBuilderKit", "./PetBuilderKit", _context.meta, extras);
  }

  function _reportPossibleCrUseOfPetEffects(extras) {
    _reporterNs.report("PetEffects", "./PetEffects", _context.meta, extras);
  }

  function _reportPossibleCrUseOfbuildRoom(extras) {
    _reporterNs.report("buildRoom", "./PetRoomBuilder", _context.meta, extras);
  }

  return {
    setters: [function (_unresolved_) {
      _reporterNs = _unresolved_;
    }, function (_cc) {
      _cclegacy = _cc.cclegacy;
      __checkObsolete__ = _cc.__checkObsolete__;
      __checkObsoleteInNamespace__ = _cc.__checkObsoleteInNamespace__;
      _decorator = _cc._decorator;
      AnimationClip = _cc.AnimationClip;
      Camera = _cc.Camera;
      Color = _cc.Color;
      Component = _cc.Component;
      DirectionalLight = _cc.DirectionalLight;
      director = _cc.director;
      EffectAsset = _cc.EffectAsset;
      instantiate = _cc.instantiate;
      Layers = _cc.Layers;
      Material = _cc.Material;
      Node = _cc.Node;
      Prefab = _cc.Prefab;
      SkeletalAnimation = _cc.SkeletalAnimation;
      Texture2D = _cc.Texture2D;
      UITransform = _cc.UITransform;
      Vec3 = _cc.Vec3;
      Vec4 = _cc.Vec4;
      resources = _cc.resources;
      screen = _cc.screen;
    }, function (_unresolved_2) {
      PetGameBridge = _unresolved_2.PetGameBridge;
    }, function (_unresolved_3) {
      PetBuilderKit = _unresolved_3.PetBuilderKit;
    }, function (_unresolved_4) {
      PetEffects = _unresolved_4.PetEffects;
    }, function (_unresolved_5) {
      buildRoom = _unresolved_5.buildRoom;
    }],
    execute: function () {
      _crd = true;

      _cclegacy._RF.push({}, "de28bIpZkFFg6SBhvUEChP3", "PetGameRoot", undefined);

      __checkObsolete__(['_decorator', 'Animation', 'AnimationClip', 'Camera', 'Color', 'Component', 'DirectionalLight', 'director', 'EffectAsset', 'instantiate', 'Layers', 'Material', 'Node', 'Prefab', 'SkeletalAnimation', 'Texture2D', 'UITransform', 'Vec3', 'Vec4', 'resources', 'screen']);

      ({
        ccclass
      } = _decorator);
      /**
       * 家园场景主组件（视觉重构 v7）。
       *
       * 结构：
       *  - 3D 世界：房间（PetRoomBuilder，统一走 pet-toon）+ 宠物（外部绑定模型，见下）
       *  - 相机机位（横屏 room / 竖屏 portrait 自动选择）
       *  - 房间氛围动画（光点上升 / 灯泡呼吸 / 光斑呼吸 / 玩具轻摆）
       *  - 2D 覆盖层：特效粒子 + 世界坐标投影
       *  - 通信桥（契约冻结，见 PetGameBridge）
       *
       * 宠物：**不再是程序化造型**。旧的 PetCatBuilder / PetModelBuilder / PetAnimations 已整体删除，
       * 改为加载 Blender 绑好骨的 GLB（`assets/resources/models/cat/pet-cat-rigged.glb`）：
       *  - 3 个网格（身体 + 两片眼皮）/ 15 根变形骨（含 4 节尾骨与 2 根眼皮骨）
       *  - 三条动画剪辑：Idle 4.00s（呼吸+头漂移+耳抽动+尾巴摆动）、
       *    Happy 1.67s（低头侧蹭+耳后压+尾巴翘摆）、Blink 0.25s（毛色眼皮扫下闭合）
       *  - 绑骨细节见 `tools/asset-pipeline/rig_cat_v2.py` 的注释（骨热在这种碎片化网格上必然失败，
       *    权重是自己算的：点到骨段距离场 × 解剖高度门控 × 空间邻接平滑；
       *    眼球位置来自 probe_eye_pick.py 的射线拾取视觉核对）
       *
       * 职责边界（不变）：只做展示与动画，数值全部来自宿主下发的 PetDisplayState，
       * 用户操作只回传 intent。契约一个字段都没改。
       */

      /**
       * 宠物模型资源路径（相对 assets/resources/）。
       *
       * ⚠️ 注意这里**多了一层重名目录**，不是笔误：glTF 导入后主资源（gltf-scene）的子资源名
       * 等于文件名本身，所以资源库注册的路径是 `目录/文件名/文件名`。
       * 从构建产物 `assets/resources/config.json` 的 paths 表实测确认：
       *     "9": ["models/cat/pet-cat-rigged/pet-cat-rigged", 7, 1]
       * 写成 `models/cat/pet-cat-rigged` 会直接报
       * `Bundle resources doesn't contain models/cat/pet-cat-rigged`（已实测踩到）。
       */

      PET_MODEL_PATH = 'models/cat/pet-cat-rigged/pet-cat-rigged';
      /** 模型所在目录（剪辑子资源按 `目录/剪辑名` 取） */

      PET_MODEL_DIR = 'models/cat/pet-cat-rigged';
      /**
       * 生动脸贴图（独立资产，运行时覆盖到 pet-toon 的 mainTexture）。
       *
       * 为什么不直接烘进 GLB 内嵌槽位：上一版 bake_alive.py 用 prVw 私有区块补长 PNG
       * 后拼接 GLB，数据层自检虽过，但把"脸"和"模型容器"绑死 —— 每次改脸都要重拼 GLB
       * 并赌一次导入器兼容性（prVw 对 Cocos 导入器始终未在真机验证过）。
       * 独立贴图走普通资产链路（meta 由构建时 asset-db 自动生成，eye_sprite 即先例），
       * GLB 保持原样、零导入风险；加载失败时回退 GLB 内嵌 albedo，不会白猫也不会缺猫。
       * 子资源路径 `.../texture` 的依据：构建产物 config.json 中
       * `"3": ["textures/eye_sprite/texture", 2, 1]`（image 导入器的 texture 子资源）。
       */

      PET_ALIVE_TEX = 'textures/pet-cat-alive/texture';
      /**
       * 需要用到的剪辑名。缺哪个就跳过哪个 —— 模型会继续迭代（比如 Blink 是后补的），
       * 不能因为少一条剪辑就让整只猫不出现。
       */

      PET_CLIPS = ['Idle', 'Happy', 'Blink'];
      /**
       * 模型缩放：源模型（QQ 宠物风格猫 qqcat-prod.glb，11,525 面）在 Blender 里高 0.8028。
       * 1.9 倍 → 身高约 1.53，与房间机位标定匹配（用 ?probe=1 复核）。
       */

      PET_MODEL_SCALE = 1.9;
      /** 宠物身高（世界单位），供构图探针使用；= 模型源高 0.8028 × 缩放 */

      PET_HEIGHT = 0.8028 * PET_MODEL_SCALE;
      /** 宠物站位（地毯中央；与房间里的软影、玩具球对齐） */

      PET_POS = new Vec3(0, 0, 0.35);
      /**
       * 相机机位。
       *
       * 主视角：轻微俯视的 3/4 视角，脚底落在地板上、头顶留出呼吸空间；
       * 比 v5 后退约 12% 把柜子/盆栽/猫窝/玩具收进画面 —— 一个"住着人"的空间需要生活痕迹。
       * 竖屏另给一组：竖屏可视横向范围窄，沿用横屏机位会让角色横向顶边。
       */

      CAMERA_SHOT = {
        room: {
          pos: [1.30, 1.36, 3.52],
          target: [0, 0.60, 0.10]
        },
        portrait: {
          pos: [1.98, 1.48, 5.36],
          target: [0, 0.56, 0.16]
        },
        front: {
          pos: [0, 0.86, 3.25],
          target: [0, 0.74, 0.20]
        },
        q34: {
          pos: [-2.00, 1.00, 2.80],
          target: [0, 0.76, 0.20]
        }
      };
      /** 纯色背景（?plain=1 验收模式：去掉房间，只留角色自证轮廓与材质） */

      PLAIN_BG = new Color(0xCF, 0xC9, 0xD6, 255);
      /**
       * 头部世界坐标（特效锚点）。
       *
       * 由模型坐标系反推：QQ 猫双眼中点 Blender 坐标约 (-0.025, -0.26, 0.52)
       * （probe_eye_qq 边界拟合），导出为 glTF（Y 向上）后变成 (-0.025, 0.52, 0.26)，
       * 乘缩放 1.9：→ (-0.05, 0.99, 0.49)。
       */

      HEAD_OFFSET = new Vec3(-0.05, 0.99, 0.49);

      _export("PetGameRoot", PetGameRoot = (_dec = ccclass('PetGameRoot'), _dec(_class = class PetGameRoot extends Component {
        constructor(...args) {
          super(...args);

          _defineProperty(this, "bridge", new (_crd && PetGameBridge === void 0 ? (_reportPossibleCrUseOfPetGameBridge({
            error: Error()
          }), PetGameBridge) : PetGameBridge)());

          _defineProperty(this, "ccRuntime", globalThis.cc);

          _defineProperty(this, "kit", null);

          _defineProperty(this, "world3d", null);

          _defineProperty(this, "room", null);

          _defineProperty(this, "camera3d", null);

          _defineProperty(this, "rootTransform", null);

          _defineProperty(this, "effects", null);

          /** 宠物节点（模型异步加载完成前为 null） */
          _defineProperty(this, "petNode", null);

          _defineProperty(this, "petAnim", null);

          /** 最近一次下发的状态：模型加载是异步的，到位后要用它补播正确的动画 */
          _defineProperty(this, "pendingPet", null);

          /** 0.35~1.0：数值低时把待机动作放慢（喘、没精神），是全片唯一的"状态→动画"映射 */
          _defineProperty(this, "speedScale", 1);

          _defineProperty(this, "blinkTimer", 3.5);

          _defineProperty(this, "blinkReady", false);

          _defineProperty(this, "plain", false);

          _defineProperty(this, "shot", 'room');

          _defineProperty(this, "probing", false);

          _defineProperty(this, "toonAsset", null);

          /** 生动脸贴图（材质前置，加载失败时为 null → 回退 GLB 内嵌 albedo） */
          _defineProperty(this, "aliveTexture", null);

          _defineProperty(this, "time", 0);

          _defineProperty(this, "roomTime", 0);

          _defineProperty(this, "orbSeeds", []);
        }

        start() {
          const params = new URLSearchParams(window.location.search);
          this.plain = params.get('plain') === '1';
          this.probing = params.get('probe') === '1';
          this.shot = params.get('shot') || (window.innerHeight > window.innerWidth ? 'portrait' : 'room');
          this.rootTransform = this.node.getComponent(UITransform);
          this.kit = new (_crd && PetBuilderKit === void 0 ? (_reportPossibleCrUseOfPetBuilderKit({
            error: Error()
          }), PetBuilderKit) : PetBuilderKit)(this.ccRuntime); // 自定义材质（pet-toon）必须先加载：EffectAsset.get 只能查到已加载的资产，
          // 若在加载完成前构建，所有部件会静默退化成引擎内置材质。

          resources.load('effects/pet-toon', EffectAsset, (error, asset) => {
            console.log(`[pet-probe] resources.load pet-toon: err=${error ? String(error) : 'none'} ` + `asset=${asset ? asset.name : 'null'}`);

            if (!error && asset) {
              this.toonAsset = asset;
              this.kit.setToonEffect(asset);
              console.log(`[pet-probe] toon ready: isToon=${this.kit.isToon}`);
            } else {
              console.warn('[pet-game] pet-toon 加载失败，材质回退为内置材质', error);
            } // 生动脸贴图同为材质前置：configurePetMaterials 是同步换材质，
            // 贴图必须在此之前就绪，否则首帧先用 GLB 内嵌 albedo、覆盖不生效。


            resources.load(PET_ALIVE_TEX, Texture2D, (texError, tex) => {
              if (texError || !tex) {
                console.warn('[pet-game] 生动脸贴图加载失败，回退 GLB 内嵌 albedo', texError);
              } else {
                this.aliveTexture = tex;
                console.log(`[pet-probe] alive texture ready: ${tex.name}`);
              }

              this.buildScene(params);
            });
          });
        }

        buildScene(params) {
          this.buildWorld();
          this.buildOverlay();
          this.bindBridge();

          if (!this.plain) {
            this.buildPetShadow();
            this.loadPet();
          }

          if (this.plain) {
            this.node.active = false;
          }

          if (params.get('probe') === '1') {
            this.probeFraming();
          }

          if (params.get('probeMat') === '1') {
            this.probeMaterials();
          }

          this.bridge.send({
            source: 'pet-game',
            type: 'ready'
          });
        } // ---------------- 宠物（外部绑定模型） ----------------

        /**
         * 加载并实例化宠物模型。
         *
         * 注意这是**异步**的：`ready` 会先发给宿主，模型可能稍后才到位。
         * 因此服务端下发的状态先存进 `pendingPet`，模型就绪后立刻补播正确动画 ——
         * 否则会出现"猫加载出来了但站着不动"或"低状态还蹦得很欢"。
         */


        loadPet() {
          resources.load(PET_MODEL_PATH, Prefab, (error, prefab) => {
            if (error || !prefab) {
              console.warn('[pet-game] 宠物模型加载失败，场景保持无角色状态', error);
              return;
            }

            const node = instantiate(prefab);
            node.name = 'Pet';
            node.setScale(PET_MODEL_SCALE, PET_MODEL_SCALE, PET_MODEL_SCALE);
            node.setPosition(PET_POS);
            this.world3d.addChild(node);
            this.petNode = node;

            if (this.probing) {
              this.dumpTree(node, 0);
            }

            this.configurePetMaterials(node); // 逐条加载剪辑。
            // ⚠️ 不能用 `resources.load([多条路径], ...)` —— 它是**全有或全无**：
            // 只要有一条路径不存在（比如 Blink 还没做出来），整批都失败并报
            // `Bundle resources doesn't contain .../Blink`，结果整只猫静止不动。已实测踩到。

            const clipPaths = PET_CLIPS.map(name => `${PET_MODEL_DIR}/${name}`);
            const loaded = [];
            let remaining = clipPaths.length;

            const settle = () => {
              remaining -= 1;

              if (remaining > 0) {
                return;
              }

              this.attachClips(node, loaded);
            };

            for (const path of clipPaths) {
              resources.load(path, AnimationClip, (clipError, clip) => {
                if (clipError || !clip) {
                  console.warn(`[pet-game] 剪辑缺失（跳过）: ${path}`);
                } else {
                  loaded.push(clip);
                }

                settle();
              });
            }
          });
        }
        /**
         * 把剪辑挂到宠物根节点上并起播。
         *
         * 组件必须挂在 `Pet`（prefab 根）而不是 `CatRig`：剪辑里的轨道路径是
         * `CatRig/Root/Hips/...`，从根解析才匹配；挂到 CatRig 上会整体少一层，全部绑不上。
         */


        attachClips(node, list) {
          if (!list.length) {
            console.warn('[pet-game] 没有任何可用剪辑，宠物保持静止');
            return;
          }

          const anim = node.addComponent(SkeletalAnimation); // 用实时骨骼动画：导入的 glTF 剪辑没有烘焙贴图动画，开着会走空分支

          anim.useBakedAnimation = false;
          anim.clips = list;
          const idle = list.find(c => c.name === 'Idle');

          if (idle) {
            anim.defaultClip = idle;
          }

          console.log(`[pet-probe] pet clips=[${list.map(c => c.name).join(', ')}] ` + `default=${idle ? idle.name : 'null'}`);
          this.blinkReady = list.some(c => c.name === 'Blink');
          this.petAnim = anim;
          this.playClip('Idle', true);
          this.applyStatsToAnimation();
        }
        /** 打印宠物节点树与各节点组件（?probe=1）—— glTF 导入的层级只能靠实测，不能猜 */


        dumpTree(node, depth) {
          const names = [];

          for (const c of node.components) {
            let extra = '';
            const model = c;

            if (model.material && model.material.effectAsset) {
              extra = ` effect=${model.material.effectAsset.name}`;
              const get = model.material.getProperty;

              if (get) {
                var _passes;

                // 贴图到底有没有绑上、albedo 是不是被顶到 1 —— 白纸片的两种可能成因
                const tex = get.call(model.material, 'mainTexture');
                const albedo = get.call(model.material, 'albedo');
                const mScale = get.call(model.material, 'albedoScale');
                extra += ` tex=${tex ? tex.name : 'null'}`;
                extra += ` albedo=${albedo ? JSON.stringify(albedo) : 'null'}`;
                extra += ` aScale=${mScale ? JSON.stringify(mScale) : 'null'}`;
                extra += ` passes=${(_passes = model.material.passes) === null || _passes === void 0 ? void 0 : _passes.length}`;
              }
            }

            if (model.skinningRoot) {
              extra += ' [skinned]';
            }

            names.push(c.constructor.name + extra);
          }

          console.log(`[pet-probe] ${'  '.repeat(depth)}${node.name} layer=${node.layer} [${names.join('+') || '-'}]`);

          for (const child of node.children) {
            this.dumpTree(child, depth + 1);
          }
        }
        /**
         * 把宠物换成**和房间同一套** pet-toon 材质。
         *
         * 为什么不沿用 glTF 导入的 `builtin-standard`：
         * 实测它在场景里渲染成惨白一片、毫无体积，且主光从 78000 降到 20000、环境光 HDR/LDR
         * 双写清零，画面**几乎没有变化** —— 说明引擎的 PBR 光照在这个 headless 管线下
         * 没有按预期参与计算（层、可见性、材质技术、贴图绑定都逐一验证过，全部正常：
         * `albedoTex=...@221a5`、`normalTex=...@3effa` 都绑上了）。
         * 与其继续调一个我不掌控的管线，不如让宠物和房间共用同一个自研着色器 ——
         * 光照语言一致、视觉完全统一，而且我完全可控。
         *
         * 蒙皮：pet-toon 的顶点着色器走 `CCVertInput(In)`（legacy/input-standard），
         * 该函数在 `CC_USE_SKINNING` 定义时会套用关节蒙皮，引擎按模型的蒙皮信息自动注入该宏。
         */


        configurePetMaterials(node) {
          const visit = n => {
            for (const comp of n.components) {
              var _this$aliveTexture;

              const renderer = comp;
              const src = renderer.material;

              if (!src) {
                continue;
              } // albedo 取舍：优先用"生动脸"独立贴图（琥珀眼/粉鼻/腮红烘焙版）；
              // 它没加载成功时退回 GLB 内嵌 albedo（奶油无脸版），保证永不白板。


              const embedded = src.getProperty('mainTexture');
              const albedo = (_this$aliveTexture = this.aliveTexture) !== null && _this$aliveTexture !== void 0 ? _this$aliveTexture : embedded;
              const toon = new Material();

              try {
                toon.initialize({
                  effectAsset: this.toonAsset,
                  technique: 0
                });
                toon.setProperty('mainColor', new Color(255, 255, 255, 255));

                if (albedo) {
                  toon.setProperty('mainTexture', albedo);
                } // 暗部**深暖灰**（176,158,140）+ 阈值对准可见区间：
                // 半兰伯特下正面法线的 ndl∈[0.5,1.0]，阈值低时暗部全落在
                // 看不见的背面 —— 可见面被压缩在 20% 动态范围里，调什么都平（已实测）。
                // x=0.72/y=0.12：右脸 ndl≈0.52 → lit≈0 → 左亮右暗的大转折。


                toon.setProperty('shadeColor', new Color(166, 148, 130, 255));
                toon.setProperty('shadeCtrl', new Vec4(0.72, 0.12, 0.16, 0.12));
                toon.setProperty('outlineCtrl', new Vec4(0.0035, 0, 0, 0));
                toon.setProperty('outlineColor', new Color(120, 102, 94, 255)); // 高光略强略聚（软陶质感，避免"哑光死面"）

                toon.setProperty('furCtrl', new Vec4(0.16, 2.0, 0.06, 10.0)); // 主光方向：**左侧强侧光**（-0.85）—— 官方参考图的立体感来自
                // "左亮右暗"的大转折；z 分量压低让正面不再均匀受光

                toon.setProperty('lightDir', new Vec4(-0.85, 0.30, 0.32, 0.0));
                toon.setProperty('fillDir', new Vec4(0.42, -0.18, 0.86, 0.0)); // 补光/轮廓光/底部 AO 加强 + 主光增益 0.88（配合更深的暗部）

                toon.setProperty('lightCtrl', new Vec4(0.88, 0.28, 0.14, 0.14)); // ⚠️ mapCtrl 必须放在**所有 setProperty 之后**：实测该引擎的材质
                // uniform 在首次绑定后才同步"最后一次写入"的值，先设置的属性
                // 会停留在旧值上（表现为参数怎么调渲染都不变，已实测多轮）。
                // x=1：贴图部件启用基色采样；y/z/w 是诊断档（uv 直出/定点采样/uniform 直读）

                toon.setProperty('mapCtrl', new Vec4(1, 0, 0, 1)); // ⚠️ 必须走 setMaterial 显式替换：模型是异步加载的，首帧可能已经渲过，
                // `.material = toon` 赋值不会触发蒙皮网格的渲染侧重绑，
                // 实测整组 uniform 落不进渲染（猫渲染成 pet-toon 默认值的白素模）。

                renderer.setMaterial(toon, 0);
              } catch (error) {
                console.warn('[pet-game] 宠物 pet-toon 材质初始化失败，保留原材质', error);
                continue;
              }

              const texBack = toon.getProperty('mainTexture');
              const ctrlBack = toon.getProperty('mapCtrl');
              const lightBack = toon.getProperty('lightCtrl');
              console.log(`[pet-probe] pet material ${n.name}: pet-toon albedoTex=` + `${texBack ? texBack.uuid : 'null'} ` + `mapCtrl=${ctrlBack ? `${ctrlBack.x},${ctrlBack.y},${ctrlBack.z},${ctrlBack.w}` : 'null'} ` + `lightCtrl=${lightBack ? `${lightBack.x},${lightBack.y}` : 'null'}`);
            }

            for (const child of n.children) {
              visit(child);
            }
          };

          visit(node);
        }
        /** 播片：名字不存在时静默回落（模型可能还没带上该剪辑，不能因此崩掉整场） */


        playClip(name, loop) {
          const anim = this.petAnim;

          if (!anim) {
            return;
          }

          const has = anim.clips.some(c => c.name === name);

          if (!has) {
            return;
          }

          const state = anim.getState(name);

          if (state) {
            state.speed = this.speedScale;
          }

          anim.play(name);

          if (!loop && anim.defaultClip) {
            var _anim$getState$durati, _anim$getState;

            // 单次动作播完回到待机
            this.scheduleOnce(() => this.playClip('Idle', true), (_anim$getState$durati = (_anim$getState = anim.getState(name)) === null || _anim$getState === void 0 ? void 0 : _anim$getState.duration) !== null && _anim$getState$durati !== void 0 ? _anim$getState$durati : 1);
          }
        }
        /** 用服务端数值决定待机强度：越虚弱，动作越慢（v1 唯一可用的状态→动画映射） */


        applyStatsToAnimation() {
          const pet = this.pendingPet;

          if (!pet) {
            return;
          }

          const worst = Math.min(pet.maxHp > 0 ? pet.hp / pet.maxHp : 1, pet.hunger / 100, pet.happiness / 100, pet.energy / 100, pet.cleanliness / 100); // 0.45(极差) ~ 1.0(健康)：线性但夹住下界，太慢会看起来卡住

          this.speedScale = Math.max(0.45, Math.min(1, 0.45 + worst * 0.55));
          const anim = this.petAnim;

          if (anim) {
            for (const clip of anim.clips) {
              const state = anim.getState(clip.name);

              if (state) {
                state.speed = this.speedScale;
              }
            }
          }
        }

        get headWorld() {
          return new Vec3(PET_POS.x + HEAD_OFFSET.x, HEAD_OFFSET.y, PET_POS.z + HEAD_OFFSET.z);
        } // ---------------- 探针 ----------------


        probeFraming() {
          this.scheduleOnce(() => {
            const camera = this.camera3d;

            if (!camera) {
              console.log('[pet-probe] camera missing');
              return;
            }

            const foot = camera.worldToScreen(new Vec3(PET_POS.x, 0, PET_POS.z), new Vec3());
            const top = camera.worldToScreen(new Vec3(PET_POS.x, PET_HEIGHT, PET_POS.z), new Vec3());
            const screenHeight = screen.windowSize.height;
            console.log(`[pet-probe] cam=${camera.node.position.toString()} fov=${camera.fov} ` + `screen=${screenHeight}px foot=(${foot.x.toFixed(1)},${foot.y.toFixed(1)}) ` + `top=(${top.x.toFixed(1)},${top.y.toFixed(1)}) ` + `height=${Math.abs(top.y - foot.y).toFixed(1)}px ` + `ratio=${(Math.abs(top.y - foot.y) / screenHeight * 100).toFixed(1)}%`);
          }, 1.2);
        }

        probeMaterials() {
          this.scheduleOnce(() => {
            var _getAll, _ref;

            const registry = (_getAll = (_ref = EffectAsset).getAll) === null || _getAll === void 0 ? void 0 : _getAll.call(_ref);
            const list = registry instanceof Map ? Array.from(registry.values()) : Array.isArray(registry) ? registry : [];
            console.log(`[pet-probe] loaded effects (${list.length}): ` + list.map(entry => entry && entry.name).join(' | '));

            const visit = node => {
              for (const comp of node.components) {
                var _model$material$effec;

                const model = comp;

                if (!model.mesh || !model.material) {
                  continue;
                }

                console.log(`[pet-probe] mat ${node.name} effect=${(_model$material$effec = model.material.effectAsset) === null || _model$material$effec === void 0 ? void 0 : _model$material$effec.name} ` + `passes=${model.material.passes ? model.material.passes.length : -1}`);
              }

              for (const child of node.children) {
                visit(child);
              }
            };

            if (this.petNode) {
              visit(this.petNode);
            }
          }, 1.5);
        }

        update(dt) {
          const step = Math.min(dt, 0.05);
          this.time += step;
          this.roomTime += step;
          this.roomAmbience(step);
          this.driveBlink(step);
        }
        /**
         * 眨眼：随机间隔触发。
         * 剪辑存在时才跑（模型重建后可能还没带上 Blink），否则会每几秒白播一次。
         */


        driveBlink(dt) {
          if (!this.blinkReady || !this.petAnim) {
            return;
          }

          this.blinkTimer -= dt;

          if (this.blinkTimer > 0) {
            return;
          }

          this.blinkTimer = 2.4 + Math.random() * 3.6;
          const anim = this.petAnim;
          const state = anim.getState('Blink');

          if (state) {
            state.speed = 1;
          }

          anim.play('Blink');
        }

        onDestroy() {
          this.bridge.dispose();
        } // ---------------- 场景构建 ----------------


        buildWorld() {
          const kit = this.kit;
          const scene = this.node.scene;
          this.world3d = kit.make3dNode(scene, 'World3D', new Vec3(0, 0, 0));
          this.room = this.plain ? null : (_crd && buildRoom === void 0 ? (_reportPossibleCrUseOfbuildRoom({
            error: Error()
          }), buildRoom) : buildRoom)(this.world3d, kit);

          if (!this.plain) {
            this.buildLighting(scene);
          }

          const cameraNode = scene.getChildByName('Main3DCamera');
          this.camera3d = cameraNode ? cameraNode.getComponent(Camera) : null;

          if (cameraNode && this.camera3d) {
            const shot = CAMERA_SHOT[this.shot] || CAMERA_SHOT.room;
            cameraNode.setPosition(shot.pos[0], shot.pos[1], shot.pos[2]);
            cameraNode.lookAt(new Vec3(shot.target[0], shot.target[1], shot.target[2]), new Vec3(0, 1, 0));
            this.camera3d.clearColor = this.plain ? PLAIN_BG : new Color(0x6E, 0x5A, 0x66, 255);
          }
        }
        /**
         * 场景光照 —— **只服务宠物**。
         *
         * 背景：房间里所有部件走自研 `pet-toon`，它自带一套写死的三点光（lightDir/fillDir/lightCtrl），
         * **完全不依赖引擎光源**，所以场景里一盏灯都没有、环境光也是默认的冷蓝天光。
         * 但宠物是 Blender 导出的 glTF，带着标准 PBR 材质（贴图 + metallic 0），
         * 它必须靠引擎光源才出体积 —— 没有灯就渲染成一张白纸片（已实测）。
         *
         * 因此这里补一盏平行光，方向**对齐 pet-toon 的主光方向**（来自窗户：左后上），
         * 让宠物与房间的受光方向一致，不会显得是贴上去的。
         * 因为 pet-toon 无视引擎光源，这一步对房间**零影响**，不存在回归风险。
         */


        buildLighting(scene) {
          // 主光（暖，左前上）：**必须从镜头这一侧来**。
          // 第一版我按 pet-toon 的 lightDir 把灯放在"窗户那侧"（左后上），结果只照亮了猫的背面，
          // 镜头看到的正面落在环境光里 → 依旧是一张白纸片。
          // 根本原因是 pet-toon 有**两盏**：主光（背打）+ 前下方暖色反弹光；
          // PBR 这边没有反弹光，所以主光必须自己承担"照亮可见面"的职责。
          const key = new Node('PetKeyLight');
          key.layer = Layers.Enum.DEFAULT;
          scene.addChild(key);
          key.setPosition(-3.2, 2.5, 2.3);
          key.lookAt(new Vec3(0, 0.62, 0.35), new Vec3(0, 1, 0));
          const keyLight = key.addComponent(DirectionalLight);
          keyLight.color = new Color(255, 231, 198); // ⚠️ 强度必须压得很低。近白毛色（#F3EEE7 ≈ sRGB 0.95）+ 本工程
          // `PostSettingsInfo._toneMappingType = 0`（无色调映射）→ 线性值直接 clip，
          // 78000 lux 会把整只猫顶成纯白、体积全丢（已实测）。2 万左右才留得住明暗。

          keyLight.illuminance = 34000; // 补光（冷，右前下）：压住暗部、给一点冷暖对比，对应 pet-toon 的 fillDir

          const fill = new Node('PetFillLight');
          fill.layer = Layers.Enum.DEFAULT;
          scene.addChild(fill);
          fill.setPosition(3.0, 0.9, 2.0);
          fill.lookAt(new Vec3(0, 0.55, 0.35), new Vec3(0, 1, 0));
          const fillLight = fill.addComponent(DirectionalLight);
          fillLight.color = new Color(196, 208, 255);
          fillLight.illuminance = 7000; // 环境光：**HDR / LDR 两份字段都要写**。
          // AmbientInfo 里同时存在 `_skyColorHDR/_skyIllumHDR` 与 `_skyColor/_skyIllum`，
          // 只写 LDR 那份在 HDR 分支下完全不生效（第一版就是这么栽的：改了环境光毫无变化）。
          // 默认值是冷蓝天空色拉满（0.2,0.5,0.8 / 20000），会把近白毛色洗成惨白、体积全丢。

          const ambient = director.getScene().globals.ambient;
          const a = ambient;
          const skyLDR = new Color(200, 205, 215);
          const groundLDR = new Color(140, 112, 84);
          ambient.skyColor = skyLDR;
          ambient.groundAlbedo = groundLDR;
          ambient.skyIllum = 1400;
          a.skyColorHDR = skyLDR;
          a.groundAlbedoHDR = groundLDR;
          a.skyIllumHDR = 1400;
          console.log(`[pet-probe] lighting: key=${keyLight.illuminance} fill=${fillLight.illuminance} ` + `ambLDR=${ambient.skyIllum} ambHDR=${a.skyIllumHDR}`);
        }
        /**
         * 宠物脚下的接触阴影。
         *
         * 场景没开实时阴影（房间各部件靠手工软影补），宠物也必须补一个，
         * 否则它会"浮"在地毯上。用 kit.decal 的柔边贴花，比贴一张 png 更省资产。
         */


        buildPetShadow() {
          const root = this.kit.make3dNode(this.world3d, 'PetShadow', new Vec3(0, 0, 0));
          this.kit.decal(root, 'Blob', [0.62, 0.010, 0.46], {
            color: new Color(0x6B, 0x4A, 0x33, 255),
            shade: new Color(0x6B, 0x4A, 0x33, 255),
            alpha: 82,
            soft: [0.02, 1.0]
          }, new Vec3(PET_POS.x, 0.075, PET_POS.z));
          return root;
        }

        buildOverlay() {
          this.effects = new (_crd && PetEffects === void 0 ? (_reportPossibleCrUseOfPetEffects({
            error: Error()
          }), PetEffects) : PetEffects)((name, x, y) => this.makeUiNode(name, x, y), world => this.project(world));
        }

        makeUiNode(name, x, y) {
          const node = new Node(name);
          node.layer = Layers.Enum.UI_2D;
          node.addComponent(UITransform).setContentSize(8, 8);
          node.setPosition(x, y, 0);
          this.node.addChild(node);
          return node;
        }

        project(world) {
          if (!this.camera3d || !this.rootTransform) {
            return new Vec3(0, 0, 0);
          }

          const screenPos = this.camera3d.worldToScreen(world, new Vec3());
          const windowSize = screen.windowSize;
          const canvasSize = this.rootTransform.contentSize;

          if (!windowSize.width || !windowSize.height || !canvasSize.width) {
            return new Vec3(0, 0, 0);
          }

          const scaleX = canvasSize.width / windowSize.width;
          const scaleY = canvasSize.height / windowSize.height;
          return new Vec3(screenPos.x * scaleX - canvasSize.width / 2, screenPos.y * scaleY - canvasSize.height / 2, 0);
        }
        /** 房间氛围：光点上升循环 / 灯泡呼吸 / 阳光光斑呼吸 / 玩具球轻摆 */


        roomAmbience(dt) {
          const room = this.room;

          if (!room) {
            return;
          }

          room.orbs.forEach((orb, index) => {
            if (this.orbSeeds.length <= index) {
              this.orbSeeds.push(Math.random() * 6.28);
            }

            const seed = this.orbSeeds[index];
            const y = orb.position.y + dt * 0.16;
            orb.setPosition(orb.position.x + Math.sin(this.roomTime * 0.8 + seed) * dt * 0.1, y > 3.4 ? 0.5 : y, orb.position.z);
            const scale = 0.85 + Math.sin(this.roomTime * 1.7 + seed) * 0.15;
            orb.setScale(scale, scale, scale);
          });
          room.bulbs.forEach((bulb, index) => {
            const scale = 0.92 + Math.sin(this.roomTime * 2.1 + index * 0.7) * 0.08;
            bulb.setScale(scale, scale, scale);
          });
          const beamScale = 1 + Math.sin(this.roomTime * 0.9) * 0.06;
          room.sunBeam.setScale(beamScale, 1, beamScale);
          const toy = room.toyBall;
          toy.setPosition(-1.35 + Math.sin(this.roomTime * 0.6) * 0.1, 0.19 + Math.abs(Math.sin(this.roomTime * 1.4)) * 0.03, 1.05);
        } // ---------------- 桥接 ----------------


        bindBridge() {
          this.bridge.bind(message => {
            switch (message.type) {
              case 'init':
              case 'petState':
                this.pendingPet = message.pet;
                this.applyStatsToAnimation();
                break;

              case 'actionResult':
                if (message.ok) {
                  this.playClip('Happy', false);
                  this.effects && this.effects.sparkle(this.headWorld, 3);
                } else {
                  this.effects && this.effects.floatText(this.headWorld, '呜…', new Color(255, 200, 200, 255));
                }

                break;

              case 'battleRounds':
                if (this.effects) {
                  this.effects.stars(this.headWorld, message.won ? 8 : 3);
                }

                break;

              case 'chatBubble':
                // 气泡属界面层（旧 HUD 已删），新 UI 层落地前先用舞台浮字顶一下
                this.effects && this.effects.floatText(this.headWorld, message.content, new Color(255, 250, 240, 255), 20);
                break;

              default:
                break;
            }
          });
        }

      }) || _class));

      _cclegacy._RF.pop();

      _crd = false;
    }
  };
});
//# sourceMappingURL=cdeb3b1adcc28385c44468a441f0161201887a6c.js.map