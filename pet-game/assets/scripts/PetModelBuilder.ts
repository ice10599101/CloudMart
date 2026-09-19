import { Color, Node, Vec3 } from 'cc'
import { PartStyle, PetBuilderKit } from './PetBuilderKit'
import { PetPalette, shift } from './PetGameTheme'
import {
    Geo, ellipsoid, lathe, profileFrom, sweep, transformed,
} from './PetMeshFactory'

/**
 * Q 版宠物 3D 造型（视觉重构 v4）—— 全部由手写连续曲面网格构建。
 *
 * 参考图分析（6 种物种的共同视觉语言）：
 *  - 头身比 ≈ 1 : 1（头几乎与身体等大），脸宽大于脸高，下巴圆润；
 *  - 眼睛极大（直径约占脸宽 25%），深色眼珠 + 上眼睑睫毛弧 + 1 大 1 小白色高光；
 *  - 鼻子极小、位置贴近嘴；嘴是一条很短的小弧；
 *  - 四肢短粗、末端有 3 个圆趾；耳/尾按物种区分；
 *  - 材质分两类：毛绒（猫/狗/兔/仓鼠：高绒光 + 低高光）与光滑（龟/猪：低绒光 + 强高光）。
 *
 * 实现要点：
 *  - 躯干/头/四肢用 lathe（旋转体放样）生成，轮廓由 profileFrom 样条插值，
 *    每一件本身就是连续光滑曲面（剪影完整、法线连续，不再有"球体拼装"的接缝）；
 *  - 耳朵/尾巴用 sweep（沿路径扫掠扁椭圆截面），端点收口为圆头；
 *  - 五官全部挂在 head 节点下（pivot 在脖颈），摇头/点头/呼吸时整体联动。
 *
 * 尺寸约定：脚底 y = 0，头顶约 y = 1.6（与原版尺度一致，房间/HUD 无需改动）。
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
    /** 双耳（[左, 右]；乌龟等无耳物种为空数组） */
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
    /** 高光基准局部坐标（视线偏移基准；z 必须保持，否则高光会缩回眼珠内部） */
    pupilBase: Vec3
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

/** 物种造型规格 */
interface SpeciesSpec {
    /** 耳形（none = 无外耳，如乌龟） */
    ear: 'cat' | 'dogDown' | 'rabbit' | 'round' | 'pig' | 'none'
    /** 尾形 */
    tail: 'curl' | 'up' | 'puff' | 'stub' | 'cone' | 'screw'
    /** 龟壳 */
    shell: boolean
    /** 吻部（muzzle = 口鼻凸起；pig = 猪鼻；none = 无） */
    snout: 'muzzle' | 'pig' | 'none'
    /** 头顶呆毛 */
    tuft: boolean
    /** 腮红 */
    blush: boolean
    /** 绒光强度（毛绒物种 0.18-0.24；光滑物种 0.04-0.08） */
    fur: number
    /** 高光强度（光滑物种 0.20-0.30；毛绒物种 0.04-0.08） */
    spec: number
    /** 躯干三轴缩放 */
    bodyScale: [number, number, number]
    /** 头部三轴缩放 */
    headScale: [number, number, number]
    /** 头节点相对躯干中心的高度（乌龟需要抬更高） */
    headLift: number
    /** 头节点前伸（相对躯干中心） */
    headForward: number
    /** 眼珠半径 */
    eyeR: number
    /** 眼睛位置（相对头中心）；x 需保证两眼内缘有留白（参考图眼距较宽） */
    eye: [number, number, number]
    /** 眼珠颜色 */
    eyeColor: string
    /** 内耳色（缺省由 palette 推导） */
    innerEar?: string
    /** 鼻色（缺省由 palette 推导） */
    nose?: string
    /** 坐姿（后腿收起、身体更低更圆） */
    sit: boolean
    /** 腿型：直立 / 向两侧摊开 */
    leg: 'stand' | 'sprawl'
}

const SPECS: Record<string, SpeciesSpec> = {
    CAT: {
        ear: 'cat', tail: 'curl', shell: false, snout: 'muzzle', tuft: true, blush: true,
        fur: 0.21, spec: 0.05,
        bodyScale: [1, 1, 0.98], headScale: [1, 0.97, 0.96],
        headLift: 0.60, headForward: 0.02,
        eyeR: 0.121, eye: [0.228, 0.085, 0.368], eyeColor: '#241C26',
        innerEar: '#E6C4CE', sit: true, leg: 'stand',
    },
    DOG: {
        ear: 'dogDown', tail: 'up', shell: false, snout: 'muzzle', tuft: true, blush: true,
        fur: 0.20, spec: 0.06,
        bodyScale: [0.98, 1.02, 0.98], headScale: [1, 1, 0.98],
        headLift: 0.60, headForward: 0.02,
        eyeR: 0.118, eye: [0.222, 0.09, 0.368], eyeColor: '#3A2A20',
        nose: '#F09AA0', sit: false, leg: 'stand',
    },
    RABBIT: {
        ear: 'rabbit', tail: 'puff', shell: false, snout: 'muzzle', tuft: true, blush: true,
        fur: 0.21, spec: 0.05,
        bodyScale: [1, 1.05, 0.98], headScale: [0.99, 1, 0.96],
        headLift: 0.60, headForward: 0.02,
        eyeR: 0.120, eye: [0.220, 0.09, 0.368], eyeColor: '#3A2A20',
        innerEar: '#F4B8C6', nose: '#F0A0B0', sit: false, leg: 'stand',
    },
    HAMSTER: {
        ear: 'round', tail: 'stub', shell: false, snout: 'muzzle', tuft: false, blush: true,
        fur: 0.23, spec: 0.05,
        bodyScale: [1.07, 1.02, 1], headScale: [1.05, 0.99, 0.98],
        headLift: 0.58, headForward: 0.02,
        eyeR: 0.104, eye: [0.214, 0.075, 0.374], eyeColor: '#221C24',
        innerEar: '#F0C0C8', nose: '#F0A8B0', sit: true, leg: 'stand',
    },
    TURTLE: {
        ear: 'none', tail: 'cone', shell: true, snout: 'none', tuft: false, blush: false,
        fur: 0.06, spec: 0.26,
        // 头更小、更低、更靠前（从壳前方探出）；壳更大更高（参考图：壳才是主体的视觉重心）
        bodyScale: [1.04, 0.86, 1.06], headScale: [0.78, 0.82, 0.80],
        headLift: 0.40, headForward: 0.60,
        eyeR: 0.096, eye: [0.190, 0.070, 0.318], eyeColor: '#2A2430',
        nose: '#F0A0AC', sit: false, leg: 'sprawl',
    },
    PIG: {
        ear: 'pig', tail: 'screw', shell: false, snout: 'pig', tuft: false, blush: true,
        fur: 0.07, spec: 0.22,
        bodyScale: [1.06, 1, 1], headScale: [1.04, 1, 0.97],
        headLift: 0.60, headForward: 0.02,
        eyeR: 0.101, eye: [0.211, 0.085, 0.378], eyeColor: '#2A2230',
        nose: '#F09AA8', sit: true, leg: 'stand',
    },
    // 旧物种键兼容（沿用相近的形态）
    FOX: {
        ear: 'cat', tail: 'curl', shell: false, snout: 'muzzle', tuft: true, blush: true,
        fur: 0.21, spec: 0.05,
        bodyScale: [1, 1, 0.98], headScale: [1, 0.98, 0.96],
        headLift: 0.60, headForward: 0.02,
        eyeR: 0.118, eye: [0.222, 0.085, 0.368], eyeColor: '#3A2A20',
        innerEar: '#F2C4C0', sit: true, leg: 'stand',
    },
    PANDA: {
        ear: 'round', tail: 'stub', shell: false, snout: 'muzzle', tuft: false, blush: true,
        fur: 0.23, spec: 0.05,
        bodyScale: [1.06, 1.02, 1], headScale: [1.04, 1, 0.98],
        headLift: 0.59, headForward: 0.02,
        eyeR: 0.113, eye: [0.220, 0.08, 0.371], eyeColor: '#241C26',
        innerEar: '#4A4050', sit: true, leg: 'stand',
    },
    WILD: {
        ear: 'dogDown', tail: 'up', shell: false, snout: 'muzzle', tuft: true, blush: true,
        fur: 0.20, spec: 0.06,
        bodyScale: [1, 1, 0.98], headScale: [1, 1, 0.98],
        headLift: 0.60, headForward: 0.02,
        eyeR: 0.117, eye: [0.222, 0.088, 0.368], eyeColor: '#33261E',
        sit: false, leg: 'stand',
    },
}

/** 造型尺寸基准（脚底 y = 0） */
const RIG = {
    /** 躯干中心高度 */
    bodyY: 0.46,
    /** 头中心相对头节点的高度 */
    headCenter: 0.02,
} as const

/** 躯干侧轮廓控制点（[半径, 相对躯干中心的 y]，从下到上）：梨形、底部圆润 */
const TORSO_CTRL: Array<[number, number]> = [
    [0.004, -0.44], [0.22, -0.42], [0.355, -0.33], [0.425, -0.19], [0.445, -0.02],
    [0.435, 0.14], [0.375, 0.28], [0.255, 0.38], [0.12, 0.44], [0.004, 0.46],
]

/** 头部侧轮廓控制点：接近球体（上下都收），脸颊略丰满 —— 避免中段平直造成的"方头"感 */
const HEAD_CTRL: Array<[number, number]> = [
    [0.004, -0.48], [0.16, -0.452], [0.30, -0.378], [0.415, -0.265], [0.478, -0.13],
    [0.50, 0.005], [0.487, 0.14], [0.44, 0.275], [0.355, 0.375], [0.205, 0.45], [0.004, 0.475],
]

/** 直立腿侧轮廓（上粗下细、底部圆头） */
const LEG_CTRL: Array<[number, number]> = [
    [0.004, -0.075], [0.105, -0.055], [0.135, 0.0], [0.142, 0.09], [0.148, 0.22], [0.152, 0.34],
]

/**
 * 构建宠物整体。
 *
 * @param petParent 宠物根节点（调用方负责在场景中定位；整体动画作用于它）
 * @param groundParent 地面层节点（影子挂这里，宠物跳起时影子不跟随）
 * @param species 物种键（CAT/DOG/RABBIT/HAMSTER/TURTLE/PIG/...）
 * @param palette 调色板（PetGameTheme 解析）
 * @param accessoryKey 配饰键（none/bell/bowtie/glasses/scarf）
 */
export function buildPet(petParent: Node, groundParent: Node, kit: PetBuilderKit,
                         species: string, palette: PetPalette, accessoryKey: string): PetRig {
    const spec = SPECS[species] || SPECS.CAT
    const p = palette

    // 材质风格：主体 / 深色 / 柔软（肚皮口鼻）/ 爪垫
    const main = makeStyle(p.body, spec, 0.006)
    const dark = makeStyle(p.dark, spec, 0.006)
    const soft = makeStyle(p.belly, spec, 0.005, 1.12)
    const paw = makeStyle(p.paw, spec, 0.004)

    // ---- 躯干：旋转体（pivot 在身体中心，呼吸从中心膨胀） ----
    const bodyY = spec.sit ? RIG.bodyY - 0.05 : RIG.bodyY
    const body = kit.make3dNode(petParent, 'Body', new Vec3(0, bodyY, 0))
    const torsoProfile = profileFrom(TORSO_CTRL, 5)
    // 躯干略宽于头部：从头正面看，身体两侧要"露出来"（参考图坐姿宠物的胸腹比头更饱满）
    kit.surf(body, 'Torso',
        transformed(lathe(torsoProfile, 34),
            { scale: [spec.bodyScale[0] * 1.09, spec.bodyScale[1], spec.bodyScale[2] * 1.02] }), main, new Vec3(0, 0, 0))
    // 胸腹亮色（柔软绒毛/浅色肚皮，略微凸出形成层次）
    kit.surf(body, 'Belly',
        transformed(ellipsoid(0.30, 0.30, 0.26, 26, 16),
            { pos: [0, -0.06, 0.20], scale: [0.96, 0.94, 0.52] }), soft, new Vec3(0, 0, 0))
    // 颈胸过渡：填充头与躯干之间的"葫芦腰"，让轮廓连成一体（Q 版宠物不应看到明显接缝）
    kit.surf(body, 'Chest',
        transformed(ellipsoid(0.425, 0.27, 0.405, 26, 16),
            { pos: [0, 0.27, 0.01] }), main, new Vec3(0, 0, 0))

    // ---- 龟壳（乌龟专属：覆盖背部） ----
    let tailBase = new Vec3(0, 0.40, -0.34)
    if (spec.shell) {
        buildShell(body, kit, p)
        tailBase = new Vec3(0, 0.06, -0.30)
    }

    // ---- 四肢 ----
    if (spec.leg === 'sprawl') {
        buildSprawlLegs(petParent, kit, main, paw)
    } else {
        buildLegs(petParent, kit, main, paw)
    }

    // ---- 尾巴（pivot 在臀后，摆动以根部为轴） ----
    const tail = kit.make3dNode(petParent, 'Tail', tailBase)
    buildTail(tail, kit, spec, main, soft)

    // ---- 头部（pivot 在脖颈；挂在躯干下，呼吸/压扁时头部天然联动） ----
    const head = kit.make3dNode(body, 'Head',
        new Vec3(0, spec.headLift - RIG.bodyY, spec.headForward))
    const headProfile = profileFrom(HEAD_CTRL, 5)
    // 头部略微收窄（x 0.97）：与加宽的躯干形成"头略小于身"的幼态比例，避免身体被头部完全遮挡
    kit.surf(head, 'Skull',
        transformed(lathe(headProfile, 34),
            { scale: [spec.headScale[0] * 0.97, spec.headScale[1], spec.headScale[2]] }), main, new Vec3(0, RIG.headCenter, 0))

    // ---- 五官 ----
    const face = buildFace(head, kit, spec, p, soft, dark)
    const earInfo = buildEars(head, kit, spec, main, p)
    const tuft = buildTuft(head, kit, spec, main)

    // ---- 配饰 ----
    const accessory = buildAccessory(petParent, kit, accessoryKey, p, spec)

    // ---- 地面软影（挂房间层：宠物跳起时影子留在地面） ----
    const shadow = kit.cylPart(groundParent, 'PetShadow', 0.58, 0.64, 0.012, {
        color: new Color(52, 38, 60, 255), shade: new Color(52, 38, 60, 255),
        glow: true, alpha: 86, outline: 0,
    }, new Vec3(petParent.position.x, 0.008, petParent.position.z + 0.05))

    const headTop = bodyY + spec.headLift + RIG.headCenter + 0.54

    return {
        root: petParent,
        body,
        head,
        tail,
        ears: earInfo.nodes,
        earBase: earInfo.bases,
        eyeGroups: face.eyeGroups,
        pupils: face.pupils,
        mouth: face.mouth,
        blush: face.blush,
        tuft,
        shadow,
        accessory,
        pupilBase: face.pupilBase,
        basePos: petParent.position.clone(),
        bodyBase: body.position.clone(),
        headBase: head.position.clone(),
        tailBase: tail.position.clone(),
        headBaseRot: head.eulerAngles.clone(),
        tailBaseRot: tail.eulerAngles.clone(),
        markers: {
            head: new Vec3(0, headTop, 0.06),
            mouth: new Vec3(0, headTop - 0.60, 0.34),
        },
    }
}

/** 材质风格构造：主色 + 物种绒光/高光 */
function makeStyle(color: Color, spec: SpeciesSpec, outline: number, furBoost = 1): PartStyle {
    return {
        color,
        shade: shift(color, -0.28),
        outline,
        fur: spec.fur * furBoost,
        spec: spec.spec,
    }
}

/** 眼睛 / 鼻 / 嘴 / 腮红 */
function buildFace(head: Node, kit: PetBuilderKit, spec: SpeciesSpec, p: PetPalette,
                   soft: PartStyle, dark: PartStyle):
        { eyeGroups: Node[]; pupils: Node[]; mouth: { smile: Node; open: Node; sad: Node }; blush: Node[]; pupilBase: Vec3 } {
    const headY = RIG.headCenter
    const eyeGroups: Node[] = []
    const pupils: Node[] = []
    const r = spec.eyeR
    const eyeStyle: PartStyle = {
        color: new Color().fromHEX(spec.eyeColor),
        shade: shift(new Color().fromHEX(spec.eyeColor), 0.06),
        outline: 0, fur: 0, spec: 0.32, specSharp: 64,
    }
    const hiStyle: PartStyle = {
        color: new Color(255, 255, 255, 255), shade: new Color(255, 255, 255, 255),
        outline: 0, glow: true, fur: 0, spec: 0,
    }
    const lashStyle: PartStyle = {
        color: new Color().fromHEX(spec.eyeColor), shade: new Color().fromHEX(spec.eyeColor),
        outline: 0, glow: true, fur: 0, spec: 0,
    }
    /** 高光所在平面（略小于眼珠 z 半径，保证高光球"贴"在眼珠表面而不是浮在空中） */
    const glintZ = r * 0.92

    for (const side of [-1, 1]) {
        const group = kit.make3dNode(head, 'Eye', new Vec3(spec.eye[0] * side, headY + spec.eye[1], spec.eye[2]),
            { rot: new Vec3(0, 9 * side, 0) })
        // 眼珠：略竖长的椭球，凸出脸颊（参考图眼睛是"凸出来的大黑豆"）
        kit.surf(group, 'Ball', ellipsoid(r, r * 1.08, r * 0.94, 26, 18), eyeStyle, new Vec3(0, 0, 0))
        // 上眼睑线：极细的深色弧，贴在眼珠上缘（参考图是"上眼睑"而非粗眉毛）
        const lashNodes: Array<[number, number, number]> = []
        for (let i = 0; i <= 6; i++) {
            const t = i / 6
            const a = (t - 0.5) * 2
            lashNodes.push([
                a * r * 0.96,
                r * (0.56 + 0.22 * (1 - a * a)),
                r * (0.70 - 0.42 * a * a),
            ])
        }
        kit.surf(group, 'Lash', sweep(lashNodes.map(n => ({ p: n, r: r * 0.075 })), 8), lashStyle, new Vec3(0, 0, 0))
        // 高光组（视线偏移：高光在眼珠表面滑动；尺寸约眼珠 1/4，位置偏外上）
        const pupil = kit.make3dNode(group, 'Glint', new Vec3(0, 0, glintZ))
        kit.surf(pupil, 'Big', ellipsoid(r * 0.25, r * 0.24, r * 0.15, 16, 12), hiStyle,
            new Vec3(r * 0.26, r * 0.30, 0))
        kit.surf(pupil, 'Small', ellipsoid(r * 0.125, r * 0.12, r * 0.10, 14, 10), hiStyle,
            new Vec3(-r * 0.30, -r * 0.26, 0))
        eyeGroups.push(group)
        pupils.push(pupil)
    }

    // ---- 口鼻（muzzle：柔软色的椭球凸起） ----
    const muzzleY = headY - 0.205
    if (spec.snout === 'muzzle') {
        kit.surf(head, 'Muzzle', ellipsoid(0.185, 0.135, 0.145, 24, 16), soft, new Vec3(0, muzzleY, 0.335))
    }

    // ---- 鼻子 ----
    const noseHex = spec.nose || (spec.snout === 'pig' ? '#F09AA8' : '#C98A96')
    const noseColor = new Color().fromHEX(noseHex)
    const noseStyle: PartStyle = {
        color: noseColor, shade: shift(noseColor, -0.14),
        outline: 0, fur: spec.fur * 0.6, spec: spec.spec * 1.4, specSharp: 40,
    }
    if (spec.snout === 'pig') {
        // 猪鼻：扁平大椭圆 + 两个鼻孔
        kit.surf(head, 'Snout', ellipsoid(0.115, 0.088, 0.07, 24, 16), noseStyle,
            new Vec3(0, headY - 0.13, 0.40))
        const holeColor = shift(noseColor, -0.34)
        const holeStyle: PartStyle = {
            color: holeColor, shade: holeColor, outline: 0, glow: true, fur: 0, spec: 0,
        }
        for (const side of [-1, 1]) {
            kit.surf(head, 'Nostril', ellipsoid(0.026, 0.040, 0.022, 12, 8), holeStyle,
                new Vec3(0.046 * side, headY - 0.132, 0.462))
        }
    } else if (spec.snout === 'muzzle') {
        // 猫/狗/兔/仓鼠：小圆鼻，贴在口鼻上沿
        kit.surf(head, 'Nose', ellipsoid(0.052, 0.040, 0.040, 20, 14), noseStyle,
            new Vec3(0, headY - 0.145, 0.455))
    } else {
        // 乌龟：极小的鼻点
        kit.surf(head, 'Nose', ellipsoid(0.030, 0.026, 0.026, 16, 12), noseStyle,
            new Vec3(0, headY - 0.03, 0.355))
    }

    // ---- 嘴：三个变体（smile 常驻 / open 吃东西 / sad 难过） ----
    const mouthColor = shift(p.dark, -0.30)
    const lineStyle: PartStyle = {
        color: mouthColor, shade: mouthColor, outline: 0, glow: true, fur: 0, spec: 0,
    }
    const tongueColor = new Color().fromHEX('#F08A9C')
    const tongueStyle: PartStyle = {
        color: tongueColor, shade: shift(tongueColor, -0.18), outline: 0, fur: 0.05, spec: 0.2,
    }
    // 嘴要贴在口鼻曲面**之外**（否则会陷进 muzzle 里看不见）
    const mouthY = headY - (spec.snout === 'pig' ? 0.235 : spec.snout === 'muzzle' ? 0.295 : 0.115)
    const mouthZ = spec.snout === 'none' ? 0.348 : 0.462
    const mouthRoot = kit.make3dNode(head, 'Mouth', new Vec3(0, mouthY, mouthZ))

    // 微笑：一条细弧（sweep 沿弧线扫掠，比排小球更精致）
    const smile = kit.make3dNode(mouthRoot, 'Smile', new Vec3(0, 0, 0))
    const smileNodes: Array<[number, number, number]> = []
    for (let i = 0; i <= 8; i++) {
        const t = i / 8
        const a = (t - 0.5) * 2
        smileNodes.push([a * 0.078, -0.014 * (1 - a * a), -Math.abs(a) * 0.020])
    }
    kit.surf(smile, 'Arc', sweep(smileNodes.map(n => ({ p: n, r: 0.015 })), 10), lineStyle, new Vec3(0, 0, 0))

    // 张嘴：小口腔 + 舌头
    const openMouth = kit.make3dNode(mouthRoot, 'Open', new Vec3(0, 0.01, 0.005))
    openMouth.active = false
    const cavity = new Color().fromHEX('#8C4658')
    kit.surf(openMouth, 'Cavity', ellipsoid(0.075, 0.062, 0.05, 20, 14), {
        color: cavity, shade: shift(cavity, -0.2), outline: 0, fur: 0, spec: 0.1,
    }, new Vec3(0, 0, 0))
    kit.surf(openMouth, 'Tongue', ellipsoid(0.046, 0.032, 0.030, 18, 12), tongueStyle,
        new Vec3(0, -0.026, 0.028))

    // 难过：下垂弧
    const sadMouth = kit.make3dNode(mouthRoot, 'Sad', new Vec3(0, 0.012, 0))
    sadMouth.active = false
    const sadNodes: Array<[number, number, number]> = []
    for (let i = 0; i <= 8; i++) {
        const t = i / 8
        const a = (t - 0.5) * 2
        sadNodes.push([a * 0.064, 0.022 * (1 - a * a), -Math.abs(a) * 0.018])
    }
    kit.surf(sadMouth, 'Arc', sweep(sadNodes.map(n => ({ p: n, r: 0.014 })), 10), lineStyle, new Vec3(0, 0, 0))

    // ---- 腮红（正对相机的柔光粉斑） ----
    const blush: Node[] = []
    if (spec.blush) {
        const blushStyle: PartStyle = {
            color: p.blush, shade: p.blush, alpha: 118, outline: 0, glow: true, fur: 0, spec: 0,
        }
        for (const side of [-1, 1]) {
            blush.push(kit.surf(head, 'Blush', ellipsoid(0.15, 0.10, 0.07, 20, 14), blushStyle,
                new Vec3(0.335 * side, headY - 0.105, 0.225),
                { rot: new Vec3(0, 26 * side, 0), scale: new Vec3(0.95, 0.6, 0.3) }))
        }
    }
    void dark

    return {
        eyeGroups,
        pupils,
        mouth: { smile, open: openMouth, sad: sadMouth },
        blush,
        pupilBase: new Vec3(0, 0, glintZ),
    }
}

/** 耳朵：按物种给出外耳 + 内耳；返回节点与初始倾角（动画复位基准） */
function buildEars(head: Node, kit: PetBuilderKit, spec: SpeciesSpec, main: PartStyle,
                   p: PetPalette): { nodes: Node[]; bases: Vec3[] } {
    const nodes: Node[] = []
    const bases: Vec3[] = []
    if (spec.ear === 'none') {
        return { nodes, bases }
    }
    const innerHex = spec.innerEar || '#E8BFC8'
    const innerColor = new Color().fromHEX(innerHex)
    const innerStyle: PartStyle = {
        color: innerColor, shade: shift(innerColor, -0.12),
        outline: 0, fur: spec.fur * 1.1, spec: spec.spec,
    }

    for (const side of [-1, 1]) {
        switch (spec.ear) {
            case 'cat': {
                // 三角尖耳：根部宽、尖端收口，截面压扁成耳片（参考图猫耳高约为头高的 1/3）
                const root = kit.make3dNode(head, 'Ear', new Vec3(0.278 * side, RIG.headCenter + 0.285, -0.02),
                    { rot: new Vec3(0, 12 * side, -17 * side) })
                const path = [
                    { p: [0, 0, 0] as [number, number, number], r: 0.178, sx: 0.42, sy: 1.02 },
                    { p: [0.032, 0.175, -0.014] as [number, number, number], r: 0.115, sx: 0.38, sy: 0.98 },
                    { p: [0.078, 0.335, -0.028] as [number, number, number], r: 0.018, sx: 0.48, sy: 0.9 },
                ]
                kit.surf(root, 'Outer', sweep(path, 20), main, new Vec3(0, 0, 0))
                const innerPath = [
                    { p: [0, 0.022, 0.030] as [number, number, number], r: 0.122, sx: 0.32, sy: 0.94 },
                    { p: [0.030, 0.165, 0.018] as [number, number, number], r: 0.074, sx: 0.30, sy: 0.90 },
                    { p: [0.068, 0.285, 0.004] as [number, number, number], r: 0.014, sx: 0.38, sy: 0.86 },
                ]
                kit.surf(root, 'Inner', sweep(innerPath, 18), innerStyle, new Vec3(0, 0, 0))
                nodes.push(root)
                break
            }
            case 'dogDown': {
                // 垂耳：从耳根向下外弯的大耳片（耳根贴到头侧最外缘，避免被头部遮挡）
                const root = kit.make3dNode(head, 'Ear', new Vec3(0.355 * side, RIG.headCenter + 0.185, 0.045),
                    { rot: new Vec3(-4, 10 * side, -20 * side) })
                const path = [
                    { p: [0, 0, 0] as [number, number, number], r: 0.160, sx: 0.50, sy: 1.10 },
                    { p: [0.070, -0.185, 0.030] as [number, number, number], r: 0.148, sx: 0.44, sy: 1.04 },
                    { p: [0.130, -0.345, 0.012] as [number, number, number], r: 0.070, sx: 0.48, sy: 0.96 },
                ]
                kit.surf(root, 'Outer', sweep(path, 20), main, new Vec3(0, 0, 0))
                const innerPath = [
                    { p: [0.012, -0.045, 0.062] as [number, number, number], r: 0.114, sx: 0.34, sy: 1.02 },
                    { p: [0.076, -0.205, 0.070] as [number, number, number], r: 0.098, sx: 0.32, sy: 0.96 },
                    { p: [0.122, -0.318, 0.048] as [number, number, number], r: 0.044, sx: 0.38, sy: 0.92 },
                ]
                kit.surf(root, 'Inner', sweep(innerPath, 18), innerStyle, new Vec3(0, 0, 0))
                nodes.push(root)
                break
            }
            case 'rabbit': {
                // 长立耳：细长、末端圆头，略外张
                const root = kit.make3dNode(head, 'Ear', new Vec3(0.145 * side, RIG.headCenter + 0.36, -0.02),
                    { rot: new Vec3(-3, 6 * side, -8 * side) })
                const path = [
                    { p: [0, 0, 0] as [number, number, number], r: 0.112, sx: 0.46, sy: 0.98 },
                    { p: [-0.012, 0.21, 0.008] as [number, number, number], r: 0.108, sx: 0.44, sy: 1.0 },
                    { p: [0.016, 0.42, 0.004] as [number, number, number], r: 0.094, sx: 0.44, sy: 0.96 },
                    { p: [0.056, 0.615, -0.012] as [number, number, number], r: 0.046, sx: 0.5, sy: 0.9 },
                ]
                kit.surf(root, 'Outer', sweep(path, 20), main, new Vec3(0, 0, 0))
                const innerPath = [
                    { p: [0.004, 0.045, 0.052] as [number, number, number], r: 0.072, sx: 0.34, sy: 0.94 },
                    { p: [-0.006, 0.21, 0.058] as [number, number, number], r: 0.068, sx: 0.32, sy: 0.96 },
                    { p: [0.022, 0.40, 0.052] as [number, number, number], r: 0.056, sx: 0.32, sy: 0.92 },
                    { p: [0.050, 0.55, 0.038] as [number, number, number], r: 0.028, sx: 0.36, sy: 0.86 },
                ]
                kit.surf(root, 'Inner', sweep(innerPath, 18), innerStyle, new Vec3(0, 0, 0))
                nodes.push(root)
                break
            }
            case 'round': {
                // 圆耳（仓鼠/熊猫）：扁圆片 + 粉色内耳
                const root = kit.make3dNode(head, 'Ear', new Vec3(0.30 * side, RIG.headCenter + 0.325, -0.015),
                    { rot: new Vec3(0, 20 * side, -12 * side) })
                kit.surf(root, 'Outer', ellipsoid(0.112, 0.118, 0.052, 22, 16), main, new Vec3(0, 0, 0))
                kit.surf(root, 'Inner', ellipsoid(0.074, 0.078, 0.030, 18, 12), innerStyle,
                    new Vec3(0.004, -0.002, 0.030))
                nodes.push(root)
                break
            }
            default: {
                // 猪耳：小而软的前倾耳片（耳根贴到头侧最外缘，否则会被头部完全遮住）
                const root = kit.make3dNode(head, 'Ear', new Vec3(0.425 * side, RIG.headCenter + 0.215, 0.185),
                    { rot: new Vec3(-10, 14 * side, -28 * side) })
                const path = [
                    { p: [0, 0, 0] as [number, number, number], r: 0.125, sx: 0.42, sy: 1.0 },
                    { p: [0.075, -0.105, 0.022] as [number, number, number], r: 0.106, sx: 0.38, sy: 0.96 },
                    { p: [0.145, -0.205, 0.008] as [number, number, number], r: 0.052, sx: 0.42, sy: 0.9 },
                ]
                kit.surf(root, 'Outer', sweep(path, 20), main, new Vec3(0, 0, 0))
                nodes.push(root)
                break
            }
        }
        bases.push(nodes[nodes.length - 1].eulerAngles.clone())
    }
    void p
    return { nodes, bases }
}

/** 呆毛：头顶一撮翘毛（生命力细节；摆动由动画层驱动） */
function buildTuft(head: Node, kit: PetBuilderKit, spec: SpeciesSpec, main: PartStyle): Node {
    const tuft = kit.make3dNode(head, 'Tuft', new Vec3(0.0, RIG.headCenter + 0.455, 0.03))
    if (!spec.tuft) {
        return tuft
    }
    kit.surf(tuft, 'Strand', sweep([
        { p: [0, 0, 0], r: 0.052, sx: 0.9, sy: 1.0 },
        { p: [0.030, 0.085, -0.012], r: 0.044, sx: 0.85, sy: 0.95 },
        { p: [0.086, 0.148, -0.010], r: 0.030, sx: 0.8, sy: 0.9 },
        { p: [0.140, 0.168, 0.0], r: 0.011, sx: 0.9, sy: 0.9 },
    ], 12), main, new Vec3(0, 0, 0))
    return tuft
}

/** 直立四肢：前腿两根 + 后脚两只，各带 3 个圆趾 */
function buildLegs(root: Node, kit: PetBuilderKit, main: PartStyle, paw: PartStyle): void {
    const legGeo = lathe(profileFrom(LEG_CTRL, 3), 22)
    const toeStyle: PartStyle = { ...paw, outline: 0.003 }
    for (const side of [-1, 1]) {
        // 前腿：立在躯干**前缘之外**（身体半径约 0.45，故置于 z≈0.47），正面能看到腿与脚掌
        kit.surf(root, 'FrontLeg', legGeo, main, new Vec3(0.185 * side, 0.075, 0.47))
        for (let i = -1; i <= 1; i++) {
            kit.surf(root, 'Toe', ellipsoid(0.056, 0.042, 0.062, 12, 10), toeStyle,
                new Vec3(0.185 * side + i * 0.048, 0.040, 0.535))
        }
        // 后脚：从身侧后方露出（Z 略前于尾根，避免被躯干完全吞掉）
        kit.surf(root, 'BackFoot', ellipsoid(0.118, 0.088, 0.135, 22, 16), main,
            new Vec3(0.255 * side, 0.082, -0.09))
        for (let i = -1; i <= 1; i++) {
            kit.surf(root, 'Toe', ellipsoid(0.050, 0.038, 0.056, 12, 10), toeStyle,
                new Vec3(0.255 * side + i * 0.045, 0.042, -0.012))
        }
    }
}

/** 摊开四肢（乌龟）：向两侧伸出的短粗腿 + 圆头脚 */
function buildSprawlLegs(root: Node, kit: PetBuilderKit, main: PartStyle, paw: PartStyle): void {
    for (const side of [-1, 1]) {
        // 前腿（斜向外前）：要从甲缘之外伸出，才能在正面看到
        kit.surf(root, 'FrontLeg', sweep([
            { p: [0.24 * side, 0.22, 0.16], r: 0.108, sx: 0.9, sy: 1.0 },
            { p: [0.40 * side, 0.145, 0.19], r: 0.102, sx: 0.9, sy: 1.0 },
            { p: [0.51 * side, 0.078, 0.205], r: 0.084, sx: 1.0, sy: 0.95 },
        ], 18), main, new Vec3(0, 0, 0))
        kit.surf(root, 'FrontToe', ellipsoid(0.080, 0.050, 0.090, 18, 12), paw,
            new Vec3(0.545 * side, 0.046, 0.22))
        // 后腿（斜向外后）
        kit.surf(root, 'BackLeg', sweep([
            { p: [0.24 * side, 0.21, -0.20], r: 0.102, sx: 0.9, sy: 1.0 },
            { p: [0.39 * side, 0.14, -0.24], r: 0.098, sx: 0.9, sy: 1.0 },
            { p: [0.48 * side, 0.075, -0.26], r: 0.080, sx: 1.0, sy: 0.95 },
        ], 18), main, new Vec3(0, 0, 0))
        kit.surf(root, 'BackToe', ellipsoid(0.074, 0.046, 0.084, 18, 12), paw,
            new Vec3(0.505 * side, 0.044, -0.275))
    }
}

/** 尾巴：按物种形态（挂在 tail 节点，pivot 在臀后） */
function buildTail(tail: Node, kit: PetBuilderKit, spec: SpeciesSpec,
                   main: PartStyle, soft: PartStyle): void {
    switch (spec.tail) {
        case 'curl': {
            // 猫：蓬松大尾，向身体右侧上方翘起（必须超出躯干轮廓，正面才看得到）
            kit.surf(tail, 'Tail', sweep([
                { p: [0.01, 0, 0], r: 0.095, sx: 0.95, sy: 0.95 },
                { p: [0.17, 0.135, -0.055], r: 0.116, sx: 0.98, sy: 0.95 },
                { p: [0.43, 0.255, 0.02], r: 0.096, sx: 0.98, sy: 0.92 },
                { p: [0.62, 0.365, 0.075], r: 0.052, sx: 1.0, sy: 0.9 },
            ], 20), main, new Vec3(0, 0, 0))
            break
        }
        case 'up': {
            // 狗：短而上翘的蓬松尾
            kit.surf(tail, 'Tail', sweep([
                { p: [0, 0, 0], r: 0.078, sx: 0.95, sy: 0.95 },
                { p: [0.012, 0.135, 0.015], r: 0.098, sx: 1.0, sy: 0.95 },
                { p: [0.032, 0.255, 0.10], r: 0.046, sx: 1.05, sy: 0.9 },
            ], 20), main, new Vec3(0, 0, 0))
            break
        }
        case 'puff': {
            // 兔：圆球短尾
            kit.surf(tail, 'Puff', ellipsoid(0.128, 0.132, 0.128, 24, 16), soft,
                new Vec3(0, 0.07, -0.04))
            break
        }
        case 'stub': {
            // 仓鼠/熊猫：小圆尾
            kit.surf(tail, 'Stub', ellipsoid(0.092, 0.088, 0.092, 20, 14), soft,
                new Vec3(0, 0.03, -0.06))
            break
        }
        case 'cone': {
            // 乌龟：小尖尾
            kit.surf(tail, 'Tail', sweep([
                { p: [0, 0, 0], r: 0.075, sx: 1.0, sy: 1.0 },
                { p: [0, -0.02, -0.09], r: 0.055, sx: 1.0, sy: 1.0 },
                { p: [0, -0.03, -0.17], r: 0.014, sx: 1.0, sy: 1.0 },
            ], 16), main, new Vec3(0, 0, 0))
            break
        }
        default: {
            // 猪：细卷尾（小螺旋）
            const nodes = []
            for (let i = 0; i <= 10; i++) {
                const t = i / 10
                const angle = t * Math.PI * 3.1
                nodes.push({
                    p: [Math.cos(angle) * 0.055 * t, 0.115 + t * 0.075, -0.03 - Math.sin(angle) * 0.055 * t] as [number, number, number],
                    r: 0.032 * (1 - t * 0.55), sx: 1, sy: 1,
                })
            }
            kit.surf(tail, 'Tail', sweep(nodes, 14), soft, new Vec3(0, 0, 0))
            break
        }
    }
}

/** 龟壳：背甲（带放射状盾片纹）+ 边缘环 + 腹甲 */
function buildShell(body: Node, kit: PetBuilderKit, p: PetPalette): void {
    const shellColor = p.dark
    const shellStyle: PartStyle = {
        color: shellColor, shade: shift(shellColor, -0.16), outline: 0.006,
        fur: 0.05, spec: 0.28, specSharp: 34,
    }
    const rimColor = shift(p.belly, 0.1)
    const rimStyle: PartStyle = {
        color: rimColor, shade: shift(rimColor, -0.14), outline: 0.005,
        fur: 0.06, spec: 0.22,
    }
    // 背甲：压扁的半球（甲缘外翻）；整体后移，让头能从壳前方探出
    kit.surf(body, 'Shell', transformed(lathe(profileFrom([
        [0.006, -0.11], [0.32, -0.085], [0.46, -0.02], [0.525, 0.08],
        [0.52, 0.18], [0.44, 0.255], [0.28, 0.315], [0.006, 0.34],
    ], 5), 30), { pos: [0, 0.20, -0.30], scale: [1.22, 1.34, 1.30] }), shellStyle, new Vec3(0, 0, 0))
    // 边缘环（甲缘浅色）
    kit.surf(body, 'ShellRim', transformed(lathe(profileFrom([
        [0.006, -0.06], [0.44, -0.055], [0.55, 0.005], [0.565, 0.055], [0.52, 0.09], [0.006, 0.095],
    ], 4), 30), { pos: [0, 0.115, -0.30], scale: [1.22, 1.34, 1.30] }), rimStyle, new Vec3(0, 0, 0))
    // 盾片纹：中心 + 两圈凸起（略深色小椭球排布，形成六边形块视觉）
    const tileColor = shift(shellColor, 0.07)
    const tileStyle: PartStyle = {
        color: tileColor, shade: shift(shellColor, -0.12), outline: 0.004,
        fur: 0.05, spec: 0.3, specSharp: 40,
    }
    kit.surf(body, 'TileC', ellipsoid(0.175, 0.062, 0.185, 18, 12), tileStyle, new Vec3(0, 0.56, -0.30))
    for (let i = 0; i < 6; i++) {
        const a = (i / 6) * Math.PI * 2
        kit.surf(body, 'Tile', ellipsoid(0.14, 0.056, 0.148, 16, 11), tileStyle,
            new Vec3(Math.cos(a) * 0.36, 0.50 - Math.abs(Math.sin(a)) * 0.035, -0.30 + Math.sin(a) * 0.39))
    }
    for (let i = 0; i < 8; i++) {
        const a = (i / 8) * Math.PI * 2
        kit.surf(body, 'TileEdge', ellipsoid(0.108, 0.048, 0.112, 14, 10), tileStyle,
            new Vec3(Math.cos(a) * 0.55, 0.34, -0.30 + Math.sin(a) * 0.59))
    }
    // 腹甲（浅色底）
    kit.surf(body, 'Plastron', transformed(ellipsoid(0.47, 0.15, 0.52, 26, 16),
        { pos: [0, -0.26, -0.22], scale: [1.0, 1.0, 1.06] }), rimStyle, new Vec3(0, 0, 0))
}

/** 配饰：铃铛 / 领结 / 眼镜 / 围巾（挂在 root，不随呼吸缩放） */
function buildAccessory(parent: Node, kit: PetBuilderKit, key: string,
                        p: PetPalette, spec: SpeciesSpec): Node | null {
    if (!key || key === 'none') {
        return null
    }
    const root = kit.make3dNode(parent, 'Accessory', new Vec3(0, 0, 0))
    const strapColor = shift(p.dark, -0.10)
    const strap: PartStyle = {
        color: strapColor, shade: shift(strapColor, -0.24), outline: 0.006,
        fur: spec.fur, spec: spec.spec,
    }
    const collarY = spec.shell ? 0.60 : 0.66
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

/** 供外部判断某物种是否为"光滑材质"（龟/猪），用于宿主侧的表现微调 */
export function isGlossySpecies(species: string): boolean {
    const spec = SPECS[species]
    return !!spec && spec.spec > 0.15
}

/** 造型几何工厂类型别名（供后续扩展：花纹/斑纹等程序化贴花） */
export type PetGeoFactory = (seed: number) => Geo
