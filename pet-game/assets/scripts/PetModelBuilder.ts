import { Color, Node, Vec3 } from 'cc'
import { PartStyle, PetBuilderKit } from './PetBuilderKit'
import { PetPalette, shift } from './PetGameTheme'
import { buildCatPet } from './PetCatBuilder'
import { ellipsoid, lathe, profileFrom, sweep } from './PetMeshFactory'

/**
 * Q 版宠物 3D 造型（视觉重构 v5）。
 *
 * 现状：**只保留一只猫**。之前 DOG / RABBIT / HAMSTER / TURTLE / PIG / FOX / PANDA / WILD
 * 的造型，以及那套"按 SpeciesSpec 分支拼零件"的通用构建器（buildFace / buildEars /
 * buildTail / buildShell / buildLegs / buildSprawlLegs …）已全部删除 —— 它们都是
 * 球体拼装思路的产物，完成度不够，留着只会拖住造型语言。
 *
 * 猫的造型在 PetCatBuilder.ts：一体连续曲面（lathe 旋转体 + sweep 扫掠管）、
 * 五官贴合面部曲率、色区用着色器柔化。本文件只保留三样东西：
 *  1) PetRig —— 动画层与宿主依赖的骨架契约（不要改动字段语义）；
 *  2) buildPet —— 唯一的入口，直接构建猫；
 *  3) buildAccessory —— 铃铛/领结/眼镜/围巾（换肤与 accessoryKey 能力保留）。
 *
 * 尺寸约定：脚底 y = 0，含耳总高约 1.5（房间/HUD 无需改动）。
 */

/** 宠物骨架引用（PetAnimations 与 PetGameRoot 通过它驱动所有演出） */
export interface PetRig {
    /** 整体根（跳跃/转圈/整体缩放） */
    root: Node
    /** 躯干（pivot 在身体中心：呼吸时从中心膨胀） */
    body: Node
    /** 头（pivot 在脖颈：点头/歪头带动五官） */
    head: Node
    /** 尾巴 */
    tail: Node
    /** 双耳（[左, 右]） */
    ears: Node[]
    /** 双耳初始欧拉角（动画复位基准） */
    earBase: Vec3[]
    /** 眼睛组（眨眼/惊喜缩放） */
    eyeGroups: Node[]
    /** 高光组（视线偏移：高光在眼珠上滑动，比移动整颗眼珠更自然） */
    pupils: Node[]
    /** 嘴部变体 */
    mouth: { smile: Node; open: Node; sad: Node }
    /** 腮红（情绪加深/变淡） */
    blush: Node[]
    /** 呆毛（随风/随呼吸微摆） */
    tuft: Node
    /** 地面软影（固定在房间层，跳跃时缩小变淡） */
    shadow: Node
    /** 配饰节点（可能为 null） */
    accessory: Node | null
    /**
     * 高光基准（视线驱动基准）：
     *  - slide 模式（旧造型）：pupil 节点的局部坐标基准（z 必须保持，否则高光会缩回眼珠内部）；
     *  - orbit 模式（当前猫）：pupil 节点的欧拉角基准（高光是贴合眼球的球面片，绕眼心旋转才贴合）。
     */
    pupilBase: Vec3
    /** 高光视线驱动方式（缺省 = slide） */
    pupilDrive?: 'slide' | 'orbit'
    /** 腮红强度更新（把腮红烘焙进头部材质，用材质参数而不是节点缩放来调节） */
    blushTint?: (strength: number) => void
    /** 腮红基准缩放（当前猫为 1，仅做整体强度变化） */
    blushBase?: Vec3
    /** 基准坐标（动画复位用） */
    basePos: Vec3
    bodyBase: Vec3
    headBase: Vec3
    tailBase: Vec3
    /** 头部初始朝向（点头/歪头叠加以它为基准） */
    headBaseRot: Vec3
    /** 尾根初始朝向（摆动叠加以它为基准） */
    tailBaseRot: Vec3
    /** 特效锚点（相对宠物根节点的本地坐标：头顶上方 / 嘴边） */
    markers: { head: Vec3; mouth: Vec3 }
}

/** 物种造型规格：目前只剩猫，只保留配饰用得到的两项材质参数 */
export interface SpeciesSpec {
    /** 绒光强度（毛绒 0.18-0.24；光滑 0.04-0.08） */
    fur: number
    /** 高光强度（光滑 0.20-0.30；毛绒 0.04-0.08） */
    spec: number
}

const SPECS: Record<string, SpeciesSpec> = {
    CAT: { fur: 0.21, spec: 0.05 },
}

/**
 * 构建宠物（当前只有猫；species 参数保留是为了不破坏宿主侧传参）。
 *
 * 传入其它物种键时一律回落到猫 —— 造型只做一只，先把这一只做到位。
 */
export function buildPet(petParent: Node, groundParent: Node, kit: PetBuilderKit,
                         species: string, palette: PetPalette, accessoryKey: string): PetRig {
    const spec = SPECS[species] || SPECS.CAT
    return buildCatPet(petParent, groundParent, kit, palette, accessoryKey,
        (parent, key, collarY) => buildAccessory(parent, kit, key, palette, spec, collarY))
}

/**
 * 配饰：铃铛 / 领结 / 眼镜 / 围巾（挂在 root，不随呼吸缩放）。
 *
 * @param collarY 项圈/围巾所在的颈部高度（不同造型语言的脖子位置不同，由调用方给出）
 */
export function buildAccessory(parent: Node, kit: PetBuilderKit, key: string,
                               p: PetPalette, spec: SpeciesSpec, collarY = 0.66): Node | null {
    if (!key || key === 'none') {
        return null
    }
    const root = kit.make3dNode(parent, 'Accessory', new Vec3(0, 0, 0))
    const strapColor = shift(p.dark, -0.10)
    const strap: PartStyle = {
        color: strapColor, shade: shift(strapColor, -0.24), outline: 0.004,
        fur: spec.fur, spec: spec.spec,
    }
    switch (key) {
        case 'bell': {
            const collarColor = new Color().fromHEX('#D65860')
            kit.surf(root, 'Collar', lathe(profileFrom([
                [0.30, -0.03], [0.335, -0.01], [0.345, 0.0], [0.335, 0.012], [0.30, 0.028],
            ], 3), 28), {
                color: collarColor, shade: shift(collarColor, -0.2),
                outline: 0.006, fur: 0.08, spec: 0.2,
            }, new Vec3(0, collarY, 0.0))
            const bellColor = new Color().fromHEX('#FFC854')
            kit.surf(root, 'Bell', ellipsoid(0.076, 0.078, 0.072, 20, 14), {
                color: bellColor, shade: shift(bellColor, -0.22),
                outline: 0.005, fur: 0.06, spec: 0.34, specSharp: 40,
            }, new Vec3(0, collarY - 0.085, 0.285))
            kit.surf(root, 'Clapper', ellipsoid(0.026, 0.026, 0.026, 14, 10), {
                color: new Color().fromHEX('#96682A'), shade: new Color().fromHEX('#7A5220'),
                outline: 0, fur: 0, spec: 0.2,
            }, new Vec3(0, collarY - 0.135, 0.30))
            break
        }
        case 'bowtie': {
            const pink = new Color().fromHEX('#FF84A8')
            const bowStyle: PartStyle = { color: pink, shade: shift(pink, -0.2), outline: 0.005, fur: 0.06, spec: 0.24 }
            kit.surf(root, 'Collar', lathe(profileFrom([
                [0.30, -0.026], [0.335, -0.008], [0.345, 0.0], [0.335, 0.010], [0.30, 0.024],
            ], 3), 28), strap, new Vec3(0, collarY, 0.0))
            for (const side of [-1, 1]) {
                kit.surf(root, 'Bow', ellipsoid(0.085, 0.062, 0.042, 18, 12), bowStyle,
                    new Vec3(0.105 * side, collarY - 0.045, 0.275),
                    { rot: new Vec3(0, 0, 22 * -side) })
            }
            kit.surf(root, 'Knot', ellipsoid(0.042, 0.042, 0.040, 16, 12), bowStyle,
                new Vec3(0, collarY - 0.045, 0.295))
            break
        }
        case 'glasses': {
            const frameColor = new Color().fromHEX('#60525E')
            const frame: PartStyle = {
                color: frameColor, shade: shift(frameColor, -0.2),
                outline: 0.004, fur: 0, spec: 0.3,
            }
            const eyeY = collarY + 0.40
            for (const side of [-1, 1]) {
                kit.torusPart(root, 'Lens', 0.135, 0.020, frame, new Vec3(0.205 * side, eyeY, 0.42))
            }
            kit.capPart(root, 'Bridge', 0.016, 0.10, frame, new Vec3(0, eyeY + 0.02, 0.44),
                { rot: new Vec3(0, 0, 90) })
            break
        }
        case 'scarf': {
            const blue = new Color().fromHEX('#7ABEFA')
            const scarf: PartStyle = { color: blue, shade: shift(blue, -0.2), outline: 0.006, fur: 0.12, spec: 0.14 }
            kit.surf(root, 'Wrap', lathe(profileFrom([
                [0.30, -0.075], [0.355, -0.05], [0.375, 0.0], [0.35, 0.05], [0.30, 0.075],
            ], 3), 28), scarf, new Vec3(0, collarY, 0.0))
            kit.surf(root, 'End', sweep([
                { p: [0.24, collarY - 0.02, 0.19], r: 0.062, sx: 0.55, sy: 1.0 },
                { p: [0.29, collarY - 0.20, 0.23], r: 0.058, sx: 0.5, sy: 1.0 },
                { p: [0.30, collarY - 0.34, 0.24], r: 0.030, sx: 0.55, sy: 0.95 },
            ], 16), scarf, new Vec3(0, 0, 0))
            break
        }
        default:
            break
    }
    return root
}

/** 供外部判断某物种是否为"光滑材质"（用于宿主侧的表现微调；当前只有毛绒猫） */
export function isGlossySpecies(species: string): boolean {
    const spec = SPECS[species]
    return !!spec && spec.spec > 0.15
}
