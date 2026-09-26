System.register(["__unresolved_0", "cc", "__unresolved_1", "__unresolved_2"], function (_export, _context) {
  "use strict";

  var _reporterNs, _cclegacy, __checkObsolete__, __checkObsoleteInNamespace__, Color, Vec3, Vec4, shift, ellipsoid, frontZ, lathe, profileFrom, sweep, transformed, _crd, PROFILE_STEPS, HEAD_CTRL, BODY_CTRL, LEG_CTRL, SIZE, HEAD_PROFILE, BODY_PROFILE, MUZZLE, SHADOW_TINT, HEAD_SCALE, BLUSH_STRENGTH, BLUSH_RX, BLUSH_RY, HEAD_MEAN_RADIUS;

  function ownKeys(e, r) { var t = Object.keys(e); if (Object.getOwnPropertySymbols) { var o = Object.getOwnPropertySymbols(e); r && (o = o.filter(function (r) { return Object.getOwnPropertyDescriptor(e, r).enumerable; })), t.push.apply(t, o); } return t; }

  function _objectSpread(e) { for (var r = 1; r < arguments.length; r++) { var t = null != arguments[r] ? arguments[r] : {}; r % 2 ? ownKeys(Object(t), !0).forEach(function (r) { _defineProperty(e, r, t[r]); }) : Object.getOwnPropertyDescriptors ? Object.defineProperties(e, Object.getOwnPropertyDescriptors(t)) : ownKeys(Object(t)).forEach(function (r) { Object.defineProperty(e, r, Object.getOwnPropertyDescriptor(t, r)); }); } return e; }

  function _defineProperty(e, r, t) { return (r = _toPropertyKey(r)) in e ? Object.defineProperty(e, r, { value: t, enumerable: !0, configurable: !0, writable: !0 }) : e[r] = t, e; }

  function _toPropertyKey(t) { var i = _toPrimitive(t, "string"); return "symbol" == typeof i ? i : i + ""; }

  function _toPrimitive(t, r) { if ("object" != typeof t || !t) return t; var e = t[Symbol.toPrimitive]; if (void 0 !== e) { var i = e.call(t, r || "default"); if ("object" != typeof i) return i; throw new TypeError("@@toPrimitive must return a primitive value."); } return ("string" === r ? String : Number)(t); }

  /** 度 → 弧度 */
  function deg(value) {
    return value * Math.PI / 180;
  }
  /**
   * 构建奶油白猫（脚底 y = 0；总高约 1.50）。
   *
   * @param petParent 宠物根节点（整体动画作用于它）
   * @param groundParent 地面层节点（影子挂这里，宠物跳起时影子不跟随）
   * @param kit 构建工具
   * @param palette 调色板（奶油白系）
   * @param accessoryKey 配饰键（none/bell/bowtie/glasses/scarf）
   * @param buildAccessory 配饰构建器（由 PetModelBuilder 注入，避免重复实现）
   */


  function buildCatPet(petParent, groundParent, kit, palette, accessoryKey, buildAccessory) {
    var p = palette; // ---- 材质风格（毛绒物种：高绒光 + 弱而宽的高光；暗部为暖灰紫，不发黑） ----

    var main = {
      color: p.body,
      // 暗部比 p.dark 再深 8%：配合收窄的明暗过渡带，脸颊才有三段体积（仍保持暖灰、不发黑）
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(p.dark, -0.08),
      // 只用于强化外轮廓：屏幕恒定宽度，细而柔（不出现硬黑边）
      outline: 0.0035,
      fur: 0.13,
      furSharp: 2.2,
      // 主体高光必须"弱而宽"：强度 0.028、锐度 14 时才不会在头顶结出一块镜面亮斑
      // （上一版 0.045 / 7 在额头形成明显的"秃斑"高光，读成塑料）
      spec: 0.028,
      specSharp: 14
    };
    var soft = {
      color: p.belly,
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(p.dark, 0.16),
      outline: 0,
      fur: 0.14,
      furSharp: 2.0,
      spec: 0.026,
      specSharp: 14
    };
    var pawStyle = {
      color: p.paw,
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(p.paw, -0.12),
      outline: 0,
      fur: 0.12,
      furSharp: 2.2,
      spec: 0.028,
      specSharp: 14
    }; // ---- 躯干：沿 z 拉长的旋转体（面包形；pivot 在身体中心，呼吸时从中心膨胀） ----
    //      整体后移 0.02：让重心落在四条腿之间，头在躯干前段上方 → 读成"站着"而不是"坐着"。

    var body = kit.make3dNode(petParent, 'Body', new Vec3(0, SIZE.bodyY, -0.02));
    kit.surf(body, 'Torso', (_crd && transformed === void 0 ? (_reportPossibleCrUseOftransformed({
      error: Error()
    }), transformed) : transformed)((_crd && lathe === void 0 ? (_reportPossibleCrUseOflathe({
      error: Error()
    }), lathe) : lathe)(BODY_PROFILE, 34), {
      scale: [1, 1, SIZE.bodyZ]
    }), _objectSpread(_objectSpread({}, main), {}, {
      // 胸腹：下半部 + 朝向观众的柔和明度过渡（不是贴一块色板）
      mixColor: p.belly,
      mixCtrl: new Vec4(0.10, -0.18, 0.78, 0.90)
    }), new Vec3(0, 0, 0)); // 脖颈处理说明：曾在下颌与胸口之间垫一层扁椭球做"胸毛"来遮盖头/身相交线，
    // 实测**更糟** —— 扁椭球自身的轮廓会在胸口形成一道"围脖台阶"（比原来的接缝更显眼）。
    // 目前的方案是让头部下沉得足够深：相交圈落在躯干最宽处附近，
    // 两个曲面近乎相切，只剩一层柔和的下颌阴影，而不是一条折痕。
    // ---- 四肢：四足站姿（挂根节点：呼吸缩放不牵动腿脚，脚底始终贴地） ----

    buildLegs(petParent, kit, main, pawStyle); // ---- 尾巴：从臀部后上方立起并向外回勾（站姿猫的尾巴是"竖起来的第五肢"） ----
    //      约束（逐轮截图量出来的）：整条尾巴 z ≤ -0.20（留在身体后方），
    //      且尾梢向 +x 甩出 0.30 以上 —— 这样 3/4 机位能看到一条完整的尾弧，
    //      又不会像坐着那版一样横过胸口或戳到脸。

    var tail = kit.make3dNode(petParent, 'Tail', new Vec3(-0.100, 0.460, -0.340));
    kit.surf(tail, 'Tail', (_crd && sweep === void 0 ? (_reportPossibleCrUseOfsweep({
      error: Error()
    }), sweep) : sweep)([{
      p: [0, 0, 0],
      r: 0.060,
      sx: 0.98,
      sy: 0.98
    }, {
      p: [-0.030, 0.175, -0.035],
      r: 0.078,
      sx: 1.0,
      sy: 0.96
    }, {
      p: [-0.052, 0.385, -0.020],
      r: 0.064,
      sx: 1.0,
      sy: 0.94
    }, {
      p: [-0.128, 0.575, 0.040],
      r: 0.048,
      sx: 1.0,
      sy: 0.92
    }, {
      p: [-0.238, 0.690, 0.135],
      r: 0.032,
      sx: 1.0,
      sy: 0.9
    }, {
      p: [-0.318, 0.712, 0.222],
      r: 0.020,
      sx: 1.0,
      sy: 0.9
    }], 20), _objectSpread(_objectSpread({}, main), {}, {
      outline: 0
    }), new Vec3(0, 0, 0)); // ---- 头部（挂躯干下：呼吸/压扁时头部天然联动；沿 z 前移到躯干前段上方） ----
    // 腮红直接烘焙进头部材质（曲面上的球形径向混色）：既没有贴片的硬边与浮空感，
    // 也不依赖本构建链上不稳定的透明通道。

    var head = kit.make3dNode(body, 'Head', new Vec3(0, SIZE.headY - SIZE.bodyY, SIZE.headZOffset + 0.02), {
      scale: new Vec3(HEAD_SCALE, HEAD_SCALE, HEAD_SCALE)
    });
    var blushColor = new Color().fromHEX('#F3A7AE'); // 腮红位置换算成头部 uv：u = 方位角 / 2π，v = 归一化高度（与 lathe 写入的 uv 一致）

    var blushSurfaceZ = (_crd && frontZ === void 0 ? (_reportPossibleCrUseOffrontZ({
      error: Error()
    }), frontZ) : frontZ)(HEAD_PROFILE, SIZE.blushX, SIZE.blushY, SIZE.headZ);
    var headYMin = HEAD_CTRL[0][1];
    var headYSpan = HEAD_CTRL[HEAD_CTRL.length - 1][1] - headYMin;
    var blushU = Math.atan2(blushSurfaceZ, SIZE.blushX) / (Math.PI * 2);
    var blushV = (SIZE.blushY - headYMin) / headYSpan;
    var blushRadiusU = BLUSH_RX / (2 * Math.PI * HEAD_MEAN_RADIUS);
    var blushRadiusV = BLUSH_RY / headYSpan;
    var skull = kit.surf(head, 'Skull', (_crd && transformed === void 0 ? (_reportPossibleCrUseOftransformed({
      error: Error()
    }), transformed) : transformed)((_crd && lathe === void 0 ? (_reportPossibleCrUseOflathe({
      error: Error()
    }), lathe) : lathe)(HEAD_PROFILE, 34), {
      scale: [1, 1, SIZE.headZ]
    }), _objectSpread(_objectSpread({}, main), {}, {
      blush: {
        color: blushColor,
        strength: BLUSH_STRENGTH,
        a: new Vec4(blushU, blushV, blushRadiusU, blushRadiusV),
        b: new Vec4(1 - blushU, blushV, blushRadiusU, blushRadiusV)
      }
    }), new Vec3(0, 0, 0)); // ---- 耳朵（圆角三角、耳根埋入头颅；左右各异形成轻微不对称） ----

    var earInfo = buildEars(head, kit, main); // ---- 面部 ----

    var face = buildFace(head, kit, p, soft); // ---- 呆毛（单撮）：短、粗、向前上方弯成一道干净曲线。
    //      上一版半径 0.038→0.010、长度 0.104 → 读成"一根插在头上的细刺"；
    //      现在基部 0.056（≈头宽 12%）、长度 0.11 且明显回勾，像一撮翘毛。

    var tuft = kit.make3dNode(head, 'Tuft', new Vec3(0.020, 0.368, 0.058));
    kit.surf(tuft, 'Strand', (_crd && sweep === void 0 ? (_reportPossibleCrUseOfsweep({
      error: Error()
    }), sweep) : sweep)([{
      p: [0, 0, 0],
      r: 0.056,
      sx: 0.92,
      sy: 1.0
    }, {
      p: [0.014, 0.046, 0.024],
      r: 0.045,
      sx: 0.88,
      sy: 0.96
    }, {
      p: [0.042, 0.072, 0.058],
      r: 0.030,
      sx: 0.86,
      sy: 0.92
    }, {
      p: [0.080, 0.064, 0.084],
      r: 0.017,
      sx: 0.90,
      sy: 0.88
    }], 12), _objectSpread(_objectSpread({}, main), {}, {
      outline: 0
    }), new Vec3(0, 0, 0)); // ---- 配饰（第一轮验收不使用；能力保留） ----

    var accessory = buildAccessory(petParent, accessoryKey, SIZE.collarY); // ---- 地面软影（挂房间层：宠物跳起时影子留在地面，脚底不悬浮） ----
    // 影子必须落在地毯之上（地毯由三层圆盘堆叠，最高面约 y = 0.07），否则会被地毯吞掉。
    //
    // 实现说明：半透明软影依赖 alpha 混合，而自定义 effect 的 transparent 技术在本构建链上
    // 会丢失材质属性（实测渲染成白色亮片）；因此这里改用**三层不透明色阶**做柔和过渡 ——
    // 基色取宠物站位（地毯中心）的地面色，逐层压暗，视觉上仍是柔软的接触阴影。

    var shadowBase = new Color().fromHEX(SHADOW_TINT);
    var shadowCenter = new Vec3(petParent.position.x, 0.082, petParent.position.z - 0.02); // 站姿的投影是**沿身体长轴拉长的椭圆**（不是正圆盘）：与四足支撑面的形状一致

    var shadowLayers = [[0.36, 0.013, 0.46, 0.06], [0.27, 0.012, 0.35, 0.15], [0.18, 0.011, 0.24, 0.26]];
    var shadowNodes = shadowLayers.map(_ref => {
      var [rx, ry, rz, darkening] = _ref;
      var tone = (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(shadowBase, -darkening);
      return kit.surf(groundParent, 'PetShadow', (_crd && ellipsoid === void 0 ? (_reportPossibleCrUseOfellipsoid({
        error: Error()
      }), ellipsoid) : ellipsoid)(rx, ry, rz, 26, 12), {
        color: tone,
        shade: tone,
        outline: 0,
        flat: true,
        fur: 0,
        spec: 0
      }, shadowCenter);
    });
    var shadow = shadowNodes[0];
    return {
      root: petParent,
      body,
      head,
      tail,
      ears: earInfo.nodes,
      earBase: earInfo.bases,
      eyeGroups: face.eyeGroups,
      pupils: face.pupils,
      mouth: face.mouth,
      blush: face.blush,
      tuft,
      shadow,
      accessory,
      pupilBase: face.pupilBase,
      pupilDrive: 'orbit',
      blushBase: new Vec3(1, 1, 1),
      // 腮红强度（情绪加深/变淡）通过头部材质的烘焙参数更新，而不是缩放贴片
      blushTint: strength => {
        var clamped = Math.max(0.35, Math.min(1.5, strength));
        kit.setPartProperty(skull, 'blushColor', new Color(blushColor.r, blushColor.g, blushColor.b, Math.round(BLUSH_STRENGTH * clamped * 255)));
      },
      basePos: petParent.position.clone(),
      bodyBase: body.position.clone(),
      headBase: head.position.clone(),
      tailBase: tail.position.clone(),
      headBaseRot: head.eulerAngles.clone(),
      tailBaseRot: tail.eulerAngles.clone(),
      markers: {
        // 特效/气泡的世界锚点：头顶上方与嘴前（站姿：头在躯干前段上方，z 比坐姿更靠前）
        head: new Vec3(0, 1.30, 0.20),
        mouth: new Vec3(0, 0.77, 0.48)
      }
    };
  }
  /**
   * 四肢：**四足站姿** —— 前腿在躯干前段两侧、后腿在后段两侧。
   *
   * 站姿的关键（之前坐姿版被指"像雪人"的根因之一就是没有四条腿）：
   *  - 四条腿从腹底伸出，脚掌略鼓、脚踝略收，腿根收口埋进躯干（不露空心管口）；
   *  - 小腿略外张（z 前方的前腿向前倾 3.5°、后腿向后倾），让四条腿在剪影上彼此分开；
   *  - 每条腿前端三个圆趾，缩到 160px 时仍能看出"脚掌朝向观众"。
   */


  function buildLegs(root, kit, main, pawStyle) {
    var legGeo = (_crd && lathe === void 0 ? (_reportPossibleCrUseOflathe({
      error: Error()
    }), lathe) : lathe)((_crd && profileFrom === void 0 ? (_reportPossibleCrUseOfprofileFrom({
      error: Error()
    }), profileFrom) : profileFrom)(LEG_CTRL, 3), 20); // 内部/附属部件不描边：外扩壳会穿出主体曲面，形成"帽檐"一样的硬线

    var limbStyle = _objectSpread(_objectSpread({}, main), {}, {
      outline: 0
    });

    var legGeos = [];
    var toeGeos = [];
    var legs = [[SIZE.frontLegX, SIZE.frontLegZ, 1], [-SIZE.frontLegX, SIZE.frontLegZ, -1], [SIZE.backLegX, SIZE.backLegZ, 1], [-SIZE.backLegX, SIZE.backLegZ, -1]];

    for (var [x, z, side] of legs) {
      // 前腿向前、后腿向后各错开一点 → 侧面看是"前后叉开"的站姿，不会四条腿并成一排
      var spread = z > 0 ? 0.030 : -0.034;
      var px = x + side * 0.016;
      var pz = z + spread;
      legGeos.push((_crd && transformed === void 0 ? (_reportPossibleCrUseOftransformed({
        error: Error()
      }), transformed) : transformed)(legGeo, {
        pos: [px, 0, pz],
        rot: [0, 0, deg(-3.5 * side)]
      }));

      for (var i = -1; i <= 1; i++) {
        toeGeos.push((_crd && transformed === void 0 ? (_reportPossibleCrUseOftransformed({
          error: Error()
        }), transformed) : transformed)((_crd && ellipsoid === void 0 ? (_reportPossibleCrUseOfellipsoid({
          error: Error()
        }), ellipsoid) : ellipsoid)(0.030, 0.024, 0.028, 12, 9), {
          pos: [px + i * 0.042, 0.028, pz + 0.072]
        }));
      }
    }

    kit.surfGroup(root, 'Legs', legGeos, limbStyle, new Vec3(0, 0, 0));
    kit.surfGroup(root, 'Toes', toeGeos, pawStyle, new Vec3(0, 0, 0));
  }
  /**
   * 耳朵：宽底圆角三角的**厚实立体耳片**（不是薄片），内耳用"朝向色区"表达。
   *
   * 上一版的两个错误：
   *  1) 耳片太薄（截面 sx=0.54~0.60 且路径短），露出部分只有头高的 ~25%，读成"两个小尖"；
   *  2) 内耳是另一条前偏的扫掠管，正面会鼓出耳片表面，形成一道像"折痕"的台阶。
   *
   * 现在：外耳是一条完整的锥形扫掠（底宽 0.43 ≈ 头宽 47%、露出高度 ≈ 头高 50%），
   * 内耳由 pet-toon 的 mixCtrl.z（按物体空间法线的朝向混合）生成 —— 长在耳片正面，
   * 既不额外占几何，也不会鼓出。
   */


  function buildEars(head, kit, main) {
    // 内耳：比主体略深、略暖（偏粉棕而不是偏灰）；由下向上消退，只有朝向观众的一面显色。
    // 混色区间必须落在"耳朵露出头颅之上"的高度（耳局部 y ≈ 0.13 才开始可见），
    // 区间取 (0.44, 0.26)：从可见处一直到 55% 高度都是内耳，靠近尖端退回主体色。
    var earStyle = _objectSpread(_objectSpread({}, main), {}, {
      // 附属部件不描边：耳根埋进头颅后，外扩描边壳会穿出头面、在头顶留下一道硬线
      outline: 0,
      mixColor: new Color().fromHEX('#D8B0A4'),
      mixCtrl: new Vec4(0.44, 0.26, 1.0, 0.95)
    });

    var nodes = [];
    var bases = [];

    for (var side of [-1, 1]) {
      // 轻微不对称：左耳额外外倾 4°、前倾 2°（避免"复制粘贴"的呆板）
      var asymZ = side === -1 ? 4 : 0;
      var asymY = side === -1 ? 2 : 0; // 耳根埋在头颅上侧内部（保证无接缝）；外倾 20° 让耳朵从正面看来是"立起来的三角"

      var root = kit.make3dNode(head, 'Ear', new Vec3(SIZE.earX * side, SIZE.earY, 0.012), {
        rot: new Vec3(6 + asymY, 0, -(20 + asymZ) * side)
      }); // 截面语义：sx = 厚度（前后）、sy = 宽度（左右）。
      // 保留 sx/sy ≈ 0.7 → 耳朵有实体厚度；尖端半径 0.03 → 圆润收口，不做圆锥武器。

      kit.surf(root, 'Outer', (_crd && sweep === void 0 ? (_reportPossibleCrUseOfsweep({
        error: Error()
      }), sweep) : sweep)([{
        p: [0, -0.070, 0.004],
        r: 0.216,
        sx: 0.72,
        sy: 1.0
      }, {
        p: [0.013, 0.076, -0.002],
        r: 0.198,
        sx: 0.70,
        sy: 1.0
      }, {
        p: [0.033, 0.206, -0.013],
        r: 0.136,
        sx: 0.66,
        sy: 0.95
      }, {
        p: [0.059, 0.312, -0.024],
        r: 0.072,
        sx: 0.64,
        sy: 0.90
      }, {
        p: [0.081, 0.378, -0.033],
        r: 0.030,
        sx: 0.66,
        sy: 0.88
      }], 20), earStyle, new Vec3(0, 0, 0));
      nodes.push(root);
      bases.push(root.eulerAngles.clone());
    }

    return {
      nodes,
      bases
    };
  }
  /** 眼睛 / 口鼻 / 嘴 / 腮红：位置全部由头部曲面求出（贴合曲率，不悬浮） */


  function buildFace(head, kit, p, soft) {
    var eyeColor = new Color().fromHEX('#352A38');
    var eyeStyle = {
      color: eyeColor,
      // 暗部提亮（深棕紫 → 略微偏暖紫）：避免在弱光下变成"两颗纯黑豆"
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(eyeColor, 0.18),
      outline: 0,
      fur: 0,
      spec: 0.22,
      specSharp: 22
    };
    var glintStyle = {
      color: new Color(255, 253, 250, 255),
      shade: new Color(255, 253, 250, 255),
      outline: 0,
      flat: true,
      blend: true,
      fur: 0,
      spec: 0,
      // 中心纯白、边缘化开：侧视时高光片的轮廓线自然消失（不会出现"浮在脸外的细线"）
      soft: [0.42, 1.0]
    };
    var eyeRadii = [SIZE.eyeRX, SIZE.eyeRY, SIZE.eyeRZ];
    var eyeZ = (_crd && frontZ === void 0 ? (_reportPossibleCrUseOffrontZ({
      error: Error()
    }), frontZ) : frontZ)(HEAD_PROFILE, SIZE.eyeX, SIZE.eyeY, SIZE.headZ) - SIZE.eyeEmbed;
    var eyeGroups = [];
    var pupils = [];

    for (var side of [-1, 1]) {
      var group = kit.make3dNode(head, 'Eye', new Vec3(SIZE.eyeX * side, SIZE.eyeY, eyeZ), {
        rot: new Vec3(0, 8 * side, 0)
      });
      kit.surf(group, 'Ball', (_crd && ellipsoid === void 0 ? (_reportPossibleCrUseOfellipsoid({
        error: Error()
      }), ellipsoid) : ellipsoid)(SIZE.eyeRX, SIZE.eyeRY, SIZE.eyeRZ, 28, 20), eyeStyle, new Vec3(0, 0, 0)); // 高光：与眼球同心的球面片（完全贴合曲率）+ 径向 alpha 渐变（边缘化开）。
      // 这样既不凸出成"交通灯"，侧视也不会露出片状轮廓。
      // 面积控制：主高光 ≈ 2.8% 眼面积、辅高光 ≈ 0.8%，合计远低于 12% 上限。

      var pupil = kit.make3dNode(group, 'Glint', new Vec3(0, 0, 0));
      kit.patch(pupil, 'GlintMain', [0.34 * side, 0.30, 0.89], 0.215, 0.190, eyeRadii, glintStyle, new Vec3(0, 0, 0), 1.004);
      kit.patch(pupil, 'GlintMinor', [-0.44 * side, -0.48, 0.76], 0.098, 0.090, eyeRadii, glintStyle, new Vec3(0, 0, 0), 1.004);
      eyeGroups.push(group);
      pupils.push(pupil);
    } // ---- 口鼻：轻微鼓起（不是熊嘴/猴嘴；颜色只比主体略亮，靠形体而非色块表达） ----


    var muzzleZ = (_crd && frontZ === void 0 ? (_reportPossibleCrUseOffrontZ({
      error: Error()
    }), frontZ) : frontZ)(HEAD_PROFILE, 0, SIZE.muzzleY, SIZE.headZ) - 0.022;
    kit.surf(head, 'Muzzle', (_crd && ellipsoid === void 0 ? (_reportPossibleCrUseOfellipsoid({
      error: Error()
    }), ellipsoid) : ellipsoid)(MUZZLE[0], MUZZLE[1], MUZZLE[2], 24, 16), _objectSpread(_objectSpread({}, soft), {}, {
      color: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(p.body, 0.16),
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(p.dark, 0.22)
    }), new Vec3(0, SIZE.muzzleY, muzzleZ)); // ---- 鼻子：小而柔和，低饱和粉棕，贴在口鼻上沿 ----

    var noseColor = new Color().fromHEX('#C98288');
    var noseZ = (_crd && frontZ === void 0 ? (_reportPossibleCrUseOffrontZ({
      error: Error()
    }), frontZ) : frontZ)(HEAD_PROFILE, 0, SIZE.noseY, SIZE.headZ) - 0.008; // 鼻子：0.043 → 0.036（宽 0.072 ≈ 头宽 9%）。上一版 0.086 宽 + 高饱和粉在正面读成"鼠鼻"，
    // 猫的鼻子应该更小、更扁、更低饱和。

    kit.surf(head, 'Nose', (_crd && ellipsoid === void 0 ? (_reportPossibleCrUseOfellipsoid({
      error: Error()
    }), ellipsoid) : ellipsoid)(0.036, 0.027, 0.023, 20, 14), {
      color: noseColor,
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(noseColor, -0.10),
      outline: 0,
      fur: 0.08,
      spec: 0.16,
      specSharp: 20
    }, new Vec3(0, SIZE.noseY, noseZ)); // ---- 嘴：一条浅弧（默认轻轻微笑），贴在口鼻表面 ----

    var mouthColor = (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
      error: Error()
    }), shift) : shift)(p.dark, -0.52);
    var lineStyle = {
      color: mouthColor,
      shade: mouthColor,
      outline: 0,
      glow: true,
      fur: 0,
      spec: 0
    };
    /** 口鼻凸起的前表面（椭球），保证嘴线贴着曲面而不是陷进去 */

    var mouthSurfaceZ = (x, y) => {
      var dx = x / MUZZLE[0];
      var dy = (y - SIZE.muzzleY) / MUZZLE[1];
      var inner = Math.max(0, 1 - dx * dx - dy * dy);
      return muzzleZ + MUZZLE[2] * Math.sqrt(inner);
    };
    /** 嘴线路径：宽度 width、下垂 lift（负值 = 微笑） */


    var arcNodes = (halfWidth, lift, radius, yBase) => {
      var nodes = [];

      for (var i = 0; i <= 8; i++) {
        var t = i / 8;
        var a = (t - 0.5) * 2;
        var x = a * halfWidth;
        var y = yBase - lift * (1 - a * a);
        nodes.push({
          p: [x, y, mouthSurfaceZ(x, y) + 0.002],
          r: radius
        });
      }

      return nodes;
    };

    var smile = kit.make3dNode(head, 'Smile', new Vec3(0, 0, 0));
    kit.surf(smile, 'Arc', (_crd && sweep === void 0 ? (_reportPossibleCrUseOfsweep({
      error: Error()
    }), sweep) : sweep)(arcNodes(0.044, 0.009, 0.0095, SIZE.mouthY), 10), lineStyle, new Vec3(0, 0, 0)); // 人中：鼻底到嘴弧中心的一小段竖线，与嘴弧合成猫的"倒 Y"（只一条弧线读起来像随机曲线）

    kit.surf(smile, 'Philtrum', (_crd && sweep === void 0 ? (_reportPossibleCrUseOfsweep({
      error: Error()
    }), sweep) : sweep)([{
      p: [0, SIZE.mouthY - 0.010, mouthSurfaceZ(0, SIZE.mouthY - 0.010) + 0.002],
      r: 0.0070
    }, {
      p: [0, SIZE.mouthY + 0.020, mouthSurfaceZ(0, SIZE.mouthY + 0.020) + 0.002],
      r: 0.0066
    }, {
      p: [0, SIZE.noseY - 0.020, mouthSurfaceZ(0, SIZE.noseY - 0.020) + 0.002],
      r: 0.0058
    }], 8), lineStyle, new Vec3(0, 0, 0)); // 张嘴：小口腔 + 舌头（进食/说话用）

    var open = kit.make3dNode(head, 'Open', new Vec3(0, SIZE.mouthY + 0.004, mouthSurfaceZ(0, SIZE.mouthY) - 0.004));
    open.active = false;
    var cavity = new Color().fromHEX('#8C5A62');
    kit.surf(open, 'Cavity', (_crd && ellipsoid === void 0 ? (_reportPossibleCrUseOfellipsoid({
      error: Error()
    }), ellipsoid) : ellipsoid)(0.040, 0.032, 0.024, 18, 12), {
      color: cavity,
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(cavity, -0.18),
      outline: 0,
      fur: 0,
      spec: 0.08
    }, new Vec3(0, 0, 0));
    kit.surf(open, 'Tongue', (_crd && ellipsoid === void 0 ? (_reportPossibleCrUseOfellipsoid({
      error: Error()
    }), ellipsoid) : ellipsoid)(0.026, 0.018, 0.016, 16, 11), {
      color: new Color().fromHEX('#E8909C'),
      shade: new Color().fromHEX('#D07884'),
      outline: 0,
      fur: 0.05,
      spec: 0.12
    }, new Vec3(0, -0.016, 0.014)); // 难过：下垂弧

    var sad = kit.make3dNode(head, 'Sad', new Vec3(0, 0, 0));
    sad.active = false;
    kit.surf(sad, 'Arc', (_crd && sweep === void 0 ? (_reportPossibleCrUseOfsweep({
      error: Error()
    }), sweep) : sweep)(arcNodes(0.036, -0.011, 0.007, SIZE.mouthY), 10), lineStyle, new Vec3(0, 0, 0)); // ---- 腮红：实际显色由头部材质的球形径向混色完成（见 buildCatPet）；
    //      这里保留左右锚点节点，用于动画层调整腮红强度（PetAnimations.setBlush）。

    var blush = [kit.make3dNode(head, 'BlushL', new Vec3(-SIZE.blushX, SIZE.blushY, 0)), kit.make3dNode(head, 'BlushR', new Vec3(SIZE.blushX, SIZE.blushY, 0))];
    return {
      eyeGroups,
      pupils,
      mouth: {
        smile,
        open,
        sad
      },
      blush,
      pupilBase: new Vec3(0, 0, 0)
    };
  }

  function _reportPossibleCrUseOfshift(extras) {
    _reporterNs.report("shift", "./PetGameTheme", _context.meta, extras);
  }

  function _reportPossibleCrUseOfellipsoid(extras) {
    _reporterNs.report("ellipsoid", "./PetMeshFactory", _context.meta, extras);
  }

  function _reportPossibleCrUseOffrontZ(extras) {
    _reporterNs.report("frontZ", "./PetMeshFactory", _context.meta, extras);
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

  function _reportPossibleCrUseOftransformed(extras) {
    _reporterNs.report("transformed", "./PetMeshFactory", _context.meta, extras);
  }

  _export("buildCatPet", buildCatPet);

  return {
    setters: [function (_unresolved_) {
      _reporterNs = _unresolved_;
    }, function (_cc) {
      _cclegacy = _cc.cclegacy;
      __checkObsolete__ = _cc.__checkObsolete__;
      __checkObsoleteInNamespace__ = _cc.__checkObsoleteInNamespace__;
      Color = _cc.Color;
      Vec3 = _cc.Vec3;
      Vec4 = _cc.Vec4;
    }, function (_unresolved_2) {
      shift = _unresolved_2.shift;
    }, function (_unresolved_3) {
      ellipsoid = _unresolved_3.ellipsoid;
      frontZ = _unresolved_3.frontZ;
      lathe = _unresolved_3.lathe;
      profileFrom = _unresolved_3.profileFrom;
      sweep = _unresolved_3.sweep;
      transformed = _unresolved_3.transformed;
    }],
    execute: function () {
      _crd = true;

      _cclegacy._RF.push({}, "3e1a5TDjdBIU5SFryoYdms6", "PetCatBuilder", undefined);

      __checkObsolete__(['Color', 'Node', 'Vec3', 'Vec4']);

      /**
       * 奶油白猫造型（视觉重构 v5）—— 治愈系 Q 版手办的"轮廓优先"实现。
       *
       * 造型原则（对应评审要求）：
       *  - **一体连续曲面**：头/躯干/四肢都是旋转体（lathe），耳朵/尾巴/呆毛是带圆润封头的扫掠管（sweep），
       *    不存在"球体堆砌"的接缝；
       *  - **面部贴合曲率**：眼、鼻、嘴、腮红的位置全部由头部旋转体的前表面函数 frontZ() 求出，
       *    五官长在脸上而不是悬浮在脸前；
       *  - **柔和边界**：肚皮用曲面渐变（mixColor）而不是色块；腮红是贴合脸颊的球面软斑（径向 alpha 衰减）；
       *  - **比例**：头（不含耳）视觉面积约占整体 60%，头略宽（宽高比 1.13）且下颌柔和收窄；
       *  - **可读性**：脚底 y = 0、总高约 1.50，缩到 160px 高时眼睛约 20px、前爪约 23px 仍可辨认。
       *
       * 关键尺寸：
       *  - 头：宽 0.86 / 高 0.76 / 深 0.81，中心 y = 1.05；
       *  - 躯干：宽 0.84 / 高 0.735，中心 y = 0.40，底部贴地；
       *  - 眼：0.196 × 0.212，中心距 0.37（内缘间距 ≈ 0.89 眼宽），位于脸中偏下；
       *  - 耳：高 0.225（约为头高 30%），左右倾角差 5°（轻微不对称）；
       *  - 尾：向右前上方卷起，尾尖明显超出躯干轮廓（正面 3/4 可见）。
       */

      /** 旋转体轮廓的插值密度（越大越圆滑；4 已足够，兼顾顶点数） */
      PROFILE_STEPS = 4;
      /**
       * 头部侧轮廓（相对头中心；半径, y）：**略宽扁**的圆润形，下颌柔和收窄。
       *
       * 宽 0.92 / 高 0.74（宽高比 1.24）—— 正圆头会读成"球"，略宽 + 下颌收窄才像猫。
       * 长度单位与躯干共用（总高约 1.55，含耳）。
       */

      HEAD_CTRL = [[0.004, -0.350], [0.152, -0.330], [0.272, -0.288], [0.372, -0.213], [0.434, -0.118], [0.460, -0.008], [0.457, 0.094], [0.430, 0.188], [0.370, 0.272], [0.238, 0.346], [0.004, 0.392]];
      /**
       * 躯干侧轮廓（相对躯干中心）：**四足站姿的"面包形"**。
       *
       * 关键：这是绕 Y 轴的旋转体（宽 = 高），必须在生成时沿 z 拉长到 1.5 倍，
       * 才得到"宽 0.55 × 高 0.47 × 长 0.82"的小猫身体 —— 站姿的身体是**横向长条**，
       * 不是一个立在地上的圆坨（圆坨 + 圆头 = 雪人，这是前两轮被指出的根本问题）。
       */

      BODY_CTRL = [[0.004, -0.268], [0.128, -0.257], [0.216, -0.214], [0.288, -0.132], [0.322, -0.028], [0.330, 0.066], [0.313, 0.148], [0.268, 0.212], [0.178, 0.254], [0.004, 0.272]];
      /**
       * 腿的侧轮廓（相对腿根；下圆上收、顶端收口埋进躯干）。
       *
       * 半径 0.088 → 0.058：脚掌略鼓、脚踝略收，避免四条腿变成四根等粗圆柱。
       * 顶端必须收口（半径 → 0）：否则从侧面能看到腿根的空心管口。
       */

      LEG_CTRL = [[0.004, -0.010], [0.066, 0.008], [0.100, 0.046], [0.115, 0.086], [0.104, 0.140], [0.090, 0.210], [0.080, 0.300], [0.075, 0.390], [0.004, 0.450]];
      /** 造型尺寸表（集中定义，便于整体校比例；脚底 y = 0，含耳总高约 1.50） */

      SIZE = {
        /** 头中心：头整体缩放 0.85（见 HEAD_SCALE）→ 下颌落在 0.63，只与躯干顶（0.762）重叠 0.13 */
        headY: 0.93,
        headZ: 0.96,

        /** 头中心沿 z 前移：头挂在躯干前段上方，形成"四足站姿"的前后关系 */
        headZOffset: 0.20,

        /** 躯干中心：腹底（body-local -0.27）落在 0.18 → 腿露出 0.18（粗短腿，不是桌腿） */
        bodyY: 0.448,

        /** 躯干沿 z 拉长倍数：0.66（宽）× 1.36 ≈ 0.90（长） */
        bodyZ: 1.364,

        /** 眼睛中心 X：内缘间距 = 2×(0.175-0.086) = 0.178 ≈ 1.03 眼宽（目标 0.8~1.0） */
        eyeX: 0.175,
        eyeY: -0.052,
        eyeRX: 0.086,
        eyeRY: 0.095,
        eyeRZ: 0.064,

        /** 眼球嵌入深度（球心相对头面前表面的内侧距离：越深越像"长在脸上"的嵌眼） */
        eyeEmbed: 0.056,
        muzzleY: -0.172,
        noseY: -0.158,
        mouthY: -0.222,
        blushX: 0.246,
        blushY: -0.122,

        /** 四条腿：前腿在躯干前段两侧、后腿在后段两侧；前后跨距 0.56，站姿稳定不倒 */
        frontLegX: 0.155,
        frontLegZ: 0.270,
        backLegX: 0.165,
        backLegZ: -0.290,

        /** 耳根：埋在头颅上侧（head-local），耳片从曲面伸出 */
        earX: 0.235,
        earY: 0.205,
        collarY: 0.62
      };
      HEAD_PROFILE = (_crd && profileFrom === void 0 ? (_reportPossibleCrUseOfprofileFrom({
        error: Error()
      }), profileFrom) : profileFrom)(HEAD_CTRL, PROFILE_STEPS);
      BODY_PROFILE = (_crd && profileFrom === void 0 ? (_reportPossibleCrUseOfprofileFrom({
        error: Error()
      }), profileFrom) : profileFrom)(BODY_CTRL, PROFILE_STEPS);
      /** 口鼻凸起的三轴半径（嘴线贴面计算也用它，避免两处硬编码不一致） */

      MUZZLE = [0.105, 0.078, 0.042];
      /**
       * 接触阴影基色：柔和的暖灰粉（接近地面明度但**去饱和**）。
       *
       * 不能用地毯的饱和粉色（#EFB6B0）：在纯色背景的验收模式下，饱和色会变成一块"粉盘子"，
       * 看上去像贴纸而不是影子；去饱和后无论在粉地毯还是纯色背景上都读成柔和的接触阴影。
       */

      SHADOW_TINT = '#C3B2B6';
      /**
       * 头部整体缩放：头（含耳、五官、呆毛）统一缩放，头局部的 authored 比例保持不变。
       *
       * 为什么缩放头而不是放大身体：四足站姿下"身体大 + 头大"会让整体高度失控。
       * 0.85 使头宽 0.92→0.78、身高 0.74→0.63，与躯干（宽 0.66 / 高 0.54 / 长 0.90）
       * 形成 1.18 倍的宽度差 —— 头略大于身，不再是大头娃娃。
       *
       * 安全性：PetAnimations 只对 head 做 rotation / position 补间，resetPose 只重置 body 的 scale，
       * 因此这里的 scale 不会被动画覆盖。
       */

      HEAD_SCALE = 0.85;
      /** 腮红：混色强度与作用范围（世界空间半径，构建时换算成头部 uv 单位） */

      BLUSH_STRENGTH = 0.62;
      BLUSH_RX = 0.115;
      BLUSH_RY = 0.086;
      /** 头部平均半径（把世界横向半径换算到"方位 u"单位：u 全周对应 2πR） */

      HEAD_MEAN_RADIUS = 0.44;

      _cclegacy._RF.pop();

      _crd = false;
    }
  };
});
//# sourceMappingURL=ef0e5ee77f92a00248541c215d8ef04497d0a26c.js.map