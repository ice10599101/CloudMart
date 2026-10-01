System.register(["__unresolved_0", "cc", "__unresolved_1"], function (_export, _context) {
  "use strict";

  var _reporterNs, _cclegacy, __checkObsolete__, __checkObsoleteInNamespace__, Color, Vec3, shift, _crd, C;

  /**
   * 快速风格：主色 + 可选暗部/描边覆盖。
   *
   * `plain` 用于需要**标准 alpha 混合**的部件（光点/阳光片/玻璃）：走引擎内置材质
   * （自定义 effect 的 transparent 技术在本构建链下会丢失材质属性）。
   */
  function style(color, opts) {
    var _opts$outline;

    return {
      color,
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
      bulbs,
      sunBeam,
      toyBall
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

    for (var z of [-1.9, -0.4, 1.1, 2.6]) {
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
    }, base, new Vec3(-4.53, 0.18, 0.4)); // 法式线条：顶线（crown）+ 腰线（chair rail），近白奶油勾出墙裙层次

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
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.windowFrame, -0.18)
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
    }, frame, new Vec3(0, -0.93, 0.08)); // 窗帘：帘杆 + 左右帘布 + 帘头

    var curtain = style(C.curtain, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.curtain, -0.16)
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
    }, curtain, new Vec3(0, 1.02, 0.12)); // 窗台小花瓶：一朵玫瑰 + 两片叶

    kit.cylPart(root, 'VaseSmall', 0.09, 0.07, 0.16, style(C.vase, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.vase, -0.1)
    }), new Vec3(0.72, -0.79, 0.12));
    kit.capPart(root, 'StemSmall', 0.016, 0.14, style(C.leafDark, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.leafDark, -0.18)
    }), new Vec3(0.72, -0.66, 0.12));
    kit.ball(root, 'BloomSmall', 0.075, style(C.heart, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.heart, -0.18)
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
    var root = kit.make3dNode(parent, 'WallDecor', new Vec3(0, 0, 0)); // 挂画：黄铜画框（法式金框），画面为奶油底 + 玫瑰心

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
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.picture, -0.12)
    }), new Vec3(0, 0, 0.04)); // 心形：两球 + 一锥（朝下的尖）

    kit.ball(frame, 'HeartL', 0.14, style(C.heart, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.heart, -0.2)
    }), new Vec3(-0.1, 0.08, 0.07));
    kit.ball(frame, 'HeartR', 0.14, style(C.heart, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.heart, -0.2)
    }), new Vec3(0.1, 0.08, 0.07));
    kit.conePart(frame, 'HeartTip', 0.19, 0.26, style(C.heart, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.heart, -0.2)
    }), new Vec3(0, -0.14, 0.07), {
      rot: new Vec3(180, 0, 0)
    }); // 云朵 + 星星（左上角墙面）

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
    }), new Vec3(0.9, 0.34, 0)); // 置物架（右后墙）：两层木板 + 书本 + 小盆栽

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
    }, woodStyle, new Vec3(0.8, 1.9, 0)); // 书本（竖放，彩色书脊）

    var books = [[C.book1, -0.5, 0.16], [C.book2, -0.3, 0.14], [C.book3, -0.11, 0.18]];

    for (var [color, x, h] of books) {
      kit.boxPart(shelf, 'Book', {
        w: 0.13,
        h,
        d: 0.3
      }, style(color, {
        shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
          error: Error()
        }), shift) : shift)(color, -0.22)
      }), new Vec3(x, 2.32 + h / 2, 0.02));
    } // 架上小花瓶：一朵雾蓝小花


    kit.cylPart(shelf, 'VaseShelf', 0.09, 0.07, 0.15, style(C.vase, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.vase, -0.1)
    }), new Vec3(0.45, 1.62 + 0.075, 0));
    kit.capPart(shelf, 'StemShelf', 0.014, 0.12, style(C.leafDark, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.leafDark, -0.18)
    }), new Vec3(0.45, 1.85, 0));
    kit.ball(shelf, 'BloomShelf', 0.065, style(C.book1, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.book1, -0.18)
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
    kit.cylPart(root, 'Edge', 2.42, 2.42, 0.045, style((_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
      error: Error()
    }), shift) : shift)(C.rugOuter, -0.2), {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.rugOuter, -0.32)
    }), new Vec3(0, 0.022, 0));
    kit.cylPart(root, 'Outer', 2.3, 2.3, 0.05, style(C.rugOuter, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.rugOuter, -0.14)
    }), new Vec3(0, 0.03, 0));
    kit.cylPart(root, 'Inner', 1.92, 1.92, 0.055, style(C.rugInner, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.rugInner, -0.14)
    }), new Vec3(0, 0.035, 0));
    kit.cylPart(root, 'Center', 0.95, 0.95, 0.06, style(C.rugCenter, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.rugCenter, -0.14)
    }), new Vec3(0, 0.04, 0));
  }
  /** 宠物窝：外圈软垫 + 内垫 + 靠枕（右侧中景） */


  function buildBed(parent, kit) {
    // 让开中央活动区：宠物窝退到右侧靠后
    var root = kit.make3dNode(parent, 'PetBed', new Vec3(2.95, 0, 1.35));
    kit.cylPart(root, 'Rim', 0.88, 0.98, 0.34, style(C.bedRim, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.bedRim, -0.18)
    }), new Vec3(0, 0.17, 0));
    kit.cylPart(root, 'Cushion', 0.72, 0.72, 0.16, style(C.bedCushion, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.bedCushion, -0.16)
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
    }, style((_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
      error: Error()
    }), shift) : shift)(C.wood, 0.14), {
      shade: C.wood
    }), new Vec3(0, 1.39, 0));
    var drawerLine = style((_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
      error: Error()
    }), shift) : shift)(C.woodDark, -0.16), {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.woodDark, -0.2)
    });

    for (var y of [0.42, 0.92]) {
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
    }), new Vec3(0, 1.14, 0.53)); // 台面餐具：立式碟架（圆盘面朝镜头，玫瑰/雾蓝描边）+ 两只粉彩圆碗 —— 替换旧版尖顶台灯
    // （平摞的薄盘在低机位下只剩一条线，立起来才读得出"盘子"）

    var china = style(C.china, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.china, -0.1)
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

    var standingPlate = (x, rim) => {
      var face = new Vec3(x, 1.645, 0.1);
      kit.cylPart(root, 'Plate', 0.16, 0.16, 0.035, china, face, {
        rot: new Vec3(90, 0, 0)
      });
      kit.cylPart(root, 'PlateRim', 0.17, 0.17, 0.016, style(rim, {
        shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
          error: Error()
        }), shift) : shift)(rim, -0.18)
      }), new Vec3(x, 1.645, 0.115), {
        rot: new Vec3(90, 0, 0)
      });
      kit.cylPart(root, 'PlateHub', 0.055, 0.055, 0.018, style(rim, {
        shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
          error: Error()
        }), shift) : shift)(rim, -0.08)
      }), new Vec3(x, 1.645, 0.128), {
        rot: new Vec3(90, 0, 0)
      });
    };

    standingPlate(-0.44, C.heart);
    standingPlate(-0.12, C.book1); // 圆碗：粉彩球体下陷成碗身 + 奶油碗口内壁（圆润立体，双色）

    var bowl = (x, color) => {
      kit.ball(root, 'BowlBody', 0.15, style(color, {
        shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
          error: Error()
        }), shift) : shift)(color, -0.2)
      }), new Vec3(x, 1.53, 0.12), {
        scale: new Vec3(1, 0.72, 1)
      });
      kit.ball(root, 'BowlInner', 0.15, style(C.china, {
        shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
          error: Error()
        }), shift) : shift)(C.china, -0.06)
      }), new Vec3(x, 1.562, 0.12), {
        scale: new Vec3(0.8, 0.3, 0.8)
      });
    };

    bowl(0.18, C.leaf);
    bowl(0.46, C.book1);
  }
  /** 鲜花花瓶（左后角）：陶瓷花瓶 + 三束鲜花（玫瑰/奶油黄/雾蓝）+ 叶——替换旧版绿盆栽 */


  function buildPlant(parent, kit) {
    var root = kit.make3dNode(parent, 'FlowerVase', new Vec3(-3.7, 0, -2.1)); // 陶瓷花瓶：鼓腹 + 束颈

    kit.cylPart(root, 'VaseBody', 0.34, 0.24, 0.5, style(C.vase, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.vase, -0.12)
    }), new Vec3(0, 0.25, 0));
    kit.cylPart(root, 'VaseNeck', 0.17, 0.21, 0.3, style(C.vase, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.vase, -0.08)
    }), new Vec3(0, 0.6, 0));
    kit.cylPart(root, 'VaseRim', 0.19, 0.19, 0.035, style(C.heart, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.heart, -0.2)
    }), new Vec3(0, 0.765, 0)); // 三束花：茎从瓶口散开，花头 = 花瓣球 + 花芯

    var blooms = [[-0.24, 1.3, 0.08, C.heart], [0.04, 1.44, -0.06, C.book3], [0.27, 1.24, 0.12, C.book1]];

    for (var [x, y, z, color] of blooms) {
      var dx = x;
      var dz = z;
      var dy = y - 0.78;
      var len = Math.sqrt(dx * dx + dy * dy + dz * dz);
      var mid = new Vec3(dx / 2, 0.78 + dy / 2, dz / 2);
      kit.capPart(root, 'Stem', 0.026, len, style(C.leafDark, {
        shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
          error: Error()
        }), shift) : shift)(C.leafDark, -0.18)
      }), mid, {
        rot: new Vec3(dz / len * 80, 0, -dx / len * 80)
      });
      kit.ball(root, 'Petal', 0.11, style(color, {
        shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
          error: Error()
        }), shift) : shift)(color, -0.2)
      }), new Vec3(x, y, z));
      kit.ball(root, 'Core', 0.045, style(C.book3, {
        shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
          error: Error()
        }), shift) : shift)(C.book3, -0.24)
      }), new Vec3(x, y + 0.02, z));
    } // 叶片点缀


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
    var root = kit.make3dNode(parent, 'Toys', new Vec3(0, 0, 0)); // 皮球（带白色条纹感：叠加一圈小球）

    var ball = kit.make3dNode(root, 'ToyBall', new Vec3(-1.35, 0.19, 1.05));
    kit.ball(ball, 'Core', 0.19, style(C.ball, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.ball, -0.24)
    }), new Vec3(0, 0, 0));
    kit.ball(ball, 'Stripe', 0.195, style(new Color(0xFD, 0xF8, 0xF0, 255), {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.ball, -0.34)
    }), new Vec3(0, 0, 0), {
      scale: new Vec3(0.32, 1.02, 1.02)
    }); // 毛线球

    kit.ball(root, 'Yarn', 0.17, style(C.yarn, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.yarn, -0.22)
    }), new Vec3(-1.95, 0.17, 0.5));
    kit.torusPart(root, 'YarnRing', 0.17, 0.02, style((_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
      error: Error()
    }), shift) : shift)(C.yarn, -0.28), {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.yarn, -0.36)
    }), new Vec3(-1.95, 0.17, 0.5), {
      rot: new Vec3(24, 0, 18)
    }); // 小黄鸭

    var duck = kit.make3dNode(root, 'Duck', new Vec3(2.15, 0, 1.75));
    kit.ball(duck, 'Body', 0.19, style(C.duck, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.duck, -0.2)
    }), new Vec3(0, 0.16, 0), {
      scale: new Vec3(1, 0.9, 1.1)
    });
    kit.ball(duck, 'Head', 0.13, style(C.duck, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.duck, -0.2)
    }), new Vec3(0, 0.36, 0.06));
    kit.conePart(duck, 'Beak', 0.055, 0.12, style(C.duckBeak, {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.duckBeak, -0.2)
    }), new Vec3(0, 0.34, 0.2), {
      rot: new Vec3(90, 0, 0)
    }); // 骨头玩具

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
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.cushion, -0.16)
    }), new Vec3(-1.3, 0.16, 2.45), {
      scale: new Vec3(1.25, 0.55, 0.95)
    });
    kit.ball(root, 'CushionSmall', 0.3, style((_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
      error: Error()
    }), shift) : shift)(C.cushion, -0.12), {
      shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
        error: Error()
      }), shift) : shift)(C.cushion, -0.24)
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
        shade: (_crd && shift === void 0 ? (_reportPossibleCrUseOfshift({
          error: Error()
        }), shift) : shift)(C.bulb, -0.08),
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

    var blob = (name, rx, rz, x, y, z, alpha) => {
      kit.decal(root, name, [rx, 0.008, rz], {
        color: C.shadow,
        shade: C.shadow,
        alpha,
        soft: [0.02, 1.0]
      }, new Vec3(x, y, z));
    }; // 地毯：圆心 (0, 0.35)、半径 2.42；Inner 半径 1.92


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

  function _reportPossibleCrUseOfshift(extras) {
    _reporterNs.report("shift", "./PetGameTheme", _context.meta, extras);
  }

  _export("buildRoom", buildRoom);

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
    }],
    execute: function () {
      _crd = true;

      _cclegacy._RF.push({}, "ade82hg2nhNwax6SHFVUJVp", "PetRoomBuilder", undefined);

      __checkObsolete__(['Color', 'Node', 'Vec3']);

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
      C = {
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

      _cclegacy._RF.pop();

      _crd = false;
    }
  };
});
//# sourceMappingURL=63e0be858d632fdb9247a36e0a9839ab56dc1949.js.map