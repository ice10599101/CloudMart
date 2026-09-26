System.register(["cc"], function (_export, _context) {
  "use strict";

  var _cclegacy, __checkObsolete__, __checkObsoleteInNamespace__, Color, _crd, SPECIES_BODY, SPECIES_DARK, SPECIES_BELLY, SPECIES_PAW, SPECIES_IRIS, SPECIES_ACCESSORY, IRIS_HIGHLIGHT, ACCENT_GOLD, ACCESSORY_COLOR, SKIN_BODY;

  // 交互演出态

  /** 十六进制 → Color */
  function hex(value) {
    return new Color().fromHEX(value);
  }
  /** 颜色向白/黑插值（amount > 0 提亮，< 0 压暗） */


  function shift(color, amount) {
    const target = amount >= 0 ? 255 : 0;
    const k = Math.abs(amount);
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
    const baseHex = colorKey && SKIN_BODY[colorKey] || SPECIES_BODY[species] || SPECIES_BODY.CAT;
    const body = hex(baseHex);
    const speciesTint = !colorKey;
    return {
      body,
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

  _export({
    shift: shift,
    resolvePalette: resolvePalette
  });

  return {
    setters: [function (_cc) {
      _cclegacy = _cc.cclegacy;
      __checkObsolete__ = _cc.__checkObsolete__;
      __checkObsoleteInNamespace__ = _cc.__checkObsoleteInNamespace__;
      Color = _cc.Color;
    }],
    execute: function () {
      _crd = true;

      _cclegacy._RF.push({}, "f65e0Qg92pF3JYE7gQ3SquJ", "PetGameTheme", undefined);

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
      __checkObsolete__(['Color']);

      /** 物种基色（唯一色源：其余部位由基色推导） */
      SPECIES_BODY = {
        CAT: '#F3EEE7' // 奶油白（唯一物种：其他物种造型已删除）

      };
      /** 深色部位覆盖（耳内/花纹；缺省由基色推导） */

      SPECIES_DARK = {
        CAT: '#CFC5BE' // 暖灰阴影（暗部保持暖调、不发黑）

      };
      /** 肚皮/口鼻覆盖（缺省 = 基色提亮 42%） */

      SPECIES_BELLY = {
        CAT: '#FFF8F0' // 口鼻与胸腹浅色

      };
      /** 爪垫覆盖（缺省 = 基色提亮 52%） */

      SPECIES_PAW = {
        CAT: '#EADBD4' // 爪垫：比主体略暗的暖灰粉（不抢眼）

      };
      /** 虹膜覆盖（缺省 = 琥珀金；取自奶灰猫实测贴图） */

      SPECIES_IRIS = {
        CAT: '#C98A3E'
      };
      /** 配饰覆盖（物种默认配饰色） */

      SPECIES_ACCESSORY = {
        CAT: '#C4A0A2' // 干枯玫瑰藕粉（针织围巾）

      };
      /**
       * 眼球高光：**固定中性色，不随物种/皮肤变化**。
       *
       * 历史教训：曾对整只眼球做定向染色，把高光 `#F4F2F1` 一起染成 `#F1E2AA`，
       * 眼内最高明度从 249 掉到 212 → 虹膜金 + 眼白黄 + 高光黄糊成一片，整只眼读作一颗金球。
       * 高光是眼内唯一的中性色，也是"湿润、有神"的全部来源，必须锁死。
       */

      IRIS_HIGHLIGHT = '#F4F3F2';
      /** 强调金色（饰扣、徽章等点睛细节；低饱和的金，避免"塑料亮片"感） */

      _export("ACCENT_GOLD", ACCENT_GOLD = '#E7B85D');
      /** 配饰键（PetDisplayState.accessory）→ 颜色 */


      ACCESSORY_COLOR = {
        none: '#C4A0A2',
        bell: '#E7C87F',
        // 浅金铃铛
        scarf: '#C4A0A2',
        // 干枯玫瑰藕粉围巾
        bowtie: '#B98A88',
        glasses: '#8B7968'
      };
      /** 皮肤色键（pet_skin_config.color）→ 基色（保留宿主自定义换色能力） */

      SKIN_BODY = {
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

      _cclegacy._RF.pop();

      _crd = false;
    }
  };
});
//# sourceMappingURL=f1741e26415a712422c4ee7f04b810ef30a51a08.js.map