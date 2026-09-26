System.register(["__unresolved_0", "cc", "__unresolved_1", "__unresolved_2"], function (_export, _context) {
  "use strict";

  var _reporterNs, _cclegacy, __checkObsolete__, __checkObsoleteInNamespace__, Color, EffectAsset, Layers, Material, Vec3, Vec4, ellipsoid, merge, radialUV, spherePatch, shift, PetBuilderKit, _crd, DEFAULT_OUTLINE;

  function ownKeys(e, r) { var t = Object.keys(e); if (Object.getOwnPropertySymbols) { var o = Object.getOwnPropertySymbols(e); r && (o = o.filter(function (r) { return Object.getOwnPropertyDescriptor(e, r).enumerable; })), t.push.apply(t, o); } return t; }

  function _objectSpread(e) { for (var r = 1; r < arguments.length; r++) { var t = null != arguments[r] ? arguments[r] : {}; r % 2 ? ownKeys(Object(t), !0).forEach(function (r) { _defineProperty(e, r, t[r]); }) : Object.getOwnPropertyDescriptors ? Object.defineProperties(e, Object.getOwnPropertyDescriptors(t)) : ownKeys(Object(t)).forEach(function (r) { Object.defineProperty(e, r, Object.getOwnPropertyDescriptor(t, r)); }); } return e; }

  function _defineProperty(e, r, t) { return (r = _toPropertyKey(r)) in e ? Object.defineProperty(e, r, { value: t, enumerable: !0, configurable: !0, writable: !0 }) : e[r] = t, e; }

  function _toPropertyKey(t) { var i = _toPrimitive(t, "string"); return "symbol" == typeof i ? i : i + ""; }

  function _toPrimitive(t, r) { if ("object" != typeof t || !t) return t; var e = t[Symbol.toPrimitive]; if (void 0 !== e) { var i = e.call(t, r || "default"); if ("object" != typeof i) return i; throw new TypeError("@@toPrimitive must return a primitive value."); } return ("string" === r ? String : Number)(t); }

  function _reportPossibleCrUseOfellipsoid(extras) {
    _reporterNs.report("ellipsoid", "./PetMeshFactory", _context.meta, extras);
  }

  function _reportPossibleCrUseOfmerge(extras) {
    _reporterNs.report("merge", "./PetMeshFactory", _context.meta, extras);
  }

  function _reportPossibleCrUseOfradialUV(extras) {
    _reporterNs.report("radialUV", "./PetMeshFactory", _context.meta, extras);
  }

  function _reportPossibleCrUseOfspherePatch(extras) {
    _reporterNs.report("spherePatch", "./PetMeshFactory", _context.meta, extras);
  }

  function _reportPossibleCrUseOfshift(extras) {
    _reporterNs.report("shift", "./PetGameTheme", _context.meta, extras);
  }

  _export("PetBuilderKit", void 0);

  return {
    setters: [function (_unresolved_) {
      _reporterNs = _unresolved_;
    }, function (_cc) {
      _cclegacy = _cc.cclegacy;
      __checkObsolete__ = _cc.__checkObsolete__;
      __checkObsoleteInNamespace__ = _cc.__checkObsoleteInNamespace__;
      Color = _cc.Color;
      EffectAsset = _cc.EffectAsset;
      Layers = _cc.Layers;
      Material = _cc.Material;
      Vec3 = _cc.Vec3;
      Vec4 = _cc.Vec4;
    }, function (_unresolved_2) {
      ellipsoid = _unresolved_2.ellipsoid;
      merge = _unresolved_2.merge;
      radialUV = _unresolved_2.radialUV;
      spherePatch = _unresolved_2.spherePatch;
    }, function (_unresolved_3) {
      shift = _unresolved_3.shift;
    }],
    execute: function () {
      _crd = true;

      _cclegacy._RF.push({}, "36d45RC16lG1Led9iTiqfBu", "PetBuilderKit", undefined);

      __checkObsolete__(['Color', 'Component', 'EffectAsset', 'Layers', 'Material', 'Mesh', 'Node', 'Vec3', 'Vec4']);

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
      DEFAULT_OUTLINE = 0.0045;
      /** 3D 场景构建工具：节点/网格/材质工厂（宠物与房间共用同一套视觉语言） */

      _export("PetBuilderKit", PetBuilderKit = class PetBuilderKit {
        constructor(cc) {
          _defineProperty(this, "cc", void 0);

          _defineProperty(this, "toonEffect", null);

          _defineProperty(this, "fallbackEffect", null);

          this.cc = cc;
        }
        /** pet-toon 是否生效（供调试与降级分支判断） */


        get isToon() {
          return !!this.toonEffect;
        }
        /**
         * 注入已加载的 pet-toon effect。
         *
         * 自定义 effect 必须由宿主先从 resources 加载（`EffectAsset.get` 只能查到**已加载**的资产），
         * 因此场景构建要等它就绪；未注入时材质自动回退内置材质（Fail-Open）。
         */


        setToonEffect(effect) {
          this.toonEffect = effect;
        }
        /**
         * 解析工程内的 pet-toon effect。
         *
         * 注意：cocos-cli 构建产物里的 effect 资产名可能带路径前缀（由导入器按 DB 相对路径命名），
         * 因此不能只依赖 `EffectAsset.get('pet-toon')`；这里追加一次"按名后缀匹配"的回退，
         * 否则材质会静默退化成内置材质（视觉表现与工程内 effect 完全不同）。
         */


        resolveToonEffect() {
          if (this.toonEffect) {
            return this.toonEffect;
          }

          var found = EffectAsset.get('pet-toon') || null;

          if (!found) {
            var _getAll, _ref;

            var registry = (_getAll = (_ref = EffectAsset).getAll) === null || _getAll === void 0 ? void 0 : _getAll.call(_ref);
            var list = registry instanceof Map ? Array.from(registry.values()) : Array.isArray(registry) ? registry : [];
            found = list.find(entry => entry && typeof entry.name === 'string' && entry.name.endsWith('pet-toon')) || null;
          }

          this.toonEffect = found;
          return found;
        }
        /** 该 effect 是否为工程内的 pet-toon（技术索引与属性名都按它来） */


        isToonEffect(effect) {
          return !!effect && typeof effect.name === 'string' && effect.name.endsWith('pet-toon');
        }
        /**
         * 引擎内置材质（Fail-Open 兜底）：未打包/加载失败时材质仍可用，
         * 只是失去 pet-toon 的描边/绒光/光照表现。
         */


        pickFallbackEffect(sample) {
          if (!this.fallbackEffect) {
            try {
              var builtin = sample._getBuiltinMaterial();

              this.fallbackEffect = builtin && builtin.effectAsset ? builtin.effectAsset : null;
            } catch (e) {// 兜底材质尚未就绪：保持 null，下一次 attach 再试
            }
          }

          return this.fallbackEffect;
        }
        /** 3D 节点（DEFAULT 层，挂到父节点） */


        make3dNode(parent, name, pos, transform) {
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


        attach(node, geometry, style) {
          var model = node.addComponent(this.cc.ModelComponent);
          model.mesh = this.cc.utils.createMesh(geometry);
          var transparent = style.blend === true || style.alpha !== undefined && style.alpha < 255; // plain：强制引擎内置材质（标准透明路径）；其余部件优先 pet-toon。
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
              technique
            });
          } catch (error) {
            // 材质初始化失败：保持未设置状态，由引擎给默认材质（Fail-Open，不阻塞场景）
            console.warn("pet-game: \u6750\u8D28\u521D\u59CB\u5316\u5931\u8D25\uFF08".concat(effect.name, " #").concat(technique, "\uFF09"), error);
            return model;
          } // 透明度必须并入主色：technique 选择只决定混合模式，alpha 仍然来自 mainColor.a


          var color = style.alpha === undefined || style.alpha >= 255 ? style.color : new Color(style.color.r, style.color.g, style.color.b, style.alpha);
          var shade = style.glow ? new Color(color.r, color.g, color.b, color.a) : style.shade || (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
            error: Error()
          }), shift) : shift)(color, -0.34);

          try {
            material.setProperty('mainColor', color);

            if (useToon) {
              var _style$rim, _style$top, _style$fur, _style$furSharp, _style$spec, _style$specSharp;

              material.setProperty('shadeColor', shade); // 明暗过渡带：0.52±0.20 —— 上一版 0.54±0.26 太宽，整张脸接近平涂，
              // 收窄后脸颊才有"亮面 → 灰面 → 暖灰紫暗面"的三段体积（仍无硬色阶）。

              material.setProperty('shadeCtrl', new Vec4(0.52, 0.20, (_style$rim = style.rim) !== null && _style$rim !== void 0 ? _style$rim : 0.13, (_style$top = style.top) !== null && _style$top !== void 0 ? _style$top : 0.14)); // 绒光/高光：毛绒物种高绒光、光滑物种高光（见 SpeciesSpec）

              material.setProperty('furCtrl', new Vec4((_style$fur = style.fur) !== null && _style$fur !== void 0 ? _style$fur : 0.14, (_style$furSharp = style.furSharp) !== null && _style$furSharp !== void 0 ? _style$furSharp : 2.0, (_style$spec = style.spec) !== null && _style$spec !== void 0 ? _style$spec : 0.05, (_style$specSharp = style.specSharp) !== null && _style$specSharp !== void 0 ? _style$specSharp : 8)); // 色区柔化（肚皮/口鼻的柔和明度过渡，替代生硬色块）

              if (style.mixCtrl) {
                material.setProperty('mixCtrl', style.mixCtrl);
              }

              if (style.mixColor) {
                material.setProperty('mixColor', style.mixColor);
              } // 柔和贴花（高光/软斑）：径向 alpha 衰减 + 平涂


              if (style.soft || style.flat) {
                material.setProperty('decalCtrl', new Vec4(style.soft ? style.soft[0] : 0, style.soft ? style.soft[1] : 0, style.flat ? 1 : 0, 0));
              } // 面部腮红：烘焙进材质（曲面上的球形径向混色）
              // 注意：Color 的 alpha 是 0-255 整数通道，强度必须换算后再传（否则会被截断为 0）


              if (style.blush) {
                material.setProperty('blushColor', new Color(style.blush.color.r, style.blush.color.g, style.blush.color.b, Math.round(style.blush.strength * 255)));
                material.setProperty('blushCtrlA', style.blush.a);
                material.setProperty('blushCtrlB', style.blush.b);
              }

              var outline = style.outline === undefined ? DEFAULT_OUTLINE : style.outline;
              material.setProperty('outlineCtrl', new Vec4(Math.max(0, outline), 0, 0, 0)); // 描边色随主色压暗（同色系柔边，禁止发黑）

              var edge = (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
                error: Error()
              }), shift) : shift)(color, -0.45);
              material.setProperty('outlineColor', new Color(edge.r, edge.g, edge.b, 255));
            }
          } catch (e) {// 属性缺失（回退 unlit）：仅主色生效，视觉降级但不影响功能
          }

          model.material = material;
          return model;
        }
        /** 通用部件：几何 + 风格 + 变换 */


        part(parent, name, geometry, style, pos, transform) {
          var node = this.make3dNode(parent, name, pos, transform);
          this.attach(node, geometry, style);
          return node;
        }
        /** 手写网格部件（旋转体/扫掠/椭球，见 PetMeshFactory） */


        surf(parent, name, geometry, style, pos, transform) {
          return this.part(parent, name, geometry, style, pos, transform);
        }
        /** 成组零件合并为一个部件：同材质 → 1 个 draw call（四肢/脚趾等成组零件，控制移动端开销） */


        surfGroup(parent, name, geos, style, pos, transform) {
          return this.part(parent, name, geos.length === 1 ? geos[0] : (_crd && merge === void 0 ? (_reportPossibleCrUseOfmerge({
            error: Error()
          }), merge) : merge)(...geos), style, pos, transform);
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


        decal(parent, name, radius, style, pos, rot) {
          var _style$soft;

          var softStyle = _objectSpread(_objectSpread({}, style), {}, {
            outline: 0,
            fur: 0,
            spec: 0,
            flat: true,
            blend: true,
            soft: (_style$soft = style.soft) !== null && _style$soft !== void 0 ? _style$soft : [0.55, 1.0]
          });

          return this.surf(parent, name, (_crd && radialUV === void 0 ? (_reportPossibleCrUseOfradialUV({
            error: Error()
          }), radialUV) : radialUV)((_crd && ellipsoid === void 0 ? (_reportPossibleCrUseOfellipsoid({
            error: Error()
          }), ellipsoid) : ellipsoid)(1, 1, 1, 24, 16)), softStyle, pos, {
            rot,
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


        patch(parent, name, dir, angleU, angleV, radii, style, pos) {
          var inflate = arguments.length > 8 && arguments[8] !== undefined ? arguments[8] : 1.006;
          var geometry = (_crd && spherePatch === void 0 ? (_reportPossibleCrUseOfspherePatch({
            error: Error()
          }), spherePatch) : spherePatch)(dir, angleU, angleV, [radii[0] * inflate, radii[1] * inflate, radii[2] * inflate]);
          return this.surf(parent, name, geometry, _objectSpread(_objectSpread({}, style), {}, {
            outline: 0,
            fur: 0,
            spec: 0
          }), pos);
        }
        /** 球体部件 */


        ball(parent, name, radius, style, pos, transform) {
          return this.part(parent, name, this.cc.primitives.sphere(radius), style, pos, transform);
        }
        /** 方块部件（家具/墙体/地板） */


        boxPart(parent, name, size, style, pos, transform) {
          return this.part(parent, name, this.cc.primitives.box({
            width: size.w,
            height: size.h,
            length: size.d
          }), style, pos, transform);
        }
        /** 圆锥部件（尖耳/装饰） */


        conePart(parent, name, radius, height, style, pos, transform) {
          return this.part(parent, name, this.cc.primitives.cone(radius, height), style, pos, transform);
        }
        /** 胶囊部件（四肢/尾巴/窗棂） */


        capPart(parent, name, radius, height, style, pos, transform) {
          return this.part(parent, name, this.cc.primitives.capsule(radius, radius, height), style, pos, transform);
        }
        /** 圆柱部件（地毯/碗/花盆/坐垫） */


        cylPart(parent, name, topR, bottomR, height, style, pos, transform) {
          return this.part(parent, name, this.cc.primitives.cylinder(topR, bottomR, height), style, pos, transform);
        }
        /** 圆环部件（呼啦圈/挂环/眼镜框） */


        torusPart(parent, name, radius, tube, style, pos, transform) {
          return this.part(parent, name, this.cc.primitives.torus(radius, tube), style, pos, transform);
        }
        /**
         * 更新已构建部件的单个材质属性。
         *
         * 用于需要动态调整的烘焙参数（如腮红强度随情绪变化）。
         */


        setPartProperty(node, name, value) {
          var model = node.components.find(c => c.mesh !== undefined);

          if (!model || !model.material) {
            return;
          }

          try {
            model.material.setProperty(name, value);
          } catch (e) {// 材质不可写：忽略（不阻塞主流程）
          }
        }
        /** 更新已有部件的材质颜色（换肤/情绪变色用；透明部件同步 alpha） */


        recolor(node, color, shade, alpha) {
          var model = node.components.find(c => c.mesh !== undefined);

          if (!model || !model.material) {
            return;
          }

          var next = alpha === undefined ? color : new Color(color.r, color.g, color.b, alpha);

          try {
            model.material.setProperty('mainColor', next);

            if (shade && this.isToon) {
              model.material.setProperty('shadeColor', shade);
            }
          } catch (e) {// 材质不可写：忽略（不阻塞主流程）
          }
        }

      });

      _cclegacy._RF.pop();

      _crd = false;
    }
  };
});
//# sourceMappingURL=eb5fb488245549325fb8ae00f3957f680eae376b.js.map