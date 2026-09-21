import { Color, Component, EffectAsset, Layers, Material, Mesh, Node, Vec3, Vec4 } from 'cc'
import { Geo, P3, ellipsoid, merge, radialUV, spherePatch } from './PetMeshFactory'
import { shift } from './PetGameTheme'

/**
 * 3D 建模基础设施（材质工厂 + 几何工具）。
 *
 * 视觉重构 v5 的关键变化：
 *  - 几何：surf() 提交手写连续曲面（旋转体/扫掠/椭球），surfGroup() 把同材质的成组零件
 *    合并为一个网格（减少 draw call），decal() 提供"柔和贴花"（腮红/高光/软影）；
 *  - 材质：pet-toon 提供屏幕恒定描边、三点式光照（暖主光 + 冷补光 + 克制背光）、
 *    色区柔化（曲面上的柔和明度过渡，替代生硬色块）与弱而宽的主体高光。
 *
 * Cocos 4.0 alpha 运行时门面与 3.x DTS 的命名差异适配：
 *  MeshRenderer→ModelComponent、MeshUtils 在 cc.utils、材质经 Material.initialize({ effectAsset })。
 */

export interface PetCcRuntime {
    Node: { new (name?: string): Node }
    /** 网格渲染组件构造器（Cocos 4.0 alpha 运行时门面：3.x 的 MeshRenderer） */
    ModelComponent: new () => Component
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
    /** 描边宽度（屏幕空间 NDC；0 = 不描边；0.003 约为屏幕高度的 0.15%） */
    outline?: number
    /** 轮廓光强度覆盖（默认 0.26） */
    rim?: number
    /** 顶部天光提亮覆盖（默认 0.14） */
    top?: number
    /** 透明度（< 255 时走 transparent 技术，depthWrite 关闭） */
    alpha?: number
    /** 自发光（暗部 = 主色，用于灯光/光点/玻璃透光） */
    glow?: boolean
    /** 绒光强度覆盖（毛绒物种 0.10-0.18；光滑物种 0.02-0.06；0 = 无绒边） */
    fur?: number
    /** 绒光锐度覆盖（默认 2.0；越小边缘越宽越柔） */
    furSharp?: number
    /** 高光强度覆盖（光滑物种 0.16-0.30 呈现湿润感；毛绒物种 0.03-0.08） */
    spec?: number
    /** 高光锐度覆盖（默认 8；越小高光越宽越柔，避免塑料感） */
    specSharp?: number
    /** 色区柔化目标色（在曲面上把主色渐变为它：肚皮/口鼻的柔和明度区分） */
    mixColor?: Color
    /** 色区柔化控制：x = y 上界（不混合） y = y 下界（完全混合） z = 前向权重 w = 强度 */
    mixCtrl?: Vec4
    /** 柔和贴花：径向 alpha 衰减 [起始半径, 全透明半径]（几何须为单位椭球） */
    soft?: [number, number]
    /** 平涂：不受光照/遮蔽影响（贴花、发光件） */
    flat?: boolean
    /** 强制半透明技术（软贴花即使 alpha=255 也需要混合来获得柔和边缘） */
    blend?: boolean
    /**
     * 使用引擎内置材质（不套用 pet-toon）。
     *
     * 用于**依赖标准 alpha 混合**的部件（软影/光斑/玻璃）：自定义 effect 的 transparent
     * 技术在本构建链（cocos-cli 4.0 alpha）下实测会丢失材质属性（颜色回落到默认白），
     * 而引擎内置 unlit 的混合路径稳定可用。
     */
    plain?: boolean
    /** 面部腮红烘焙参数（头部材质专用：在曲面上做球形径向混色） */
    blush?: { color: Color; strength: number; a: Vec4; b: Vec4 }
}

interface PartTransform {
    rot?: Vec3
    scale?: Vec3
}

/** 默认描边宽度（屏幕空间 NDC）：只做轮廓"收边"，不出现硬黑边 */
const DEFAULT_OUTLINE = 0.0045

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
     * 注入已加载的 pet-toon effect。
     *
     * 自定义 effect 必须由宿主先从 resources 加载（`EffectAsset.get` 只能查到**已加载**的资产），
     * 因此场景构建要等它就绪；未注入时材质自动回退内置材质（Fail-Open）。
     */
    setToonEffect(effect: EffectAsset | null): void {
        this.toonEffect = effect
    }

    /**
     * 解析工程内的 pet-toon effect。
     *
     * 注意：cocos-cli 构建产物里的 effect 资产名可能带路径前缀（由导入器按 DB 相对路径命名），
     * 因此不能只依赖 `EffectAsset.get('pet-toon')`；这里追加一次"按名后缀匹配"的回退，
     * 否则材质会静默退化成内置材质（视觉表现与工程内 effect 完全不同）。
     */
    private resolveToonEffect(): EffectAsset | null {
        if (this.toonEffect) {
            return this.toonEffect
        }
        let found = EffectAsset.get('pet-toon') || null
        if (!found) {
            const registry = (EffectAsset as unknown as { getAll?: () => unknown }).getAll?.()
            const list: EffectAsset[] = registry instanceof Map
                ? Array.from(registry.values() as Iterable<EffectAsset>)
                : (Array.isArray(registry) ? registry as EffectAsset[] : [])
            found = list.find(entry => entry && typeof entry.name === 'string' && entry.name.endsWith('pet-toon')) || null
        }
        this.toonEffect = found
        return found
    }

    /** 该 effect 是否为工程内的 pet-toon（技术索引与属性名都按它来） */
    private isToonEffect(effect: EffectAsset | null): boolean {
        return !!effect && typeof effect.name === 'string' && effect.name.endsWith('pet-toon')
    }

    /**
     * 引擎内置材质（Fail-Open 兜底）：未打包/加载失败时材质仍可用，
     * 只是失去 pet-toon 的描边/绒光/光照表现。
     */
    private pickFallbackEffect(sample: PetModelLike): EffectAsset | null {
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

        const transparent = style.blend === true || (style.alpha !== undefined && style.alpha < 255)
        // plain：强制引擎内置材质（标准透明路径）；其余部件优先 pet-toon。
        // 注意：必须以**最终选中的 effect** 判断技术索引 —— 若用"解析结果是否为空"判断，
        // 一旦出现"解析失败但兜底拿到 pet-toon"的组合，technique 索引就会越界，
        // 材质初始化静默失败并退回内置材质（曾导致所有 toon 表现失效）。
        const effect = style.plain
            ? this.pickFallbackEffect(model)
            : (this.resolveToonEffect() || this.pickFallbackEffect(model))
        if (!effect) {
            return model
        }
        const useToon = !style.plain && this.isToonEffect(effect)

        const material = new Material()
        const technique = useToon ? (transparent ? 1 : 0) : (transparent ? 2 : 0)
        try {
            material.initialize({ effectAsset: effect, technique })
        } catch (error) {
            // 材质初始化失败：保持未设置状态，由引擎给默认材质（Fail-Open，不阻塞场景）
            console.warn(`pet-game: 材质初始化失败（${effect.name} #${technique}）`, error)
            return model
        }

        // 透明度必须并入主色：technique 选择只决定混合模式，alpha 仍然来自 mainColor.a
        const color = style.alpha === undefined || style.alpha >= 255
            ? style.color
            : new Color(style.color.r, style.color.g, style.color.b, style.alpha)
        const shade = style.glow ? new Color(color.r, color.g, color.b, color.a) : (style.shade || shift(color, -0.34))
        try {
            material.setProperty('mainColor', color)
            if (useToon) {
                material.setProperty('shadeColor', shade)
                // 明暗过渡带：0.52±0.20 —— 上一版 0.54±0.26 太宽，整张脸接近平涂，
                // 收窄后脸颊才有"亮面 → 灰面 → 暖灰紫暗面"的三段体积（仍无硬色阶）。
                material.setProperty('shadeCtrl', new Vec4(0.52, 0.20, style.rim ?? 0.13, style.top ?? 0.14))
                // 绒光/高光：毛绒物种高绒光、光滑物种高光（见 SpeciesSpec）
                material.setProperty('furCtrl', new Vec4(
                    style.fur ?? 0.14, style.furSharp ?? 2.0, style.spec ?? 0.05, style.specSharp ?? 8))
                // 色区柔化（肚皮/口鼻的柔和明度过渡，替代生硬色块）
                if (style.mixCtrl) {
                    material.setProperty('mixCtrl', style.mixCtrl)
                }
                if (style.mixColor) {
                    material.setProperty('mixColor', style.mixColor)
                }
                // 柔和贴花（高光/软斑）：径向 alpha 衰减 + 平涂
                if (style.soft || style.flat) {
                    material.setProperty('decalCtrl', new Vec4(
                        style.soft ? style.soft[0] : 0, style.soft ? style.soft[1] : 0, style.flat ? 1 : 0, 0))
                }
                // 面部腮红：烘焙进材质（曲面上的球形径向混色）
                // 注意：Color 的 alpha 是 0-255 整数通道，强度必须换算后再传（否则会被截断为 0）
                if (style.blush) {
                    material.setProperty('blushColor', new Color(
                        style.blush.color.r, style.blush.color.g, style.blush.color.b,
                        Math.round(style.blush.strength * 255)))
                    material.setProperty('blushCtrlA', style.blush.a)
                    material.setProperty('blushCtrlB', style.blush.b)
                }
                const outline = style.outline === undefined ? DEFAULT_OUTLINE : style.outline
                material.setProperty('outlineCtrl', new Vec4(Math.max(0, outline), 0, 0, 0))
                // 描边色随主色压暗（同色系柔边，禁止发黑）
                const edge = shift(color, -0.45)
                material.setProperty('outlineColor', new Color(edge.r, edge.g, edge.b, 255))
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

    /** 成组零件合并为一个部件：同材质 → 1 个 draw call（四肢/脚趾等成组零件，控制移动端开销） */
    surfGroup(parent: Node, name: string, geos: Geo[], style: PartStyle, pos: Vec3, transform?: PartTransform): Node {
        return this.part(parent, name, geos.length === 1 ? geos[0] : merge(...geos), style, pos, transform)
    }

    /**
     * 柔和贴花部件（脚底软影 / 地面光斑）。
     *
     * 几何为单位椭球（uv.x 写入归一化径向距离）、由节点缩放决定实际尺寸 ——
     * 着色器据此生成径向 alpha 渐变，得到"边缘化开"的柔和色斑，而不是硬边色板。
     *
     * 注意：扁平椭球只适合贴在**平面**上；贴在凸曲面（脸颊/眼球）上请用 patch()，
     * 否则边缘会离开表面形成"浮片"。
     *
     * @param radius 三轴半径（同时作为节点缩放）
     */
    decal(parent: Node, name: string, radius: [number, number, number], style: PartStyle,
          pos: Vec3, rot?: Vec3): Node {
        const softStyle: PartStyle = {
            ...style, outline: 0, fur: 0, spec: 0, flat: true, blend: true,
            soft: style.soft ?? [0.55, 1.0],
        }
        return this.surf(parent, name, radialUV(ellipsoid(1, 1, 1, 24, 16)), softStyle, pos,
            { rot, scale: new Vec3(radius[0], radius[1], radius[2]) })
    }

    /**
     * 球面贴花（贴合曲面的软色斑 / 亮片）：腮红、眼球高光。
     *
     * 几何与目标曲面同心、半径按 inflate 略放大，因此完全贴合曲率；
     * 边缘柔和度由 uv.x 的径向衰减（style.soft）控制。
     *
     * @param dir 片中心方向（球心指向片中心，局部空间）
     * @param angleU/angleV 水平/垂直张角（弧度）
     * @param radii 目标椭球三轴半径（如眼珠、头部）
     * @param inflate 相对目标表面的外扩比例（1.004 ≈ 贴合不穿插）
     */
    patch(parent: Node, name: string, dir: P3, angleU: number, angleV: number,
          radii: [number, number, number], style: PartStyle, pos: Vec3, inflate = 1.006): Node {
        const geometry = spherePatch(dir, angleU, angleV,
            [radii[0] * inflate, radii[1] * inflate, radii[2] * inflate])
        return this.surf(parent, name, geometry, { ...style, outline: 0, fur: 0, spec: 0 }, pos)
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

    /**
     * 更新已构建部件的单个材质属性。
     *
     * 用于需要动态调整的烘焙参数（如腮红强度随情绪变化）。
     */
    setPartProperty(node: Node, name: string, value: Color | Vec4): void {
        const model = node.components.find(
            c => (c as unknown as PetModelLike).mesh !== undefined,
        ) as unknown as PetModelLike | null
        if (!model || !model.material) {
            return
        }
        try {
            model.material.setProperty(name, value)
        } catch (e) {
            // 材质不可写：忽略（不阻塞主流程）
        }
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
