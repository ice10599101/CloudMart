System.register("chunks:///_virtual/main.js", ['./PetBuilderKit.js', './PetEffects.js', './PetGameBridge.js', './PetGameRoot.js', './PetGameTheme.js', './PetMeshFactory.js', './PetRoomBuilder.js'], function () {
  return {
    setters: [null, null, null, null, null, null, null],
    execute: function () {}
  };
});

System.register("chunks:///_virtual/PetBuilderKit.js", ['./rollupPluginModLoBabelHelpers.js', 'cc', './PetMeshFactory.js', './PetGameTheme.js'], function (exports) {
  var _createClass, _toConsumableArray, _objectSpread2, _classCallCheck, _defineProperty, cclegacy, EffectAsset, Layers, Material, Color, Vec4, Vec3, merge, radialUV, ellipsoid, spherePatch, shift;
  return {
    setters: [function (module) {
      _createClass = module.createClass;
      _toConsumableArray = module.toConsumableArray;
      _objectSpread2 = module.objectSpread2;
      _classCallCheck = module.classCallCheck;
      _defineProperty = module.defineProperty;
    }, function (module) {
      cclegacy = module.cclegacy;
      EffectAsset = module.EffectAsset;
      Layers = module.Layers;
      Material = module.Material;
      Color = module.Color;
      Vec4 = module.Vec4;
      Vec3 = module.Vec3;
    }, function (module) {
      merge = module.merge;
      radialUV = module.radialUV;
      ellipsoid = module.ellipsoid;
      spherePatch = module.spherePatch;
    }, function (module) {
      shift = module.shift;
    }],
    execute: function () {
      cclegacy._RF.push({}, "36d45RC16lG1Led9iTiqfBu", "PetBuilderKit", undefined);

      /**
       * 3D 建模基础设施（材质工厂 + 几何工具）。
       *
       * 视觉重构 v5 的关键变化：
       *  - 几何：surf() 提交手写连续曲面（旋转体/扫掠/椭球），surfGroup() 把同材质的成组零件
       *    合并为一个网格（减少 draw call），decal() 提供"柔和贴花"（腮红/高光/软影）；
       *  - 材质：pet-toon 提供屏幕恒定描边、三点式光照（暖主光 + 冷补光 + 克制背光）、
       *    色区柔化（曲面上的柔和明度过渡，替代生硬色块）与弱而宽的主体高光。
       *
       * Cocos 4.0 alpha 运行时门面与 3.x DTS 的命名差异适配：
       *  MeshRenderer→ModelComponent、MeshUtils 在 cc.utils、材质经 Material.initialize({ effectAsset })。
       */

      /** 部件风格：颜色 + 明暗/质感/透明等表现参数 */

      /** 默认描边宽度（屏幕空间 NDC）：只做轮廓"收边"，不出现硬黑边 */
      var DEFAULT_OUTLINE = 0.0045;

      /** 3D 场景构建工具：节点/网格/材质工厂（宠物与房间共用同一套视觉语言） */
      var PetBuilderKit = exports('PetBuilderKit', /*#__PURE__*/function () {
        function PetBuilderKit(cc) {
          _classCallCheck(this, PetBuilderKit);
          _defineProperty(this, "cc", void 0);
          _defineProperty(this, "toonEffect", null);
          _defineProperty(this, "fallbackEffect", null);
          this.cc = cc;
        }

        /** pet-toon 是否生效（供调试与降级分支判断） */
        return _createClass(PetBuilderKit, [{
          key: "isToon",
          get: function get() {
            return !!this.toonEffect;
          }

          /**
           * 注入已加载的 pet-toon effect。
           *
           * 自定义 effect 必须由宿主先从 resources 加载（`EffectAsset.get` 只能查到**已加载**的资产），
           * 因此场景构建要等它就绪；未注入时材质自动回退内置材质（Fail-Open）。
           */
        }, {
          key: "setToonEffect",
          value: function setToonEffect(effect) {
            this.toonEffect = effect;
          }

          /**
           * 解析工程内的 pet-toon effect。
           *
           * 注意：cocos-cli 构建产物里的 effect 资产名可能带路径前缀（由导入器按 DB 相对路径命名），
           * 因此不能只依赖 `EffectAsset.get('pet-toon')`；这里追加一次"按名后缀匹配"的回退，
           * 否则材质会静默退化成内置材质（视觉表现与工程内 effect 完全不同）。
           */
        }, {
          key: "resolveToonEffect",
          value: function resolveToonEffect() {
            if (this.toonEffect) {
              return this.toonEffect;
            }
            var found = EffectAsset.get('pet-toon') || null;
            if (!found) {
              var _getAll, _ref;
              var registry = (_getAll = (_ref = EffectAsset).getAll) === null || _getAll === void 0 ? void 0 : _getAll.call(_ref);
              var list = registry instanceof Map ? Array.from(registry.values()) : Array.isArray(registry) ? registry : [];
              found = list.find(function (entry) {
                return entry && typeof entry.name === 'string' && entry.name.endsWith('pet-toon');
              }) || null;
            }
            this.toonEffect = found;
            return found;
          }

          /** 该 effect 是否为工程内的 pet-toon（技术索引与属性名都按它来） */
        }, {
          key: "isToonEffect",
          value: function isToonEffect(effect) {
            return !!effect && typeof effect.name === 'string' && effect.name.endsWith('pet-toon');
          }

          /**
           * 引擎内置材质（Fail-Open 兜底）：未打包/加载失败时材质仍可用，
           * 只是失去 pet-toon 的描边/绒光/光照表现。
           */
        }, {
          key: "pickFallbackEffect",
          value: function pickFallbackEffect(sample) {
            if (!this.fallbackEffect) {
              try {
                var builtin = sample._getBuiltinMaterial();
                this.fallbackEffect = builtin && builtin.effectAsset ? builtin.effectAsset : null;
              } catch (e) {
                // 兜底材质尚未就绪：保持 null，下一次 attach 再试
              }
            }
            return this.fallbackEffect;
          }

          /** 3D 节点（DEFAULT 层，挂到父节点） */
        }, {
          key: "make3dNode",
          value: function make3dNode(parent, name, pos, transform) {
            var node = new this.cc.Node(name);
            node.layer = Layers.Enum.DEFAULT;
            node.setPosition(pos);
            if (transform !== null && transform !== void 0 && transform.rot) {
              node.setRotationFromEuler(transform.rot.x, transform.rot.y, transform.rot.z);
            }
            if (transform !== null && transform !== void 0 && transform.scale) {
              node.setScale(transform.scale.x, transform.scale.y, transform.scale.z);
            }
            parent.addChild(node);
            return node;
          }

          /** 组装网格 + 卡通材质（材质策略见类注释） */
        }, {
          key: "attach",
          value: function attach(node, geometry, style) {
            var model = node.addComponent(this.cc.ModelComponent);
            model.mesh = this.cc.utils.createMesh(geometry);
            var transparent = style.blend === true || style.alpha !== undefined && style.alpha < 255;
            // plain：强制引擎内置材质（标准透明路径）；其余部件优先 pet-toon。
            // 注意：必须以**最终选中的 effect** 判断技术索引 —— 若用"解析结果是否为空"判断，
            // 一旦出现"解析失败但兜底拿到 pet-toon"的组合，technique 索引就会越界，
            // 材质初始化静默失败并退回内置材质（曾导致所有 toon 表现失效）。
            var effect = style.plain ? this.pickFallbackEffect(model) : this.resolveToonEffect() || this.pickFallbackEffect(model);
            if (!effect) {
              return model;
            }
            var useToon = !style.plain && this.isToonEffect(effect);
            var material = new Material();
            var technique = useToon ? transparent ? 1 : 0 : transparent ? 2 : 0;
            try {
              material.initialize({
                effectAsset: effect,
                technique: technique
              });
            } catch (error) {
              // 材质初始化失败：保持未设置状态，由引擎给默认材质（Fail-Open，不阻塞场景）
              console.warn("pet-game: \u6750\u8D28\u521D\u59CB\u5316\u5931\u8D25\uFF08".concat(effect.name, " #").concat(technique, "\uFF09"), error);
              return model;
            }

            // 透明度必须并入主色：technique 选择只决定混合模式，alpha 仍然来自 mainColor.a
            var color = style.alpha === undefined || style.alpha >= 255 ? style.color : new Color(style.color.r, style.color.g, style.color.b, style.alpha);
            var shade = style.glow ? new Color(color.r, color.g, color.b, color.a) : style.shade || shift(color, -0.34);
            try {
              material.setProperty('mainColor', color);
              if (useToon) {
                var _style$rim, _style$top, _style$fur, _style$furSharp, _style$spec, _style$specSharp;
                material.setProperty('shadeColor', shade);
                // 明暗过渡带：0.52±0.20 —— 上一版 0.54±0.26 太宽，整张脸接近平涂，
                // 收窄后脸颊才有"亮面 → 灰面 → 暖灰紫暗面"的三段体积（仍无硬色阶）。
                material.setProperty('shadeCtrl', new Vec4(0.52, 0.20, (_style$rim = style.rim) !== null && _style$rim !== void 0 ? _style$rim : 0.13, (_style$top = style.top) !== null && _style$top !== void 0 ? _style$top : 0.14));
                // 绒光/高光：毛绒物种高绒光、光滑物种高光（见 SpeciesSpec）
                material.setProperty('furCtrl', new Vec4((_style$fur = style.fur) !== null && _style$fur !== void 0 ? _style$fur : 0.14, (_style$furSharp = style.furSharp) !== null && _style$furSharp !== void 0 ? _style$furSharp : 2.0, (_style$spec = style.spec) !== null && _style$spec !== void 0 ? _style$spec : 0.05, (_style$specSharp = style.specSharp) !== null && _style$specSharp !== void 0 ? _style$specSharp : 8));
                // 色区柔化（肚皮/口鼻的柔和明度过渡，替代生硬色块）
                if (style.mixCtrl) {
                  material.setProperty('mixCtrl', style.mixCtrl);
                }
                if (style.mixColor) {
                  material.setProperty('mixColor', style.mixColor);
                }
                // 柔和贴花（高光/软斑）：径向 alpha 衰减 + 平涂
                if (style.soft || style.flat) {
                  material.setProperty('decalCtrl', new Vec4(style.soft ? style.soft[0] : 0, style.soft ? style.soft[1] : 0, style.flat ? 1 : 0, 0));
                }
                // 面部腮红：烘焙进材质（曲面上的球形径向混色）
                // 注意：Color 的 alpha 是 0-255 整数通道，强度必须换算后再传（否则会被截断为 0）
                if (style.blush) {
                  material.setProperty('blushColor', new Color(style.blush.color.r, style.blush.color.g, style.blush.color.b, Math.round(style.blush.strength * 255)));
                  material.setProperty('blushCtrlA', style.blush.a);
                  material.setProperty('blushCtrlB', style.blush.b);
                }
                var outline = style.outline === undefined ? DEFAULT_OUTLINE : style.outline;
                material.setProperty('outlineCtrl', new Vec4(Math.max(0, outline), 0, 0, 0));
                // 描边色随主色压暗（同色系柔边，禁止发黑）
                var edge = shift(color, -0.45);
                material.setProperty('outlineColor', new Color(edge.r, edge.g, edge.b, 255));
              }
            } catch (e) {
              // 属性缺失（回退 unlit）：仅主色生效，视觉降级但不影响功能
            }
            model.material = material;
            return model;
          }

          /** 通用部件：几何 + 风格 + 变换 */
        }, {
          key: "part",
          value: function part(parent, name, geometry, style, pos, transform) {
            var node = this.make3dNode(parent, name, pos, transform);
            this.attach(node, geometry, style);
            return node;
          }

          /** 手写网格部件（旋转体/扫掠/椭球，见 PetMeshFactory） */
        }, {
          key: "surf",
          value: function surf(parent, name, geometry, style, pos, transform) {
            return this.part(parent, name, geometry, style, pos, transform);
          }

          /** 成组零件合并为一个部件：同材质 → 1 个 draw call（四肢/脚趾等成组零件，控制移动端开销） */
        }, {
          key: "surfGroup",
          value: function surfGroup(parent, name, geos, style, pos, transform) {
            return this.part(parent, name, geos.length === 1 ? geos[0] : merge.apply(void 0, _toConsumableArray(geos)), style, pos, transform);
          }

          /**
           * 柔和贴花部件（脚底软影 / 地面光斑）。
           *
           * 几何为单位椭球（uv.x 写入归一化径向距离）、由节点缩放决定实际尺寸 ——
           * 着色器据此生成径向 alpha 渐变，得到"边缘化开"的柔和色斑，而不是硬边色板。
           *
           * 注意：扁平椭球只适合贴在**平面**上；贴在凸曲面（脸颊/眼球）上请用 patch()，
           * 否则边缘会离开表面形成"浮片"。
           *
           * @param radius 三轴半径（同时作为节点缩放）
           */
        }, {
          key: "decal",
          value: function decal(parent, name, radius, style, pos, rot) {
            var _style$soft;
            var softStyle = _objectSpread2(_objectSpread2({}, style), {}, {
              outline: 0,
              fur: 0,
              spec: 0,
              flat: true,
              blend: true,
              soft: (_style$soft = style.soft) !== null && _style$soft !== void 0 ? _style$soft : [0.55, 1.0]
            });
            return this.surf(parent, name, radialUV(ellipsoid(1, 1, 1, 24, 16)), softStyle, pos, {
              rot: rot,
              scale: new Vec3(radius[0], radius[1], radius[2])
            });
          }

          /**
           * 球面贴花（贴合曲面的软色斑 / 亮片）：腮红、眼球高光。
           *
           * 几何与目标曲面同心、半径按 inflate 略放大，因此完全贴合曲率；
           * 边缘柔和度由 uv.x 的径向衰减（style.soft）控制。
           *
           * @param dir 片中心方向（球心指向片中心，局部空间）
           * @param angleU/angleV 水平/垂直张角（弧度）
           * @param radii 目标椭球三轴半径（如眼珠、头部）
           * @param inflate 相对目标表面的外扩比例（1.004 ≈ 贴合不穿插）
           */
        }, {
          key: "patch",
          value: function patch(parent, name, dir, angleU, angleV, radii, style, pos) {
            var inflate = arguments.length > 8 && arguments[8] !== undefined ? arguments[8] : 1.006;
            var geometry = spherePatch(dir, angleU, angleV, [radii[0] * inflate, radii[1] * inflate, radii[2] * inflate]);
            return this.surf(parent, name, geometry, _objectSpread2(_objectSpread2({}, style), {}, {
              outline: 0,
              fur: 0,
              spec: 0
            }), pos);
          }

          /** 球体部件 */
        }, {
          key: "ball",
          value: function ball(parent, name, radius, style, pos, transform) {
            return this.part(parent, name, this.cc.primitives.sphere(radius), style, pos, transform);
          }

          /** 方块部件（家具/墙体/地板） */
        }, {
          key: "boxPart",
          value: function boxPart(parent, name, size, style, pos, transform) {
            return this.part(parent, name, this.cc.primitives.box({
              width: size.w,
              height: size.h,
              length: size.d
            }), style, pos, transform);
          }

          /** 圆锥部件（尖耳/装饰） */
        }, {
          key: "conePart",
          value: function conePart(parent, name, radius, height, style, pos, transform) {
            return this.part(parent, name, this.cc.primitives.cone(radius, height), style, pos, transform);
          }

          /** 胶囊部件（四肢/尾巴/窗棂） */
        }, {
          key: "capPart",
          value: function capPart(parent, name, radius, height, style, pos, transform) {
            return this.part(parent, name, this.cc.primitives.capsule(radius, radius, height), style, pos, transform);
          }

          /** 圆柱部件（地毯/碗/花盆/坐垫） */
        }, {
          key: "cylPart",
          value: function cylPart(parent, name, topR, bottomR, height, style, pos, transform) {
            return this.part(parent, name, this.cc.primitives.cylinder(topR, bottomR, height), style, pos, transform);
          }

          /** 圆环部件（呼啦圈/挂环/眼镜框） */
        }, {
          key: "torusPart",
          value: function torusPart(parent, name, radius, tube, style, pos, transform) {
            return this.part(parent, name, this.cc.primitives.torus(radius, tube), style, pos, transform);
          }

          /**
           * 更新已构建部件的单个材质属性。
           *
           * 用于需要动态调整的烘焙参数（如腮红强度随情绪变化）。
           */
        }, {
          key: "setPartProperty",
          value: function setPartProperty(node, name, value) {
            var model = node.components.find(function (c) {
              return c.mesh !== undefined;
            });
            if (!model || !model.material) {
              return;
            }
            try {
              model.material.setProperty(name, value);
            } catch (e) {
              // 材质不可写：忽略（不阻塞主流程）
            }
          }

          /** 更新已有部件的材质颜色（换肤/情绪变色用；透明部件同步 alpha） */
        }, {
          key: "recolor",
          value: function recolor(node, color, shade, alpha) {
            var model = node.components.find(function (c) {
              return c.mesh !== undefined;
            });
            if (!model || !model.material) {
              return;
            }
            var next = alpha === undefined ? color : new Color(color.r, color.g, color.b, alpha);
            try {
              model.material.setProperty('mainColor', next);
              if (shade && this.isToon) {
                model.material.setProperty('shadeColor', shade);
              }
            } catch (e) {
              // 材质不可写：忽略（不阻塞主流程）
            }
          }
        }]);
      }());
      cclegacy._RF.pop();
    }
  };
});

System.register("chunks:///_virtual/PetEffects.js", ['./rollupPluginModLoBabelHelpers.js', 'cc'], function (exports) {
  var _createClass, _classCallCheck, _defineProperty, cclegacy, Color, Graphics, Label, UIOpacity, tween, Vec3;
  return {
    setters: [function (module) {
      _createClass = module.createClass;
      _classCallCheck = module.classCallCheck;
      _defineProperty = module.defineProperty;
    }, function (module) {
      cclegacy = module.cclegacy;
      Color = module.Color;
      Graphics = module.Graphics;
      Label = module.Label;
      UIOpacity = module.UIOpacity;
      tween = module.tween;
      Vec3 = module.Vec3;
    }],
    execute: function () {
      cclegacy._RF.push({}, "92b70HK6j1Aer6obP8bq7lQ", "PetEffects", undefined);

      /**
       * 2D 特效层（视觉重构 v3）：爱心 / 星星 / 泡泡 / 睡意 / 食物 / 数值浮字 / 光圈。
       *
       * 设计原则：
       *  - 所有反馈都"从宠物身上长出来"：调用方传入 3D 世界坐标，本层换算为 UI 坐标，
       *    保证粒子与宠物在屏幕上严格对齐（不同窗口尺寸下依然贴合）；
       *  - 弹出现（backOut）→ 飘移（正弦侧移）→ 渐隐销毁，三段式，避免机械直线运动；
       *  - 全部 Graphics 程序化绘制（零贴图资产），形状即品牌：圆角爱心 + 五角星 + 泡泡。
       */

      /** 世界坐标 → UI 坐标换算（由 PetGameRoot 注入，内部用 3D 相机投影） */

      var PetEffects = exports('PetEffects', /*#__PURE__*/function () {
        function PetEffects(makeNode, toUi) {
          _classCallCheck(this, PetEffects);
          _defineProperty(this, "makeNode", void 0);
          _defineProperty(this, "toUi", void 0);
          this.makeNode = makeNode;
          this.toUi = toUi;
        }

        /** 爱心群：被抚摸 / 开心 / 进食（从宠物头顶冒出，带随机侧移） */
        return _createClass(PetEffects, [{
          key: "hearts",
          value: function hearts(world) {
            var count = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 3;
            var color = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : new Color(255, 122, 158, 255);
            for (var index = 0; index < count; index += 1) {
              var offset = this.toUi(world);
              var node = this.makeNode('fx-heart', offset.x + (index - (count - 1) / 2) * 26, offset.y);
              var g = node.addComponent(Graphics);
              this.drawHeart(g, 30 + index * 3, color);
              this.popAndRise(node, 0.08 * index, 120 + index * 26, index % 2 === 0 ? 26 : -26);
            }
          }

          /** 星星群：升级 / 成就 / 玩耍（四散爆开） */
        }, {
          key: "stars",
          value: function stars(world) {
            var _this = this;
            var count = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 6;
            var color = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : new Color(255, 214, 110, 255);
            var origin = this.toUi(world);
            var _loop = function _loop() {
              var angle = Math.PI * 2 * index / count - Math.PI / 2;
              var node = _this.makeNode('fx-star', origin.x, origin.y);
              var g = node.addComponent(Graphics);
              _this.drawStar(g, 13 + index % 3 * 3, color);
              node.setScale(0.1, 0.1, 1);
              var opacity = node.addComponent(UIOpacity);
              tween(node).delay(0.05 * index).to(0.22, {
                scale: new Vec3(1.1, 1.1, 1)
              }, {
                easing: 'backOut'
              }).to(0.16, {
                scale: new Vec3(1, 1, 1)
              }, {
                easing: 'sineOut'
              }).by(0.75, {
                position: new Vec3(Math.cos(angle) * (70 + index * 6), Math.sin(angle) * 56 + 46, 0)
              }, {
                easing: 'sineOut'
              }).call(function () {
                return node.destroy();
              }).start();
              tween(opacity).delay(0.5).to(0.5, {
                opacity: 0
              }).start();
            };
            for (var index = 0; index < count; index += 1) {
              _loop();
            }
          }

          /** 泡泡群：清洁（从宠物周身向上飘散，带螺旋侧移） */
        }, {
          key: "bubbles",
          value: function bubbles(world) {
            var _this2 = this;
            var count = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 9;
            var origin = this.toUi(world);
            var _loop2 = function _loop2() {
              var node = _this2.makeNode('fx-bubble', origin.x + (index % 5 - 2) * 34, origin.y - 20);
              var g = node.addComponent(Graphics);
              _this2.drawBubble(g, 9 + index % 4 * 4);
              var opacity = node.addComponent(UIOpacity);
              opacity.opacity = 220;
              node.setScale(0.2, 0.2, 1);
              var sway = index % 2 === 0 ? 30 : -30;
              tween(node).delay(0.07 * index).to(0.2, {
                scale: new Vec3(1, 1, 1)
              }, {
                easing: 'backOut'
              }).by(0.9, {
                position: new Vec3(sway, 130 + index % 3 * 30, 0)
              }, {
                easing: 'sineOut'
              }).call(function () {
                return node.destroy();
              }).start();
              tween(opacity).delay(0.4).to(0.7, {
                opacity: 0
              }).start();
            };
            for (var index = 0; index < count; index += 1) {
              _loop2();
            }
          }

          /** 睡意 Zzz：睡觉时循环冒出（由调用方按间隔重复调用） */
        }, {
          key: "sleepZ",
          value: function sleepZ(world) {
            var phase = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 0;
            var origin = this.toUi(world);
            var node = this.makeNode('fx-zzz', origin.x + 30, origin.y + 10);
            var label = node.addComponent(Label);
            label.string = phase % 2 === 0 ? 'Z' : 'z';
            label.fontSize = phase % 2 === 0 ? 30 : 22;
            label.color = new Color(150, 132, 190, 255);
            var opacity = node.addComponent(UIOpacity);
            opacity.opacity = 235;
            tween(node).by(1.1, {
              position: new Vec3(26, 82, 0)
            }, {
              easing: 'sineOut'
            }).call(function () {
              return node.destroy();
            }).start();
            tween(opacity).delay(0.5).to(0.6, {
              opacity: 0
            }).start();
          }

          /** 食物：从画面下方飞到宠物嘴边（喂食演出） */
        }, {
          key: "food",
          value: function food(world, icon, onArrive) {
            var target = this.toUi(world);
            var node = this.makeNode('fx-food', target.x - 90, target.y - 120);
            var label = node.addComponent(Label);
            label.string = icon;
            label.fontSize = 46;
            node.setScale(0.5, 0.5, 1);
            var opacity = node.addComponent(UIOpacity);
            tween(node).to(0.34, {
              position: new Vec3(target.x, target.y - 40, 0),
              scale: new Vec3(1, 1, 1)
            }, {
              easing: 'quadOut'
            }).call(function () {
              return onArrive && onArrive();
            }).delay(0.5).to(0.24, {
              position: new Vec3(target.x, target.y - 20, 0),
              scale: new Vec3(0.2, 0.2, 1)
            }, {
              easing: 'sineIn'
            }).call(function () {
              return node.destroy();
            }).start();
            tween(opacity).delay(1.0).to(0.24, {
              opacity: 0
            }).start();
          }

          /** 数值/结果浮字：上飘渐隐（带轻微侧摆，避免机械直线） */
        }, {
          key: "floatText",
          value: function floatText(world, text) {
            var color = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : new Color(255, 246, 226, 255);
            var fontSize = arguments.length > 3 && arguments[3] !== undefined ? arguments[3] : 24;
            var origin = this.toUi(world);
            var node = this.makeNode('fx-text', origin.x, origin.y + 30);
            var label = node.addComponent(Label);
            label.string = text;
            label.fontSize = fontSize;
            label.lineHeight = Math.round(fontSize * 1.2);
            label.color = color;
            var opacity = node.addComponent(UIOpacity);
            tween(node).to(0.34, {
              position: new Vec3(origin.x + 12, origin.y + 76, 0)
            }, {
              easing: 'sineOut'
            }).to(0.5, {
              position: new Vec3(origin.x - 6, origin.y + 122, 0)
            }, {
              easing: 'sineInOut'
            }).call(function () {
              return node.destroy();
            }).start();
            tween(opacity).delay(0.5).to(0.45, {
              opacity: 0
            }).start();
          }

          /** 光圈扩散：点击/升级的冲击波（一圈放大淡出的圆环） */
        }, {
          key: "ring",
          value: function ring(world) {
            var color = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : new Color(255, 226, 160, 255);
            var origin = this.toUi(world);
            var node = this.makeNode('fx-ring', origin.x, origin.y);
            var g = node.addComponent(Graphics);
            g.lineWidth = 4;
            g.strokeColor = color;
            g.circle(0, 0, 26);
            g.stroke();
            var opacity = node.addComponent(UIOpacity);
            tween(node).to(0.42, {
              scale: new Vec3(2.6, 2.6, 1)
            }, {
              easing: 'sineOut'
            }).call(function () {
              return node.destroy();
            }).start();
            tween(opacity).to(0.42, {
              opacity: 0
            }).start();
          }

          /** 小闪点：任意交互的通用点缀（随机短距离外爆） */
        }, {
          key: "sparkle",
          value: function sparkle(world) {
            var _this3 = this;
            var count = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 4;
            var origin = this.toUi(world);
            var _loop3 = function _loop3() {
              var node = _this3.makeNode('fx-spark', origin.x, origin.y);
              var g = node.addComponent(Graphics);
              _this3.drawStar(g, 7, new Color(255, 255, 255, 255));
              var opacity = node.addComponent(UIOpacity);
              var angle = Math.random() * Math.PI * 2;
              var distance = 40 + Math.random() * 40;
              tween(node).by(0.5, {
                position: new Vec3(Math.cos(angle) * distance, Math.sin(angle) * distance, 0)
              }, {
                easing: 'sineOut'
              }).call(function () {
                return node.destroy();
              }).start();
              tween(opacity).to(0.5, {
                opacity: 0
              }).start();
            };
            for (var index = 0; index < count; index += 1) {
              _loop3();
            }
          }

          // ---------------- 形状绘制 ----------------

          /** 心形（两段贝塞尔，圆润饱满） */
        }, {
          key: "drawHeart",
          value: function drawHeart(g, size, color) {
            var s = size / 30;
            g.fillColor = color;
            g.moveTo(0, 6 * s);
            g.bezierCurveTo(8 * s, 16 * s, 22 * s, 8 * s, 0, -14 * s);
            g.bezierCurveTo(-22 * s, 8 * s, -8 * s, 16 * s, 0, 6 * s);
            g.fill();
          }

          /** 五角星 */
        }, {
          key: "drawStar",
          value: function drawStar(g, radius, color) {
            g.fillColor = color;
            var points = [];
            for (var index = 0; index < 10; index += 1) {
              var r = index % 2 === 0 ? radius : radius * 0.46;
              var angle = -Math.PI / 2 + index * Math.PI / 5;
              points.push(Math.cos(angle) * r, Math.sin(angle) * r);
            }
            g.moveTo(points[0], points[1]);
            for (var _index = 2; _index < points.length; _index += 2) {
              g.lineTo(points[_index], points[_index + 1]);
            }
            g.close();
            g.fill();
          }

          /** 泡泡（描边圆 + 左上高光点） */
        }, {
          key: "drawBubble",
          value: function drawBubble(g, radius) {
            g.lineWidth = 2.4;
            g.strokeColor = new Color(206, 234, 255, 235);
            g.fillColor = new Color(206, 234, 255, 52);
            g.circle(0, 0, radius);
            g.fill();
            g.stroke();
            g.fillColor = new Color(255, 255, 255, 210);
            g.circle(-radius * 0.32, radius * 0.34, Math.max(1.6, radius * 0.18));
            g.fill();
          }

          /** 弹出 → 飘移 → 渐隐销毁（爱心专用节奏） */
        }, {
          key: "popAndRise",
          value: function popAndRise(node, delay, rise, drift) {
            var opacity = node.addComponent(UIOpacity);
            node.setScale(0.2, 0.2, 1);
            tween(node).delay(delay).to(0.2, {
              scale: new Vec3(1.18, 1.18, 1)
            }, {
              easing: 'backOut'
            }).to(0.14, {
              scale: new Vec3(1, 1, 1)
            }, {
              easing: 'sineOut'
            }).by(0.86, {
              position: new Vec3(drift, rise, 0)
            }, {
              easing: 'sineOut'
            }).call(function () {
              return node.destroy();
            }).start();
            tween(opacity).delay(delay + 0.5).to(0.5, {
              opacity: 0
            }).start();
          }
        }]);
      }());
      cclegacy._RF.pop();
    }
  };
});

System.register("chunks:///_virtual/PetGameBridge.js", ['./rollupPluginModLoBabelHelpers.js', 'cc'], function (exports) {
  var _createClass, _classCallCheck, _defineProperty, cclegacy;
  return {
    setters: [function (module) {
      _createClass = module.createClass;
      _classCallCheck = module.classCallCheck;
      _defineProperty = module.defineProperty;
    }, function (module) {
      cclegacy = module.cclegacy;
    }],
    execute: function () {
      cclegacy._RF.push({}, "743bdZ6W2FG+6h8KlSCZnG/", "PetGameBridge", undefined);
      /**
       * PetGameBridge：Cocos 场景与三端宿主（Web iframe / App WebView / 小程序 web-view）
       * 之间的唯一通信契约（实施文档 §2.2）。
       *
       * 职责边界：
       *  - 游戏 → 宿主只发"意图"（intent），绝不携带业务数值、绝不直接调 API；
       *  - 宿主 → 游戏只发"状态/结果"（服务端权威数据），游戏只做展示与动画。
       */
      /** 宿主下发的宠物展示状态（服务端 PetVO 直接映射，客户端零计算） */
      /** 服务端战斗引擎回合流水（客户端只播放，可跳过） */
      /** 游戏可发起的意图（宿主据此调用 mall-pet API） */
      var PetGameBridge = exports('PetGameBridge', /*#__PURE__*/function () {
        function PetGameBridge() {
          var _this = this;
          _classCallCheck(this, PetGameBridge);
          _defineProperty(this, "hostHandler", null);
          _defineProperty(this, "onMessage", function (event) {
            // P05：来源校验——message 通道仅服务 Web iframe 场景，pet-game 与宿主
            // 同源静态部署，origin 必须等于本窗口 origin；跨源投递一律丢弃
            //（App/RN 宿主走 __petHostMessage 注入、小程序走 wx 通道，均不经本监听）
            if (event.origin !== window.location.origin) {
              return;
            }
            _this.dispatch(event.data);
          });
        }
        return _createClass(PetGameBridge, [{
          key: "detectHost",
          value: /** 宿主环境：iframe(web) / ReactNativeWebView(app) / wx.miniProgram(weapp) */
          function detectHost() {
            var w = window;
            if (w.wx && w.wx.miniProgram) {
              return 'weapp';
            }
            if (w.ReactNativeWebView) {
              return 'app';
            }
            return 'web';
          }

          /** 接收宿主消息（iframe postMessage + RN injectJavaScript 双通道） */
        }, {
          key: "bind",
          value: function bind(hostHandler) {
            var _this2 = this;
            this.hostHandler = hostHandler;
            window.addEventListener('message', this.onMessage);
            // App 宿主通过 injectJavaScript 调用该入口（Web 端 iframe 场景同样可用）
            window.__petHostMessage = function (raw) {
              if (typeof raw === 'string') {
                try {
                  _this2.dispatch(JSON.parse(raw));
                } catch (e) {
                  // 非法 JSON 直接忽略（宿主脚本注入异常不崩溃场景）
                  console.warn('[PetGameBridge] invalid host message ignored');
                }
              } else {
                _this2.dispatch(raw);
              }
            };
          }

          /** 卸载（组件销毁时调用，避免事件监听泄漏） */
        }, {
          key: "dispose",
          value: function dispose() {
            window.removeEventListener('message', this.onMessage);
            this.hostHandler = null;
            delete window.__petHostMessage;
          }
        }, {
          key: "dispatch",
          value: function dispatch(data) {
            var msg = data;
            if (!msg || msg.source !== 'pet-host' || !this.hostHandler) {
              return;
            }
            this.hostHandler(msg);
          }

          /** 发送消息到宿主（按环境选通道；weapp 的实时通道是 navigateTo 语义，见 README） */
        }, {
          key: "send",
          value: function send(message) {
            var host = this.detectHost();
            var w = window;
            if (host === 'app' && w.ReactNativeWebView) {
              w.ReactNativeWebView.postMessage(JSON.stringify(message));
              return;
            }
            if (host === 'weapp' && w.wx && w.wx.miniProgram) {
              w.wx.miniProgram.postMessage({
                data: message
              });
              if (message.type === 'intent') {
                // 微信 web-view 的 postMessage 仅在回退/分享时机投递；实时意图走 navigateTo 落到原生页
                w.wx.miniProgram.navigateTo({
                  url: '/pages/pet/index?intent=' + message.action
                });
              }
              return;
            }
            // Web iframe（仅在真实 iframe 内发送）。targetOrigin 用 '*'：
            // 宿主源随端而异（Taro 舞台=同源、Expo Web=独立源），收紧会在异源宿主下被浏览器丢弃；
            // 安全边界在宿主侧（宿主只接受白名单 origin 的消息）。
            if (window.parent && window.parent !== window) {
              window.parent.postMessage(message, '*');
            }
          }
        }]);
      }());
      cclegacy._RF.pop();
    }
  };
});

System.register("chunks:///_virtual/PetGameRoot.js", ['./rollupPluginModLoBabelHelpers.js', 'cc', './PetGameBridge.js', './PetBuilderKit.js', './PetEffects.js', './PetRoomBuilder.js'], function (exports) {
  var _defineProperty, _inherits, _createClass, _createForOfIteratorHelper, _classCallCheck, _callSuper, cclegacy, _decorator, Vec3, Color, UITransform, resources, EffectAsset, input, Input, Prefab, instantiate, AnimationClip, SkeletalAnimation, screen, Camera, Node, Layers, DirectionalLight, director, Component, tween, Material, Vec4, PetGameBridge, PetBuilderKit, PetEffects, buildRoom;
  return {
    setters: [function (module) {
      _defineProperty = module.defineProperty;
      _inherits = module.inherits;
      _createClass = module.createClass;
      _createForOfIteratorHelper = module.createForOfIteratorHelper;
      _classCallCheck = module.classCallCheck;
      _callSuper = module.callSuper;
    }, function (module) {
      cclegacy = module.cclegacy;
      _decorator = module._decorator;
      Vec3 = module.Vec3;
      Color = module.Color;
      UITransform = module.UITransform;
      resources = module.resources;
      EffectAsset = module.EffectAsset;
      input = module.input;
      Input = module.Input;
      Prefab = module.Prefab;
      instantiate = module.instantiate;
      AnimationClip = module.AnimationClip;
      SkeletalAnimation = module.SkeletalAnimation;
      screen = module.screen;
      Camera = module.Camera;
      Node = module.Node;
      Layers = module.Layers;
      DirectionalLight = module.DirectionalLight;
      director = module.director;
      Component = module.Component;
      tween = module.tween;
      Material = module.Material;
      Vec4 = module.Vec4;
    }, function (module) {
      PetGameBridge = module.PetGameBridge;
    }, function (module) {
      PetBuilderKit = module.PetBuilderKit;
    }, function (module) {
      PetEffects = module.PetEffects;
    }, function (module) {
      buildRoom = module.buildRoom;
    }],
    execute: function () {
      var _dec, _class, _PetGameRoot;
      cclegacy._RF.push({}, "de28bIpZkFFg6SBhvUEChP3", "PetGameRoot", undefined);
      var ccclass = _decorator.ccclass;

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
      var PET_CLIPS = ['Idle', 'Happy', 'Blink'];
      /** 宠物站位（地毯中央；与房间里的软影、玩具球对齐） */
      var PET_POS = new Vec3(0, 0, 0.35);

      /** 水果规格：GLB 路径（`目录/文件名/文件名` 三段式）/ 缩放 / 身高 / 头部特效锚点 / 接触阴影尺寸 */

      /** 五果规格表（建模值 × 缩放，与 tools/fruit-pipeline 各 build 脚本一一对应） */
      var FRUIT_SPECS = {
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
      var DEFAULT_FRUIT = 'strawberry';
      /**
       * 物种槽位映射：mall-pet 的 species 枚举（CAT/DOG/RABBIT/FOX/PANDA）迁移成水果码之前，
       * 按固定顺序把 5 个动物槽位映射到 5 只水果（每个 DB 宠物各得一只果）；
       * 枚举迁移落地后枚举值本身就是水果码，同表直查。URL `?species=orange` 可强制指定预览。
       */
      var SPECIES_SLOT = {
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
      /**
       * 相机机位。
       *
       * v9：房间内容齐备 + 页面拉宽（16:10 舞台更宽），机位再拉远到能收进"完整的家"：
       * 后墙从踢脚线到顶线、两侧家具、地毯全貌；两侧近景墙体自然形成画框。
       * 竖屏另给一组：竖屏横向窄，同距离下再抬高一点。
       */
      var CAMERA_SHOT = {
        room: {
          pos: [1.15, 2.25, 7.0],
          target: [0, 1.05, -0.2]
        },
        // 竖屏拉远：手机 web-view 竖屏可视横宽窄，z=10.5 才能收进完整的家（后墙+两侧家具+地毯）
        portrait: {
          pos: [2.0, 2.6, 10.5],
          target: [0, 1.15, -0.2]
        },
        front: {
          pos: [0, 1.6, 6.2],
          target: [0, 1.05, -0.1]
        },
        q34: {
          pos: [-3.0, 1.9, 5.6],
          target: [0, 1.0, 0.1]
        }
      };

      /** 纯色背景（?plain=1 验收模式：去掉房间，只留角色自证轮廓与材质） */
      var PLAIN_BG = new Color(0xCF, 0xC9, 0xD6, 255);
      var PetGameRoot = exports('PetGameRoot', (_dec = ccclass('PetGameRoot'), _dec(_class = (_PetGameRoot = /*#__PURE__*/function (_Component) {
        function PetGameRoot() {
          var _this;
          _classCallCheck(this, PetGameRoot);
          for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
            args[_key] = arguments[_key];
          }
          _this = _callSuper(this, PetGameRoot, [].concat(args));
          _defineProperty(_this, "bridge", new PetGameBridge());
          _defineProperty(_this, "ccRuntime", globalThis.cc);
          _defineProperty(_this, "kit", null);
          _defineProperty(_this, "world3d", null);
          _defineProperty(_this, "room", null);
          _defineProperty(_this, "camera3d", null);
          _defineProperty(_this, "rootTransform", null);
          _defineProperty(_this, "effects", null);
          /** 宠物节点（模型异步加载完成前为 null） */
          _defineProperty(_this, "petNode", null);
          _defineProperty(_this, "tapCooldownAt", 0);
          _defineProperty(_this, "tapCombo", 0);
          _defineProperty(_this, "tapComboAt", 0);
          /** 防刷：固定 4 小时周期（自然时间对齐 0/4/8/16/20 点边界），每周期无论怎么摸只结算前 3 次 */
          _defineProperty(_this, "rewardPeriodKey", -1);
          _defineProperty(_this, "rewardUsedInPeriod", 0);
          _defineProperty(_this, "petAnim", null);
          /** 最近一次下发的状态：模型加载是异步的，到位后要用它补播正确的动画 */
          _defineProperty(_this, "pendingPet", null);
          /** 0.35~1.0：数值低时把待机动作放慢（蔫、没精神），是全片唯一的"状态→动画"映射 */
          _defineProperty(_this, "speedScale", 1);
          _defineProperty(_this, "blinkTimer", 3.5);
          _defineProperty(_this, "blinkReady", false);
          _defineProperty(_this, "plain", false);
          _defineProperty(_this, "shot", 'room');
          _defineProperty(_this, "probing", false);
          /** ?rawmat=1：跳过 pet-toon 材质替换（诊断蒙皮/材质问题用，保留 glTF 原材质） */
          _defineProperty(_this, "rawMat", false);
          _defineProperty(_this, "toonAsset", null);
          /** 当前水果规格（loadPet 时按 URL 参数 / 宿主物种解析） */
          _defineProperty(_this, "fruit", FRUIT_SPECS[DEFAULT_FRUIT]);
          _defineProperty(_this, "urlSpecies", null);
          _defineProperty(_this, "time", 0);
          _defineProperty(_this, "roomTime", 0);
          /**
           * 点击宠物本体：屏幕触点 → 相机射线 → 与宠物包围球求交（手写向量数学，零物理依赖）。
           * 连击设计：2.5s 内连续命中递增 combo——反应文案随层数变化，3 层起加星星，
           * 5 连触发彩蛋（开心到转圈圈）；combo 封顶 8。每次命中都通知宿主执行玩耍互动（服务端权威计次）。
           */
          _defineProperty(_this, "onStageTap", function (event) {
            if (!_this.camera3d || !_this.petNode) return;
            var now = Date.now();
            if (now - _this.tapCooldownAt < 900) return;
            _this.tapCooldownAt = now;
            var combo = now - _this.tapComboAt < 2500 ? Math.min(8, _this.tapCombo + 1) : 1;
            _this.tapCombo = combo;
            _this.tapComboAt = now;
            var ui = event.getUILocation();
            var ray = _this.camera3d.screenPointToRay(ui.x, ui.y);
            var wp = _this.petNode.worldPosition;
            var centerY = wp.y + _this.fruit.height * 0.45;
            var radius = _this.fruit.height * 0.66;
            var ox = ray.o.x - wp.x;
            var oy = ray.o.y - centerY;
            var oz = ray.o.z - wp.z;
            var t = -(ox * ray.d.x + oy * ray.d.y + oz * ray.d.z);
            if (t <= 0) return;
            var px = ox + ray.d.x * t;
            var py = oy + ray.d.y * t;
            var pz = oz + ray.d.z * t;
            if (px * px + py * py + pz * pz > radius * radius) return;
            _this.playClip('Happy', false);
            var lines = PetGameRoot.TAP_LINES;
            var line = lines[Math.min(combo - 1, lines.length - 1)];
            _this.effects && _this.effects.floatText(_this.headWorld, line, new Color(255, 250, 240, 255), 20);
            _this.effects && _this.effects.sparkle(_this.headWorld, 3 + combo);
            if (combo >= 3) {
              _this.effects && _this.effects.stars(_this.headWorld, combo);
            }
            if (combo === 5) {
              // 彩蛋：开心到转圈圈（Y 轴整圈后自然复位，两次 by 各半圈）
              var node = _this.petNode;
              tween(node).by(0.42, {
                eulerAngles: new Vec3(0, 180, 0)
              }).by(0.42, {
                eulerAngles: new Vec3(0, 180, 0)
              }).call(function () {
                node.eulerAngles = new Vec3(0, 0, 0);
              }).start();
              _this.effects && _this.effects.stars(_this.headWorld, 10);
            }
            // 防刷：固定 4 小时周期内前 3 次才发 intent 结算，周期切换自动重置，之后只保留互动特效
            var period = Math.floor(now / PetGameRoot.REWARD_PERIOD_MS);
            if (period !== _this.rewardPeriodKey) {
              _this.rewardPeriodKey = period;
              _this.rewardUsedInPeriod = 0;
            }
            if (_this.rewardUsedInPeriod < PetGameRoot.REWARD_MAX_PER_PERIOD) {
              _this.rewardUsedInPeriod += 1;
              _this.bridge.send({
                source: 'pet-game',
                type: 'intent',
                action: 'play'
              });
            }
          });
          return _this;
        }
        _inherits(PetGameRoot, _Component);
        return _createClass(PetGameRoot, [{
          key: "start",
          value: function start() {
            var _engine$director,
              _engine$director$setD,
              _this2 = this;
            // 关闭引擎调试统计面板（CLI debug 构建默认开启；正式观感不需要 FPS/Draw call 悬浮框）
            var engine = this.ccRuntime;
            (_engine$director = engine.director) === null || _engine$director === void 0 || (_engine$director$setD = _engine$director.setDisplayStats) === null || _engine$director$setD === void 0 || _engine$director$setD.call(_engine$director, false);
            var params = new URLSearchParams(window.location.search);
            this.plain = params.get('plain') === '1';
            this.probing = params.get('probe') === '1';
            this.shot = params.get('shot') || (window.innerHeight > window.innerWidth ? 'portrait' : 'room');
            this.rootTransform = this.node.getComponent(UITransform);
            this.kit = new PetBuilderKit(this.ccRuntime);
            this.rawMat = params.get('rawmat') === '1';
            this.urlSpecies = params.get('species');

            // 自定义材质（pet-toon）必须先加载：EffectAsset.get 只能查到已加载的资产，
            // 若在加载完成前构建，所有部件会静默退化成引擎内置材质。
            resources.load('effects/pet-toon', EffectAsset, function (error, asset) {
              console.log("[pet-probe] resources.load pet-toon: err=".concat(error ? String(error) : 'none', " ") + "asset=".concat(asset ? asset.name : 'null'));
              if (!error && asset) {
                _this2.toonAsset = asset;
                _this2.kit.setToonEffect(asset);
                console.log("[pet-probe] toon ready: isToon=".concat(_this2.kit.isToon));
              } else {
                console.warn('[pet-game] pet-toon 加载失败，材质回退为内置材质', error);
              }
              _this2.buildScene(params);
            });
          }
        }, {
          key: "buildScene",
          value: function buildScene(params) {
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
          }

          // ---------------- 宠物（外部绑定模型） ----------------

          /**
           * 加载并实例化宠物模型。
           *
           * 注意这是**异步**的：`ready` 会先发给宿主，模型可能稍后才到位。
           * 因此服务端下发的状态先存进 `pendingPet`，模型就绪后立刻补播正确动画 ——
           * 否则会出现"模型加载出来了但站着不动"或"低状态还蹦得很欢"。
           */
        }, {
          key: "loadPet",
          value: function loadPet() {
            var _this$pendingPet$spec,
              _this$pendingPet,
              _this3 = this;
            // 果种解析：URL ?species=（预览强制）→ 宿主下发的 species 槽位 → 默认草莓
            this.fruit = FRUIT_SPECS[resolveFruitKey(this.urlSpecies, (_this$pendingPet$spec = (_this$pendingPet = this.pendingPet) === null || _this$pendingPet === void 0 ? void 0 : _this$pendingPet.species) !== null && _this$pendingPet$spec !== void 0 ? _this$pendingPet$spec : null)];
            var spec = this.fruit;
            // 点击宠物本体 → 开心动画 + 通知宿主互动（B06 互动入口的 3D 直触形式）
            input.on(Input.EventType.TOUCH_END, this.onStageTap, this);
            resources.load(spec.path, Prefab, function (error, prefab) {
              if (error || !prefab) {
                console.warn('[pet-game] 宠物模型加载失败，场景保持无角色状态', error);
                return;
              }
              var node = instantiate(prefab);
              node.name = 'Pet';
              node.setScale(spec.scale, spec.scale, spec.scale);
              node.setPosition(PET_POS);
              _this3.world3d.addChild(node);
              _this3.petNode = node;
              if (_this3.probing) {
                _this3.dumpTree(node, 0);
              }
              if (!_this3.rawMat) {
                _this3.configurePetMaterials(node);
              }

              // 逐条加载剪辑。
              // ⚠️ 不能用 `resources.load([多条路径], ...)` —— 它是**全有或全无**：
              // 只要有一条路径不存在，整批都失败，结果整只宠物静止不动。猫版已实测踩到。
              var clipPaths = PET_CLIPS.map(function (name) {
                return "".concat(spec.dir, "/").concat(name);
              });
              var loaded = [];
              var remaining = clipPaths.length;
              var settle = function settle() {
                remaining -= 1;
                if (remaining > 0) {
                  return;
                }
                _this3.attachClips(node, loaded);
              };
              var _iterator = _createForOfIteratorHelper(clipPaths),
                _step;
              try {
                var _loop = function _loop() {
                  var path = _step.value;
                  resources.load(path, AnimationClip, function (clipError, clip) {
                    if (clipError || !clip) {
                      console.warn("[pet-game] \u526A\u8F91\u7F3A\u5931\uFF08\u8DF3\u8FC7\uFF09: ".concat(path));
                    } else {
                      loaded.push(clip);
                    }
                    settle();
                  });
                };
                for (_iterator.s(); !(_step = _iterator.n()).done;) {
                  _loop();
                }
              } catch (err) {
                _iterator.e(err);
              } finally {
                _iterator.f();
              }
            });
          }

          /**
           * 把剪辑挂到宠物根节点上并起播。
           *
           * 组件必须挂在 `Pet`（prefab 根）而不是 `FruitRig`：剪辑里的轨道路径是
           * `FruitRig/Body/...`，从根解析才匹配；挂到 FruitRig 上会整体少一层，全部绑不上。
           */
        }, {
          key: "attachClips",
          value: function attachClips(node, list) {
            if (!list.length) {
              console.warn('[pet-game] 没有任何可用剪辑，宠物保持静止');
              return;
            }
            var anim = node.addComponent(SkeletalAnimation);
            // 用实时骨骼动画：导入的 glTF 剪辑没有烘焙贴图动画，开着会走空分支
            anim.useBakedAnimation = false;
            anim.clips = list;
            var idle = list.find(function (c) {
              return c.name === 'Idle';
            });
            if (idle) {
              anim.defaultClip = idle;
            }
            console.log("[pet-probe] pet clips=[".concat(list.map(function (c) {
              return c.name;
            }).join(', '), "] ") + "default=".concat(idle ? idle.name : 'null'));
            this.blinkReady = list.some(function (c) {
              return c.name === 'Blink';
            });
            this.petAnim = anim;
            this.playClip('Idle', true);
            this.applyStatsToAnimation();
          }

          /** 打印宠物节点树与各节点组件（?probe=1）—— glTF 导入的层级只能靠实测，不能猜 */
        }, {
          key: "dumpTree",
          value: function dumpTree(node, depth) {
            var names = [];
            var _iterator2 = _createForOfIteratorHelper(node.components),
              _step2;
            try {
              for (_iterator2.s(); !(_step2 = _iterator2.n()).done;) {
                var c = _step2.value;
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
            } catch (err) {
              _iterator2.e(err);
            } finally {
              _iterator2.f();
            }
            console.log("[pet-probe] ".concat('  '.repeat(depth)).concat(node.name, " layer=").concat(node.layer, " ") + "wp=".concat(node.worldPosition.toString(), " s=").concat(node.scale.toString(), " [").concat(names.join('+') || '-', "]"));
            var _iterator3 = _createForOfIteratorHelper(node.children),
              _step3;
            try {
              for (_iterator3.s(); !(_step3 = _iterator3.n()).done;) {
                var child = _step3.value;
                this.dumpTree(child, depth + 1);
              }
            } catch (err) {
              _iterator3.e(err);
            } finally {
              _iterator3.f();
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
        }, {
          key: "configurePetMaterials",
          value: function configurePetMaterials(node) {
            var _this4 = this;
            var _visit = function visit(n) {
              var _iterator4 = _createForOfIteratorHelper(n.components),
                _step4;
              try {
                for (_iterator4.s(); !(_step4 = _iterator4.n()).done;) {
                  var comp = _step4.value;
                  var renderer = comp;
                  var src = renderer.material;
                  if (!src) {
                    continue;
                  }
                  var embedded = src.getProperty('mainTexture');
                  var toon = new Material();
                  try {
                    toon.initialize({
                      effectAsset: _this4.toonAsset,
                      technique: 0
                    });
                    toon.setProperty('mainColor', new Color(255, 255, 255, 255));
                    if (embedded) {
                      toon.setProperty('mainTexture', embedded);
                    }
                    // 暗部**深玫瑰灰**：草莓饱和度高，暗部偏冷会发灰、偏暖会发橙，
                    // 用带玫瑰倾向的暖灰保住"红而不焦"（猫版的暖灰 166,148,130 在这里会脏）
                    toon.setProperty('shadeColor', new Color(190, 118, 130, 255));
                    toon.setProperty('shadeCtrl', new Vec4(0.72, 0.12, 0.16, 0.12));
                    toon.setProperty('outlineCtrl', new Vec4(0.0035, 0, 0, 0));
                    toon.setProperty('outlineColor', new Color(128, 72, 84, 255));
                    // 高光略强略聚（软陶/果蜡质感）
                    toon.setProperty('furCtrl', new Vec4(0.16, 2.0, 0.06, 10.0));
                    // 主光方向：与房间一致（左前上主光 + 右前下冷补光），猫版验证过的参数
                    toon.setProperty('lightDir', new Vec4(-0.85, 0.30, 0.32, 0.0));
                    toon.setProperty('fillDir', new Vec4(0.42, -0.18, 0.86, 0.0));
                    toon.setProperty('lightCtrl', new Vec4(0.88, 0.28, 0.14, 0.14));
                    // ⚠️ mapCtrl 必须放在**所有 setProperty 之后**：材质 uniform 在首次绑定后
                    // 才同步"最后一次写入"的值（猫版已实测多轮）
                    toon.setProperty('mapCtrl', new Vec4(1, 0, 0, 1));
                    // ⚠️ 必须走 setMaterial 显式替换：模型是异步加载的，首帧可能已经渲过，
                    // `.material = toon` 赋值不会触发蒙皮网格的渲染侧重绑
                    renderer.setMaterial(toon, 0);
                  } catch (error) {
                    console.warn('[pet-game] 宠物 pet-toon 材质初始化失败，保留原材质', error);
                    continue;
                  }
                  var texBack = toon.getProperty('mainTexture');
                  console.log("[pet-probe] pet material ".concat(n.name, ": pet-toon ") + "tex=".concat(texBack ? texBack.uuid : 'null'));
                }
              } catch (err) {
                _iterator4.e(err);
              } finally {
                _iterator4.f();
              }
              var _iterator5 = _createForOfIteratorHelper(n.children),
                _step5;
              try {
                for (_iterator5.s(); !(_step5 = _iterator5.n()).done;) {
                  var child = _step5.value;
                  _visit(child);
                }
              } catch (err) {
                _iterator5.e(err);
              } finally {
                _iterator5.f();
              }
            };
            _visit(node);
          }

          /** 摸头反应文案池（连击越深，措辞越亲昵） */
        }, {
          key: "playClip",
          value: /** 播片：名字不存在时静默回落（模型可能还没带上该剪辑，不能因此崩掉整场） */
          function playClip(name, loop) {
            var _this5 = this;
            var anim = this.petAnim;
            if (!anim) {
              return;
            }
            var has = anim.clips.some(function (c) {
              return c.name === name;
            });
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
              this.scheduleOnce(function () {
                return _this5.playClip('Idle', true);
              }, (_anim$getState$durati = (_anim$getState = anim.getState(name)) === null || _anim$getState === void 0 ? void 0 : _anim$getState.duration) !== null && _anim$getState$durati !== void 0 ? _anim$getState$durati : 1);
            }
          }

          /** 用服务端数值决定待机强度：越虚弱，动作越慢（唯一的"状态→动画"映射） */
        }, {
          key: "applyStatsToAnimation",
          value: function applyStatsToAnimation() {
            var pet = this.pendingPet;
            if (!pet) {
              return;
            }
            var worst = Math.min(pet.maxHp > 0 ? pet.hp / pet.maxHp : 1, pet.hunger / 100, pet.happiness / 100, pet.energy / 100, pet.cleanliness / 100);
            // 0.45(极差) ~ 1.0(健康)：线性但夹住下界，太慢会看起来卡住
            this.speedScale = Math.max(0.45, Math.min(1, 0.45 + worst * 0.55));
            var anim = this.petAnim;
            if (anim) {
              var _iterator6 = _createForOfIteratorHelper(anim.clips),
                _step6;
              try {
                for (_iterator6.s(); !(_step6 = _iterator6.n()).done;) {
                  var clip = _step6.value;
                  var state = anim.getState(clip.name);
                  if (state) {
                    state.speed = this.speedScale;
                  }
                }
              } catch (err) {
                _iterator6.e(err);
              } finally {
                _iterator6.f();
              }
            }
          }
        }, {
          key: "headWorld",
          get: function get() {
            var head = this.fruit.head;
            return new Vec3(PET_POS.x + head.x, head.y, PET_POS.z + head.z);
          }

          // ---------------- 探针 ----------------
        }, {
          key: "probeFraming",
          value: function probeFraming() {
            var _this6 = this;
            this.scheduleOnce(function () {
              var camera = _this6.camera3d;
              if (!camera) {
                console.log('[pet-probe] camera missing');
                return;
              }
              var foot = camera.worldToScreen(new Vec3(PET_POS.x, 0, PET_POS.z), new Vec3());
              var top = camera.worldToScreen(new Vec3(PET_POS.x, _this6.fruit.height, PET_POS.z), new Vec3());
              var screenHeight = screen.windowSize.height;
              console.log("[pet-probe] cam=".concat(camera.node.position.toString(), " fov=").concat(camera.fov, " ") + "screen=".concat(screenHeight, "px foot=(").concat(foot.x.toFixed(1), ",").concat(foot.y.toFixed(1), ") ") + "top=(".concat(top.x.toFixed(1), ",").concat(top.y.toFixed(1), ") ") + "height=".concat(Math.abs(top.y - foot.y).toFixed(1), "px ") + "ratio=".concat((Math.abs(top.y - foot.y) / screenHeight * 100).toFixed(1), "%"));
            }, 1.2);
          }
        }, {
          key: "probeMaterials",
          value: function probeMaterials() {
            var _this7 = this;
            this.scheduleOnce(function () {
              var _getAll, _ref;
              var registry = (_getAll = (_ref = EffectAsset).getAll) === null || _getAll === void 0 ? void 0 : _getAll.call(_ref);
              var list = registry instanceof Map ? Array.from(registry.values()) : Array.isArray(registry) ? registry : [];
              console.log("[pet-probe] loaded effects (".concat(list.length, "): ") + list.map(function (entry) {
                return entry && entry.name;
              }).join(' | '));
              var _visit2 = function visit(node) {
                var _iterator7 = _createForOfIteratorHelper(node.components),
                  _step7;
                try {
                  for (_iterator7.s(); !(_step7 = _iterator7.n()).done;) {
                    var _model$material$effec;
                    var comp = _step7.value;
                    var model = comp;
                    if (!model.mesh || !model.material) {
                      continue;
                    }
                    console.log("[pet-probe] mat ".concat(node.name, " effect=").concat((_model$material$effec = model.material.effectAsset) === null || _model$material$effec === void 0 ? void 0 : _model$material$effec.name, " ") + "passes=".concat(model.material.passes ? model.material.passes.length : -1));
                  }
                } catch (err) {
                  _iterator7.e(err);
                } finally {
                  _iterator7.f();
                }
                var _iterator8 = _createForOfIteratorHelper(node.children),
                  _step8;
                try {
                  for (_iterator8.s(); !(_step8 = _iterator8.n()).done;) {
                    var child = _step8.value;
                    _visit2(child);
                  }
                } catch (err) {
                  _iterator8.e(err);
                } finally {
                  _iterator8.f();
                }
              };
              if (_this7.petNode) {
                _visit2(_this7.petNode);
              }
            }, 1.5);
          }
        }, {
          key: "update",
          value: function update(dt) {
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
        }, {
          key: "driveBlink",
          value: function driveBlink(dt) {
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
        }, {
          key: "onDestroy",
          value: function onDestroy() {
            input.off(Input.EventType.TOUCH_END, this.onStageTap, this);
            this.bridge.dispose();
          }

          // ---------------- 场景构建 ----------------
        }, {
          key: "buildWorld",
          value: function buildWorld() {
            var kit = this.kit;
            var scene = this.node.scene;
            this.world3d = kit.make3dNode(scene, 'World3D', new Vec3(0, 0, 0));
            this.room = this.plain ? null : buildRoom(this.world3d, kit);
            if (!this.plain) {
              this.buildLighting(scene);
            }
            var cameraNode = scene.getChildByName('Main3DCamera');
            this.camera3d = cameraNode ? cameraNode.getComponent(Camera) : null;
            if (cameraNode && this.camera3d) {
              var shot = CAMERA_SHOT[this.shot] || CAMERA_SHOT.room;
              cameraNode.setPosition(shot.pos[0], shot.pos[1], shot.pos[2]);
              cameraNode.lookAt(new Vec3(shot.target[0], shot.target[1], shot.target[2]), new Vec3(0, 1, 0));
              // 清屏色 = 奶油底：任何画布/容器比例误差都隐形成奶油色，而不是深蓝条
              this.camera3d.clearColor = this.plain ? PLAIN_BG : new Color(0xFD, 0xF6, 0xEC, 255);
            }
          }

          /**
           * 场景光照 —— **只服务宠物**。
           *
           * 房间所有部件走自研 `pet-toon`（自带写死的三点光，不依赖引擎光源）；
           * 宠物 glTF 材质必须靠引擎光源才出体积。补一盏平行光对齐 pet-toon 主光方向，
           * 因为 pet-toon 无视引擎光源，这一步对房间**零影响**。
           */
        }, {
          key: "buildLighting",
          value: function buildLighting(scene) {
            // 主光（暖，左前上）：**必须从镜头这一侧来**（猫版实证：放窗户那侧只照亮背面）
            var key = new Node('PetKeyLight');
            key.layer = Layers.Enum.DEFAULT;
            scene.addChild(key);
            key.setPosition(-3.2, 2.5, 2.3);
            key.lookAt(new Vec3(0, 0.62, 0.35), new Vec3(0, 1, 0));
            var keyLight = key.addComponent(DirectionalLight);
            keyLight.color = new Color(255, 231, 198);
            // ⚠️ 强度必须压得低：无色调映射管线线性值直接 clip，过亮会把体积全顶丢
            keyLight.illuminance = 34000;

            // 补光（冷，右前下）：压住暗部、给一点冷暖对比
            var fill = new Node('PetFillLight');
            fill.layer = Layers.Enum.DEFAULT;
            scene.addChild(fill);
            fill.setPosition(3.0, 0.9, 2.0);
            fill.lookAt(new Vec3(0, 0.55, 0.35), new Vec3(0, 1, 0));
            var fillLight = fill.addComponent(DirectionalLight);
            fillLight.color = new Color(196, 208, 255);
            fillLight.illuminance = 7000;

            // 环境光：HDR / LDR 两份字段都要写（猫版实证：只写 LDR 在 HDR 分支下不生效；
            // HDR 字段在部分引擎形态下是只读 getter，直接赋值会抛 TypeError 中断 buildScene）
            var ambient = director.getScene().globals.ambient;
            var a = ambient;
            var skyLDR = new Color(200, 205, 215);
            var groundLDR = new Color(140, 112, 84);
            ambient.skyColor = skyLDR;
            ambient.groundAlbedo = groundLDR;
            ambient.skyIllum = 1400;
            var hdrWritable = function (_Object$getOwnPropert) {
              var desc = (_Object$getOwnPropert = Object.getOwnPropertyDescriptor(a, 'skyColorHDR')) !== null && _Object$getOwnPropert !== void 0 ? _Object$getOwnPropert : Object.getOwnPropertyDescriptor(Object.getPrototypeOf(ambient), 'skyColorHDR');
              return !desc || desc.set !== undefined;
            }();
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
        }, {
          key: "buildPetShadow",
          value: function buildPetShadow() {
            var root = this.kit.make3dNode(this.world3d, 'PetShadow', new Vec3(0, 0, 0));
            this.kit.decal(root, 'Blob', this.fruit.shadow, {
              color: new Color(0x6B, 0x4A, 0x33, 255),
              shade: new Color(0x6B, 0x4A, 0x33, 255),
              alpha: 82,
              soft: [0.02, 1.0]
            }, new Vec3(PET_POS.x, 0.075, PET_POS.z));
            return root;
          }
        }, {
          key: "buildOverlay",
          value: function buildOverlay() {
            var _this8 = this;
            this.effects = new PetEffects(function (name, x, y) {
              return _this8.makeUiNode(name, x, y);
            }, function (world) {
              return _this8.project(world);
            });
          }
        }, {
          key: "makeUiNode",
          value: function makeUiNode(name, x, y) {
            var node = new Node(name);
            node.layer = Layers.Enum.UI_2D;
            node.addComponent(UITransform).setContentSize(8, 8);
            node.setPosition(x, y, 0);
            this.node.addChild(node);
            return node;
          }
        }, {
          key: "project",
          value: function project(world) {
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

          /** 房间氛围：灯泡呼吸 / 阳光光斑呼吸 / 玩具球轻摆 */
        }, {
          key: "roomAmbience",
          value: function roomAmbience(dt) {
            var _this9 = this;
            var room = this.room;
            if (!room) {
              return;
            }
            room.bulbs.forEach(function (bulb, index) {
              var scale = 0.92 + Math.sin(_this9.roomTime * 2.1 + index * 0.7) * 0.08;
              bulb.setScale(scale, scale, scale);
            });
            var beamScale = 1 + Math.sin(this.roomTime * 0.9) * 0.06;
            room.sunBeam.setScale(beamScale, 1, beamScale);
            var toy = room.toyBall;
            toy.setPosition(-1.35 + Math.sin(this.roomTime * 0.6) * 0.1, 0.19 + Math.abs(Math.sin(this.roomTime * 1.4)) * 0.03, 1.05);
          }

          // ---------------- 桥接 ----------------
        }, {
          key: "bindBridge",
          value: function bindBridge() {
            var _this0 = this;
            this.bridge.bind(function (message) {
              switch (message.type) {
                case 'init':
                case 'petState':
                  _this0.pendingPet = message.pet;
                  _this0.applyStatsToAnimation();
                  break;
                case 'actionResult':
                  if (message.ok) {
                    _this0.playClip('Happy', false);
                    _this0.effects && _this0.effects.sparkle(_this0.headWorld, 3);
                  } else {
                    _this0.effects && _this0.effects.floatText(_this0.headWorld, '呜…', new Color(255, 200, 200, 255));
                  }
                  break;
                case 'battleRounds':
                  if (_this0.effects) {
                    _this0.effects.stars(_this0.headWorld, message.won ? 8 : 3);
                  }
                  break;
                case 'chatBubble':
                  // 气泡属界面层（旧 HUD 已删），新 UI 层落地前先用舞台浮字顶一下
                  _this0.effects && _this0.effects.floatText(_this0.headWorld, message.content, new Color(255, 250, 240, 255), 20);
                  break;
              }
            });
          }
        }]);
      }(Component), _defineProperty(_PetGameRoot, "REWARD_PERIOD_MS", 4 * 60 * 60 * 1000), _defineProperty(_PetGameRoot, "REWARD_MAX_PER_PERIOD", 3), _defineProperty(_PetGameRoot, "TAP_LINES", ['嘿嘿～', '再摸摸～', '好舒服～', '（蹭蹭你的手）', '最喜欢你啦', '呼噜呼噜…']), _PetGameRoot)) || _class));
      cclegacy._RF.pop();
    }
  };
});

System.register("chunks:///_virtual/PetGameTheme.js", ['cc'], function (exports) {
  var cclegacy, Color;
  return {
    setters: [function (module) {
      cclegacy = module.cclegacy;
      Color = module.Color;
    }],
    execute: function () {
      exports({
        resolvePalette: resolvePalette,
        shift: shift
      });
      cclegacy._RF.push({}, "f65e0Qg92pF3JYE7gQ3SquJ", "PetGameTheme", undefined);

      /**
       * 宠物本体配色（角色层令牌，非界面层）。
       *
       * 职责边界：
       *  - 本文件**只管宠物自己的颜色**：由物种基色自动推导完整调色板，供 3D 造型与材质使用；
       *  - **不含任何界面令牌**。旧版的 HUD 卡片色 / 状态条色 / 动作按钮色 / emoji 图标表
       *    （`HUD`、`STATE_ROWS`、`ACTION_BUTTONS`、`NAV_BUTTONS`）已随 PetHud 一并删除，
       *    界面层令牌将由新设计稿定稿后在独立模块中重建。
       *
       * 约定：所有颜色都是 sRGB 语义（与素材、设计稿一致），
       * 仅 pet-toon effect 的 linear 属性上传时由引擎做线性化，代码侧不做转换。
       */

      /** 宠物完整调色板（8 槽；后三项为视觉重构 v5 新增） */

      /** 物种基色（唯一色源：其余部位由基色推导） */
      var SPECIES_BODY = {
        CAT: '#F3EEE7' // 奶油白（唯一物种：其他物种造型已删除）
      };

      /** 深色部位覆盖（耳内/花纹；缺省由基色推导） */
      var SPECIES_DARK = {
        CAT: '#CFC5BE' // 暖灰阴影（暗部保持暖调、不发黑）
      };

      /** 肚皮/口鼻覆盖（缺省 = 基色提亮 42%） */
      var SPECIES_BELLY = {
        CAT: '#FFF8F0' // 口鼻与胸腹浅色
      };

      /** 爪垫覆盖（缺省 = 基色提亮 52%） */
      var SPECIES_PAW = {
        CAT: '#EADBD4' // 爪垫：比主体略暗的暖灰粉（不抢眼）
      };

      /** 虹膜覆盖（缺省 = 琥珀金；取自奶灰猫实测贴图） */
      var SPECIES_IRIS = {
        CAT: '#C98A3E'
      };

      /** 配饰覆盖（物种默认配饰色） */
      var SPECIES_ACCESSORY = {
        CAT: '#C4A0A2' // 干枯玫瑰藕粉（针织围巾）
      };

      /**
       * 眼球高光：**固定中性色，不随物种/皮肤变化**。
       *
       * 历史教训：曾对整只眼球做定向染色，把高光 `#F4F2F1` 一起染成 `#F1E2AA`，
       * 眼内最高明度从 249 掉到 212 → 虹膜金 + 眼白黄 + 高光黄糊成一片，整只眼读作一颗金球。
       * 高光是眼内唯一的中性色，也是"湿润、有神"的全部来源，必须锁死。
       */
      var IRIS_HIGHLIGHT = '#F4F3F2';

      /** 强调金色（饰扣、徽章等点睛细节；低饱和的金，避免"塑料亮片"感） */
      var ACCENT_GOLD = exports('ACCENT_GOLD', '#E7B85D');

      /** 配饰键（PetDisplayState.accessory）→ 颜色 */
      var ACCESSORY_COLOR = {
        none: '#C4A0A2',
        bell: '#E7C87F',
        // 浅金铃铛
        scarf: '#C4A0A2',
        // 干枯玫瑰藕粉围巾
        bowtie: '#B98A88',
        glasses: '#8B7968'
      };

      /** 皮肤色键（pet_skin_config.color）→ 基色（保留宿主自定义换色能力） */
      var SKIN_BODY = {
        orange: '#F7B05A',
        gray: '#B7BDC9',
        white: '#FAF7F3',
        brown: '#C08A5E',
        pink: '#F7AFC4',
        black: '#57525F',
        mint: '#8FE3C8',
        golden: '#F2CE74',
        snow: '#E6EFFF',
        midnight: '#8C86C4',
        ink: '#4A4A58',
        aurora: '#9FB6FF'
      };

      /** 宠物当前情绪（驱动待机表情与姿态；属角色层，不属界面层） */

      // 交互演出态

      /** 十六进制 → Color */
      function hex(value) {
        return new Color().fromHEX(value);
      }

      /** 颜色向白/黑插值（amount > 0 提亮，< 0 压暗） */
      function shift(color, amount) {
        var target = amount >= 0 ? 255 : 0;
        var k = Math.abs(amount);
        return new Color(Math.round(color.r + (target - color.r) * k), Math.round(color.g + (target - color.g) * k), Math.round(color.b + (target - color.b) * k), color.a);
      }

      /**
       * 解析宠物调色板：皮肤色优先，其次物种配色；未知键回落银白猫。
       *
       * 说明：
       *  - 物种专属的肚皮/爪垫/虹膜覆盖只在"未自定义肤色"时生效，
       *    否则按用户选的基色重新推导，保证换色后整体协调；
       *  - 高光色不参与推导，任何情况下都是固定中性色。
       */
      function resolvePalette(species, colorKey, accessoryKey) {
        var baseHex = colorKey && SKIN_BODY[colorKey] || SPECIES_BODY[species] || SPECIES_BODY.CAT;
        var body = hex(baseHex);
        var speciesTint = !colorKey;
        return {
          body: body,
          dark: speciesTint && SPECIES_DARK[species] ? hex(SPECIES_DARK[species]) : shift(body, -0.22),
          // 肚皮：基色大幅提亮（白色系物种不会过曝，shift 有上限保护）
          belly: speciesTint && SPECIES_BELLY[species] ? hex(SPECIES_BELLY[species]) : shift(body, 0.42),
          // 爪垫：比肚皮更亮一点点，形成"袜子"层次
          paw: speciesTint && SPECIES_PAW[species] ? hex(SPECIES_PAW[species]) : shift(body, 0.52),
          // 腮红：固定的暖粉，与任何主色都协调（造型侧以低透明度 + 柔和边缘使用）
          blush: hex('#F3A7AE'),
          // 虹膜：未自定义肤色时用物种专属琥珀金；换色后基色偏冷，虹膜向亮金提一档保持对比
          iris: speciesTint && SPECIES_IRIS[species] ? hex(SPECIES_IRIS[species]) : shift(hex('#C98A3E'), 0.1),
          irisHighlight: hex(IRIS_HIGHLIGHT),
          accessory: hex(accessoryKey && ACCESSORY_COLOR[accessoryKey] || SPECIES_ACCESSORY[species] || ACCESSORY_COLOR.none)
        };
      }
      cclegacy._RF.pop();
    }
  };
});

System.register("chunks:///_virtual/PetMeshFactory.js", ['./rollupPluginModLoBabelHelpers.js', 'cc'], function (exports) {
  var _slicedToArray, _toConsumableArray, _createForOfIteratorHelper, cclegacy;
  return {
    setters: [function (module) {
      _slicedToArray = module.slicedToArray;
      _toConsumableArray = module.toConsumableArray;
      _createForOfIteratorHelper = module.createForOfIteratorHelper;
    }, function (module) {
      cclegacy = module.cclegacy;
    }],
    execute: function () {
      exports({
        ellipsoid: ellipsoid,
        frontZ: frontZ,
        lathe: lathe,
        merge: merge,
        profileAt: profileAt,
        profileFrom: profileFrom,
        radialUV: radialUV,
        smoothNormals: smoothNormals,
        spherePatch: spherePatch,
        sweep: sweep,
        transformed: transformed
      });
      cclegacy._RF.push({}, "0f82cLOaQ5F858250xrrJWb", "PetMeshFactory", undefined);
      /**
       * 手写网格工厂（视觉重构 v4）—— 用连续曲面替代"球体拼装"。
       *
       * 为什么需要它：
       *  - primitives 只有球/柱/锥等简单体，拼装出的角色剪影破碎、接缝穿插、法线不连续，
       *    这正是上一版"零件感、廉价感"的根本原因；
       *  - 引擎提供 utils.MeshUtils.createMesh(IGeometry)，允许直接提交任意顶点数据，
       *    因此这里用"旋转体放样(lathe) + 扫掠(sweep) + 平滑法线"生成一体化连续曲面。
       *
       * 约定：
       *  - 坐标系为 Cocos 右手系，+Y 向上，+Z 为角色正前方（与 PetModelBuilder 一致）；
       *  - 几何在本地空间生成，由调用方用节点变换定位（与旧版 part/ball 用法保持一致）；
       *  - 输出结构与 primitives.IGeometry 兼容，可直接喂给 createMesh。
       */
      /** 与 primitives.IGeometry 兼容的几何数据（仅使用渲染必需字段） */
      /** 三维点（数组形式，避免在数学层引入引擎依赖） */
      /** 扫掠路径节点：位置 + 截面半径（可分别缩放 x/y 形成椭圆截面） */
      /** 顶点变换参数（位置/欧拉角(弧度)/缩放） */
      var EPS = 1e-6;

      /**
       * 旋转体：把 (半径, 高度) 侧轮廓绕 Y 轴旋转成连续曲面。
       *
       * 这是本项目最重要的建模原语：头、躯干、耳朵、四肢全部由它生成，
       * 因为轮廓可以任意精细，且法线由轮廓切线解析求出（天然光滑、无接缝）。
       *
       * @param profile 侧轮廓，从下到上 [radius, y]；半径 0 表示收口（自动形成极点）
       * @param segments 圆周分段（建议 28-48，越低越"低多边形"）
       */
      function lathe(profile) {
        var segments = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 32;
        var rings = profile.length;
        var positions = [];
        var normals = [];
        var uvs = [];
        var indices = [];

        // 预先求每层轮廓的 2D 法线：轮廓切线顺时针旋转 90° 指向外侧
        var rimNormal = [];
        for (var i = 0; i < rings; i++) {
          var prev = profile[Math.max(0, i - 1)];
          var next = profile[Math.min(rings - 1, i + 1)];
          var tr = next[0] - prev[0];
          var ty = next[1] - prev[1];
          var len = Math.hypot(tr, ty);
          if (len < EPS) {
            tr = 0;
            ty = 1;
          } else {
            tr /= len;
            ty /= len;
          }
          rimNormal.push([ty, -tr]);
        }

        // uv：u = 圆周方位（u = θ/2π），v = 归一化高度（0 = 轮廓最低点，1 = 最高点）。
        // v 用高度而不是环索引，是为了让"按 uv 定位的贴花"（如烘焙腮红）能按实际几何位置计算。
        var yMin = profile[0][1];
        var yMax = profile[rings - 1][1];
        var ySpan = Math.abs(yMax - yMin) < EPS ? 1 : yMax - yMin;
        for (var _i = 0; _i < rings; _i++) {
          var _profile$_i = _slicedToArray(profile[_i], 2),
            radius = _profile$_i[0],
            y = _profile$_i[1];
          var _rimNormal$_i = _slicedToArray(rimNormal[_i], 2),
            nr = _rimNormal$_i[0],
            ny = _rimNormal$_i[1];
          var v = (y - yMin) / ySpan;
          for (var j = 0; j <= segments; j++) {
            var u = j / segments;
            var theta = u * Math.PI * 2;
            var cos = Math.cos(theta);
            var sin = Math.sin(theta);
            positions.push(radius * cos, y, radius * sin);
            normals.push(nr * cos, ny, nr * sin);
            uvs.push(u, v);
          }
        }
        var stride = segments + 1;
        for (var _i2 = 0; _i2 < rings - 1; _i2++) {
          for (var _j = 0; _j < segments; _j++) {
            var a = _i2 * stride + _j;
            var b = a + 1;
            var c = a + stride;
            var d = c + 1;
            indices.push(a, c, b, b, c, d);
          }
        }
        return {
          positions: positions,
          normals: normals,
          uvs: uvs,
          indices: indices
        };
      }

      /**
       * 椭球：三轴半径独立（Q 版头/身体/眼睛的基本体）。
       * 法线按椭球解析式求出（比球面法线更准确，暗部过渡更自然）。
       */
      function ellipsoid(rx, ry, rz) {
        var segments = arguments.length > 3 && arguments[3] !== undefined ? arguments[3] : 32;
        var rings = arguments.length > 4 && arguments[4] !== undefined ? arguments[4] : 22;
        var positions = [];
        var normals = [];
        var uvs = [];
        var indices = [];
        for (var i = 0; i <= rings; i++) {
          var phi = i / rings * Math.PI; // 0(顶) → π(底)
          var sp = Math.sin(phi);
          var cp = Math.cos(phi);
          for (var j = 0; j <= segments; j++) {
            var theta = j / segments * Math.PI * 2;
            var st = Math.sin(theta);
            var ct = Math.cos(theta);
            var x = rx * sp * ct;
            var y = ry * cp;
            var z = rz * sp * st;
            positions.push(x, y, z);
            // 椭球隐式梯度 (x/rx², y/ry², z/rz²)
            var nx = x / (rx * rx);
            var ny = y / (ry * ry);
            var nz = z / (rz * rz);
            var len = Math.hypot(nx, ny, nz) || 1;
            nx /= len;
            ny /= len;
            nz /= len;
            normals.push(nx, ny, nz);
            uvs.push(j / segments, 1 - i / rings);
          }
        }
        var stride = segments + 1;
        for (var _i3 = 0; _i3 < rings; _i3++) {
          for (var _j2 = 0; _j2 < segments; _j2++) {
            var a = _i3 * stride + _j2;
            var b = a + 1;
            var c = a + stride;
            var d = c + 1;
            indices.push(a, b, c, b, d, c);
          }
        }
        return {
          positions: positions,
          normals: normals,
          uvs: uvs,
          indices: indices
        };
      }

      /** 归一化向量 */
      function norm(v) {
        var len = Math.hypot(v[0], v[1], v[2]) || 1;
        return [v[0] / len, v[1] / len, v[2] / len];
      }

      /** 叉乘 */
      function cross(a, b) {
        return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
      }

      /** 扫掠环：截面中心 + 基向量（法线/副法线）+ 椭圆半径（局部坐标系） */

      /**
       * 扫掠管：沿 3D 路径扫掠椭圆截面（耳朵/尾巴/四肢/呆毛）。
       *
       * 使用"平行传输"框架逐节点推进截面，避免路径转弯时截面扭转撕裂；
       * 首尾自动补**半球封头**（Q 版造型里所有末端都必须是圆的，否则从侧后方能看到"空心管口"）。
       *
       * @param nodes 路径节点（位置 + 截面半径 + 可选椭圆缩放）
       * @param radial 圆周分段
       * @param capSteps 封头细分（0 = 不封头；3 已足够圆润）
       */
      function sweep(nodes) {
        var radial = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 18;
        var capSteps = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : 3;
        var count = nodes.length;
        var positions = [];
        var normals = [];
        var uvs = [];
        var indices = [];

        // 每个节点的切线
        var tangents = [];
        for (var i = 0; i < count; i++) {
          var prev = nodes[Math.max(0, i - 1)].p;
          var next = nodes[Math.min(count - 1, i + 1)].p;
          tangents.push(norm([next[0] - prev[0], next[1] - prev[1], next[2] - prev[2]]));
        }

        // 平行传输：逐节点推进截面基向量（避免急转弯处扭转撕裂）
        var ref = Math.abs(tangents[0][1]) > 0.92 ? [1, 0, 0] : [0, 1, 0];
        var normal = norm(cross(tangents[0], ref));
        var rings = [];
        for (var _i4 = 0; _i4 < count; _i4++) {
          var t = tangents[_i4];
          var dot = normal[0] * t[0] + normal[1] * t[1] + normal[2] * t[2];
          var n = norm([normal[0] - t[0] * dot, normal[1] - t[1] * dot, normal[2] - t[2] * dot]);
          if (!isFinite(n[0]) || !isFinite(n[1]) || !isFinite(n[2])) {
            n = norm(cross(t, ref));
          }
          normal = n;
          var binormal = norm(cross(t, normal));
          var node = nodes[_i4];
          rings.push({
            c: node.p,
            n: normal,
            b: binormal,
            r: node.r,
            sx: node.sx === undefined ? 1 : node.sx,
            sy: node.sy === undefined ? 1 : node.sy
          });
        }

        // 半球封头：以端点截面为赤道，沿切线外侧按 cos 收缩到极点
        if (capSteps > 0 && count >= 2) {
          var startCap = [];
          var head = rings[0];
          var dirStart = [-tangents[0][0], -tangents[0][1], -tangents[0][2]];
          for (var k = capSteps; k >= 1; k -= 1) {
            var angle = k / capSteps * (Math.PI / 2);
            var s = Math.sin(angle);
            var c = Math.cos(angle);
            startCap.push({
              c: [head.c[0] + dirStart[0] * head.r * s, head.c[1] + dirStart[1] * head.r * s, head.c[2] + dirStart[2] * head.r * s],
              n: head.n,
              b: head.b,
              r: head.r * c,
              sx: head.sx,
              sy: head.sy
            });
          }
          var endCap = [];
          var last = rings[rings.length - 1];
          var dirEnd = tangents[count - 1];
          for (var _k = 1; _k <= capSteps; _k += 1) {
            var _angle = _k / capSteps * (Math.PI / 2);
            var _s = Math.sin(_angle);
            var _c = Math.cos(_angle);
            endCap.push({
              c: [last.c[0] + dirEnd[0] * last.r * _s, last.c[1] + dirEnd[1] * last.r * _s, last.c[2] + dirEnd[2] * last.r * _s],
              n: last.n,
              b: last.b,
              r: last.r * _c,
              sx: last.sx,
              sy: last.sy
            });
          }
          rings.unshift.apply(rings, startCap);
          rings.push.apply(rings, endCap);
        }
        var total = rings.length;
        for (var _i5 = 0; _i5 < total; _i5++) {
          var ring = rings[_i5];
          var v = total > 1 ? _i5 / (total - 1) : 0;
          for (var j = 0; j <= radial; j++) {
            var u = j / radial;
            var theta = u * Math.PI * 2;
            var cos = Math.cos(theta);
            var sin = Math.sin(theta);
            positions.push(ring.c[0] + (ring.n[0] * cos * ring.sx + ring.b[0] * sin * ring.sy) * ring.r, ring.c[1] + (ring.n[1] * cos * ring.sx + ring.b[1] * sin * ring.sy) * ring.r, ring.c[2] + (ring.n[2] * cos * ring.sx + ring.b[2] * sin * ring.sy) * ring.r);
            var nrm = norm([ring.n[0] * cos * ring.sy + ring.b[0] * sin * ring.sx, ring.n[1] * cos * ring.sy + ring.b[1] * sin * ring.sx, ring.n[2] * cos * ring.sy + ring.b[2] * sin * ring.sx]);
            normals.push(nrm[0], nrm[1], nrm[2]);
            uvs.push(u, v);
          }
        }
        var stride = radial + 1;
        for (var _i6 = 0; _i6 < total - 1; _i6++) {
          for (var _j3 = 0; _j3 < radial; _j3++) {
            var a = _i6 * stride + _j3;
            var b = a + 1;
            var _c2 = a + stride;
            var d = _c2 + 1;
            // 绕序说明：binormal = cross(tangent, normal)，截面 θ 方向与 outward(normal) 相反，
            // 因此顺序需与 lathe 相反，才能保证正面朝外（否则会被背面剔除）。
            indices.push(a, b, _c2, b, d, _c2);
          }
        }
        return {
          positions: positions,
          normals: normals,
          uvs: uvs,
          indices: indices
        };
      }

      /** 欧拉角（XYZ 顺序，弧度）→ 3×3 旋转矩阵（行主序） */
      function eulerMatrix(rot) {
        var _rot = _slicedToArray(rot, 3),
          rx = _rot[0],
          ry = _rot[1],
          rz = _rot[2];
        var cx = Math.cos(rx),
          sx = Math.sin(rx);
        var cy = Math.cos(ry),
          sy = Math.sin(ry);
        var cz = Math.cos(rz),
          sz = Math.sin(rz);
        return [cy * cz, -cy * sz, sy, sx * sy * cz + cx * sz, -sx * sy * sz + cx * cz, -sx * cy, -cx * sy * cz + sx * sz, cx * sy * sz + sx * cz, cx * cy];
      }

      /** 几何体变换（位置/旋转/缩放；法线用旋转矩阵变换，随后归一化） */
      function transformed(geo, t) {
        var rot = t.rot || [0, 0, 0];
        var scale = t.scale || [1, 1, 1];
        var pos = t.pos || [0, 0, 0];
        var m = eulerMatrix(rot);
        var positions = [];
        var normals = [];
        var count = geo.positions.length / 3;
        for (var i = 0; i < count; i++) {
          var x = geo.positions[i * 3] * scale[0];
          var y = geo.positions[i * 3 + 1] * scale[1];
          var z = geo.positions[i * 3 + 2] * scale[2];
          positions.push(m[0] * x + m[1] * y + m[2] * z + pos[0], m[3] * x + m[4] * y + m[5] * z + pos[1], m[6] * x + m[7] * y + m[8] * z + pos[2]);
          var nx = geo.normals[i * 3];
          var ny = geo.normals[i * 3 + 1];
          var nz = geo.normals[i * 3 + 2];
          var tx = m[0] * nx + m[1] * ny + m[2] * nz;
          var ty = m[3] * nx + m[4] * ny + m[5] * nz;
          var tz = m[6] * nx + m[7] * ny + m[8] * nz;
          var len = Math.hypot(tx, ty, tz) || 1;
          normals.push(tx / len, ty / len, tz / len);
        }
        return {
          positions: positions,
          normals: normals,
          uvs: geo.uvs.slice(),
          indices: geo.indices.slice()
        };
      }

      /** 合并多个几何体为一个（同一材质下减少 draw call；索引自动偏移） */
      function merge() {
        var out = {
          positions: [],
          normals: [],
          uvs: [],
          indices: []
        };
        var base = 0;
        for (var _len = arguments.length, geos = new Array(_len), _key = 0; _key < _len; _key++) {
          geos[_key] = arguments[_key];
        }
        for (var _i7 = 0, _geos = geos; _i7 < _geos.length; _i7++) {
          var _out$positions, _out$normals, _out$uvs;
          var geo = _geos[_i7];
          (_out$positions = out.positions).push.apply(_out$positions, _toConsumableArray(geo.positions));
          (_out$normals = out.normals).push.apply(_out$normals, _toConsumableArray(geo.normals));
          (_out$uvs = out.uvs).push.apply(_out$uvs, _toConsumableArray(geo.uvs));
          var _iterator = _createForOfIteratorHelper(geo.indices),
            _step;
          try {
            for (_iterator.s(); !(_step = _iterator.n()).done;) {
              var index = _step.value;
              out.indices.push(index + base);
            }
          } catch (err) {
            _iterator.e(err);
          } finally {
            _iterator.f();
          }
          base += geo.positions.length / 3;
        }
        return out;
      }

      /**
       * 平滑法线：按位置把重合顶点分组求平均后写回。
       *
       * 用途：把多个部件（如头 + 腮 + 额头）焊接处的法线统一，
       * 消除"两个球相交处出现硬边/亮线"的廉价感。
       */
      function smoothNormals(geo) {
        var precision = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 4;
        var map = new Map();
        var count = geo.positions.length / 3;
        var key = function key(i) {
          return geo.positions[i * 3].toFixed(precision) + ',' + geo.positions[i * 3 + 1].toFixed(precision) + ',' + geo.positions[i * 3 + 2].toFixed(precision);
        };
        for (var i = 0; i < count; i++) {
          var k = key(i);
          var acc = map.get(k);
          if (acc) {
            acc[0] += geo.normals[i * 3];
            acc[1] += geo.normals[i * 3 + 1];
            acc[2] += geo.normals[i * 3 + 2];
          } else {
            map.set(k, [geo.normals[i * 3], geo.normals[i * 3 + 1], geo.normals[i * 3 + 2]]);
          }
        }
        var normals = geo.normals.slice();
        for (var _i8 = 0; _i8 < count; _i8++) {
          var _acc = map.get(key(_i8));
          var len = Math.hypot(_acc[0], _acc[1], _acc[2]) || 1;
          normals[_i8 * 3] = _acc[0] / len;
          normals[_i8 * 3 + 1] = _acc[1] / len;
          normals[_i8 * 3 + 2] = _acc[2] / len;
        }
        return {
          positions: geo.positions.slice(),
          normals: normals,
          uvs: geo.uvs.slice(),
          indices: geo.indices.slice()
        };
      }

      /**
       * 由"半径-高度"控制点生成顺滑的旋转体轮廓（Catmull-Rom 插值）。
       *
       * 好处：造型只写少量关键点（如头部的额/颊/下巴宽度），
       * 由插值生成几十层轮廓，保证曲面连续（不会出现折角）。
       */
      /**
       * 旋转体轮廓在指定高度的半径（线性插值）。
       *
       * 用途：头部是 lathe 生成的旋转体，五官定位时需要知道"脸在某个高度有多宽"，
       * 据此把眼睛/鼻子/嘴贴到曲面外侧的正确位置。
       */
      function profileAt(profile, y) {
        if (profile.length === 0) {
          return 0;
        }
        if (y <= profile[0][1]) {
          return profile[0][0];
        }
        var last = profile[profile.length - 1];
        if (y >= last[1]) {
          return last[0];
        }
        for (var i = 0; i < profile.length - 1; i++) {
          var a = profile[i];
          var b = profile[i + 1];
          if (y >= a[1] && y <= b[1]) {
            var span = b[1] - a[1];
            var t = span < 1e-6 ? 0 : (y - a[1]) / span;
            return a[0] + (b[0] - a[0]) * t;
          }
        }
        return last[0];
      }

      /**
       * 旋转体前表面深度：给定 (x, y) 求该处表面在前半球的 z。
       *
       * 面部所有部件（眼/鼻/嘴/腮红）都应通过它定位，
       * 保证五官贴合面部曲率（否则会像零件悬浮在脸前）。
       * 超出轮廓范围时返回 0（调用方应保证 x 在脸颊范围内）。
       *
       * @param profile 旋转体侧轮廓（局部坐标，见 lathe）
       * @param x 相对旋转轴的水平偏移
       * @param y 相对旋转体原点的垂直偏移
       * @param zScale 生成几何时 z 方向的缩放系数（默认 1）
       */
      function frontZ(profile, x, y) {
        var zScale = arguments.length > 3 && arguments[3] !== undefined ? arguments[3] : 1;
        var radius = profileAt(profile, y);
        var inner = radius * radius - x * x;
        return inner <= 0 ? 0 : Math.sqrt(inner) * zScale;
      }

      /**
       * 球面片：贴在球面上的"椭圆帽"，用于需要**贴合曲面曲率**的部件
       * （腮红的柔和色斑、贴在眼球表面的高光片）。
       *
       * 顶点方向来自切平面椭圆参数化：dir(t, φ) = normalize(w + u·t·tanU·cosφ + v·t·tanV·sinφ)，
       * t ∈ [0,1] 是到片中心的归一化径向距离，写入 uv.x（柔和 alpha 衰减直接读它）。
       *
       * 用球面片而不是扁平椭球的原因：扁平片贴到凸曲面上时边缘会离开表面（悬浮感），
       * 球面片的曲率与头部/眼球一致，视觉上是"长在脸上"而不是"贴上去"。
       *
       * @param dir 片中心方向（球心指向片中心）
       * @param angleU 水平张角（弧度）
       * @param angleV 垂直张角（弧度）
       * @param radii 椭球三轴半径
       */
      function spherePatch(dir, angleU, angleV, radii) {
        var segments = arguments.length > 4 && arguments[4] !== undefined ? arguments[4] : 26;
        var rings = arguments.length > 5 && arguments[5] !== undefined ? arguments[5] : 5;
        var w = norm(dir);
        var ref = Math.abs(w[1]) > 0.9 ? [1, 0, 0] : [0, 1, 0];
        var u = norm(cross(ref, w));
        var v = cross(w, u);
        var tanU = Math.tan(angleU);
        var tanV = Math.tan(angleV);
        var positions = [];
        var normals = [];
        var uvs = [];
        var indices = [];
        for (var i = 0; i <= rings; i++) {
          var t = i / rings;
          for (var j = 0; j <= segments; j++) {
            var phi = j / segments * Math.PI * 2;
            var dx = Math.cos(phi) * tanU * t;
            var dy = Math.sin(phi) * tanV * t;
            var inv = 1 / Math.sqrt(1 + dx * dx + dy * dy);
            var sx = (w[0] + u[0] * dx + v[0] * dy) * inv;
            var sy = (w[1] + u[1] * dx + v[1] * dy) * inv;
            var sz = (w[2] + u[2] * dx + v[2] * dy) * inv;
            positions.push(sx * radii[0], sy * radii[1], sz * radii[2]);
            // 椭球法线：逐分量除以半径平方后归一化
            var nx = sx / (radii[0] * radii[0]);
            var ny = sy / (radii[1] * radii[1]);
            var nz = sz / (radii[2] * radii[2]);
            var nl = Math.hypot(nx, ny, nz) || 1;
            normals.push(nx / nl, ny / nl, nz / nl);
            uvs.push(t, phi / (Math.PI * 2));
          }
        }
        var stride = segments + 1;
        for (var _i9 = 0; _i9 < rings; _i9++) {
          for (var _j4 = 0; _j4 < segments; _j4++) {
            var a = _i9 * stride + _j4;
            var b = a + 1;
            var c = a + stride;
            var d = c + 1;
            indices.push(a, c, b, b, c, d);
          }
        }
        return {
          positions: positions,
          normals: normals,
          uvs: uvs,
          indices: indices
        };
      }

      /** 把 uv.x 重写为"到原点的归一化距离"（单位球贴花用；柔和 alpha 衰减读取 uv.x） */
      function radialUV(geo) {
        var count = geo.positions.length / 3;
        var uvs = geo.uvs.slice();
        var maxLen = 0;
        for (var i = 0; i < count; i++) {
          var len = Math.hypot(geo.positions[i * 3], geo.positions[i * 3 + 1], geo.positions[i * 3 + 2]);
          if (len > maxLen) {
            maxLen = len;
          }
        }
        var inv = maxLen > 0 ? 1 / maxLen : 1;
        for (var _i0 = 0; _i0 < count; _i0++) {
          uvs[_i0 * 2] = Math.hypot(geo.positions[_i0 * 3], geo.positions[_i0 * 3 + 1], geo.positions[_i0 * 3 + 2]) * inv;
        }
        return {
          positions: geo.positions.slice(),
          normals: geo.normals.slice(),
          uvs: uvs,
          indices: geo.indices.slice()
        };
      }
      function profileFrom(control) {
        var steps = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 18;
        if (control.length < 2) {
          return control.slice();
        }
        var out = [];
        var at = function at(i) {
          return control[Math.max(0, Math.min(control.length - 1, i))];
        };
        for (var i = 0; i < control.length - 1; i++) {
          var p0 = at(i - 1);
          var p1 = at(i);
          var p2 = at(i + 1);
          var p3 = at(i + 2);
          var last = i === control.length - 2;
          var n = last ? steps : steps - 1;
          for (var s = 0; s <= n; s++) {
            var t = s / n;
            var t2 = t * t;
            var t3 = t2 * t;
            var r = 0.5 * (2 * p1[0] + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3);
            var y = 0.5 * (2 * p1[1] + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3);
            out.push([Math.max(0, r), y]);
          }
        }
        return out;
      }
      cclegacy._RF.pop();
    }
  };
});

System.register("chunks:///_virtual/PetRoomBuilder.js", ['./rollupPluginModLoBabelHelpers.js', 'cc', './PetGameTheme.js'], function (exports) {
  var _slicedToArray, cclegacy, Color, Vec3, shift;
  return {
    setters: [function (module) {
      _slicedToArray = module.slicedToArray;
    }, function (module) {
      cclegacy = module.cclegacy;
      Color = module.Color;
      Vec3 = module.Vec3;
    }, function (module) {
      shift = module.shift;
    }],
    execute: function () {
      exports('buildRoom', buildRoom);
      cclegacy._RF.push({}, "ade82hg2nhNwax6SHFVUJVp", "PetRoomBuilder", undefined);

      /**
       * 温馨宠物房间场景（视觉重构 v3）。
       *
       * 空间分层（相机在 +z 看向 -z，宠物位于地毯中央 z≈0.35）：
       *  - 背景层：后墙 / 踢脚线 / 大窗 + 窗帘 + 阳光光斑 / 挂画 / 挂灯串 / 置物架；
       *  - 中景层：木地板（含板缝） / 三层圆地毯 / 宠物窝 / 斗柜 / 绿植 / 散落玩具；
       *  - 前景层：靠垫、地毯边缘玩具、飘浮光点（由动画层驱动上升），制造纵深。
       *
       * 配色走"暖奶油 + 蜜糖木色 + 柔粉"的低饱和暖色系，与宠物高饱和主色形成对比，
       * 让角色从环境中跳出来；墙面与地板只用大色块 + 细分割线，避免抢戏。
       *
       * 全部零外部资产：引擎原语 + pet-toon 材质程序化构建。
       */

      /** 房间可动元素引用（PetGameRoot.update 驱动氛围微动画） */

      /**
       * 房间配色（集中定义，便于整体调色）。
       *
       * v7 改为**法式奶油风**：墙/护墙板走象牙奶油，地板走浅橡木，家具一律奶油漆面（不再用褐色实木），
       * 地毯用玫瑰+鼠尾草双色，黄铜作金属点缀。
       * 旧版（v6）墙 0xC69A70 / 木家具 0xAF7C4C 是褐色系，整屋偏暗偏旧 —— 已整体替换。
       */
      var C = {
        // 墙：象牙奶油（亮），暗部只压一档暖灰，绝不再落到深褐
        wall: new Color(0xF5, 0xE6, 0xD2, 255),
        wallShade: new Color(0xD9, 0xC3, 0xA8, 255),
        // 护墙板/线条：近白奶油，做出法式墙裙的层次
        baseboard: new Color(0xFB, 0xF5, 0xEC, 255),
        baseboardShade: new Color(0xE4, 0xD4, 0xC0, 255),
        // 地板：浅橡木奶油（比墙略深，拉出层次但不过曝）
        floor: new Color(0xEA, 0xD8, 0xBF, 255),
        floorShade: new Color(0xCF, 0xB9, 0x9A, 255),
        floorLine: new Color(0xC8, 0xB1, 0x92, 255),
        // 地毯：玫瑰外圈 + 奶油中圈 + 鼠尾草中心（宠物站在中心仍有明度层级）
        rugOuter: new Color(0xE8, 0xB4, 0xB8, 255),
        rugInner: new Color(0xF7, 0xEC, 0xDA, 255),
        rugCenter: new Color(0xA8, 0xC3, 0x9A, 255),
        // 接触阴影（暖褐，绝不用黑：冷黑阴影在暖色空间里会发脏）
        shadow: new Color(0x9A, 0x7C, 0x62, 255),
        // 家具：奶油漆面木（法式白木），不再是褐色
        wood: new Color(0xF2, 0xE1, 0xCA, 255),
        woodDark: new Color(0xD8, 0xC0, 0xA2, 255),
        // 黄铜点缀（把手/杆件/镜框）
        brass: new Color(0xCB, 0xA3, 0x6B, 255),
        brassDark: new Color(0xA8, 0x83, 0x4F, 255),
        bedRim: new Color(0xE6, 0xD0, 0xB2, 255),
        bedCushion: new Color(0xF6, 0xBF, 0xCE, 255),
        windowFrame: new Color(0xFD, 0xF9, 0xF4, 255),
        glass: new Color(0xB8, 0xDF, 0xF3, 255),
        curtain: new Color(0xF8, 0xD7, 0xDA, 255),
        sun: new Color(0xFF, 0xF1, 0xC8, 255),
        leaf: new Color(0x7E, 0xC1, 0x93, 255),
        leafDark: new Color(0x5F, 0xA4, 0x79, 255),
        // 陶瓷器皿（花瓶/碗盘）：近白暖瓷
        vase: new Color(0xFB, 0xF5, 0xEB, 255),
        china: new Color(0xFD, 0xF8, 0xF0, 255),
        // 书本与玩具：雾蓝 / 玫瑰 / 奶油黄的三色小面积点缀
        book1: new Color(0x9C, 0xBE, 0xE0, 255),
        book2: new Color(0xF4, 0xB0, 0xC2, 255),
        book3: new Color(0xF7, 0xDD, 0xAE, 255),
        bulb: new Color(0xFF, 0xE2, 0x9E, 255),
        ball: new Color(0x9C, 0xBE, 0xE0, 255),
        yarn: new Color(0xF4, 0xB0, 0xC2, 255),
        duck: new Color(0xF7, 0xDD, 0xAE, 255),
        duckBeak: new Color(0xEF, 0xB1, 0x77, 255),
        cushion: new Color(0xF9, 0xEC, 0xD8, 255),
        picture: new Color(0xFE, 0xF6, 0xEA, 255),
        heart: new Color(0xFF, 0x9C, 0xB0, 255)
      };

      /**
       * 快速风格：主色 + 可选暗部/描边覆盖。
       *
       * `plain` 用于需要**标准 alpha 混合**的部件（光点/阳光片/玻璃）：走引擎内置材质
       * （自定义 effect 的 transparent 技术在本构建链下会丢失材质属性）。
       */
      function style(color, opts) {
        var _opts$outline;
        return {
          color: color,
          shade: opts === null || opts === void 0 ? void 0 : opts.shade,
          outline: (_opts$outline = opts === null || opts === void 0 ? void 0 : opts.outline) !== null && _opts$outline !== void 0 ? _opts$outline : 0,
          alpha: opts === null || opts === void 0 ? void 0 : opts.alpha,
          glow: opts === null || opts === void 0 ? void 0 : opts.glow,
          plain: opts === null || opts === void 0 ? void 0 : opts.plain
        };
      }

      /** 构建整个房间（parent 为世界根节点；返回氛围动画引用） */
      function buildRoom(parent, kit) {
        buildFloor(parent, kit);
        buildWalls(parent, kit);
        buildWindow(parent, kit);
        buildWallDecor(parent, kit);
        buildRug(parent, kit);
        buildBed(parent, kit);
        buildCabinet(parent, kit);
        buildPlant(parent, kit);
        var toyBall = buildToys(parent, kit);
        buildContactShadows(parent, kit);
        buildForeground(parent, kit);
        var bulbs = buildLampString(parent, kit);
        var sunBeam = buildSunBeam(parent, kit);
        return {
          bulbs: bulbs,
          sunBeam: sunBeam,
          toyBall: toyBall
        };
      }

      /** 地板：大色块 + 竖向板缝（长条木板感），顶面 y=0 */
      function buildFloor(parent, kit) {
        var root = kit.make3dNode(parent, 'Floor', new Vec3(0, 0, 0));
        kit.boxPart(root, 'Slab', {
          w: 16,
          h: 0.2,
          d: 7.6
        }, style(C.floor, {
          shade: C.floorShade
        }), new Vec3(0, -0.1, 0.4));
        var line = style(C.floorLine, {
          shade: C.floorLine
        });
        for (var index = -5; index <= 5; index += 1) {
          kit.boxPart(root, 'Seam', {
            w: 0.035,
            h: 0.014,
            d: 7.6
          }, line, new Vec3(index * 1.55, 0.006, 0.4));
        }
        for (var _i = 0, _arr = [-1.9, -0.4, 1.1, 2.6]; _i < _arr.length; _i++) {
          var z = _arr[_i];
          kit.boxPart(root, 'Joint', {
            w: 16,
            h: 0.014,
            d: 0.035
          }, line, new Vec3(0, 0.006, z));
        }
      }

      /** 墙体：后墙 + 左墙 + 踢脚线（背景层次，不做描边避免抢戏） */
      function buildWalls(parent, kit) {
        var root = kit.make3dNode(parent, 'Walls', new Vec3(0, 0, 0));
        var wall = style(C.wall, {
          shade: C.wallShade
        });
        var base = style(C.baseboard, {
          shade: C.baseboardShade
        });
        kit.boxPart(root, 'Back', {
          w: 16,
          h: 4.8,
          d: 0.3
        }, wall, new Vec3(0, 2.4, -3.0));
        kit.boxPart(root, 'BackBase', {
          w: 16,
          h: 0.36,
          d: 0.4
        }, base, new Vec3(0, 0.18, -2.93));
        kit.boxPart(root, 'Left', {
          w: 0.3,
          h: 4.8,
          d: 7.6
        }, wall, new Vec3(-4.6, 2.4, 0.4));
        kit.boxPart(root, 'LeftBase', {
          w: 0.4,
          h: 0.36,
          d: 7.6
        }, base, new Vec3(-4.53, 0.18, 0.4));
        // 法式线条：顶线（crown）+ 腰线（chair rail），近白奶油勾出墙裙层次
        kit.boxPart(root, 'CrownBack', {
          w: 16,
          h: 0.16,
          d: 0.44
        }, base, new Vec3(0, 4.62, -2.92));
        kit.boxPart(root, 'RailBack', {
          w: 16,
          h: 0.1,
          d: 0.4
        }, base, new Vec3(0, 1.12, -2.94));
        kit.boxPart(root, 'CrownLeft', {
          w: 0.44,
          h: 0.16,
          d: 7.6
        }, base, new Vec3(-4.5, 4.62, 0.4));
        kit.boxPart(root, 'RailLeft', {
          w: 0.4,
          h: 0.1,
          d: 7.6
        }, base, new Vec3(-4.52, 1.12, 0.4));
      }

      /** 大窗：窗框 + 透光玻璃（自发光）+ 十字窗棂 + 两侧窗帘 */
      function buildWindow(parent, kit) {
        var root = kit.make3dNode(parent, 'Window', new Vec3(-2.0, 2.45, -2.82));
        var frame = style(C.windowFrame, {
          shade: shift(C.windowFrame, -0.18)
        });
        kit.boxPart(root, 'Frame', {
          w: 2.15,
          h: 1.75,
          d: 0.14
        }, frame, new Vec3(0, 0, 0));
        kit.boxPart(root, 'Glass', {
          w: 1.86,
          h: 1.46,
          d: 0.06
        }, style(C.glass, {
          glow: true
        }), new Vec3(0, 0, 0.05));
        kit.boxPart(root, 'MullionV', {
          w: 0.09,
          h: 1.46,
          d: 0.1
        }, frame, new Vec3(0, 0, 0.06));
        kit.boxPart(root, 'MullionH', {
          w: 1.86,
          h: 0.09,
          d: 0.1
        }, frame, new Vec3(0, 0, 0.06));
        kit.boxPart(root, 'Sill', {
          w: 2.4,
          h: 0.12,
          d: 0.34
        }, frame, new Vec3(0, -0.93, 0.08));

        // 窗帘：帘杆 + 左右帘布 + 帘头
        var curtain = style(C.curtain, {
          shade: shift(C.curtain, -0.16)
        });
        kit.capPart(root, 'Rod', 0.045, 3.5, style(C.brass, {
          shade: C.brassDark
        }), new Vec3(0, 1.06, 0.12), {
          rot: new Vec3(0, 0, 90)
        });
        kit.boxPart(root, 'CurtainL', {
          w: 0.5,
          h: 2.3,
          d: 0.18
        }, curtain, new Vec3(-1.28, -0.1, 0.12));
        kit.boxPart(root, 'CurtainR', {
          w: 0.5,
          h: 2.3,
          d: 0.18
        }, curtain, new Vec3(1.28, -0.1, 0.12));
        kit.boxPart(root, 'Pelmet', {
          w: 2.9,
          h: 0.26,
          d: 0.24
        }, curtain, new Vec3(0, 1.02, 0.12));

        // 窗台小花瓶：一朵玫瑰 + 两片叶
        kit.cylPart(root, 'VaseSmall', 0.09, 0.07, 0.16, style(C.vase, {
          shade: shift(C.vase, -0.1)
        }), new Vec3(0.72, -0.79, 0.12));
        kit.capPart(root, 'StemSmall', 0.016, 0.14, style(C.leafDark, {
          shade: shift(C.leafDark, -0.18)
        }), new Vec3(0.72, -0.66, 0.12));
        kit.ball(root, 'BloomSmall', 0.075, style(C.heart, {
          shade: shift(C.heart, -0.18)
        }), new Vec3(0.72, -0.55, 0.12));
        kit.ball(root, 'LeafTinyA', 0.045, style(C.leaf, {
          shade: C.leafDark
        }), new Vec3(0.66, -0.62, 0.1), {
          scale: new Vec3(1, 0.6, 1)
        });
        kit.ball(root, 'LeafTinyB', 0.04, style(C.leaf, {
          shade: C.leafDark
        }), new Vec3(0.78, -0.6, 0.14), {
          scale: new Vec3(1, 0.6, 1)
        });
      }

      /** 墙面装饰：挂画（含心形图案）+ 挂灯串（灯泡节点）+ 云朵星饰 */
      function buildWallDecor(parent, kit) {
        var root = kit.make3dNode(parent, 'WallDecor', new Vec3(0, 0, 0));

        // 挂画：黄铜画框（法式金框），画面为奶油底 + 玫瑰心
        var frame = kit.make3dNode(root, 'Painting', new Vec3(1.85, 2.55, -2.8));
        kit.boxPart(frame, 'Frame', {
          w: 1.5,
          h: 1.2,
          d: 0.1
        }, style(C.brass, {
          shade: C.brassDark
        }), new Vec3(0, 0, 0));
        kit.boxPart(frame, 'Canvas', {
          w: 1.26,
          h: 0.96,
          d: 0.06
        }, style(C.picture, {
          shade: shift(C.picture, -0.12)
        }), new Vec3(0, 0, 0.04));
        // 心形：两球 + 一锥（朝下的尖）
        kit.ball(frame, 'HeartL', 0.14, style(C.heart, {
          shade: shift(C.heart, -0.2)
        }), new Vec3(-0.1, 0.08, 0.07));
        kit.ball(frame, 'HeartR', 0.14, style(C.heart, {
          shade: shift(C.heart, -0.2)
        }), new Vec3(0.1, 0.08, 0.07));
        kit.conePart(frame, 'HeartTip', 0.19, 0.26, style(C.heart, {
          shade: shift(C.heart, -0.2)
        }), new Vec3(0, -0.14, 0.07), {
          rot: new Vec3(180, 0, 0)
        });

        // 云朵 + 星星（左上角墙面）
        var cloud = kit.make3dNode(root, 'Cloud', new Vec3(-3.3, 3.5, -2.8));
        kit.ball(cloud, 'c1', 0.26, style(new Color(0xFF, 0xFB, 0xF2, 255), {
          shade: C.wallShade
        }), new Vec3(-0.24, 0, 0));
        kit.ball(cloud, 'c2', 0.34, style(new Color(0xFF, 0xFB, 0xF2, 255), {
          shade: C.wallShade
        }), new Vec3(0.15, 0.06, 0));
        kit.ball(cloud, 'c3', 0.22, style(new Color(0xFF, 0xFB, 0xF2, 255), {
          shade: C.wallShade
        }), new Vec3(0.5, -0.02, 0));
        kit.ball(cloud, 'star', 0.1, style(C.bulb, {
          glow: true
        }), new Vec3(0.9, 0.34, 0));

        // 置物架（右后墙）：两层木板 + 书本 + 小盆栽
        var shelf = kit.make3dNode(root, 'Shelf', new Vec3(3.35, 0, -2.7));
        var woodStyle = style(C.wood, {
          shade: C.woodDark
        });
        kit.boxPart(shelf, 'BoardTop', {
          w: 1.7,
          h: 0.08,
          d: 0.44
        }, woodStyle, new Vec3(0, 2.28, 0));
        kit.boxPart(shelf, 'BoardBottom', {
          w: 1.7,
          h: 0.08,
          d: 0.44
        }, woodStyle, new Vec3(0, 1.52, 0));
        kit.boxPart(shelf, 'SideL', {
          w: 0.09,
          h: 1.5,
          d: 0.44
        }, woodStyle, new Vec3(-0.8, 1.9, 0));
        kit.boxPart(shelf, 'SideR', {
          w: 0.09,
          h: 1.5,
          d: 0.44
        }, woodStyle, new Vec3(0.8, 1.9, 0));
        // 书本（竖放，彩色书脊）
        var books = [[C.book1, -0.5, 0.16], [C.book2, -0.3, 0.14], [C.book3, -0.11, 0.18]];
        for (var _i2 = 0, _books = books; _i2 < _books.length; _i2++) {
          var _books$_i = _slicedToArray(_books[_i2], 3),
            color = _books$_i[0],
            x = _books$_i[1],
            h = _books$_i[2];
          kit.boxPart(shelf, 'Book', {
            w: 0.13,
            h: h,
            d: 0.3
          }, style(color, {
            shade: shift(color, -0.22)
          }), new Vec3(x, 2.32 + h / 2, 0.02));
        }
        // 架上小花瓶：一朵雾蓝小花
        kit.cylPart(shelf, 'VaseShelf', 0.09, 0.07, 0.15, style(C.vase, {
          shade: shift(C.vase, -0.1)
        }), new Vec3(0.45, 1.62 + 0.075, 0));
        kit.capPart(shelf, 'StemShelf', 0.014, 0.12, style(C.leafDark, {
          shade: shift(C.leafDark, -0.18)
        }), new Vec3(0.45, 1.85, 0));
        kit.ball(shelf, 'BloomShelf', 0.065, style(C.book1, {
          shade: shift(C.book1, -0.18)
        }), new Vec3(0.45, 1.94, 0));
        kit.ball(shelf, 'LeafShelf', 0.05, style(C.leaf, {
          shade: C.leafDark
        }), new Vec3(0.52, 1.88, 0.02), {
          scale: new Vec3(1, 0.6, 1)
        });
      }

      /** 圆地毯（三层同心圆 + 边缘滚边），宠物活动区中心 */
      function buildRug(parent, kit) {
        var root = kit.make3dNode(parent, 'Rug', new Vec3(0, 0, 0.35));
        kit.cylPart(root, 'Edge', 2.42, 2.42, 0.045, style(shift(C.rugOuter, -0.2), {
          shade: shift(C.rugOuter, -0.32)
        }), new Vec3(0, 0.022, 0));
        kit.cylPart(root, 'Outer', 2.3, 2.3, 0.05, style(C.rugOuter, {
          shade: shift(C.rugOuter, -0.14)
        }), new Vec3(0, 0.03, 0));
        kit.cylPart(root, 'Inner', 1.92, 1.92, 0.055, style(C.rugInner, {
          shade: shift(C.rugInner, -0.14)
        }), new Vec3(0, 0.035, 0));
        kit.cylPart(root, 'Center', 0.95, 0.95, 0.06, style(C.rugCenter, {
          shade: shift(C.rugCenter, -0.14)
        }), new Vec3(0, 0.04, 0));
      }

      /** 宠物窝：外圈软垫 + 内垫 + 靠枕（右侧中景） */
      function buildBed(parent, kit) {
        // 让开中央活动区：宠物窝退到右侧靠后
        var root = kit.make3dNode(parent, 'PetBed', new Vec3(2.95, 0, 1.35));
        kit.cylPart(root, 'Rim', 0.88, 0.98, 0.34, style(C.bedRim, {
          shade: shift(C.bedRim, -0.18)
        }), new Vec3(0, 0.17, 0));
        kit.cylPart(root, 'Cushion', 0.72, 0.72, 0.16, style(C.bedCushion, {
          shade: shift(C.bedCushion, -0.16)
        }), new Vec3(0, 0.26, 0));
        kit.ball(root, 'Pillow', 0.34, style(new Color(0xFF, 0xE3, 0xC9, 255), {
          shade: new Color(0xE8, 0xC6, 0xA6, 255)
        }), new Vec3(-0.2, 0.34, -0.42), {
          scale: new Vec3(1.15, 0.72, 0.85)
        });
      }

      /** 斗柜（左侧背景）：柜体 + 抽屉线 + 把手 + 台面摆件 */
      function buildCabinet(parent, kit) {
        var root = kit.make3dNode(parent, 'Cabinet', new Vec3(-3.55, 0, -0.1));
        var woodStyle = style(C.wood, {
          shade: C.woodDark
        });
        kit.boxPart(root, 'Body', {
          w: 1.25,
          h: 1.35,
          d: 1.0
        }, woodStyle, new Vec3(0, 0.675, 0));
        kit.boxPart(root, 'Top', {
          w: 1.35,
          h: 0.09,
          d: 1.1
        }, style(shift(C.wood, 0.14), {
          shade: C.wood
        }), new Vec3(0, 1.39, 0));
        var drawerLine = style(shift(C.woodDark, -0.16), {
          shade: shift(C.woodDark, -0.2)
        });
        for (var _i3 = 0, _arr2 = [0.42, 0.92]; _i3 < _arr2.length; _i3++) {
          var y = _arr2[_i3];
          kit.boxPart(root, 'DrawerGap', {
            w: 1.19,
            h: 0.045,
            d: 1.02
          }, drawerLine, new Vec3(0, y, 0));
        }
        kit.ball(root, 'Knob1', 0.055, style(C.brass, {
          shade: C.brassDark
        }), new Vec3(0, 0.66, 0.53));
        kit.ball(root, 'Knob2', 0.055, style(C.brass, {
          shade: C.brassDark
        }), new Vec3(0, 1.14, 0.53));
        // 台面餐具：立式碟架（圆盘面朝镜头，玫瑰/雾蓝描边）+ 两只粉彩圆碗 —— 替换旧版尖顶台灯
        // （平摞的薄盘在低机位下只剩一条线，立起来才读得出"盘子"）
        var china = style(C.china, {
          shade: shift(C.china, -0.1)
        });
        var rackWood = style(C.wood, {
          shade: C.woodDark
        });
        kit.boxPart(root, 'RackBase', {
          w: 0.72,
          h: 0.05,
          d: 0.26
        }, rackWood, new Vec3(-0.28, 1.46, 0.1));
        kit.boxPart(root, 'RackBack', {
          w: 0.72,
          h: 0.34,
          d: 0.035
        }, rackWood, new Vec3(-0.28, 1.63, -0.03));
        kit.boxPart(root, 'RackPostL', {
          w: 0.05,
          h: 0.3,
          d: 0.24
        }, rackWood, new Vec3(-0.55, 1.62, 0.06));
        kit.boxPart(root, 'RackPostR', {
          w: 0.05,
          h: 0.3,
          d: 0.24
        }, rackWood, new Vec3(-0.01, 1.62, 0.06));
        var standingPlate = function standingPlate(x, rim) {
          var face = new Vec3(x, 1.645, 0.1);
          kit.cylPart(root, 'Plate', 0.16, 0.16, 0.035, china, face, {
            rot: new Vec3(90, 0, 0)
          });
          kit.cylPart(root, 'PlateRim', 0.17, 0.17, 0.016, style(rim, {
            shade: shift(rim, -0.18)
          }), new Vec3(x, 1.645, 0.115), {
            rot: new Vec3(90, 0, 0)
          });
          kit.cylPart(root, 'PlateHub', 0.055, 0.055, 0.018, style(rim, {
            shade: shift(rim, -0.08)
          }), new Vec3(x, 1.645, 0.128), {
            rot: new Vec3(90, 0, 0)
          });
        };
        standingPlate(-0.44, C.heart);
        standingPlate(-0.12, C.book1);
        // 圆碗：粉彩球体下陷成碗身 + 奶油碗口内壁（圆润立体，双色）
        var bowl = function bowl(x, color) {
          kit.ball(root, 'BowlBody', 0.15, style(color, {
            shade: shift(color, -0.2)
          }), new Vec3(x, 1.53, 0.12), {
            scale: new Vec3(1, 0.72, 1)
          });
          kit.ball(root, 'BowlInner', 0.15, style(C.china, {
            shade: shift(C.china, -0.06)
          }), new Vec3(x, 1.562, 0.12), {
            scale: new Vec3(0.8, 0.3, 0.8)
          });
        };
        bowl(0.18, C.leaf);
        bowl(0.46, C.book1);
      }

      /** 鲜花花瓶（左后角）：陶瓷花瓶 + 三束鲜花（玫瑰/奶油黄/雾蓝）+ 叶——替换旧版绿盆栽 */
      function buildPlant(parent, kit) {
        var root = kit.make3dNode(parent, 'FlowerVase', new Vec3(-3.7, 0, -2.1));
        // 陶瓷花瓶：鼓腹 + 束颈
        kit.cylPart(root, 'VaseBody', 0.34, 0.24, 0.5, style(C.vase, {
          shade: shift(C.vase, -0.12)
        }), new Vec3(0, 0.25, 0));
        kit.cylPart(root, 'VaseNeck', 0.17, 0.21, 0.3, style(C.vase, {
          shade: shift(C.vase, -0.08)
        }), new Vec3(0, 0.6, 0));
        kit.cylPart(root, 'VaseRim', 0.19, 0.19, 0.035, style(C.heart, {
          shade: shift(C.heart, -0.2)
        }), new Vec3(0, 0.765, 0));
        // 三束花：茎从瓶口散开，花头 = 花瓣球 + 花芯
        var blooms = [[-0.24, 1.3, 0.08, C.heart], [0.04, 1.44, -0.06, C.book3], [0.27, 1.24, 0.12, C.book1]];
        for (var _i4 = 0, _blooms = blooms; _i4 < _blooms.length; _i4++) {
          var _blooms$_i = _slicedToArray(_blooms[_i4], 4),
            x = _blooms$_i[0],
            y = _blooms$_i[1],
            z = _blooms$_i[2],
            color = _blooms$_i[3];
          var dx = x;
          var dz = z;
          var dy = y - 0.78;
          var len = Math.sqrt(dx * dx + dy * dy + dz * dz);
          var mid = new Vec3(dx / 2, 0.78 + dy / 2, dz / 2);
          kit.capPart(root, 'Stem', 0.026, len, style(C.leafDark, {
            shade: shift(C.leafDark, -0.18)
          }), mid, {
            rot: new Vec3(dz / len * 80, 0, -dx / len * 80)
          });
          kit.ball(root, 'Petal', 0.11, style(color, {
            shade: shift(color, -0.2)
          }), new Vec3(x, y, z));
          kit.ball(root, 'Core', 0.045, style(C.book3, {
            shade: shift(C.book3, -0.24)
          }), new Vec3(x, y + 0.02, z));
        }
        // 叶片点缀
        kit.ball(root, 'LeafA', 0.09, style(C.leaf, {
          shade: C.leafDark
        }), new Vec3(-0.16, 0.95, 0.05), {
          scale: new Vec3(1, 0.55, 1)
        });
        kit.ball(root, 'LeafB', 0.08, style(C.leaf, {
          shade: C.leafDark
        }), new Vec3(0.18, 1.02, 0.02), {
          scale: new Vec3(1, 0.55, 1)
        });
      }

      /** 散落玩具：皮球 / 毛线球 / 小黄鸭 / 骨头（中景与窝边，避免遮挡宠物正面）；返回皮球供氛围动画引用 */
      function buildToys(parent, kit) {
        var root = kit.make3dNode(parent, 'Toys', new Vec3(0, 0, 0));
        // 皮球（带白色条纹感：叠加一圈小球）
        var ball = kit.make3dNode(root, 'ToyBall', new Vec3(-1.35, 0.19, 1.05));
        kit.ball(ball, 'Core', 0.19, style(C.ball, {
          shade: shift(C.ball, -0.24)
        }), new Vec3(0, 0, 0));
        kit.ball(ball, 'Stripe', 0.195, style(new Color(0xFD, 0xF8, 0xF0, 255), {
          shade: shift(C.ball, -0.34)
        }), new Vec3(0, 0, 0), {
          scale: new Vec3(0.32, 1.02, 1.02)
        });
        // 毛线球
        kit.ball(root, 'Yarn', 0.17, style(C.yarn, {
          shade: shift(C.yarn, -0.22)
        }), new Vec3(-1.95, 0.17, 0.5));
        kit.torusPart(root, 'YarnRing', 0.17, 0.02, style(shift(C.yarn, -0.28), {
          shade: shift(C.yarn, -0.36)
        }), new Vec3(-1.95, 0.17, 0.5), {
          rot: new Vec3(24, 0, 18)
        });
        // 小黄鸭
        var duck = kit.make3dNode(root, 'Duck', new Vec3(2.15, 0, 1.75));
        kit.ball(duck, 'Body', 0.19, style(C.duck, {
          shade: shift(C.duck, -0.2)
        }), new Vec3(0, 0.16, 0), {
          scale: new Vec3(1, 0.9, 1.1)
        });
        kit.ball(duck, 'Head', 0.13, style(C.duck, {
          shade: shift(C.duck, -0.2)
        }), new Vec3(0, 0.36, 0.06));
        kit.conePart(duck, 'Beak', 0.055, 0.12, style(C.duckBeak, {
          shade: shift(C.duckBeak, -0.2)
        }), new Vec3(0, 0.34, 0.2), {
          rot: new Vec3(90, 0, 0)
        });
        // 骨头玩具
        var bone = kit.make3dNode(root, 'Bone', new Vec3(1.45, 0.1, 2.0));
        kit.capPart(bone, 'Bar', 0.06, 0.34, style(new Color(0xFD, 0xF2, 0xDE, 255), {
          shade: new Color(0xE3, 0xD2, 0xB8, 255)
        }), new Vec3(0, 0, 0), {
          rot: new Vec3(0, 0, 74)
        });
        kit.ball(bone, 'KnobL', 0.09, style(new Color(0xFD, 0xF2, 0xDE, 255), {
          shade: new Color(0xE3, 0xD2, 0xB8, 255)
        }), new Vec3(-0.17, 0.04, 0));
        kit.ball(bone, 'KnobR', 0.09, style(new Color(0xFD, 0xF2, 0xDE, 255), {
          shade: new Color(0xE3, 0xD2, 0xB8, 255)
        }), new Vec3(0.17, -0.04, 0));
        return ball;
      }

      /** 前景：地毯前缘靠垫（制造前后景层次） */
      function buildForeground(parent, kit) {
        var root = kit.make3dNode(parent, 'Foreground', new Vec3(0, 0, 0));
        kit.ball(root, 'Cushion', 0.46, style(C.cushion, {
          shade: shift(C.cushion, -0.16)
        }), new Vec3(-1.3, 0.16, 2.45), {
          scale: new Vec3(1.25, 0.55, 0.95)
        });
        kit.ball(root, 'CushionSmall', 0.3, style(shift(C.cushion, -0.12), {
          shade: shift(C.cushion, -0.24)
        }), new Vec3(2.0, 0.12, 2.6), {
          scale: new Vec3(1.2, 0.6, 1)
        });
      }

      /** 挂灯串：暖黄灯泡沿后墙上方弧线分布（轻微呼吸闪烁） */
      function buildLampString(parent, kit) {
        var root = kit.make3dNode(parent, 'LampString', new Vec3(0, 0, -2.72));
        var bulbs = [];
        for (var index = 0; index < 9; index += 1) {
          var t = index / 8;
          var x = -4.4 + t * 8.8;
          var y = 3.72 - Math.sin(t * Math.PI) * 0.42;
          var bulb = kit.ball(root, 'Bulb' + index, 0.085, {
            color: C.bulb,
            shade: shift(C.bulb, -0.08),
            glow: true,
            outline: 0
          }, new Vec3(x, y, 0));
          bulbs.push(bulb);
        }
        return bulbs;
      }

      /** 窗边阳光光斑（地板上斜置的暖光片，轻微呼吸） */
      /**
       * 家具与道具的接触阴影 + 墙脚环境遮蔽。
       *
       * 本工程**没有实时阴影**（场景无光源节点、ShadowsInfo 关闭，pet-toon 也是自研着色器
       * 不采样阴影贴图），所以任何贴地物件都不会在地板上留影 —— 结果是所有家具都"浮"着。
       * 这里用柔边贴花逐个补：暖褐色 + 径向 alpha 渐变，叠在物体正下方的地板上。
       *
       * ⚠️ 高度必须按**落点所在的那一层**给：地毯是逐层叠起来的圆盘
       * （Edge 顶 ≈0.045 / Outer ≈0.055 / Inner ≈0.063 / Center ≈0.07，圆心在 z=0.35、半径 2.42），
       * 所以落在地毯上的物件（球、磨牙棒）阴影必须抬到地毯面之上，
       * 否则会被地毯整个盖住 —— 第一版全放在 y=0.009，结果一个都看不见。
       *
       * 摆位与各 build*() 一一对应（改摆位时必须同步改这里）。
       */
      function buildContactShadows(parent, kit) {
        var root = kit.make3dNode(parent, 'ContactShadows', new Vec3(0, 0, 0));
        var blob = function blob(name, rx, rz, x, y, z, alpha) {
          kit.decal(root, name, [rx, 0.008, rz], {
            color: C.shadow,
            shade: C.shadow,
            alpha: alpha,
            soft: [0.02, 1.0]
          }, new Vec3(x, y, z));
        };
        // 地毯：圆心 (0, 0.35)、半径 2.42；Inner 半径 1.92
        blob('ShadowCabinet', 1.15, 0.62, -3.55, 0.009, -0.10, 78); // 柜子（裸地板）
        blob('ShadowPlant', 0.44, 0.44, -3.70, 0.009, -2.10, 66); // 盆栽（裸地板）
        blob('ShadowBed', 1.05, 0.78, 2.60, 0.009, 1.15, 70); // 猫窝（裸地板）
        blob('ShadowDuck', 0.32, 0.24, 2.15, 0.009, 1.75, 70); // 小黄鸭（裸地板）
        blob('ShadowBall', 0.28, 0.28, -1.35, 0.066, 1.05, 74); // 玩具球（地毯 Inner 面之上）
        blob('ShadowBone', 0.38, 0.16, 1.45, 0.058, 2.00, 62); // 磨牙棒（地毯 Outer 面之上）
        // 墙脚环境遮蔽：墙与地板交界处的暗带，是"空间感"最廉价也最有效的一笔
        blob('AOBackWall', 5.20, 0.55, 0.00, 0.012, -2.45, 58); // 后墙（墙面 z=-2.85）
        blob('AOLeftWall', 0.52, 4.20, -4.10, 0.012, 0.40, 46); // 左墙（墙面 x=-4.45）
      }

      /**
       * 窗光落在地板上的柔光斑（"光落地"是判断一个虚拟空间是否真实最有效的线索）。
       *
       * 旧实现是一块 alpha 70 的**硬边**方板（boxPart + plain 引擎材质）——
       * 在没有实时阴影的场景里，它读作"地上贴了一块白斑"而不是光，反而加重了假。
       * 现改为 `kit.decal`：单位椭球把归一化径向距离写进 uv.x，由 pet-toon 的 decalCtrl
       * 生成径向 alpha 渐变，压扁成地面椭圆后边缘自然化开；长轴按窗光入射方向
       * （世界 +x/+z，即右前方）旋转对齐 —— 光斑的朝向必须与主光一致，否则方向感互相打架。
       * 两层叠加（主斑 + 外圈光晕）避免单层椭圆露出边界。
       *
       * 落点按几何反推：窗在 (-2.0, 2.45, -2.85)，主光行进方向 (0.46,-0.56,0.69)，
       * 落到 y=0 的地面时为 t = 2.45/0.56 ≈ 4.38 → (0.0, 0, 0.17)。
       * 那个点正好是地毯中心（宠物站位），所以光斑会被地毯吃掉大部分 ——
       * 因此这里把光斑放在地毯边缘外的裸地板上（左后），既在画面里看得见，也还在窗光的来向上。
       */
      function buildSunBeam(parent, kit) {
        var root = kit.make3dNode(parent, 'SunBeam', new Vec3(0, 0, 0));
        kit.decal(root, 'Pool', [2.30, 0.012, 1.35], {
          color: C.sun,
          shade: C.sun,
          alpha: 132,
          soft: [0.06, 1.0]
        }, new Vec3(-1.55, 0.014, -1.70), new Vec3(0, -56, 0));
        kit.decal(root, 'Halo', [3.30, 0.010, 2.00], {
          color: C.sun,
          shade: C.sun,
          alpha: 54,
          soft: [0.12, 1.0]
        }, new Vec3(-1.05, 0.011, -1.35), new Vec3(0, -56, 0));
        return root;
      }
      cclegacy._RF.pop();
    }
  };
});

(function(r) {
  r('virtual:///prerequisite-imports/main', 'chunks:///_virtual/main.js'); 
})(function(mid, cid) {
    System.register(mid, [cid], function (_export, _context) {
    return {
        setters: [function(_m) {
            var _exportObj = {};

            for (var _key in _m) {
              if (_key !== "default" && _key !== "__esModule") _exportObj[_key] = _m[_key];
            }
      
            _export(_exportObj);
        }],
        execute: function () { }
    };
    });
});
//# sourceMappingURL=data:application/json;charset=utf-8;base64,eyJ2ZXJzaW9uIjozLCJzb3VyY2VzIjpbIi4uL2ZpbGU6L0Q6L0lkZS9JZGVhUHJvamVjdHMvQ2xvdWRNYXJ0L3BldC1nYW1lL2Fzc2V0cy9zY3JpcHRzL2ZpbGU6L0Q6L0lkZS9JZGVhUHJvamVjdHMvQ2xvdWRNYXJ0L3BldC1nYW1lL2Fzc2V0cy9zY3JpcHRzL1BldEJ1aWxkZXJLaXQudHMiLCIuLi9maWxlOi9EOi9JZGUvSWRlYVByb2plY3RzL0Nsb3VkTWFydC9wZXQtZ2FtZS9hc3NldHMvc2NyaXB0cy9maWxlOi9EOi9JZGUvSWRlYVByb2plY3RzL0Nsb3VkTWFydC9wZXQtZ2FtZS9hc3NldHMvc2NyaXB0cy9QZXRFZmZlY3RzLnRzIiwiLi4vZmlsZTovRDovSWRlL0lkZWFQcm9qZWN0cy9DbG91ZE1hcnQvcGV0LWdhbWUvYXNzZXRzL3NjcmlwdHMvZmlsZTovRDovSWRlL0lkZWFQcm9qZWN0cy9DbG91ZE1hcnQvcGV0LWdhbWUvYXNzZXRzL3NjcmlwdHMvUGV0R2FtZUJyaWRnZS50cyIsIi4uL2ZpbGU6L0Q6L0lkZS9JZGVhUHJvamVjdHMvQ2xvdWRNYXJ0L3BldC1nYW1lL2Fzc2V0cy9zY3JpcHRzL2ZpbGU6L0Q6L0lkZS9JZGVhUHJvamVjdHMvQ2xvdWRNYXJ0L3BldC1nYW1lL2Fzc2V0cy9zY3JpcHRzL1BldEdhbWVSb290LnRzIiwiLi4vZmlsZTovRDovSWRlL0lkZWFQcm9qZWN0cy9DbG91ZE1hcnQvcGV0LWdhbWUvYXNzZXRzL3NjcmlwdHMvZmlsZTovRDovSWRlL0lkZWFQcm9qZWN0cy9DbG91ZE1hcnQvcGV0LWdhbWUvYXNzZXRzL3NjcmlwdHMvUGV0R2FtZVRoZW1lLnRzIiwiLi4vZmlsZTovRDovSWRlL0lkZWFQcm9qZWN0cy9DbG91ZE1hcnQvcGV0LWdhbWUvYXNzZXRzL3NjcmlwdHMvZmlsZTovRDovSWRlL0lkZWFQcm9qZWN0cy9DbG91ZE1hcnQvcGV0LWdhbWUvYXNzZXRzL3NjcmlwdHMvUGV0TWVzaEZhY3RvcnkudHMiLCIuLi9maWxlOi9EOi9JZGUvSWRlYVByb2plY3RzL0Nsb3VkTWFydC9wZXQtZ2FtZS9hc3NldHMvc2NyaXB0cy9maWxlOi9EOi9JZGUvSWRlYVByb2plY3RzL0Nsb3VkTWFydC9wZXQtZ2FtZS9hc3NldHMvc2NyaXB0cy9QZXRSb29tQnVpbGRlci50cyJdLCJuYW1lcyI6WyJERUZBVUxUX09VVExJTkUiLCJQZXRCdWlsZGVyS2l0IiwiZXhwb3J0cyIsImNjIiwiX2NsYXNzQ2FsbENoZWNrIiwiX2RlZmluZVByb3BlcnR5IiwiX2NyZWF0ZUNsYXNzIiwia2V5IiwiZ2V0IiwidG9vbkVmZmVjdCIsInZhbHVlIiwic2V0VG9vbkVmZmVjdCIsImVmZmVjdCIsInJlc29sdmVUb29uRWZmZWN0IiwiZm91bmQiLCJFZmZlY3RBc3NldCIsIl9nZXRBbGwiLCJfcmVmIiwicmVnaXN0cnkiLCJnZXRBbGwiLCJjYWxsIiwibGlzdCIsIk1hcCIsIkFycmF5IiwiZnJvbSIsInZhbHVlcyIsImlzQXJyYXkiLCJmaW5kIiwiZW50cnkiLCJuYW1lIiwiZW5kc1dpdGgiLCJpc1Rvb25FZmZlY3QiLCJwaWNrRmFsbGJhY2tFZmZlY3QiLCJzYW1wbGUiLCJmYWxsYmFja0VmZmVjdCIsImJ1aWx0aW4iLCJfZ2V0QnVpbHRpbk1hdGVyaWFsIiwiZWZmZWN0QXNzZXQiLCJlIiwibWFrZTNkTm9kZSIsInBhcmVudCIsInBvcyIsInRyYW5zZm9ybSIsIm5vZGUiLCJOb2RlIiwibGF5ZXIiLCJMYXllcnMiLCJFbnVtIiwiREVGQVVMVCIsInNldFBvc2l0aW9uIiwicm90Iiwic2V0Um90YXRpb25Gcm9tRXVsZXIiLCJ4IiwieSIsInoiLCJzY2FsZSIsInNldFNjYWxlIiwiYWRkQ2hpbGQiLCJhdHRhY2giLCJnZW9tZXRyeSIsInN0eWxlIiwibW9kZWwiLCJhZGRDb21wb25lbnQiLCJNb2RlbENvbXBvbmVudCIsIm1lc2giLCJ1dGlscyIsImNyZWF0ZU1lc2giLCJ0cmFuc3BhcmVudCIsImJsZW5kIiwiYWxwaGEiLCJ1bmRlZmluZWQiLCJwbGFpbiIsInVzZVRvb24iLCJtYXRlcmlhbCIsIk1hdGVyaWFsIiwidGVjaG5pcXVlIiwiaW5pdGlhbGl6ZSIsImVycm9yIiwiY29uc29sZSIsIndhcm4iLCJjb25jYXQiLCJjb2xvciIsIkNvbG9yIiwiciIsImciLCJiIiwic2hhZGUiLCJnbG93IiwiYSIsInNoaWZ0Iiwic2V0UHJvcGVydHkiLCJfc3R5bGUkcmltIiwiX3N0eWxlJHRvcCIsIl9zdHlsZSRmdXIiLCJfc3R5bGUkZnVyU2hhcnAiLCJfc3R5bGUkc3BlYyIsIl9zdHlsZSRzcGVjU2hhcnAiLCJWZWM0IiwicmltIiwidG9wIiwiZnVyIiwiZnVyU2hhcnAiLCJzcGVjIiwic3BlY1NoYXJwIiwibWl4Q3RybCIsIm1peENvbG9yIiwic29mdCIsImZsYXQiLCJibHVzaCIsIk1hdGgiLCJyb3VuZCIsInN0cmVuZ3RoIiwib3V0bGluZSIsIm1heCIsImVkZ2UiLCJwYXJ0Iiwic3VyZiIsInN1cmZHcm91cCIsImdlb3MiLCJsZW5ndGgiLCJtZXJnZSIsImFwcGx5IiwiX3RvQ29uc3VtYWJsZUFycmF5IiwiZGVjYWwiLCJyYWRpdXMiLCJfc3R5bGUkc29mdCIsInNvZnRTdHlsZSIsIl9vYmplY3RTcHJlYWQiLCJyYWRpYWxVViIsImVsbGlwc29pZCIsIlZlYzMiLCJwYXRjaCIsImRpciIsImFuZ2xlVSIsImFuZ2xlViIsInJhZGlpIiwiaW5mbGF0ZSIsImFyZ3VtZW50cyIsInNwaGVyZVBhdGNoIiwiYmFsbCIsInByaW1pdGl2ZXMiLCJzcGhlcmUiLCJib3hQYXJ0Iiwic2l6ZSIsImJveCIsIndpZHRoIiwidyIsImhlaWdodCIsImgiLCJkIiwiY29uZVBhcnQiLCJjb25lIiwiY2FwUGFydCIsImNhcHN1bGUiLCJjeWxQYXJ0IiwidG9wUiIsImJvdHRvbVIiLCJjeWxpbmRlciIsInRvcnVzUGFydCIsInR1YmUiLCJ0b3J1cyIsInNldFBhcnRQcm9wZXJ0eSIsImNvbXBvbmVudHMiLCJjIiwicmVjb2xvciIsIm5leHQiLCJpc1Rvb24iLCJfY2NsZWdhY3kiLCJfUkYiLCJwb3AiLCJQZXRFZmZlY3RzIiwibWFrZU5vZGUiLCJ0b1VpIiwiaGVhcnRzIiwid29ybGQiLCJjb3VudCIsImluZGV4Iiwib2Zmc2V0IiwiR3JhcGhpY3MiLCJkcmF3SGVhcnQiLCJwb3BBbmRSaXNlIiwic3RhcnMiLCJfdGhpcyIsIm9yaWdpbiIsIl9sb29wIiwiYW5nbGUiLCJQSSIsImRyYXdTdGFyIiwib3BhY2l0eSIsIlVJT3BhY2l0eSIsInR3ZWVuIiwiZGVsYXkiLCJ0byIsImVhc2luZyIsImJ5IiwicG9zaXRpb24iLCJjb3MiLCJzaW4iLCJkZXN0cm95Iiwic3RhcnQiLCJidWJibGVzIiwiX3RoaXMyIiwiX2xvb3AyIiwiZHJhd0J1YmJsZSIsInN3YXkiLCJzbGVlcFoiLCJwaGFzZSIsImxhYmVsIiwiTGFiZWwiLCJzdHJpbmciLCJmb250U2l6ZSIsImZvb2QiLCJpY29uIiwib25BcnJpdmUiLCJ0YXJnZXQiLCJmbG9hdFRleHQiLCJ0ZXh0IiwibGluZUhlaWdodCIsInJpbmciLCJsaW5lV2lkdGgiLCJzdHJva2VDb2xvciIsImNpcmNsZSIsInN0cm9rZSIsInNwYXJrbGUiLCJfdGhpczMiLCJfbG9vcDMiLCJyYW5kb20iLCJkaXN0YW5jZSIsInMiLCJmaWxsQ29sb3IiLCJtb3ZlVG8iLCJiZXppZXJDdXJ2ZVRvIiwiZmlsbCIsInBvaW50cyIsInB1c2giLCJsaW5lVG8iLCJjbG9zZSIsInJpc2UiLCJkcmlmdCIsIlBldEdhbWVCcmlkZ2UiLCJldmVudCIsIndpbmRvdyIsImxvY2F0aW9uIiwiZGlzcGF0Y2giLCJkYXRhIiwiZGV0ZWN0SG9zdCIsInd4IiwibWluaVByb2dyYW0iLCJSZWFjdE5hdGl2ZVdlYlZpZXciLCJiaW5kIiwiaG9zdEhhbmRsZXIiLCJhZGRFdmVudExpc3RlbmVyIiwib25NZXNzYWdlIiwiX19wZXRIb3N0TWVzc2FnZSIsInJhdyIsIkpTT04iLCJwYXJzZSIsImRpc3Bvc2UiLCJyZW1vdmVFdmVudExpc3RlbmVyIiwibXNnIiwic291cmNlIiwic2VuZCIsIm1lc3NhZ2UiLCJob3N0IiwicG9zdE1lc3NhZ2UiLCJzdHJpbmdpZnkiLCJ0eXBlIiwibmF2aWdhdGVUbyIsInVybCIsImFjdGlvbiIsImNjY2xhc3MiLCJfZGVjb3JhdG9yIiwiUEVUX0NMSVBTIiwiUEVUX1BPUyIsIkZSVUlUX1NQRUNTIiwic3RyYXdiZXJyeSIsInBhdGgiLCJoZWFkIiwic2hhZG93Iiwib3JhbmdlIiwid2F0ZXJtZWxvbiIsImJsdWViZXJyeSIsImRyYWdvbmZydWl0IiwiREVGQVVMVF9GUlVJVCIsIlNQRUNJRVNfU0xPVCIsIkNBVCIsIkRPRyIsIlJBQkJJVCIsIkZPWCIsIlBBTkRBIiwiU1RSQVdCRVJSWSIsIk9SQU5HRSIsIldBVEVSTUVMT04iLCJCTFVFQkVSUlkiLCJEUkFHT05GUlVJVCIsInJlc29sdmVGcnVpdEtleSIsInVybFNwZWNpZXMiLCJwZXRTcGVjaWVzIiwidG9Mb3dlckNhc2UiLCJzbG90IiwidG9VcHBlckNhc2UiLCJDQU1FUkFfU0hPVCIsInJvb20iLCJwb3J0cmFpdCIsImZyb250IiwicTM0IiwiUExBSU5fQkciLCJQZXRHYW1lUm9vdCIsIl9kZWMiLCJfY2xhc3MiLCJfUGV0R2FtZVJvb3QiLCJfQ29tcG9uZW50IiwiX2xlbiIsImFyZ3MiLCJfa2V5IiwiX2NhbGxTdXBlciIsImdsb2JhbFRoaXMiLCJjYW1lcmEzZCIsInBldE5vZGUiLCJub3ciLCJEYXRlIiwidGFwQ29vbGRvd25BdCIsImNvbWJvIiwidGFwQ29tYm9BdCIsIm1pbiIsInRhcENvbWJvIiwidWkiLCJnZXRVSUxvY2F0aW9uIiwicmF5Iiwic2NyZWVuUG9pbnRUb1JheSIsIndwIiwid29ybGRQb3NpdGlvbiIsImNlbnRlclkiLCJmcnVpdCIsIm94IiwibyIsIm95Iiwib3oiLCJ0IiwicHgiLCJweSIsInB6IiwicGxheUNsaXAiLCJsaW5lcyIsIlRBUF9MSU5FUyIsImxpbmUiLCJlZmZlY3RzIiwiaGVhZFdvcmxkIiwiZXVsZXJBbmdsZXMiLCJwZXJpb2QiLCJmbG9vciIsIlJFV0FSRF9QRVJJT0RfTVMiLCJyZXdhcmRQZXJpb2RLZXkiLCJyZXdhcmRVc2VkSW5QZXJpb2QiLCJSRVdBUkRfTUFYX1BFUl9QRVJJT0QiLCJicmlkZ2UiLCJfaW5oZXJpdHMiLCJfZW5naW5lJGRpcmVjdG9yIiwiX2VuZ2luZSRkaXJlY3RvciRzZXREIiwiZW5naW5lIiwiY2NSdW50aW1lIiwiZGlyZWN0b3IiLCJzZXREaXNwbGF5U3RhdHMiLCJwYXJhbXMiLCJVUkxTZWFyY2hQYXJhbXMiLCJzZWFyY2giLCJwcm9iaW5nIiwic2hvdCIsImlubmVySGVpZ2h0IiwiaW5uZXJXaWR0aCIsInJvb3RUcmFuc2Zvcm0iLCJnZXRDb21wb25lbnQiLCJVSVRyYW5zZm9ybSIsImtpdCIsInJhd01hdCIsInJlc291cmNlcyIsImxvYWQiLCJhc3NldCIsImxvZyIsIlN0cmluZyIsInRvb25Bc3NldCIsImJ1aWxkU2NlbmUiLCJidWlsZFdvcmxkIiwiYnVpbGRPdmVybGF5IiwiYmluZEJyaWRnZSIsImJ1aWxkUGV0U2hhZG93IiwibG9hZFBldCIsImFjdGl2ZSIsInByb2JlRnJhbWluZyIsInByb2JlTWF0ZXJpYWxzIiwiX3RoaXMkcGVuZGluZ1BldCRzcGVjIiwiX3RoaXMkcGVuZGluZ1BldCIsInBlbmRpbmdQZXQiLCJzcGVjaWVzIiwiaW5wdXQiLCJvbiIsIklucHV0IiwiRXZlbnRUeXBlIiwiVE9VQ0hfRU5EIiwib25TdGFnZVRhcCIsIlByZWZhYiIsInByZWZhYiIsImluc3RhbnRpYXRlIiwid29ybGQzZCIsImR1bXBUcmVlIiwiY29uZmlndXJlUGV0TWF0ZXJpYWxzIiwiY2xpcFBhdGhzIiwibWFwIiwibG9hZGVkIiwicmVtYWluaW5nIiwic2V0dGxlIiwiYXR0YWNoQ2xpcHMiLCJfaXRlcmF0b3IiLCJfY3JlYXRlRm9yT2ZJdGVyYXRvckhlbHBlciIsIl9zdGVwIiwiQW5pbWF0aW9uQ2xpcCIsImNsaXBFcnJvciIsImNsaXAiLCJuIiwiZG9uZSIsImVyciIsImYiLCJhbmltIiwiU2tlbGV0YWxBbmltYXRpb24iLCJ1c2VCYWtlZEFuaW1hdGlvbiIsImNsaXBzIiwiaWRsZSIsImRlZmF1bHRDbGlwIiwiam9pbiIsImJsaW5rUmVhZHkiLCJzb21lIiwicGV0QW5pbSIsImFwcGx5U3RhdHNUb0FuaW1hdGlvbiIsImRlcHRoIiwibmFtZXMiLCJfaXRlcmF0b3IyIiwiX3N0ZXAyIiwiZXh0cmEiLCJnZXRQcm9wZXJ0eSIsInRleCIsImFsYmVkbyIsInNraW5uaW5nUm9vdCIsImNvbnN0cnVjdG9yIiwicmVwZWF0IiwidG9TdHJpbmciLCJfaXRlcmF0b3IzIiwiY2hpbGRyZW4iLCJfc3RlcDMiLCJjaGlsZCIsIl90aGlzNCIsInZpc2l0IiwiX2l0ZXJhdG9yNCIsIl9zdGVwNCIsImNvbXAiLCJyZW5kZXJlciIsInNyYyIsImVtYmVkZGVkIiwidG9vbiIsInNldE1hdGVyaWFsIiwidGV4QmFjayIsInV1aWQiLCJfaXRlcmF0b3I1IiwiX3N0ZXA1IiwibG9vcCIsIl90aGlzNSIsImhhcyIsInN0YXRlIiwiZ2V0U3RhdGUiLCJzcGVlZCIsInNwZWVkU2NhbGUiLCJwbGF5IiwiX2FuaW0kZ2V0U3RhdGUkZHVyYXRpIiwiX2FuaW0kZ2V0U3RhdGUiLCJzY2hlZHVsZU9uY2UiLCJkdXJhdGlvbiIsInBldCIsIndvcnN0IiwibWF4SHAiLCJocCIsImh1bmdlciIsImhhcHBpbmVzcyIsImVuZXJneSIsImNsZWFubGluZXNzIiwiX2l0ZXJhdG9yNiIsIl9zdGVwNiIsIl90aGlzNiIsImNhbWVyYSIsImZvb3QiLCJ3b3JsZFRvU2NyZWVuIiwic2NyZWVuSGVpZ2h0Iiwic2NyZWVuIiwid2luZG93U2l6ZSIsImZvdiIsInRvRml4ZWQiLCJhYnMiLCJfdGhpczciLCJfaXRlcmF0b3I3IiwiX3N0ZXA3IiwiX21vZGVsJG1hdGVyaWFsJGVmZmVjIiwicGFzc2VzIiwiX2l0ZXJhdG9yOCIsIl9zdGVwOCIsInVwZGF0ZSIsImR0Iiwic3RlcCIsInRpbWUiLCJyb29tVGltZSIsInJvb21BbWJpZW5jZSIsImRyaXZlQmxpbmsiLCJibGlua1RpbWVyIiwib25EZXN0cm95Iiwib2ZmIiwic2NlbmUiLCJidWlsZFJvb20iLCJidWlsZExpZ2h0aW5nIiwiY2FtZXJhTm9kZSIsImdldENoaWxkQnlOYW1lIiwiQ2FtZXJhIiwibG9va0F0IiwiY2xlYXJDb2xvciIsImtleUxpZ2h0IiwiRGlyZWN0aW9uYWxMaWdodCIsImlsbHVtaW5hbmNlIiwiZmlsbExpZ2h0IiwiYW1iaWVudCIsImdldFNjZW5lIiwiZ2xvYmFscyIsInNreUxEUiIsImdyb3VuZExEUiIsInNreUNvbG9yIiwiZ3JvdW5kQWxiZWRvIiwic2t5SWxsdW0iLCJoZHJXcml0YWJsZSIsIl9PYmplY3QkZ2V0T3duUHJvcGVydCIsImRlc2MiLCJPYmplY3QiLCJnZXRPd25Qcm9wZXJ0eURlc2NyaXB0b3IiLCJnZXRQcm90b3R5cGVPZiIsInNldCIsInNreUNvbG9ySERSIiwiZ3JvdW5kQWxiZWRvSERSIiwic2t5SWxsdW1IRFIiLCJyb290IiwiX3RoaXM4IiwibWFrZVVpTm9kZSIsInByb2plY3QiLCJVSV8yRCIsInNldENvbnRlbnRTaXplIiwic2NyZWVuUG9zIiwiY2FudmFzU2l6ZSIsImNvbnRlbnRTaXplIiwic2NhbGVYIiwic2NhbGVZIiwiX3RoaXM5IiwiYnVsYnMiLCJmb3JFYWNoIiwiYnVsYiIsImJlYW1TY2FsZSIsInN1bkJlYW0iLCJ0b3kiLCJ0b3lCYWxsIiwiX3RoaXMwIiwib2siLCJ3b24iLCJjb250ZW50IiwiQ29tcG9uZW50IiwiU1BFQ0lFU19CT0RZIiwiU1BFQ0lFU19EQVJLIiwiU1BFQ0lFU19CRUxMWSIsIlNQRUNJRVNfUEFXIiwiU1BFQ0lFU19JUklTIiwiU1BFQ0lFU19BQ0NFU1NPUlkiLCJJUklTX0hJR0hMSUdIVCIsIkFDQ0VOVF9HT0xEIiwiQUNDRVNTT1JZX0NPTE9SIiwibm9uZSIsImJlbGwiLCJzY2FyZiIsImJvd3RpZSIsImdsYXNzZXMiLCJTS0lOX0JPRFkiLCJncmF5Iiwid2hpdGUiLCJicm93biIsInBpbmsiLCJibGFjayIsIm1pbnQiLCJnb2xkZW4iLCJzbm93IiwibWlkbmlnaHQiLCJpbmsiLCJhdXJvcmEiLCJoZXgiLCJmcm9tSEVYIiwiYW1vdW50IiwiayIsInJlc29sdmVQYWxldHRlIiwiY29sb3JLZXkiLCJhY2Nlc3NvcnlLZXkiLCJiYXNlSGV4IiwiYm9keSIsInNwZWNpZXNUaW50IiwiZGFyayIsImJlbGx5IiwicGF3IiwiaXJpcyIsImlyaXNIaWdobGlnaHQiLCJhY2Nlc3NvcnkiLCJFUFMiLCJsYXRoZSIsInByb2ZpbGUiLCJzZWdtZW50cyIsInJpbmdzIiwicG9zaXRpb25zIiwibm9ybWFscyIsInV2cyIsImluZGljZXMiLCJyaW1Ob3JtYWwiLCJpIiwicHJldiIsInRyIiwidHkiLCJsZW4iLCJoeXBvdCIsInlNaW4iLCJ5TWF4IiwieVNwYW4iLCJfcHJvZmlsZSRfaSIsIl9zbGljZWRUb0FycmF5IiwiX3JpbU5vcm1hbCRfaSIsIm5yIiwibnkiLCJ2IiwiaiIsInUiLCJ0aGV0YSIsInN0cmlkZSIsInJ4IiwicnkiLCJyeiIsInBoaSIsInNwIiwiY3AiLCJzdCIsImN0IiwibngiLCJueiIsIm5vcm0iLCJjcm9zcyIsInN3ZWVwIiwibm9kZXMiLCJyYWRpYWwiLCJjYXBTdGVwcyIsInRhbmdlbnRzIiwicCIsInJlZiIsIm5vcm1hbCIsImRvdCIsImlzRmluaXRlIiwiYmlub3JtYWwiLCJzeCIsInN5Iiwic3RhcnRDYXAiLCJkaXJTdGFydCIsImVuZENhcCIsImxhc3QiLCJkaXJFbmQiLCJ1bnNoaWZ0IiwidG90YWwiLCJucm0iLCJldWxlck1hdHJpeCIsIl9yb3QiLCJjeCIsImN5IiwiY3oiLCJzeiIsInRyYW5zZm9ybWVkIiwiZ2VvIiwibSIsInR4IiwidHoiLCJzbGljZSIsIm91dCIsImJhc2UiLCJfaTciLCJfZ2VvcyIsIl9vdXQkcG9zaXRpb25zIiwiX291dCRub3JtYWxzIiwiX291dCR1dnMiLCJzbW9vdGhOb3JtYWxzIiwicHJlY2lzaW9uIiwiYWNjIiwicHJvZmlsZUF0Iiwic3BhbiIsImZyb250WiIsInpTY2FsZSIsImlubmVyIiwic3FydCIsInRhblUiLCJ0YW4iLCJ0YW5WIiwiZHgiLCJkeSIsImludiIsIm5sIiwibWF4TGVuIiwicHJvZmlsZUZyb20iLCJjb250cm9sIiwic3RlcHMiLCJhdCIsInAwIiwicDEiLCJwMiIsInAzIiwidDIiLCJ0MyIsIkMiLCJ3YWxsIiwid2FsbFNoYWRlIiwiYmFzZWJvYXJkIiwiYmFzZWJvYXJkU2hhZGUiLCJmbG9vclNoYWRlIiwiZmxvb3JMaW5lIiwicnVnT3V0ZXIiLCJydWdJbm5lciIsInJ1Z0NlbnRlciIsIndvb2QiLCJ3b29kRGFyayIsImJyYXNzIiwiYnJhc3NEYXJrIiwiYmVkUmltIiwiYmVkQ3VzaGlvbiIsIndpbmRvd0ZyYW1lIiwiZ2xhc3MiLCJjdXJ0YWluIiwic3VuIiwibGVhZiIsImxlYWZEYXJrIiwidmFzZSIsImNoaW5hIiwiYm9vazEiLCJib29rMiIsImJvb2szIiwieWFybiIsImR1Y2siLCJkdWNrQmVhayIsImN1c2hpb24iLCJwaWN0dXJlIiwiaGVhcnQiLCJvcHRzIiwiX29wdHMkb3V0bGluZSIsImJ1aWxkRmxvb3IiLCJidWlsZFdhbGxzIiwiYnVpbGRXaW5kb3ciLCJidWlsZFdhbGxEZWNvciIsImJ1aWxkUnVnIiwiYnVpbGRCZWQiLCJidWlsZENhYmluZXQiLCJidWlsZFBsYW50IiwiYnVpbGRUb3lzIiwiYnVpbGRDb250YWN0U2hhZG93cyIsImJ1aWxkRm9yZWdyb3VuZCIsImJ1aWxkTGFtcFN0cmluZyIsImJ1aWxkU3VuQmVhbSIsIl9pIiwiX2FyciIsImZyYW1lIiwiY2xvdWQiLCJzaGVsZiIsIndvb2RTdHlsZSIsImJvb2tzIiwiX2kyIiwiX2Jvb2tzIiwiX2Jvb2tzJF9pIiwiZHJhd2VyTGluZSIsIl9pMyIsIl9hcnIyIiwicmFja1dvb2QiLCJzdGFuZGluZ1BsYXRlIiwiZmFjZSIsImJvd2wiLCJibG9vbXMiLCJfaTQiLCJfYmxvb21zIiwiX2Jsb29tcyRfaSIsImR6IiwibWlkIiwiYm9uZSIsImJsb2IiXSwibWFwcGluZ3MiOiI7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7O01BSUE7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RUFBRSxDQUFDLENBQUMsQ0FBQztRQUN0RG1QLEdBQUcsQ0FBQzFQLEtBQUssQ0FBQ21hLElBQUksRUFBRSxNQUFNLEVBQUUsQ0FBQyxJQUFJLEVBQUUsS0FBSyxFQUFFLElBQUksQ0FBQyxFQUFFO1VBQ3pDN2MsS0FBSyxFQUFFMm5CLENBQUMsQ0FBQ21CLEdBQUc7VUFBRXpvQixLQUFLLEVBQUVzbkIsQ0FBQyxDQUFDbUIsR0FBRztVQUFFMXBCLEtBQUssRUFBRSxFQUFFO1VBQUVxQyxJQUFJLEVBQUUsQ0FBQyxJQUFJLEVBQUUsR0FBRztRQUMxRCxDQUFBLEVBQUUsSUFBSXdCLElBQUksQ0FBQyxDQUFDLElBQUksRUFBRSxLQUFLLEVBQUUsQ0FBQyxJQUFJLENBQUMsRUFBRSxJQUFJQSxJQUFJLENBQUMsQ0FBQyxFQUFFLENBQUMsRUFBRSxFQUFFLENBQUMsQ0FBQyxDQUFDO1FBQ3RELE9BQU80WixJQUFJO01BQ2Y7TUFBQ3ZYLFFBQUEsQ0FBQUMsR0FBQSxDQUFBQyxHQUFBLENBQUEsQ0FBQSIsImZpbGUiOiJhbGwuanMiLCJzb3VyY2VzQ29udGVudCI6WyJpbXBvcnQgeyBDb2xvciwgQ29tcG9uZW50LCBFZmZlY3RBc3NldCwgTGF5ZXJzLCBNYXRlcmlhbCwgTWVzaCwgTm9kZSwgVmVjMywgVmVjNCB9IGZyb20gJ2NjJ1xyXG5pbXBvcnQgeyBHZW8sIFAzLCBlbGxpcHNvaWQsIG1lcmdlLCByYWRpYWxVViwgc3BoZXJlUGF0Y2ggfSBmcm9tICcuL1BldE1lc2hGYWN0b3J5J1xyXG5pbXBvcnQgeyBzaGlmdCB9IGZyb20gJy4vUGV0R2FtZVRoZW1lJ1xyXG5cclxuLyoqXHJcbiAqIDNEIOW7uuaooeWfuuehgOiuvuaWve+8iOadkOi0qOW3peWOgiArIOWHoOS9leW3peWFt++8ieOAglxyXG4gKlxyXG4gKiDop4bop4nph43mnoQgdjUg55qE5YWz6ZSu5Y+Y5YyW77yaXHJcbiAqICAtIOWHoOS9le+8mnN1cmYoKSDmj5DkuqTmiYvlhpnov57nu63mm7LpnaLvvIjml4vovazkvZMv5omr5o6gL+akreeQg++8ie+8jHN1cmZHcm91cCgpIOaKiuWQjOadkOi0qOeahOaIkOe7hOmbtuS7tlxyXG4gKiAgICDlkIjlubbkuLrkuIDkuKrnvZHmoLzvvIjlh4/lsJEgZHJhdyBjYWxs77yJ77yMZGVjYWwoKSDmj5DkvptcIuaflOWSjOi0tOiKsVwi77yI6IWu57qiL+mrmOWFiS/ova/lvbHvvInvvJtcclxuICogIC0g5p2Q6LSo77yacGV0LXRvb24g5o+Q5L6b5bGP5bmV5oGS5a6a5o+P6L6544CB5LiJ54K55byP5YWJ54Wn77yI5pqW5Li75YWJICsg5Ya36KGl5YWJICsg5YWL5Yi26IOM5YWJ77yJ44CBXHJcbiAqICAgIOiJsuWMuuaflOWMlu+8iOabsumdouS4iueahOaflOWSjOaYjuW6pui/h+a4oe+8jOabv+S7o+eUn+ehrOiJsuWdl++8ieS4juW8seiAjOWuveeahOS4u+S9k+mrmOWFieOAglxyXG4gKlxyXG4gKiBDb2NvcyA0LjAgYWxwaGEg6L+Q6KGM5pe26Zeo6Z2i5LiOIDMueCBEVFMg55qE5ZG95ZCN5beu5byC6YCC6YWN77yaXHJcbiAqICBNZXNoUmVuZGVyZXLihpJNb2RlbENvbXBvbmVudOOAgU1lc2hVdGlscyDlnKggY2MudXRpbHPjgIHmnZDotKjnu48gTWF0ZXJpYWwuaW5pdGlhbGl6ZSh7IGVmZmVjdEFzc2V0IH0p44CCXHJcbiAqL1xyXG5cclxuZXhwb3J0IGludGVyZmFjZSBQZXRDY1J1bnRpbWUge1xyXG4gICAgTm9kZTogeyBuZXcgKG5hbWU/OiBzdHJpbmcpOiBOb2RlIH1cclxuICAgIC8qKiDnvZHmoLzmuLLmn5Pnu4Tku7bmnoTpgKDlmajvvIhDb2NvcyA0LjAgYWxwaGEg6L+Q6KGM5pe26Zeo6Z2i77yaMy54IOeahCBNZXNoUmVuZGVyZXLvvIkgKi9cclxuICAgIE1vZGVsQ29tcG9uZW50OiBuZXcgKCkgPT4gQ29tcG9uZW50XHJcbiAgICBNYXRlcmlhbDogeyBuZXcgKCk6IE1hdGVyaWFsIH1cclxuICAgIHV0aWxzOiB7IGNyZWF0ZU1lc2g6IChnZW9tZXRyeTogb2JqZWN0KSA9PiBNZXNoIH1cclxuICAgIHByaW1pdGl2ZXM6IHtcclxuICAgICAgICBzcGhlcmU6IChyYWRpdXM/OiBudW1iZXIpID0+IG9iamVjdFxyXG4gICAgICAgIGNhcHN1bGU6IChyYWRpdXNUb3A/OiBudW1iZXIsIHJhZGl1c0JvdHRvbT86IG51bWJlciwgaGVpZ2h0PzogbnVtYmVyKSA9PiBvYmplY3RcclxuICAgICAgICBjb25lOiAocmFkaXVzPzogbnVtYmVyLCBoZWlnaHQ/OiBudW1iZXIpID0+IG9iamVjdFxyXG4gICAgICAgIGN5bGluZGVyOiAocmFkaXVzVG9wPzogbnVtYmVyLCByYWRpdXNCb3R0b20/OiBudW1iZXIsIGhlaWdodD86IG51bWJlcikgPT4gb2JqZWN0XHJcbiAgICAgICAgYm94OiAob3B0aW9ucz86IHsgd2lkdGg/OiBudW1iZXI7IGhlaWdodD86IG51bWJlcjsgbGVuZ3RoPzogbnVtYmVyIH0pID0+IG9iamVjdFxyXG4gICAgICAgIHRvcnVzOiAocmFkaXVzPzogbnVtYmVyLCB0dWJlPzogbnVtYmVyKSA9PiBvYmplY3RcclxuICAgIH1cclxufVxyXG5cclxuZXhwb3J0IGludGVyZmFjZSBQZXRNb2RlbExpa2Uge1xyXG4gICAgbWVzaDogTWVzaCB8IG51bGxcclxuICAgIG1hdGVyaWFsOiBNYXRlcmlhbCB8IG51bGxcclxuICAgIF9nZXRCdWlsdGluTWF0ZXJpYWwoKTogTWF0ZXJpYWxcclxufVxyXG5cclxuLyoqIOmDqOS7tumjjuagvO+8muminOiJsiArIOaYjuaaly/otKjmhJ8v6YCP5piO562J6KGo546w5Y+C5pWwICovXHJcbmV4cG9ydCBpbnRlcmZhY2UgUGFydFN0eWxlIHtcclxuICAgIC8qKiDkuLvoibLvvIjkuq7pg6jvvIkgKi9cclxuICAgIGNvbG9yOiBDb2xvclxyXG4gICAgLyoqIOaal+mDqOiJsu+8m+e8uuecgeaMieS4u+iJsuWOi+aalyAzNCUgKi9cclxuICAgIHNoYWRlPzogQ29sb3JcclxuICAgIC8qKiDmj4/ovrnlrr3luqbvvIjlsY/luZXnqbrpl7QgTkRD77ybMCA9IOS4jeaPj+i+ue+8mzAuMDAzIOe6puS4uuWxj+W5lemrmOW6pueahCAwLjE1Je+8iSAqL1xyXG4gICAgb3V0bGluZT86IG51bWJlclxyXG4gICAgLyoqIOi9ruW7k+WFieW8uuW6puimhueblu+8iOm7mOiupCAwLjI277yJICovXHJcbiAgICByaW0/OiBudW1iZXJcclxuICAgIC8qKiDpobbpg6jlpKnlhYnmj5Dkuq7opobnm5bvvIjpu5jorqQgMC4xNO+8iSAqL1xyXG4gICAgdG9wPzogbnVtYmVyXHJcbiAgICAvKiog6YCP5piO5bqm77yIPCAyNTUg5pe26LWwIHRyYW5zcGFyZW50IOaKgOacr++8jGRlcHRoV3JpdGUg5YWz6Zet77yJICovXHJcbiAgICBhbHBoYT86IG51bWJlclxyXG4gICAgLyoqIOiHquWPkeWFie+8iOaal+mDqCA9IOS4u+iJsu+8jOeUqOS6jueBr+WFiS/lhYnngrkv546755KD6YCP5YWJ77yJICovXHJcbiAgICBnbG93PzogYm9vbGVhblxyXG4gICAgLyoqIOe7kuWFieW8uuW6puimhueblu+8iOavm+e7kueJqeenjSAwLjEwLTAuMTjvvJvlhYnmu5Hniannp40gMC4wMi0wLjA277ybMCA9IOaXoOe7kui+ue+8iSAqL1xyXG4gICAgZnVyPzogbnVtYmVyXHJcbiAgICAvKiog57uS5YWJ6ZSQ5bqm6KaG55uW77yI6buY6K6kIDIuMO+8m+i2iuWwj+i+uee8mOi2iuWuvei2iuaflO+8iSAqL1xyXG4gICAgZnVyU2hhcnA/OiBudW1iZXJcclxuICAgIC8qKiDpq5jlhYnlvLrluqbopobnm5bvvIjlhYnmu5Hniannp40gMC4xNi0wLjMwIOWRiOeOsOa5v+a2puaEn++8m+avm+e7kueJqeenjSAwLjAzLTAuMDjvvIkgKi9cclxuICAgIHNwZWM/OiBudW1iZXJcclxuICAgIC8qKiDpq5jlhYnplJDluqbopobnm5bvvIjpu5jorqQgOO+8m+i2iuWwj+mrmOWFiei2iuWuvei2iuaflO+8jOmBv+WFjeWhkeaWmeaEn++8iSAqL1xyXG4gICAgc3BlY1NoYXJwPzogbnVtYmVyXHJcbiAgICAvKiog6Imy5Yy65p+U5YyW55uu5qCH6Imy77yI5Zyo5puy6Z2i5LiK5oqK5Li76Imy5riQ5Y+Y5Li65a6D77ya6IKa55quL+WPo+m8u+eahOaflOWSjOaYjuW6puWMuuWIhu+8iSAqL1xyXG4gICAgbWl4Q29sb3I/OiBDb2xvclxyXG4gICAgLyoqIOiJsuWMuuaflOWMluaOp+WItu+8mnggPSB5IOS4iueVjO+8iOS4jea3t+WQiO+8iSB5ID0geSDkuIvnlYzvvIjlrozlhajmt7flkIjvvIkgeiA9IOWJjeWQkeadg+mHjSB3ID0g5by65bqmICovXHJcbiAgICBtaXhDdHJsPzogVmVjNFxyXG4gICAgLyoqIOaflOWSjOi0tOiKse+8muW+hOWQkSBhbHBoYSDoobDlh48gW+i1t+Wni+WNiuW+hCwg5YWo6YCP5piO5Y2K5b6EXe+8iOWHoOS9lemhu+S4uuWNleS9jeakreeQg++8iSAqL1xyXG4gICAgc29mdD86IFtudW1iZXIsIG51bWJlcl1cclxuICAgIC8qKiDlubPmtoLvvJrkuI3lj5flhYnnhacv6YGu6JS95b2x5ZON77yI6LS06Iqx44CB5Y+R5YWJ5Lu277yJICovXHJcbiAgICBmbGF0PzogYm9vbGVhblxyXG4gICAgLyoqIOW8uuWItuWNiumAj+aYjuaKgOacr++8iOi9r+i0tOiKseWNs+S9vyBhbHBoYT0yNTUg5Lmf6ZyA6KaB5re35ZCI5p2l6I635b6X5p+U5ZKM6L6557yY77yJICovXHJcbiAgICBibGVuZD86IGJvb2xlYW5cclxuICAgIC8qKlxyXG4gICAgICog5L2/55So5byV5pOO5YaF572u5p2Q6LSo77yI5LiN5aWX55SoIHBldC10b29u77yJ44CCXHJcbiAgICAgKlxyXG4gICAgICog55So5LqOKirkvp3otZbmoIflh4YgYWxwaGEg5re35ZCIKirnmoTpg6jku7bvvIjova/lvbEv5YWJ5paRL+eOu+eSg++8ie+8muiHquWumuS5iSBlZmZlY3Qg55qEIHRyYW5zcGFyZW50XHJcbiAgICAgKiDmioDmnK/lnKjmnKzmnoTlu7rpk77vvIhjb2Nvcy1jbGkgNC4wIGFscGhh77yJ5LiL5a6e5rWL5Lya5Lii5aSx5p2Q6LSo5bGe5oCn77yI6aKc6Imy5Zue6JC95Yiw6buY6K6k55m977yJ77yMXHJcbiAgICAgKiDogIzlvJXmk47lhoXnva4gdW5saXQg55qE5re35ZCI6Lev5b6E56iz5a6a5Y+v55So44CCXHJcbiAgICAgKi9cclxuICAgIHBsYWluPzogYm9vbGVhblxyXG4gICAgLyoqIOmdoumDqOiFrue6oueDmOeEmeWPguaVsO+8iOWktOmDqOadkOi0qOS4k+eUqO+8muWcqOabsumdouS4iuWBmueQg+W9ouW+hOWQkea3t+iJsu+8iSAqL1xyXG4gICAgYmx1c2g/OiB7IGNvbG9yOiBDb2xvcjsgc3RyZW5ndGg6IG51bWJlcjsgYTogVmVjNDsgYjogVmVjNCB9XHJcbn1cclxuXHJcbmludGVyZmFjZSBQYXJ0VHJhbnNmb3JtIHtcclxuICAgIHJvdD86IFZlYzNcclxuICAgIHNjYWxlPzogVmVjM1xyXG59XHJcblxyXG4vKiog6buY6K6k5o+P6L655a695bqm77yI5bGP5bmV56m66Ze0IE5EQ++8ie+8muWPquWBmui9ruW7k1wi5pS26L65XCLvvIzkuI3lh7rnjrDnoazpu5HovrkgKi9cclxuY29uc3QgREVGQVVMVF9PVVRMSU5FID0gMC4wMDQ1XHJcblxyXG4vKiogM0Qg5Zy65pmv5p6E5bu65bel5YW377ya6IqC54K5L+e9keagvC/mnZDotKjlt6XljoLvvIjlrqDniankuI7miL/pl7TlhbHnlKjlkIzkuIDlpZfop4bop4nor63oqIDvvIkgKi9cclxuZXhwb3J0IGNsYXNzIFBldEJ1aWxkZXJLaXQge1xyXG5cclxuICAgIHJlYWRvbmx5IGNjOiBQZXRDY1J1bnRpbWVcclxuICAgIHByaXZhdGUgdG9vbkVmZmVjdDogRWZmZWN0QXNzZXQgfCBudWxsID0gbnVsbFxyXG4gICAgcHJpdmF0ZSBmYWxsYmFja0VmZmVjdDogRWZmZWN0QXNzZXQgfCBudWxsID0gbnVsbFxyXG5cclxuICAgIGNvbnN0cnVjdG9yKGNjOiBQZXRDY1J1bnRpbWUpIHtcclxuICAgICAgICB0aGlzLmNjID0gY2NcclxuICAgIH1cclxuXHJcbiAgICAvKiogcGV0LXRvb24g5piv5ZCm55Sf5pWI77yI5L6b6LCD6K+V5LiO6ZmN57qn5YiG5pSv5Yik5pat77yJICovXHJcbiAgICBnZXQgaXNUb29uKCk6IGJvb2xlYW4ge1xyXG4gICAgICAgIHJldHVybiAhIXRoaXMudG9vbkVmZmVjdFxyXG4gICAgfVxyXG5cclxuICAgIC8qKlxyXG4gICAgICog5rOo5YWl5bey5Yqg6L2955qEIHBldC10b29uIGVmZmVjdOOAglxyXG4gICAgICpcclxuICAgICAqIOiHquWumuS5iSBlZmZlY3Qg5b+F6aG755Sx5a6/5Li75YWI5LuOIHJlc291cmNlcyDliqDovb3vvIhgRWZmZWN0QXNzZXQuZ2V0YCDlj6rog73mn6XliLAqKuW3suWKoOi9vSoq55qE6LWE5Lqn77yJ77yMXHJcbiAgICAgKiDlm6DmraTlnLrmma/mnoTlu7ropoHnrYnlroPlsLHnu6rvvJvmnKrms6jlhaXml7bmnZDotKjoh6rliqjlm57pgIDlhoXnva7mnZDotKjvvIhGYWlsLU9wZW7vvInjgIJcclxuICAgICAqL1xyXG4gICAgc2V0VG9vbkVmZmVjdChlZmZlY3Q6IEVmZmVjdEFzc2V0IHwgbnVsbCk6IHZvaWQge1xyXG4gICAgICAgIHRoaXMudG9vbkVmZmVjdCA9IGVmZmVjdFxyXG4gICAgfVxyXG5cclxuICAgIC8qKlxyXG4gICAgICog6Kej5p6Q5bel56iL5YaF55qEIHBldC10b29uIGVmZmVjdOOAglxyXG4gICAgICpcclxuICAgICAqIOazqOaEj++8mmNvY29zLWNsaSDmnoTlu7rkuqfnianph4znmoQgZWZmZWN0IOi1hOS6p+WQjeWPr+iDveW4pui3r+W+hOWJjee8gO+8iOeUseWvvOWFpeWZqOaMiSBEQiDnm7jlr7not6/lvoTlkb3lkI3vvInvvIxcclxuICAgICAqIOWboOatpOS4jeiDveWPquS+nei1liBgRWZmZWN0QXNzZXQuZ2V0KCdwZXQtdG9vbicpYO+8m+i/memHjOi/veWKoOS4gOasoVwi5oyJ5ZCN5ZCO57yA5Yy56YWNXCLnmoTlm57pgIDvvIxcclxuICAgICAqIOWQpuWImeadkOi0qOS8mumdmem7mOmAgOWMluaIkOWGhee9ruadkOi0qO+8iOinhuinieihqOeOsOS4juW3peeoi+WGhSBlZmZlY3Qg5a6M5YWo5LiN5ZCM77yJ44CCXHJcbiAgICAgKi9cclxuICAgIHByaXZhdGUgcmVzb2x2ZVRvb25FZmZlY3QoKTogRWZmZWN0QXNzZXQgfCBudWxsIHtcclxuICAgICAgICBpZiAodGhpcy50b29uRWZmZWN0KSB7XHJcbiAgICAgICAgICAgIHJldHVybiB0aGlzLnRvb25FZmZlY3RcclxuICAgICAgICB9XHJcbiAgICAgICAgbGV0IGZvdW5kID0gRWZmZWN0QXNzZXQuZ2V0KCdwZXQtdG9vbicpIHx8IG51bGxcclxuICAgICAgICBpZiAoIWZvdW5kKSB7XHJcbiAgICAgICAgICAgIGNvbnN0IHJlZ2lzdHJ5ID0gKEVmZmVjdEFzc2V0IGFzIHVua25vd24gYXMgeyBnZXRBbGw/OiAoKSA9PiB1bmtub3duIH0pLmdldEFsbD8uKClcclxuICAgICAgICAgICAgY29uc3QgbGlzdDogRWZmZWN0QXNzZXRbXSA9IHJlZ2lzdHJ5IGluc3RhbmNlb2YgTWFwXHJcbiAgICAgICAgICAgICAgICA/IEFycmF5LmZyb20ocmVnaXN0cnkudmFsdWVzKCkgYXMgSXRlcmFibGU8RWZmZWN0QXNzZXQ+KVxyXG4gICAgICAgICAgICAgICAgOiAoQXJyYXkuaXNBcnJheShyZWdpc3RyeSkgPyByZWdpc3RyeSBhcyBFZmZlY3RBc3NldFtdIDogW10pXHJcbiAgICAgICAgICAgIGZvdW5kID0gbGlzdC5maW5kKGVudHJ5ID0+IGVudHJ5ICYmIHR5cGVvZiBlbnRyeS5uYW1lID09PSAnc3RyaW5nJyAmJiBlbnRyeS5uYW1lLmVuZHNXaXRoKCdwZXQtdG9vbicpKSB8fCBudWxsXHJcbiAgICAgICAgfVxyXG4gICAgICAgIHRoaXMudG9vbkVmZmVjdCA9IGZvdW5kXHJcbiAgICAgICAgcmV0dXJuIGZvdW5kXHJcbiAgICB9XHJcblxyXG4gICAgLyoqIOivpSBlZmZlY3Qg5piv5ZCm5Li65bel56iL5YaF55qEIHBldC10b29u77yI5oqA5pyv57Si5byV5LiO5bGe5oCn5ZCN6YO95oyJ5a6D5p2l77yJICovXHJcbiAgICBwcml2YXRlIGlzVG9vbkVmZmVjdChlZmZlY3Q6IEVmZmVjdEFzc2V0IHwgbnVsbCk6IGJvb2xlYW4ge1xyXG4gICAgICAgIHJldHVybiAhIWVmZmVjdCAmJiB0eXBlb2YgZWZmZWN0Lm5hbWUgPT09ICdzdHJpbmcnICYmIGVmZmVjdC5uYW1lLmVuZHNXaXRoKCdwZXQtdG9vbicpXHJcbiAgICB9XHJcblxyXG4gICAgLyoqXHJcbiAgICAgKiDlvJXmk47lhoXnva7mnZDotKjvvIhGYWlsLU9wZW4g5YWc5bqV77yJ77ya5pyq5omT5YyFL+WKoOi9veWksei0peaXtuadkOi0qOS7jeWPr+eUqO+8jFxyXG4gICAgICog5Y+q5piv5aSx5Y67IHBldC10b29uIOeahOaPj+i+uS/nu5LlhYkv5YWJ54Wn6KGo546w44CCXHJcbiAgICAgKi9cclxuICAgIHByaXZhdGUgcGlja0ZhbGxiYWNrRWZmZWN0KHNhbXBsZTogUGV0TW9kZWxMaWtlKTogRWZmZWN0QXNzZXQgfCBudWxsIHtcclxuICAgICAgICBpZiAoIXRoaXMuZmFsbGJhY2tFZmZlY3QpIHtcclxuICAgICAgICAgICAgdHJ5IHtcclxuICAgICAgICAgICAgICAgIGNvbnN0IGJ1aWx0aW4gPSBzYW1wbGUuX2dldEJ1aWx0aW5NYXRlcmlhbCgpXHJcbiAgICAgICAgICAgICAgICB0aGlzLmZhbGxiYWNrRWZmZWN0ID0gYnVpbHRpbiAmJiBidWlsdGluLmVmZmVjdEFzc2V0ID8gYnVpbHRpbi5lZmZlY3RBc3NldCA6IG51bGxcclxuICAgICAgICAgICAgfSBjYXRjaCAoZSkge1xyXG4gICAgICAgICAgICAgICAgLy8g5YWc5bqV5p2Q6LSo5bCa5pyq5bCx57uq77ya5L+d5oyBIG51bGzvvIzkuIvkuIDmrKEgYXR0YWNoIOWGjeivlVxyXG4gICAgICAgICAgICB9XHJcbiAgICAgICAgfVxyXG4gICAgICAgIHJldHVybiB0aGlzLmZhbGxiYWNrRWZmZWN0XHJcbiAgICB9XHJcblxyXG4gICAgLyoqIDNEIOiKgueCue+8iERFRkFVTFQg5bGC77yM5oyC5Yiw54i26IqC54K577yJICovXHJcbiAgICBtYWtlM2ROb2RlKHBhcmVudDogTm9kZSwgbmFtZTogc3RyaW5nLCBwb3M6IFZlYzMsIHRyYW5zZm9ybT86IFBhcnRUcmFuc2Zvcm0pOiBOb2RlIHtcclxuICAgICAgICBjb25zdCBub2RlID0gbmV3IHRoaXMuY2MuTm9kZShuYW1lKVxyXG4gICAgICAgIG5vZGUubGF5ZXIgPSBMYXllcnMuRW51bS5ERUZBVUxUXHJcbiAgICAgICAgbm9kZS5zZXRQb3NpdGlvbihwb3MpXHJcbiAgICAgICAgaWYgKHRyYW5zZm9ybT8ucm90KSB7XHJcbiAgICAgICAgICAgIG5vZGUuc2V0Um90YXRpb25Gcm9tRXVsZXIodHJhbnNmb3JtLnJvdC54LCB0cmFuc2Zvcm0ucm90LnksIHRyYW5zZm9ybS5yb3QueilcclxuICAgICAgICB9XHJcbiAgICAgICAgaWYgKHRyYW5zZm9ybT8uc2NhbGUpIHtcclxuICAgICAgICAgICAgbm9kZS5zZXRTY2FsZSh0cmFuc2Zvcm0uc2NhbGUueCwgdHJhbnNmb3JtLnNjYWxlLnksIHRyYW5zZm9ybS5zY2FsZS56KVxyXG4gICAgICAgIH1cclxuICAgICAgICBwYXJlbnQuYWRkQ2hpbGQobm9kZSlcclxuICAgICAgICByZXR1cm4gbm9kZVxyXG4gICAgfVxyXG5cclxuICAgIC8qKiDnu4Too4XnvZHmoLwgKyDljaHpgJrmnZDotKjvvIjmnZDotKjnrZbnlaXop4Hnsbvms6jph4rvvIkgKi9cclxuICAgIHByaXZhdGUgYXR0YWNoKG5vZGU6IE5vZGUsIGdlb21ldHJ5OiBvYmplY3QsIHN0eWxlOiBQYXJ0U3R5bGUpOiBQZXRNb2RlbExpa2Uge1xyXG4gICAgICAgIGNvbnN0IG1vZGVsID0gbm9kZS5hZGRDb21wb25lbnQodGhpcy5jYy5Nb2RlbENvbXBvbmVudCkgYXMgdW5rbm93biBhcyBQZXRNb2RlbExpa2VcclxuICAgICAgICBtb2RlbC5tZXNoID0gdGhpcy5jYy51dGlscy5jcmVhdGVNZXNoKGdlb21ldHJ5KVxyXG5cclxuICAgICAgICBjb25zdCB0cmFuc3BhcmVudCA9IHN0eWxlLmJsZW5kID09PSB0cnVlIHx8IChzdHlsZS5hbHBoYSAhPT0gdW5kZWZpbmVkICYmIHN0eWxlLmFscGhhIDwgMjU1KVxyXG4gICAgICAgIC8vIHBsYWlu77ya5by65Yi25byV5pOO5YaF572u5p2Q6LSo77yI5qCH5YeG6YCP5piO6Lev5b6E77yJ77yb5YW25L2Z6YOo5Lu25LyY5YWIIHBldC10b29u44CCXHJcbiAgICAgICAgLy8g5rOo5oSP77ya5b+F6aG75LulKirmnIDnu4jpgInkuK3nmoQgZWZmZWN0Kiog5Yik5pat5oqA5pyv57Si5byVIOKAlOKAlCDoi6XnlKhcIuino+aekOe7k+aenOaYr+WQpuS4uuepulwi5Yik5pat77yMXHJcbiAgICAgICAgLy8g5LiA5pem5Ye6546wXCLop6PmnpDlpLHotKXkvYblhZzlupXmi7/liLAgcGV0LXRvb25cIueahOe7hOWQiO+8jHRlY2huaXF1ZSDntKLlvJXlsLHkvJrotornlYzvvIxcclxuICAgICAgICAvLyDmnZDotKjliJ3lp4vljJbpnZnpu5jlpLHotKXlubbpgIDlm57lhoXnva7mnZDotKjvvIjmm77lr7zoh7TmiYDmnIkgdG9vbiDooajnjrDlpLHmlYjvvInjgIJcclxuICAgICAgICBjb25zdCBlZmZlY3QgPSBzdHlsZS5wbGFpblxyXG4gICAgICAgICAgICA/IHRoaXMucGlja0ZhbGxiYWNrRWZmZWN0KG1vZGVsKVxyXG4gICAgICAgICAgICA6ICh0aGlzLnJlc29sdmVUb29uRWZmZWN0KCkgfHwgdGhpcy5waWNrRmFsbGJhY2tFZmZlY3QobW9kZWwpKVxyXG4gICAgICAgIGlmICghZWZmZWN0KSB7XHJcbiAgICAgICAgICAgIHJldHVybiBtb2RlbFxyXG4gICAgICAgIH1cclxuICAgICAgICBjb25zdCB1c2VUb29uID0gIXN0eWxlLnBsYWluICYmIHRoaXMuaXNUb29uRWZmZWN0KGVmZmVjdClcclxuXHJcbiAgICAgICAgY29uc3QgbWF0ZXJpYWwgPSBuZXcgTWF0ZXJpYWwoKVxyXG4gICAgICAgIGNvbnN0IHRlY2huaXF1ZSA9IHVzZVRvb24gPyAodHJhbnNwYXJlbnQgPyAxIDogMCkgOiAodHJhbnNwYXJlbnQgPyAyIDogMClcclxuICAgICAgICB0cnkge1xyXG4gICAgICAgICAgICBtYXRlcmlhbC5pbml0aWFsaXplKHsgZWZmZWN0QXNzZXQ6IGVmZmVjdCwgdGVjaG5pcXVlIH0pXHJcbiAgICAgICAgfSBjYXRjaCAoZXJyb3IpIHtcclxuICAgICAgICAgICAgLy8g5p2Q6LSo5Yid5aeL5YyW5aSx6LSl77ya5L+d5oyB5pyq6K6+572u54q25oCB77yM55Sx5byV5pOO57uZ6buY6K6k5p2Q6LSo77yIRmFpbC1PcGVu77yM5LiN6Zi75aGe5Zy65pmv77yJXHJcbiAgICAgICAgICAgIGNvbnNvbGUud2FybihgcGV0LWdhbWU6IOadkOi0qOWIneWni+WMluWksei0pe+8iCR7ZWZmZWN0Lm5hbWV9ICMke3RlY2huaXF1ZX3vvIlgLCBlcnJvcilcclxuICAgICAgICAgICAgcmV0dXJuIG1vZGVsXHJcbiAgICAgICAgfVxyXG5cclxuICAgICAgICAvLyDpgI/mmI7luqblv4XpobvlubblhaXkuLvoibLvvJp0ZWNobmlxdWUg6YCJ5oup5Y+q5Yaz5a6a5re35ZCI5qih5byP77yMYWxwaGEg5LuN54S25p2l6IeqIG1haW5Db2xvci5hXHJcbiAgICAgICAgY29uc3QgY29sb3IgPSBzdHlsZS5hbHBoYSA9PT0gdW5kZWZpbmVkIHx8IHN0eWxlLmFscGhhID49IDI1NVxyXG4gICAgICAgICAgICA/IHN0eWxlLmNvbG9yXHJcbiAgICAgICAgICAgIDogbmV3IENvbG9yKHN0eWxlLmNvbG9yLnIsIHN0eWxlLmNvbG9yLmcsIHN0eWxlLmNvbG9yLmIsIHN0eWxlLmFscGhhKVxyXG4gICAgICAgIGNvbnN0IHNoYWRlID0gc3R5bGUuZ2xvdyA/IG5ldyBDb2xvcihjb2xvci5yLCBjb2xvci5nLCBjb2xvci5iLCBjb2xvci5hKSA6IChzdHlsZS5zaGFkZSB8fCBzaGlmdChjb2xvciwgLTAuMzQpKVxyXG4gICAgICAgIHRyeSB7XHJcbiAgICAgICAgICAgIG1hdGVyaWFsLnNldFByb3BlcnR5KCdtYWluQ29sb3InLCBjb2xvcilcclxuICAgICAgICAgICAgaWYgKHVzZVRvb24pIHtcclxuICAgICAgICAgICAgICAgIG1hdGVyaWFsLnNldFByb3BlcnR5KCdzaGFkZUNvbG9yJywgc2hhZGUpXHJcbiAgICAgICAgICAgICAgICAvLyDmmI7mmpfov4fmuKHluKbvvJowLjUywrEwLjIwIOKAlOKAlCDkuIrkuIDniYggMC41NMKxMC4yNiDlpKrlrr3vvIzmlbTlvKDohLjmjqXov5HlubPmtoLvvIxcclxuICAgICAgICAgICAgICAgIC8vIOaUtueqhOWQjuiEuOmiiuaJjeaciVwi5Lqu6Z2iIOKGkiDngbDpnaIg4oaSIOaalueBsOe0q+aal+mdolwi55qE5LiJ5q615L2T56ev77yI5LuN5peg56Gs6Imy6Zi277yJ44CCXHJcbiAgICAgICAgICAgICAgICBtYXRlcmlhbC5zZXRQcm9wZXJ0eSgnc2hhZGVDdHJsJywgbmV3IFZlYzQoMC41MiwgMC4yMCwgc3R5bGUucmltID8/IDAuMTMsIHN0eWxlLnRvcCA/PyAwLjE0KSlcclxuICAgICAgICAgICAgICAgIC8vIOe7kuWFiS/pq5jlhYnvvJrmr5vnu5Lniannp43pq5jnu5LlhYnjgIHlhYnmu5Hniannp43pq5jlhYnvvIjop4EgU3BlY2llc1NwZWPvvIlcclxuICAgICAgICAgICAgICAgIG1hdGVyaWFsLnNldFByb3BlcnR5KCdmdXJDdHJsJywgbmV3IFZlYzQoXHJcbiAgICAgICAgICAgICAgICAgICAgc3R5bGUuZnVyID8/IDAuMTQsIHN0eWxlLmZ1clNoYXJwID8/IDIuMCwgc3R5bGUuc3BlYyA/PyAwLjA1LCBzdHlsZS5zcGVjU2hhcnAgPz8gOCkpXHJcbiAgICAgICAgICAgICAgICAvLyDoibLljLrmn5TljJbvvIjogprnmq4v5Y+j6by755qE5p+U5ZKM5piO5bqm6L+H5rih77yM5pu/5Luj55Sf56Gs6Imy5Z2X77yJXHJcbiAgICAgICAgICAgICAgICBpZiAoc3R5bGUubWl4Q3RybCkge1xyXG4gICAgICAgICAgICAgICAgICAgIG1hdGVyaWFsLnNldFByb3BlcnR5KCdtaXhDdHJsJywgc3R5bGUubWl4Q3RybClcclxuICAgICAgICAgICAgICAgIH1cclxuICAgICAgICAgICAgICAgIGlmIChzdHlsZS5taXhDb2xvcikge1xyXG4gICAgICAgICAgICAgICAgICAgIG1hdGVyaWFsLnNldFByb3BlcnR5KCdtaXhDb2xvcicsIHN0eWxlLm1peENvbG9yKVxyXG4gICAgICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICAgICAgLy8g5p+U5ZKM6LS06Iqx77yI6auY5YWJL+i9r+aWke+8ie+8muW+hOWQkSBhbHBoYSDoobDlh48gKyDlubPmtoJcclxuICAgICAgICAgICAgICAgIGlmIChzdHlsZS5zb2Z0IHx8IHN0eWxlLmZsYXQpIHtcclxuICAgICAgICAgICAgICAgICAgICBtYXRlcmlhbC5zZXRQcm9wZXJ0eSgnZGVjYWxDdHJsJywgbmV3IFZlYzQoXHJcbiAgICAgICAgICAgICAgICAgICAgICAgIHN0eWxlLnNvZnQgPyBzdHlsZS5zb2Z0WzBdIDogMCwgc3R5bGUuc29mdCA/IHN0eWxlLnNvZnRbMV0gOiAwLCBzdHlsZS5mbGF0ID8gMSA6IDAsIDApKVxyXG4gICAgICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICAgICAgLy8g6Z2i6YOo6IWu57qi77ya54OY54SZ6L+b5p2Q6LSo77yI5puy6Z2i5LiK55qE55CD5b2i5b6E5ZCR5re36Imy77yJXHJcbiAgICAgICAgICAgICAgICAvLyDms6jmhI/vvJpDb2xvciDnmoQgYWxwaGEg5pivIDAtMjU1IOaVtOaVsOmAmumBk++8jOW8uuW6puW/hemhu+aNoueul+WQjuWGjeS8oO+8iOWQpuWImeS8muiiq+aIquaWreS4uiAw77yJXHJcbiAgICAgICAgICAgICAgICBpZiAoc3R5bGUuYmx1c2gpIHtcclxuICAgICAgICAgICAgICAgICAgICBtYXRlcmlhbC5zZXRQcm9wZXJ0eSgnYmx1c2hDb2xvcicsIG5ldyBDb2xvcihcclxuICAgICAgICAgICAgICAgICAgICAgICAgc3R5bGUuYmx1c2guY29sb3Iuciwgc3R5bGUuYmx1c2guY29sb3IuZywgc3R5bGUuYmx1c2guY29sb3IuYixcclxuICAgICAgICAgICAgICAgICAgICAgICAgTWF0aC5yb3VuZChzdHlsZS5ibHVzaC5zdHJlbmd0aCAqIDI1NSkpKVxyXG4gICAgICAgICAgICAgICAgICAgIG1hdGVyaWFsLnNldFByb3BlcnR5KCdibHVzaEN0cmxBJywgc3R5bGUuYmx1c2guYSlcclxuICAgICAgICAgICAgICAgICAgICBtYXRlcmlhbC5zZXRQcm9wZXJ0eSgnYmx1c2hDdHJsQicsIHN0eWxlLmJsdXNoLmIpXHJcbiAgICAgICAgICAgICAgICB9XHJcbiAgICAgICAgICAgICAgICBjb25zdCBvdXRsaW5lID0gc3R5bGUub3V0bGluZSA9PT0gdW5kZWZpbmVkID8gREVGQVVMVF9PVVRMSU5FIDogc3R5bGUub3V0bGluZVxyXG4gICAgICAgICAgICAgICAgbWF0ZXJpYWwuc2V0UHJvcGVydHkoJ291dGxpbmVDdHJsJywgbmV3IFZlYzQoTWF0aC5tYXgoMCwgb3V0bGluZSksIDAsIDAsIDApKVxyXG4gICAgICAgICAgICAgICAgLy8g5o+P6L656Imy6ZqP5Li76Imy5Y6L5pqX77yI5ZCM6Imy57O75p+U6L6577yM56aB5q2i5Y+R6buR77yJXHJcbiAgICAgICAgICAgICAgICBjb25zdCBlZGdlID0gc2hpZnQoY29sb3IsIC0wLjQ1KVxyXG4gICAgICAgICAgICAgICAgbWF0ZXJpYWwuc2V0UHJvcGVydHkoJ291dGxpbmVDb2xvcicsIG5ldyBDb2xvcihlZGdlLnIsIGVkZ2UuZywgZWRnZS5iLCAyNTUpKVxyXG4gICAgICAgICAgICB9XHJcbiAgICAgICAgfSBjYXRjaCAoZSkge1xyXG4gICAgICAgICAgICAvLyDlsZ7mgKfnvLrlpLHvvIjlm57pgIAgdW5saXTvvInvvJrku4XkuLvoibLnlJ/mlYjvvIzop4bop4npmY3nuqfkvYbkuI3lvbHlk43lip/og71cclxuICAgICAgICB9XHJcbiAgICAgICAgbW9kZWwubWF0ZXJpYWwgPSBtYXRlcmlhbFxyXG4gICAgICAgIHJldHVybiBtb2RlbFxyXG4gICAgfVxyXG5cclxuICAgIC8qKiDpgJrnlKjpg6jku7bvvJrlh6DkvZUgKyDpo47moLwgKyDlj5jmjaIgKi9cclxuICAgIHBhcnQocGFyZW50OiBOb2RlLCBuYW1lOiBzdHJpbmcsIGdlb21ldHJ5OiBvYmplY3QsIHN0eWxlOiBQYXJ0U3R5bGUsIHBvczogVmVjMywgdHJhbnNmb3JtPzogUGFydFRyYW5zZm9ybSk6IE5vZGUge1xyXG4gICAgICAgIGNvbnN0IG5vZGUgPSB0aGlzLm1ha2UzZE5vZGUocGFyZW50LCBuYW1lLCBwb3MsIHRyYW5zZm9ybSlcclxuICAgICAgICB0aGlzLmF0dGFjaChub2RlLCBnZW9tZXRyeSwgc3R5bGUpXHJcbiAgICAgICAgcmV0dXJuIG5vZGVcclxuICAgIH1cclxuXHJcbiAgICAvKiog5omL5YaZ572R5qC86YOo5Lu277yI5peL6L2s5L2TL+aJq+aOoC/mpK3nkIPvvIzop4EgUGV0TWVzaEZhY3RvcnnvvIkgKi9cclxuICAgIHN1cmYocGFyZW50OiBOb2RlLCBuYW1lOiBzdHJpbmcsIGdlb21ldHJ5OiBHZW8sIHN0eWxlOiBQYXJ0U3R5bGUsIHBvczogVmVjMywgdHJhbnNmb3JtPzogUGFydFRyYW5zZm9ybSk6IE5vZGUge1xyXG4gICAgICAgIHJldHVybiB0aGlzLnBhcnQocGFyZW50LCBuYW1lLCBnZW9tZXRyeSwgc3R5bGUsIHBvcywgdHJhbnNmb3JtKVxyXG4gICAgfVxyXG5cclxuICAgIC8qKiDmiJDnu4Tpm7bku7blkIjlubbkuLrkuIDkuKrpg6jku7bvvJrlkIzmnZDotKgg4oaSIDEg5LiqIGRyYXcgY2FsbO+8iOWbm+iCoi/ohJrotr7nrYnmiJDnu4Tpm7bku7bvvIzmjqfliLbnp7vliqjnq6/lvIDplIDvvIkgKi9cclxuICAgIHN1cmZHcm91cChwYXJlbnQ6IE5vZGUsIG5hbWU6IHN0cmluZywgZ2VvczogR2VvW10sIHN0eWxlOiBQYXJ0U3R5bGUsIHBvczogVmVjMywgdHJhbnNmb3JtPzogUGFydFRyYW5zZm9ybSk6IE5vZGUge1xyXG4gICAgICAgIHJldHVybiB0aGlzLnBhcnQocGFyZW50LCBuYW1lLCBnZW9zLmxlbmd0aCA9PT0gMSA/IGdlb3NbMF0gOiBtZXJnZSguLi5nZW9zKSwgc3R5bGUsIHBvcywgdHJhbnNmb3JtKVxyXG4gICAgfVxyXG5cclxuICAgIC8qKlxyXG4gICAgICog5p+U5ZKM6LS06Iqx6YOo5Lu277yI6ISa5bqV6L2v5b2xIC8g5Zyw6Z2i5YWJ5paR77yJ44CCXHJcbiAgICAgKlxyXG4gICAgICog5Yeg5L2V5Li65Y2V5L2N5qSt55CD77yIdXYueCDlhpnlhaXlvZLkuIDljJblvoTlkJHot53nprvvvInjgIHnlLHoioLngrnnvKnmlL7lhrPlrprlrp7pmYXlsLrlr7gg4oCU4oCUXHJcbiAgICAgKiDnnYDoibLlmajmja7mraTnlJ/miJDlvoTlkJEgYWxwaGEg5riQ5Y+Y77yM5b6X5YiwXCLovrnnvJjljJblvIBcIueahOaflOWSjOiJsuaWke+8jOiAjOS4jeaYr+ehrOi+ueiJsuadv+OAglxyXG4gICAgICpcclxuICAgICAqIOazqOaEj++8muaJgeW5s+akreeQg+WPqumAguWQiOi0tOWcqCoq5bmz6Z2iKirkuIrvvJvotLTlnKjlh7jmm7LpnaLvvIjohLjpooov55y855CD77yJ5LiK6K+355SoIHBhdGNoKCnvvIxcclxuICAgICAqIOWQpuWImei+uee8mOS8muemu+W8gOihqOmdouW9ouaIkFwi5rWu54mHXCLjgIJcclxuICAgICAqXHJcbiAgICAgKiBAcGFyYW0gcmFkaXVzIOS4iei9tOWNiuW+hO+8iOWQjOaXtuS9nOS4uuiKgueCuee8qeaUvu+8iVxyXG4gICAgICovXHJcbiAgICBkZWNhbChwYXJlbnQ6IE5vZGUsIG5hbWU6IHN0cmluZywgcmFkaXVzOiBbbnVtYmVyLCBudW1iZXIsIG51bWJlcl0sIHN0eWxlOiBQYXJ0U3R5bGUsXHJcbiAgICAgICAgICBwb3M6IFZlYzMsIHJvdD86IFZlYzMpOiBOb2RlIHtcclxuICAgICAgICBjb25zdCBzb2Z0U3R5bGU6IFBhcnRTdHlsZSA9IHtcclxuICAgICAgICAgICAgLi4uc3R5bGUsIG91dGxpbmU6IDAsIGZ1cjogMCwgc3BlYzogMCwgZmxhdDogdHJ1ZSwgYmxlbmQ6IHRydWUsXHJcbiAgICAgICAgICAgIHNvZnQ6IHN0eWxlLnNvZnQgPz8gWzAuNTUsIDEuMF0sXHJcbiAgICAgICAgfVxyXG4gICAgICAgIHJldHVybiB0aGlzLnN1cmYocGFyZW50LCBuYW1lLCByYWRpYWxVVihlbGxpcHNvaWQoMSwgMSwgMSwgMjQsIDE2KSksIHNvZnRTdHlsZSwgcG9zLFxyXG4gICAgICAgICAgICB7IHJvdCwgc2NhbGU6IG5ldyBWZWMzKHJhZGl1c1swXSwgcmFkaXVzWzFdLCByYWRpdXNbMl0pIH0pXHJcbiAgICB9XHJcblxyXG4gICAgLyoqXHJcbiAgICAgKiDnkIPpnaLotLToirHvvIjotLTlkIjmm7LpnaLnmoTova/oibLmlpEgLyDkuq7niYfvvInvvJroha7nuqLjgIHnnLznkIPpq5jlhYnjgIJcclxuICAgICAqXHJcbiAgICAgKiDlh6DkvZXkuI7nm67moIfmm7LpnaLlkIzlv4PjgIHljYrlvoTmjIkgaW5mbGF0ZSDnlaXmlL7lpKfvvIzlm6DmraTlrozlhajotLTlkIjmm7LnjofvvJtcclxuICAgICAqIOi+uee8mOaflOWSjOW6pueUsSB1di54IOeahOW+hOWQkeihsOWHj++8iHN0eWxlLnNvZnTvvInmjqfliLbjgIJcclxuICAgICAqXHJcbiAgICAgKiBAcGFyYW0gZGlyIOeJh+S4reW/g+aWueWQke+8iOeQg+W/g+aMh+WQkeeJh+S4reW/g++8jOWxgOmDqOepuumXtO+8iVxyXG4gICAgICogQHBhcmFtIGFuZ2xlVS9hbmdsZVYg5rC05bmzL+WeguebtOW8oOinku+8iOW8p+W6pu+8iVxyXG4gICAgICogQHBhcmFtIHJhZGlpIOebruagh+akreeQg+S4iei9tOWNiuW+hO+8iOWmguecvOePoOOAgeWktOmDqO+8iVxyXG4gICAgICogQHBhcmFtIGluZmxhdGUg55u45a+555uu5qCH6KGo6Z2i55qE5aSW5omp5q+U5L6L77yIMS4wMDQg4omIIOi0tOWQiOS4jeepv+aPku+8iVxyXG4gICAgICovXHJcbiAgICBwYXRjaChwYXJlbnQ6IE5vZGUsIG5hbWU6IHN0cmluZywgZGlyOiBQMywgYW5nbGVVOiBudW1iZXIsIGFuZ2xlVjogbnVtYmVyLFxyXG4gICAgICAgICAgcmFkaWk6IFtudW1iZXIsIG51bWJlciwgbnVtYmVyXSwgc3R5bGU6IFBhcnRTdHlsZSwgcG9zOiBWZWMzLCBpbmZsYXRlID0gMS4wMDYpOiBOb2RlIHtcclxuICAgICAgICBjb25zdCBnZW9tZXRyeSA9IHNwaGVyZVBhdGNoKGRpciwgYW5nbGVVLCBhbmdsZVYsXHJcbiAgICAgICAgICAgIFtyYWRpaVswXSAqIGluZmxhdGUsIHJhZGlpWzFdICogaW5mbGF0ZSwgcmFkaWlbMl0gKiBpbmZsYXRlXSlcclxuICAgICAgICByZXR1cm4gdGhpcy5zdXJmKHBhcmVudCwgbmFtZSwgZ2VvbWV0cnksIHsgLi4uc3R5bGUsIG91dGxpbmU6IDAsIGZ1cjogMCwgc3BlYzogMCB9LCBwb3MpXHJcbiAgICB9XHJcblxyXG4gICAgLyoqIOeQg+S9k+mDqOS7tiAqL1xyXG4gICAgYmFsbChwYXJlbnQ6IE5vZGUsIG5hbWU6IHN0cmluZywgcmFkaXVzOiBudW1iZXIsIHN0eWxlOiBQYXJ0U3R5bGUsIHBvczogVmVjMywgdHJhbnNmb3JtPzogUGFydFRyYW5zZm9ybSk6IE5vZGUge1xyXG4gICAgICAgIHJldHVybiB0aGlzLnBhcnQocGFyZW50LCBuYW1lLCB0aGlzLmNjLnByaW1pdGl2ZXMuc3BoZXJlKHJhZGl1cyksIHN0eWxlLCBwb3MsIHRyYW5zZm9ybSlcclxuICAgIH1cclxuXHJcbiAgICAvKiog5pa55Z2X6YOo5Lu277yI5a625YW3L+WimeS9ky/lnLDmnb/vvIkgKi9cclxuICAgIGJveFBhcnQocGFyZW50OiBOb2RlLCBuYW1lOiBzdHJpbmcsIHNpemU6IHsgdzogbnVtYmVyOyBoOiBudW1iZXI7IGQ6IG51bWJlciB9LCBzdHlsZTogUGFydFN0eWxlLFxyXG4gICAgICAgICAgICBwb3M6IFZlYzMsIHRyYW5zZm9ybT86IFBhcnRUcmFuc2Zvcm0pOiBOb2RlIHtcclxuICAgICAgICByZXR1cm4gdGhpcy5wYXJ0KHBhcmVudCwgbmFtZSxcclxuICAgICAgICAgICAgdGhpcy5jYy5wcmltaXRpdmVzLmJveCh7IHdpZHRoOiBzaXplLncsIGhlaWdodDogc2l6ZS5oLCBsZW5ndGg6IHNpemUuZCB9KSwgc3R5bGUsIHBvcywgdHJhbnNmb3JtKVxyXG4gICAgfVxyXG5cclxuICAgIC8qKiDlnIbplKXpg6jku7bvvIjlsJbogLMv6KOF6aWw77yJICovXHJcbiAgICBjb25lUGFydChwYXJlbnQ6IE5vZGUsIG5hbWU6IHN0cmluZywgcmFkaXVzOiBudW1iZXIsIGhlaWdodDogbnVtYmVyLCBzdHlsZTogUGFydFN0eWxlLFxyXG4gICAgICAgICAgICAgcG9zOiBWZWMzLCB0cmFuc2Zvcm0/OiBQYXJ0VHJhbnNmb3JtKTogTm9kZSB7XHJcbiAgICAgICAgcmV0dXJuIHRoaXMucGFydChwYXJlbnQsIG5hbWUsIHRoaXMuY2MucHJpbWl0aXZlcy5jb25lKHJhZGl1cywgaGVpZ2h0KSwgc3R5bGUsIHBvcywgdHJhbnNmb3JtKVxyXG4gICAgfVxyXG5cclxuICAgIC8qKiDog7blm4rpg6jku7bvvIjlm5vogqIv5bC+5be0L+eql+ajgu+8iSAqL1xyXG4gICAgY2FwUGFydChwYXJlbnQ6IE5vZGUsIG5hbWU6IHN0cmluZywgcmFkaXVzOiBudW1iZXIsIGhlaWdodDogbnVtYmVyLCBzdHlsZTogUGFydFN0eWxlLFxyXG4gICAgICAgICAgICBwb3M6IFZlYzMsIHRyYW5zZm9ybT86IFBhcnRUcmFuc2Zvcm0pOiBOb2RlIHtcclxuICAgICAgICByZXR1cm4gdGhpcy5wYXJ0KHBhcmVudCwgbmFtZSwgdGhpcy5jYy5wcmltaXRpdmVzLmNhcHN1bGUocmFkaXVzLCByYWRpdXMsIGhlaWdodCksIHN0eWxlLCBwb3MsIHRyYW5zZm9ybSlcclxuICAgIH1cclxuXHJcbiAgICAvKiog5ZyG5p+x6YOo5Lu277yI5Zyw5q+vL+eily/oirHnm4Yv5Z2Q5Z6r77yJICovXHJcbiAgICBjeWxQYXJ0KHBhcmVudDogTm9kZSwgbmFtZTogc3RyaW5nLCB0b3BSOiBudW1iZXIsIGJvdHRvbVI6IG51bWJlciwgaGVpZ2h0OiBudW1iZXIsIHN0eWxlOiBQYXJ0U3R5bGUsXHJcbiAgICAgICAgICAgIHBvczogVmVjMywgdHJhbnNmb3JtPzogUGFydFRyYW5zZm9ybSk6IE5vZGUge1xyXG4gICAgICAgIHJldHVybiB0aGlzLnBhcnQocGFyZW50LCBuYW1lLCB0aGlzLmNjLnByaW1pdGl2ZXMuY3lsaW5kZXIodG9wUiwgYm90dG9tUiwgaGVpZ2h0KSwgc3R5bGUsIHBvcywgdHJhbnNmb3JtKVxyXG4gICAgfVxyXG5cclxuICAgIC8qKiDlnIbnjq/pg6jku7bvvIjlkbzllablnIgv5oyC546vL+ecvOmVnOahhu+8iSAqL1xyXG4gICAgdG9ydXNQYXJ0KHBhcmVudDogTm9kZSwgbmFtZTogc3RyaW5nLCByYWRpdXM6IG51bWJlciwgdHViZTogbnVtYmVyLCBzdHlsZTogUGFydFN0eWxlLFxyXG4gICAgICAgICAgICAgIHBvczogVmVjMywgdHJhbnNmb3JtPzogUGFydFRyYW5zZm9ybSk6IE5vZGUge1xyXG4gICAgICAgIHJldHVybiB0aGlzLnBhcnQocGFyZW50LCBuYW1lLCB0aGlzLmNjLnByaW1pdGl2ZXMudG9ydXMocmFkaXVzLCB0dWJlKSwgc3R5bGUsIHBvcywgdHJhbnNmb3JtKVxyXG4gICAgfVxyXG5cclxuICAgIC8qKlxyXG4gICAgICog5pu05paw5bey5p6E5bu66YOo5Lu255qE5Y2V5Liq5p2Q6LSo5bGe5oCn44CCXHJcbiAgICAgKlxyXG4gICAgICog55So5LqO6ZyA6KaB5Yqo5oCB6LCD5pW055qE54OY54SZ5Y+C5pWw77yI5aaC6IWu57qi5by65bqm6ZqP5oOF57uq5Y+Y5YyW77yJ44CCXHJcbiAgICAgKi9cclxuICAgIHNldFBhcnRQcm9wZXJ0eShub2RlOiBOb2RlLCBuYW1lOiBzdHJpbmcsIHZhbHVlOiBDb2xvciB8IFZlYzQpOiB2b2lkIHtcclxuICAgICAgICBjb25zdCBtb2RlbCA9IG5vZGUuY29tcG9uZW50cy5maW5kKFxyXG4gICAgICAgICAgICBjID0+IChjIGFzIHVua25vd24gYXMgUGV0TW9kZWxMaWtlKS5tZXNoICE9PSB1bmRlZmluZWQsXHJcbiAgICAgICAgKSBhcyB1bmtub3duIGFzIFBldE1vZGVsTGlrZSB8IG51bGxcclxuICAgICAgICBpZiAoIW1vZGVsIHx8ICFtb2RlbC5tYXRlcmlhbCkge1xyXG4gICAgICAgICAgICByZXR1cm5cclxuICAgICAgICB9XHJcbiAgICAgICAgdHJ5IHtcclxuICAgICAgICAgICAgbW9kZWwubWF0ZXJpYWwuc2V0UHJvcGVydHkobmFtZSwgdmFsdWUpXHJcbiAgICAgICAgfSBjYXRjaCAoZSkge1xyXG4gICAgICAgICAgICAvLyDmnZDotKjkuI3lj6/lhpnvvJrlv73nlaXvvIjkuI3pmLvloZ7kuLvmtYHnqIvvvIlcclxuICAgICAgICB9XHJcbiAgICB9XHJcblxyXG4gICAgLyoqIOabtOaWsOW3suaciemDqOS7tueahOadkOi0qOminOiJsu+8iOaNouiCpC/mg4Xnu6rlj5joibLnlKjvvJvpgI/mmI7pg6jku7blkIzmraUgYWxwaGHvvIkgKi9cclxuICAgIHJlY29sb3Iobm9kZTogTm9kZSwgY29sb3I6IENvbG9yLCBzaGFkZT86IENvbG9yLCBhbHBoYT86IG51bWJlcik6IHZvaWQge1xyXG4gICAgICAgIGNvbnN0IG1vZGVsID0gbm9kZS5jb21wb25lbnRzLmZpbmQoXHJcbiAgICAgICAgICAgIGMgPT4gKGMgYXMgdW5rbm93biBhcyBQZXRNb2RlbExpa2UpLm1lc2ggIT09IHVuZGVmaW5lZCxcclxuICAgICAgICApIGFzIHVua25vd24gYXMgUGV0TW9kZWxMaWtlIHwgbnVsbFxyXG4gICAgICAgIGlmICghbW9kZWwgfHwgIW1vZGVsLm1hdGVyaWFsKSB7XHJcbiAgICAgICAgICAgIHJldHVyblxyXG4gICAgICAgIH1cclxuICAgICAgICBjb25zdCBuZXh0ID0gYWxwaGEgPT09IHVuZGVmaW5lZCA/IGNvbG9yIDogbmV3IENvbG9yKGNvbG9yLnIsIGNvbG9yLmcsIGNvbG9yLmIsIGFscGhhKVxyXG4gICAgICAgIHRyeSB7XHJcbiAgICAgICAgICAgIG1vZGVsLm1hdGVyaWFsLnNldFByb3BlcnR5KCdtYWluQ29sb3InLCBuZXh0KVxyXG4gICAgICAgICAgICBpZiAoc2hhZGUgJiYgdGhpcy5pc1Rvb24pIHtcclxuICAgICAgICAgICAgICAgIG1vZGVsLm1hdGVyaWFsLnNldFByb3BlcnR5KCdzaGFkZUNvbG9yJywgc2hhZGUpXHJcbiAgICAgICAgICAgIH1cclxuICAgICAgICB9IGNhdGNoIChlKSB7XHJcbiAgICAgICAgICAgIC8vIOadkOi0qOS4jeWPr+WGme+8muW/veeVpe+8iOS4jemYu+WhnuS4u+a1geeoi++8iVxyXG4gICAgICAgIH1cclxuICAgIH1cclxufVxyXG4iLCJpbXBvcnQgeyBDb2xvciwgR3JhcGhpY3MsIExhYmVsLCBOb2RlLCBVSU9wYWNpdHksIFZlYzMsIHR3ZWVuIH0gZnJvbSAnY2MnXHJcblxyXG4vKipcclxuICogMkQg54m55pWI5bGC77yI6KeG6KeJ6YeN5p6EIHYz77yJ77ya54ix5b+DIC8g5pif5pifIC8g5rOh5rOhIC8g552h5oSPIC8g6aOf54mpIC8g5pWw5YC85rWu5a2XIC8g5YWJ5ZyI44CCXHJcbiAqXHJcbiAqIOiuvuiuoeWOn+WIme+8mlxyXG4gKiAgLSDmiYDmnInlj43ppojpg71cIuS7juWuoOeJqei6q+S4iumVv+WHuuadpVwi77ya6LCD55So5pa55Lyg5YWlIDNEIOS4lueVjOWdkOagh++8jOacrOWxguaNoueul+S4uiBVSSDlnZDmoIfvvIxcclxuICogICAg5L+d6K+B57KS5a2Q5LiO5a6g54mp5Zyo5bGP5bmV5LiK5Lil5qC85a+56b2Q77yI5LiN5ZCM56qX5Y+j5bC65a+45LiL5L6d54S26LS05ZCI77yJ77ybXHJcbiAqICAtIOW8ueWHuueOsO+8iGJhY2tPdXTvvInihpIg6aOY56e777yI5q2j5bym5L6n56e777yJ4oaSIOa4kOmakOmUgOavge+8jOS4ieauteW8j++8jOmBv+WFjeacuuaisOebtOe6v+i/kOWKqO+8m1xyXG4gKiAgLSDlhajpg6ggR3JhcGhpY3Mg56iL5bqP5YyW57uY5Yi277yI6Zu26LS05Zu+6LWE5Lqn77yJ77yM5b2i54q25Y2z5ZOB54mM77ya5ZyG6KeS54ix5b+DICsg5LqU6KeS5pifICsg5rOh5rOh44CCXHJcbiAqL1xyXG5cclxudHlwZSBOb2RlRmFjdG9yeSA9IChuYW1lOiBzdHJpbmcsIHg6IG51bWJlciwgeTogbnVtYmVyKSA9PiBOb2RlXHJcblxyXG4vKiog5LiW55WM5Z2Q5qCHIOKGkiBVSSDlnZDmoIfmjaLnrpfvvIjnlLEgUGV0R2FtZVJvb3Qg5rOo5YWl77yM5YaF6YOo55SoIDNEIOebuOacuuaKleW9se+8iSAqL1xyXG50eXBlIFByb2plY3RvciA9ICh3b3JsZDogVmVjMykgPT4gVmVjM1xyXG5cclxuZXhwb3J0IGNsYXNzIFBldEVmZmVjdHMge1xyXG5cclxuICAgIHByaXZhdGUgcmVhZG9ubHkgbWFrZU5vZGU6IE5vZGVGYWN0b3J5XHJcbiAgICBwcml2YXRlIHJlYWRvbmx5IHRvVWk6IFByb2plY3RvclxyXG5cclxuICAgIGNvbnN0cnVjdG9yKG1ha2VOb2RlOiBOb2RlRmFjdG9yeSwgdG9VaTogUHJvamVjdG9yKSB7XHJcbiAgICAgICAgdGhpcy5tYWtlTm9kZSA9IG1ha2VOb2RlXHJcbiAgICAgICAgdGhpcy50b1VpID0gdG9VaVxyXG4gICAgfVxyXG5cclxuICAgIC8qKiDniLHlv4PnvqTvvJrooqvmiprmkbggLyDlvIDlv4MgLyDov5vpo5/vvIjku47lrqDnianlpLTpobblhpLlh7rvvIzluKbpmo/mnLrkvqfnp7vvvIkgKi9cclxuICAgIGhlYXJ0cyh3b3JsZDogVmVjMywgY291bnQgPSAzLCBjb2xvciA9IG5ldyBDb2xvcigyNTUsIDEyMiwgMTU4LCAyNTUpKTogdm9pZCB7XHJcbiAgICAgICAgZm9yIChsZXQgaW5kZXggPSAwOyBpbmRleCA8IGNvdW50OyBpbmRleCArPSAxKSB7XHJcbiAgICAgICAgICAgIGNvbnN0IG9mZnNldCA9IHRoaXMudG9VaSh3b3JsZClcclxuICAgICAgICAgICAgY29uc3Qgbm9kZSA9IHRoaXMubWFrZU5vZGUoJ2Z4LWhlYXJ0Jywgb2Zmc2V0LnggKyAoaW5kZXggLSAoY291bnQgLSAxKSAvIDIpICogMjYsIG9mZnNldC55KVxyXG4gICAgICAgICAgICBjb25zdCBnID0gbm9kZS5hZGRDb21wb25lbnQoR3JhcGhpY3MpXHJcbiAgICAgICAgICAgIHRoaXMuZHJhd0hlYXJ0KGcsIDMwICsgaW5kZXggKiAzLCBjb2xvcilcclxuICAgICAgICAgICAgdGhpcy5wb3BBbmRSaXNlKG5vZGUsIDAuMDggKiBpbmRleCwgMTIwICsgaW5kZXggKiAyNiwgaW5kZXggJSAyID09PSAwID8gMjYgOiAtMjYpXHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG5cclxuICAgIC8qKiDmmJ/mmJ/nvqTvvJrljYfnuqcgLyDmiJDlsLEgLyDnjqnogI3vvIjlm5vmlaPniIblvIDvvIkgKi9cclxuICAgIHN0YXJzKHdvcmxkOiBWZWMzLCBjb3VudCA9IDYsIGNvbG9yID0gbmV3IENvbG9yKDI1NSwgMjE0LCAxMTAsIDI1NSkpOiB2b2lkIHtcclxuICAgICAgICBjb25zdCBvcmlnaW4gPSB0aGlzLnRvVWkod29ybGQpXHJcbiAgICAgICAgZm9yIChsZXQgaW5kZXggPSAwOyBpbmRleCA8IGNvdW50OyBpbmRleCArPSAxKSB7XHJcbiAgICAgICAgICAgIGNvbnN0IGFuZ2xlID0gKE1hdGguUEkgKiAyICogaW5kZXgpIC8gY291bnQgLSBNYXRoLlBJIC8gMlxyXG4gICAgICAgICAgICBjb25zdCBub2RlID0gdGhpcy5tYWtlTm9kZSgnZngtc3RhcicsIG9yaWdpbi54LCBvcmlnaW4ueSlcclxuICAgICAgICAgICAgY29uc3QgZyA9IG5vZGUuYWRkQ29tcG9uZW50KEdyYXBoaWNzKVxyXG4gICAgICAgICAgICB0aGlzLmRyYXdTdGFyKGcsIDEzICsgKGluZGV4ICUgMykgKiAzLCBjb2xvcilcclxuICAgICAgICAgICAgbm9kZS5zZXRTY2FsZSgwLjEsIDAuMSwgMSlcclxuICAgICAgICAgICAgY29uc3Qgb3BhY2l0eSA9IG5vZGUuYWRkQ29tcG9uZW50KFVJT3BhY2l0eSlcclxuICAgICAgICAgICAgdHdlZW4obm9kZSlcclxuICAgICAgICAgICAgICAgIC5kZWxheSgwLjA1ICogaW5kZXgpXHJcbiAgICAgICAgICAgICAgICAudG8oMC4yMiwgeyBzY2FsZTogbmV3IFZlYzMoMS4xLCAxLjEsIDEpIH0sIHsgZWFzaW5nOiAnYmFja091dCcgfSlcclxuICAgICAgICAgICAgICAgIC50bygwLjE2LCB7IHNjYWxlOiBuZXcgVmVjMygxLCAxLCAxKSB9LCB7IGVhc2luZzogJ3NpbmVPdXQnIH0pXHJcbiAgICAgICAgICAgICAgICAuYnkoMC43NSwge1xyXG4gICAgICAgICAgICAgICAgICAgIHBvc2l0aW9uOiBuZXcgVmVjMyhNYXRoLmNvcyhhbmdsZSkgKiAoNzAgKyBpbmRleCAqIDYpLCBNYXRoLnNpbihhbmdsZSkgKiA1NiArIDQ2LCAwKSxcclxuICAgICAgICAgICAgICAgIH0sIHsgZWFzaW5nOiAnc2luZU91dCcgfSlcclxuICAgICAgICAgICAgICAgIC5jYWxsKCgpID0+IG5vZGUuZGVzdHJveSgpKVxyXG4gICAgICAgICAgICAgICAgLnN0YXJ0KClcclxuICAgICAgICAgICAgdHdlZW4ob3BhY2l0eSkuZGVsYXkoMC41KS50bygwLjUsIHsgb3BhY2l0eTogMCB9KS5zdGFydCgpXHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG5cclxuICAgIC8qKiDms6Hms6HnvqTvvJrmuIXmtIHvvIjku47lrqDnianlkajouqvlkJHkuIrpo5jmlaPvvIzluKbonrrml4vkvqfnp7vvvIkgKi9cclxuICAgIGJ1YmJsZXMod29ybGQ6IFZlYzMsIGNvdW50ID0gOSk6IHZvaWQge1xyXG4gICAgICAgIGNvbnN0IG9yaWdpbiA9IHRoaXMudG9VaSh3b3JsZClcclxuICAgICAgICBmb3IgKGxldCBpbmRleCA9IDA7IGluZGV4IDwgY291bnQ7IGluZGV4ICs9IDEpIHtcclxuICAgICAgICAgICAgY29uc3Qgbm9kZSA9IHRoaXMubWFrZU5vZGUoJ2Z4LWJ1YmJsZScsIG9yaWdpbi54ICsgKGluZGV4ICUgNSAtIDIpICogMzQsIG9yaWdpbi55IC0gMjApXHJcbiAgICAgICAgICAgIGNvbnN0IGcgPSBub2RlLmFkZENvbXBvbmVudChHcmFwaGljcylcclxuICAgICAgICAgICAgdGhpcy5kcmF3QnViYmxlKGcsIDkgKyAoaW5kZXggJSA0KSAqIDQpXHJcbiAgICAgICAgICAgIGNvbnN0IG9wYWNpdHkgPSBub2RlLmFkZENvbXBvbmVudChVSU9wYWNpdHkpXHJcbiAgICAgICAgICAgIG9wYWNpdHkub3BhY2l0eSA9IDIyMFxyXG4gICAgICAgICAgICBub2RlLnNldFNjYWxlKDAuMiwgMC4yLCAxKVxyXG4gICAgICAgICAgICBjb25zdCBzd2F5ID0gaW5kZXggJSAyID09PSAwID8gMzAgOiAtMzBcclxuICAgICAgICAgICAgdHdlZW4obm9kZSlcclxuICAgICAgICAgICAgICAgIC5kZWxheSgwLjA3ICogaW5kZXgpXHJcbiAgICAgICAgICAgICAgICAudG8oMC4yLCB7IHNjYWxlOiBuZXcgVmVjMygxLCAxLCAxKSB9LCB7IGVhc2luZzogJ2JhY2tPdXQnIH0pXHJcbiAgICAgICAgICAgICAgICAuYnkoMC45LCB7IHBvc2l0aW9uOiBuZXcgVmVjMyhzd2F5LCAxMzAgKyAoaW5kZXggJSAzKSAqIDMwLCAwKSB9LCB7IGVhc2luZzogJ3NpbmVPdXQnIH0pXHJcbiAgICAgICAgICAgICAgICAuY2FsbCgoKSA9PiBub2RlLmRlc3Ryb3koKSlcclxuICAgICAgICAgICAgICAgIC5zdGFydCgpXHJcbiAgICAgICAgICAgIHR3ZWVuKG9wYWNpdHkpLmRlbGF5KDAuNCkudG8oMC43LCB7IG9wYWNpdHk6IDAgfSkuc3RhcnQoKVxyXG4gICAgICAgIH1cclxuICAgIH1cclxuXHJcbiAgICAvKiog552h5oSPIFp6eu+8muedoeinieaXtuW+queOr+WGkuWHuu+8iOeUseiwg+eUqOaWueaMiemXtOmalOmHjeWkjeiwg+eUqO+8iSAqL1xyXG4gICAgc2xlZXBaKHdvcmxkOiBWZWMzLCBwaGFzZSA9IDApOiB2b2lkIHtcclxuICAgICAgICBjb25zdCBvcmlnaW4gPSB0aGlzLnRvVWkod29ybGQpXHJcbiAgICAgICAgY29uc3Qgbm9kZSA9IHRoaXMubWFrZU5vZGUoJ2Z4LXp6eicsIG9yaWdpbi54ICsgMzAsIG9yaWdpbi55ICsgMTApXHJcbiAgICAgICAgY29uc3QgbGFiZWwgPSBub2RlLmFkZENvbXBvbmVudChMYWJlbClcclxuICAgICAgICBsYWJlbC5zdHJpbmcgPSBwaGFzZSAlIDIgPT09IDAgPyAnWicgOiAneidcclxuICAgICAgICBsYWJlbC5mb250U2l6ZSA9IHBoYXNlICUgMiA9PT0gMCA/IDMwIDogMjJcclxuICAgICAgICBsYWJlbC5jb2xvciA9IG5ldyBDb2xvcigxNTAsIDEzMiwgMTkwLCAyNTUpXHJcbiAgICAgICAgY29uc3Qgb3BhY2l0eSA9IG5vZGUuYWRkQ29tcG9uZW50KFVJT3BhY2l0eSlcclxuICAgICAgICBvcGFjaXR5Lm9wYWNpdHkgPSAyMzVcclxuICAgICAgICB0d2Vlbihub2RlKVxyXG4gICAgICAgICAgICAuYnkoMS4xLCB7IHBvc2l0aW9uOiBuZXcgVmVjMygyNiwgODIsIDApIH0sIHsgZWFzaW5nOiAnc2luZU91dCcgfSlcclxuICAgICAgICAgICAgLmNhbGwoKCkgPT4gbm9kZS5kZXN0cm95KCkpXHJcbiAgICAgICAgICAgIC5zdGFydCgpXHJcbiAgICAgICAgdHdlZW4ob3BhY2l0eSkuZGVsYXkoMC41KS50bygwLjYsIHsgb3BhY2l0eTogMCB9KS5zdGFydCgpXHJcbiAgICB9XHJcblxyXG4gICAgLyoqIOmjn+eJqe+8muS7jueUu+mdouS4i+aWuemjnuWIsOWuoOeJqeWYtOi+ue+8iOWWgumjn+a8lOWHuu+8iSAqL1xyXG4gICAgZm9vZCh3b3JsZDogVmVjMywgaWNvbjogc3RyaW5nLCBvbkFycml2ZT86ICgpID0+IHZvaWQpOiB2b2lkIHtcclxuICAgICAgICBjb25zdCB0YXJnZXQgPSB0aGlzLnRvVWkod29ybGQpXHJcbiAgICAgICAgY29uc3Qgbm9kZSA9IHRoaXMubWFrZU5vZGUoJ2Z4LWZvb2QnLCB0YXJnZXQueCAtIDkwLCB0YXJnZXQueSAtIDEyMClcclxuICAgICAgICBjb25zdCBsYWJlbCA9IG5vZGUuYWRkQ29tcG9uZW50KExhYmVsKVxyXG4gICAgICAgIGxhYmVsLnN0cmluZyA9IGljb25cclxuICAgICAgICBsYWJlbC5mb250U2l6ZSA9IDQ2XHJcbiAgICAgICAgbm9kZS5zZXRTY2FsZSgwLjUsIDAuNSwgMSlcclxuICAgICAgICBjb25zdCBvcGFjaXR5ID0gbm9kZS5hZGRDb21wb25lbnQoVUlPcGFjaXR5KVxyXG4gICAgICAgIHR3ZWVuKG5vZGUpXHJcbiAgICAgICAgICAgIC50bygwLjM0LCB7IHBvc2l0aW9uOiBuZXcgVmVjMyh0YXJnZXQueCwgdGFyZ2V0LnkgLSA0MCwgMCksIHNjYWxlOiBuZXcgVmVjMygxLCAxLCAxKSB9LFxyXG4gICAgICAgICAgICAgICAgeyBlYXNpbmc6ICdxdWFkT3V0JyB9KVxyXG4gICAgICAgICAgICAuY2FsbCgoKSA9PiBvbkFycml2ZSAmJiBvbkFycml2ZSgpKVxyXG4gICAgICAgICAgICAuZGVsYXkoMC41KVxyXG4gICAgICAgICAgICAudG8oMC4yNCwgeyBwb3NpdGlvbjogbmV3IFZlYzModGFyZ2V0LngsIHRhcmdldC55IC0gMjAsIDApLCBzY2FsZTogbmV3IFZlYzMoMC4yLCAwLjIsIDEpIH0sXHJcbiAgICAgICAgICAgICAgICB7IGVhc2luZzogJ3NpbmVJbicgfSlcclxuICAgICAgICAgICAgLmNhbGwoKCkgPT4gbm9kZS5kZXN0cm95KCkpXHJcbiAgICAgICAgICAgIC5zdGFydCgpXHJcbiAgICAgICAgdHdlZW4ob3BhY2l0eSkuZGVsYXkoMS4wKS50bygwLjI0LCB7IG9wYWNpdHk6IDAgfSkuc3RhcnQoKVxyXG4gICAgfVxyXG5cclxuICAgIC8qKiDmlbDlgLwv57uT5p6c5rWu5a2X77ya5LiK6aOY5riQ6ZqQ77yI5bim6L275b6u5L6n5pGG77yM6YG/5YWN5py65qKw55u057q/77yJICovXHJcbiAgICBmbG9hdFRleHQod29ybGQ6IFZlYzMsIHRleHQ6IHN0cmluZywgY29sb3IgPSBuZXcgQ29sb3IoMjU1LCAyNDYsIDIyNiwgMjU1KSwgZm9udFNpemUgPSAyNCk6IHZvaWQge1xyXG4gICAgICAgIGNvbnN0IG9yaWdpbiA9IHRoaXMudG9VaSh3b3JsZClcclxuICAgICAgICBjb25zdCBub2RlID0gdGhpcy5tYWtlTm9kZSgnZngtdGV4dCcsIG9yaWdpbi54LCBvcmlnaW4ueSArIDMwKVxyXG4gICAgICAgIGNvbnN0IGxhYmVsID0gbm9kZS5hZGRDb21wb25lbnQoTGFiZWwpXHJcbiAgICAgICAgbGFiZWwuc3RyaW5nID0gdGV4dFxyXG4gICAgICAgIGxhYmVsLmZvbnRTaXplID0gZm9udFNpemVcclxuICAgICAgICBsYWJlbC5saW5lSGVpZ2h0ID0gTWF0aC5yb3VuZChmb250U2l6ZSAqIDEuMilcclxuICAgICAgICBsYWJlbC5jb2xvciA9IGNvbG9yXHJcbiAgICAgICAgY29uc3Qgb3BhY2l0eSA9IG5vZGUuYWRkQ29tcG9uZW50KFVJT3BhY2l0eSlcclxuICAgICAgICB0d2Vlbihub2RlKVxyXG4gICAgICAgICAgICAudG8oMC4zNCwgeyBwb3NpdGlvbjogbmV3IFZlYzMob3JpZ2luLnggKyAxMiwgb3JpZ2luLnkgKyA3NiwgMCkgfSwgeyBlYXNpbmc6ICdzaW5lT3V0JyB9KVxyXG4gICAgICAgICAgICAudG8oMC41LCB7IHBvc2l0aW9uOiBuZXcgVmVjMyhvcmlnaW4ueCAtIDYsIG9yaWdpbi55ICsgMTIyLCAwKSB9LCB7IGVhc2luZzogJ3NpbmVJbk91dCcgfSlcclxuICAgICAgICAgICAgLmNhbGwoKCkgPT4gbm9kZS5kZXN0cm95KCkpXHJcbiAgICAgICAgICAgIC5zdGFydCgpXHJcbiAgICAgICAgdHdlZW4ob3BhY2l0eSkuZGVsYXkoMC41KS50bygwLjQ1LCB7IG9wYWNpdHk6IDAgfSkuc3RhcnQoKVxyXG4gICAgfVxyXG5cclxuICAgIC8qKiDlhYnlnIjmianmlaPvvJrngrnlh7sv5Y2H57qn55qE5Yay5Ye75rOi77yI5LiA5ZyI5pS+5aSn5reh5Ye655qE5ZyG546v77yJICovXHJcbiAgICByaW5nKHdvcmxkOiBWZWMzLCBjb2xvciA9IG5ldyBDb2xvcigyNTUsIDIyNiwgMTYwLCAyNTUpKTogdm9pZCB7XHJcbiAgICAgICAgY29uc3Qgb3JpZ2luID0gdGhpcy50b1VpKHdvcmxkKVxyXG4gICAgICAgIGNvbnN0IG5vZGUgPSB0aGlzLm1ha2VOb2RlKCdmeC1yaW5nJywgb3JpZ2luLngsIG9yaWdpbi55KVxyXG4gICAgICAgIGNvbnN0IGcgPSBub2RlLmFkZENvbXBvbmVudChHcmFwaGljcylcclxuICAgICAgICBnLmxpbmVXaWR0aCA9IDRcclxuICAgICAgICBnLnN0cm9rZUNvbG9yID0gY29sb3JcclxuICAgICAgICBnLmNpcmNsZSgwLCAwLCAyNilcclxuICAgICAgICBnLnN0cm9rZSgpXHJcbiAgICAgICAgY29uc3Qgb3BhY2l0eSA9IG5vZGUuYWRkQ29tcG9uZW50KFVJT3BhY2l0eSlcclxuICAgICAgICB0d2Vlbihub2RlKVxyXG4gICAgICAgICAgICAudG8oMC40MiwgeyBzY2FsZTogbmV3IFZlYzMoMi42LCAyLjYsIDEpIH0sIHsgZWFzaW5nOiAnc2luZU91dCcgfSlcclxuICAgICAgICAgICAgLmNhbGwoKCkgPT4gbm9kZS5kZXN0cm95KCkpXHJcbiAgICAgICAgICAgIC5zdGFydCgpXHJcbiAgICAgICAgdHdlZW4ob3BhY2l0eSkudG8oMC40MiwgeyBvcGFjaXR5OiAwIH0pLnN0YXJ0KClcclxuICAgIH1cclxuXHJcbiAgICAvKiog5bCP6Zeq54K577ya5Lu75oSP5Lqk5LqS55qE6YCa55So54K557yA77yI6ZqP5py655+t6Led56a75aSW54iG77yJICovXHJcbiAgICBzcGFya2xlKHdvcmxkOiBWZWMzLCBjb3VudCA9IDQpOiB2b2lkIHtcclxuICAgICAgICBjb25zdCBvcmlnaW4gPSB0aGlzLnRvVWkod29ybGQpXHJcbiAgICAgICAgZm9yIChsZXQgaW5kZXggPSAwOyBpbmRleCA8IGNvdW50OyBpbmRleCArPSAxKSB7XHJcbiAgICAgICAgICAgIGNvbnN0IG5vZGUgPSB0aGlzLm1ha2VOb2RlKCdmeC1zcGFyaycsIG9yaWdpbi54LCBvcmlnaW4ueSlcclxuICAgICAgICAgICAgY29uc3QgZyA9IG5vZGUuYWRkQ29tcG9uZW50KEdyYXBoaWNzKVxyXG4gICAgICAgICAgICB0aGlzLmRyYXdTdGFyKGcsIDcsIG5ldyBDb2xvcigyNTUsIDI1NSwgMjU1LCAyNTUpKVxyXG4gICAgICAgICAgICBjb25zdCBvcGFjaXR5ID0gbm9kZS5hZGRDb21wb25lbnQoVUlPcGFjaXR5KVxyXG4gICAgICAgICAgICBjb25zdCBhbmdsZSA9IE1hdGgucmFuZG9tKCkgKiBNYXRoLlBJICogMlxyXG4gICAgICAgICAgICBjb25zdCBkaXN0YW5jZSA9IDQwICsgTWF0aC5yYW5kb20oKSAqIDQwXHJcbiAgICAgICAgICAgIHR3ZWVuKG5vZGUpXHJcbiAgICAgICAgICAgICAgICAuYnkoMC41LCB7XHJcbiAgICAgICAgICAgICAgICAgICAgcG9zaXRpb246IG5ldyBWZWMzKE1hdGguY29zKGFuZ2xlKSAqIGRpc3RhbmNlLCBNYXRoLnNpbihhbmdsZSkgKiBkaXN0YW5jZSwgMCksXHJcbiAgICAgICAgICAgICAgICB9LCB7IGVhc2luZzogJ3NpbmVPdXQnIH0pXHJcbiAgICAgICAgICAgICAgICAuY2FsbCgoKSA9PiBub2RlLmRlc3Ryb3koKSlcclxuICAgICAgICAgICAgICAgIC5zdGFydCgpXHJcbiAgICAgICAgICAgIHR3ZWVuKG9wYWNpdHkpLnRvKDAuNSwgeyBvcGFjaXR5OiAwIH0pLnN0YXJ0KClcclxuICAgICAgICB9XHJcbiAgICB9XHJcblxyXG4gICAgLy8gLS0tLS0tLS0tLS0tLS0tLSDlvaLnirbnu5jliLYgLS0tLS0tLS0tLS0tLS0tLVxyXG5cclxuICAgIC8qKiDlv4PlvaLvvIjkuKTmrrXotJ3loZ7lsJTvvIzlnIbmtqbppbHmu6HvvIkgKi9cclxuICAgIHByaXZhdGUgZHJhd0hlYXJ0KGc6IEdyYXBoaWNzLCBzaXplOiBudW1iZXIsIGNvbG9yOiBDb2xvcik6IHZvaWQge1xyXG4gICAgICAgIGNvbnN0IHMgPSBzaXplIC8gMzBcclxuICAgICAgICBnLmZpbGxDb2xvciA9IGNvbG9yXHJcbiAgICAgICAgZy5tb3ZlVG8oMCwgNiAqIHMpXHJcbiAgICAgICAgZy5iZXppZXJDdXJ2ZVRvKDggKiBzLCAxNiAqIHMsIDIyICogcywgOCAqIHMsIDAsIC0xNCAqIHMpXHJcbiAgICAgICAgZy5iZXppZXJDdXJ2ZVRvKC0yMiAqIHMsIDggKiBzLCAtOCAqIHMsIDE2ICogcywgMCwgNiAqIHMpXHJcbiAgICAgICAgZy5maWxsKClcclxuICAgIH1cclxuXHJcbiAgICAvKiog5LqU6KeS5pifICovXHJcbiAgICBwcml2YXRlIGRyYXdTdGFyKGc6IEdyYXBoaWNzLCByYWRpdXM6IG51bWJlciwgY29sb3I6IENvbG9yKTogdm9pZCB7XHJcbiAgICAgICAgZy5maWxsQ29sb3IgPSBjb2xvclxyXG4gICAgICAgIGNvbnN0IHBvaW50czogbnVtYmVyW10gPSBbXVxyXG4gICAgICAgIGZvciAobGV0IGluZGV4ID0gMDsgaW5kZXggPCAxMDsgaW5kZXggKz0gMSkge1xyXG4gICAgICAgICAgICBjb25zdCByID0gaW5kZXggJSAyID09PSAwID8gcmFkaXVzIDogcmFkaXVzICogMC40NlxyXG4gICAgICAgICAgICBjb25zdCBhbmdsZSA9IC1NYXRoLlBJIC8gMiArIChpbmRleCAqIE1hdGguUEkpIC8gNVxyXG4gICAgICAgICAgICBwb2ludHMucHVzaChNYXRoLmNvcyhhbmdsZSkgKiByLCBNYXRoLnNpbihhbmdsZSkgKiByKVxyXG4gICAgICAgIH1cclxuICAgICAgICBnLm1vdmVUbyhwb2ludHNbMF0sIHBvaW50c1sxXSlcclxuICAgICAgICBmb3IgKGxldCBpbmRleCA9IDI7IGluZGV4IDwgcG9pbnRzLmxlbmd0aDsgaW5kZXggKz0gMikge1xyXG4gICAgICAgICAgICBnLmxpbmVUbyhwb2ludHNbaW5kZXhdLCBwb2ludHNbaW5kZXggKyAxXSlcclxuICAgICAgICB9XHJcbiAgICAgICAgZy5jbG9zZSgpXHJcbiAgICAgICAgZy5maWxsKClcclxuICAgIH1cclxuXHJcbiAgICAvKiog5rOh5rOh77yI5o+P6L655ZyGICsg5bem5LiK6auY5YWJ54K577yJICovXHJcbiAgICBwcml2YXRlIGRyYXdCdWJibGUoZzogR3JhcGhpY3MsIHJhZGl1czogbnVtYmVyKTogdm9pZCB7XHJcbiAgICAgICAgZy5saW5lV2lkdGggPSAyLjRcclxuICAgICAgICBnLnN0cm9rZUNvbG9yID0gbmV3IENvbG9yKDIwNiwgMjM0LCAyNTUsIDIzNSlcclxuICAgICAgICBnLmZpbGxDb2xvciA9IG5ldyBDb2xvcigyMDYsIDIzNCwgMjU1LCA1MilcclxuICAgICAgICBnLmNpcmNsZSgwLCAwLCByYWRpdXMpXHJcbiAgICAgICAgZy5maWxsKClcclxuICAgICAgICBnLnN0cm9rZSgpXHJcbiAgICAgICAgZy5maWxsQ29sb3IgPSBuZXcgQ29sb3IoMjU1LCAyNTUsIDI1NSwgMjEwKVxyXG4gICAgICAgIGcuY2lyY2xlKC1yYWRpdXMgKiAwLjMyLCByYWRpdXMgKiAwLjM0LCBNYXRoLm1heCgxLjYsIHJhZGl1cyAqIDAuMTgpKVxyXG4gICAgICAgIGcuZmlsbCgpXHJcbiAgICB9XHJcblxyXG4gICAgLyoqIOW8ueWHuiDihpIg6aOY56e7IOKGkiDmuJDpmpDplIDmr4HvvIjniLHlv4PkuJPnlKjoioLlpY/vvIkgKi9cclxuICAgIHByaXZhdGUgcG9wQW5kUmlzZShub2RlOiBOb2RlLCBkZWxheTogbnVtYmVyLCByaXNlOiBudW1iZXIsIGRyaWZ0OiBudW1iZXIpOiB2b2lkIHtcclxuICAgICAgICBjb25zdCBvcGFjaXR5ID0gbm9kZS5hZGRDb21wb25lbnQoVUlPcGFjaXR5KVxyXG4gICAgICAgIG5vZGUuc2V0U2NhbGUoMC4yLCAwLjIsIDEpXHJcbiAgICAgICAgdHdlZW4obm9kZSlcclxuICAgICAgICAgICAgLmRlbGF5KGRlbGF5KVxyXG4gICAgICAgICAgICAudG8oMC4yLCB7IHNjYWxlOiBuZXcgVmVjMygxLjE4LCAxLjE4LCAxKSB9LCB7IGVhc2luZzogJ2JhY2tPdXQnIH0pXHJcbiAgICAgICAgICAgIC50bygwLjE0LCB7IHNjYWxlOiBuZXcgVmVjMygxLCAxLCAxKSB9LCB7IGVhc2luZzogJ3NpbmVPdXQnIH0pXHJcbiAgICAgICAgICAgIC5ieSgwLjg2LCB7IHBvc2l0aW9uOiBuZXcgVmVjMyhkcmlmdCwgcmlzZSwgMCkgfSwgeyBlYXNpbmc6ICdzaW5lT3V0JyB9KVxyXG4gICAgICAgICAgICAuY2FsbCgoKSA9PiBub2RlLmRlc3Ryb3koKSlcclxuICAgICAgICAgICAgLnN0YXJ0KClcclxuICAgICAgICB0d2VlbihvcGFjaXR5KVxyXG4gICAgICAgICAgICAuZGVsYXkoZGVsYXkgKyAwLjUpXHJcbiAgICAgICAgICAgIC50bygwLjUsIHsgb3BhY2l0eTogMCB9KVxyXG4gICAgICAgICAgICAuc3RhcnQoKVxyXG4gICAgfVxyXG59XHJcbiIsImltcG9ydCB7IF9kZWNvcmF0b3IgfSBmcm9tICdjYyc7XG5cbi8qKlxuICogUGV0R2FtZUJyaWRnZe+8mkNvY29zIOWcuuaZr+S4juS4ieerr+Wuv+S4u++8iFdlYiBpZnJhbWUgLyBBcHAgV2ViVmlldyAvIOWwj+eoi+W6jyB3ZWItdmlld++8iVxuICog5LmL6Ze055qE5ZSv5LiA6YCa5L+h5aWR57qm77yI5a6e5pa95paH5qGjIMKnMi4y77yJ44CCXG4gKlxuICog6IGM6LSj6L6555WM77yaXG4gKiAgLSDmuLjmiI8g4oaSIOWuv+S4u+WPquWPkVwi5oSP5Zu+XCLvvIhpbnRlbnTvvInvvIznu53kuI3mkLrluKbkuJrliqHmlbDlgLzjgIHnu53kuI3nm7TmjqXosIMgQVBJ77ybXG4gKiAgLSDlrr/kuLsg4oaSIOa4uOaIj+WPquWPkVwi54q25oCBL+e7k+aenFwi77yI5pyN5Yqh56uv5p2D5aiB5pWw5o2u77yJ77yM5ri45oiP5Y+q5YGa5bGV56S65LiO5Yqo55S744CCXG4gKi9cblxuLyoqIOWuv+S4u+S4i+WPkeeahOWuoOeJqeWxleekuueKtuaAge+8iOacjeWKoeerryBQZXRWTyDnm7TmjqXmmKDlsITvvIzlrqLmiLfnq6/pm7borqHnrpfvvIkgKi9cbmV4cG9ydCBpbnRlcmZhY2UgUGV0RGlzcGxheVN0YXRlIHtcbiAgICBuYW1lOiBzdHJpbmc7XG4gICAgc3BlY2llczogc3RyaW5nO1xuICAgIGdyb3d0aFN0YWdlOiBzdHJpbmc7XG4gICAgbGV2ZWw6IG51bWJlcjtcbiAgICBleHBQZXJjZW50OiBudW1iZXI7XG4gICAgaHA6IG51bWJlcjtcbiAgICBtYXhIcDogbnVtYmVyO1xuICAgIGh1bmdlcjogbnVtYmVyO1xuICAgIGhhcHBpbmVzczogbnVtYmVyO1xuICAgIGVuZXJneTogbnVtYmVyO1xuICAgIGNsZWFubGluZXNzOiBudW1iZXI7XG4gICAgc3RhdHVzOiBzdHJpbmc7XG4gICAgYWN0aXZpdHlOYW1lPzogc3RyaW5nO1xuICAgIHNwZWVjaD86IHN0cmluZztcbiAgICAvKiog5aSW6KeC5Li76Imy6ZSu77yI55qu6IKk5Y+v5pS55Y+Y77yb57y655yB5oyJ56eN57G76YWN6Imy77yM6KeBIFBldEdhbWVSb290LlNLSU5fQ09MT1JT77yJICovXG4gICAgY29sb3I/OiBzdHJpbmc7XG4gICAgLyoqIOmFjemlsOmUru+8iOearuiCpOWPr+aUueWPmO+8m+S7heWxleekuuaAp+aPkOekuu+8iSAqL1xuICAgIGFjY2Vzc29yeT86IHN0cmluZztcbiAgICAvKiog6L+b5YyW6Zi25q6177yIMCDmnKrov5vljJbvvJvnlKjkuo7kvZPlnosv5YWJ5pWI5by65bqm77yJICovXG4gICAgZXZvbHV0aW9uU3RhZ2U/OiBudW1iZXI7XG59XG5cbi8qKiDmnI3liqHnq6/miJjmlpflvJXmk47lm57lkIjmtYHmsLTvvIjlrqLmiLfnq6/lj6rmkq3mlL7vvIzlj6/ot7Pov4fvvIkgKi9cbmV4cG9ydCBpbnRlcmZhY2UgQmF0dGxlUm91bmQge1xuICAgIHJvdW5kOiBudW1iZXI7XG4gICAgYWN0b3JOYW1lOiBzdHJpbmc7XG4gICAgYWN0aW9uOiBzdHJpbmc7XG4gICAgZGFtYWdlOiBudW1iZXI7XG4gICAgY3JpdGljYWw6IGJvb2xlYW47XG4gICAgZG9kZ2VkOiBib29sZWFuO1xuICAgIHRhcmdldE5hbWU6IHN0cmluZztcbiAgICB0YXJnZXRSZW1haW5pbmdIcDogbnVtYmVyO1xufVxuXG4vKiog5ri45oiP5Y+v5Y+R6LW355qE5oSP5Zu+77yI5a6/5Li75o2u5q2k6LCD55SoIG1hbGwtcGV0IEFQSe+8iSAqL1xuZXhwb3J0IHR5cGUgUGV0SW50ZW50QWN0aW9uID1cbiAgICB8ICdmZWVkJ1xuICAgIHwgJ3BsYXknXG4gICAgfCAnY2xlYW4nXG4gICAgfCAncmVzdCdcbiAgICB8ICdvcGVuV29yaydcbiAgICB8ICdvcGVuU3R1ZHknXG4gICAgfCAnb3BlbkJvdHRsZSdcbiAgICB8ICdvcGVuQmF0dGxlJ1xuICAgIHwgJ29wZW5DaGF0J1xuICAgIHwgJ29wZW5BY2hpZXZlbWVudHMnXG4gICAgfCAnb3BlblByb2ZpbGUnXG4gICAgfCAnb3BlblJhbmtpbmdzJ1xuICAgIC8qKiDlhbvmiJDpnaLmnb/vvIjllYbln44v6IOM5YyFL+aKgOiDvS/ov5vljJYv5rS75YqoL+S4sumXqC/lpJrlrqDnianvvJvljp/mlofmoaMgwqc4Oe+8iSAqL1xuICAgIHwgJ29wZW5DYXJlJ1xuICAgIC8qKiDkuInmnJ/vvJrlrrblm63vvIjmiL/pl7TluIPnva4v5a625YW3L+aLnOiuv++8iSAqL1xuICAgIHwgJ29wZW5Sb29tJ1xuICAgIC8qKiDkuInmnJ/vvJrmr4/ml6Xku7vliqHvvIjov5vluqYv6aKG5aWWL+WFqOa4heWuneeuse+8iSAqL1xuICAgIHwgJ29wZW5EYWlseSdcbiAgICAvKiog5LiJ5pyf77ya56S+5Lqk77yI5YWz57O7L+WlveWPiy/nlZnoqIDlopnvvIkgKi9cbiAgICB8ICdvcGVuU29jaWFsJ1xuICAgIC8qKiDkuInmnJ/vvJrogYzkuJrvvIjlhaXogYwv5bel5L2cL+aZi+WNh++8jOiQveWFu+aIkOmdouadv+iBjOS4mumhteetvu+8iSAqL1xuICAgIHwgJ29wZW5DYXJlZXInO1xuXG5leHBvcnQgdHlwZSBIb3N0VG9HYW1lID1cbiAgICB8IHsgc291cmNlOiAncGV0LWhvc3QnOyB0eXBlOiAnaW5pdCc7IHBldDogUGV0RGlzcGxheVN0YXRlOyB0aGVtZT86IHsgZGFyazogYm9vbGVhbiB9IH1cbiAgICB8IHsgc291cmNlOiAncGV0LWhvc3QnOyB0eXBlOiAncGV0U3RhdGUnOyBwZXQ6IFBldERpc3BsYXlTdGF0ZSB9XG4gICAgfCB7IHNvdXJjZTogJ3BldC1ob3N0JzsgdHlwZTogJ2FjdGlvblJlc3VsdCc7IGFjdGlvbjogc3RyaW5nOyBvazogYm9vbGVhbjsgbWVzc2FnZT86IHN0cmluZyB9XG4gICAgfCB7IHNvdXJjZTogJ3BldC1ob3N0JzsgdHlwZTogJ2JhdHRsZVJvdW5kcyc7IHJvdW5kczogQmF0dGxlUm91bmRbXTsgd29uOiBib29sZWFuIH1cbiAgICB8IHsgc291cmNlOiAncGV0LWhvc3QnOyB0eXBlOiAnY2hhdEJ1YmJsZSc7IGNvbnRlbnQ6IHN0cmluZyB9O1xuXG5leHBvcnQgdHlwZSBHYW1lVG9Ib3N0ID1cbiAgICB8IHsgc291cmNlOiAncGV0LWdhbWUnOyB0eXBlOiAncmVhZHknIH1cbiAgICB8IHsgc291cmNlOiAncGV0LWdhbWUnOyB0eXBlOiAnaW50ZW50JzsgYWN0aW9uOiBQZXRJbnRlbnRBY3Rpb24gfVxuICAgIHwgeyBzb3VyY2U6ICdwZXQtZ2FtZSc7IHR5cGU6ICdwZXRUYXBwZWQnIH07XG5cbmludGVyZmFjZSBXZWFwcE1pbmlQcm9ncmFtIHtcbiAgICBwb3N0TWVzc2FnZTogKGRhdGE6IHsgZGF0YTogR2FtZVRvSG9zdCB9KSA9PiB2b2lkO1xuICAgIG5hdmlnYXRlVG86IChvcHRpb25zOiB7IHVybDogc3RyaW5nIH0pID0+IHZvaWQ7XG59XG5cbmV4cG9ydCBjbGFzcyBQZXRHYW1lQnJpZGdlIHtcblxuICAgIHByaXZhdGUgaG9zdEhhbmRsZXI6ICgobWVzc2FnZTogSG9zdFRvR2FtZSkgPT4gdm9pZCkgfCBudWxsID0gbnVsbDtcbiAgICBwcml2YXRlIHJlYWRvbmx5IG9uTWVzc2FnZSA9IChldmVudDogTWVzc2FnZUV2ZW50KTogdm9pZCA9PiB7XG4gICAgICAgIC8vIFAwNe+8muadpea6kOagoemqjOKAlOKAlG1lc3NhZ2Ug6YCa6YGT5LuF5pyN5YqhIFdlYiBpZnJhbWUg5Zy65pmv77yMcGV0LWdhbWUg5LiO5a6/5Li7XG4gICAgICAgIC8vIOWQjOa6kOmdmeaAgemDqOe9su+8jG9yaWdpbiDlv4XpobvnrYnkuo7mnKznqpflj6Mgb3JpZ2lu77yb6Leo5rqQ5oqV6YCS5LiA5b6L5Lii5byDXG4gICAgICAgIC8v77yIQXBwL1JOIOWuv+S4u+i1sCBfX3BldEhvc3RNZXNzYWdlIOazqOWFpeOAgeWwj+eoi+W6j+i1sCB3eCDpgJrpgZPvvIzlnYfkuI3nu4/mnKznm5HlkKzvvIlcbiAgICAgICAgaWYgKGV2ZW50Lm9yaWdpbiAhPT0gd2luZG93LmxvY2F0aW9uLm9yaWdpbikge1xuICAgICAgICAgICAgcmV0dXJuO1xuICAgICAgICB9XG4gICAgICAgIHRoaXMuZGlzcGF0Y2goZXZlbnQuZGF0YSk7XG4gICAgfTtcblxuICAgIC8qKiDlrr/kuLvnjq/looPvvJppZnJhbWUod2ViKSAvIFJlYWN0TmF0aXZlV2ViVmlldyhhcHApIC8gd3gubWluaVByb2dyYW0od2VhcHApICovXG4gICAgcHJpdmF0ZSBkZXRlY3RIb3N0KCk6ICd3ZWInIHwgJ2FwcCcgfCAnd2VhcHAnIHtcbiAgICAgICAgY29uc3QgdyA9IHdpbmRvdyBhcyB1bmtub3duIGFzIHtcbiAgICAgICAgICAgIFJlYWN0TmF0aXZlV2ViVmlldz86IHsgcG9zdE1lc3NhZ2U6IChyYXc6IHN0cmluZykgPT4gdm9pZCB9O1xuICAgICAgICAgICAgd3g/OiB7IG1pbmlQcm9ncmFtPzogV2VhcHBNaW5pUHJvZ3JhbSB9O1xuICAgICAgICB9O1xuICAgICAgICBpZiAody53eCAmJiB3Lnd4Lm1pbmlQcm9ncmFtKSB7XG4gICAgICAgICAgICByZXR1cm4gJ3dlYXBwJztcbiAgICAgICAgfVxuICAgICAgICBpZiAody5SZWFjdE5hdGl2ZVdlYlZpZXcpIHtcbiAgICAgICAgICAgIHJldHVybiAnYXBwJztcbiAgICAgICAgfVxuICAgICAgICByZXR1cm4gJ3dlYic7XG4gICAgfVxuXG4gICAgLyoqIOaOpeaUtuWuv+S4u+a2iOaBr++8iGlmcmFtZSBwb3N0TWVzc2FnZSArIFJOIGluamVjdEphdmFTY3JpcHQg5Y+M6YCa6YGT77yJICovXG4gICAgYmluZChob3N0SGFuZGxlcjogKG1lc3NhZ2U6IEhvc3RUb0dhbWUpID0+IHZvaWQpOiB2b2lkIHtcbiAgICAgICAgdGhpcy5ob3N0SGFuZGxlciA9IGhvc3RIYW5kbGVyO1xuICAgICAgICB3aW5kb3cuYWRkRXZlbnRMaXN0ZW5lcignbWVzc2FnZScsIHRoaXMub25NZXNzYWdlKTtcbiAgICAgICAgLy8gQXBwIOWuv+S4u+mAmui/hyBpbmplY3RKYXZhU2NyaXB0IOiwg+eUqOivpeWFpeWPo++8iFdlYiDnq68gaWZyYW1lIOWcuuaZr+WQjOagt+WPr+eUqO+8iVxuICAgICAgICAod2luZG93IGFzIHVua25vd24gYXMgeyBfX3BldEhvc3RNZXNzYWdlPzogKHJhdzogdW5rbm93bikgPT4gdm9pZCB9KS5fX3BldEhvc3RNZXNzYWdlID1cbiAgICAgICAgICAgIChyYXc6IHVua25vd24pID0+IHtcbiAgICAgICAgICAgICAgICBpZiAodHlwZW9mIHJhdyA9PT0gJ3N0cmluZycpIHtcbiAgICAgICAgICAgICAgICAgICAgdHJ5IHtcbiAgICAgICAgICAgICAgICAgICAgICAgIHRoaXMuZGlzcGF0Y2goSlNPTi5wYXJzZShyYXcpKTtcbiAgICAgICAgICAgICAgICAgICAgfSBjYXRjaCAoZSkge1xuICAgICAgICAgICAgICAgICAgICAgICAgLy8g6Z2e5rOVIEpTT04g55u05o6l5b+955Wl77yI5a6/5Li76ISa5pys5rOo5YWl5byC5bi45LiN5bSp5rqD5Zy65pmv77yJXG4gICAgICAgICAgICAgICAgICAgICAgICBjb25zb2xlLndhcm4oJ1tQZXRHYW1lQnJpZGdlXSBpbnZhbGlkIGhvc3QgbWVzc2FnZSBpZ25vcmVkJyk7XG4gICAgICAgICAgICAgICAgICAgIH1cbiAgICAgICAgICAgICAgICB9IGVsc2Uge1xuICAgICAgICAgICAgICAgICAgICB0aGlzLmRpc3BhdGNoKHJhdyk7XG4gICAgICAgICAgICAgICAgfVxuICAgICAgICAgICAgfTtcbiAgICB9XG5cbiAgICAvKiog5Y246L2977yI57uE5Lu26ZSA5q+B5pe26LCD55So77yM6YG/5YWN5LqL5Lu255uR5ZCs5rOE5ryP77yJICovXG4gICAgZGlzcG9zZSgpOiB2b2lkIHtcbiAgICAgICAgd2luZG93LnJlbW92ZUV2ZW50TGlzdGVuZXIoJ21lc3NhZ2UnLCB0aGlzLm9uTWVzc2FnZSk7XG4gICAgICAgIHRoaXMuaG9zdEhhbmRsZXIgPSBudWxsO1xuICAgICAgICBkZWxldGUgKHdpbmRvdyBhcyB1bmtub3duIGFzIHsgX19wZXRIb3N0TWVzc2FnZT86IChyYXc6IHVua25vd24pID0+IHZvaWQgfSkuX19wZXRIb3N0TWVzc2FnZTtcbiAgICB9XG5cbiAgICBwcml2YXRlIGRpc3BhdGNoKGRhdGE6IHVua25vd24pOiB2b2lkIHtcbiAgICAgICAgY29uc3QgbXNnID0gZGF0YSBhcyBIb3N0VG9HYW1lO1xuICAgICAgICBpZiAoIW1zZyB8fCBtc2cuc291cmNlICE9PSAncGV0LWhvc3QnIHx8ICF0aGlzLmhvc3RIYW5kbGVyKSB7XG4gICAgICAgICAgICByZXR1cm47XG4gICAgICAgIH1cbiAgICAgICAgdGhpcy5ob3N0SGFuZGxlcihtc2cpO1xuICAgIH1cblxuICAgIC8qKiDlj5HpgIHmtojmga/liLDlrr/kuLvvvIjmjInnjq/looPpgInpgJrpgZPvvJt3ZWFwcCDnmoTlrp7ml7bpgJrpgZPmmK8gbmF2aWdhdGVUbyDor63kuYnvvIzop4EgUkVBRE1F77yJICovXG4gICAgc2VuZChtZXNzYWdlOiBHYW1lVG9Ib3N0KTogdm9pZCB7XG4gICAgICAgIGNvbnN0IGhvc3QgPSB0aGlzLmRldGVjdEhvc3QoKTtcbiAgICAgICAgY29uc3QgdyA9IHdpbmRvdyBhcyB1bmtub3duIGFzIHtcbiAgICAgICAgICAgIFJlYWN0TmF0aXZlV2ViVmlldz86IHsgcG9zdE1lc3NhZ2U6IChyYXc6IHN0cmluZykgPT4gdm9pZCB9O1xuICAgICAgICAgICAgd3g/OiB7IG1pbmlQcm9ncmFtPzogV2VhcHBNaW5pUHJvZ3JhbSB9O1xuICAgICAgICB9O1xuICAgICAgICBpZiAoaG9zdCA9PT0gJ2FwcCcgJiYgdy5SZWFjdE5hdGl2ZVdlYlZpZXcpIHtcbiAgICAgICAgICAgIHcuUmVhY3ROYXRpdmVXZWJWaWV3LnBvc3RNZXNzYWdlKEpTT04uc3RyaW5naWZ5KG1lc3NhZ2UpKTtcbiAgICAgICAgICAgIHJldHVybjtcbiAgICAgICAgfVxuICAgICAgICBpZiAoaG9zdCA9PT0gJ3dlYXBwJyAmJiB3Lnd4ICYmIHcud3gubWluaVByb2dyYW0pIHtcbiAgICAgICAgICAgIHcud3gubWluaVByb2dyYW0ucG9zdE1lc3NhZ2UoeyBkYXRhOiBtZXNzYWdlIH0pO1xuICAgICAgICAgICAgaWYgKG1lc3NhZ2UudHlwZSA9PT0gJ2ludGVudCcpIHtcbiAgICAgICAgICAgICAgICAvLyDlvq7kv6Egd2ViLXZpZXcg55qEIHBvc3RNZXNzYWdlIOS7heWcqOWbnumAgC/liIbkuqvml7bmnLrmipXpgJLvvJvlrp7ml7bmhI/lm77otbAgbmF2aWdhdGVUbyDokL3liLDljp/nlJ/pobVcbiAgICAgICAgICAgICAgICB3Lnd4Lm1pbmlQcm9ncmFtLm5hdmlnYXRlVG8oeyB1cmw6ICcvcGFnZXMvcGV0L2luZGV4P2ludGVudD0nICsgbWVzc2FnZS5hY3Rpb24gfSk7XG4gICAgICAgICAgICB9XG4gICAgICAgICAgICByZXR1cm47XG4gICAgICAgIH1cbiAgICAgICAgLy8gV2ViIGlmcmFtZe+8iOS7heWcqOecn+WuniBpZnJhbWUg5YaF5Y+R6YCB77yJ44CCdGFyZ2V0T3JpZ2luIOeUqCAnKifvvJpcbiAgICAgICAgLy8g5a6/5Li75rqQ6ZqP56uv6ICM5byC77yIVGFybyDoiJ7lj7A95ZCM5rqQ44CBRXhwbyBXZWI954us56uL5rqQ77yJ77yM5pS257Sn5Lya5Zyo5byC5rqQ5a6/5Li75LiL6KKr5rWP6KeI5Zmo5Lii5byD77ybXG4gICAgICAgIC8vIOWuieWFqOi+ueeVjOWcqOWuv+S4u+S+p++8iOWuv+S4u+WPquaOpeWPl+eZveWQjeWNlSBvcmlnaW4g55qE5raI5oGv77yJ44CCXG4gICAgICAgIGlmICh3aW5kb3cucGFyZW50ICYmIHdpbmRvdy5wYXJlbnQgIT09IHdpbmRvdykge1xuICAgICAgICAgICAgd2luZG93LnBhcmVudC5wb3N0TWVzc2FnZShtZXNzYWdlLCAnKicpO1xuICAgICAgICB9XG4gICAgfVxufVxuIiwiaW1wb3J0IHtcclxuICAgIF9kZWNvcmF0b3IsXHJcbiAgICBBbmltYXRpb24sXHJcbiAgICBBbmltYXRpb25DbGlwLFxyXG4gICAgQ2FtZXJhLFxyXG4gICAgQ29sb3IsXHJcbiAgICBDb21wb25lbnQsXHJcbiAgICBEaXJlY3Rpb25hbExpZ2h0LFxyXG4gICAgZGlyZWN0b3IsXHJcbiAgICBFZmZlY3RBc3NldCxcclxuICAgIGluc3RhbnRpYXRlLFxyXG4gICAgSW5wdXQsXHJcbiAgICBFdmVudFRvdWNoLFxyXG4gICAgaW5wdXQsXHJcbiAgICBMYXllcnMsXHJcbiAgICBNYXRlcmlhbCxcclxuICAgIE5vZGUsXHJcbiAgICBQcmVmYWIsXHJcbiAgICBTa2VsZXRhbEFuaW1hdGlvbixcclxuICAgIFRleHR1cmUyRCxcclxuICAgIHR3ZWVuLFxyXG4gICAgVUlUcmFuc2Zvcm0sXHJcbiAgICBWZWMzLFxyXG4gICAgVmVjNCxcclxuICAgIHJlc291cmNlcyxcclxuICAgIHNjcmVlbixcclxufSBmcm9tICdjYydcclxuaW1wb3J0IHsgSG9zdFRvR2FtZSwgUGV0RGlzcGxheVN0YXRlLCBQZXRHYW1lQnJpZGdlIH0gZnJvbSAnLi9QZXRHYW1lQnJpZGdlJ1xyXG5pbXBvcnQgeyBQZXRCdWlsZGVyS2l0IH0gZnJvbSAnLi9QZXRCdWlsZGVyS2l0J1xyXG5pbXBvcnQgeyBQZXRFZmZlY3RzIH0gZnJvbSAnLi9QZXRFZmZlY3RzJ1xyXG5pbXBvcnQgeyBidWlsZFJvb20sIFJvb21SZWZzIH0gZnJvbSAnLi9QZXRSb29tQnVpbGRlcidcclxuXHJcbmNvbnN0IHsgY2NjbGFzcyB9ID0gX2RlY29yYXRvclxyXG5cclxuLyoqXHJcbiAqIOWutuWbreWcuuaZr+S4u+e7hOS7tu+8iOinhuiniemHjeaehCB2OSDCtyDmsLTmnpzlrqDnianmiZPmoLfvvInjgIJcclxuICpcclxuICog57uT5p6E77yaXHJcbiAqICAtIDNEIOS4lueVjO+8muaIv+mXtO+8iFBldFJvb21CdWlsZGVy77yM57uf5LiA6LWwIHBldC10b29u77yJKyDlrqDnianvvIjlpJbpg6jnu5HlrprmqKHlnovvvIzop4HkuIvvvIlcclxuICogIC0g55u45py65py65L2N77yI5qiq5bGPIHJvb20gLyDnq5blsY8gcG9ydHJhaXQg6Ieq5Yqo6YCJ5oup77yJXHJcbiAqICAtIOaIv+mXtOawm+WbtOWKqOeUu++8iOWFieeCueS4iuWNhyAvIOeBr+azoeWRvOWQuCAvIOWFieaWkeWRvOWQuCAvIOeOqeWFt+i9u+aRhu+8iVxyXG4gKiAgLSAyRCDopobnm5blsYLvvJrnibnmlYjnspLlrZAgKyDkuJbnlYzlnZDmoIfmipXlvbFcclxuICogIC0g6YCa5L+h5qGl77yI5aWR57qm5Ya757uT77yM6KeBIFBldEdhbWVCcmlkZ2XvvIlcclxuICpcclxuICog5a6g54mp77ya5LqU5p6c6Zi15a655YWo6YOo6L+b5Zy6ID0g6I2J6I6TIC8g5qmY5a2QIC8g6KW/55OcIC8g6JOd6I6TIC8g54Gr6b6Z5p6c77yIdG9vbHMvZnJ1aXQtcGlwZWxpbmUg5om56YeP5Lqn5Ye677yJ44CCXHJcbiAqICAtIOavj+WPqumDveaYr+mXreWQiOaXi+i9rOabsumdoiArIOi0tOWbvuiKsee6uSArIDNEIOWkp+ecvCArIM+JIOi0tOmdoueMq+WYtCArIOiFrue6oiArIOi9r+iQjOW4vS/ps43lj7ZcclxuICogIC0g57uf5LiAIDQg6aqo6L2757uR5a6a77yIQm9keS9DYWx5eC9FeWVML0V5ZVLvvInvvIzkuInmnaHliarovpHvvJpJZGxlIDRzIC8gSGFwcHkgMS42N3MgLyBCbGluayAwLjI1c1xyXG4gKiAgLSDmnpznp43mjIkgRlJVSVRfU1BFQ1Mg5Yqg6L2977yaVVJMID9zcGVjaWVzPSDlvLrliLbpooTop4gg4oaSIOWuv+S4uyBzcGVjaWVzIOanveS9jeaYoOWwhCDihpIg6buY6K6k6I2J6I6TXHJcbiAqICAtIOaMpOWOiy/mi4nkvLjku6XlnLDpnaLkuLrplJrvvIhCb2R5IOmqqOWktOWfi+WcqOiEmuS4i++8ie+8jOaYr+awtOaenOWKqOaEn+eahOaguOW/g1xyXG4gKlxyXG4gKiDogYzotKPovrnnlYzvvIjkuI3lj5jvvInvvJrlj6rlgZrlsZXnpLrkuI7liqjnlLvvvIzmlbDlgLzlhajpg6jmnaXoh6rlrr/kuLvkuIvlj5HnmoQgUGV0RGlzcGxheVN0YXRl77yMXHJcbiAqIOeUqOaIt+aTjeS9nOWPquWbnuS8oCBpbnRlbnTjgILlpZHnuqbkuIDkuKrlrZfmrrXpg73msqHmlLnjgIJcclxuICovXHJcblxyXG4vKipcclxuICog5a6g54mp5qih5Z6L6LWE5rqQ6Lev5b6E77yI55u45a+5IGFzc2V0cy9yZXNvdXJjZXMv77yJ44CCXHJcbiAqXHJcbiAqIOKaoO+4jyBnbFRGIOWvvOWFpeWQjuS4u+i1hOa6kO+8iGdsdGYtc2NlbmXvvInnmoTlrZDotYTmupDlkI3nrYnkuo7mlofku7blkI3mnKzouqvvvIxcclxuICog5omA5Lul6LWE5rqQ5bqT5rOo5YaM55qE6Lev5b6E5pivIGDnm67lvZUv5paH5Lu25ZCNL+aWh+S7tuWQjWDvvIjnjKvniYjlrp7mtYvnoa7orqTnmoTop4TliJnvvIzmsLTmnpzmsr/nlKjvvInjgIJcclxuICovXHJcbi8qKiDpnIDopoHnlKjliLDnmoTliarovpHlkI3vvIjkupTmnpznu5/kuIDvvInjgILnvLrlk6rkuKrlsLHot7Pov4flk6rkuKrvvIzkuI3og73lm6DkuLrlsJHkuIDmnaHliarovpHlsLHorqnlrqDniankuI3lh7rnjrDjgIIgKi9cclxuY29uc3QgUEVUX0NMSVBTID0gWydJZGxlJywgJ0hhcHB5JywgJ0JsaW5rJ11cclxuLyoqIOWuoOeJqeermeS9je+8iOWcsOavr+S4reWkru+8m+S4juaIv+mXtOmHjOeahOi9r+W9seOAgeeOqeWFt+eQg+Wvuem9kO+8iSAqL1xyXG5jb25zdCBQRVRfUE9TID0gbmV3IFZlYzMoMCwgMCwgMC4zNSlcclxuXHJcbi8qKiDmsLTmnpzop4TmoLzvvJpHTEIg6Lev5b6E77yIYOebruW9lS/mlofku7blkI0v5paH5Lu25ZCNYCDkuInmrrXlvI/vvIkvIOe8qeaUviAvIOi6q+mrmCAvIOWktOmDqOeJueaViOmUmueCuSAvIOaOpeinpumYtOW9seWwuuWvuCAqL1xyXG5pbnRlcmZhY2UgRnJ1aXRTcGVjIHtcclxuICAgIHBhdGg6IHN0cmluZ1xyXG4gICAgZGlyOiBzdHJpbmdcclxuICAgIHNjYWxlOiBudW1iZXJcclxuICAgIGhlaWdodDogbnVtYmVyXHJcbiAgICBoZWFkOiBWZWMzXHJcbiAgICBzaGFkb3c6IFtudW1iZXIsIG51bWJlciwgbnVtYmVyXVxyXG59XHJcblxyXG4vKiog5LqU5p6c6KeE5qC86KGo77yI5bu65qih5YC8IMOXIOe8qeaUvu+8jOS4jiB0b29scy9mcnVpdC1waXBlbGluZSDlkIQgYnVpbGQg6ISa5pys5LiA5LiA5a+55bqU77yJICovXHJcbmNvbnN0IEZSVUlUX1NQRUNTOiBSZWNvcmQ8c3RyaW5nLCBGcnVpdFNwZWM+ID0ge1xyXG4gICAgc3RyYXdiZXJyeToge1xyXG4gICAgICAgIHBhdGg6ICdtb2RlbHMvZnJ1aXQvc3RyYXdiZXJyeS9zdHJhd2JlcnJ5L3N0cmF3YmVycnknLFxyXG4gICAgICAgIGRpcjogJ21vZGVscy9mcnVpdC9zdHJhd2JlcnJ5L3N0cmF3YmVycnknLFxyXG4gICAgICAgIHNjYWxlOiAxLjUsIGhlaWdodDogMS4wLCBoZWFkOiBuZXcgVmVjMygwLCAwLjgxLCAwLjQ1KSwgc2hhZG93OiBbMC41OCwgMC4wMTAsIDAuNDVdLFxyXG4gICAgfSxcclxuICAgIG9yYW5nZToge1xyXG4gICAgICAgIHBhdGg6ICdtb2RlbHMvZnJ1aXQvb3JhbmdlL29yYW5nZS9vcmFuZ2UnLFxyXG4gICAgICAgIGRpcjogJ21vZGVscy9mcnVpdC9vcmFuZ2Uvb3JhbmdlJyxcclxuICAgICAgICBzY2FsZTogMS42NSwgaGVpZ2h0OiAwLjg3LCBoZWFkOiBuZXcgVmVjMygwLCAwLjc2LCAwLjU4KSwgc2hhZG93OiBbMC42MCwgMC4wMTAsIDAuNDZdLFxyXG4gICAgfSxcclxuICAgIHdhdGVybWVsb246IHtcclxuICAgICAgICBwYXRoOiAnbW9kZWxzL2ZydWl0L3dhdGVybWVsb24vd2F0ZXJtZWxvbi93YXRlcm1lbG9uJyxcclxuICAgICAgICBkaXI6ICdtb2RlbHMvZnJ1aXQvd2F0ZXJtZWxvbi93YXRlcm1lbG9uJyxcclxuICAgICAgICBzY2FsZTogMS42LCBoZWlnaHQ6IDAuOTksIGhlYWQ6IG5ldyBWZWMzKDAsIDAuNzcsIDAuNjMpLCBzaGFkb3c6IFswLjY2LCAwLjAxMCwgMC41Ml0sXHJcbiAgICB9LFxyXG4gICAgYmx1ZWJlcnJ5OiB7XHJcbiAgICAgICAgcGF0aDogJ21vZGVscy9mcnVpdC9ibHVlYmVycnkvYmx1ZWJlcnJ5L2JsdWViZXJyeScsXHJcbiAgICAgICAgZGlyOiAnbW9kZWxzL2ZydWl0L2JsdWViZXJyeS9ibHVlYmVycnknLFxyXG4gICAgICAgIHNjYWxlOiAxLjUsIGhlaWdodDogMC43MCwgaGVhZDogbmV3IFZlYzMoMCwgMC41NiwgMC40NyksIHNoYWRvdzogWzAuNDQsIDAuMDEwLCAwLjM0XSxcclxuICAgIH0sXHJcbiAgICBkcmFnb25mcnVpdDoge1xyXG4gICAgICAgIHBhdGg6ICdtb2RlbHMvZnJ1aXQvZHJhZ29uZnJ1aXQvZHJhZ29uZnJ1aXQvZHJhZ29uZnJ1aXQnLFxyXG4gICAgICAgIGRpcjogJ21vZGVscy9mcnVpdC9kcmFnb25mcnVpdC9kcmFnb25mcnVpdCcsXHJcbiAgICAgICAgc2NhbGU6IDEuNiwgaGVpZ2h0OiAwLjk1LCBoZWFkOiBuZXcgVmVjMygwLCAwLjc5LCAwLjUzKSwgc2hhZG93OiBbMC41MCwgMC4wMTAsIDAuNDBdLFxyXG4gICAgfSxcclxufVxyXG5jb25zdCBERUZBVUxUX0ZSVUlUID0gJ3N0cmF3YmVycnknXHJcbi8qKlxyXG4gKiDniannp43mp73kvY3mmKDlsITvvJptYWxsLXBldCDnmoQgc3BlY2llcyDmnprkuL7vvIhDQVQvRE9HL1JBQkJJVC9GT1gvUEFOREHvvInov4Hnp7vmiJDmsLTmnpznoIHkuYvliY3vvIxcclxuICog5oyJ5Zu65a6a6aG65bqP5oqKIDUg5Liq5Yqo54mp5qe95L2N5pig5bCE5YiwIDUg5Y+q5rC05p6c77yI5q+P5LiqIERCIOWuoOeJqeWQhOW+l+S4gOWPquaenO+8ie+8m1xyXG4gKiDmnprkuL7ov4Hnp7vokL3lnLDlkI7mnprkuL7lgLzmnKzouqvlsLHmmK/msLTmnpznoIHvvIzlkIzooajnm7Tmn6XjgIJVUkwgYD9zcGVjaWVzPW9yYW5nZWAg5Y+v5by65Yi25oyH5a6a6aKE6KeI44CCXHJcbiAqL1xyXG5jb25zdCBTUEVDSUVTX1NMT1Q6IFJlY29yZDxzdHJpbmcsIHN0cmluZz4gPSB7XHJcbiAgICBDQVQ6ICdzdHJhd2JlcnJ5JywgRE9HOiAnb3JhbmdlJywgUkFCQklUOiAnd2F0ZXJtZWxvbicsIEZPWDogJ2JsdWViZXJyeScsIFBBTkRBOiAnZHJhZ29uZnJ1aXQnLFxyXG4gICAgU1RSQVdCRVJSWTogJ3N0cmF3YmVycnknLCBPUkFOR0U6ICdvcmFuZ2UnLCBXQVRFUk1FTE9OOiAnd2F0ZXJtZWxvbicsXHJcbiAgICBCTFVFQkVSUlk6ICdibHVlYmVycnknLCBEUkFHT05GUlVJVDogJ2RyYWdvbmZydWl0JyxcclxufVxyXG5cclxuZnVuY3Rpb24gcmVzb2x2ZUZydWl0S2V5KHVybFNwZWNpZXM6IHN0cmluZyB8IG51bGwsIHBldFNwZWNpZXM6IHN0cmluZyB8IG51bGwpOiBzdHJpbmcge1xyXG4gICAgaWYgKHVybFNwZWNpZXMpIHtcclxuICAgICAgICBjb25zdCBrZXkgPSB1cmxTcGVjaWVzLnRvTG93ZXJDYXNlKClcclxuICAgICAgICBpZiAoRlJVSVRfU1BFQ1Nba2V5XSkge1xyXG4gICAgICAgICAgICByZXR1cm4ga2V5XHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG4gICAgaWYgKHBldFNwZWNpZXMpIHtcclxuICAgICAgICBjb25zdCBzbG90ID0gU1BFQ0lFU19TTE9UW3BldFNwZWNpZXMudG9VcHBlckNhc2UoKV1cclxuICAgICAgICBpZiAoc2xvdCkge1xyXG4gICAgICAgICAgICByZXR1cm4gc2xvdFxyXG4gICAgICAgIH1cclxuICAgIH1cclxuICAgIHJldHVybiBERUZBVUxUX0ZSVUlUXHJcbn1cclxuXHJcbi8qKlxyXG4gKiDnm7jmnLrmnLrkvY3jgIJcclxuICpcclxuICog5Li76KeG6KeS77ya6L275b6u5L+v6KeG55qEIDMvNCDop4bop5LvvIzohJrlupXokL3lnKjlnLDmnb/kuIrjgIHlpLTpobbnlZnlh7rlkbzlkLjnqbrpl7TvvJtcclxuICog5q+UIHY1IOWQjumAgOe6piAxMiUg5oqK5p+c5a2QL+ebhuagvS/nqp0v546p5YW35pS26L+b55S76Z2iIOKAlOKAlCDkuIDkuKpcIuS9j+edgOS6ulwi55qE56m66Ze06ZyA6KaB55Sf5rS755eV6L+544CCXHJcbiAqIOerluWxj+WPpue7meS4gOe7hO+8muerluWxj+WPr+inhuaoquWQkeiMg+WbtOeqhO+8jOayv+eUqOaoquWxj+acuuS9jeS8muiuqeinkuiJsuaoquWQkemhtui+ueOAglxyXG4gKi9cclxuLyoqXHJcbiAqIOebuOacuuacuuS9jeOAglxyXG4gKlxyXG4gKiB2Oe+8muaIv+mXtOWGheWuuem9kOWkhyArIOmhtemdouaLieWuve+8iDE2OjEwIOiInuWPsOabtOWuve+8ie+8jOacuuS9jeWGjeaLiei/nOWIsOiDveaUtui/m1wi5a6M5pW055qE5a62XCLvvJpcclxuICog5ZCO5aKZ5LuO6Lii6ISa57q/5Yiw6aG257q/44CB5Lik5L6n5a625YW344CB5Zyw5q+v5YWo6LKM77yb5Lik5L6n6L+R5pmv5aKZ5L2T6Ieq54S25b2i5oiQ55S75qGG44CCXHJcbiAqIOerluWxj+WPpue7meS4gOe7hO+8muerluWxj+aoquWQkeeqhO+8jOWQjOi3neemu+S4i+WGjeaKrOmrmOS4gOeCueOAglxyXG4gKi9cclxuY29uc3QgQ0FNRVJBX1NIT1QgPSB7XHJcbiAgICByb29tOiB7IHBvczogWzEuMTUsIDIuMjUsIDcuMF0sIHRhcmdldDogWzAsIDEuMDUsIC0wLjJdIH0sXHJcbiAgICAvLyDnq5blsY/mi4nov5zvvJrmiYvmnLogd2ViLXZpZXcg56uW5bGP5Y+v6KeG5qiq5a6956qE77yMej0xMC41IOaJjeiDveaUtui/m+WujOaVtOeahOWutu+8iOWQjuWimSvkuKTkvqflrrblhbcr5Zyw5q+v77yJXHJcbiAgICBwb3J0cmFpdDogeyBwb3M6IFsyLjAsIDIuNiwgMTAuNV0sIHRhcmdldDogWzAsIDEuMTUsIC0wLjJdIH0sXHJcbiAgICBmcm9udDogeyBwb3M6IFswLCAxLjYsIDYuMl0sIHRhcmdldDogWzAsIDEuMDUsIC0wLjFdIH0sXHJcbiAgICBxMzQ6IHsgcG9zOiBbLTMuMCwgMS45LCA1LjZdLCB0YXJnZXQ6IFswLCAxLjAsIDAuMV0gfSxcclxufSBhcyBjb25zdFxyXG5cclxuLyoqIOe6r+iJsuiDjOaZr++8iD9wbGFpbj0xIOmqjOaUtuaooeW8j++8muWOu+aOieaIv+mXtO+8jOWPqueVmeinkuiJsuiHquivgei9ruW7k+S4juadkOi0qO+8iSAqL1xyXG5jb25zdCBQTEFJTl9CRyA9IG5ldyBDb2xvcigweENGLCAweEM5LCAweEQ2LCAyNTUpXHJcblxyXG5AY2NjbGFzcygnUGV0R2FtZVJvb3QnKVxyXG5leHBvcnQgY2xhc3MgUGV0R2FtZVJvb3QgZXh0ZW5kcyBDb21wb25lbnQge1xyXG5cclxuICAgIHByaXZhdGUgcmVhZG9ubHkgYnJpZGdlID0gbmV3IFBldEdhbWVCcmlkZ2UoKVxyXG4gICAgcHJpdmF0ZSByZWFkb25seSBjY1J1bnRpbWUgPSAoZ2xvYmFsVGhpcyBhcyB1bmtub3duIGFzIHsgY2M6IG5ldmVyIH0pLmNjXHJcblxyXG4gICAgcHJpdmF0ZSBraXQ6IFBldEJ1aWxkZXJLaXQgfCBudWxsID0gbnVsbFxyXG4gICAgcHJpdmF0ZSB3b3JsZDNkOiBOb2RlIHwgbnVsbCA9IG51bGxcclxuICAgIHByaXZhdGUgcm9vbTogUm9vbVJlZnMgfCBudWxsID0gbnVsbFxyXG4gICAgcHJpdmF0ZSBjYW1lcmEzZDogQ2FtZXJhIHwgbnVsbCA9IG51bGxcclxuICAgIHByaXZhdGUgcm9vdFRyYW5zZm9ybTogVUlUcmFuc2Zvcm0gfCBudWxsID0gbnVsbFxyXG5cclxuICAgIHByaXZhdGUgZWZmZWN0czogUGV0RWZmZWN0cyB8IG51bGwgPSBudWxsXHJcblxyXG4gICAgLyoqIOWuoOeJqeiKgueCue+8iOaooeWei+W8guatpeWKoOi9veWujOaIkOWJjeS4uiBudWxs77yJICovXHJcbiAgICBwcml2YXRlIHBldE5vZGU6IE5vZGUgfCBudWxsID0gbnVsbFxyXG4gICAgcHJpdmF0ZSB0YXBDb29sZG93bkF0ID0gMFxyXG4gICAgcHJpdmF0ZSB0YXBDb21ibyA9IDBcclxuICAgIHByaXZhdGUgdGFwQ29tYm9BdCA9IDBcclxuICAgIC8qKiDpmLLliLfvvJrlm7rlrpogNCDlsI/ml7blkajmnJ/vvIjoh6rnhLbml7bpl7Tlr7npvZAgMC80LzgvMTYvMjAg54K56L6555WM77yJ77yM5q+P5ZGo5pyf5peg6K665oCO5LmI5pG45Y+q57uT566X5YmNIDMg5qyhICovXHJcbiAgICBwcml2YXRlIHJld2FyZFBlcmlvZEtleSA9IC0xXHJcbiAgICBwcml2YXRlIHJld2FyZFVzZWRJblBlcmlvZCA9IDBcclxuICAgIHByaXZhdGUgc3RhdGljIHJlYWRvbmx5IFJFV0FSRF9QRVJJT0RfTVMgPSA0ICogNjAgKiA2MCAqIDEwMDBcclxuICAgIHByaXZhdGUgc3RhdGljIHJlYWRvbmx5IFJFV0FSRF9NQVhfUEVSX1BFUklPRCA9IDNcclxuICAgIHByaXZhdGUgcGV0QW5pbTogU2tlbGV0YWxBbmltYXRpb24gfCBBbmltYXRpb24gfCBudWxsID0gbnVsbFxyXG4gICAgLyoqIOacgOi/keS4gOasoeS4i+WPkeeahOeKtuaAge+8muaooeWei+WKoOi9veaYr+W8guatpeeahO+8jOWIsOS9jeWQjuimgeeUqOWug+ihpeaSreato+ehrueahOWKqOeUuyAqL1xyXG4gICAgcHJpdmF0ZSBwZW5kaW5nUGV0OiBQZXREaXNwbGF5U3RhdGUgfCBudWxsID0gbnVsbFxyXG4gICAgLyoqIDAuMzV+MS4w77ya5pWw5YC85L2O5pe25oqK5b6F5py65Yqo5L2c5pS+5oWi77yI6JSr44CB5rKh57K+56We77yJ77yM5piv5YWo54mH5ZSv5LiA55qEXCLnirbmgIHihpLliqjnlLtcIuaYoOWwhCAqL1xyXG4gICAgcHJpdmF0ZSBzcGVlZFNjYWxlID0gMVxyXG4gICAgcHJpdmF0ZSBibGlua1RpbWVyID0gMy41XHJcbiAgICBwcml2YXRlIGJsaW5rUmVhZHkgPSBmYWxzZVxyXG5cclxuICAgIHByaXZhdGUgcGxhaW4gPSBmYWxzZVxyXG4gICAgcHJpdmF0ZSBzaG90ID0gJ3Jvb20nXHJcbiAgICBwcml2YXRlIHByb2JpbmcgPSBmYWxzZVxyXG4gICAgLyoqID9yYXdtYXQ9Me+8mui3s+i/hyBwZXQtdG9vbiDmnZDotKjmm7/mjaLvvIjor4rmlq3okpnnmq4v5p2Q6LSo6Zeu6aKY55So77yM5L+d55WZIGdsVEYg5Y6f5p2Q6LSo77yJICovXHJcbiAgICBwcml2YXRlIHJhd01hdCA9IGZhbHNlXHJcbiAgICBwcml2YXRlIHRvb25Bc3NldDogRWZmZWN0QXNzZXQgfCBudWxsID0gbnVsbFxyXG5cclxuICAgIC8qKiDlvZPliY3msLTmnpzop4TmoLzvvIhsb2FkUGV0IOaXtuaMiSBVUkwg5Y+C5pWwIC8g5a6/5Li754mp56eN6Kej5p6Q77yJICovXHJcbiAgICBwcml2YXRlIGZydWl0OiBGcnVpdFNwZWMgPSBGUlVJVF9TUEVDU1tERUZBVUxUX0ZSVUlUXVxyXG4gICAgcHJpdmF0ZSB1cmxTcGVjaWVzOiBzdHJpbmcgfCBudWxsID0gbnVsbFxyXG5cclxuICAgIHByaXZhdGUgdGltZSA9IDBcclxuICAgIHByaXZhdGUgcm9vbVRpbWUgPSAwXHJcblxyXG4gICAgc3RhcnQoKTogdm9pZCB7XHJcbiAgICAgICAgLy8g5YWz6Zet5byV5pOO6LCD6K+V57uf6K6h6Z2i5p2/77yIQ0xJIGRlYnVnIOaehOW7uum7mOiupOW8gOWQr++8m+ato+W8j+inguaEn+S4jemcgOimgSBGUFMvRHJhdyBjYWxsIOaCrOa1ruahhu+8iVxyXG4gICAgICAgIGNvbnN0IGVuZ2luZSA9IHRoaXMuY2NSdW50aW1lIGFzIHVua25vd24gYXMgeyBkaXJlY3Rvcj86IHsgc2V0RGlzcGxheVN0YXRzPzogKHY6IGJvb2xlYW4pID0+IHZvaWQgfSB9XHJcbiAgICAgICAgZW5naW5lLmRpcmVjdG9yPy5zZXREaXNwbGF5U3RhdHM/LihmYWxzZSlcclxuICAgICAgICBjb25zdCBwYXJhbXMgPSBuZXcgVVJMU2VhcmNoUGFyYW1zKHdpbmRvdy5sb2NhdGlvbi5zZWFyY2gpXHJcbiAgICAgICAgdGhpcy5wbGFpbiA9IHBhcmFtcy5nZXQoJ3BsYWluJykgPT09ICcxJ1xyXG4gICAgICAgIHRoaXMucHJvYmluZyA9IHBhcmFtcy5nZXQoJ3Byb2JlJykgPT09ICcxJ1xyXG4gICAgICAgIHRoaXMuc2hvdCA9IHBhcmFtcy5nZXQoJ3Nob3QnKVxyXG4gICAgICAgICAgICB8fCAod2luZG93LmlubmVySGVpZ2h0ID4gd2luZG93LmlubmVyV2lkdGggPyAncG9ydHJhaXQnIDogJ3Jvb20nKVxyXG5cclxuICAgICAgICB0aGlzLnJvb3RUcmFuc2Zvcm0gPSB0aGlzLm5vZGUuZ2V0Q29tcG9uZW50KFVJVHJhbnNmb3JtKVxyXG4gICAgICAgIHRoaXMua2l0ID0gbmV3IFBldEJ1aWxkZXJLaXQodGhpcy5jY1J1bnRpbWUpXHJcbiAgICAgICAgdGhpcy5yYXdNYXQgPSBwYXJhbXMuZ2V0KCdyYXdtYXQnKSA9PT0gJzEnXHJcbiAgICAgICAgdGhpcy51cmxTcGVjaWVzID0gcGFyYW1zLmdldCgnc3BlY2llcycpXHJcblxyXG4gICAgICAgIC8vIOiHquWumuS5ieadkOi0qO+8iHBldC10b29u77yJ5b+F6aG75YWI5Yqg6L2977yaRWZmZWN0QXNzZXQuZ2V0IOWPquiDveafpeWIsOW3suWKoOi9veeahOi1hOS6p++8jFxyXG4gICAgICAgIC8vIOiLpeWcqOWKoOi9veWujOaIkOWJjeaehOW7uu+8jOaJgOaciemDqOS7tuS8mumdmem7mOmAgOWMluaIkOW8leaTjuWGhee9ruadkOi0qOOAglxyXG4gICAgICAgIHJlc291cmNlcy5sb2FkKCdlZmZlY3RzL3BldC10b29uJywgRWZmZWN0QXNzZXQsIChlcnJvciwgYXNzZXQpID0+IHtcclxuICAgICAgICAgICAgY29uc29sZS5sb2coYFtwZXQtcHJvYmVdIHJlc291cmNlcy5sb2FkIHBldC10b29uOiBlcnI9JHtlcnJvciA/IFN0cmluZyhlcnJvcikgOiAnbm9uZSd9IGAgK1xyXG4gICAgICAgICAgICAgICAgYGFzc2V0PSR7YXNzZXQgPyBhc3NldC5uYW1lIDogJ251bGwnfWApXHJcbiAgICAgICAgICAgIGlmICghZXJyb3IgJiYgYXNzZXQpIHtcclxuICAgICAgICAgICAgICAgIHRoaXMudG9vbkFzc2V0ID0gYXNzZXRcclxuICAgICAgICAgICAgICAgIHRoaXMua2l0IS5zZXRUb29uRWZmZWN0KGFzc2V0KVxyXG4gICAgICAgICAgICAgICAgY29uc29sZS5sb2coYFtwZXQtcHJvYmVdIHRvb24gcmVhZHk6IGlzVG9vbj0ke3RoaXMua2l0IS5pc1Rvb259YClcclxuICAgICAgICAgICAgfSBlbHNlIHtcclxuICAgICAgICAgICAgICAgIGNvbnNvbGUud2FybignW3BldC1nYW1lXSBwZXQtdG9vbiDliqDovb3lpLHotKXvvIzmnZDotKjlm57pgIDkuLrlhoXnva7mnZDotKgnLCBlcnJvcilcclxuICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICB0aGlzLmJ1aWxkU2NlbmUocGFyYW1zKVxyXG4gICAgICAgIH0pXHJcbiAgICB9XHJcblxyXG4gICAgcHJpdmF0ZSBidWlsZFNjZW5lKHBhcmFtczogVVJMU2VhcmNoUGFyYW1zKTogdm9pZCB7XHJcbiAgICAgICAgdGhpcy5idWlsZFdvcmxkKClcclxuICAgICAgICB0aGlzLmJ1aWxkT3ZlcmxheSgpXHJcbiAgICAgICAgdGhpcy5iaW5kQnJpZGdlKClcclxuICAgICAgICBpZiAoIXRoaXMucGxhaW4pIHtcclxuICAgICAgICAgICAgdGhpcy5idWlsZFBldFNoYWRvdygpXHJcbiAgICAgICAgICAgIHRoaXMubG9hZFBldCgpXHJcbiAgICAgICAgfVxyXG5cclxuICAgICAgICBpZiAodGhpcy5wbGFpbikge1xyXG4gICAgICAgICAgICB0aGlzLm5vZGUuYWN0aXZlID0gZmFsc2VcclxuICAgICAgICB9XHJcbiAgICAgICAgaWYgKHBhcmFtcy5nZXQoJ3Byb2JlJykgPT09ICcxJykge1xyXG4gICAgICAgICAgICB0aGlzLnByb2JlRnJhbWluZygpXHJcbiAgICAgICAgfVxyXG4gICAgICAgIGlmIChwYXJhbXMuZ2V0KCdwcm9iZU1hdCcpID09PSAnMScpIHtcclxuICAgICAgICAgICAgdGhpcy5wcm9iZU1hdGVyaWFscygpXHJcbiAgICAgICAgfVxyXG5cclxuICAgICAgICB0aGlzLmJyaWRnZS5zZW5kKHsgc291cmNlOiAncGV0LWdhbWUnLCB0eXBlOiAncmVhZHknIH0pXHJcbiAgICB9XHJcblxyXG4gICAgLy8gLS0tLS0tLS0tLS0tLS0tLSDlrqDnianvvIjlpJbpg6jnu5HlrprmqKHlnovvvIkgLS0tLS0tLS0tLS0tLS0tLVxyXG5cclxuICAgIC8qKlxyXG4gICAgICog5Yqg6L295bm25a6e5L6L5YyW5a6g54mp5qih5Z6L44CCXHJcbiAgICAgKlxyXG4gICAgICog5rOo5oSP6L+Z5pivKirlvILmraUqKueahO+8mmByZWFkeWAg5Lya5YWI5Y+R57uZ5a6/5Li777yM5qih5Z6L5Y+v6IO956iN5ZCO5omN5Yiw5L2N44CCXHJcbiAgICAgKiDlm6DmraTmnI3liqHnq6/kuIvlj5HnmoTnirbmgIHlhYjlrZjov5sgYHBlbmRpbmdQZXRg77yM5qih5Z6L5bCx57uq5ZCO56uL5Yi76KGl5pKt5q2j56Gu5Yqo55S7IOKAlOKAlFxyXG4gICAgICog5ZCm5YiZ5Lya5Ye6546wXCLmqKHlnovliqDovb3lh7rmnaXkuobkvYbnq5nnnYDkuI3liqhcIuaIllwi5L2O54q25oCB6L+Y6Lmm5b6X5b6I5qyiXCLjgIJcclxuICAgICAqL1xyXG4gICAgcHJpdmF0ZSBsb2FkUGV0KCk6IHZvaWQge1xyXG4gICAgICAgIC8vIOaenOenjeino+aekO+8mlVSTCA/c3BlY2llcz3vvIjpooTop4jlvLrliLbvvInihpIg5a6/5Li75LiL5Y+R55qEIHNwZWNpZXMg5qe95L2NIOKGkiDpu5jorqTojYnojpNcclxuICAgICAgICB0aGlzLmZydWl0ID0gRlJVSVRfU1BFQ1NbcmVzb2x2ZUZydWl0S2V5KHRoaXMudXJsU3BlY2llcywgdGhpcy5wZW5kaW5nUGV0Py5zcGVjaWVzID8/IG51bGwpXVxyXG4gICAgICAgIGNvbnN0IHNwZWMgPSB0aGlzLmZydWl0XHJcbiAgICAgICAgLy8g54K55Ye75a6g54mp5pys5L2TIOKGkiDlvIDlv4PliqjnlLsgKyDpgJrnn6Xlrr/kuLvkupLliqjvvIhCMDYg5LqS5Yqo5YWl5Y+j55qEIDNEIOebtOinpuW9ouW8j++8iVxyXG4gICAgICAgIGlucHV0Lm9uKElucHV0LkV2ZW50VHlwZS5UT1VDSF9FTkQsIHRoaXMub25TdGFnZVRhcCwgdGhpcylcclxuXHJcbiAgICAgICAgcmVzb3VyY2VzLmxvYWQoc3BlYy5wYXRoLCBQcmVmYWIsIChlcnJvciwgcHJlZmFiKSA9PiB7XHJcbiAgICAgICAgICAgIGlmIChlcnJvciB8fCAhcHJlZmFiKSB7XHJcbiAgICAgICAgICAgICAgICBjb25zb2xlLndhcm4oJ1twZXQtZ2FtZV0g5a6g54mp5qih5Z6L5Yqg6L295aSx6LSl77yM5Zy65pmv5L+d5oyB5peg6KeS6Imy54q25oCBJywgZXJyb3IpXHJcbiAgICAgICAgICAgICAgICByZXR1cm5cclxuICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICBjb25zdCBub2RlID0gaW5zdGFudGlhdGUocHJlZmFiKVxyXG4gICAgICAgICAgICBub2RlLm5hbWUgPSAnUGV0J1xyXG4gICAgICAgICAgICBub2RlLnNldFNjYWxlKHNwZWMuc2NhbGUsIHNwZWMuc2NhbGUsIHNwZWMuc2NhbGUpXHJcbiAgICAgICAgICAgIG5vZGUuc2V0UG9zaXRpb24oUEVUX1BPUylcclxuICAgICAgICAgICAgdGhpcy53b3JsZDNkIS5hZGRDaGlsZChub2RlKVxyXG4gICAgICAgICAgICB0aGlzLnBldE5vZGUgPSBub2RlXHJcblxyXG4gICAgICAgICAgICBpZiAodGhpcy5wcm9iaW5nKSB7XHJcbiAgICAgICAgICAgICAgICB0aGlzLmR1bXBUcmVlKG5vZGUsIDApXHJcbiAgICAgICAgICAgIH1cclxuICAgICAgICAgICAgaWYgKCF0aGlzLnJhd01hdCkge1xyXG4gICAgICAgICAgICAgICAgdGhpcy5jb25maWd1cmVQZXRNYXRlcmlhbHMobm9kZSlcclxuICAgICAgICAgICAgfVxyXG5cclxuICAgICAgICAgICAgLy8g6YCQ5p2h5Yqg6L295Ymq6L6R44CCXHJcbiAgICAgICAgICAgIC8vIOKaoO+4jyDkuI3og73nlKggYHJlc291cmNlcy5sb2FkKFvlpJrmnaHot6/lvoRdLCAuLi4pYCDigJTigJQg5a6D5pivKirlhajmnInmiJblhajml6AqKu+8mlxyXG4gICAgICAgICAgICAvLyDlj6ropoHmnInkuIDmnaHot6/lvoTkuI3lrZjlnKjvvIzmlbTmibnpg73lpLHotKXvvIznu5PmnpzmlbTlj6rlrqDnianpnZnmraLkuI3liqjjgILnjKvniYjlt7Llrp7mtYvouKnliLDjgIJcclxuICAgICAgICAgICAgY29uc3QgY2xpcFBhdGhzID0gUEVUX0NMSVBTLm1hcChuYW1lID0+IGAke3NwZWMuZGlyfS8ke25hbWV9YClcclxuICAgICAgICAgICAgY29uc3QgbG9hZGVkOiBBbmltYXRpb25DbGlwW10gPSBbXVxyXG4gICAgICAgICAgICBsZXQgcmVtYWluaW5nID0gY2xpcFBhdGhzLmxlbmd0aFxyXG4gICAgICAgICAgICBjb25zdCBzZXR0bGUgPSAoKTogdm9pZCA9PiB7XHJcbiAgICAgICAgICAgICAgICByZW1haW5pbmcgLT0gMVxyXG4gICAgICAgICAgICAgICAgaWYgKHJlbWFpbmluZyA+IDApIHtcclxuICAgICAgICAgICAgICAgICAgICByZXR1cm5cclxuICAgICAgICAgICAgICAgIH1cclxuICAgICAgICAgICAgICAgIHRoaXMuYXR0YWNoQ2xpcHMobm9kZSwgbG9hZGVkKVxyXG4gICAgICAgICAgICB9XHJcbiAgICAgICAgICAgIGZvciAoY29uc3QgcGF0aCBvZiBjbGlwUGF0aHMpIHtcclxuICAgICAgICAgICAgICAgIHJlc291cmNlcy5sb2FkKHBhdGgsIEFuaW1hdGlvbkNsaXAsIChjbGlwRXJyb3IsIGNsaXApID0+IHtcclxuICAgICAgICAgICAgICAgICAgICBpZiAoY2xpcEVycm9yIHx8ICFjbGlwKSB7XHJcbiAgICAgICAgICAgICAgICAgICAgICAgIGNvbnNvbGUud2FybihgW3BldC1nYW1lXSDliarovpHnvLrlpLHvvIjot7Pov4fvvIk6ICR7cGF0aH1gKVxyXG4gICAgICAgICAgICAgICAgICAgIH0gZWxzZSB7XHJcbiAgICAgICAgICAgICAgICAgICAgICAgIGxvYWRlZC5wdXNoKGNsaXApXHJcbiAgICAgICAgICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICAgICAgICAgIHNldHRsZSgpXHJcbiAgICAgICAgICAgICAgICB9KVxyXG4gICAgICAgICAgICB9XHJcbiAgICAgICAgfSlcclxuICAgIH1cclxuXHJcbiAgICAvKipcclxuICAgICAqIOaKiuWJqui+keaMguWIsOWuoOeJqeagueiKgueCueS4iuW5tui1t+aSreOAglxyXG4gICAgICpcclxuICAgICAqIOe7hOS7tuW/hemhu+aMguWcqCBgUGV0YO+8iHByZWZhYiDmoLnvvInogIzkuI3mmK8gYEZydWl0UmlnYO+8muWJqui+kemHjOeahOi9qOmBk+i3r+W+hOaYr1xyXG4gICAgICogYEZydWl0UmlnL0JvZHkvLi4uYO+8jOS7juagueino+aekOaJjeWMuemFje+8m+aMguWIsCBGcnVpdFJpZyDkuIrkvJrmlbTkvZPlsJHkuIDlsYLvvIzlhajpg6jnu5HkuI3kuIrjgIJcclxuICAgICAqL1xyXG4gICAgcHJpdmF0ZSBhdHRhY2hDbGlwcyhub2RlOiBOb2RlLCBsaXN0OiBBbmltYXRpb25DbGlwW10pOiB2b2lkIHtcclxuICAgICAgICBpZiAoIWxpc3QubGVuZ3RoKSB7XHJcbiAgICAgICAgICAgIGNvbnNvbGUud2FybignW3BldC1nYW1lXSDmsqHmnInku7vkvZXlj6/nlKjliarovpHvvIzlrqDniankv53mjIHpnZnmraInKVxyXG4gICAgICAgICAgICByZXR1cm5cclxuICAgICAgICB9XHJcbiAgICAgICAgY29uc3QgYW5pbSA9IG5vZGUuYWRkQ29tcG9uZW50KFNrZWxldGFsQW5pbWF0aW9uKVxyXG4gICAgICAgIC8vIOeUqOWunuaXtumqqOmqvOWKqOeUu++8muWvvOWFpeeahCBnbFRGIOWJqui+keayoeacieeDmOeEmei0tOWbvuWKqOeUu++8jOW8gOedgOS8mui1sOepuuWIhuaUr1xyXG4gICAgICAgIGFuaW0udXNlQmFrZWRBbmltYXRpb24gPSBmYWxzZVxyXG4gICAgICAgIGFuaW0uY2xpcHMgPSBsaXN0XHJcbiAgICAgICAgY29uc3QgaWRsZSA9IGxpc3QuZmluZChjID0+IGMubmFtZSA9PT0gJ0lkbGUnKVxyXG4gICAgICAgIGlmIChpZGxlKSB7XHJcbiAgICAgICAgICAgIGFuaW0uZGVmYXVsdENsaXAgPSBpZGxlXHJcbiAgICAgICAgfVxyXG4gICAgICAgIGNvbnNvbGUubG9nKGBbcGV0LXByb2JlXSBwZXQgY2xpcHM9WyR7bGlzdC5tYXAoYyA9PiBjLm5hbWUpLmpvaW4oJywgJyl9XSBgICtcclxuICAgICAgICAgICAgYGRlZmF1bHQ9JHtpZGxlID8gaWRsZS5uYW1lIDogJ251bGwnfWApXHJcbiAgICAgICAgdGhpcy5ibGlua1JlYWR5ID0gbGlzdC5zb21lKGMgPT4gYy5uYW1lID09PSAnQmxpbmsnKVxyXG4gICAgICAgIHRoaXMucGV0QW5pbSA9IGFuaW1cclxuICAgICAgICB0aGlzLnBsYXlDbGlwKCdJZGxlJywgdHJ1ZSlcclxuICAgICAgICB0aGlzLmFwcGx5U3RhdHNUb0FuaW1hdGlvbigpXHJcbiAgICB9XHJcblxyXG4gICAgLyoqIOaJk+WNsOWuoOeJqeiKgueCueagkeS4juWQhOiKgueCuee7hOS7tu+8iD9wcm9iZT0x77yJ4oCU4oCUIGdsVEYg5a+85YWl55qE5bGC57qn5Y+q6IO96Z2g5a6e5rWL77yM5LiN6IO954ycICovXHJcbiAgICBwcml2YXRlIGR1bXBUcmVlKG5vZGU6IE5vZGUsIGRlcHRoOiBudW1iZXIpOiB2b2lkIHtcclxuICAgICAgICBjb25zdCBuYW1lczogc3RyaW5nW10gPSBbXVxyXG4gICAgICAgIGZvciAoY29uc3QgYyBvZiBub2RlLmNvbXBvbmVudHMpIHtcclxuICAgICAgICAgICAgbGV0IGV4dHJhID0gJydcclxuICAgICAgICAgICAgY29uc3QgbW9kZWwgPSBjIGFzIHVua25vd24gYXMge1xyXG4gICAgICAgICAgICAgICAgbWF0ZXJpYWw/OiB7XHJcbiAgICAgICAgICAgICAgICAgICAgZWZmZWN0QXNzZXQ/OiB7IG5hbWU/OiBzdHJpbmcgfVxyXG4gICAgICAgICAgICAgICAgICAgIGdldFByb3BlcnR5PzogKGs6IHN0cmluZykgPT4gdW5rbm93blxyXG4gICAgICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICAgICAgc2tpbm5pbmdSb290PzogdW5rbm93blxyXG4gICAgICAgICAgICB9XHJcbiAgICAgICAgICAgIGlmIChtb2RlbC5tYXRlcmlhbCAmJiBtb2RlbC5tYXRlcmlhbC5lZmZlY3RBc3NldCkge1xyXG4gICAgICAgICAgICAgICAgZXh0cmEgPSBgIGVmZmVjdD0ke21vZGVsLm1hdGVyaWFsLmVmZmVjdEFzc2V0Lm5hbWV9YFxyXG4gICAgICAgICAgICAgICAgY29uc3QgZ2V0ID0gbW9kZWwubWF0ZXJpYWwuZ2V0UHJvcGVydHlcclxuICAgICAgICAgICAgICAgIGlmIChnZXQpIHtcclxuICAgICAgICAgICAgICAgICAgICBjb25zdCB0ZXggPSBnZXQuY2FsbChtb2RlbC5tYXRlcmlhbCwgJ21haW5UZXh0dXJlJylcclxuICAgICAgICAgICAgICAgICAgICBjb25zdCBhbGJlZG8gPSBnZXQuY2FsbChtb2RlbC5tYXRlcmlhbCwgJ2FsYmVkbycpXHJcbiAgICAgICAgICAgICAgICAgICAgZXh0cmEgKz0gYCB0ZXg9JHt0ZXggPyAodGV4IGFzIHsgbmFtZT86IHN0cmluZyB9KS5uYW1lIDogJ251bGwnfWBcclxuICAgICAgICAgICAgICAgICAgICBleHRyYSArPSBgIGFsYmVkbz0ke2FsYmVkbyA/IEpTT04uc3RyaW5naWZ5KGFsYmVkbykgOiAnbnVsbCd9YFxyXG4gICAgICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICB9XHJcbiAgICAgICAgICAgIGlmIChtb2RlbC5za2lubmluZ1Jvb3QpIHtcclxuICAgICAgICAgICAgICAgIGV4dHJhICs9ICcgW3NraW5uZWRdJ1xyXG4gICAgICAgICAgICB9XHJcbiAgICAgICAgICAgIG5hbWVzLnB1c2goYy5jb25zdHJ1Y3Rvci5uYW1lICsgZXh0cmEpXHJcbiAgICAgICAgfVxyXG4gICAgICAgIGNvbnNvbGUubG9nKGBbcGV0LXByb2JlXSAkeycgICcucmVwZWF0KGRlcHRoKX0ke25vZGUubmFtZX0gbGF5ZXI9JHtub2RlLmxheWVyfSBgICtcclxuICAgICAgICAgICAgYHdwPSR7bm9kZS53b3JsZFBvc2l0aW9uLnRvU3RyaW5nKCl9IHM9JHtub2RlLnNjYWxlLnRvU3RyaW5nKCl9IFske25hbWVzLmpvaW4oJysnKSB8fCAnLSd9XWApXHJcbiAgICAgICAgZm9yIChjb25zdCBjaGlsZCBvZiBub2RlLmNoaWxkcmVuKSB7XHJcbiAgICAgICAgICAgIHRoaXMuZHVtcFRyZWUoY2hpbGQsIGRlcHRoICsgMSlcclxuICAgICAgICB9XHJcbiAgICB9XHJcblxyXG4gICAgLyoqXHJcbiAgICAgKiDmiorlrqDnianmjaLmiJAqKuWSjOaIv+mXtOWQjOS4gOWllyoqIHBldC10b29uIOadkOi0qO+8iOminOiJsuWQhOiHquS/neeVme+8ieOAglxyXG4gICAgICpcclxuICAgICAqIOS4uuS7gOS5iOS4jeayv+eUqCBnbFRGIOWvvOWFpeeahCBgYnVpbHRpbi1zdGFuZGFyZGDvvJrlrp7mtYvlroPlnKjlnLrmma/ph4zmuLLmn5PmiJDmg6jnmb3kuIDniYfjgIHmr6vml6DkvZPnp69cclxuICAgICAqIO+8iOW8leaTjiBQQlIg5YWJ54Wn5Zyo6L+Z5LiqIGhlYWRsZXNzIOeuoee6v+S4i+ayoeacieaMiemihOacn+WPguS4juiuoeeul++8jOeMq+eJiOW3sumAkOS4gOaOkuafpei/h++8ieOAglxyXG4gICAgICog6K6p5a6g54mp5ZKM5oi/6Ze05YWx55So5ZCM5LiA5Liq6Ieq56CU552A6Imy5ZmoIOKAlOKAlCDlhYnnhafor63oqIDkuIDoh7TjgIHop4bop4nlrozlhajnu5/kuIDvvIzogIzkuJTlrozlhajlj6/mjqfjgIJcclxuICAgICAqXHJcbiAgICAgKiDmr4/lnZfnvZHmoLzkv53nlZnoh6rlt7HnmoQgR0xCIOWGheW1jCBhbGJlZG/vvIjojYnojpPkvZPooag956eN57G96LS05Zu+77yM6JC854mHL+ecvC/lm7Tlt74957qv6Imy5bCP6LS05Zu+77yJ77yMXHJcbiAgICAgKiDlj6rmjaLnnYDoibLlmajkuI3mjaLpopzoibLjgILmmpfpg6joibIv6L2u5buT6Imy5oyJ6I2J6I6T55qE546r55Gw57qi6YeN5paw6LCD6L+H44CCXHJcbiAgICAgKlxyXG4gICAgICog6JKZ55qu77yacGV0LXRvb24g55qE6aG254K5552A6Imy5Zmo6LWwIGBDQ1ZlcnRJbnB1dChJbilg77yM5byV5pOO5oyJ5qih5Z6L55qE6JKZ55qu5L+h5oGv6Ieq5Yqo5rOo5YWlXHJcbiAgICAgKiBgQ0NfVVNFX1NLSU5OSU5HYO+8jOaXoOmcgOaJi+W3peWkhOeQhuOAglxyXG4gICAgICovXHJcbiAgICBwcml2YXRlIGNvbmZpZ3VyZVBldE1hdGVyaWFscyhub2RlOiBOb2RlKTogdm9pZCB7XHJcbiAgICAgICAgY29uc3QgdmlzaXQgPSAobjogTm9kZSk6IHZvaWQgPT4ge1xyXG4gICAgICAgICAgICBmb3IgKGNvbnN0IGNvbXAgb2Ygbi5jb21wb25lbnRzKSB7XHJcbiAgICAgICAgICAgICAgICBjb25zdCByZW5kZXJlciA9IGNvbXAgYXMgdW5rbm93biBhcyB7XHJcbiAgICAgICAgICAgICAgICAgICAgbWF0ZXJpYWw/OiBNYXRlcmlhbFxyXG4gICAgICAgICAgICAgICAgICAgIHNldE1hdGVyaWFsPzogKG1hdDogTWF0ZXJpYWwsIGluZGV4PzogbnVtYmVyKSA9PiB2b2lkXHJcbiAgICAgICAgICAgICAgICB9XHJcbiAgICAgICAgICAgICAgICBjb25zdCBzcmMgPSByZW5kZXJlci5tYXRlcmlhbFxyXG4gICAgICAgICAgICAgICAgaWYgKCFzcmMpIHtcclxuICAgICAgICAgICAgICAgICAgICBjb250aW51ZVxyXG4gICAgICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICAgICAgY29uc3QgZW1iZWRkZWQgPSBzcmMuZ2V0UHJvcGVydHkoJ21haW5UZXh0dXJlJykgYXMgVGV4dHVyZTJEIHwgbnVsbFxyXG4gICAgICAgICAgICAgICAgY29uc3QgdG9vbiA9IG5ldyBNYXRlcmlhbCgpXHJcbiAgICAgICAgICAgICAgICB0cnkge1xyXG4gICAgICAgICAgICAgICAgICAgIHRvb24uaW5pdGlhbGl6ZSh7IGVmZmVjdEFzc2V0OiB0aGlzLnRvb25Bc3NldCEsIHRlY2huaXF1ZTogMCB9KVxyXG4gICAgICAgICAgICAgICAgICAgIHRvb24uc2V0UHJvcGVydHkoJ21haW5Db2xvcicsIG5ldyBDb2xvcigyNTUsIDI1NSwgMjU1LCAyNTUpKVxyXG4gICAgICAgICAgICAgICAgICAgIGlmIChlbWJlZGRlZCkge1xyXG4gICAgICAgICAgICAgICAgICAgICAgICB0b29uLnNldFByb3BlcnR5KCdtYWluVGV4dHVyZScsIGVtYmVkZGVkKVxyXG4gICAgICAgICAgICAgICAgICAgIH1cclxuICAgICAgICAgICAgICAgICAgICAvLyDmmpfpg6gqKua3seeOq+eRsOeBsCoq77ya6I2J6I6T6aWx5ZKM5bqm6auY77yM5pqX6YOo5YGP5Ya35Lya5Y+R54Gw44CB5YGP5pqW5Lya5Y+R5qmZ77yMXHJcbiAgICAgICAgICAgICAgICAgICAgLy8g55So5bim546r55Gw5YC+5ZCR55qE5pqW54Gw5L+d5L2PXCLnuqLogIzkuI3nhKZcIu+8iOeMq+eJiOeahOaalueBsCAxNjYsMTQ4LDEzMCDlnKjov5nph4zkvJrohI/vvIlcclxuICAgICAgICAgICAgICAgICAgICB0b29uLnNldFByb3BlcnR5KCdzaGFkZUNvbG9yJywgbmV3IENvbG9yKDE5MCwgMTE4LCAxMzAsIDI1NSkpXHJcbiAgICAgICAgICAgICAgICAgICAgdG9vbi5zZXRQcm9wZXJ0eSgnc2hhZGVDdHJsJywgbmV3IFZlYzQoMC43MiwgMC4xMiwgMC4xNiwgMC4xMikpXHJcbiAgICAgICAgICAgICAgICAgICAgdG9vbi5zZXRQcm9wZXJ0eSgnb3V0bGluZUN0cmwnLCBuZXcgVmVjNCgwLjAwMzUsIDAsIDAsIDApKVxyXG4gICAgICAgICAgICAgICAgICAgIHRvb24uc2V0UHJvcGVydHkoJ291dGxpbmVDb2xvcicsIG5ldyBDb2xvcigxMjgsIDcyLCA4NCwgMjU1KSlcclxuICAgICAgICAgICAgICAgICAgICAvLyDpq5jlhYnnlaXlvLrnlaXogZrvvIjova/pmbYv5p6c6Jyh6LSo5oSf77yJXHJcbiAgICAgICAgICAgICAgICAgICAgdG9vbi5zZXRQcm9wZXJ0eSgnZnVyQ3RybCcsIG5ldyBWZWM0KDAuMTYsIDIuMCwgMC4wNiwgMTAuMCkpXHJcbiAgICAgICAgICAgICAgICAgICAgLy8g5Li75YWJ5pa55ZCR77ya5LiO5oi/6Ze05LiA6Ie077yI5bem5YmN5LiK5Li75YWJICsg5Y+z5YmN5LiL5Ya36KGl5YWJ77yJ77yM54yr54mI6aqM6K+B6L+H55qE5Y+C5pWwXHJcbiAgICAgICAgICAgICAgICAgICAgdG9vbi5zZXRQcm9wZXJ0eSgnbGlnaHREaXInLCBuZXcgVmVjNCgtMC44NSwgMC4zMCwgMC4zMiwgMC4wKSlcclxuICAgICAgICAgICAgICAgICAgICB0b29uLnNldFByb3BlcnR5KCdmaWxsRGlyJywgbmV3IFZlYzQoMC40MiwgLTAuMTgsIDAuODYsIDAuMCkpXHJcbiAgICAgICAgICAgICAgICAgICAgdG9vbi5zZXRQcm9wZXJ0eSgnbGlnaHRDdHJsJywgbmV3IFZlYzQoMC44OCwgMC4yOCwgMC4xNCwgMC4xNCkpXHJcbiAgICAgICAgICAgICAgICAgICAgLy8g4pqg77iPIG1hcEN0cmwg5b+F6aG75pS+5ZyoKirmiYDmnIkgc2V0UHJvcGVydHkg5LmL5ZCOKirvvJrmnZDotKggdW5pZm9ybSDlnKjpppbmrKHnu5HlrprlkI5cclxuICAgICAgICAgICAgICAgICAgICAvLyDmiY3lkIzmraVcIuacgOWQjuS4gOasoeWGmeWFpVwi55qE5YC877yI54yr54mI5bey5a6e5rWL5aSa6L2u77yJXHJcbiAgICAgICAgICAgICAgICAgICAgdG9vbi5zZXRQcm9wZXJ0eSgnbWFwQ3RybCcsIG5ldyBWZWM0KDEsIDAsIDAsIDEpKVxyXG4gICAgICAgICAgICAgICAgICAgIC8vIOKaoO+4jyDlv4XpobvotbAgc2V0TWF0ZXJpYWwg5pi+5byP5pu/5o2i77ya5qih5Z6L5piv5byC5q2l5Yqg6L2955qE77yM6aaW5bin5Y+v6IO95bey57uP5riy6L+H77yMXHJcbiAgICAgICAgICAgICAgICAgICAgLy8gYC5tYXRlcmlhbCA9IHRvb25gIOi1i+WAvOS4jeS8muinpuWPkeiSmeearue9keagvOeahOa4suafk+S+p+mHjee7kVxyXG4gICAgICAgICAgICAgICAgICAgIHJlbmRlcmVyLnNldE1hdGVyaWFsISh0b29uLCAwKVxyXG4gICAgICAgICAgICAgICAgfSBjYXRjaCAoZXJyb3IpIHtcclxuICAgICAgICAgICAgICAgICAgICBjb25zb2xlLndhcm4oJ1twZXQtZ2FtZV0g5a6g54mpIHBldC10b29uIOadkOi0qOWIneWni+WMluWksei0pe+8jOS/neeVmeWOn+adkOi0qCcsIGVycm9yKVxyXG4gICAgICAgICAgICAgICAgICAgIGNvbnRpbnVlXHJcbiAgICAgICAgICAgICAgICB9XHJcbiAgICAgICAgICAgICAgICBjb25zdCB0ZXhCYWNrID0gdG9vbi5nZXRQcm9wZXJ0eSgnbWFpblRleHR1cmUnKSBhcyBUZXh0dXJlMkQgfCBudWxsXHJcbiAgICAgICAgICAgICAgICBjb25zb2xlLmxvZyhgW3BldC1wcm9iZV0gcGV0IG1hdGVyaWFsICR7bi5uYW1lfTogcGV0LXRvb24gYCArXHJcbiAgICAgICAgICAgICAgICAgICAgYHRleD0ke3RleEJhY2sgPyAodGV4QmFjayBhcyB1bmtub3duIGFzIHsgdXVpZD86IHN0cmluZyB9KS51dWlkIDogJ251bGwnfWApXHJcbiAgICAgICAgICAgIH1cclxuICAgICAgICAgICAgZm9yIChjb25zdCBjaGlsZCBvZiBuLmNoaWxkcmVuKSB7XHJcbiAgICAgICAgICAgICAgICB2aXNpdChjaGlsZClcclxuICAgICAgICAgICAgfVxyXG4gICAgICAgIH1cclxuICAgICAgICB2aXNpdChub2RlKVxyXG4gICAgfVxyXG5cclxuICAgIC8qKiDmkbjlpLTlj43lupTmlofmoYjmsaDvvIjov57lh7votormt7HvvIzmjqrovp7otorkurLmmLXvvIkgKi9cclxuICAgIHByaXZhdGUgc3RhdGljIHJlYWRvbmx5IFRBUF9MSU5FUyA9IFtcclxuICAgICAgICAn5Zi/5Zi/772eJywgJ+WGjeaRuOaRuO+9nicsICflpb3oiJLmnI3vvZ4nLCAn77yI6Lmt6Lmt5L2g55qE5omL77yJJywgJ+acgOWWnOasouS9oOWVpicsICflkbzlmZzlkbzlmZzigKYnLFxyXG4gICAgXVxyXG5cclxuICAgIC8qKlxyXG4gICAgICog54K55Ye75a6g54mp5pys5L2T77ya5bGP5bmV6Kem54K5IOKGkiDnm7jmnLrlsITnur8g4oaSIOS4juWuoOeJqeWMheWbtOeQg+axguS6pO+8iOaJi+WGmeWQkemHj+aVsOWtpu+8jOmbtueJqeeQhuS+nei1lu+8ieOAglxyXG4gICAgICog6L+e5Ye76K6+6K6h77yaMi41cyDlhoXov57nu63lkb3kuK3pgJLlop4gY29tYm/igJTigJTlj43lupTmlofmoYjpmo/lsYLmlbDlj5jljJbvvIwzIOWxgui1t+WKoOaYn+aYn++8jFxyXG4gICAgICogNSDov57op6blj5Hlvanom4vvvIjlvIDlv4PliLDovazlnIjlnIjvvInvvJtjb21ibyDlsIHpobYgOOOAguavj+asoeWRveS4remDvemAmuefpeWuv+S4u+aJp+ihjOeOqeiAjeS6kuWKqO+8iOacjeWKoeerr+adg+Wogeiuoeasoe+8ieOAglxyXG4gICAgICovXHJcbiAgICBwcml2YXRlIG9uU3RhZ2VUYXAgPSAoZXZlbnQ6IEV2ZW50VG91Y2gpOiB2b2lkID0+IHtcclxuICAgICAgICBpZiAoIXRoaXMuY2FtZXJhM2QgfHwgIXRoaXMucGV0Tm9kZSkgcmV0dXJuXHJcbiAgICAgICAgY29uc3Qgbm93ID0gRGF0ZS5ub3coKVxyXG4gICAgICAgIGlmIChub3cgLSB0aGlzLnRhcENvb2xkb3duQXQgPCA5MDApIHJldHVyblxyXG4gICAgICAgIHRoaXMudGFwQ29vbGRvd25BdCA9IG5vd1xyXG4gICAgICAgIGNvbnN0IGNvbWJvID0gbm93IC0gdGhpcy50YXBDb21ib0F0IDwgMjUwMCA/IE1hdGgubWluKDgsIHRoaXMudGFwQ29tYm8gKyAxKSA6IDFcclxuICAgICAgICB0aGlzLnRhcENvbWJvID0gY29tYm9cclxuICAgICAgICB0aGlzLnRhcENvbWJvQXQgPSBub3dcclxuICAgICAgICBjb25zdCB1aSA9IGV2ZW50LmdldFVJTG9jYXRpb24oKVxyXG4gICAgICAgIGNvbnN0IHJheSA9IHRoaXMuY2FtZXJhM2Quc2NyZWVuUG9pbnRUb1JheSh1aS54LCB1aS55KVxyXG4gICAgICAgIGNvbnN0IHdwID0gdGhpcy5wZXROb2RlLndvcmxkUG9zaXRpb25cclxuICAgICAgICBjb25zdCBjZW50ZXJZID0gd3AueSArIHRoaXMuZnJ1aXQuaGVpZ2h0ICogMC40NVxyXG4gICAgICAgIGNvbnN0IHJhZGl1cyA9IHRoaXMuZnJ1aXQuaGVpZ2h0ICogMC42NlxyXG4gICAgICAgIGNvbnN0IG94ID0gcmF5Lm8ueCAtIHdwLnhcclxuICAgICAgICBjb25zdCBveSA9IHJheS5vLnkgLSBjZW50ZXJZXHJcbiAgICAgICAgY29uc3Qgb3ogPSByYXkuby56IC0gd3AuelxyXG4gICAgICAgIGNvbnN0IHQgPSAtKG94ICogcmF5LmQueCArIG95ICogcmF5LmQueSArIG96ICogcmF5LmQueilcclxuICAgICAgICBpZiAodCA8PSAwKSByZXR1cm5cclxuICAgICAgICBjb25zdCBweCA9IG94ICsgcmF5LmQueCAqIHRcclxuICAgICAgICBjb25zdCBweSA9IG95ICsgcmF5LmQueSAqIHRcclxuICAgICAgICBjb25zdCBweiA9IG96ICsgcmF5LmQueiAqIHRcclxuICAgICAgICBpZiAocHggKiBweCArIHB5ICogcHkgKyBweiAqIHB6ID4gcmFkaXVzICogcmFkaXVzKSByZXR1cm5cclxuICAgICAgICB0aGlzLnBsYXlDbGlwKCdIYXBweScsIGZhbHNlKVxyXG4gICAgICAgIGNvbnN0IGxpbmVzID0gUGV0R2FtZVJvb3QuVEFQX0xJTkVTXHJcbiAgICAgICAgY29uc3QgbGluZSA9IGxpbmVzW01hdGgubWluKGNvbWJvIC0gMSwgbGluZXMubGVuZ3RoIC0gMSldXHJcbiAgICAgICAgdGhpcy5lZmZlY3RzICYmIHRoaXMuZWZmZWN0cy5mbG9hdFRleHQodGhpcy5oZWFkV29ybGQsIGxpbmUsIG5ldyBDb2xvcigyNTUsIDI1MCwgMjQwLCAyNTUpLCAyMClcclxuICAgICAgICB0aGlzLmVmZmVjdHMgJiYgdGhpcy5lZmZlY3RzLnNwYXJrbGUodGhpcy5oZWFkV29ybGQsIDMgKyBjb21ibylcclxuICAgICAgICBpZiAoY29tYm8gPj0gMykge1xyXG4gICAgICAgICAgICB0aGlzLmVmZmVjdHMgJiYgdGhpcy5lZmZlY3RzLnN0YXJzKHRoaXMuaGVhZFdvcmxkLCBjb21ibylcclxuICAgICAgICB9XHJcbiAgICAgICAgaWYgKGNvbWJvID09PSA1KSB7XHJcbiAgICAgICAgICAgIC8vIOW9qeibi++8muW8gOW/g+WIsOi9rOWciOWciO+8iFkg6L205pW05ZyI5ZCO6Ieq54S25aSN5L2N77yM5Lik5qyhIGJ5IOWQhOWNiuWciO+8iVxyXG4gICAgICAgICAgICBjb25zdCBub2RlID0gdGhpcy5wZXROb2RlXHJcbiAgICAgICAgICAgIHR3ZWVuKG5vZGUpXHJcbiAgICAgICAgICAgICAgICAuYnkoMC40MiwgeyBldWxlckFuZ2xlczogbmV3IFZlYzMoMCwgMTgwLCAwKSB9KVxyXG4gICAgICAgICAgICAgICAgLmJ5KDAuNDIsIHsgZXVsZXJBbmdsZXM6IG5ldyBWZWMzKDAsIDE4MCwgMCkgfSlcclxuICAgICAgICAgICAgICAgIC5jYWxsKCgpID0+IHsgbm9kZS5ldWxlckFuZ2xlcyA9IG5ldyBWZWMzKDAsIDAsIDApIH0pXHJcbiAgICAgICAgICAgICAgICAuc3RhcnQoKVxyXG4gICAgICAgICAgICB0aGlzLmVmZmVjdHMgJiYgdGhpcy5lZmZlY3RzLnN0YXJzKHRoaXMuaGVhZFdvcmxkLCAxMClcclxuICAgICAgICB9XHJcbiAgICAgICAgLy8g6Ziy5Yi377ya5Zu65a6aIDQg5bCP5pe25ZGo5pyf5YaF5YmNIDMg5qyh5omN5Y+RIGludGVudCDnu5PnrpfvvIzlkajmnJ/liIfmjaLoh6rliqjph43nva7vvIzkuYvlkI7lj6rkv53nlZnkupLliqjnibnmlYhcclxuICAgICAgICBjb25zdCBwZXJpb2QgPSBNYXRoLmZsb29yKG5vdyAvIFBldEdhbWVSb290LlJFV0FSRF9QRVJJT0RfTVMpXHJcbiAgICAgICAgaWYgKHBlcmlvZCAhPT0gdGhpcy5yZXdhcmRQZXJpb2RLZXkpIHtcclxuICAgICAgICAgICAgdGhpcy5yZXdhcmRQZXJpb2RLZXkgPSBwZXJpb2RcclxuICAgICAgICAgICAgdGhpcy5yZXdhcmRVc2VkSW5QZXJpb2QgPSAwXHJcbiAgICAgICAgfVxyXG4gICAgICAgIGlmICh0aGlzLnJld2FyZFVzZWRJblBlcmlvZCA8IFBldEdhbWVSb290LlJFV0FSRF9NQVhfUEVSX1BFUklPRCkge1xyXG4gICAgICAgICAgICB0aGlzLnJld2FyZFVzZWRJblBlcmlvZCArPSAxXHJcbiAgICAgICAgICAgIHRoaXMuYnJpZGdlLnNlbmQoeyBzb3VyY2U6ICdwZXQtZ2FtZScsIHR5cGU6ICdpbnRlbnQnLCBhY3Rpb246ICdwbGF5JyB9KVxyXG4gICAgICAgIH1cclxuICAgIH1cclxuXHJcbiAgICAvKiog5pKt54mH77ya5ZCN5a2X5LiN5a2Y5Zyo5pe26Z2Z6buY5Zue6JC977yI5qih5Z6L5Y+v6IO96L+Y5rKh5bim5LiK6K+l5Ymq6L6R77yM5LiN6IO95Zug5q2k5bSp5o6J5pW05Zy677yJICovXHJcbiAgICBwcml2YXRlIHBsYXlDbGlwKG5hbWU6IHN0cmluZywgbG9vcDogYm9vbGVhbik6IHZvaWQge1xyXG4gICAgICAgIGNvbnN0IGFuaW0gPSB0aGlzLnBldEFuaW1cclxuICAgICAgICBpZiAoIWFuaW0pIHtcclxuICAgICAgICAgICAgcmV0dXJuXHJcbiAgICAgICAgfVxyXG4gICAgICAgIGNvbnN0IGhhcyA9IGFuaW0uY2xpcHMuc29tZShjID0+IGMubmFtZSA9PT0gbmFtZSlcclxuICAgICAgICBpZiAoIWhhcykge1xyXG4gICAgICAgICAgICByZXR1cm5cclxuICAgICAgICB9XHJcbiAgICAgICAgY29uc3Qgc3RhdGUgPSBhbmltLmdldFN0YXRlKG5hbWUpXHJcbiAgICAgICAgaWYgKHN0YXRlKSB7XHJcbiAgICAgICAgICAgIHN0YXRlLnNwZWVkID0gdGhpcy5zcGVlZFNjYWxlXHJcbiAgICAgICAgfVxyXG4gICAgICAgIGFuaW0ucGxheShuYW1lKVxyXG4gICAgICAgIGlmICghbG9vcCAmJiBhbmltLmRlZmF1bHRDbGlwKSB7XHJcbiAgICAgICAgICAgIC8vIOWNleasoeWKqOS9nOaSreWujOWbnuWIsOW+heaculxyXG4gICAgICAgICAgICB0aGlzLnNjaGVkdWxlT25jZSgoKSA9PiB0aGlzLnBsYXlDbGlwKCdJZGxlJywgdHJ1ZSksIGFuaW0uZ2V0U3RhdGUobmFtZSk/LmR1cmF0aW9uID8/IDEpXHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG5cclxuICAgIC8qKiDnlKjmnI3liqHnq6/mlbDlgLzlhrPlrprlvoXmnLrlvLrluqbvvJrotoromZrlvLHvvIzliqjkvZzotormhaLvvIjllK/kuIDnmoRcIueKtuaAgeKGkuWKqOeUu1wi5pig5bCE77yJICovXHJcbiAgICBwcml2YXRlIGFwcGx5U3RhdHNUb0FuaW1hdGlvbigpOiB2b2lkIHtcclxuICAgICAgICBjb25zdCBwZXQgPSB0aGlzLnBlbmRpbmdQZXRcclxuICAgICAgICBpZiAoIXBldCkge1xyXG4gICAgICAgICAgICByZXR1cm5cclxuICAgICAgICB9XHJcbiAgICAgICAgY29uc3Qgd29yc3QgPSBNYXRoLm1pbihcclxuICAgICAgICAgICAgcGV0Lm1heEhwID4gMCA/IHBldC5ocCAvIHBldC5tYXhIcCA6IDEsXHJcbiAgICAgICAgICAgIHBldC5odW5nZXIgLyAxMDAsIHBldC5oYXBwaW5lc3MgLyAxMDAsIHBldC5lbmVyZ3kgLyAxMDAsIHBldC5jbGVhbmxpbmVzcyAvIDEwMCxcclxuICAgICAgICApXHJcbiAgICAgICAgLy8gMC40NSjmnoHlt64pIH4gMS4wKOWBpeW6tynvvJrnur/mgKfkvYblpLnkvY/kuIvnlYzvvIzlpKrmhaLkvJrnnIvotbfmnaXljaHkvY9cclxuICAgICAgICB0aGlzLnNwZWVkU2NhbGUgPSBNYXRoLm1heCgwLjQ1LCBNYXRoLm1pbigxLCAwLjQ1ICsgd29yc3QgKiAwLjU1KSlcclxuICAgICAgICBjb25zdCBhbmltID0gdGhpcy5wZXRBbmltXHJcbiAgICAgICAgaWYgKGFuaW0pIHtcclxuICAgICAgICAgICAgZm9yIChjb25zdCBjbGlwIG9mIGFuaW0uY2xpcHMpIHtcclxuICAgICAgICAgICAgICAgIGNvbnN0IHN0YXRlID0gYW5pbS5nZXRTdGF0ZShjbGlwLm5hbWUpXHJcbiAgICAgICAgICAgICAgICBpZiAoc3RhdGUpIHtcclxuICAgICAgICAgICAgICAgICAgICBzdGF0ZS5zcGVlZCA9IHRoaXMuc3BlZWRTY2FsZVxyXG4gICAgICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICB9XHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG5cclxuICAgIHByaXZhdGUgZ2V0IGhlYWRXb3JsZCgpOiBWZWMzIHtcclxuICAgICAgICBjb25zdCBoZWFkID0gdGhpcy5mcnVpdC5oZWFkXHJcbiAgICAgICAgcmV0dXJuIG5ldyBWZWMzKFBFVF9QT1MueCArIGhlYWQueCwgaGVhZC55LCBQRVRfUE9TLnogKyBoZWFkLnopXHJcbiAgICB9XHJcblxyXG4gICAgLy8gLS0tLS0tLS0tLS0tLS0tLSDmjqLpkoggLS0tLS0tLS0tLS0tLS0tLVxyXG5cclxuICAgIHByaXZhdGUgcHJvYmVGcmFtaW5nKCk6IHZvaWQge1xyXG4gICAgICAgIHRoaXMuc2NoZWR1bGVPbmNlKCgpID0+IHtcclxuICAgICAgICAgICAgY29uc3QgY2FtZXJhID0gdGhpcy5jYW1lcmEzZFxyXG4gICAgICAgICAgICBpZiAoIWNhbWVyYSkge1xyXG4gICAgICAgICAgICAgICAgY29uc29sZS5sb2coJ1twZXQtcHJvYmVdIGNhbWVyYSBtaXNzaW5nJylcclxuICAgICAgICAgICAgICAgIHJldHVyblxyXG4gICAgICAgICAgICB9XHJcbiAgICAgICAgICAgIGNvbnN0IGZvb3QgPSBjYW1lcmEud29ybGRUb1NjcmVlbihuZXcgVmVjMyhQRVRfUE9TLngsIDAsIFBFVF9QT1MueiksIG5ldyBWZWMzKCkpXHJcbiAgICAgICAgICAgIGNvbnN0IHRvcCA9IGNhbWVyYS53b3JsZFRvU2NyZWVuKG5ldyBWZWMzKFBFVF9QT1MueCwgdGhpcy5mcnVpdC5oZWlnaHQsIFBFVF9QT1MueiksIG5ldyBWZWMzKCkpXHJcbiAgICAgICAgICAgIGNvbnN0IHNjcmVlbkhlaWdodCA9IHNjcmVlbi53aW5kb3dTaXplLmhlaWdodFxyXG4gICAgICAgICAgICBjb25zb2xlLmxvZyhgW3BldC1wcm9iZV0gY2FtPSR7Y2FtZXJhLm5vZGUucG9zaXRpb24udG9TdHJpbmcoKX0gZm92PSR7Y2FtZXJhLmZvdn0gYCArXHJcbiAgICAgICAgICAgICAgICBgc2NyZWVuPSR7c2NyZWVuSGVpZ2h0fXB4IGZvb3Q9KCR7Zm9vdC54LnRvRml4ZWQoMSl9LCR7Zm9vdC55LnRvRml4ZWQoMSl9KSBgICtcclxuICAgICAgICAgICAgICAgIGB0b3A9KCR7dG9wLngudG9GaXhlZCgxKX0sJHt0b3AueS50b0ZpeGVkKDEpfSkgYCArXHJcbiAgICAgICAgICAgICAgICBgaGVpZ2h0PSR7TWF0aC5hYnModG9wLnkgLSBmb290LnkpLnRvRml4ZWQoMSl9cHggYCArXHJcbiAgICAgICAgICAgICAgICBgcmF0aW89JHsoTWF0aC5hYnModG9wLnkgLSBmb290LnkpIC8gc2NyZWVuSGVpZ2h0ICogMTAwKS50b0ZpeGVkKDEpfSVgKVxyXG4gICAgICAgIH0sIDEuMilcclxuICAgIH1cclxuXHJcbiAgICBwcml2YXRlIHByb2JlTWF0ZXJpYWxzKCk6IHZvaWQge1xyXG4gICAgICAgIHRoaXMuc2NoZWR1bGVPbmNlKCgpID0+IHtcclxuICAgICAgICAgICAgY29uc3QgcmVnaXN0cnkgPSAoRWZmZWN0QXNzZXQgYXMgdW5rbm93biBhcyB7IGdldEFsbD86ICgpID0+IHVua25vd24gfSkuZ2V0QWxsPy4oKVxyXG4gICAgICAgICAgICBjb25zdCBsaXN0OiBFZmZlY3RBc3NldFtdID0gcmVnaXN0cnkgaW5zdGFuY2VvZiBNYXBcclxuICAgICAgICAgICAgICAgID8gQXJyYXkuZnJvbShyZWdpc3RyeS52YWx1ZXMoKSBhcyBJdGVyYWJsZTxFZmZlY3RBc3NldD4pXHJcbiAgICAgICAgICAgICAgICA6IChBcnJheS5pc0FycmF5KHJlZ2lzdHJ5KSA/IHJlZ2lzdHJ5IGFzIEVmZmVjdEFzc2V0W10gOiBbXSlcclxuICAgICAgICAgICAgY29uc29sZS5sb2coYFtwZXQtcHJvYmVdIGxvYWRlZCBlZmZlY3RzICgke2xpc3QubGVuZ3RofSk6IGAgK1xyXG4gICAgICAgICAgICAgICAgbGlzdC5tYXAoZW50cnkgPT4gZW50cnkgJiYgZW50cnkubmFtZSkuam9pbignIHwgJykpXHJcbiAgICAgICAgICAgIGNvbnN0IHZpc2l0ID0gKG5vZGU6IE5vZGUpOiB2b2lkID0+IHtcclxuICAgICAgICAgICAgICAgIGZvciAoY29uc3QgY29tcCBvZiBub2RlLmNvbXBvbmVudHMpIHtcclxuICAgICAgICAgICAgICAgICAgICBjb25zdCBtb2RlbCA9IGNvbXAgYXMgdW5rbm93biBhcyB7XHJcbiAgICAgICAgICAgICAgICAgICAgICAgIG1lc2g/OiB1bmtub3duXHJcbiAgICAgICAgICAgICAgICAgICAgICAgIG1hdGVyaWFsPzogeyBwYXNzZXM/OiB1bmtub3duW107IGVmZmVjdEFzc2V0PzogeyBuYW1lPzogc3RyaW5nIH0gfVxyXG4gICAgICAgICAgICAgICAgICAgIH1cclxuICAgICAgICAgICAgICAgICAgICBpZiAoIW1vZGVsLm1lc2ggfHwgIW1vZGVsLm1hdGVyaWFsKSB7XHJcbiAgICAgICAgICAgICAgICAgICAgICAgIGNvbnRpbnVlXHJcbiAgICAgICAgICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICAgICAgICAgIGNvbnNvbGUubG9nKGBbcGV0LXByb2JlXSBtYXQgJHtub2RlLm5hbWV9IGVmZmVjdD0ke21vZGVsLm1hdGVyaWFsLmVmZmVjdEFzc2V0Py5uYW1lfSBgICtcclxuICAgICAgICAgICAgICAgICAgICAgICAgYHBhc3Nlcz0ke21vZGVsLm1hdGVyaWFsLnBhc3NlcyA/IG1vZGVsLm1hdGVyaWFsLnBhc3Nlcy5sZW5ndGggOiAtMX1gKVxyXG4gICAgICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICAgICAgZm9yIChjb25zdCBjaGlsZCBvZiBub2RlLmNoaWxkcmVuKSB7XHJcbiAgICAgICAgICAgICAgICAgICAgdmlzaXQoY2hpbGQpXHJcbiAgICAgICAgICAgICAgICB9XHJcbiAgICAgICAgICAgIH1cclxuICAgICAgICAgICAgaWYgKHRoaXMucGV0Tm9kZSkge1xyXG4gICAgICAgICAgICAgICAgdmlzaXQodGhpcy5wZXROb2RlKVxyXG4gICAgICAgICAgICB9XHJcbiAgICAgICAgfSwgMS41KVxyXG4gICAgfVxyXG5cclxuICAgIHVwZGF0ZShkdDogbnVtYmVyKTogdm9pZCB7XHJcbiAgICAgICAgY29uc3Qgc3RlcCA9IE1hdGgubWluKGR0LCAwLjA1KVxyXG4gICAgICAgIHRoaXMudGltZSArPSBzdGVwXHJcbiAgICAgICAgdGhpcy5yb29tVGltZSArPSBzdGVwXHJcbiAgICAgICAgdGhpcy5yb29tQW1iaWVuY2Uoc3RlcClcclxuICAgICAgICB0aGlzLmRyaXZlQmxpbmsoc3RlcClcclxuICAgIH1cclxuXHJcbiAgICAvKipcclxuICAgICAqIOecqOecvO+8mumaj+acuumXtOmalOinpuWPkeOAglxyXG4gICAgICog5Ymq6L6R5a2Y5Zyo5pe25omN6LeR77yI5qih5Z6L5Y+v6IO95rKh5bim5LiKIEJsaW5r77yJ77yM5ZCm5YiZ5Lya5q+P5Yeg56eS55m95pKt5LiA5qyh44CCXHJcbiAgICAgKi9cclxuICAgIHByaXZhdGUgZHJpdmVCbGluayhkdDogbnVtYmVyKTogdm9pZCB7XHJcbiAgICAgICAgaWYgKCF0aGlzLmJsaW5rUmVhZHkgfHwgIXRoaXMucGV0QW5pbSkge1xyXG4gICAgICAgICAgICByZXR1cm5cclxuICAgICAgICB9XHJcbiAgICAgICAgdGhpcy5ibGlua1RpbWVyIC09IGR0XHJcbiAgICAgICAgaWYgKHRoaXMuYmxpbmtUaW1lciA+IDApIHtcclxuICAgICAgICAgICAgcmV0dXJuXHJcbiAgICAgICAgfVxyXG4gICAgICAgIHRoaXMuYmxpbmtUaW1lciA9IDIuNCArIE1hdGgucmFuZG9tKCkgKiAzLjZcclxuICAgICAgICBjb25zdCBhbmltID0gdGhpcy5wZXRBbmltXHJcbiAgICAgICAgY29uc3Qgc3RhdGUgPSBhbmltLmdldFN0YXRlKCdCbGluaycpXHJcbiAgICAgICAgaWYgKHN0YXRlKSB7XHJcbiAgICAgICAgICAgIHN0YXRlLnNwZWVkID0gMVxyXG4gICAgICAgIH1cclxuICAgICAgICBhbmltLnBsYXkoJ0JsaW5rJylcclxuICAgIH1cclxuXHJcbiAgICBvbkRlc3Ryb3koKTogdm9pZCB7XHJcbiAgICAgICAgaW5wdXQub2ZmKElucHV0LkV2ZW50VHlwZS5UT1VDSF9FTkQsIHRoaXMub25TdGFnZVRhcCwgdGhpcylcclxuICAgICAgICB0aGlzLmJyaWRnZS5kaXNwb3NlKClcclxuICAgIH1cclxuXHJcbiAgICAvLyAtLS0tLS0tLS0tLS0tLS0tIOWcuuaZr+aehOW7uiAtLS0tLS0tLS0tLS0tLS0tXHJcblxyXG4gICAgcHJpdmF0ZSBidWlsZFdvcmxkKCk6IHZvaWQge1xyXG4gICAgICAgIGNvbnN0IGtpdCA9IHRoaXMua2l0IVxyXG4gICAgICAgIGNvbnN0IHNjZW5lID0gdGhpcy5ub2RlLnNjZW5lXHJcbiAgICAgICAgdGhpcy53b3JsZDNkID0ga2l0Lm1ha2UzZE5vZGUoc2NlbmUsICdXb3JsZDNEJywgbmV3IFZlYzMoMCwgMCwgMCkpXHJcbiAgICAgICAgdGhpcy5yb29tID0gdGhpcy5wbGFpbiA/IG51bGwgOiBidWlsZFJvb20odGhpcy53b3JsZDNkLCBraXQpXHJcbiAgICAgICAgaWYgKCF0aGlzLnBsYWluKSB7XHJcbiAgICAgICAgICAgIHRoaXMuYnVpbGRMaWdodGluZyhzY2VuZSlcclxuICAgICAgICB9XHJcblxyXG4gICAgICAgIGNvbnN0IGNhbWVyYU5vZGUgPSBzY2VuZS5nZXRDaGlsZEJ5TmFtZSgnTWFpbjNEQ2FtZXJhJylcclxuICAgICAgICB0aGlzLmNhbWVyYTNkID0gY2FtZXJhTm9kZSA/IGNhbWVyYU5vZGUuZ2V0Q29tcG9uZW50KENhbWVyYSkgOiBudWxsXHJcbiAgICAgICAgaWYgKGNhbWVyYU5vZGUgJiYgdGhpcy5jYW1lcmEzZCkge1xyXG4gICAgICAgICAgICBjb25zdCBzaG90ID0gQ0FNRVJBX1NIT1RbdGhpcy5zaG90IGFzIGtleW9mIHR5cGVvZiBDQU1FUkFfU0hPVF0gfHwgQ0FNRVJBX1NIT1Qucm9vbVxyXG4gICAgICAgICAgICBjYW1lcmFOb2RlLnNldFBvc2l0aW9uKHNob3QucG9zWzBdLCBzaG90LnBvc1sxXSwgc2hvdC5wb3NbMl0pXHJcbiAgICAgICAgICAgIGNhbWVyYU5vZGUubG9va0F0KG5ldyBWZWMzKHNob3QudGFyZ2V0WzBdLCBzaG90LnRhcmdldFsxXSwgc2hvdC50YXJnZXRbMl0pLCBuZXcgVmVjMygwLCAxLCAwKSlcclxuICAgICAgICAgICAgLy8g5riF5bGP6ImyID0g5aW25rK55bqV77ya5Lu75L2V55S75biDL+WuueWZqOavlOS+i+ivr+W3rumDvemakOW9ouaIkOWltuayueiJsu+8jOiAjOS4jeaYr+a3seiTneadoVxyXG4gICAgICAgICAgICB0aGlzLmNhbWVyYTNkLmNsZWFyQ29sb3IgPSB0aGlzLnBsYWluID8gUExBSU5fQkcgOiBuZXcgQ29sb3IoMHhGRCwgMHhGNiwgMHhFQywgMjU1KVxyXG4gICAgICAgIH1cclxuICAgIH1cclxuXHJcbiAgICAvKipcclxuICAgICAqIOWcuuaZr+WFieeFpyDigJTigJQgKirlj6rmnI3liqHlrqDniakqKuOAglxyXG4gICAgICpcclxuICAgICAqIOaIv+mXtOaJgOaciemDqOS7tui1sOiHqueglCBgcGV0LXRvb25g77yI6Ieq5bim5YaZ5q2755qE5LiJ54K55YWJ77yM5LiN5L6d6LWW5byV5pOO5YWJ5rqQ77yJ77ybXHJcbiAgICAgKiDlrqDniakgZ2xURiDmnZDotKjlv4XpobvpnaDlvJXmk47lhYnmupDmiY3lh7rkvZPnp6/jgILooaXkuIDnm4/lubPooYzlhYnlr7npvZAgcGV0LXRvb24g5Li75YWJ5pa55ZCR77yMXHJcbiAgICAgKiDlm6DkuLogcGV0LXRvb24g5peg6KeG5byV5pOO5YWJ5rqQ77yM6L+Z5LiA5q2l5a+55oi/6Ze0Kirpm7blvbHlk40qKuOAglxyXG4gICAgICovXHJcbiAgICBwcml2YXRlIGJ1aWxkTGlnaHRpbmcoc2NlbmU6IE5vZGUpOiB2b2lkIHtcclxuICAgICAgICAvLyDkuLvlhYnvvIjmmpbvvIzlt6bliY3kuIrvvInvvJoqKuW/hemhu+S7jumVnOWktOi/meS4gOS+p+adpSoq77yI54yr54mI5a6e6K+B77ya5pS+56qX5oi36YKj5L6n5Y+q54Wn5Lqu6IOM6Z2i77yJXHJcbiAgICAgICAgY29uc3Qga2V5ID0gbmV3IE5vZGUoJ1BldEtleUxpZ2h0JylcclxuICAgICAgICBrZXkubGF5ZXIgPSBMYXllcnMuRW51bS5ERUZBVUxUXHJcbiAgICAgICAgc2NlbmUuYWRkQ2hpbGQoa2V5KVxyXG4gICAgICAgIGtleS5zZXRQb3NpdGlvbigtMy4yLCAyLjUsIDIuMylcclxuICAgICAgICBrZXkubG9va0F0KG5ldyBWZWMzKDAsIDAuNjIsIDAuMzUpLCBuZXcgVmVjMygwLCAxLCAwKSlcclxuICAgICAgICBjb25zdCBrZXlMaWdodCA9IGtleS5hZGRDb21wb25lbnQoRGlyZWN0aW9uYWxMaWdodClcclxuICAgICAgICBrZXlMaWdodC5jb2xvciA9IG5ldyBDb2xvcigyNTUsIDIzMSwgMTk4KVxyXG4gICAgICAgIC8vIOKaoO+4jyDlvLrluqblv4XpobvljovlvpfkvY7vvJrml6DoibLosIPmmKDlsITnrqHnur/nur/mgKflgLznm7TmjqUgY2xpcO+8jOi/h+S6ruS8muaKiuS9k+enr+WFqOmhtuS4olxyXG4gICAgICAgIGtleUxpZ2h0LmlsbHVtaW5hbmNlID0gMzQwMDBcclxuXHJcbiAgICAgICAgLy8g6KGl5YWJ77yI5Ya377yM5Y+z5YmN5LiL77yJ77ya5Y6L5L2P5pqX6YOo44CB57uZ5LiA54K55Ya35pqW5a+55q+UXHJcbiAgICAgICAgY29uc3QgZmlsbCA9IG5ldyBOb2RlKCdQZXRGaWxsTGlnaHQnKVxyXG4gICAgICAgIGZpbGwubGF5ZXIgPSBMYXllcnMuRW51bS5ERUZBVUxUXHJcbiAgICAgICAgc2NlbmUuYWRkQ2hpbGQoZmlsbClcclxuICAgICAgICBmaWxsLnNldFBvc2l0aW9uKDMuMCwgMC45LCAyLjApXHJcbiAgICAgICAgZmlsbC5sb29rQXQobmV3IFZlYzMoMCwgMC41NSwgMC4zNSksIG5ldyBWZWMzKDAsIDEsIDApKVxyXG4gICAgICAgIGNvbnN0IGZpbGxMaWdodCA9IGZpbGwuYWRkQ29tcG9uZW50KERpcmVjdGlvbmFsTGlnaHQpXHJcbiAgICAgICAgZmlsbExpZ2h0LmNvbG9yID0gbmV3IENvbG9yKDE5NiwgMjA4LCAyNTUpXHJcbiAgICAgICAgZmlsbExpZ2h0LmlsbHVtaW5hbmNlID0gNzAwMFxyXG5cclxuICAgICAgICAvLyDnjq/looPlhYnvvJpIRFIgLyBMRFIg5Lik5Lu95a2X5q616YO96KaB5YaZ77yI54yr54mI5a6e6K+B77ya5Y+q5YaZIExEUiDlnKggSERSIOWIhuaUr+S4i+S4jeeUn+aViO+8m1xyXG4gICAgICAgIC8vIEhEUiDlrZfmrrXlnKjpg6jliIblvJXmk47lvaLmgIHkuIvmmK/lj6ror7sgZ2V0dGVy77yM55u05o6l6LWL5YC85Lya5oqbIFR5cGVFcnJvciDkuK3mlq0gYnVpbGRTY2VuZe+8iVxyXG4gICAgICAgIGNvbnN0IGFtYmllbnQgPSBkaXJlY3Rvci5nZXRTY2VuZSgpIS5nbG9iYWxzLmFtYmllbnRcclxuICAgICAgICBjb25zdCBhID0gYW1iaWVudCBhcyB1bmtub3duIGFzIFJlY29yZDxzdHJpbmcsIHVua25vd24+XHJcbiAgICAgICAgY29uc3Qgc2t5TERSID0gbmV3IENvbG9yKDIwMCwgMjA1LCAyMTUpXHJcbiAgICAgICAgY29uc3QgZ3JvdW5kTERSID0gbmV3IENvbG9yKDE0MCwgMTEyLCA4NClcclxuICAgICAgICBhbWJpZW50LnNreUNvbG9yID0gc2t5TERSXHJcbiAgICAgICAgYW1iaWVudC5ncm91bmRBbGJlZG8gPSBncm91bmRMRFJcclxuICAgICAgICBhbWJpZW50LnNreUlsbHVtID0gMTQwMFxyXG4gICAgICAgIGNvbnN0IGhkcldyaXRhYmxlID0gKCgpID0+IHtcclxuICAgICAgICAgICAgY29uc3QgZGVzYyA9IE9iamVjdC5nZXRPd25Qcm9wZXJ0eURlc2NyaXB0b3IoYSwgJ3NreUNvbG9ySERSJylcclxuICAgICAgICAgICAgICAgID8/IE9iamVjdC5nZXRPd25Qcm9wZXJ0eURlc2NyaXB0b3IoT2JqZWN0LmdldFByb3RvdHlwZU9mKGFtYmllbnQpLCAnc2t5Q29sb3JIRFInKVxyXG4gICAgICAgICAgICByZXR1cm4gIWRlc2MgfHwgZGVzYy5zZXQgIT09IHVuZGVmaW5lZFxyXG4gICAgICAgIH0pKClcclxuICAgICAgICBpZiAoaGRyV3JpdGFibGUpIHtcclxuICAgICAgICAgICAgYS5za3lDb2xvckhEUiA9IHNreUxEUlxyXG4gICAgICAgICAgICBhLmdyb3VuZEFsYmVkb0hEUiA9IGdyb3VuZExEUlxyXG4gICAgICAgICAgICBhLnNreUlsbHVtSERSID0gMTQwMFxyXG4gICAgICAgIH0gZWxzZSB7XHJcbiAgICAgICAgICAgIGNvbnNvbGUud2FybignW3BldC1nYW1lXSBIRFIgYW1iaWVudCDlj6ror7vvvIhwcmV2aWV3IOW9ouaAge+8ie+8jOi3s+i/hyBIRFIg5Y+M5YaZJylcclxuICAgICAgICB9XHJcbiAgICAgICAgY29uc29sZS5sb2coYFtwZXQtcHJvYmVdIGxpZ2h0aW5nOiBrZXk9JHtrZXlMaWdodC5pbGx1bWluYW5jZX0gZmlsbD0ke2ZpbGxMaWdodC5pbGx1bWluYW5jZX0gYCArXHJcbiAgICAgICAgICAgIGBhbWJMRFI9JHthbWJpZW50LnNreUlsbHVtfSBhbWJIRFI9JHtTdHJpbmcoYS5za3lJbGx1bUhEUil9IGhkcldyaXRhYmxlPSR7aGRyV3JpdGFibGV9YClcclxuICAgIH1cclxuXHJcbiAgICAvKipcclxuICAgICAqIOWuoOeJqeiEmuS4i+eahOaOpeinpumYtOW9seOAglxyXG4gICAgICog5Zy65pmv5rKh5byA5a6e5pe26Zi05b2x77yI5oi/6Ze05ZCE6YOo5Lu26Z2g5omL5bel6L2v5b2x6KGl77yJ77yM5a6g54mp5Lmf5b+F6aG76KGl5LiA5Liq77yMXHJcbiAgICAgKiDlkKbliJnlroPkvJpcIua1rlwi5Zyo5Zyw5q+v5LiK44CC55SoIGtpdC5kZWNhbCDnmoTmn5TovrnotLToirHjgILojYnojpMgZm9vdHByaW50IOeVpeWwj+S6jueMq+OAglxyXG4gICAgICovXHJcbiAgICBwcml2YXRlIGJ1aWxkUGV0U2hhZG93KCk6IE5vZGUge1xyXG4gICAgICAgIGNvbnN0IHJvb3QgPSB0aGlzLmtpdCEubWFrZTNkTm9kZSh0aGlzLndvcmxkM2QhLCAnUGV0U2hhZG93JywgbmV3IFZlYzMoMCwgMCwgMCkpXHJcbiAgICAgICAgdGhpcy5raXQhLmRlY2FsKHJvb3QsICdCbG9iJywgdGhpcy5mcnVpdC5zaGFkb3csIHtcclxuICAgICAgICAgICAgY29sb3I6IG5ldyBDb2xvcigweDZCLCAweDRBLCAweDMzLCAyNTUpLFxyXG4gICAgICAgICAgICBzaGFkZTogbmV3IENvbG9yKDB4NkIsIDB4NEEsIDB4MzMsIDI1NSksXHJcbiAgICAgICAgICAgIGFscGhhOiA4MixcclxuICAgICAgICAgICAgc29mdDogWzAuMDIsIDEuMF0sXHJcbiAgICAgICAgfSwgbmV3IFZlYzMoUEVUX1BPUy54LCAwLjA3NSwgUEVUX1BPUy56KSlcclxuICAgICAgICByZXR1cm4gcm9vdFxyXG4gICAgfVxyXG5cclxuICAgIHByaXZhdGUgYnVpbGRPdmVybGF5KCk6IHZvaWQge1xyXG4gICAgICAgIHRoaXMuZWZmZWN0cyA9IG5ldyBQZXRFZmZlY3RzKFxyXG4gICAgICAgICAgICAobmFtZTogc3RyaW5nLCB4OiBudW1iZXIsIHk6IG51bWJlcikgPT4gdGhpcy5tYWtlVWlOb2RlKG5hbWUsIHgsIHkpLFxyXG4gICAgICAgICAgICAod29ybGQ6IFZlYzMpID0+IHRoaXMucHJvamVjdCh3b3JsZCksXHJcbiAgICAgICAgKVxyXG4gICAgfVxyXG5cclxuICAgIHByaXZhdGUgbWFrZVVpTm9kZShuYW1lOiBzdHJpbmcsIHg6IG51bWJlciwgeTogbnVtYmVyKTogTm9kZSB7XHJcbiAgICAgICAgY29uc3Qgbm9kZSA9IG5ldyBOb2RlKG5hbWUpXHJcbiAgICAgICAgbm9kZS5sYXllciA9IExheWVycy5FbnVtLlVJXzJEXHJcbiAgICAgICAgbm9kZS5hZGRDb21wb25lbnQoVUlUcmFuc2Zvcm0pLnNldENvbnRlbnRTaXplKDgsIDgpXHJcbiAgICAgICAgbm9kZS5zZXRQb3NpdGlvbih4LCB5LCAwKVxyXG4gICAgICAgIHRoaXMubm9kZS5hZGRDaGlsZChub2RlKVxyXG4gICAgICAgIHJldHVybiBub2RlXHJcbiAgICB9XHJcblxyXG4gICAgcHJpdmF0ZSBwcm9qZWN0KHdvcmxkOiBWZWMzKTogVmVjMyB7XHJcbiAgICAgICAgaWYgKCF0aGlzLmNhbWVyYTNkIHx8ICF0aGlzLnJvb3RUcmFuc2Zvcm0pIHtcclxuICAgICAgICAgICAgcmV0dXJuIG5ldyBWZWMzKDAsIDAsIDApXHJcbiAgICAgICAgfVxyXG4gICAgICAgIGNvbnN0IHNjcmVlblBvcyA9IHRoaXMuY2FtZXJhM2Qud29ybGRUb1NjcmVlbih3b3JsZCwgbmV3IFZlYzMoKSlcclxuICAgICAgICBjb25zdCB3aW5kb3dTaXplID0gc2NyZWVuLndpbmRvd1NpemVcclxuICAgICAgICBjb25zdCBjYW52YXNTaXplID0gdGhpcy5yb290VHJhbnNmb3JtLmNvbnRlbnRTaXplXHJcbiAgICAgICAgaWYgKCF3aW5kb3dTaXplLndpZHRoIHx8ICF3aW5kb3dTaXplLmhlaWdodCB8fCAhY2FudmFzU2l6ZS53aWR0aCkge1xyXG4gICAgICAgICAgICByZXR1cm4gbmV3IFZlYzMoMCwgMCwgMClcclxuICAgICAgICB9XHJcbiAgICAgICAgY29uc3Qgc2NhbGVYID0gY2FudmFzU2l6ZS53aWR0aCAvIHdpbmRvd1NpemUud2lkdGhcclxuICAgICAgICBjb25zdCBzY2FsZVkgPSBjYW52YXNTaXplLmhlaWdodCAvIHdpbmRvd1NpemUuaGVpZ2h0XHJcbiAgICAgICAgcmV0dXJuIG5ldyBWZWMzKFxyXG4gICAgICAgICAgICBzY3JlZW5Qb3MueCAqIHNjYWxlWCAtIGNhbnZhc1NpemUud2lkdGggLyAyLFxyXG4gICAgICAgICAgICBzY3JlZW5Qb3MueSAqIHNjYWxlWSAtIGNhbnZhc1NpemUuaGVpZ2h0IC8gMixcclxuICAgICAgICAgICAgMCxcclxuICAgICAgICApXHJcbiAgICB9XHJcblxyXG4gICAgLyoqIOaIv+mXtOawm+WbtO+8mueBr+azoeWRvOWQuCAvIOmYs+WFieWFieaWkeWRvOWQuCAvIOeOqeWFt+eQg+i9u+aRhiAqL1xyXG4gICAgcHJpdmF0ZSByb29tQW1iaWVuY2UoZHQ6IG51bWJlcik6IHZvaWQge1xyXG4gICAgICAgIGNvbnN0IHJvb20gPSB0aGlzLnJvb21cclxuICAgICAgICBpZiAoIXJvb20pIHtcclxuICAgICAgICAgICAgcmV0dXJuXHJcbiAgICAgICAgfVxyXG4gICAgICAgIHJvb20uYnVsYnMuZm9yRWFjaCgoYnVsYiwgaW5kZXgpID0+IHtcclxuICAgICAgICAgICAgY29uc3Qgc2NhbGUgPSAwLjkyICsgTWF0aC5zaW4odGhpcy5yb29tVGltZSAqIDIuMSArIGluZGV4ICogMC43KSAqIDAuMDhcclxuICAgICAgICAgICAgYnVsYi5zZXRTY2FsZShzY2FsZSwgc2NhbGUsIHNjYWxlKVxyXG4gICAgICAgIH0pXHJcbiAgICAgICAgY29uc3QgYmVhbVNjYWxlID0gMSArIE1hdGguc2luKHRoaXMucm9vbVRpbWUgKiAwLjkpICogMC4wNlxyXG4gICAgICAgIHJvb20uc3VuQmVhbS5zZXRTY2FsZShiZWFtU2NhbGUsIDEsIGJlYW1TY2FsZSlcclxuICAgICAgICBjb25zdCB0b3kgPSByb29tLnRveUJhbGxcclxuICAgICAgICB0b3kuc2V0UG9zaXRpb24oXHJcbiAgICAgICAgICAgIC0xLjM1ICsgTWF0aC5zaW4odGhpcy5yb29tVGltZSAqIDAuNikgKiAwLjEsXHJcbiAgICAgICAgICAgIDAuMTkgKyBNYXRoLmFicyhNYXRoLnNpbih0aGlzLnJvb21UaW1lICogMS40KSkgKiAwLjAzLFxyXG4gICAgICAgICAgICAxLjA1LFxyXG4gICAgICAgIClcclxuICAgIH1cclxuXHJcbiAgICAvLyAtLS0tLS0tLS0tLS0tLS0tIOahpeaOpSAtLS0tLS0tLS0tLS0tLS0tXHJcblxyXG4gICAgcHJpdmF0ZSBiaW5kQnJpZGdlKCk6IHZvaWQge1xyXG4gICAgICAgIHRoaXMuYnJpZGdlLmJpbmQoKG1lc3NhZ2U6IEhvc3RUb0dhbWUpID0+IHtcclxuICAgICAgICAgICAgc3dpdGNoIChtZXNzYWdlLnR5cGUpIHtcclxuICAgICAgICAgICAgICAgIGNhc2UgJ2luaXQnOlxyXG4gICAgICAgICAgICAgICAgY2FzZSAncGV0U3RhdGUnOlxyXG4gICAgICAgICAgICAgICAgICAgIHRoaXMucGVuZGluZ1BldCA9IG1lc3NhZ2UucGV0XHJcbiAgICAgICAgICAgICAgICAgICAgdGhpcy5hcHBseVN0YXRzVG9BbmltYXRpb24oKVxyXG4gICAgICAgICAgICAgICAgICAgIGJyZWFrXHJcbiAgICAgICAgICAgICAgICBjYXNlICdhY3Rpb25SZXN1bHQnOlxyXG4gICAgICAgICAgICAgICAgICAgIGlmIChtZXNzYWdlLm9rKSB7XHJcbiAgICAgICAgICAgICAgICAgICAgICAgIHRoaXMucGxheUNsaXAoJ0hhcHB5JywgZmFsc2UpXHJcbiAgICAgICAgICAgICAgICAgICAgICAgIHRoaXMuZWZmZWN0cyAmJiB0aGlzLmVmZmVjdHMuc3BhcmtsZSh0aGlzLmhlYWRXb3JsZCwgMylcclxuICAgICAgICAgICAgICAgICAgICB9IGVsc2Uge1xyXG4gICAgICAgICAgICAgICAgICAgICAgICB0aGlzLmVmZmVjdHMgJiYgdGhpcy5lZmZlY3RzLmZsb2F0VGV4dCh0aGlzLmhlYWRXb3JsZCwgJ+WRnOKApicsIG5ldyBDb2xvcigyNTUsIDIwMCwgMjAwLCAyNTUpKVxyXG4gICAgICAgICAgICAgICAgICAgIH1cclxuICAgICAgICAgICAgICAgICAgICBicmVha1xyXG4gICAgICAgICAgICAgICAgY2FzZSAnYmF0dGxlUm91bmRzJzpcclxuICAgICAgICAgICAgICAgICAgICBpZiAodGhpcy5lZmZlY3RzKSB7XHJcbiAgICAgICAgICAgICAgICAgICAgICAgIHRoaXMuZWZmZWN0cy5zdGFycyh0aGlzLmhlYWRXb3JsZCwgbWVzc2FnZS53b24gPyA4IDogMylcclxuICAgICAgICAgICAgICAgICAgICB9XHJcbiAgICAgICAgICAgICAgICAgICAgYnJlYWtcclxuICAgICAgICAgICAgICAgIGNhc2UgJ2NoYXRCdWJibGUnOlxyXG4gICAgICAgICAgICAgICAgICAgIC8vIOawlOazoeWxnueVjOmdouWxgu+8iOaXpyBIVUQg5bey5Yig77yJ77yM5pawIFVJIOWxguiQveWcsOWJjeWFiOeUqOiInuWPsOa1ruWtl+mhtuS4gOS4i1xyXG4gICAgICAgICAgICAgICAgICAgIHRoaXMuZWZmZWN0cyAmJiB0aGlzLmVmZmVjdHMuZmxvYXRUZXh0KHRoaXMuaGVhZFdvcmxkLCBtZXNzYWdlLmNvbnRlbnQsIG5ldyBDb2xvcigyNTUsIDI1MCwgMjQwLCAyNTUpLCAyMClcclxuICAgICAgICAgICAgICAgICAgICBicmVha1xyXG4gICAgICAgICAgICAgICAgZGVmYXVsdDpcclxuICAgICAgICAgICAgICAgICAgICBicmVha1xyXG4gICAgICAgICAgICB9XHJcbiAgICAgICAgfSlcclxuICAgIH1cclxufVxyXG4iLCJpbXBvcnQgeyBDb2xvciB9IGZyb20gJ2NjJ1xyXG5cclxuLyoqXHJcbiAqIOWuoOeJqeacrOS9k+mFjeiJsu+8iOinkuiJsuWxguS7pOeJjO+8jOmdnueVjOmdouWxgu+8ieOAglxyXG4gKlxyXG4gKiDogYzotKPovrnnlYzvvJpcclxuICogIC0g5pys5paH5Lu2Kirlj6rnrqHlrqDnianoh6rlt7HnmoTpopzoibIqKu+8mueUseeJqeenjeWfuuiJsuiHquWKqOaOqOWvvOWujOaVtOiwg+iJsuadv++8jOS+myAzRCDpgKDlnovkuI7mnZDotKjkvb/nlKjvvJtcclxuICogIC0gKirkuI3lkKvku7vkvZXnlYzpnaLku6TniYwqKuOAguaXp+eJiOeahCBIVUQg5Y2h54mH6ImyIC8g54q25oCB5p2h6ImyIC8g5Yqo5L2c5oyJ6ZKu6ImyIC8gZW1vamkg5Zu+5qCH6KGoXHJcbiAqICAgIO+8iGBIVURg44CBYFNUQVRFX1JPV1Ng44CBYEFDVElPTl9CVVRUT05TYOOAgWBOQVZfQlVUVE9OU2DvvInlt7Lpmo8gUGV0SHVkIOS4gOW5tuWIoOmZpO+8jFxyXG4gKiAgICDnlYzpnaLlsYLku6TniYzlsIbnlLHmlrDorr7orqHnqL/lrprnqL/lkI7lnKjni6znq4vmqKHlnZfkuK3ph43lu7rjgIJcclxuICpcclxuICog57qm5a6a77ya5omA5pyJ6aKc6Imy6YO95pivIHNSR0Ig6K+t5LmJ77yI5LiO57Sg5p2Q44CB6K6+6K6h56i/5LiA6Ie077yJ77yMXHJcbiAqIOS7hSBwZXQtdG9vbiBlZmZlY3Qg55qEIGxpbmVhciDlsZ7mgKfkuIrkvKDml7bnlLHlvJXmk47lgZrnur/mgKfljJbvvIzku6PnoIHkvqfkuI3lgZrovazmjaLjgIJcclxuICovXHJcblxyXG4vKiog5a6g54mp5a6M5pW06LCD6Imy5p2/77yIOCDmp73vvJvlkI7kuInpobnkuLrop4bop4nph43mnoQgdjUg5paw5aKe77yJICovXHJcbmV4cG9ydCBpbnRlcmZhY2UgUGV0UGFsZXR0ZSB7XHJcbiAgICAvKiog5Li76Imy77yI6Lqr5L2TL+WktC/ogLMv5bC+77yJICovXHJcbiAgICBib2R5OiBDb2xvclxyXG4gICAgLyoqIOa3seiJsu+8iOiAs+WGhS/lsL7lt7Qv6Iqx57q577yJICovXHJcbiAgICBkYXJrOiBDb2xvclxyXG4gICAgLyoqIOiCmueari/lj6PpvLvkuq7oibIgKi9cclxuICAgIGJlbGx5OiBDb2xvclxyXG4gICAgLyoqIOeIquWeq+iJsu+8iOiEmuaOjC/ohJrotr7vvIkgKi9cclxuICAgIHBhdzogQ29sb3JcclxuICAgIC8qKiDoha7nuqIgKi9cclxuICAgIGJsdXNoOiBDb2xvclxyXG4gICAgLyoqIOiZueiGnOiJsu+8iOaWsOWinu+8ieKAlOKAlCDop5LoibLllK/kuIDnmoTmmpboibLorrDlv4bngrnvvIzlv4Xpobvku6PnoIHlj6/mjqfvvIzkuI3pnaDotLTlm77ng5jmrbsgKi9cclxuICAgIGlyaXM6IENvbG9yXHJcbiAgICAvKiog55y855CD6auY5YWJ6Imy77yI5paw5aKe77yJ4oCU4oCUIOWbuuWumuS4reaAp++8jOe7neS4jeWFgeiuuOiiq+afk+iJsu+8muWug+aYr1wi55y8552b6L+Y5rS7552AXCLnmoTllK/kuIDmnaXmupAgKi9cclxuICAgIGlyaXNIaWdobGlnaHQ6IENvbG9yXHJcbiAgICAvKiog6YWN6aWw6Imy77yI5paw5aKe77yJ4oCU4oCUIOWbtOW3vi/pooblt74v6ZOD6ZOb562J77yM5LiO55WM6Z2i5qyh5by66LCD6Imy5ZCM5rqQICovXHJcbiAgICBhY2Nlc3Nvcnk6IENvbG9yXHJcbn1cclxuXHJcbi8qKiDniannp43ln7roibLvvIjllK/kuIDoibLmupDvvJrlhbbkvZnpg6jkvY3nlLHln7roibLmjqjlr7zvvIkgKi9cclxuY29uc3QgU1BFQ0lFU19CT0RZOiBSZWNvcmQ8c3RyaW5nLCBzdHJpbmc+ID0ge1xyXG4gICAgQ0FUOiAnI0YzRUVFNycsICAgICAgLy8g5aW25rK555m977yI5ZSv5LiA54mp56eN77ya5YW25LuW54mp56eN6YCg5Z6L5bey5Yig6Zmk77yJXHJcbn1cclxuXHJcbi8qKiDmt7HoibLpg6jkvY3opobnm5bvvIjogLPlhoUv6Iqx57q577yb57y655yB55Sx5Z+66Imy5o6o5a+877yJICovXHJcbmNvbnN0IFNQRUNJRVNfREFSSzogUmVjb3JkPHN0cmluZywgc3RyaW5nPiA9IHtcclxuICAgIENBVDogJyNDRkM1QkUnLCAgICAgIC8vIOaalueBsOmYtOW9se+8iOaal+mDqOS/neaMgeaaluiwg+OAgeS4jeWPkem7ke+8iVxyXG59XHJcblxyXG4vKiog6IKa55quL+WPo+m8u+imhueblu+8iOe8uuecgSA9IOWfuuiJsuaPkOS6riA0MiXvvIkgKi9cclxuY29uc3QgU1BFQ0lFU19CRUxMWTogUmVjb3JkPHN0cmluZywgc3RyaW5nPiA9IHtcclxuICAgIENBVDogJyNGRkY4RjAnLCAgICAgIC8vIOWPo+m8u+S4juiDuOiFuea1heiJslxyXG59XHJcblxyXG4vKiog54iq5Z6r6KaG55uW77yI57y655yBID0g5Z+66Imy5o+Q5LquIDUyJe+8iSAqL1xyXG5jb25zdCBTUEVDSUVTX1BBVzogUmVjb3JkPHN0cmluZywgc3RyaW5nPiA9IHtcclxuICAgIENBVDogJyNFQURCRDQnLCAgICAgIC8vIOeIquWeq++8muavlOS4u+S9k+eVpeaal+eahOaalueBsOeyie+8iOS4jeaKouecvO+8iVxyXG59XHJcblxyXG4vKiog6Jm56Iac6KaG55uW77yI57y655yBID0g55Cl54+A6YeR77yb5Y+W6Ieq5aW254Gw54yr5a6e5rWL6LS05Zu+77yJICovXHJcbmNvbnN0IFNQRUNJRVNfSVJJUzogUmVjb3JkPHN0cmluZywgc3RyaW5nPiA9IHtcclxuICAgIENBVDogJyNDOThBM0UnLFxyXG59XHJcblxyXG4vKiog6YWN6aWw6KaG55uW77yI54mp56eN6buY6K6k6YWN6aWw6Imy77yJICovXHJcbmNvbnN0IFNQRUNJRVNfQUNDRVNTT1JZOiBSZWNvcmQ8c3RyaW5nLCBzdHJpbmc+ID0ge1xyXG4gICAgQ0FUOiAnI0M0QTBBMicsICAgICAgLy8g5bmy5p6v546r55Gw6JeV57KJ77yI6ZKI57uH5Zu05be+77yJXHJcbn1cclxuXHJcbi8qKlxyXG4gKiDnnLznkIPpq5jlhYnvvJoqKuWbuuWumuS4reaAp+iJsu+8jOS4jemaj+eJqeenjS/nmq7ogqTlj5jljJYqKuOAglxyXG4gKlxyXG4gKiDljoblj7LmlZnorq3vvJrmm77lr7nmlbTlj6rnnLznkIPlgZrlrprlkJHmn5PoibLvvIzmiorpq5jlhYkgYCNGNEYyRjFgIOS4gOi1t+afk+aIkCBgI0YxRTJBQWDvvIxcclxuICog55y85YaF5pyA6auY5piO5bqm5LuOIDI0OSDmjonliLAgMjEyIOKGkiDombnohpzph5EgKyDnnLznmb3pu4QgKyDpq5jlhYnpu4Tns4rmiJDkuIDniYfvvIzmlbTlj6rnnLzor7vkvZzkuIDpopfph5HnkIPjgIJcclxuICog6auY5YWJ5piv55y85YaF5ZSv5LiA55qE5Lit5oCn6Imy77yM5Lmf5pivXCLmub/mtqbjgIHmnInnpZ5cIueahOWFqOmDqOadpea6kO+8jOW/hemhu+mUgeatu+OAglxyXG4gKi9cclxuY29uc3QgSVJJU19ISUdITElHSFQgPSAnI0Y0RjNGMidcclxuXHJcbi8qKiDlvLrosIPph5HoibLvvIjppbDmiaPjgIHlvr3nq6DnrYnngrnnnZvnu4boioLvvJvkvY7ppbHlkoznmoTph5HvvIzpgb/lhY1cIuWhkeaWmeS6rueJh1wi5oSf77yJICovXHJcbmV4cG9ydCBjb25zdCBBQ0NFTlRfR09MRCA9ICcjRTdCODVEJ1xyXG5cclxuLyoqIOmFjemlsOmUru+8iFBldERpc3BsYXlTdGF0ZS5hY2Nlc3NvcnnvvInihpIg6aKc6ImyICovXHJcbmNvbnN0IEFDQ0VTU09SWV9DT0xPUjogUmVjb3JkPHN0cmluZywgc3RyaW5nPiA9IHtcclxuICAgIG5vbmU6ICcjQzRBMEEyJyxcclxuICAgIGJlbGw6ICcjRTdDODdGJywgICAgIC8vIOa1hemHkemTg+mTm1xyXG4gICAgc2NhcmY6ICcjQzRBMEEyJywgICAgLy8g5bmy5p6v546r55Gw6JeV57KJ5Zu05be+XHJcbiAgICBib3d0aWU6ICcjQjk4QTg4JyxcclxuICAgIGdsYXNzZXM6ICcjOEI3OTY4JyxcclxufVxyXG5cclxuLyoqIOearuiCpOiJsumUru+8iHBldF9za2luX2NvbmZpZy5jb2xvcu+8ieKGkiDln7roibLvvIjkv53nlZnlrr/kuLvoh6rlrprkuYnmjaLoibLog73lipvvvIkgKi9cclxuY29uc3QgU0tJTl9CT0RZOiBSZWNvcmQ8c3RyaW5nLCBzdHJpbmc+ID0ge1xyXG4gICAgb3JhbmdlOiAnI0Y3QjA1QScsXHJcbiAgICBncmF5OiAnI0I3QkRDOScsXHJcbiAgICB3aGl0ZTogJyNGQUY3RjMnLFxyXG4gICAgYnJvd246ICcjQzA4QTVFJyxcclxuICAgIHBpbms6ICcjRjdBRkM0JyxcclxuICAgIGJsYWNrOiAnIzU3NTI1RicsXHJcbiAgICBtaW50OiAnIzhGRTNDOCcsXHJcbiAgICBnb2xkZW46ICcjRjJDRTc0JyxcclxuICAgIHNub3c6ICcjRTZFRkZGJyxcclxuICAgIG1pZG5pZ2h0OiAnIzhDODZDNCcsXHJcbiAgICBpbms6ICcjNEE0QTU4JyxcclxuICAgIGF1cm9yYTogJyM5RkI2RkYnLFxyXG59XHJcblxyXG4vKiog5a6g54mp5b2T5YmN5oOF57uq77yI6amx5Yqo5b6F5py66KGo5oOF5LiO5ae/5oCB77yb5bGe6KeS6Imy5bGC77yM5LiN5bGe55WM6Z2i5bGC77yJICovXHJcbmV4cG9ydCB0eXBlIFBldEVtb3Rpb24gPVxyXG4gICAgfCAnaWRsZScgICAgLy8g5bmz6Z2Z5oKg6ZeyXHJcbiAgICB8ICdoYXBweScgICAvLyDlvIDlv4PvvIjlv4Pmg4Xpq5jvvIlcclxuICAgIHwgJ2h1bmdyeScgIC8vIOmlpemlv++8iOmlsemjn+S9ju+8iVxyXG4gICAgfCAnc2FkJyAgICAgLy8g6Zq+6L+H77yI5aSa6aG55pWw5YC85L2O77yJXHJcbiAgICB8ICdzbGVlcHknICAvLyDlm7DlgKbvvIjnsr7lipvkvY7vvIlcclxuICAgIHwgJ2VhdCcgfCAncGxheScgfCAnY2xlYW4nIHwgJ3NsZWVwJyB8ICdwZXQnIHwgJ2xvdmUnICAvLyDkuqTkupLmvJTlh7rmgIFcclxuXHJcbi8qKiDljYHlha3ov5vliLYg4oaSIENvbG9yICovXHJcbmZ1bmN0aW9uIGhleCh2YWx1ZTogc3RyaW5nKTogQ29sb3Ige1xyXG4gICAgcmV0dXJuIG5ldyBDb2xvcigpLmZyb21IRVgodmFsdWUpXHJcbn1cclxuXHJcbi8qKiDpopzoibLlkJHnmb0v6buR5o+S5YC877yIYW1vdW50ID4gMCDmj5Dkuq7vvIw8IDAg5Y6L5pqX77yJICovXHJcbmV4cG9ydCBmdW5jdGlvbiBzaGlmdChjb2xvcjogQ29sb3IsIGFtb3VudDogbnVtYmVyKTogQ29sb3Ige1xyXG4gICAgY29uc3QgdGFyZ2V0ID0gYW1vdW50ID49IDAgPyAyNTUgOiAwXHJcbiAgICBjb25zdCBrID0gTWF0aC5hYnMoYW1vdW50KVxyXG4gICAgcmV0dXJuIG5ldyBDb2xvcihcclxuICAgICAgICBNYXRoLnJvdW5kKGNvbG9yLnIgKyAodGFyZ2V0IC0gY29sb3IucikgKiBrKSxcclxuICAgICAgICBNYXRoLnJvdW5kKGNvbG9yLmcgKyAodGFyZ2V0IC0gY29sb3IuZykgKiBrKSxcclxuICAgICAgICBNYXRoLnJvdW5kKGNvbG9yLmIgKyAodGFyZ2V0IC0gY29sb3IuYikgKiBrKSxcclxuICAgICAgICBjb2xvci5hLFxyXG4gICAgKVxyXG59XHJcblxyXG4vKipcclxuICog6Kej5p6Q5a6g54mp6LCD6Imy5p2/77ya55qu6IKk6Imy5LyY5YWI77yM5YW25qyh54mp56eN6YWN6Imy77yb5pyq55+l6ZSu5Zue6JC96ZO255m954yr44CCXHJcbiAqXHJcbiAqIOivtOaYju+8mlxyXG4gKiAgLSDniannp43kuJPlsZ7nmoTogprnmq4v54iq5Z6rL+iZueiGnOimhuebluWPquWcqFwi5pyq6Ieq5a6a5LmJ6IKk6ImyXCLml7bnlJ/mlYjvvIxcclxuICogICAg5ZCm5YiZ5oyJ55So5oi36YCJ55qE5Z+66Imy6YeN5paw5o6o5a+877yM5L+d6K+B5o2i6Imy5ZCO5pW05L2T5Y2P6LCD77ybXHJcbiAqICAtIOmrmOWFieiJsuS4jeWPguS4juaOqOWvvO+8jOS7u+S9leaDheWGteS4i+mDveaYr+WbuuWumuS4reaAp+iJsuOAglxyXG4gKi9cclxuZXhwb3J0IGZ1bmN0aW9uIHJlc29sdmVQYWxldHRlKHNwZWNpZXM6IHN0cmluZywgY29sb3JLZXk/OiBzdHJpbmcsIGFjY2Vzc29yeUtleT86IHN0cmluZyk6IFBldFBhbGV0dGUge1xyXG4gICAgY29uc3QgYmFzZUhleCA9IChjb2xvcktleSAmJiBTS0lOX0JPRFlbY29sb3JLZXldKSB8fCBTUEVDSUVTX0JPRFlbc3BlY2llc10gfHwgU1BFQ0lFU19CT0RZLkNBVFxyXG4gICAgY29uc3QgYm9keSA9IGhleChiYXNlSGV4KVxyXG4gICAgY29uc3Qgc3BlY2llc1RpbnQgPSAhY29sb3JLZXlcclxuICAgIHJldHVybiB7XHJcbiAgICAgICAgYm9keSxcclxuICAgICAgICBkYXJrOiBzcGVjaWVzVGludCAmJiBTUEVDSUVTX0RBUktbc3BlY2llc10gPyBoZXgoU1BFQ0lFU19EQVJLW3NwZWNpZXNdKSA6IHNoaWZ0KGJvZHksIC0wLjIyKSxcclxuICAgICAgICAvLyDogprnmq7vvJrln7roibLlpKfluYXmj5Dkuq7vvIjnmb3oibLns7vniannp43kuI3kvJrov4fmm53vvIxzaGlmdCDmnInkuIrpmZDkv53miqTvvIlcclxuICAgICAgICBiZWxseTogc3BlY2llc1RpbnQgJiYgU1BFQ0lFU19CRUxMWVtzcGVjaWVzXSA/IGhleChTUEVDSUVTX0JFTExZW3NwZWNpZXNdKSA6IHNoaWZ0KGJvZHksIDAuNDIpLFxyXG4gICAgICAgIC8vIOeIquWeq++8muavlOiCmuearuabtOS6ruS4gOeCueeCue+8jOW9ouaIkFwi6KKc5a2QXCLlsYLmrKFcclxuICAgICAgICBwYXc6IHNwZWNpZXNUaW50ICYmIFNQRUNJRVNfUEFXW3NwZWNpZXNdID8gaGV4KFNQRUNJRVNfUEFXW3NwZWNpZXNdKSA6IHNoaWZ0KGJvZHksIDAuNTIpLFxyXG4gICAgICAgIC8vIOiFrue6ou+8muWbuuWumueahOaalueyie+8jOS4juS7u+S9leS4u+iJsumDveWNj+iwg++8iOmAoOWei+S+p+S7peS9jumAj+aYjuW6piArIOaflOWSjOi+uee8mOS9v+eUqO+8iVxyXG4gICAgICAgIGJsdXNoOiBoZXgoJyNGM0E3QUUnKSxcclxuICAgICAgICAvLyDombnohpzvvJrmnKroh6rlrprkuYnogqToibLml7bnlKjniannp43kuJPlsZ7nkKXnj4Dph5HvvJvmjaLoibLlkI7ln7roibLlgY/lhrfvvIzombnohpzlkJHkuq7ph5Hmj5DkuIDmoaPkv53mjIHlr7nmr5RcclxuICAgICAgICBpcmlzOiBzcGVjaWVzVGludCAmJiBTUEVDSUVTX0lSSVNbc3BlY2llc10gPyBoZXgoU1BFQ0lFU19JUklTW3NwZWNpZXNdKSA6IHNoaWZ0KGhleCgnI0M5OEEzRScpLCAwLjEpLFxyXG4gICAgICAgIGlyaXNIaWdobGlnaHQ6IGhleChJUklTX0hJR0hMSUdIVCksXHJcbiAgICAgICAgYWNjZXNzb3J5OiBoZXgoXHJcbiAgICAgICAgICAgIChhY2Nlc3NvcnlLZXkgJiYgQUNDRVNTT1JZX0NPTE9SW2FjY2Vzc29yeUtleV0pXHJcbiAgICAgICAgICAgIHx8IFNQRUNJRVNfQUNDRVNTT1JZW3NwZWNpZXNdXHJcbiAgICAgICAgICAgIHx8IEFDQ0VTU09SWV9DT0xPUi5ub25lLFxyXG4gICAgICAgICksXHJcbiAgICB9XHJcbn1cclxuIiwiLyoqXHJcbiAqIOaJi+WGmee9keagvOW3peWOgu+8iOinhuiniemHjeaehCB2NO+8ieKAlOKAlCDnlKjov57nu63mm7LpnaLmm7/ku6NcIueQg+S9k+aLvOijhVwi44CCXHJcbiAqXHJcbiAqIOS4uuS7gOS5iOmcgOimgeWug++8mlxyXG4gKiAgLSBwcmltaXRpdmVzIOWPquacieeQgy/mn7Ev6ZSl562J566A5Y2V5L2T77yM5ou86KOF5Ye655qE6KeS6Imy5Ymq5b2x56C056KO44CB5o6l57yd56m/5o+S44CB5rOV57q/5LiN6L+e57ut77yMXHJcbiAqICAgIOi/meato+aYr+S4iuS4gOeJiFwi6Zu25Lu25oSf44CB5buJ5Lu35oSfXCLnmoTmoLnmnKzljp/lm6DvvJtcclxuICogIC0g5byV5pOO5o+Q5L6bIHV0aWxzLk1lc2hVdGlscy5jcmVhdGVNZXNoKElHZW9tZXRyeSnvvIzlhYHorrjnm7TmjqXmj5DkuqTku7vmhI/pobbngrnmlbDmja7vvIxcclxuICogICAg5Zug5q2k6L+Z6YeM55SoXCLml4vovazkvZPmlL7moLcobGF0aGUpICsg5omr5o6gKHN3ZWVwKSArIOW5s+a7keazlee6v1wi55Sf5oiQ5LiA5L2T5YyW6L+e57ut5puy6Z2i44CCXHJcbiAqXHJcbiAqIOe6puWumu+8mlxyXG4gKiAgLSDlnZDmoIfns7vkuLogQ29jb3Mg5Y+z5omL57O777yMK1kg5ZCR5LiK77yMK1og5Li66KeS6Imy5q2j5YmN5pa577yI5LiOIFBldE1vZGVsQnVpbGRlciDkuIDoh7TvvInvvJtcclxuICogIC0g5Yeg5L2V5Zyo5pys5Zyw56m66Ze055Sf5oiQ77yM55Sx6LCD55So5pa555So6IqC54K55Y+Y5o2i5a6a5L2N77yI5LiO5pen54mIIHBhcnQvYmFsbCDnlKjms5Xkv53mjIHkuIDoh7TvvInvvJtcclxuICogIC0g6L6T5Ye657uT5p6E5LiOIHByaW1pdGl2ZXMuSUdlb21ldHJ5IOWFvOWuue+8jOWPr+ebtOaOpeWWgue7mSBjcmVhdGVNZXNo44CCXHJcbiAqL1xyXG5cclxuLyoqIOS4jiBwcmltaXRpdmVzLklHZW9tZXRyeSDlhbzlrrnnmoTlh6DkvZXmlbDmja7vvIjku4Xkvb/nlKjmuLLmn5Plv4XpnIDlrZfmrrXvvIkgKi9cclxuZXhwb3J0IGludGVyZmFjZSBHZW8ge1xyXG4gICAgcG9zaXRpb25zOiBudW1iZXJbXVxyXG4gICAgbm9ybWFsczogbnVtYmVyW11cclxuICAgIHV2czogbnVtYmVyW11cclxuICAgIGluZGljZXM6IG51bWJlcltdXHJcbn1cclxuXHJcbi8qKiDkuInnu7TngrnvvIjmlbDnu4TlvaLlvI/vvIzpgb/lhY3lnKjmlbDlrablsYLlvJXlhaXlvJXmk47kvp3otZbvvIkgKi9cclxuZXhwb3J0IHR5cGUgUDMgPSBbbnVtYmVyLCBudW1iZXIsIG51bWJlcl1cclxuXHJcbi8qKiDmiavmjqDot6/lvoToioLngrnvvJrkvY3nva4gKyDmiKrpnaLljYrlvoTvvIjlj6/liIbliKvnvKnmlL4geC95IOW9ouaIkOakreWchuaIqumdou+8iSAqL1xyXG5leHBvcnQgaW50ZXJmYWNlIFN3ZWVwTm9kZSB7XHJcbiAgICBwOiBQM1xyXG4gICAgcjogbnVtYmVyXHJcbiAgICAvKiog5oiq6Z2i5qiq5ZCR57yp5pS+77yI6buY6K6kIDHvvJvnlKjkuo7miYHogLPjgIHmiYHlsL7nrYnvvIkgKi9cclxuICAgIHN4PzogbnVtYmVyXHJcbiAgICAvKiog5oiq6Z2i57q15ZCR57yp5pS+77yI6buY6K6kIDHvvIkgKi9cclxuICAgIHN5PzogbnVtYmVyXHJcbn1cclxuXHJcbi8qKiDpobbngrnlj5jmjaLlj4LmlbDvvIjkvY3nva4v5qyn5ouJ6KeSKOW8p+W6pikv57yp5pS+77yJICovXHJcbmV4cG9ydCBpbnRlcmZhY2UgR2VvVHJhbnNmb3JtIHtcclxuICAgIHBvcz86IFAzXHJcbiAgICAvKiog5qyn5ouJ6KeS77yI5byn5bqm77yJ77yM5oyJIFjihpJZ4oaSWiDpobrluo8gKi9cclxuICAgIHJvdD86IFAzXHJcbiAgICBzY2FsZT86IFAzXHJcbn1cclxuXHJcbmNvbnN0IEVQUyA9IDFlLTZcclxuXHJcbi8qKlxyXG4gKiDml4vovazkvZPvvJrmioogKOWNiuW+hCwg6auY5bqmKSDkvqfova7lu5Pnu5UgWSDovbTml4vovazmiJDov57nu63mm7LpnaLjgIJcclxuICpcclxuICog6L+Z5piv5pys6aG555uu5pyA6YeN6KaB55qE5bu65qih5Y6f6K+t77ya5aS044CB6Lqv5bmy44CB6ICz5py144CB5Zub6IKi5YWo6YOo55Sx5a6D55Sf5oiQ77yMXHJcbiAqIOWboOS4uui9ruW7k+WPr+S7peS7u+aEj+eyvue7hu+8jOS4lOazlee6v+eUsei9ruW7k+WIh+e6v+ino+aekOaxguWHuu+8iOWkqeeEtuWFiea7keOAgeaXoOaOpee8ne+8ieOAglxyXG4gKlxyXG4gKiBAcGFyYW0gcHJvZmlsZSDkvqfova7lu5PvvIzku47kuIvliLDkuIogW3JhZGl1cywgeV3vvJvljYrlvoQgMCDooajnpLrmlLblj6PvvIjoh6rliqjlvaLmiJDmnoHngrnvvIlcclxuICogQHBhcmFtIHNlZ21lbnRzIOWchuWRqOWIhuaute+8iOW7uuiuriAyOC00OO+8jOi2iuS9jui2ilwi5L2O5aSa6L655b2iXCLvvIlcclxuICovXHJcbmV4cG9ydCBmdW5jdGlvbiBsYXRoZShwcm9maWxlOiBBcnJheTxbbnVtYmVyLCBudW1iZXJdPiwgc2VnbWVudHMgPSAzMik6IEdlbyB7XHJcbiAgICBjb25zdCByaW5ncyA9IHByb2ZpbGUubGVuZ3RoXHJcbiAgICBjb25zdCBwb3NpdGlvbnM6IG51bWJlcltdID0gW11cclxuICAgIGNvbnN0IG5vcm1hbHM6IG51bWJlcltdID0gW11cclxuICAgIGNvbnN0IHV2czogbnVtYmVyW10gPSBbXVxyXG4gICAgY29uc3QgaW5kaWNlczogbnVtYmVyW10gPSBbXVxyXG5cclxuICAgIC8vIOmihOWFiOaxguavj+Wxgui9ruW7k+eahCAyRCDms5Xnur/vvJrova7lu5PliIfnur/pobrml7bpkojml4vovawgOTDCsCDmjIflkJHlpJbkvqdcclxuICAgIGNvbnN0IHJpbU5vcm1hbDogQXJyYXk8W251bWJlciwgbnVtYmVyXT4gPSBbXVxyXG4gICAgZm9yIChsZXQgaSA9IDA7IGkgPCByaW5nczsgaSsrKSB7XHJcbiAgICAgICAgY29uc3QgcHJldiA9IHByb2ZpbGVbTWF0aC5tYXgoMCwgaSAtIDEpXVxyXG4gICAgICAgIGNvbnN0IG5leHQgPSBwcm9maWxlW01hdGgubWluKHJpbmdzIC0gMSwgaSArIDEpXVxyXG4gICAgICAgIGxldCB0ciA9IG5leHRbMF0gLSBwcmV2WzBdXHJcbiAgICAgICAgbGV0IHR5ID0gbmV4dFsxXSAtIHByZXZbMV1cclxuICAgICAgICBjb25zdCBsZW4gPSBNYXRoLmh5cG90KHRyLCB0eSlcclxuICAgICAgICBpZiAobGVuIDwgRVBTKSB7XHJcbiAgICAgICAgICAgIHRyID0gMFxyXG4gICAgICAgICAgICB0eSA9IDFcclxuICAgICAgICB9IGVsc2Uge1xyXG4gICAgICAgICAgICB0ciAvPSBsZW5cclxuICAgICAgICAgICAgdHkgLz0gbGVuXHJcbiAgICAgICAgfVxyXG4gICAgICAgIHJpbU5vcm1hbC5wdXNoKFt0eSwgLXRyXSlcclxuICAgIH1cclxuXHJcbiAgICAvLyB1du+8mnUgPSDlnIblkajmlrnkvY3vvIh1ID0gzrgvMs+A77yJ77yMdiA9IOW9kuS4gOWMlumrmOW6pu+8iDAgPSDova7lu5PmnIDkvY7ngrnvvIwxID0g5pyA6auY54K577yJ44CCXHJcbiAgICAvLyB2IOeUqOmrmOW6puiAjOS4jeaYr+eOr+e0ouW8le+8jOaYr+S4uuS6huiuqVwi5oyJIHV2IOWumuS9jeeahOi0tOiKsVwi77yI5aaC54OY54SZ6IWu57qi77yJ6IO95oyJ5a6e6ZmF5Yeg5L2V5L2N572u6K6h566X44CCXHJcbiAgICBjb25zdCB5TWluID0gcHJvZmlsZVswXVsxXVxyXG4gICAgY29uc3QgeU1heCA9IHByb2ZpbGVbcmluZ3MgLSAxXVsxXVxyXG4gICAgY29uc3QgeVNwYW4gPSBNYXRoLmFicyh5TWF4IC0geU1pbikgPCBFUFMgPyAxIDogeU1heCAtIHlNaW5cclxuXHJcbiAgICBmb3IgKGxldCBpID0gMDsgaSA8IHJpbmdzOyBpKyspIHtcclxuICAgICAgICBjb25zdCBbcmFkaXVzLCB5XSA9IHByb2ZpbGVbaV1cclxuICAgICAgICBjb25zdCBbbnIsIG55XSA9IHJpbU5vcm1hbFtpXVxyXG4gICAgICAgIGNvbnN0IHYgPSAoeSAtIHlNaW4pIC8geVNwYW5cclxuICAgICAgICBmb3IgKGxldCBqID0gMDsgaiA8PSBzZWdtZW50czsgaisrKSB7XHJcbiAgICAgICAgICAgIGNvbnN0IHUgPSBqIC8gc2VnbWVudHNcclxuICAgICAgICAgICAgY29uc3QgdGhldGEgPSB1ICogTWF0aC5QSSAqIDJcclxuICAgICAgICAgICAgY29uc3QgY29zID0gTWF0aC5jb3ModGhldGEpXHJcbiAgICAgICAgICAgIGNvbnN0IHNpbiA9IE1hdGguc2luKHRoZXRhKVxyXG4gICAgICAgICAgICBwb3NpdGlvbnMucHVzaChyYWRpdXMgKiBjb3MsIHksIHJhZGl1cyAqIHNpbilcclxuICAgICAgICAgICAgbm9ybWFscy5wdXNoKG5yICogY29zLCBueSwgbnIgKiBzaW4pXHJcbiAgICAgICAgICAgIHV2cy5wdXNoKHUsIHYpXHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG5cclxuICAgIGNvbnN0IHN0cmlkZSA9IHNlZ21lbnRzICsgMVxyXG4gICAgZm9yIChsZXQgaSA9IDA7IGkgPCByaW5ncyAtIDE7IGkrKykge1xyXG4gICAgICAgIGZvciAobGV0IGogPSAwOyBqIDwgc2VnbWVudHM7IGorKykge1xyXG4gICAgICAgICAgICBjb25zdCBhID0gaSAqIHN0cmlkZSArIGpcclxuICAgICAgICAgICAgY29uc3QgYiA9IGEgKyAxXHJcbiAgICAgICAgICAgIGNvbnN0IGMgPSBhICsgc3RyaWRlXHJcbiAgICAgICAgICAgIGNvbnN0IGQgPSBjICsgMVxyXG4gICAgICAgICAgICBpbmRpY2VzLnB1c2goYSwgYywgYiwgYiwgYywgZClcclxuICAgICAgICB9XHJcbiAgICB9XHJcbiAgICByZXR1cm4geyBwb3NpdGlvbnMsIG5vcm1hbHMsIHV2cywgaW5kaWNlcyB9XHJcbn1cclxuXHJcbi8qKlxyXG4gKiDmpK3nkIPvvJrkuInovbTljYrlvoTni6znq4vvvIhRIOeJiOWktC/ouqvkvZMv55y8552b55qE5Z+65pys5L2T77yJ44CCXHJcbiAqIOazlee6v+aMieakreeQg+ino+aekOW8j+axguWHuu+8iOavlOeQg+mdouazlee6v+abtOWHhuehru+8jOaal+mDqOi/h+a4oeabtOiHqueEtu+8ieOAglxyXG4gKi9cclxuZXhwb3J0IGZ1bmN0aW9uIGVsbGlwc29pZChyeDogbnVtYmVyLCByeTogbnVtYmVyLCByejogbnVtYmVyLCBzZWdtZW50cyA9IDMyLCByaW5ncyA9IDIyKTogR2VvIHtcclxuICAgIGNvbnN0IHBvc2l0aW9uczogbnVtYmVyW10gPSBbXVxyXG4gICAgY29uc3Qgbm9ybWFsczogbnVtYmVyW10gPSBbXVxyXG4gICAgY29uc3QgdXZzOiBudW1iZXJbXSA9IFtdXHJcbiAgICBjb25zdCBpbmRpY2VzOiBudW1iZXJbXSA9IFtdXHJcblxyXG4gICAgZm9yIChsZXQgaSA9IDA7IGkgPD0gcmluZ3M7IGkrKykge1xyXG4gICAgICAgIGNvbnN0IHBoaSA9IChpIC8gcmluZ3MpICogTWF0aC5QSSAgICAgICAgICAgLy8gMCjpobYpIOKGkiDPgCjlupUpXHJcbiAgICAgICAgY29uc3Qgc3AgPSBNYXRoLnNpbihwaGkpXHJcbiAgICAgICAgY29uc3QgY3AgPSBNYXRoLmNvcyhwaGkpXHJcbiAgICAgICAgZm9yIChsZXQgaiA9IDA7IGogPD0gc2VnbWVudHM7IGorKykge1xyXG4gICAgICAgICAgICBjb25zdCB0aGV0YSA9IChqIC8gc2VnbWVudHMpICogTWF0aC5QSSAqIDJcclxuICAgICAgICAgICAgY29uc3Qgc3QgPSBNYXRoLnNpbih0aGV0YSlcclxuICAgICAgICAgICAgY29uc3QgY3QgPSBNYXRoLmNvcyh0aGV0YSlcclxuICAgICAgICAgICAgY29uc3QgeCA9IHJ4ICogc3AgKiBjdFxyXG4gICAgICAgICAgICBjb25zdCB5ID0gcnkgKiBjcFxyXG4gICAgICAgICAgICBjb25zdCB6ID0gcnogKiBzcCAqIHN0XHJcbiAgICAgICAgICAgIHBvc2l0aW9ucy5wdXNoKHgsIHksIHopXHJcbiAgICAgICAgICAgIC8vIOakreeQg+makOW8j+air+W6piAoeC9yeMKyLCB5L3J5wrIsIHovcnrCsilcclxuICAgICAgICAgICAgbGV0IG54ID0geCAvIChyeCAqIHJ4KVxyXG4gICAgICAgICAgICBsZXQgbnkgPSB5IC8gKHJ5ICogcnkpXHJcbiAgICAgICAgICAgIGxldCBueiA9IHogLyAocnogKiByeilcclxuICAgICAgICAgICAgY29uc3QgbGVuID0gTWF0aC5oeXBvdChueCwgbnksIG56KSB8fCAxXHJcbiAgICAgICAgICAgIG54IC89IGxlbjsgbnkgLz0gbGVuOyBueiAvPSBsZW5cclxuICAgICAgICAgICAgbm9ybWFscy5wdXNoKG54LCBueSwgbnopXHJcbiAgICAgICAgICAgIHV2cy5wdXNoKGogLyBzZWdtZW50cywgMSAtIGkgLyByaW5ncylcclxuICAgICAgICB9XHJcbiAgICB9XHJcbiAgICBjb25zdCBzdHJpZGUgPSBzZWdtZW50cyArIDFcclxuICAgIGZvciAobGV0IGkgPSAwOyBpIDwgcmluZ3M7IGkrKykge1xyXG4gICAgICAgIGZvciAobGV0IGogPSAwOyBqIDwgc2VnbWVudHM7IGorKykge1xyXG4gICAgICAgICAgICBjb25zdCBhID0gaSAqIHN0cmlkZSArIGpcclxuICAgICAgICAgICAgY29uc3QgYiA9IGEgKyAxXHJcbiAgICAgICAgICAgIGNvbnN0IGMgPSBhICsgc3RyaWRlXHJcbiAgICAgICAgICAgIGNvbnN0IGQgPSBjICsgMVxyXG4gICAgICAgICAgICBpbmRpY2VzLnB1c2goYSwgYiwgYywgYiwgZCwgYylcclxuICAgICAgICB9XHJcbiAgICB9XHJcbiAgICByZXR1cm4geyBwb3NpdGlvbnMsIG5vcm1hbHMsIHV2cywgaW5kaWNlcyB9XHJcbn1cclxuXHJcbi8qKiDlvZLkuIDljJblkJHph48gKi9cclxuZnVuY3Rpb24gbm9ybSh2OiBQMyk6IFAzIHtcclxuICAgIGNvbnN0IGxlbiA9IE1hdGguaHlwb3QodlswXSwgdlsxXSwgdlsyXSkgfHwgMVxyXG4gICAgcmV0dXJuIFt2WzBdIC8gbGVuLCB2WzFdIC8gbGVuLCB2WzJdIC8gbGVuXVxyXG59XHJcblxyXG4vKiog5Y+J5LmYICovXHJcbmZ1bmN0aW9uIGNyb3NzKGE6IFAzLCBiOiBQMyk6IFAzIHtcclxuICAgIHJldHVybiBbYVsxXSAqIGJbMl0gLSBhWzJdICogYlsxXSwgYVsyXSAqIGJbMF0gLSBhWzBdICogYlsyXSwgYVswXSAqIGJbMV0gLSBhWzFdICogYlswXV1cclxufVxyXG5cclxuLyoqIOaJq+aOoOeOr++8muaIqumdouS4reW/gyArIOWfuuWQkemHj++8iOazlee6vy/lia/ms5Xnur/vvIkrIOakreWchuWNiuW+hO+8iOWxgOmDqOWdkOagh+ezu++8iSAqL1xyXG5pbnRlcmZhY2UgU3dlZXBSaW5nIHtcclxuICAgIGM6IFAzXHJcbiAgICBuOiBQM1xyXG4gICAgYjogUDNcclxuICAgIHI6IG51bWJlclxyXG4gICAgc3g6IG51bWJlclxyXG4gICAgc3k6IG51bWJlclxyXG59XHJcblxyXG4vKipcclxuICog5omr5o6g566h77ya5rK/IDNEIOi3r+W+hOaJq+aOoOakreWchuaIqumdou+8iOiAs+actS/lsL7lt7Qv5Zub6IKiL+WRhuavm++8ieOAglxyXG4gKlxyXG4gKiDkvb/nlKhcIuW5s+ihjOS8oOi+k1wi5qGG5p626YCQ6IqC54K55o6o6L+b5oiq6Z2i77yM6YG/5YWN6Lev5b6E6L2s5byv5pe25oiq6Z2i5omt6L2s5pKV6KOC77ybXHJcbiAqIOmmluWwvuiHquWKqOihpSoq5Y2K55CD5bCB5aS0KirvvIhRIOeJiOmAoOWei+mHjOaJgOacieacq+err+mDveW/hemhu+aYr+WchueahO+8jOWQpuWImeS7juS+p+WQjuaWueiDveeci+WIsFwi56m65b+D566h5Y+jXCLvvInjgIJcclxuICpcclxuICogQHBhcmFtIG5vZGVzIOi3r+W+hOiKgueCue+8iOS9jee9riArIOaIqumdouWNiuW+hCArIOWPr+mAieakreWchue8qeaUvu+8iVxyXG4gKiBAcGFyYW0gcmFkaWFsIOWchuWRqOWIhuautVxyXG4gKiBAcGFyYW0gY2FwU3RlcHMg5bCB5aS057uG5YiG77yIMCA9IOS4jeWwgeWktO+8mzMg5bey6Laz5aSf5ZyG5ram77yJXHJcbiAqL1xyXG5leHBvcnQgZnVuY3Rpb24gc3dlZXAobm9kZXM6IFN3ZWVwTm9kZVtdLCByYWRpYWwgPSAxOCwgY2FwU3RlcHMgPSAzKTogR2VvIHtcclxuICAgIGNvbnN0IGNvdW50ID0gbm9kZXMubGVuZ3RoXHJcbiAgICBjb25zdCBwb3NpdGlvbnM6IG51bWJlcltdID0gW11cclxuICAgIGNvbnN0IG5vcm1hbHM6IG51bWJlcltdID0gW11cclxuICAgIGNvbnN0IHV2czogbnVtYmVyW10gPSBbXVxyXG4gICAgY29uc3QgaW5kaWNlczogbnVtYmVyW10gPSBbXVxyXG5cclxuICAgIC8vIOavj+S4quiKgueCueeahOWIh+e6v1xyXG4gICAgY29uc3QgdGFuZ2VudHM6IFAzW10gPSBbXVxyXG4gICAgZm9yIChsZXQgaSA9IDA7IGkgPCBjb3VudDsgaSsrKSB7XHJcbiAgICAgICAgY29uc3QgcHJldiA9IG5vZGVzW01hdGgubWF4KDAsIGkgLSAxKV0ucFxyXG4gICAgICAgIGNvbnN0IG5leHQgPSBub2Rlc1tNYXRoLm1pbihjb3VudCAtIDEsIGkgKyAxKV0ucFxyXG4gICAgICAgIHRhbmdlbnRzLnB1c2gobm9ybShbbmV4dFswXSAtIHByZXZbMF0sIG5leHRbMV0gLSBwcmV2WzFdLCBuZXh0WzJdIC0gcHJldlsyXV0pKVxyXG4gICAgfVxyXG5cclxuICAgIC8vIOW5s+ihjOS8oOi+k++8mumAkOiKgueCueaOqOi/m+aIqumdouWfuuWQkemHj++8iOmBv+WFjeaApei9rOW8r+WkhOaJrei9rOaSleijgu+8iVxyXG4gICAgY29uc3QgcmVmOiBQMyA9IE1hdGguYWJzKHRhbmdlbnRzWzBdWzFdKSA+IDAuOTIgPyBbMSwgMCwgMF0gOiBbMCwgMSwgMF1cclxuICAgIGxldCBub3JtYWwgPSBub3JtKGNyb3NzKHRhbmdlbnRzWzBdLCByZWYpKVxyXG4gICAgY29uc3QgcmluZ3M6IFN3ZWVwUmluZ1tdID0gW11cclxuICAgIGZvciAobGV0IGkgPSAwOyBpIDwgY291bnQ7IGkrKykge1xyXG4gICAgICAgIGNvbnN0IHQgPSB0YW5nZW50c1tpXVxyXG4gICAgICAgIGNvbnN0IGRvdCA9IG5vcm1hbFswXSAqIHRbMF0gKyBub3JtYWxbMV0gKiB0WzFdICsgbm9ybWFsWzJdICogdFsyXVxyXG4gICAgICAgIGxldCBuOiBQMyA9IG5vcm0oW25vcm1hbFswXSAtIHRbMF0gKiBkb3QsIG5vcm1hbFsxXSAtIHRbMV0gKiBkb3QsIG5vcm1hbFsyXSAtIHRbMl0gKiBkb3RdKVxyXG4gICAgICAgIGlmICghaXNGaW5pdGUoblswXSkgfHwgIWlzRmluaXRlKG5bMV0pIHx8ICFpc0Zpbml0ZShuWzJdKSkge1xyXG4gICAgICAgICAgICBuID0gbm9ybShjcm9zcyh0LCByZWYpKVxyXG4gICAgICAgIH1cclxuICAgICAgICBub3JtYWwgPSBuXHJcbiAgICAgICAgY29uc3QgYmlub3JtYWwgPSBub3JtKGNyb3NzKHQsIG5vcm1hbCkpXHJcbiAgICAgICAgY29uc3Qgbm9kZSA9IG5vZGVzW2ldXHJcbiAgICAgICAgcmluZ3MucHVzaCh7XHJcbiAgICAgICAgICAgIGM6IG5vZGUucCxcclxuICAgICAgICAgICAgbjogbm9ybWFsLFxyXG4gICAgICAgICAgICBiOiBiaW5vcm1hbCxcclxuICAgICAgICAgICAgcjogbm9kZS5yLFxyXG4gICAgICAgICAgICBzeDogbm9kZS5zeCA9PT0gdW5kZWZpbmVkID8gMSA6IG5vZGUuc3gsXHJcbiAgICAgICAgICAgIHN5OiBub2RlLnN5ID09PSB1bmRlZmluZWQgPyAxIDogbm9kZS5zeSxcclxuICAgICAgICB9KVxyXG4gICAgfVxyXG5cclxuICAgIC8vIOWNiueQg+WwgeWktO+8muS7peerr+eCueaIqumdouS4uui1pOmBk++8jOayv+WIh+e6v+WkluS+p+aMiSBjb3Mg5pS257yp5Yiw5p6B54K5XHJcbiAgICBpZiAoY2FwU3RlcHMgPiAwICYmIGNvdW50ID49IDIpIHtcclxuICAgICAgICBjb25zdCBzdGFydENhcDogU3dlZXBSaW5nW10gPSBbXVxyXG4gICAgICAgIGNvbnN0IGhlYWQgPSByaW5nc1swXVxyXG4gICAgICAgIGNvbnN0IGRpclN0YXJ0OiBQMyA9IFstdGFuZ2VudHNbMF1bMF0sIC10YW5nZW50c1swXVsxXSwgLXRhbmdlbnRzWzBdWzJdXVxyXG4gICAgICAgIGZvciAobGV0IGsgPSBjYXBTdGVwczsgayA+PSAxOyBrIC09IDEpIHtcclxuICAgICAgICAgICAgY29uc3QgYW5nbGUgPSAoayAvIGNhcFN0ZXBzKSAqIChNYXRoLlBJIC8gMilcclxuICAgICAgICAgICAgY29uc3QgcyA9IE1hdGguc2luKGFuZ2xlKVxyXG4gICAgICAgICAgICBjb25zdCBjID0gTWF0aC5jb3MoYW5nbGUpXHJcbiAgICAgICAgICAgIHN0YXJ0Q2FwLnB1c2goe1xyXG4gICAgICAgICAgICAgICAgYzogW2hlYWQuY1swXSArIGRpclN0YXJ0WzBdICogaGVhZC5yICogcywgaGVhZC5jWzFdICsgZGlyU3RhcnRbMV0gKiBoZWFkLnIgKiBzLCBoZWFkLmNbMl0gKyBkaXJTdGFydFsyXSAqIGhlYWQuciAqIHNdLFxyXG4gICAgICAgICAgICAgICAgbjogaGVhZC5uLCBiOiBoZWFkLmIsIHI6IGhlYWQuciAqIGMsIHN4OiBoZWFkLnN4LCBzeTogaGVhZC5zeSxcclxuICAgICAgICAgICAgfSlcclxuICAgICAgICB9XHJcbiAgICAgICAgY29uc3QgZW5kQ2FwOiBTd2VlcFJpbmdbXSA9IFtdXHJcbiAgICAgICAgY29uc3QgbGFzdCA9IHJpbmdzW3JpbmdzLmxlbmd0aCAtIDFdXHJcbiAgICAgICAgY29uc3QgZGlyRW5kID0gdGFuZ2VudHNbY291bnQgLSAxXVxyXG4gICAgICAgIGZvciAobGV0IGsgPSAxOyBrIDw9IGNhcFN0ZXBzOyBrICs9IDEpIHtcclxuICAgICAgICAgICAgY29uc3QgYW5nbGUgPSAoayAvIGNhcFN0ZXBzKSAqIChNYXRoLlBJIC8gMilcclxuICAgICAgICAgICAgY29uc3QgcyA9IE1hdGguc2luKGFuZ2xlKVxyXG4gICAgICAgICAgICBjb25zdCBjID0gTWF0aC5jb3MoYW5nbGUpXHJcbiAgICAgICAgICAgIGVuZENhcC5wdXNoKHtcclxuICAgICAgICAgICAgICAgIGM6IFtsYXN0LmNbMF0gKyBkaXJFbmRbMF0gKiBsYXN0LnIgKiBzLCBsYXN0LmNbMV0gKyBkaXJFbmRbMV0gKiBsYXN0LnIgKiBzLCBsYXN0LmNbMl0gKyBkaXJFbmRbMl0gKiBsYXN0LnIgKiBzXSxcclxuICAgICAgICAgICAgICAgIG46IGxhc3QubiwgYjogbGFzdC5iLCByOiBsYXN0LnIgKiBjLCBzeDogbGFzdC5zeCwgc3k6IGxhc3Quc3ksXHJcbiAgICAgICAgICAgIH0pXHJcbiAgICAgICAgfVxyXG4gICAgICAgIHJpbmdzLnVuc2hpZnQoLi4uc3RhcnRDYXApXHJcbiAgICAgICAgcmluZ3MucHVzaCguLi5lbmRDYXApXHJcbiAgICB9XHJcblxyXG4gICAgY29uc3QgdG90YWwgPSByaW5ncy5sZW5ndGhcclxuICAgIGZvciAobGV0IGkgPSAwOyBpIDwgdG90YWw7IGkrKykge1xyXG4gICAgICAgIGNvbnN0IHJpbmcgPSByaW5nc1tpXVxyXG4gICAgICAgIGNvbnN0IHYgPSB0b3RhbCA+IDEgPyBpIC8gKHRvdGFsIC0gMSkgOiAwXHJcbiAgICAgICAgZm9yIChsZXQgaiA9IDA7IGogPD0gcmFkaWFsOyBqKyspIHtcclxuICAgICAgICAgICAgY29uc3QgdSA9IGogLyByYWRpYWxcclxuICAgICAgICAgICAgY29uc3QgdGhldGEgPSB1ICogTWF0aC5QSSAqIDJcclxuICAgICAgICAgICAgY29uc3QgY29zID0gTWF0aC5jb3ModGhldGEpXHJcbiAgICAgICAgICAgIGNvbnN0IHNpbiA9IE1hdGguc2luKHRoZXRhKVxyXG4gICAgICAgICAgICBwb3NpdGlvbnMucHVzaChcclxuICAgICAgICAgICAgICAgIHJpbmcuY1swXSArIChyaW5nLm5bMF0gKiBjb3MgKiByaW5nLnN4ICsgcmluZy5iWzBdICogc2luICogcmluZy5zeSkgKiByaW5nLnIsXHJcbiAgICAgICAgICAgICAgICByaW5nLmNbMV0gKyAocmluZy5uWzFdICogY29zICogcmluZy5zeCArIHJpbmcuYlsxXSAqIHNpbiAqIHJpbmcuc3kpICogcmluZy5yLFxyXG4gICAgICAgICAgICAgICAgcmluZy5jWzJdICsgKHJpbmcublsyXSAqIGNvcyAqIHJpbmcuc3ggKyByaW5nLmJbMl0gKiBzaW4gKiByaW5nLnN5KSAqIHJpbmcucixcclxuICAgICAgICAgICAgKVxyXG4gICAgICAgICAgICBjb25zdCBucm0gPSBub3JtKFtcclxuICAgICAgICAgICAgICAgIHJpbmcublswXSAqIGNvcyAqIHJpbmcuc3kgKyByaW5nLmJbMF0gKiBzaW4gKiByaW5nLnN4LFxyXG4gICAgICAgICAgICAgICAgcmluZy5uWzFdICogY29zICogcmluZy5zeSArIHJpbmcuYlsxXSAqIHNpbiAqIHJpbmcuc3gsXHJcbiAgICAgICAgICAgICAgICByaW5nLm5bMl0gKiBjb3MgKiByaW5nLnN5ICsgcmluZy5iWzJdICogc2luICogcmluZy5zeCxcclxuICAgICAgICAgICAgXSlcclxuICAgICAgICAgICAgbm9ybWFscy5wdXNoKG5ybVswXSwgbnJtWzFdLCBucm1bMl0pXHJcbiAgICAgICAgICAgIHV2cy5wdXNoKHUsIHYpXHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG5cclxuICAgIGNvbnN0IHN0cmlkZSA9IHJhZGlhbCArIDFcclxuICAgIGZvciAobGV0IGkgPSAwOyBpIDwgdG90YWwgLSAxOyBpKyspIHtcclxuICAgICAgICBmb3IgKGxldCBqID0gMDsgaiA8IHJhZGlhbDsgaisrKSB7XHJcbiAgICAgICAgICAgIGNvbnN0IGEgPSBpICogc3RyaWRlICsgalxyXG4gICAgICAgICAgICBjb25zdCBiID0gYSArIDFcclxuICAgICAgICAgICAgY29uc3QgYyA9IGEgKyBzdHJpZGVcclxuICAgICAgICAgICAgY29uc3QgZCA9IGMgKyAxXHJcbiAgICAgICAgICAgIC8vIOe7leW6j+ivtOaYju+8mmJpbm9ybWFsID0gY3Jvc3ModGFuZ2VudCwgbm9ybWFsKe+8jOaIqumdoiDOuCDmlrnlkJHkuI4gb3V0d2FyZChub3JtYWwpIOebuOWPje+8jFxyXG4gICAgICAgICAgICAvLyDlm6DmraTpobrluo/pnIDkuI4gbGF0aGUg55u45Y+N77yM5omN6IO95L+d6K+B5q2j6Z2i5pyd5aSW77yI5ZCm5YiZ5Lya6KKr6IOM6Z2i5YmU6Zmk77yJ44CCXHJcbiAgICAgICAgICAgIGluZGljZXMucHVzaChhLCBiLCBjLCBiLCBkLCBjKVxyXG4gICAgICAgIH1cclxuICAgIH1cclxuICAgIHJldHVybiB7IHBvc2l0aW9ucywgbm9ybWFscywgdXZzLCBpbmRpY2VzIH1cclxufVxyXG5cclxuLyoqIOasp+aLieinku+8iFhZWiDpobrluo/vvIzlvKfluqbvvInihpIgM8OXMyDml4vovaznn6npmLXvvIjooYzkuLvluo/vvIkgKi9cclxuZnVuY3Rpb24gZXVsZXJNYXRyaXgocm90OiBQMyk6IG51bWJlcltdIHtcclxuICAgIGNvbnN0IFtyeCwgcnksIHJ6XSA9IHJvdFxyXG4gICAgY29uc3QgY3ggPSBNYXRoLmNvcyhyeCksIHN4ID0gTWF0aC5zaW4ocngpXHJcbiAgICBjb25zdCBjeSA9IE1hdGguY29zKHJ5KSwgc3kgPSBNYXRoLnNpbihyeSlcclxuICAgIGNvbnN0IGN6ID0gTWF0aC5jb3MocnopLCBzeiA9IE1hdGguc2luKHJ6KVxyXG4gICAgcmV0dXJuIFtcclxuICAgICAgICBjeSAqIGN6LCAtY3kgKiBzeiwgc3ksXHJcbiAgICAgICAgc3ggKiBzeSAqIGN6ICsgY3ggKiBzeiwgLXN4ICogc3kgKiBzeiArIGN4ICogY3osIC1zeCAqIGN5LFxyXG4gICAgICAgIC1jeCAqIHN5ICogY3ogKyBzeCAqIHN6LCBjeCAqIHN5ICogc3ogKyBzeCAqIGN6LCBjeCAqIGN5LFxyXG4gICAgXVxyXG59XHJcblxyXG4vKiog5Yeg5L2V5L2T5Y+Y5o2i77yI5L2N572uL+aXi+i9rC/nvKnmlL7vvJvms5Xnur/nlKjml4vovaznn6npmLXlj5jmjaLvvIzpmo/lkI7lvZLkuIDljJbvvIkgKi9cclxuZXhwb3J0IGZ1bmN0aW9uIHRyYW5zZm9ybWVkKGdlbzogR2VvLCB0OiBHZW9UcmFuc2Zvcm0pOiBHZW8ge1xyXG4gICAgY29uc3Qgcm90ID0gdC5yb3QgfHwgWzAsIDAsIDBdXHJcbiAgICBjb25zdCBzY2FsZTogUDMgPSB0LnNjYWxlIHx8IFsxLCAxLCAxXVxyXG4gICAgY29uc3QgcG9zOiBQMyA9IHQucG9zIHx8IFswLCAwLCAwXVxyXG4gICAgY29uc3QgbSA9IGV1bGVyTWF0cml4KHJvdClcclxuICAgIGNvbnN0IHBvc2l0aW9uczogbnVtYmVyW10gPSBbXVxyXG4gICAgY29uc3Qgbm9ybWFsczogbnVtYmVyW10gPSBbXVxyXG4gICAgY29uc3QgY291bnQgPSBnZW8ucG9zaXRpb25zLmxlbmd0aCAvIDNcclxuICAgIGZvciAobGV0IGkgPSAwOyBpIDwgY291bnQ7IGkrKykge1xyXG4gICAgICAgIGNvbnN0IHggPSBnZW8ucG9zaXRpb25zW2kgKiAzXSAqIHNjYWxlWzBdXHJcbiAgICAgICAgY29uc3QgeSA9IGdlby5wb3NpdGlvbnNbaSAqIDMgKyAxXSAqIHNjYWxlWzFdXHJcbiAgICAgICAgY29uc3QgeiA9IGdlby5wb3NpdGlvbnNbaSAqIDMgKyAyXSAqIHNjYWxlWzJdXHJcbiAgICAgICAgcG9zaXRpb25zLnB1c2goXHJcbiAgICAgICAgICAgIG1bMF0gKiB4ICsgbVsxXSAqIHkgKyBtWzJdICogeiArIHBvc1swXSxcclxuICAgICAgICAgICAgbVszXSAqIHggKyBtWzRdICogeSArIG1bNV0gKiB6ICsgcG9zWzFdLFxyXG4gICAgICAgICAgICBtWzZdICogeCArIG1bN10gKiB5ICsgbVs4XSAqIHogKyBwb3NbMl0sXHJcbiAgICAgICAgKVxyXG4gICAgICAgIGNvbnN0IG54ID0gZ2VvLm5vcm1hbHNbaSAqIDNdXHJcbiAgICAgICAgY29uc3QgbnkgPSBnZW8ubm9ybWFsc1tpICogMyArIDFdXHJcbiAgICAgICAgY29uc3QgbnogPSBnZW8ubm9ybWFsc1tpICogMyArIDJdXHJcbiAgICAgICAgY29uc3QgdHggPSBtWzBdICogbnggKyBtWzFdICogbnkgKyBtWzJdICogbnpcclxuICAgICAgICBjb25zdCB0eSA9IG1bM10gKiBueCArIG1bNF0gKiBueSArIG1bNV0gKiBuelxyXG4gICAgICAgIGNvbnN0IHR6ID0gbVs2XSAqIG54ICsgbVs3XSAqIG55ICsgbVs4XSAqIG56XHJcbiAgICAgICAgY29uc3QgbGVuID0gTWF0aC5oeXBvdCh0eCwgdHksIHR6KSB8fCAxXHJcbiAgICAgICAgbm9ybWFscy5wdXNoKHR4IC8gbGVuLCB0eSAvIGxlbiwgdHogLyBsZW4pXHJcbiAgICB9XHJcbiAgICByZXR1cm4geyBwb3NpdGlvbnMsIG5vcm1hbHMsIHV2czogZ2VvLnV2cy5zbGljZSgpLCBpbmRpY2VzOiBnZW8uaW5kaWNlcy5zbGljZSgpIH1cclxufVxyXG5cclxuLyoqIOWQiOW5tuWkmuS4quWHoOS9leS9k+S4uuS4gOS4qu+8iOWQjOS4gOadkOi0qOS4i+WHj+WwkSBkcmF3IGNhbGzvvJvntKLlvJXoh6rliqjlgY/np7vvvIkgKi9cclxuZXhwb3J0IGZ1bmN0aW9uIG1lcmdlKC4uLmdlb3M6IEdlb1tdKTogR2VvIHtcclxuICAgIGNvbnN0IG91dDogR2VvID0geyBwb3NpdGlvbnM6IFtdLCBub3JtYWxzOiBbXSwgdXZzOiBbXSwgaW5kaWNlczogW10gfVxyXG4gICAgbGV0IGJhc2UgPSAwXHJcbiAgICBmb3IgKGNvbnN0IGdlbyBvZiBnZW9zKSB7XHJcbiAgICAgICAgb3V0LnBvc2l0aW9ucy5wdXNoKC4uLmdlby5wb3NpdGlvbnMpXHJcbiAgICAgICAgb3V0Lm5vcm1hbHMucHVzaCguLi5nZW8ubm9ybWFscylcclxuICAgICAgICBvdXQudXZzLnB1c2goLi4uZ2VvLnV2cylcclxuICAgICAgICBmb3IgKGNvbnN0IGluZGV4IG9mIGdlby5pbmRpY2VzKSB7XHJcbiAgICAgICAgICAgIG91dC5pbmRpY2VzLnB1c2goaW5kZXggKyBiYXNlKVxyXG4gICAgICAgIH1cclxuICAgICAgICBiYXNlICs9IGdlby5wb3NpdGlvbnMubGVuZ3RoIC8gM1xyXG4gICAgfVxyXG4gICAgcmV0dXJuIG91dFxyXG59XHJcblxyXG4vKipcclxuICog5bmz5ruR5rOV57q/77ya5oyJ5L2N572u5oqK6YeN5ZCI6aG254K55YiG57uE5rGC5bmz5Z2H5ZCO5YaZ5Zue44CCXHJcbiAqXHJcbiAqIOeUqOmAlO+8muaKiuWkmuS4qumDqOS7tu+8iOWmguWktCArIOiFriArIOmineWktO+8ieeEiuaOpeWkhOeahOazlee6v+e7n+S4gO+8jFxyXG4gKiDmtojpmaRcIuS4pOS4queQg+ebuOS6pOWkhOWHuueOsOehrOi+uS/kuq7nur9cIueahOW7ieS7t+aEn+OAglxyXG4gKi9cclxuZXhwb3J0IGZ1bmN0aW9uIHNtb290aE5vcm1hbHMoZ2VvOiBHZW8sIHByZWNpc2lvbiA9IDQpOiBHZW8ge1xyXG4gICAgY29uc3QgbWFwID0gbmV3IE1hcDxzdHJpbmcsIG51bWJlcltdPigpXHJcbiAgICBjb25zdCBjb3VudCA9IGdlby5wb3NpdGlvbnMubGVuZ3RoIC8gM1xyXG4gICAgY29uc3Qga2V5ID0gKGk6IG51bWJlcik6IHN0cmluZyA9PlxyXG4gICAgICAgIGdlby5wb3NpdGlvbnNbaSAqIDNdLnRvRml4ZWQocHJlY2lzaW9uKSArICcsJyArXHJcbiAgICAgICAgZ2VvLnBvc2l0aW9uc1tpICogMyArIDFdLnRvRml4ZWQocHJlY2lzaW9uKSArICcsJyArXHJcbiAgICAgICAgZ2VvLnBvc2l0aW9uc1tpICogMyArIDJdLnRvRml4ZWQocHJlY2lzaW9uKVxyXG4gICAgZm9yIChsZXQgaSA9IDA7IGkgPCBjb3VudDsgaSsrKSB7XHJcbiAgICAgICAgY29uc3QgayA9IGtleShpKVxyXG4gICAgICAgIGNvbnN0IGFjYyA9IG1hcC5nZXQoaylcclxuICAgICAgICBpZiAoYWNjKSB7XHJcbiAgICAgICAgICAgIGFjY1swXSArPSBnZW8ubm9ybWFsc1tpICogM11cclxuICAgICAgICAgICAgYWNjWzFdICs9IGdlby5ub3JtYWxzW2kgKiAzICsgMV1cclxuICAgICAgICAgICAgYWNjWzJdICs9IGdlby5ub3JtYWxzW2kgKiAzICsgMl1cclxuICAgICAgICB9IGVsc2Uge1xyXG4gICAgICAgICAgICBtYXAuc2V0KGssIFtnZW8ubm9ybWFsc1tpICogM10sIGdlby5ub3JtYWxzW2kgKiAzICsgMV0sIGdlby5ub3JtYWxzW2kgKiAzICsgMl1dKVxyXG4gICAgICAgIH1cclxuICAgIH1cclxuICAgIGNvbnN0IG5vcm1hbHMgPSBnZW8ubm9ybWFscy5zbGljZSgpXHJcbiAgICBmb3IgKGxldCBpID0gMDsgaSA8IGNvdW50OyBpKyspIHtcclxuICAgICAgICBjb25zdCBhY2MgPSBtYXAuZ2V0KGtleShpKSkhXHJcbiAgICAgICAgY29uc3QgbGVuID0gTWF0aC5oeXBvdChhY2NbMF0sIGFjY1sxXSwgYWNjWzJdKSB8fCAxXHJcbiAgICAgICAgbm9ybWFsc1tpICogM10gPSBhY2NbMF0gLyBsZW5cclxuICAgICAgICBub3JtYWxzW2kgKiAzICsgMV0gPSBhY2NbMV0gLyBsZW5cclxuICAgICAgICBub3JtYWxzW2kgKiAzICsgMl0gPSBhY2NbMl0gLyBsZW5cclxuICAgIH1cclxuICAgIHJldHVybiB7IHBvc2l0aW9uczogZ2VvLnBvc2l0aW9ucy5zbGljZSgpLCBub3JtYWxzLCB1dnM6IGdlby51dnMuc2xpY2UoKSwgaW5kaWNlczogZ2VvLmluZGljZXMuc2xpY2UoKSB9XHJcbn1cclxuXHJcbi8qKlxyXG4gKiDnlLFcIuWNiuW+hC3pq5jluqZcIuaOp+WItueCueeUn+aIkOmhuua7keeahOaXi+i9rOS9k+i9ruW7k++8iENhdG11bGwtUm9tIOaPkuWAvO+8ieOAglxyXG4gKlxyXG4gKiDlpb3lpITvvJrpgKDlnovlj6rlhpnlsJHph4/lhbPplK7ngrnvvIjlpoLlpLTpg6jnmoTpop0v6aKKL+S4i+W3tOWuveW6pu+8ie+8jFxyXG4gKiDnlLHmj5LlgLznlJ/miJDlh6DljYHlsYLova7lu5PvvIzkv53or4Hmm7LpnaLov57nu63vvIjkuI3kvJrlh7rnjrDmipjop5LvvInjgIJcclxuICovXHJcbi8qKlxyXG4gKiDml4vovazkvZPova7lu5PlnKjmjIflrprpq5jluqbnmoTljYrlvoTvvIjnur/mgKfmj5LlgLzvvInjgIJcclxuICpcclxuICog55So6YCU77ya5aS06YOo5pivIGxhdGhlIOeUn+aIkOeahOaXi+i9rOS9k++8jOS6lOWumOWumuS9jeaXtumcgOimgeefpemBk1wi6IS45Zyo5p+Q5Liq6auY5bqm5pyJ5aSa5a69XCLvvIxcclxuICog5o2u5q2k5oqK55y8552bL+m8u+WtkC/lmLTotLTliLDmm7LpnaLlpJbkvqfnmoTmraPnoa7kvY3nva7jgIJcclxuICovXHJcbmV4cG9ydCBmdW5jdGlvbiBwcm9maWxlQXQocHJvZmlsZTogQXJyYXk8W251bWJlciwgbnVtYmVyXT4sIHk6IG51bWJlcik6IG51bWJlciB7XHJcbiAgICBpZiAocHJvZmlsZS5sZW5ndGggPT09IDApIHtcclxuICAgICAgICByZXR1cm4gMFxyXG4gICAgfVxyXG4gICAgaWYgKHkgPD0gcHJvZmlsZVswXVsxXSkge1xyXG4gICAgICAgIHJldHVybiBwcm9maWxlWzBdWzBdXHJcbiAgICB9XHJcbiAgICBjb25zdCBsYXN0ID0gcHJvZmlsZVtwcm9maWxlLmxlbmd0aCAtIDFdXHJcbiAgICBpZiAoeSA+PSBsYXN0WzFdKSB7XHJcbiAgICAgICAgcmV0dXJuIGxhc3RbMF1cclxuICAgIH1cclxuICAgIGZvciAobGV0IGkgPSAwOyBpIDwgcHJvZmlsZS5sZW5ndGggLSAxOyBpKyspIHtcclxuICAgICAgICBjb25zdCBhID0gcHJvZmlsZVtpXVxyXG4gICAgICAgIGNvbnN0IGIgPSBwcm9maWxlW2kgKyAxXVxyXG4gICAgICAgIGlmICh5ID49IGFbMV0gJiYgeSA8PSBiWzFdKSB7XHJcbiAgICAgICAgICAgIGNvbnN0IHNwYW4gPSBiWzFdIC0gYVsxXVxyXG4gICAgICAgICAgICBjb25zdCB0ID0gc3BhbiA8IDFlLTYgPyAwIDogKHkgLSBhWzFdKSAvIHNwYW5cclxuICAgICAgICAgICAgcmV0dXJuIGFbMF0gKyAoYlswXSAtIGFbMF0pICogdFxyXG4gICAgICAgIH1cclxuICAgIH1cclxuICAgIHJldHVybiBsYXN0WzBdXHJcbn1cclxuXHJcbi8qKlxyXG4gKiDml4vovazkvZPliY3ooajpnaLmt7HluqbvvJrnu5nlrpogKHgsIHkpIOaxguivpeWkhOihqOmdouWcqOWJjeWNiueQg+eahCB644CCXHJcbiAqXHJcbiAqIOmdoumDqOaJgOaciemDqOS7tu+8iOecvC/pvLsv5Zi0L+iFrue6ou+8iemDveW6lOmAmui/h+Wug+WumuS9je+8jFxyXG4gKiDkv53or4HkupTlrpjotLTlkIjpnaLpg6jmm7LnjofvvIjlkKbliJnkvJrlg4/pm7bku7bmgqzmta7lnKjohLjliY3vvInjgIJcclxuICog6LaF5Ye66L2u5buT6IyD5Zu05pe26L+U5ZueIDDvvIjosIPnlKjmlrnlupTkv53or4EgeCDlnKjohLjpoorojIPlm7TlhoXvvInjgIJcclxuICpcclxuICogQHBhcmFtIHByb2ZpbGUg5peL6L2s5L2T5L6n6L2u5buT77yI5bGA6YOo5Z2Q5qCH77yM6KeBIGxhdGhl77yJXHJcbiAqIEBwYXJhbSB4IOebuOWvueaXi+i9rOi9tOeahOawtOW5s+WBj+enu1xyXG4gKiBAcGFyYW0geSDnm7jlr7nml4vovazkvZPljp/ngrnnmoTlnoLnm7TlgY/np7tcclxuICogQHBhcmFtIHpTY2FsZSDnlJ/miJDlh6DkvZXml7YgeiDmlrnlkJHnmoTnvKnmlL7ns7vmlbDvvIjpu5jorqQgMe+8iVxyXG4gKi9cclxuZXhwb3J0IGZ1bmN0aW9uIGZyb250Wihwcm9maWxlOiBBcnJheTxbbnVtYmVyLCBudW1iZXJdPiwgeDogbnVtYmVyLCB5OiBudW1iZXIsIHpTY2FsZSA9IDEpOiBudW1iZXIge1xyXG4gICAgY29uc3QgcmFkaXVzID0gcHJvZmlsZUF0KHByb2ZpbGUsIHkpXHJcbiAgICBjb25zdCBpbm5lciA9IHJhZGl1cyAqIHJhZGl1cyAtIHggKiB4XHJcbiAgICByZXR1cm4gaW5uZXIgPD0gMCA/IDAgOiBNYXRoLnNxcnQoaW5uZXIpICogelNjYWxlXHJcbn1cclxuXHJcbi8qKlxyXG4gKiDnkIPpnaLniYfvvJrotLTlnKjnkIPpnaLkuIrnmoRcIuakreWchuW4vVwi77yM55So5LqO6ZyA6KaBKirotLTlkIjmm7LpnaLmm7LnjocqKueahOmDqOS7tlxyXG4gKiDvvIjoha7nuqLnmoTmn5TlkozoibLmlpHjgIHotLTlnKjnnLznkIPooajpnaLnmoTpq5jlhYnniYfvvInjgIJcclxuICpcclxuICog6aG254K55pa55ZCR5p2l6Ieq5YiH5bmz6Z2i5qSt5ZyG5Y+C5pWw5YyW77yaZGlyKHQsIM+GKSA9IG5vcm1hbGl6ZSh3ICsgdcK3dMK3dGFuVcK3Y29zz4YgKyB2wrd0wrd0YW5WwrdzaW7PhinvvIxcclxuICogdCDiiIggWzAsMV0g5piv5Yiw54mH5Lit5b+D55qE5b2S5LiA5YyW5b6E5ZCR6Led56a777yM5YaZ5YWlIHV2LnjvvIjmn5TlkowgYWxwaGEg6KGw5YeP55u05o6l6K+75a6D77yJ44CCXHJcbiAqXHJcbiAqIOeUqOeQg+mdoueJh+iAjOS4jeaYr+aJgeW5s+akreeQg+eahOWOn+WboO+8muaJgeW5s+eJh+i0tOWIsOWHuOabsumdouS4iuaXtui+uee8mOS8muemu+W8gOihqOmdou+8iOaCrOa1ruaEn++8ie+8jFxyXG4gKiDnkIPpnaLniYfnmoTmm7LnjofkuI7lpLTpg6gv55y855CD5LiA6Ie077yM6KeG6KeJ5LiK5pivXCLplb/lnKjohLjkuIpcIuiAjOS4jeaYr1wi6LS05LiK5Y67XCLjgIJcclxuICpcclxuICogQHBhcmFtIGRpciDniYfkuK3lv4PmlrnlkJHvvIjnkIPlv4PmjIflkJHniYfkuK3lv4PvvIlcclxuICogQHBhcmFtIGFuZ2xlVSDmsLTlubPlvKDop5LvvIjlvKfluqbvvIlcclxuICogQHBhcmFtIGFuZ2xlViDlnoLnm7TlvKDop5LvvIjlvKfluqbvvIlcclxuICogQHBhcmFtIHJhZGlpIOakreeQg+S4iei9tOWNiuW+hFxyXG4gKi9cclxuZXhwb3J0IGZ1bmN0aW9uIHNwaGVyZVBhdGNoKGRpcjogUDMsIGFuZ2xlVTogbnVtYmVyLCBhbmdsZVY6IG51bWJlciwgcmFkaWk6IFAzLFxyXG4gICAgICAgICAgICAgICAgICAgICAgICAgICAgc2VnbWVudHMgPSAyNiwgcmluZ3MgPSA1KTogR2VvIHtcclxuICAgIGNvbnN0IHcgPSBub3JtKGRpcilcclxuICAgIGNvbnN0IHJlZjogUDMgPSBNYXRoLmFicyh3WzFdKSA+IDAuOSA/IFsxLCAwLCAwXSA6IFswLCAxLCAwXVxyXG4gICAgY29uc3QgdSA9IG5vcm0oY3Jvc3MocmVmLCB3KSlcclxuICAgIGNvbnN0IHYgPSBjcm9zcyh3LCB1KVxyXG4gICAgY29uc3QgdGFuVSA9IE1hdGgudGFuKGFuZ2xlVSlcclxuICAgIGNvbnN0IHRhblYgPSBNYXRoLnRhbihhbmdsZVYpXHJcbiAgICBjb25zdCBwb3NpdGlvbnM6IG51bWJlcltdID0gW11cclxuICAgIGNvbnN0IG5vcm1hbHM6IG51bWJlcltdID0gW11cclxuICAgIGNvbnN0IHV2czogbnVtYmVyW10gPSBbXVxyXG4gICAgY29uc3QgaW5kaWNlczogbnVtYmVyW10gPSBbXVxyXG5cclxuICAgIGZvciAobGV0IGkgPSAwOyBpIDw9IHJpbmdzOyBpKyspIHtcclxuICAgICAgICBjb25zdCB0ID0gaSAvIHJpbmdzXHJcbiAgICAgICAgZm9yIChsZXQgaiA9IDA7IGogPD0gc2VnbWVudHM7IGorKykge1xyXG4gICAgICAgICAgICBjb25zdCBwaGkgPSAoaiAvIHNlZ21lbnRzKSAqIE1hdGguUEkgKiAyXHJcbiAgICAgICAgICAgIGNvbnN0IGR4ID0gTWF0aC5jb3MocGhpKSAqIHRhblUgKiB0XHJcbiAgICAgICAgICAgIGNvbnN0IGR5ID0gTWF0aC5zaW4ocGhpKSAqIHRhblYgKiB0XHJcbiAgICAgICAgICAgIGNvbnN0IGludiA9IDEgLyBNYXRoLnNxcnQoMSArIGR4ICogZHggKyBkeSAqIGR5KVxyXG4gICAgICAgICAgICBjb25zdCBzeCA9ICh3WzBdICsgdVswXSAqIGR4ICsgdlswXSAqIGR5KSAqIGludlxyXG4gICAgICAgICAgICBjb25zdCBzeSA9ICh3WzFdICsgdVsxXSAqIGR4ICsgdlsxXSAqIGR5KSAqIGludlxyXG4gICAgICAgICAgICBjb25zdCBzeiA9ICh3WzJdICsgdVsyXSAqIGR4ICsgdlsyXSAqIGR5KSAqIGludlxyXG4gICAgICAgICAgICBwb3NpdGlvbnMucHVzaChzeCAqIHJhZGlpWzBdLCBzeSAqIHJhZGlpWzFdLCBzeiAqIHJhZGlpWzJdKVxyXG4gICAgICAgICAgICAvLyDmpK3nkIPms5Xnur/vvJrpgJDliIbph4/pmaTku6XljYrlvoTlubPmlrnlkI7lvZLkuIDljJZcclxuICAgICAgICAgICAgbGV0IG54ID0gc3ggLyAocmFkaWlbMF0gKiByYWRpaVswXSlcclxuICAgICAgICAgICAgbGV0IG55ID0gc3kgLyAocmFkaWlbMV0gKiByYWRpaVsxXSlcclxuICAgICAgICAgICAgbGV0IG56ID0gc3ogLyAocmFkaWlbMl0gKiByYWRpaVsyXSlcclxuICAgICAgICAgICAgY29uc3QgbmwgPSBNYXRoLmh5cG90KG54LCBueSwgbnopIHx8IDFcclxuICAgICAgICAgICAgbm9ybWFscy5wdXNoKG54IC8gbmwsIG55IC8gbmwsIG56IC8gbmwpXHJcbiAgICAgICAgICAgIHV2cy5wdXNoKHQsIHBoaSAvIChNYXRoLlBJICogMikpXHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG4gICAgY29uc3Qgc3RyaWRlID0gc2VnbWVudHMgKyAxXHJcbiAgICBmb3IgKGxldCBpID0gMDsgaSA8IHJpbmdzOyBpKyspIHtcclxuICAgICAgICBmb3IgKGxldCBqID0gMDsgaiA8IHNlZ21lbnRzOyBqKyspIHtcclxuICAgICAgICAgICAgY29uc3QgYSA9IGkgKiBzdHJpZGUgKyBqXHJcbiAgICAgICAgICAgIGNvbnN0IGIgPSBhICsgMVxyXG4gICAgICAgICAgICBjb25zdCBjID0gYSArIHN0cmlkZVxyXG4gICAgICAgICAgICBjb25zdCBkID0gYyArIDFcclxuICAgICAgICAgICAgaW5kaWNlcy5wdXNoKGEsIGMsIGIsIGIsIGMsIGQpXHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG4gICAgcmV0dXJuIHsgcG9zaXRpb25zLCBub3JtYWxzLCB1dnMsIGluZGljZXMgfVxyXG59XHJcblxyXG4vKiog5oqKIHV2Lngg6YeN5YaZ5Li6XCLliLDljp/ngrnnmoTlvZLkuIDljJbot53nprtcIu+8iOWNleS9jeeQg+i0tOiKseeUqO+8m+aflOWSjCBhbHBoYSDoobDlh4/or7vlj5YgdXYueO+8iSAqL1xyXG5leHBvcnQgZnVuY3Rpb24gcmFkaWFsVVYoZ2VvOiBHZW8pOiBHZW8ge1xyXG4gICAgY29uc3QgY291bnQgPSBnZW8ucG9zaXRpb25zLmxlbmd0aCAvIDNcclxuICAgIGNvbnN0IHV2cyA9IGdlby51dnMuc2xpY2UoKVxyXG4gICAgbGV0IG1heExlbiA9IDBcclxuICAgIGZvciAobGV0IGkgPSAwOyBpIDwgY291bnQ7IGkrKykge1xyXG4gICAgICAgIGNvbnN0IGxlbiA9IE1hdGguaHlwb3QoZ2VvLnBvc2l0aW9uc1tpICogM10sIGdlby5wb3NpdGlvbnNbaSAqIDMgKyAxXSwgZ2VvLnBvc2l0aW9uc1tpICogMyArIDJdKVxyXG4gICAgICAgIGlmIChsZW4gPiBtYXhMZW4pIHtcclxuICAgICAgICAgICAgbWF4TGVuID0gbGVuXHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG4gICAgY29uc3QgaW52ID0gbWF4TGVuID4gMCA/IDEgLyBtYXhMZW4gOiAxXHJcbiAgICBmb3IgKGxldCBpID0gMDsgaSA8IGNvdW50OyBpKyspIHtcclxuICAgICAgICB1dnNbaSAqIDJdID0gTWF0aC5oeXBvdChcclxuICAgICAgICAgICAgZ2VvLnBvc2l0aW9uc1tpICogM10sIGdlby5wb3NpdGlvbnNbaSAqIDMgKyAxXSwgZ2VvLnBvc2l0aW9uc1tpICogMyArIDJdKSAqIGludlxyXG4gICAgfVxyXG4gICAgcmV0dXJuIHsgcG9zaXRpb25zOiBnZW8ucG9zaXRpb25zLnNsaWNlKCksIG5vcm1hbHM6IGdlby5ub3JtYWxzLnNsaWNlKCksIHV2cywgaW5kaWNlczogZ2VvLmluZGljZXMuc2xpY2UoKSB9XHJcbn1cclxuXHJcbmV4cG9ydCBmdW5jdGlvbiBwcm9maWxlRnJvbShjb250cm9sOiBBcnJheTxbbnVtYmVyLCBudW1iZXJdPiwgc3RlcHMgPSAxOCk6IEFycmF5PFtudW1iZXIsIG51bWJlcl0+IHtcclxuICAgIGlmIChjb250cm9sLmxlbmd0aCA8IDIpIHtcclxuICAgICAgICByZXR1cm4gY29udHJvbC5zbGljZSgpXHJcbiAgICB9XHJcbiAgICBjb25zdCBvdXQ6IEFycmF5PFtudW1iZXIsIG51bWJlcl0+ID0gW11cclxuICAgIGNvbnN0IGF0ID0gKGk6IG51bWJlcik6IFtudW1iZXIsIG51bWJlcl0gPT4gY29udHJvbFtNYXRoLm1heCgwLCBNYXRoLm1pbihjb250cm9sLmxlbmd0aCAtIDEsIGkpKV1cclxuICAgIGZvciAobGV0IGkgPSAwOyBpIDwgY29udHJvbC5sZW5ndGggLSAxOyBpKyspIHtcclxuICAgICAgICBjb25zdCBwMCA9IGF0KGkgLSAxKVxyXG4gICAgICAgIGNvbnN0IHAxID0gYXQoaSlcclxuICAgICAgICBjb25zdCBwMiA9IGF0KGkgKyAxKVxyXG4gICAgICAgIGNvbnN0IHAzID0gYXQoaSArIDIpXHJcbiAgICAgICAgY29uc3QgbGFzdCA9IGkgPT09IGNvbnRyb2wubGVuZ3RoIC0gMlxyXG4gICAgICAgIGNvbnN0IG4gPSBsYXN0ID8gc3RlcHMgOiBzdGVwcyAtIDFcclxuICAgICAgICBmb3IgKGxldCBzID0gMDsgcyA8PSBuOyBzKyspIHtcclxuICAgICAgICAgICAgY29uc3QgdCA9IHMgLyBuXHJcbiAgICAgICAgICAgIGNvbnN0IHQyID0gdCAqIHRcclxuICAgICAgICAgICAgY29uc3QgdDMgPSB0MiAqIHRcclxuICAgICAgICAgICAgY29uc3QgciA9IDAuNSAqICgoMiAqIHAxWzBdKSArICgtcDBbMF0gKyBwMlswXSkgKiB0XHJcbiAgICAgICAgICAgICAgICArICgyICogcDBbMF0gLSA1ICogcDFbMF0gKyA0ICogcDJbMF0gLSBwM1swXSkgKiB0MlxyXG4gICAgICAgICAgICAgICAgKyAoLXAwWzBdICsgMyAqIHAxWzBdIC0gMyAqIHAyWzBdICsgcDNbMF0pICogdDMpXHJcbiAgICAgICAgICAgIGNvbnN0IHkgPSAwLjUgKiAoKDIgKiBwMVsxXSkgKyAoLXAwWzFdICsgcDJbMV0pICogdFxyXG4gICAgICAgICAgICAgICAgKyAoMiAqIHAwWzFdIC0gNSAqIHAxWzFdICsgNCAqIHAyWzFdIC0gcDNbMV0pICogdDJcclxuICAgICAgICAgICAgICAgICsgKC1wMFsxXSArIDMgKiBwMVsxXSAtIDMgKiBwMlsxXSArIHAzWzFdKSAqIHQzKVxyXG4gICAgICAgICAgICBvdXQucHVzaChbTWF0aC5tYXgoMCwgciksIHldKVxyXG4gICAgICAgIH1cclxuICAgIH1cclxuICAgIHJldHVybiBvdXRcclxufVxyXG4iLCJpbXBvcnQgeyBDb2xvciwgTm9kZSwgVmVjMyB9IGZyb20gJ2NjJ1xyXG5pbXBvcnQgeyBQYXJ0U3R5bGUsIFBldEJ1aWxkZXJLaXQgfSBmcm9tICcuL1BldEJ1aWxkZXJLaXQnXHJcbmltcG9ydCB7IHNoaWZ0IH0gZnJvbSAnLi9QZXRHYW1lVGhlbWUnXHJcblxyXG4vKipcclxuICog5rip6aao5a6g54mp5oi/6Ze05Zy65pmv77yI6KeG6KeJ6YeN5p6EIHYz77yJ44CCXHJcbiAqXHJcbiAqIOepuumXtOWIhuWxgu+8iOebuOacuuWcqCAreiDnnIvlkJEgLXrvvIzlrqDniankvY3kuo7lnLDmr6/kuK3lpK4geuKJiDAuMzXvvInvvJpcclxuICogIC0g6IOM5pmv5bGC77ya5ZCO5aKZIC8g6Lii6ISa57q/IC8g5aSn56qXICsg56qX5biYICsg6Ziz5YWJ5YWJ5paRIC8g5oyC55S7IC8g5oyC54Gv5LiyIC8g572u54mp5p6277ybXHJcbiAqICAtIOS4reaZr+Wxgu+8muacqOWcsOadv++8iOWQq+adv+e8ne+8iSAvIOS4ieWxguWchuWcsOavryAvIOWuoOeJqeeqnSAvIOaWl+afnCAvIOe7v+akjSAvIOaVo+iQveeOqeWFt++8m1xyXG4gKiAgLSDliY3mma/lsYLvvJrpnaDlnqvjgIHlnLDmr6/ovrnnvJjnjqnlhbfjgIHpo5jmta7lhYnngrnvvIjnlLHliqjnlLvlsYLpqbHliqjkuIrljYfvvInvvIzliLbpgKDnurXmt7HjgIJcclxuICpcclxuICog6YWN6Imy6LWwXCLmmpblpbbmsrkgKyDonJzns5bmnKjoibIgKyDmn5TnsolcIueahOS9jumlseWSjOaaluiJsuezu++8jOS4juWuoOeJqemrmOmlseWSjOS4u+iJsuW9ouaIkOWvueavlO+8jFxyXG4gKiDorqnop5LoibLku47njq/looPkuK3ot7Plh7rmnaXvvJvlopnpnaLkuI7lnLDmnb/lj6rnlKjlpKfoibLlnZcgKyDnu4bliIblibLnur/vvIzpgb/lhY3miqLmiI/jgIJcclxuICpcclxuICog5YWo6YOo6Zu25aSW6YOo6LWE5Lqn77ya5byV5pOO5Y6f6K+tICsgcGV0LXRvb24g5p2Q6LSo56iL5bqP5YyW5p6E5bu644CCXHJcbiAqL1xyXG5cclxuLyoqIOaIv+mXtOWPr+WKqOWFg+e0oOW8leeUqO+8iFBldEdhbWVSb290LnVwZGF0ZSDpqbHliqjmsJvlm7Tlvq7liqjnlLvvvIkgKi9cclxuZXhwb3J0IGludGVyZmFjZSBSb29tUmVmcyB7XHJcbiAgICAvKiog5oyC54Gv5Liy54Gv5rOh77yI5ZG85ZC46Zeq54OB77yJICovXHJcbiAgICBidWxiczogTm9kZVtdXHJcbiAgICAvKiog56qX6L656Ziz5YWJ5YWJ5paR77yI5ZG85ZC45aKe5by6L+WHj+W8se+8iSAqL1xyXG4gICAgc3VuQmVhbTogTm9kZVxyXG4gICAgLyoqIOWcsOavr+S4iueahOeOqeWFt+eQg++8iOi9u+W+rua7muWKqOaEn++8iSAqL1xyXG4gICAgdG95QmFsbDogTm9kZVxyXG59XHJcblxyXG4vKipcclxuICog5oi/6Ze06YWN6Imy77yI6ZuG5Lit5a6a5LmJ77yM5L6/5LqO5pW05L2T6LCD6Imy77yJ44CCXHJcbiAqXHJcbiAqIHY3IOaUueS4uioq5rOV5byP5aW25rK56aOOKirvvJrlopkv5oqk5aKZ5p2/6LWw6LGh54mZ5aW25rK577yM5Zyw5p2/6LWw5rWF5qmh5pyo77yM5a625YW35LiA5b6L5aW25rK55ryG6Z2i77yI5LiN5YaN55So6KSQ6Imy5a6e5pyo77yJ77yMXHJcbiAqIOWcsOavr+eUqOeOq+eRsCvpvKDlsL7ojYnlj4zoibLvvIzpu4Tpk5zkvZzph5HlsZ7ngrnnvIDjgIJcclxuICog5pen54mI77yIdjbvvInlopkgMHhDNjlBNzAgLyDmnKjlrrblhbcgMHhBRjdDNEMg5piv6KSQ6Imy57O777yM5pW05bGL5YGP5pqX5YGP5penIOKAlOKAlCDlt7LmlbTkvZPmm7/mjaLjgIJcclxuICovXHJcbmNvbnN0IEMgPSB7XHJcbiAgICAvLyDlopnvvJrosaHniZnlpbbmsrnvvIjkuq7vvInvvIzmmpfpg6jlj6rljovkuIDmoaPmmpbngbDvvIznu53kuI3lho3okL3liLDmt7HopJBcclxuICAgIHdhbGw6IG5ldyBDb2xvcigweEY1LCAweEU2LCAweEQyLCAyNTUpLFxyXG4gICAgd2FsbFNoYWRlOiBuZXcgQ29sb3IoMHhEOSwgMHhDMywgMHhBOCwgMjU1KSxcclxuICAgIC8vIOaKpOWimeadvy/nur/mnaHvvJrov5Hnmb3lpbbmsrnvvIzlgZrlh7rms5XlvI/lopnoo5nnmoTlsYLmrKFcclxuICAgIGJhc2Vib2FyZDogbmV3IENvbG9yKDB4RkIsIDB4RjUsIDB4RUMsIDI1NSksXHJcbiAgICBiYXNlYm9hcmRTaGFkZTogbmV3IENvbG9yKDB4RTQsIDB4RDQsIDB4QzAsIDI1NSksXHJcbiAgICAvLyDlnLDmnb/vvJrmtYXmqaHmnKjlpbbmsrnvvIjmr5TlopnnlaXmt7HvvIzmi4nlh7rlsYLmrKHkvYbkuI3ov4fmm53vvIlcclxuICAgIGZsb29yOiBuZXcgQ29sb3IoMHhFQSwgMHhEOCwgMHhCRiwgMjU1KSxcclxuICAgIGZsb29yU2hhZGU6IG5ldyBDb2xvcigweENGLCAweEI5LCAweDlBLCAyNTUpLFxyXG4gICAgZmxvb3JMaW5lOiBuZXcgQ29sb3IoMHhDOCwgMHhCMSwgMHg5MiwgMjU1KSxcclxuICAgIC8vIOWcsOavr++8mueOq+eRsOWkluWciCArIOWltuayueS4reWciCArIOm8oOWwvuiNieS4reW/g++8iOWuoOeJqeermeWcqOS4reW/g+S7jeacieaYjuW6puWxgue6p++8iVxyXG4gICAgcnVnT3V0ZXI6IG5ldyBDb2xvcigweEU4LCAweEI0LCAweEI4LCAyNTUpLFxyXG4gICAgcnVnSW5uZXI6IG5ldyBDb2xvcigweEY3LCAweEVDLCAweERBLCAyNTUpLFxyXG4gICAgcnVnQ2VudGVyOiBuZXcgQ29sb3IoMHhBOCwgMHhDMywgMHg5QSwgMjU1KSxcclxuICAgIC8vIOaOpeinpumYtOW9se+8iOaaluikkO+8jOe7neS4jeeUqOm7ke+8muWGt+m7kemYtOW9seWcqOaaluiJsuepuumXtOmHjOS8muWPkeiEj++8iVxyXG4gICAgc2hhZG93OiBuZXcgQ29sb3IoMHg5QSwgMHg3QywgMHg2MiwgMjU1KSxcclxuICAgIC8vIOWutuWFt++8muWltuayuea8humdouacqO+8iOazleW8j+eZveacqO+8ie+8jOS4jeWGjeaYr+ikkOiJslxyXG4gICAgd29vZDogbmV3IENvbG9yKDB4RjIsIDB4RTEsIDB4Q0EsIDI1NSksXHJcbiAgICB3b29kRGFyazogbmV3IENvbG9yKDB4RDgsIDB4QzAsIDB4QTIsIDI1NSksXHJcbiAgICAvLyDpu4Tpk5zngrnnvIDvvIjmiormiYsv5p2G5Lu2L+mVnOahhu+8iVxyXG4gICAgYnJhc3M6IG5ldyBDb2xvcigweENCLCAweEEzLCAweDZCLCAyNTUpLFxyXG4gICAgYnJhc3NEYXJrOiBuZXcgQ29sb3IoMHhBOCwgMHg4MywgMHg0RiwgMjU1KSxcclxuICAgIGJlZFJpbTogbmV3IENvbG9yKDB4RTYsIDB4RDAsIDB4QjIsIDI1NSksXHJcbiAgICBiZWRDdXNoaW9uOiBuZXcgQ29sb3IoMHhGNiwgMHhCRiwgMHhDRSwgMjU1KSxcclxuICAgIHdpbmRvd0ZyYW1lOiBuZXcgQ29sb3IoMHhGRCwgMHhGOSwgMHhGNCwgMjU1KSxcclxuICAgIGdsYXNzOiBuZXcgQ29sb3IoMHhCOCwgMHhERiwgMHhGMywgMjU1KSxcclxuICAgIGN1cnRhaW46IG5ldyBDb2xvcigweEY4LCAweEQ3LCAweERBLCAyNTUpLFxyXG4gICAgc3VuOiBuZXcgQ29sb3IoMHhGRiwgMHhGMSwgMHhDOCwgMjU1KSxcclxuICAgIGxlYWY6IG5ldyBDb2xvcigweDdFLCAweEMxLCAweDkzLCAyNTUpLFxyXG4gICAgbGVhZkRhcms6IG5ldyBDb2xvcigweDVGLCAweEE0LCAweDc5LCAyNTUpLFxyXG4gICAgLy8g6Zm255O35Zmo55q/77yI6Iqx55O2L+eil+ebmO+8ie+8mui/keeZveaalueTt1xyXG4gICAgdmFzZTogbmV3IENvbG9yKDB4RkIsIDB4RjUsIDB4RUIsIDI1NSksXHJcbiAgICBjaGluYTogbmV3IENvbG9yKDB4RkQsIDB4RjgsIDB4RjAsIDI1NSksXHJcbiAgICAvLyDkuabmnKzkuI7njqnlhbfvvJrpm77ok50gLyDnjqvnkbAgLyDlpbbmsrnpu4TnmoTkuInoibLlsI/pnaLnp6/ngrnnvIBcclxuICAgIGJvb2sxOiBuZXcgQ29sb3IoMHg5QywgMHhCRSwgMHhFMCwgMjU1KSxcclxuICAgIGJvb2syOiBuZXcgQ29sb3IoMHhGNCwgMHhCMCwgMHhDMiwgMjU1KSxcclxuICAgIGJvb2szOiBuZXcgQ29sb3IoMHhGNywgMHhERCwgMHhBRSwgMjU1KSxcclxuICAgIGJ1bGI6IG5ldyBDb2xvcigweEZGLCAweEUyLCAweDlFLCAyNTUpLFxyXG4gICAgYmFsbDogbmV3IENvbG9yKDB4OUMsIDB4QkUsIDB4RTAsIDI1NSksXHJcbiAgICB5YXJuOiBuZXcgQ29sb3IoMHhGNCwgMHhCMCwgMHhDMiwgMjU1KSxcclxuICAgIGR1Y2s6IG5ldyBDb2xvcigweEY3LCAweERELCAweEFFLCAyNTUpLFxyXG4gICAgZHVja0JlYWs6IG5ldyBDb2xvcigweEVGLCAweEIxLCAweDc3LCAyNTUpLFxyXG4gICAgY3VzaGlvbjogbmV3IENvbG9yKDB4RjksIDB4RUMsIDB4RDgsIDI1NSksXHJcbiAgICBwaWN0dXJlOiBuZXcgQ29sb3IoMHhGRSwgMHhGNiwgMHhFQSwgMjU1KSxcclxuICAgIGhlYXJ0OiBuZXcgQ29sb3IoMHhGRiwgMHg5QywgMHhCMCwgMjU1KSxcclxufSBhcyBjb25zdFxyXG5cclxuLyoqXHJcbiAqIOW/q+mAn+mjjuagvO+8muS4u+iJsiArIOWPr+mAieaal+mDqC/mj4/ovrnopobnm5bjgIJcclxuICpcclxuICogYHBsYWluYCDnlKjkuo7pnIDopoEqKuagh+WHhiBhbHBoYSDmt7flkIgqKueahOmDqOS7tu+8iOWFieeCuS/pmLPlhYnniYcv546755KD77yJ77ya6LWw5byV5pOO5YaF572u5p2Q6LSoXHJcbiAqIO+8iOiHquWumuS5iSBlZmZlY3Qg55qEIHRyYW5zcGFyZW50IOaKgOacr+WcqOacrOaehOW7uumTvuS4i+S8muS4ouWkseadkOi0qOWxnuaAp++8ieOAglxyXG4gKi9cclxuZnVuY3Rpb24gc3R5bGUoY29sb3I6IENvbG9yLCBvcHRzPzoge1xyXG4gICAgc2hhZGU/OiBDb2xvcjsgb3V0bGluZT86IG51bWJlcjsgYWxwaGE/OiBudW1iZXI7IGdsb3c/OiBib29sZWFuOyBwbGFpbj86IGJvb2xlYW5cclxufSk6IFBhcnRTdHlsZSB7XHJcbiAgICByZXR1cm4ge1xyXG4gICAgICAgIGNvbG9yLFxyXG4gICAgICAgIHNoYWRlOiBvcHRzPy5zaGFkZSxcclxuICAgICAgICBvdXRsaW5lOiBvcHRzPy5vdXRsaW5lID8/IDAsXHJcbiAgICAgICAgYWxwaGE6IG9wdHM/LmFscGhhLFxyXG4gICAgICAgIGdsb3c6IG9wdHM/Lmdsb3csXHJcbiAgICAgICAgcGxhaW46IG9wdHM/LnBsYWluLFxyXG4gICAgfVxyXG59XHJcblxyXG4vKiog5p6E5bu65pW05Liq5oi/6Ze077yIcGFyZW50IOS4uuS4lueVjOagueiKgueCue+8m+i/lOWbnuawm+WbtOWKqOeUu+W8leeUqO+8iSAqL1xyXG5leHBvcnQgZnVuY3Rpb24gYnVpbGRSb29tKHBhcmVudDogTm9kZSwga2l0OiBQZXRCdWlsZGVyS2l0KTogUm9vbVJlZnMge1xyXG4gICAgYnVpbGRGbG9vcihwYXJlbnQsIGtpdClcclxuICAgIGJ1aWxkV2FsbHMocGFyZW50LCBraXQpXHJcbiAgICBidWlsZFdpbmRvdyhwYXJlbnQsIGtpdClcclxuICAgIGJ1aWxkV2FsbERlY29yKHBhcmVudCwga2l0KVxyXG4gICAgYnVpbGRSdWcocGFyZW50LCBraXQpXHJcbiAgICBidWlsZEJlZChwYXJlbnQsIGtpdClcclxuICAgIGJ1aWxkQ2FiaW5ldChwYXJlbnQsIGtpdClcclxuICAgIGJ1aWxkUGxhbnQocGFyZW50LCBraXQpXHJcbiAgICBjb25zdCB0b3lCYWxsID0gYnVpbGRUb3lzKHBhcmVudCwga2l0KVxyXG4gICAgYnVpbGRDb250YWN0U2hhZG93cyhwYXJlbnQsIGtpdClcclxuICAgIGJ1aWxkRm9yZWdyb3VuZChwYXJlbnQsIGtpdClcclxuICAgIGNvbnN0IGJ1bGJzID0gYnVpbGRMYW1wU3RyaW5nKHBhcmVudCwga2l0KVxyXG4gICAgY29uc3Qgc3VuQmVhbSA9IGJ1aWxkU3VuQmVhbShwYXJlbnQsIGtpdClcclxuICAgIHJldHVybiB7IGJ1bGJzLCBzdW5CZWFtLCB0b3lCYWxsIH1cclxufVxyXG5cclxuLyoqIOWcsOadv++8muWkp+iJsuWdlyArIOerluWQkeadv+e8ne+8iOmVv+adoeacqOadv+aEn++8ie+8jOmhtumdoiB5PTAgKi9cclxuZnVuY3Rpb24gYnVpbGRGbG9vcihwYXJlbnQ6IE5vZGUsIGtpdDogUGV0QnVpbGRlcktpdCk6IHZvaWQge1xyXG4gICAgY29uc3Qgcm9vdCA9IGtpdC5tYWtlM2ROb2RlKHBhcmVudCwgJ0Zsb29yJywgbmV3IFZlYzMoMCwgMCwgMCkpXHJcbiAgICBraXQuYm94UGFydChyb290LCAnU2xhYicsIHsgdzogMTYsIGg6IDAuMiwgZDogNy42IH0sIHN0eWxlKEMuZmxvb3IsIHsgc2hhZGU6IEMuZmxvb3JTaGFkZSB9KSxcclxuICAgICAgICBuZXcgVmVjMygwLCAtMC4xLCAwLjQpKVxyXG4gICAgY29uc3QgbGluZSA9IHN0eWxlKEMuZmxvb3JMaW5lLCB7IHNoYWRlOiBDLmZsb29yTGluZSB9KVxyXG4gICAgZm9yIChsZXQgaW5kZXggPSAtNTsgaW5kZXggPD0gNTsgaW5kZXggKz0gMSkge1xyXG4gICAgICAgIGtpdC5ib3hQYXJ0KHJvb3QsICdTZWFtJywgeyB3OiAwLjAzNSwgaDogMC4wMTQsIGQ6IDcuNiB9LCBsaW5lLCBuZXcgVmVjMyhpbmRleCAqIDEuNTUsIDAuMDA2LCAwLjQpKVxyXG4gICAgfVxyXG4gICAgZm9yIChjb25zdCB6IG9mIFstMS45LCAtMC40LCAxLjEsIDIuNl0pIHtcclxuICAgICAgICBraXQuYm94UGFydChyb290LCAnSm9pbnQnLCB7IHc6IDE2LCBoOiAwLjAxNCwgZDogMC4wMzUgfSwgbGluZSwgbmV3IFZlYzMoMCwgMC4wMDYsIHopKVxyXG4gICAgfVxyXG59XHJcblxyXG4vKiog5aKZ5L2T77ya5ZCO5aKZICsg5bem5aKZICsg6Lii6ISa57q/77yI6IOM5pmv5bGC5qyh77yM5LiN5YGa5o+P6L656YG/5YWN5oqi5oiP77yJICovXHJcbmZ1bmN0aW9uIGJ1aWxkV2FsbHMocGFyZW50OiBOb2RlLCBraXQ6IFBldEJ1aWxkZXJLaXQpOiB2b2lkIHtcclxuICAgIGNvbnN0IHJvb3QgPSBraXQubWFrZTNkTm9kZShwYXJlbnQsICdXYWxscycsIG5ldyBWZWMzKDAsIDAsIDApKVxyXG4gICAgY29uc3Qgd2FsbCA9IHN0eWxlKEMud2FsbCwgeyBzaGFkZTogQy53YWxsU2hhZGUgfSlcclxuICAgIGNvbnN0IGJhc2UgPSBzdHlsZShDLmJhc2Vib2FyZCwgeyBzaGFkZTogQy5iYXNlYm9hcmRTaGFkZSB9KVxyXG4gICAga2l0LmJveFBhcnQocm9vdCwgJ0JhY2snLCB7IHc6IDE2LCBoOiA0LjgsIGQ6IDAuMyB9LCB3YWxsLCBuZXcgVmVjMygwLCAyLjQsIC0zLjApKVxyXG4gICAga2l0LmJveFBhcnQocm9vdCwgJ0JhY2tCYXNlJywgeyB3OiAxNiwgaDogMC4zNiwgZDogMC40IH0sIGJhc2UsIG5ldyBWZWMzKDAsIDAuMTgsIC0yLjkzKSlcclxuICAgIGtpdC5ib3hQYXJ0KHJvb3QsICdMZWZ0JywgeyB3OiAwLjMsIGg6IDQuOCwgZDogNy42IH0sIHdhbGwsIG5ldyBWZWMzKC00LjYsIDIuNCwgMC40KSlcclxuICAgIGtpdC5ib3hQYXJ0KHJvb3QsICdMZWZ0QmFzZScsIHsgdzogMC40LCBoOiAwLjM2LCBkOiA3LjYgfSwgYmFzZSwgbmV3IFZlYzMoLTQuNTMsIDAuMTgsIDAuNCkpXHJcbiAgICAvLyDms5XlvI/nur/mnaHvvJrpobbnur/vvIhjcm93bu+8iSsg6IWw57q/77yIY2hhaXIgcmFpbO+8ie+8jOi/keeZveWltuayueWLvuWHuuWimeijmeWxguasoVxyXG4gICAga2l0LmJveFBhcnQocm9vdCwgJ0Nyb3duQmFjaycsIHsgdzogMTYsIGg6IDAuMTYsIGQ6IDAuNDQgfSwgYmFzZSwgbmV3IFZlYzMoMCwgNC42MiwgLTIuOTIpKVxyXG4gICAga2l0LmJveFBhcnQocm9vdCwgJ1JhaWxCYWNrJywgeyB3OiAxNiwgaDogMC4xLCBkOiAwLjQgfSwgYmFzZSwgbmV3IFZlYzMoMCwgMS4xMiwgLTIuOTQpKVxyXG4gICAga2l0LmJveFBhcnQocm9vdCwgJ0Nyb3duTGVmdCcsIHsgdzogMC40NCwgaDogMC4xNiwgZDogNy42IH0sIGJhc2UsIG5ldyBWZWMzKC00LjUsIDQuNjIsIDAuNCkpXHJcbiAgICBraXQuYm94UGFydChyb290LCAnUmFpbExlZnQnLCB7IHc6IDAuNCwgaDogMC4xLCBkOiA3LjYgfSwgYmFzZSwgbmV3IFZlYzMoLTQuNTIsIDEuMTIsIDAuNCkpXHJcbn1cclxuXHJcbi8qKiDlpKfnqpfvvJrnqpfmoYYgKyDpgI/lhYnnjrvnkoPvvIjoh6rlj5HlhYnvvIkrIOWNgeWtl+eql+ajgiArIOS4pOS+p+eql+W4mCAqL1xyXG5mdW5jdGlvbiBidWlsZFdpbmRvdyhwYXJlbnQ6IE5vZGUsIGtpdDogUGV0QnVpbGRlcktpdCk6IHZvaWQge1xyXG4gICAgY29uc3Qgcm9vdCA9IGtpdC5tYWtlM2ROb2RlKHBhcmVudCwgJ1dpbmRvdycsIG5ldyBWZWMzKC0yLjAsIDIuNDUsIC0yLjgyKSlcclxuICAgIGNvbnN0IGZyYW1lID0gc3R5bGUoQy53aW5kb3dGcmFtZSwgeyBzaGFkZTogc2hpZnQoQy53aW5kb3dGcmFtZSwgLTAuMTgpIH0pXHJcbiAgICBraXQuYm94UGFydChyb290LCAnRnJhbWUnLCB7IHc6IDIuMTUsIGg6IDEuNzUsIGQ6IDAuMTQgfSwgZnJhbWUsIG5ldyBWZWMzKDAsIDAsIDApKVxyXG4gICAga2l0LmJveFBhcnQocm9vdCwgJ0dsYXNzJywgeyB3OiAxLjg2LCBoOiAxLjQ2LCBkOiAwLjA2IH0sIHN0eWxlKEMuZ2xhc3MsIHsgZ2xvdzogdHJ1ZSB9KSxcclxuICAgICAgICBuZXcgVmVjMygwLCAwLCAwLjA1KSlcclxuICAgIGtpdC5ib3hQYXJ0KHJvb3QsICdNdWxsaW9uVicsIHsgdzogMC4wOSwgaDogMS40NiwgZDogMC4xIH0sIGZyYW1lLCBuZXcgVmVjMygwLCAwLCAwLjA2KSlcclxuICAgIGtpdC5ib3hQYXJ0KHJvb3QsICdNdWxsaW9uSCcsIHsgdzogMS44NiwgaDogMC4wOSwgZDogMC4xIH0sIGZyYW1lLCBuZXcgVmVjMygwLCAwLCAwLjA2KSlcclxuICAgIGtpdC5ib3hQYXJ0KHJvb3QsICdTaWxsJywgeyB3OiAyLjQsIGg6IDAuMTIsIGQ6IDAuMzQgfSwgZnJhbWUsIG5ldyBWZWMzKDAsIC0wLjkzLCAwLjA4KSlcclxuXHJcbiAgICAvLyDnqpfluJjvvJrluJjmnYYgKyDlt6blj7PluJjluIMgKyDluJjlpLRcclxuICAgIGNvbnN0IGN1cnRhaW4gPSBzdHlsZShDLmN1cnRhaW4sIHsgc2hhZGU6IHNoaWZ0KEMuY3VydGFpbiwgLTAuMTYpIH0pXHJcbiAgICBraXQuY2FwUGFydChyb290LCAnUm9kJywgMC4wNDUsIDMuNSwgc3R5bGUoQy5icmFzcywgeyBzaGFkZTogQy5icmFzc0RhcmsgfSksXHJcbiAgICAgICAgbmV3IFZlYzMoMCwgMS4wNiwgMC4xMiksIHsgcm90OiBuZXcgVmVjMygwLCAwLCA5MCkgfSlcclxuICAgIGtpdC5ib3hQYXJ0KHJvb3QsICdDdXJ0YWluTCcsIHsgdzogMC41LCBoOiAyLjMsIGQ6IDAuMTggfSwgY3VydGFpbiwgbmV3IFZlYzMoLTEuMjgsIC0wLjEsIDAuMTIpKVxyXG4gICAga2l0LmJveFBhcnQocm9vdCwgJ0N1cnRhaW5SJywgeyB3OiAwLjUsIGg6IDIuMywgZDogMC4xOCB9LCBjdXJ0YWluLCBuZXcgVmVjMygxLjI4LCAtMC4xLCAwLjEyKSlcclxuICAgIGtpdC5ib3hQYXJ0KHJvb3QsICdQZWxtZXQnLCB7IHc6IDIuOSwgaDogMC4yNiwgZDogMC4yNCB9LCBjdXJ0YWluLCBuZXcgVmVjMygwLCAxLjAyLCAwLjEyKSlcclxuXHJcbiAgICAvLyDnqpflj7DlsI/oirHnk7bvvJrkuIDmnLXnjqvnkbAgKyDkuKTniYflj7ZcclxuICAgIGtpdC5jeWxQYXJ0KHJvb3QsICdWYXNlU21hbGwnLCAwLjA5LCAwLjA3LCAwLjE2LCBzdHlsZShDLnZhc2UsIHsgc2hhZGU6IHNoaWZ0KEMudmFzZSwgLTAuMSkgfSksXHJcbiAgICAgICAgbmV3IFZlYzMoMC43MiwgLTAuNzksIDAuMTIpKVxyXG4gICAga2l0LmNhcFBhcnQocm9vdCwgJ1N0ZW1TbWFsbCcsIDAuMDE2LCAwLjE0LCBzdHlsZShDLmxlYWZEYXJrLCB7IHNoYWRlOiBzaGlmdChDLmxlYWZEYXJrLCAtMC4xOCkgfSksXHJcbiAgICAgICAgbmV3IFZlYzMoMC43MiwgLTAuNjYsIDAuMTIpKVxyXG4gICAga2l0LmJhbGwocm9vdCwgJ0Jsb29tU21hbGwnLCAwLjA3NSwgc3R5bGUoQy5oZWFydCwgeyBzaGFkZTogc2hpZnQoQy5oZWFydCwgLTAuMTgpIH0pLFxyXG4gICAgICAgIG5ldyBWZWMzKDAuNzIsIC0wLjU1LCAwLjEyKSlcclxuICAgIGtpdC5iYWxsKHJvb3QsICdMZWFmVGlueUEnLCAwLjA0NSwgc3R5bGUoQy5sZWFmLCB7IHNoYWRlOiBDLmxlYWZEYXJrIH0pLCBuZXcgVmVjMygwLjY2LCAtMC42MiwgMC4xKSwge1xyXG4gICAgICAgIHNjYWxlOiBuZXcgVmVjMygxLCAwLjYsIDEpLFxyXG4gICAgfSlcclxuICAgIGtpdC5iYWxsKHJvb3QsICdMZWFmVGlueUInLCAwLjA0LCBzdHlsZShDLmxlYWYsIHsgc2hhZGU6IEMubGVhZkRhcmsgfSksIG5ldyBWZWMzKDAuNzgsIC0wLjYsIDAuMTQpLCB7XHJcbiAgICAgICAgc2NhbGU6IG5ldyBWZWMzKDEsIDAuNiwgMSksXHJcbiAgICB9KVxyXG59XHJcblxyXG4vKiog5aKZ6Z2i6KOF6aWw77ya5oyC55S777yI5ZCr5b+D5b2i5Zu+5qGI77yJKyDmjILnga/kuLLvvIjnga/ms6HoioLngrnvvIkrIOS6keacteaYn+mlsCAqL1xyXG5mdW5jdGlvbiBidWlsZFdhbGxEZWNvcihwYXJlbnQ6IE5vZGUsIGtpdDogUGV0QnVpbGRlcktpdCk6IHZvaWQge1xyXG4gICAgY29uc3Qgcm9vdCA9IGtpdC5tYWtlM2ROb2RlKHBhcmVudCwgJ1dhbGxEZWNvcicsIG5ldyBWZWMzKDAsIDAsIDApKVxyXG5cclxuICAgIC8vIOaMgueUu++8mum7hOmTnOeUu+ahhu+8iOazleW8j+mHkeahhu+8ie+8jOeUu+mdouS4uuWltuayueW6lSArIOeOq+eRsOW/g1xyXG4gICAgY29uc3QgZnJhbWUgPSBraXQubWFrZTNkTm9kZShyb290LCAnUGFpbnRpbmcnLCBuZXcgVmVjMygxLjg1LCAyLjU1LCAtMi44KSlcclxuICAgIGtpdC5ib3hQYXJ0KGZyYW1lLCAnRnJhbWUnLCB7IHc6IDEuNSwgaDogMS4yLCBkOiAwLjEgfSxcclxuICAgICAgICBzdHlsZShDLmJyYXNzLCB7IHNoYWRlOiBDLmJyYXNzRGFyayB9KSwgbmV3IFZlYzMoMCwgMCwgMCkpXHJcbiAgICBraXQuYm94UGFydChmcmFtZSwgJ0NhbnZhcycsIHsgdzogMS4yNiwgaDogMC45NiwgZDogMC4wNiB9LCBzdHlsZShDLnBpY3R1cmUsIHsgc2hhZGU6IHNoaWZ0KEMucGljdHVyZSwgLTAuMTIpIH0pLFxyXG4gICAgICAgIG5ldyBWZWMzKDAsIDAsIDAuMDQpKVxyXG4gICAgLy8g5b+D5b2i77ya5Lik55CDICsg5LiA6ZSl77yI5pyd5LiL55qE5bCW77yJXHJcbiAgICBraXQuYmFsbChmcmFtZSwgJ0hlYXJ0TCcsIDAuMTQsIHN0eWxlKEMuaGVhcnQsIHsgc2hhZGU6IHNoaWZ0KEMuaGVhcnQsIC0wLjIpIH0pLCBuZXcgVmVjMygtMC4xLCAwLjA4LCAwLjA3KSlcclxuICAgIGtpdC5iYWxsKGZyYW1lLCAnSGVhcnRSJywgMC4xNCwgc3R5bGUoQy5oZWFydCwgeyBzaGFkZTogc2hpZnQoQy5oZWFydCwgLTAuMikgfSksIG5ldyBWZWMzKDAuMSwgMC4wOCwgMC4wNykpXHJcbiAgICBraXQuY29uZVBhcnQoZnJhbWUsICdIZWFydFRpcCcsIDAuMTksIDAuMjYsIHN0eWxlKEMuaGVhcnQsIHsgc2hhZGU6IHNoaWZ0KEMuaGVhcnQsIC0wLjIpIH0pLFxyXG4gICAgICAgIG5ldyBWZWMzKDAsIC0wLjE0LCAwLjA3KSwgeyByb3Q6IG5ldyBWZWMzKDE4MCwgMCwgMCkgfSlcclxuXHJcbiAgICAvLyDkupHmnLUgKyDmmJ/mmJ/vvIjlt6bkuIrop5LlopnpnaLvvIlcclxuICAgIGNvbnN0IGNsb3VkID0ga2l0Lm1ha2UzZE5vZGUocm9vdCwgJ0Nsb3VkJywgbmV3IFZlYzMoLTMuMywgMy41LCAtMi44KSlcclxuICAgIGtpdC5iYWxsKGNsb3VkLCAnYzEnLCAwLjI2LCBzdHlsZShuZXcgQ29sb3IoMHhGRiwgMHhGQiwgMHhGMiwgMjU1KSwgeyBzaGFkZTogQy53YWxsU2hhZGUgfSksXHJcbiAgICAgICAgbmV3IFZlYzMoLTAuMjQsIDAsIDApKVxyXG4gICAga2l0LmJhbGwoY2xvdWQsICdjMicsIDAuMzQsIHN0eWxlKG5ldyBDb2xvcigweEZGLCAweEZCLCAweEYyLCAyNTUpLCB7IHNoYWRlOiBDLndhbGxTaGFkZSB9KSxcclxuICAgICAgICBuZXcgVmVjMygwLjE1LCAwLjA2LCAwKSlcclxuICAgIGtpdC5iYWxsKGNsb3VkLCAnYzMnLCAwLjIyLCBzdHlsZShuZXcgQ29sb3IoMHhGRiwgMHhGQiwgMHhGMiwgMjU1KSwgeyBzaGFkZTogQy53YWxsU2hhZGUgfSksXHJcbiAgICAgICAgbmV3IFZlYzMoMC41LCAtMC4wMiwgMCkpXHJcbiAgICBraXQuYmFsbChjbG91ZCwgJ3N0YXInLCAwLjEsIHN0eWxlKEMuYnVsYiwgeyBnbG93OiB0cnVlIH0pLCBuZXcgVmVjMygwLjksIDAuMzQsIDApKVxyXG5cclxuICAgIC8vIOe9rueJqeaetu+8iOWPs+WQjuWime+8ie+8muS4pOWxguacqOadvyArIOS5puacrCArIOWwj+ebhuagvVxyXG4gICAgY29uc3Qgc2hlbGYgPSBraXQubWFrZTNkTm9kZShyb290LCAnU2hlbGYnLCBuZXcgVmVjMygzLjM1LCAwLCAtMi43KSlcclxuICAgIGNvbnN0IHdvb2RTdHlsZSA9IHN0eWxlKEMud29vZCwgeyBzaGFkZTogQy53b29kRGFyayB9KVxyXG4gICAga2l0LmJveFBhcnQoc2hlbGYsICdCb2FyZFRvcCcsIHsgdzogMS43LCBoOiAwLjA4LCBkOiAwLjQ0IH0sIHdvb2RTdHlsZSwgbmV3IFZlYzMoMCwgMi4yOCwgMCkpXHJcbiAgICBraXQuYm94UGFydChzaGVsZiwgJ0JvYXJkQm90dG9tJywgeyB3OiAxLjcsIGg6IDAuMDgsIGQ6IDAuNDQgfSwgd29vZFN0eWxlLCBuZXcgVmVjMygwLCAxLjUyLCAwKSlcclxuICAgIGtpdC5ib3hQYXJ0KHNoZWxmLCAnU2lkZUwnLCB7IHc6IDAuMDksIGg6IDEuNSwgZDogMC40NCB9LCB3b29kU3R5bGUsIG5ldyBWZWMzKC0wLjgsIDEuOSwgMCkpXHJcbiAgICBraXQuYm94UGFydChzaGVsZiwgJ1NpZGVSJywgeyB3OiAwLjA5LCBoOiAxLjUsIGQ6IDAuNDQgfSwgd29vZFN0eWxlLCBuZXcgVmVjMygwLjgsIDEuOSwgMCkpXHJcbiAgICAvLyDkuabmnKzvvIjnq5bmlL7vvIzlvanoibLkuabohIrvvIlcclxuICAgIGNvbnN0IGJvb2tzOiBBcnJheTxbQ29sb3IsIG51bWJlciwgbnVtYmVyXT4gPSBbW0MuYm9vazEsIC0wLjUsIDAuMTZdLCBbQy5ib29rMiwgLTAuMywgMC4xNF0sIFtDLmJvb2szLCAtMC4xMSwgMC4xOF1dXHJcbiAgICBmb3IgKGNvbnN0IFtjb2xvciwgeCwgaF0gb2YgYm9va3MpIHtcclxuICAgICAgICBraXQuYm94UGFydChzaGVsZiwgJ0Jvb2snLCB7IHc6IDAuMTMsIGgsIGQ6IDAuMyB9LCBzdHlsZShjb2xvciwgeyBzaGFkZTogc2hpZnQoY29sb3IsIC0wLjIyKSB9KSxcclxuICAgICAgICAgICAgbmV3IFZlYzMoeCwgMi4zMiArIGggLyAyLCAwLjAyKSlcclxuICAgIH1cclxuICAgIC8vIOaetuS4iuWwj+iKseeTtu+8muS4gOactembvuiTneWwj+iKsVxyXG4gICAga2l0LmN5bFBhcnQoc2hlbGYsICdWYXNlU2hlbGYnLCAwLjA5LCAwLjA3LCAwLjE1LCBzdHlsZShDLnZhc2UsIHsgc2hhZGU6IHNoaWZ0KEMudmFzZSwgLTAuMSkgfSksXHJcbiAgICAgICAgbmV3IFZlYzMoMC40NSwgMS42MiArIDAuMDc1LCAwKSlcclxuICAgIGtpdC5jYXBQYXJ0KHNoZWxmLCAnU3RlbVNoZWxmJywgMC4wMTQsIDAuMTIsIHN0eWxlKEMubGVhZkRhcmssIHsgc2hhZGU6IHNoaWZ0KEMubGVhZkRhcmssIC0wLjE4KSB9KSxcclxuICAgICAgICBuZXcgVmVjMygwLjQ1LCAxLjg1LCAwKSlcclxuICAgIGtpdC5iYWxsKHNoZWxmLCAnQmxvb21TaGVsZicsIDAuMDY1LCBzdHlsZShDLmJvb2sxLCB7IHNoYWRlOiBzaGlmdChDLmJvb2sxLCAtMC4xOCkgfSksXHJcbiAgICAgICAgbmV3IFZlYzMoMC40NSwgMS45NCwgMCkpXHJcbiAgICBraXQuYmFsbChzaGVsZiwgJ0xlYWZTaGVsZicsIDAuMDUsIHN0eWxlKEMubGVhZiwgeyBzaGFkZTogQy5sZWFmRGFyayB9KSwgbmV3IFZlYzMoMC41MiwgMS44OCwgMC4wMiksIHtcclxuICAgICAgICBzY2FsZTogbmV3IFZlYzMoMSwgMC42LCAxKSxcclxuICAgIH0pXHJcbn1cclxuXHJcbi8qKiDlnIblnLDmr6/vvIjkuInlsYLlkIzlv4PlnIYgKyDovrnnvJjmu5rovrnvvInvvIzlrqDnianmtLvliqjljLrkuK3lv4MgKi9cclxuZnVuY3Rpb24gYnVpbGRSdWcocGFyZW50OiBOb2RlLCBraXQ6IFBldEJ1aWxkZXJLaXQpOiB2b2lkIHtcclxuICAgIGNvbnN0IHJvb3QgPSBraXQubWFrZTNkTm9kZShwYXJlbnQsICdSdWcnLCBuZXcgVmVjMygwLCAwLCAwLjM1KSlcclxuICAgIGtpdC5jeWxQYXJ0KHJvb3QsICdFZGdlJywgMi40MiwgMi40MiwgMC4wNDUsIHN0eWxlKHNoaWZ0KEMucnVnT3V0ZXIsIC0wLjIpLCB7IHNoYWRlOiBzaGlmdChDLnJ1Z091dGVyLCAtMC4zMikgfSksXHJcbiAgICAgICAgbmV3IFZlYzMoMCwgMC4wMjIsIDApKVxyXG4gICAga2l0LmN5bFBhcnQocm9vdCwgJ091dGVyJywgMi4zLCAyLjMsIDAuMDUsIHN0eWxlKEMucnVnT3V0ZXIsIHsgc2hhZGU6IHNoaWZ0KEMucnVnT3V0ZXIsIC0wLjE0KSB9KSxcclxuICAgICAgICBuZXcgVmVjMygwLCAwLjAzLCAwKSlcclxuICAgIGtpdC5jeWxQYXJ0KHJvb3QsICdJbm5lcicsIDEuOTIsIDEuOTIsIDAuMDU1LCBzdHlsZShDLnJ1Z0lubmVyLCB7IHNoYWRlOiBzaGlmdChDLnJ1Z0lubmVyLCAtMC4xNCkgfSksXHJcbiAgICAgICAgbmV3IFZlYzMoMCwgMC4wMzUsIDApKVxyXG4gICAga2l0LmN5bFBhcnQocm9vdCwgJ0NlbnRlcicsIDAuOTUsIDAuOTUsIDAuMDYsIHN0eWxlKEMucnVnQ2VudGVyLCB7IHNoYWRlOiBzaGlmdChDLnJ1Z0NlbnRlciwgLTAuMTQpIH0pLFxyXG4gICAgICAgIG5ldyBWZWMzKDAsIDAuMDQsIDApKVxyXG59XHJcblxyXG4vKiog5a6g54mp56qd77ya5aSW5ZyI6L2v5Z6rICsg5YaF5Z6rICsg6Z2g5p6V77yI5Y+z5L6n5Lit5pmv77yJICovXHJcbmZ1bmN0aW9uIGJ1aWxkQmVkKHBhcmVudDogTm9kZSwga2l0OiBQZXRCdWlsZGVyS2l0KTogdm9pZCB7XHJcbiAgICAvLyDorqnlvIDkuK3lpK7mtLvliqjljLrvvJrlrqDniannqp3pgIDliLDlj7PkvqfpnaDlkI5cclxuICAgIGNvbnN0IHJvb3QgPSBraXQubWFrZTNkTm9kZShwYXJlbnQsICdQZXRCZWQnLCBuZXcgVmVjMygyLjk1LCAwLCAxLjM1KSlcclxuICAgIGtpdC5jeWxQYXJ0KHJvb3QsICdSaW0nLCAwLjg4LCAwLjk4LCAwLjM0LCBzdHlsZShDLmJlZFJpbSwgeyBzaGFkZTogc2hpZnQoQy5iZWRSaW0sIC0wLjE4KSB9KSxcclxuICAgICAgICBuZXcgVmVjMygwLCAwLjE3LCAwKSlcclxuICAgIGtpdC5jeWxQYXJ0KHJvb3QsICdDdXNoaW9uJywgMC43MiwgMC43MiwgMC4xNiwgc3R5bGUoQy5iZWRDdXNoaW9uLCB7IHNoYWRlOiBzaGlmdChDLmJlZEN1c2hpb24sIC0wLjE2KSB9KSxcclxuICAgICAgICBuZXcgVmVjMygwLCAwLjI2LCAwKSlcclxuICAgIGtpdC5iYWxsKHJvb3QsICdQaWxsb3cnLCAwLjM0LCBzdHlsZShuZXcgQ29sb3IoMHhGRiwgMHhFMywgMHhDOSwgMjU1KSwgeyBzaGFkZTogbmV3IENvbG9yKDB4RTgsIDB4QzYsIDB4QTYsIDI1NSkgfSksXHJcbiAgICAgICAgbmV3IFZlYzMoLTAuMiwgMC4zNCwgLTAuNDIpLCB7IHNjYWxlOiBuZXcgVmVjMygxLjE1LCAwLjcyLCAwLjg1KSB9KVxyXG59XHJcblxyXG4vKiog5paX5p+c77yI5bem5L6n6IOM5pmv77yJ77ya5p+c5L2TICsg5oq95bGJ57q/ICsg5oqK5omLICsg5Y+w6Z2i5pGG5Lu2ICovXHJcbmZ1bmN0aW9uIGJ1aWxkQ2FiaW5ldChwYXJlbnQ6IE5vZGUsIGtpdDogUGV0QnVpbGRlcktpdCk6IHZvaWQge1xyXG4gICAgY29uc3Qgcm9vdCA9IGtpdC5tYWtlM2ROb2RlKHBhcmVudCwgJ0NhYmluZXQnLCBuZXcgVmVjMygtMy41NSwgMCwgLTAuMSkpXHJcbiAgICBjb25zdCB3b29kU3R5bGUgPSBzdHlsZShDLndvb2QsIHsgc2hhZGU6IEMud29vZERhcmsgfSlcclxuICAgIGtpdC5ib3hQYXJ0KHJvb3QsICdCb2R5JywgeyB3OiAxLjI1LCBoOiAxLjM1LCBkOiAxLjAgfSwgd29vZFN0eWxlLCBuZXcgVmVjMygwLCAwLjY3NSwgMCkpXHJcbiAgICBraXQuYm94UGFydChyb290LCAnVG9wJywgeyB3OiAxLjM1LCBoOiAwLjA5LCBkOiAxLjEgfSwgc3R5bGUoc2hpZnQoQy53b29kLCAwLjE0KSwgeyBzaGFkZTogQy53b29kIH0pLFxyXG4gICAgICAgIG5ldyBWZWMzKDAsIDEuMzksIDApKVxyXG4gICAgY29uc3QgZHJhd2VyTGluZSA9IHN0eWxlKHNoaWZ0KEMud29vZERhcmssIC0wLjE2KSwgeyBzaGFkZTogc2hpZnQoQy53b29kRGFyaywgLTAuMikgfSlcclxuICAgIGZvciAoY29uc3QgeSBvZiBbMC40MiwgMC45Ml0pIHtcclxuICAgICAgICBraXQuYm94UGFydChyb290LCAnRHJhd2VyR2FwJywgeyB3OiAxLjE5LCBoOiAwLjA0NSwgZDogMS4wMiB9LCBkcmF3ZXJMaW5lLCBuZXcgVmVjMygwLCB5LCAwKSlcclxuICAgIH1cclxuICAgIGtpdC5iYWxsKHJvb3QsICdLbm9iMScsIDAuMDU1LCBzdHlsZShDLmJyYXNzLCB7IHNoYWRlOiBDLmJyYXNzRGFyayB9KSxcclxuICAgICAgICBuZXcgVmVjMygwLCAwLjY2LCAwLjUzKSlcclxuICAgIGtpdC5iYWxsKHJvb3QsICdLbm9iMicsIDAuMDU1LCBzdHlsZShDLmJyYXNzLCB7IHNoYWRlOiBDLmJyYXNzRGFyayB9KSxcclxuICAgICAgICBuZXcgVmVjMygwLCAxLjE0LCAwLjUzKSlcclxuICAgIC8vIOWPsOmdoumkkOWFt++8mueri+W8j+ein+aetu+8iOWchuebmOmdouacnemVnOWktO+8jOeOq+eRsC/pm77ok53mj4/ovrnvvIkrIOS4pOWPqueyieW9qeWchueilyDigJTigJQg5pu/5o2i5pen54mI5bCW6aG25Y+w54GvXHJcbiAgICAvLyDvvIjlubPmkZ7nmoToloTnm5jlnKjkvY7mnLrkvY3kuIvlj6rliankuIDmnaHnur/vvIznq4votbfmnaXmiY3or7vlvpflh7pcIuebmOWtkFwi77yJXHJcbiAgICBjb25zdCBjaGluYSA9IHN0eWxlKEMuY2hpbmEsIHsgc2hhZGU6IHNoaWZ0KEMuY2hpbmEsIC0wLjEpIH0pXHJcbiAgICBjb25zdCByYWNrV29vZCA9IHN0eWxlKEMud29vZCwgeyBzaGFkZTogQy53b29kRGFyayB9KVxyXG4gICAga2l0LmJveFBhcnQocm9vdCwgJ1JhY2tCYXNlJywgeyB3OiAwLjcyLCBoOiAwLjA1LCBkOiAwLjI2IH0sIHJhY2tXb29kLCBuZXcgVmVjMygtMC4yOCwgMS40NiwgMC4xKSlcclxuICAgIGtpdC5ib3hQYXJ0KHJvb3QsICdSYWNrQmFjaycsIHsgdzogMC43MiwgaDogMC4zNCwgZDogMC4wMzUgfSwgcmFja1dvb2QsIG5ldyBWZWMzKC0wLjI4LCAxLjYzLCAtMC4wMykpXHJcbiAgICBraXQuYm94UGFydChyb290LCAnUmFja1Bvc3RMJywgeyB3OiAwLjA1LCBoOiAwLjMsIGQ6IDAuMjQgfSwgcmFja1dvb2QsIG5ldyBWZWMzKC0wLjU1LCAxLjYyLCAwLjA2KSlcclxuICAgIGtpdC5ib3hQYXJ0KHJvb3QsICdSYWNrUG9zdFInLCB7IHc6IDAuMDUsIGg6IDAuMywgZDogMC4yNCB9LCByYWNrV29vZCwgbmV3IFZlYzMoLTAuMDEsIDEuNjIsIDAuMDYpKVxyXG4gICAgY29uc3Qgc3RhbmRpbmdQbGF0ZSA9ICh4OiBudW1iZXIsIHJpbTogQ29sb3IpOiB2b2lkID0+IHtcclxuICAgICAgICBjb25zdCBmYWNlID0gbmV3IFZlYzMoeCwgMS42NDUsIDAuMSlcclxuICAgICAgICBraXQuY3lsUGFydChyb290LCAnUGxhdGUnLCAwLjE2LCAwLjE2LCAwLjAzNSwgY2hpbmEsIGZhY2UsIHsgcm90OiBuZXcgVmVjMyg5MCwgMCwgMCkgfSlcclxuICAgICAgICBraXQuY3lsUGFydChyb290LCAnUGxhdGVSaW0nLCAwLjE3LCAwLjE3LCAwLjAxNiwgc3R5bGUocmltLCB7IHNoYWRlOiBzaGlmdChyaW0sIC0wLjE4KSB9KSxcclxuICAgICAgICAgICAgbmV3IFZlYzMoeCwgMS42NDUsIDAuMTE1KSwgeyByb3Q6IG5ldyBWZWMzKDkwLCAwLCAwKSB9KVxyXG4gICAgICAgIGtpdC5jeWxQYXJ0KHJvb3QsICdQbGF0ZUh1YicsIDAuMDU1LCAwLjA1NSwgMC4wMTgsIHN0eWxlKHJpbSwgeyBzaGFkZTogc2hpZnQocmltLCAtMC4wOCkgfSksXHJcbiAgICAgICAgICAgIG5ldyBWZWMzKHgsIDEuNjQ1LCAwLjEyOCksIHsgcm90OiBuZXcgVmVjMyg5MCwgMCwgMCkgfSlcclxuICAgIH1cclxuICAgIHN0YW5kaW5nUGxhdGUoLTAuNDQsIEMuaGVhcnQpXHJcbiAgICBzdGFuZGluZ1BsYXRlKC0wLjEyLCBDLmJvb2sxKVxyXG4gICAgLy8g5ZyG56KX77ya57KJ5b2p55CD5L2T5LiL6Zm35oiQ56KX6LqrICsg5aW25rK556KX5Y+j5YaF5aOB77yI5ZyG5ram56uL5L2T77yM5Y+M6Imy77yJXHJcbiAgICBjb25zdCBib3dsID0gKHg6IG51bWJlciwgY29sb3I6IENvbG9yKTogdm9pZCA9PiB7XHJcbiAgICAgICAga2l0LmJhbGwocm9vdCwgJ0Jvd2xCb2R5JywgMC4xNSwgc3R5bGUoY29sb3IsIHsgc2hhZGU6IHNoaWZ0KGNvbG9yLCAtMC4yKSB9KSxcclxuICAgICAgICAgICAgbmV3IFZlYzMoeCwgMS41MywgMC4xMiksIHsgc2NhbGU6IG5ldyBWZWMzKDEsIDAuNzIsIDEpIH0pXHJcbiAgICAgICAga2l0LmJhbGwocm9vdCwgJ0Jvd2xJbm5lcicsIDAuMTUsIHN0eWxlKEMuY2hpbmEsIHsgc2hhZGU6IHNoaWZ0KEMuY2hpbmEsIC0wLjA2KSB9KSxcclxuICAgICAgICAgICAgbmV3IFZlYzMoeCwgMS41NjIsIDAuMTIpLCB7IHNjYWxlOiBuZXcgVmVjMygwLjgsIDAuMywgMC44KSB9KVxyXG4gICAgfVxyXG4gICAgYm93bCgwLjE4LCBDLmxlYWYpXHJcbiAgICBib3dsKDAuNDYsIEMuYm9vazEpXHJcbn1cclxuXHJcbi8qKiDpspzoirHoirHnk7bvvIjlt6blkI7op5LvvInvvJrpmbbnk7foirHnk7YgKyDkuInmnZ/pspzoirHvvIjnjqvnkbAv5aW25rK56buEL+mbvuiTne+8iSsg5Y+24oCU4oCU5pu/5o2i5pen54mI57u/55uG5qC9ICovXHJcbmZ1bmN0aW9uIGJ1aWxkUGxhbnQocGFyZW50OiBOb2RlLCBraXQ6IFBldEJ1aWxkZXJLaXQpOiB2b2lkIHtcclxuICAgIGNvbnN0IHJvb3QgPSBraXQubWFrZTNkTm9kZShwYXJlbnQsICdGbG93ZXJWYXNlJywgbmV3IFZlYzMoLTMuNywgMCwgLTIuMSkpXHJcbiAgICAvLyDpmbbnk7foirHnk7bvvJrpvJPohbkgKyDmnZ/poohcclxuICAgIGtpdC5jeWxQYXJ0KHJvb3QsICdWYXNlQm9keScsIDAuMzQsIDAuMjQsIDAuNSwgc3R5bGUoQy52YXNlLCB7IHNoYWRlOiBzaGlmdChDLnZhc2UsIC0wLjEyKSB9KSxcclxuICAgICAgICBuZXcgVmVjMygwLCAwLjI1LCAwKSlcclxuICAgIGtpdC5jeWxQYXJ0KHJvb3QsICdWYXNlTmVjaycsIDAuMTcsIDAuMjEsIDAuMywgc3R5bGUoQy52YXNlLCB7IHNoYWRlOiBzaGlmdChDLnZhc2UsIC0wLjA4KSB9KSxcclxuICAgICAgICBuZXcgVmVjMygwLCAwLjYsIDApKVxyXG4gICAga2l0LmN5bFBhcnQocm9vdCwgJ1Zhc2VSaW0nLCAwLjE5LCAwLjE5LCAwLjAzNSwgc3R5bGUoQy5oZWFydCwgeyBzaGFkZTogc2hpZnQoQy5oZWFydCwgLTAuMikgfSksXHJcbiAgICAgICAgbmV3IFZlYzMoMCwgMC43NjUsIDApKVxyXG4gICAgLy8g5LiJ5p2f6Iqx77ya6IyO5LuO55O25Y+j5pWj5byA77yM6Iqx5aS0ID0g6Iqx55Oj55CDICsg6Iqx6IqvXHJcbiAgICBjb25zdCBibG9vbXM6IEFycmF5PFtudW1iZXIsIG51bWJlciwgbnVtYmVyLCBDb2xvcl0+ID0gW1xyXG4gICAgICAgIFstMC4yNCwgMS4zLCAwLjA4LCBDLmhlYXJ0XSxcclxuICAgICAgICBbMC4wNCwgMS40NCwgLTAuMDYsIEMuYm9vazNdLFxyXG4gICAgICAgIFswLjI3LCAxLjI0LCAwLjEyLCBDLmJvb2sxXSxcclxuICAgIF1cclxuICAgIGZvciAoY29uc3QgW3gsIHksIHosIGNvbG9yXSBvZiBibG9vbXMpIHtcclxuICAgICAgICBjb25zdCBkeCA9IHhcclxuICAgICAgICBjb25zdCBkeiA9IHpcclxuICAgICAgICBjb25zdCBkeSA9IHkgLSAwLjc4XHJcbiAgICAgICAgY29uc3QgbGVuID0gTWF0aC5zcXJ0KGR4ICogZHggKyBkeSAqIGR5ICsgZHogKiBkeilcclxuICAgICAgICBjb25zdCBtaWQgPSBuZXcgVmVjMyhkeCAvIDIsIDAuNzggKyBkeSAvIDIsIGR6IC8gMilcclxuICAgICAgICBraXQuY2FwUGFydChyb290LCAnU3RlbScsIDAuMDI2LCBsZW4sIHN0eWxlKEMubGVhZkRhcmssIHsgc2hhZGU6IHNoaWZ0KEMubGVhZkRhcmssIC0wLjE4KSB9KSxcclxuICAgICAgICAgICAgbWlkLCB7IHJvdDogbmV3IFZlYzMoKGR6IC8gbGVuKSAqIDgwLCAwLCAoLWR4IC8gbGVuKSAqIDgwKSB9KVxyXG4gICAgICAgIGtpdC5iYWxsKHJvb3QsICdQZXRhbCcsIDAuMTEsIHN0eWxlKGNvbG9yLCB7IHNoYWRlOiBzaGlmdChjb2xvciwgLTAuMikgfSksIG5ldyBWZWMzKHgsIHksIHopKVxyXG4gICAgICAgIGtpdC5iYWxsKHJvb3QsICdDb3JlJywgMC4wNDUsIHN0eWxlKEMuYm9vazMsIHsgc2hhZGU6IHNoaWZ0KEMuYm9vazMsIC0wLjI0KSB9KSxcclxuICAgICAgICAgICAgbmV3IFZlYzMoeCwgeSArIDAuMDIsIHopKVxyXG4gICAgfVxyXG4gICAgLy8g5Y+254mH54K557yAXHJcbiAgICBraXQuYmFsbChyb290LCAnTGVhZkEnLCAwLjA5LCBzdHlsZShDLmxlYWYsIHsgc2hhZGU6IEMubGVhZkRhcmsgfSksIG5ldyBWZWMzKC0wLjE2LCAwLjk1LCAwLjA1KSwge1xyXG4gICAgICAgIHNjYWxlOiBuZXcgVmVjMygxLCAwLjU1LCAxKSxcclxuICAgIH0pXHJcbiAgICBraXQuYmFsbChyb290LCAnTGVhZkInLCAwLjA4LCBzdHlsZShDLmxlYWYsIHsgc2hhZGU6IEMubGVhZkRhcmsgfSksIG5ldyBWZWMzKDAuMTgsIDEuMDIsIDAuMDIpLCB7XHJcbiAgICAgICAgc2NhbGU6IG5ldyBWZWMzKDEsIDAuNTUsIDEpLFxyXG4gICAgfSlcclxufVxyXG5cclxuLyoqIOaVo+iQveeOqeWFt++8muearueQgyAvIOavm+e6v+eQgyAvIOWwj+m7hOm4rSAvIOmqqOWktO+8iOS4reaZr+S4jueqnei+ue+8jOmBv+WFjemBruaMoeWuoOeJqeato+mdou+8ie+8m+i/lOWbnuearueQg+S+m+awm+WbtOWKqOeUu+W8leeUqCAqL1xyXG5mdW5jdGlvbiBidWlsZFRveXMocGFyZW50OiBOb2RlLCBraXQ6IFBldEJ1aWxkZXJLaXQpOiBOb2RlIHtcclxuICAgIGNvbnN0IHJvb3QgPSBraXQubWFrZTNkTm9kZShwYXJlbnQsICdUb3lzJywgbmV3IFZlYzMoMCwgMCwgMCkpXHJcbiAgICAvLyDnmq7nkIPvvIjluKbnmb3oibLmnaHnurnmhJ/vvJrlj6DliqDkuIDlnIjlsI/nkIPvvIlcclxuICAgIGNvbnN0IGJhbGwgPSBraXQubWFrZTNkTm9kZShyb290LCAnVG95QmFsbCcsIG5ldyBWZWMzKC0xLjM1LCAwLjE5LCAxLjA1KSlcclxuICAgIGtpdC5iYWxsKGJhbGwsICdDb3JlJywgMC4xOSwgc3R5bGUoQy5iYWxsLCB7IHNoYWRlOiBzaGlmdChDLmJhbGwsIC0wLjI0KSB9KSwgbmV3IFZlYzMoMCwgMCwgMCkpXHJcbiAgICBraXQuYmFsbChiYWxsLCAnU3RyaXBlJywgMC4xOTUsIHN0eWxlKG5ldyBDb2xvcigweEZELCAweEY4LCAweEYwLCAyNTUpLCB7IHNoYWRlOiBzaGlmdChDLmJhbGwsIC0wLjM0KSB9KSxcclxuICAgICAgICBuZXcgVmVjMygwLCAwLCAwKSwgeyBzY2FsZTogbmV3IFZlYzMoMC4zMiwgMS4wMiwgMS4wMikgfSlcclxuICAgIC8vIOavm+e6v+eQg1xyXG4gICAga2l0LmJhbGwocm9vdCwgJ1lhcm4nLCAwLjE3LCBzdHlsZShDLnlhcm4sIHsgc2hhZGU6IHNoaWZ0KEMueWFybiwgLTAuMjIpIH0pLCBuZXcgVmVjMygtMS45NSwgMC4xNywgMC41KSlcclxuICAgIGtpdC50b3J1c1BhcnQocm9vdCwgJ1lhcm5SaW5nJywgMC4xNywgMC4wMiwgc3R5bGUoc2hpZnQoQy55YXJuLCAtMC4yOCksIHsgc2hhZGU6IHNoaWZ0KEMueWFybiwgLTAuMzYpIH0pLFxyXG4gICAgICAgIG5ldyBWZWMzKC0xLjk1LCAwLjE3LCAwLjUpLCB7IHJvdDogbmV3IFZlYzMoMjQsIDAsIDE4KSB9KVxyXG4gICAgLy8g5bCP6buE6bitXHJcbiAgICBjb25zdCBkdWNrID0ga2l0Lm1ha2UzZE5vZGUocm9vdCwgJ0R1Y2snLCBuZXcgVmVjMygyLjE1LCAwLCAxLjc1KSlcclxuICAgIGtpdC5iYWxsKGR1Y2ssICdCb2R5JywgMC4xOSwgc3R5bGUoQy5kdWNrLCB7IHNoYWRlOiBzaGlmdChDLmR1Y2ssIC0wLjIpIH0pLCBuZXcgVmVjMygwLCAwLjE2LCAwKSwge1xyXG4gICAgICAgIHNjYWxlOiBuZXcgVmVjMygxLCAwLjksIDEuMSksXHJcbiAgICB9KVxyXG4gICAga2l0LmJhbGwoZHVjaywgJ0hlYWQnLCAwLjEzLCBzdHlsZShDLmR1Y2ssIHsgc2hhZGU6IHNoaWZ0KEMuZHVjaywgLTAuMikgfSksIG5ldyBWZWMzKDAsIDAuMzYsIDAuMDYpKVxyXG4gICAga2l0LmNvbmVQYXJ0KGR1Y2ssICdCZWFrJywgMC4wNTUsIDAuMTIsIHN0eWxlKEMuZHVja0JlYWssIHsgc2hhZGU6IHNoaWZ0KEMuZHVja0JlYWssIC0wLjIpIH0pLFxyXG4gICAgICAgIG5ldyBWZWMzKDAsIDAuMzQsIDAuMiksIHsgcm90OiBuZXcgVmVjMyg5MCwgMCwgMCkgfSlcclxuICAgIC8vIOmqqOWktOeOqeWFt1xyXG4gICAgY29uc3QgYm9uZSA9IGtpdC5tYWtlM2ROb2RlKHJvb3QsICdCb25lJywgbmV3IFZlYzMoMS40NSwgMC4xLCAyLjApKVxyXG4gICAga2l0LmNhcFBhcnQoYm9uZSwgJ0JhcicsIDAuMDYsIDAuMzQsIHN0eWxlKG5ldyBDb2xvcigweEZELCAweEYyLCAweERFLCAyNTUpLCB7IHNoYWRlOiBuZXcgQ29sb3IoMHhFMywgMHhEMiwgMHhCOCwgMjU1KSB9KSxcclxuICAgICAgICBuZXcgVmVjMygwLCAwLCAwKSwgeyByb3Q6IG5ldyBWZWMzKDAsIDAsIDc0KSB9KVxyXG4gICAga2l0LmJhbGwoYm9uZSwgJ0tub2JMJywgMC4wOSwgc3R5bGUobmV3IENvbG9yKDB4RkQsIDB4RjIsIDB4REUsIDI1NSksIHsgc2hhZGU6IG5ldyBDb2xvcigweEUzLCAweEQyLCAweEI4LCAyNTUpIH0pLFxyXG4gICAgICAgIG5ldyBWZWMzKC0wLjE3LCAwLjA0LCAwKSlcclxuICAgIGtpdC5iYWxsKGJvbmUsICdLbm9iUicsIDAuMDksIHN0eWxlKG5ldyBDb2xvcigweEZELCAweEYyLCAweERFLCAyNTUpLCB7IHNoYWRlOiBuZXcgQ29sb3IoMHhFMywgMHhEMiwgMHhCOCwgMjU1KSB9KSxcclxuICAgICAgICBuZXcgVmVjMygwLjE3LCAtMC4wNCwgMCkpXHJcbiAgICByZXR1cm4gYmFsbFxyXG59XHJcblxyXG4vKiog5YmN5pmv77ya5Zyw5q+v5YmN57yY6Z2g5Z6r77yI5Yi26YCg5YmN5ZCO5pmv5bGC5qyh77yJICovXHJcbmZ1bmN0aW9uIGJ1aWxkRm9yZWdyb3VuZChwYXJlbnQ6IE5vZGUsIGtpdDogUGV0QnVpbGRlcktpdCk6IHZvaWQge1xyXG4gICAgY29uc3Qgcm9vdCA9IGtpdC5tYWtlM2ROb2RlKHBhcmVudCwgJ0ZvcmVncm91bmQnLCBuZXcgVmVjMygwLCAwLCAwKSlcclxuICAgIGtpdC5iYWxsKHJvb3QsICdDdXNoaW9uJywgMC40Niwgc3R5bGUoQy5jdXNoaW9uLCB7IHNoYWRlOiBzaGlmdChDLmN1c2hpb24sIC0wLjE2KSB9KSxcclxuICAgICAgICBuZXcgVmVjMygtMS4zLCAwLjE2LCAyLjQ1KSwgeyBzY2FsZTogbmV3IFZlYzMoMS4yNSwgMC41NSwgMC45NSkgfSlcclxuICAgIGtpdC5iYWxsKHJvb3QsICdDdXNoaW9uU21hbGwnLCAwLjMsIHN0eWxlKHNoaWZ0KEMuY3VzaGlvbiwgLTAuMTIpLCB7IHNoYWRlOiBzaGlmdChDLmN1c2hpb24sIC0wLjI0KSB9KSxcclxuICAgICAgICBuZXcgVmVjMygyLjAsIDAuMTIsIDIuNiksIHsgc2NhbGU6IG5ldyBWZWMzKDEuMiwgMC42LCAxKSB9KVxyXG59XHJcblxyXG4vKiog5oyC54Gv5Liy77ya5pqW6buE54Gv5rOh5rK/5ZCO5aKZ5LiK5pa55byn57q/5YiG5biD77yI6L275b6u5ZG85ZC46Zeq54OB77yJICovXHJcbmZ1bmN0aW9uIGJ1aWxkTGFtcFN0cmluZyhwYXJlbnQ6IE5vZGUsIGtpdDogUGV0QnVpbGRlcktpdCk6IE5vZGVbXSB7XHJcbiAgICBjb25zdCByb290ID0ga2l0Lm1ha2UzZE5vZGUocGFyZW50LCAnTGFtcFN0cmluZycsIG5ldyBWZWMzKDAsIDAsIC0yLjcyKSlcclxuICAgIGNvbnN0IGJ1bGJzOiBOb2RlW10gPSBbXVxyXG4gICAgZm9yIChsZXQgaW5kZXggPSAwOyBpbmRleCA8IDk7IGluZGV4ICs9IDEpIHtcclxuICAgICAgICBjb25zdCB0ID0gaW5kZXggLyA4XHJcbiAgICAgICAgY29uc3QgeCA9IC00LjQgKyB0ICogOC44XHJcbiAgICAgICAgY29uc3QgeSA9IDMuNzIgLSBNYXRoLnNpbih0ICogTWF0aC5QSSkgKiAwLjQyXHJcbiAgICAgICAgY29uc3QgYnVsYiA9IGtpdC5iYWxsKHJvb3QsICdCdWxiJyArIGluZGV4LCAwLjA4NSwge1xyXG4gICAgICAgICAgICBjb2xvcjogQy5idWxiLCBzaGFkZTogc2hpZnQoQy5idWxiLCAtMC4wOCksIGdsb3c6IHRydWUsIG91dGxpbmU6IDAsXHJcbiAgICAgICAgfSwgbmV3IFZlYzMoeCwgeSwgMCkpXHJcbiAgICAgICAgYnVsYnMucHVzaChidWxiKVxyXG4gICAgfVxyXG4gICAgcmV0dXJuIGJ1bGJzXHJcbn1cclxuXHJcbi8qKiDnqpfovrnpmLPlhYnlhYnmlpHvvIjlnLDmnb/kuIrmlpznva7nmoTmmpblhYnniYfvvIzovbvlvq7lkbzlkLjvvIkgKi9cclxuLyoqXHJcbiAqIOWutuWFt+S4jumBk+WFt+eahOaOpeinpumYtOW9sSArIOWimeiEmueOr+Wig+mBruiUveOAglxyXG4gKlxyXG4gKiDmnKzlt6XnqIsqKuayoeacieWunuaXtumYtOW9sSoq77yI5Zy65pmv5peg5YWJ5rqQ6IqC54K544CBU2hhZG93c0luZm8g5YWz6Zet77yMcGV0LXRvb24g5Lmf5piv6Ieq56CU552A6Imy5ZmoXHJcbiAqIOS4jemHh+agt+mYtOW9sei0tOWbvu+8ie+8jOaJgOS7peS7u+S9lei0tOWcsOeJqeS7tumDveS4jeS8muWcqOWcsOadv+S4iueVmeW9sSDigJTigJQg57uT5p6c5piv5omA5pyJ5a625YW36YO9XCLmta5cIuedgOOAglxyXG4gKiDov5nph4znlKjmn5TovrnotLToirHpgJDkuKrooaXvvJrmmpbopJDoibIgKyDlvoTlkJEgYWxwaGEg5riQ5Y+Y77yM5Y+g5Zyo54mp5L2T5q2j5LiL5pa555qE5Zyw5p2/5LiK44CCXHJcbiAqXHJcbiAqIOKaoO+4jyDpq5jluqblv4XpobvmjIkqKuiQveeCueaJgOWcqOeahOmCo+S4gOWxgioq57uZ77ya5Zyw5q+v5piv6YCQ5bGC5Y+g6LW35p2l55qE5ZyG55uYXHJcbiAqIO+8iEVkZ2Ug6aG2IOKJiDAuMDQ1IC8gT3V0ZXIg4omIMC4wNTUgLyBJbm5lciDiiYgwLjA2MyAvIENlbnRlciDiiYgwLjA377yM5ZyG5b+D5ZyoIHo9MC4zNeOAgeWNiuW+hCAyLjQy77yJ77yMXHJcbiAqIOaJgOS7peiQveWcqOWcsOavr+S4iueahOeJqeS7tu+8iOeQg+OAgeejqOeJmeajku+8iemYtOW9seW/hemhu+aKrOWIsOWcsOavr+mdouS5i+S4iu+8jFxyXG4gKiDlkKbliJnkvJrooqvlnLDmr6/mlbTkuKrnm5bkvY8g4oCU4oCUIOesrOS4gOeJiOWFqOaUvuWcqCB5PTAuMDA577yM57uT5p6c5LiA5Liq6YO955yL5LiN6KeB44CCXHJcbiAqXHJcbiAqIOaRhuS9jeS4juWQhCBidWlsZCooKSDkuIDkuIDlr7nlupTvvIjmlLnmkYbkvY3ml7blv4XpobvlkIzmraXmlLnov5nph4zvvInjgIJcclxuICovXHJcbmZ1bmN0aW9uIGJ1aWxkQ29udGFjdFNoYWRvd3MocGFyZW50OiBOb2RlLCBraXQ6IFBldEJ1aWxkZXJLaXQpOiB2b2lkIHtcclxuICAgIGNvbnN0IHJvb3QgPSBraXQubWFrZTNkTm9kZShwYXJlbnQsICdDb250YWN0U2hhZG93cycsIG5ldyBWZWMzKDAsIDAsIDApKVxyXG4gICAgY29uc3QgYmxvYiA9IChuYW1lOiBzdHJpbmcsIHJ4OiBudW1iZXIsIHJ6OiBudW1iZXIsIHg6IG51bWJlciwgeTogbnVtYmVyLCB6OiBudW1iZXIsIGFscGhhOiBudW1iZXIpOiB2b2lkID0+IHtcclxuICAgICAgICBraXQuZGVjYWwocm9vdCwgbmFtZSwgW3J4LCAwLjAwOCwgcnpdLCB7XHJcbiAgICAgICAgICAgIGNvbG9yOiBDLnNoYWRvdywgc2hhZGU6IEMuc2hhZG93LCBhbHBoYSwgc29mdDogWzAuMDIsIDEuMF0sXHJcbiAgICAgICAgfSwgbmV3IFZlYzMoeCwgeSwgeikpXHJcbiAgICB9XHJcbiAgICAvLyDlnLDmr6/vvJrlnIblv4MgKDAsIDAuMzUp44CB5Y2K5b6EIDIuNDLvvJtJbm5lciDljYrlvoQgMS45MlxyXG4gICAgYmxvYignU2hhZG93Q2FiaW5ldCcsIDEuMTUsIDAuNjIsIC0zLjU1LCAwLjAwOSwgLTAuMTAsIDc4KSAgIC8vIOafnOWtkO+8iOijuOWcsOadv++8iVxyXG4gICAgYmxvYignU2hhZG93UGxhbnQnLCAwLjQ0LCAwLjQ0LCAtMy43MCwgMC4wMDksIC0yLjEwLCA2NikgICAgIC8vIOebhuagve+8iOijuOWcsOadv++8iVxyXG4gICAgYmxvYignU2hhZG93QmVkJywgMS4wNSwgMC43OCwgMi42MCwgMC4wMDksIDEuMTUsIDcwKSAgICAgICAgIC8vIOeMq+eqne+8iOijuOWcsOadv++8iVxyXG4gICAgYmxvYignU2hhZG93RHVjaycsIDAuMzIsIDAuMjQsIDIuMTUsIDAuMDA5LCAxLjc1LCA3MCkgICAgICAgIC8vIOWwj+m7hOm4re+8iOijuOWcsOadv++8iVxyXG4gICAgYmxvYignU2hhZG93QmFsbCcsIDAuMjgsIDAuMjgsIC0xLjM1LCAwLjA2NiwgMS4wNSwgNzQpICAgICAgIC8vIOeOqeWFt+eQg++8iOWcsOavryBJbm5lciDpnaLkuYvkuIrvvIlcclxuICAgIGJsb2IoJ1NoYWRvd0JvbmUnLCAwLjM4LCAwLjE2LCAxLjQ1LCAwLjA1OCwgMi4wMCwgNjIpICAgICAgICAvLyDno6jniZnmo5LvvIjlnLDmr68gT3V0ZXIg6Z2i5LmL5LiK77yJXHJcbiAgICAvLyDlopnohJrnjq/looPpga7olL3vvJrlopnkuI7lnLDmnb/kuqTnlYzlpITnmoTmmpfluKbvvIzmmK9cIuepuumXtOaEn1wi5pyA5buJ5Lu35Lmf5pyA5pyJ5pWI55qE5LiA56yUXHJcbiAgICBibG9iKCdBT0JhY2tXYWxsJywgNS4yMCwgMC41NSwgMC4wMCwgMC4wMTIsIC0yLjQ1LCA1OCkgICAgICAgLy8g5ZCO5aKZ77yI5aKZ6Z2iIHo9LTIuODXvvIlcclxuICAgIGJsb2IoJ0FPTGVmdFdhbGwnLCAwLjUyLCA0LjIwLCAtNC4xMCwgMC4wMTIsIDAuNDAsIDQ2KSAgICAgICAvLyDlt6blopnvvIjlopnpnaIgeD0tNC40Ne+8iVxyXG59XHJcblxyXG4vKipcclxuICog56qX5YWJ6JC95Zyo5Zyw5p2/5LiK55qE5p+U5YWJ5paR77yIXCLlhYnokL3lnLBcIuaYr+WIpOaWreS4gOS4quiZmuaLn+epuumXtOaYr+WQpuecn+WunuacgOacieaViOeahOe6v+e0ou+8ieOAglxyXG4gKlxyXG4gKiDml6flrp7njrDmmK/kuIDlnZcgYWxwaGEgNzAg55qEKirnoazovrkqKuaWueadv++8iGJveFBhcnQgKyBwbGFpbiDlvJXmk47mnZDotKjvvInigJTigJRcclxuICog5Zyo5rKh5pyJ5a6e5pe26Zi05b2x55qE5Zy65pmv6YeM77yM5a6D6K+75L2cXCLlnLDkuIrotLTkuobkuIDlnZfnmb3mlpFcIuiAjOS4jeaYr+WFie+8jOWPjeiAjOWKoOmHjeS6huWBh+OAglxyXG4gKiDnjrDmlLnkuLogYGtpdC5kZWNhbGDvvJrljZXkvY3mpK3nkIPmiorlvZLkuIDljJblvoTlkJHot53nprvlhpnov5sgdXYueO+8jOeUsSBwZXQtdG9vbiDnmoQgZGVjYWxDdHJsXHJcbiAqIOeUn+aIkOW+hOWQkSBhbHBoYSDmuJDlj5jvvIzljovmiYHmiJDlnLDpnaLmpK3lnIblkI7ovrnnvJjoh6rnhLbljJblvIDvvJvplb/ovbTmjInnqpflhYnlhaXlsITmlrnlkJFcclxuICog77yI5LiW55WMICt4Lyt677yM5Y2z5Y+z5YmN5pa577yJ5peL6L2s5a+56b2QIOKAlOKAlCDlhYnmlpHnmoTmnJ3lkJHlv4XpobvkuI7kuLvlhYnkuIDoh7TvvIzlkKbliJnmlrnlkJHmhJ/kupLnm7jmiZPmnrbjgIJcclxuICog5Lik5bGC5Y+g5Yqg77yI5Li75paRICsg5aSW5ZyI5YWJ5pmV77yJ6YG/5YWN5Y2V5bGC5qSt5ZyG6Zyy5Ye66L6555WM44CCXHJcbiAqXHJcbiAqIOiQveeCueaMieWHoOS9leWPjeaOqO+8mueql+WcqCAoLTIuMCwgMi40NSwgLTIuODUp77yM5Li75YWJ6KGM6L+b5pa55ZCRICgwLjQ2LC0wLjU2LDAuNjkp77yMXHJcbiAqIOiQveWIsCB5PTAg55qE5Zyw6Z2i5pe25Li6IHQgPSAyLjQ1LzAuNTYg4omIIDQuMzgg4oaSICgwLjAsIDAsIDAuMTcp44CCXHJcbiAqIOmCo+S4queCueato+WlveaYr+WcsOavr+S4reW/g++8iOWuoOeJqeermeS9je+8ie+8jOaJgOS7peWFieaWkeS8muiiq+WcsOavr+WQg+aOieWkp+mDqOWIhiDigJTigJRcclxuICog5Zug5q2k6L+Z6YeM5oqK5YWJ5paR5pS+5Zyo5Zyw5q+v6L6557yY5aSW55qE6KO45Zyw5p2/5LiK77yI5bem5ZCO77yJ77yM5pei5Zyo55S76Z2i6YeM55yL5b6X6KeB77yM5Lmf6L+Y5Zyo56qX5YWJ55qE5p2l5ZCR5LiK44CCXHJcbiAqL1xyXG5mdW5jdGlvbiBidWlsZFN1bkJlYW0ocGFyZW50OiBOb2RlLCBraXQ6IFBldEJ1aWxkZXJLaXQpOiBOb2RlIHtcclxuICAgIGNvbnN0IHJvb3QgPSBraXQubWFrZTNkTm9kZShwYXJlbnQsICdTdW5CZWFtJywgbmV3IFZlYzMoMCwgMCwgMCkpXHJcbiAgICBraXQuZGVjYWwocm9vdCwgJ1Bvb2wnLCBbMi4zMCwgMC4wMTIsIDEuMzVdLCB7XHJcbiAgICAgICAgY29sb3I6IEMuc3VuLCBzaGFkZTogQy5zdW4sIGFscGhhOiAxMzIsIHNvZnQ6IFswLjA2LCAxLjBdLFxyXG4gICAgfSwgbmV3IFZlYzMoLTEuNTUsIDAuMDE0LCAtMS43MCksIG5ldyBWZWMzKDAsIC01NiwgMCkpXHJcbiAgICBraXQuZGVjYWwocm9vdCwgJ0hhbG8nLCBbMy4zMCwgMC4wMTAsIDIuMDBdLCB7XHJcbiAgICAgICAgY29sb3I6IEMuc3VuLCBzaGFkZTogQy5zdW4sIGFscGhhOiA1NCwgc29mdDogWzAuMTIsIDEuMF0sXHJcbiAgICB9LCBuZXcgVmVjMygtMS4wNSwgMC4wMTEsIC0xLjM1KSwgbmV3IFZlYzMoMCwgLTU2LCAwKSlcclxuICAgIHJldHVybiByb290XHJcbn1cclxuIl19