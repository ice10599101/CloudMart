System.register(["cc"], function (_export, _context) {
  "use strict";

  var _cclegacy, __checkObsolete__, __checkObsoleteInNamespace__, Node, Sprite, SpriteFrame, UITransform, Vec3, math, resources, tween, PetCatSprite, _crd, CANVAS, CONTENT_H, FOOT_OFFSET, TAIL_PIVOT, TAIL_ANCHOR, BREATH, TAIL_WAG, BLINK, JUMP;

  function _defineProperty(e, r, t) { return (r = _toPropertyKey(r)) in e ? Object.defineProperty(e, r, { value: t, enumerable: !0, configurable: !0, writable: !0 }) : e[r] = t, e; }

  function _toPropertyKey(t) { var i = _toPrimitive(t, "string"); return "symbol" == typeof i ? i : i + ""; }

  function _toPrimitive(t, r) { if ("object" != typeof t || !t) return t; var e = t[Symbol.toPrimitive]; if (void 0 !== e) { var i = e.call(t, r || "default"); if ("object" != typeof i) return i; throw new TypeError("@@toPrimitive must return a primitive value."); } return ("string" === r ? String : Number)(t); }

  _export("PetCatSprite", void 0);

  return {
    setters: [function (_cc) {
      _cclegacy = _cc.cclegacy;
      __checkObsolete__ = _cc.__checkObsolete__;
      __checkObsoleteInNamespace__ = _cc.__checkObsoleteInNamespace__;
      Node = _cc.Node;
      Sprite = _cc.Sprite;
      SpriteFrame = _cc.SpriteFrame;
      UITransform = _cc.UITransform;
      Vec3 = _cc.Vec3;
      math = _cc.math;
      resources = _cc.resources;
      tween = _cc.tween;
    }],
    execute: function () {
      _crd = true;

      _cclegacy._RF.push({}, "f61a19FH79LHpxe4wfXe8op", "PetCatSprite", undefined);

      /**
       * 银渐层猫 · 2D 分层动画实现（严格按 模型/银渐层/pet_spec.json）。
       *
       * 为什么换成贴图分层：程序化 3D 拼装无论怎么调比例，都到不了"厚涂三渲二"的完成度；
       * 这里改成"美术出图 + 引擎做局部动作"——身体是一张整图，只有尾巴和闭眼是独立层。
       *
       * 节点树（与 spec 完全一致，三层共用 1024×1024 同一坐标系，所以各层 position 都是 (0,0)）：
       *   PetVisual（容器：整体缩放/位移/呼吸/跳跃）
       *     ├ body           Sprite  anchor(0.5, 0.5)
       *     ├ tailPivot      空节点  position(-169, -367)（尾根枢轴）
       *     │   └ tail       Sprite  anchor(0.335, 0.142)
       *     └ eyes_closed    Sprite  anchor(0.5, 0.5)  默认 active = false
       *
       * 三条硬约束（踩过的坑，都写在注释里了）：
       *  1) 呼吸/跳跃缩放会带动脚底位移，必须同步补偿 dy = (856 * scale / 2) * (k - 1)，否则脚陷进地面；
       *  2) 眨眼只能切 active，**不能** scaleY 压扁（会把眼睛拉变形）；
       *  3) 三层必须共用同一 scale，任何一层单独缩放/调 position 都会错位。
       */

      /** 画布尺寸与内容高度（pet_spec.json → assembly） */
      __checkObsolete__(['Node', 'Sprite', 'SpriteFrame', 'UITransform', 'Vec3', 'math', 'resources', 'tween']);

      CANVAS = 1024;
      CONTENT_H = 856;
      /** 脚底相对画布中心的像素偏移（向下为正） */

      FOOT_OFFSET = 444;
      /** 尾根枢轴相对画布中心的偏移（spec → layers.tail.pivot_offset_from_center） */

      TAIL_PIVOT = {
        x: -169,
        y: -367
      };
      /** 尾巴精灵的锚点：像素 [343, 879]（左上原点）→ uv */

      TAIL_ANCHOR = {
        x: 0.335,
        y: 0.142
      };
      /** 呼吸参数 */

      BREATH = {
        scaleY: 1.025,
        legSec: 0.8
      };
      /** 摇尾参数 */

      TAIL_WAG = {
        deg: 8,
        legSec: 0.6
      };
      /** 眨眼参数 */

      BLINK = {
        closedSec: 0.09,
        minGap: 2.5,
        maxGap: 5.0
      };
      /** 开心跳跃（dy 是贴图像素，实际位移 = dy * scale） */

      JUMP = {
        squash: {
          scaleX: 1.06,
          scaleY: 0.88,
          sec: 0.12
        },
        launch: {
          scaleX: 0.96,
          scaleY: 1.06,
          dy: 90,
          sec: 0.28
        },
        fall: {
          sec: 0.24
        },
        land: {
          scaleX: 1.05,
          scaleY: 0.92,
          sec: 0.10
        },
        recover: {
          sec: 0.12
        }
      };
      /**
       * 银渐层猫（分层贴图）。
       *
       * 用法：
       *   const cat = new PetCatSprite(rootUiNode, displayHeight)
       *   await cat.load()            // 载入三层贴图并搭好节点树
       *   cat.setGroundY(uiY)         // 把脚底对齐到 UI 坐标的地面线
       *   cat.happyJump()             // 开心跳跃
       *   cat.dispose()               // 重建时销毁
       */

      _export("PetCatSprite", PetCatSprite = class PetCatSprite {
        constructor(parent, displayHeight) {
          /** 容器：负责整体缩放/位移/呼吸/跳跃（节点树下称 PetVisual） */
          _defineProperty(this, "visual", void 0);

          _defineProperty(this, "body", void 0);

          _defineProperty(this, "tailPivot", void 0);

          _defineProperty(this, "tail", void 0);

          _defineProperty(this, "eyesClosed", void 0);

          /** 显示缩放：H / 856 */
          _defineProperty(this, "s", void 0);

          /** 地面 UI 坐标 */
          _defineProperty(this, "groundY", 0);

          _defineProperty(this, "blinkTimer", 0);

          _defineProperty(this, "disposed", false);

          this.s = displayHeight / CONTENT_H;
          this.visual = this.uiNode(parent, 'PetVisual');
          this.body = this.spriteNode(this.visual, 'body', {
            x: 0.5,
            y: 0.5
          });
          this.eyesClosed = this.spriteNode(this.visual, 'eyes_closed', {
            x: 0.5,
            y: 0.5
          });
          this.eyesClosed.active = false;
          this.tailPivot = this.uiNode(this.visual, 'tailPivot');
          this.tailPivot.setPosition(TAIL_PIVOT.x, TAIL_PIVOT.y, 0);
          this.tail = this.spriteNode(this.tailPivot, 'tail', TAIL_ANCHOR);
          this.visual.setScale(this.s, this.s, 1);
          this.syncVisualY();
        }
        /** 载入三层贴图（RGBA8888 / 保持原始比例 / 关闭自动图集 Trim 由工程设置保证） */


        async load() {
          const [body, tail, eyes] = await Promise.all([this.loadFrame('pets/layers/body'), this.loadFrame('pets/layers/tail'), this.loadFrame('pets/layers/eyes_closed')]);

          if (this.disposed) {
            return;
          }

          this.apply(this.body, body);
          this.apply(this.tail, tail);
          this.apply(this.eyesClosed, eyes);
        }
        /** 把脚底对齐到 UI 坐标的地面线：position.y = groundY + 444 * scale */


        setGroundY(uiY) {
          this.groundY = uiY;
          this.syncVisualY();
        }
        /** 显示高度（像素）——按内容高度换算，与 spec 的 scale = H / 856 一致 */


        setDisplayHeight(displayHeight) {
          this.s = displayHeight / CONTENT_H;
          this.visual.setScale(this.s, this.s, 1);
          this.syncVisualY();
        }
        /** 呼吸：scaleY 1.0 ↔ 1.025 往复 + 脚底补偿；无限循环 */


        startBreath() {
          const k = BREATH.scaleY;
          const baseY = this.groundY + FOOT_OFFSET * this.s; // 缩放围绕节点原点（画布中心）进行，脚底会因此位移，必须反向补偿

          const lift = CONTENT_H * this.s / 2 * (k - 1);
          tween(this.visual).repeatForever(tween(this.visual).to(BREATH.legSec, {
            scale: new Vec3(this.s, this.s * k, 1),
            position: new Vec3(0, baseY + lift, 0)
          }, {
            easing: 'sineInOut'
          }).to(BREATH.legSec, {
            scale: new Vec3(this.s, this.s, 1),
            position: new Vec3(0, baseY, 0)
          }, {
            easing: 'sineInOut'
          })).start();
        }
        /** 摇尾：绕尾根枢轴往复摆动，身体不动；无限循环 */


        startTailWag() {
          tween(this.tailPivot).repeatForever(tween(this.tailPivot).to(TAIL_WAG.legSec, {
            angle: TAIL_WAG.deg
          }, {
            easing: 'sineInOut'
          }).to(TAIL_WAG.legSec, {
            angle: -TAIL_WAG.deg
          }, {
            easing: 'sineInOut'
          })).start();
        }
        /** 眨眼：仅切换 active 0.09s，间隔 2.5~5.0s 随机（不做 scaleY 压扁） */


        startBlink() {
          this.blinkTimer = math.randomRange(BLINK.minGap, BLINK.maxGap);
        }
        /** 开心跳跃：下压 → 起跳 → 下落 → 落地 → 回弹 */


        happyJump() {
          const baseY = this.groundY + FOOT_OFFSET * this.s;
          const s = this.s;
          tween(this.visual).to(JUMP.squash.sec, {
            scale: new Vec3(s * JUMP.squash.scaleX, s * JUMP.squash.scaleY, 1)
          }, {
            easing: 'quadOut'
          }).to(JUMP.launch.sec, {
            scale: new Vec3(s * JUMP.launch.scaleX, s * JUMP.launch.scaleY, 1),
            position: new Vec3(0, baseY + JUMP.launch.dy * s, 0)
          }, {
            easing: 'quadOut'
          }).to(JUMP.fall.sec, {
            position: new Vec3(0, baseY, 0)
          }, {
            easing: 'quadIn'
          }).to(JUMP.land.sec, {
            scale: new Vec3(s * JUMP.land.scaleX, s * JUMP.land.scaleY, 1)
          }, {
            easing: 'quadOut'
          }).to(JUMP.recover.sec, {
            scale: new Vec3(s, s, 1)
          }, {
            easing: 'backOut'
          }).start();
        }
        /** 每帧驱动（呼吸/跳跃的补间由 tween 负责，这里只处理眨眼计时） */


        tick(dt) {
          if (this.blinkTimer <= 0) {
            return;
          }

          this.blinkTimer -= dt;

          if (this.blinkTimer <= 0) {
            this.eyesClosed.active = true;
            this.blinkTimer = math.randomRange(BLINK.minGap, BLINK.maxGap);
            tween(this.eyesClosed).delay(BLINK.closedSec).call(() => {
              if (!this.disposed) {
                this.eyesClosed.active = false;
              }
            }).start();
          }
        }

        dispose() {
          this.disposed = true;

          for (const node of [this.visual, this.tailPivot, this.eyesClosed]) {
            tween(node).stop();
          }

          this.visual.destroy();
        } // ---------------- 内部 ----------------


        syncVisualY() {
          this.visual.setPosition(0, this.groundY + FOOT_OFFSET * this.s, 0);
        }

        apply(node, frame) {
          const sprite = node.getComponent(Sprite);

          if (!sprite) {
            return;
          }

          sprite.spriteFrame = frame; // 三层同尺寸同轴：统一设 1024×1024 内容尺寸，各层 position 保持 (0,0) 即严丝合缝

          node.getComponent(UITransform).setContentSize(CANVAS, CANVAS);
        }

        uiNode(parent, name) {
          const node = new Node(name);
          node.layer = parent.layer;
          node.addComponent(UITransform);
          parent.addChild(node);
          return node;
        }

        spriteNode(parent, name, anchor) {
          const node = this.uiNode(parent, name);
          const sprite = node.addComponent(Sprite); // SIMPLE + 关闭 trim：整图按 1024×1024 原样绘制，不做九宫/裁剪

          sprite.sizeMode = Sprite.SizeMode.CUSTOM;
          sprite.trim = false;
          node.getComponent(UITransform).setAnchorPoint(anchor.x, anchor.y);
          node.getComponent(UITransform).setContentSize(CANVAS, CANVAS);
          return node;
        }

        loadFrame(path) {
          return new Promise((resolve, reject) => {
            resources.load(`${path}/spriteFrame`, SpriteFrame, (err, frame) => {
              if (err || !frame) {
                reject(err || new Error(`missing spriteFrame: ${path}`));
                return;
              }

              resolve(frame);
            });
          });
        }

      });

      _cclegacy._RF.pop();

      _crd = false;
    }
  };
});
//# sourceMappingURL=ae3d214d71a47eefea860dfabf930eae3c8ad575.js.map