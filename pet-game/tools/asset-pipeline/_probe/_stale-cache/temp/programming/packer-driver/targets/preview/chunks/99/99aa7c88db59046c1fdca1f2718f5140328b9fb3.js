System.register(["__unresolved_0", "cc", "__unresolved_1", "__unresolved_2", "__unresolved_3"], function (_export, _context) {
  "use strict";

  var _reporterNs, _cclegacy, __checkObsolete__, __checkObsoleteInNamespace__, Color, Vec3, shift, buildCatPet, ellipsoid, lathe, profileFrom, sweep, _crd, SPECS;

  /**
   * 构建宠物（当前只有猫；species 参数保留是为了不破坏宿主侧传参）。
   *
   * 传入其它物种键时一律回落到猫 —— 造型只做一只，先把这一只做到位。
   */
  function buildPet(petParent, groundParent, kit, species, palette, accessoryKey) {
    var spec = SPECS[species] || SPECS.CAT;
    return (_crd && buildCatPet === void 0 ? (_reportPossibleCrUseOfbuildCatPet({
      error: Error()
    }), buildCatPet) : buildCatPet)(petParent, groundParent, kit, palette, accessoryKey, (parent, key, collarY) => buildAccessory(parent, kit, key, palette, spec, collarY));
  }
  /**
   * 配饰：铃铛 / 领结 / 眼镜 / 围巾（挂在 root，不随呼吸缩放）。
   *
   * @param collarY 项圈/围巾所在的颈部高度（不同造型语言的脖子位置不同，由调用方给出）
   */


  function buildAccessory(parent, kit, key, p, spec) {
    var collarY = arguments.length > 5 && arguments[5] !== undefined ? arguments[5] : 0.66;

    if (!key || key === 'none') {
      return null;
    }

    var root = kit.make3dNode(parent, 'Accessory', new Vec3(0, 0, 0));
    var strapColor = (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
      error: Error()
    }), shift) : shift)(p.dark, -0.10);
    var strap = {
      color: strapColor,
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(strapColor, -0.24),
      outline: 0.004,
      fur: spec.fur,
      spec: spec.spec
    };

    switch (key) {
      case 'bell':
        {
          var collarColor = new Color().fromHEX('#D65860');
          kit.surf(root, 'Collar', (_crd && lathe === void 0 ? (_reportPossibleCrUseOflathe({
            error: Error()
          }), lathe) : lathe)((_crd && profileFrom === void 0 ? (_reportPossibleCrUseOfprofileFrom({
            error: Error()
          }), profileFrom) : profileFrom)([[0.30, -0.03], [0.335, -0.01], [0.345, 0.0], [0.335, 0.012], [0.30, 0.028]], 3), 28), {
            color: collarColor,
            shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
              error: Error()
            }), shift) : shift)(collarColor, -0.2),
            outline: 0.006,
            fur: 0.08,
            spec: 0.2
          }, new Vec3(0, collarY, 0.0));
          var bellColor = new Color().fromHEX('#FFC854');
          kit.surf(root, 'Bell', (_crd && ellipsoid === void 0 ? (_reportPossibleCrUseOfellipsoid({
            error: Error()
          }), ellipsoid) : ellipsoid)(0.076, 0.078, 0.072, 20, 14), {
            color: bellColor,
            shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
              error: Error()
            }), shift) : shift)(bellColor, -0.22),
            outline: 0.005,
            fur: 0.06,
            spec: 0.34,
            specSharp: 40
          }, new Vec3(0, collarY - 0.085, 0.285));
          kit.surf(root, 'Clapper', (_crd && ellipsoid === void 0 ? (_reportPossibleCrUseOfellipsoid({
            error: Error()
          }), ellipsoid) : ellipsoid)(0.026, 0.026, 0.026, 14, 10), {
            color: new Color().fromHEX('#96682A'),
            shade: new Color().fromHEX('#7A5220'),
            outline: 0,
            fur: 0,
            spec: 0.2
          }, new Vec3(0, collarY - 0.135, 0.30));
          break;
        }

      case 'bowtie':
        {
          var pink = new Color().fromHEX('#FF84A8');
          var bowStyle = {
            color: pink,
            shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
              error: Error()
            }), shift) : shift)(pink, -0.2),
            outline: 0.005,
            fur: 0.06,
            spec: 0.24
          };
          kit.surf(root, 'Collar', (_crd && lathe === void 0 ? (_reportPossibleCrUseOflathe({
            error: Error()
          }), lathe) : lathe)((_crd && profileFrom === void 0 ? (_reportPossibleCrUseOfprofileFrom({
            error: Error()
          }), profileFrom) : profileFrom)([[0.30, -0.026], [0.335, -0.008], [0.345, 0.0], [0.335, 0.010], [0.30, 0.024]], 3), 28), strap, new Vec3(0, collarY, 0.0));

          for (var side of [-1, 1]) {
            kit.surf(root, 'Bow', (_crd && ellipsoid === void 0 ? (_reportPossibleCrUseOfellipsoid({
              error: Error()
            }), ellipsoid) : ellipsoid)(0.085, 0.062, 0.042, 18, 12), bowStyle, new Vec3(0.105 * side, collarY - 0.045, 0.275), {
              rot: new Vec3(0, 0, 22 * -side)
            });
          }

          kit.surf(root, 'Knot', (_crd && ellipsoid === void 0 ? (_reportPossibleCrUseOfellipsoid({
            error: Error()
          }), ellipsoid) : ellipsoid)(0.042, 0.042, 0.040, 16, 12), bowStyle, new Vec3(0, collarY - 0.045, 0.295));
          break;
        }

      case 'glasses':
        {
          var frameColor = new Color().fromHEX('#60525E');
          var frame = {
            color: frameColor,
            shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
              error: Error()
            }), shift) : shift)(frameColor, -0.2),
            outline: 0.004,
            fur: 0,
            spec: 0.3
          };
          var eyeY = collarY + 0.40;

          for (var _side of [-1, 1]) {
            kit.torusPart(root, 'Lens', 0.135, 0.020, frame, new Vec3(0.205 * _side, eyeY, 0.42));
          }

          kit.capPart(root, 'Bridge', 0.016, 0.10, frame, new Vec3(0, eyeY + 0.02, 0.44), {
            rot: new Vec3(0, 0, 90)
          });
          break;
        }

      case 'scarf':
        {
          var blue = new Color().fromHEX('#7ABEFA');
          var scarf = {
            color: blue,
            shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
              error: Error()
            }), shift) : shift)(blue, -0.2),
            outline: 0.006,
            fur: 0.12,
            spec: 0.14
          };
          kit.surf(root, 'Wrap', (_crd && lathe === void 0 ? (_reportPossibleCrUseOflathe({
            error: Error()
          }), lathe) : lathe)((_crd && profileFrom === void 0 ? (_reportPossibleCrUseOfprofileFrom({
            error: Error()
          }), profileFrom) : profileFrom)([[0.30, -0.075], [0.355, -0.05], [0.375, 0.0], [0.35, 0.05], [0.30, 0.075]], 3), 28), scarf, new Vec3(0, collarY, 0.0));
          kit.surf(root, 'End', (_crd && sweep === void 0 ? (_reportPossibleCrUseOfsweep({
            error: Error()
          }), sweep) : sweep)([{
            p: [0.24, collarY - 0.02, 0.19],
            r: 0.062,
            sx: 0.55,
            sy: 1.0
          }, {
            p: [0.29, collarY - 0.20, 0.23],
            r: 0.058,
            sx: 0.5,
            sy: 1.0
          }, {
            p: [0.30, collarY - 0.34, 0.24],
            r: 0.030,
            sx: 0.55,
            sy: 0.95
          }], 16), scarf, new Vec3(0, 0, 0));
          break;
        }

      default:
        break;
    }

    return root;
  }
  /** 供外部判断某物种是否为"光滑材质"（用于宿主侧的表现微调；当前只有毛绒猫） */


  function isGlossySpecies(species) {
    var spec = SPECS[species];
    return !!spec && spec.spec > 0.15;
  }

  function _reportPossibleCrUseOfshift(extras) {
    _reporterNs.report("shift", "./PetGameTheme", _context.meta, extras);
  }

  function _reportPossibleCrUseOfbuildCatPet(extras) {
    _reporterNs.report("buildCatPet", "./PetCatBuilder", _context.meta, extras);
  }

  function _reportPossibleCrUseOfellipsoid(extras) {
    _reporterNs.report("ellipsoid", "./PetMeshFactory", _context.meta, extras);
  }

  function _reportPossibleCrUseOflathe(extras) {
    _reporterNs.report("lathe", "./PetMeshFactory", _context.meta, extras);
  }

  function _reportPossibleCrUseOfprofileFrom(extras) {
    _reporterNs.report("profileFrom", "./PetMeshFactory", _context.meta, extras);
  }

  function _reportPossibleCrUseOfsweep(extras) {
    _reporterNs.report("sweep", "./PetMeshFactory", _context.meta, extras);
  }

  _export({
    buildPet: buildPet,
    buildAccessory: buildAccessory,
    isGlossySpecies: isGlossySpecies
  });

  return {
    setters: [function (_unresolved_) {
      _reporterNs = _unresolved_;
    }, function (_cc) {
      _cclegacy = _cc.cclegacy;
      __checkObsolete__ = _cc.__checkObsolete__;
      __checkObsoleteInNamespace__ = _cc.__checkObsoleteInNamespace__;
      Color = _cc.Color;
      Vec3 = _cc.Vec3;
    }, function (_unresolved_2) {
      shift = _unresolved_2.shift;
    }, function (_unresolved_3) {
      buildCatPet = _unresolved_3.buildCatPet;
    }, function (_unresolved_4) {
      ellipsoid = _unresolved_4.ellipsoid;
      lathe = _unresolved_4.lathe;
      profileFrom = _unresolved_4.profileFrom;
      sweep = _unresolved_4.sweep;
    }],
    execute: function () {
      _crd = true;

      _cclegacy._RF.push({}, "57f9beNDiFG6Z3r9w7DIjD6", "PetModelBuilder", undefined);

      __checkObsolete__(['Color', 'Node', 'Vec3']);

      /**
       * Q 版宠物 3D 造型（视觉重构 v5）。
       *
       * 现状：**只保留一只猫**。之前 DOG / RABBIT / HAMSTER / TURTLE / PIG / FOX / PANDA / WILD
       * 的造型，以及那套"按 SpeciesSpec 分支拼零件"的通用构建器（buildFace / buildEars /
       * buildTail / buildShell / buildLegs / buildSprawlLegs …）已全部删除 —— 它们都是
       * 球体拼装思路的产物，完成度不够，留着只会拖住造型语言。
       *
       * 猫的造型在 PetCatBuilder.ts：一体连续曲面（lathe 旋转体 + sweep 扫掠管）、
       * 五官贴合面部曲率、色区用着色器柔化。本文件只保留三样东西：
       *  1) PetRig —— 动画层与宿主依赖的骨架契约（不要改动字段语义）；
       *  2) buildPet —— 唯一的入口，直接构建猫；
       *  3) buildAccessory —— 铃铛/领结/眼镜/围巾（换肤与 accessoryKey 能力保留）。
       *
       * 尺寸约定：脚底 y = 0，含耳总高约 1.5（房间/HUD 无需改动）。
       */

      /** 宠物骨架引用（PetAnimations 与 PetGameRoot 通过它驱动所有演出） */

      /** 物种造型规格：目前只剩猫，只保留配饰用得到的两项材质参数 */
      SPECS = {
        CAT: {
          fur: 0.21,
          spec: 0.05
        }
      };

      _cclegacy._RF.pop();

      _crd = false;
    }
  };
});
//# sourceMappingURL=99aa7c88db59046c1fdca1f2718f5140328b9fb3.js.map