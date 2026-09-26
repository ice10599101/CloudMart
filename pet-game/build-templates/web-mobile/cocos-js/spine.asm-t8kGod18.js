// spine 伪装分块（构建模板覆盖产物；同名真实文件是引擎的 asm.js，实例化有缺陷）。
//
// 为什么覆盖它：本工程不使用 spine，但 vendored 引擎（4.0.0-alpha.34）的基础
// 初始化链无条件加载并实例化 spine asm.js，且该链被 cc.game.init await
// （game.ts: onPostInfrastructureInitDelegate.dispatch()）。真实 asm 实例化抛
// embind/BindingError → init 中断 → 渲染管线 program 全部缺失 → 黑屏。
//
// 本文件把 asm 分块替换为"惰性空实现"：
//   - default 是工厂，resolve 一个 Proxy 哑库（不执行任何 emscripten 代码）；
//   - 哑库 ownKeys 提供全部 spine 类名，nHt 的 `for in` 把它们拷进引擎命名空间，
//     随后的原型嫁接（tHt/rHt…）拿到的是"任意方法皆 no-op"的哑类，链路完整走通；
//   - get 陷阱对 then/catch/finally 返回 undefined：Promise.resolve 以 .then 探测
//     thenable，返回函数会让链路无声卡死（已实测）。
// 本工程无任何 spine 资产，哑库永远不会被业务代码触碰。
// 注意：重构建后此文件由 build-templates 机制自动覆盖回产物，无需手工处理。
System.register([], function (e) {
  "use strict";
  return {
    execute: function () {
      function anyMethod() {
        return { size: function () { return 0 }, get: function () { return null } };
      }
      function makeClass() {
        function DummyClass() {}
        DummyClass.prototype = new Proxy({}, { get: function () { return anyMethod } });
        return DummyClass;
      }
      function makeVector() {
        function SPVectorFloat() {}
        SPVectorFloat.prototype.size = function () { return 0 };
        SPVectorFloat.prototype.get = function () { return null };
        SPVectorFloat.prototype.set = function () {};
        SPVectorFloat.prototype.resize = function () {};
        return SPVectorFloat;
      }
      var CLASS_NAMES = [
      'SpineWasmUtil', 'SPVectorFloat',
      'Animation', 'AnimationState', 'AnimationStateData', 'AtlasAttachmentLoader',
      'Attachment', 'AttachmentTimeline', 'Bone', 'BoneData',
      'BoundingBoxAttachment', 'ClipAttachment', 'ColorTimeline', 'Constraint',
      'CurveTimeline', 'DeformTimeline', 'DrawOrderTimeline', 'Event',
      'EventTimeline', 'IkConstraint', 'IkConstraintData', 'MeshAttachment',
      'PathAttachment', 'PathConstraint', 'PathConstraintData',
      'PhysicsConstraint', 'PhysicsConstraintData', 'PointAttachment',
      'RegionAttachment', 'RotateTimeline', 'Skeleton', 'SkeletonBinary',
      'SkeletonData', 'SkeletonJson', 'Skin', 'SkinEntry', 'Slot', 'SlotData',
      'TextureAtlas', 'Timeline', 'TrackEntry', 'TransformConstraint',
      'TransformConstraintData', 'VertexAttachment',
      ];
      var lib = new Proxy({}, {
        get: function (target, prop) {
          if (prop === 'then' || prop === 'catch' || prop === 'finally') {
            return undefined;
          }
          if (prop === 'SpineWasmUtil') {
            return { spineWasmInit: function () {}, wasm: null };
          }
          if (prop === 'SPVectorFloat') {
            return makeVector();
          }
          return makeClass();
        },
        ownKeys: function () {
          return CLASS_NAMES.slice();
        },
        getOwnPropertyDescriptor: function () {
          return { enumerable: true, configurable: true };
        },
      });
      e('default', function () {
        return Promise.resolve(lib);
      });
    },
  };
});
