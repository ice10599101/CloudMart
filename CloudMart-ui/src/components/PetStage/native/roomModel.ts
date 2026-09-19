import * as THREE from 'three'
import { createToonMesh, PetMaterialKit } from './materials'

/**
 * 温馨宠物房间（原生 Three.js 实现，与 pet-game/PetRoomBuilder.ts 同一空间设计）。
 *
 * 空间分层（相机在 +z 俯视 -14°，宠物位于地毯中央约 z = 0.35）：
 *  - 背景：奶油色后墙 / 木质踢脚线 / 大窗 + 窗帘 + 阳光光斑 / 挂画 / 云朵星饰 / 置物架 / 挂灯串；
 *  - 中景：木地板（含板缝） / 三层圆地毯 / 宠物窝 / 斗柜台灯 / 大绿植 / 散落玩具；
 *  - 前景：靠垫与地毯边缘玩具，制造纵深；
 *  - 氛围：飘浮光点缓慢上升、灯泡呼吸、光斑明暗起伏（由 stageEngine 逐帧驱动）。
 *
 * 配色走"暖奶油 + 蜜糖木色 + 柔粉"低饱和暖色系，与宠物高饱和主色形成对比，让角色跳出来。
 */

export interface RoomRefs {
    orbs: THREE.Mesh[]
    bulbs: THREE.Mesh[]
    sunBeam: THREE.Mesh
    toyBall: THREE.Group
}

const C = {
    wall: '#FEF8F1',
    baseboard: '#D8B084',
    floor: '#F3DDBC',
    floorLine: '#E2CDA8',
    rugOuter: '#EEAAA2',
    rugInner: '#F8DCA8',
    rugCenter: '#FAC6C0',
    wood: '#C89C6E',
    woodDark: '#AE8258',
    bedRim: '#C29266',
    bedCushion: '#FFC9D6',
    windowFrame: '#FBF7F1',
    glass: '#AFDDF5',
    curtain: '#F7C4C4',
    sun: '#FFF1C8',
    leaf: '#74C58B',
    leafDark: '#54A870',
    pot: '#D9966B',
    bulb: '#FFE29E',
    pink: '#FF9FB4',
    blue: '#74B6F0',
    yellow: '#FFD166',
    duckBeak: '#F59B4B',
    cushion: '#F6E3C2',
    picture: '#FDEFDC',
    heart: '#FF8AA0',
    cloud: '#FFFBF2',
} as const

/** 构建房间（返回可动元素引用供氛围动画使用） */
export function buildRoom(world: THREE.Object3D, kit: PetMaterialKit): RoomRefs {
    buildFloor(world, kit)
    buildWalls(world, kit)
    const { bulbs } = buildWindowAndLamps(world, kit)
    buildWallDecor(world, kit)
    buildRug(world, kit)
    buildBed(world, kit)
    buildCabinet(world, kit)
    buildPlant(world, kit)
    const toyBall = buildToys(world, kit)
    buildForeground(world, kit)
    const orbs = buildOrbs(world, kit)
    const sunBeam = buildSunBeam(world, kit)
    return { orbs, bulbs, sunBeam, toyBall }
}

/** 地板：大色块 + 板缝（顶面 y = 0，接收阴影） */
function buildFloor(world: THREE.Object3D, kit: PetMaterialKit): void {
    const slab = createToonMesh(new THREE.BoxGeometry(16, 0.2, 7.6), kit.toon(C.floor), kit, {
        name: 'floorSlab', position: [0, -0.1, 0.4], castShadow: false, receiveShadow: true,
    })
    world.add(slab)
    const lineMat = kit.toon(C.floorLine)
    for (let index = -5; index <= 5; index += 1) {
        world.add(createToonMesh(new THREE.BoxGeometry(0.035, 0.014, 7.6), lineMat, kit, {
            name: 'floorSeam', position: [index * 1.55, 0.006, 0.4], castShadow: false, receiveShadow: true,
        }))
    }
    for (const z of [-1.9, -0.4, 1.1, 2.6]) {
        world.add(createToonMesh(new THREE.BoxGeometry(16, 0.014, 0.035), lineMat, kit, {
            name: 'floorJoint', position: [0, 0.006, z], castShadow: false, receiveShadow: true,
        }))
    }
}

/** 墙体：后墙 + 左墙 + 踢脚线（不做描边，避免线条抢戏） */
function buildWalls(world: THREE.Object3D, kit: PetMaterialKit): void {
    const wallMat = kit.toon(C.wall)
    const baseMat = kit.toon(C.baseboard)
    world.add(createToonMesh(new THREE.BoxGeometry(16, 4.8, 0.3), wallMat, kit, {
        name: 'backWall', position: [0, 2.4, -3.0], castShadow: false, receiveShadow: true,
    }))
    world.add(createToonMesh(new THREE.BoxGeometry(16, 0.36, 0.4), baseMat, kit, {
        name: 'backBase', position: [0, 0.18, -2.93], castShadow: false, receiveShadow: true,
    }))
    world.add(createToonMesh(new THREE.BoxGeometry(0.3, 4.8, 7.6), wallMat, kit, {
        name: 'leftWall', position: [-4.6, 2.4, 0.4], castShadow: false, receiveShadow: true,
    }))
    world.add(createToonMesh(new THREE.BoxGeometry(0.4, 0.36, 7.6), baseMat, kit, {
        name: 'leftBase', position: [-4.53, 0.18, 0.4], castShadow: false, receiveShadow: true,
    }))
}

/** 大窗（窗框 + 发光玻璃 + 窗帘）与挂灯串 */
function buildWindowAndLamps(world: THREE.Object3D, kit: PetMaterialKit): { bulbs: THREE.Mesh[] } {
    const frameMat = kit.toon(C.windowFrame)
    const glassMat = kit.glow(C.glass, 0.92)
    const curtainMat = kit.toon(C.curtain)
    const hold = new THREE.Group()
    hold.position.set(-2.0, 2.45, -2.82)
    world.add(hold)
    hold.add(createToonMesh(new THREE.BoxGeometry(2.15, 1.75, 0.14), frameMat, kit, { name: 'frame', castShadow: false }))
    hold.add(createToonMesh(new THREE.BoxGeometry(1.86, 1.46, 0.06), glassMat, kit, {
        name: 'glass', position: [0, 0, 0.05], castShadow: false, glow: true,
    }))
    hold.add(createToonMesh(new THREE.BoxGeometry(0.09, 1.46, 0.1), frameMat, kit, { name: 'mullionV', position: [0, 0, 0.06], castShadow: false }))
    hold.add(createToonMesh(new THREE.BoxGeometry(1.86, 0.09, 0.1), frameMat, kit, { name: 'mullionH', position: [0, 0, 0.06], castShadow: false }))
    hold.add(createToonMesh(new THREE.BoxGeometry(2.4, 0.12, 0.34), frameMat, kit, { name: 'sill', position: [0, -0.93, 0.08] }))
    hold.add(createToonMesh(new THREE.CapsuleGeometry(0.045, 3.4, 6, 14), kit.toon(C.woodDark), kit, {
        name: 'rod', position: [0, 1.06, 0.12], rotation: [0, 0, Math.PI / 2],
    }))
    hold.add(createToonMesh(new THREE.BoxGeometry(0.5, 2.3, 0.18), curtainMat, kit, { name: 'curtainL', position: [-1.28, -0.1, 0.12] }))
    hold.add(createToonMesh(new THREE.BoxGeometry(0.5, 2.3, 0.18), curtainMat, kit, { name: 'curtainR', position: [1.28, -0.1, 0.12] }))
    hold.add(createToonMesh(new THREE.BoxGeometry(2.9, 0.26, 0.24), curtainMat, kit, { name: 'pelmet', position: [0, 1.02, 0.12] }))
    // 窗台小盆栽
    hold.add(createToonMesh(new THREE.CylinderGeometry(0.16, 0.12, 0.2, 18), kit.toon(C.pot), kit, {
        name: 'potSmall', position: [0.72, -0.8, 0.12],
    }))
    hold.add(createToonMesh(new THREE.SphereGeometry(0.15, 18, 14), kit.toon(C.leaf), kit, {
        name: 'leafSmall', position: [0.72, -0.62, 0.12], scale: [1, 0.9, 1], outline: 0.005,
    }))

    // 挂灯串（9 个暖黄灯泡沿弧线）
    const bulbs: THREE.Mesh[] = []
    const bulbMat = kit.glow(C.bulb)
    for (let index = 0; index < 9; index += 1) {
        const t = index / 8
        const x = -4.4 + t * 8.8
        const y = 3.72 - Math.sin(t * Math.PI) * 0.42
        const bulb = new THREE.Mesh(new THREE.SphereGeometry(0.085, 16, 12), bulbMat)
        bulb.position.set(x, y, -2.72)
        bulb.name = 'bulb'
        world.add(bulb)
        bulbs.push(bulb)
    }
    return { bulbs }
}

/** 墙面装饰：挂画（含心形）/ 云朵星饰 / 置物架 */
function buildWallDecor(world: THREE.Object3D, kit: PetMaterialKit): void {
    // 挂画
    const painting = new THREE.Group()
    painting.position.set(1.85, 2.55, -2.8)
    world.add(painting)
    painting.add(createToonMesh(new THREE.BoxGeometry(1.5, 1.2, 0.1), kit.toon(C.wood), kit, { name: 'frame', castShadow: false }))
    painting.add(createToonMesh(new THREE.BoxGeometry(1.26, 0.96, 0.06), kit.toon(C.picture), kit, {
        name: 'canvas', position: [0, 0, 0.04], castShadow: false,
    }))
    const heartMat = kit.toon(C.heart)
    painting.add(createToonMesh(new THREE.SphereGeometry(0.14, 18, 14), heartMat, kit, {
        name: 'heartL', position: [-0.1, 0.08, 0.07], castShadow: false,
    }))
    painting.add(createToonMesh(new THREE.SphereGeometry(0.14, 18, 14), heartMat, kit, {
        name: 'heartR', position: [0.1, 0.08, 0.07], castShadow: false,
    }))
    painting.add(createToonMesh(new THREE.ConeGeometry(0.19, 0.26, 18), heartMat, kit, {
        name: 'heartTip', position: [0, -0.14, 0.07], rotation: [Math.PI, 0, 0], castShadow: false,
    }))

    // 云朵 + 星星
    const cloudMat = kit.toon(C.cloud)
    const cloud = new THREE.Group()
    cloud.position.set(-3.3, 3.5, -2.8)
    world.add(cloud)
    cloud.add(createToonMesh(new THREE.SphereGeometry(0.26, 18, 14), cloudMat, kit, { name: 'c1', position: [-0.24, 0, 0], castShadow: false }))
    cloud.add(createToonMesh(new THREE.SphereGeometry(0.34, 18, 14), cloudMat, kit, { name: 'c2', position: [0.15, 0.06, 0], castShadow: false }))
    cloud.add(createToonMesh(new THREE.SphereGeometry(0.22, 18, 14), cloudMat, kit, { name: 'c3', position: [0.5, -0.02, 0], castShadow: false }))
    const star = new THREE.Mesh(new THREE.SphereGeometry(0.1, 14, 10), kit.glow(C.bulb))
    star.position.set(0.9, 0.34, 0)
    cloud.add(star)

    // 置物架
    const shelf = new THREE.Group()
    shelf.position.set(3.35, 0, -2.7)
    world.add(shelf)
    const woodMat = kit.toon(C.wood)
    shelf.add(createToonMesh(new THREE.BoxGeometry(1.7, 0.08, 0.44), woodMat, kit, { name: 'boardTop', position: [0, 2.28, 0] }))
    shelf.add(createToonMesh(new THREE.BoxGeometry(1.7, 0.08, 0.44), woodMat, kit, { name: 'boardBottom', position: [0, 1.52, 0] }))
    shelf.add(createToonMesh(new THREE.BoxGeometry(0.09, 1.5, 0.44), woodMat, kit, { name: 'sideL', position: [-0.8, 1.9, 0] }))
    shelf.add(createToonMesh(new THREE.BoxGeometry(0.09, 1.5, 0.44), woodMat, kit, { name: 'sideR', position: [0.8, 1.9, 0] }))
    const books: Array<[string, number, number]> = [[C.blue, -0.5, 0.16], [C.pink, -0.3, 0.14], [C.yellow, -0.11, 0.18]]
    for (const [color, x, h] of books) {
        shelf.add(createToonMesh(new THREE.BoxGeometry(0.13, h, 0.3), kit.toon(color), kit, {
            name: 'book', position: [x, 2.32 + h / 2, 0.02], outline: 0.004,
        }))
    }
    shelf.add(createToonMesh(new THREE.CylinderGeometry(0.18, 0.14, 0.22, 18), kit.toon(C.pot), kit, {
        name: 'potShelf', position: [0.45, 1.73, 0],
    }))
    shelf.add(createToonMesh(new THREE.SphereGeometry(0.17, 18, 14), kit.toon(C.leaf), kit, {
        name: 'leafShelf', position: [0.45, 1.94, 0], scale: [1, 0.92, 1], outline: 0.006,
    }))
    shelf.add(createToonMesh(new THREE.SphereGeometry(0.11, 16, 12), kit.toon(C.leaf), kit, {
        name: 'leafShelf2', position: [0.62, 1.83, 0.03], outline: 0.005,
    }))
}

/** 三层圆地毯（宠物活动区中心） */
function buildRug(world: THREE.Object3D, kit: PetMaterialKit): void {
    const rug = new THREE.Group()
    rug.position.set(0, 0, 0.35)
    world.add(rug)
    rug.add(createToonMesh(new THREE.CylinderGeometry(2.42, 2.42, 0.045, 44), kit.toon('#D98A82'), kit, {
        name: 'edge', position: [0, 0.022, 0], castShadow: false, receiveShadow: true,
    }))
    rug.add(createToonMesh(new THREE.CylinderGeometry(2.3, 2.3, 0.05, 44), kit.toon(C.rugOuter), kit, {
        name: 'outer', position: [0, 0.03, 0], castShadow: false, receiveShadow: true,
    }))
    rug.add(createToonMesh(new THREE.CylinderGeometry(1.92, 1.92, 0.055, 40), kit.toon(C.rugInner), kit, {
        name: 'inner', position: [0, 0.035, 0], castShadow: false, receiveShadow: true,
    }))
    rug.add(createToonMesh(new THREE.CylinderGeometry(0.95, 0.95, 0.06, 32), kit.toon(C.rugCenter), kit, {
        name: 'center', position: [0, 0.04, 0], castShadow: false, receiveShadow: true,
    }))
}

/** 宠物窝（外圈软垫 + 内垫 + 靠枕） */
function buildBed(world: THREE.Object3D, kit: PetMaterialKit): void {
    const bed = new THREE.Group()
    bed.position.set(2.6, 0, 1.15)
    world.add(bed)
    bed.add(createToonMesh(new THREE.CylinderGeometry(0.88, 0.98, 0.34, 32), kit.toon(C.bedRim), kit, { name: 'rim', position: [0, 0.17, 0] }))
    bed.add(createToonMesh(new THREE.CylinderGeometry(0.72, 0.72, 0.16, 30), kit.toon(C.bedCushion), kit, {
        name: 'cushion', position: [0, 0.26, 0],
    }))
    bed.add(createToonMesh(new THREE.SphereGeometry(0.34, 22, 16), kit.toon('#FFE3C9'), kit, {
        name: 'pillow', position: [-0.2, 0.34, -0.42], scale: [1.15, 0.72, 0.85], outline: 0.006,
    }))
}

/** 斗柜（左侧背景）+ 台灯 */
function buildCabinet(world: THREE.Object3D, kit: PetMaterialKit): void {
    const cabinet = new THREE.Group()
    cabinet.position.set(-3.55, 0, -0.1)
    world.add(cabinet)
    const woodMat = kit.toon(C.wood)
    cabinet.add(createToonMesh(new THREE.BoxGeometry(1.25, 1.35, 1.0), woodMat, kit, { name: 'body', position: [0, 0.675, 0] }))
    cabinet.add(createToonMesh(new THREE.BoxGeometry(1.35, 0.09, 1.1), kit.toon('#D6B084'), kit, { name: 'top', position: [0, 1.39, 0] }))
    const gapMat = kit.toon('#8E6640')
    for (const y of [0.42, 0.92]) {
        cabinet.add(createToonMesh(new THREE.BoxGeometry(1.19, 0.045, 1.02), gapMat, kit, { name: 'drawerGap', position: [0, y, 0] }))
    }
    const knobMat = kit.toon('#FFE0A8')
    cabinet.add(createToonMesh(new THREE.SphereGeometry(0.055, 14, 10), knobMat, kit, { name: 'knob1', position: [0, 0.66, 0.53] }))
    cabinet.add(createToonMesh(new THREE.SphereGeometry(0.055, 14, 10), knobMat, kit, { name: 'knob2', position: [0, 1.14, 0.53] }))
    cabinet.add(createToonMesh(new THREE.CapsuleGeometry(0.035, 0.36, 6, 12), kit.toon(C.woodDark), kit, {
        name: 'lampPole', position: [0.32, 1.64, 0],
    }))
    cabinet.add(createToonMesh(new THREE.ConeGeometry(0.26, 0.34, 20), kit.glow('#FFD99E'), kit, {
        name: 'lampShade', position: [0.32, 1.98, 0], glow: true, castShadow: false,
    }))
}

/** 大绿植（左后角） */
function buildPlant(world: THREE.Object3D, kit: PetMaterialKit): void {
    const plant = new THREE.Group()
    plant.position.set(-3.7, 0, -2.1)
    world.add(plant)
    plant.add(createToonMesh(new THREE.CylinderGeometry(0.42, 0.32, 0.55, 22), kit.toon(C.pot), kit, { name: 'pot', position: [0, 0.275, 0] }))
    plant.add(createToonMesh(new THREE.CapsuleGeometry(0.05, 0.8, 6, 12), kit.toon(C.leafDark), kit, { name: 'stem', position: [0, 1.0, 0] }))
    const leaves: Array<[number, number, number, number]> = [
        [0, 1.45, 0, 0.34], [0.3, 1.25, 0.1, 0.28], [-0.3, 1.22, 0.08, 0.3],
        [0.16, 1.62, -0.08, 0.24], [-0.18, 1.58, -0.06, 0.22],
    ]
    const leafMat = kit.toon(C.leaf)
    for (const [x, y, z, r] of leaves) {
        plant.add(createToonMesh(new THREE.SphereGeometry(r, 18, 14), leafMat, kit, {
            name: 'leaf', position: [x, y, z], scale: [1, 0.72, 1], outline: 0.006,
        }))
    }
}

/** 散落玩具：皮球（带条纹）/ 毛线球 / 小黄鸭 / 骨头；返回皮球供氛围动画引用 */
function buildToys(world: THREE.Object3D, kit: PetMaterialKit): THREE.Group {
    const ball = new THREE.Group()
    ball.position.set(-1.35, 0.19, 1.05)
    ball.name = 'toyBall'
    world.add(ball)
    ball.add(createToonMesh(new THREE.SphereGeometry(0.19, 22, 16), kit.toon(C.blue), kit, { name: 'core', outline: 0.007 }))
    ball.add(createToonMesh(new THREE.SphereGeometry(0.195, 22, 16), kit.toon('#FDF8F0'), kit, {
        name: 'stripe', scale: [0.32, 1.02, 1.02], castShadow: false,
    }))

    const yarn = new THREE.Group()
    yarn.position.set(-1.95, 0.17, 0.5)
    world.add(yarn)
    yarn.add(createToonMesh(new THREE.SphereGeometry(0.17, 20, 14), kit.toon(C.pink), kit, { name: 'core', outline: 0.006 }))
    yarn.add(createToonMesh(new THREE.TorusGeometry(0.17, 0.02, 8, 24), kit.toon('#E0708C'), kit, {
        name: 'ring', rotation: [0.42, 0, 0.31], castShadow: false,
    }))

    const duck = new THREE.Group()
    duck.position.set(2.15, 0, 1.75)
    world.add(duck)
    duck.add(createToonMesh(new THREE.SphereGeometry(0.19, 20, 14), kit.toon(C.yellow), kit, {
        name: 'body', position: [0, 0.16, 0], scale: [1, 0.9, 1.1], outline: 0.007,
    }))
    duck.add(createToonMesh(new THREE.SphereGeometry(0.13, 18, 14), kit.toon(C.yellow), kit, {
        name: 'head', position: [0, 0.36, 0.06], outline: 0.006,
    }))
    duck.add(createToonMesh(new THREE.ConeGeometry(0.055, 0.12, 16), kit.toon(C.duckBeak), kit, {
        name: 'beak', position: [0, 0.34, 0.2], rotation: [Math.PI / 2, 0, 0], outline: 0.004,
    }))

    const bone = new THREE.Group()
    bone.position.set(1.45, 0.1, 2.0)
    world.add(bone)
    const boneMat = kit.toon('#FDF2DE')
    bone.add(createToonMesh(new THREE.CapsuleGeometry(0.06, 0.3, 6, 12), boneMat, kit, {
        name: 'bar', rotation: [0, 0, (74 * Math.PI) / 180], outline: 0.005,
    }))
    bone.add(createToonMesh(new THREE.SphereGeometry(0.09, 16, 12), boneMat, kit, { name: 'knobL', position: [-0.17, 0.04, 0], outline: 0.005 }))
    bone.add(createToonMesh(new THREE.SphereGeometry(0.09, 16, 12), boneMat, kit, { name: 'knobR', position: [0.17, -0.04, 0], outline: 0.005 }))

    return ball
}

/** 前景靠垫（制造前后景层次） */
function buildForeground(world: THREE.Object3D, kit: PetMaterialKit): void {
    const cushionMat = kit.toon(C.cushion)
    world.add(createToonMesh(new THREE.SphereGeometry(0.46, 22, 16), cushionMat, kit, {
        name: 'cushionFront', position: [-1.3, 0.16, 2.45], scale: [1.25, 0.55, 0.95], outline: 0.008, receiveShadow: true,
    }))
    world.add(createToonMesh(new THREE.SphereGeometry(0.3, 20, 14), kit.toon('#EBD3AE'), kit, {
        name: 'cushionSmall', position: [2.0, 0.12, 2.6], scale: [1.2, 0.6, 1], outline: 0.006, receiveShadow: true,
    }))
}

/** 飘浮光点（缓慢上升循环，由引擎驱动） */
function buildOrbs(world: THREE.Object3D, kit: PetMaterialKit): THREE.Mesh[] {
    const seeds: Array<[number, number, number, number]> = [
        [-2.4, 0.9, -1.2, 0.05], [1.7, 1.3, -0.6, 0.042], [-0.9, 1.9, -1.8, 0.048],
        [2.6, 0.7, 0.6, 0.038], [-2.9, 1.7, 0.9, 0.045], [0.6, 2.3, -1.4, 0.04],
        [3.1, 1.8, -2.0, 0.05], [-1.8, 0.6, 1.9, 0.036],
    ]
    const material = kit.glow('#FFF3CE', 0.66)
    const orbs: THREE.Mesh[] = []
    seeds.forEach(([x, y, z, r], index) => {
        const orb = new THREE.Mesh(new THREE.SphereGeometry(r, 12, 8), material)
        orb.position.set(x, y, z)
        orb.name = 'orb' + index
        world.add(orb)
        orbs.push(orb)
    })
    return orbs
}

/** 窗边阳光光斑（地板上斜置的暖光片，呼吸明暗） */
function buildSunBeam(world: THREE.Object3D, kit: PetMaterialKit): THREE.Mesh {
    const beam = createToonMesh(new THREE.BoxGeometry(3.2, 0.02, 2.0), kit.glow(C.sun, 0.3), kit, {
        name: 'sunBeam', position: [0.55, 0.014, -1.35], rotation: [0, (-18 * Math.PI) / 180, 0],
        castShadow: false, receiveShadow: false, glow: true,
    })
    world.add(beam)
    const mesh = beam.children[0] as THREE.Mesh
    mesh.name = 'sunBeam'
    return mesh
}
