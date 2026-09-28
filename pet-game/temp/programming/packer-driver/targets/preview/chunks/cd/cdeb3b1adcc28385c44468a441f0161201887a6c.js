System.register(["__unresolved_0", "cc", "__unresolved_1", "__unresolved_2", "__unresolved_3", "__unresolved_4"], function (_export, _context) {
  "use strict";

  var _reporterNs, _cclegacy, __checkObsolete__, __checkObsoleteInNamespace__, _decorator, AnimationClip, Camera, Color, Component, DirectionalLight, director, EffectAsset, instantiate, Layers, Material, Node, Prefab, SkeletalAnimation, UITransform, Vec3, Vec4, resources, screen, PetGameBridge, PetBuilderKit, PetEffects, buildRoom, _dec, _class, _crd, ccclass, PET_CLIPS, PET_POS, FRUIT_SPECS, DEFAULT_FRUIT, SPECIES_SLOT, CAMERA_SHOT, PLAIN_BG, PetGameRoot;

  function _defineProperty(e, r, t) { return (r = _toPropertyKey(r)) in e ? Object.defineProperty(e, r, { value: t, enumerable: !0, configurable: !0, writable: !0 }) : e[r] = t, e; }

  function _toPropertyKey(t) { var i = _toPrimitive(t, "string"); return "symbol" == typeof i ? i : i + ""; }

  function _toPrimitive(t, r) { if ("object" != typeof t || !t) return t; var e = t[Symbol.toPrimitive]; if (void 0 !== e) { var i = e.call(t, r || "default"); if ("object" != typeof i) return i; throw new TypeError("@@toPrimitive must return a primitive value."); } return ("string" === r ? String : Number)(t); }

  function resolveFruitKey(urlSpecies, petSpecies) {
    if (urlSpecies) {
      var key = urlSpecies.toLowerCase();

      if (FRUIT_SPECS[key]) {
        return key;
      }
    }

    if (petSpecies) {
      var slot = SPECIES_SLOT[petSpecies.toUpperCase()];

      if (slot) {
        return slot;
      }
    }

    return DEFAULT_FRUIT;
  }
  /**
   * 相机机位。
   *
   * 主视角：轻微俯视的 3/4 视角，脚底落在地板上、头顶留出呼吸空间；
   * 比 v5 后退约 12% 把柜子/盆栽/窝/玩具收进画面 —— 一个"住着人"的空间需要生活痕迹。
   * 竖屏另给一组：竖屏可视横向范围窄，沿用横屏机位会让角色横向顶边。
   */


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
       * 家园场景主组件（视觉重构 v9 · 水果宠物打样）。
       *
       * 结构：
       *  - 3D 世界：房间（PetRoomBuilder，统一走 pet-toon）+ 宠物（外部绑定模型，见下）
       *  - 相机机位（横屏 room / 竖屏 portrait 自动选择）
       *  - 房间氛围动画（光点上升 / 灯泡呼吸 / 光斑呼吸 / 玩具轻摆）
       *  - 2D 覆盖层：特效粒子 + 世界坐标投影
       *  - 通信桥（契约冻结，见 PetGameBridge）
       *
       * 宠物：五果阵容全部进场 = 草莓 / 橘子 / 西瓜 / 蓝莓 / 火龙果（tools/fruit-pipeline 批量产出）。
       *  - 每只都是闭合旋转曲面 + 贴图花纹 + 3D 大眼 + ω 贴面猫嘴 + 腮红 + 软萌帽/鳍叶
       *  - 统一 4 骨轻绑定（Body/Calyx/EyeL/EyeR），三条剪辑：Idle 4s / Happy 1.67s / Blink 0.25s
       *  - 果种按 FRUIT_SPECS 加载：URL ?species= 强制预览 → 宿主 species 槽位映射 → 默认草莓
       *  - 挤压/拉伸以地面为锚（Body 骨头埋在脚下），是水果动感的核心
       *
       * 职责边界（不变）：只做展示与动画，数值全部来自宿主下发的 PetDisplayState，
       * 用户操作只回传 intent。契约一个字段都没改。
       */

      /**
       * 宠物模型资源路径（相对 assets/resources/）。
       *
       * ⚠️ glTF 导入后主资源（gltf-scene）的子资源名等于文件名本身，
       * 所以资源库注册的路径是 `目录/文件名/文件名`（猫版实测确认的规则，水果沿用）。
       */

      /** 需要用到的剪辑名（五果统一）。缺哪个就跳过哪个，不能因为少一条剪辑就让宠物不出现。 */

      PET_CLIPS = ['Idle', 'Happy', 'Blink'];
      /** 宠物站位（地毯中央；与房间里的软影、玩具球对齐） */

      PET_POS = new Vec3(0, 0, 0.35);
      /** 水果规格：GLB 路径（`目录/文件名/文件名` 三段式）/ 缩放 / 身高 / 头部特效锚点 / 接触阴影尺寸 */

      /** 五果规格表（建模值 × 缩放，与 tools/fruit-pipeline 各 build 脚本一一对应） */
      FRUIT_SPECS = {
        strawberry: {
          path: 'models/fruit/strawberry/strawberry/strawberry',
          dir: 'models/fruit/strawberry/strawberry',
          scale: 1.5,
          height: 1.0,
          head: new Vec3(0, 0.81, 0.45),
          shadow: [0.58, 0.010, 0.45]
        },
        orange: {
          path: 'models/fruit/orange/orange/orange',
          dir: 'models/fruit/orange/orange',
          scale: 1.65,
          height: 0.87,
          head: new Vec3(0, 0.76, 0.58),
          shadow: [0.60, 0.010, 0.46]
        },
        watermelon: {
          path: 'models/fruit/watermelon/watermelon/watermelon',
          dir: 'models/fruit/watermelon/watermelon',
          scale: 1.6,
          height: 0.99,
          head: new Vec3(0, 0.77, 0.63),
          shadow: [0.66, 0.010, 0.52]
        },
        blueberry: {
          path: 'models/fruit/blueberry/blueberry/blueberry',
          dir: 'models/fruit/blueberry/blueberry',
          scale: 1.5,
          height: 0.70,
          head: new Vec3(0, 0.56, 0.47),
          shadow: [0.44, 0.010, 0.34]
        },
        dragonfruit: {
          path: 'models/fruit/dragonfruit/dragonfruit/dragonfruit',
          dir: 'models/fruit/dragonfruit/dragonfruit',
          scale: 1.6,
          height: 0.95,
          head: new Vec3(0, 0.79, 0.53),
          shadow: [0.50, 0.010, 0.40]
        }
      };
      DEFAULT_FRUIT = 'strawberry';
      /**
       * 物种槽位映射：mall-pet 的 species 枚举（CAT/DOG/RABBIT/FOX/PANDA）迁移成水果码之前，
       * 按固定顺序把 5 个动物槽位映射到 5 只水果（每个 DB 宠物各得一只果）；
       * 枚举迁移落地后枚举值本身就是水果码，同表直查。URL `?species=orange` 可强制指定预览。
       */

      SPECIES_SLOT = {
        CAT: 'strawberry',
        DOG: 'orange',
        RABBIT: 'watermelon',
        FOX: 'blueberry',
        PANDA: 'dragonfruit',
        STRAWBERRY: 'strawberry',
        ORANGE: 'orange',
        WATERMELON: 'watermelon',
        BLUEBERRY: 'blueberry',
        DRAGONFRUIT: 'dragonfruit'
      };
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

      _export("PetGameRoot", PetGameRoot = (_dec = ccclass('PetGameRoot'), _dec(_class = class PetGameRoot extends Component {
        constructor() {
          super(...arguments);

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

          /** 0.35~1.0：数值低时把待机动作放慢（蔫、没精神），是全片唯一的"状态→动画"映射 */
          _defineProperty(this, "speedScale", 1);

          _defineProperty(this, "blinkTimer", 3.5);

          _defineProperty(this, "blinkReady", false);

          _defineProperty(this, "plain", false);

          _defineProperty(this, "shot", 'room');

          _defineProperty(this, "probing", false);

          /** ?rawmat=1：跳过 pet-toon 材质替换（诊断蒙皮/材质问题用，保留 glTF 原材质） */
          _defineProperty(this, "rawMat", false);

          _defineProperty(this, "toonAsset", null);

          /** 当前水果规格（loadPet 时按 URL 参数 / 宿主物种解析） */
          _defineProperty(this, "fruit", FRUIT_SPECS[DEFAULT_FRUIT]);

          _defineProperty(this, "urlSpecies", null);

          _defineProperty(this, "time", 0);

          _defineProperty(this, "roomTime", 0);

          _defineProperty(this, "orbSeeds", []);
        }

        start() {
          var params = new URLSearchParams(window.location.search);
          this.plain = params.get('plain') === '1';
          this.probing = params.get('probe') === '1';
          this.shot = params.get('shot') || (window.innerHeight > window.innerWidth ? 'portrait' : 'room');
          this.rootTransform = this.node.getComponent(UITransform);
          this.kit = new (_crd && PetBuilderKit === void 0 ? (_reportPossibleCrUseOfPetBuilderKit({
            error: Error()
          }), PetBuilderKit) : PetBuilderKit)(this.ccRuntime);
          this.rawMat = params.get('rawmat') === '1';
          this.urlSpecies = params.get('species'); // 自定义材质（pet-toon）必须先加载：EffectAsset.get 只能查到已加载的资产，
          // 若在加载完成前构建，所有部件会静默退化成引擎内置材质。

          resources.load('effects/pet-toon', EffectAsset, (error, asset) => {
            console.log("[pet-probe] resources.load pet-toon: err=".concat(error ? String(error) : 'none', " ") + "asset=".concat(asset ? asset.name : 'null'));

            if (!error && asset) {
              this.toonAsset = asset;
              this.kit.setToonEffect(asset);
              console.log("[pet-probe] toon ready: isToon=".concat(this.kit.isToon));
            } else {
              console.warn('[pet-game] pet-toon 加载失败，材质回退为内置材质', error);
            }

            this.buildScene(params);
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
         * 否则会出现"模型加载出来了但站着不动"或"低状态还蹦得很欢"。
         */


        loadPet() {
          var _this$pendingPet$spec, _this$pendingPet;

          // 果种解析：URL ?species=（预览强制）→ 宿主下发的 species 槽位 → 默认草莓
          this.fruit = FRUIT_SPECS[resolveFruitKey(this.urlSpecies, (_this$pendingPet$spec = (_this$pendingPet = this.pendingPet) === null || _this$pendingPet === void 0 ? void 0 : _this$pendingPet.species) !== null && _this$pendingPet$spec !== void 0 ? _this$pendingPet$spec : null)];
          var spec = this.fruit;
          resources.load(spec.path, Prefab, (error, prefab) => {
            if (error || !prefab) {
              console.warn('[pet-game] 宠物模型加载失败，场景保持无角色状态', error);
              return;
            }

            var node = instantiate(prefab);
            node.name = 'Pet';
            node.setScale(spec.scale, spec.scale, spec.scale);
            node.setPosition(PET_POS);
            this.world3d.addChild(node);
            this.petNode = node;

            if (this.probing) {
              this.dumpTree(node, 0);
            }

            if (!this.rawMat) {
              this.configurePetMaterials(node);
            } // 逐条加载剪辑。
            // ⚠️ 不能用 `resources.load([多条路径], ...)` —— 它是**全有或全无**：
            // 只要有一条路径不存在，整批都失败，结果整只宠物静止不动。猫版已实测踩到。


            var clipPaths = PET_CLIPS.map(name => "".concat(spec.dir, "/").concat(name));
            var loaded = [];
            var remaining = clipPaths.length;

            var settle = () => {
              remaining -= 1;

              if (remaining > 0) {
                return;
              }

              this.attachClips(node, loaded);
            };

            var _loop = function _loop(path) {
              resources.load(path, AnimationClip, (clipError, clip) => {
                if (clipError || !clip) {
                  console.warn("[pet-game] \u526A\u8F91\u7F3A\u5931\uFF08\u8DF3\u8FC7\uFF09: ".concat(path));
                } else {
                  loaded.push(clip);
                }

                settle();
              });
            };

            for (var path of clipPaths) {
              _loop(path);
            }
          });
        }
        /**
         * 把剪辑挂到宠物根节点上并起播。
         *
         * 组件必须挂在 `Pet`（prefab 根）而不是 `FruitRig`：剪辑里的轨道路径是
         * `FruitRig/Body/...`，从根解析才匹配；挂到 FruitRig 上会整体少一层，全部绑不上。
         */


        attachClips(node, list) {
          if (!list.length) {
            console.warn('[pet-game] 没有任何可用剪辑，宠物保持静止');
            return;
          }

          var anim = node.addComponent(SkeletalAnimation); // 用实时骨骼动画：导入的 glTF 剪辑没有烘焙贴图动画，开着会走空分支

          anim.useBakedAnimation = false;
          anim.clips = list;
          var idle = list.find(c => c.name === 'Idle');

          if (idle) {
            anim.defaultClip = idle;
          }

          console.log("[pet-probe] pet clips=[".concat(list.map(c => c.name).join(', '), "] ") + "default=".concat(idle ? idle.name : 'null'));
          this.blinkReady = list.some(c => c.name === 'Blink');
          this.petAnim = anim;
          this.playClip('Idle', true);
          this.applyStatsToAnimation();
        }
        /** 打印宠物节点树与各节点组件（?probe=1）—— glTF 导入的层级只能靠实测，不能猜 */


        dumpTree(node, depth) {
          var names = [];

          for (var c of node.components) {
            var extra = '';
            var model = c;

            if (model.material && model.material.effectAsset) {
              extra = " effect=".concat(model.material.effectAsset.name);
              var get = model.material.getProperty;

              if (get) {
                var tex = get.call(model.material, 'mainTexture');
                var albedo = get.call(model.material, 'albedo');
                extra += " tex=".concat(tex ? tex.name : 'null');
                extra += " albedo=".concat(albedo ? JSON.stringify(albedo) : 'null');
              }
            }

            if (model.skinningRoot) {
              extra += ' [skinned]';
            }

            names.push(c.constructor.name + extra);
          }

          console.log("[pet-probe] ".concat('  '.repeat(depth)).concat(node.name, " layer=").concat(node.layer, " [").concat(names.join('+') || '-', "]"));

          for (var child of node.children) {
            this.dumpTree(child, depth + 1);
          }
        }
        /**
         * 把宠物换成**和房间同一套** pet-toon 材质（颜色各自保留）。
         *
         * 为什么不沿用 glTF 导入的 `builtin-standard`：实测它在场景里渲染成惨白一片、毫无体积
         * （引擎 PBR 光照在这个 headless 管线下没有按预期参与计算，猫版已逐一排查过）。
         * 让宠物和房间共用同一个自研着色器 —— 光照语言一致、视觉完全统一，而且完全可控。
         *
         * 每块网格保留自己的 GLB 内嵌 albedo（草莓体表=种籽贴图，萼片/眼/围巾=纯色小贴图），
         * 只换着色器不换颜色。暗部色/轮廓色按草莓的玫瑰红重新调过。
         *
         * 蒙皮：pet-toon 的顶点着色器走 `CCVertInput(In)`，引擎按模型的蒙皮信息自动注入
         * `CC_USE_SKINNING`，无需手工处理。
         */


        configurePetMaterials(node) {
          var visit = n => {
            for (var comp of n.components) {
              var renderer = comp;
              var src = renderer.material;

              if (!src) {
                continue;
              }

              var embedded = src.getProperty('mainTexture');
              var toon = new Material();

              try {
                toon.initialize({
                  effectAsset: this.toonAsset,
                  technique: 0
                });
                toon.setProperty('mainColor', new Color(255, 255, 255, 255));

                if (embedded) {
                  toon.setProperty('mainTexture', embedded);
                } // 暗部**深玫瑰灰**：草莓饱和度高，暗部偏冷会发灰、偏暖会发橙，
                // 用带玫瑰倾向的暖灰保住"红而不焦"（猫版的暖灰 166,148,130 在这里会脏）


                toon.setProperty('shadeColor', new Color(190, 118, 130, 255));
                toon.setProperty('shadeCtrl', new Vec4(0.72, 0.12, 0.16, 0.12));
                toon.setProperty('outlineCtrl', new Vec4(0.0035, 0, 0, 0));
                toon.setProperty('outlineColor', new Color(128, 72, 84, 255)); // 高光略强略聚（软陶/果蜡质感）

                toon.setProperty('furCtrl', new Vec4(0.16, 2.0, 0.06, 10.0)); // 主光方向：与房间一致（左前上主光 + 右前下冷补光），猫版验证过的参数

                toon.setProperty('lightDir', new Vec4(-0.85, 0.30, 0.32, 0.0));
                toon.setProperty('fillDir', new Vec4(0.42, -0.18, 0.86, 0.0));
                toon.setProperty('lightCtrl', new Vec4(0.88, 0.28, 0.14, 0.14)); // ⚠️ mapCtrl 必须放在**所有 setProperty 之后**：材质 uniform 在首次绑定后
                // 才同步"最后一次写入"的值（猫版已实测多轮）

                toon.setProperty('mapCtrl', new Vec4(1, 0, 0, 1)); // ⚠️ 必须走 setMaterial 显式替换：模型是异步加载的，首帧可能已经渲过，
                // `.material = toon` 赋值不会触发蒙皮网格的渲染侧重绑

                renderer.setMaterial(toon, 0);
              } catch (error) {
                console.warn('[pet-game] 宠物 pet-toon 材质初始化失败，保留原材质', error);
                continue;
              }

              var texBack = toon.getProperty('mainTexture');
              console.log("[pet-probe] pet material ".concat(n.name, ": pet-toon ") + "tex=".concat(texBack ? texBack.uuid : 'null'));
            }

            for (var child of n.children) {
              visit(child);
            }
          };

          visit(node);
        }
        /** 播片：名字不存在时静默回落（模型可能还没带上该剪辑，不能因此崩掉整场） */


        playClip(name, loop) {
          var anim = this.petAnim;

          if (!anim) {
            return;
          }

          var has = anim.clips.some(c => c.name === name);

          if (!has) {
            return;
          }

          var state = anim.getState(name);

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
        /** 用服务端数值决定待机强度：越虚弱，动作越慢（唯一的"状态→动画"映射） */


        applyStatsToAnimation() {
          var pet = this.pendingPet;

          if (!pet) {
            return;
          }

          var worst = Math.min(pet.maxHp > 0 ? pet.hp / pet.maxHp : 1, pet.hunger / 100, pet.happiness / 100, pet.energy / 100, pet.cleanliness / 100); // 0.45(极差) ~ 1.0(健康)：线性但夹住下界，太慢会看起来卡住

          this.speedScale = Math.max(0.45, Math.min(1, 0.45 + worst * 0.55));
          var anim = this.petAnim;

          if (anim) {
            for (var clip of anim.clips) {
              var state = anim.getState(clip.name);

              if (state) {
                state.speed = this.speedScale;
              }
            }
          }
        }

        get headWorld() {
          var head = this.fruit.head;
          return new Vec3(PET_POS.x + head.x, head.y, PET_POS.z + head.z);
        } // ---------------- 探针 ----------------


        probeFraming() {
          this.scheduleOnce(() => {
            var camera = this.camera3d;

            if (!camera) {
              console.log('[pet-probe] camera missing');
              return;
            }

            var foot = camera.worldToScreen(new Vec3(PET_POS.x, 0, PET_POS.z), new Vec3());
            var top = camera.worldToScreen(new Vec3(PET_POS.x, this.fruit.height, PET_POS.z), new Vec3());
            var screenHeight = screen.windowSize.height;
            console.log("[pet-probe] cam=".concat(camera.node.position.toString(), " fov=").concat(camera.fov, " ") + "screen=".concat(screenHeight, "px foot=(").concat(foot.x.toFixed(1), ",").concat(foot.y.toFixed(1), ") ") + "top=(".concat(top.x.toFixed(1), ",").concat(top.y.toFixed(1), ") ") + "height=".concat(Math.abs(top.y - foot.y).toFixed(1), "px ") + "ratio=".concat((Math.abs(top.y - foot.y) / screenHeight * 100).toFixed(1), "%"));
          }, 1.2);
        }

        probeMaterials() {
          this.scheduleOnce(() => {
            var _getAll, _ref;

            var registry = (_getAll = (_ref = EffectAsset).getAll) === null || _getAll === void 0 ? void 0 : _getAll.call(_ref);
            var list = registry instanceof Map ? Array.from(registry.values()) : Array.isArray(registry) ? registry : [];
            console.log("[pet-probe] loaded effects (".concat(list.length, "): ") + list.map(entry => entry && entry.name).join(' | '));

            var visit = node => {
              for (var comp of node.components) {
                var _model$material$effec;

                var model = comp;

                if (!model.mesh || !model.material) {
                  continue;
                }

                console.log("[pet-probe] mat ".concat(node.name, " effect=").concat((_model$material$effec = model.material.effectAsset) === null || _model$material$effec === void 0 ? void 0 : _model$material$effec.name, " ") + "passes=".concat(model.material.passes ? model.material.passes.length : -1));
              }

              for (var child of node.children) {
                visit(child);
              }
            };

            if (this.petNode) {
              visit(this.petNode);
            }
          }, 1.5);
        }

        update(dt) {
          var step = Math.min(dt, 0.05);
          this.time += step;
          this.roomTime += step;
          this.roomAmbience(step);
          this.driveBlink(step);
        }
        /**
         * 眨眼：随机间隔触发。
         * 剪辑存在时才跑（模型可能没带上 Blink），否则会每几秒白播一次。
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
          var anim = this.petAnim;
          var state = anim.getState('Blink');

          if (state) {
            state.speed = 1;
          }

          anim.play('Blink');
        }

        onDestroy() {
          this.bridge.dispose();
        } // ---------------- 场景构建 ----------------


        buildWorld() {
          var kit = this.kit;
          var scene = this.node.scene;
          this.world3d = kit.make3dNode(scene, 'World3D', new Vec3(0, 0, 0));
          this.room = this.plain ? null : (_crd && buildRoom === void 0 ? (_reportPossibleCrUseOfbuildRoom({
            error: Error()
          }), buildRoom) : buildRoom)(this.world3d, kit);

          if (!this.plain) {
            this.buildLighting(scene);
          }

          var cameraNode = scene.getChildByName('Main3DCamera');
          this.camera3d = cameraNode ? cameraNode.getComponent(Camera) : null;

          if (cameraNode && this.camera3d) {
            var shot = CAMERA_SHOT[this.shot] || CAMERA_SHOT.room;
            cameraNode.setPosition(shot.pos[0], shot.pos[1], shot.pos[2]);
            cameraNode.lookAt(new Vec3(shot.target[0], shot.target[1], shot.target[2]), new Vec3(0, 1, 0));
            this.camera3d.clearColor = this.plain ? PLAIN_BG : new Color(0x6E, 0x5A, 0x66, 255);
          }
        }
        /**
         * 场景光照 —— **只服务宠物**。
         *
         * 房间所有部件走自研 `pet-toon`（自带写死的三点光，不依赖引擎光源）；
         * 宠物 glTF 材质必须靠引擎光源才出体积。补一盏平行光对齐 pet-toon 主光方向，
         * 因为 pet-toon 无视引擎光源，这一步对房间**零影响**。
         */


        buildLighting(scene) {
          // 主光（暖，左前上）：**必须从镜头这一侧来**（猫版实证：放窗户那侧只照亮背面）
          var key = new Node('PetKeyLight');
          key.layer = Layers.Enum.DEFAULT;
          scene.addChild(key);
          key.setPosition(-3.2, 2.5, 2.3);
          key.lookAt(new Vec3(0, 0.62, 0.35), new Vec3(0, 1, 0));
          var keyLight = key.addComponent(DirectionalLight);
          keyLight.color = new Color(255, 231, 198); // ⚠️ 强度必须压得低：无色调映射管线线性值直接 clip，过亮会把体积全顶丢

          keyLight.illuminance = 34000; // 补光（冷，右前下）：压住暗部、给一点冷暖对比

          var fill = new Node('PetFillLight');
          fill.layer = Layers.Enum.DEFAULT;
          scene.addChild(fill);
          fill.setPosition(3.0, 0.9, 2.0);
          fill.lookAt(new Vec3(0, 0.55, 0.35), new Vec3(0, 1, 0));
          var fillLight = fill.addComponent(DirectionalLight);
          fillLight.color = new Color(196, 208, 255);
          fillLight.illuminance = 7000; // 环境光：HDR / LDR 两份字段都要写（猫版实证：只写 LDR 在 HDR 分支下不生效；
          // HDR 字段在部分引擎形态下是只读 getter，直接赋值会抛 TypeError 中断 buildScene）

          var ambient = director.getScene().globals.ambient;
          var a = ambient;
          var skyLDR = new Color(200, 205, 215);
          var groundLDR = new Color(140, 112, 84);
          ambient.skyColor = skyLDR;
          ambient.groundAlbedo = groundLDR;
          ambient.skyIllum = 1400;

          var hdrWritable = (_Object$getOwnPropert => {
            var desc = (_Object$getOwnPropert = Object.getOwnPropertyDescriptor(a, 'skyColorHDR')) !== null && _Object$getOwnPropert !== void 0 ? _Object$getOwnPropert : Object.getOwnPropertyDescriptor(Object.getPrototypeOf(ambient), 'skyColorHDR');
            return !desc || desc.set !== undefined;
          })();

          if (hdrWritable) {
            a.skyColorHDR = skyLDR;
            a.groundAlbedoHDR = groundLDR;
            a.skyIllumHDR = 1400;
          } else {
            console.warn('[pet-game] HDR ambient 只读（preview 形态），跳过 HDR 双写');
          }

          console.log("[pet-probe] lighting: key=".concat(keyLight.illuminance, " fill=").concat(fillLight.illuminance, " ") + "ambLDR=".concat(ambient.skyIllum, " ambHDR=").concat(String(a.skyIllumHDR), " hdrWritable=").concat(hdrWritable));
        }
        /**
         * 宠物脚下的接触阴影。
         * 场景没开实时阴影（房间各部件靠手工软影补），宠物也必须补一个，
         * 否则它会"浮"在地毯上。用 kit.decal 的柔边贴花。草莓 footprint 略小于猫。
         */


        buildPetShadow() {
          var root = this.kit.make3dNode(this.world3d, 'PetShadow', new Vec3(0, 0, 0));
          this.kit.decal(root, 'Blob', this.fruit.shadow, {
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
          var node = new Node(name);
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

          var screenPos = this.camera3d.worldToScreen(world, new Vec3());
          var windowSize = screen.windowSize;
          var canvasSize = this.rootTransform.contentSize;

          if (!windowSize.width || !windowSize.height || !canvasSize.width) {
            return new Vec3(0, 0, 0);
          }

          var scaleX = canvasSize.width / windowSize.width;
          var scaleY = canvasSize.height / windowSize.height;
          return new Vec3(screenPos.x * scaleX - canvasSize.width / 2, screenPos.y * scaleY - canvasSize.height / 2, 0);
        }
        /** 房间氛围：光点上升循环 / 灯泡呼吸 / 阳光光斑呼吸 / 玩具球轻摆 */


        roomAmbience(dt) {
          var room = this.room;

          if (!room) {
            return;
          }

          room.orbs.forEach((orb, index) => {
            if (this.orbSeeds.length <= index) {
              this.orbSeeds.push(Math.random() * 6.28);
            }

            var seed = this.orbSeeds[index];
            var y = orb.position.y + dt * 0.16;
            orb.setPosition(orb.position.x + Math.sin(this.roomTime * 0.8 + seed) * dt * 0.1, y > 3.4 ? 0.5 : y, orb.position.z);
            var scale = 0.85 + Math.sin(this.roomTime * 1.7 + seed) * 0.15;
            orb.setScale(scale, scale, scale);
          });
          room.bulbs.forEach((bulb, index) => {
            var scale = 0.92 + Math.sin(this.roomTime * 2.1 + index * 0.7) * 0.08;
            bulb.setScale(scale, scale, scale);
          });
          var beamScale = 1 + Math.sin(this.roomTime * 0.9) * 0.06;
          room.sunBeam.setScale(beamScale, 1, beamScale);
          var toy = room.toyBall;
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