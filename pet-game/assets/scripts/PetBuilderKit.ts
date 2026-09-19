import { Color, EffectAsset, Layers, Material, Mesh, Node, Vec3, Vec4 } from 'cc'
import { Geo } from './PetMeshFactory'
import { shift } from './PetGameTheme'

/**
 * 3D 建模基础设施（材质工厂 + 几何工具）。
 *
 * 视觉重构 v4 的关键变化：
 *  - 几何：新增 surf()，直接提交手写网格（PetMeshFactory 的旋转体/扫掠/椭球），
 *    替代 v3 的"球体拼装"（拼装会导致剪影破碎、接缝穿插、法线不连续）；
 *  - 材质：pet-toon 升级为"柔和绒感"（宽羽化渐变 + 绒光边缘 + 可调高光 + 极细描边），
 *    对齐参考图的毛绒/软陶质感；毛绒物种调高 fur，光滑物种调高 spec。
 *
 * Cocos 4.0 alpha 运行时门面与 3.x DTS 的命名差异适配：
 *  MeshRenderer→ModelComponent、MeshUtils 在 cc.utils、材质经 Material.initialize({ effectAsset })。
 */

export interface PetCcRuntime {
    Node: { new (name?: string): Node }
    ModelComponent: object
    Material: { new (): Material }
    utils: { createMesh: (geometry: object) => Mesh }
    primitives: {
        sphere: (radius?: number) => object
        capsule: (radiusTop?: number, radiusBottom?: number, height?: number) => object
        cone: (radius?: number, height?: number) => object
        cylinder: (radiusTop?: number, radiusBottom?: number, height?: number) => object
        box: (options?: { width?: number; height?: number; length?: number }) => object
        torus: (radius?: number, tube?: number) => object
    }
}

export interface PetModelLike {
    mesh: Mesh | null
    material: Material | null
    _getBuiltinMaterial(): Material
}

/** 部件风格：颜色 + 明暗/质感/透明等表现参数 */
export interface PartStyle {
    /** 主色（亮部） */
    color: Color
    /** 暗部色；缺省按主色压暗 34% */
    shade?: Color
    /** 描边宽度（模型空间，0 = 不描边；默认 0.006 的极细边） */
    outline?: number
    /** 轮廓光强度覆盖（默认 0.26） */
    rim?: number
    /** 顶部天光提亮覆盖（默认 0.14） */
    top?: number
    /** 透明度（< 255 时走 transparent 技术，depthWrite 关闭） */
    alpha?: number
    /** 自发光（暗部 = 主色，用于灯光/光点/玻璃透光） */
    glow?: boolean
    /** 绒光强度覆盖（毛绒物种 0.14-0.22；光滑物种 0.03-0.08；0 = 无绒边） */
    fur?: number
    /** 绒光锐度覆盖（默认 1.7；越小边缘越宽越柔） */
    furSharp?: number
    /** 高光强度覆盖（光滑物种 0.16-0.30 呈现湿润感；毛绒物种 0.02-0.08） */
    spec?: number
    /** 高光锐度覆盖（默认 26；越大越"点状"） */
    specSharp?: number
}

interface PartTransform {
    rot?: Vec3
    scale?: Vec3
}

/** 默认描边宽度：参考图没有硬描边，只保留极细的轮廓"收边" */
const DEFAULT_OUTLINE = 0.006

/** 3D 场景构建工具：节点/网格/材质工厂（宠物与房间共用同一套视觉语言） */
export class PetBuilderKit {

    readonly cc: PetCcRuntime
    private toonEffect: EffectAsset | null = null
    private fallbackEffect: EffectAsset | null = null

    constructor(cc: PetCcRuntime) {
        this.cc = cc
    }

    /** pet-toon 是否生效（供调试与降级分支判断） */
    get isToon(): boolean {
        return !!this.toonEffect
    }

    /**
     * 解析可用 effect：优先工程内 pet-toon；
     * 未打包/编译失败时回退 ModelComponent 内置 unlit（Fail-Open）。
     */
    private pickEffect(sample: PetModelLike): EffectAsset | null {
        if (!this.toonEffect) {
            this.toonEffect = EffectAsset.get('pet-toon')
        }
        if (this.toonEffect) {
            return this.toonEffect
        }
        if (!this.fallbackEffect) {
            try {
                const builtin = sample._getBuiltinMaterial()
                this.fallbackEffect = builtin && builtin.effectAsset ? builtin.effectAsset : null
            } catch (e) {
                // 兜底材质尚未就绪：保持 null，下一次 attach 再试
            }
        }
        return this.fallbackEffect
    }

    /** 3D 节点（DEFAULT 层，挂到父节点） */
    make3dNode(parent: Node, name: string, pos: Vec3, transform?: PartTransform): Node {
        const node = new this.cc.Node(name)
        node.layer = Layers.Enum.DEFAULT
        node.setPosition(pos)
        if (transform?.rot) {
            node.setRotationFromEuler(transform.rot.x, transform.rot.y, transform.rot.z)
        }
        if (transform?.scale) {
            node.setScale(transform.scale.x, transform.scale.y, transform.scale.z)
        }
        parent.addChild(node)
        return node
    }

    /** 组装网格 + 卡通材质（材质策略见类注释） */
    private attach(node: Node, geometry: object, style: PartStyle): PetModelLike {
        const model = node.addComponent(this.cc.ModelComponent) as unknown as PetModelLike
        model.mesh = this.cc.utils.createMesh(geometry)

        const toonEffect = EffectAsset.get('pet-toon') || this.toonEffect
        const effect = toonEffect || this.pickEffect(model)
        if (!effect) {
            return model
        }
        const useToon = !!toonEffect
        this.toonEffect = toonEffect

        const material = new Material()
        const transparent = style.alpha !== undefined && style.alpha < 255
        try {
            material.initialize({
                effectAsset: effect,
                // 自定义 effect：0=opaque（含描边 pass），1=transparent；unlit 兜底：2=alpha-blend
                technique: useToon ? (transparent ? 1 : 0) : (transparent ? 2 : 0),
            })
        } catch (e) {
            return model
        }

        const color = style.color
        const shade = style.glow ? new Color(color.r, color.g, color.b, color.a) : (style.shade || shift(color, -0.34))
        try {
            material.setProperty('mainColor', color)
            if (useToon) {
                material.setProperty('shadeColor', shade)
                // 羽化加宽 → 柔和渐变（参考图没有硬色阶）
                material.setProperty('shadeCtrl', new Vec4(0.52, 0.22, style.rim ?? 0.30, style.top ?? 0.16))
                // 绒光/高光：毛绒物种高绒光、光滑物种高光（见 SpeciesSpec）
                material.setProperty('furCtrl', new Vec4(
                    style.fur ?? 0.16, style.furSharp ?? 1.7, style.spec ?? 0.06, style.specSharp ?? 26))
                const outline = style.outline === undefined ? DEFAULT_OUTLINE : style.outline
                material.setProperty('outlineCtrl', new Vec4(Math.max(0, outline), 0, 0, 0))
            }
        } catch (e) {
            // 属性缺失（回退 unlit）：仅主色生效，视觉降级但不影响功能
        }
        model.material = material
        return model
    }

    /** 通用部件：几何 + 风格 + 变换 */
    part(parent: Node, name: string, geometry: object, style: PartStyle, pos: Vec3, transform?: PartTransform): Node {
        const node = this.make3dNode(parent, name, pos, transform)
        this.attach(node, geometry, style)
        return node
    }

    /** 手写网格部件（旋转体/扫掠/椭球，见 PetMeshFactory） */
    surf(parent: Node, name: string, geometry: Geo, style: PartStyle, pos: Vec3, transform?: PartTransform): Node {
        return this.part(parent, name, geometry, style, pos, transform)
    }

    /** 球体部件 */
    ball(parent: Node, name: string, radius: number, style: PartStyle, pos: Vec3, transform?: PartTransform): Node {
        return this.part(parent, name, this.cc.primitives.sphere(radius), style, pos, transform)
    }

    /** 方块部件（家具/墙体/地板） */
    boxPart(parent: Node, name: string, size: { w: number; h: number; d: number }, style: PartStyle,
            pos: Vec3, transform?: PartTransform): Node {
        return this.part(parent, name,
            this.cc.primitives.box({ width: size.w, height: size.h, length: size.d }), style, pos, transform)
    }

    /** 圆锥部件（尖耳/装饰） */
    conePart(parent: Node, name: string, radius: number, height: number, style: PartStyle,
             pos: Vec3, transform?: PartTransform): Node {
        return this.part(parent, name, this.cc.primitives.cone(radius, height), style, pos, transform)
    }

    /** 胶囊部件（四肢/尾巴/窗棂） */
    capPart(parent: Node, name: string, radius: number, height: number, style: PartStyle,
            pos: Vec3, transform?: PartTransform): Node {
        return this.part(parent, name, this.cc.primitives.capsule(radius, radius, height), style, pos, transform)
    }

    /** 圆柱部件（地毯/碗/花盆/坐垫） */
    cylPart(parent: Node, name: string, topR: number, bottomR: number, height: number, style: PartStyle,
            pos: Vec3, transform?: PartTransform): Node {
        return this.part(parent, name, this.cc.primitives.cylinder(topR, bottomR, height), style, pos, transform)
    }

    /** 圆环部件（呼啦圈/挂环/眼镜框） */
    torusPart(parent: Node, name: string, radius: number, tube: number, style: PartStyle,
              pos: Vec3, transform?: PartTransform): Node {
        return this.part(parent, name, this.cc.primitives.torus(radius, tube), style, pos, transform)
    }

    /** 更新已有部件的材质颜色（换肤/情绪变色用；透明部件同步 alpha） */
    recolor(node: Node, color: Color, shade?: Color, alpha?: number): void {
        const model = node.components.find(
            c => (c as unknown as PetModelLike).mesh !== undefined,
        ) as unknown as PetModelLike | null
        if (!model || !model.material) {
            return
        }
        const next = alpha === undefined ? color : new Color(color.r, color.g, color.b, alpha)
        try {
            model.material.setProperty('mainColor', next)
            if (shade && this.isToon) {
                model.material.setProperty('shadeColor', shade)
            }
        } catch (e) {
            // 材质不可写：忽略（不阻塞主流程）
        }
    }
}
