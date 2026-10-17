System.register("chunks:///_virtual/builtin-pipeline-settings.js", ['./rollupPluginModLoBabelHelpers.js', 'cc', './builtin-pipeline-types.js'], function (exports) {
  var _applyDecoratedDescriptor, _inherits, _createClass, _classCallCheck, _callSuper, _initializerDefineProperty, cclegacy, _decorator, Camera, CCBoolean, CCInteger, CCFloat, Material, Texture2D, rendering, Component, BloomType, makePipelineSettings, fillRequiredPipelineSettings;
  return {
    setters: [function (module) {
      _applyDecoratedDescriptor = module.applyDecoratedDescriptor;
      _inherits = module.inherits;
      _createClass = module.createClass;
      _classCallCheck = module.classCallCheck;
      _callSuper = module.callSuper;
      _initializerDefineProperty = module.initializerDefineProperty;
    }, function (module) {
      cclegacy = module.cclegacy;
      _decorator = module._decorator;
      Camera = module.Camera;
      CCBoolean = module.CCBoolean;
      CCInteger = module.CCInteger;
      CCFloat = module.CCFloat;
      Material = module.Material;
      Texture2D = module.Texture2D;
      rendering = module.rendering;
      Component = module.Component;
    }, function (module) {
      BloomType = module.BloomType;
      makePipelineSettings = module.makePipelineSettings;
      fillRequiredPipelineSettings = module.fillRequiredPipelineSettings;
    }],
    execute: function () {
      var _dec, _dec2, _dec3, _dec4, _dec5, _dec6, _dec7, _dec8, _dec9, _dec0, _dec1, _dec10, _dec11, _dec12, _dec13, _dec14, _dec15, _dec16, _dec17, _dec18, _dec19, _dec20, _dec21, _dec22, _dec23, _dec24, _dec25, _dec26, _dec27, _class, _class2, _descriptor, _descriptor2;
      cclegacy._RF.push({}, "de1c2EHcMhAIYRZY5nyTQHG", "builtin-pipeline-settings", undefined);
      var ccclass = _decorator.ccclass,
        disallowMultiple = _decorator.disallowMultiple,
        executeInEditMode = _decorator.executeInEditMode,
        menu = _decorator.menu,
        property = _decorator.property,
        requireComponent = _decorator.requireComponent,
        type = _decorator.type;
      var BuiltinPipelineSettings = exports('BuiltinPipelineSettings', (_dec = ccclass('BuiltinPipelineSettings'), _dec2 = menu('Rendering/BuiltinPipelineSettings'), _dec3 = requireComponent(Camera), _dec4 = property(CCBoolean), _dec5 = property({
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
      }), _dec1 = type(BloomType), _dec10 = property({
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
      }), _dec(_class = _dec2(_class = _dec3(_class = disallowMultiple(_class = executeInEditMode(_class = (_class2 = /*#__PURE__*/function (_Component) {
        function BuiltinPipelineSettings() {
          var _this;
          _classCallCheck(this, BuiltinPipelineSettings);
          for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
            args[_key] = arguments[_key];
          }
          _this = _callSuper(this, BuiltinPipelineSettings, [].concat(args));
          _initializerDefineProperty(_this, "_settings", _descriptor, _this);
          // Editor Preview
          _initializerDefineProperty(_this, "_editorPreview", _descriptor2, _this);
          return _this;
        }
        _inherits(BuiltinPipelineSettings, _Component);
        return _createClass(BuiltinPipelineSettings, [{
          key: "getPipelineSettings",
          value: function getPipelineSettings() {
            return this._settings;
          }

          // Enable/Disable
        }, {
          key: "onEnable",
          value: function onEnable() {
            fillRequiredPipelineSettings(this._settings);
            var cameraComponent = this.getComponent(Camera);
            var camera = cameraComponent.camera;
            camera.pipelineSettings = this._settings;
          }
        }, {
          key: "onDisable",
          value: function onDisable() {
            var cameraComponent = this.getComponent(Camera);
            var camera = cameraComponent.camera;
            if (camera) {
              camera.pipelineSettings = null;
            }
          }
        }, {
          key: "editorPreview",
          get: function get() {
            return this._editorPreview;
          },
          set: function set(v) {
            this._editorPreview = v;
          }
        }, {
          key: "_tryEnableEditorPreview",
          value: function _tryEnableEditorPreview() {
            if (rendering === undefined) {
              return;
            }
            if (this._editorPreview) {
              rendering.setEditorPipelineSettings(this._settings);
            } else {
              this._disableEditorPreview();
            }
          }
        }, {
          key: "_disableEditorPreview",
          value: function _disableEditorPreview() {
            if (rendering === undefined) {
              return;
            }
            var current = rendering.getEditorPipelineSettings();
            if (current === this._settings) {
              rendering.setEditorPipelineSettings(null);
            }
          }

          // MSAA
        }, {
          key: "MsaaEnable",
          get: function get() {
            return this._settings.msaa.enabled;
          },
          set: function set(value) {
            this._settings.msaa.enabled = value;
          }
        }, {
          key: "msaaSampleCount",
          get: function get() {
            return this._settings.msaa.sampleCount;
          }

          // Shading Scale
          ,

          set: function set(value) {
            value = Math.pow(2, Math.ceil(Math.log2(Math.max(value, 2))));
            value = Math.min(value, 4);
            this._settings.msaa.sampleCount = value;
          }
        }, {
          key: "shadingScaleEnable",
          get: function get() {
            return this._settings.enableShadingScale;
          },
          set: function set(value) {
            this._settings.enableShadingScale = value;
          }
        }, {
          key: "shadingScale",
          get: function get() {
            return this._settings.shadingScale;
          }

          // Bloom
          ,

          set: function set(value) {
            this._settings.shadingScale = value;
          }
        }, {
          key: "bloomEnable",
          get: function get() {
            return this._settings.bloom.enabled;
          },
          set: function set(value) {
            this._settings.bloom.enabled = value;
          }
        }, {
          key: "bloomType",
          get: function get() {
            return this._settings.bloom.type;
          },
          set: function set(value) {
            this._settings.bloom.type = value;
          }
        }, {
          key: "kawaseBloomMaterial",
          get: function get() {
            return this._settings.bloom.kawaseFilterMaterial;
          },
          set: function set(value) {
            if (this._settings.bloom.kawaseFilterMaterial === value) {
              return;
            }
            this._settings.bloom.kawaseFilterMaterial = value;
          }
        }, {
          key: "mipmapBloomMaterial",
          get: function get() {
            return this._settings.bloom.mipmapFilterMaterial;
          },
          set: function set(value) {
            if (this._settings.bloom.mipmapFilterMaterial === value) {
              return;
            }
            this._settings.bloom.mipmapFilterMaterial = value;
          }
        }, {
          key: "bloomEnableAlphaMask",
          get: function get() {
            return this._settings.bloom.enableAlphaMask;
          },
          set: function set(value) {
            this._settings.bloom.enableAlphaMask = value;
          }
        }, {
          key: "bloomIterations",
          get: function get() {
            return this._settings.bloom.iterations;
          },
          set: function set(value) {
            this._settings.bloom.iterations = value;
          }
        }, {
          key: "bloomThreshold",
          get: function get() {
            return this._settings.bloom.threshold;
          },
          set: function set(value) {
            this._settings.bloom.threshold = value;
          }
        }, {
          key: "bloomIntensity",
          get: function get() {
            return this._settings.bloom.intensity;
          }

          // Color Grading (LDR)
          ,

          set: function set(value) {
            this._settings.bloom.intensity = value;
          }
        }, {
          key: "colorGradingEnable",
          get: function get() {
            return this._settings.colorGrading.enabled;
          },
          set: function set(value) {
            this._settings.colorGrading.enabled = value;
          }
        }, {
          key: "colorGradingMaterial",
          get: function get() {
            return this._settings.colorGrading.material;
          },
          set: function set(value) {
            if (this._settings.colorGrading.material === value) {
              return;
            }
            this._settings.colorGrading.material = value;
          }
        }, {
          key: "colorGradingContribute",
          get: function get() {
            return this._settings.colorGrading.contribute;
          },
          set: function set(value) {
            this._settings.colorGrading.contribute = value;
          }
        }, {
          key: "colorGradingMap",
          get: function get() {
            return this._settings.colorGrading.colorGradingMap;
          }

          // FXAA
          ,

          set: function set(val) {
            this._settings.colorGrading.colorGradingMap = val;
          }
        }, {
          key: "fxaaEnable",
          get: function get() {
            return this._settings.fxaa.enabled;
          },
          set: function set(value) {
            this._settings.fxaa.enabled = value;
          }
        }, {
          key: "fxaaMaterial",
          get: function get() {
            return this._settings.fxaa.material;
          }

          // FSR
          ,

          set: function set(value) {
            if (this._settings.fxaa.material === value) {
              return;
            }
            this._settings.fxaa.material = value;
          }
        }, {
          key: "fsrEnable",
          get: function get() {
            return this._settings.fsr.enabled;
          },
          set: function set(value) {
            this._settings.fsr.enabled = value;
          }
        }, {
          key: "fsrMaterial",
          get: function get() {
            return this._settings.fsr.material;
          },
          set: function set(value) {
            if (this._settings.fsr.material === value) {
              return;
            }
            this._settings.fsr.material = value;
          }
        }, {
          key: "fsrSharpness",
          get: function get() {
            return this._settings.fsr.sharpness;
          },
          set: function set(value) {
            this._settings.fsr.sharpness = value;
          }
        }, {
          key: "toneMappingMaterial",
          get: function get() {
            return this._settings.toneMapping.material;
          },
          set: function set(value) {
            if (this._settings.toneMapping.material === value) {
              return;
            }
            this._settings.toneMapping.material = value;
          }
        }]);
      }(Component), _descriptor = _applyDecoratedDescriptor(_class2.prototype, "_settings", [property], {
        configurable: true,
        enumerable: true,
        writable: true,
        initializer: function initializer() {
          return makePipelineSettings();
        }
      }), _descriptor2 = _applyDecoratedDescriptor(_class2.prototype, "_editorPreview", [_dec4], {
        configurable: true,
        enumerable: true,
        writable: true,
        initializer: function initializer() {
          return false;
        }
      }), _applyDecoratedDescriptor(_class2.prototype, "editorPreview", [_dec5], Object.getOwnPropertyDescriptor(_class2.prototype, "editorPreview"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "MsaaEnable", [_dec6], Object.getOwnPropertyDescriptor(_class2.prototype, "MsaaEnable"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "msaaSampleCount", [_dec7], Object.getOwnPropertyDescriptor(_class2.prototype, "msaaSampleCount"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "shadingScaleEnable", [_dec8], Object.getOwnPropertyDescriptor(_class2.prototype, "shadingScaleEnable"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "shadingScale", [_dec9], Object.getOwnPropertyDescriptor(_class2.prototype, "shadingScale"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "bloomEnable", [_dec0], Object.getOwnPropertyDescriptor(_class2.prototype, "bloomEnable"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "bloomType", [_dec1, _dec10], Object.getOwnPropertyDescriptor(_class2.prototype, "bloomType"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "kawaseBloomMaterial", [_dec11], Object.getOwnPropertyDescriptor(_class2.prototype, "kawaseBloomMaterial"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "mipmapBloomMaterial", [_dec12], Object.getOwnPropertyDescriptor(_class2.prototype, "mipmapBloomMaterial"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "bloomEnableAlphaMask", [_dec13], Object.getOwnPropertyDescriptor(_class2.prototype, "bloomEnableAlphaMask"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "bloomIterations", [_dec14], Object.getOwnPropertyDescriptor(_class2.prototype, "bloomIterations"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "bloomThreshold", [_dec15], Object.getOwnPropertyDescriptor(_class2.prototype, "bloomThreshold"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "bloomIntensity", [_dec16, _dec17], Object.getOwnPropertyDescriptor(_class2.prototype, "bloomIntensity"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "colorGradingEnable", [_dec18], Object.getOwnPropertyDescriptor(_class2.prototype, "colorGradingEnable"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "colorGradingMaterial", [_dec19], Object.getOwnPropertyDescriptor(_class2.prototype, "colorGradingMaterial"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "colorGradingContribute", [_dec20], Object.getOwnPropertyDescriptor(_class2.prototype, "colorGradingContribute"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "colorGradingMap", [_dec21], Object.getOwnPropertyDescriptor(_class2.prototype, "colorGradingMap"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "fxaaEnable", [_dec22], Object.getOwnPropertyDescriptor(_class2.prototype, "fxaaEnable"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "fxaaMaterial", [_dec23], Object.getOwnPropertyDescriptor(_class2.prototype, "fxaaMaterial"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "fsrEnable", [_dec24], Object.getOwnPropertyDescriptor(_class2.prototype, "fsrEnable"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "fsrMaterial", [_dec25], Object.getOwnPropertyDescriptor(_class2.prototype, "fsrMaterial"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "fsrSharpness", [_dec26], Object.getOwnPropertyDescriptor(_class2.prototype, "fsrSharpness"), _class2.prototype), _applyDecoratedDescriptor(_class2.prototype, "toneMappingMaterial", [_dec27], Object.getOwnPropertyDescriptor(_class2.prototype, "toneMappingMaterial"), _class2.prototype), _class2)) || _class) || _class) || _class) || _class) || _class));
      cclegacy._RF.pop();
    }
  };
});

System.register("chunks:///_virtual/builtin-pipeline-types.js", ['cc'], function (exports) {
  var cclegacy, gfx, ccenum;
  return {
    setters: [function (module) {
      cclegacy = module.cclegacy;
      gfx = module.gfx;
      ccenum = module.ccenum;
    }],
    execute: function () {
      exports({
        fillRequiredBloom: fillRequiredBloom,
        fillRequiredColorGrading: fillRequiredColorGrading,
        fillRequiredFSR: fillRequiredFSR,
        fillRequiredFXAA: fillRequiredFXAA,
        fillRequiredHBAO: fillRequiredHBAO,
        fillRequiredMSAA: fillRequiredMSAA,
        fillRequiredPipelineSettings: fillRequiredPipelineSettings,
        fillRequiredToneMapping: fillRequiredToneMapping,
        makeBloom: makeBloom,
        makeColorGrading: makeColorGrading,
        makeFSR: makeFSR,
        makeFXAA: makeFXAA,
        makeHBAO: makeHBAO,
        makeMSAA: makeMSAA,
        makePipelineSettings: makePipelineSettings,
        makeToneMapping: makeToneMapping
      });
      cclegacy._RF.push({}, "cbf30kCUX9A3K+QpVC6wnzx", "builtin-pipeline-types", undefined);
      var SampleCount = gfx.SampleCount;
      function makeMSAA() {
        return {
          enabled: false,
          sampleCount: SampleCount.X4
        };
      }
      function fillRequiredMSAA(value) {
        if (value.enabled === undefined) {
          value.enabled = false;
        }
        if (value.sampleCount === undefined) {
          value.sampleCount = SampleCount.X4;
        }
      }
      function makeHBAO() {
        return {
          enabled: false,
          radiusScale: 1,
          angleBiasDegree: 10,
          blurSharpness: 3,
          aoSaturation: 1,
          needBlur: false
        };
      }
      function fillRequiredHBAO(value) {
        if (value.enabled === undefined) {
          value.enabled = false;
        }
        if (value.radiusScale === undefined) {
          value.radiusScale = 1;
        }
        if (value.angleBiasDegree === undefined) {
          value.angleBiasDegree = 10;
        }
        if (value.blurSharpness === undefined) {
          value.blurSharpness = 3;
        }
        if (value.aoSaturation === undefined) {
          value.aoSaturation = 1;
        }
        if (value.needBlur === undefined) {
          value.needBlur = false;
        }
      }
      var BloomType = exports('BloomType', /*#__PURE__*/function (BloomType) {
        BloomType[BloomType["KawaseDualFilter"] = 0] = "KawaseDualFilter";
        BloomType[BloomType["MipmapFilter"] = 1] = "MipmapFilter";
        return BloomType;
      }({}));
      ccenum(BloomType);
      function makeBloom() {
        return {
          enabled: false,
          type: BloomType.KawaseDualFilter,
          material: null,
          kawaseFilterMaterial: null,
          mipmapFilterMaterial: null,
          enableAlphaMask: false,
          iterations: 3,
          threshold: 0.8,
          intensity: 1
        };
      }
      function fillRequiredBloom(value) {
        if (value.enabled === undefined) {
          value.enabled = false;
        }
        if (value.type === undefined) {
          value.type = BloomType.KawaseDualFilter;
        }
        if (value.material === undefined) {
          value.material = null;
        }
        if (value.kawaseFilterMaterial === undefined) {
          value.kawaseFilterMaterial = value.material || null;
        }
        if (value.mipmapFilterMaterial === undefined) {
          value.mipmapFilterMaterial = null;
        }
        if (value.enableAlphaMask === undefined) {
          value.enableAlphaMask = false;
        }
        if (value.iterations === undefined) {
          value.iterations = 3;
        }
        if (value.threshold === undefined) {
          value.threshold = 0.8;
        }
        if (value.intensity === undefined) {
          value.intensity = 1;
        }
      }
      function makeColorGrading() {
        return {
          enabled: false,
          material: null,
          contribute: 1,
          colorGradingMap: null
        };
      }
      function fillRequiredColorGrading(value) {
        if (value.enabled === undefined) {
          value.enabled = false;
        }
        if (value.material === undefined) {
          value.material = null;
        }
        if (value.contribute === undefined) {
          value.contribute = 1;
        }
        if (value.colorGradingMap === undefined) {
          value.colorGradingMap = null;
        }
      }
      function makeFSR() {
        return {
          enabled: false,
          material: null,
          sharpness: 0.8
        };
      }
      function fillRequiredFSR(value) {
        if (value.enabled === undefined) {
          value.enabled = false;
        }
        if (value.material === undefined) {
          value.material = null;
        }
        if (value.sharpness === undefined) {
          value.sharpness = 0.8;
        }
      }
      function makeFXAA() {
        return {
          enabled: false,
          material: null
        };
      }
      function fillRequiredFXAA(value) {
        if (value.enabled === undefined) {
          value.enabled = false;
        }
        if (value.material === undefined) {
          value.material = null;
        }
      }
      function makeToneMapping() {
        return {
          material: null
        };
      }
      function fillRequiredToneMapping(value) {
        if (value.material === undefined) {
          value.material = null;
        }
      }
      function makePipelineSettings() {
        return {
          msaa: makeMSAA(),
          enableShadingScale: false,
          shadingScale: 0.5,
          bloom: makeBloom(),
          toneMapping: makeToneMapping(),
          colorGrading: makeColorGrading(),
          fsr: makeFSR(),
          fxaa: makeFXAA()
        };
      }
      function fillRequiredPipelineSettings(value) {
        if (!value.msaa) {
          value.msaa = makeMSAA();
        } else {
          fillRequiredMSAA(value.msaa);
        }
        if (value.enableShadingScale === undefined) {
          value.enableShadingScale = false;
        }
        if (value.shadingScale === undefined) {
          value.shadingScale = 0.5;
        }
        if (!value.bloom) {
          value.bloom = makeBloom();
        } else {
          fillRequiredBloom(value.bloom);
        }
        if (!value.toneMapping) {
          value.toneMapping = makeToneMapping();
        } else {
          fillRequiredToneMapping(value.toneMapping);
        }
        if (!value.colorGrading) {
          value.colorGrading = makeColorGrading();
        } else {
          fillRequiredColorGrading(value.colorGrading);
        }
        if (!value.fsr) {
          value.fsr = makeFSR();
        } else {
          fillRequiredFSR(value.fsr);
        }
        if (!value.fxaa) {
          value.fxaa = makeFXAA();
        } else {
          fillRequiredFXAA(value.fxaa);
        }
      }
      cclegacy._RF.pop();
    }
  };
});

System.register("chunks:///_virtual/builtin-pipeline.js", ['./rollupPluginModLoBabelHelpers.js', 'cc', './env.js', './builtin-pipeline-types.js'], function (exports) {
  var _defineProperty, _createClass, _classCallCheck, _createForOfIteratorHelper, cclegacy, geometry, gfx, renderer, rendering, Vec2, Vec4, macro, assert, warn, clamp, Layers, PipelineEventType, sys, pipeline, Vec3, Material, EDITOR, makePipelineSettings, BloomType;
  return {
    setters: [function (module) {
      _defineProperty = module.defineProperty;
      _createClass = module.createClass;
      _classCallCheck = module.classCallCheck;
      _createForOfIteratorHelper = module.createForOfIteratorHelper;
    }, function (module) {
      cclegacy = module.cclegacy;
      geometry = module.geometry;
      gfx = module.gfx;
      renderer = module.renderer;
      rendering = module.rendering;
      Vec2 = module.Vec2;
      Vec4 = module.Vec4;
      macro = module.macro;
      assert = module.assert;
      warn = module.warn;
      clamp = module.clamp;
      Layers = module.Layers;
      PipelineEventType = module.PipelineEventType;
      sys = module.sys;
      pipeline = module.pipeline;
      Vec3 = module.Vec3;
      Material = module.Material;
    }, function (module) {
      EDITOR = module.EDITOR;
    }, function (module) {
      makePipelineSettings = module.makePipelineSettings;
      BloomType = module.BloomType;
    }],
    execute: function () {
      exports('getPingPongRenderTarget', getPingPongRenderTarget);
      cclegacy._RF.push({}, "ff9b0GZzgRM/obMbHGfCNbk", "builtin-pipeline", undefined);
      var AABB = geometry.AABB,
        Sphere = geometry.Sphere,
        intersect = geometry.intersect;
      var ClearFlagBit = gfx.ClearFlagBit,
        Color = gfx.Color,
        Format = gfx.Format,
        FormatFeatureBit = gfx.FormatFeatureBit,
        LoadOp = gfx.LoadOp,
        StoreOp = gfx.StoreOp,
        TextureType = gfx.TextureType,
        Viewport = gfx.Viewport;
      var scene = renderer.scene;
      var CameraUsage = scene.CameraUsage,
        CSMLevel = scene.CSMLevel,
        LightType = scene.LightType;
      function forwardNeedClearColor(camera) {
        return !!(camera.clearFlag & (ClearFlagBit.COLOR | ClearFlagBit.STENCIL << 1));
      }
      function getCsmMainLightViewport(light, w, h, level, vp, screenSpaceSignY) {
        if (light.shadowFixedArea || light.csmLevel === CSMLevel.LEVEL_1) {
          vp.left = 0;
          vp.top = 0;
          vp.width = Math.trunc(w);
          vp.height = Math.trunc(h);
        } else {
          vp.left = Math.trunc(level % 2 * 0.5 * w);
          if (screenSpaceSignY > 0) {
            vp.top = Math.trunc((1 - Math.floor(level / 2)) * 0.5 * h);
          } else {
            vp.top = Math.trunc(Math.floor(level / 2) * 0.5 * h);
          }
          vp.width = Math.trunc(0.5 * w);
          vp.height = Math.trunc(0.5 * h);
        }
        vp.left = Math.max(0, vp.left);
        vp.top = Math.max(0, vp.top);
        vp.width = Math.max(1, vp.width);
        vp.height = Math.max(1, vp.height);
      }
      var PipelineConfigs = exports('PipelineConfigs', /*#__PURE__*/_createClass(function PipelineConfigs() {
        _classCallCheck(this, PipelineConfigs);
        _defineProperty(this, "isWeb", false);
        _defineProperty(this, "isWebGL1", false);
        _defineProperty(this, "isWebGL2", false);
        _defineProperty(this, "isWebGPU", false);
        _defineProperty(this, "isMobile", false);
        _defineProperty(this, "isHDR", false);
        _defineProperty(this, "useFloatOutput", false);
        _defineProperty(this, "toneMappingType", 0);
        // 0: ACES, 1: None
        _defineProperty(this, "shadowEnabled", false);
        _defineProperty(this, "shadowMapFormat", Format.R32F);
        _defineProperty(this, "shadowMapSize", new Vec2(1, 1));
        _defineProperty(this, "usePlanarShadow", false);
        _defineProperty(this, "screenSpaceSignY", 1);
        _defineProperty(this, "supportDepthSample", false);
        _defineProperty(this, "mobileMaxSpotLightShadowMaps", 1);
        _defineProperty(this, "platform", new Vec4(0, 0, 0, 0));
      }));
      function setupPipelineConfigs(ppl, configs) {
        var sampleFeature = FormatFeatureBit.SAMPLED_TEXTURE | FormatFeatureBit.LINEAR_FILTER;
        var device = ppl.device;
        // Platform
        configs.isWeb = !sys.isNative;
        configs.isWebGL1 = device.gfxAPI === gfx.API.WEBGL;
        configs.isWebGL2 = device.gfxAPI === gfx.API.WEBGL2;
        configs.isWebGPU = device.gfxAPI === gfx.API.WEBGPU;
        configs.isMobile = sys.isMobile;

        // Rendering
        configs.isHDR = ppl.pipelineSceneData.isHDR; // Has tone mapping
        configs.useFloatOutput = ppl.getMacroBool('CC_USE_FLOAT_OUTPUT');
        configs.toneMappingType = ppl.pipelineSceneData.postSettings.toneMappingType;
        // Shadow
        var shadowInfo = ppl.pipelineSceneData.shadows;
        configs.shadowEnabled = shadowInfo.enabled;
        configs.shadowMapFormat = pipeline.supportsR32FloatTexture(ppl.device) ? Format.R32F : Format.RGBA8;
        configs.shadowMapSize.set(shadowInfo.size);
        configs.usePlanarShadow = shadowInfo.enabled && shadowInfo.type === renderer.scene.ShadowType.Planar;
        // Device
        configs.screenSpaceSignY = ppl.device.capabilities.screenSpaceSignY;
        configs.supportDepthSample = (ppl.device.getFormatFeatures(Format.DEPTH_STENCIL) & sampleFeature) === sampleFeature;
        // Constants
        var screenSpaceSignY = device.capabilities.screenSpaceSignY;
        configs.platform.x = configs.isMobile ? 1.0 : 0.0;
        configs.platform.w = screenSpaceSignY * 0.5 + 0.5 << 1 | device.capabilities.clipSpaceSignY * 0.5 + 0.5;
      }
      var defaultSettings = makePipelineSettings();
      var CameraConfigs = exports('CameraConfigs', /*#__PURE__*/_createClass(function CameraConfigs() {
        _classCallCheck(this, CameraConfigs);
        _defineProperty(this, "settings", defaultSettings);
        // Window
        _defineProperty(this, "isMainGameWindow", false);
        _defineProperty(this, "renderWindowId", 0);
        // Camera
        _defineProperty(this, "colorName", '');
        _defineProperty(this, "depthStencilName", '');
        // Pipeline
        _defineProperty(this, "enableFullPipeline", false);
        _defineProperty(this, "enableProfiler", false);
        _defineProperty(this, "remainingPasses", 0);
        // Shading Scale
        _defineProperty(this, "enableShadingScale", false);
        _defineProperty(this, "shadingScale", 1.0);
        _defineProperty(this, "nativeWidth", 1);
        _defineProperty(this, "nativeHeight", 1);
        _defineProperty(this, "width", 1);
        // Scaled width
        _defineProperty(this, "height", 1);
        // Scaled height
        // Radiance
        _defineProperty(this, "enableHDR", false);
        _defineProperty(this, "radianceFormat", gfx.Format.RGBA8);
        // Tone Mapping
        _defineProperty(this, "copyAndTonemapMaterial", null);
        // Depth
        /** @en mutable */
        _defineProperty(this, "enableStoreSceneDepth", false);
      }));
      var sClearColorTransparentBlack = new Color(0, 0, 0, 0);
      function sortPipelinePassBuildersByConfigOrder(passBuilders) {
        passBuilders.sort(function (a, b) {
          return a.getConfigOrder() - b.getConfigOrder();
        });
      }
      function sortPipelinePassBuildersByRenderOrder(passBuilders) {
        passBuilders.sort(function (a, b) {
          return a.getRenderOrder() - b.getRenderOrder();
        });
      }
      function addCopyToScreenPass(ppl, pplConfigs, cameraConfigs, input) {
        assert(!!cameraConfigs.copyAndTonemapMaterial);
        var pass = ppl.addRenderPass(cameraConfigs.nativeWidth, cameraConfigs.nativeHeight, 'cc-tone-mapping');
        pass.addRenderTarget(cameraConfigs.colorName, LoadOp.CLEAR, StoreOp.STORE, sClearColorTransparentBlack);
        pass.addTexture(input, 'inputTexture');
        pass.addQueue(rendering.QueueHint.OPAQUE).addFullscreenQuad(cameraConfigs.copyAndTonemapMaterial, 1);
        return pass;
      }
      function getPingPongRenderTarget(prevName, prefix, id) {
        if (prevName.startsWith(prefix)) {
          return "".concat(prefix).concat(1 - Number(prevName.charAt(prefix.length)), "_").concat(id);
        } else {
          return "".concat(prefix, "0_").concat(id);
        }
      }
      var ForwardLighting = /*#__PURE__*/function () {
        function ForwardLighting() {
          _classCallCheck(this, ForwardLighting);
          // Active lights
          _defineProperty(this, "lights", []);
          // Active spot lights with shadows (Mutually exclusive with `lights`)
          _defineProperty(this, "shadowEnabledSpotLights", []);
          // Internal cached resources
          _defineProperty(this, "_sphere", Sphere.create(0, 0, 0, 1));
          _defineProperty(this, "_boundingBox", new AABB());
          _defineProperty(this, "_rangedDirLightBoundingBox", new AABB(0.0, 0.0, 0.0, 0.5, 0.5, 0.5));
        }
        return _createClass(ForwardLighting, [{
          key: "cullLights",
          value:
          // ----------------------------------------------------------------
          // Interface
          // ----------------------------------------------------------------
          function cullLights(scene, frustum, cameraPos) {
            // TODO(zhouzhenglong): Make light culling native
            this.lights.length = 0;
            this.shadowEnabledSpotLights.length = 0;
            // spot lights
            var _iterator = _createForOfIteratorHelper(scene.spotLights),
              _step;
            try {
              for (_iterator.s(); !(_step = _iterator.n()).done;) {
                var light = _step.value;
                if (light.baked) {
                  continue;
                }
                Sphere.set(this._sphere, light.position.x, light.position.y, light.position.z, light.range);
                if (intersect.sphereFrustum(this._sphere, frustum)) {
                  if (light.shadowEnabled) {
                    this.shadowEnabledSpotLights.push(light);
                  } else {
                    this.lights.push(light);
                  }
                }
              }
              // sphere lights
            } catch (err) {
              _iterator.e(err);
            } finally {
              _iterator.f();
            }
            var _iterator2 = _createForOfIteratorHelper(scene.sphereLights),
              _step2;
            try {
              for (_iterator2.s(); !(_step2 = _iterator2.n()).done;) {
                var _light = _step2.value;
                if (_light.baked) {
                  continue;
                }
                Sphere.set(this._sphere, _light.position.x, _light.position.y, _light.position.z, _light.range);
                if (intersect.sphereFrustum(this._sphere, frustum)) {
                  this.lights.push(_light);
                }
              }
              // point lights
            } catch (err) {
              _iterator2.e(err);
            } finally {
              _iterator2.f();
            }
            var _iterator3 = _createForOfIteratorHelper(scene.pointLights),
              _step3;
            try {
              for (_iterator3.s(); !(_step3 = _iterator3.n()).done;) {
                var _light2 = _step3.value;
                if (_light2.baked) {
                  continue;
                }
                Sphere.set(this._sphere, _light2.position.x, _light2.position.y, _light2.position.z, _light2.range);
                if (intersect.sphereFrustum(this._sphere, frustum)) {
                  this.lights.push(_light2);
                }
              }
              // ranged dir lights
            } catch (err) {
              _iterator3.e(err);
            } finally {
              _iterator3.f();
            }
            var _iterator4 = _createForOfIteratorHelper(scene.rangedDirLights),
              _step4;
            try {
              for (_iterator4.s(); !(_step4 = _iterator4.n()).done;) {
                var _light3 = _step4.value;
                AABB.transform(this._boundingBox, this._rangedDirLightBoundingBox, _light3.node.getWorldMatrix());
                if (intersect.aabbFrustum(this._boundingBox, frustum)) {
                  this.lights.push(_light3);
                }
              }
            } catch (err) {
              _iterator4.e(err);
            } finally {
              _iterator4.f();
            }
            if (cameraPos) {
              this.shadowEnabledSpotLights.sort(function (lhs, rhs) {
                return Vec3.squaredDistance(cameraPos, lhs.position) - Vec3.squaredDistance(cameraPos, rhs.position);
              });
            }
          }
        }, {
          key: "_addLightQueues",
          value: function _addLightQueues(camera, pass) {
            var _iterator5 = _createForOfIteratorHelper(this.lights),
              _step5;
            try {
              for (_iterator5.s(); !(_step5 = _iterator5.n()).done;) {
                var light = _step5.value;
                var queue = pass.addQueue(rendering.QueueHint.BLEND, 'forward-add');
                switch (light.type) {
                  case LightType.SPHERE:
                    queue.name = 'sphere-light';
                    break;
                  case LightType.SPOT:
                    queue.name = 'spot-light';
                    break;
                  case LightType.POINT:
                    queue.name = 'point-light';
                    break;
                  case LightType.RANGED_DIRECTIONAL:
                    queue.name = 'ranged-directional-light';
                    break;
                  default:
                    queue.name = 'unknown-light';
                }
                queue.addScene(camera, rendering.SceneFlags.BLEND, light);
              }
            } catch (err) {
              _iterator5.e(err);
            } finally {
              _iterator5.f();
            }
          }
        }, {
          key: "addSpotlightShadowPasses",
          value: function addSpotlightShadowPasses(ppl, camera, maxNumShadowMaps) {
            var i = 0;
            var _iterator6 = _createForOfIteratorHelper(this.shadowEnabledSpotLights),
              _step6;
            try {
              for (_iterator6.s(); !(_step6 = _iterator6.n()).done;) {
                var light = _step6.value;
                var shadowMapSize = ppl.pipelineSceneData.shadows.size;
                var shadowPass = ppl.addRenderPass(shadowMapSize.x, shadowMapSize.y, 'default');
                shadowPass.name = "SpotLightShadowPass".concat(i);
                shadowPass.addRenderTarget("SpotShadowMap".concat(i), LoadOp.CLEAR, StoreOp.STORE, new Color(1, 1, 1, 1));
                shadowPass.addDepthStencil("SpotShadowDepth".concat(i), LoadOp.CLEAR, StoreOp.DISCARD);
                shadowPass.addQueue(rendering.QueueHint.NONE, 'shadow-caster').addScene(camera, rendering.SceneFlags.OPAQUE | rendering.SceneFlags.MASK | rendering.SceneFlags.SHADOW_CASTER).useLightFrustum(light);
                ++i;
                if (i >= maxNumShadowMaps) {
                  break;
                }
              }
            } catch (err) {
              _iterator6.e(err);
            } finally {
              _iterator6.f();
            }
          }
        }, {
          key: "addLightQueues",
          value: function addLightQueues(pass, camera, maxNumShadowMaps) {
            this._addLightQueues(camera, pass);
            var i = 0;
            var _iterator7 = _createForOfIteratorHelper(this.shadowEnabledSpotLights),
              _step7;
            try {
              for (_iterator7.s(); !(_step7 = _iterator7.n()).done;) {
                var light = _step7.value;
                // Add spot-light pass
                // Save last RenderPass to the `pass` variable
                // TODO(zhouzhenglong): Fix per queue addTexture
                pass.addTexture("SpotShadowMap".concat(i), 'cc_spotShadowMap');
                var queue = pass.addQueue(rendering.QueueHint.BLEND, 'forward-add');
                queue.addScene(camera, rendering.SceneFlags.BLEND, light);
                ++i;
                if (i >= maxNumShadowMaps) {
                  break;
                }
              }
            } catch (err) {
              _iterator7.e(err);
            } finally {
              _iterator7.f();
            }
          }

          // Notice: ForwardLighting cannot handle a lot of lights.
          // If there are too many lights, the performance will be very poor.
          // If many lights are needed, please implement a forward+ or deferred rendering pipeline.
        }, {
          key: "addLightPasses",
          value: function addLightPasses(colorName, depthStencilName, depthStencilStoreOp, id,
          // window id
          width, height, camera, viewport, ppl, pass) {
            this._addLightQueues(camera, pass);
            var count = 0;
            var shadowMapSize = ppl.pipelineSceneData.shadows.size;
            var _iterator8 = _createForOfIteratorHelper(this.shadowEnabledSpotLights),
              _step8;
            try {
              for (_iterator8.s(); !(_step8 = _iterator8.n()).done;) {
                var light = _step8.value;
                var shadowPass = ppl.addRenderPass(shadowMapSize.x, shadowMapSize.y, 'default');
                shadowPass.name = 'SpotlightShadowPass';
                // Reuse csm shadow map
                shadowPass.addRenderTarget("ShadowMap".concat(id), LoadOp.CLEAR, StoreOp.STORE, new Color(1, 1, 1, 1));
                shadowPass.addDepthStencil("ShadowDepth".concat(id), LoadOp.CLEAR, StoreOp.DISCARD);
                shadowPass.addQueue(rendering.QueueHint.NONE, 'shadow-caster').addScene(camera, rendering.SceneFlags.OPAQUE | rendering.SceneFlags.MASK | rendering.SceneFlags.SHADOW_CASTER).useLightFrustum(light);

                // Add spot-light pass
                // Save last RenderPass to the `pass` variable
                ++count;
                var storeOp = count === this.shadowEnabledSpotLights.length ? depthStencilStoreOp : StoreOp.STORE;
                pass = ppl.addRenderPass(width, height, 'default');
                pass.name = 'SpotlightWithShadowMap';
                pass.setViewport(viewport);
                pass.addRenderTarget(colorName, LoadOp.LOAD);
                pass.addDepthStencil(depthStencilName, LoadOp.LOAD, storeOp);
                pass.addTexture("ShadowMap".concat(id), 'cc_spotShadowMap');
                var queue = pass.addQueue(rendering.QueueHint.BLEND, 'forward-add');
                queue.addScene(camera, rendering.SceneFlags.BLEND, light);
              }
            } catch (err) {
              _iterator8.e(err);
            } finally {
              _iterator8.f();
            }
            return pass;
          }
        }, {
          key: "isMultipleLightPassesNeeded",
          value: function isMultipleLightPassesNeeded() {
            return this.shadowEnabledSpotLights.length > 0;
          }
        }]);
      }();
      var BuiltinForwardPassBuilder = exports('BuiltinForwardPassBuilder', /*#__PURE__*/function () {
        function BuiltinForwardPassBuilder() {
          _classCallCheck(this, BuiltinForwardPassBuilder);
          _defineProperty(this, "forwardLighting", new ForwardLighting());
          _defineProperty(this, "_viewport", new Viewport());
          _defineProperty(this, "_clearColor", new Color(0, 0, 0, 1));
          _defineProperty(this, "_reflectionProbeClearColor", new Vec3(0, 0, 0));
        }
        return _createClass(BuiltinForwardPassBuilder, [{
          key: "getConfigOrder",
          value: function getConfigOrder() {
            return BuiltinForwardPassBuilder.ConfigOrder;
          }
        }, {
          key: "getRenderOrder",
          value: function getRenderOrder() {
            return BuiltinForwardPassBuilder.RenderOrder;
          }
        }, {
          key: "configCamera",
          value: function configCamera(camera, pipelineConfigs, cameraConfigs) {
            // Shadow
            cameraConfigs.enableMainLightShadowMap = pipelineConfigs.shadowEnabled && !pipelineConfigs.usePlanarShadow && !!camera.scene && !!camera.scene.mainLight && camera.scene.mainLight.shadowEnabled;
            cameraConfigs.enableMainLightPlanarShadowMap = pipelineConfigs.shadowEnabled && pipelineConfigs.usePlanarShadow && !!camera.scene && !!camera.scene.mainLight && camera.scene.mainLight.shadowEnabled;

            // Reflection Probe
            cameraConfigs.enablePlanarReflectionProbe = cameraConfigs.isMainGameWindow || camera.cameraUsage === CameraUsage.SCENE_VIEW || camera.cameraUsage === CameraUsage.GAME_VIEW || camera.cameraUsage === CameraUsage.PREVIEW;

            // MSAA
            cameraConfigs.enableMSAA = cameraConfigs.settings.msaa.enabled && (!pipelineConfigs.isWebGL2 || cameraConfigs.remainingPasses > 0 || !macro.ENABLE_WEBGL_ANTIALIAS && macro.ENABLE_TRANSPARENT_CANVAS) && !cameraConfigs.enableStoreSceneDepth // Cannot store MS depth, resolve depth is also not cross-platform
            && !pipelineConfigs.isWebGL1;

            // Forward rendering (Depend on MSAA and TBR)
            cameraConfigs.enableSingleForwardPass = pipelineConfigs.isMobile || cameraConfigs.enableMSAA;
            ++cameraConfigs.remainingPasses;
          }
        }, {
          key: "windowResize",
          value: function windowResize(ppl, pplConfigs, cameraConfigs, window, camera, nativeWidth, nativeHeight) {
            var ResourceFlags = rendering.ResourceFlags;
            var ResourceResidency = rendering.ResourceResidency;
            var id = window.renderWindowId;
            var settings = cameraConfigs.settings;
            var width = cameraConfigs.enableShadingScale ? Math.max(Math.floor(nativeWidth * cameraConfigs.shadingScale), 1) : nativeWidth;
            var height = cameraConfigs.enableShadingScale ? Math.max(Math.floor(nativeHeight * cameraConfigs.shadingScale), 1) : nativeHeight;

            // MsaaRadiance
            if (cameraConfigs.enableMSAA) {
              // Notice: We never store multisample results.
              // These samples are always resolved and discarded at the end of the render pass.
              // So the ResourceResidency should be MEMORYLESS.
              if (cameraConfigs.enableHDR) {
                ppl.addTexture("MsaaRadiance".concat(id), TextureType.TEX2D, cameraConfigs.radianceFormat, width, height, 1, 1, 1, settings.msaa.sampleCount, ResourceFlags.COLOR_ATTACHMENT, ResourceResidency.MEMORYLESS);
              } else {
                ppl.addTexture("MsaaRadiance".concat(id), TextureType.TEX2D, Format.RGBA8, width, height, 1, 1, 1, settings.msaa.sampleCount, ResourceFlags.COLOR_ATTACHMENT, ResourceResidency.MEMORYLESS);
              }
              ppl.addTexture("MsaaDepthStencil".concat(id), TextureType.TEX2D, Format.DEPTH_STENCIL, width, height, 1, 1, 1, settings.msaa.sampleCount, ResourceFlags.DEPTH_STENCIL_ATTACHMENT, ResourceResidency.MEMORYLESS);
            }

            // Mainlight ShadowMap
            ppl.addRenderTarget("ShadowMap".concat(id), pplConfigs.shadowMapFormat, pplConfigs.shadowMapSize.x, pplConfigs.shadowMapSize.y);
            ppl.addDepthStencil("ShadowDepth".concat(id), Format.DEPTH_STENCIL, pplConfigs.shadowMapSize.x, pplConfigs.shadowMapSize.y);

            // Spot-light shadow maps
            if (cameraConfigs.enableSingleForwardPass) {
              var count = pplConfigs.mobileMaxSpotLightShadowMaps;
              for (var i = 0; i !== count; ++i) {
                ppl.addRenderTarget("SpotShadowMap".concat(i), pplConfigs.shadowMapFormat, pplConfigs.shadowMapSize.x, pplConfigs.shadowMapSize.y);
                ppl.addDepthStencil("SpotShadowDepth".concat(i), Format.DEPTH_STENCIL, pplConfigs.shadowMapSize.x, pplConfigs.shadowMapSize.y);
              }
            }
          }
        }, {
          key: "setup",
          value: function setup(ppl, pplConfigs, cameraConfigs, camera, context) {
            // Add global constants
            ppl.setVec4('g_platform', pplConfigs.platform);
            var id = camera.window.renderWindowId;
            var scene = camera.scene;
            var mainLight = scene.mainLight;
            --cameraConfigs.remainingPasses;
            assert(cameraConfigs.remainingPasses >= 0);

            // Forward Lighting (Light Culling)
            this.forwardLighting.cullLights(scene, camera.frustum);

            // Main Directional light CSM Shadow Map
            if (cameraConfigs.enableMainLightShadowMap) {
              assert(!!mainLight);
              this._addCascadedShadowMapPass(ppl, pplConfigs, id, mainLight, camera);
            }

            // Spot light shadow maps (Mobile or MSAA)
            if (cameraConfigs.enableSingleForwardPass) {
              // Currently, only support 1 spot light with shadow map on mobile platform.
              // TODO(zhouzhenglong): Relex this limitation.
              this.forwardLighting.addSpotlightShadowPasses(ppl, camera, pplConfigs.mobileMaxSpotLightShadowMaps);
            }
            this._tryAddReflectionProbePasses(ppl, cameraConfigs, id, mainLight, camera.scene, camera);
            if (cameraConfigs.remainingPasses > 0 || cameraConfigs.enableShadingScale) {
              context.colorName = cameraConfigs.enableShadingScale ? "ScaledRadiance0_".concat(id) : "Radiance0_".concat(id);
              context.depthStencilName = cameraConfigs.enableShadingScale ? "ScaledSceneDepth_".concat(id) : "SceneDepth_".concat(id);
            } else {
              context.colorName = cameraConfigs.colorName;
              context.depthStencilName = cameraConfigs.depthStencilName;
            }
            var pass = this._addForwardRadiancePasses(ppl, pplConfigs, cameraConfigs, id, camera, cameraConfigs.width, cameraConfigs.height, mainLight, context.colorName, context.depthStencilName, !cameraConfigs.enableMSAA, cameraConfigs.enableStoreSceneDepth ? StoreOp.STORE : StoreOp.DISCARD);
            if (!cameraConfigs.enableStoreSceneDepth) {
              context.depthStencilName = '';
            }
            if (cameraConfigs.remainingPasses === 0 && cameraConfigs.enableShadingScale) {
              return addCopyToScreenPass(ppl, pplConfigs, cameraConfigs, context.colorName);
            } else {
              return pass;
            }
          }
        }, {
          key: "_addCascadedShadowMapPass",
          value: function _addCascadedShadowMapPass(ppl, pplConfigs, id, light, camera) {
            var QueueHint = rendering.QueueHint;
            var SceneFlags = rendering.SceneFlags;
            // ----------------------------------------------------------------
            // Dynamic states
            // ----------------------------------------------------------------
            var shadowSize = ppl.pipelineSceneData.shadows.size;
            var width = shadowSize.x;
            var height = shadowSize.y;
            var viewport = this._viewport;
            viewport.left = viewport.top = 0;
            viewport.width = width;
            viewport.height = height;

            // ----------------------------------------------------------------
            // CSM Shadow Map
            // ----------------------------------------------------------------
            var pass = ppl.addRenderPass(width, height, 'default');
            pass.name = 'CascadedShadowMap';
            pass.addRenderTarget("ShadowMap".concat(id), LoadOp.CLEAR, StoreOp.STORE, new Color(1, 1, 1, 1));
            pass.addDepthStencil("ShadowDepth".concat(id), LoadOp.CLEAR, StoreOp.DISCARD);
            var csmLevel = ppl.pipelineSceneData.csmSupported ? light.csmLevel : 1;

            // Add shadow map viewports
            for (var level = 0; level !== csmLevel; ++level) {
              getCsmMainLightViewport(light, width, height, level, this._viewport, pplConfigs.screenSpaceSignY);
              var queue = pass.addQueue(QueueHint.NONE, 'shadow-caster');
              if (!pplConfigs.isWebGPU) {
                // Temporary workaround for WebGPU
                queue.setViewport(this._viewport);
              }
              queue.addScene(camera, SceneFlags.OPAQUE | SceneFlags.MASK | SceneFlags.SHADOW_CASTER).useLightFrustum(light, level);
            }
          }
        }, {
          key: "_tryAddReflectionProbePasses",
          value: function _tryAddReflectionProbePasses(ppl, cameraConfigs, id, mainLight, scene, sourceCamera) {
            var reflectionProbeManager = cclegacy.internal.reflectionProbeManager;
            if (!reflectionProbeManager) {
              return;
            }
            var ResourceResidency = rendering.ResourceResidency;
            var probes = reflectionProbeManager.getProbes();
            var maxProbeCount = 4;
            var probeID = 0;
            var _iterator9 = _createForOfIteratorHelper(probes),
              _step9;
            try {
              for (_iterator9.s(); !(_step9 = _iterator9.n()).done;) {
                var probe = _step9.value;
                if (!probe.needRender) {
                  continue;
                }
                var area = probe.renderArea();
                var width = Math.max(Math.floor(area.x), 1);
                var height = Math.max(Math.floor(area.y), 1);
                if (probe.probeType === renderer.scene.ProbeType.PLANAR) {
                  if (!cameraConfigs.enablePlanarReflectionProbe) {
                    continue;
                  }
                  var reflectionCamera = probe.camera;
                  if (EDITOR && sourceCamera.cameraUsage === CameraUsage.PREVIEW) ;
                  var window = probe.realtimePlanarTexture.window;
                  var colorName = "PlanarProbeRT".concat(probeID);
                  var depthStencilName = "PlanarProbeDS".concat(probeID);
                  // ProbeResource
                  ppl.addRenderWindow(colorName, cameraConfigs.radianceFormat, width, height, window);
                  ppl.addDepthStencil(depthStencilName, gfx.Format.DEPTH_STENCIL, width, height, ResourceResidency.MEMORYLESS);

                  // Rendering
                  var probePass = ppl.addRenderPass(width, height, 'default');
                  probePass.name = "PlanarReflectionProbe".concat(probeID);
                  this._buildReflectionProbePass(probePass, cameraConfigs, id, reflectionCamera, probe, colorName, depthStencilName, mainLight);
                } else {
                  var faceIdx, _window, _colorName, _depthStencilName, _probePass;
                  if (EDITOR) ;
                }
                ++probeID;
                if (probeID === maxProbeCount) {
                  break;
                }
              }
            } catch (err) {
              _iterator9.e(err);
            } finally {
              _iterator9.f();
            }
          }
        }, {
          key: "_buildReflectionProbePass",
          value: function _buildReflectionProbePass(pass, cameraConfigs, id, camera, probe, colorName, depthStencilName, mainLight) {
            var QueueHint = rendering.QueueHint;
            var SceneFlags = rendering.SceneFlags;
            // set viewport
            var colorStoreOp = cameraConfigs.enableMSAA ? StoreOp.DISCARD : StoreOp.STORE;

            // bind output render target
            if (forwardNeedClearColor(camera)) {
              this._reflectionProbeClearColor.x = camera.clearColor.x;
              this._reflectionProbeClearColor.y = camera.clearColor.y;
              this._reflectionProbeClearColor.z = camera.clearColor.z;
              var clearColor = rendering.packRGBE(this._reflectionProbeClearColor);
              this._clearColor.x = clearColor.x;
              this._clearColor.y = clearColor.y;
              this._clearColor.z = clearColor.z;
              this._clearColor.w = clearColor.w;
              pass.addRenderTarget(colorName, LoadOp.CLEAR, colorStoreOp, this._clearColor);
            } else {
              pass.addRenderTarget(colorName, LoadOp.LOAD, colorStoreOp);
            }

            // bind depth stencil buffer
            if (camera.clearFlag & ClearFlagBit.DEPTH_STENCIL) {
              pass.addDepthStencil(depthStencilName, LoadOp.CLEAR, StoreOp.DISCARD, camera.clearDepth, camera.clearStencil, camera.clearFlag & ClearFlagBit.DEPTH_STENCIL);
            } else {
              pass.addDepthStencil(depthStencilName, LoadOp.LOAD, StoreOp.DISCARD);
            }

            // Set shadow map if enabled
            if (cameraConfigs.enableMainLightShadowMap) {
              pass.addTexture("ShadowMap".concat(id), 'cc_shadowMap');
            }

            // TODO(zhouzhenglong): Separate OPAQUE and MASK queue

            // add opaque and mask queue
            var lightInfo = new rendering.LightInfo(mainLight || null, 0, false, probe);
            pass.addQueue(QueueHint.NONE, 'reflect-map') // Currently we put OPAQUE and MASK into one queue, so QueueHint is NONE
            .addSceneOfCamera(camera, lightInfo, SceneFlags.OPAQUE | SceneFlags.MASK | SceneFlags.REFLECTION_PROBE);
          }
        }, {
          key: "_addForwardRadiancePasses",
          value: function _addForwardRadiancePasses(ppl, pplConfigs, cameraConfigs, id, camera, width, height, mainLight, colorName, depthStencilName) {
            var disableMSAA = arguments.length > 10 && arguments[10] !== undefined ? arguments[10] : false;
            var depthStencilStoreOp = arguments.length > 11 && arguments[11] !== undefined ? arguments[11] : StoreOp.DISCARD;
            var QueueHint = rendering.QueueHint;
            var SceneFlags = rendering.SceneFlags;
            // ----------------------------------------------------------------
            // Dynamic states
            // ----------------------------------------------------------------
            // Prepare camera clear color
            var clearColor = camera.clearColor; // Reduce C++/TS interop
            this._clearColor.x = clearColor.x;
            this._clearColor.y = clearColor.y;
            this._clearColor.z = clearColor.z;
            this._clearColor.w = clearColor.w;

            // Prepare camera viewport
            var viewport = camera.viewport; // Reduce C++/TS interop
            this._viewport.left = Math.round(viewport.x * width);
            this._viewport.top = Math.round(viewport.y * height);
            // Here we must use camera.viewport.width instead of camera.viewport.z, which
            // is undefined on native platform. The same as camera.viewport.height.
            this._viewport.width = Math.max(Math.round(viewport.width * width), 1);
            this._viewport.height = Math.max(Math.round(viewport.height * height), 1);

            // MSAA
            var enableMSAA = !disableMSAA && cameraConfigs.enableMSAA;
            assert(!enableMSAA || cameraConfigs.enableSingleForwardPass);

            // ----------------------------------------------------------------
            // Forward Lighting (Main Directional Light)
            // ----------------------------------------------------------------
            var pass = cameraConfigs.enableSingleForwardPass ? this._addForwardSingleRadiancePass(ppl, pplConfigs, cameraConfigs, id, camera, enableMSAA, width, height, mainLight, colorName, depthStencilName, depthStencilStoreOp) : this._addForwardMultipleRadiancePasses(ppl, cameraConfigs, id, camera, width, height, mainLight, colorName, depthStencilName, depthStencilStoreOp);

            // Planar Shadow
            if (cameraConfigs.enableMainLightPlanarShadowMap) {
              this._addPlanarShadowQueue(camera, mainLight, pass);
            }

            // ----------------------------------------------------------------
            // Forward Lighting (Blend)
            // ----------------------------------------------------------------
            // Add transparent queue

            var sceneFlags = SceneFlags.BLEND | (camera.geometryRenderer ? SceneFlags.GEOMETRY : SceneFlags.NONE);
            pass.addQueue(QueueHint.BLEND).addScene(camera, sceneFlags, mainLight || undefined);
            return pass;
          }
        }, {
          key: "_addForwardSingleRadiancePass",
          value: function _addForwardSingleRadiancePass(ppl, pplConfigs, cameraConfigs, id, camera, enableMSAA, width, height, mainLight, colorName, depthStencilName, depthStencilStoreOp) {
            assert(cameraConfigs.enableSingleForwardPass);
            // ----------------------------------------------------------------
            // Forward Lighting (Main Directional Light)
            // ----------------------------------------------------------------
            var pass;
            if (enableMSAA) {
              var msaaRadianceName = "MsaaRadiance".concat(id);
              var msaaDepthStencilName = "MsaaDepthStencil".concat(id);
              var sampleCount = cameraConfigs.settings.msaa.sampleCount;
              var msPass = ppl.addMultisampleRenderPass(width, height, sampleCount, 0, 'default');
              msPass.name = 'MsaaForwardPass';

              // MSAA always discards depth stencil
              this._buildForwardMainLightPass(msPass, cameraConfigs, id, camera, msaaRadianceName, msaaDepthStencilName, StoreOp.DISCARD, mainLight);
              msPass.resolveRenderTarget(msaaRadianceName, colorName);
              pass = msPass;
            } else {
              pass = ppl.addRenderPass(width, height, 'default');
              pass.name = 'ForwardPass';
              this._buildForwardMainLightPass(pass, cameraConfigs, id, camera, colorName, depthStencilName, depthStencilStoreOp, mainLight);
            }
            assert(pass !== undefined);

            // Forward Lighting (Additive Lights)
            this.forwardLighting.addLightQueues(pass, camera, pplConfigs.mobileMaxSpotLightShadowMaps);
            return pass;
          }
        }, {
          key: "_addForwardMultipleRadiancePasses",
          value: function _addForwardMultipleRadiancePasses(ppl, cameraConfigs, id, camera, width, height, mainLight, colorName, depthStencilName, depthStencilStoreOp) {
            assert(!cameraConfigs.enableSingleForwardPass);

            // Forward Lighting (Main Directional Light)
            var pass = ppl.addRenderPass(width, height, 'default');
            pass.name = 'ForwardPass';
            var firstStoreOp = this.forwardLighting.isMultipleLightPassesNeeded() ? StoreOp.STORE : depthStencilStoreOp;
            this._buildForwardMainLightPass(pass, cameraConfigs, id, camera, colorName, depthStencilName, firstStoreOp, mainLight);

            // Forward Lighting (Additive Lights)
            pass = this.forwardLighting.addLightPasses(colorName, depthStencilName, depthStencilStoreOp, id, width, height, camera, this._viewport, ppl, pass);
            return pass;
          }
        }, {
          key: "_buildForwardMainLightPass",
          value: function _buildForwardMainLightPass(pass, cameraConfigs, id, camera, colorName, depthStencilName, depthStencilStoreOp, mainLight) {
            var scene = arguments.length > 8 && arguments[8] !== undefined ? arguments[8] : null;
            var QueueHint = rendering.QueueHint;
            var SceneFlags = rendering.SceneFlags;
            // set viewport
            pass.setViewport(this._viewport);
            var colorStoreOp = cameraConfigs.enableMSAA ? StoreOp.DISCARD : StoreOp.STORE;

            // bind output render target
            if (forwardNeedClearColor(camera)) {
              pass.addRenderTarget(colorName, LoadOp.CLEAR, colorStoreOp, this._clearColor);
            } else {
              pass.addRenderTarget(colorName, LoadOp.LOAD, colorStoreOp);
            }

            // bind depth stencil buffer
            {
              if (colorName === cameraConfigs.colorName && depthStencilName !== cameraConfigs.depthStencilName) {
                warn('Default framebuffer cannot use custom depth stencil buffer');
              }
            }
            if (camera.clearFlag & ClearFlagBit.DEPTH_STENCIL) {
              pass.addDepthStencil(depthStencilName, LoadOp.CLEAR, depthStencilStoreOp, camera.clearDepth, camera.clearStencil, camera.clearFlag & ClearFlagBit.DEPTH_STENCIL);
            } else {
              pass.addDepthStencil(depthStencilName, LoadOp.LOAD, depthStencilStoreOp);
            }

            // Set shadow map if enabled
            if (cameraConfigs.enableMainLightShadowMap) {
              pass.addTexture("ShadowMap".concat(id), 'cc_shadowMap');
            }

            // TODO(zhouzhenglong): Separate OPAQUE and MASK queue

            // add opaque and mask queue
            pass.addQueue(QueueHint.NONE) // Currently we put OPAQUE and MASK into one queue, so QueueHint is NONE
            .addScene(camera, SceneFlags.OPAQUE | SceneFlags.MASK, mainLight || undefined, scene ? scene : undefined);
          }
        }, {
          key: "_addPlanarShadowQueue",
          value: function _addPlanarShadowQueue(camera, mainLight, pass) {
            var QueueHint = rendering.QueueHint;
            var SceneFlags = rendering.SceneFlags;
            pass.addQueue(QueueHint.BLEND, 'planar-shadow').addScene(camera, SceneFlags.SHADOW_CASTER | SceneFlags.PLANAR_SHADOW | SceneFlags.BLEND, mainLight || undefined);
          }
        }]);
      }());
      _defineProperty(BuiltinForwardPassBuilder, "ConfigOrder", 100);
      _defineProperty(BuiltinForwardPassBuilder, "RenderOrder", 100);
      function downSize(size, scale) {
        return Math.max(Math.floor(size * scale), 1);
      }
      var BuiltinBloomPassBuilder = exports('BuiltinBloomPassBuilder', /*#__PURE__*/function () {
        function BuiltinBloomPassBuilder() {
          _classCallCheck(this, BuiltinBloomPassBuilder);
          // Bloom
          _defineProperty(this, "_clearColorTransparentBlack", new Color(0, 0, 0, 0));
          _defineProperty(this, "_bloomParams", new Vec4(0, 0, 0, 0));
          _defineProperty(this, "_bloomTexSize", new Vec4(0, 0, 0, 0));
          _defineProperty(this, "_bloomWidths", []);
          _defineProperty(this, "_bloomHeights", []);
          _defineProperty(this, "_bloomTexNames", []);
          // Mipmap Bloom
          _defineProperty(this, "_bloomUpSampleTexDescs", []);
          _defineProperty(this, "_bloomDownSampleTexDescs", []);
          _defineProperty(this, "_prefilterTexDesc", {
            name: '',
            width: 0,
            height: 0
          });
          _defineProperty(this, "_originalColorDesc", {
            name: '',
            width: 0,
            height: 0
          });
        }
        return _createClass(BuiltinBloomPassBuilder, [{
          key: "getConfigOrder",
          value: function getConfigOrder() {
            return 0;
          }
        }, {
          key: "getRenderOrder",
          value: function getRenderOrder() {
            return 200;
          }
        }, {
          key: "configCamera",
          value: function configCamera(camera, pipelineConfigs, cameraConfigs) {
            var bloom = cameraConfigs.settings.bloom;
            var hasValidMaterial = bloom.type === BloomType.KawaseDualFilter && !!bloom.kawaseFilterMaterial || bloom.type === BloomType.MipmapFilter && !!bloom.mipmapFilterMaterial;
            cameraConfigs.enableBloom = bloom.enabled && hasValidMaterial;
            if (cameraConfigs.enableBloom) {
              ++cameraConfigs.remainingPasses;
            }
          }
        }, {
          key: "windowResize",
          value: function windowResize(ppl, pplConfigs, cameraConfigs, window) {
            if (!cameraConfigs.enableBloom) {
              return;
            }
            var width = cameraConfigs.width,
              height = cameraConfigs.height,
              bloom = cameraConfigs.settings.bloom;
            var id = window.renderWindowId;
            var format = cameraConfigs.radianceFormat;
            if (bloom.type === BloomType.KawaseDualFilter) {
              var bloomWidth = cameraConfigs.width;
              var bloomHeight = cameraConfigs.height;
              for (var i = 0; i !== bloom.iterations + 1; ++i) {
                bloomWidth = Math.max(Math.floor(bloomWidth / 2), 1);
                bloomHeight = Math.max(Math.floor(bloomHeight / 2), 1);
                ppl.addRenderTarget("BloomTex".concat(id, "_").concat(i), format, bloomWidth, bloomHeight);
              }
            } else if (bloom.type === BloomType.MipmapFilter) {
              var iterations = bloom.iterations;
              for (var _i = 0; _i !== iterations + 1; ++_i) {
                // DownSample
                if (_i < iterations) {
                  var scale = Math.pow(0.5, _i + 2);
                  this._bloomDownSampleTexDescs[_i] = this.createTexture(ppl, "DownSampleColor".concat(id).concat(_i), downSize(width, scale), downSize(height, scale), format);
                }
                // UpSample
                if (_i < iterations - 1) {
                  var _scale = Math.pow(0.5, iterations - _i - 1);
                  this._bloomUpSampleTexDescs[_i] = this.createTexture(ppl, "UpSampleColor".concat(id).concat(_i), downSize(width, _scale), downSize(height, _scale), format);
                }
              }
              this._originalColorDesc = this.createTexture(ppl, "OriginalColor".concat(id), width, height, format);
              this._prefilterTexDesc = this.createTexture(ppl, "PrefilterColor".concat(id), downSize(width, 0.5), downSize(height, 0.5), format);
            }
          }
        }, {
          key: "createTexture",
          value: function createTexture(ppl, name, width, height, format) {
            var desc = {
              name: name,
              width: width,
              height: height
            };
            ppl.addRenderTarget(desc.name, format, desc.width, desc.height);
            return desc;
          }
        }, {
          key: "setup",
          value: function setup(ppl, pplConfigs, cameraConfigs, camera, context, prevRenderPass) {
            if (!cameraConfigs.enableBloom) {
              return prevRenderPass;
            }
            --cameraConfigs.remainingPasses;
            assert(cameraConfigs.remainingPasses >= 0);
            var bloom = cameraConfigs.settings.bloom;
            var id = camera.window.renderWindowId;
            switch (bloom.type) {
              case BloomType.KawaseDualFilter:
                {
                  var material = bloom.kawaseFilterMaterial;
                  assert(!!material);
                  return this._addKawaseDualFilterBloomPasses(ppl, pplConfigs, cameraConfigs, cameraConfigs.settings, material, id, cameraConfigs.width, cameraConfigs.height, context.colorName);
                }
              case BloomType.MipmapFilter:
                {
                  var _material = bloom.mipmapFilterMaterial;
                  assert(!!_material);
                  return this._addMipmapFilterBloomPasses(ppl, pplConfigs, cameraConfigs, cameraConfigs.settings, _material, id, cameraConfigs.width, cameraConfigs.height, context.colorName);
                }
              default:
                return prevRenderPass;
            }
          }
        }, {
          key: "_addKawaseDualFilterBloomPasses",
          value: function _addKawaseDualFilterBloomPasses(ppl, pplConfigs, cameraConfigs, settings, bloomMaterial, id, width, height, radianceName) {
            var QueueHint = rendering.QueueHint;
            // Based on Kawase Dual Filter Blur. Saves bandwidth on mobile devices.
            // eslint-disable-next-line max-len
            // https://community.arm.com/cfs-file/__key/communityserver-blogs-components-weblogfiles/00-00-00-20-66/siggraph2015_2D00_mmg_2D00_marius_2D00_slides.pdf

            // Size: [prefilter(1/2), downsample(1/4), downsample(1/8), downsample(1/16), ...]
            var iterations = settings.bloom.iterations;
            var sizeCount = iterations + 1;
            this._bloomWidths.length = sizeCount;
            this._bloomHeights.length = sizeCount;
            this._bloomWidths[0] = Math.max(Math.floor(width / 2), 1);
            this._bloomHeights[0] = Math.max(Math.floor(height / 2), 1);
            for (var i = 1; i !== sizeCount; ++i) {
              this._bloomWidths[i] = Math.max(Math.floor(this._bloomWidths[i - 1] / 2), 1);
              this._bloomHeights[i] = Math.max(Math.floor(this._bloomHeights[i - 1] / 2), 1);
            }

            // Bloom texture names
            this._bloomTexNames.length = sizeCount;
            for (var _i2 = 0; _i2 !== sizeCount; ++_i2) {
              this._bloomTexNames[_i2] = "BloomTex".concat(id, "_").concat(_i2);
            }

            // Setup bloom parameters
            this._bloomParams.x = pplConfigs.useFloatOutput ? 1 : 0;
            this._bloomParams.y = 0; // unused
            this._bloomParams.z = settings.bloom.threshold;
            this._bloomParams.w = settings.bloom.enableAlphaMask ? 1 : 0;

            // Prefilter pass
            var prefilterPass = ppl.addRenderPass(this._bloomWidths[0], this._bloomHeights[0], 'cc-bloom-prefilter');
            prefilterPass.addRenderTarget(this._bloomTexNames[0], LoadOp.CLEAR, StoreOp.STORE, this._clearColorTransparentBlack);
            prefilterPass.addTexture(radianceName, 'inputTexture');
            prefilterPass.setVec4('bloomParams', this._bloomParams);
            prefilterPass.addQueue(QueueHint.OPAQUE).addFullscreenQuad(bloomMaterial, 0);

            // Downsample passes
            for (var _i3 = 1; _i3 !== sizeCount; ++_i3) {
              var downPass = ppl.addRenderPass(this._bloomWidths[_i3], this._bloomHeights[_i3], 'cc-bloom-downsample');
              downPass.addRenderTarget(this._bloomTexNames[_i3], LoadOp.CLEAR, StoreOp.STORE, this._clearColorTransparentBlack);
              downPass.addTexture(this._bloomTexNames[_i3 - 1], 'bloomTexture');
              this._bloomTexSize.x = this._bloomWidths[_i3 - 1];
              this._bloomTexSize.y = this._bloomHeights[_i3 - 1];
              downPass.setVec4('bloomTexSize', this._bloomTexSize);
              downPass.addQueue(QueueHint.OPAQUE).addFullscreenQuad(bloomMaterial, 1);
            }

            // Upsample passes
            for (var _i4 = iterations; _i4-- > 0;) {
              var upPass = ppl.addRenderPass(this._bloomWidths[_i4], this._bloomHeights[_i4], 'cc-bloom-upsample');
              upPass.addRenderTarget(this._bloomTexNames[_i4], LoadOp.CLEAR, StoreOp.STORE, this._clearColorTransparentBlack);
              upPass.addTexture(this._bloomTexNames[_i4 + 1], 'bloomTexture');
              this._bloomTexSize.x = this._bloomWidths[_i4 + 1];
              this._bloomTexSize.y = this._bloomHeights[_i4 + 1];
              upPass.setVec4('bloomTexSize', this._bloomTexSize);
              upPass.addQueue(QueueHint.OPAQUE).addFullscreenQuad(bloomMaterial, 2);
            }

            // Combine pass
            this._bloomParams.w = settings.bloom.intensity;
            var combinePass = ppl.addRenderPass(width, height, 'cc-bloom-combine');
            combinePass.addRenderTarget(radianceName, LoadOp.LOAD, StoreOp.STORE);
            combinePass.addTexture(this._bloomTexNames[0], 'bloomTexture');
            combinePass.setVec4('bloomParams', this._bloomParams);
            combinePass.addQueue(QueueHint.BLEND).addFullscreenQuad(bloomMaterial, 3);
            if (cameraConfigs.remainingPasses === 0) {
              return addCopyToScreenPass(ppl, pplConfigs, cameraConfigs, radianceName);
            } else {
              return combinePass;
            }
          }
        }, {
          key: "_addPass",
          value: function _addPass(ppl, width, height, layout, colorName, material, passIndex) {
            var loadOp = arguments.length > 7 && arguments[7] !== undefined ? arguments[7] : LoadOp.CLEAR;
            var clearColor = arguments.length > 8 && arguments[8] !== undefined ? arguments[8] : sClearColorTransparentBlack;
            var queueHint = arguments.length > 9 && arguments[9] !== undefined ? arguments[9] : rendering.QueueHint.OPAQUE;
            var pass = ppl.addRenderPass(width, height, layout);
            pass.addRenderTarget(colorName, loadOp, StoreOp.STORE, clearColor);
            pass.addQueue(queueHint).addFullscreenQuad(material, passIndex);
            return pass;
          }
        }, {
          key: "_addMipmapFilterBloomPasses",
          value: function _addMipmapFilterBloomPasses(ppl, pplConfigs, cameraConfigs, settings, bloomMaterial, id, width, height, radianceName) {
            // Setup bloom parameters
            this._bloomParams.x = pplConfigs.useFloatOutput ? 1 : 0;
            this._bloomParams.x = 0; // unused
            this._bloomParams.z = settings.bloom.threshold;
            this._bloomParams.w = settings.bloom.intensity;
            var prefilterInfo = this._prefilterTexDesc;

            // Prefilter pass
            var currSamplePass = this._addPass(ppl, prefilterInfo.width, prefilterInfo.height, 'cc-bloom-mipmap-prefilter', prefilterInfo.name, bloomMaterial, 0);
            currSamplePass.addTexture(radianceName, 'mainTexture');
            currSamplePass.setVec4('bloomParams', this._bloomParams);
            var downSampleInfos = this._bloomDownSampleTexDescs;
            // Downsample passes
            for (var i = 0; i < downSampleInfos.length; ++i) {
              var currInfo = downSampleInfos[i];
              var samplerSrc = i === 0 ? prefilterInfo : downSampleInfos[i - 1];
              var samplerSrcName = samplerSrc.name;
              this._bloomTexSize.x = 1 / samplerSrc.width;
              this._bloomTexSize.y = 1 / samplerSrc.height;
              currSamplePass = this._addPass(ppl, currInfo.width, currInfo.height, 'cc-bloom-mipmap-downsample', currInfo.name, bloomMaterial, 1);
              currSamplePass.addTexture(samplerSrcName, 'mainTexture');
              currSamplePass.setVec4('bloomParams', this._bloomTexSize);
            }
            var lastIndex = downSampleInfos.length - 1;
            var upSampleInfos = this._bloomUpSampleTexDescs;
            // Upsample passes
            for (var _i5 = 0; _i5 < upSampleInfos.length; _i5++) {
              var _currInfo = upSampleInfos[_i5];
              var sampleSrc = _i5 === 0 ? downSampleInfos[lastIndex] : upSampleInfos[_i5 - 1];
              var sampleSrcName = sampleSrc.name;
              this._bloomTexSize.x = 1 / sampleSrc.width;
              this._bloomTexSize.y = 1 / sampleSrc.height;
              currSamplePass = this._addPass(ppl, _currInfo.width, _currInfo.height, 'cc-bloom-mipmap-upsample', _currInfo.name, bloomMaterial, 2);
              currSamplePass.addTexture(sampleSrcName, 'mainTexture');
              currSamplePass.addTexture(downSampleInfos[lastIndex - 1 - _i5].name, 'downsampleTexture');
              currSamplePass.setVec4('bloomParams', this._bloomTexSize);
            }

            // Combine pass
            var combinePass = this._addPass(ppl, width, height, 'cc-bloom-mipmap-combine', radianceName, bloomMaterial, 3, LoadOp.LOAD);
            combinePass.addTexture(upSampleInfos[upSampleInfos.length - 1].name, 'bloomTexture');
            combinePass.setVec4('bloomParams', this._bloomParams);
            if (cameraConfigs.remainingPasses === 0) {
              return addCopyToScreenPass(ppl, pplConfigs, cameraConfigs, radianceName);
            } else {
              return combinePass;
            }
          }
        }]);
      }());
      var BuiltinToneMappingPassBuilder = exports('BuiltinToneMappingPassBuilder', /*#__PURE__*/function () {
        function BuiltinToneMappingPassBuilder() {
          _classCallCheck(this, BuiltinToneMappingPassBuilder);
          _defineProperty(this, "_colorGradingTexSize", new Vec2(0, 0));
        }
        return _createClass(BuiltinToneMappingPassBuilder, [{
          key: "getConfigOrder",
          value: function getConfigOrder() {
            return 0;
          }
        }, {
          key: "getRenderOrder",
          value: function getRenderOrder() {
            return 300;
          }
        }, {
          key: "configCamera",
          value: function configCamera(camera, pplConfigs, cameraConfigs) {
            var settings = cameraConfigs.settings;
            cameraConfigs.enableColorGrading = settings.colorGrading.enabled && !!settings.colorGrading.material && !!settings.colorGrading.colorGradingMap;
            cameraConfigs.enableToneMapping = cameraConfigs.enableHDR // From Half to RGBA8
            || cameraConfigs.enableColorGrading; // Color grading

            if (cameraConfigs.enableToneMapping) {
              ++cameraConfigs.remainingPasses;
            }
          }
        }, {
          key: "windowResize",
          value: function windowResize(ppl, pplConfigs, cameraConfigs) {
            if (cameraConfigs.enableColorGrading) {
              assert(!!cameraConfigs.settings.colorGrading.material);
              cameraConfigs.settings.colorGrading.material.setProperty('colorGradingMap', cameraConfigs.settings.colorGrading.colorGradingMap);
            }
          }
        }, {
          key: "setup",
          value: function setup(ppl, pplConfigs, cameraConfigs, camera, context, prevRenderPass) {
            if (!cameraConfigs.enableToneMapping) {
              return prevRenderPass;
            }
            --cameraConfigs.remainingPasses;
            assert(cameraConfigs.remainingPasses >= 0);
            if (cameraConfigs.remainingPasses === 0) {
              return this._addCopyAndTonemapPass(ppl, pplConfigs, cameraConfigs, cameraConfigs.width, cameraConfigs.height, context.colorName, cameraConfigs.colorName);
            } else {
              var id = cameraConfigs.renderWindowId;
              var ldrColorPrefix = cameraConfigs.enableShadingScale ? "ScaledLdrColor" : "LdrColor";
              var ldrColorName = getPingPongRenderTarget(context.colorName, ldrColorPrefix, id);
              var radianceName = context.colorName;
              context.colorName = ldrColorName;
              return this._addCopyAndTonemapPass(ppl, pplConfigs, cameraConfigs, cameraConfigs.width, cameraConfigs.height, radianceName, ldrColorName);
            }
          }
        }, {
          key: "_addCopyAndTonemapPass",
          value: function _addCopyAndTonemapPass(ppl, pplConfigs, cameraConfigs, width, height, radianceName, colorName) {
            var pass;
            var settings = cameraConfigs.settings;
            if (cameraConfigs.enableColorGrading) {
              assert(!!settings.colorGrading.material);
              assert(!!settings.colorGrading.colorGradingMap);
              var lutTex = settings.colorGrading.colorGradingMap;
              this._colorGradingTexSize.x = lutTex.width;
              this._colorGradingTexSize.y = lutTex.height;
              var isSquareMap = lutTex.width === lutTex.height;
              if (isSquareMap) {
                pass = ppl.addRenderPass(width, height, 'cc-color-grading-8x8');
              } else {
                pass = ppl.addRenderPass(width, height, 'cc-color-grading-nx1');
              }
              pass.addRenderTarget(colorName, LoadOp.CLEAR, StoreOp.STORE, sClearColorTransparentBlack);
              pass.addTexture(radianceName, 'sceneColorMap');
              pass.setVec2('lutTextureSize', this._colorGradingTexSize);
              pass.setFloat('contribute', settings.colorGrading.contribute);
              pass.addQueue(rendering.QueueHint.OPAQUE).addFullscreenQuad(settings.colorGrading.material, isSquareMap ? 1 : 0);
            } else {
              pass = ppl.addRenderPass(width, height, 'cc-tone-mapping');
              pass.addRenderTarget(colorName, LoadOp.CLEAR, StoreOp.STORE, sClearColorTransparentBlack);
              pass.addTexture(radianceName, 'inputTexture');
              if (settings.toneMapping.material) {
                pass.addQueue(rendering.QueueHint.OPAQUE).addFullscreenQuad(settings.toneMapping.material, 0);
              } else {
                assert(!!cameraConfigs.copyAndTonemapMaterial);
                pass.addQueue(rendering.QueueHint.OPAQUE).addFullscreenQuad(cameraConfigs.copyAndTonemapMaterial, 0);
              }
            }
            return pass;
          }
        }]);
      }());
      var BuiltinFXAAPassBuilder = exports('BuiltinFXAAPassBuilder', /*#__PURE__*/function () {
        function BuiltinFXAAPassBuilder() {
          _classCallCheck(this, BuiltinFXAAPassBuilder);
          // FXAA
          _defineProperty(this, "_fxaaParams", new Vec4(0, 0, 0, 0));
        }
        return _createClass(BuiltinFXAAPassBuilder, [{
          key: "getConfigOrder",
          value: function getConfigOrder() {
            return 0;
          }
        }, {
          key: "getRenderOrder",
          value: function getRenderOrder() {
            return 400;
          }
        }, {
          key: "configCamera",
          value: function configCamera(camera, pplConfigs, cameraConfigs) {
            cameraConfigs.enableFXAA = cameraConfigs.settings.fxaa.enabled && !!cameraConfigs.settings.fxaa.material;
            if (cameraConfigs.enableFXAA) {
              ++cameraConfigs.remainingPasses;
            }
          }
        }, {
          key: "setup",
          value: function setup(ppl, pplConfigs, cameraConfigs, camera, context, prevRenderPass) {
            if (!cameraConfigs.enableFXAA) {
              return prevRenderPass;
            }
            --cameraConfigs.remainingPasses;
            assert(cameraConfigs.remainingPasses >= 0);
            var id = cameraConfigs.renderWindowId;
            var ldrColorPrefix = cameraConfigs.enableShadingScale ? "ScaledLdrColor" : "LdrColor";
            var ldrColorName = getPingPongRenderTarget(context.colorName, ldrColorPrefix, id);
            assert(!!cameraConfigs.settings.fxaa.material);
            if (cameraConfigs.remainingPasses === 0) {
              if (cameraConfigs.enableShadingScale) {
                this._addFxaaPass(ppl, pplConfigs, cameraConfigs.settings.fxaa.material, cameraConfigs.width, cameraConfigs.height, context.colorName, ldrColorName);
                return addCopyToScreenPass(ppl, pplConfigs, cameraConfigs, ldrColorName);
              } else {
                assert(cameraConfigs.width === cameraConfigs.nativeWidth);
                assert(cameraConfigs.height === cameraConfigs.nativeHeight);
                return this._addFxaaPass(ppl, pplConfigs, cameraConfigs.settings.fxaa.material, cameraConfigs.width, cameraConfigs.height, context.colorName, cameraConfigs.colorName);
              }
            } else {
              var inputColorName = context.colorName;
              context.colorName = ldrColorName;
              var lastPass = this._addFxaaPass(ppl, pplConfigs, cameraConfigs.settings.fxaa.material, cameraConfigs.width, cameraConfigs.height, inputColorName, ldrColorName);
              return lastPass;
            }
          }
        }, {
          key: "_addFxaaPass",
          value: function _addFxaaPass(ppl, pplConfigs, fxaaMaterial, width, height, ldrColorName, colorName) {
            this._fxaaParams.x = width;
            this._fxaaParams.y = height;
            this._fxaaParams.z = 1 / width;
            this._fxaaParams.w = 1 / height;
            var pass = ppl.addRenderPass(width, height, 'cc-fxaa');
            pass.addRenderTarget(colorName, LoadOp.CLEAR, StoreOp.STORE, sClearColorTransparentBlack);
            pass.addTexture(ldrColorName, 'sceneColorMap');
            pass.setVec4('texSize', this._fxaaParams);
            pass.addQueue(rendering.QueueHint.OPAQUE).addFullscreenQuad(fxaaMaterial, 0);
            return pass;
          }
        }]);
      }());
      var BuiltinFsrPassBuilder = exports('BuiltinFsrPassBuilder', /*#__PURE__*/function () {
        function BuiltinFsrPassBuilder() {
          _classCallCheck(this, BuiltinFsrPassBuilder);
          // FSR
          _defineProperty(this, "_fsrParams", new Vec4(0, 0, 0, 0));
          _defineProperty(this, "_fsrTexSize", new Vec4(0, 0, 0, 0));
        }
        return _createClass(BuiltinFsrPassBuilder, [{
          key: "getConfigOrder",
          value: function getConfigOrder() {
            return 0;
          }
        }, {
          key: "getRenderOrder",
          value: function getRenderOrder() {
            return 500;
          }
        }, {
          key: "configCamera",
          value: function configCamera(camera, pplConfigs, cameraConfigs) {
            // FSR (Depend on Shading scale)
            cameraConfigs.enableFSR = cameraConfigs.settings.fsr.enabled && !!cameraConfigs.settings.fsr.material && cameraConfigs.enableShadingScale && cameraConfigs.shadingScale < 1.0;
            if (cameraConfigs.enableFSR) {
              ++cameraConfigs.remainingPasses;
            }
          }
        }, {
          key: "setup",
          value: function setup(ppl, pplConfigs, cameraConfigs, camera, context, prevRenderPass) {
            if (!cameraConfigs.enableFSR) {
              return prevRenderPass;
            }
            --cameraConfigs.remainingPasses;
            var inputColorName = context.colorName;
            var outputColorName = cameraConfigs.remainingPasses === 0 ? cameraConfigs.colorName : getPingPongRenderTarget(context.colorName, 'UiColor', cameraConfigs.renderWindowId);
            context.colorName = outputColorName;
            assert(!!cameraConfigs.settings.fsr.material);
            return this._addFsrPass(ppl, pplConfigs, cameraConfigs, cameraConfigs.settings, cameraConfigs.settings.fsr.material, cameraConfigs.renderWindowId, cameraConfigs.width, cameraConfigs.height, inputColorName, cameraConfigs.nativeWidth, cameraConfigs.nativeHeight, outputColorName);
          }
        }, {
          key: "_addFsrPass",
          value: function _addFsrPass(ppl, pplConfigs, cameraConfigs, settings, fsrMaterial, id, width, height, inputColorName, nativeWidth, nativeHeight, outputColorName) {
            this._fsrTexSize.x = width;
            this._fsrTexSize.y = height;
            this._fsrTexSize.z = nativeWidth;
            this._fsrTexSize.w = nativeHeight;
            this._fsrParams.x = clamp(1.0 - settings.fsr.sharpness, 0.02, 0.98);
            var uiColorPrefix = 'UiColor';
            var fsrColorName = getPingPongRenderTarget(outputColorName, uiColorPrefix, id);
            var easuPass = ppl.addRenderPass(nativeWidth, nativeHeight, 'cc-fsr-easu');
            easuPass.addRenderTarget(fsrColorName, LoadOp.CLEAR, StoreOp.STORE, sClearColorTransparentBlack);
            easuPass.addTexture(inputColorName, 'outputResultMap');
            easuPass.setVec4('fsrTexSize', this._fsrTexSize);
            easuPass.addQueue(rendering.QueueHint.OPAQUE).addFullscreenQuad(fsrMaterial, 0);
            var rcasPass = ppl.addRenderPass(nativeWidth, nativeHeight, 'cc-fsr-rcas');
            rcasPass.addRenderTarget(outputColorName, LoadOp.CLEAR, StoreOp.STORE, sClearColorTransparentBlack);
            rcasPass.addTexture(fsrColorName, 'outputResultMap');
            rcasPass.setVec4('fsrTexSize', this._fsrTexSize);
            rcasPass.setVec4('fsrParams', this._fsrParams);
            rcasPass.addQueue(rendering.QueueHint.OPAQUE).addFullscreenQuad(fsrMaterial, 1);
            return rcasPass;
          }
        }]);
      }());
      var BuiltinUiPassBuilder = exports('BuiltinUiPassBuilder', /*#__PURE__*/function () {
        function BuiltinUiPassBuilder() {
          _classCallCheck(this, BuiltinUiPassBuilder);
        }
        return _createClass(BuiltinUiPassBuilder, [{
          key: "getConfigOrder",
          value: function getConfigOrder() {
            return 0;
          }
        }, {
          key: "getRenderOrder",
          value: function getRenderOrder() {
            return 1000;
          }
        }, {
          key: "setup",
          value: function setup(ppl, pplConfigs, cameraConfigs, camera, context, prevRenderPass) {
            assert(!!prevRenderPass);
            var flags = rendering.SceneFlags.UI;
            if (cameraConfigs.enableProfiler) {
              flags |= rendering.SceneFlags.PROFILER;
              prevRenderPass.showStatistics = true;
            }
            prevRenderPass.addQueue(rendering.QueueHint.BLEND, 'default', 'default').addScene(camera, flags);
            return prevRenderPass;
          }
        }]);
      }());
      if (rendering) {
        var QueueHint = rendering.QueueHint,
          SceneFlags = rendering.SceneFlags;
        var BuiltinPipelineBuilder = /*#__PURE__*/function () {
          function BuiltinPipelineBuilder() {
            _classCallCheck(this, BuiltinPipelineBuilder);
            _defineProperty(this, "_pipelineEvent", cclegacy.director.root.pipelineEvent);
            _defineProperty(this, "_forwardPass", new BuiltinForwardPassBuilder());
            _defineProperty(this, "_bloomPass", new BuiltinBloomPassBuilder());
            _defineProperty(this, "_toneMappingPass", new BuiltinToneMappingPassBuilder());
            _defineProperty(this, "_fxaaPass", new BuiltinFXAAPassBuilder());
            _defineProperty(this, "_fsrPass", new BuiltinFsrPassBuilder());
            _defineProperty(this, "_uiPass", new BuiltinUiPassBuilder());
            // Internal cached resources
            _defineProperty(this, "_clearColor", new Color(0, 0, 0, 1));
            _defineProperty(this, "_viewport", new Viewport());
            _defineProperty(this, "_configs", new PipelineConfigs());
            _defineProperty(this, "_cameraConfigs", new CameraConfigs());
            // Materials
            _defineProperty(this, "_copyAndTonemapMaterial", new Material());
            // Internal States
            _defineProperty(this, "_initialized", false);
            // TODO(zhouzhenglong): Make default effect asset loading earlier and remove this flag
            _defineProperty(this, "_passBuilders", []);
          }
          return _createClass(BuiltinPipelineBuilder, [{
            key: "_setupPipelinePreview",
            value: function _setupPipelinePreview(camera, cameraConfigs) {
              var isEditorView = camera.cameraUsage === CameraUsage.SCENE_VIEW || camera.cameraUsage === CameraUsage.PREVIEW;
              if (isEditorView) {
                var editorSettings = rendering.getEditorPipelineSettings();
                if (editorSettings) {
                  cameraConfigs.settings = editorSettings;
                } else {
                  cameraConfigs.settings = defaultSettings;
                }
              } else {
                if (camera.pipelineSettings) {
                  cameraConfigs.settings = camera.pipelineSettings;
                } else {
                  cameraConfigs.settings = defaultSettings;
                }
              }
            }
          }, {
            key: "_preparePipelinePasses",
            value: function _preparePipelinePasses(cameraConfigs) {
              var passBuilders = this._passBuilders;
              passBuilders.length = 0;
              var settings = cameraConfigs.settings;
              if (settings._passes) {
                var _iterator0 = _createForOfIteratorHelper(settings._passes),
                  _step0;
                try {
                  for (_iterator0.s(); !(_step0 = _iterator0.n()).done;) {
                    var pass = _step0.value;
                    passBuilders.push(pass);
                  }
                } catch (err) {
                  _iterator0.e(err);
                } finally {
                  _iterator0.f();
                }
                assert(passBuilders.length === settings._passes.length);
              }
              passBuilders.push(this._forwardPass);
              if (settings.bloom.enabled) {
                passBuilders.push(this._bloomPass);
              }
              passBuilders.push(this._toneMappingPass);
              if (settings.fxaa.enabled) {
                passBuilders.push(this._fxaaPass);
              }
              if (settings.fsr.enabled) {
                passBuilders.push(this._fsrPass);
              }
              passBuilders.push(this._uiPass);
            }
          }, {
            key: "_setupBuiltinCameraConfigs",
            value: function _setupBuiltinCameraConfigs(ppl, camera, pipelineConfigs, cameraConfigs) {
              var window = camera.window;
              var isMainGameWindow = camera.cameraUsage === CameraUsage.GAME && !!window.swapchain;
              var isGameView = isMainGameWindow || camera.cameraUsage === CameraUsage.GAME_VIEW;

              // Window
              cameraConfigs.isMainGameWindow = isMainGameWindow;
              cameraConfigs.renderWindowId = window.renderWindowId;

              // Camera
              cameraConfigs.colorName = window.colorName;
              cameraConfigs.depthStencilName = window.depthStencilName;

              // Pipeline
              cameraConfigs.enableFullPipeline = (camera.visibility & Layers.Enum.DEFAULT) !== 0;
              cameraConfigs.enableProfiler = ppl.profiler && isGameView;
              cameraConfigs.remainingPasses = 0;

              // Shading scale
              cameraConfigs.shadingScale = cameraConfigs.settings.shadingScale;
              cameraConfigs.enableShadingScale = cameraConfigs.settings.enableShadingScale && cameraConfigs.shadingScale !== 1.0;
              cameraConfigs.nativeWidth = Math.max(Math.floor(window.width), 1);
              cameraConfigs.nativeHeight = Math.max(Math.floor(window.height), 1);
              cameraConfigs.width = cameraConfigs.enableShadingScale ? Math.max(Math.floor(cameraConfigs.nativeWidth * cameraConfigs.shadingScale), 1) : cameraConfigs.nativeWidth;
              cameraConfigs.height = cameraConfigs.enableShadingScale ? Math.max(Math.floor(cameraConfigs.nativeHeight * cameraConfigs.shadingScale), 1) : cameraConfigs.nativeHeight;

              // Radiance
              cameraConfigs.enableHDR = cameraConfigs.enableFullPipeline && pipelineConfigs.useFloatOutput;
              cameraConfigs.radianceFormat = cameraConfigs.enableHDR ? gfx.Format.RGBA16F : gfx.Format.RGBA8;

              // Tone Mapping
              cameraConfigs.copyAndTonemapMaterial = this._copyAndTonemapMaterial;

              // Depth
              cameraConfigs.enableStoreSceneDepth = false;
            }
          }, {
            key: "_setupCameraConfigs",
            value: function _setupCameraConfigs(ppl, camera, pipelineConfigs, cameraConfigs) {
              this._setupPipelinePreview(camera, cameraConfigs);
              this._preparePipelinePasses(cameraConfigs);
              sortPipelinePassBuildersByConfigOrder(this._passBuilders);
              this._setupBuiltinCameraConfigs(ppl, camera, pipelineConfigs, cameraConfigs);
              var _iterator1 = _createForOfIteratorHelper(this._passBuilders),
                _step1;
              try {
                for (_iterator1.s(); !(_step1 = _iterator1.n()).done;) {
                  var builder = _step1.value;
                  if (builder.configCamera) {
                    builder.configCamera(camera, pipelineConfigs, cameraConfigs);
                  }
                }
              } catch (err) {
                _iterator1.e(err);
              } finally {
                _iterator1.f();
              }
            }

            // ----------------------------------------------------------------
            // Interface
            // ----------------------------------------------------------------
          }, {
            key: "windowResize",
            value: function windowResize(ppl, window, camera, nativeWidth, nativeHeight) {
              setupPipelineConfigs(ppl, this._configs);
              this._setupCameraConfigs(ppl, camera, this._configs, this._cameraConfigs);

              // Render Window (UI)
              var id = window.renderWindowId;
              ppl.addRenderWindow(this._cameraConfigs.colorName, Format.RGBA8, nativeWidth, nativeHeight, window, this._cameraConfigs.depthStencilName);
              var width = this._cameraConfigs.width;
              var height = this._cameraConfigs.height;
              if (this._cameraConfigs.enableShadingScale) {
                ppl.addDepthStencil("ScaledSceneDepth_".concat(id), Format.DEPTH_STENCIL, width, height);
                ppl.addRenderTarget("ScaledRadiance0_".concat(id), this._cameraConfigs.radianceFormat, width, height);
                ppl.addRenderTarget("ScaledRadiance1_".concat(id), this._cameraConfigs.radianceFormat, width, height);
                ppl.addRenderTarget("ScaledLdrColor0_".concat(id), Format.RGBA8, width, height);
                ppl.addRenderTarget("ScaledLdrColor1_".concat(id), Format.RGBA8, width, height);
              } else {
                ppl.addDepthStencil("SceneDepth_".concat(id), Format.DEPTH_STENCIL, width, height);
                ppl.addRenderTarget("Radiance0_".concat(id), this._cameraConfigs.radianceFormat, width, height);
                ppl.addRenderTarget("Radiance1_".concat(id), this._cameraConfigs.radianceFormat, width, height);
                ppl.addRenderTarget("LdrColor0_".concat(id), Format.RGBA8, width, height);
                ppl.addRenderTarget("LdrColor1_".concat(id), Format.RGBA8, width, height);
              }
              ppl.addRenderTarget("UiColor0_".concat(id), Format.RGBA8, nativeWidth, nativeHeight);
              ppl.addRenderTarget("UiColor1_".concat(id), Format.RGBA8, nativeWidth, nativeHeight);
              var _iterator10 = _createForOfIteratorHelper(this._passBuilders),
                _step10;
              try {
                for (_iterator10.s(); !(_step10 = _iterator10.n()).done;) {
                  var builder = _step10.value;
                  if (builder.windowResize) {
                    builder.windowResize(ppl, this._configs, this._cameraConfigs, window, camera, nativeWidth, nativeHeight);
                  }
                }
              } catch (err) {
                _iterator10.e(err);
              } finally {
                _iterator10.f();
              }
            }
          }, {
            key: "setup",
            value: function setup(cameras, ppl) {
              // TODO(zhouzhenglong): Make default effect asset loading earlier and remove _initMaterials
              if (this._initMaterials(ppl)) {
                return;
              }

              // Render cameras
              // log(`==================== One Frame ====================`);
              var _iterator11 = _createForOfIteratorHelper(cameras),
                _step11;
              try {
                for (_iterator11.s(); !(_step11 = _iterator11.n()).done;) {
                  var camera = _step11.value;
                  // Skip invalid camera
                  if (!camera.scene || !camera.window) {
                    continue;
                  }
                  // Setup camera configs
                  this._setupCameraConfigs(ppl, camera, this._configs, this._cameraConfigs);
                  // log(`Setup camera: ${camera.node!.name}, window: ${camera.window.renderWindowId}, isFull: ${this._cameraConfigs.enableFullPipeline}, `
                  //     + `size: ${camera.window.width}x${camera.window.height}`);

                  this._pipelineEvent.emit(PipelineEventType.RENDER_CAMERA_BEGIN, camera);

                  // Build pipeline
                  if (this._cameraConfigs.enableFullPipeline) {
                    this._buildForwardPipeline(ppl, camera, camera.scene, this._passBuilders);
                  } else {
                    this._buildSimplePipeline(ppl, camera);
                  }
                  this._pipelineEvent.emit(PipelineEventType.RENDER_CAMERA_END, camera);
                }
              } catch (err) {
                _iterator11.e(err);
              } finally {
                _iterator11.f();
              }
            }
            // ----------------------------------------------------------------
            // Pipelines
            // ----------------------------------------------------------------
          }, {
            key: "_buildSimplePipeline",
            value: function _buildSimplePipeline(ppl, camera) {
              var width = Math.max(Math.floor(camera.window.width), 1);
              var height = Math.max(Math.floor(camera.window.height), 1);
              var colorName = this._cameraConfigs.colorName;
              var depthStencilName = this._cameraConfigs.depthStencilName;
              var viewport = camera.viewport; // Reduce C++/TS interop
              this._viewport.left = Math.round(viewport.x * width);
              this._viewport.top = Math.round(viewport.y * height);
              // Here we must use camera.viewport.width instead of camera.viewport.z, which
              // is undefined on native platform. The same as camera.viewport.height.
              this._viewport.width = Math.max(Math.round(viewport.width * width), 1);
              this._viewport.height = Math.max(Math.round(viewport.height * height), 1);
              var clearColor = camera.clearColor; // Reduce C++/TS interop
              this._clearColor.x = clearColor.x;
              this._clearColor.y = clearColor.y;
              this._clearColor.z = clearColor.z;
              this._clearColor.w = clearColor.w;
              var pass = ppl.addRenderPass(width, height, 'default');

              // bind output render target
              if (forwardNeedClearColor(camera)) {
                pass.addRenderTarget(colorName, LoadOp.CLEAR, StoreOp.STORE, this._clearColor);
              } else {
                pass.addRenderTarget(colorName, LoadOp.LOAD, StoreOp.STORE);
              }

              // bind depth stencil buffer
              if (camera.clearFlag & ClearFlagBit.DEPTH_STENCIL) {
                pass.addDepthStencil(depthStencilName, LoadOp.CLEAR, StoreOp.DISCARD, camera.clearDepth, camera.clearStencil, camera.clearFlag & ClearFlagBit.DEPTH_STENCIL);
              } else {
                pass.addDepthStencil(depthStencilName, LoadOp.LOAD, StoreOp.DISCARD);
              }
              pass.setViewport(this._viewport);

              // The opaque queue is used for Reflection probe preview
              pass.addQueue(QueueHint.OPAQUE).addScene(camera, SceneFlags.OPAQUE);

              // The blend queue is used for UI and Gizmos
              var flags = SceneFlags.BLEND | SceneFlags.UI;
              if (this._cameraConfigs.enableProfiler) {
                flags |= SceneFlags.PROFILER;
                pass.showStatistics = true;
              }
              pass.addQueue(QueueHint.BLEND).addScene(camera, flags);
            }
          }, {
            key: "_buildForwardPipeline",
            value: function _buildForwardPipeline(ppl, camera, scene, passBuilders) {
              sortPipelinePassBuildersByRenderOrder(passBuilders);
              var context = {
                colorName: '',
                depthStencilName: ''
              };
              var lastPass = undefined;
              var _iterator12 = _createForOfIteratorHelper(passBuilders),
                _step12;
              try {
                for (_iterator12.s(); !(_step12 = _iterator12.n()).done;) {
                  var builder = _step12.value;
                  if (builder.setup) {
                    lastPass = builder.setup(ppl, this._configs, this._cameraConfigs, camera, context, lastPass);
                  }
                }
              } catch (err) {
                _iterator12.e(err);
              } finally {
                _iterator12.f();
              }
              assert(this._cameraConfigs.remainingPasses === 0);
            }
          }, {
            key: "_initMaterials",
            value: function _initMaterials(ppl) {
              if (this._initialized) {
                return 0;
              }
              setupPipelineConfigs(ppl, this._configs);

              // When add new effect asset, please add its uuid to the dependentAssets in cc.config.json.
              this._copyAndTonemapMaterial._uuid = "builtin-pipeline-tone-mapping-material";
              this._copyAndTonemapMaterial.initialize({
                effectName: 'pipeline/post-process/tone-mapping'
              });
              if (this._copyAndTonemapMaterial.effectAsset) {
                this._initialized = true;
              }
              return this._initialized ? 0 : 1;
            }
          }]);
        }();
        rendering.setCustomPipeline('Builtin', new BuiltinPipelineBuilder());
      } // if (rendering)
      cclegacy._RF.pop();
    }
  };
});

System.register("chunks:///_virtual/internal.js", ['./builtin-pipeline-settings.js', './builtin-pipeline-types.js', './builtin-pipeline.js'], function () {
  return {
    setters: [null, null, null],
    execute: function () {}
  };
});

(function(r) {
  r('virtual:///prerequisite-imports/internal', 'chunks:///_virtual/internal.js'); 
})(function(mid, cid) {
    System.register(mid, [cid], function (_export, _context) {
    return {
        setters: [function(_m) {
            var _exportObj = {};

            for (var _key in _m) {
              if (_key !== "default" && _key !== "__esModule") _exportObj[_key] = _m[_key];
            }
      
            _export(_exportObj);
        }],
        execute: function () { }
    };
    });
});
//# sourceMappingURL=data:application/json;charset=utf-8;base64,eyJ2ZXJzaW9uIjozLCJzb3VyY2VzIjpbIi4uL2ZpbGU6L0Q6L0lkZS9JZGVhUHJvamVjdHMvQ2xvdWRNYXJ0L3BldC1nYW1lL2NvY29zLWNsaS9wYWNrYWdlcy9lbmdpbmUvZWRpdG9yL2Fzc2V0cy9kZWZhdWx0X3JlbmRlcnBpcGVsaW5lL2ZpbGU6L0Q6L0lkZS9JZGVhUHJvamVjdHMvQ2xvdWRNYXJ0L3BldC1nYW1lL2NvY29zLWNsaS9wYWNrYWdlcy9lbmdpbmUvZWRpdG9yL2Fzc2V0cy9kZWZhdWx0X3JlbmRlcnBpcGVsaW5lL2J1aWx0aW4tcGlwZWxpbmUtc2V0dGluZ3MudHMiLCIuLi9maWxlOi9EOi9JZGUvSWRlYVByb2plY3RzL0Nsb3VkTWFydC9wZXQtZ2FtZS9jb2Nvcy1jbGkvcGFja2FnZXMvZW5naW5lL2VkaXRvci9hc3NldHMvZGVmYXVsdF9yZW5kZXJwaXBlbGluZS9maWxlOi9EOi9JZGUvSWRlYVByb2plY3RzL0Nsb3VkTWFydC9wZXQtZ2FtZS9jb2Nvcy1jbGkvcGFja2FnZXMvZW5naW5lL2VkaXRvci9hc3NldHMvZGVmYXVsdF9yZW5kZXJwaXBlbGluZS9idWlsdGluLXBpcGVsaW5lLXR5cGVzLnRzIiwiLi4vZmlsZTovRDovSWRlL0lkZWFQcm9qZWN0cy9DbG91ZE1hcnQvcGV0LWdhbWUvY29jb3MtY2xpL3BhY2thZ2VzL2VuZ2luZS9lZGl0b3IvYXNzZXRzL2RlZmF1bHRfcmVuZGVycGlwZWxpbmUvZmlsZTovRDovSWRlL0lkZWFQcm9qZWN0cy9DbG91ZE1hcnQvcGV0LWdhbWUvY29jb3MtY2xpL3BhY2thZ2VzL2VuZ2luZS9lZGl0b3IvYXNzZXRzL2RlZmF1bHRfcmVuZGVycGlwZWxpbmUvYnVpbHRpbi1waXBlbGluZS50cyJdLCJuYW1lcyI6WyJjY2NsYXNzIiwiX2RlY29yYXRvciIsImRpc2FsbG93TXVsdGlwbGUiLCJleGVjdXRlSW5FZGl0TW9kZSIsIm1lbnUiLCJwcm9wZXJ0eSIsInJlcXVpcmVDb21wb25lbnQiLCJ0eXBlIiwiQnVpbHRpblBpcGVsaW5lU2V0dGluZ3MiLCJleHBvcnRzIiwiX2RlYyIsIl9kZWMyIiwiX2RlYzMiLCJDYW1lcmEiLCJfZGVjNCIsIkNDQm9vbGVhbiIsIl9kZWM1IiwiZGlzcGxheU5hbWUiLCJfZGVjNiIsImdyb3VwIiwiaWQiLCJuYW1lIiwiX2RlYzciLCJzdHlsZSIsIkNDSW50ZWdlciIsInJhbmdlIiwiX2RlYzgiLCJfZGVjOSIsInRvb2x0aXAiLCJDQ0Zsb2F0Iiwic2xpZGUiLCJfZGVjMCIsIl9kZWMxIiwiQmxvb21UeXBlIiwiX2RlYzEwIiwiX2RlYzExIiwiTWF0ZXJpYWwiLCJfZGVjMTIiLCJfZGVjMTMiLCJfZGVjMTQiLCJfZGVjMTUiLCJtaW4iLCJfZGVjMTYiLCJfZGVjMTciLCJfZGVjMTgiLCJfZGVjMTkiLCJfZGVjMjAiLCJfZGVjMjEiLCJUZXh0dXJlMkQiLCJfZGVjMjIiLCJfZGVjMjMiLCJfZGVjMjQiLCJfZGVjMjUiLCJfZGVjMjYiLCJfZGVjMjciLCJfY2xhc3MiLCJfY2xhc3MyIiwiX0NvbXBvbmVudCIsIl90aGlzIiwiX2NsYXNzQ2FsbENoZWNrIiwiX2xlbiIsImFyZ3VtZW50cyIsImxlbmd0aCIsImFyZ3MiLCJBcnJheSIsIl9rZXkiLCJfY2FsbFN1cGVyIiwiY29uY2F0IiwiX2luaXRpYWxpemVyRGVmaW5lUHJvcGVydHkiLCJfZGVzY3JpcHRvciIsIl9kZXNjcmlwdG9yMiIsIl9pbmhlcml0cyIsIl9jcmVhdGVDbGFzcyIsImtleSIsInZhbHVlIiwiZ2V0UGlwZWxpbmVTZXR0aW5ncyIsIl9zZXR0aW5ncyIsIm9uRW5hYmxlIiwiZmlsbFJlcXVpcmVkUGlwZWxpbmVTZXR0aW5ncyIsImNhbWVyYUNvbXBvbmVudCIsImdldENvbXBvbmVudCIsImNhbWVyYSIsInBpcGVsaW5lU2V0dGluZ3MiLCJvbkRpc2FibGUiLCJnZXQiLCJfZWRpdG9yUHJldmlldyIsInNldCIsInYiLCJfdHJ5RW5hYmxlRWRpdG9yUHJldmlldyIsInJlbmRlcmluZyIsInVuZGVmaW5lZCIsInNldEVkaXRvclBpcGVsaW5lU2V0dGluZ3MiLCJfZGlzYWJsZUVkaXRvclByZXZpZXciLCJjdXJyZW50IiwiZ2V0RWRpdG9yUGlwZWxpbmVTZXR0aW5ncyIsIm1zYWEiLCJlbmFibGVkIiwic2FtcGxlQ291bnQiLCJNYXRoIiwicG93IiwiY2VpbCIsImxvZzIiLCJtYXgiLCJlbmFibGVTaGFkaW5nU2NhbGUiLCJzaGFkaW5nU2NhbGUiLCJibG9vbSIsImthd2FzZUZpbHRlck1hdGVyaWFsIiwibWlwbWFwRmlsdGVyTWF0ZXJpYWwiLCJlbmFibGVBbHBoYU1hc2siLCJpdGVyYXRpb25zIiwidGhyZXNob2xkIiwiaW50ZW5zaXR5IiwiY29sb3JHcmFkaW5nIiwibWF0ZXJpYWwiLCJjb250cmlidXRlIiwiY29sb3JHcmFkaW5nTWFwIiwidmFsIiwiZnhhYSIsImZzciIsInNoYXJwbmVzcyIsInRvbmVNYXBwaW5nIiwiQ29tcG9uZW50IiwiX2FwcGx5RGVjb3JhdGVkRGVzY3JpcHRvciIsInByb3RvdHlwZSIsImNvbmZpZ3VyYWJsZSIsImVudW1lcmFibGUiLCJ3cml0YWJsZSIsImluaXRpYWxpemVyIiwibWFrZVBpcGVsaW5lU2V0dGluZ3MiLCJPYmplY3QiLCJnZXRPd25Qcm9wZXJ0eURlc2NyaXB0b3IiLCJfY2NsZWdhY3kiLCJfUkYiLCJwb3AiLCJTYW1wbGVDb3VudCIsImdmeCIsIm1ha2VNU0FBIiwiWDQiLCJmaWxsUmVxdWlyZWRNU0FBIiwibWFrZUhCQU8iLCJyYWRpdXNTY2FsZSIsImFuZ2xlQmlhc0RlZ3JlZSIsImJsdXJTaGFycG5lc3MiLCJhb1NhdHVyYXRpb24iLCJuZWVkQmx1ciIsImZpbGxSZXF1aXJlZEhCQU8iLCJjY2VudW0iLCJtYWtlQmxvb20iLCJLYXdhc2VEdWFsRmlsdGVyIiwiZmlsbFJlcXVpcmVkQmxvb20iLCJtYWtlQ29sb3JHcmFkaW5nIiwiZmlsbFJlcXVpcmVkQ29sb3JHcmFkaW5nIiwibWFrZUZTUiIsImZpbGxSZXF1aXJlZEZTUiIsIm1ha2VGWEFBIiwiZmlsbFJlcXVpcmVkRlhBQSIsIm1ha2VUb25lTWFwcGluZyIsImZpbGxSZXF1aXJlZFRvbmVNYXBwaW5nIiwiQUFCQiIsImdlb21ldHJ5IiwiU3BoZXJlIiwiaW50ZXJzZWN0IiwiQ2xlYXJGbGFnQml0IiwiQ29sb3IiLCJGb3JtYXQiLCJGb3JtYXRGZWF0dXJlQml0IiwiTG9hZE9wIiwiU3RvcmVPcCIsIlRleHR1cmVUeXBlIiwiVmlld3BvcnQiLCJzY2VuZSIsInJlbmRlcmVyIiwiQ2FtZXJhVXNhZ2UiLCJDU01MZXZlbCIsIkxpZ2h0VHlwZSIsImZvcndhcmROZWVkQ2xlYXJDb2xvciIsImNsZWFyRmxhZyIsIkNPTE9SIiwiU1RFTkNJTCIsImdldENzbU1haW5MaWdodFZpZXdwb3J0IiwibGlnaHQiLCJ3IiwiaCIsImxldmVsIiwidnAiLCJzY3JlZW5TcGFjZVNpZ25ZIiwic2hhZG93Rml4ZWRBcmVhIiwiY3NtTGV2ZWwiLCJMRVZFTF8xIiwibGVmdCIsInRvcCIsIndpZHRoIiwidHJ1bmMiLCJoZWlnaHQiLCJmbG9vciIsIlBpcGVsaW5lQ29uZmlncyIsIl9kZWZpbmVQcm9wZXJ0eSIsIlIzMkYiLCJWZWMyIiwiVmVjNCIsInNldHVwUGlwZWxpbmVDb25maWdzIiwicHBsIiwiY29uZmlncyIsInNhbXBsZUZlYXR1cmUiLCJTQU1QTEVEX1RFWFRVUkUiLCJMSU5FQVJfRklMVEVSIiwiZGV2aWNlIiwiaXNXZWIiLCJzeXMiLCJpc05hdGl2ZSIsImlzV2ViR0wxIiwiZ2Z4QVBJIiwiQVBJIiwiV0VCR0wiLCJpc1dlYkdMMiIsIldFQkdMMiIsImlzV2ViR1BVIiwiV0VCR1BVIiwiaXNNb2JpbGUiLCJpc0hEUiIsInBpcGVsaW5lU2NlbmVEYXRhIiwidXNlRmxvYXRPdXRwdXQiLCJnZXRNYWNyb0Jvb2wiLCJ0b25lTWFwcGluZ1R5cGUiLCJwb3N0U2V0dGluZ3MiLCJzaGFkb3dJbmZvIiwic2hhZG93cyIsInNoYWRvd0VuYWJsZWQiLCJzaGFkb3dNYXBGb3JtYXQiLCJwaXBlbGluZSIsInN1cHBvcnRzUjMyRmxvYXRUZXh0dXJlIiwiUkdCQTgiLCJzaGFkb3dNYXBTaXplIiwic2l6ZSIsInVzZVBsYW5hclNoYWRvdyIsIlNoYWRvd1R5cGUiLCJQbGFuYXIiLCJjYXBhYmlsaXRpZXMiLCJzdXBwb3J0RGVwdGhTYW1wbGUiLCJnZXRGb3JtYXRGZWF0dXJlcyIsIkRFUFRIX1NURU5DSUwiLCJwbGF0Zm9ybSIsIngiLCJjbGlwU3BhY2VTaWduWSIsImRlZmF1bHRTZXR0aW5ncyIsIkNhbWVyYUNvbmZpZ3MiLCJzQ2xlYXJDb2xvclRyYW5zcGFyZW50QmxhY2siLCJzb3J0UGlwZWxpbmVQYXNzQnVpbGRlcnNCeUNvbmZpZ09yZGVyIiwicGFzc0J1aWxkZXJzIiwic29ydCIsImEiLCJiIiwiZ2V0Q29uZmlnT3JkZXIiLCJzb3J0UGlwZWxpbmVQYXNzQnVpbGRlcnNCeVJlbmRlck9yZGVyIiwiZ2V0UmVuZGVyT3JkZXIiLCJhZGRDb3B5VG9TY3JlZW5QYXNzIiwicHBsQ29uZmlncyIsImNhbWVyYUNvbmZpZ3MiLCJpbnB1dCIsImFzc2VydCIsImNvcHlBbmRUb25lbWFwTWF0ZXJpYWwiLCJwYXNzIiwiYWRkUmVuZGVyUGFzcyIsIm5hdGl2ZVdpZHRoIiwibmF0aXZlSGVpZ2h0IiwiYWRkUmVuZGVyVGFyZ2V0IiwiY29sb3JOYW1lIiwiQ0xFQVIiLCJTVE9SRSIsImFkZFRleHR1cmUiLCJhZGRRdWV1ZSIsIlF1ZXVlSGludCIsIk9QQVFVRSIsImFkZEZ1bGxzY3JlZW5RdWFkIiwiZ2V0UGluZ1BvbmdSZW5kZXJUYXJnZXQiLCJwcmV2TmFtZSIsInByZWZpeCIsInN0YXJ0c1dpdGgiLCJOdW1iZXIiLCJjaGFyQXQiLCJGb3J3YXJkTGlnaHRpbmciLCJjcmVhdGUiLCJjdWxsTGlnaHRzIiwiZnJ1c3R1bSIsImNhbWVyYVBvcyIsImxpZ2h0cyIsInNoYWRvd0VuYWJsZWRTcG90TGlnaHRzIiwiX2l0ZXJhdG9yIiwiX2NyZWF0ZUZvck9mSXRlcmF0b3JIZWxwZXIiLCJzcG90TGlnaHRzIiwiX3N0ZXAiLCJzIiwibiIsImRvbmUiLCJiYWtlZCIsIl9zcGhlcmUiLCJwb3NpdGlvbiIsInkiLCJ6Iiwic3BoZXJlRnJ1c3R1bSIsInB1c2giLCJlcnIiLCJlIiwiZiIsIl9pdGVyYXRvcjIiLCJzcGhlcmVMaWdodHMiLCJfc3RlcDIiLCJfaXRlcmF0b3IzIiwicG9pbnRMaWdodHMiLCJfc3RlcDMiLCJfaXRlcmF0b3I0IiwicmFuZ2VkRGlyTGlnaHRzIiwiX3N0ZXA0IiwidHJhbnNmb3JtIiwiX2JvdW5kaW5nQm94IiwiX3JhbmdlZERpckxpZ2h0Qm91bmRpbmdCb3giLCJub2RlIiwiZ2V0V29ybGRNYXRyaXgiLCJhYWJiRnJ1c3R1bSIsImxocyIsInJocyIsIlZlYzMiLCJzcXVhcmVkRGlzdGFuY2UiLCJfYWRkTGlnaHRRdWV1ZXMiLCJfaXRlcmF0b3I1IiwiX3N0ZXA1IiwicXVldWUiLCJCTEVORCIsIlNQSEVSRSIsIlNQT1QiLCJQT0lOVCIsIlJBTkdFRF9ESVJFQ1RJT05BTCIsImFkZFNjZW5lIiwiU2NlbmVGbGFncyIsImFkZFNwb3RsaWdodFNoYWRvd1Bhc3NlcyIsIm1heE51bVNoYWRvd01hcHMiLCJpIiwiX2l0ZXJhdG9yNiIsIl9zdGVwNiIsInNoYWRvd1Bhc3MiLCJhZGREZXB0aFN0ZW5jaWwiLCJESVNDQVJEIiwiTk9ORSIsIk1BU0siLCJTSEFET1dfQ0FTVEVSIiwidXNlTGlnaHRGcnVzdHVtIiwiYWRkTGlnaHRRdWV1ZXMiLCJfaXRlcmF0b3I3IiwiX3N0ZXA3IiwiYWRkTGlnaHRQYXNzZXMiLCJkZXB0aFN0ZW5jaWxOYW1lIiwiZGVwdGhTdGVuY2lsU3RvcmVPcCIsInZpZXdwb3J0IiwiY291bnQiLCJfaXRlcmF0b3I4IiwiX3N0ZXA4Iiwic3RvcmVPcCIsInNldFZpZXdwb3J0IiwiTE9BRCIsImlzTXVsdGlwbGVMaWdodFBhc3Nlc05lZWRlZCIsIkJ1aWx0aW5Gb3J3YXJkUGFzc0J1aWxkZXIiLCJDb25maWdPcmRlciIsIlJlbmRlck9yZGVyIiwiY29uZmlnQ2FtZXJhIiwicGlwZWxpbmVDb25maWdzIiwiZW5hYmxlTWFpbkxpZ2h0U2hhZG93TWFwIiwibWFpbkxpZ2h0IiwiZW5hYmxlTWFpbkxpZ2h0UGxhbmFyU2hhZG93TWFwIiwiZW5hYmxlUGxhbmFyUmVmbGVjdGlvblByb2JlIiwiaXNNYWluR2FtZVdpbmRvdyIsImNhbWVyYVVzYWdlIiwiU0NFTkVfVklFVyIsIkdBTUVfVklFVyIsIlBSRVZJRVciLCJlbmFibGVNU0FBIiwic2V0dGluZ3MiLCJyZW1haW5pbmdQYXNzZXMiLCJtYWNybyIsIkVOQUJMRV9XRUJHTF9BTlRJQUxJQVMiLCJFTkFCTEVfVFJBTlNQQVJFTlRfQ0FOVkFTIiwiZW5hYmxlU3RvcmVTY2VuZURlcHRoIiwiZW5hYmxlU2luZ2xlRm9yd2FyZFBhc3MiLCJ3aW5kb3dSZXNpemUiLCJ3aW5kb3ciLCJSZXNvdXJjZUZsYWdzIiwiUmVzb3VyY2VSZXNpZGVuY3kiLCJyZW5kZXJXaW5kb3dJZCIsImVuYWJsZUhEUiIsIlRFWDJEIiwicmFkaWFuY2VGb3JtYXQiLCJDT0xPUl9BVFRBQ0hNRU5UIiwiTUVNT1JZTEVTUyIsIkRFUFRIX1NURU5DSUxfQVRUQUNITUVOVCIsIm1vYmlsZU1heFNwb3RMaWdodFNoYWRvd01hcHMiLCJzZXR1cCIsImNvbnRleHQiLCJzZXRWZWM0IiwiZm9yd2FyZExpZ2h0aW5nIiwiX2FkZENhc2NhZGVkU2hhZG93TWFwUGFzcyIsIl90cnlBZGRSZWZsZWN0aW9uUHJvYmVQYXNzZXMiLCJfYWRkRm9yd2FyZFJhZGlhbmNlUGFzc2VzIiwic2hhZG93U2l6ZSIsIl92aWV3cG9ydCIsImNzbVN1cHBvcnRlZCIsInNvdXJjZUNhbWVyYSIsInJlZmxlY3Rpb25Qcm9iZU1hbmFnZXIiLCJjY2xlZ2FjeSIsImludGVybmFsIiwicHJvYmVzIiwiZ2V0UHJvYmVzIiwibWF4UHJvYmVDb3VudCIsInByb2JlSUQiLCJfaXRlcmF0b3I5IiwiX3N0ZXA5IiwicHJvYmUiLCJuZWVkUmVuZGVyIiwiYXJlYSIsInJlbmRlckFyZWEiLCJwcm9iZVR5cGUiLCJQcm9iZVR5cGUiLCJQTEFOQVIiLCJyZWZsZWN0aW9uQ2FtZXJhIiwiRURJVE9SIiwicmVhbHRpbWVQbGFuYXJUZXh0dXJlIiwiYWRkUmVuZGVyV2luZG93IiwicHJvYmVQYXNzIiwiX2J1aWxkUmVmbGVjdGlvblByb2JlUGFzcyIsImZhY2VJZHgiLCJfd2luZG93IiwiX2NvbG9yTmFtZSIsIl9kZXB0aFN0ZW5jaWxOYW1lIiwiX3Byb2JlUGFzcyIsImNvbG9yU3RvcmVPcCIsIl9yZWZsZWN0aW9uUHJvYmVDbGVhckNvbG9yIiwiY2xlYXJDb2xvciIsInBhY2tSR0JFIiwiX2NsZWFyQ29sb3IiLCJjbGVhckRlcHRoIiwiY2xlYXJTdGVuY2lsIiwibGlnaHRJbmZvIiwiTGlnaHRJbmZvIiwiYWRkU2NlbmVPZkNhbWVyYSIsIlJFRkxFQ1RJT05fUFJPQkUiLCJkaXNhYmxlTVNBQSIsInJvdW5kIiwiX2FkZEZvcndhcmRTaW5nbGVSYWRpYW5jZVBhc3MiLCJfYWRkRm9yd2FyZE11bHRpcGxlUmFkaWFuY2VQYXNzZXMiLCJfYWRkUGxhbmFyU2hhZG93UXVldWUiLCJzY2VuZUZsYWdzIiwiZ2VvbWV0cnlSZW5kZXJlciIsIkdFT01FVFJZIiwibXNhYVJhZGlhbmNlTmFtZSIsIm1zYWFEZXB0aFN0ZW5jaWxOYW1lIiwibXNQYXNzIiwiYWRkTXVsdGlzYW1wbGVSZW5kZXJQYXNzIiwiX2J1aWxkRm9yd2FyZE1haW5MaWdodFBhc3MiLCJyZXNvbHZlUmVuZGVyVGFyZ2V0IiwiZmlyc3RTdG9yZU9wIiwid2FybiIsIlBMQU5BUl9TSEFET1ciLCJkb3duU2l6ZSIsInNjYWxlIiwiQnVpbHRpbkJsb29tUGFzc0J1aWxkZXIiLCJoYXNWYWxpZE1hdGVyaWFsIiwiTWlwbWFwRmlsdGVyIiwiZW5hYmxlQmxvb20iLCJmb3JtYXQiLCJibG9vbVdpZHRoIiwiYmxvb21IZWlnaHQiLCJfYmxvb21Eb3duU2FtcGxlVGV4RGVzY3MiLCJjcmVhdGVUZXh0dXJlIiwiX2Jsb29tVXBTYW1wbGVUZXhEZXNjcyIsIl9vcmlnaW5hbENvbG9yRGVzYyIsIl9wcmVmaWx0ZXJUZXhEZXNjIiwiZGVzYyIsInByZXZSZW5kZXJQYXNzIiwiX2FkZEthd2FzZUR1YWxGaWx0ZXJCbG9vbVBhc3NlcyIsIl9hZGRNaXBtYXBGaWx0ZXJCbG9vbVBhc3NlcyIsImJsb29tTWF0ZXJpYWwiLCJyYWRpYW5jZU5hbWUiLCJzaXplQ291bnQiLCJfYmxvb21XaWR0aHMiLCJfYmxvb21IZWlnaHRzIiwiX2Jsb29tVGV4TmFtZXMiLCJfYmxvb21QYXJhbXMiLCJwcmVmaWx0ZXJQYXNzIiwiX2NsZWFyQ29sb3JUcmFuc3BhcmVudEJsYWNrIiwiZG93blBhc3MiLCJfYmxvb21UZXhTaXplIiwidXBQYXNzIiwiY29tYmluZVBhc3MiLCJfYWRkUGFzcyIsImxheW91dCIsInBhc3NJbmRleCIsImxvYWRPcCIsInF1ZXVlSGludCIsInByZWZpbHRlckluZm8iLCJjdXJyU2FtcGxlUGFzcyIsImRvd25TYW1wbGVJbmZvcyIsImN1cnJJbmZvIiwic2FtcGxlclNyYyIsInNhbXBsZXJTcmNOYW1lIiwibGFzdEluZGV4IiwidXBTYW1wbGVJbmZvcyIsInNhbXBsZVNyYyIsInNhbXBsZVNyY05hbWUiLCJCdWlsdGluVG9uZU1hcHBpbmdQYXNzQnVpbGRlciIsImVuYWJsZUNvbG9yR3JhZGluZyIsImVuYWJsZVRvbmVNYXBwaW5nIiwic2V0UHJvcGVydHkiLCJfYWRkQ29weUFuZFRvbmVtYXBQYXNzIiwibGRyQ29sb3JQcmVmaXgiLCJsZHJDb2xvck5hbWUiLCJsdXRUZXgiLCJfY29sb3JHcmFkaW5nVGV4U2l6ZSIsImlzU3F1YXJlTWFwIiwic2V0VmVjMiIsInNldEZsb2F0IiwiQnVpbHRpbkZYQUFQYXNzQnVpbGRlciIsImVuYWJsZUZYQUEiLCJfYWRkRnhhYVBhc3MiLCJpbnB1dENvbG9yTmFtZSIsImxhc3RQYXNzIiwiZnhhYU1hdGVyaWFsIiwiX2Z4YWFQYXJhbXMiLCJCdWlsdGluRnNyUGFzc0J1aWxkZXIiLCJlbmFibGVGU1IiLCJvdXRwdXRDb2xvck5hbWUiLCJfYWRkRnNyUGFzcyIsImZzck1hdGVyaWFsIiwiX2ZzclRleFNpemUiLCJfZnNyUGFyYW1zIiwiY2xhbXAiLCJ1aUNvbG9yUHJlZml4IiwiZnNyQ29sb3JOYW1lIiwiZWFzdVBhc3MiLCJyY2FzUGFzcyIsIkJ1aWx0aW5VaVBhc3NCdWlsZGVyIiwiZmxhZ3MiLCJVSSIsImVuYWJsZVByb2ZpbGVyIiwiUFJPRklMRVIiLCJzaG93U3RhdGlzdGljcyIsIkJ1aWx0aW5QaXBlbGluZUJ1aWxkZXIiLCJkaXJlY3RvciIsInJvb3QiLCJwaXBlbGluZUV2ZW50IiwiX3NldHVwUGlwZWxpbmVQcmV2aWV3IiwiaXNFZGl0b3JWaWV3IiwiZWRpdG9yU2V0dGluZ3MiLCJfcHJlcGFyZVBpcGVsaW5lUGFzc2VzIiwiX3Bhc3NCdWlsZGVycyIsIl9wYXNzZXMiLCJfaXRlcmF0b3IwIiwiX3N0ZXAwIiwiX2ZvcndhcmRQYXNzIiwiX2Jsb29tUGFzcyIsIl90b25lTWFwcGluZ1Bhc3MiLCJfZnhhYVBhc3MiLCJfZnNyUGFzcyIsIl91aVBhc3MiLCJfc2V0dXBCdWlsdGluQ2FtZXJhQ29uZmlncyIsIkdBTUUiLCJzd2FwY2hhaW4iLCJpc0dhbWVWaWV3IiwiZW5hYmxlRnVsbFBpcGVsaW5lIiwidmlzaWJpbGl0eSIsIkxheWVycyIsIkVudW0iLCJERUZBVUxUIiwicHJvZmlsZXIiLCJSR0JBMTZGIiwiX2NvcHlBbmRUb25lbWFwTWF0ZXJpYWwiLCJfc2V0dXBDYW1lcmFDb25maWdzIiwiX2l0ZXJhdG9yMSIsIl9zdGVwMSIsImJ1aWxkZXIiLCJfY29uZmlncyIsIl9jYW1lcmFDb25maWdzIiwiX2l0ZXJhdG9yMTAiLCJfc3RlcDEwIiwiY2FtZXJhcyIsIl9pbml0TWF0ZXJpYWxzIiwiX2l0ZXJhdG9yMTEiLCJfc3RlcDExIiwiX3BpcGVsaW5lRXZlbnQiLCJlbWl0IiwiUGlwZWxpbmVFdmVudFR5cGUiLCJSRU5ERVJfQ0FNRVJBX0JFR0lOIiwiX2J1aWxkRm9yd2FyZFBpcGVsaW5lIiwiX2J1aWxkU2ltcGxlUGlwZWxpbmUiLCJSRU5ERVJfQ0FNRVJBX0VORCIsIl9pdGVyYXRvcjEyIiwiX3N0ZXAxMiIsIl9pbml0aWFsaXplZCIsIl91dWlkIiwiaW5pdGlhbGl6ZSIsImVmZmVjdE5hbWUiLCJlZmZlY3RBc3NldCIsInNldEN1c3RvbVBpcGVsaW5lIl0sIm1hcHBpbmdzIjoiOzs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7OztNQW9DQSxJQUFRQSxPQUFPLEdBQWtGQyxVQUFVLENBQW5HRCxPQUFPO1FBQUVFLGdCQUFnQixHQUFnRUQsVUFBVSxDQUExRkMsZ0JBQWdCO1FBQUVDLGlCQUFpQixHQUE2Q0YsVUFBVSxDQUF4RUUsaUJBQWlCO1FBQUVDLElBQUksR0FBdUNILFVBQVUsQ0FBckRHLElBQUk7UUFBRUMsUUFBUSxHQUE2QkosVUFBVSxDQUEvQ0ksUUFBUTtRQUFFQyxnQkFBZ0IsR0FBV0wsVUFBVSxDQUFyQ0ssZ0JBQWdCO1FBQUVDLElBQUksR0FBS04sVUFBVSxDQUFuQk0sSUFBSTtNQUU1RixJQUthQyx1QkFBdUIsR0FBQUMsT0FBQSxDQUFBLHlCQUFBLEdBQUFDLElBQUEsR0FMbkNWLE9BQU8sQ0FBQyx5QkFBeUIsQ0FBQyxFQUFBVyxLQUFBLEdBQ2xDUCxJQUFJLENBQUMsbUNBQW1DLENBQUMsRUFBQVEsS0FBQSxHQUN6Q04sZ0JBQWdCLENBQUNPLE1BQU0sQ0FBQyxFQUFBQyxLQUFBLEdBa0NwQlQsUUFBUSxDQUFDVSxTQUFTLENBQUMsRUFBQUMsS0FBQSxHQUduQlgsUUFBUSxDQUFDO1FBQ05ZLFdBQVcsRUFBRSwrQkFBK0I7UUFDNUNWLElBQUksRUFBRVE7TUFDVixDQUFDLENBQUMsRUFBQUcsS0FBQSxHQStCRGIsUUFBUSxDQUFDO1FBQ05jLEtBQUssRUFBRTtVQUFFQyxFQUFFLEVBQUUsTUFBTTtVQUFFQyxJQUFJLEVBQUU7UUFBNkIsQ0FBQTtRQUN4RGQsSUFBSSxFQUFFUTtNQUNWLENBQUMsQ0FBQyxFQUFBTyxLQUFBLEdBV0RqQixRQUFRLENBQUM7UUFDTmMsS0FBSyxFQUFFO1VBQUVDLEVBQUUsRUFBRSxNQUFNO1VBQUVDLElBQUksRUFBRSwyQkFBMkI7VUFBRUUsS0FBSyxFQUFFO1FB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LENBQUEsRUFBQTtRQUdMemYsU0FBUyxDQUFDOGYsaUJBQWlCLENBQUMsU0FBUyxFQUFFLElBQUl4RCxzQkFBc0IsQ0FBRSxDQUFBLENBQUM7TUFFeEUsQ0FBQyxDQUFBO01BQUM1WixRQUFBLENBQUFDLEdBQUEsQ0FBQUMsR0FBQSxDQUFBLENBQUEiLCJmaWxlIjoiYWxsLmpzIiwic291cmNlc0NvbnRlbnQiOlsiLypcclxuIENvcHlyaWdodCAoYykgMjAyMS0yMDI0IFhpYW1lbiBZYWppIFNvZnR3YXJlIENvLiwgTHRkLlxyXG5cclxuIGh0dHBzOi8vd3d3LmNvY29zLmNvbS9cclxuXHJcbiBQZXJtaXNzaW9uIGlzIGhlcmVieSBncmFudGVkLCBmcmVlIG9mIGNoYXJnZSwgdG8gYW55IHBlcnNvbiBvYnRhaW5pbmcgYSBjb3B5XHJcbiBvZiB0aGlzIHNvZnR3YXJlIGFuZCBhc3NvY2lhdGVkIGRvY3VtZW50YXRpb24gZmlsZXMgKHRoZSBcIlNvZnR3YXJlXCIpLCB0byBkZWFsXHJcbiBpbiB0aGUgU29mdHdhcmUgd2l0aG91dCByZXN0cmljdGlvbiwgaW5jbHVkaW5nIHdpdGhvdXQgbGltaXRhdGlvbiB0aGUgcmlnaHRzIHRvXHJcbiB1c2UsIGNvcHksIG1vZGlmeSwgbWVyZ2UsIHB1Ymxpc2gsIGRpc3RyaWJ1dGUsIHN1YmxpY2Vuc2UsIGFuZC9vciBzZWxsIGNvcGllc1xyXG4gb2YgdGhlIFNvZnR3YXJlLCBhbmQgdG8gcGVybWl0IHBlcnNvbnMgdG8gd2hvbSB0aGUgU29mdHdhcmUgaXMgZnVybmlzaGVkIHRvIGRvIHNvLFxyXG4gc3ViamVjdCB0byB0aGUgZm9sbG93aW5nIGNvbmRpdGlvbnM6XHJcblxyXG4gVGhlIGFib3ZlIGNvcHlyaWdodCBub3RpY2UgYW5kIHRoaXMgcGVybWlzc2lvbiBub3RpY2Ugc2hhbGwgYmUgaW5jbHVkZWQgaW5cclxuIGFsbCBjb3BpZXMgb3Igc3Vic3RhbnRpYWwgcG9ydGlvbnMgb2YgdGhlIFNvZnR3YXJlLlxyXG5cclxuIFRIRSBTT0ZUV0FSRSBJUyBQUk9WSURFRCBcIkFTIElTXCIsIFdJVEhPVVQgV0FSUkFOVFkgT0YgQU5ZIEtJTkQsIEVYUFJFU1MgT1JcclxuIElNUExJRUQsIElOQ0xVRElORyBCVVQgTk9UIExJTUlURUQgVE8gVEhFIFdBUlJBTlRJRVMgT0YgTUVSQ0hBTlRBQklMSVRZLFxyXG4gRklUTkVTUyBGT1IgQSBQQVJUSUNVTEFSIFBVUlBPU0UgQU5EIE5PTklORlJJTkdFTUVOVC4gSU4gTk8gRVZFTlQgU0hBTEwgVEhFXHJcbiBBVVRIT1JTIE9SIENPUFlSSUdIVCBIT0xERVJTIEJFIExJQUJMRSBGT1IgQU5ZIENMQUlNLCBEQU1BR0VTIE9SIE9USEVSXHJcbiBMSUFCSUxJVFksIFdIRVRIRVIgSU4gQU4gQUNUSU9OIE9GIENPTlRSQUNULCBUT1JUIE9SIE9USEVSV0lTRSwgQVJJU0lORyBGUk9NLFxyXG4gT1VUIE9GIE9SIElOIENPTk5FQ1RJT04gV0lUSCBUSEUgU09GVFdBUkUgT1IgVEhFIFVTRSBPUiBPVEhFUiBERUFMSU5HUyBJTlxyXG4gVEhFIFNPRlRXQVJFLlxyXG4qL1xyXG5cclxuaW1wb3J0IHtcclxuICAgIF9kZWNvcmF0b3IsIENhbWVyYSwgQ0NCb29sZWFuLCBDQ0Zsb2F0LCBDQ0ludGVnZXIsIENvbXBvbmVudCxcclxuICAgIE1hdGVyaWFsLCByZW5kZXJpbmcsIFRleHR1cmUyRCxcclxufSBmcm9tICdjYyc7XHJcblxyXG5pbXBvcnQgeyBFRElUT1IgfSBmcm9tICdjYy9lbnYnO1xyXG5cclxuaW1wb3J0IHtcclxuICAgIEJsb29tVHlwZSxcclxuICAgIGZpbGxSZXF1aXJlZFBpcGVsaW5lU2V0dGluZ3MsIG1ha2VQaXBlbGluZVNldHRpbmdzLCBQaXBlbGluZVNldHRpbmdzLFxyXG59IGZyb20gJy4vYnVpbHRpbi1waXBlbGluZS10eXBlcyc7XHJcblxyXG5jb25zdCB7IGNjY2xhc3MsIGRpc2FsbG93TXVsdGlwbGUsIGV4ZWN1dGVJbkVkaXRNb2RlLCBtZW51LCBwcm9wZXJ0eSwgcmVxdWlyZUNvbXBvbmVudCwgdHlwZSB9ID0gX2RlY29yYXRvcjtcclxuXHJcbkBjY2NsYXNzKCdCdWlsdGluUGlwZWxpbmVTZXR0aW5ncycpXHJcbkBtZW51KCdSZW5kZXJpbmcvQnVpbHRpblBpcGVsaW5lU2V0dGluZ3MnKVxyXG5AcmVxdWlyZUNvbXBvbmVudChDYW1lcmEpXHJcbkBkaXNhbGxvd011bHRpcGxlXHJcbkBleGVjdXRlSW5FZGl0TW9kZVxyXG5leHBvcnQgY2xhc3MgQnVpbHRpblBpcGVsaW5lU2V0dGluZ3MgZXh0ZW5kcyBDb21wb25lbnQge1xyXG4gICAgQHByb3BlcnR5XHJcbiAgICBwcml2YXRlIHJlYWRvbmx5IF9zZXR0aW5nczogUGlwZWxpbmVTZXR0aW5ncyA9IG1ha2VQaXBlbGluZVNldHRpbmdzKCk7XHJcblxyXG4gICAgZ2V0UGlwZWxpbmVTZXR0aW5ncygpOiBQaXBlbGluZVNldHRpbmdzIHtcclxuICAgICAgICByZXR1cm4gdGhpcy5fc2V0dGluZ3M7XHJcbiAgICB9XHJcblxyXG4gICAgLy8gRW5hYmxlL0Rpc2FibGVcclxuICAgIG9uRW5hYmxlKCk6IHZvaWQge1xyXG4gICAgICAgIGZpbGxSZXF1aXJlZFBpcGVsaW5lU2V0dGluZ3ModGhpcy5fc2V0dGluZ3MpO1xyXG4gICAgICAgIGNvbnN0IGNhbWVyYUNvbXBvbmVudCA9IHRoaXMuZ2V0Q29tcG9uZW50KENhbWVyYSkhO1xyXG4gICAgICAgIGNvbnN0IGNhbWVyYSA9IGNhbWVyYUNvbXBvbmVudC5jYW1lcmE7XHJcbiAgICAgICAgY2FtZXJhLnBpcGVsaW5lU2V0dGluZ3MgPSB0aGlzLl9zZXR0aW5ncztcclxuXHJcbiAgICAgICAgaWYgKEVESVRPUikge1xyXG4gICAgICAgICAgICB0aGlzLl90cnlFbmFibGVFZGl0b3JQcmV2aWV3KCk7XHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG4gICAgb25EaXNhYmxlKCk6IHZvaWQge1xyXG4gICAgICAgIGNvbnN0IGNhbWVyYUNvbXBvbmVudCA9IHRoaXMuZ2V0Q29tcG9uZW50KENhbWVyYSkhO1xyXG4gICAgICAgIGNvbnN0IGNhbWVyYSA9IGNhbWVyYUNvbXBvbmVudC5jYW1lcmE7XHJcbiAgICAgICAgaWYgKGNhbWVyYSkge1xyXG4gICAgICAgICAgICBjYW1lcmEucGlwZWxpbmVTZXR0aW5ncyA9IG51bGw7XHJcbiAgICAgICAgfVxyXG4gICAgICAgIGlmIChFRElUT1IpIHtcclxuICAgICAgICAgICAgdGhpcy5fZGlzYWJsZUVkaXRvclByZXZpZXcoKTtcclxuICAgICAgICB9XHJcbiAgICB9XHJcblxyXG4gICAgLy8gRWRpdG9yIFByZXZpZXdcclxuICAgIEBwcm9wZXJ0eShDQ0Jvb2xlYW4pXHJcbiAgICBwcm90ZWN0ZWQgX2VkaXRvclByZXZpZXcgPSBmYWxzZTtcclxuXHJcbiAgICBAcHJvcGVydHkoe1xyXG4gICAgICAgIGRpc3BsYXlOYW1lOiAnRWRpdG9yIFByZXZpZXcgKEV4cGVyaW1lbnRhbCknLFxyXG4gICAgICAgIHR5cGU6IENDQm9vbGVhbixcclxuICAgIH0pXHJcbiAgICBnZXQgZWRpdG9yUHJldmlldygpOiBib29sZWFuIHtcclxuICAgICAgICByZXR1cm4gdGhpcy5fZWRpdG9yUHJldmlldztcclxuICAgIH1cclxuICAgIHNldCBlZGl0b3JQcmV2aWV3KHY6IGJvb2xlYW4pIHtcclxuICAgICAgICB0aGlzLl9lZGl0b3JQcmV2aWV3ID0gdjtcclxuICAgICAgICBpZiAoRURJVE9SKSB7XHJcbiAgICAgICAgICAgIHRoaXMuX3RyeUVuYWJsZUVkaXRvclByZXZpZXcoKTtcclxuICAgICAgICB9XHJcbiAgICB9XHJcbiAgICBwdWJsaWMgX3RyeUVuYWJsZUVkaXRvclByZXZpZXcoKTogdm9pZCB7XHJcbiAgICAgICAgaWYgKHJlbmRlcmluZyA9PT0gdW5kZWZpbmVkKSB7XHJcbiAgICAgICAgICAgIHJldHVybjtcclxuICAgICAgICB9XHJcbiAgICAgICAgaWYgKHRoaXMuX2VkaXRvclByZXZpZXcpIHtcclxuICAgICAgICAgICAgcmVuZGVyaW5nLnNldEVkaXRvclBpcGVsaW5lU2V0dGluZ3ModGhpcy5fc2V0dGluZ3MpO1xyXG4gICAgICAgIH0gZWxzZSB7XHJcbiAgICAgICAgICAgIHRoaXMuX2Rpc2FibGVFZGl0b3JQcmV2aWV3KCk7XHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG4gICAgcHVibGljIF9kaXNhYmxlRWRpdG9yUHJldmlldygpOiB2b2lkIHtcclxuICAgICAgICBpZiAocmVuZGVyaW5nID09PSB1bmRlZmluZWQpIHtcclxuICAgICAgICAgICAgcmV0dXJuO1xyXG4gICAgICAgIH1cclxuICAgICAgICBjb25zdCBjdXJyZW50ID0gcmVuZGVyaW5nLmdldEVkaXRvclBpcGVsaW5lU2V0dGluZ3MoKSBhcyBQaXBlbGluZVNldHRpbmdzIHwgbnVsbDtcclxuICAgICAgICBpZiAoY3VycmVudCA9PT0gdGhpcy5fc2V0dGluZ3MpIHtcclxuICAgICAgICAgICAgcmVuZGVyaW5nLnNldEVkaXRvclBpcGVsaW5lU2V0dGluZ3MobnVsbCk7XHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG5cclxuICAgIC8vIE1TQUFcclxuICAgIEBwcm9wZXJ0eSh7XHJcbiAgICAgICAgZ3JvdXA6IHsgaWQ6ICdNU0FBJywgbmFtZTogJ011bHRpc2FtcGxlIEFudGktQWxpYXNpbmcnIH0sXHJcbiAgICAgICAgdHlwZTogQ0NCb29sZWFuLFxyXG4gICAgfSlcclxuICAgIGdldCBNc2FhRW5hYmxlKCk6IGJvb2xlYW4ge1xyXG4gICAgICAgIHJldHVybiB0aGlzLl9zZXR0aW5ncy5tc2FhLmVuYWJsZWQ7XHJcbiAgICB9XHJcbiAgICBzZXQgTXNhYUVuYWJsZSh2YWx1ZTogYm9vbGVhbikge1xyXG4gICAgICAgIHRoaXMuX3NldHRpbmdzLm1zYWEuZW5hYmxlZCA9IHZhbHVlO1xyXG4gICAgICAgIGlmIChFRElUT1IpIHtcclxuICAgICAgICAgICAgdGhpcy5fdHJ5RW5hYmxlRWRpdG9yUHJldmlldygpO1xyXG4gICAgICAgIH1cclxuICAgIH1cclxuXHJcbiAgICBAcHJvcGVydHkoe1xyXG4gICAgICAgIGdyb3VwOiB7IGlkOiAnTVNBQScsIG5hbWU6ICdNdWx0aXNhbXBsZSBBbnRpLUFsaWFzaW5nJywgc3R5bGU6ICdzZWN0aW9uJyB9LFxyXG4gICAgICAgIHR5cGU6IENDSW50ZWdlcixcclxuICAgICAgICByYW5nZTogWzIsIDQsIDJdLFxyXG4gICAgfSlcclxuICAgIHNldCBtc2FhU2FtcGxlQ291bnQodmFsdWU6IG51bWJlcikge1xyXG4gICAgICAgIHZhbHVlID0gMiAqKiBNYXRoLmNlaWwoTWF0aC5sb2cyKE1hdGgubWF4KHZhbHVlLCAyKSkpO1xyXG4gICAgICAgIHZhbHVlID0gTWF0aC5taW4odmFsdWUsIDQpO1xyXG4gICAgICAgIHRoaXMuX3NldHRpbmdzLm1zYWEuc2FtcGxlQ291bnQgPSB2YWx1ZTtcclxuICAgICAgICBpZiAoRURJVE9SKSB7XHJcbiAgICAgICAgICAgIHRoaXMuX3RyeUVuYWJsZUVkaXRvclByZXZpZXcoKTtcclxuICAgICAgICB9XHJcbiAgICB9XHJcbiAgICBnZXQgbXNhYVNhbXBsZUNvdW50KCk6IG51bWJlciB7XHJcbiAgICAgICAgcmV0dXJuIHRoaXMuX3NldHRpbmdzLm1zYWEuc2FtcGxlQ291bnQ7XHJcbiAgICB9XHJcblxyXG4gICAgLy8gU2hhZGluZyBTY2FsZVxyXG4gICAgQHByb3BlcnR5KHtcclxuICAgICAgICBncm91cDogeyBpZDogJ1NoYWRpbmdTY2FsZScsIG5hbWU6ICdTaGFkaW5nU2NhbGUnLCBzdHlsZTogJ3NlY3Rpb24nIH0sXHJcbiAgICAgICAgdHlwZTogQ0NCb29sZWFuLFxyXG4gICAgfSlcclxuICAgIHNldCBzaGFkaW5nU2NhbGVFbmFibGUodmFsdWU6IGJvb2xlYW4pIHtcclxuICAgICAgICB0aGlzLl9zZXR0aW5ncy5lbmFibGVTaGFkaW5nU2NhbGUgPSB2YWx1ZTtcclxuICAgICAgICBpZiAoRURJVE9SKSB7XHJcbiAgICAgICAgICAgIHRoaXMuX3RyeUVuYWJsZUVkaXRvclByZXZpZXcoKTtcclxuICAgICAgICB9XHJcbiAgICB9XHJcbiAgICBnZXQgc2hhZGluZ1NjYWxlRW5hYmxlKCk6IGJvb2xlYW4ge1xyXG4gICAgICAgIHJldHVybiB0aGlzLl9zZXR0aW5ncy5lbmFibGVTaGFkaW5nU2NhbGU7XHJcbiAgICB9XHJcblxyXG4gICAgQHByb3BlcnR5KHtcclxuICAgICAgICB0b29sdGlwOiAnaTE4bjpwb3N0cHJvY2Vzcy5zaGFkaW5nU2NhbGUnLFxyXG4gICAgICAgIGdyb3VwOiB7IGlkOiAnU2hhZGluZ1NjYWxlJywgbmFtZTogJ1NoYWRpbmdTY2FsZScgfSxcclxuICAgICAgICB0eXBlOiBDQ0Zsb2F0LFxyXG4gICAgICAgIHJhbmdlOiBbMC4wMSwgNCwgMC4wMV0sXHJcbiAgICAgICAgc2xpZGU6IHRydWUsXHJcbiAgICB9KVxyXG4gICAgc2V0IHNoYWRpbmdTY2FsZSh2YWx1ZTogbnVtYmVyKSB7XHJcbiAgICAgICAgdGhpcy5fc2V0dGluZ3Muc2hhZGluZ1NjYWxlID0gdmFsdWU7XHJcbiAgICAgICAgaWYgKEVESVRPUikge1xyXG4gICAgICAgICAgICB0aGlzLl90cnlFbmFibGVFZGl0b3JQcmV2aWV3KCk7XHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG4gICAgZ2V0IHNoYWRpbmdTY2FsZSgpOiBudW1iZXIge1xyXG4gICAgICAgIHJldHVybiB0aGlzLl9zZXR0aW5ncy5zaGFkaW5nU2NhbGU7XHJcbiAgICB9XHJcblxyXG4gICAgLy8gQmxvb21cclxuICAgIEBwcm9wZXJ0eSh7XHJcbiAgICAgICAgZ3JvdXA6IHsgaWQ6ICdCbG9vbScsIG5hbWU6ICdCbG9vbSAoUG9zdFByb2Nlc3NpbmcpJywgc3R5bGU6ICdzZWN0aW9uJyB9LFxyXG4gICAgICAgIHR5cGU6IENDQm9vbGVhbixcclxuICAgIH0pXHJcbiAgICBzZXQgYmxvb21FbmFibGUodmFsdWU6IGJvb2xlYW4pIHtcclxuICAgICAgICB0aGlzLl9zZXR0aW5ncy5ibG9vbS5lbmFibGVkID0gdmFsdWU7XHJcbiAgICAgICAgaWYgKEVESVRPUikge1xyXG4gICAgICAgICAgICB0aGlzLl90cnlFbmFibGVFZGl0b3JQcmV2aWV3KCk7XHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG4gICAgZ2V0IGJsb29tRW5hYmxlKCk6IGJvb2xlYW4ge1xyXG4gICAgICAgIHJldHVybiB0aGlzLl9zZXR0aW5ncy5ibG9vbS5lbmFibGVkO1xyXG4gICAgfVxyXG5cclxuICAgIEB0eXBlKEJsb29tVHlwZSlcclxuICAgIEBwcm9wZXJ0eSh7XHJcbiAgICAgICAgZ3JvdXA6IHsgaWQ6ICdCbG9vbScsIG5hbWU6ICdCbG9vbSAoUG9zdFByb2Nlc3NpbmcpJywgc3R5bGU6ICdzZWN0aW9uJyB9LFxyXG4gICAgfSlcclxuICAgIHNldCBibG9vbVR5cGUodmFsdWU6IEJsb29tVHlwZSkge1xyXG4gICAgICAgIHRoaXMuX3NldHRpbmdzLmJsb29tLnR5cGUgPSB2YWx1ZTtcclxuICAgICAgICBpZiAoRURJVE9SKSB7XHJcbiAgICAgICAgICAgIHRoaXMuX3RyeUVuYWJsZUVkaXRvclByZXZpZXcoKTtcclxuICAgICAgICB9XHJcbiAgICB9XHJcblxyXG4gICAgZ2V0IGJsb29tVHlwZSgpOiBCbG9vbVR5cGUge1xyXG4gICAgICAgIHJldHVybiB0aGlzLl9zZXR0aW5ncy5ibG9vbS50eXBlO1xyXG4gICAgfVxyXG5cclxuICAgIEBwcm9wZXJ0eSh7XHJcbiAgICAgICAgZ3JvdXA6IHsgaWQ6ICdCbG9vbScsIG5hbWU6ICdCbG9vbSAoUG9zdFByb2Nlc3NpbmcpJywgc3R5bGU6ICdzZWN0aW9uJyB9LFxyXG4gICAgICAgIHR5cGU6IE1hdGVyaWFsLFxyXG4gICAgfSlcclxuICAgIHNldCBrYXdhc2VCbG9vbU1hdGVyaWFsKHZhbHVlOiBNYXRlcmlhbCkge1xyXG4gICAgICAgIGlmICh0aGlzLl9zZXR0aW5ncy5ibG9vbS5rYXdhc2VGaWx0ZXJNYXRlcmlhbCA9PT0gdmFsdWUpIHtcclxuICAgICAgICAgICAgcmV0dXJuO1xyXG4gICAgICAgIH1cclxuICAgICAgICB0aGlzLl9zZXR0aW5ncy5ibG9vbS5rYXdhc2VGaWx0ZXJNYXRlcmlhbCA9IHZhbHVlO1xyXG4gICAgICAgIGlmIChFRElUT1IpIHtcclxuICAgICAgICAgICAgdGhpcy5fdHJ5RW5hYmxlRWRpdG9yUHJldmlldygpO1xyXG4gICAgICAgIH1cclxuICAgIH1cclxuICAgIGdldCBrYXdhc2VCbG9vbU1hdGVyaWFsKCk6IE1hdGVyaWFsIHtcclxuICAgICAgICByZXR1cm4gdGhpcy5fc2V0dGluZ3MuYmxvb20ua2F3YXNlRmlsdGVyTWF0ZXJpYWwhO1xyXG4gICAgfVxyXG5cclxuICAgIEBwcm9wZXJ0eSh7XHJcbiAgICAgICAgZ3JvdXA6IHsgaWQ6ICdCbG9vbScsIG5hbWU6ICdCbG9vbSAoUG9zdFByb2Nlc3NpbmcpJywgc3R5bGU6ICdzZWN0aW9uJyB9LFxyXG4gICAgICAgIHR5cGU6IE1hdGVyaWFsLFxyXG4gICAgfSlcclxuICAgIHNldCBtaXBtYXBCbG9vbU1hdGVyaWFsKHZhbHVlOiBNYXRlcmlhbCkge1xyXG4gICAgICAgIGlmICh0aGlzLl9zZXR0aW5ncy5ibG9vbS5taXBtYXBGaWx0ZXJNYXRlcmlhbCA9PT0gdmFsdWUpIHtcclxuICAgICAgICAgICAgcmV0dXJuO1xyXG4gICAgICAgIH1cclxuICAgICAgICB0aGlzLl9zZXR0aW5ncy5ibG9vbS5taXBtYXBGaWx0ZXJNYXRlcmlhbCA9IHZhbHVlO1xyXG4gICAgICAgIGlmIChFRElUT1IpIHtcclxuICAgICAgICAgICAgdGhpcy5fdHJ5RW5hYmxlRWRpdG9yUHJldmlldygpO1xyXG4gICAgICAgIH1cclxuICAgIH1cclxuICAgIGdldCBtaXBtYXBCbG9vbU1hdGVyaWFsKCk6IE1hdGVyaWFsIHtcclxuICAgICAgICByZXR1cm4gdGhpcy5fc2V0dGluZ3MuYmxvb20ubWlwbWFwRmlsdGVyTWF0ZXJpYWwhO1xyXG4gICAgfVxyXG5cclxuICAgIEBwcm9wZXJ0eSh7XHJcbiAgICAgICAgdG9vbHRpcDogJ2kxOG46Ymxvb20uZW5hYmxlQWxwaGFNYXNrJyxcclxuICAgICAgICBncm91cDogeyBpZDogJ0Jsb29tJywgbmFtZTogJ0Jsb29tIChQb3N0UHJvY2Vzc2luZyknLCBzdHlsZTogJ3NlY3Rpb24nIH0sXHJcbiAgICAgICAgdHlwZTogQ0NCb29sZWFuLFxyXG4gICAgfSlcclxuICAgIHNldCBibG9vbUVuYWJsZUFscGhhTWFzayh2YWx1ZTogYm9vbGVhbikge1xyXG4gICAgICAgIHRoaXMuX3NldHRpbmdzLmJsb29tLmVuYWJsZUFscGhhTWFzayA9IHZhbHVlO1xyXG4gICAgICAgIGlmIChFRElUT1IpIHtcclxuICAgICAgICAgICAgdGhpcy5fdHJ5RW5hYmxlRWRpdG9yUHJldmlldygpO1xyXG4gICAgICAgIH1cclxuICAgIH1cclxuICAgIGdldCBibG9vbUVuYWJsZUFscGhhTWFzaygpOiBib29sZWFuIHtcclxuICAgICAgICByZXR1cm4gdGhpcy5fc2V0dGluZ3MuYmxvb20uZW5hYmxlQWxwaGFNYXNrO1xyXG4gICAgfVxyXG5cclxuICAgIEBwcm9wZXJ0eSh7XHJcbiAgICAgICAgdG9vbHRpcDogJ2kxOG46Ymxvb20uaXRlcmF0aW9ucycsXHJcbiAgICAgICAgZ3JvdXA6IHsgaWQ6ICdCbG9vbScsIG5hbWU6ICdCbG9vbSAoUG9zdFByb2Nlc3NpbmcpJywgc3R5bGU6ICdzZWN0aW9uJyB9LFxyXG4gICAgICAgIHR5cGU6IENDSW50ZWdlcixcclxuICAgICAgICByYW5nZTogWzEsIDYsIDFdLFxyXG4gICAgICAgIHNsaWRlOiB0cnVlLFxyXG4gICAgfSlcclxuICAgIHNldCBibG9vbUl0ZXJhdGlvbnModmFsdWU6IG51bWJlcikge1xyXG4gICAgICAgIHRoaXMuX3NldHRpbmdzLmJsb29tLml0ZXJhdGlvbnMgPSB2YWx1ZTtcclxuICAgICAgICBpZiAoRURJVE9SKSB7XHJcbiAgICAgICAgICAgIHRoaXMuX3RyeUVuYWJsZUVkaXRvclByZXZpZXcoKTtcclxuICAgICAgICB9XHJcbiAgICB9XHJcbiAgICBnZXQgYmxvb21JdGVyYXRpb25zKCk6IG51bWJlciB7XHJcbiAgICAgICAgcmV0dXJuIHRoaXMuX3NldHRpbmdzLmJsb29tLml0ZXJhdGlvbnM7XHJcbiAgICB9XHJcblxyXG4gICAgQHByb3BlcnR5KHtcclxuICAgICAgICB0b29sdGlwOiAnaTE4bjpibG9vbS50aHJlc2hvbGQnLFxyXG4gICAgICAgIGdyb3VwOiB7IGlkOiAnQmxvb20nLCBuYW1lOiAnQmxvb20gKFBvc3RQcm9jZXNzaW5nKScsIHN0eWxlOiAnc2VjdGlvbicgfSxcclxuICAgICAgICB0eXBlOiBDQ0Zsb2F0LFxyXG4gICAgICAgIG1pbjogMCxcclxuICAgIH0pXHJcbiAgICBzZXQgYmxvb21UaHJlc2hvbGQodmFsdWU6IG51bWJlcikge1xyXG4gICAgICAgIHRoaXMuX3NldHRpbmdzLmJsb29tLnRocmVzaG9sZCA9IHZhbHVlO1xyXG4gICAgfVxyXG4gICAgZ2V0IGJsb29tVGhyZXNob2xkKCk6IG51bWJlciB7XHJcbiAgICAgICAgcmV0dXJuIHRoaXMuX3NldHRpbmdzLmJsb29tLnRocmVzaG9sZDtcclxuICAgIH1cclxuXHJcbiAgICBAdHlwZShDQ0Zsb2F0KVxyXG4gICAgQHByb3BlcnR5KHtcclxuICAgICAgICBncm91cDogeyBpZDogJ0Jsb29tJywgbmFtZTogJ0Jsb29tIChQb3N0UHJvY2Vzc2luZyknLCBzdHlsZTogJ3NlY3Rpb24nIH0sXHJcbiAgICB9KVxyXG4gICAgc2V0IGJsb29tSW50ZW5zaXR5KHZhbHVlOiBudW1iZXIpIHtcclxuICAgICAgICB0aGlzLl9zZXR0aW5ncy5ibG9vbS5pbnRlbnNpdHkgPSB2YWx1ZTtcclxuICAgICAgICBpZiAoRURJVE9SKSB7XHJcbiAgICAgICAgICAgIHRoaXMuX3RyeUVuYWJsZUVkaXRvclByZXZpZXcoKTtcclxuICAgICAgICB9XHJcbiAgICB9XHJcbiAgICBnZXQgYmxvb21JbnRlbnNpdHkoKTogbnVtYmVyIHtcclxuICAgICAgICByZXR1cm4gdGhpcy5fc2V0dGluZ3MuYmxvb20uaW50ZW5zaXR5O1xyXG4gICAgfVxyXG5cclxuICAgIC8vIENvbG9yIEdyYWRpbmcgKExEUilcclxuICAgIEBwcm9wZXJ0eSh7XHJcbiAgICAgICAgZ3JvdXA6IHsgaWQ6ICdDb2xvciBHcmFkaW5nJywgbmFtZTogJ0NvbG9yR3JhZGluZyAoTERSKSAoUG9zdFByb2Nlc3NpbmcpJywgc3R5bGU6ICdzZWN0aW9uJyB9LFxyXG4gICAgICAgIHR5cGU6IENDQm9vbGVhbixcclxuICAgIH0pXHJcbiAgICBzZXQgY29sb3JHcmFkaW5nRW5hYmxlKHZhbHVlOiBib29sZWFuKSB7XHJcbiAgICAgICAgdGhpcy5fc2V0dGluZ3MuY29sb3JHcmFkaW5nLmVuYWJsZWQgPSB2YWx1ZTtcclxuICAgICAgICBpZiAoRURJVE9SKSB7XHJcbiAgICAgICAgICAgIHRoaXMuX3RyeUVuYWJsZUVkaXRvclByZXZpZXcoKTtcclxuICAgICAgICB9XHJcbiAgICB9XHJcbiAgICBnZXQgY29sb3JHcmFkaW5nRW5hYmxlKCk6IGJvb2xlYW4ge1xyXG4gICAgICAgIHJldHVybiB0aGlzLl9zZXR0aW5ncy5jb2xvckdyYWRpbmcuZW5hYmxlZDtcclxuICAgIH1cclxuXHJcbiAgICBAcHJvcGVydHkoe1xyXG4gICAgICAgIGdyb3VwOiB7IGlkOiAnQ29sb3IgR3JhZGluZycsIG5hbWU6ICdDb2xvckdyYWRpbmcgKExEUikgKFBvc3RQcm9jZXNzaW5nKScsIHN0eWxlOiAnc2VjdGlvbicgfSxcclxuICAgICAgICB0eXBlOiBNYXRlcmlhbCxcclxuICAgIH0pXHJcbiAgICBzZXQgY29sb3JHcmFkaW5nTWF0ZXJpYWwodmFsdWU6IE1hdGVyaWFsKSB7XHJcbiAgICAgICAgaWYgKHRoaXMuX3NldHRpbmdzLmNvbG9yR3JhZGluZy5tYXRlcmlhbCA9PT0gdmFsdWUpIHtcclxuICAgICAgICAgICAgcmV0dXJuO1xyXG4gICAgICAgIH1cclxuICAgICAgICB0aGlzLl9zZXR0aW5ncy5jb2xvckdyYWRpbmcubWF0ZXJpYWwgPSB2YWx1ZTtcclxuICAgICAgICBpZiAoRURJVE9SKSB7XHJcbiAgICAgICAgICAgIHRoaXMuX3RyeUVuYWJsZUVkaXRvclByZXZpZXcoKTtcclxuICAgICAgICB9XHJcbiAgICB9XHJcbiAgICBnZXQgY29sb3JHcmFkaW5nTWF0ZXJpYWwoKTogTWF0ZXJpYWwge1xyXG4gICAgICAgIHJldHVybiB0aGlzLl9zZXR0aW5ncy5jb2xvckdyYWRpbmcubWF0ZXJpYWwhO1xyXG4gICAgfVxyXG5cclxuICAgIEBwcm9wZXJ0eSh7XHJcbiAgICAgICAgdG9vbHRpcDogJ2kxOG46Y29sb3JfZ3JhZGluZy5jb250cmlidXRlJyxcclxuICAgICAgICBncm91cDogeyBpZDogJ0NvbG9yIEdyYWRpbmcnLCBuYW1lOiAnQ29sb3JHcmFkaW5nIChMRFIpIChQb3N0UHJvY2Vzc2luZyknLCBzdHlsZTogJ3NlY3Rpb24nIH0sXHJcbiAgICAgICAgdHlwZTogQ0NGbG9hdCxcclxuICAgICAgICByYW5nZTogWzAsIDEsIDAuMDFdLFxyXG4gICAgICAgIHNsaWRlOiB0cnVlLFxyXG4gICAgfSlcclxuICAgIHNldCBjb2xvckdyYWRpbmdDb250cmlidXRlKHZhbHVlOiBudW1iZXIpIHtcclxuICAgICAgICB0aGlzLl9zZXR0aW5ncy5jb2xvckdyYWRpbmcuY29udHJpYnV0ZSA9IHZhbHVlO1xyXG4gICAgfVxyXG4gICAgZ2V0IGNvbG9yR3JhZGluZ0NvbnRyaWJ1dGUoKTogbnVtYmVyIHtcclxuICAgICAgICByZXR1cm4gdGhpcy5fc2V0dGluZ3MuY29sb3JHcmFkaW5nLmNvbnRyaWJ1dGU7XHJcbiAgICB9XHJcblxyXG4gICAgQHByb3BlcnR5KHtcclxuICAgICAgICB0b29sdGlwOiAnaTE4bjpjb2xvcl9ncmFkaW5nLm9yaWdpbmFsTWFwJyxcclxuICAgICAgICBncm91cDogeyBpZDogJ0NvbG9yIEdyYWRpbmcnLCBuYW1lOiAnQ29sb3JHcmFkaW5nIChMRFIpIChQb3N0UHJvY2Vzc2luZyknLCBzdHlsZTogJ3NlY3Rpb24nIH0sXHJcbiAgICAgICAgdHlwZTogVGV4dHVyZTJELFxyXG4gICAgfSlcclxuICAgIHNldCBjb2xvckdyYWRpbmdNYXAodmFsOiBUZXh0dXJlMkQpIHtcclxuICAgICAgICB0aGlzLl9zZXR0aW5ncy5jb2xvckdyYWRpbmcuY29sb3JHcmFkaW5nTWFwID0gdmFsO1xyXG4gICAgICAgIGlmIChFRElUT1IpIHtcclxuICAgICAgICAgICAgdGhpcy5fdHJ5RW5hYmxlRWRpdG9yUHJldmlldygpO1xyXG4gICAgICAgIH1cclxuICAgIH1cclxuICAgIGdldCBjb2xvckdyYWRpbmdNYXAoKTogVGV4dHVyZTJEIHtcclxuICAgICAgICByZXR1cm4gdGhpcy5fc2V0dGluZ3MuY29sb3JHcmFkaW5nLmNvbG9yR3JhZGluZ01hcCE7XHJcbiAgICB9XHJcblxyXG4gICAgLy8gRlhBQVxyXG4gICAgQHByb3BlcnR5KHtcclxuICAgICAgICBncm91cDogeyBpZDogJ0ZYQUEnLCBuYW1lOiAnRmFzdCBBcHByb3hpbWF0ZSBBbnRpLUFsaWFzaW5nIChQb3N0UHJvY2Vzc2luZyknLCBzdHlsZTogJ3NlY3Rpb24nIH0sXHJcbiAgICAgICAgdHlwZTogQ0NCb29sZWFuLFxyXG4gICAgfSlcclxuICAgIHNldCBmeGFhRW5hYmxlKHZhbHVlOiBib29sZWFuKSB7XHJcbiAgICAgICAgdGhpcy5fc2V0dGluZ3MuZnhhYS5lbmFibGVkID0gdmFsdWU7XHJcbiAgICAgICAgaWYgKEVESVRPUikge1xyXG4gICAgICAgICAgICB0aGlzLl90cnlFbmFibGVFZGl0b3JQcmV2aWV3KCk7XHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG4gICAgZ2V0IGZ4YWFFbmFibGUoKTogYm9vbGVhbiB7XHJcbiAgICAgICAgcmV0dXJuIHRoaXMuX3NldHRpbmdzLmZ4YWEuZW5hYmxlZDtcclxuICAgIH1cclxuXHJcbiAgICBAcHJvcGVydHkoe1xyXG4gICAgICAgIGdyb3VwOiB7IGlkOiAnRlhBQScsIG5hbWU6ICdGYXN0IEFwcHJveGltYXRlIEFudGktQWxpYXNpbmcgKFBvc3RQcm9jZXNzaW5nKScsIHN0eWxlOiAnc2VjdGlvbicgfSxcclxuICAgICAgICB0eXBlOiBNYXRlcmlhbCxcclxuICAgIH0pXHJcbiAgICBzZXQgZnhhYU1hdGVyaWFsKHZhbHVlOiBNYXRlcmlhbCkge1xyXG4gICAgICAgIGlmICh0aGlzLl9zZXR0aW5ncy5meGFhLm1hdGVyaWFsID09PSB2YWx1ZSkge1xyXG4gICAgICAgICAgICByZXR1cm47XHJcbiAgICAgICAgfVxyXG4gICAgICAgIHRoaXMuX3NldHRpbmdzLmZ4YWEubWF0ZXJpYWwgPSB2YWx1ZTtcclxuICAgICAgICBpZiAoRURJVE9SKSB7XHJcbiAgICAgICAgICAgIHRoaXMuX3RyeUVuYWJsZUVkaXRvclByZXZpZXcoKTtcclxuICAgICAgICB9XHJcbiAgICB9XHJcbiAgICBnZXQgZnhhYU1hdGVyaWFsKCk6IE1hdGVyaWFsIHtcclxuICAgICAgICByZXR1cm4gdGhpcy5fc2V0dGluZ3MuZnhhYS5tYXRlcmlhbCE7XHJcbiAgICB9XHJcblxyXG4gICAgLy8gRlNSXHJcbiAgICBAcHJvcGVydHkoe1xyXG4gICAgICAgIGdyb3VwOiB7IGlkOiAnRlNSJywgbmFtZTogJ0ZpZGVsaXR5RlggU3VwZXIgUmVzb2x1dGlvbicsIHN0eWxlOiAnc2VjdGlvbicgfSxcclxuICAgICAgICB0eXBlOiBDQ0Jvb2xlYW4sXHJcbiAgICB9KVxyXG4gICAgc2V0IGZzckVuYWJsZSh2YWx1ZTogYm9vbGVhbikge1xyXG4gICAgICAgIHRoaXMuX3NldHRpbmdzLmZzci5lbmFibGVkID0gdmFsdWU7XHJcbiAgICAgICAgaWYgKEVESVRPUikge1xyXG4gICAgICAgICAgICB0aGlzLl90cnlFbmFibGVFZGl0b3JQcmV2aWV3KCk7XHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG4gICAgZ2V0IGZzckVuYWJsZSgpOiBib29sZWFuIHtcclxuICAgICAgICByZXR1cm4gdGhpcy5fc2V0dGluZ3MuZnNyLmVuYWJsZWQ7XHJcbiAgICB9XHJcblxyXG4gICAgQHByb3BlcnR5KHtcclxuICAgICAgICBncm91cDogeyBpZDogJ0ZTUicsIG5hbWU6ICdGaWRlbGl0eUZYIFN1cGVyIFJlc29sdXRpb24nLCBzdHlsZTogJ3NlY3Rpb24nIH0sXHJcbiAgICAgICAgdHlwZTogTWF0ZXJpYWwsXHJcbiAgICB9KVxyXG4gICAgc2V0IGZzck1hdGVyaWFsKHZhbHVlOiBNYXRlcmlhbCkge1xyXG4gICAgICAgIGlmICh0aGlzLl9zZXR0aW5ncy5mc3IubWF0ZXJpYWwgPT09IHZhbHVlKSB7XHJcbiAgICAgICAgICAgIHJldHVybjtcclxuICAgICAgICB9XHJcbiAgICAgICAgdGhpcy5fc2V0dGluZ3MuZnNyLm1hdGVyaWFsID0gdmFsdWU7XHJcbiAgICAgICAgaWYgKEVESVRPUikge1xyXG4gICAgICAgICAgICB0aGlzLl90cnlFbmFibGVFZGl0b3JQcmV2aWV3KCk7XHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG4gICAgZ2V0IGZzck1hdGVyaWFsKCk6IE1hdGVyaWFsIHtcclxuICAgICAgICByZXR1cm4gdGhpcy5fc2V0dGluZ3MuZnNyLm1hdGVyaWFsITtcclxuICAgIH1cclxuXHJcbiAgICBAcHJvcGVydHkoe1xyXG4gICAgICAgIGdyb3VwOiB7IGlkOiAnRlNSJywgbmFtZTogJ0ZpZGVsaXR5RlggU3VwZXIgUmVzb2x1dGlvbicsIHN0eWxlOiAnc2VjdGlvbicgfSxcclxuICAgICAgICB0eXBlOiBDQ0Zsb2F0LFxyXG4gICAgICAgIHJhbmdlOiBbMCwgMSwgMC4wMV0sXHJcbiAgICAgICAgc2xpZGU6IHRydWUsXHJcbiAgICB9KVxyXG4gICAgc2V0IGZzclNoYXJwbmVzcyh2YWx1ZTogbnVtYmVyKSB7XHJcbiAgICAgICAgdGhpcy5fc2V0dGluZ3MuZnNyLnNoYXJwbmVzcyA9IHZhbHVlO1xyXG4gICAgfVxyXG4gICAgZ2V0IGZzclNoYXJwbmVzcygpOiBudW1iZXIge1xyXG4gICAgICAgIHJldHVybiB0aGlzLl9zZXR0aW5ncy5mc3Iuc2hhcnBuZXNzO1xyXG4gICAgfVxyXG5cclxuICAgIEBwcm9wZXJ0eSh7XHJcbiAgICAgICAgZ3JvdXA6IHsgaWQ6ICdUb25lTWFwcGluZycsIG5hbWU6ICdUb25lTWFwcGluZycsIHN0eWxlOiAnc2VjdGlvbicgfSxcclxuICAgICAgICB0eXBlOiBNYXRlcmlhbCxcclxuICAgIH0pXHJcbiAgICBzZXQgdG9uZU1hcHBpbmdNYXRlcmlhbCh2YWx1ZTogTWF0ZXJpYWwpIHtcclxuICAgICAgICBpZiAodGhpcy5fc2V0dGluZ3MudG9uZU1hcHBpbmcubWF0ZXJpYWwgPT09IHZhbHVlKSB7XHJcbiAgICAgICAgICAgIHJldHVybjtcclxuICAgICAgICB9XHJcbiAgICAgICAgdGhpcy5fc2V0dGluZ3MudG9uZU1hcHBpbmcubWF0ZXJpYWwgPSB2YWx1ZTtcclxuICAgICAgICBpZiAoRURJVE9SKSB7XHJcbiAgICAgICAgICAgIHRoaXMuX3RyeUVuYWJsZUVkaXRvclByZXZpZXcoKTtcclxuICAgICAgICB9XHJcbiAgICB9XHJcbiAgICBnZXQgdG9uZU1hcHBpbmdNYXRlcmlhbCgpOiBNYXRlcmlhbCB7XHJcbiAgICAgICAgcmV0dXJuIHRoaXMuX3NldHRpbmdzLnRvbmVNYXBwaW5nLm1hdGVyaWFsITtcclxuICAgIH1cclxufVxyXG4iLCIvKlxyXG4gQ29weXJpZ2h0IChjKSAyMDIxLTIwMjQgWGlhbWVuIFlhamkgU29mdHdhcmUgQ28uLCBMdGQuXHJcblxyXG4gaHR0cHM6Ly93d3cuY29jb3MuY29tXHJcblxyXG4gUGVybWlzc2lvbiBpcyBoZXJlYnkgZ3JhbnRlZCwgZnJlZSBvZiBjaGFyZ2UsIHRvIGFueSBwZXJzb24gb2J0YWluaW5nIGEgY29weVxyXG4gb2YgdGhpcyBzb2Z0d2FyZSBhbmQgYXNzb2NpYXRlZCBkb2N1bWVudGF0aW9uIGZpbGVzICh0aGUgXCJTb2Z0d2FyZVwiKSwgdG8gZGVhbFxyXG4gaW4gdGhlIFNvZnR3YXJlIHdpdGhvdXQgcmVzdHJpY3Rpb24sIGluY2x1ZGluZyB3aXRob3V0IGxpbWl0YXRpb24gdGhlIHJpZ2h0cyB0b1xyXG4gdXNlLCBjb3B5LCBtb2RpZnksIG1lcmdlLCBwdWJsaXNoLCBkaXN0cmlidXRlLCBzdWJsaWNlbnNlLCBhbmQvb3Igc2VsbCBjb3BpZXNcclxuIG9mIHRoZSBTb2Z0d2FyZSwgYW5kIHRvIHBlcm1pdCBwZXJzb25zIHRvIHdob20gdGhlIFNvZnR3YXJlIGlzIGZ1cm5pc2hlZCB0byBkbyBzbyxcclxuIHN1YmplY3QgdG8gdGhlIGZvbGxvd2luZyBjb25kaXRpb25zOlxyXG5cclxuIFRoZSBhYm92ZSBjb3B5cmlnaHQgbm90aWNlIGFuZCB0aGlzIHBlcm1pc3Npb24gbm90aWNlIHNoYWxsIGJlIGluY2x1ZGVkIGluXHJcbiBhbGwgY29waWVzIG9yIHN1YnN0YW50aWFsIHBvcnRpb25zIG9mIHRoZSBTb2Z0d2FyZS5cclxuXHJcbiBUSEUgU09GVFdBUkUgSVMgUFJPVklERUQgXCJBUyBJU1wiLCBXSVRIT1VUIFdBUlJBTlRZIE9GIEFOWSBLSU5ELCBFWFBSRVNTIE9SXHJcbiBJTVBMSUVELCBJTkNMVURJTkcgQlVUIE5PVCBMSU1JVEVEIFRPIFRIRSBXQVJSQU5USUVTIE9GIE1FUkNIQU5UQUJJTElUWSxcclxuIEZJVE5FU1MgRk9SIEEgUEFSVElDVUxBUiBQVVJQT1NFIEFORCBOT05JTkZSSU5HRU1FTlQuIElOIE5PIEVWRU5UIFNIQUxMIFRIRVxyXG4gQVVUSE9SUyBPUiBDT1BZUklHSFQgSE9MREVSUyBCRSBMSUFCTEUgRk9SIEFOWSBDTEFJTSwgREFNQUdFUyBPUiBPVEhFUlxyXG4gTElBQklMSVRZLCBXSEVUSEVSIElOIEFOIEFDVElPTiBPRiBDT05UUkFDVCwgVE9SVCBPUiBPVEhFUldJU0UsIEFSSVNJTkcgRlJPTSxcclxuIE9VVCBPRiBPUiBJTiBDT05ORUNUSU9OIFdJVEggVEhFIFNPRlRXQVJFIE9SIFRIRSBVU0UgT1IgT1RIRVIgREVBTElOR1MgSU5cclxuIFRIRSBTT0ZUV0FSRS5cclxuKi9cclxuXHJcbi8qKlxyXG4gKiA9PT09PT09PT09PT09PT09PT09PT09PT09ICFETyBOT1QgQ0hBTkdFIFRIRSBGT0xMT1dJTkcgU0VDVElPTiBNQU5VQUxMWSEgPT09PT09PT09PT09PT09PT09PT09PT09PVxyXG4gKiBUaGUgZm9sbG93aW5nIHNlY3Rpb24gaXMgYXV0by1nZW5lcmF0ZWQuXHJcbiAqID09PT09PT09PT09PT09PT09PT09PT09PT0gIURPIE5PVCBDSEFOR0UgVEhFIEZPTExPV0lORyBTRUNUSU9OIE1BTlVBTExZISA9PT09PT09PT09PT09PT09PT09PT09PT09XHJcbiAqL1xyXG4vKiBlc2xpbnQtZGlzYWJsZSBtYXgtbGVuICovXHJcbmltcG9ydCB7IE1hdGVyaWFsLCBUZXh0dXJlMkQsIGNjZW51bSwgZ2Z4IH0gZnJvbSAnY2MnO1xyXG5cclxuY29uc3QgeyBTYW1wbGVDb3VudCB9ID0gZ2Z4O1xyXG5cclxuZXhwb3J0IGludGVyZmFjZSBNU0FBIHtcclxuICAgIGVuYWJsZWQ6IGJvb2xlYW47IC8qIGZhbHNlICovXHJcbiAgICBzYW1wbGVDb3VudDogZ2Z4LlNhbXBsZUNvdW50OyAvKiBTYW1wbGVDb3VudC5YNCAqL1xyXG4gICAgW25hbWU6IHN0cmluZ106IHVua25vd247XHJcbn1cclxuXHJcbmV4cG9ydCBmdW5jdGlvbiBtYWtlTVNBQSgpOiBNU0FBIHtcclxuICAgIHJldHVybiB7XHJcbiAgICAgICAgZW5hYmxlZDogZmFsc2UsXHJcbiAgICAgICAgc2FtcGxlQ291bnQ6IFNhbXBsZUNvdW50Llg0LFxyXG4gICAgfTtcclxufVxyXG5cclxuZXhwb3J0IGZ1bmN0aW9uIGZpbGxSZXF1aXJlZE1TQUEodmFsdWU6IE1TQUEpOiB2b2lkIHtcclxuICAgIGlmICh2YWx1ZS5lbmFibGVkID09PSB1bmRlZmluZWQpIHtcclxuICAgICAgICB2YWx1ZS5lbmFibGVkID0gZmFsc2U7XHJcbiAgICB9XHJcbiAgICBpZiAodmFsdWUuc2FtcGxlQ291bnQgPT09IHVuZGVmaW5lZCkge1xyXG4gICAgICAgIHZhbHVlLnNhbXBsZUNvdW50ID0gU2FtcGxlQ291bnQuWDQ7XHJcbiAgICB9XHJcbn1cclxuXHJcbmV4cG9ydCBpbnRlcmZhY2UgRm9yd2FyZFBhc3NDb25maWdzIHtcclxuICAgIGVuYWJsZU1haW5MaWdodFNoYWRvd01hcDogYm9vbGVhbjsgLyogZmFsc2UgKi9cclxuICAgIGVuYWJsZU1haW5MaWdodFBsYW5hclNoYWRvd01hcDogYm9vbGVhbjsgLyogZmFsc2UgKi9cclxuICAgIGVuYWJsZVBsYW5hclJlZmxlY3Rpb25Qcm9iZTogYm9vbGVhbjsgLyogZmFsc2UgKi9cclxuICAgIGVuYWJsZU1TQUE6IGJvb2xlYW47IC8qIGZhbHNlICovXHJcbiAgICBlbmFibGVTaW5nbGVGb3J3YXJkUGFzczogYm9vbGVhbjsgLyogZmFsc2UgKi9cclxuICAgIFtuYW1lOiBzdHJpbmddOiB1bmtub3duO1xyXG59XHJcblxyXG5leHBvcnQgaW50ZXJmYWNlIEhCQU8ge1xyXG4gICAgZW5hYmxlZDogYm9vbGVhbjsgLyogZmFsc2UgKi9cclxuICAgIHJhZGl1c1NjYWxlOiBudW1iZXI7IC8qIDEgKi9cclxuICAgIGFuZ2xlQmlhc0RlZ3JlZTogbnVtYmVyOyAvKiAxMCAqL1xyXG4gICAgYmx1clNoYXJwbmVzczogbnVtYmVyOyAvKiAzICovXHJcbiAgICBhb1NhdHVyYXRpb246IG51bWJlcjsgLyogMSAqL1xyXG4gICAgbmVlZEJsdXI6IGJvb2xlYW47IC8qIGZhbHNlICovXHJcbiAgICBbbmFtZTogc3RyaW5nXTogdW5rbm93bjtcclxufVxyXG5cclxuZXhwb3J0IGZ1bmN0aW9uIG1ha2VIQkFPKCk6IEhCQU8ge1xyXG4gICAgcmV0dXJuIHtcclxuICAgICAgICBlbmFibGVkOiBmYWxzZSxcclxuICAgICAgICByYWRpdXNTY2FsZTogMSxcclxuICAgICAgICBhbmdsZUJpYXNEZWdyZWU6IDEwLFxyXG4gICAgICAgIGJsdXJTaGFycG5lc3M6IDMsXHJcbiAgICAgICAgYW9TYXR1cmF0aW9uOiAxLFxyXG4gICAgICAgIG5lZWRCbHVyOiBmYWxzZSxcclxuICAgIH07XHJcbn1cclxuXHJcbmV4cG9ydCBmdW5jdGlvbiBmaWxsUmVxdWlyZWRIQkFPKHZhbHVlOiBIQkFPKTogdm9pZCB7XHJcbiAgICBpZiAodmFsdWUuZW5hYmxlZCA9PT0gdW5kZWZpbmVkKSB7XHJcbiAgICAgICAgdmFsdWUuZW5hYmxlZCA9IGZhbHNlO1xyXG4gICAgfVxyXG4gICAgaWYgKHZhbHVlLnJhZGl1c1NjYWxlID09PSB1bmRlZmluZWQpIHtcclxuICAgICAgICB2YWx1ZS5yYWRpdXNTY2FsZSA9IDE7XHJcbiAgICB9XHJcbiAgICBpZiAodmFsdWUuYW5nbGVCaWFzRGVncmVlID09PSB1bmRlZmluZWQpIHtcclxuICAgICAgICB2YWx1ZS5hbmdsZUJpYXNEZWdyZWUgPSAxMDtcclxuICAgIH1cclxuICAgIGlmICh2YWx1ZS5ibHVyU2hhcnBuZXNzID09PSB1bmRlZmluZWQpIHtcclxuICAgICAgICB2YWx1ZS5ibHVyU2hhcnBuZXNzID0gMztcclxuICAgIH1cclxuICAgIGlmICh2YWx1ZS5hb1NhdHVyYXRpb24gPT09IHVuZGVmaW5lZCkge1xyXG4gICAgICAgIHZhbHVlLmFvU2F0dXJhdGlvbiA9IDE7XHJcbiAgICB9XHJcbiAgICBpZiAodmFsdWUubmVlZEJsdXIgPT09IHVuZGVmaW5lZCkge1xyXG4gICAgICAgIHZhbHVlLm5lZWRCbHVyID0gZmFsc2U7XHJcbiAgICB9XHJcbn1cclxuXHJcbmV4cG9ydCBlbnVtIEJsb29tVHlwZSB7XHJcbiAgICBLYXdhc2VEdWFsRmlsdGVyLFxyXG4gICAgTWlwbWFwRmlsdGVyLFxyXG59XHJcbmNjZW51bShCbG9vbVR5cGUpO1xyXG5cclxuZXhwb3J0IGludGVyZmFjZSBCbG9vbSB7XHJcbiAgICBlbmFibGVkOiBib29sZWFuOyAvKiBmYWxzZSAqL1xyXG4gICAgdHlwZTogQmxvb21UeXBlOyAvKiBCbG9vbVR5cGUuS2F3YXNlRHVhbEZpbHRlciAqL1xyXG4gICAgLyogcmVmY291bnQgKi8gbWF0ZXJpYWw6IE1hdGVyaWFsIHwgbnVsbDtcclxuICAgIC8qIHJlZmNvdW50ICovIGthd2FzZUZpbHRlck1hdGVyaWFsOiBNYXRlcmlhbCB8IG51bGw7XHJcbiAgICAvKiByZWZjb3VudCAqLyBtaXBtYXBGaWx0ZXJNYXRlcmlhbDogTWF0ZXJpYWwgfCBudWxsO1xyXG4gICAgZW5hYmxlQWxwaGFNYXNrOiBib29sZWFuOyAvKiBmYWxzZSAqL1xyXG4gICAgaXRlcmF0aW9uczogbnVtYmVyOyAvKiAzICovXHJcbiAgICB0aHJlc2hvbGQ6IG51bWJlcjsgLyogMC44ICovXHJcbiAgICBpbnRlbnNpdHk6IG51bWJlcjsgLyogMSAqL1xyXG4gICAgW25hbWU6IHN0cmluZ106IHVua25vd247XHJcbn1cclxuXHJcbmV4cG9ydCBmdW5jdGlvbiBtYWtlQmxvb20oKTogQmxvb20ge1xyXG4gICAgcmV0dXJuIHtcclxuICAgICAgICBlbmFibGVkOiBmYWxzZSxcclxuICAgICAgICB0eXBlOiBCbG9vbVR5cGUuS2F3YXNlRHVhbEZpbHRlcixcclxuICAgICAgICBtYXRlcmlhbDogbnVsbCxcclxuICAgICAgICBrYXdhc2VGaWx0ZXJNYXRlcmlhbDogbnVsbCxcclxuICAgICAgICBtaXBtYXBGaWx0ZXJNYXRlcmlhbDogbnVsbCxcclxuICAgICAgICBlbmFibGVBbHBoYU1hc2s6IGZhbHNlLFxyXG4gICAgICAgIGl0ZXJhdGlvbnM6IDMsXHJcbiAgICAgICAgdGhyZXNob2xkOiAwLjgsXHJcbiAgICAgICAgaW50ZW5zaXR5OiAxLFxyXG4gICAgfTtcclxufVxyXG5cclxuZXhwb3J0IGZ1bmN0aW9uIGZpbGxSZXF1aXJlZEJsb29tKHZhbHVlOiBCbG9vbSk6IHZvaWQge1xyXG4gICAgaWYgKHZhbHVlLmVuYWJsZWQgPT09IHVuZGVmaW5lZCkge1xyXG4gICAgICAgIHZhbHVlLmVuYWJsZWQgPSBmYWxzZTtcclxuICAgIH1cclxuICAgIGlmICh2YWx1ZS50eXBlID09PSB1bmRlZmluZWQpIHtcclxuICAgICAgICB2YWx1ZS50eXBlID0gQmxvb21UeXBlLkthd2FzZUR1YWxGaWx0ZXI7XHJcbiAgICB9XHJcbiAgICBpZiAodmFsdWUubWF0ZXJpYWwgPT09IHVuZGVmaW5lZCkge1xyXG4gICAgICAgIHZhbHVlLm1hdGVyaWFsID0gbnVsbDtcclxuICAgIH1cclxuICAgIGlmICh2YWx1ZS5rYXdhc2VGaWx0ZXJNYXRlcmlhbCA9PT0gdW5kZWZpbmVkKSB7XHJcbiAgICAgICAgdmFsdWUua2F3YXNlRmlsdGVyTWF0ZXJpYWwgPSB2YWx1ZS5tYXRlcmlhbCB8fCBudWxsO1xyXG4gICAgfVxyXG4gICAgaWYgKHZhbHVlLm1pcG1hcEZpbHRlck1hdGVyaWFsID09PSB1bmRlZmluZWQpIHtcclxuICAgICAgICB2YWx1ZS5taXBtYXBGaWx0ZXJNYXRlcmlhbCA9IG51bGw7XHJcbiAgICB9XHJcbiAgICBpZiAodmFsdWUuZW5hYmxlQWxwaGFNYXNrID09PSB1bmRlZmluZWQpIHtcclxuICAgICAgICB2YWx1ZS5lbmFibGVBbHBoYU1hc2sgPSBmYWxzZTtcclxuICAgIH1cclxuICAgIGlmICh2YWx1ZS5pdGVyYXRpb25zID09PSB1bmRlZmluZWQpIHtcclxuICAgICAgICB2YWx1ZS5pdGVyYXRpb25zID0gMztcclxuICAgIH1cclxuICAgIGlmICh2YWx1ZS50aHJlc2hvbGQgPT09IHVuZGVmaW5lZCkge1xyXG4gICAgICAgIHZhbHVlLnRocmVzaG9sZCA9IDAuODtcclxuICAgIH1cclxuICAgIGlmICh2YWx1ZS5pbnRlbnNpdHkgPT09IHVuZGVmaW5lZCkge1xyXG4gICAgICAgIHZhbHVlLmludGVuc2l0eSA9IDE7XHJcbiAgICB9XHJcbn1cclxuXHJcbmV4cG9ydCBpbnRlcmZhY2UgQ29sb3JHcmFkaW5nIHtcclxuICAgIGVuYWJsZWQ6IGJvb2xlYW47IC8qIGZhbHNlICovXHJcbiAgICAvKiByZWZjb3VudCAqLyBtYXRlcmlhbDogTWF0ZXJpYWwgfCBudWxsO1xyXG4gICAgY29udHJpYnV0ZTogbnVtYmVyOyAvKiAxICovXHJcbiAgICAvKiByZWZjb3VudCAqLyBjb2xvckdyYWRpbmdNYXA6IFRleHR1cmUyRCB8IG51bGw7XHJcbiAgICBbbmFtZTogc3RyaW5nXTogdW5rbm93bjtcclxufVxyXG5cclxuZXhwb3J0IGZ1bmN0aW9uIG1ha2VDb2xvckdyYWRpbmcoKTogQ29sb3JHcmFkaW5nIHtcclxuICAgIHJldHVybiB7XHJcbiAgICAgICAgZW5hYmxlZDogZmFsc2UsXHJcbiAgICAgICAgbWF0ZXJpYWw6IG51bGwsXHJcbiAgICAgICAgY29udHJpYnV0ZTogMSxcclxuICAgICAgICBjb2xvckdyYWRpbmdNYXA6IG51bGwsXHJcbiAgICB9O1xyXG59XHJcblxyXG5leHBvcnQgZnVuY3Rpb24gZmlsbFJlcXVpcmVkQ29sb3JHcmFkaW5nKHZhbHVlOiBDb2xvckdyYWRpbmcpOiB2b2lkIHtcclxuICAgIGlmICh2YWx1ZS5lbmFibGVkID09PSB1bmRlZmluZWQpIHtcclxuICAgICAgICB2YWx1ZS5lbmFibGVkID0gZmFsc2U7XHJcbiAgICB9XHJcbiAgICBpZiAodmFsdWUubWF0ZXJpYWwgPT09IHVuZGVmaW5lZCkge1xyXG4gICAgICAgIHZhbHVlLm1hdGVyaWFsID0gbnVsbDtcclxuICAgIH1cclxuICAgIGlmICh2YWx1ZS5jb250cmlidXRlID09PSB1bmRlZmluZWQpIHtcclxuICAgICAgICB2YWx1ZS5jb250cmlidXRlID0gMTtcclxuICAgIH1cclxuICAgIGlmICh2YWx1ZS5jb2xvckdyYWRpbmdNYXAgPT09IHVuZGVmaW5lZCkge1xyXG4gICAgICAgIHZhbHVlLmNvbG9yR3JhZGluZ01hcCA9IG51bGw7XHJcbiAgICB9XHJcbn1cclxuXHJcbmV4cG9ydCBpbnRlcmZhY2UgRlNSIHtcclxuICAgIGVuYWJsZWQ6IGJvb2xlYW47IC8qIGZhbHNlICovXHJcbiAgICAvKiByZWZjb3VudCAqLyBtYXRlcmlhbDogTWF0ZXJpYWwgfCBudWxsO1xyXG4gICAgc2hhcnBuZXNzOiBudW1iZXI7IC8qIDAuOCAqL1xyXG4gICAgW25hbWU6IHN0cmluZ106IHVua25vd247XHJcbn1cclxuXHJcbmV4cG9ydCBmdW5jdGlvbiBtYWtlRlNSKCk6IEZTUiB7XHJcbiAgICByZXR1cm4ge1xyXG4gICAgICAgIGVuYWJsZWQ6IGZhbHNlLFxyXG4gICAgICAgIG1hdGVyaWFsOiBudWxsLFxyXG4gICAgICAgIHNoYXJwbmVzczogMC44LFxyXG4gICAgfTtcclxufVxyXG5cclxuZXhwb3J0IGZ1bmN0aW9uIGZpbGxSZXF1aXJlZEZTUih2YWx1ZTogRlNSKTogdm9pZCB7XHJcbiAgICBpZiAodmFsdWUuZW5hYmxlZCA9PT0gdW5kZWZpbmVkKSB7XHJcbiAgICAgICAgdmFsdWUuZW5hYmxlZCA9IGZhbHNlO1xyXG4gICAgfVxyXG4gICAgaWYgKHZhbHVlLm1hdGVyaWFsID09PSB1bmRlZmluZWQpIHtcclxuICAgICAgICB2YWx1ZS5tYXRlcmlhbCA9IG51bGw7XHJcbiAgICB9XHJcbiAgICBpZiAodmFsdWUuc2hhcnBuZXNzID09PSB1bmRlZmluZWQpIHtcclxuICAgICAgICB2YWx1ZS5zaGFycG5lc3MgPSAwLjg7XHJcbiAgICB9XHJcbn1cclxuXHJcbmV4cG9ydCBpbnRlcmZhY2UgRlhBQSB7XHJcbiAgICBlbmFibGVkOiBib29sZWFuOyAvKiBmYWxzZSAqL1xyXG4gICAgLyogcmVmY291bnQgKi8gbWF0ZXJpYWw6IE1hdGVyaWFsIHwgbnVsbDtcclxuICAgIFtuYW1lOiBzdHJpbmddOiB1bmtub3duO1xyXG59XHJcblxyXG5leHBvcnQgZnVuY3Rpb24gbWFrZUZYQUEoKTogRlhBQSB7XHJcbiAgICByZXR1cm4ge1xyXG4gICAgICAgIGVuYWJsZWQ6IGZhbHNlLFxyXG4gICAgICAgIG1hdGVyaWFsOiBudWxsLFxyXG4gICAgfTtcclxufVxyXG5cclxuZXhwb3J0IGZ1bmN0aW9uIGZpbGxSZXF1aXJlZEZYQUEodmFsdWU6IEZYQUEpOiB2b2lkIHtcclxuICAgIGlmICh2YWx1ZS5lbmFibGVkID09PSB1bmRlZmluZWQpIHtcclxuICAgICAgICB2YWx1ZS5lbmFibGVkID0gZmFsc2U7XHJcbiAgICB9XHJcbiAgICBpZiAodmFsdWUubWF0ZXJpYWwgPT09IHVuZGVmaW5lZCkge1xyXG4gICAgICAgIHZhbHVlLm1hdGVyaWFsID0gbnVsbDtcclxuICAgIH1cclxufVxyXG5cclxuZXhwb3J0IGludGVyZmFjZSBUb25lTWFwcGluZyB7XHJcbiAgICAvKiByZWZjb3VudCAqLyBtYXRlcmlhbDogTWF0ZXJpYWwgfCBudWxsO1xyXG4gICAgW25hbWU6IHN0cmluZ106IHVua25vd247XHJcbn1cclxuXHJcbmV4cG9ydCBmdW5jdGlvbiBtYWtlVG9uZU1hcHBpbmcoKTogVG9uZU1hcHBpbmcge1xyXG4gICAgcmV0dXJuIHtcclxuICAgICAgICBtYXRlcmlhbDogbnVsbCxcclxuICAgIH07XHJcbn1cclxuXHJcbmV4cG9ydCBmdW5jdGlvbiBmaWxsUmVxdWlyZWRUb25lTWFwcGluZyh2YWx1ZTogVG9uZU1hcHBpbmcpOiB2b2lkIHtcclxuICAgIGlmICh2YWx1ZS5tYXRlcmlhbCA9PT0gdW5kZWZpbmVkKSB7XHJcbiAgICAgICAgdmFsdWUubWF0ZXJpYWwgPSBudWxsO1xyXG4gICAgfVxyXG59XHJcblxyXG5leHBvcnQgaW50ZXJmYWNlIFBpcGVsaW5lU2V0dGluZ3Mge1xyXG4gICAgcmVhZG9ubHkgbXNhYTogTVNBQTtcclxuICAgIGVuYWJsZVNoYWRpbmdTY2FsZTogYm9vbGVhbjsgLyogZmFsc2UgKi9cclxuICAgIHNoYWRpbmdTY2FsZTogbnVtYmVyOyAvKiAwLjUgKi9cclxuICAgIHJlYWRvbmx5IGJsb29tOiBCbG9vbTtcclxuICAgIHJlYWRvbmx5IHRvbmVNYXBwaW5nOiBUb25lTWFwcGluZztcclxuICAgIHJlYWRvbmx5IGNvbG9yR3JhZGluZzogQ29sb3JHcmFkaW5nO1xyXG4gICAgcmVhZG9ubHkgZnNyOiBGU1I7XHJcbiAgICByZWFkb25seSBmeGFhOiBGWEFBO1xyXG4gICAgW25hbWU6IHN0cmluZ106IHVua25vd247XHJcbn1cclxuXHJcbmV4cG9ydCBmdW5jdGlvbiBtYWtlUGlwZWxpbmVTZXR0aW5ncygpOiBQaXBlbGluZVNldHRpbmdzIHtcclxuICAgIHJldHVybiB7XHJcbiAgICAgICAgbXNhYTogbWFrZU1TQUEoKSxcclxuICAgICAgICBlbmFibGVTaGFkaW5nU2NhbGU6IGZhbHNlLFxyXG4gICAgICAgIHNoYWRpbmdTY2FsZTogMC41LFxyXG4gICAgICAgIGJsb29tOiBtYWtlQmxvb20oKSxcclxuICAgICAgICB0b25lTWFwcGluZzogbWFrZVRvbmVNYXBwaW5nKCksXHJcbiAgICAgICAgY29sb3JHcmFkaW5nOiBtYWtlQ29sb3JHcmFkaW5nKCksXHJcbiAgICAgICAgZnNyOiBtYWtlRlNSKCksXHJcbiAgICAgICAgZnhhYTogbWFrZUZYQUEoKSxcclxuICAgIH07XHJcbn1cclxuXHJcbmV4cG9ydCBmdW5jdGlvbiBmaWxsUmVxdWlyZWRQaXBlbGluZVNldHRpbmdzKHZhbHVlOiBQaXBlbGluZVNldHRpbmdzKTogdm9pZCB7XHJcbiAgICBpZiAoIXZhbHVlLm1zYWEpIHtcclxuICAgICAgICAodmFsdWUubXNhYSBhcyBNU0FBKSA9IG1ha2VNU0FBKCk7XHJcbiAgICB9IGVsc2Uge1xyXG4gICAgICAgIGZpbGxSZXF1aXJlZE1TQUEodmFsdWUubXNhYSk7XHJcbiAgICB9XHJcbiAgICBpZiAodmFsdWUuZW5hYmxlU2hhZGluZ1NjYWxlID09PSB1bmRlZmluZWQpIHtcclxuICAgICAgICB2YWx1ZS5lbmFibGVTaGFkaW5nU2NhbGUgPSBmYWxzZTtcclxuICAgIH1cclxuICAgIGlmICh2YWx1ZS5zaGFkaW5nU2NhbGUgPT09IHVuZGVmaW5lZCkge1xyXG4gICAgICAgIHZhbHVlLnNoYWRpbmdTY2FsZSA9IDAuNTtcclxuICAgIH1cclxuICAgIGlmICghdmFsdWUuYmxvb20pIHtcclxuICAgICAgICAodmFsdWUuYmxvb20gYXMgQmxvb20pID0gbWFrZUJsb29tKCk7XHJcbiAgICB9IGVsc2Uge1xyXG4gICAgICAgIGZpbGxSZXF1aXJlZEJsb29tKHZhbHVlLmJsb29tKTtcclxuICAgIH1cclxuICAgIGlmICghdmFsdWUudG9uZU1hcHBpbmcpIHtcclxuICAgICAgICAodmFsdWUudG9uZU1hcHBpbmcgYXMgVG9uZU1hcHBpbmcpID0gbWFrZVRvbmVNYXBwaW5nKCk7XHJcbiAgICB9IGVsc2Uge1xyXG4gICAgICAgIGZpbGxSZXF1aXJlZFRvbmVNYXBwaW5nKHZhbHVlLnRvbmVNYXBwaW5nKTtcclxuICAgIH1cclxuICAgIGlmICghdmFsdWUuY29sb3JHcmFkaW5nKSB7XHJcbiAgICAgICAgKHZhbHVlLmNvbG9yR3JhZGluZyBhcyBDb2xvckdyYWRpbmcpID0gbWFrZUNvbG9yR3JhZGluZygpO1xyXG4gICAgfSBlbHNlIHtcclxuICAgICAgICBmaWxsUmVxdWlyZWRDb2xvckdyYWRpbmcodmFsdWUuY29sb3JHcmFkaW5nKTtcclxuICAgIH1cclxuICAgIGlmICghdmFsdWUuZnNyKSB7XHJcbiAgICAgICAgKHZhbHVlLmZzciBhcyBGU1IpID0gbWFrZUZTUigpO1xyXG4gICAgfSBlbHNlIHtcclxuICAgICAgICBmaWxsUmVxdWlyZWRGU1IodmFsdWUuZnNyKTtcclxuICAgIH1cclxuICAgIGlmICghdmFsdWUuZnhhYSkge1xyXG4gICAgICAgICh2YWx1ZS5meGFhIGFzIEZYQUEpID0gbWFrZUZYQUEoKTtcclxuICAgIH0gZWxzZSB7XHJcbiAgICAgICAgZmlsbFJlcXVpcmVkRlhBQSh2YWx1ZS5meGFhKTtcclxuICAgIH1cclxufVxyXG4iLCIvKlxyXG4gQ29weXJpZ2h0IChjKSAyMDIxLTIwMjQgWGlhbWVuIFlhamkgU29mdHdhcmUgQ28uLCBMdGQuXHJcblxyXG4gaHR0cHM6Ly93d3cuY29jb3MuY29tL1xyXG5cclxuIFBlcm1pc3Npb24gaXMgaGVyZWJ5IGdyYW50ZWQsIGZyZWUgb2YgY2hhcmdlLCB0byBhbnkgcGVyc29uIG9idGFpbmluZyBhIGNvcHlcclxuIG9mIHRoaXMgc29mdHdhcmUgYW5kIGFzc29jaWF0ZWQgZG9jdW1lbnRhdGlvbiBmaWxlcyAodGhlIFwiU29mdHdhcmVcIiksIHRvIGRlYWxcclxuIGluIHRoZSBTb2Z0d2FyZSB3aXRob3V0IHJlc3RyaWN0aW9uLCBpbmNsdWRpbmcgd2l0aG91dCBsaW1pdGF0aW9uIHRoZSByaWdodHMgdG9cclxuIHVzZSwgY29weSwgbW9kaWZ5LCBtZXJnZSwgcHVibGlzaCwgZGlzdHJpYnV0ZSwgc3VibGljZW5zZSwgYW5kL29yIHNlbGwgY29waWVzXHJcbiBvZiB0aGUgU29mdHdhcmUsIGFuZCB0byBwZXJtaXQgcGVyc29ucyB0byB3aG9tIHRoZSBTb2Z0d2FyZSBpcyBmdXJuaXNoZWQgdG8gZG8gc28sXHJcbiBzdWJqZWN0IHRvIHRoZSBmb2xsb3dpbmcgY29uZGl0aW9uczpcclxuXHJcbiBUaGUgYWJvdmUgY29weXJpZ2h0IG5vdGljZSBhbmQgdGhpcyBwZXJtaXNzaW9uIG5vdGljZSBzaGFsbCBiZSBpbmNsdWRlZCBpblxyXG4gYWxsIGNvcGllcyBvciBzdWJzdGFudGlhbCBwb3J0aW9ucyBvZiB0aGUgU29mdHdhcmUuXHJcblxyXG4gVEhFIFNPRlRXQVJFIElTIFBST1ZJREVEIFwiQVMgSVNcIiwgV0lUSE9VVCBXQVJSQU5UWSBPRiBBTlkgS0lORCwgRVhQUkVTUyBPUlxyXG4gSU1QTElFRCwgSU5DTFVESU5HIEJVVCBOT1QgTElNSVRFRCBUTyBUSEUgV0FSUkFOVElFUyBPRiBNRVJDSEFOVEFCSUxJVFksXHJcbiBGSVRORVNTIEZPUiBBIFBBUlRJQ1VMQVIgUFVSUE9TRSBBTkQgTk9OSU5GUklOR0VNRU5ULiBJTiBOTyBFVkVOVCBTSEFMTCBUSEVcclxuIEFVVEhPUlMgT1IgQ09QWVJJR0hUIEhPTERFUlMgQkUgTElBQkxFIEZPUiBBTlkgQ0xBSU0sIERBTUFHRVMgT1IgT1RIRVJcclxuIExJQUJJTElUWSwgV0hFVEhFUiBJTiBBTiBBQ1RJT04gT0YgQ09OVFJBQ1QsIFRPUlQgT1IgT1RIRVJXSVNFLCBBUklTSU5HIEZST00sXHJcbiBPVVQgT0YgT1IgSU4gQ09OTkVDVElPTiBXSVRIIFRIRSBTT0ZUV0FSRSBPUiBUSEUgVVNFIE9SIE9USEVSIERFQUxJTkdTIElOXHJcbiBUSEUgU09GVFdBUkUuXHJcbiovXHJcblxyXG5pbXBvcnQge1xyXG4gICAgYXNzZXJ0LCBjY2xlZ2FjeSwgY2xhbXAsIGdlb21ldHJ5LCBnZngsIExheWVycywgTWF0ZXJpYWwsIHBpcGVsaW5lLFxyXG4gICAgUGlwZWxpbmVFdmVudFByb2Nlc3NvciwgUGlwZWxpbmVFdmVudFR5cGUsIFJlZmxlY3Rpb25Qcm9iZU1hbmFnZXIsIHJlbmRlcmVyLFxyXG4gICAgcmVuZGVyaW5nLCBzeXMsIFZlYzIsIFZlYzMsIFZlYzQsIHdhcm4sIG1hY3JvLFxyXG59IGZyb20gJ2NjJztcclxuXHJcbmltcG9ydCB7IERFQlVHLCBFRElUT1IgfSBmcm9tICdjYy9lbnYnO1xyXG5cclxuaW1wb3J0IHtcclxuICAgIEJsb29tVHlwZSxcclxuICAgIG1ha2VQaXBlbGluZVNldHRpbmdzLFxyXG4gICAgUGlwZWxpbmVTZXR0aW5ncyxcclxufSBmcm9tICcuL2J1aWx0aW4tcGlwZWxpbmUtdHlwZXMnO1xyXG5cclxuY29uc3QgeyBBQUJCLCBTcGhlcmUsIGludGVyc2VjdCB9ID0gZ2VvbWV0cnk7XHJcbmNvbnN0IHsgQ2xlYXJGbGFnQml0LCBDb2xvciwgRm9ybWF0LCBGb3JtYXRGZWF0dXJlQml0LCBMb2FkT3AsIFN0b3JlT3AsIFRleHR1cmVUeXBlLCBWaWV3cG9ydCB9ID0gZ2Z4O1xyXG5jb25zdCB7IHNjZW5lIH0gPSByZW5kZXJlcjtcclxuY29uc3QgeyBDYW1lcmFVc2FnZSwgQ1NNTGV2ZWwsIExpZ2h0VHlwZSB9ID0gc2NlbmU7XHJcblxyXG5mdW5jdGlvbiBmb3J3YXJkTmVlZENsZWFyQ29sb3IoY2FtZXJhOiByZW5kZXJlci5zY2VuZS5DYW1lcmEpOiBib29sZWFuIHtcclxuICAgIHJldHVybiAhIShjYW1lcmEuY2xlYXJGbGFnICYgKENsZWFyRmxhZ0JpdC5DT0xPUiB8IChDbGVhckZsYWdCaXQuU1RFTkNJTCA8PCAxKSkpO1xyXG59XHJcblxyXG5mdW5jdGlvbiBnZXRDc21NYWluTGlnaHRWaWV3cG9ydChcclxuICAgIGxpZ2h0OiByZW5kZXJlci5zY2VuZS5EaXJlY3Rpb25hbExpZ2h0LFxyXG4gICAgdzogbnVtYmVyLFxyXG4gICAgaDogbnVtYmVyLFxyXG4gICAgbGV2ZWw6IG51bWJlcixcclxuICAgIHZwOiBnZnguVmlld3BvcnQsXHJcbiAgICBzY3JlZW5TcGFjZVNpZ25ZOiBudW1iZXIsXHJcbik6IHZvaWQge1xyXG4gICAgaWYgKGxpZ2h0LnNoYWRvd0ZpeGVkQXJlYSB8fCBsaWdodC5jc21MZXZlbCA9PT0gQ1NNTGV2ZWwuTEVWRUxfMSkge1xyXG4gICAgICAgIHZwLmxlZnQgPSAwO1xyXG4gICAgICAgIHZwLnRvcCA9IDA7XHJcbiAgICAgICAgdnAud2lkdGggPSBNYXRoLnRydW5jKHcpO1xyXG4gICAgICAgIHZwLmhlaWdodCA9IE1hdGgudHJ1bmMoaCk7XHJcbiAgICB9IGVsc2Uge1xyXG4gICAgICAgIHZwLmxlZnQgPSBNYXRoLnRydW5jKGxldmVsICUgMiAqIDAuNSAqIHcpO1xyXG4gICAgICAgIGlmIChzY3JlZW5TcGFjZVNpZ25ZID4gMCkge1xyXG4gICAgICAgICAgICB2cC50b3AgPSBNYXRoLnRydW5jKCgxIC0gTWF0aC5mbG9vcihsZXZlbCAvIDIpKSAqIDAuNSAqIGgpO1xyXG4gICAgICAgIH0gZWxzZSB7XHJcbiAgICAgICAgICAgIHZwLnRvcCA9IE1hdGgudHJ1bmMoTWF0aC5mbG9vcihsZXZlbCAvIDIpICogMC41ICogaCk7XHJcbiAgICAgICAgfVxyXG4gICAgICAgIHZwLndpZHRoID0gTWF0aC50cnVuYygwLjUgKiB3KTtcclxuICAgICAgICB2cC5oZWlnaHQgPSBNYXRoLnRydW5jKDAuNSAqIGgpO1xyXG4gICAgfVxyXG4gICAgdnAubGVmdCA9IE1hdGgubWF4KDAsIHZwLmxlZnQpO1xyXG4gICAgdnAudG9wID0gTWF0aC5tYXgoMCwgdnAudG9wKTtcclxuICAgIHZwLndpZHRoID0gTWF0aC5tYXgoMSwgdnAud2lkdGgpO1xyXG4gICAgdnAuaGVpZ2h0ID0gTWF0aC5tYXgoMSwgdnAuaGVpZ2h0KTtcclxufVxyXG5cclxuZXhwb3J0IGNsYXNzIFBpcGVsaW5lQ29uZmlncyB7XHJcbiAgICBpc1dlYiA9IGZhbHNlO1xyXG4gICAgaXNXZWJHTDEgPSBmYWxzZTtcclxuICAgIGlzV2ViR0wyID0gZmFsc2U7XHJcbiAgICBpc1dlYkdQVSA9IGZhbHNlO1xyXG4gICAgaXNNb2JpbGUgPSBmYWxzZTtcclxuICAgIGlzSERSID0gZmFsc2U7XHJcbiAgICB1c2VGbG9hdE91dHB1dCA9IGZhbHNlO1xyXG4gICAgdG9uZU1hcHBpbmdUeXBlID0gMDsgLy8gMDogQUNFUywgMTogTm9uZVxyXG4gICAgc2hhZG93RW5hYmxlZCA9IGZhbHNlO1xyXG4gICAgc2hhZG93TWFwRm9ybWF0ID0gRm9ybWF0LlIzMkY7XHJcbiAgICBzaGFkb3dNYXBTaXplID0gbmV3IFZlYzIoMSwgMSk7XHJcbiAgICB1c2VQbGFuYXJTaGFkb3cgPSBmYWxzZTtcclxuICAgIHNjcmVlblNwYWNlU2lnblkgPSAxO1xyXG4gICAgc3VwcG9ydERlcHRoU2FtcGxlID0gZmFsc2U7XHJcbiAgICBtb2JpbGVNYXhTcG90TGlnaHRTaGFkb3dNYXBzID0gMTtcclxuXHJcbiAgICBwbGF0Zm9ybSA9IG5ldyBWZWM0KDAsIDAsIDAsIDApO1xyXG59XHJcblxyXG5mdW5jdGlvbiBzZXR1cFBpcGVsaW5lQ29uZmlncyhcclxuICAgIHBwbDogcmVuZGVyaW5nLkJhc2ljUGlwZWxpbmUsXHJcbiAgICBjb25maWdzOiBQaXBlbGluZUNvbmZpZ3MsXHJcbik6IHZvaWQge1xyXG4gICAgY29uc3Qgc2FtcGxlRmVhdHVyZSA9IEZvcm1hdEZlYXR1cmVCaXQuU0FNUExFRF9URVhUVVJFIHwgRm9ybWF0RmVhdHVyZUJpdC5MSU5FQVJfRklMVEVSO1xyXG4gICAgY29uc3QgZGV2aWNlID0gcHBsLmRldmljZTtcclxuICAgIC8vIFBsYXRmb3JtXHJcbiAgICBjb25maWdzLmlzV2ViID0gIXN5cy5pc05hdGl2ZTtcclxuICAgIGNvbmZpZ3MuaXNXZWJHTDEgPSBkZXZpY2UuZ2Z4QVBJID09PSBnZnguQVBJLldFQkdMO1xyXG4gICAgY29uZmlncy5pc1dlYkdMMiA9IGRldmljZS5nZnhBUEkgPT09IGdmeC5BUEkuV0VCR0wyO1xyXG4gICAgY29uZmlncy5pc1dlYkdQVSA9IGRldmljZS5nZnhBUEkgPT09IGdmeC5BUEkuV0VCR1BVO1xyXG4gICAgY29uZmlncy5pc01vYmlsZSA9IHN5cy5pc01vYmlsZTtcclxuXHJcbiAgICAvLyBSZW5kZXJpbmdcclxuICAgIGNvbmZpZ3MuaXNIRFIgPSBwcGwucGlwZWxpbmVTY2VuZURhdGEuaXNIRFI7IC8vIEhhcyB0b25lIG1hcHBpbmdcclxuICAgIGNvbmZpZ3MudXNlRmxvYXRPdXRwdXQgPSBwcGwuZ2V0TWFjcm9Cb29sKCdDQ19VU0VfRkxPQVRfT1VUUFVUJyk7XHJcbiAgICBjb25maWdzLnRvbmVNYXBwaW5nVHlwZSA9IHBwbC5waXBlbGluZVNjZW5lRGF0YS5wb3N0U2V0dGluZ3MudG9uZU1hcHBpbmdUeXBlO1xyXG4gICAgLy8gU2hhZG93XHJcbiAgICBjb25zdCBzaGFkb3dJbmZvID0gcHBsLnBpcGVsaW5lU2NlbmVEYXRhLnNoYWRvd3M7XHJcbiAgICBjb25maWdzLnNoYWRvd0VuYWJsZWQgPSBzaGFkb3dJbmZvLmVuYWJsZWQ7XHJcbiAgICBjb25maWdzLnNoYWRvd01hcEZvcm1hdCA9IHBpcGVsaW5lLnN1cHBvcnRzUjMyRmxvYXRUZXh0dXJlKHBwbC5kZXZpY2UpID8gRm9ybWF0LlIzMkYgOiBGb3JtYXQuUkdCQTg7XHJcbiAgICBjb25maWdzLnNoYWRvd01hcFNpemUuc2V0KHNoYWRvd0luZm8uc2l6ZSk7XHJcbiAgICBjb25maWdzLnVzZVBsYW5hclNoYWRvdyA9IHNoYWRvd0luZm8uZW5hYmxlZCAmJiBzaGFkb3dJbmZvLnR5cGUgPT09IHJlbmRlcmVyLnNjZW5lLlNoYWRvd1R5cGUuUGxhbmFyO1xyXG4gICAgLy8gRGV2aWNlXHJcbiAgICBjb25maWdzLnNjcmVlblNwYWNlU2lnblkgPSBwcGwuZGV2aWNlLmNhcGFiaWxpdGllcy5zY3JlZW5TcGFjZVNpZ25ZO1xyXG4gICAgY29uZmlncy5zdXBwb3J0RGVwdGhTYW1wbGUgPSAocHBsLmRldmljZS5nZXRGb3JtYXRGZWF0dXJlcyhGb3JtYXQuREVQVEhfU1RFTkNJTCkgJiBzYW1wbGVGZWF0dXJlKSA9PT0gc2FtcGxlRmVhdHVyZTtcclxuICAgIC8vIENvbnN0YW50c1xyXG4gICAgY29uc3Qgc2NyZWVuU3BhY2VTaWduWSA9IGRldmljZS5jYXBhYmlsaXRpZXMuc2NyZWVuU3BhY2VTaWduWTtcclxuICAgIGNvbmZpZ3MucGxhdGZvcm0ueCA9IGNvbmZpZ3MuaXNNb2JpbGUgPyAxLjAgOiAwLjA7XHJcbiAgICBjb25maWdzLnBsYXRmb3JtLncgPSAoc2NyZWVuU3BhY2VTaWduWSAqIDAuNSArIDAuNSkgPDwgMSB8IChkZXZpY2UuY2FwYWJpbGl0aWVzLmNsaXBTcGFjZVNpZ25ZICogMC41ICsgMC41KTtcclxufVxyXG5cclxuZXhwb3J0IGludGVyZmFjZSBQaXBlbGluZVNldHRpbmdzMiBleHRlbmRzIFBpcGVsaW5lU2V0dGluZ3Mge1xyXG4gICAgX3Bhc3Nlcz86IHJlbmRlcmluZy5QaXBlbGluZVBhc3NCdWlsZGVyW107XHJcbn1cclxuXHJcbmNvbnN0IGRlZmF1bHRTZXR0aW5ncyA9IG1ha2VQaXBlbGluZVNldHRpbmdzKCk7XHJcblxyXG5leHBvcnQgY2xhc3MgQ2FtZXJhQ29uZmlncyB7XHJcbiAgICBzZXR0aW5nczogUGlwZWxpbmVTZXR0aW5ncyA9IGRlZmF1bHRTZXR0aW5ncztcclxuICAgIC8vIFdpbmRvd1xyXG4gICAgaXNNYWluR2FtZVdpbmRvdyA9IGZhbHNlO1xyXG4gICAgcmVuZGVyV2luZG93SWQgPSAwO1xyXG4gICAgLy8gQ2FtZXJhXHJcbiAgICBjb2xvck5hbWUgPSAnJztcclxuICAgIGRlcHRoU3RlbmNpbE5hbWUgPSAnJztcclxuICAgIC8vIFBpcGVsaW5lXHJcbiAgICBlbmFibGVGdWxsUGlwZWxpbmUgPSBmYWxzZTtcclxuICAgIGVuYWJsZVByb2ZpbGVyID0gZmFsc2U7XHJcbiAgICByZW1haW5pbmdQYXNzZXMgPSAwO1xyXG4gICAgLy8gU2hhZGluZyBTY2FsZVxyXG4gICAgZW5hYmxlU2hhZGluZ1NjYWxlID0gZmFsc2U7XHJcbiAgICBzaGFkaW5nU2NhbGUgPSAxLjA7XHJcbiAgICBuYXRpdmVXaWR0aCA9IDE7XHJcbiAgICBuYXRpdmVIZWlnaHQgPSAxO1xyXG4gICAgd2lkdGggPSAxOyAvLyBTY2FsZWQgd2lkdGhcclxuICAgIGhlaWdodCA9IDE7IC8vIFNjYWxlZCBoZWlnaHRcclxuICAgIC8vIFJhZGlhbmNlXHJcbiAgICBlbmFibGVIRFIgPSBmYWxzZTtcclxuICAgIHJhZGlhbmNlRm9ybWF0ID0gZ2Z4LkZvcm1hdC5SR0JBODtcclxuICAgIC8vIFRvbmUgTWFwcGluZ1xyXG4gICAgY29weUFuZFRvbmVtYXBNYXRlcmlhbDogTWF0ZXJpYWwgfCBudWxsID0gbnVsbDtcclxuICAgIC8vIERlcHRoXHJcbiAgICAvKiogQGVuIG11dGFibGUgKi9cclxuICAgIGVuYWJsZVN0b3JlU2NlbmVEZXB0aCA9IGZhbHNlO1xyXG59XHJcblxyXG5jb25zdCBzQ2xlYXJDb2xvclRyYW5zcGFyZW50QmxhY2sgPSBuZXcgQ29sb3IoMCwgMCwgMCwgMCk7XHJcblxyXG5mdW5jdGlvbiBzb3J0UGlwZWxpbmVQYXNzQnVpbGRlcnNCeUNvbmZpZ09yZGVyKHBhc3NCdWlsZGVyczogcmVuZGVyaW5nLlBpcGVsaW5lUGFzc0J1aWxkZXJbXSk6IHZvaWQge1xyXG4gICAgcGFzc0J1aWxkZXJzLnNvcnQoKGEsIGIpID0+IHtcclxuICAgICAgICByZXR1cm4gYS5nZXRDb25maWdPcmRlcigpIC0gYi5nZXRDb25maWdPcmRlcigpO1xyXG4gICAgfSk7XHJcbn1cclxuXHJcbmZ1bmN0aW9uIHNvcnRQaXBlbGluZVBhc3NCdWlsZGVyc0J5UmVuZGVyT3JkZXIocGFzc0J1aWxkZXJzOiByZW5kZXJpbmcuUGlwZWxpbmVQYXNzQnVpbGRlcltdKTogdm9pZCB7XHJcbiAgICBwYXNzQnVpbGRlcnMuc29ydCgoYSwgYikgPT4ge1xyXG4gICAgICAgIHJldHVybiBhLmdldFJlbmRlck9yZGVyKCkgLSBiLmdldFJlbmRlck9yZGVyKCk7XHJcbiAgICB9KTtcclxufVxyXG5cclxuZnVuY3Rpb24gYWRkQ29weVRvU2NyZWVuUGFzcyhcclxuICAgIHBwbDogcmVuZGVyaW5nLkJhc2ljUGlwZWxpbmUsXHJcbiAgICBwcGxDb25maWdzOiBSZWFkb25seTxQaXBlbGluZUNvbmZpZ3M+LFxyXG4gICAgY2FtZXJhQ29uZmlnczogQ2FtZXJhQ29uZmlncyxcclxuICAgIGlucHV0OiBzdHJpbmcsXHJcbik6IHJlbmRlcmluZy5CYXNpY1JlbmRlclBhc3NCdWlsZGVyIHtcclxuICAgIGFzc2VydCghIWNhbWVyYUNvbmZpZ3MuY29weUFuZFRvbmVtYXBNYXRlcmlhbCk7XHJcbiAgICBjb25zdCBwYXNzID0gcHBsLmFkZFJlbmRlclBhc3MoXHJcbiAgICAgICAgY2FtZXJhQ29uZmlncy5uYXRpdmVXaWR0aCxcclxuICAgICAgICBjYW1lcmFDb25maWdzLm5hdGl2ZUhlaWdodCxcclxuICAgICAgICAnY2MtdG9uZS1tYXBwaW5nJyk7XHJcbiAgICBwYXNzLmFkZFJlbmRlclRhcmdldChcclxuICAgICAgICBjYW1lcmFDb25maWdzLmNvbG9yTmFtZSxcclxuICAgICAgICBMb2FkT3AuQ0xFQVIsIFN0b3JlT3AuU1RPUkUsXHJcbiAgICAgICAgc0NsZWFyQ29sb3JUcmFuc3BhcmVudEJsYWNrKTtcclxuICAgIHBhc3MuYWRkVGV4dHVyZShpbnB1dCwgJ2lucHV0VGV4dHVyZScpO1xyXG4gICAgcGFzcy5hZGRRdWV1ZShyZW5kZXJpbmcuUXVldWVIaW50Lk9QQVFVRSlcclxuICAgICAgICAuYWRkRnVsbHNjcmVlblF1YWQoY2FtZXJhQ29uZmlncy5jb3B5QW5kVG9uZW1hcE1hdGVyaWFsLCAxKTtcclxuICAgIHJldHVybiBwYXNzO1xyXG59XHJcblxyXG5leHBvcnQgZnVuY3Rpb24gZ2V0UGluZ1BvbmdSZW5kZXJUYXJnZXQocHJldk5hbWU6IHN0cmluZywgcHJlZml4OiBzdHJpbmcsIGlkOiBudW1iZXIpOiBzdHJpbmcge1xyXG4gICAgaWYgKHByZXZOYW1lLnN0YXJ0c1dpdGgocHJlZml4KSkge1xyXG4gICAgICAgIHJldHVybiBgJHtwcmVmaXh9JHsxIC0gTnVtYmVyKHByZXZOYW1lLmNoYXJBdChwcmVmaXgubGVuZ3RoKSl9XyR7aWR9YDtcclxuICAgIH0gZWxzZSB7XHJcbiAgICAgICAgcmV0dXJuIGAke3ByZWZpeH0wXyR7aWR9YDtcclxuICAgIH1cclxufVxyXG5cclxuZXhwb3J0IGludGVyZmFjZSBQaXBlbGluZUNvbnRleHQge1xyXG4gICAgY29sb3JOYW1lOiBzdHJpbmc7XHJcbiAgICBkZXB0aFN0ZW5jaWxOYW1lOiBzdHJpbmc7XHJcbn1cclxuXHJcbmNsYXNzIEZvcndhcmRMaWdodGluZyB7XHJcbiAgICAvLyBBY3RpdmUgbGlnaHRzXHJcbiAgICBwcml2YXRlIHJlYWRvbmx5IGxpZ2h0czogcmVuZGVyZXIuc2NlbmUuTGlnaHRbXSA9IFtdO1xyXG4gICAgLy8gQWN0aXZlIHNwb3QgbGlnaHRzIHdpdGggc2hhZG93cyAoTXV0dWFsbHkgZXhjbHVzaXZlIHdpdGggYGxpZ2h0c2ApXHJcbiAgICBwcml2YXRlIHJlYWRvbmx5IHNoYWRvd0VuYWJsZWRTcG90TGlnaHRzOiByZW5kZXJlci5zY2VuZS5TcG90TGlnaHRbXSA9IFtdO1xyXG5cclxuICAgIC8vIEludGVybmFsIGNhY2hlZCByZXNvdXJjZXNcclxuICAgIHByaXZhdGUgcmVhZG9ubHkgX3NwaGVyZSA9IFNwaGVyZS5jcmVhdGUoMCwgMCwgMCwgMSk7XHJcbiAgICBwcml2YXRlIHJlYWRvbmx5IF9ib3VuZGluZ0JveCA9IG5ldyBBQUJCKCk7XHJcbiAgICBwcml2YXRlIHJlYWRvbmx5IF9yYW5nZWREaXJMaWdodEJvdW5kaW5nQm94ID0gbmV3IEFBQkIoMC4wLCAwLjAsIDAuMCwgMC41LCAwLjUsIDAuNSk7XHJcblxyXG4gICAgLy8gLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLVxyXG4gICAgLy8gSW50ZXJmYWNlXHJcbiAgICAvLyAtLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tXHJcbiAgICBwdWJsaWMgY3VsbExpZ2h0cyhzY2VuZTogcmVuZGVyZXIuUmVuZGVyU2NlbmUsIGZydXN0dW06IGdlb21ldHJ5LkZydXN0dW0sIGNhbWVyYVBvcz86IFZlYzMpOiB2b2lkIHtcclxuICAgICAgICAvLyBUT0RPKHpob3V6aGVuZ2xvbmcpOiBNYWtlIGxpZ2h0IGN1bGxpbmcgbmF0aXZlXHJcbiAgICAgICAgdGhpcy5saWdodHMubGVuZ3RoID0gMDtcclxuICAgICAgICB0aGlzLnNoYWRvd0VuYWJsZWRTcG90TGlnaHRzLmxlbmd0aCA9IDA7XHJcbiAgICAgICAgLy8gc3BvdCBsaWdodHNcclxuICAgICAgICBmb3IgKGNvbnN0IGxpZ2h0IG9mIHNjZW5lLnNwb3RMaWdodHMpIHtcclxuICAgICAgICAgICAgaWYgKGxpZ2h0LmJha2VkKSB7XHJcbiAgICAgICAgICAgICAgICBjb250aW51ZTtcclxuICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICBTcGhlcmUuc2V0KHRoaXMuX3NwaGVyZSwgbGlnaHQucG9zaXRpb24ueCwgbGlnaHQucG9zaXRpb24ueSwgbGlnaHQucG9zaXRpb24ueiwgbGlnaHQucmFuZ2UpO1xyXG4gICAgICAgICAgICBpZiAoaW50ZXJzZWN0LnNwaGVyZUZydXN0dW0odGhpcy5fc3BoZXJlLCBmcnVzdHVtKSkge1xyXG4gICAgICAgICAgICAgICAgaWYgKGxpZ2h0LnNoYWRvd0VuYWJsZWQpIHtcclxuICAgICAgICAgICAgICAgICAgICB0aGlzLnNoYWRvd0VuYWJsZWRTcG90TGlnaHRzLnB1c2gobGlnaHQpO1xyXG4gICAgICAgICAgICAgICAgfSBlbHNlIHtcclxuICAgICAgICAgICAgICAgICAgICB0aGlzLmxpZ2h0cy5wdXNoKGxpZ2h0KTtcclxuICAgICAgICAgICAgICAgIH1cclxuICAgICAgICAgICAgfVxyXG4gICAgICAgIH1cclxuICAgICAgICAvLyBzcGhlcmUgbGlnaHRzXHJcbiAgICAgICAgZm9yIChjb25zdCBsaWdodCBvZiBzY2VuZS5zcGhlcmVMaWdodHMpIHtcclxuICAgICAgICAgICAgaWYgKGxpZ2h0LmJha2VkKSB7XHJcbiAgICAgICAgICAgICAgICBjb250aW51ZTtcclxuICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICBTcGhlcmUuc2V0KHRoaXMuX3NwaGVyZSwgbGlnaHQucG9zaXRpb24ueCwgbGlnaHQucG9zaXRpb24ueSwgbGlnaHQucG9zaXRpb24ueiwgbGlnaHQucmFuZ2UpO1xyXG4gICAgICAgICAgICBpZiAoaW50ZXJzZWN0LnNwaGVyZUZydXN0dW0odGhpcy5fc3BoZXJlLCBmcnVzdHVtKSkge1xyXG4gICAgICAgICAgICAgICAgdGhpcy5saWdodHMucHVzaChsaWdodCk7XHJcbiAgICAgICAgICAgIH1cclxuICAgICAgICB9XHJcbiAgICAgICAgLy8gcG9pbnQgbGlnaHRzXHJcbiAgICAgICAgZm9yIChjb25zdCBsaWdodCBvZiBzY2VuZS5wb2ludExpZ2h0cykge1xyXG4gICAgICAgICAgICBpZiAobGlnaHQuYmFrZWQpIHtcclxuICAgICAgICAgICAgICAgIGNvbnRpbnVlO1xyXG4gICAgICAgICAgICB9XHJcbiAgICAgICAgICAgIFNwaGVyZS5zZXQodGhpcy5fc3BoZXJlLCBsaWdodC5wb3NpdGlvbi54LCBsaWdodC5wb3NpdGlvbi55LCBsaWdodC5wb3NpdGlvbi56LCBsaWdodC5yYW5nZSk7XHJcbiAgICAgICAgICAgIGlmIChpbnRlcnNlY3Quc3BoZXJlRnJ1c3R1bSh0aGlzLl9zcGhlcmUsIGZydXN0dW0pKSB7XHJcbiAgICAgICAgICAgICAgICB0aGlzLmxpZ2h0cy5wdXNoKGxpZ2h0KTtcclxuICAgICAgICAgICAgfVxyXG4gICAgICAgIH1cclxuICAgICAgICAvLyByYW5nZWQgZGlyIGxpZ2h0c1xyXG4gICAgICAgIGZvciAoY29uc3QgbGlnaHQgb2Ygc2NlbmUucmFuZ2VkRGlyTGlnaHRzKSB7XHJcbiAgICAgICAgICAgIEFBQkIudHJhbnNmb3JtKHRoaXMuX2JvdW5kaW5nQm94LCB0aGlzLl9yYW5nZWREaXJMaWdodEJvdW5kaW5nQm94LCBsaWdodC5ub2RlIS5nZXRXb3JsZE1hdHJpeCgpKTtcclxuICAgICAgICAgICAgaWYgKGludGVyc2VjdC5hYWJiRnJ1c3R1bSh0aGlzLl9ib3VuZGluZ0JveCwgZnJ1c3R1bSkpIHtcclxuICAgICAgICAgICAgICAgIHRoaXMubGlnaHRzLnB1c2gobGlnaHQpO1xyXG4gICAgICAgICAgICB9XHJcbiAgICAgICAgfVxyXG5cclxuICAgICAgICBpZiAoY2FtZXJhUG9zKSB7XHJcbiAgICAgICAgICAgIHRoaXMuc2hhZG93RW5hYmxlZFNwb3RMaWdodHMuc29ydChcclxuICAgICAgICAgICAgICAgIChsaHMsIHJocykgPT4gVmVjMy5zcXVhcmVkRGlzdGFuY2UoY2FtZXJhUG9zLCBsaHMucG9zaXRpb24pIC0gVmVjMy5zcXVhcmVkRGlzdGFuY2UoY2FtZXJhUG9zLCByaHMucG9zaXRpb24pLFxyXG4gICAgICAgICAgICApO1xyXG4gICAgICAgIH1cclxuICAgIH1cclxuICAgIHByaXZhdGUgX2FkZExpZ2h0UXVldWVzKGNhbWVyYTogcmVuZGVyZXIuc2NlbmUuQ2FtZXJhLCBwYXNzOiByZW5kZXJpbmcuQmFzaWNSZW5kZXJQYXNzQnVpbGRlcik6IHZvaWQge1xyXG4gICAgICAgIGZvciAoY29uc3QgbGlnaHQgb2YgdGhpcy5saWdodHMpIHtcclxuICAgICAgICAgICAgY29uc3QgcXVldWUgPSBwYXNzLmFkZFF1ZXVlKHJlbmRlcmluZy5RdWV1ZUhpbnQuQkxFTkQsICdmb3J3YXJkLWFkZCcpO1xyXG4gICAgICAgICAgICBzd2l0Y2ggKGxpZ2h0LnR5cGUpIHtcclxuICAgICAgICAgICAgICAgIGNhc2UgTGlnaHRUeXBlLlNQSEVSRTpcclxuICAgICAgICAgICAgICAgICAgICBxdWV1ZS5uYW1lID0gJ3NwaGVyZS1saWdodCc7XHJcbiAgICAgICAgICAgICAgICAgICAgYnJlYWs7XHJcbiAgICAgICAgICAgICAgICBjYXNlIExpZ2h0VHlwZS5TUE9UOlxyXG4gICAgICAgICAgICAgICAgICAgIHF1ZXVlLm5hbWUgPSAnc3BvdC1saWdodCc7XHJcbiAgICAgICAgICAgICAgICAgICAgYnJlYWs7XHJcbiAgICAgICAgICAgICAgICBjYXNlIExpZ2h0VHlwZS5QT0lOVDpcclxuICAgICAgICAgICAgICAgICAgICBxdWV1ZS5uYW1lID0gJ3BvaW50LWxpZ2h0JztcclxuICAgICAgICAgICAgICAgICAgICBicmVhaztcclxuICAgICAgICAgICAgICAgIGNhc2UgTGlnaHRUeXBlLlJBTkdFRF9ESVJFQ1RJT05BTDpcclxuICAgICAgICAgICAgICAgICAgICBxdWV1ZS5uYW1lID0gJ3JhbmdlZC1kaXJlY3Rpb25hbC1saWdodCc7XHJcbiAgICAgICAgICAgICAgICAgICAgYnJlYWs7XHJcbiAgICAgICAgICAgICAgICBkZWZhdWx0OlxyXG4gICAgICAgICAgICAgICAgICAgIHF1ZXVlLm5hbWUgPSAndW5rbm93bi1saWdodCc7XHJcbiAgICAgICAgICAgIH1cclxuICAgICAgICAgICAgcXVldWUuYWRkU2NlbmUoXHJcbiAgICAgICAgICAgICAgICBjYW1lcmEsXHJcbiAgICAgICAgICAgICAgICByZW5kZXJpbmcuU2NlbmVGbGFncy5CTEVORCxcclxuICAgICAgICAgICAgICAgIGxpZ2h0LFxyXG4gICAgICAgICAgICApO1xyXG4gICAgICAgIH1cclxuICAgIH1cclxuICAgIHB1YmxpYyBhZGRTcG90bGlnaHRTaGFkb3dQYXNzZXMoXHJcbiAgICAgICAgcHBsOiByZW5kZXJpbmcuQmFzaWNQaXBlbGluZSxcclxuICAgICAgICBjYW1lcmE6IHJlbmRlcmVyLnNjZW5lLkNhbWVyYSxcclxuICAgICAgICBtYXhOdW1TaGFkb3dNYXBzOiBudW1iZXIsXHJcbiAgICApOiB2b2lkIHtcclxuICAgICAgICBsZXQgaSA9IDA7XHJcbiAgICAgICAgZm9yIChjb25zdCBsaWdodCBvZiB0aGlzLnNoYWRvd0VuYWJsZWRTcG90TGlnaHRzKSB7XHJcbiAgICAgICAgICAgIGNvbnN0IHNoYWRvd01hcFNpemUgPSBwcGwucGlwZWxpbmVTY2VuZURhdGEuc2hhZG93cy5zaXplO1xyXG4gICAgICAgICAgICBjb25zdCBzaGFkb3dQYXNzID0gcHBsLmFkZFJlbmRlclBhc3Moc2hhZG93TWFwU2l6ZS54LCBzaGFkb3dNYXBTaXplLnksICdkZWZhdWx0Jyk7XHJcbiAgICAgICAgICAgIHNoYWRvd1Bhc3MubmFtZSA9IGBTcG90TGlnaHRTaGFkb3dQYXNzJHtpfWA7XHJcbiAgICAgICAgICAgIHNoYWRvd1Bhc3MuYWRkUmVuZGVyVGFyZ2V0KGBTcG90U2hhZG93TWFwJHtpfWAsIExvYWRPcC5DTEVBUiwgU3RvcmVPcC5TVE9SRSwgbmV3IENvbG9yKDEsIDEsIDEsIDEpKTtcclxuICAgICAgICAgICAgc2hhZG93UGFzcy5hZGREZXB0aFN0ZW5jaWwoYFNwb3RTaGFkb3dEZXB0aCR7aX1gLCBMb2FkT3AuQ0xFQVIsIFN0b3JlT3AuRElTQ0FSRCk7XHJcbiAgICAgICAgICAgIHNoYWRvd1Bhc3MuYWRkUXVldWUocmVuZGVyaW5nLlF1ZXVlSGludC5OT05FLCAnc2hhZG93LWNhc3RlcicpXHJcbiAgICAgICAgICAgICAgICAuYWRkU2NlbmUoY2FtZXJhLCByZW5kZXJpbmcuU2NlbmVGbGFncy5PUEFRVUUgfCByZW5kZXJpbmcuU2NlbmVGbGFncy5NQVNLIHwgcmVuZGVyaW5nLlNjZW5lRmxhZ3MuU0hBRE9XX0NBU1RFUilcclxuICAgICAgICAgICAgICAgIC51c2VMaWdodEZydXN0dW0obGlnaHQpO1xyXG4gICAgICAgICAgICArK2k7XHJcbiAgICAgICAgICAgIGlmIChpID49IG1heE51bVNoYWRvd01hcHMpIHtcclxuICAgICAgICAgICAgICAgIGJyZWFrO1xyXG4gICAgICAgICAgICB9XHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG4gICAgcHVibGljIGFkZExpZ2h0UXVldWVzKHBhc3M6IHJlbmRlcmluZy5CYXNpY1JlbmRlclBhc3NCdWlsZGVyLFxyXG4gICAgICAgIGNhbWVyYTogcmVuZGVyZXIuc2NlbmUuQ2FtZXJhLCBtYXhOdW1TaGFkb3dNYXBzOiBudW1iZXIpOiB2b2lkIHtcclxuICAgICAgICB0aGlzLl9hZGRMaWdodFF1ZXVlcyhjYW1lcmEsIHBhc3MpO1xyXG4gICAgICAgIGxldCBpID0gMDtcclxuICAgICAgICBmb3IgKGNvbnN0IGxpZ2h0IG9mIHRoaXMuc2hhZG93RW5hYmxlZFNwb3RMaWdodHMpIHtcclxuICAgICAgICAgICAgLy8gQWRkIHNwb3QtbGlnaHQgcGFzc1xyXG4gICAgICAgICAgICAvLyBTYXZlIGxhc3QgUmVuZGVyUGFzcyB0byB0aGUgYHBhc3NgIHZhcmlhYmxlXHJcbiAgICAgICAgICAgIC8vIFRPRE8oemhvdXpoZW5nbG9uZyk6IEZpeCBwZXIgcXVldWUgYWRkVGV4dHVyZVxyXG4gICAgICAgICAgICBwYXNzLmFkZFRleHR1cmUoYFNwb3RTaGFkb3dNYXAke2l9YCwgJ2NjX3Nwb3RTaGFkb3dNYXAnKTtcclxuICAgICAgICAgICAgY29uc3QgcXVldWUgPSBwYXNzLmFkZFF1ZXVlKHJlbmRlcmluZy5RdWV1ZUhpbnQuQkxFTkQsICdmb3J3YXJkLWFkZCcpO1xyXG4gICAgICAgICAgICBxdWV1ZS5hZGRTY2VuZShjYW1lcmEsIHJlbmRlcmluZy5TY2VuZUZsYWdzLkJMRU5ELCBsaWdodCk7XHJcbiAgICAgICAgICAgICsraTtcclxuICAgICAgICAgICAgaWYgKGkgPj0gbWF4TnVtU2hhZG93TWFwcykge1xyXG4gICAgICAgICAgICAgICAgYnJlYWs7XHJcbiAgICAgICAgICAgIH1cclxuICAgICAgICB9XHJcbiAgICB9XHJcblxyXG4gICAgLy8gTm90aWNlOiBGb3J3YXJkTGlnaHRpbmcgY2Fubm90IGhhbmRsZSBhIGxvdCBvZiBsaWdodHMuXHJcbiAgICAvLyBJZiB0aGVyZSBhcmUgdG9vIG1hbnkgbGlnaHRzLCB0aGUgcGVyZm9ybWFuY2Ugd2lsbCBiZSB2ZXJ5IHBvb3IuXHJcbiAgICAvLyBJZiBtYW55IGxpZ2h0cyBhcmUgbmVlZGVkLCBwbGVhc2UgaW1wbGVtZW50IGEgZm9yd2FyZCsgb3IgZGVmZXJyZWQgcmVuZGVyaW5nIHBpcGVsaW5lLlxyXG4gICAgcHVibGljIGFkZExpZ2h0UGFzc2VzKFxyXG4gICAgICAgIGNvbG9yTmFtZTogc3RyaW5nLFxyXG4gICAgICAgIGRlcHRoU3RlbmNpbE5hbWU6IHN0cmluZyxcclxuICAgICAgICBkZXB0aFN0ZW5jaWxTdG9yZU9wOiBnZnguU3RvcmVPcCxcclxuICAgICAgICBpZDogbnVtYmVyLCAvLyB3aW5kb3cgaWRcclxuICAgICAgICB3aWR0aDogbnVtYmVyLFxyXG4gICAgICAgIGhlaWdodDogbnVtYmVyLFxyXG4gICAgICAgIGNhbWVyYTogcmVuZGVyZXIuc2NlbmUuQ2FtZXJhLFxyXG4gICAgICAgIHZpZXdwb3J0OiBnZnguVmlld3BvcnQsXHJcbiAgICAgICAgcHBsOiByZW5kZXJpbmcuQmFzaWNQaXBlbGluZSxcclxuICAgICAgICBwYXNzOiByZW5kZXJpbmcuQmFzaWNSZW5kZXJQYXNzQnVpbGRlcixcclxuICAgICk6IHJlbmRlcmluZy5CYXNpY1JlbmRlclBhc3NCdWlsZGVyIHtcclxuICAgICAgICB0aGlzLl9hZGRMaWdodFF1ZXVlcyhjYW1lcmEsIHBhc3MpO1xyXG5cclxuICAgICAgICBsZXQgY291bnQgPSAwO1xyXG4gICAgICAgIGNvbnN0IHNoYWRvd01hcFNpemUgPSBwcGwucGlwZWxpbmVTY2VuZURhdGEuc2hhZG93cy5zaXplO1xyXG4gICAgICAgIGZvciAoY29uc3QgbGlnaHQgb2YgdGhpcy5zaGFkb3dFbmFibGVkU3BvdExpZ2h0cykge1xyXG4gICAgICAgICAgICBjb25zdCBzaGFkb3dQYXNzID0gcHBsLmFkZFJlbmRlclBhc3Moc2hhZG93TWFwU2l6ZS54LCBzaGFkb3dNYXBTaXplLnksICdkZWZhdWx0Jyk7XHJcbiAgICAgICAgICAgIHNoYWRvd1Bhc3MubmFtZSA9ICdTcG90bGlnaHRTaGFkb3dQYXNzJztcclxuICAgICAgICAgICAgLy8gUmV1c2UgY3NtIHNoYWRvdyBtYXBcclxuICAgICAgICAgICAgc2hhZG93UGFzcy5hZGRSZW5kZXJUYXJnZXQoYFNoYWRvd01hcCR7aWR9YCwgTG9hZE9wLkNMRUFSLCBTdG9yZU9wLlNUT1JFLCBuZXcgQ29sb3IoMSwgMSwgMSwgMSkpO1xyXG4gICAgICAgICAgICBzaGFkb3dQYXNzLmFkZERlcHRoU3RlbmNpbChgU2hhZG93RGVwdGgke2lkfWAsIExvYWRPcC5DTEVBUiwgU3RvcmVPcC5ESVNDQVJEKTtcclxuICAgICAgICAgICAgc2hhZG93UGFzcy5hZGRRdWV1ZShyZW5kZXJpbmcuUXVldWVIaW50Lk5PTkUsICdzaGFkb3ctY2FzdGVyJylcclxuICAgICAgICAgICAgICAgIC5hZGRTY2VuZShjYW1lcmEsIHJlbmRlcmluZy5TY2VuZUZsYWdzLk9QQVFVRSB8IHJlbmRlcmluZy5TY2VuZUZsYWdzLk1BU0sgfCByZW5kZXJpbmcuU2NlbmVGbGFncy5TSEFET1dfQ0FTVEVSKVxyXG4gICAgICAgICAgICAgICAgLnVzZUxpZ2h0RnJ1c3R1bShsaWdodCk7XHJcblxyXG4gICAgICAgICAgICAvLyBBZGQgc3BvdC1saWdodCBwYXNzXHJcbiAgICAgICAgICAgIC8vIFNhdmUgbGFzdCBSZW5kZXJQYXNzIHRvIHRoZSBgcGFzc2AgdmFyaWFibGVcclxuICAgICAgICAgICAgKytjb3VudDtcclxuICAgICAgICAgICAgY29uc3Qgc3RvcmVPcCA9IGNvdW50ID09PSB0aGlzLnNoYWRvd0VuYWJsZWRTcG90TGlnaHRzLmxlbmd0aFxyXG4gICAgICAgICAgICAgICAgPyBkZXB0aFN0ZW5jaWxTdG9yZU9wXHJcbiAgICAgICAgICAgICAgICA6IFN0b3JlT3AuU1RPUkU7XHJcblxyXG4gICAgICAgICAgICBwYXNzID0gcHBsLmFkZFJlbmRlclBhc3Mod2lkdGgsIGhlaWdodCwgJ2RlZmF1bHQnKTtcclxuICAgICAgICAgICAgcGFzcy5uYW1lID0gJ1Nwb3RsaWdodFdpdGhTaGFkb3dNYXAnO1xyXG4gICAgICAgICAgICBwYXNzLnNldFZpZXdwb3J0KHZpZXdwb3J0KTtcclxuICAgICAgICAgICAgcGFzcy5hZGRSZW5kZXJUYXJnZXQoY29sb3JOYW1lLCBMb2FkT3AuTE9BRCk7XHJcbiAgICAgICAgICAgIHBhc3MuYWRkRGVwdGhTdGVuY2lsKGRlcHRoU3RlbmNpbE5hbWUsIExvYWRPcC5MT0FELCBzdG9yZU9wKTtcclxuICAgICAgICAgICAgcGFzcy5hZGRUZXh0dXJlKGBTaGFkb3dNYXAke2lkfWAsICdjY19zcG90U2hhZG93TWFwJyk7XHJcbiAgICAgICAgICAgIGNvbnN0IHF1ZXVlID0gcGFzcy5hZGRRdWV1ZShyZW5kZXJpbmcuUXVldWVIaW50LkJMRU5ELCAnZm9yd2FyZC1hZGQnKTtcclxuICAgICAgICAgICAgcXVldWUuYWRkU2NlbmUoXHJcbiAgICAgICAgICAgICAgICBjYW1lcmEsXHJcbiAgICAgICAgICAgICAgICByZW5kZXJpbmcuU2NlbmVGbGFncy5CTEVORCxcclxuICAgICAgICAgICAgICAgIGxpZ2h0LFxyXG4gICAgICAgICAgICApO1xyXG4gICAgICAgIH1cclxuICAgICAgICByZXR1cm4gcGFzcztcclxuICAgIH1cclxuXHJcbiAgICBwdWJsaWMgaXNNdWx0aXBsZUxpZ2h0UGFzc2VzTmVlZGVkKCk6IGJvb2xlYW4ge1xyXG4gICAgICAgIHJldHVybiB0aGlzLnNoYWRvd0VuYWJsZWRTcG90TGlnaHRzLmxlbmd0aCA+IDA7XHJcbiAgICB9XHJcbn1cclxuXHJcbmV4cG9ydCBpbnRlcmZhY2UgRm9yd2FyZFBhc3NDb25maWdzIHtcclxuICAgIGVuYWJsZU1haW5MaWdodFNoYWRvd01hcDogYm9vbGVhbjtcclxuICAgIGVuYWJsZU1haW5MaWdodFBsYW5hclNoYWRvd01hcDogYm9vbGVhbjtcclxuICAgIGVuYWJsZVBsYW5hclJlZmxlY3Rpb25Qcm9iZTogYm9vbGVhbjtcclxuICAgIGVuYWJsZU1TQUE6IGJvb2xlYW47XHJcbiAgICBlbmFibGVTaW5nbGVGb3J3YXJkUGFzczogYm9vbGVhbjtcclxufVxyXG5cclxuZXhwb3J0IGNsYXNzIEJ1aWx0aW5Gb3J3YXJkUGFzc0J1aWxkZXIgaW1wbGVtZW50cyByZW5kZXJpbmcuUGlwZWxpbmVQYXNzQnVpbGRlciB7XHJcbiAgICBzdGF0aWMgQ29uZmlnT3JkZXIgPSAxMDA7XHJcbiAgICBzdGF0aWMgUmVuZGVyT3JkZXIgPSAxMDA7XHJcbiAgICBnZXRDb25maWdPcmRlcigpOiBudW1iZXIge1xyXG4gICAgICAgIHJldHVybiBCdWlsdGluRm9yd2FyZFBhc3NCdWlsZGVyLkNvbmZpZ09yZGVyO1xyXG4gICAgfVxyXG4gICAgZ2V0UmVuZGVyT3JkZXIoKTogbnVtYmVyIHtcclxuICAgICAgICByZXR1cm4gQnVpbHRpbkZvcndhcmRQYXNzQnVpbGRlci5SZW5kZXJPcmRlcjtcclxuICAgIH1cclxuICAgIGNvbmZpZ0NhbWVyYShcclxuICAgICAgICBjYW1lcmE6IFJlYWRvbmx5PHJlbmRlcmVyLnNjZW5lLkNhbWVyYT4sXHJcbiAgICAgICAgcGlwZWxpbmVDb25maWdzOiBSZWFkb25seTxQaXBlbGluZUNvbmZpZ3M+LFxyXG4gICAgICAgIGNhbWVyYUNvbmZpZ3M6IENhbWVyYUNvbmZpZ3MgJiBGb3J3YXJkUGFzc0NvbmZpZ3MpOiB2b2lkIHtcclxuICAgICAgICAvLyBTaGFkb3dcclxuICAgICAgICBjYW1lcmFDb25maWdzLmVuYWJsZU1haW5MaWdodFNoYWRvd01hcCA9IHBpcGVsaW5lQ29uZmlncy5zaGFkb3dFbmFibGVkXHJcbiAgICAgICAgICAgICYmICFwaXBlbGluZUNvbmZpZ3MudXNlUGxhbmFyU2hhZG93XHJcbiAgICAgICAgICAgICYmICEhY2FtZXJhLnNjZW5lXHJcbiAgICAgICAgICAgICYmICEhY2FtZXJhLnNjZW5lLm1haW5MaWdodFxyXG4gICAgICAgICAgICAmJiBjYW1lcmEuc2NlbmUubWFpbkxpZ2h0LnNoYWRvd0VuYWJsZWQ7XHJcblxyXG4gICAgICAgIGNhbWVyYUNvbmZpZ3MuZW5hYmxlTWFpbkxpZ2h0UGxhbmFyU2hhZG93TWFwID0gcGlwZWxpbmVDb25maWdzLnNoYWRvd0VuYWJsZWRcclxuICAgICAgICAgICAgJiYgcGlwZWxpbmVDb25maWdzLnVzZVBsYW5hclNoYWRvd1xyXG4gICAgICAgICAgICAmJiAhIWNhbWVyYS5zY2VuZVxyXG4gICAgICAgICAgICAmJiAhIWNhbWVyYS5zY2VuZS5tYWluTGlnaHRcclxuICAgICAgICAgICAgJiYgY2FtZXJhLnNjZW5lLm1haW5MaWdodC5zaGFkb3dFbmFibGVkO1xyXG5cclxuICAgICAgICAvLyBSZWZsZWN0aW9uIFByb2JlXHJcbiAgICAgICAgY2FtZXJhQ29uZmlncy5lbmFibGVQbGFuYXJSZWZsZWN0aW9uUHJvYmUgPSBjYW1lcmFDb25maWdzLmlzTWFpbkdhbWVXaW5kb3dcclxuICAgICAgICAgICAgfHwgY2FtZXJhLmNhbWVyYVVzYWdlID09PSBDYW1lcmFVc2FnZS5TQ0VORV9WSUVXXHJcbiAgICAgICAgICAgIHx8IGNhbWVyYS5jYW1lcmFVc2FnZSA9PT0gQ2FtZXJhVXNhZ2UuR0FNRV9WSUVXXHJcbiAgICAgICAgICAgIHx8IGNhbWVyYS5jYW1lcmFVc2FnZSA9PT0gQ2FtZXJhVXNhZ2UuUFJFVklFVztcclxuXHJcbiAgICAgICAgLy8gTVNBQVxyXG4gICAgICAgIGNhbWVyYUNvbmZpZ3MuZW5hYmxlTVNBQSA9IGNhbWVyYUNvbmZpZ3Muc2V0dGluZ3MubXNhYS5lbmFibGVkXHJcbiAgICAgICAgICAgICYmICghcGlwZWxpbmVDb25maWdzLmlzV2ViR0wyIHx8IChjYW1lcmFDb25maWdzLnJlbWFpbmluZ1Bhc3NlcyA+IDAgfHwgKCFtYWNyby5FTkFCTEVfV0VCR0xfQU5USUFMSUFTICYmIG1hY3JvLkVOQUJMRV9UUkFOU1BBUkVOVF9DQU5WQVMpKSlcclxuICAgICAgICAgICAgJiYgIWNhbWVyYUNvbmZpZ3MuZW5hYmxlU3RvcmVTY2VuZURlcHRoIC8vIENhbm5vdCBzdG9yZSBNUyBkZXB0aCwgcmVzb2x2ZSBkZXB0aCBpcyBhbHNvIG5vdCBjcm9zcy1wbGF0Zm9ybVxyXG4gICAgICAgICAgICAmJiAhcGlwZWxpbmVDb25maWdzLmlzV2ViR0wxO1xyXG5cclxuICAgICAgICAvLyBGb3J3YXJkIHJlbmRlcmluZyAoRGVwZW5kIG9uIE1TQUEgYW5kIFRCUilcclxuICAgICAgICBjYW1lcmFDb25maWdzLmVuYWJsZVNpbmdsZUZvcndhcmRQYXNzXHJcbiAgICAgICAgICAgID0gcGlwZWxpbmVDb25maWdzLmlzTW9iaWxlIHx8IGNhbWVyYUNvbmZpZ3MuZW5hYmxlTVNBQTtcclxuXHJcbiAgICAgICAgKytjYW1lcmFDb25maWdzLnJlbWFpbmluZ1Bhc3NlcztcclxuICAgIH1cclxuICAgIHdpbmRvd1Jlc2l6ZShcclxuICAgICAgICBwcGw6IHJlbmRlcmluZy5CYXNpY1BpcGVsaW5lLFxyXG4gICAgICAgIHBwbENvbmZpZ3M6IFJlYWRvbmx5PFBpcGVsaW5lQ29uZmlncz4sXHJcbiAgICAgICAgY2FtZXJhQ29uZmlnczogUmVhZG9ubHk8Q2FtZXJhQ29uZmlncyAmIEZvcndhcmRQYXNzQ29uZmlncz4sXHJcbiAgICAgICAgd2luZG93OiByZW5kZXJlci5SZW5kZXJXaW5kb3csXHJcbiAgICAgICAgY2FtZXJhOiByZW5kZXJlci5zY2VuZS5DYW1lcmEsXHJcbiAgICAgICAgbmF0aXZlV2lkdGg6IG51bWJlcixcclxuICAgICAgICBuYXRpdmVIZWlnaHQ6IG51bWJlcik6IHZvaWQge1xyXG4gICAgICAgIGNvbnN0IFJlc291cmNlRmxhZ3MgPSByZW5kZXJpbmcuUmVzb3VyY2VGbGFncztcclxuICAgICAgICBjb25zdCBSZXNvdXJjZVJlc2lkZW5jeSA9IHJlbmRlcmluZy5SZXNvdXJjZVJlc2lkZW5jeTtcclxuICAgICAgICBjb25zdCBpZCA9IHdpbmRvdy5yZW5kZXJXaW5kb3dJZDtcclxuICAgICAgICBjb25zdCBzZXR0aW5ncyA9IGNhbWVyYUNvbmZpZ3Muc2V0dGluZ3M7XHJcblxyXG4gICAgICAgIGNvbnN0IHdpZHRoID0gY2FtZXJhQ29uZmlncy5lbmFibGVTaGFkaW5nU2NhbGVcclxuICAgICAgICAgICAgPyBNYXRoLm1heChNYXRoLmZsb29yKG5hdGl2ZVdpZHRoICogY2FtZXJhQ29uZmlncy5zaGFkaW5nU2NhbGUpLCAxKVxyXG4gICAgICAgICAgICA6IG5hdGl2ZVdpZHRoO1xyXG4gICAgICAgIGNvbnN0IGhlaWdodCA9IGNhbWVyYUNvbmZpZ3MuZW5hYmxlU2hhZGluZ1NjYWxlXHJcbiAgICAgICAgICAgID8gTWF0aC5tYXgoTWF0aC5mbG9vcihuYXRpdmVIZWlnaHQgKiBjYW1lcmFDb25maWdzLnNoYWRpbmdTY2FsZSksIDEpXHJcbiAgICAgICAgICAgIDogbmF0aXZlSGVpZ2h0O1xyXG5cclxuICAgICAgICAvLyBNc2FhUmFkaWFuY2VcclxuICAgICAgICBpZiAoY2FtZXJhQ29uZmlncy5lbmFibGVNU0FBKSB7XHJcbiAgICAgICAgICAgIC8vIE5vdGljZTogV2UgbmV2ZXIgc3RvcmUgbXVsdGlzYW1wbGUgcmVzdWx0cy5cclxuICAgICAgICAgICAgLy8gVGhlc2Ugc2FtcGxlcyBhcmUgYWx3YXlzIHJlc29sdmVkIGFuZCBkaXNjYXJkZWQgYXQgdGhlIGVuZCBvZiB0aGUgcmVuZGVyIHBhc3MuXHJcbiAgICAgICAgICAgIC8vIFNvIHRoZSBSZXNvdXJjZVJlc2lkZW5jeSBzaG91bGQgYmUgTUVNT1JZTEVTUy5cclxuICAgICAgICAgICAgaWYgKGNhbWVyYUNvbmZpZ3MuZW5hYmxlSERSKSB7XHJcbiAgICAgICAgICAgICAgICBwcGwuYWRkVGV4dHVyZShgTXNhYVJhZGlhbmNlJHtpZH1gLCBUZXh0dXJlVHlwZS5URVgyRCwgY2FtZXJhQ29uZmlncy5yYWRpYW5jZUZvcm1hdCwgd2lkdGgsIGhlaWdodCwgMSwgMSwgMSxcclxuICAgICAgICAgICAgICAgICAgICBzZXR0aW5ncy5tc2FhLnNhbXBsZUNvdW50LCBSZXNvdXJjZUZsYWdzLkNPTE9SX0FUVEFDSE1FTlQsIFJlc291cmNlUmVzaWRlbmN5Lk1FTU9SWUxFU1MpO1xyXG4gICAgICAgICAgICB9IGVsc2Uge1xyXG4gICAgICAgICAgICAgICAgcHBsLmFkZFRleHR1cmUoYE1zYWFSYWRpYW5jZSR7aWR9YCwgVGV4dHVyZVR5cGUuVEVYMkQsIEZvcm1hdC5SR0JBOCwgd2lkdGgsIGhlaWdodCwgMSwgMSwgMSxcclxuICAgICAgICAgICAgICAgICAgICBzZXR0aW5ncy5tc2FhLnNhbXBsZUNvdW50LCBSZXNvdXJjZUZsYWdzLkNPTE9SX0FUVEFDSE1FTlQsIFJlc291cmNlUmVzaWRlbmN5Lk1FTU9SWUxFU1MpO1xyXG4gICAgICAgICAgICB9XHJcbiAgICAgICAgICAgIHBwbC5hZGRUZXh0dXJlKGBNc2FhRGVwdGhTdGVuY2lsJHtpZH1gLCBUZXh0dXJlVHlwZS5URVgyRCwgRm9ybWF0LkRFUFRIX1NURU5DSUwsIHdpZHRoLCBoZWlnaHQsIDEsIDEsIDEsXHJcbiAgICAgICAgICAgICAgICBzZXR0aW5ncy5tc2FhLnNhbXBsZUNvdW50LCBSZXNvdXJjZUZsYWdzLkRFUFRIX1NURU5DSUxfQVRUQUNITUVOVCwgUmVzb3VyY2VSZXNpZGVuY3kuTUVNT1JZTEVTUyk7XHJcbiAgICAgICAgfVxyXG5cclxuICAgICAgICAvLyBNYWlubGlnaHQgU2hhZG93TWFwXHJcbiAgICAgICAgcHBsLmFkZFJlbmRlclRhcmdldChcclxuICAgICAgICAgICAgYFNoYWRvd01hcCR7aWR9YCxcclxuICAgICAgICAgICAgcHBsQ29uZmlncy5zaGFkb3dNYXBGb3JtYXQsXHJcbiAgICAgICAgICAgIHBwbENvbmZpZ3Muc2hhZG93TWFwU2l6ZS54LFxyXG4gICAgICAgICAgICBwcGxDb25maWdzLnNoYWRvd01hcFNpemUueSxcclxuICAgICAgICApO1xyXG4gICAgICAgIHBwbC5hZGREZXB0aFN0ZW5jaWwoXHJcbiAgICAgICAgICAgIGBTaGFkb3dEZXB0aCR7aWR9YCxcclxuICAgICAgICAgICAgRm9ybWF0LkRFUFRIX1NURU5DSUwsXHJcbiAgICAgICAgICAgIHBwbENvbmZpZ3Muc2hhZG93TWFwU2l6ZS54LFxyXG4gICAgICAgICAgICBwcGxDb25maWdzLnNoYWRvd01hcFNpemUueSxcclxuICAgICAgICApO1xyXG5cclxuICAgICAgICAvLyBTcG90LWxpZ2h0IHNoYWRvdyBtYXBzXHJcbiAgICAgICAgaWYgKGNhbWVyYUNvbmZpZ3MuZW5hYmxlU2luZ2xlRm9yd2FyZFBhc3MpIHtcclxuICAgICAgICAgICAgY29uc3QgY291bnQgPSBwcGxDb25maWdzLm1vYmlsZU1heFNwb3RMaWdodFNoYWRvd01hcHM7XHJcbiAgICAgICAgICAgIGZvciAobGV0IGkgPSAwOyBpICE9PSBjb3VudDsgKytpKSB7XHJcbiAgICAgICAgICAgICAgICBwcGwuYWRkUmVuZGVyVGFyZ2V0KFxyXG4gICAgICAgICAgICAgICAgICAgIGBTcG90U2hhZG93TWFwJHtpfWAsXHJcbiAgICAgICAgICAgICAgICAgICAgcHBsQ29uZmlncy5zaGFkb3dNYXBGb3JtYXQsXHJcbiAgICAgICAgICAgICAgICAgICAgcHBsQ29uZmlncy5zaGFkb3dNYXBTaXplLngsXHJcbiAgICAgICAgICAgICAgICAgICAgcHBsQ29uZmlncy5zaGFkb3dNYXBTaXplLnksXHJcbiAgICAgICAgICAgICAgICApO1xyXG4gICAgICAgICAgICAgICAgcHBsLmFkZERlcHRoU3RlbmNpbChcclxuICAgICAgICAgICAgICAgICAgICBgU3BvdFNoYWRvd0RlcHRoJHtpfWAsXHJcbiAgICAgICAgICAgICAgICAgICAgRm9ybWF0LkRFUFRIX1NURU5DSUwsXHJcbiAgICAgICAgICAgICAgICAgICAgcHBsQ29uZmlncy5zaGFkb3dNYXBTaXplLngsXHJcbiAgICAgICAgICAgICAgICAgICAgcHBsQ29uZmlncy5zaGFkb3dNYXBTaXplLnksXHJcbiAgICAgICAgICAgICAgICApO1xyXG4gICAgICAgICAgICB9XHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG4gICAgc2V0dXAoXHJcbiAgICAgICAgcHBsOiByZW5kZXJpbmcuQmFzaWNQaXBlbGluZSxcclxuICAgICAgICBwcGxDb25maWdzOiBSZWFkb25seTxQaXBlbGluZUNvbmZpZ3M+LFxyXG4gICAgICAgIGNhbWVyYUNvbmZpZ3M6IENhbWVyYUNvbmZpZ3MgJiBGb3J3YXJkUGFzc0NvbmZpZ3MsXHJcbiAgICAgICAgY2FtZXJhOiByZW5kZXJlci5zY2VuZS5DYW1lcmEsXHJcbiAgICAgICAgY29udGV4dDogUGlwZWxpbmVDb250ZXh0KTogcmVuZGVyaW5nLkJhc2ljUmVuZGVyUGFzc0J1aWxkZXIgfCB1bmRlZmluZWQge1xyXG4gICAgICAgIC8vIEFkZCBnbG9iYWwgY29uc3RhbnRzXHJcbiAgICAgICAgcHBsLnNldFZlYzQoJ2dfcGxhdGZvcm0nLCBwcGxDb25maWdzLnBsYXRmb3JtKTtcclxuXHJcbiAgICAgICAgY29uc3QgaWQgPSBjYW1lcmEud2luZG93LnJlbmRlcldpbmRvd0lkO1xyXG5cclxuICAgICAgICBjb25zdCBzY2VuZSA9IGNhbWVyYS5zY2VuZSE7XHJcbiAgICAgICAgY29uc3QgbWFpbkxpZ2h0ID0gc2NlbmUubWFpbkxpZ2h0O1xyXG5cclxuICAgICAgICAtLWNhbWVyYUNvbmZpZ3MucmVtYWluaW5nUGFzc2VzO1xyXG4gICAgICAgIGFzc2VydChjYW1lcmFDb25maWdzLnJlbWFpbmluZ1Bhc3NlcyA+PSAwKTtcclxuXHJcbiAgICAgICAgLy8gRm9yd2FyZCBMaWdodGluZyAoTGlnaHQgQ3VsbGluZylcclxuICAgICAgICB0aGlzLmZvcndhcmRMaWdodGluZy5jdWxsTGlnaHRzKHNjZW5lLCBjYW1lcmEuZnJ1c3R1bSk7XHJcblxyXG4gICAgICAgIC8vIE1haW4gRGlyZWN0aW9uYWwgbGlnaHQgQ1NNIFNoYWRvdyBNYXBcclxuICAgICAgICBpZiAoY2FtZXJhQ29uZmlncy5lbmFibGVNYWluTGlnaHRTaGFkb3dNYXApIHtcclxuICAgICAgICAgICAgYXNzZXJ0KCEhbWFpbkxpZ2h0KTtcclxuICAgICAgICAgICAgdGhpcy5fYWRkQ2FzY2FkZWRTaGFkb3dNYXBQYXNzKHBwbCwgcHBsQ29uZmlncywgaWQsIG1haW5MaWdodCwgY2FtZXJhKTtcclxuICAgICAgICB9XHJcblxyXG4gICAgICAgIC8vIFNwb3QgbGlnaHQgc2hhZG93IG1hcHMgKE1vYmlsZSBvciBNU0FBKVxyXG4gICAgICAgIGlmIChjYW1lcmFDb25maWdzLmVuYWJsZVNpbmdsZUZvcndhcmRQYXNzKSB7XHJcbiAgICAgICAgICAgIC8vIEN1cnJlbnRseSwgb25seSBzdXBwb3J0IDEgc3BvdCBsaWdodCB3aXRoIHNoYWRvdyBtYXAgb24gbW9iaWxlIHBsYXRmb3JtLlxyXG4gICAgICAgICAgICAvLyBUT0RPKHpob3V6aGVuZ2xvbmcpOiBSZWxleCB0aGlzIGxpbWl0YXRpb24uXHJcbiAgICAgICAgICAgIHRoaXMuZm9yd2FyZExpZ2h0aW5nLmFkZFNwb3RsaWdodFNoYWRvd1Bhc3NlcyhcclxuICAgICAgICAgICAgICAgIHBwbCwgY2FtZXJhLCBwcGxDb25maWdzLm1vYmlsZU1heFNwb3RMaWdodFNoYWRvd01hcHMpO1xyXG4gICAgICAgIH1cclxuXHJcbiAgICAgICAgdGhpcy5fdHJ5QWRkUmVmbGVjdGlvblByb2JlUGFzc2VzKHBwbCwgY2FtZXJhQ29uZmlncywgaWQsIG1haW5MaWdodCwgY2FtZXJhLnNjZW5lLCBjYW1lcmEpO1xyXG5cclxuICAgICAgICBpZiAoY2FtZXJhQ29uZmlncy5yZW1haW5pbmdQYXNzZXMgPiAwIHx8IGNhbWVyYUNvbmZpZ3MuZW5hYmxlU2hhZGluZ1NjYWxlKSB7XHJcbiAgICAgICAgICAgIGNvbnRleHQuY29sb3JOYW1lID0gY2FtZXJhQ29uZmlncy5lbmFibGVTaGFkaW5nU2NhbGVcclxuICAgICAgICAgICAgICAgID8gYFNjYWxlZFJhZGlhbmNlMF8ke2lkfWBcclxuICAgICAgICAgICAgICAgIDogYFJhZGlhbmNlMF8ke2lkfWA7XHJcbiAgICAgICAgICAgIGNvbnRleHQuZGVwdGhTdGVuY2lsTmFtZSA9IGNhbWVyYUNvbmZpZ3MuZW5hYmxlU2hhZGluZ1NjYWxlXHJcbiAgICAgICAgICAgICAgICA/IGBTY2FsZWRTY2VuZURlcHRoXyR7aWR9YFxyXG4gICAgICAgICAgICAgICAgOiBgU2NlbmVEZXB0aF8ke2lkfWA7XHJcbiAgICAgICAgfSBlbHNlIHtcclxuICAgICAgICAgICAgY29udGV4dC5jb2xvck5hbWUgPSBjYW1lcmFDb25maWdzLmNvbG9yTmFtZTtcclxuICAgICAgICAgICAgY29udGV4dC5kZXB0aFN0ZW5jaWxOYW1lID0gY2FtZXJhQ29uZmlncy5kZXB0aFN0ZW5jaWxOYW1lO1xyXG4gICAgICAgIH1cclxuXHJcbiAgICAgICAgY29uc3QgcGFzcyA9IHRoaXMuX2FkZEZvcndhcmRSYWRpYW5jZVBhc3NlcyhcclxuICAgICAgICAgICAgcHBsLCBwcGxDb25maWdzLCBjYW1lcmFDb25maWdzLCBpZCwgY2FtZXJhLFxyXG4gICAgICAgICAgICBjYW1lcmFDb25maWdzLndpZHRoLCBjYW1lcmFDb25maWdzLmhlaWdodCwgbWFpbkxpZ2h0LFxyXG4gICAgICAgICAgICBjb250ZXh0LmNvbG9yTmFtZSwgY29udGV4dC5kZXB0aFN0ZW5jaWxOYW1lLFxyXG4gICAgICAgICAgICAhY2FtZXJhQ29uZmlncy5lbmFibGVNU0FBLFxyXG4gICAgICAgICAgICBjYW1lcmFDb25maWdzLmVuYWJsZVN0b3JlU2NlbmVEZXB0aCA/IFN0b3JlT3AuU1RPUkUgOiBTdG9yZU9wLkRJU0NBUkQpO1xyXG5cclxuICAgICAgICBpZiAoIWNhbWVyYUNvbmZpZ3MuZW5hYmxlU3RvcmVTY2VuZURlcHRoKSB7XHJcbiAgICAgICAgICAgIGNvbnRleHQuZGVwdGhTdGVuY2lsTmFtZSA9ICcnO1xyXG4gICAgICAgIH1cclxuXHJcbiAgICAgICAgaWYgKGNhbWVyYUNvbmZpZ3MucmVtYWluaW5nUGFzc2VzID09PSAwICYmIGNhbWVyYUNvbmZpZ3MuZW5hYmxlU2hhZGluZ1NjYWxlKSB7XHJcbiAgICAgICAgICAgIHJldHVybiBhZGRDb3B5VG9TY3JlZW5QYXNzKHBwbCwgcHBsQ29uZmlncywgY2FtZXJhQ29uZmlncywgY29udGV4dC5jb2xvck5hbWUpO1xyXG4gICAgICAgIH0gZWxzZSB7XHJcbiAgICAgICAgICAgIHJldHVybiBwYXNzO1xyXG4gICAgICAgIH1cclxuICAgIH1cclxuICAgIHByaXZhdGUgX2FkZENhc2NhZGVkU2hhZG93TWFwUGFzcyhcclxuICAgICAgICBwcGw6IHJlbmRlcmluZy5CYXNpY1BpcGVsaW5lLFxyXG4gICAgICAgIHBwbENvbmZpZ3M6IFJlYWRvbmx5PFBpcGVsaW5lQ29uZmlncz4sXHJcbiAgICAgICAgaWQ6IG51bWJlcixcclxuICAgICAgICBsaWdodDogcmVuZGVyZXIuc2NlbmUuRGlyZWN0aW9uYWxMaWdodCxcclxuICAgICAgICBjYW1lcmE6IHJlbmRlcmVyLnNjZW5lLkNhbWVyYSxcclxuICAgICk6IHZvaWQge1xyXG4gICAgICAgIGNvbnN0IFF1ZXVlSGludCA9IHJlbmRlcmluZy5RdWV1ZUhpbnQ7XHJcbiAgICAgICAgY29uc3QgU2NlbmVGbGFncyA9IHJlbmRlcmluZy5TY2VuZUZsYWdzO1xyXG4gICAgICAgIC8vIC0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS1cclxuICAgICAgICAvLyBEeW5hbWljIHN0YXRlc1xyXG4gICAgICAgIC8vIC0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS1cclxuICAgICAgICBjb25zdCBzaGFkb3dTaXplID0gcHBsLnBpcGVsaW5lU2NlbmVEYXRhLnNoYWRvd3Muc2l6ZTtcclxuICAgICAgICBjb25zdCB3aWR0aCA9IHNoYWRvd1NpemUueDtcclxuICAgICAgICBjb25zdCBoZWlnaHQgPSBzaGFkb3dTaXplLnk7XHJcblxyXG4gICAgICAgIGNvbnN0IHZpZXdwb3J0ID0gdGhpcy5fdmlld3BvcnQ7XHJcbiAgICAgICAgdmlld3BvcnQubGVmdCA9IHZpZXdwb3J0LnRvcCA9IDA7XHJcbiAgICAgICAgdmlld3BvcnQud2lkdGggPSB3aWR0aDtcclxuICAgICAgICB2aWV3cG9ydC5oZWlnaHQgPSBoZWlnaHQ7XHJcblxyXG4gICAgICAgIC8vIC0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS1cclxuICAgICAgICAvLyBDU00gU2hhZG93IE1hcFxyXG4gICAgICAgIC8vIC0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS1cclxuICAgICAgICBjb25zdCBwYXNzID0gcHBsLmFkZFJlbmRlclBhc3Mod2lkdGgsIGhlaWdodCwgJ2RlZmF1bHQnKTtcclxuICAgICAgICBwYXNzLm5hbWUgPSAnQ2FzY2FkZWRTaGFkb3dNYXAnO1xyXG4gICAgICAgIHBhc3MuYWRkUmVuZGVyVGFyZ2V0KGBTaGFkb3dNYXAke2lkfWAsIExvYWRPcC5DTEVBUiwgU3RvcmVPcC5TVE9SRSwgbmV3IENvbG9yKDEsIDEsIDEsIDEpKTtcclxuICAgICAgICBwYXNzLmFkZERlcHRoU3RlbmNpbChgU2hhZG93RGVwdGgke2lkfWAsIExvYWRPcC5DTEVBUiwgU3RvcmVPcC5ESVNDQVJEKTtcclxuICAgICAgICBjb25zdCBjc21MZXZlbCA9IHBwbC5waXBlbGluZVNjZW5lRGF0YS5jc21TdXBwb3J0ZWQgPyBsaWdodC5jc21MZXZlbCA6IDE7XHJcblxyXG4gICAgICAgIC8vIEFkZCBzaGFkb3cgbWFwIHZpZXdwb3J0c1xyXG4gICAgICAgIGZvciAobGV0IGxldmVsID0gMDsgbGV2ZWwgIT09IGNzbUxldmVsOyArK2xldmVsKSB7XHJcbiAgICAgICAgICAgIGdldENzbU1haW5MaWdodFZpZXdwb3J0KGxpZ2h0LCB3aWR0aCwgaGVpZ2h0LCBsZXZlbCwgdGhpcy5fdmlld3BvcnQsIHBwbENvbmZpZ3Muc2NyZWVuU3BhY2VTaWduWSk7XHJcbiAgICAgICAgICAgIGNvbnN0IHF1ZXVlID0gcGFzcy5hZGRRdWV1ZShRdWV1ZUhpbnQuTk9ORSwgJ3NoYWRvdy1jYXN0ZXInKTtcclxuICAgICAgICAgICAgaWYgKCFwcGxDb25maWdzLmlzV2ViR1BVKSB7IC8vIFRlbXBvcmFyeSB3b3JrYXJvdW5kIGZvciBXZWJHUFVcclxuICAgICAgICAgICAgICAgIHF1ZXVlLnNldFZpZXdwb3J0KHRoaXMuX3ZpZXdwb3J0KTtcclxuICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICBxdWV1ZVxyXG4gICAgICAgICAgICAgICAgLmFkZFNjZW5lKGNhbWVyYSwgU2NlbmVGbGFncy5PUEFRVUUgfCBTY2VuZUZsYWdzLk1BU0sgfCBTY2VuZUZsYWdzLlNIQURPV19DQVNURVIpXHJcbiAgICAgICAgICAgICAgICAudXNlTGlnaHRGcnVzdHVtKGxpZ2h0LCBsZXZlbCk7XHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG4gICAgcHJpdmF0ZSBfdHJ5QWRkUmVmbGVjdGlvblByb2JlUGFzc2VzKFxyXG4gICAgICAgIHBwbDogcmVuZGVyaW5nLkJhc2ljUGlwZWxpbmUsXHJcbiAgICAgICAgY2FtZXJhQ29uZmlnczogUmVhZG9ubHk8Q2FtZXJhQ29uZmlncyAmIEZvcndhcmRQYXNzQ29uZmlncz4sXHJcbiAgICAgICAgaWQ6IG51bWJlcixcclxuICAgICAgICBtYWluTGlnaHQ6IHJlbmRlcmVyLnNjZW5lLkRpcmVjdGlvbmFsTGlnaHQgfCBudWxsLFxyXG4gICAgICAgIHNjZW5lOiByZW5kZXJlci5SZW5kZXJTY2VuZSB8IG51bGwsXHJcbiAgICAgICAgc291cmNlQ2FtZXJhOiByZW5kZXJlci5zY2VuZS5DYW1lcmEsXHJcbiAgICApOiB2b2lkIHtcclxuICAgICAgICBjb25zdCByZWZsZWN0aW9uUHJvYmVNYW5hZ2VyID0gY2NsZWdhY3kuaW50ZXJuYWwucmVmbGVjdGlvblByb2JlTWFuYWdlciBhcyBSZWZsZWN0aW9uUHJvYmVNYW5hZ2VyIHwgdW5kZWZpbmVkO1xyXG4gICAgICAgIGlmICghcmVmbGVjdGlvblByb2JlTWFuYWdlcikge1xyXG4gICAgICAgICAgICByZXR1cm47XHJcbiAgICAgICAgfVxyXG4gICAgICAgIGNvbnN0IFJlc291cmNlUmVzaWRlbmN5ID0gcmVuZGVyaW5nLlJlc291cmNlUmVzaWRlbmN5O1xyXG4gICAgICAgIGNvbnN0IHByb2JlcyA9IHJlZmxlY3Rpb25Qcm9iZU1hbmFnZXIuZ2V0UHJvYmVzKCk7XHJcbiAgICAgICAgY29uc3QgbWF4UHJvYmVDb3VudCA9IDQ7XHJcbiAgICAgICAgbGV0IHByb2JlSUQgPSAwO1xyXG4gICAgICAgIGZvciAoY29uc3QgcHJvYmUgb2YgcHJvYmVzKSB7XHJcbiAgICAgICAgICAgIGlmICghcHJvYmUubmVlZFJlbmRlcikge1xyXG4gICAgICAgICAgICAgICAgY29udGludWU7XHJcbiAgICAgICAgICAgIH1cclxuICAgICAgICAgICAgY29uc3QgYXJlYSA9IHByb2JlLnJlbmRlckFyZWEoKTtcclxuICAgICAgICAgICAgY29uc3Qgd2lkdGggPSBNYXRoLm1heChNYXRoLmZsb29yKGFyZWEueCksIDEpO1xyXG4gICAgICAgICAgICBjb25zdCBoZWlnaHQgPSBNYXRoLm1heChNYXRoLmZsb29yKGFyZWEueSksIDEpO1xyXG5cclxuICAgICAgICAgICAgaWYgKHByb2JlLnByb2JlVHlwZSA9PT0gcmVuZGVyZXIuc2NlbmUuUHJvYmVUeXBlLlBMQU5BUikge1xyXG4gICAgICAgICAgICAgICAgaWYgKCFjYW1lcmFDb25maWdzLmVuYWJsZVBsYW5hclJlZmxlY3Rpb25Qcm9iZSkge1xyXG4gICAgICAgICAgICAgICAgICAgIGNvbnRpbnVlO1xyXG4gICAgICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICAgICAgbGV0IHJlZmxlY3Rpb25DYW1lcmEgPSBwcm9iZS5jYW1lcmE7XHJcbiAgICAgICAgICAgICAgICBpZiAoRURJVE9SICYmIHNvdXJjZUNhbWVyYS5jYW1lcmFVc2FnZSA9PT0gQ2FtZXJhVXNhZ2UuUFJFVklFVykge1xyXG4gICAgICAgICAgICAgICAgICAgIHJlZmxlY3Rpb25DYW1lcmEgPSBwcm9iZS5yZW5kZXJQcmV2aWV3UGxhbmFyUmVmbGVjdGlvbihzb3VyY2VDYW1lcmEpO1xyXG4gICAgICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICAgICAgY29uc3Qgd2luZG93OiByZW5kZXJlci5SZW5kZXJXaW5kb3cgPSBwcm9iZS5yZWFsdGltZVBsYW5hclRleHR1cmUhLndpbmRvdyE7XHJcbiAgICAgICAgICAgICAgICBjb25zdCBjb2xvck5hbWUgPSBgUGxhbmFyUHJvYmVSVCR7cHJvYmVJRH1gO1xyXG4gICAgICAgICAgICAgICAgY29uc3QgZGVwdGhTdGVuY2lsTmFtZSA9IGBQbGFuYXJQcm9iZURTJHtwcm9iZUlEfWA7XHJcbiAgICAgICAgICAgICAgICAvLyBQcm9iZVJlc291cmNlXHJcbiAgICAgICAgICAgICAgICBwcGwuYWRkUmVuZGVyV2luZG93KGNvbG9yTmFtZSxcclxuICAgICAgICAgICAgICAgICAgICBjYW1lcmFDb25maWdzLnJhZGlhbmNlRm9ybWF0LCB3aWR0aCwgaGVpZ2h0LCB3aW5kb3cpO1xyXG4gICAgICAgICAgICAgICAgcHBsLmFkZERlcHRoU3RlbmNpbChkZXB0aFN0ZW5jaWxOYW1lLFxyXG4gICAgICAgICAgICAgICAgICAgIGdmeC5Gb3JtYXQuREVQVEhfU1RFTkNJTCwgd2lkdGgsIGhlaWdodCwgUmVzb3VyY2VSZXNpZGVuY3kuTUVNT1JZTEVTUyk7XHJcblxyXG4gICAgICAgICAgICAgICAgLy8gUmVuZGVyaW5nXHJcbiAgICAgICAgICAgICAgICBjb25zdCBwcm9iZVBhc3MgPSBwcGwuYWRkUmVuZGVyUGFzcyh3aWR0aCwgaGVpZ2h0LCAnZGVmYXVsdCcpO1xyXG4gICAgICAgICAgICAgICAgcHJvYmVQYXNzLm5hbWUgPSBgUGxhbmFyUmVmbGVjdGlvblByb2JlJHtwcm9iZUlEfWA7XHJcbiAgICAgICAgICAgICAgICB0aGlzLl9idWlsZFJlZmxlY3Rpb25Qcm9iZVBhc3MocHJvYmVQYXNzLCBjYW1lcmFDb25maWdzLCBpZCwgcmVmbGVjdGlvbkNhbWVyYSwgcHJvYmUsXHJcbiAgICAgICAgICAgICAgICAgICAgY29sb3JOYW1lLCBkZXB0aFN0ZW5jaWxOYW1lLCBtYWluTGlnaHQpO1xyXG4gICAgICAgICAgICB9IGVsc2UgaWYgKEVESVRPUikge1xyXG4gICAgICAgICAgICAgICAgZm9yIChsZXQgZmFjZUlkeCA9IDA7IGZhY2VJZHggPCBwcm9iZS5iYWtlZEN1YmVUZXh0dXJlcy5sZW5ndGg7IGZhY2VJZHgrKykge1xyXG4gICAgICAgICAgICAgICAgICAgIHByb2JlLnVwZGF0ZUNhbWVyYURpcihmYWNlSWR4KTtcclxuICAgICAgICAgICAgICAgICAgICBjb25zdCB3aW5kb3c6IHJlbmRlcmVyLlJlbmRlcldpbmRvdyA9IHByb2JlLmJha2VkQ3ViZVRleHR1cmVzW2ZhY2VJZHhdLndpbmRvdyE7XHJcbiAgICAgICAgICAgICAgICAgICAgY29uc3QgY29sb3JOYW1lID0gYEN1YmVQcm9iZVJUJHtwcm9iZUlEfSR7ZmFjZUlkeH1gO1xyXG4gICAgICAgICAgICAgICAgICAgIGNvbnN0IGRlcHRoU3RlbmNpbE5hbWUgPSBgQ3ViZVByb2JlRFMke3Byb2JlSUR9JHtmYWNlSWR4fWA7XHJcbiAgICAgICAgICAgICAgICAgICAgLy8gUHJvYmVSZXNvdXJjZVxyXG4gICAgICAgICAgICAgICAgICAgIHBwbC5hZGRSZW5kZXJXaW5kb3coY29sb3JOYW1lLFxyXG4gICAgICAgICAgICAgICAgICAgICAgICBjYW1lcmFDb25maWdzLnJhZGlhbmNlRm9ybWF0LCB3aWR0aCwgaGVpZ2h0LCB3aW5kb3cpO1xyXG4gICAgICAgICAgICAgICAgICAgIHBwbC5hZGREZXB0aFN0ZW5jaWwoZGVwdGhTdGVuY2lsTmFtZSxcclxuICAgICAgICAgICAgICAgICAgICAgICAgZ2Z4LkZvcm1hdC5ERVBUSF9TVEVOQ0lMLCB3aWR0aCwgaGVpZ2h0LCBSZXNvdXJjZVJlc2lkZW5jeS5NRU1PUllMRVNTKTtcclxuXHJcbiAgICAgICAgICAgICAgICAgICAgLy8gUmVuZGVyaW5nXHJcbiAgICAgICAgICAgICAgICAgICAgY29uc3QgcHJvYmVQYXNzID0gcHBsLmFkZFJlbmRlclBhc3Mod2lkdGgsIGhlaWdodCwgJ2RlZmF1bHQnKTtcclxuICAgICAgICAgICAgICAgICAgICBwcm9iZVBhc3MubmFtZSA9IGBDdWJlUHJvYmUke3Byb2JlSUR9JHtmYWNlSWR4fWA7XHJcbiAgICAgICAgICAgICAgICAgICAgdGhpcy5fYnVpbGRSZWZsZWN0aW9uUHJvYmVQYXNzKHByb2JlUGFzcywgY2FtZXJhQ29uZmlncywgaWQsIHByb2JlLmNhbWVyYSwgcHJvYmUsXHJcbiAgICAgICAgICAgICAgICAgICAgICAgIGNvbG9yTmFtZSwgZGVwdGhTdGVuY2lsTmFtZSwgbWFpbkxpZ2h0KTtcclxuICAgICAgICAgICAgICAgIH1cclxuICAgICAgICAgICAgICAgIHByb2JlLm5lZWRSZW5kZXIgPSBmYWxzZTtcclxuICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICArK3Byb2JlSUQ7XHJcbiAgICAgICAgICAgIGlmIChwcm9iZUlEID09PSBtYXhQcm9iZUNvdW50KSB7XHJcbiAgICAgICAgICAgICAgICBicmVhaztcclxuICAgICAgICAgICAgfVxyXG4gICAgICAgIH1cclxuICAgIH1cclxuICAgIHByaXZhdGUgX2J1aWxkUmVmbGVjdGlvblByb2JlUGFzcyhcclxuICAgICAgICBwYXNzOiByZW5kZXJpbmcuQmFzaWNSZW5kZXJQYXNzQnVpbGRlcixcclxuICAgICAgICBjYW1lcmFDb25maWdzOiBSZWFkb25seTxDYW1lcmFDb25maWdzICYgRm9yd2FyZFBhc3NDb25maWdzPixcclxuICAgICAgICBpZDogbnVtYmVyLFxyXG4gICAgICAgIGNhbWVyYTogcmVuZGVyZXIuc2NlbmUuQ2FtZXJhLFxyXG4gICAgICAgIHByb2JlOiByZW5kZXJlci5zY2VuZS5SZWZsZWN0aW9uUHJvYmUsXHJcbiAgICAgICAgY29sb3JOYW1lOiBzdHJpbmcsXHJcbiAgICAgICAgZGVwdGhTdGVuY2lsTmFtZTogc3RyaW5nLFxyXG4gICAgICAgIG1haW5MaWdodDogcmVuZGVyZXIuc2NlbmUuRGlyZWN0aW9uYWxMaWdodCB8IG51bGwsXHJcbiAgICApOiB2b2lkIHtcclxuICAgICAgICBjb25zdCBRdWV1ZUhpbnQgPSByZW5kZXJpbmcuUXVldWVIaW50O1xyXG4gICAgICAgIGNvbnN0IFNjZW5lRmxhZ3MgPSByZW5kZXJpbmcuU2NlbmVGbGFncztcclxuICAgICAgICAvLyBzZXQgdmlld3BvcnRcclxuICAgICAgICBjb25zdCBjb2xvclN0b3JlT3AgPSBjYW1lcmFDb25maWdzLmVuYWJsZU1TQUEgPyBTdG9yZU9wLkRJU0NBUkQgOiBTdG9yZU9wLlNUT1JFO1xyXG5cclxuICAgICAgICAvLyBiaW5kIG91dHB1dCByZW5kZXIgdGFyZ2V0XHJcbiAgICAgICAgaWYgKGZvcndhcmROZWVkQ2xlYXJDb2xvcihjYW1lcmEpKSB7XHJcbiAgICAgICAgICAgIHRoaXMuX3JlZmxlY3Rpb25Qcm9iZUNsZWFyQ29sb3IueCA9IGNhbWVyYS5jbGVhckNvbG9yLng7XHJcbiAgICAgICAgICAgIHRoaXMuX3JlZmxlY3Rpb25Qcm9iZUNsZWFyQ29sb3IueSA9IGNhbWVyYS5jbGVhckNvbG9yLnk7XHJcbiAgICAgICAgICAgIHRoaXMuX3JlZmxlY3Rpb25Qcm9iZUNsZWFyQ29sb3IueiA9IGNhbWVyYS5jbGVhckNvbG9yLno7XHJcbiAgICAgICAgICAgIGNvbnN0IGNsZWFyQ29sb3IgPSByZW5kZXJpbmcucGFja1JHQkUodGhpcy5fcmVmbGVjdGlvblByb2JlQ2xlYXJDb2xvcik7XHJcbiAgICAgICAgICAgIHRoaXMuX2NsZWFyQ29sb3IueCA9IGNsZWFyQ29sb3IueDtcclxuICAgICAgICAgICAgdGhpcy5fY2xlYXJDb2xvci55ID0gY2xlYXJDb2xvci55O1xyXG4gICAgICAgICAgICB0aGlzLl9jbGVhckNvbG9yLnogPSBjbGVhckNvbG9yLno7XHJcbiAgICAgICAgICAgIHRoaXMuX2NsZWFyQ29sb3IudyA9IGNsZWFyQ29sb3IudztcclxuICAgICAgICAgICAgcGFzcy5hZGRSZW5kZXJUYXJnZXQoY29sb3JOYW1lLCBMb2FkT3AuQ0xFQVIsIGNvbG9yU3RvcmVPcCwgdGhpcy5fY2xlYXJDb2xvcik7XHJcbiAgICAgICAgfSBlbHNlIHtcclxuICAgICAgICAgICAgcGFzcy5hZGRSZW5kZXJUYXJnZXQoY29sb3JOYW1lLCBMb2FkT3AuTE9BRCwgY29sb3JTdG9yZU9wKTtcclxuICAgICAgICB9XHJcblxyXG4gICAgICAgIC8vIGJpbmQgZGVwdGggc3RlbmNpbCBidWZmZXJcclxuICAgICAgICBpZiAoY2FtZXJhLmNsZWFyRmxhZyAmIENsZWFyRmxhZ0JpdC5ERVBUSF9TVEVOQ0lMKSB7XHJcbiAgICAgICAgICAgIHBhc3MuYWRkRGVwdGhTdGVuY2lsKFxyXG4gICAgICAgICAgICAgICAgZGVwdGhTdGVuY2lsTmFtZSxcclxuICAgICAgICAgICAgICAgIExvYWRPcC5DTEVBUixcclxuICAgICAgICAgICAgICAgIFN0b3JlT3AuRElTQ0FSRCxcclxuICAgICAgICAgICAgICAgIGNhbWVyYS5jbGVhckRlcHRoLFxyXG4gICAgICAgICAgICAgICAgY2FtZXJhLmNsZWFyU3RlbmNpbCxcclxuICAgICAgICAgICAgICAgIGNhbWVyYS5jbGVhckZsYWcgJiBDbGVhckZsYWdCaXQuREVQVEhfU1RFTkNJTCxcclxuICAgICAgICAgICAgKTtcclxuICAgICAgICB9IGVsc2Uge1xyXG4gICAgICAgICAgICBwYXNzLmFkZERlcHRoU3RlbmNpbChkZXB0aFN0ZW5jaWxOYW1lLCBMb2FkT3AuTE9BRCwgU3RvcmVPcC5ESVNDQVJEKTtcclxuICAgICAgICB9XHJcblxyXG4gICAgICAgIC8vIFNldCBzaGFkb3cgbWFwIGlmIGVuYWJsZWRcclxuICAgICAgICBpZiAoY2FtZXJhQ29uZmlncy5lbmFibGVNYWluTGlnaHRTaGFkb3dNYXApIHtcclxuICAgICAgICAgICAgcGFzcy5hZGRUZXh0dXJlKGBTaGFkb3dNYXAke2lkfWAsICdjY19zaGFkb3dNYXAnKTtcclxuICAgICAgICB9XHJcblxyXG4gICAgICAgIC8vIFRPRE8oemhvdXpoZW5nbG9uZyk6IFNlcGFyYXRlIE9QQVFVRSBhbmQgTUFTSyBxdWV1ZVxyXG5cclxuICAgICAgICAvLyBhZGQgb3BhcXVlIGFuZCBtYXNrIHF1ZXVlXHJcbiAgICAgICAgY29uc3QgbGlnaHRJbmZvID0gbmV3IHJlbmRlcmluZy5MaWdodEluZm8obWFpbkxpZ2h0IHx8IG51bGwsIDAsIGZhbHNlLCBwcm9iZSk7XHJcbiAgICAgICAgcGFzcy5hZGRRdWV1ZShRdWV1ZUhpbnQuTk9ORSwgJ3JlZmxlY3QtbWFwJykgLy8gQ3VycmVudGx5IHdlIHB1dCBPUEFRVUUgYW5kIE1BU0sgaW50byBvbmUgcXVldWUsIHNvIFF1ZXVlSGludCBpcyBOT05FXHJcbiAgICAgICAgICAgIC5hZGRTY2VuZU9mQ2FtZXJhKGNhbWVyYSxcclxuICAgICAgICAgICAgICAgIGxpZ2h0SW5mbyxcclxuICAgICAgICAgICAgICAgIFNjZW5lRmxhZ3MuT1BBUVVFIHwgU2NlbmVGbGFncy5NQVNLIHwgU2NlbmVGbGFncy5SRUZMRUNUSU9OX1BST0JFKTtcclxuICAgIH1cclxuICAgIHByaXZhdGUgX2FkZEZvcndhcmRSYWRpYW5jZVBhc3NlcyhcclxuICAgICAgICBwcGw6IHJlbmRlcmluZy5CYXNpY1BpcGVsaW5lLFxyXG4gICAgICAgIHBwbENvbmZpZ3M6IFJlYWRvbmx5PFBpcGVsaW5lQ29uZmlncz4sXHJcbiAgICAgICAgY2FtZXJhQ29uZmlnczogUmVhZG9ubHk8Q2FtZXJhQ29uZmlncyAmIEZvcndhcmRQYXNzQ29uZmlncz4sXHJcbiAgICAgICAgaWQ6IG51bWJlcixcclxuICAgICAgICBjYW1lcmE6IHJlbmRlcmVyLnNjZW5lLkNhbWVyYSxcclxuICAgICAgICB3aWR0aDogbnVtYmVyLFxyXG4gICAgICAgIGhlaWdodDogbnVtYmVyLFxyXG4gICAgICAgIG1haW5MaWdodDogcmVuZGVyZXIuc2NlbmUuRGlyZWN0aW9uYWxMaWdodCB8IG51bGwsXHJcbiAgICAgICAgY29sb3JOYW1lOiBzdHJpbmcsXHJcbiAgICAgICAgZGVwdGhTdGVuY2lsTmFtZTogc3RyaW5nLFxyXG4gICAgICAgIGRpc2FibGVNU0FBOiBib29sZWFuID0gZmFsc2UsXHJcbiAgICAgICAgZGVwdGhTdGVuY2lsU3RvcmVPcDogZ2Z4LlN0b3JlT3AgPSBTdG9yZU9wLkRJU0NBUkQsXHJcbiAgICApOiByZW5kZXJpbmcuQmFzaWNSZW5kZXJQYXNzQnVpbGRlciB7XHJcbiAgICAgICAgY29uc3QgUXVldWVIaW50ID0gcmVuZGVyaW5nLlF1ZXVlSGludDtcclxuICAgICAgICBjb25zdCBTY2VuZUZsYWdzID0gcmVuZGVyaW5nLlNjZW5lRmxhZ3M7XHJcbiAgICAgICAgLy8gLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLVxyXG4gICAgICAgIC8vIER5bmFtaWMgc3RhdGVzXHJcbiAgICAgICAgLy8gLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLVxyXG4gICAgICAgIC8vIFByZXBhcmUgY2FtZXJhIGNsZWFyIGNvbG9yXHJcbiAgICAgICAgY29uc3QgY2xlYXJDb2xvciA9IGNhbWVyYS5jbGVhckNvbG9yOyAvLyBSZWR1Y2UgQysrL1RTIGludGVyb3BcclxuICAgICAgICB0aGlzLl9jbGVhckNvbG9yLnggPSBjbGVhckNvbG9yLng7XHJcbiAgICAgICAgdGhpcy5fY2xlYXJDb2xvci55ID0gY2xlYXJDb2xvci55O1xyXG4gICAgICAgIHRoaXMuX2NsZWFyQ29sb3IueiA9IGNsZWFyQ29sb3IuejtcclxuICAgICAgICB0aGlzLl9jbGVhckNvbG9yLncgPSBjbGVhckNvbG9yLnc7XHJcblxyXG4gICAgICAgIC8vIFByZXBhcmUgY2FtZXJhIHZpZXdwb3J0XHJcbiAgICAgICAgY29uc3Qgdmlld3BvcnQgPSBjYW1lcmEudmlld3BvcnQ7IC8vIFJlZHVjZSBDKysvVFMgaW50ZXJvcFxyXG4gICAgICAgIHRoaXMuX3ZpZXdwb3J0LmxlZnQgPSBNYXRoLnJvdW5kKHZpZXdwb3J0LnggKiB3aWR0aCk7XHJcbiAgICAgICAgdGhpcy5fdmlld3BvcnQudG9wID0gTWF0aC5yb3VuZCh2aWV3cG9ydC55ICogaGVpZ2h0KTtcclxuICAgICAgICAvLyBIZXJlIHdlIG11c3QgdXNlIGNhbWVyYS52aWV3cG9ydC53aWR0aCBpbnN0ZWFkIG9mIGNhbWVyYS52aWV3cG9ydC56LCB3aGljaFxyXG4gICAgICAgIC8vIGlzIHVuZGVmaW5lZCBvbiBuYXRpdmUgcGxhdGZvcm0uIFRoZSBzYW1lIGFzIGNhbWVyYS52aWV3cG9ydC5oZWlnaHQuXHJcbiAgICAgICAgdGhpcy5fdmlld3BvcnQud2lkdGggPSBNYXRoLm1heChNYXRoLnJvdW5kKHZpZXdwb3J0LndpZHRoICogd2lkdGgpLCAxKTtcclxuICAgICAgICB0aGlzLl92aWV3cG9ydC5oZWlnaHQgPSBNYXRoLm1heChNYXRoLnJvdW5kKHZpZXdwb3J0LmhlaWdodCAqIGhlaWdodCksIDEpO1xyXG5cclxuICAgICAgICAvLyBNU0FBXHJcbiAgICAgICAgY29uc3QgZW5hYmxlTVNBQSA9ICFkaXNhYmxlTVNBQSAmJiBjYW1lcmFDb25maWdzLmVuYWJsZU1TQUE7XHJcbiAgICAgICAgYXNzZXJ0KCFlbmFibGVNU0FBIHx8IGNhbWVyYUNvbmZpZ3MuZW5hYmxlU2luZ2xlRm9yd2FyZFBhc3MpO1xyXG5cclxuICAgICAgICAvLyAtLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tXHJcbiAgICAgICAgLy8gRm9yd2FyZCBMaWdodGluZyAoTWFpbiBEaXJlY3Rpb25hbCBMaWdodClcclxuICAgICAgICAvLyAtLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tXHJcbiAgICAgICAgY29uc3QgcGFzcyA9IGNhbWVyYUNvbmZpZ3MuZW5hYmxlU2luZ2xlRm9yd2FyZFBhc3NcclxuICAgICAgICAgICAgPyB0aGlzLl9hZGRGb3J3YXJkU2luZ2xlUmFkaWFuY2VQYXNzKHBwbCwgcHBsQ29uZmlncywgY2FtZXJhQ29uZmlncyxcclxuICAgICAgICAgICAgICAgIGlkLCBjYW1lcmEsIGVuYWJsZU1TQUEsIHdpZHRoLCBoZWlnaHQsIG1haW5MaWdodCxcclxuICAgICAgICAgICAgICAgIGNvbG9yTmFtZSwgZGVwdGhTdGVuY2lsTmFtZSwgZGVwdGhTdGVuY2lsU3RvcmVPcClcclxuICAgICAgICAgICAgOiB0aGlzLl9hZGRGb3J3YXJkTXVsdGlwbGVSYWRpYW5jZVBhc3NlcyhwcGwsIGNhbWVyYUNvbmZpZ3MsXHJcbiAgICAgICAgICAgICAgICBpZCwgY2FtZXJhLCB3aWR0aCwgaGVpZ2h0LCBtYWluTGlnaHQsXHJcbiAgICAgICAgICAgICAgICBjb2xvck5hbWUsIGRlcHRoU3RlbmNpbE5hbWUsIGRlcHRoU3RlbmNpbFN0b3JlT3ApO1xyXG5cclxuICAgICAgICAvLyBQbGFuYXIgU2hhZG93XHJcbiAgICAgICAgaWYgKGNhbWVyYUNvbmZpZ3MuZW5hYmxlTWFpbkxpZ2h0UGxhbmFyU2hhZG93TWFwKSB7XHJcbiAgICAgICAgICAgIHRoaXMuX2FkZFBsYW5hclNoYWRvd1F1ZXVlKGNhbWVyYSwgbWFpbkxpZ2h0LCBwYXNzKTtcclxuICAgICAgICB9XHJcblxyXG4gICAgICAgIC8vIC0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS1cclxuICAgICAgICAvLyBGb3J3YXJkIExpZ2h0aW5nIChCbGVuZClcclxuICAgICAgICAvLyAtLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tXHJcbiAgICAgICAgLy8gQWRkIHRyYW5zcGFyZW50IHF1ZXVlXHJcblxyXG4gICAgICAgIGNvbnN0IHNjZW5lRmxhZ3MgPSBTY2VuZUZsYWdzLkJMRU5EIHxcclxuICAgICAgICAgICAgKGNhbWVyYS5nZW9tZXRyeVJlbmRlcmVyXHJcbiAgICAgICAgICAgICAgICA/IFNjZW5lRmxhZ3MuR0VPTUVUUllcclxuICAgICAgICAgICAgICAgIDogU2NlbmVGbGFncy5OT05FKTtcclxuXHJcbiAgICAgICAgcGFzc1xyXG4gICAgICAgICAgICAuYWRkUXVldWUoUXVldWVIaW50LkJMRU5EKVxyXG4gICAgICAgICAgICAuYWRkU2NlbmUoY2FtZXJhLCBzY2VuZUZsYWdzLCBtYWluTGlnaHQgfHwgdW5kZWZpbmVkKTtcclxuXHJcbiAgICAgICAgcmV0dXJuIHBhc3M7XHJcbiAgICB9XHJcbiAgICBwcml2YXRlIF9hZGRGb3J3YXJkU2luZ2xlUmFkaWFuY2VQYXNzKFxyXG4gICAgICAgIHBwbDogcmVuZGVyaW5nLkJhc2ljUGlwZWxpbmUsXHJcbiAgICAgICAgcHBsQ29uZmlnczogUmVhZG9ubHk8UGlwZWxpbmVDb25maWdzPixcclxuICAgICAgICBjYW1lcmFDb25maWdzOiBSZWFkb25seTxDYW1lcmFDb25maWdzICYgRm9yd2FyZFBhc3NDb25maWdzPixcclxuICAgICAgICBpZDogbnVtYmVyLFxyXG4gICAgICAgIGNhbWVyYTogcmVuZGVyZXIuc2NlbmUuQ2FtZXJhLFxyXG4gICAgICAgIGVuYWJsZU1TQUE6IGJvb2xlYW4sXHJcbiAgICAgICAgd2lkdGg6IG51bWJlcixcclxuICAgICAgICBoZWlnaHQ6IG51bWJlcixcclxuICAgICAgICBtYWluTGlnaHQ6IHJlbmRlcmVyLnNjZW5lLkRpcmVjdGlvbmFsTGlnaHQgfCBudWxsLFxyXG4gICAgICAgIGNvbG9yTmFtZTogc3RyaW5nLFxyXG4gICAgICAgIGRlcHRoU3RlbmNpbE5hbWU6IHN0cmluZyxcclxuICAgICAgICBkZXB0aFN0ZW5jaWxTdG9yZU9wOiBnZnguU3RvcmVPcFxyXG4gICAgKTogcmVuZGVyaW5nLkJhc2ljUmVuZGVyUGFzc0J1aWxkZXIge1xyXG4gICAgICAgIGFzc2VydChjYW1lcmFDb25maWdzLmVuYWJsZVNpbmdsZUZvcndhcmRQYXNzKTtcclxuICAgICAgICAvLyAtLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tXHJcbiAgICAgICAgLy8gRm9yd2FyZCBMaWdodGluZyAoTWFpbiBEaXJlY3Rpb25hbCBMaWdodClcclxuICAgICAgICAvLyAtLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tXHJcbiAgICAgICAgbGV0IHBhc3M6IHJlbmRlcmluZy5CYXNpY1JlbmRlclBhc3NCdWlsZGVyO1xyXG4gICAgICAgIGlmIChlbmFibGVNU0FBKSB7XHJcbiAgICAgICAgICAgIGNvbnN0IG1zYWFSYWRpYW5jZU5hbWUgPSBgTXNhYVJhZGlhbmNlJHtpZH1gO1xyXG4gICAgICAgICAgICBjb25zdCBtc2FhRGVwdGhTdGVuY2lsTmFtZSA9IGBNc2FhRGVwdGhTdGVuY2lsJHtpZH1gO1xyXG4gICAgICAgICAgICBjb25zdCBzYW1wbGVDb3VudCA9IGNhbWVyYUNvbmZpZ3Muc2V0dGluZ3MubXNhYS5zYW1wbGVDb3VudDtcclxuXHJcbiAgICAgICAgICAgIGNvbnN0IG1zUGFzcyA9IHBwbC5hZGRNdWx0aXNhbXBsZVJlbmRlclBhc3Mod2lkdGgsIGhlaWdodCwgc2FtcGxlQ291bnQsIDAsICdkZWZhdWx0Jyk7XHJcbiAgICAgICAgICAgIG1zUGFzcy5uYW1lID0gJ01zYWFGb3J3YXJkUGFzcyc7XHJcblxyXG4gICAgICAgICAgICAvLyBNU0FBIGFsd2F5cyBkaXNjYXJkcyBkZXB0aCBzdGVuY2lsXHJcbiAgICAgICAgICAgIHRoaXMuX2J1aWxkRm9yd2FyZE1haW5MaWdodFBhc3MobXNQYXNzLCBjYW1lcmFDb25maWdzLCBpZCwgY2FtZXJhLFxyXG4gICAgICAgICAgICAgICAgbXNhYVJhZGlhbmNlTmFtZSwgbXNhYURlcHRoU3RlbmNpbE5hbWUsIFN0b3JlT3AuRElTQ0FSRCwgbWFpbkxpZ2h0KTtcclxuXHJcbiAgICAgICAgICAgIG1zUGFzcy5yZXNvbHZlUmVuZGVyVGFyZ2V0KG1zYWFSYWRpYW5jZU5hbWUsIGNvbG9yTmFtZSk7XHJcblxyXG4gICAgICAgICAgICBwYXNzID0gbXNQYXNzO1xyXG4gICAgICAgIH0gZWxzZSB7XHJcbiAgICAgICAgICAgIHBhc3MgPSBwcGwuYWRkUmVuZGVyUGFzcyh3aWR0aCwgaGVpZ2h0LCAnZGVmYXVsdCcpO1xyXG4gICAgICAgICAgICBwYXNzLm5hbWUgPSAnRm9yd2FyZFBhc3MnO1xyXG5cclxuICAgICAgICAgICAgdGhpcy5fYnVpbGRGb3J3YXJkTWFpbkxpZ2h0UGFzcyhwYXNzLCBjYW1lcmFDb25maWdzLCBpZCwgY2FtZXJhLFxyXG4gICAgICAgICAgICAgICAgY29sb3JOYW1lLCBkZXB0aFN0ZW5jaWxOYW1lLCBkZXB0aFN0ZW5jaWxTdG9yZU9wLCBtYWluTGlnaHQpO1xyXG4gICAgICAgIH1cclxuICAgICAgICBhc3NlcnQocGFzcyAhPT0gdW5kZWZpbmVkKTtcclxuXHJcbiAgICAgICAgLy8gRm9yd2FyZCBMaWdodGluZyAoQWRkaXRpdmUgTGlnaHRzKVxyXG4gICAgICAgIHRoaXMuZm9yd2FyZExpZ2h0aW5nLmFkZExpZ2h0UXVldWVzKFxyXG4gICAgICAgICAgICBwYXNzLFxyXG4gICAgICAgICAgICBjYW1lcmEsXHJcbiAgICAgICAgICAgIHBwbENvbmZpZ3MubW9iaWxlTWF4U3BvdExpZ2h0U2hhZG93TWFwcyxcclxuICAgICAgICApO1xyXG5cclxuICAgICAgICByZXR1cm4gcGFzcztcclxuICAgIH1cclxuICAgIHByaXZhdGUgX2FkZEZvcndhcmRNdWx0aXBsZVJhZGlhbmNlUGFzc2VzKFxyXG4gICAgICAgIHBwbDogcmVuZGVyaW5nLkJhc2ljUGlwZWxpbmUsXHJcbiAgICAgICAgY2FtZXJhQ29uZmlnczogUmVhZG9ubHk8Q2FtZXJhQ29uZmlncyAmIEZvcndhcmRQYXNzQ29uZmlncz4sXHJcbiAgICAgICAgaWQ6IG51bWJlcixcclxuICAgICAgICBjYW1lcmE6IHJlbmRlcmVyLnNjZW5lLkNhbWVyYSxcclxuICAgICAgICB3aWR0aDogbnVtYmVyLFxyXG4gICAgICAgIGhlaWdodDogbnVtYmVyLFxyXG4gICAgICAgIG1haW5MaWdodDogcmVuZGVyZXIuc2NlbmUuRGlyZWN0aW9uYWxMaWdodCB8IG51bGwsXHJcbiAgICAgICAgY29sb3JOYW1lOiBzdHJpbmcsXHJcbiAgICAgICAgZGVwdGhTdGVuY2lsTmFtZTogc3RyaW5nLFxyXG4gICAgICAgIGRlcHRoU3RlbmNpbFN0b3JlT3A6IGdmeC5TdG9yZU9wXHJcbiAgICApOiByZW5kZXJpbmcuQmFzaWNSZW5kZXJQYXNzQnVpbGRlciB7XHJcbiAgICAgICAgYXNzZXJ0KCFjYW1lcmFDb25maWdzLmVuYWJsZVNpbmdsZUZvcndhcmRQYXNzKTtcclxuXHJcbiAgICAgICAgLy8gRm9yd2FyZCBMaWdodGluZyAoTWFpbiBEaXJlY3Rpb25hbCBMaWdodClcclxuICAgICAgICBsZXQgcGFzcyA9IHBwbC5hZGRSZW5kZXJQYXNzKHdpZHRoLCBoZWlnaHQsICdkZWZhdWx0Jyk7XHJcbiAgICAgICAgcGFzcy5uYW1lID0gJ0ZvcndhcmRQYXNzJztcclxuXHJcbiAgICAgICAgY29uc3QgZmlyc3RTdG9yZU9wID0gdGhpcy5mb3J3YXJkTGlnaHRpbmcuaXNNdWx0aXBsZUxpZ2h0UGFzc2VzTmVlZGVkKClcclxuICAgICAgICAgICAgPyBTdG9yZU9wLlNUT1JFXHJcbiAgICAgICAgICAgIDogZGVwdGhTdGVuY2lsU3RvcmVPcDtcclxuXHJcbiAgICAgICAgdGhpcy5fYnVpbGRGb3J3YXJkTWFpbkxpZ2h0UGFzcyhwYXNzLCBjYW1lcmFDb25maWdzLFxyXG4gICAgICAgICAgICBpZCwgY2FtZXJhLCBjb2xvck5hbWUsIGRlcHRoU3RlbmNpbE5hbWUsIGZpcnN0U3RvcmVPcCwgbWFpbkxpZ2h0KTtcclxuXHJcbiAgICAgICAgLy8gRm9yd2FyZCBMaWdodGluZyAoQWRkaXRpdmUgTGlnaHRzKVxyXG4gICAgICAgIHBhc3MgPSB0aGlzLmZvcndhcmRMaWdodGluZ1xyXG4gICAgICAgICAgICAuYWRkTGlnaHRQYXNzZXMoY29sb3JOYW1lLCBkZXB0aFN0ZW5jaWxOYW1lLCBkZXB0aFN0ZW5jaWxTdG9yZU9wLFxyXG4gICAgICAgICAgICAgICAgaWQsIHdpZHRoLCBoZWlnaHQsIGNhbWVyYSwgdGhpcy5fdmlld3BvcnQsIHBwbCwgcGFzcyk7XHJcblxyXG4gICAgICAgIHJldHVybiBwYXNzO1xyXG4gICAgfVxyXG4gICAgcHJpdmF0ZSBfYnVpbGRGb3J3YXJkTWFpbkxpZ2h0UGFzcyhcclxuICAgICAgICBwYXNzOiByZW5kZXJpbmcuQmFzaWNSZW5kZXJQYXNzQnVpbGRlcixcclxuICAgICAgICBjYW1lcmFDb25maWdzOiBSZWFkb25seTxDYW1lcmFDb25maWdzICYgRm9yd2FyZFBhc3NDb25maWdzPixcclxuICAgICAgICBpZDogbnVtYmVyLFxyXG4gICAgICAgIGNhbWVyYTogcmVuZGVyZXIuc2NlbmUuQ2FtZXJhLFxyXG4gICAgICAgIGNvbG9yTmFtZTogc3RyaW5nLFxyXG4gICAgICAgIGRlcHRoU3RlbmNpbE5hbWU6IHN0cmluZyxcclxuICAgICAgICBkZXB0aFN0ZW5jaWxTdG9yZU9wOiBnZnguU3RvcmVPcCxcclxuICAgICAgICBtYWluTGlnaHQ6IHJlbmRlcmVyLnNjZW5lLkRpcmVjdGlvbmFsTGlnaHQgfCBudWxsLFxyXG4gICAgICAgIHNjZW5lOiByZW5kZXJlci5SZW5kZXJTY2VuZSB8IG51bGwgPSBudWxsLFxyXG4gICAgKTogdm9pZCB7XHJcbiAgICAgICAgY29uc3QgUXVldWVIaW50ID0gcmVuZGVyaW5nLlF1ZXVlSGludDtcclxuICAgICAgICBjb25zdCBTY2VuZUZsYWdzID0gcmVuZGVyaW5nLlNjZW5lRmxhZ3M7XHJcbiAgICAgICAgLy8gc2V0IHZpZXdwb3J0XHJcbiAgICAgICAgcGFzcy5zZXRWaWV3cG9ydCh0aGlzLl92aWV3cG9ydCk7XHJcblxyXG4gICAgICAgIGNvbnN0IGNvbG9yU3RvcmVPcCA9IGNhbWVyYUNvbmZpZ3MuZW5hYmxlTVNBQSA/IFN0b3JlT3AuRElTQ0FSRCA6IFN0b3JlT3AuU1RPUkU7XHJcblxyXG4gICAgICAgIC8vIGJpbmQgb3V0cHV0IHJlbmRlciB0YXJnZXRcclxuICAgICAgICBpZiAoZm9yd2FyZE5lZWRDbGVhckNvbG9yKGNhbWVyYSkpIHtcclxuICAgICAgICAgICAgcGFzcy5hZGRSZW5kZXJUYXJnZXQoY29sb3JOYW1lLCBMb2FkT3AuQ0xFQVIsIGNvbG9yU3RvcmVPcCwgdGhpcy5fY2xlYXJDb2xvcik7XHJcbiAgICAgICAgfSBlbHNlIHtcclxuICAgICAgICAgICAgcGFzcy5hZGRSZW5kZXJUYXJnZXQoY29sb3JOYW1lLCBMb2FkT3AuTE9BRCwgY29sb3JTdG9yZU9wKTtcclxuICAgICAgICB9XHJcblxyXG4gICAgICAgIC8vIGJpbmQgZGVwdGggc3RlbmNpbCBidWZmZXJcclxuICAgICAgICBpZiAoREVCVUcpIHtcclxuICAgICAgICAgICAgaWYgKGNvbG9yTmFtZSA9PT0gY2FtZXJhQ29uZmlncy5jb2xvck5hbWUgJiZcclxuICAgICAgICAgICAgICAgIGRlcHRoU3RlbmNpbE5hbWUgIT09IGNhbWVyYUNvbmZpZ3MuZGVwdGhTdGVuY2lsTmFtZSkge1xyXG4gICAgICAgICAgICAgICAgd2FybignRGVmYXVsdCBmcmFtZWJ1ZmZlciBjYW5ub3QgdXNlIGN1c3RvbSBkZXB0aCBzdGVuY2lsIGJ1ZmZlcicpO1xyXG4gICAgICAgICAgICB9XHJcbiAgICAgICAgfVxyXG5cclxuICAgICAgICBpZiAoY2FtZXJhLmNsZWFyRmxhZyAmIENsZWFyRmxhZ0JpdC5ERVBUSF9TVEVOQ0lMKSB7XHJcbiAgICAgICAgICAgIHBhc3MuYWRkRGVwdGhTdGVuY2lsKFxyXG4gICAgICAgICAgICAgICAgZGVwdGhTdGVuY2lsTmFtZSxcclxuICAgICAgICAgICAgICAgIExvYWRPcC5DTEVBUixcclxuICAgICAgICAgICAgICAgIGRlcHRoU3RlbmNpbFN0b3JlT3AsXHJcbiAgICAgICAgICAgICAgICBjYW1lcmEuY2xlYXJEZXB0aCxcclxuICAgICAgICAgICAgICAgIGNhbWVyYS5jbGVhclN0ZW5jaWwsXHJcbiAgICAgICAgICAgICAgICBjYW1lcmEuY2xlYXJGbGFnICYgQ2xlYXJGbGFnQml0LkRFUFRIX1NURU5DSUwsXHJcbiAgICAgICAgICAgICk7XHJcbiAgICAgICAgfSBlbHNlIHtcclxuICAgICAgICAgICAgcGFzcy5hZGREZXB0aFN0ZW5jaWwoZGVwdGhTdGVuY2lsTmFtZSwgTG9hZE9wLkxPQUQsIGRlcHRoU3RlbmNpbFN0b3JlT3ApO1xyXG4gICAgICAgIH1cclxuXHJcbiAgICAgICAgLy8gU2V0IHNoYWRvdyBtYXAgaWYgZW5hYmxlZFxyXG4gICAgICAgIGlmIChjYW1lcmFDb25maWdzLmVuYWJsZU1haW5MaWdodFNoYWRvd01hcCkge1xyXG4gICAgICAgICAgICBwYXNzLmFkZFRleHR1cmUoYFNoYWRvd01hcCR7aWR9YCwgJ2NjX3NoYWRvd01hcCcpO1xyXG4gICAgICAgIH1cclxuXHJcbiAgICAgICAgLy8gVE9ETyh6aG91emhlbmdsb25nKTogU2VwYXJhdGUgT1BBUVVFIGFuZCBNQVNLIHF1ZXVlXHJcblxyXG4gICAgICAgIC8vIGFkZCBvcGFxdWUgYW5kIG1hc2sgcXVldWVcclxuICAgICAgICBwYXNzLmFkZFF1ZXVlKFF1ZXVlSGludC5OT05FKSAvLyBDdXJyZW50bHkgd2UgcHV0IE9QQVFVRSBhbmQgTUFTSyBpbnRvIG9uZSBxdWV1ZSwgc28gUXVldWVIaW50IGlzIE5PTkVcclxuICAgICAgICAgICAgLmFkZFNjZW5lKGNhbWVyYSxcclxuICAgICAgICAgICAgICAgIFNjZW5lRmxhZ3MuT1BBUVVFIHwgU2NlbmVGbGFncy5NQVNLLFxyXG4gICAgICAgICAgICAgICAgbWFpbkxpZ2h0IHx8IHVuZGVmaW5lZCxcclxuICAgICAgICAgICAgICAgIHNjZW5lID8gc2NlbmUgOiB1bmRlZmluZWQpO1xyXG4gICAgfVxyXG4gICAgcHJpdmF0ZSBfYWRkUGxhbmFyU2hhZG93UXVldWUoXHJcbiAgICAgICAgY2FtZXJhOiByZW5kZXJlci5zY2VuZS5DYW1lcmEsXHJcbiAgICAgICAgbWFpbkxpZ2h0OiByZW5kZXJlci5zY2VuZS5EaXJlY3Rpb25hbExpZ2h0IHwgbnVsbCxcclxuICAgICAgICBwYXNzOiByZW5kZXJpbmcuQmFzaWNSZW5kZXJQYXNzQnVpbGRlcixcclxuICAgICkge1xyXG4gICAgICAgIGNvbnN0IFF1ZXVlSGludCA9IHJlbmRlcmluZy5RdWV1ZUhpbnQ7XHJcbiAgICAgICAgY29uc3QgU2NlbmVGbGFncyA9IHJlbmRlcmluZy5TY2VuZUZsYWdzO1xyXG4gICAgICAgIHBhc3MuYWRkUXVldWUoUXVldWVIaW50LkJMRU5ELCAncGxhbmFyLXNoYWRvdycpXHJcbiAgICAgICAgICAgIC5hZGRTY2VuZShcclxuICAgICAgICAgICAgICAgIGNhbWVyYSxcclxuICAgICAgICAgICAgICAgIFNjZW5lRmxhZ3MuU0hBRE9XX0NBU1RFUiB8IFNjZW5lRmxhZ3MuUExBTkFSX1NIQURPVyB8IFNjZW5lRmxhZ3MuQkxFTkQsXHJcbiAgICAgICAgICAgICAgICBtYWluTGlnaHQgfHwgdW5kZWZpbmVkLFxyXG4gICAgICAgICAgICApO1xyXG4gICAgfVxyXG4gICAgcHJpdmF0ZSByZWFkb25seSBmb3J3YXJkTGlnaHRpbmcgPSBuZXcgRm9yd2FyZExpZ2h0aW5nKCk7XHJcbiAgICBwcml2YXRlIHJlYWRvbmx5IF92aWV3cG9ydCA9IG5ldyBWaWV3cG9ydCgpO1xyXG4gICAgcHJpdmF0ZSByZWFkb25seSBfY2xlYXJDb2xvciA9IG5ldyBDb2xvcigwLCAwLCAwLCAxKTtcclxuICAgIHByaXZhdGUgcmVhZG9ubHkgX3JlZmxlY3Rpb25Qcm9iZUNsZWFyQ29sb3IgPSBuZXcgVmVjMygwLCAwLCAwKTtcclxufVxyXG5cclxuZXhwb3J0IGludGVyZmFjZSBCbG9vbVBhc3NDb25maWdzIHtcclxuICAgIGVuYWJsZUJsb29tOiBib29sZWFuO1xyXG59XHJcblxyXG5mdW5jdGlvbiBkb3duU2l6ZShzaXplOiBudW1iZXIsIHNjYWxlOiBudW1iZXIpOiBudW1iZXIge1xyXG4gICAgcmV0dXJuIE1hdGgubWF4KE1hdGguZmxvb3Ioc2l6ZSAqIHNjYWxlKSwgMSk7XHJcbn1cclxuXHJcbmludGVyZmFjZSBSZW5kZXJUZXh0dXJlRGVzYyB7XHJcbiAgICBuYW1lOiBzdHJpbmc7XHJcbiAgICB3aWR0aDogbnVtYmVyO1xyXG4gICAgaGVpZ2h0OiBudW1iZXI7XHJcbn1cclxuXHJcbmV4cG9ydCBjbGFzcyBCdWlsdGluQmxvb21QYXNzQnVpbGRlciBpbXBsZW1lbnRzIHJlbmRlcmluZy5QaXBlbGluZVBhc3NCdWlsZGVyIHtcclxuICAgIGdldENvbmZpZ09yZGVyKCk6IG51bWJlciB7XHJcbiAgICAgICAgcmV0dXJuIDA7XHJcbiAgICB9XHJcbiAgICBnZXRSZW5kZXJPcmRlcigpOiBudW1iZXIge1xyXG4gICAgICAgIHJldHVybiAyMDA7XHJcbiAgICB9XHJcbiAgICBjb25maWdDYW1lcmEoXHJcbiAgICAgICAgY2FtZXJhOiBSZWFkb25seTxyZW5kZXJlci5zY2VuZS5DYW1lcmE+LFxyXG4gICAgICAgIHBpcGVsaW5lQ29uZmlnczogUmVhZG9ubHk8UGlwZWxpbmVDb25maWdzPixcclxuICAgICAgICBjYW1lcmFDb25maWdzOiBDYW1lcmFDb25maWdzICYgQmxvb21QYXNzQ29uZmlncyk6IHZvaWQge1xyXG4gICAgICAgIGNvbnN0IHsgYmxvb20gfSA9IGNhbWVyYUNvbmZpZ3Muc2V0dGluZ3M7XHJcbiAgICAgICAgY29uc3QgaGFzVmFsaWRNYXRlcmlhbCA9IChcclxuICAgICAgICAgICAgYmxvb20udHlwZSA9PT0gQmxvb21UeXBlLkthd2FzZUR1YWxGaWx0ZXIgJiYgISFibG9vbS5rYXdhc2VGaWx0ZXJNYXRlcmlhbCB8fFxyXG4gICAgICAgICAgICBibG9vbS50eXBlID09PSBCbG9vbVR5cGUuTWlwbWFwRmlsdGVyICYmICEhYmxvb20ubWlwbWFwRmlsdGVyTWF0ZXJpYWxcclxuICAgICAgICApO1xyXG4gICAgICAgIGNhbWVyYUNvbmZpZ3MuZW5hYmxlQmxvb20gPSBibG9vbS5lbmFibGVkICYmIGhhc1ZhbGlkTWF0ZXJpYWw7XHJcblxyXG4gICAgICAgIGlmIChjYW1lcmFDb25maWdzLmVuYWJsZUJsb29tKSB7XHJcbiAgICAgICAgICAgICsrY2FtZXJhQ29uZmlncy5yZW1haW5pbmdQYXNzZXM7XHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG4gICAgd2luZG93UmVzaXplKFxyXG4gICAgICAgIHBwbDogcmVuZGVyaW5nLkJhc2ljUGlwZWxpbmUsXHJcbiAgICAgICAgcHBsQ29uZmlnczogUmVhZG9ubHk8UGlwZWxpbmVDb25maWdzPixcclxuICAgICAgICBjYW1lcmFDb25maWdzOiBDYW1lcmFDb25maWdzICYgQmxvb21QYXNzQ29uZmlncyxcclxuICAgICAgICB3aW5kb3c6IHJlbmRlcmVyLlJlbmRlcldpbmRvdyk6IHZvaWQge1xyXG4gICAgICAgIGlmICghY2FtZXJhQ29uZmlncy5lbmFibGVCbG9vbSkge1xyXG4gICAgICAgICAgICByZXR1cm47XHJcbiAgICAgICAgfVxyXG5cclxuICAgICAgICBjb25zdCB7IHdpZHRoLCBoZWlnaHQsIHNldHRpbmdzOiB7IGJsb29tIH0gfSA9IGNhbWVyYUNvbmZpZ3M7XHJcbiAgICAgICAgY29uc3QgaWQgPSB3aW5kb3cucmVuZGVyV2luZG93SWQ7XHJcbiAgICAgICAgY29uc3QgZm9ybWF0ID0gY2FtZXJhQ29uZmlncy5yYWRpYW5jZUZvcm1hdDtcclxuXHJcbiAgICAgICAgaWYgKGJsb29tLnR5cGUgPT09IEJsb29tVHlwZS5LYXdhc2VEdWFsRmlsdGVyKSB7XHJcbiAgICAgICAgICAgIGxldCBibG9vbVdpZHRoID0gY2FtZXJhQ29uZmlncy53aWR0aDtcclxuICAgICAgICAgICAgbGV0IGJsb29tSGVpZ2h0ID0gY2FtZXJhQ29uZmlncy5oZWlnaHQ7XHJcbiAgICAgICAgICAgIGZvciAobGV0IGkgPSAwOyBpICE9PSBibG9vbS5pdGVyYXRpb25zICsgMTsgKytpKSB7XHJcbiAgICAgICAgICAgICAgICBibG9vbVdpZHRoID0gTWF0aC5tYXgoTWF0aC5mbG9vcihibG9vbVdpZHRoIC8gMiksIDEpO1xyXG4gICAgICAgICAgICAgICAgYmxvb21IZWlnaHQgPSBNYXRoLm1heChNYXRoLmZsb29yKGJsb29tSGVpZ2h0IC8gMiksIDEpO1xyXG4gICAgICAgICAgICAgICAgcHBsLmFkZFJlbmRlclRhcmdldChgQmxvb21UZXgke2lkfV8ke2l9YCwgZm9ybWF0LCBibG9vbVdpZHRoLCBibG9vbUhlaWdodCk7XHJcbiAgICAgICAgICAgIH1cclxuICAgICAgICB9IGVsc2UgaWYgKGJsb29tLnR5cGUgPT09IEJsb29tVHlwZS5NaXBtYXBGaWx0ZXIpIHtcclxuICAgICAgICAgICAgY29uc3QgaXRlcmF0aW9ucyA9IGJsb29tLml0ZXJhdGlvbnM7XHJcbiAgICAgICAgICAgIGZvciAobGV0IGkgPSAwOyBpICE9PSBpdGVyYXRpb25zICsgMTsgKytpKSB7XHJcbiAgICAgICAgICAgICAgICAvLyBEb3duU2FtcGxlXHJcbiAgICAgICAgICAgICAgICBpZiAoaSA8IGl0ZXJhdGlvbnMpIHtcclxuICAgICAgICAgICAgICAgICAgICBjb25zdCBzY2FsZSA9IE1hdGgucG93KDAuNSwgaSArIDIpO1xyXG4gICAgICAgICAgICAgICAgICAgIHRoaXMuX2Jsb29tRG93blNhbXBsZVRleERlc2NzW2ldID0gdGhpcy5jcmVhdGVUZXh0dXJlKFxyXG4gICAgICAgICAgICAgICAgICAgICAgICBwcGwsXHJcbiAgICAgICAgICAgICAgICAgICAgICAgIGBEb3duU2FtcGxlQ29sb3Ike2lkfSR7aX1gLFxyXG4gICAgICAgICAgICAgICAgICAgICAgICBkb3duU2l6ZSh3aWR0aCwgc2NhbGUpLFxyXG4gICAgICAgICAgICAgICAgICAgICAgICBkb3duU2l6ZShoZWlnaHQsIHNjYWxlKSxcclxuICAgICAgICAgICAgICAgICAgICAgICAgZm9ybWF0KTtcclxuICAgICAgICAgICAgICAgIH1cclxuICAgICAgICAgICAgICAgIC8vIFVwU2FtcGxlXHJcbiAgICAgICAgICAgICAgICBpZiAoaSA8IGl0ZXJhdGlvbnMgLSAxKSB7XHJcbiAgICAgICAgICAgICAgICAgICAgY29uc3Qgc2NhbGUgPSBNYXRoLnBvdygwLjUsIGl0ZXJhdGlvbnMgLSBpIC0gMSk7XHJcbiAgICAgICAgICAgICAgICAgICAgdGhpcy5fYmxvb21VcFNhbXBsZVRleERlc2NzW2ldID0gdGhpcy5jcmVhdGVUZXh0dXJlKFxyXG4gICAgICAgICAgICAgICAgICAgICAgICBwcGwsXHJcbiAgICAgICAgICAgICAgICAgICAgICAgIGBVcFNhbXBsZUNvbG9yJHtpZH0ke2l9YCxcclxuICAgICAgICAgICAgICAgICAgICAgICAgZG93blNpemUod2lkdGgsIHNjYWxlKSxcclxuICAgICAgICAgICAgICAgICAgICAgICAgZG93blNpemUoaGVpZ2h0LCBzY2FsZSksXHJcbiAgICAgICAgICAgICAgICAgICAgICAgIGZvcm1hdCk7XHJcbiAgICAgICAgICAgICAgICB9XHJcbiAgICAgICAgICAgIH1cclxuICAgICAgICAgICAgdGhpcy5fb3JpZ2luYWxDb2xvckRlc2MgPSB0aGlzLmNyZWF0ZVRleHR1cmUocHBsLCBgT3JpZ2luYWxDb2xvciR7aWR9YCwgd2lkdGgsIGhlaWdodCwgZm9ybWF0KTtcclxuICAgICAgICAgICAgdGhpcy5fcHJlZmlsdGVyVGV4RGVzYyA9IHRoaXMuY3JlYXRlVGV4dHVyZShwcGwsIGBQcmVmaWx0ZXJDb2xvciR7aWR9YCxcclxuICAgICAgICAgICAgICAgIGRvd25TaXplKHdpZHRoLCAwLjUpLCBkb3duU2l6ZShoZWlnaHQsIDAuNSksIGZvcm1hdCk7XHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG4gICAgcHJpdmF0ZSBjcmVhdGVUZXh0dXJlKFxyXG4gICAgICAgIHBwbDogcmVuZGVyaW5nLkJhc2ljUGlwZWxpbmUsXHJcbiAgICAgICAgbmFtZTogc3RyaW5nLCB3aWR0aDogbnVtYmVyLCBoZWlnaHQ6IG51bWJlciwgZm9ybWF0OiBudW1iZXIpOiBSZW5kZXJUZXh0dXJlRGVzYyB7XHJcbiAgICAgICAgY29uc3QgZGVzYyA9IHsgbmFtZSwgd2lkdGgsIGhlaWdodCB9O1xyXG4gICAgICAgIHBwbC5hZGRSZW5kZXJUYXJnZXQoZGVzYy5uYW1lLCBmb3JtYXQsIGRlc2Mud2lkdGgsIGRlc2MuaGVpZ2h0KTtcclxuICAgICAgICByZXR1cm4gZGVzYztcclxuICAgIH1cclxuXHJcbiAgICBzZXR1cChcclxuICAgICAgICBwcGw6IHJlbmRlcmluZy5CYXNpY1BpcGVsaW5lLFxyXG4gICAgICAgIHBwbENvbmZpZ3M6IFJlYWRvbmx5PFBpcGVsaW5lQ29uZmlncz4sXHJcbiAgICAgICAgY2FtZXJhQ29uZmlnczogQ2FtZXJhQ29uZmlncyAmIEJsb29tUGFzc0NvbmZpZ3MsXHJcbiAgICAgICAgY2FtZXJhOiByZW5kZXJlci5zY2VuZS5DYW1lcmEsXHJcbiAgICAgICAgY29udGV4dDogUGlwZWxpbmVDb250ZXh0LFxyXG4gICAgICAgIHByZXZSZW5kZXJQYXNzPzogcmVuZGVyaW5nLkJhc2ljUmVuZGVyUGFzc0J1aWxkZXIpXHJcbiAgICAgICAgOiByZW5kZXJpbmcuQmFzaWNSZW5kZXJQYXNzQnVpbGRlciB8IHVuZGVmaW5lZCB7XHJcbiAgICAgICAgaWYgKCFjYW1lcmFDb25maWdzLmVuYWJsZUJsb29tKSB7XHJcbiAgICAgICAgICAgIHJldHVybiBwcmV2UmVuZGVyUGFzcztcclxuICAgICAgICB9XHJcblxyXG4gICAgICAgIC0tY2FtZXJhQ29uZmlncy5yZW1haW5pbmdQYXNzZXM7XHJcbiAgICAgICAgYXNzZXJ0KGNhbWVyYUNvbmZpZ3MucmVtYWluaW5nUGFzc2VzID49IDApO1xyXG5cclxuICAgICAgICBjb25zdCBibG9vbSA9IGNhbWVyYUNvbmZpZ3Muc2V0dGluZ3MuYmxvb207XHJcbiAgICAgICAgY29uc3QgaWQgPSBjYW1lcmEud2luZG93LnJlbmRlcldpbmRvd0lkO1xyXG5cclxuICAgICAgICBzd2l0Y2ggKGJsb29tLnR5cGUpIHtcclxuICAgICAgICAgICAgY2FzZSBCbG9vbVR5cGUuS2F3YXNlRHVhbEZpbHRlcjoge1xyXG4gICAgICAgICAgICAgICAgY29uc3QgbWF0ZXJpYWwgPSBibG9vbS5rYXdhc2VGaWx0ZXJNYXRlcmlhbDtcclxuICAgICAgICAgICAgICAgIGFzc2VydCghIW1hdGVyaWFsKTtcclxuICAgICAgICAgICAgICAgIHJldHVybiB0aGlzLl9hZGRLYXdhc2VEdWFsRmlsdGVyQmxvb21QYXNzZXMoXHJcbiAgICAgICAgICAgICAgICAgICAgcHBsLCBwcGxDb25maWdzLFxyXG4gICAgICAgICAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3MsXHJcbiAgICAgICAgICAgICAgICAgICAgY2FtZXJhQ29uZmlncy5zZXR0aW5ncyxcclxuICAgICAgICAgICAgICAgICAgICBtYXRlcmlhbCxcclxuICAgICAgICAgICAgICAgICAgICBpZCxcclxuICAgICAgICAgICAgICAgICAgICBjYW1lcmFDb25maWdzLndpZHRoLFxyXG4gICAgICAgICAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3MuaGVpZ2h0LFxyXG4gICAgICAgICAgICAgICAgICAgIGNvbnRleHQuY29sb3JOYW1lKTtcclxuICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICBjYXNlIEJsb29tVHlwZS5NaXBtYXBGaWx0ZXI6IHtcclxuICAgICAgICAgICAgICAgIGNvbnN0IG1hdGVyaWFsID0gYmxvb20ubWlwbWFwRmlsdGVyTWF0ZXJpYWw7XHJcbiAgICAgICAgICAgICAgICBhc3NlcnQoISFtYXRlcmlhbCk7XHJcbiAgICAgICAgICAgICAgICByZXR1cm4gdGhpcy5fYWRkTWlwbWFwRmlsdGVyQmxvb21QYXNzZXMoXHJcbiAgICAgICAgICAgICAgICAgICAgcHBsLCBwcGxDb25maWdzLFxyXG4gICAgICAgICAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3MsXHJcbiAgICAgICAgICAgICAgICAgICAgY2FtZXJhQ29uZmlncy5zZXR0aW5ncyxcclxuICAgICAgICAgICAgICAgICAgICBtYXRlcmlhbCxcclxuICAgICAgICAgICAgICAgICAgICBpZCxcclxuICAgICAgICAgICAgICAgICAgICBjYW1lcmFDb25maWdzLndpZHRoLFxyXG4gICAgICAgICAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3MuaGVpZ2h0LFxyXG4gICAgICAgICAgICAgICAgICAgIGNvbnRleHQuY29sb3JOYW1lKTtcclxuICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICBkZWZhdWx0OlxyXG4gICAgICAgICAgICAgICAgcmV0dXJuIHByZXZSZW5kZXJQYXNzO1xyXG4gICAgICAgIH1cclxuICAgIH1cclxuICAgIHByaXZhdGUgX2FkZEthd2FzZUR1YWxGaWx0ZXJCbG9vbVBhc3NlcyhcclxuICAgICAgICBwcGw6IHJlbmRlcmluZy5CYXNpY1BpcGVsaW5lLFxyXG4gICAgICAgIHBwbENvbmZpZ3M6IFJlYWRvbmx5PFBpcGVsaW5lQ29uZmlncz4sXHJcbiAgICAgICAgY2FtZXJhQ29uZmlnczogQ2FtZXJhQ29uZmlncyAmIFJlYWRvbmx5PEJsb29tUGFzc0NvbmZpZ3M+LFxyXG4gICAgICAgIHNldHRpbmdzOiBQaXBlbGluZVNldHRpbmdzLFxyXG4gICAgICAgIGJsb29tTWF0ZXJpYWw6IE1hdGVyaWFsLFxyXG4gICAgICAgIGlkOiBudW1iZXIsXHJcbiAgICAgICAgd2lkdGg6IG51bWJlcixcclxuICAgICAgICBoZWlnaHQ6IG51bWJlcixcclxuICAgICAgICByYWRpYW5jZU5hbWU6IHN0cmluZyxcclxuICAgICk6IHJlbmRlcmluZy5CYXNpY1JlbmRlclBhc3NCdWlsZGVyIHtcclxuICAgICAgICBjb25zdCBRdWV1ZUhpbnQgPSByZW5kZXJpbmcuUXVldWVIaW50O1xyXG4gICAgICAgIC8vIEJhc2VkIG9uIEthd2FzZSBEdWFsIEZpbHRlciBCbHVyLiBTYXZlcyBiYW5kd2lkdGggb24gbW9iaWxlIGRldmljZXMuXHJcbiAgICAgICAgLy8gZXNsaW50LWRpc2FibGUtbmV4dC1saW5lIG1heC1sZW5cclxuICAgICAgICAvLyBodHRwczovL2NvbW11bml0eS5hcm0uY29tL2Nmcy1maWxlL19fa2V5L2NvbW11bml0eXNlcnZlci1ibG9ncy1jb21wb25lbnRzLXdlYmxvZ2ZpbGVzLzAwLTAwLTAwLTIwLTY2L3NpZ2dyYXBoMjAxNV8yRDAwX21tZ18yRDAwX21hcml1c18yRDAwX3NsaWRlcy5wZGZcclxuXHJcbiAgICAgICAgLy8gU2l6ZTogW3ByZWZpbHRlcigxLzIpLCBkb3duc2FtcGxlKDEvNCksIGRvd25zYW1wbGUoMS84KSwgZG93bnNhbXBsZSgxLzE2KSwgLi4uXVxyXG4gICAgICAgIGNvbnN0IGl0ZXJhdGlvbnMgPSBzZXR0aW5ncy5ibG9vbS5pdGVyYXRpb25zO1xyXG4gICAgICAgIGNvbnN0IHNpemVDb3VudCA9IGl0ZXJhdGlvbnMgKyAxO1xyXG4gICAgICAgIHRoaXMuX2Jsb29tV2lkdGhzLmxlbmd0aCA9IHNpemVDb3VudDtcclxuICAgICAgICB0aGlzLl9ibG9vbUhlaWdodHMubGVuZ3RoID0gc2l6ZUNvdW50O1xyXG4gICAgICAgIHRoaXMuX2Jsb29tV2lkdGhzWzBdID0gTWF0aC5tYXgoTWF0aC5mbG9vcih3aWR0aCAvIDIpLCAxKTtcclxuICAgICAgICB0aGlzLl9ibG9vbUhlaWdodHNbMF0gPSBNYXRoLm1heChNYXRoLmZsb29yKGhlaWdodCAvIDIpLCAxKTtcclxuICAgICAgICBmb3IgKGxldCBpID0gMTsgaSAhPT0gc2l6ZUNvdW50OyArK2kpIHtcclxuICAgICAgICAgICAgdGhpcy5fYmxvb21XaWR0aHNbaV0gPSBNYXRoLm1heChNYXRoLmZsb29yKHRoaXMuX2Jsb29tV2lkdGhzW2kgLSAxXSAvIDIpLCAxKTtcclxuICAgICAgICAgICAgdGhpcy5fYmxvb21IZWlnaHRzW2ldID0gTWF0aC5tYXgoTWF0aC5mbG9vcih0aGlzLl9ibG9vbUhlaWdodHNbaSAtIDFdIC8gMiksIDEpO1xyXG4gICAgICAgIH1cclxuXHJcbiAgICAgICAgLy8gQmxvb20gdGV4dHVyZSBuYW1lc1xyXG4gICAgICAgIHRoaXMuX2Jsb29tVGV4TmFtZXMubGVuZ3RoID0gc2l6ZUNvdW50O1xyXG4gICAgICAgIGZvciAobGV0IGkgPSAwOyBpICE9PSBzaXplQ291bnQ7ICsraSkge1xyXG4gICAgICAgICAgICB0aGlzLl9ibG9vbVRleE5hbWVzW2ldID0gYEJsb29tVGV4JHtpZH1fJHtpfWA7XHJcbiAgICAgICAgfVxyXG5cclxuICAgICAgICAvLyBTZXR1cCBibG9vbSBwYXJhbWV0ZXJzXHJcbiAgICAgICAgdGhpcy5fYmxvb21QYXJhbXMueCA9IHBwbENvbmZpZ3MudXNlRmxvYXRPdXRwdXQgPyAxIDogMDtcclxuICAgICAgICB0aGlzLl9ibG9vbVBhcmFtcy55ID0gMDsgLy8gdW51c2VkXHJcbiAgICAgICAgdGhpcy5fYmxvb21QYXJhbXMueiA9IHNldHRpbmdzLmJsb29tLnRocmVzaG9sZDtcclxuICAgICAgICB0aGlzLl9ibG9vbVBhcmFtcy53ID0gc2V0dGluZ3MuYmxvb20uZW5hYmxlQWxwaGFNYXNrID8gMSA6IDA7XHJcblxyXG4gICAgICAgIC8vIFByZWZpbHRlciBwYXNzXHJcbiAgICAgICAgY29uc3QgcHJlZmlsdGVyUGFzcyA9IHBwbC5hZGRSZW5kZXJQYXNzKHRoaXMuX2Jsb29tV2lkdGhzWzBdLCB0aGlzLl9ibG9vbUhlaWdodHNbMF0sICdjYy1ibG9vbS1wcmVmaWx0ZXInKTtcclxuICAgICAgICBwcmVmaWx0ZXJQYXNzLmFkZFJlbmRlclRhcmdldChcclxuICAgICAgICAgICAgdGhpcy5fYmxvb21UZXhOYW1lc1swXSxcclxuICAgICAgICAgICAgTG9hZE9wLkNMRUFSLFxyXG4gICAgICAgICAgICBTdG9yZU9wLlNUT1JFLFxyXG4gICAgICAgICAgICB0aGlzLl9jbGVhckNvbG9yVHJhbnNwYXJlbnRCbGFjayxcclxuICAgICAgICApO1xyXG4gICAgICAgIHByZWZpbHRlclBhc3MuYWRkVGV4dHVyZShyYWRpYW5jZU5hbWUsICdpbnB1dFRleHR1cmUnKTtcclxuICAgICAgICBwcmVmaWx0ZXJQYXNzLnNldFZlYzQoJ2Jsb29tUGFyYW1zJywgdGhpcy5fYmxvb21QYXJhbXMpO1xyXG4gICAgICAgIHByZWZpbHRlclBhc3NcclxuICAgICAgICAgICAgLmFkZFF1ZXVlKFF1ZXVlSGludC5PUEFRVUUpXHJcbiAgICAgICAgICAgIC5hZGRGdWxsc2NyZWVuUXVhZChibG9vbU1hdGVyaWFsLCAwKTtcclxuXHJcbiAgICAgICAgLy8gRG93bnNhbXBsZSBwYXNzZXNcclxuICAgICAgICBmb3IgKGxldCBpID0gMTsgaSAhPT0gc2l6ZUNvdW50OyArK2kpIHtcclxuICAgICAgICAgICAgY29uc3QgZG93blBhc3MgPSBwcGwuYWRkUmVuZGVyUGFzcyh0aGlzLl9ibG9vbVdpZHRoc1tpXSwgdGhpcy5fYmxvb21IZWlnaHRzW2ldLCAnY2MtYmxvb20tZG93bnNhbXBsZScpO1xyXG4gICAgICAgICAgICBkb3duUGFzcy5hZGRSZW5kZXJUYXJnZXQodGhpcy5fYmxvb21UZXhOYW1lc1tpXSwgTG9hZE9wLkNMRUFSLCBTdG9yZU9wLlNUT1JFLCB0aGlzLl9jbGVhckNvbG9yVHJhbnNwYXJlbnRCbGFjayk7XHJcbiAgICAgICAgICAgIGRvd25QYXNzLmFkZFRleHR1cmUodGhpcy5fYmxvb21UZXhOYW1lc1tpIC0gMV0sICdibG9vbVRleHR1cmUnKTtcclxuICAgICAgICAgICAgdGhpcy5fYmxvb21UZXhTaXplLnggPSB0aGlzLl9ibG9vbVdpZHRoc1tpIC0gMV07XHJcbiAgICAgICAgICAgIHRoaXMuX2Jsb29tVGV4U2l6ZS55ID0gdGhpcy5fYmxvb21IZWlnaHRzW2kgLSAxXTtcclxuICAgICAgICAgICAgZG93blBhc3Muc2V0VmVjNCgnYmxvb21UZXhTaXplJywgdGhpcy5fYmxvb21UZXhTaXplKTtcclxuICAgICAgICAgICAgZG93blBhc3NcclxuICAgICAgICAgICAgICAgIC5hZGRRdWV1ZShRdWV1ZUhpbnQuT1BBUVVFKVxyXG4gICAgICAgICAgICAgICAgLmFkZEZ1bGxzY3JlZW5RdWFkKGJsb29tTWF0ZXJpYWwsIDEpO1xyXG4gICAgICAgIH1cclxuXHJcbiAgICAgICAgLy8gVXBzYW1wbGUgcGFzc2VzXHJcbiAgICAgICAgZm9yIChsZXQgaSA9IGl0ZXJhdGlvbnM7IGktLSA+IDA7KSB7XHJcbiAgICAgICAgICAgIGNvbnN0IHVwUGFzcyA9IHBwbC5hZGRSZW5kZXJQYXNzKHRoaXMuX2Jsb29tV2lkdGhzW2ldLCB0aGlzLl9ibG9vbUhlaWdodHNbaV0sICdjYy1ibG9vbS11cHNhbXBsZScpO1xyXG4gICAgICAgICAgICB1cFBhc3MuYWRkUmVuZGVyVGFyZ2V0KHRoaXMuX2Jsb29tVGV4TmFtZXNbaV0sIExvYWRPcC5DTEVBUiwgU3RvcmVPcC5TVE9SRSwgdGhpcy5fY2xlYXJDb2xvclRyYW5zcGFyZW50QmxhY2spO1xyXG4gICAgICAgICAgICB1cFBhc3MuYWRkVGV4dHVyZSh0aGlzLl9ibG9vbVRleE5hbWVzW2kgKyAxXSwgJ2Jsb29tVGV4dHVyZScpO1xyXG4gICAgICAgICAgICB0aGlzLl9ibG9vbVRleFNpemUueCA9IHRoaXMuX2Jsb29tV2lkdGhzW2kgKyAxXTtcclxuICAgICAgICAgICAgdGhpcy5fYmxvb21UZXhTaXplLnkgPSB0aGlzLl9ibG9vbUhlaWdodHNbaSArIDFdO1xyXG4gICAgICAgICAgICB1cFBhc3Muc2V0VmVjNCgnYmxvb21UZXhTaXplJywgdGhpcy5fYmxvb21UZXhTaXplKTtcclxuICAgICAgICAgICAgdXBQYXNzXHJcbiAgICAgICAgICAgICAgICAuYWRkUXVldWUoUXVldWVIaW50Lk9QQVFVRSlcclxuICAgICAgICAgICAgICAgIC5hZGRGdWxsc2NyZWVuUXVhZChibG9vbU1hdGVyaWFsLCAyKTtcclxuICAgICAgICB9XHJcblxyXG4gICAgICAgIC8vIENvbWJpbmUgcGFzc1xyXG4gICAgICAgIHRoaXMuX2Jsb29tUGFyYW1zLncgPSBzZXR0aW5ncy5ibG9vbS5pbnRlbnNpdHk7XHJcbiAgICAgICAgY29uc3QgY29tYmluZVBhc3MgPSBwcGwuYWRkUmVuZGVyUGFzcyh3aWR0aCwgaGVpZ2h0LCAnY2MtYmxvb20tY29tYmluZScpO1xyXG4gICAgICAgIGNvbWJpbmVQYXNzLmFkZFJlbmRlclRhcmdldChyYWRpYW5jZU5hbWUsIExvYWRPcC5MT0FELCBTdG9yZU9wLlNUT1JFKTtcclxuICAgICAgICBjb21iaW5lUGFzcy5hZGRUZXh0dXJlKHRoaXMuX2Jsb29tVGV4TmFtZXNbMF0sICdibG9vbVRleHR1cmUnKTtcclxuICAgICAgICBjb21iaW5lUGFzcy5zZXRWZWM0KCdibG9vbVBhcmFtcycsIHRoaXMuX2Jsb29tUGFyYW1zKTtcclxuICAgICAgICBjb21iaW5lUGFzc1xyXG4gICAgICAgICAgICAuYWRkUXVldWUoUXVldWVIaW50LkJMRU5EKVxyXG4gICAgICAgICAgICAuYWRkRnVsbHNjcmVlblF1YWQoYmxvb21NYXRlcmlhbCwgMyk7XHJcblxyXG4gICAgICAgIGlmIChjYW1lcmFDb25maWdzLnJlbWFpbmluZ1Bhc3NlcyA9PT0gMCkge1xyXG4gICAgICAgICAgICByZXR1cm4gYWRkQ29weVRvU2NyZWVuUGFzcyhwcGwsIHBwbENvbmZpZ3MsIGNhbWVyYUNvbmZpZ3MsIHJhZGlhbmNlTmFtZSk7XHJcbiAgICAgICAgfSBlbHNlIHtcclxuICAgICAgICAgICAgcmV0dXJuIGNvbWJpbmVQYXNzO1xyXG4gICAgICAgIH1cclxuICAgIH1cclxuICAgIHByaXZhdGUgX2FkZFBhc3MoXHJcbiAgICAgICAgcHBsOiByZW5kZXJpbmcuQmFzaWNQaXBlbGluZSxcclxuICAgICAgICB3aWR0aDogbnVtYmVyLFxyXG4gICAgICAgIGhlaWdodDogbnVtYmVyLFxyXG4gICAgICAgIGxheW91dDogc3RyaW5nLFxyXG4gICAgICAgIGNvbG9yTmFtZTogc3RyaW5nLFxyXG4gICAgICAgIG1hdGVyaWFsOiBNYXRlcmlhbCxcclxuICAgICAgICBwYXNzSW5kZXg6IG51bWJlcixcclxuICAgICAgICBsb2FkT3A6IGdmeC5Mb2FkT3AgPSBMb2FkT3AuQ0xFQVIsXHJcbiAgICAgICAgY2xlYXJDb2xvcjogZ2Z4LkNvbG9yID0gc0NsZWFyQ29sb3JUcmFuc3BhcmVudEJsYWNrLFxyXG4gICAgICAgIHF1ZXVlSGludDogcmVuZGVyaW5nLlF1ZXVlSGludCA9IHJlbmRlcmluZy5RdWV1ZUhpbnQuT1BBUVVFKTogcmVuZGVyaW5nLkJhc2ljUmVuZGVyUGFzc0J1aWxkZXIge1xyXG4gICAgICAgIGNvbnN0IHBhc3MgPSBwcGwuYWRkUmVuZGVyUGFzcyh3aWR0aCwgaGVpZ2h0LCBsYXlvdXQpO1xyXG4gICAgICAgIHBhc3MuYWRkUmVuZGVyVGFyZ2V0KGNvbG9yTmFtZSwgbG9hZE9wLCBTdG9yZU9wLlNUT1JFLCBjbGVhckNvbG9yKTtcclxuICAgICAgICBwYXNzLmFkZFF1ZXVlKHF1ZXVlSGludClcclxuICAgICAgICAgICAgLmFkZEZ1bGxzY3JlZW5RdWFkKG1hdGVyaWFsLCBwYXNzSW5kZXgpO1xyXG4gICAgICAgIHJldHVybiBwYXNzO1xyXG4gICAgfVxyXG4gICAgcHJpdmF0ZSBfYWRkTWlwbWFwRmlsdGVyQmxvb21QYXNzZXMoXHJcbiAgICAgICAgcHBsOiByZW5kZXJpbmcuQmFzaWNQaXBlbGluZSxcclxuICAgICAgICBwcGxDb25maWdzOiBSZWFkb25seTxQaXBlbGluZUNvbmZpZ3M+LFxyXG4gICAgICAgIGNhbWVyYUNvbmZpZ3M6IENhbWVyYUNvbmZpZ3MgJiBSZWFkb25seTxCbG9vbVBhc3NDb25maWdzPixcclxuICAgICAgICBzZXR0aW5nczogUGlwZWxpbmVTZXR0aW5ncyxcclxuICAgICAgICBibG9vbU1hdGVyaWFsOiBNYXRlcmlhbCxcclxuICAgICAgICBpZDogbnVtYmVyLFxyXG4gICAgICAgIHdpZHRoOiBudW1iZXIsXHJcbiAgICAgICAgaGVpZ2h0OiBudW1iZXIsXHJcbiAgICAgICAgcmFkaWFuY2VOYW1lOiBzdHJpbmcsXHJcbiAgICApOiByZW5kZXJpbmcuQmFzaWNSZW5kZXJQYXNzQnVpbGRlciB7XHJcbiAgICAgICAgLy8gU2V0dXAgYmxvb20gcGFyYW1ldGVyc1xyXG4gICAgICAgIHRoaXMuX2Jsb29tUGFyYW1zLnggPSBwcGxDb25maWdzLnVzZUZsb2F0T3V0cHV0ID8gMSA6IDA7XHJcbiAgICAgICAgdGhpcy5fYmxvb21QYXJhbXMueCA9IDA7IC8vIHVudXNlZFxyXG4gICAgICAgIHRoaXMuX2Jsb29tUGFyYW1zLnogPSBzZXR0aW5ncy5ibG9vbS50aHJlc2hvbGQ7XHJcbiAgICAgICAgdGhpcy5fYmxvb21QYXJhbXMudyA9IHNldHRpbmdzLmJsb29tLmludGVuc2l0eTtcclxuICAgICAgICBjb25zdCBwcmVmaWx0ZXJJbmZvID0gdGhpcy5fcHJlZmlsdGVyVGV4RGVzYztcclxuXHJcbiAgICAgICAgLy8gUHJlZmlsdGVyIHBhc3NcclxuICAgICAgICBsZXQgY3VyclNhbXBsZVBhc3MgPSB0aGlzLl9hZGRQYXNzKFxyXG4gICAgICAgICAgICBwcGwsXHJcbiAgICAgICAgICAgIHByZWZpbHRlckluZm8ud2lkdGgsXHJcbiAgICAgICAgICAgIHByZWZpbHRlckluZm8uaGVpZ2h0LFxyXG4gICAgICAgICAgICAnY2MtYmxvb20tbWlwbWFwLXByZWZpbHRlcicsXHJcbiAgICAgICAgICAgIHByZWZpbHRlckluZm8ubmFtZSxcclxuICAgICAgICAgICAgYmxvb21NYXRlcmlhbCxcclxuICAgICAgICAgICAgMCxcclxuICAgICAgICApO1xyXG4gICAgICAgIGN1cnJTYW1wbGVQYXNzLmFkZFRleHR1cmUocmFkaWFuY2VOYW1lLCAnbWFpblRleHR1cmUnKTtcclxuICAgICAgICBjdXJyU2FtcGxlUGFzcy5zZXRWZWM0KCdibG9vbVBhcmFtcycsIHRoaXMuX2Jsb29tUGFyYW1zKTtcclxuXHJcbiAgICAgICAgY29uc3QgZG93blNhbXBsZUluZm9zID0gdGhpcy5fYmxvb21Eb3duU2FtcGxlVGV4RGVzY3M7XHJcbiAgICAgICAgLy8gRG93bnNhbXBsZSBwYXNzZXNcclxuICAgICAgICBmb3IgKGxldCBpID0gMDsgaSA8IGRvd25TYW1wbGVJbmZvcy5sZW5ndGg7ICsraSkge1xyXG4gICAgICAgICAgICBjb25zdCBjdXJySW5mbyA9IGRvd25TYW1wbGVJbmZvc1tpXTtcclxuICAgICAgICAgICAgY29uc3Qgc2FtcGxlclNyYyA9IGkgPT09IDAgPyBwcmVmaWx0ZXJJbmZvIDogZG93blNhbXBsZUluZm9zW2kgLSAxXTtcclxuICAgICAgICAgICAgY29uc3Qgc2FtcGxlclNyY05hbWUgPSBzYW1wbGVyU3JjLm5hbWU7XHJcbiAgICAgICAgICAgIHRoaXMuX2Jsb29tVGV4U2l6ZS54ID0gMSAvIHNhbXBsZXJTcmMud2lkdGg7XHJcbiAgICAgICAgICAgIHRoaXMuX2Jsb29tVGV4U2l6ZS55ID0gMSAvIHNhbXBsZXJTcmMuaGVpZ2h0O1xyXG4gICAgICAgICAgICBjdXJyU2FtcGxlUGFzcyA9IHRoaXMuX2FkZFBhc3MoXHJcbiAgICAgICAgICAgICAgICBwcGwsXHJcbiAgICAgICAgICAgICAgICBjdXJySW5mby53aWR0aCxcclxuICAgICAgICAgICAgICAgIGN1cnJJbmZvLmhlaWdodCxcclxuICAgICAgICAgICAgICAgICdjYy1ibG9vbS1taXBtYXAtZG93bnNhbXBsZScsXHJcbiAgICAgICAgICAgICAgICBjdXJySW5mby5uYW1lLFxyXG4gICAgICAgICAgICAgICAgYmxvb21NYXRlcmlhbCxcclxuICAgICAgICAgICAgICAgIDEsXHJcbiAgICAgICAgICAgICk7XHJcbiAgICAgICAgICAgIGN1cnJTYW1wbGVQYXNzLmFkZFRleHR1cmUoc2FtcGxlclNyY05hbWUsICdtYWluVGV4dHVyZScpO1xyXG4gICAgICAgICAgICBjdXJyU2FtcGxlUGFzcy5zZXRWZWM0KCdibG9vbVBhcmFtcycsIHRoaXMuX2Jsb29tVGV4U2l6ZSk7XHJcbiAgICAgICAgfVxyXG4gICAgICAgIGNvbnN0IGxhc3RJbmRleCA9IGRvd25TYW1wbGVJbmZvcy5sZW5ndGggLSAxO1xyXG4gICAgICAgIGNvbnN0IHVwU2FtcGxlSW5mb3MgPSB0aGlzLl9ibG9vbVVwU2FtcGxlVGV4RGVzY3M7XHJcbiAgICAgICAgLy8gVXBzYW1wbGUgcGFzc2VzXHJcbiAgICAgICAgZm9yIChsZXQgaSA9IDA7IGkgPCB1cFNhbXBsZUluZm9zLmxlbmd0aDsgaSsrKSB7XHJcbiAgICAgICAgICAgIGNvbnN0IGN1cnJJbmZvID0gdXBTYW1wbGVJbmZvc1tpXTtcclxuICAgICAgICAgICAgY29uc3Qgc2FtcGxlU3JjID0gaSA9PT0gMCA/IGRvd25TYW1wbGVJbmZvc1tsYXN0SW5kZXhdIDogdXBTYW1wbGVJbmZvc1tpIC0gMV07XHJcbiAgICAgICAgICAgIGNvbnN0IHNhbXBsZVNyY05hbWUgPSBzYW1wbGVTcmMubmFtZTtcclxuICAgICAgICAgICAgdGhpcy5fYmxvb21UZXhTaXplLnggPSAxIC8gc2FtcGxlU3JjLndpZHRoO1xyXG4gICAgICAgICAgICB0aGlzLl9ibG9vbVRleFNpemUueSA9IDEgLyBzYW1wbGVTcmMuaGVpZ2h0O1xyXG4gICAgICAgICAgICBjdXJyU2FtcGxlUGFzcyA9IHRoaXMuX2FkZFBhc3MoXHJcbiAgICAgICAgICAgICAgICBwcGwsXHJcbiAgICAgICAgICAgICAgICBjdXJySW5mby53aWR0aCxcclxuICAgICAgICAgICAgICAgIGN1cnJJbmZvLmhlaWdodCxcclxuICAgICAgICAgICAgICAgICdjYy1ibG9vbS1taXBtYXAtdXBzYW1wbGUnLFxyXG4gICAgICAgICAgICAgICAgY3VyckluZm8ubmFtZSxcclxuICAgICAgICAgICAgICAgIGJsb29tTWF0ZXJpYWwsXHJcbiAgICAgICAgICAgICAgICAyLFxyXG4gICAgICAgICAgICApO1xyXG4gICAgICAgICAgICBjdXJyU2FtcGxlUGFzcy5hZGRUZXh0dXJlKHNhbXBsZVNyY05hbWUsICdtYWluVGV4dHVyZScpO1xyXG4gICAgICAgICAgICBjdXJyU2FtcGxlUGFzcy5hZGRUZXh0dXJlKGRvd25TYW1wbGVJbmZvc1tsYXN0SW5kZXggLSAxIC0gaV0ubmFtZSwgJ2Rvd25zYW1wbGVUZXh0dXJlJyk7XHJcbiAgICAgICAgICAgIGN1cnJTYW1wbGVQYXNzLnNldFZlYzQoJ2Jsb29tUGFyYW1zJywgdGhpcy5fYmxvb21UZXhTaXplKTtcclxuICAgICAgICB9XHJcblxyXG4gICAgICAgIC8vIENvbWJpbmUgcGFzc1xyXG4gICAgICAgIGNvbnN0IGNvbWJpbmVQYXNzID0gdGhpcy5fYWRkUGFzcyhcclxuICAgICAgICAgICAgcHBsLFxyXG4gICAgICAgICAgICB3aWR0aCxcclxuICAgICAgICAgICAgaGVpZ2h0LFxyXG4gICAgICAgICAgICAnY2MtYmxvb20tbWlwbWFwLWNvbWJpbmUnLFxyXG4gICAgICAgICAgICByYWRpYW5jZU5hbWUsXHJcbiAgICAgICAgICAgIGJsb29tTWF0ZXJpYWwsXHJcbiAgICAgICAgICAgIDMsXHJcbiAgICAgICAgICAgIExvYWRPcC5MT0FELFxyXG4gICAgICAgICk7XHJcbiAgICAgICAgY29tYmluZVBhc3MuYWRkVGV4dHVyZSh1cFNhbXBsZUluZm9zW3VwU2FtcGxlSW5mb3MubGVuZ3RoIC0gMV0ubmFtZSwgJ2Jsb29tVGV4dHVyZScpO1xyXG4gICAgICAgIGNvbWJpbmVQYXNzLnNldFZlYzQoJ2Jsb29tUGFyYW1zJywgdGhpcy5fYmxvb21QYXJhbXMpO1xyXG4gICAgICAgIGlmIChjYW1lcmFDb25maWdzLnJlbWFpbmluZ1Bhc3NlcyA9PT0gMCkge1xyXG4gICAgICAgICAgICByZXR1cm4gYWRkQ29weVRvU2NyZWVuUGFzcyhwcGwsIHBwbENvbmZpZ3MsIGNhbWVyYUNvbmZpZ3MsIHJhZGlhbmNlTmFtZSk7XHJcbiAgICAgICAgfSBlbHNlIHtcclxuICAgICAgICAgICAgcmV0dXJuIGNvbWJpbmVQYXNzO1xyXG4gICAgICAgIH1cclxuICAgIH1cclxuXHJcbiAgICAvLyBCbG9vbVxyXG4gICAgcHJpdmF0ZSByZWFkb25seSBfY2xlYXJDb2xvclRyYW5zcGFyZW50QmxhY2sgPSBuZXcgQ29sb3IoMCwgMCwgMCwgMCk7XHJcbiAgICBwcml2YXRlIHJlYWRvbmx5IF9ibG9vbVBhcmFtcyA9IG5ldyBWZWM0KDAsIDAsIDAsIDApO1xyXG4gICAgcHJpdmF0ZSByZWFkb25seSBfYmxvb21UZXhTaXplID0gbmV3IFZlYzQoMCwgMCwgMCwgMCk7XHJcbiAgICBwcml2YXRlIHJlYWRvbmx5IF9ibG9vbVdpZHRoczogQXJyYXk8bnVtYmVyPiA9IFtdO1xyXG4gICAgcHJpdmF0ZSByZWFkb25seSBfYmxvb21IZWlnaHRzOiBBcnJheTxudW1iZXI+ID0gW107XHJcbiAgICBwcml2YXRlIHJlYWRvbmx5IF9ibG9vbVRleE5hbWVzOiBBcnJheTxzdHJpbmc+ID0gW107XHJcblxyXG4gICAgLy8gTWlwbWFwIEJsb29tXHJcbiAgICBwcml2YXRlIHJlYWRvbmx5IF9ibG9vbVVwU2FtcGxlVGV4RGVzY3M6IEFycmF5PFJlbmRlclRleHR1cmVEZXNjPiA9IFtdO1xyXG4gICAgcHJpdmF0ZSByZWFkb25seSBfYmxvb21Eb3duU2FtcGxlVGV4RGVzY3M6IEFycmF5PFJlbmRlclRleHR1cmVEZXNjPiA9IFtdO1xyXG4gICAgcHJpdmF0ZSBfcHJlZmlsdGVyVGV4RGVzYzogUmVuZGVyVGV4dHVyZURlc2MgPSB7IG5hbWU6ICcnLCB3aWR0aDogMCwgaGVpZ2h0OiAwIH07XHJcbiAgICBwcml2YXRlIF9vcmlnaW5hbENvbG9yRGVzYzogUmVuZGVyVGV4dHVyZURlc2MgPSB7IG5hbWU6ICcnLCB3aWR0aDogMCwgaGVpZ2h0OiAwIH07XHJcbn1cclxuXHJcbmV4cG9ydCBpbnRlcmZhY2UgVG9uZU1hcHBpbmdQYXNzQ29uZmlncyB7XHJcbiAgICBlbmFibGVUb25lTWFwcGluZzogYm9vbGVhbjtcclxuICAgIGVuYWJsZUNvbG9yR3JhZGluZzogYm9vbGVhbjtcclxufVxyXG5cclxuZXhwb3J0IGNsYXNzIEJ1aWx0aW5Ub25lTWFwcGluZ1Bhc3NCdWlsZGVyIGltcGxlbWVudHMgcmVuZGVyaW5nLlBpcGVsaW5lUGFzc0J1aWxkZXIge1xyXG4gICAgZ2V0Q29uZmlnT3JkZXIoKTogbnVtYmVyIHtcclxuICAgICAgICByZXR1cm4gMDtcclxuICAgIH1cclxuICAgIGdldFJlbmRlck9yZGVyKCk6IG51bWJlciB7XHJcbiAgICAgICAgcmV0dXJuIDMwMDtcclxuICAgIH1cclxuICAgIGNvbmZpZ0NhbWVyYShcclxuICAgICAgICBjYW1lcmE6IFJlYWRvbmx5PHJlbmRlcmVyLnNjZW5lLkNhbWVyYT4sXHJcbiAgICAgICAgcHBsQ29uZmlnczogUmVhZG9ubHk8UGlwZWxpbmVDb25maWdzPixcclxuICAgICAgICBjYW1lcmFDb25maWdzOiBDYW1lcmFDb25maWdzICYgVG9uZU1hcHBpbmdQYXNzQ29uZmlncyk6IHZvaWQge1xyXG4gICAgICAgIGNvbnN0IHNldHRpbmdzID0gY2FtZXJhQ29uZmlncy5zZXR0aW5ncztcclxuXHJcbiAgICAgICAgY2FtZXJhQ29uZmlncy5lbmFibGVDb2xvckdyYWRpbmdcclxuICAgICAgICAgICAgPSBzZXR0aW5ncy5jb2xvckdyYWRpbmcuZW5hYmxlZFxyXG4gICAgICAgICAgICAmJiAhIXNldHRpbmdzLmNvbG9yR3JhZGluZy5tYXRlcmlhbFxyXG4gICAgICAgICAgICAmJiAhIXNldHRpbmdzLmNvbG9yR3JhZGluZy5jb2xvckdyYWRpbmdNYXA7XHJcblxyXG4gICAgICAgIGNhbWVyYUNvbmZpZ3MuZW5hYmxlVG9uZU1hcHBpbmdcclxuICAgICAgICAgICAgPSBjYW1lcmFDb25maWdzLmVuYWJsZUhEUiAvLyBGcm9tIEhhbGYgdG8gUkdCQThcclxuICAgICAgICAgICAgfHwgY2FtZXJhQ29uZmlncy5lbmFibGVDb2xvckdyYWRpbmc7IC8vIENvbG9yIGdyYWRpbmdcclxuXHJcbiAgICAgICAgaWYgKGNhbWVyYUNvbmZpZ3MuZW5hYmxlVG9uZU1hcHBpbmcpIHtcclxuICAgICAgICAgICAgKytjYW1lcmFDb25maWdzLnJlbWFpbmluZ1Bhc3NlcztcclxuICAgICAgICB9XHJcbiAgICB9XHJcbiAgICB3aW5kb3dSZXNpemUoXHJcbiAgICAgICAgcHBsOiByZW5kZXJpbmcuQmFzaWNQaXBlbGluZSxcclxuICAgICAgICBwcGxDb25maWdzOiBSZWFkb25seTxQaXBlbGluZUNvbmZpZ3M+LFxyXG4gICAgICAgIGNhbWVyYUNvbmZpZ3M6IENhbWVyYUNvbmZpZ3MgJiBUb25lTWFwcGluZ1Bhc3NDb25maWdzKTogdm9pZCB7XHJcbiAgICAgICAgaWYgKGNhbWVyYUNvbmZpZ3MuZW5hYmxlQ29sb3JHcmFkaW5nKSB7XHJcbiAgICAgICAgICAgIGFzc2VydCghIWNhbWVyYUNvbmZpZ3Muc2V0dGluZ3MuY29sb3JHcmFkaW5nLm1hdGVyaWFsKTtcclxuICAgICAgICAgICAgY2FtZXJhQ29uZmlncy5zZXR0aW5ncy5jb2xvckdyYWRpbmcubWF0ZXJpYWwuc2V0UHJvcGVydHkoXHJcbiAgICAgICAgICAgICAgICAnY29sb3JHcmFkaW5nTWFwJyxcclxuICAgICAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3Muc2V0dGluZ3MuY29sb3JHcmFkaW5nLmNvbG9yR3JhZGluZ01hcCk7XHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG4gICAgc2V0dXAoXHJcbiAgICAgICAgcHBsOiByZW5kZXJpbmcuQmFzaWNQaXBlbGluZSxcclxuICAgICAgICBwcGxDb25maWdzOiBSZWFkb25seTxQaXBlbGluZUNvbmZpZ3M+LFxyXG4gICAgICAgIGNhbWVyYUNvbmZpZ3M6IENhbWVyYUNvbmZpZ3MgJiBUb25lTWFwcGluZ1Bhc3NDb25maWdzLFxyXG4gICAgICAgIGNhbWVyYTogcmVuZGVyZXIuc2NlbmUuQ2FtZXJhLFxyXG4gICAgICAgIGNvbnRleHQ6IFBpcGVsaW5lQ29udGV4dCxcclxuICAgICAgICBwcmV2UmVuZGVyUGFzcz86IHJlbmRlcmluZy5CYXNpY1JlbmRlclBhc3NCdWlsZGVyKVxyXG4gICAgICAgIDogcmVuZGVyaW5nLkJhc2ljUmVuZGVyUGFzc0J1aWxkZXIgfCB1bmRlZmluZWQge1xyXG4gICAgICAgIGlmICghY2FtZXJhQ29uZmlncy5lbmFibGVUb25lTWFwcGluZykge1xyXG4gICAgICAgICAgICByZXR1cm4gcHJldlJlbmRlclBhc3M7XHJcbiAgICAgICAgfVxyXG5cclxuICAgICAgICAtLWNhbWVyYUNvbmZpZ3MucmVtYWluaW5nUGFzc2VzO1xyXG4gICAgICAgIGFzc2VydChjYW1lcmFDb25maWdzLnJlbWFpbmluZ1Bhc3NlcyA+PSAwKTtcclxuICAgICAgICBpZiAoY2FtZXJhQ29uZmlncy5yZW1haW5pbmdQYXNzZXMgPT09IDApIHtcclxuICAgICAgICAgICAgcmV0dXJuIHRoaXMuX2FkZENvcHlBbmRUb25lbWFwUGFzcyhwcGwsIHBwbENvbmZpZ3MsIGNhbWVyYUNvbmZpZ3MsXHJcbiAgICAgICAgICAgICAgICBjYW1lcmFDb25maWdzLndpZHRoLCBjYW1lcmFDb25maWdzLmhlaWdodCxcclxuICAgICAgICAgICAgICAgIGNvbnRleHQuY29sb3JOYW1lLCBjYW1lcmFDb25maWdzLmNvbG9yTmFtZSk7XHJcbiAgICAgICAgfSBlbHNlIHtcclxuICAgICAgICAgICAgY29uc3QgaWQgPSBjYW1lcmFDb25maWdzLnJlbmRlcldpbmRvd0lkO1xyXG4gICAgICAgICAgICBjb25zdCBsZHJDb2xvclByZWZpeCA9IGNhbWVyYUNvbmZpZ3MuZW5hYmxlU2hhZGluZ1NjYWxlXHJcbiAgICAgICAgICAgICAgICA/IGBTY2FsZWRMZHJDb2xvcmBcclxuICAgICAgICAgICAgICAgIDogYExkckNvbG9yYDtcclxuXHJcbiAgICAgICAgICAgIGNvbnN0IGxkckNvbG9yTmFtZSA9IGdldFBpbmdQb25nUmVuZGVyVGFyZ2V0KGNvbnRleHQuY29sb3JOYW1lLCBsZHJDb2xvclByZWZpeCwgaWQpO1xyXG4gICAgICAgICAgICBjb25zdCByYWRpYW5jZU5hbWUgPSBjb250ZXh0LmNvbG9yTmFtZTtcclxuICAgICAgICAgICAgY29udGV4dC5jb2xvck5hbWUgPSBsZHJDb2xvck5hbWU7XHJcblxyXG4gICAgICAgICAgICByZXR1cm4gdGhpcy5fYWRkQ29weUFuZFRvbmVtYXBQYXNzKHBwbCwgcHBsQ29uZmlncywgY2FtZXJhQ29uZmlncyxcclxuICAgICAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3Mud2lkdGgsIGNhbWVyYUNvbmZpZ3MuaGVpZ2h0LFxyXG4gICAgICAgICAgICAgICAgcmFkaWFuY2VOYW1lLCBsZHJDb2xvck5hbWUpO1xyXG4gICAgICAgIH1cclxuICAgIH1cclxuICAgIHByaXZhdGUgX2FkZENvcHlBbmRUb25lbWFwUGFzcyhcclxuICAgICAgICBwcGw6IHJlbmRlcmluZy5CYXNpY1BpcGVsaW5lLFxyXG4gICAgICAgIHBwbENvbmZpZ3M6IFJlYWRvbmx5PFBpcGVsaW5lQ29uZmlncz4sXHJcbiAgICAgICAgY2FtZXJhQ29uZmlnczogQ2FtZXJhQ29uZmlncyAmIFRvbmVNYXBwaW5nUGFzc0NvbmZpZ3MsXHJcbiAgICAgICAgd2lkdGg6IG51bWJlcixcclxuICAgICAgICBoZWlnaHQ6IG51bWJlcixcclxuICAgICAgICByYWRpYW5jZU5hbWU6IHN0cmluZyxcclxuICAgICAgICBjb2xvck5hbWU6IHN0cmluZyxcclxuICAgICk6IHJlbmRlcmluZy5CYXNpY1JlbmRlclBhc3NCdWlsZGVyIHtcclxuICAgICAgICBsZXQgcGFzczogcmVuZGVyaW5nLkJhc2ljUmVuZGVyUGFzc0J1aWxkZXI7XHJcbiAgICAgICAgY29uc3Qgc2V0dGluZ3MgPSBjYW1lcmFDb25maWdzLnNldHRpbmdzO1xyXG4gICAgICAgIGlmIChjYW1lcmFDb25maWdzLmVuYWJsZUNvbG9yR3JhZGluZykge1xyXG4gICAgICAgICAgICBhc3NlcnQoISFzZXR0aW5ncy5jb2xvckdyYWRpbmcubWF0ZXJpYWwpO1xyXG4gICAgICAgICAgICBhc3NlcnQoISFzZXR0aW5ncy5jb2xvckdyYWRpbmcuY29sb3JHcmFkaW5nTWFwKTtcclxuXHJcbiAgICAgICAgICAgIGNvbnN0IGx1dFRleCA9IHNldHRpbmdzLmNvbG9yR3JhZGluZy5jb2xvckdyYWRpbmdNYXA7XHJcbiAgICAgICAgICAgIHRoaXMuX2NvbG9yR3JhZGluZ1RleFNpemUueCA9IGx1dFRleC53aWR0aDtcclxuICAgICAgICAgICAgdGhpcy5fY29sb3JHcmFkaW5nVGV4U2l6ZS55ID0gbHV0VGV4LmhlaWdodDtcclxuXHJcbiAgICAgICAgICAgIGNvbnN0IGlzU3F1YXJlTWFwID0gbHV0VGV4LndpZHRoID09PSBsdXRUZXguaGVpZ2h0O1xyXG4gICAgICAgICAgICBpZiAoaXNTcXVhcmVNYXApIHtcclxuICAgICAgICAgICAgICAgIHBhc3MgPSBwcGwuYWRkUmVuZGVyUGFzcyh3aWR0aCwgaGVpZ2h0LCAnY2MtY29sb3ItZ3JhZGluZy04eDgnKTtcclxuICAgICAgICAgICAgfSBlbHNlIHtcclxuICAgICAgICAgICAgICAgIHBhc3MgPSBwcGwuYWRkUmVuZGVyUGFzcyh3aWR0aCwgaGVpZ2h0LCAnY2MtY29sb3ItZ3JhZGluZy1ueDEnKTtcclxuICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICBwYXNzLmFkZFJlbmRlclRhcmdldChjb2xvck5hbWUsIExvYWRPcC5DTEVBUiwgU3RvcmVPcC5TVE9SRSwgc0NsZWFyQ29sb3JUcmFuc3BhcmVudEJsYWNrKTtcclxuICAgICAgICAgICAgcGFzcy5hZGRUZXh0dXJlKHJhZGlhbmNlTmFtZSwgJ3NjZW5lQ29sb3JNYXAnKTtcclxuICAgICAgICAgICAgcGFzcy5zZXRWZWMyKCdsdXRUZXh0dXJlU2l6ZScsIHRoaXMuX2NvbG9yR3JhZGluZ1RleFNpemUpO1xyXG4gICAgICAgICAgICBwYXNzLnNldEZsb2F0KCdjb250cmlidXRlJywgc2V0dGluZ3MuY29sb3JHcmFkaW5nLmNvbnRyaWJ1dGUpO1xyXG4gICAgICAgICAgICBwYXNzLmFkZFF1ZXVlKHJlbmRlcmluZy5RdWV1ZUhpbnQuT1BBUVVFKVxyXG4gICAgICAgICAgICAgICAgLmFkZEZ1bGxzY3JlZW5RdWFkKHNldHRpbmdzLmNvbG9yR3JhZGluZy5tYXRlcmlhbCwgaXNTcXVhcmVNYXAgPyAxIDogMCk7XHJcbiAgICAgICAgfSBlbHNlIHtcclxuICAgICAgICAgICAgcGFzcyA9IHBwbC5hZGRSZW5kZXJQYXNzKHdpZHRoLCBoZWlnaHQsICdjYy10b25lLW1hcHBpbmcnKTtcclxuICAgICAgICAgICAgcGFzcy5hZGRSZW5kZXJUYXJnZXQoY29sb3JOYW1lLCBMb2FkT3AuQ0xFQVIsIFN0b3JlT3AuU1RPUkUsIHNDbGVhckNvbG9yVHJhbnNwYXJlbnRCbGFjayk7XHJcbiAgICAgICAgICAgIHBhc3MuYWRkVGV4dHVyZShyYWRpYW5jZU5hbWUsICdpbnB1dFRleHR1cmUnKTtcclxuICAgICAgICAgICAgaWYgKHNldHRpbmdzLnRvbmVNYXBwaW5nLm1hdGVyaWFsKSB7XHJcbiAgICAgICAgICAgICAgICBwYXNzLmFkZFF1ZXVlKHJlbmRlcmluZy5RdWV1ZUhpbnQuT1BBUVVFKVxyXG4gICAgICAgICAgICAgICAgICAgIC5hZGRGdWxsc2NyZWVuUXVhZChzZXR0aW5ncy50b25lTWFwcGluZy5tYXRlcmlhbCwgMCk7XHJcbiAgICAgICAgICAgIH0gZWxzZSB7XHJcbiAgICAgICAgICAgICAgICBhc3NlcnQoISFjYW1lcmFDb25maWdzLmNvcHlBbmRUb25lbWFwTWF0ZXJpYWwpO1xyXG4gICAgICAgICAgICAgICAgcGFzcy5hZGRRdWV1ZShyZW5kZXJpbmcuUXVldWVIaW50Lk9QQVFVRSlcclxuICAgICAgICAgICAgICAgICAgICAuYWRkRnVsbHNjcmVlblF1YWQoY2FtZXJhQ29uZmlncy5jb3B5QW5kVG9uZW1hcE1hdGVyaWFsLCAwKTtcclxuICAgICAgICAgICAgfVxyXG4gICAgICAgIH1cclxuICAgICAgICByZXR1cm4gcGFzcztcclxuICAgIH1cclxuICAgIHByaXZhdGUgcmVhZG9ubHkgX2NvbG9yR3JhZGluZ1RleFNpemUgPSBuZXcgVmVjMigwLCAwKTtcclxufVxyXG5cclxuZXhwb3J0IGludGVyZmFjZSBGWEFBUGFzc0NvbmZpZ3Mge1xyXG4gICAgZW5hYmxlRlhBQTogYm9vbGVhbjtcclxufVxyXG5cclxuZXhwb3J0IGNsYXNzIEJ1aWx0aW5GWEFBUGFzc0J1aWxkZXIgaW1wbGVtZW50cyByZW5kZXJpbmcuUGlwZWxpbmVQYXNzQnVpbGRlciB7XHJcbiAgICBnZXRDb25maWdPcmRlcigpOiBudW1iZXIge1xyXG4gICAgICAgIHJldHVybiAwO1xyXG4gICAgfVxyXG4gICAgZ2V0UmVuZGVyT3JkZXIoKTogbnVtYmVyIHtcclxuICAgICAgICByZXR1cm4gNDAwO1xyXG4gICAgfVxyXG4gICAgY29uZmlnQ2FtZXJhKFxyXG4gICAgICAgIGNhbWVyYTogUmVhZG9ubHk8cmVuZGVyZXIuc2NlbmUuQ2FtZXJhPixcclxuICAgICAgICBwcGxDb25maWdzOiBSZWFkb25seTxQaXBlbGluZUNvbmZpZ3M+LFxyXG4gICAgICAgIGNhbWVyYUNvbmZpZ3M6IENhbWVyYUNvbmZpZ3MgJiBGWEFBUGFzc0NvbmZpZ3MpOiB2b2lkIHtcclxuICAgICAgICBjYW1lcmFDb25maWdzLmVuYWJsZUZYQUFcclxuICAgICAgICAgICAgPSBjYW1lcmFDb25maWdzLnNldHRpbmdzLmZ4YWEuZW5hYmxlZFxyXG4gICAgICAgICAgICAmJiAhIWNhbWVyYUNvbmZpZ3Muc2V0dGluZ3MuZnhhYS5tYXRlcmlhbDtcclxuICAgICAgICBpZiAoY2FtZXJhQ29uZmlncy5lbmFibGVGWEFBKSB7XHJcbiAgICAgICAgICAgICsrY2FtZXJhQ29uZmlncy5yZW1haW5pbmdQYXNzZXM7XHJcbiAgICAgICAgfVxyXG4gICAgfVxyXG4gICAgc2V0dXAoXHJcbiAgICAgICAgcHBsOiByZW5kZXJpbmcuQmFzaWNQaXBlbGluZSxcclxuICAgICAgICBwcGxDb25maWdzOiBSZWFkb25seTxQaXBlbGluZUNvbmZpZ3M+LFxyXG4gICAgICAgIGNhbWVyYUNvbmZpZ3M6IENhbWVyYUNvbmZpZ3MgJiBGWEFBUGFzc0NvbmZpZ3MsXHJcbiAgICAgICAgY2FtZXJhOiByZW5kZXJlci5zY2VuZS5DYW1lcmEsXHJcbiAgICAgICAgY29udGV4dDogUGlwZWxpbmVDb250ZXh0LFxyXG4gICAgICAgIHByZXZSZW5kZXJQYXNzPzogcmVuZGVyaW5nLkJhc2ljUmVuZGVyUGFzc0J1aWxkZXIpXHJcbiAgICAgICAgOiByZW5kZXJpbmcuQmFzaWNSZW5kZXJQYXNzQnVpbGRlciB8IHVuZGVmaW5lZCB7XHJcbiAgICAgICAgaWYgKCFjYW1lcmFDb25maWdzLmVuYWJsZUZYQUEpIHtcclxuICAgICAgICAgICAgcmV0dXJuIHByZXZSZW5kZXJQYXNzO1xyXG4gICAgICAgIH1cclxuICAgICAgICAtLWNhbWVyYUNvbmZpZ3MucmVtYWluaW5nUGFzc2VzO1xyXG4gICAgICAgIGFzc2VydChjYW1lcmFDb25maWdzLnJlbWFpbmluZ1Bhc3NlcyA+PSAwKTtcclxuXHJcbiAgICAgICAgY29uc3QgaWQgPSBjYW1lcmFDb25maWdzLnJlbmRlcldpbmRvd0lkO1xyXG4gICAgICAgIGNvbnN0IGxkckNvbG9yUHJlZml4ID0gY2FtZXJhQ29uZmlncy5lbmFibGVTaGFkaW5nU2NhbGVcclxuICAgICAgICAgICAgPyBgU2NhbGVkTGRyQ29sb3JgXHJcbiAgICAgICAgICAgIDogYExkckNvbG9yYDtcclxuICAgICAgICBjb25zdCBsZHJDb2xvck5hbWUgPSBnZXRQaW5nUG9uZ1JlbmRlclRhcmdldChjb250ZXh0LmNvbG9yTmFtZSwgbGRyQ29sb3JQcmVmaXgsIGlkKTtcclxuXHJcbiAgICAgICAgYXNzZXJ0KCEhY2FtZXJhQ29uZmlncy5zZXR0aW5ncy5meGFhLm1hdGVyaWFsKTtcclxuICAgICAgICBpZiAoY2FtZXJhQ29uZmlncy5yZW1haW5pbmdQYXNzZXMgPT09IDApIHtcclxuICAgICAgICAgICAgaWYgKGNhbWVyYUNvbmZpZ3MuZW5hYmxlU2hhZGluZ1NjYWxlKSB7XHJcbiAgICAgICAgICAgICAgICB0aGlzLl9hZGRGeGFhUGFzcyhwcGwsIHBwbENvbmZpZ3MsXHJcbiAgICAgICAgICAgICAgICAgICAgY2FtZXJhQ29uZmlncy5zZXR0aW5ncy5meGFhLm1hdGVyaWFsLFxyXG4gICAgICAgICAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3Mud2lkdGgsXHJcbiAgICAgICAgICAgICAgICAgICAgY2FtZXJhQ29uZmlncy5oZWlnaHQsXHJcbiAgICAgICAgICAgICAgICAgICAgY29udGV4dC5jb2xvck5hbWUsXHJcbiAgICAgICAgICAgICAgICAgICAgbGRyQ29sb3JOYW1lKTtcclxuICAgICAgICAgICAgICAgIHJldHVybiBhZGRDb3B5VG9TY3JlZW5QYXNzKHBwbCwgcHBsQ29uZmlncywgY2FtZXJhQ29uZmlncywgbGRyQ29sb3JOYW1lKTtcclxuICAgICAgICAgICAgfSBlbHNlIHtcclxuICAgICAgICAgICAgICAgIGFzc2VydChjYW1lcmFDb25maWdzLndpZHRoID09PSBjYW1lcmFDb25maWdzLm5hdGl2ZVdpZHRoKTtcclxuICAgICAgICAgICAgICAgIGFzc2VydChjYW1lcmFDb25maWdzLmhlaWdodCA9PT0gY2FtZXJhQ29uZmlncy5uYXRpdmVIZWlnaHQpO1xyXG4gICAgICAgICAgICAgICAgcmV0dXJuIHRoaXMuX2FkZEZ4YWFQYXNzKHBwbCwgcHBsQ29uZmlncyxcclxuICAgICAgICAgICAgICAgICAgICBjYW1lcmFDb25maWdzLnNldHRpbmdzLmZ4YWEubWF0ZXJpYWwsXHJcbiAgICAgICAgICAgICAgICAgICAgY2FtZXJhQ29uZmlncy53aWR0aCxcclxuICAgICAgICAgICAgICAgICAgICBjYW1lcmFDb25maWdzLmhlaWdodCxcclxuICAgICAgICAgICAgICAgICAgICBjb250ZXh0LmNvbG9yTmFtZSxcclxuICAgICAgICAgICAgICAgICAgICBjYW1lcmFDb25maWdzLmNvbG9yTmFtZSk7XHJcbiAgICAgICAgICAgIH1cclxuICAgICAgICB9IGVsc2Uge1xyXG4gICAgICAgICAgICBjb25zdCBpbnB1dENvbG9yTmFtZSA9IGNvbnRleHQuY29sb3JOYW1lO1xyXG4gICAgICAgICAgICBjb250ZXh0LmNvbG9yTmFtZSA9IGxkckNvbG9yTmFtZTtcclxuICAgICAgICAgICAgY29uc3QgbGFzdFBhc3MgPSB0aGlzLl9hZGRGeGFhUGFzcyhwcGwsIHBwbENvbmZpZ3MsXHJcbiAgICAgICAgICAgICAgICBjYW1lcmFDb25maWdzLnNldHRpbmdzLmZ4YWEubWF0ZXJpYWwsXHJcbiAgICAgICAgICAgICAgICBjYW1lcmFDb25maWdzLndpZHRoLFxyXG4gICAgICAgICAgICAgICAgY2FtZXJhQ29uZmlncy5oZWlnaHQsXHJcbiAgICAgICAgICAgICAgICBpbnB1dENvbG9yTmFtZSxcclxuICAgICAgICAgICAgICAgIGxkckNvbG9yTmFtZSk7XHJcbiAgICAgICAgICAgIHJldHVybiBsYXN0UGFzcztcclxuICAgICAgICB9XHJcbiAgICB9XHJcbiAgICBwcml2YXRlIF9hZGRGeGFhUGFzcyhcclxuICAgICAgICBwcGw6IHJlbmRlcmluZy5CYXNpY1BpcGVsaW5lLFxyXG4gICAgICAgIHBwbENvbmZpZ3M6IFJlYWRvbmx5PFBpcGVsaW5lQ29uZmlncz4sXHJcbiAgICAgICAgZnhhYU1hdGVyaWFsOiBNYXRlcmlhbCxcclxuICAgICAgICB3aWR0aDogbnVtYmVyLFxyXG4gICAgICAgIGhlaWdodDogbnVtYmVyLFxyXG4gICAgICAgIGxkckNvbG9yTmFtZTogc3RyaW5nLFxyXG4gICAgICAgIGNvbG9yTmFtZTogc3RyaW5nLFxyXG4gICAgKTogcmVuZGVyaW5nLkJhc2ljUmVuZGVyUGFzc0J1aWxkZXIge1xyXG4gICAgICAgIHRoaXMuX2Z4YWFQYXJhbXMueCA9IHdpZHRoO1xyXG4gICAgICAgIHRoaXMuX2Z4YWFQYXJhbXMueSA9IGhlaWdodDtcclxuICAgICAgICB0aGlzLl9meGFhUGFyYW1zLnogPSAxIC8gd2lkdGg7XHJcbiAgICAgICAgdGhpcy5fZnhhYVBhcmFtcy53ID0gMSAvIGhlaWdodDtcclxuXHJcbiAgICAgICAgY29uc3QgcGFzcyA9IHBwbC5hZGRSZW5kZXJQYXNzKHdpZHRoLCBoZWlnaHQsICdjYy1meGFhJyk7XHJcbiAgICAgICAgcGFzcy5hZGRSZW5kZXJUYXJnZXQoY29sb3JOYW1lLCBMb2FkT3AuQ0xFQVIsIFN0b3JlT3AuU1RPUkUsIHNDbGVhckNvbG9yVHJhbnNwYXJlbnRCbGFjayk7XHJcbiAgICAgICAgcGFzcy5hZGRUZXh0dXJlKGxkckNvbG9yTmFtZSwgJ3NjZW5lQ29sb3JNYXAnKTtcclxuICAgICAgICBwYXNzLnNldFZlYzQoJ3RleFNpemUnLCB0aGlzLl9meGFhUGFyYW1zKTtcclxuICAgICAgICBwYXNzLmFkZFF1ZXVlKHJlbmRlcmluZy5RdWV1ZUhpbnQuT1BBUVVFKVxyXG4gICAgICAgICAgICAuYWRkRnVsbHNjcmVlblF1YWQoZnhhYU1hdGVyaWFsLCAwKTtcclxuICAgICAgICByZXR1cm4gcGFzcztcclxuICAgIH1cclxuICAgIC8vIEZYQUFcclxuICAgIHByaXZhdGUgcmVhZG9ubHkgX2Z4YWFQYXJhbXMgPSBuZXcgVmVjNCgwLCAwLCAwLCAwKTtcclxufVxyXG5cclxuZXhwb3J0IGludGVyZmFjZSBGU1JQYXNzQ29uZmlncyB7XHJcbiAgICBlbmFibGVGU1I6IGJvb2xlYW47XHJcbn1cclxuXHJcbmV4cG9ydCBjbGFzcyBCdWlsdGluRnNyUGFzc0J1aWxkZXIgaW1wbGVtZW50cyByZW5kZXJpbmcuUGlwZWxpbmVQYXNzQnVpbGRlciB7XHJcbiAgICBnZXRDb25maWdPcmRlcigpOiBudW1iZXIge1xyXG4gICAgICAgIHJldHVybiAwO1xyXG4gICAgfVxyXG4gICAgZ2V0UmVuZGVyT3JkZXIoKTogbnVtYmVyIHtcclxuICAgICAgICByZXR1cm4gNTAwO1xyXG4gICAgfVxyXG4gICAgY29uZmlnQ2FtZXJhKFxyXG4gICAgICAgIGNhbWVyYTogUmVhZG9ubHk8cmVuZGVyZXIuc2NlbmUuQ2FtZXJhPixcclxuICAgICAgICBwcGxDb25maWdzOiBSZWFkb25seTxQaXBlbGluZUNvbmZpZ3M+LFxyXG4gICAgICAgIGNhbWVyYUNvbmZpZ3M6IENhbWVyYUNvbmZpZ3MgJiBGU1JQYXNzQ29uZmlncyk6IHZvaWQge1xyXG4gICAgICAgIC8vIEZTUiAoRGVwZW5kIG9uIFNoYWRpbmcgc2NhbGUpXHJcbiAgICAgICAgY2FtZXJhQ29uZmlncy5lbmFibGVGU1IgPSBjYW1lcmFDb25maWdzLnNldHRpbmdzLmZzci5lbmFibGVkXHJcbiAgICAgICAgICAgICYmICEhY2FtZXJhQ29uZmlncy5zZXR0aW5ncy5mc3IubWF0ZXJpYWxcclxuICAgICAgICAgICAgJiYgY2FtZXJhQ29uZmlncy5lbmFibGVTaGFkaW5nU2NhbGVcclxuICAgICAgICAgICAgJiYgY2FtZXJhQ29uZmlncy5zaGFkaW5nU2NhbGUgPCAxLjA7XHJcblxyXG4gICAgICAgIGlmIChjYW1lcmFDb25maWdzLmVuYWJsZUZTUikge1xyXG4gICAgICAgICAgICArK2NhbWVyYUNvbmZpZ3MucmVtYWluaW5nUGFzc2VzO1xyXG4gICAgICAgIH1cclxuICAgIH1cclxuICAgIHNldHVwKFxyXG4gICAgICAgIHBwbDogcmVuZGVyaW5nLkJhc2ljUGlwZWxpbmUsXHJcbiAgICAgICAgcHBsQ29uZmlnczogUmVhZG9ubHk8UGlwZWxpbmVDb25maWdzPixcclxuICAgICAgICBjYW1lcmFDb25maWdzOiBDYW1lcmFDb25maWdzICYgRlNSUGFzc0NvbmZpZ3MsXHJcbiAgICAgICAgY2FtZXJhOiByZW5kZXJlci5zY2VuZS5DYW1lcmEsXHJcbiAgICAgICAgY29udGV4dDogUGlwZWxpbmVDb250ZXh0LFxyXG4gICAgICAgIHByZXZSZW5kZXJQYXNzPzogcmVuZGVyaW5nLkJhc2ljUmVuZGVyUGFzc0J1aWxkZXIpXHJcbiAgICAgICAgOiByZW5kZXJpbmcuQmFzaWNSZW5kZXJQYXNzQnVpbGRlciB8IHVuZGVmaW5lZCB7XHJcbiAgICAgICAgaWYgKCFjYW1lcmFDb25maWdzLmVuYWJsZUZTUikge1xyXG4gICAgICAgICAgICByZXR1cm4gcHJldlJlbmRlclBhc3M7XHJcbiAgICAgICAgfVxyXG4gICAgICAgIC0tY2FtZXJhQ29uZmlncy5yZW1haW5pbmdQYXNzZXM7XHJcblxyXG4gICAgICAgIGNvbnN0IGlucHV0Q29sb3JOYW1lID0gY29udGV4dC5jb2xvck5hbWU7XHJcbiAgICAgICAgY29uc3Qgb3V0cHV0Q29sb3JOYW1lXHJcbiAgICAgICAgICAgID0gY2FtZXJhQ29uZmlncy5yZW1haW5pbmdQYXNzZXMgPT09IDBcclxuICAgICAgICAgICAgICAgID8gY2FtZXJhQ29uZmlncy5jb2xvck5hbWVcclxuICAgICAgICAgICAgICAgIDogZ2V0UGluZ1BvbmdSZW5kZXJUYXJnZXQoY29udGV4dC5jb2xvck5hbWUsICdVaUNvbG9yJywgY2FtZXJhQ29uZmlncy5yZW5kZXJXaW5kb3dJZCk7XHJcbiAgICAgICAgY29udGV4dC5jb2xvck5hbWUgPSBvdXRwdXRDb2xvck5hbWU7XHJcblxyXG4gICAgICAgIGFzc2VydCghIWNhbWVyYUNvbmZpZ3Muc2V0dGluZ3MuZnNyLm1hdGVyaWFsKTtcclxuICAgICAgICByZXR1cm4gdGhpcy5fYWRkRnNyUGFzcyhwcGwsIHBwbENvbmZpZ3MsIGNhbWVyYUNvbmZpZ3MsXHJcbiAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3Muc2V0dGluZ3MsXHJcbiAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3Muc2V0dGluZ3MuZnNyLm1hdGVyaWFsLFxyXG4gICAgICAgICAgICBjYW1lcmFDb25maWdzLnJlbmRlcldpbmRvd0lkLFxyXG4gICAgICAgICAgICBjYW1lcmFDb25maWdzLndpZHRoLFxyXG4gICAgICAgICAgICBjYW1lcmFDb25maWdzLmhlaWdodCxcclxuICAgICAgICAgICAgaW5wdXRDb2xvck5hbWUsXHJcbiAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3MubmF0aXZlV2lkdGgsXHJcbiAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3MubmF0aXZlSGVpZ2h0LFxyXG4gICAgICAgICAgICBvdXRwdXRDb2xvck5hbWUpO1xyXG4gICAgfVxyXG4gICAgcHJpdmF0ZSBfYWRkRnNyUGFzcyhcclxuICAgICAgICBwcGw6IHJlbmRlcmluZy5CYXNpY1BpcGVsaW5lLFxyXG4gICAgICAgIHBwbENvbmZpZ3M6IFJlYWRvbmx5PFBpcGVsaW5lQ29uZmlncz4sXHJcbiAgICAgICAgY2FtZXJhQ29uZmlnczogQ2FtZXJhQ29uZmlncyAmIEZTUlBhc3NDb25maWdzLFxyXG4gICAgICAgIHNldHRpbmdzOiBQaXBlbGluZVNldHRpbmdzLFxyXG4gICAgICAgIGZzck1hdGVyaWFsOiBNYXRlcmlhbCxcclxuICAgICAgICBpZDogbnVtYmVyLFxyXG4gICAgICAgIHdpZHRoOiBudW1iZXIsXHJcbiAgICAgICAgaGVpZ2h0OiBudW1iZXIsXHJcbiAgICAgICAgaW5wdXRDb2xvck5hbWU6IHN0cmluZyxcclxuICAgICAgICBuYXRpdmVXaWR0aDogbnVtYmVyLFxyXG4gICAgICAgIG5hdGl2ZUhlaWdodDogbnVtYmVyLFxyXG4gICAgICAgIG91dHB1dENvbG9yTmFtZTogc3RyaW5nLFxyXG4gICAgKTogcmVuZGVyaW5nLkJhc2ljUmVuZGVyUGFzc0J1aWxkZXIge1xyXG4gICAgICAgIHRoaXMuX2ZzclRleFNpemUueCA9IHdpZHRoO1xyXG4gICAgICAgIHRoaXMuX2ZzclRleFNpemUueSA9IGhlaWdodDtcclxuICAgICAgICB0aGlzLl9mc3JUZXhTaXplLnogPSBuYXRpdmVXaWR0aDtcclxuICAgICAgICB0aGlzLl9mc3JUZXhTaXplLncgPSBuYXRpdmVIZWlnaHQ7XHJcbiAgICAgICAgdGhpcy5fZnNyUGFyYW1zLnggPSBjbGFtcCgxLjAgLSBzZXR0aW5ncy5mc3Iuc2hhcnBuZXNzLCAwLjAyLCAwLjk4KTtcclxuXHJcbiAgICAgICAgY29uc3QgdWlDb2xvclByZWZpeCA9ICdVaUNvbG9yJztcclxuXHJcbiAgICAgICAgY29uc3QgZnNyQ29sb3JOYW1lID0gZ2V0UGluZ1BvbmdSZW5kZXJUYXJnZXQob3V0cHV0Q29sb3JOYW1lLCB1aUNvbG9yUHJlZml4LCBpZCk7XHJcblxyXG4gICAgICAgIGNvbnN0IGVhc3VQYXNzID0gcHBsLmFkZFJlbmRlclBhc3MobmF0aXZlV2lkdGgsIG5hdGl2ZUhlaWdodCwgJ2NjLWZzci1lYXN1Jyk7XHJcbiAgICAgICAgZWFzdVBhc3MuYWRkUmVuZGVyVGFyZ2V0KGZzckNvbG9yTmFtZSwgTG9hZE9wLkNMRUFSLCBTdG9yZU9wLlNUT1JFLCBzQ2xlYXJDb2xvclRyYW5zcGFyZW50QmxhY2spO1xyXG4gICAgICAgIGVhc3VQYXNzLmFkZFRleHR1cmUoaW5wdXRDb2xvck5hbWUsICdvdXRwdXRSZXN1bHRNYXAnKTtcclxuICAgICAgICBlYXN1UGFzcy5zZXRWZWM0KCdmc3JUZXhTaXplJywgdGhpcy5fZnNyVGV4U2l6ZSk7XHJcbiAgICAgICAgZWFzdVBhc3NcclxuICAgICAgICAgICAgLmFkZFF1ZXVlKHJlbmRlcmluZy5RdWV1ZUhpbnQuT1BBUVVFKVxyXG4gICAgICAgICAgICAuYWRkRnVsbHNjcmVlblF1YWQoZnNyTWF0ZXJpYWwsIDApO1xyXG5cclxuICAgICAgICBjb25zdCByY2FzUGFzcyA9IHBwbC5hZGRSZW5kZXJQYXNzKG5hdGl2ZVdpZHRoLCBuYXRpdmVIZWlnaHQsICdjYy1mc3ItcmNhcycpO1xyXG4gICAgICAgIHJjYXNQYXNzLmFkZFJlbmRlclRhcmdldChvdXRwdXRDb2xvck5hbWUsIExvYWRPcC5DTEVBUiwgU3RvcmVPcC5TVE9SRSwgc0NsZWFyQ29sb3JUcmFuc3BhcmVudEJsYWNrKTtcclxuICAgICAgICByY2FzUGFzcy5hZGRUZXh0dXJlKGZzckNvbG9yTmFtZSwgJ291dHB1dFJlc3VsdE1hcCcpO1xyXG4gICAgICAgIHJjYXNQYXNzLnNldFZlYzQoJ2ZzclRleFNpemUnLCB0aGlzLl9mc3JUZXhTaXplKTtcclxuICAgICAgICByY2FzUGFzcy5zZXRWZWM0KCdmc3JQYXJhbXMnLCB0aGlzLl9mc3JQYXJhbXMpO1xyXG4gICAgICAgIHJjYXNQYXNzXHJcbiAgICAgICAgICAgIC5hZGRRdWV1ZShyZW5kZXJpbmcuUXVldWVIaW50Lk9QQVFVRSlcclxuICAgICAgICAgICAgLmFkZEZ1bGxzY3JlZW5RdWFkKGZzck1hdGVyaWFsLCAxKTtcclxuXHJcbiAgICAgICAgcmV0dXJuIHJjYXNQYXNzO1xyXG4gICAgfVxyXG4gICAgLy8gRlNSXHJcbiAgICBwcml2YXRlIHJlYWRvbmx5IF9mc3JQYXJhbXMgPSBuZXcgVmVjNCgwLCAwLCAwLCAwKTtcclxuICAgIHByaXZhdGUgcmVhZG9ubHkgX2ZzclRleFNpemUgPSBuZXcgVmVjNCgwLCAwLCAwLCAwKTtcclxufVxyXG5cclxuZXhwb3J0IGNsYXNzIEJ1aWx0aW5VaVBhc3NCdWlsZGVyIGltcGxlbWVudHMgcmVuZGVyaW5nLlBpcGVsaW5lUGFzc0J1aWxkZXIge1xyXG4gICAgZ2V0Q29uZmlnT3JkZXIoKTogbnVtYmVyIHtcclxuICAgICAgICByZXR1cm4gMDtcclxuICAgIH1cclxuICAgIGdldFJlbmRlck9yZGVyKCk6IG51bWJlciB7XHJcbiAgICAgICAgcmV0dXJuIDEwMDA7XHJcbiAgICB9XHJcbiAgICBzZXR1cChcclxuICAgICAgICBwcGw6IHJlbmRlcmluZy5CYXNpY1BpcGVsaW5lLFxyXG4gICAgICAgIHBwbENvbmZpZ3M6IFJlYWRvbmx5PFBpcGVsaW5lQ29uZmlncz4sXHJcbiAgICAgICAgY2FtZXJhQ29uZmlnczogQ2FtZXJhQ29uZmlncyAmIEZTUlBhc3NDb25maWdzLFxyXG4gICAgICAgIGNhbWVyYTogcmVuZGVyZXIuc2NlbmUuQ2FtZXJhLFxyXG4gICAgICAgIGNvbnRleHQ6IFBpcGVsaW5lQ29udGV4dCxcclxuICAgICAgICBwcmV2UmVuZGVyUGFzcz86IHJlbmRlcmluZy5CYXNpY1JlbmRlclBhc3NCdWlsZGVyKVxyXG4gICAgICAgIDogcmVuZGVyaW5nLkJhc2ljUmVuZGVyUGFzc0J1aWxkZXIgfCB1bmRlZmluZWQge1xyXG4gICAgICAgIGFzc2VydCghIXByZXZSZW5kZXJQYXNzKTtcclxuXHJcbiAgICAgICAgbGV0IGZsYWdzID0gcmVuZGVyaW5nLlNjZW5lRmxhZ3MuVUk7XHJcbiAgICAgICAgaWYgKGNhbWVyYUNvbmZpZ3MuZW5hYmxlUHJvZmlsZXIpIHtcclxuICAgICAgICAgICAgZmxhZ3MgfD0gcmVuZGVyaW5nLlNjZW5lRmxhZ3MuUFJPRklMRVI7XHJcbiAgICAgICAgICAgIHByZXZSZW5kZXJQYXNzLnNob3dTdGF0aXN0aWNzID0gdHJ1ZTtcclxuICAgICAgICB9XHJcbiAgICAgICAgcHJldlJlbmRlclBhc3NcclxuICAgICAgICAgICAgLmFkZFF1ZXVlKHJlbmRlcmluZy5RdWV1ZUhpbnQuQkxFTkQsICdkZWZhdWx0JywgJ2RlZmF1bHQnKVxyXG4gICAgICAgICAgICAuYWRkU2NlbmUoY2FtZXJhLCBmbGFncyk7XHJcblxyXG4gICAgICAgIHJldHVybiBwcmV2UmVuZGVyUGFzcztcclxuICAgIH1cclxufVxyXG5cclxuaWYgKHJlbmRlcmluZykge1xyXG5cclxuICAgIGNvbnN0IHsgUXVldWVIaW50LCBTY2VuZUZsYWdzIH0gPSByZW5kZXJpbmc7XHJcblxyXG4gICAgY2xhc3MgQnVpbHRpblBpcGVsaW5lQnVpbGRlciBpbXBsZW1lbnRzIHJlbmRlcmluZy5QaXBlbGluZUJ1aWxkZXIge1xyXG4gICAgICAgIHByaXZhdGUgcmVhZG9ubHkgX3BpcGVsaW5lRXZlbnQ6IFBpcGVsaW5lRXZlbnRQcm9jZXNzb3IgPSBjY2xlZ2FjeS5kaXJlY3Rvci5yb290LnBpcGVsaW5lRXZlbnQgYXMgUGlwZWxpbmVFdmVudFByb2Nlc3NvcjtcclxuICAgICAgICBwcml2YXRlIHJlYWRvbmx5IF9mb3J3YXJkUGFzcyA9IG5ldyBCdWlsdGluRm9yd2FyZFBhc3NCdWlsZGVyKCk7XHJcbiAgICAgICAgcHJpdmF0ZSByZWFkb25seSBfYmxvb21QYXNzID0gbmV3IEJ1aWx0aW5CbG9vbVBhc3NCdWlsZGVyKCk7XHJcbiAgICAgICAgcHJpdmF0ZSByZWFkb25seSBfdG9uZU1hcHBpbmdQYXNzID0gbmV3IEJ1aWx0aW5Ub25lTWFwcGluZ1Bhc3NCdWlsZGVyKCk7XHJcbiAgICAgICAgcHJpdmF0ZSByZWFkb25seSBfZnhhYVBhc3MgPSBuZXcgQnVpbHRpbkZYQUFQYXNzQnVpbGRlcigpO1xyXG4gICAgICAgIHByaXZhdGUgcmVhZG9ubHkgX2ZzclBhc3MgPSBuZXcgQnVpbHRpbkZzclBhc3NCdWlsZGVyKCk7XHJcbiAgICAgICAgcHJpdmF0ZSByZWFkb25seSBfdWlQYXNzID0gbmV3IEJ1aWx0aW5VaVBhc3NCdWlsZGVyKCk7XHJcbiAgICAgICAgLy8gSW50ZXJuYWwgY2FjaGVkIHJlc291cmNlc1xyXG4gICAgICAgIHByaXZhdGUgcmVhZG9ubHkgX2NsZWFyQ29sb3IgPSBuZXcgQ29sb3IoMCwgMCwgMCwgMSk7XHJcbiAgICAgICAgcHJpdmF0ZSByZWFkb25seSBfdmlld3BvcnQgPSBuZXcgVmlld3BvcnQoKTtcclxuICAgICAgICBwcml2YXRlIHJlYWRvbmx5IF9jb25maWdzID0gbmV3IFBpcGVsaW5lQ29uZmlncygpO1xyXG4gICAgICAgIHByaXZhdGUgcmVhZG9ubHkgX2NhbWVyYUNvbmZpZ3MgPSBuZXcgQ2FtZXJhQ29uZmlncygpO1xyXG4gICAgICAgIC8vIE1hdGVyaWFsc1xyXG4gICAgICAgIHByaXZhdGUgcmVhZG9ubHkgX2NvcHlBbmRUb25lbWFwTWF0ZXJpYWwgPSBuZXcgTWF0ZXJpYWwoKTtcclxuXHJcbiAgICAgICAgLy8gSW50ZXJuYWwgU3RhdGVzXHJcbiAgICAgICAgcHJpdmF0ZSBfaW5pdGlhbGl6ZWQgPSBmYWxzZTsgLy8gVE9ETyh6aG91emhlbmdsb25nKTogTWFrZSBkZWZhdWx0IGVmZmVjdCBhc3NldCBsb2FkaW5nIGVhcmxpZXIgYW5kIHJlbW92ZSB0aGlzIGZsYWdcclxuICAgICAgICBwcml2YXRlIF9wYXNzQnVpbGRlcnM6IHJlbmRlcmluZy5QaXBlbGluZVBhc3NCdWlsZGVyW10gPSBbXTtcclxuXHJcbiAgICAgICAgcHJpdmF0ZSBfc2V0dXBQaXBlbGluZVByZXZpZXcoXHJcbiAgICAgICAgICAgIGNhbWVyYTogcmVuZGVyZXIuc2NlbmUuQ2FtZXJhLFxyXG4gICAgICAgICAgICBjYW1lcmFDb25maWdzOiBDYW1lcmFDb25maWdzKSB7XHJcbiAgICAgICAgICAgIGNvbnN0IGlzRWRpdG9yVmlldzogYm9vbGVhblxyXG4gICAgICAgICAgICAgICAgPSBjYW1lcmEuY2FtZXJhVXNhZ2UgPT09IENhbWVyYVVzYWdlLlNDRU5FX1ZJRVdcclxuICAgICAgICAgICAgICAgIHx8IGNhbWVyYS5jYW1lcmFVc2FnZSA9PT0gQ2FtZXJhVXNhZ2UuUFJFVklFVztcclxuXHJcbiAgICAgICAgICAgIGlmIChpc0VkaXRvclZpZXcpIHtcclxuICAgICAgICAgICAgICAgIGNvbnN0IGVkaXRvclNldHRpbmdzID0gcmVuZGVyaW5nLmdldEVkaXRvclBpcGVsaW5lU2V0dGluZ3MoKSBhcyBQaXBlbGluZVNldHRpbmdzIHwgbnVsbDtcclxuICAgICAgICAgICAgICAgIGlmIChlZGl0b3JTZXR0aW5ncykge1xyXG4gICAgICAgICAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3Muc2V0dGluZ3MgPSBlZGl0b3JTZXR0aW5ncztcclxuICAgICAgICAgICAgICAgIH0gZWxzZSB7XHJcbiAgICAgICAgICAgICAgICAgICAgY2FtZXJhQ29uZmlncy5zZXR0aW5ncyA9IGRlZmF1bHRTZXR0aW5ncztcclxuICAgICAgICAgICAgICAgIH1cclxuICAgICAgICAgICAgfSBlbHNlIHtcclxuICAgICAgICAgICAgICAgIGlmIChjYW1lcmEucGlwZWxpbmVTZXR0aW5ncykge1xyXG4gICAgICAgICAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3Muc2V0dGluZ3MgPSBjYW1lcmEucGlwZWxpbmVTZXR0aW5ncyBhcyBQaXBlbGluZVNldHRpbmdzO1xyXG4gICAgICAgICAgICAgICAgfSBlbHNlIHtcclxuICAgICAgICAgICAgICAgICAgICBjYW1lcmFDb25maWdzLnNldHRpbmdzID0gZGVmYXVsdFNldHRpbmdzO1xyXG4gICAgICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICB9XHJcbiAgICAgICAgfVxyXG5cclxuICAgICAgICBwcml2YXRlIF9wcmVwYXJlUGlwZWxpbmVQYXNzZXMoY2FtZXJhQ29uZmlnczogQ2FtZXJhQ29uZmlncyk6IHZvaWQge1xyXG4gICAgICAgICAgICBjb25zdCBwYXNzQnVpbGRlcnMgPSB0aGlzLl9wYXNzQnVpbGRlcnM7XHJcbiAgICAgICAgICAgIHBhc3NCdWlsZGVycy5sZW5ndGggPSAwO1xyXG5cclxuICAgICAgICAgICAgY29uc3Qgc2V0dGluZ3MgPSBjYW1lcmFDb25maWdzLnNldHRpbmdzIGFzIFBpcGVsaW5lU2V0dGluZ3MyO1xyXG4gICAgICAgICAgICBpZiAoc2V0dGluZ3MuX3Bhc3Nlcykge1xyXG4gICAgICAgICAgICAgICAgZm9yIChjb25zdCBwYXNzIG9mIHNldHRpbmdzLl9wYXNzZXMpIHtcclxuICAgICAgICAgICAgICAgICAgICBwYXNzQnVpbGRlcnMucHVzaChwYXNzKTtcclxuICAgICAgICAgICAgICAgIH1cclxuICAgICAgICAgICAgICAgIGFzc2VydChwYXNzQnVpbGRlcnMubGVuZ3RoID09PSBzZXR0aW5ncy5fcGFzc2VzLmxlbmd0aCk7XHJcbiAgICAgICAgICAgIH1cclxuXHJcbiAgICAgICAgICAgIHBhc3NCdWlsZGVycy5wdXNoKHRoaXMuX2ZvcndhcmRQYXNzKTtcclxuXHJcbiAgICAgICAgICAgIGlmIChzZXR0aW5ncy5ibG9vbS5lbmFibGVkKSB7XHJcbiAgICAgICAgICAgICAgICBwYXNzQnVpbGRlcnMucHVzaCh0aGlzLl9ibG9vbVBhc3MpO1xyXG4gICAgICAgICAgICB9XHJcblxyXG4gICAgICAgICAgICBwYXNzQnVpbGRlcnMucHVzaCh0aGlzLl90b25lTWFwcGluZ1Bhc3MpO1xyXG5cclxuICAgICAgICAgICAgaWYgKHNldHRpbmdzLmZ4YWEuZW5hYmxlZCkge1xyXG4gICAgICAgICAgICAgICAgcGFzc0J1aWxkZXJzLnB1c2godGhpcy5fZnhhYVBhc3MpO1xyXG4gICAgICAgICAgICB9XHJcblxyXG4gICAgICAgICAgICBpZiAoc2V0dGluZ3MuZnNyLmVuYWJsZWQpIHtcclxuICAgICAgICAgICAgICAgIHBhc3NCdWlsZGVycy5wdXNoKHRoaXMuX2ZzclBhc3MpO1xyXG4gICAgICAgICAgICB9XHJcbiAgICAgICAgICAgIHBhc3NCdWlsZGVycy5wdXNoKHRoaXMuX3VpUGFzcyk7XHJcbiAgICAgICAgfVxyXG5cclxuICAgICAgICBwcml2YXRlIF9zZXR1cEJ1aWx0aW5DYW1lcmFDb25maWdzKFxyXG4gICAgICAgICAgICBwcGw6IHJlbmRlcmluZy5CYXNpY1BpcGVsaW5lLFxyXG4gICAgICAgICAgICBjYW1lcmE6IHJlbmRlcmVyLnNjZW5lLkNhbWVyYSxcclxuICAgICAgICAgICAgcGlwZWxpbmVDb25maWdzOiBQaXBlbGluZUNvbmZpZ3MsXHJcbiAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3M6IENhbWVyYUNvbmZpZ3NcclxuICAgICAgICApIHtcclxuICAgICAgICAgICAgY29uc3Qgd2luZG93ID0gY2FtZXJhLndpbmRvdztcclxuICAgICAgICAgICAgY29uc3QgaXNNYWluR2FtZVdpbmRvdzogYm9vbGVhbiA9IGNhbWVyYS5jYW1lcmFVc2FnZSA9PT0gQ2FtZXJhVXNhZ2UuR0FNRSAmJiAhIXdpbmRvdy5zd2FwY2hhaW47XHJcbiAgICAgICAgICAgIGNvbnN0IGlzR2FtZVZpZXcgPSBpc01haW5HYW1lV2luZG93IHx8IGNhbWVyYS5jYW1lcmFVc2FnZSA9PT0gQ2FtZXJhVXNhZ2UuR0FNRV9WSUVXO1xyXG5cclxuICAgICAgICAgICAgLy8gV2luZG93XHJcbiAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3MuaXNNYWluR2FtZVdpbmRvdyA9IGlzTWFpbkdhbWVXaW5kb3c7XHJcbiAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3MucmVuZGVyV2luZG93SWQgPSB3aW5kb3cucmVuZGVyV2luZG93SWQ7XHJcblxyXG4gICAgICAgICAgICAvLyBDYW1lcmFcclxuICAgICAgICAgICAgY2FtZXJhQ29uZmlncy5jb2xvck5hbWUgPSB3aW5kb3cuY29sb3JOYW1lO1xyXG4gICAgICAgICAgICBjYW1lcmFDb25maWdzLmRlcHRoU3RlbmNpbE5hbWUgPSB3aW5kb3cuZGVwdGhTdGVuY2lsTmFtZTtcclxuXHJcbiAgICAgICAgICAgIC8vIFBpcGVsaW5lXHJcbiAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3MuZW5hYmxlRnVsbFBpcGVsaW5lID0gKGNhbWVyYS52aXNpYmlsaXR5ICYgKExheWVycy5FbnVtLkRFRkFVTFQpKSAhPT0gMDtcclxuICAgICAgICAgICAgY2FtZXJhQ29uZmlncy5lbmFibGVQcm9maWxlciA9IHBwbC5wcm9maWxlciAmJiBpc0dhbWVWaWV3O1xyXG4gICAgICAgICAgICBjYW1lcmFDb25maWdzLnJlbWFpbmluZ1Bhc3NlcyA9IDA7XHJcblxyXG4gICAgICAgICAgICAvLyBTaGFkaW5nIHNjYWxlXHJcbiAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3Muc2hhZGluZ1NjYWxlID0gY2FtZXJhQ29uZmlncy5zZXR0aW5ncy5zaGFkaW5nU2NhbGU7XHJcbiAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3MuZW5hYmxlU2hhZGluZ1NjYWxlID0gY2FtZXJhQ29uZmlncy5zZXR0aW5ncy5lbmFibGVTaGFkaW5nU2NhbGVcclxuICAgICAgICAgICAgICAgICYmIGNhbWVyYUNvbmZpZ3Muc2hhZGluZ1NjYWxlICE9PSAxLjA7XHJcblxyXG4gICAgICAgICAgICBjYW1lcmFDb25maWdzLm5hdGl2ZVdpZHRoID0gTWF0aC5tYXgoTWF0aC5mbG9vcih3aW5kb3cud2lkdGgpLCAxKTtcclxuICAgICAgICAgICAgY2FtZXJhQ29uZmlncy5uYXRpdmVIZWlnaHQgPSBNYXRoLm1heChNYXRoLmZsb29yKHdpbmRvdy5oZWlnaHQpLCAxKTtcclxuXHJcbiAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3Mud2lkdGggPSBjYW1lcmFDb25maWdzLmVuYWJsZVNoYWRpbmdTY2FsZVxyXG4gICAgICAgICAgICAgICAgPyBNYXRoLm1heChNYXRoLmZsb29yKGNhbWVyYUNvbmZpZ3MubmF0aXZlV2lkdGggKiBjYW1lcmFDb25maWdzLnNoYWRpbmdTY2FsZSksIDEpXHJcbiAgICAgICAgICAgICAgICA6IGNhbWVyYUNvbmZpZ3MubmF0aXZlV2lkdGg7XHJcbiAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3MuaGVpZ2h0ID0gY2FtZXJhQ29uZmlncy5lbmFibGVTaGFkaW5nU2NhbGVcclxuICAgICAgICAgICAgICAgID8gTWF0aC5tYXgoTWF0aC5mbG9vcihjYW1lcmFDb25maWdzLm5hdGl2ZUhlaWdodCAqIGNhbWVyYUNvbmZpZ3Muc2hhZGluZ1NjYWxlKSwgMSlcclxuICAgICAgICAgICAgICAgIDogY2FtZXJhQ29uZmlncy5uYXRpdmVIZWlnaHQ7XHJcblxyXG4gICAgICAgICAgICAvLyBSYWRpYW5jZVxyXG4gICAgICAgICAgICBjYW1lcmFDb25maWdzLmVuYWJsZUhEUiA9IGNhbWVyYUNvbmZpZ3MuZW5hYmxlRnVsbFBpcGVsaW5lXHJcbiAgICAgICAgICAgICAgICAmJiBwaXBlbGluZUNvbmZpZ3MudXNlRmxvYXRPdXRwdXQ7XHJcbiAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3MucmFkaWFuY2VGb3JtYXQgPSBjYW1lcmFDb25maWdzLmVuYWJsZUhEUlxyXG4gICAgICAgICAgICAgICAgPyBnZnguRm9ybWF0LlJHQkExNkYgOiBnZnguRm9ybWF0LlJHQkE4O1xyXG5cclxuICAgICAgICAgICAgLy8gVG9uZSBNYXBwaW5nXHJcbiAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3MuY29weUFuZFRvbmVtYXBNYXRlcmlhbCA9IHRoaXMuX2NvcHlBbmRUb25lbWFwTWF0ZXJpYWw7XHJcblxyXG4gICAgICAgICAgICAvLyBEZXB0aFxyXG4gICAgICAgICAgICBjYW1lcmFDb25maWdzLmVuYWJsZVN0b3JlU2NlbmVEZXB0aCA9IGZhbHNlO1xyXG4gICAgICAgIH1cclxuXHJcbiAgICAgICAgcHJpdmF0ZSBfc2V0dXBDYW1lcmFDb25maWdzKFxyXG4gICAgICAgICAgICBwcGw6IHJlbmRlcmluZy5CYXNpY1BpcGVsaW5lLFxyXG4gICAgICAgICAgICBjYW1lcmE6IHJlbmRlcmVyLnNjZW5lLkNhbWVyYSxcclxuICAgICAgICAgICAgcGlwZWxpbmVDb25maWdzOiBQaXBlbGluZUNvbmZpZ3MsXHJcbiAgICAgICAgICAgIGNhbWVyYUNvbmZpZ3M6IENhbWVyYUNvbmZpZ3NcclxuICAgICAgICApOiB2b2lkIHtcclxuICAgICAgICAgICAgdGhpcy5fc2V0dXBQaXBlbGluZVByZXZpZXcoY2FtZXJhLCBjYW1lcmFDb25maWdzKTtcclxuXHJcbiAgICAgICAgICAgIHRoaXMuX3ByZXBhcmVQaXBlbGluZVBhc3NlcyhjYW1lcmFDb25maWdzKTtcclxuXHJcbiAgICAgICAgICAgIHNvcnRQaXBlbGluZVBhc3NCdWlsZGVyc0J5Q29uZmlnT3JkZXIodGhpcy5fcGFzc0J1aWxkZXJzKTtcclxuXHJcbiAgICAgICAgICAgIHRoaXMuX3NldHVwQnVpbHRpbkNhbWVyYUNvbmZpZ3MocHBsLCBjYW1lcmEsIHBpcGVsaW5lQ29uZmlncywgY2FtZXJhQ29uZmlncyk7XHJcblxyXG4gICAgICAgICAgICBmb3IgKGNvbnN0IGJ1aWxkZXIgb2YgdGhpcy5fcGFzc0J1aWxkZXJzKSB7XHJcbiAgICAgICAgICAgICAgICBpZiAoYnVpbGRlci5jb25maWdDYW1lcmEpIHtcclxuICAgICAgICAgICAgICAgICAgICBidWlsZGVyLmNvbmZpZ0NhbWVyYShjYW1lcmEsIHBpcGVsaW5lQ29uZmlncywgY2FtZXJhQ29uZmlncyk7XHJcbiAgICAgICAgICAgICAgICB9XHJcbiAgICAgICAgICAgIH1cclxuICAgICAgICB9XHJcblxyXG4gICAgICAgIC8vIC0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS1cclxuICAgICAgICAvLyBJbnRlcmZhY2VcclxuICAgICAgICAvLyAtLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tXHJcbiAgICAgICAgd2luZG93UmVzaXplKFxyXG4gICAgICAgICAgICBwcGw6IHJlbmRlcmluZy5CYXNpY1BpcGVsaW5lLFxyXG4gICAgICAgICAgICB3aW5kb3c6IHJlbmRlcmVyLlJlbmRlcldpbmRvdyxcclxuICAgICAgICAgICAgY2FtZXJhOiByZW5kZXJlci5zY2VuZS5DYW1lcmEsXHJcbiAgICAgICAgICAgIG5hdGl2ZVdpZHRoOiBudW1iZXIsXHJcbiAgICAgICAgICAgIG5hdGl2ZUhlaWdodDogbnVtYmVyLFxyXG4gICAgICAgICk6IHZvaWQge1xyXG4gICAgICAgICAgICBzZXR1cFBpcGVsaW5lQ29uZmlncyhwcGwsIHRoaXMuX2NvbmZpZ3MpO1xyXG5cclxuICAgICAgICAgICAgdGhpcy5fc2V0dXBDYW1lcmFDb25maWdzKHBwbCwgY2FtZXJhLCB0aGlzLl9jb25maWdzLCB0aGlzLl9jYW1lcmFDb25maWdzKTtcclxuXHJcbiAgICAgICAgICAgIC8vIFJlbmRlciBXaW5kb3cgKFVJKVxyXG4gICAgICAgICAgICBjb25zdCBpZCA9IHdpbmRvdy5yZW5kZXJXaW5kb3dJZDtcclxuXHJcbiAgICAgICAgICAgIHBwbC5hZGRSZW5kZXJXaW5kb3codGhpcy5fY2FtZXJhQ29uZmlncy5jb2xvck5hbWUsXHJcbiAgICAgICAgICAgICAgICBGb3JtYXQuUkdCQTgsIG5hdGl2ZVdpZHRoLCBuYXRpdmVIZWlnaHQsIHdpbmRvdyxcclxuICAgICAgICAgICAgICAgIHRoaXMuX2NhbWVyYUNvbmZpZ3MuZGVwdGhTdGVuY2lsTmFtZSk7XHJcblxyXG4gICAgICAgICAgICBjb25zdCB3aWR0aCA9IHRoaXMuX2NhbWVyYUNvbmZpZ3Mud2lkdGg7XHJcbiAgICAgICAgICAgIGNvbnN0IGhlaWdodCA9IHRoaXMuX2NhbWVyYUNvbmZpZ3MuaGVpZ2h0O1xyXG5cclxuICAgICAgICAgICAgaWYgKHRoaXMuX2NhbWVyYUNvbmZpZ3MuZW5hYmxlU2hhZGluZ1NjYWxlKSB7XHJcbiAgICAgICAgICAgICAgICBwcGwuYWRkRGVwdGhTdGVuY2lsKGBTY2FsZWRTY2VuZURlcHRoXyR7aWR9YCwgRm9ybWF0LkRFUFRIX1NURU5DSUwsIHdpZHRoLCBoZWlnaHQpO1xyXG4gICAgICAgICAgICAgICAgcHBsLmFkZFJlbmRlclRhcmdldChgU2NhbGVkUmFkaWFuY2UwXyR7aWR9YCwgdGhpcy5fY2FtZXJhQ29uZmlncy5yYWRpYW5jZUZvcm1hdCwgd2lkdGgsIGhlaWdodCk7XHJcbiAgICAgICAgICAgICAgICBwcGwuYWRkUmVuZGVyVGFyZ2V0KGBTY2FsZWRSYWRpYW5jZTFfJHtpZH1gLCB0aGlzLl9jYW1lcmFDb25maWdzLnJhZGlhbmNlRm9ybWF0LCB3aWR0aCwgaGVpZ2h0KTtcclxuICAgICAgICAgICAgICAgIHBwbC5hZGRSZW5kZXJUYXJnZXQoYFNjYWxlZExkckNvbG9yMF8ke2lkfWAsIEZvcm1hdC5SR0JBOCwgd2lkdGgsIGhlaWdodCk7XHJcbiAgICAgICAgICAgICAgICBwcGwuYWRkUmVuZGVyVGFyZ2V0KGBTY2FsZWRMZHJDb2xvcjFfJHtpZH1gLCBGb3JtYXQuUkdCQTgsIHdpZHRoLCBoZWlnaHQpO1xyXG4gICAgICAgICAgICB9IGVsc2Uge1xyXG4gICAgICAgICAgICAgICAgcHBsLmFkZERlcHRoU3RlbmNpbChgU2NlbmVEZXB0aF8ke2lkfWAsIEZvcm1hdC5ERVBUSF9TVEVOQ0lMLCB3aWR0aCwgaGVpZ2h0KTtcclxuICAgICAgICAgICAgICAgIHBwbC5hZGRSZW5kZXJUYXJnZXQoYFJhZGlhbmNlMF8ke2lkfWAsIHRoaXMuX2NhbWVyYUNvbmZpZ3MucmFkaWFuY2VGb3JtYXQsIHdpZHRoLCBoZWlnaHQpO1xyXG4gICAgICAgICAgICAgICAgcHBsLmFkZFJlbmRlclRhcmdldChgUmFkaWFuY2UxXyR7aWR9YCwgdGhpcy5fY2FtZXJhQ29uZmlncy5yYWRpYW5jZUZvcm1hdCwgd2lkdGgsIGhlaWdodCk7XHJcbiAgICAgICAgICAgICAgICBwcGwuYWRkUmVuZGVyVGFyZ2V0KGBMZHJDb2xvcjBfJHtpZH1gLCBGb3JtYXQuUkdCQTgsIHdpZHRoLCBoZWlnaHQpO1xyXG4gICAgICAgICAgICAgICAgcHBsLmFkZFJlbmRlclRhcmdldChgTGRyQ29sb3IxXyR7aWR9YCwgRm9ybWF0LlJHQkE4LCB3aWR0aCwgaGVpZ2h0KTtcclxuICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICBwcGwuYWRkUmVuZGVyVGFyZ2V0KGBVaUNvbG9yMF8ke2lkfWAsIEZvcm1hdC5SR0JBOCwgbmF0aXZlV2lkdGgsIG5hdGl2ZUhlaWdodCk7XHJcbiAgICAgICAgICAgIHBwbC5hZGRSZW5kZXJUYXJnZXQoYFVpQ29sb3IxXyR7aWR9YCwgRm9ybWF0LlJHQkE4LCBuYXRpdmVXaWR0aCwgbmF0aXZlSGVpZ2h0KTtcclxuXHJcbiAgICAgICAgICAgIGZvciAoY29uc3QgYnVpbGRlciBvZiB0aGlzLl9wYXNzQnVpbGRlcnMpIHtcclxuICAgICAgICAgICAgICAgIGlmIChidWlsZGVyLndpbmRvd1Jlc2l6ZSkge1xyXG4gICAgICAgICAgICAgICAgICAgIGJ1aWxkZXIud2luZG93UmVzaXplKHBwbCwgdGhpcy5fY29uZmlncywgdGhpcy5fY2FtZXJhQ29uZmlncywgd2luZG93LCBjYW1lcmEsIG5hdGl2ZVdpZHRoLCBuYXRpdmVIZWlnaHQpO1xyXG4gICAgICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICB9XHJcbiAgICAgICAgfVxyXG4gICAgICAgIHNldHVwKGNhbWVyYXM6IHJlbmRlcmVyLnNjZW5lLkNhbWVyYVtdLCBwcGw6IHJlbmRlcmluZy5CYXNpY1BpcGVsaW5lKTogdm9pZCB7XHJcbiAgICAgICAgICAgIC8vIFRPRE8oemhvdXpoZW5nbG9uZyk6IE1ha2UgZGVmYXVsdCBlZmZlY3QgYXNzZXQgbG9hZGluZyBlYXJsaWVyIGFuZCByZW1vdmUgX2luaXRNYXRlcmlhbHNcclxuICAgICAgICAgICAgaWYgKHRoaXMuX2luaXRNYXRlcmlhbHMocHBsKSkge1xyXG4gICAgICAgICAgICAgICAgcmV0dXJuO1xyXG4gICAgICAgICAgICB9XHJcblxyXG4gICAgICAgICAgICAvLyBSZW5kZXIgY2FtZXJhc1xyXG4gICAgICAgICAgICAvLyBsb2coYD09PT09PT09PT09PT09PT09PT09IE9uZSBGcmFtZSA9PT09PT09PT09PT09PT09PT09PWApO1xyXG4gICAgICAgICAgICBmb3IgKGNvbnN0IGNhbWVyYSBvZiBjYW1lcmFzKSB7XHJcbiAgICAgICAgICAgICAgICAvLyBTa2lwIGludmFsaWQgY2FtZXJhXHJcbiAgICAgICAgICAgICAgICBpZiAoIWNhbWVyYS5zY2VuZSB8fCAhY2FtZXJhLndpbmRvdykge1xyXG4gICAgICAgICAgICAgICAgICAgIGNvbnRpbnVlO1xyXG4gICAgICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICAgICAgLy8gU2V0dXAgY2FtZXJhIGNvbmZpZ3NcclxuICAgICAgICAgICAgICAgIHRoaXMuX3NldHVwQ2FtZXJhQ29uZmlncyhwcGwsIGNhbWVyYSwgdGhpcy5fY29uZmlncywgdGhpcy5fY2FtZXJhQ29uZmlncyk7XHJcbiAgICAgICAgICAgICAgICAvLyBsb2coYFNldHVwIGNhbWVyYTogJHtjYW1lcmEubm9kZSEubmFtZX0sIHdpbmRvdzogJHtjYW1lcmEud2luZG93LnJlbmRlcldpbmRvd0lkfSwgaXNGdWxsOiAke3RoaXMuX2NhbWVyYUNvbmZpZ3MuZW5hYmxlRnVsbFBpcGVsaW5lfSwgYFxyXG4gICAgICAgICAgICAgICAgLy8gICAgICsgYHNpemU6ICR7Y2FtZXJhLndpbmRvdy53aWR0aH14JHtjYW1lcmEud2luZG93LmhlaWdodH1gKTtcclxuXHJcbiAgICAgICAgICAgICAgICB0aGlzLl9waXBlbGluZUV2ZW50LmVtaXQoUGlwZWxpbmVFdmVudFR5cGUuUkVOREVSX0NBTUVSQV9CRUdJTiwgY2FtZXJhKTtcclxuXHJcbiAgICAgICAgICAgICAgICAvLyBCdWlsZCBwaXBlbGluZVxyXG4gICAgICAgICAgICAgICAgaWYgKHRoaXMuX2NhbWVyYUNvbmZpZ3MuZW5hYmxlRnVsbFBpcGVsaW5lKSB7XHJcbiAgICAgICAgICAgICAgICAgICAgdGhpcy5fYnVpbGRGb3J3YXJkUGlwZWxpbmUocHBsLCBjYW1lcmEsIGNhbWVyYS5zY2VuZSwgdGhpcy5fcGFzc0J1aWxkZXJzKTtcclxuICAgICAgICAgICAgICAgIH0gZWxzZSB7XHJcbiAgICAgICAgICAgICAgICAgICAgdGhpcy5fYnVpbGRTaW1wbGVQaXBlbGluZShwcGwsIGNhbWVyYSk7XHJcbiAgICAgICAgICAgICAgICB9XHJcblxyXG4gICAgICAgICAgICAgICAgdGhpcy5fcGlwZWxpbmVFdmVudC5lbWl0KFBpcGVsaW5lRXZlbnRUeXBlLlJFTkRFUl9DQU1FUkFfRU5ELCBjYW1lcmEpO1xyXG4gICAgICAgICAgICB9XHJcbiAgICAgICAgfVxyXG4gICAgICAgIC8vIC0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS1cclxuICAgICAgICAvLyBQaXBlbGluZXNcclxuICAgICAgICAvLyAtLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tXHJcbiAgICAgICAgcHJpdmF0ZSBfYnVpbGRTaW1wbGVQaXBlbGluZShcclxuICAgICAgICAgICAgcHBsOiByZW5kZXJpbmcuQmFzaWNQaXBlbGluZSxcclxuICAgICAgICAgICAgY2FtZXJhOiByZW5kZXJlci5zY2VuZS5DYW1lcmEsXHJcbiAgICAgICAgKTogdm9pZCB7XHJcbiAgICAgICAgICAgIGNvbnN0IHdpZHRoID0gTWF0aC5tYXgoTWF0aC5mbG9vcihjYW1lcmEud2luZG93LndpZHRoKSwgMSk7XHJcbiAgICAgICAgICAgIGNvbnN0IGhlaWdodCA9IE1hdGgubWF4KE1hdGguZmxvb3IoY2FtZXJhLndpbmRvdy5oZWlnaHQpLCAxKTtcclxuICAgICAgICAgICAgY29uc3QgY29sb3JOYW1lID0gdGhpcy5fY2FtZXJhQ29uZmlncy5jb2xvck5hbWU7XHJcbiAgICAgICAgICAgIGNvbnN0IGRlcHRoU3RlbmNpbE5hbWUgPSB0aGlzLl9jYW1lcmFDb25maWdzLmRlcHRoU3RlbmNpbE5hbWU7XHJcblxyXG4gICAgICAgICAgICBjb25zdCB2aWV3cG9ydCA9IGNhbWVyYS52aWV3cG9ydDsgIC8vIFJlZHVjZSBDKysvVFMgaW50ZXJvcFxyXG4gICAgICAgICAgICB0aGlzLl92aWV3cG9ydC5sZWZ0ID0gTWF0aC5yb3VuZCh2aWV3cG9ydC54ICogd2lkdGgpO1xyXG4gICAgICAgICAgICB0aGlzLl92aWV3cG9ydC50b3AgPSBNYXRoLnJvdW5kKHZpZXdwb3J0LnkgKiBoZWlnaHQpO1xyXG4gICAgICAgICAgICAvLyBIZXJlIHdlIG11c3QgdXNlIGNhbWVyYS52aWV3cG9ydC53aWR0aCBpbnN0ZWFkIG9mIGNhbWVyYS52aWV3cG9ydC56LCB3aGljaFxyXG4gICAgICAgICAgICAvLyBpcyB1bmRlZmluZWQgb24gbmF0aXZlIHBsYXRmb3JtLiBUaGUgc2FtZSBhcyBjYW1lcmEudmlld3BvcnQuaGVpZ2h0LlxyXG4gICAgICAgICAgICB0aGlzLl92aWV3cG9ydC53aWR0aCA9IE1hdGgubWF4KE1hdGgucm91bmQodmlld3BvcnQud2lkdGggKiB3aWR0aCksIDEpO1xyXG4gICAgICAgICAgICB0aGlzLl92aWV3cG9ydC5oZWlnaHQgPSBNYXRoLm1heChNYXRoLnJvdW5kKHZpZXdwb3J0LmhlaWdodCAqIGhlaWdodCksIDEpO1xyXG5cclxuICAgICAgICAgICAgY29uc3QgY2xlYXJDb2xvciA9IGNhbWVyYS5jbGVhckNvbG9yOyAgLy8gUmVkdWNlIEMrKy9UUyBpbnRlcm9wXHJcbiAgICAgICAgICAgIHRoaXMuX2NsZWFyQ29sb3IueCA9IGNsZWFyQ29sb3IueDtcclxuICAgICAgICAgICAgdGhpcy5fY2xlYXJDb2xvci55ID0gY2xlYXJDb2xvci55O1xyXG4gICAgICAgICAgICB0aGlzLl9jbGVhckNvbG9yLnogPSBjbGVhckNvbG9yLno7XHJcbiAgICAgICAgICAgIHRoaXMuX2NsZWFyQ29sb3IudyA9IGNsZWFyQ29sb3IudztcclxuXHJcbiAgICAgICAgICAgIGNvbnN0IHBhc3MgPSBwcGwuYWRkUmVuZGVyUGFzcyh3aWR0aCwgaGVpZ2h0LCAnZGVmYXVsdCcpO1xyXG5cclxuICAgICAgICAgICAgLy8gYmluZCBvdXRwdXQgcmVuZGVyIHRhcmdldFxyXG4gICAgICAgICAgICBpZiAoZm9yd2FyZE5lZWRDbGVhckNvbG9yKGNhbWVyYSkpIHtcclxuICAgICAgICAgICAgICAgIHBhc3MuYWRkUmVuZGVyVGFyZ2V0KGNvbG9yTmFtZSwgTG9hZE9wLkNMRUFSLCBTdG9yZU9wLlNUT1JFLCB0aGlzLl9jbGVhckNvbG9yKTtcclxuICAgICAgICAgICAgfSBlbHNlIHtcclxuICAgICAgICAgICAgICAgIHBhc3MuYWRkUmVuZGVyVGFyZ2V0KGNvbG9yTmFtZSwgTG9hZE9wLkxPQUQsIFN0b3JlT3AuU1RPUkUpO1xyXG4gICAgICAgICAgICB9XHJcblxyXG4gICAgICAgICAgICAvLyBiaW5kIGRlcHRoIHN0ZW5jaWwgYnVmZmVyXHJcbiAgICAgICAgICAgIGlmIChjYW1lcmEuY2xlYXJGbGFnICYgQ2xlYXJGbGFnQml0LkRFUFRIX1NURU5DSUwpIHtcclxuICAgICAgICAgICAgICAgIHBhc3MuYWRkRGVwdGhTdGVuY2lsKFxyXG4gICAgICAgICAgICAgICAgICAgIGRlcHRoU3RlbmNpbE5hbWUsXHJcbiAgICAgICAgICAgICAgICAgICAgTG9hZE9wLkNMRUFSLFxyXG4gICAgICAgICAgICAgICAgICAgIFN0b3JlT3AuRElTQ0FSRCxcclxuICAgICAgICAgICAgICAgICAgICBjYW1lcmEuY2xlYXJEZXB0aCxcclxuICAgICAgICAgICAgICAgICAgICBjYW1lcmEuY2xlYXJTdGVuY2lsLFxyXG4gICAgICAgICAgICAgICAgICAgIGNhbWVyYS5jbGVhckZsYWcgJiBDbGVhckZsYWdCaXQuREVQVEhfU1RFTkNJTCxcclxuICAgICAgICAgICAgICAgICk7XHJcbiAgICAgICAgICAgIH0gZWxzZSB7XHJcbiAgICAgICAgICAgICAgICBwYXNzLmFkZERlcHRoU3RlbmNpbChkZXB0aFN0ZW5jaWxOYW1lLCBMb2FkT3AuTE9BRCwgU3RvcmVPcC5ESVNDQVJEKTtcclxuICAgICAgICAgICAgfVxyXG5cclxuICAgICAgICAgICAgcGFzcy5zZXRWaWV3cG9ydCh0aGlzLl92aWV3cG9ydCk7XHJcblxyXG4gICAgICAgICAgICAvLyBUaGUgb3BhcXVlIHF1ZXVlIGlzIHVzZWQgZm9yIFJlZmxlY3Rpb24gcHJvYmUgcHJldmlld1xyXG4gICAgICAgICAgICBwYXNzLmFkZFF1ZXVlKFF1ZXVlSGludC5PUEFRVUUpXHJcbiAgICAgICAgICAgICAgICAuYWRkU2NlbmUoY2FtZXJhLCBTY2VuZUZsYWdzLk9QQVFVRSk7XHJcblxyXG4gICAgICAgICAgICAvLyBUaGUgYmxlbmQgcXVldWUgaXMgdXNlZCBmb3IgVUkgYW5kIEdpem1vc1xyXG4gICAgICAgICAgICBsZXQgZmxhZ3MgPSBTY2VuZUZsYWdzLkJMRU5EIHwgU2NlbmVGbGFncy5VSTtcclxuICAgICAgICAgICAgaWYgKHRoaXMuX2NhbWVyYUNvbmZpZ3MuZW5hYmxlUHJvZmlsZXIpIHtcclxuICAgICAgICAgICAgICAgIGZsYWdzIHw9IFNjZW5lRmxhZ3MuUFJPRklMRVI7XHJcbiAgICAgICAgICAgICAgICBwYXNzLnNob3dTdGF0aXN0aWNzID0gdHJ1ZTtcclxuICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICBwYXNzLmFkZFF1ZXVlKFF1ZXVlSGludC5CTEVORClcclxuICAgICAgICAgICAgICAgIC5hZGRTY2VuZShjYW1lcmEsIGZsYWdzKTtcclxuICAgICAgICB9XHJcblxyXG4gICAgICAgIHByaXZhdGUgX2J1aWxkRm9yd2FyZFBpcGVsaW5lKFxyXG4gICAgICAgICAgICBwcGw6IHJlbmRlcmluZy5CYXNpY1BpcGVsaW5lLFxyXG4gICAgICAgICAgICBjYW1lcmE6IHJlbmRlcmVyLnNjZW5lLkNhbWVyYSxcclxuICAgICAgICAgICAgc2NlbmU6IHJlbmRlcmVyLlJlbmRlclNjZW5lLFxyXG4gICAgICAgICAgICBwYXNzQnVpbGRlcnM6IHJlbmRlcmluZy5QaXBlbGluZVBhc3NCdWlsZGVyW10sXHJcbiAgICAgICAgKTogdm9pZCB7XHJcbiAgICAgICAgICAgIHNvcnRQaXBlbGluZVBhc3NCdWlsZGVyc0J5UmVuZGVyT3JkZXIocGFzc0J1aWxkZXJzKTtcclxuXHJcbiAgICAgICAgICAgIGNvbnN0IGNvbnRleHQ6IFBpcGVsaW5lQ29udGV4dCA9IHtcclxuICAgICAgICAgICAgICAgIGNvbG9yTmFtZTogJycsXHJcbiAgICAgICAgICAgICAgICBkZXB0aFN0ZW5jaWxOYW1lOiAnJyxcclxuICAgICAgICAgICAgfTtcclxuXHJcbiAgICAgICAgICAgIGxldCBsYXN0UGFzczogcmVuZGVyaW5nLkJhc2ljUmVuZGVyUGFzc0J1aWxkZXIgfCB1bmRlZmluZWQgPSB1bmRlZmluZWQ7XHJcblxyXG4gICAgICAgICAgICBmb3IgKGNvbnN0IGJ1aWxkZXIgb2YgcGFzc0J1aWxkZXJzKSB7XHJcbiAgICAgICAgICAgICAgICBpZiAoYnVpbGRlci5zZXR1cCkge1xyXG4gICAgICAgICAgICAgICAgICAgIGxhc3RQYXNzID0gYnVpbGRlci5zZXR1cChwcGwsIHRoaXMuX2NvbmZpZ3MsIHRoaXMuX2NhbWVyYUNvbmZpZ3MsXHJcbiAgICAgICAgICAgICAgICAgICAgICAgIGNhbWVyYSwgY29udGV4dCwgbGFzdFBhc3MpO1xyXG4gICAgICAgICAgICAgICAgfVxyXG4gICAgICAgICAgICB9XHJcblxyXG4gICAgICAgICAgICBhc3NlcnQodGhpcy5fY2FtZXJhQ29uZmlncy5yZW1haW5pbmdQYXNzZXMgPT09IDApO1xyXG4gICAgICAgIH1cclxuXHJcbiAgICAgICAgcHJpdmF0ZSBfaW5pdE1hdGVyaWFscyhwcGw6IHJlbmRlcmluZy5CYXNpY1BpcGVsaW5lKTogbnVtYmVyIHtcclxuICAgICAgICAgICAgaWYgKHRoaXMuX2luaXRpYWxpemVkKSB7XHJcbiAgICAgICAgICAgICAgICByZXR1cm4gMDtcclxuICAgICAgICAgICAgfVxyXG5cclxuICAgICAgICAgICAgc2V0dXBQaXBlbGluZUNvbmZpZ3MocHBsLCB0aGlzLl9jb25maWdzKTtcclxuXHJcbiAgICAgICAgICAgIC8vIFdoZW4gYWRkIG5ldyBlZmZlY3QgYXNzZXQsIHBsZWFzZSBhZGQgaXRzIHV1aWQgdG8gdGhlIGRlcGVuZGVudEFzc2V0cyBpbiBjYy5jb25maWcuanNvbi5cclxuICAgICAgICAgICAgdGhpcy5fY29weUFuZFRvbmVtYXBNYXRlcmlhbC5fdXVpZCA9IGBidWlsdGluLXBpcGVsaW5lLXRvbmUtbWFwcGluZy1tYXRlcmlhbGA7XHJcbiAgICAgICAgICAgIHRoaXMuX2NvcHlBbmRUb25lbWFwTWF0ZXJpYWwuaW5pdGlhbGl6ZSh7IGVmZmVjdE5hbWU6ICdwaXBlbGluZS9wb3N0LXByb2Nlc3MvdG9uZS1tYXBwaW5nJyB9KTtcclxuXHJcbiAgICAgICAgICAgIGlmICh0aGlzLl9jb3B5QW5kVG9uZW1hcE1hdGVyaWFsLmVmZmVjdEFzc2V0KSB7XHJcbiAgICAgICAgICAgICAgICB0aGlzLl9pbml0aWFsaXplZCA9IHRydWU7XHJcbiAgICAgICAgICAgIH1cclxuXHJcbiAgICAgICAgICAgIHJldHVybiB0aGlzLl9pbml0aWFsaXplZCA/IDAgOiAxO1xyXG4gICAgICAgIH1cclxuICAgIH1cclxuXHJcbiAgICByZW5kZXJpbmcuc2V0Q3VzdG9tUGlwZWxpbmUoJ0J1aWx0aW4nLCBuZXcgQnVpbHRpblBpcGVsaW5lQnVpbGRlcigpKTtcclxuXHJcbn0gLy8gaWYgKHJlbmRlcmluZylcclxuIl19