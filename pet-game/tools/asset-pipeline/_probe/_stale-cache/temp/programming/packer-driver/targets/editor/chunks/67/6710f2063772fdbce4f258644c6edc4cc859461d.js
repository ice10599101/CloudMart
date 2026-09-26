System.register(["cc"], function (_export, _context) {
  "use strict";

  var _cclegacy, __checkObsolete__, __checkObsoleteInNamespace__, Tween, Vec3, tween, PetAnimations, _crd, BLUSH_BASE_SCALE;

  _export("PetAnimations", void 0);

  return {
    setters: [function (_cc) {
      _cclegacy = _cc.cclegacy;
      __checkObsolete__ = _cc.__checkObsolete__;
      __checkObsoleteInNamespace__ = _cc.__checkObsoleteInNamespace__;
      Tween = _cc.Tween;
      Vec3 = _cc.Vec3;
      tween = _cc.tween;
    }],
    execute: function () {
      _crd = true;

      _cclegacy._RF.push({}, "0757a19AKlBWr55kDiSSkwt", "PetAnimations", undefined);

      __checkObsolete__(['Node', 'Tween', 'Vec3', 'tween']);
      /**
       * 宠物动作系统（视觉重构 v3）。
       *
       * 分工：
       *  - 逐帧驱动（update）：呼吸 / 尾巴摆动 / 耳朵姿态与抖动 / 视线 / 眨眼 / 呆毛 / 情绪体态
       *    —— 这些是"它是活的"的底层生命感，必须连续、随机、贴合情绪；
       *  - tween 演出（本文件的 hop/feed/play/...）：一次性交互反馈，
       *    统一遵循「预备 → 动作 → 回弹 → 缓停」四拍，结束必回基准（防多次交互漂移）。
       *
       * 演出与逐帧驱动会争抢同一节点（比如 feed 低头 vs 呼吸抬头），
       * 由 PetGameRoot 的演出锁统一仲裁：演出期间暂停 body/head 的逐帧驱动。
       */


      /** 腮红基准缩放（与 PetModelBuilder 的初始 scale 保持同步） */
      BLUSH_BASE_SCALE = new Vec3(0.95, 0.6, 0.3);

      _export("PetAnimations", PetAnimations = class PetAnimations {
        // ---------------- 逐帧待机（生命感） ----------------

        /** 呼吸：躯干从中心膨胀 + 轻微起伏；头部挂在躯干下，天然联动 */
        static breathe(rig, t, rate = 2.05) {
          const wave = Math.sin(t * rate);
          const squeeze = wave * 0.022;
          rig.body.setScale(1 + squeeze, 1 - squeeze * 1.15, 1 + squeeze);
          rig.body.setPosition(rig.bodyBase.x, rig.bodyBase.y + wave * 0.012, rig.bodyBase.z);
        }
        /** 尾巴摆动：mood 0（低落，慢而小）→ 1（兴奋，快而大） */


        static tailSway(rig, t, mood) {
          const speed = 1.9 + mood * 3.4;
          const amplitude = 9 + mood * 15;
          const base = rig.tailBaseRot;
          rig.tail.setRotationFromEuler(base.x, base.y + Math.sin(t * speed) * amplitude, base.z + Math.sin(t * speed + 0.8) * amplitude * 0.55);
        }
        /** 耳朵姿态：droop 0（竖起好奇）→ 1（耷拉低落），叠加随机小抖动 */


        static earPose(rig, t, droop) {
          const twitchGate = Math.sin(t * 5.7 + 1.3) > 0.94 ? Math.sin(t * 27) * 6 : 0;
          rig.ears.forEach((ear, index) => {
            const base = rig.earBase[index];
            const side = index === 0 ? -1 : 1;
            ear.setRotationFromEuler(base.x + droop * 24 + twitchGate, base.y, base.z + droop * 30 * side + twitchGate * side * 0.6);
          });
        }
        /** 视线：x/y ∈ [-1, 1]（高光随视线移动，制造"看着你/看向食物"） */


        static look(rig, x, y) {
          const base = rig.pupilBase;

          if (rig.pupilDrive === 'orbit') {
            // 高光是贴合眼球的球面片：绕眼心旋转才能始终贴合球面（平移会穿出眼球）
            for (const pupil of rig.pupils) {
              pupil.setRotationFromEuler(base.x - y * 15, base.y + x * 17, base.z);
            }

            return;
          }

          for (const pupil of rig.pupils) {
            pupil.setPosition(base.x + x * 0.032, base.y + y * 0.030, base.z);
          }
        }
        /** 眨眼：openness 0（闭合）→ 1（睁开） */


        static blink(rig, openness) {
          const scaleY = Math.max(0.06, Math.min(1.14, openness * 1.14));

          for (const eye of rig.eyeGroups) {
            eye.setScale(1, scaleY, 1);
          }
        }
        /** 呆毛摆动：随呼吸与随机微风轻摆（生命力细节） */


        static tuftIdle(rig, t) {
          rig.tuft.setRotationFromEuler(Math.sin(t * 1.7) * 4, 0, Math.sin(t * 2.3 + 0.5) * 8);
        } // ---------------- 表情 ----------------

        /** 嘴部变体切换（smile 常驻 / open 说话吃东西 / sad 难过饥饿） */


        static setMouth(rig, kind) {
          rig.mouth.smile.active = kind === 'smile';
          rig.mouth.open.active = kind === 'open';
          rig.mouth.sad.active = kind === 'sad';
        }
        /** 腮红强度：1 常驻，>1 更娇羞（被抚摸/开心时） */


        static setBlush(rig, strength) {
          const s = Math.max(0.5, Math.min(1.45, strength));

          if (rig.blushTint) {
            // 新造型：腮红烘焙在头部材质上，按强度调整混色比例
            rig.blushTint(s);
            return;
          }

          const base = rig.blushBase || BLUSH_BASE_SCALE;

          for (const blush of rig.blush) {
            blush.setScale(base.x * s, base.y * s, base.z * s);
          }
        } // ---------------- 一次性演出 ----------------

        /** 点击弹跳（预备下蹲 → 跃起 → 落地挤压回弹） */


        static hop(rig, onDone) {
          const base = rig.basePos;
          Tween.stopAllByTarget(rig.root);
          tween(rig.root).to(0.07, {
            position: new Vec3(base.x, base.y - 0.055, base.z)
          }, {
            easing: 'sineIn'
          }).to(0.15, {
            position: new Vec3(base.x, base.y + 0.34, base.z)
          }, {
            easing: 'sineOut'
          }).to(0.13, {
            position: new Vec3(base.x, base.y, base.z)
          }, {
            easing: 'quadIn'
          }).call(() => {
            rig.root.setPosition(base);
            onDone && onDone();
          }).start();
          tween(rig.body).to(0.07, {
            scale: new Vec3(1.1, 0.88, 1.1)
          }, {
            easing: 'sineOut'
          }).to(0.22, {
            scale: new Vec3(1, 1, 1)
          }, {
            easing: 'backOut'
          }).start();
        }
        /** 被抚摸：享受地侧头蹭一蹭 + 身体轻压 */


        static petting(rig, onDone) {
          Tween.stopAllByTarget(rig.head);
          tween(rig.head).to(0.18, {
            eulerAngles: new Vec3(9, -17, 4)
          }, {
            easing: 'sineInOut'
          }).to(0.24, {
            eulerAngles: new Vec3(7, 15, -4)
          }, {
            easing: 'sineInOut'
          }).to(0.22, {
            eulerAngles: new Vec3(2, 0, 0)
          }, {
            easing: 'backOut'
          }).call(() => {
            rig.head.setRotationFromEuler(rig.headBaseRot.x, rig.headBaseRot.y, rig.headBaseRot.z);
            onDone && onDone();
          }).start();
          tween(rig.body).to(0.16, {
            scale: new Vec3(1.1, 0.9, 1.1)
          }, {
            easing: 'sineOut'
          }).to(0.3, {
            scale: new Vec3(1, 1, 1)
          }, {
            easing: 'elasticOut'
          }).start();
        }
        /** 进食：三次低头咀嚼 → 满足仰头回弹（配合 PetEffects 的爱心与食物粒子） */


        static feed(rig, onDone) {
          Tween.stopAllByTarget(rig.head);
          tween(rig.head).to(0.2, {
            eulerAngles: new Vec3(24, 0, 0)
          }, {
            easing: 'sineIn'
          }).to(0.1, {
            eulerAngles: new Vec3(18, 0, 0)
          }, {
            easing: 'sineInOut'
          }).to(0.1, {
            eulerAngles: new Vec3(25, 0, 0)
          }, {
            easing: 'sineInOut'
          }).to(0.1, {
            eulerAngles: new Vec3(18, 0, 0)
          }, {
            easing: 'sineInOut'
          }).to(0.1, {
            eulerAngles: new Vec3(25, 0, 0)
          }, {
            easing: 'sineInOut'
          }).to(0.22, {
            eulerAngles: new Vec3(-7, 0, 0)
          }, {
            easing: 'backOut'
          }).to(0.16, {
            eulerAngles: new Vec3(0, 0, 0)
          }, {
            easing: 'sineOut'
          }).call(() => onDone && onDone()).start();
          tween(rig.body).delay(0.3).to(0.12, {
            scale: new Vec3(1.14, 0.86, 1.14)
          }, {
            easing: 'sineOut'
          }).to(0.1, {
            scale: new Vec3(0.97, 1.05, 0.97)
          }, {
            easing: 'sineInOut'
          }).to(0.2, {
            scale: new Vec3(1, 1, 1)
          }, {
            easing: 'backOut'
          }).start();
        }
        /** 玩耍：两连跳 + 转半圈 + 摇头（结束回正，避免朝向漂移） */


        static play(rig, onDone) {
          const base = rig.basePos;
          Tween.stopAllByTarget(rig.root);
          tween(rig.root).to(0.15, {
            position: new Vec3(base.x, base.y + 0.40, base.z),
            eulerAngles: new Vec3(0, 180, 0)
          }, {
            easing: 'sineOut'
          }).to(0.16, {
            position: new Vec3(base.x, base.y, base.z)
          }, {
            easing: 'quadIn'
          }).to(0.13, {
            position: new Vec3(base.x, base.y + 0.26, base.z)
          }, {
            easing: 'sineOut'
          }).to(0.16, {
            position: new Vec3(base.x, base.y, base.z),
            eulerAngles: new Vec3(0, 360, 0)
          }, {
            easing: 'quadIn'
          }).call(() => {
            rig.root.setPosition(base);
            rig.root.setRotationFromEuler(0, 0, 0);
            onDone && onDone();
          }).start();
          Tween.stopAllByTarget(rig.head);
          tween(rig.head).repeat(3, tween().to(0.09, {
            eulerAngles: new Vec3(-7, 13, 0)
          }, {
            easing: 'sineInOut'
          }).to(0.09, {
            eulerAngles: new Vec3(-7, -13, 0)
          }, {
            easing: 'sineInOut'
          })).to(0.12, {
            eulerAngles: new Vec3(0, 0, 0)
          }, {
            easing: 'sineOut'
          }).start();
        }
        /** 清洁：被搓揉的左右摇摆（幅度递减）+ 甩干抖动 */


        static clean(rig, onDone) {
          const base = rig.basePos;
          Tween.stopAllByTarget(rig.root);
          tween(rig.root).to(0.15, {
            position: new Vec3(base.x - 0.06, base.y, base.z),
            eulerAngles: new Vec3(0, 0, 13)
          }, {
            easing: 'sineInOut'
          }).to(0.15, {
            position: new Vec3(base.x + 0.06, base.y, base.z),
            eulerAngles: new Vec3(0, 0, -15)
          }, {
            easing: 'sineInOut'
          }).to(0.13, {
            position: new Vec3(base.x - 0.04, base.y, base.z),
            eulerAngles: new Vec3(0, 0, 10)
          }, {
            easing: 'sineInOut'
          }).to(0.13, {
            position: new Vec3(base.x + 0.03, base.y, base.z),
            eulerAngles: new Vec3(0, 0, -7)
          }, {
            easing: 'sineInOut'
          }).to(0.1, {
            position: base,
            eulerAngles: new Vec3(0, 22, 0)
          }, {
            easing: 'sineOut'
          }).to(0.1, {
            eulerAngles: new Vec3(0, -18, 0)
          }, {
            easing: 'sineInOut'
          }).to(0.14, {
            eulerAngles: new Vec3(0, 0, 0)
          }, {
            easing: 'backOut'
          }).call(() => {
            rig.root.setPosition(base);
            rig.root.setRotationFromEuler(0, 0, 0);
            onDone && onDone();
          }).start();
        }
        /** 入睡：趴下缩成一团（身体压扁 + 头贴地 + 耳朵耷拉）；保持趴姿直到 wakeUp */


        static sleepEnter(rig, onDone) {
          Tween.stopAllByTarget(rig.body);
          Tween.stopAllByTarget(rig.head);
          tween(rig.body).to(0.55, {
            scale: new Vec3(1.3, 0.62, 1.24),
            position: new Vec3(rig.bodyBase.x, rig.bodyBase.y - 0.18, rig.bodyBase.z)
          }, {
            easing: 'sineInOut'
          }).call(() => onDone && onDone()).start();
          tween(rig.head).to(0.55, {
            eulerAngles: new Vec3(26, 8, 8),
            position: new Vec3(rig.headBase.x, rig.headBase.y - 0.14, rig.headBase.z + 0.05)
          }, {
            easing: 'sineInOut'
          }).start();
        }
        /** 醒来：弹回站姿（带过冲回弹） */


        static wakeUp(rig, onDone) {
          Tween.stopAllByTarget(rig.body);
          Tween.stopAllByTarget(rig.head);
          tween(rig.body).to(0.32, {
            scale: new Vec3(0.94, 1.1, 0.94),
            position: rig.bodyBase.clone()
          }, {
            easing: 'sineOut'
          }).to(0.26, {
            scale: new Vec3(1, 1, 1)
          }, {
            easing: 'backOut'
          }).call(() => onDone && onDone()).start();
          tween(rig.head).to(0.24, {
            eulerAngles: new Vec3(-8, 0, 0),
            position: rig.headBase.clone()
          }, {
            easing: 'sineOut'
          }).to(0.3, {
            eulerAngles: new Vec3(0, 0, 0)
          }, {
            easing: 'elasticOut'
          }).start();
        }
        /** 受击/失败：左右抖动 + 头低垂 + 耳朵耷拉一拍 */


        static hurt(rig, onDone) {
          const base = rig.basePos;
          Tween.stopAllByTarget(rig.root);
          tween(rig.root).to(0.06, {
            position: new Vec3(base.x - 0.15, base.y, base.z)
          }, {
            easing: 'sineOut'
          }).to(0.06, {
            position: new Vec3(base.x + 0.26, base.y, base.z)
          }, {
            easing: 'sineInOut'
          }).to(0.06, {
            position: new Vec3(base.x - 0.18, base.y, base.z)
          }, {
            easing: 'sineInOut'
          }).to(0.1, {
            position: base
          }, {
            easing: 'backOut'
          }).call(() => {
            rig.root.setPosition(base);
            onDone && onDone();
          }).start();
          Tween.stopAllByTarget(rig.head);
          tween(rig.head).to(0.1, {
            eulerAngles: new Vec3(14, 0, 0)
          }, {
            easing: 'sineIn'
          }).to(0.32, {
            eulerAngles: new Vec3(0, 0, 0)
          }, {
            easing: 'elasticOut'
          }).start();
        }
        /** 升级：整体放大回弹 + 抬头欢呼（星光粒子由 PetEffects 负责） */


        static levelUp(rig, onDone) {
          Tween.stopAllByTarget(rig.root);
          tween(rig.root).to(0.22, {
            scale: new Vec3(1.26, 1.26, 1.26)
          }, {
            easing: 'backOut'
          }).to(0.28, {
            scale: new Vec3(1, 1, 1)
          }, {
            easing: 'sineIn'
          }).call(() => onDone && onDone()).start();
          Tween.stopAllByTarget(rig.head);
          tween(rig.head).to(0.2, {
            eulerAngles: new Vec3(-12, 0, 0)
          }, {
            easing: 'sineOut'
          }).to(0.32, {
            eulerAngles: new Vec3(0, 0, 0)
          }, {
            easing: 'sineInOut'
          }).start();
        }
        /** 开心欢呼：三连小跳 + 左右摇（奖励/高分心情时的表现） */


        static cheer(rig, onDone) {
          const base = rig.basePos;
          Tween.stopAllByTarget(rig.root);
          tween(rig.root).to(0.11, {
            position: new Vec3(base.x, base.y + 0.20, base.z)
          }, {
            easing: 'sineOut'
          }).to(0.11, {
            position: base.clone()
          }, {
            easing: 'quadIn'
          }).to(0.1, {
            position: new Vec3(base.x, base.y + 0.24, base.z)
          }, {
            easing: 'sineOut'
          }).to(0.11, {
            position: base.clone()
          }, {
            easing: 'quadIn'
          }).to(0.1, {
            position: new Vec3(base.x, base.y + 0.18, base.z)
          }, {
            easing: 'sineOut'
          }).to(0.12, {
            position: base.clone()
          }, {
            easing: 'quadIn'
          }).call(() => {
            rig.root.setPosition(base);
            onDone && onDone();
          }).start();
          Tween.stopAllByTarget(rig.head);
          tween(rig.head).to(0.12, {
            eulerAngles: new Vec3(-6, 16, 6)
          }, {
            easing: 'sineInOut'
          }).to(0.12, {
            eulerAngles: new Vec3(-6, -16, -6)
          }, {
            easing: 'sineInOut'
          }).to(0.12, {
            eulerAngles: new Vec3(-6, 16, 6)
          }, {
            easing: 'sineInOut'
          }).to(0.16, {
            eulerAngles: new Vec3(0, 0, 0)
          }, {
            easing: 'backOut'
          }).start();
        }
        /** 看向某个方向（快速转头 + 视线锁定，喂食前看食物 / 点击时看用户） */


        static glance(rig, yaw, pitch, onDone) {
          Tween.stopAllByTarget(rig.head);
          tween(rig.head).to(0.16, {
            eulerAngles: new Vec3(pitch, yaw, yaw * 0.15)
          }, {
            easing: 'sineOut'
          }).delay(0.42).to(0.24, {
            eulerAngles: new Vec3(0, 0, 0)
          }, {
            easing: 'sineInOut'
          }).call(() => onDone && onDone()).start();
        }
        /** 通用停掉 tween 并复位全部基准（重复交互时避免动画叠加漂移） */


        static resetPose(rig) {
          for (const node of [rig.root, rig.body, rig.head, rig.tail, rig.tuft]) {
            Tween.stopAllByTarget(node);
          }

          rig.root.setPosition(rig.basePos);
          rig.root.setScale(1, 1, 1);
          rig.root.setRotationFromEuler(0, 0, 0);
          rig.body.setPosition(rig.bodyBase);
          rig.body.setScale(1, 1, 1);
          rig.head.setPosition(rig.headBase);
          rig.head.setRotationFromEuler(rig.headBaseRot.x, rig.headBaseRot.y, rig.headBaseRot.z);
          rig.tail.setRotationFromEuler(rig.tailBaseRot.x, rig.tailBaseRot.y, rig.tailBaseRot.z);
        }

      });

      _cclegacy._RF.pop();

      _crd = false;
    }
  };
});
//# sourceMappingURL=6710f2063772fdbce4f258644c6edc4cc859461d.js.map