System.register(["cc"], function (_export, _context) {
  "use strict";

  var _cclegacy, __checkObsolete__, __checkObsoleteInNamespace__, Color, Graphics, Label, UIOpacity, Vec3, tween, PetEffects, _crd;

  function _defineProperty(e, r, t) { return (r = _toPropertyKey(r)) in e ? Object.defineProperty(e, r, { value: t, enumerable: !0, configurable: !0, writable: !0 }) : e[r] = t, e; }

  function _toPropertyKey(t) { var i = _toPrimitive(t, "string"); return "symbol" == typeof i ? i : i + ""; }

  function _toPrimitive(t, r) { if ("object" != typeof t || !t) return t; var e = t[Symbol.toPrimitive]; if (void 0 !== e) { var i = e.call(t, r || "default"); if ("object" != typeof i) return i; throw new TypeError("@@toPrimitive must return a primitive value."); } return ("string" === r ? String : Number)(t); }

  _export("PetEffects", void 0);

  return {
    setters: [function (_cc) {
      _cclegacy = _cc.cclegacy;
      __checkObsolete__ = _cc.__checkObsolete__;
      __checkObsoleteInNamespace__ = _cc.__checkObsoleteInNamespace__;
      Color = _cc.Color;
      Graphics = _cc.Graphics;
      Label = _cc.Label;
      UIOpacity = _cc.UIOpacity;
      Vec3 = _cc.Vec3;
      tween = _cc.tween;
    }],
    execute: function () {
      _crd = true;

      _cclegacy._RF.push({}, "92b70HK6j1Aer6obP8bq7lQ", "PetEffects", undefined);

      /**
       * 2D 特效层（视觉重构 v3）：爱心 / 星星 / 泡泡 / 睡意 / 食物 / 数值浮字 / 光圈。
       *
       * 设计原则：
       *  - 所有反馈都"从宠物身上长出来"：调用方传入 3D 世界坐标，本层换算为 UI 坐标，
       *    保证粒子与宠物在屏幕上严格对齐（不同窗口尺寸下依然贴合）；
       *  - 弹出现（backOut）→ 飘移（正弦侧移）→ 渐隐销毁，三段式，避免机械直线运动；
       *  - 全部 Graphics 程序化绘制（零贴图资产），形状即品牌：圆角爱心 + 五角星 + 泡泡。
       */
      __checkObsolete__(['Color', 'Graphics', 'Label', 'Node', 'UIOpacity', 'Vec3', 'tween']);
      /** 世界坐标 → UI 坐标换算（由 PetGameRoot 注入，内部用 3D 相机投影） */


      _export("PetEffects", PetEffects = class PetEffects {
        constructor(makeNode, toUi) {
          _defineProperty(this, "makeNode", void 0);

          _defineProperty(this, "toUi", void 0);

          this.makeNode = makeNode;
          this.toUi = toUi;
        }
        /** 爱心群：被抚摸 / 开心 / 进食（从宠物头顶冒出，带随机侧移） */


        hearts(world) {
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


        stars(world) {
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
            }).call(() => node.destroy()).start();
            tween(opacity).delay(0.5).to(0.5, {
              opacity: 0
            }).start();
          };

          for (var index = 0; index < count; index += 1) {
            _loop();
          }
        }
        /** 泡泡群：清洁（从宠物周身向上飘散，带螺旋侧移） */


        bubbles(world) {
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
            }).call(() => node.destroy()).start();
            tween(opacity).delay(0.4).to(0.7, {
              opacity: 0
            }).start();
          };

          for (var index = 0; index < count; index += 1) {
            _loop2();
          }
        }
        /** 睡意 Zzz：睡觉时循环冒出（由调用方按间隔重复调用） */


        sleepZ(world) {
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
          }).call(() => node.destroy()).start();
          tween(opacity).delay(0.5).to(0.6, {
            opacity: 0
          }).start();
        }
        /** 食物：从画面下方飞到宠物嘴边（喂食演出） */


        food(world, icon, onArrive) {
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
          }).call(() => onArrive && onArrive()).delay(0.5).to(0.24, {
            position: new Vec3(target.x, target.y - 20, 0),
            scale: new Vec3(0.2, 0.2, 1)
          }, {
            easing: 'sineIn'
          }).call(() => node.destroy()).start();
          tween(opacity).delay(1.0).to(0.24, {
            opacity: 0
          }).start();
        }
        /** 数值/结果浮字：上飘渐隐（带轻微侧摆，避免机械直线） */


        floatText(world, text) {
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
          }).call(() => node.destroy()).start();
          tween(opacity).delay(0.5).to(0.45, {
            opacity: 0
          }).start();
        }
        /** 光圈扩散：点击/升级的冲击波（一圈放大淡出的圆环） */


        ring(world) {
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
          }).call(() => node.destroy()).start();
          tween(opacity).to(0.42, {
            opacity: 0
          }).start();
        }
        /** 小闪点：任意交互的通用点缀（随机短距离外爆） */


        sparkle(world) {
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
            }).call(() => node.destroy()).start();
            tween(opacity).to(0.5, {
              opacity: 0
            }).start();
          };

          for (var index = 0; index < count; index += 1) {
            _loop3();
          }
        } // ---------------- 形状绘制 ----------------

        /** 心形（两段贝塞尔，圆润饱满） */


        drawHeart(g, size, color) {
          var s = size / 30;
          g.fillColor = color;
          g.moveTo(0, 6 * s);
          g.bezierCurveTo(8 * s, 16 * s, 22 * s, 8 * s, 0, -14 * s);
          g.bezierCurveTo(-22 * s, 8 * s, -8 * s, 16 * s, 0, 6 * s);
          g.fill();
        }
        /** 五角星 */


        drawStar(g, radius, color) {
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


        drawBubble(g, radius) {
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


        popAndRise(node, delay, rise, drift) {
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
          }).call(() => node.destroy()).start();
          tween(opacity).delay(delay + 0.5).to(0.5, {
            opacity: 0
          }).start();
        }

      });

      _cclegacy._RF.pop();

      _crd = false;
    }
  };
});
//# sourceMappingURL=3c095426926d49c34dd0c2b23215e251620660cf.js.map