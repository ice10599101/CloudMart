System.register(["__unresolved_0", "cc", "cc/env", "__unresolved_1"], function (_export, _context) {
  "use strict";

  var _reporterNs, _cclegacy, __checkObsolete__, __checkObsoleteInNamespace__, _decorator, Camera, CCBoolean, CCFloat, CCInteger, Component, Material, rendering, Texture2D, EDITOR, BloomType, fillRequiredPipelineSettings, makePipelineSettings, _dec, _dec2, _dec3, _dec4, _dec5, _dec6, _dec7, _dec8, _dec9, _dec0, _dec1, _dec10, _dec11, _dec12, _dec13, _dec14, _dec15, _dec16, _dec17, _dec18, _dec19, _dec20, _dec21, _dec22, _dec23, _dec24, _dec25, _dec26, _dec27, _class, _class2, _descriptor, _descriptor2, _crd, ccclass, disallowMultiple, executeInEditMode, menu, property, requireComponent, type, BuiltinPipelineSettings;

  function _initializerDefineProperty(e, i, r, l) { r && Object.defineProperty(e, i, { enumerable: r.enumerable, configurable: r.configurable, writable: r.writable, value: r.initializer ? r.initializer.call(l) : void 0 }); }

  function _defineProperty(e, r, t) { return (r = _toPropertyKey(r)) in e ? Object.defineProperty(e, r, { value: t, enumerable: !0, configurable: !0, writable: !0 }) : e[r] = t, e; }

  function _toPropertyKey(t) { var i = _toPrimitive(t, "string"); return "symbol" == typeof i ? i : i + ""; }

  function _toPrimitive(t, r) { if ("object" != typeof t || !t) return t; var e = t[Symbol.toPrimitive]; if (void 0 !== e) { var i = e.call(t, r || "default"); if ("object" != typeof i) return i; throw new TypeError("@@toPrimitive must return a primitive value."); } return ("string" === r ? String : Number)(t); }

  function _applyDecoratedDescriptor(i, e, r, n, l) { var a = {}; return Object.keys(n).forEach(function (i) { a[i] = n[i]; }), a.enumerable = !!a.enumerable, a.configurable = !!a.configurable, ("value" in a || a.initializer) && (a.writable = !0), a = r.slice().reverse().reduce(function (r, n) { return n(i, e, r) || r; }, a), l && void 0 !== a.initializer && (a.value = a.initializer ? a.initializer.call(l) : void 0, a.initializer = void 0), void 0 === a.initializer ? (Object.defineProperty(i, e, a), null) : a; }

  function _initializerWarningHelper(r, e) { throw Error("Decorating class property failed. Please ensure that transform-class-properties is enabled and runs after the decorators transform."); }

  function _reportPossibleCrUseOfBloomType(extras) {
    _reporterNs.report("BloomType", "./builtin-pipeline-types", _context.meta, extras);
  }

  function _reportPossibleCrUseOffillRequiredPipelineSettings(extras) {
    _reporterNs.report("fillRequiredPipelineSettings", "./builtin-pipeline-types", _context.meta, extras);
  }

  function _reportPossibleCrUseOfmakePipelineSettings(extras) {
    _reporterNs.report("makePipelineSettings", "./builtin-pipeline-types", _context.meta, extras);
  }

  function _reportPossibleCrUseOfPipelineSettings(extras) {
    _reporterNs.report("PipelineSettings", "./builtin-pipeline-types", _context.meta, extras);
  }

  return {
    setters: [function (_unresolved_) {
      _reporterNs = _unresolved_;
    }, function (_cc) {
      _cclegacy = _cc.cclegacy;
      __checkObsolete__ = _cc.__checkObsolete__;
      __checkObsoleteInNamespace__ = _cc.__checkObsoleteInNamespace__;
      _decorator = _cc._decorator;
      Camera = _cc.Camera;
      CCBoolean = _cc.CCBoolean;
      CCFloat = _cc.CCFloat;
      CCInteger = _cc.CCInteger;
      Component = _cc.Component;
      Material = _cc.Material;
      rendering = _cc.rendering;
      Texture2D = _cc.Texture2D;
    }, function (_ccEnv) {
      EDITOR = _ccEnv.EDITOR;
    }, function (_unresolved_2) {
      BloomType = _unresolved_2.BloomType;
      fillRequiredPipelineSettings = _unresolved_2.fillRequiredPipelineSettings;
      makePipelineSettings = _unresolved_2.makePipelineSettings;
    }],
    execute: function () {
      _crd = true;

      _cclegacy._RF.push({}, "de1c2EHcMhAIYRZY5nyTQHG", "builtin-pipeline-settings", undefined);
      /*
       Copyright (c) 2021-2024 Xiamen Yaji Software Co., Ltd.
      
       https://www.cocos.com/
      
       Permission is hereby granted, free of charge, to any person obtaining a copy
       of this software and associated documentation files (the "Software"), to deal
       in the Software without restriction, including without limitation the rights to
       use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
       of the Software, and to permit persons to whom the Software is furnished to do so,
       subject to the following conditions:
      
       The above copyright notice and this permission notice shall be included in
       all copies or substantial portions of the Software.
      
       THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
       IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
       FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
       AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
       LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
       OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
       THE SOFTWARE.
      */


      __checkObsolete__(['_decorator', 'Camera', 'CCBoolean', 'CCFloat', 'CCInteger', 'Component', 'Material', 'rendering', 'Texture2D']);

      ({
        ccclass,
        disallowMultiple,
        executeInEditMode,
        menu,
        property,
        requireComponent,
        type
      } = _decorator);

      _export("BuiltinPipelineSettings", BuiltinPipelineSettings = (_dec = ccclass('BuiltinPipelineSettings'), _dec2 = menu('Rendering/BuiltinPipelineSettings'), _dec3 = requireComponent(Camera), _dec4 = property(CCBoolean), _dec5 = property({
        displayName: 'Editor Preview (Experimental)',
        type: CCBoolean
      }), _dec6 = property({
        group: {
          id: 'MSAA',
          name: 'Multisample Anti-Aliasing'
        },
        type: CCBoolean
      }), _dec7 = property({
        group: {
          id: 'MSAA',
          name: 'Multisample Anti-Aliasing',
          style: 'section'
        },
        type: CCInteger,
        range: [2, 4, 2]
      }), _dec8 = property({
        group: {
          id: 'ShadingScale',
          name: 'ShadingScale',
          style: 'section'
        },
        type: CCBoolean
      }), _dec9 = property({
        tooltip: 'i18n:postprocess.shadingScale',
        group: {
          id: 'ShadingScale',
          name: 'ShadingScale'
        },
        type: CCFloat,
        range: [0.01, 4, 0.01],
        slide: true
      }), _dec0 = property({
        group: {
          id: 'Bloom',
          name: 'Bloom (PostProcessing)',
          style: 'section'
        },
        type: CCBoolean
      }), _dec1 = type(_crd && BloomType === void 0 ? (_reportPossibleCrUseOfBloomType({
        error: Error()
      }), BloomType) : BloomType), _dec10 = property({
        group: {
          id: 'Bloom',
          name: 'Bloom (PostProcessing)',
          style: 'section'
        }
      }), _dec11 = property({
        group: {
          id: 'Bloom',
          name: 'Bloom (PostProcessing)',
          style: 'section'
        },
        type: Material
      }), _dec12 = property({
        group: {
          id: 'Bloom',
          name: 'Bloom (PostProcessing)',
          style: 'section'
        },
        type: Material
      }), _dec13 = property({
        tooltip: 'i18n:bloom.enableAlphaMask',
        group: {
          id: 'Bloom',
          name: 'Bloom (PostProcessing)',
          style: 'section'
        },
        type: CCBoolean
      }), _dec14 = property({
        tooltip: 'i18n:bloom.iterations',
        group: {
          id: 'Bloom',
          name: 'Bloom (PostProcessing)',
          style: 'section'
        },
        type: CCInteger,
        range: [1, 6, 1],
        slide: true
      }), _dec15 = property({
        tooltip: 'i18n:bloom.threshold',
        group: {
          id: 'Bloom',
          name: 'Bloom (PostProcessing)',
          style: 'section'
        },
        type: CCFloat,
        min: 0
      }), _dec16 = type(CCFloat), _dec17 = property({
        group: {
          id: 'Bloom',
          name: 'Bloom (PostProcessing)',
          style: 'section'
        }
      }), _dec18 = property({
        group: {
          id: 'Color Grading',
          name: 'ColorGrading (LDR) (PostProcessing)',
          style: 'section'
        },
        type: CCBoolean
      }), _dec19 = property({
        group: {
          id: 'Color Grading',
          name: 'ColorGrading (LDR) (PostProcessing)',
          style: 'section'
        },
        type: Material
      }), _dec20 = property({
        tooltip: 'i18n:color_grading.contribute',
        group: {
          id: 'Color Grading',
          name: 'ColorGrading (LDR) (PostProcessing)',
          style: 'section'
        },
        type: CCFloat,
        range: [0, 1, 0.01],
        slide: true
      }), _dec21 = property({
        tooltip: 'i18n:color_grading.originalMap',
        group: {
          id: 'Color Grading',
          name: 'ColorGrading (LDR) (PostProcessing)',
          style: 'section'
        },
        type: Texture2D
      }), _dec22 = property({
        group: {
          id: 'FXAA',
          name: 'Fast Approximate Anti-Aliasing (PostProcessing)',
          style: 'section'
        },
        type: CCBoolean
      }), _dec23 = property({
        group: {
          id: 'FXAA',
          name: 'Fast Approximate Anti-Aliasing (PostProcessing)',
          style: 'section'
        },
        type: Material
      }), _dec24 = property({
        group: {
          id: 'FSR',
          name: 'FidelityFX Super Resolution',
          style: 'section'
        },
        type: CCBoolean
      }), _dec25 = property({
        group: {
          id: 'FSR',
          name: 'FidelityFX Super Resolution',
          style: 'section'
        },
        type: Material
      }), _dec26 = property({
        group: {
          id: 'FSR',
          name: 'FidelityFX Super Resolution',
          style: 'section'
        },
        type: CCFloat,
        range: [0, 1, 0.01],
        slide: true
      }), _dec27 = property({
        group: {
          id: 'ToneMapping',
          name: 'ToneMapping',
          style: 'section'
        },
        type: Material
      }), _dec(_class = _dec2(_class = _dec3(_class = disallowMultiple(_class = executeInEditMode(_class = (_class2 = class BuiltinPipelineSettings extends Component {
        constructor(...args) {
          super(...args);

          _initializerDefineProperty(this, "_settings", _descriptor, this);

          // Editor Preview
          _initializerDefineProperty(this, "_editorPreview", _descriptor2, this);
        }

        getPipelineSettings() {
          return this._settings;
        } // Enable/Disable


        onEnable() {
          (_crd && fillRequiredPipelineSettings === void 0 ? (_reportPossibleCrUseOffillRequiredPipelineSettings({
            error: Error()
          }), fillRequiredPipelineSettings) : fillRequiredPipelineSettings)(this._settings);
          const cameraComponent = this.getComponent(Camera);
          const camera = cameraComponent.camera;
          camera.pipelineSettings = this._settings;

          if (EDITOR) {
            this._tryEnableEditorPreview();
          }
        }

        onDisable() {
          const cameraComponent = this.getComponent(Camera);
          const camera = cameraComponent.camera;

          if (camera) {
            camera.pipelineSettings = null;
          }

          if (EDITOR) {
            this._disableEditorPreview();
          }
        }

        get editorPreview() {
          return this._editorPreview;
        }

        set editorPreview(v) {
          this._editorPreview = v;

          if (EDITOR) {
            this._tryEnableEditorPreview();
          }
        }

        _tryEnableEditorPreview() {
          if (rendering === undefined) {
            return;
          }

          if (this._editorPreview) {
            rendering.setEditorPipelineSettings(this._settings);
          } else {
            this._disableEditorPreview();
          }
        }

        _disableEditorPreview() {
          if (rendering === undefined) {
            return;
          }

          const current = rendering.getEditorPipelineSettings();

          if (current === this._settings) {
            rendering.setEditorPipelineSettings(null);
          }
        } // MSAA


        get MsaaEnable() {
          return this._settings.msaa.enabled;
        }

        set MsaaEnable(value) {
          this._settings.msaa.enabled = value;

          if (EDITOR) {
            this._tryEnableEditorPreview();
          }
        }

        set msaaSampleCount(value) {
          value = 2 ** Math.ceil(Math.log2(Math.max(value, 2)));
          value = Math.min(value, 4);
          this._settings.msaa.sampleCount = value;

          if (EDITOR) {
            this._tryEnableEditorPreview();
          }
        }

        get msaaSampleCount() {
          return this._settings.msaa.sampleCount;
        } // Shading Scale


        set shadingScaleEnable(value) {
          this._settings.enableShadingScale = value;

          if (EDITOR) {
            this._tryEnableEditorPreview();
          }
        }

        get shadingScaleEnable() {
          return this._settings.enableShadingScale;
        }

        set shadingScale(value) {
          this._settings.shadingScale = value;

          if (EDITOR) {
            this._tryEnableEditorPreview();
          }
        }

        get shadingScale() {
          return this._settings.shadingScale;
        } // Bloom


        set bloomEnable(value) {
          this._settings.bloom.enabled = value;

          if (EDITOR) {
            this._tryEnableEditorPreview();
          }
        }

        get bloomEnable() {
          return this._settings.bloom.enabled;
        }

        set bloomType(value) {
          this._settings.bloom.type = value;

          if (EDITOR) {
            this._tryEnableEditorPreview();
          }
        }

        get bloomType() {
          return this._settings.bloom.type;
        }

        set kawaseBloomMaterial(value) {
          if (this._settings.bloom.kawaseFilterMaterial === value) {
            return;
          }

          this._settings.bloom.kawaseFilterMaterial = value;

          if (EDITOR) {
            this._tryEnableEditorPreview();
          }
        }

        get kawaseBloomMaterial() {
          return this._settings.bloom.kawaseFilterMaterial;
        }

        set mipmapBloomMaterial(value) {
          if (this._settings.bloom.mipmapFilterMaterial === value) {
            return;
          }

          this._settings.bloom.mipmapFilterMaterial = value;

          if (EDITOR) {
            this._tryEnableEditorPreview();
          }
        }

        get mipmapBloomMaterial() {
          return this._settings.bloom.mipmapFilterMaterial;
        }

        set bloomEnableAlphaMask(value) {
          this._settings.bloom.enableAlphaMask = value;

          if (EDITOR) {
            this._tryEnableEditorPreview();
          }
        }

        get bloomEnableAlphaMask() {
          return this._settings.bloom.enableAlphaMask;
        }

        set bloomIterations(value) {
          this._settings.bloom.iterations = value;

          if (EDITOR) {
            this._tryEnableEditorPreview();
          }
        }

        get bloomIterations() {
          return this._settings.bloom.iterations;
        }

        set bloomThreshold(value) {
          this._settings.bloom.threshold = value;
        }

        get bloomThreshold() {
          return this._settings.bloom.threshold;
        }

        set bloomIntensity(value) {
          this._settings.bloom.intensity = value;

          if (EDITOR) {
            this._tryEnableEditorPreview();
          }
        }

        get bloomIntensity() {
          return this._settings.bloom.intensity;
        } // Color Grading (LDR)


        set colorGradingEnable(value) {
          this._settings.colorGrading.enabled = value;

          if (EDITOR) {
            this._tryEnableEditorPreview();
          }
        }

        get colorGradingEnable() {
          return this._settings.colorGrading.enabled;
        }

        set colorGradingMaterial(value) {
          if (this._settings.colorGrading.material === value) {
            return;
          }

          this._settings.colorGrading.material = value;

          if (EDITOR) {
            this._tryEnableEditorPreview();
          }
        }

        get colorGradingMaterial() {
          return this._settings.colorGrading.material;
        }

        set colorGradingContribute(value) {
          this._settings.colorGrading.contribute = value;
        }

        get colorGradingContribute() {
          return this._settings.colorGrading.contribute;
        }

        set colorGradingMap(val) {
          this._settings.colorGrading.colorGradingMap = val;

          if (EDITOR) {
            this._tryEnableEditorPreview();
          }
        }

        get colorGradingMap() {
          return this._settings.colorGrading.colorGradingMap;
        } // FXAA


        set fxaaEnable(value) {
          this._settings.fxaa.enabled = value;

          if (EDITOR) {
            this._tryEnableEditorPreview();
          }
        }

        get fxaaEnable() {
          return this._settings.fxaa.enabled;
        }

        set fxaaMaterial(value) {
          if (this._settings.fxaa.material === value) {
            return;
          }

          this._settings.fxaa.material = value;

          if (EDITOR) {
            this._tryEnableEditorPreview();
          }
        }

        get fxaaMaterial() {
          return this._settings.fxaa.material;
        } // FSR


        set fsrEnable(value) {
          this._settings.fsr.enabled = value;

          if (EDITOR) {
            this._tryEnableEditorPreview();
          }
        }

        get fsrEnable() {
          return this._settings.fsr.enabled;
        }

        set fsrMaterial(value) {
          if (this._settings.fsr.material === value) {
            return;
          }

          this._settings.fsr.material = value;

          if (EDITOR) {
            this._tryEnableEditorPreview();
          }
        }

        get fsrMaterial() {
          return this._settings.fsr.material;
        }

        set fsrSharpness(value) {
          this._settings.fsr.sharpness = value;
        }

        get fsrSharpness() {
          return this._settings.fsr.sharpness;
        }

        set toneMappingMaterial(value) {
          if (this._settings.toneMapping.material === value) {
            return;
          }

          this._settings.toneMapping.material = value;

          if (EDITOR) {
            this._tryEnableEditorPreview();
          }
        }

        get toneMappingMaterial() {
          return this._settings.toneMapping.material;
        }

      }, (_descriptor = _applyDecoratedDescriptor(_class2.prototype, "_settings", [property], {
        configurable: true,
        enumerable: true,
        writable: true,
        initializer: function () {
          return (_crd && makePipelineSettings === void 0 ? (_reportPossibleCrUseOfmakePipelineSettings({
            error: Error()
          }), makePipelineSettings) : makePipelineSettings)();
        }
      }), _descriptor2 = _applyDecoratedDescriptor(_class2.prototype, "_editorPreview", [_dec4], {
        configurable: true,
        enumerable: true,
        writable: true,
        initializer: function () {
          return false;
        }
      }), _applyDecoratedDescriptor(_class2.prototype, "editorPreview", [_dec5], Object.getOwnPropertyDescriptor(_class2.prototype, "editorPreview"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "MsaaEnable", [_dec6], Object.getOwnPropertyDescriptor(_class2.prototype, "MsaaEnable"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "msaaSampleCount", [_dec7], Object.getOwnPropertyDescriptor(_class2.prototype, "msaaSampleCount"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "shadingScaleEnable", [_dec8], Object.getOwnPropertyDescriptor(_class2.prototype, "shadingScaleEnable"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "shadingScale", [_dec9], Object.getOwnPropertyDescriptor(_class2.prototype, "shadingScale"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "bloomEnable", [_dec0], Object.getOwnPropertyDescriptor(_class2.prototype, "bloomEnable"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "bloomType", [_dec1, _dec10], Object.getOwnPropertyDescriptor(_class2.prototype, "bloomType"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "kawaseBloomMaterial", [_dec11], Object.getOwnPropertyDescriptor(_class2.prototype, "kawaseBloomMaterial"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "mipmapBloomMaterial", [_dec12], Object.getOwnPropertyDescriptor(_class2.prototype, "mipmapBloomMaterial"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "bloomEnableAlphaMask", [_dec13], Object.getOwnPropertyDescriptor(_class2.prototype, "bloomEnableAlphaMask"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "bloomIterations", [_dec14], Object.getOwnPropertyDescriptor(_class2.prototype, "bloomIterations"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "bloomThreshold", [_dec15], Object.getOwnPropertyDescriptor(_class2.prototype, "bloomThreshold"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "bloomIntensity", [_dec16, _dec17], Object.getOwnPropertyDescriptor(_class2.prototype, "bloomIntensity"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "colorGradingEnable", [_dec18], Object.getOwnPropertyDescriptor(_class2.prototype, "colorGradingEnable"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "colorGradingMaterial", [_dec19], Object.getOwnPropertyDescriptor(_class2.prototype, "colorGradingMaterial"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "colorGradingContribute", [_dec20], Object.getOwnPropertyDescriptor(_class2.prototype, "colorGradingContribute"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "colorGradingMap", [_dec21], Object.getOwnPropertyDescriptor(_class2.prototype, "colorGradingMap"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "fxaaEnable", [_dec22], Object.getOwnPropertyDescriptor(_class2.prototype, "fxaaEnable"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "fxaaMaterial", [_dec23], Object.getOwnPropertyDescriptor(_class2.prototype, "fxaaMaterial"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "fsrEnable", [_dec24], Object.getOwnPropertyDescriptor(_class2.prototype, "fsrEnable"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "fsrMaterial", [_dec25], Object.getOwnPropertyDescriptor(_class2.prototype, "fsrMaterial"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "fsrSharpness", [_dec26], Object.getOwnPropertyDescriptor(_class2.prototype, "fsrSharpness"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "toneMappingMaterial", [_dec27], Object.getOwnPropertyDescriptor(_class2.prototype, "toneMappingMaterial"), _class2.prototype)), _class2)) || _class) || _class) || _class) || _class) || _class));

      _cclegacy._RF.pop();

      _crd = false;
    }
  };
});
//# sourceMappingURL=60d79e4022ab5d642fe0cc64a0d71fe2c8d56729.js.map