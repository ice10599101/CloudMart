import { Color, Node, Vec3 } from 'cc'
import { PartStyle, PetBuilderKit } from './PetBuilderKit'
import { PetPalette, shift } from './PetGameTheme'

/**
 * Q 版宠物 3D 造型（视觉重构 v3）。
 *
 * 造型语言：大头（头身比 ≈ 1.25）、大眼（三层球：眼白/瞳孔/双高光）、圆润躯干、
 * 物种专属耳尾、腮红、呆毛、地面软影；所有五官挂在 head 节点下（pivot 在脖颈），
 * 因此摇头/点头时五官与耳朵整体联动，天然具备"活物"感。
 *
 * 情绪表达载体：
 *  - 眼睛：眨眼（eyeGroup 整体 Y 缩放）、视线（pupil 位移）、惊喜（整体放大）；
 *  - 嘴部三变体：smile / open / sad（切换 active，无需重建）；
 *  - 耳朵/尾巴：角度与摆动由动画层驱动。
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
    /** 双耳初始欧拉角（动画复位基准；耳朵造型自带倾角） */
    earBase: Vec3[]
    /** 眼睛组（眨眼/惊喜缩放） */
    eyeGroups: Node[]
    /** 瞳孔（视线偏移） */
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
    /** 瞳孔基准局部坐标（视线偏移基准） */
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
}

/** 物种特征参数：耳朵/尾巴造型 */
interface SpeciesTraits {
    ear: 'cat' | 'dog' | 'rabbit' | 'fox' | 'panda'
    tail: 'slim' | 'curl' | 'puff' | 'bun' | 'stub'
}

const TRAITS: Record<string, SpeciesTraits> = {
    CAT: { ear: 'cat', tail: 'slim' },
    DOG: { ear: 'dog', tail: 'curl' },
    RABBIT: { ear: 'rabbit', tail: 'bun' },
    FOX: { ear: 'fox', tail: 'puff' },
    PANDA: { ear: 'panda', tail: 'stub' },
    WILD: { ear: 'fox', tail: 'puff' },
}

/** 主造型尺寸（单位：米；脚底 y=0） */
const S = {
    bodyRadius: 0.40,
    bodyY: 0.47,
    headRadius: 0.50,
    neckY: 0.94,
    headCenterY: 0.26,
    eyeY: 1.26,
    eyeX: 0.185,
} as const

/**
 * 构建宠物整体。
 *
 * @param petParent 宠物根节点（调用方负责在场景中定位；整体动画作用于它）
 * @param groundParent 地面层节点（影子挂这里，宠物跳起时影子不跟随）
 * @param species 物种键（CAT/DOG/...；决定耳尾造型）
 * @param accessoryKey 配饰键（none/bell/bowtie/glasses/scarf）
 */
export function buildPet(petParent: Node, groundParent: Node, kit: PetBuilderKit,
                         species: string, palette: PetPalette, accessoryKey: string): PetRig {
    const traits = TRAITS[species] || TRAITS.CAT
    const bodyMat = styleOf(palette.body, 0.014)
    const darkMat = styleOf(palette.dark, 0.012)
    const bellyMat = styleOf(palette.belly, 0.010)
    const pawMat = styleOf(palette.paw, 0.008)

    // ---- 躯干（pivot 在身体中心，呼吸从中心膨胀） ----
    const body = kit.make3dNode(petParent, 'Body', new Vec3(0, S.bodyY, 0))
    kit.ball(body, 'Torso', S.bodyRadius, bodyMat, new Vec3(0, 0, 0), {
        scale: new Vec3(1.02, 0.95, 0.95),
    })
    kit.ball(body, 'Belly', 0.30, bellyMat, new Vec3(0, -0.05, 0.19), {
        scale: new Vec3(0.95, 0.88, 0.5),
    })

    // ---- 四肢（挂在 root，呼吸不带动脚） ----
    for (const side of [-1, 1]) {
        kit.ball(petParent, 'FrontPaw', 0.15, pawMat, new Vec3(0.205 * side, 0.14, 0.30), {
            scale: new Vec3(1, 0.8, 1.12),
        })
        kit.ball(petParent, 'BackPaw', 0.145, pawMat, new Vec3(0.235 * side, 0.135, -0.12), {
            scale: new Vec3(1, 0.82, 1.15),
        })
    }

    // ---- 尾巴（pivot 在臀后，摆动以根部为轴） ----
    const tailBase = new Vec3(0, 0.60, -0.32)
    const tail = kit.make3dNode(petParent, 'Tail', tailBase)
    buildTail(tail, kit, traits.tail, bodyMat, darkMat, bellyMat)

    // ---- 头部（pivot 在脖颈；挂在躯干下，呼吸/压扁时头部天然联动） ----
    const head = kit.make3dNode(body, 'Head', new Vec3(0, S.neckY - S.bodyY, 0))
    kit.ball(head, 'Skull', S.headRadius, bodyMat, new Vec3(0, S.headCenterY, 0.02), {
        scale: new Vec3(1, 0.97, 0.96),
    })

    // 口鼻 + 鼻子
    kit.ball(head, 'Muzzle', 0.175, bellyMat, new Vec3(0, S.headCenterY - 0.24, 0.38), {
        scale: new Vec3(1.08, 0.8, 0.72),
    })
    kit.ball(head, 'Nose', 0.056, styleOf(shift(palette.dark, -0.18), 0),
        new Vec3(0, S.headCenterY - 0.17, 0.52))

    // 嘴部三变体（同一位置，active 切换；视觉上只保留一个）
    const mouthRoot = kit.make3dNode(head, 'Mouth', new Vec3(0, S.headCenterY - 0.275, 0.475))
    const mouthColor = shift(palette.dark, -0.25)
    const smile = kit.make3dNode(mouthRoot, 'Smile', new Vec3(0, 0, 0))
    kit.ball(smile, 'l', 0.030, styleOf(mouthColor, 0), new Vec3(-0.045, 0.008, 0))
    kit.ball(smile, 'r', 0.030, styleOf(mouthColor, 0), new Vec3(0.045, 0.008, 0))
    kit.ball(smile, 'c', 0.030, styleOf(mouthColor, 0), new Vec3(0, -0.008, 0.015), {
        scale: new Vec3(1, 0.85, 1),
    })
    const openMouth = kit.make3dNode(mouthRoot, 'Open', new Vec3(0, -0.012, 0.01))
    openMouth.active = false
    kit.ball(openMouth, 'Cavity', 0.052, styleOf(new Color(126, 62, 84, 255), 0), new Vec3(0, 0, 0), {
        scale: new Vec3(1, 0.82, 0.55),
    })
    const sadMouth = kit.make3dNode(mouthRoot, 'Sad', new Vec3(0, 0.012, 0))
    sadMouth.active = false
    kit.ball(sadMouth, 'l', 0.028, styleOf(mouthColor, 0), new Vec3(-0.05, -0.012, 0))
    kit.ball(sadMouth, 'r', 0.028, styleOf(mouthColor, 0), new Vec3(0.05, -0.012, 0))
    kit.ball(sadMouth, 'c', 0.028, styleOf(mouthColor, 0), new Vec3(0, 0.012, 0.015), {
        scale: new Vec3(1, 0.85, 1),
    })

    // 眼睛（三层球）+ 腮红
    const eyeGroups: Node[] = []
    const pupils: Node[] = []
    const blush: Node[] = []
    const eyeWhiteColor = new Color(255, 253, 250, 255)
    const pupilColor = new Color(46, 34, 58, 255)
    for (const side of [-1, 1]) {
        const group = kit.make3dNode(head, 'Eye', new Vec3(S.eyeX * side, S.eyeY - S.neckY, 0.34), {
            rot: new Vec3(0, 8 * side, 0),
        })
        kit.ball(group, 'White', 0.135, styleOf(eyeWhiteColor, 0.008), new Vec3(0, 0, 0), {
            scale: new Vec3(1, 1.14, 0.6),
        })
        const pupil = kit.make3dNode(group, 'Pupil', new Vec3(0, 0, 0.068))
        kit.ball(pupil, 'Ball', 0.098, styleOf(pupilColor, 0), new Vec3(0, 0, 0), {
            scale: new Vec3(1, 1, 0.62),
        })
        kit.ball(pupil, 'Highlight', 0.040, styleOf(new Color(255, 255, 255, 255), 0),
            new Vec3(0.042, 0.05, 0.05))
        kit.ball(pupil, 'HighlightSmall', 0.020, styleOf(new Color(255, 255, 255, 228), 0),
            new Vec3(-0.038, -0.042, 0.052))
        eyeGroups.push(group)
        pupils.push(pupil)

        blush.push(kit.ball(head, 'Blush', 0.135, {
            color: palette.blush, shade: palette.blush, alpha: 132, outline: 0, glow: true,
        }, new Vec3(0.335 * side, S.headCenterY - 0.14, 0.30), {
            rot: new Vec3(0, 16 * side, 0),
            scale: new Vec3(0.95, 0.6, 0.3),
        }))
    }

    // 耳朵（按物种；记录初始倾角供动画复位）
    const earInfo = buildEars(head, kit, traits.ear, palette)

    // 呆毛（头顶翘毛，随呼吸微摆 —— 生命力细节）
    const tuft = kit.make3dNode(head, 'Tuft', new Vec3(0.02, S.headCenterY + 0.46, 0.0))
    kit.ball(tuft, 'a', 0.055, bodyMat, new Vec3(0, 0, 0))
    kit.ball(tuft, 'b', 0.048, bodyMat, new Vec3(0.045, 0.075, 0.008), { rot: new Vec3(0, 0, -28) })
    kit.ball(tuft, 'c', 0.038, bodyMat, new Vec3(0.098, 0.128, 0.018), { rot: new Vec3(0, 0, -48) })

    // 配饰
    const accessory = buildAccessory(petParent, kit, accessoryKey, palette)

    // 地面软影（挂房间层：宠物跳起时影子留在地面）
    const shadow = kit.cylPart(groundParent, 'PetShadow', 0.60, 0.66, 0.014, {
        color: new Color(44, 30, 52, 255), shade: new Color(44, 30, 52, 255),
        glow: true, alpha: 96, outline: 0,
    }, new Vec3(petParent.position.x, 0.008, petParent.position.z + 0.05))

    return {
        root: petParent,
        body,
        head,
        tail,
        ears: earInfo.nodes,
        earBase: earInfo.bases,
        eyeGroups,
        pupils,
        mouth: { smile, open: openMouth, sad: sadMouth },
        blush,
        tuft,
        shadow,
        accessory,
        pupilBase: new Vec3(0, 0, 0.068),
        basePos: petParent.position.clone(),
        bodyBase: body.position.clone(),
        headBase: head.position.clone(),
        tailBase: tail.position.clone(),
        headBaseRot: head.eulerAngles.clone(),
        tailBaseRot: tail.eulerAngles.clone(),
    }
}

/** 造型风格快速构造：主色 + 描边宽度 */
function styleOf(color: Color, outline: number): PartStyle {
    return { color, outline }
}

/** 耳朵：按物种给出外耳 + 内耳（内耳统一偏粉，符合 Q 版审美）；返回节点与初始倾角 */
function buildEars(head: Node, kit: PetBuilderKit, kind: SpeciesTraits['ear'], palette: PetPalette):
        { nodes: Node[]; bases: Vec3[] } {
    const ears: Node[] = []
    const bases: Vec3[] = []
    const outer = styleOf(palette.body, 0.012)
    const dark = styleOf(palette.dark, 0.010)
    const inner: PartStyle = { color: shift(palette.blush, -0.06), outline: 0.006 }
    for (const side of [-1, 1]) {
        let root: Node
        switch (kind) {
            case 'dog': {
                root = kit.make3dNode(head, 'Ear', new Vec3(0.40 * side, 0.34, -0.02), {
                    rot: new Vec3(0, 0, -36 * side),
                })
                kit.ball(root, 'Outer', 0.17, dark, new Vec3(0, 0, 0), { scale: new Vec3(0.44, 0.98, 0.34) })
                break
            }
            case 'rabbit': {
                root = kit.make3dNode(head, 'Ear', new Vec3(0.155 * side, 0.62, -0.03), {
                    rot: new Vec3(-4, 0, -12 * side),
                })
                kit.ball(root, 'Outer', 0.145, outer, new Vec3(0, 0, 0), { scale: new Vec3(0.46, 1.62, 0.46) })
                kit.ball(root, 'Inner', 0.10, inner, new Vec3(0, 0.02, 0.06), { scale: new Vec3(0.42, 1.5, 0.34) })
                break
            }
            case 'fox': {
                root = kit.make3dNode(head, 'Ear', new Vec3(0.30 * side, 0.56, -0.02), {
                    rot: new Vec3(0, 0, -26 * side),
                })
                kit.conePart(root, 'Outer', 0.205, 0.40, outer, new Vec3(0, 0, 0))
                kit.conePart(root, 'Inner', 0.118, 0.24, { color: shift(palette.dark, -0.1), outline: 0.006 },
                    new Vec3(0, -0.02, 0.055))
                break
            }
            case 'panda': {
                root = kit.make3dNode(head, 'Ear', new Vec3(0.375 * side, 0.50, -0.04), {
                    rot: new Vec3(0, 0, -14 * side),
                })
                kit.ball(root, 'Outer', 0.165, dark, new Vec3(0, 0, 0), { scale: new Vec3(1, 1, 0.7) })
                break
            }
            default: {   // cat
                root = kit.make3dNode(head, 'Ear', new Vec3(0.255 * side, 0.50, -0.02), {
                    rot: new Vec3(0, 0, -22 * side),
                })
                kit.conePart(root, 'Outer', 0.175, 0.32, outer, new Vec3(0, 0, 0))
                kit.conePart(root, 'Inner', 0.098, 0.18, inner, new Vec3(0, -0.015, 0.05))
                break
            }
        }
        ears.push(root)
        bases.push(root.eulerAngles.clone())
    }
    return { nodes: ears, bases }
}

/** 尾巴：按物种给出形态（挂在 tail 节点，pivot 在臀后） */
function buildTail(tail: Node, kit: PetBuilderKit, kind: SpeciesTraits['tail'],
                   bodyMat: PartStyle, darkMat: PartStyle, bellyMat: PartStyle): void {
    switch (kind) {
        case 'curl': {
            kit.ball(tail, 'Seg1', 0.105, darkMat, new Vec3(0, 0.06, -0.14))
            kit.ball(tail, 'Seg2', 0.095, darkMat, new Vec3(0, 0.235, -0.30))
            kit.ball(tail, 'Seg3', 0.085, darkMat, new Vec3(0, 0.40, -0.33))
            break
        }
        case 'puff': {
            kit.ball(tail, 'Puff', 0.23, bodyMat, new Vec3(0, 0.08, -0.26), {
                scale: new Vec3(1, 1.08, 1.28),
            })
            kit.ball(tail, 'Tip', 0.125, bellyMat, new Vec3(0, 0.13, -0.52), { scale: new Vec3(1, 1, 1.1) })
            break
        }
        case 'bun': {
            kit.ball(tail, 'Bun', 0.155, bellyMat, new Vec3(0, 0.04, -0.20))
            break
        }
        case 'stub': {
            kit.ball(tail, 'Stub', 0.125, darkMat, new Vec3(0, 0.04, -0.20))
            break
        }
        default: {   // slim：斜向上的细尾
            kit.capPart(tail, 'Slim', 0.065, 0.46, bodyMat, new Vec3(0, 0.20, -0.16), {
                rot: new Vec3(64, 0, 0),
            })
            break
        }
    }
}

/** 配饰：铃铛 / 领结 / 眼镜 / 围巾（挂在 root，不随呼吸缩放） */
function buildAccessory(parent: Node, kit: PetBuilderKit, key: string, palette: PetPalette): Node | null {
    if (!key || key === 'none') {
        return null
    }
    const root = kit.make3dNode(parent, 'Accessory', new Vec3(0, 0, 0))
    const strap = styleOf(shift(palette.dark, -0.12), 0.008)
    switch (key) {
        case 'bell': {
            kit.cylPart(root, 'Collar', 0.335, 0.35, 0.075, styleOf(new Color(214, 88, 96, 255), 0.008),
                new Vec3(0, 0.70, 0.0))
            kit.ball(root, 'Bell', 0.078, styleOf(new Color(255, 200, 84, 255), 0.008), new Vec3(0, 0.615, 0.30))
            kit.ball(root, 'Clapper', 0.026, styleOf(new Color(150, 104, 40, 255), 0), new Vec3(0, 0.565, 0.315))
            break
        }
        case 'bowtie': {
            kit.cylPart(root, 'Collar', 0.335, 0.35, 0.065, strap, new Vec3(0, 0.70, 0.0))
            kit.conePart(root, 'BowL', 0.10, 0.18, styleOf(new Color(255, 132, 168, 255), 0.008),
                new Vec3(-0.12, 0.66, 0.28), { rot: new Vec3(0, 0, 88) })
            kit.conePart(root, 'BowR', 0.10, 0.18, styleOf(new Color(255, 132, 168, 255), 0.008),
                new Vec3(0.12, 0.66, 0.28), { rot: new Vec3(0, 0, -88) })
            kit.ball(root, 'Knot', 0.052, styleOf(new Color(226, 96, 132, 255), 0), new Vec3(0, 0.66, 0.30))
            break
        }
        case 'glasses': {
            const frame = styleOf(new Color(96, 72, 96, 255), 0.006)
            kit.torusPart(root, 'LensL', 0.135, 0.020, frame, new Vec3(-0.185, S.eyeY, 0.40))
            kit.torusPart(root, 'LensR', 0.135, 0.020, frame, new Vec3(0.185, S.eyeY, 0.40))
            kit.capPart(root, 'Bridge', 0.018, 0.09, frame, new Vec3(0, S.eyeY + 0.02, 0.42), {
                rot: new Vec3(0, 0, 90),
            })
            break
        }
        case 'scarf': {
            const scarf = styleOf(new Color(122, 190, 250, 255), 0.010)
            kit.cylPart(root, 'Wrap', 0.36, 0.40, 0.155, scarf, new Vec3(0, 0.70, 0.0))
            kit.capPart(root, 'End', 0.055, 0.22, scarf, new Vec3(0.30, 0.55, 0.22), {
                rot: new Vec3(18, 0, -22),
            })
            break
        }
        default:
            break
    }
    return root
}
