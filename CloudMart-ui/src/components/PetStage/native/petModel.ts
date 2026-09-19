import * as THREE from 'three'
import { createToonMesh, PetMaterialKit } from './materials'
import { PetPalette } from './theme'

/**
 * Q 版宠物 3D 造型（原生 Three.js 实现，与 pet-game/PetModelBuilder.ts 同一套造型语言）。
 *
 * 造型要点：
 *  - 大头大眼：头身比 ≈ 1.25，眼睛是三层结构（眼白 / 瞳孔 / 双高光），高光用发光材质，
 *    这是"眼睛里有星星"的关键；
 *  - 圆润躯干 + 浅色肚皮 + 爪垫"袜子"，形成 Q 版软萌剪影；
 *  - 五官与耳朵全部挂在 head 节点下（pivot 在脖颈），头一动整张脸跟着动；
 *  - 影子单独返回（不挂在宠物根下），跳跃时影子留在地面并缩小变淡。
 *
 * 该文件只负责"建出来"；表情切换与演出由 animations.ts 驱动。
 */

/** 宠物骨架引用（动画层通过它驱动所有部件） */
export interface PetRig {
    root: THREE.Group
    body: THREE.Group
    head: THREE.Group
    tail: THREE.Group
    ears: THREE.Group[]
    earBase: THREE.Euler[]
    eyeGroups: THREE.Group[]
    pupils: THREE.Group[]
    mouth: { smile: THREE.Group; open: THREE.Group; sad: THREE.Group }
    blush: THREE.Mesh[]
    tuft: THREE.Group
    accessory: THREE.Group | null
    shadow: THREE.Mesh
    basePos: THREE.Vector3
    bodyBase: THREE.Vector3
    headBase: THREE.Vector3
    headBaseRot: THREE.Euler
    tailBaseRot: THREE.Euler
    pupilBase: THREE.Vector3
    blushBaseScale: THREE.Vector3
}

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

/**
 * 主造型尺寸（米；脚底 y = 0）。
 *
 * 比例取自"幼态讨喜"经验值：头身比 ≈ 1.35（头比身体大）、眼睛占脸宽 ≈ 40%、
 * 四肢外扩到身体轮廓之外（否则正面看会被躯干吃掉，显得像一颗蛋）。
 */
const S = {
    bodyRadius: 0.35,
    bodyY: 0.44,
    headRadius: 0.54,
    headCenterY: 0.28,
    eyeLocalY: 0.21,
    eyeX: 0.155,
    neckWorldY: 0.88,
} as const

const HEAD_LOCAL_Y = S.neckWorldY - S.bodyY   // head 在 body 下的局部高度（脖颈位置）
const EYE_WORLD_Y = S.neckWorldY + S.eyeLocalY
const NECK_WORLD_Y = S.neckWorldY

/** 几何体工厂（按零件尺度选择分段数，兼顾圆润度与性能） */
const sphere = (radius: number, quality: 'high' | 'mid' | 'low' = 'mid'): THREE.SphereGeometry =>
    new THREE.SphereGeometry(radius, quality === 'high' ? 32 : quality === 'mid' ? 22 : 16,
        quality === 'high' ? 24 : quality === 'mid' ? 16 : 12)

const capsule = (radius: number, length: number): THREE.CapsuleGeometry =>
    new THREE.CapsuleGeometry(radius, length, 6, 16)

const cone = (radius: number, height: number): THREE.ConeGeometry => new THREE.ConeGeometry(radius, height, 20)

const cylinder = (top: number, bottom: number, height: number): THREE.CylinderGeometry =>
    new THREE.CylinderGeometry(top, bottom, height, 24)

const torus = (radius: number, tube: number): THREE.TorusGeometry => new THREE.TorusGeometry(radius, tube, 10, 28)

/** 构建宠物；返回骨架引用（宠物组已挂到传入的 world 上） */
export function buildPet(
    world: THREE.Object3D,
    kit: PetMaterialKit,
    species: string,
    palette: PetPalette,
    accessoryKey: string,
): PetRig {
    const traits = TRAITS[species] || TRAITS.CAT
    const root = new THREE.Group()
    root.name = 'pet'
    root.position.set(0, 0, 0.35)
    world.add(root)

    const bodyMat = kit.toon(palette.body)
    const bellyMat = kit.toon(palette.belly)

    // ---- 躯干（pivot 在身体中心：呼吸从中心膨胀） ----
    const body = new THREE.Group()
    body.name = 'body'
    body.position.set(0, S.bodyY, 0)
    root.add(body)
    body.add(createToonMesh(sphere(S.bodyRadius, 'high'), bodyMat, kit, {
        name: 'torso', scale: [1.22, 0.84, 1.05], outline: 0.013,
    }))
    body.add(createToonMesh(sphere(0.28, 'mid'), bellyMat, kit, {
        name: 'belly', position: [0, -0.04, 0.17], scale: [0.92, 0.82, 0.52], outline: 0.008,
    }))

    // ---- 四肢（挂在 root：呼吸不带动脚；外扩到躯干轮廓之外才看得见） ----
    const pawMat = kit.toon(palette.paw)
    for (const side of [-1, 1]) {
        root.add(createToonMesh(sphere(0.145, 'mid'), pawMat, kit, {
            name: 'frontPaw',
            position: [0.235 * side, 0.115, 0.30],
            scale: [1, 0.76, 1.2],
            outline: 0.009,
        }))
        root.add(createToonMesh(sphere(0.15, 'mid'), pawMat, kit, {
            name: 'backPaw',
            position: [0.30 * side, 0.115, -0.05],
            scale: [1, 0.78, 1.22],
            outline: 0.009,
        }))
    }

    // ---- 尾巴（pivot 在臀后偏右：正面也能看到尾巴尖，角色剪影更完整） ----
    const tail = new THREE.Group()
    tail.name = 'tail'
    tail.position.set(0.17, 0.50, -0.26)
    tail.rotation.z = -30 * (Math.PI / 180)
    root.add(tail)
    buildTail(tail, kit, traits.tail, palette)

    // ---- 头部（pivot 在脖颈；挂在躯干下，呼吸/压扁时头部联动） ----
    const head = new THREE.Group()
    head.name = 'head'
    head.position.set(0, HEAD_LOCAL_Y, 0)
    body.add(head)
    head.add(createToonMesh(sphere(S.headRadius, 'high'), bodyMat, kit, {
        name: 'skull', position: [0, S.headCenterY, 0.02], scale: [1, 0.97, 0.96], outline: 0.013,
    }))
    // 额头浅色区（贴脸的一片亮色）：给大头分层，避免"一整颗橘球"
    head.add(createToonMesh(sphere(0.30, 'mid'), bellyMat, kit, {
        name: 'forehead', position: [0, S.headCenterY + 0.33, 0.24], scale: [1.05, 0.42, 0.62],
        rotation: [-24 * (Math.PI / 180), 0, 0], outline: 0,
    }))

    // 口鼻 + 鼻子（口鼻区要够大，Q 版的脸才有"鼓鼓的"婴儿感）
    head.add(createToonMesh(sphere(0.225, 'mid'), bellyMat, kit, {
        name: 'muzzle', position: [0, S.headCenterY - 0.24, 0.42], scale: [1.05, 0.8, 0.66], outline: 0.007,
    }))
    head.add(createToonMesh(sphere(0.068, 'low'), kit.toon(shade(palette.dark, -0.22)), kit, {
        name: 'nose', position: [0, S.headCenterY - 0.14, 0.585], outline: 0.004,
    }))

    // 猫胡须（每侧三根）：显著提升"猫科"辨识度，也让脸不至于太空
    const whiskerMat = kit.toon('#E4CDB6')
    for (const side of [-1, 1]) {
        for (let index = 0; index < 3; index += 1) {
            const tilt = -10 + index * 10
            head.add(createToonMesh(capsule(0.0075, 0.17), whiskerMat, kit, {
                name: 'whisker',
                position: [0.19 * side, S.headCenterY - 0.22 - index * 0.04, 0.50],
                rotation: [0, 0, (side * (92 + tilt) * Math.PI) / 180],
            }))
        }
    }

    // 嘴部三变体（同位置切换显示）
    const mouthRoot = new THREE.Group()
    mouthRoot.position.set(0, S.headCenterY - 0.27, 0.50)
    head.add(mouthRoot)
    const mouthMat = kit.toon(shade(palette.dark, -0.25))
    const smile = new THREE.Group()
    smile.add(createToonMesh(sphere(0.030, 'low'), mouthMat, kit, { name: 'l', position: [-0.045, 0.008, 0], outline: 0.003 }))
    smile.add(createToonMesh(sphere(0.030, 'low'), mouthMat, kit, { name: 'r', position: [0.045, 0.008, 0], outline: 0.003 }))
    smile.add(createToonMesh(sphere(0.030, 'low'), mouthMat, kit, {
        name: 'c', position: [0, -0.008, 0.015], scale: [1, 0.85, 1], outline: 0.003,
    }))
    mouthRoot.add(smile)

    const open = new THREE.Group()
    open.position.set(0, -0.012, 0.01)
    open.add(createToonMesh(sphere(0.052, 'low'), kit.toon('#7E3E54'), kit, {
        name: 'cavity', scale: [1, 0.82, 0.55], outline: 0.003,
    }))
    mouthRoot.add(open)

    const sad = new THREE.Group()
    sad.position.set(0, 0.012, 0)
    sad.add(createToonMesh(sphere(0.028, 'low'), mouthMat, kit, { name: 'l', position: [-0.05, -0.012, 0], outline: 0.003 }))
    sad.add(createToonMesh(sphere(0.028, 'low'), mouthMat, kit, { name: 'r', position: [0.05, -0.012, 0], outline: 0.003 }))
    sad.add(createToonMesh(sphere(0.028, 'low'), mouthMat, kit, {
        name: 'c', position: [0, 0.012, 0.015], scale: [1, 0.85, 1], outline: 0.003,
    }))
    mouthRoot.add(sad)

    // 眼睛（三层球）+ 腮红
    const eyeGroups: THREE.Group[] = []
    const pupils: THREE.Group[] = []
    const blush: THREE.Mesh[] = []
    const eyeWhiteMat = kit.toon('#FFFDFA')
    const pupilMat = kit.toon('#2E223A')
    const highlightMat = kit.glow('#FFFFFF')
    let blushBaseScale = new THREE.Vector3(1, 1, 1)
    for (const side of [-1, 1]) {
        const group = new THREE.Group()
        group.name = 'eye'
        group.position.set(S.eyeX * side, S.eyeLocalY, 0.40)
        group.rotation.y = (8 * side * Math.PI) / 180
        head.add(group)
        group.add(createToonMesh(sphere(0.175, 'mid'), eyeWhiteMat, kit, {
            name: 'white', scale: [1, 1.14, 0.55], outline: 0.007,
        }))
        const pupil = new THREE.Group()
        pupil.position.set(0, 0, 0.082)
        group.add(pupil)
        // 瞳孔占眼白约 3/4 —— 日系萌系角色的"大黑眼"比例
        pupil.add(createToonMesh(sphere(0.138, 'mid'), pupilMat, kit, { name: 'ball', scale: [1, 1, 0.58] }))
        pupil.add(createToonMesh(sphere(0.056, 'low'), highlightMat, kit, { name: 'hl', position: [0.05, 0.062, 0.058] }))
        pupil.add(createToonMesh(sphere(0.026, 'low'), highlightMat, kit, { name: 'hls', position: [-0.048, -0.052, 0.06] }))
        eyeGroups.push(group)
        pupils.push(pupil)

        const blushMesh = new THREE.Mesh(sphere(0.145, 'mid'), kit.glow(palette.blush, 0.5))
        blushMesh.position.set(0.35 * side, S.headCenterY - 0.16, 0.30)
        blushMesh.rotation.y = (16 * side * Math.PI) / 180
        blushMesh.scale.set(0.95, 0.58, 0.28)
        blushMesh.name = 'blush'
        head.add(blushMesh)
        blush.push(blushMesh)
        blushBaseScale = blushMesh.scale.clone()
    }

    // 耳朵（按物种）
    const earInfo = buildEars(head, kit, traits.ear, palette)

    // 呆毛（头顶翘毛）
    const tuft = new THREE.Group()
    tuft.name = 'tuft'
    tuft.position.set(0.03, S.headCenterY + 0.50, 0)
    head.add(tuft)
    tuft.add(createToonMesh(sphere(0.055, 'low'), bodyMat, kit, { name: 'a', outline: 0.005 }))
    tuft.add(createToonMesh(sphere(0.048, 'low'), bodyMat, kit, {
        name: 'b', position: [0.045, 0.075, 0.008], rotation: [0, 0, -28 * Math.PI / 180], outline: 0.005,
    }))
    tuft.add(createToonMesh(sphere(0.038, 'low'), bodyMat, kit, {
        name: 'c', position: [0.098, 0.128, 0.018], rotation: [0, 0, -48 * Math.PI / 180], outline: 0.005,
    }))

    // 配饰
    const accessory = buildAccessory(root, kit, accessoryKey, palette)

    // 地面软影（挂在世界层，跳跃时留在地面；y 略高于地毯表面，避免被地毯吃掉）
    const shadow = new THREE.Mesh(new THREE.CircleGeometry(0.62, 40), kit.glow('#2C1E34', 0.32))
    shadow.rotation.x = -Math.PI / 2
    shadow.position.set(root.position.x, 0.082, root.position.z + 0.05)
    shadow.scale.set(1, 1, 1)
    shadow.name = 'petShadow'
    world.add(shadow)

    return {
        root,
        body,
        head,
        tail,
        ears: earInfo.nodes,
        earBase: earInfo.bases,
        eyeGroups,
        pupils,
        mouth: { smile, open, sad },
        blush,
        tuft,
        accessory,
        shadow,
        basePos: root.position.clone(),
        bodyBase: body.position.clone(),
        headBase: head.position.clone(),
        headBaseRot: head.rotation.clone(),
        tailBaseRot: tail.rotation.clone(),
        pupilBase: new THREE.Vector3(0, 0, 0.068),
        blushBaseScale,
    }
}

/** 颜色向白/黑插值（与 theme.shift 同一算法，供零件局部调色） */
function shade(hex: string, amount: number): string {
    const value = parseInt(hex.slice(1), 16)
    const target = amount >= 0 ? 255 : 0
    const k = Math.abs(amount)
    const mix = (channel: number): number => Math.round(channel + (target - channel) * k)
    const r = mix((value >> 16) & 0xff)
    const g = mix((value >> 8) & 0xff)
    const b = mix(value & 0xff)
    return `#${((r << 16) | (g << 8) | b).toString(16).padStart(6, '0')}`
}

/** 耳朵（按物种）：返回节点与初始朝向（动画复位基准） */
function buildEars(head: THREE.Group, kit: PetMaterialKit, kind: SpeciesTraits['ear'], palette: PetPalette):
    { nodes: THREE.Group[]; bases: THREE.Euler[] } {
    const nodes: THREE.Group[] = []
    const bases: THREE.Euler[] = []
    const outer = kit.toon(palette.body)
    const dark = kit.toon(palette.dark)
    const inner = kit.toon(shade(palette.blush, -0.06))
    for (const side of [-1, 1]) {
        const ear = new THREE.Group()
        ear.name = 'ear'
        switch (kind) {
            case 'dog': {
                ear.position.set(0.42 * side, 0.36, -0.02)
                ear.rotation.z = (-38 * side * Math.PI) / 180
                ear.add(createToonMesh(sphere(0.19, 'mid'), dark, kit, {
                    name: 'outer', scale: [0.46, 1.0, 0.36], outline: 0.009,
                }))
                break
            }
            case 'rabbit': {
                ear.position.set(0.165 * side, 0.64, -0.03)
                ear.rotation.set((-4 * Math.PI) / 180, 0, (-12 * side * Math.PI) / 180)
                ear.add(createToonMesh(sphere(0.155, 'mid'), outer, kit, {
                    name: 'outer', scale: [0.46, 1.66, 0.46], outline: 0.009,
                }))
                ear.add(createToonMesh(sphere(0.105, 'low'), inner, kit, {
                    name: 'inner', position: [0, 0.02, 0.065], scale: [0.42, 1.5, 0.34], outline: 0.004,
                }))
                break
            }
            case 'fox': {
                ear.position.set(0.31 * side, 0.56, -0.02)
                ear.rotation.z = (-28 * side * Math.PI) / 180
                ear.add(createToonMesh(cone(0.225, 0.50), outer, kit, { name: 'outer', outline: 0.011 }))
                ear.add(createToonMesh(cone(0.128, 0.30), kit.toon(shade(palette.dark, -0.1)), kit, {
                    name: 'inner', position: [0, -0.02, 0.06], outline: 0.004,
                }))
                break
            }
            case 'panda': {
                ear.position.set(0.39 * side, 0.50, -0.04)
                ear.rotation.z = (-14 * side * Math.PI) / 180
                ear.add(createToonMesh(sphere(0.175, 'mid'), dark, kit, {
                    name: 'outer', scale: [1, 1, 0.7], outline: 0.009,
                }))
                break
            }
            default: {   // cat
                ear.position.set(0.30 * side, 0.44, -0.02)
                ear.rotation.z = (-28 * side * Math.PI) / 180
                ear.add(createToonMesh(cone(0.25, 0.58), outer, kit, { name: 'outer', outline: 0.012 }))
                ear.add(createToonMesh(cone(0.145, 0.36), inner, kit, {
                    name: 'inner', position: [0, -0.03, 0.065], outline: 0.004,
                }))
                break
            }
        }
        head.add(ear)
        nodes.push(ear)
        bases.push(ear.rotation.clone())
    }
    return { nodes, bases }
}

/** 尾巴（按物种） */
function buildTail(tail: THREE.Group, kit: PetMaterialKit, kind: SpeciesTraits['tail'], palette: PetPalette): void {
    const bodyMat = kit.toon(palette.body)
    const darkMat = kit.toon(palette.dark)
    const bellyMat = kit.toon(palette.belly)
    switch (kind) {
        case 'curl': {
            tail.add(createToonMesh(sphere(0.105, 'mid'), darkMat, kit, { name: 'seg1', position: [0, 0.06, -0.14], outline: 0.006 }))
            tail.add(createToonMesh(sphere(0.095, 'mid'), darkMat, kit, { name: 'seg2', position: [0, 0.235, -0.30], outline: 0.006 }))
            tail.add(createToonMesh(sphere(0.085, 'mid'), darkMat, kit, { name: 'seg3', position: [0, 0.40, -0.33], outline: 0.006 }))
            break
        }
        case 'puff': {
            tail.add(createToonMesh(sphere(0.23, 'mid'), bodyMat, kit, {
                name: 'puff', position: [0, 0.08, -0.26], scale: [1, 1.08, 1.28], outline: 0.010,
            }))
            tail.add(createToonMesh(sphere(0.125, 'mid'), bellyMat, kit, {
                name: 'tip', position: [0, 0.13, -0.52], scale: [1, 1, 1.1], outline: 0.006,
            }))
            break
        }
        case 'bun': {
            tail.add(createToonMesh(sphere(0.155, 'mid'), bellyMat, kit, { name: 'bun', position: [0, 0.04, -0.20], outline: 0.008 }))
            break
        }
        case 'stub': {
            tail.add(createToonMesh(sphere(0.125, 'mid'), darkMat, kit, { name: 'stub', position: [0, 0.04, -0.20], outline: 0.006 }))
            break
        }
        default: {   // slim：斜向上翘的细尾（从身体右后方伸出，正面可见尾尖）
            tail.add(createToonMesh(capsule(0.062, 0.38), bodyMat, kit, {
                name: 'slim', position: [0, 0.23, -0.05], rotation: [(26 * Math.PI) / 180, 0, 0], outline: 0.006,
            }))
            break
        }
    }
}

/** 配饰（铃铛 / 领结 / 眼镜 / 围巾） */
function buildAccessory(root: THREE.Group, kit: PetMaterialKit, key: string, palette: PetPalette): THREE.Group | null {
    if (!key || key === 'none') {
        return null
    }
    const group = new THREE.Group()
    group.name = 'accessory'
    root.add(group)
    switch (key) {
        case 'bell': {
            group.add(createToonMesh(cylinder(0.30, 0.32, 0.075), kit.toon('#D65860'), kit, {
                name: 'collar', position: [0, NECK_WORLD_Y - 0.18, 0], outline: 0.006,
            }))
            group.add(createToonMesh(sphere(0.082, 'mid'), kit.toon('#FFC854'), kit, {
                name: 'bell', position: [0, NECK_WORLD_Y - 0.27, 0.27], outline: 0.005,
            }))
            group.add(createToonMesh(sphere(0.028, 'low'), kit.toon('#966828'), kit, {
                name: 'clapper', position: [0, NECK_WORLD_Y - 0.32, 0.285], outline: 0.003,
            }))
            break
        }
        case 'bowtie': {
            group.add(createToonMesh(cylinder(0.30, 0.32, 0.065), kit.toon(shade(palette.dark, -0.12)), kit, {
                name: 'collar', position: [0, NECK_WORLD_Y - 0.18, 0], outline: 0.006,
            }))
            group.add(createToonMesh(cone(0.105, 0.19), kit.toon('#FF84A8'), kit, {
                name: 'bowL', position: [-0.125, NECK_WORLD_Y - 0.22, 0.28],
                rotation: [0, 0, (88 * Math.PI) / 180], outline: 0.005,
            }))
            group.add(createToonMesh(cone(0.105, 0.19), kit.toon('#FF84A8'), kit, {
                name: 'bowR', position: [0.125, NECK_WORLD_Y - 0.22, 0.28],
                rotation: [0, 0, (-88 * Math.PI) / 180], outline: 0.005,
            }))
            group.add(createToonMesh(sphere(0.055, 'low'), kit.toon('#E26084'), kit, {
                name: 'knot', position: [0, NECK_WORLD_Y - 0.22, 0.30], outline: 0.004,
            }))
            break
        }
        case 'glasses': {
            const frame = kit.toon('#604860')
            group.add(createToonMesh(torus(0.15, 0.022), frame, kit, {
                name: 'lensL', position: [-S.eyeX - 0.02, EYE_WORLD_Y, 0.44], outline: 0.004,
            }))
            group.add(createToonMesh(torus(0.15, 0.022), frame, kit, {
                name: 'lensR', position: [S.eyeX + 0.02, EYE_WORLD_Y, 0.44], outline: 0.004,
            }))
            group.add(createToonMesh(capsule(0.018, 0.07), frame, kit, {
                name: 'bridge', position: [0, EYE_WORLD_Y + 0.02, 0.46], rotation: [0, 0, Math.PI / 2], outline: 0.003,
            }))
            break
        }
        case 'scarf': {
            const scarf = kit.toon('#7ABEFA')
            group.add(createToonMesh(cylinder(0.33, 0.36, 0.16), scarf, kit, {
                name: 'wrap', position: [0, NECK_WORLD_Y - 0.20, 0], outline: 0.008,
            }))
            group.add(createToonMesh(capsule(0.055, 0.18), scarf, kit, {
                name: 'end',
                position: [0.30, NECK_WORLD_Y - 0.34, 0.20],
                rotation: [(18 * Math.PI) / 180, 0, (-22 * Math.PI) / 180],
                outline: 0.005,
            }))
            break
        }
        default:
            break
    }
    return group
}
