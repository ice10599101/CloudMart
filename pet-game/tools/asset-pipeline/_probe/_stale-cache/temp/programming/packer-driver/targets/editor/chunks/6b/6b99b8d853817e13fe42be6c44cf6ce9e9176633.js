System.register(["cc"], function (_export, _context) {
  "use strict";

  var _cclegacy, __checkObsolete__, __checkObsoleteInNamespace__, PetGameBridge, _crd;

  function _defineProperty(e, r, t) { return (r = _toPropertyKey(r)) in e ? Object.defineProperty(e, r, { value: t, enumerable: !0, configurable: !0, writable: !0 }) : e[r] = t, e; }

  function _toPropertyKey(t) { var i = _toPrimitive(t, "string"); return "symbol" == typeof i ? i : i + ""; }

  function _toPrimitive(t, r) { if ("object" != typeof t || !t) return t; var e = t[Symbol.toPrimitive]; if (void 0 !== e) { var i = e.call(t, r || "default"); if ("object" != typeof i) return i; throw new TypeError("@@toPrimitive must return a primitive value."); } return ("string" === r ? String : Number)(t); }

  _export("PetGameBridge", void 0);

  return {
    setters: [function (_cc) {
      _cclegacy = _cc.cclegacy;
      __checkObsolete__ = _cc.__checkObsolete__;
      __checkObsoleteInNamespace__ = _cc.__checkObsoleteInNamespace__;
    }],
    execute: function () {
      _crd = true;

      _cclegacy._RF.push({}, "743bdZ6W2FG+6h8KlSCZnG/", "PetGameBridge", undefined);

      /**
       * PetGameBridge：Cocos 场景与三端宿主（Web iframe / App WebView / 小程序 web-view）
       * 之间的唯一通信契约（实施文档 §2.2）。
       *
       * 职责边界：
       *  - 游戏 → 宿主只发"意图"（intent），绝不携带业务数值、绝不直接调 API；
       *  - 宿主 → 游戏只发"状态/结果"（服务端权威数据），游戏只做展示与动画。
       */

      /** 宿主下发的宠物展示状态（服务端 PetVO 直接映射，客户端零计算） */
      __checkObsolete__(['_decorator']);
      /** 服务端战斗引擎回合流水（客户端只播放，可跳过） */

      /** 游戏可发起的意图（宿主据此调用 mall-pet API） */


      _export("PetGameBridge", PetGameBridge = class PetGameBridge {
        constructor() {
          _defineProperty(this, "hostHandler", null);

          _defineProperty(this, "onMessage", event => {
            this.dispatch(event.data);
          });
        }

        /** 宿主环境：iframe(web) / ReactNativeWebView(app) / wx.miniProgram(weapp) */
        detectHost() {
          const w = window;

          if (w.wx && w.wx.miniProgram) {
            return 'weapp';
          }

          if (w.ReactNativeWebView) {
            return 'app';
          }

          return 'web';
        }
        /** 接收宿主消息（iframe postMessage + RN injectJavaScript 双通道） */


        bind(hostHandler) {
          this.hostHandler = hostHandler;
          window.addEventListener('message', this.onMessage); // App 宿主通过 injectJavaScript 调用该入口（Web 端 iframe 场景同样可用）

          window.__petHostMessage = raw => {
            if (typeof raw === 'string') {
              try {
                this.dispatch(JSON.parse(raw));
              } catch (e) {
                // 非法 JSON 直接忽略（宿主脚本注入异常不崩溃场景）
                console.warn('[PetGameBridge] invalid host message ignored');
              }
            } else {
              this.dispatch(raw);
            }
          };
        }
        /** 卸载（组件销毁时调用，避免事件监听泄漏） */


        dispose() {
          window.removeEventListener('message', this.onMessage);
          this.hostHandler = null;
          delete window.__petHostMessage;
        }

        dispatch(data) {
          const msg = data;

          if (!msg || msg.source !== 'pet-host' || !this.hostHandler) {
            return;
          }

          this.hostHandler(msg);
        }
        /** 发送消息到宿主（按环境选通道；weapp 的实时通道是 navigateTo 语义，见 README） */


        send(message) {
          const host = this.detectHost();
          const w = window;

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
          } // Web iframe（同源，'*' 仅为本工程静态资源；如需收紧可改为宿主 origin）


          window.parent.postMessage(message, '*');
        }

      });

      _cclegacy._RF.pop();

      _crd = false;
    }
  };
});
//# sourceMappingURL=6b99b8d853817e13fe42be6c44cf6ce9e9176633.js.map