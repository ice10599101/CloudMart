import { Color, Node, Vec3 } from 'cc'
import { PartStyle, PetBuilderKit } from './PetBuilderKit'
import { shift } from './PetGameTheme'

/**
 * 温馨宠物房间场景（视觉重构 v3）。
 *
 * 空间分层（相机在 +z 看向 -z，宠物位于地毯中央 z≈0.35）：
 *  - 背景层：后墙 / 踢脚线 / 大窗 + 窗帘 + 阳光光斑 / 挂画 / 挂灯串 / 置物架；
 *  - 中景层：木地板（含板缝） / 三层圆地毯 / 宠物窝 / 斗柜 / 绿植 / 散落玩具；
 *  - 前景层：靠垫、地毯边缘玩具、飘浮光点（由动画层驱动上升），制造纵深。
 *
 * 配色走"暖奶油 + 蜜糖木色 + 柔粉"的低饱和暖色系，与宠物高饱和主色形成对比，
 * 让角色从环境中跳出来；墙面与地板只用大色块 + 细分割线，避免抢戏。
 *
 * 全部零外部资产：引擎原语 + pet-toon 材质程序化构建。
 */

/** 房间可动元素引用（PetGameRoot.update 驱动氛围微动画） */
export interface RoomRefs {
    /** 飘浮光点（缓慢上升循环） */
    orbs: Node[]
    /** 挂灯串灯泡（呼吸闪烁） */
    bulbs: Node[]
    /** 窗边阳光光斑（呼吸增强/减弱） */
    sunBeam: Node
    /** 地毯上的玩具球（轻微滚动感） */
    toyBall: Node
}

/** 房间配色（集中定义，便于整体调色） */
const C = {
    wall: new Color(0xF7, 0xE8, 0xD6, 255),
    wallShade: new Color(0xE6, 0xD2, 0xB8, 255),
    baseboard: new Color(0xCE, 0xA2, 0x74, 255),
    baseboardShade: new Color(0xB4, 0x8A, 0x5E, 255),
    floor: new Color(0xEC, 0xCF, 0xA4, 255),
    floorShade: new Color(0xD8, 0xB4, 0x84, 255),
    floorLine: new Color(0xCA, 0xA4, 0x76, 255),
    rugOuter: new Color(0xEE, 0xAA, 0xA2, 255),
    rugInner: new Color(0xF8, 0xDC, 0xA8, 255),
    rugCenter: new Color(0xFA, 0xC6, 0xC0, 255),
    wood: new Color(0xC8, 0x9C, 0x6E, 255),
    woodDark: new Color(0xAE, 0x82, 0x58, 255),
    bedRim: new Color(0xC2, 0x92, 0x66, 255),
    bedCushion: new Color(0xFF, 0xC9, 0xD6, 255),
    windowFrame: new Color(0xFB, 0xF7, 0xF1, 255),
    glass: new Color(0xAF, 0xDD, 0xF5, 255),
    curtain: new Color(0xF7, 0xC4, 0xC4, 255),
    sun: new Color(0xFF, 0xF1, 0xC8, 255),
    leaf: new Color(0x74, 0xC5, 0x8B, 255),
    leafDark: new Color(0x54, 0xA8, 0x70, 255),
    pot: new Color(0xD9, 0x96, 0x6B, 255),
    book1: new Color(0x74, 0xB6, 0xF0, 255),
    book2: new Color(0xFF, 0x9F, 0xB4, 255),
    book3: new Color(0xFF, 0xD1, 0x66, 255),
    bulb: new Color(0xFF, 0xE2, 0x9E, 255),
    ball: new Color(0x74, 0xB6, 0xF0, 255),
    yarn: new Color(0xFF, 0x9F, 0xB4, 255),
    duck: new Color(0xFF, 0xD1, 0x66, 255),
    duckBeak: new Color(0xF5, 0x9B, 0x4B, 255),
    cushion: new Color(0xF6, 0xE3, 0xC2, 255),
    picture: new Color(0xFD, 0xEF, 0xDC, 255),
    heart: new Color(0xFF, 0x8A, 0xA0, 255),
    orb: new Color(0xFF, 0xF3, 0xCE, 255),
} as const

/** 快速风格：主色 + 可选暗部/描边覆盖 */
function style(color: Color, opts?: { shade?: Color; outline?: number; alpha?: number; glow?: boolean }): PartStyle {
    return {
        color,
        shade: opts?.shade,
        outline: opts?.outline ?? 0,
        alpha: opts?.alpha,
        glow: opts?.glow,
    }
}

/** 构建整个房间（parent 为世界根节点；返回氛围动画引用） */
export function buildRoom(parent: Node, kit: PetBuilderKit): RoomRefs {
    buildFloor(parent, kit)
    buildWalls(parent, kit)
    buildWindow(parent, kit)
    buildWallDecor(parent, kit)
    buildRug(parent, kit)
    buildBed(parent, kit)
    buildCabinet(parent, kit)
    buildPlant(parent, kit)
    const toyBall = buildToys(parent, kit)
    buildForeground(parent, kit)
    const orbs = buildOrbs(parent, kit)
    const bulbs = buildLampString(parent, kit)
    const sunBeam = buildSunBeam(parent, kit)
    return { orbs, bulbs, sunBeam, toyBall }
}

/** 地板：大色块 + 竖向板缝（长条木板感），顶面 y=0 */
function buildFloor(parent: Node, kit: PetBuilderKit): void {
    const root = kit.make3dNode(parent, 'Floor', new Vec3(0, 0, 0))
    kit.boxPart(root, 'Slab', { w: 16, h: 0.2, d: 7.6 }, style(C.floor, { shade: C.floorShade }),
        new Vec3(0, -0.1, 0.4))
    const line = style(C.floorLine, { shade: C.floorLine })
    for (let index = -5; index <= 5; index += 1) {
        kit.boxPart(root, 'Seam', { w: 0.035, h: 0.014, d: 7.6 }, line, new Vec3(index * 1.55, 0.006, 0.4))
    }
    for (const z of [-1.9, -0.4, 1.1, 2.6]) {
        kit.boxPart(root, 'Joint', { w: 16, h: 0.014, d: 0.035 }, line, new Vec3(0, 0.006, z))
    }
}

/** 墙体：后墙 + 左墙 + 踢脚线（背景层次，不做描边避免抢戏） */
function buildWalls(parent: Node, kit: PetBuilderKit): void {
    const root = kit.make3dNode(parent, 'Walls', new Vec3(0, 0, 0))
    const wall = style(C.wall, { shade: C.wallShade })
    const base = style(C.baseboard, { shade: C.baseboardShade })
    kit.boxPart(root, 'Back', { w: 16, h: 4.8, d: 0.3 }, wall, new Vec3(0, 2.4, -3.0))
    kit.boxPart(root, 'BackBase', { w: 16, h: 0.36, d: 0.4 }, base, new Vec3(0, 0.18, -2.93))
    kit.boxPart(root, 'Left', { w: 0.3, h: 4.8, d: 7.6 }, wall, new Vec3(-4.6, 2.4, 0.4))
    kit.boxPart(root, 'LeftBase', { w: 0.4, h: 0.36, d: 7.6 }, base, new Vec3(-4.53, 0.18, 0.4))
}

/** 大窗：窗框 + 透光玻璃（自发光）+ 十字窗棂 + 两侧窗帘 */
function buildWindow(parent: Node, kit: PetBuilderKit): void {
    const root = kit.make3dNode(parent, 'Window', new Vec3(-2.0, 2.45, -2.82))
    const frame = style(C.windowFrame, { shade: shift(C.windowFrame, -0.18) })
    kit.boxPart(root, 'Frame', { w: 2.15, h: 1.75, d: 0.14 }, frame, new Vec3(0, 0, 0))
    kit.boxPart(root, 'Glass', { w: 1.86, h: 1.46, d: 0.06 }, style(C.glass, { glow: true }),
        new Vec3(0, 0, 0.05))
    kit.boxPart(root, 'MullionV', { w: 0.09, h: 1.46, d: 0.1 }, frame, new Vec3(0, 0, 0.06))
    kit.boxPart(root, 'MullionH', { w: 1.86, h: 0.09, d: 0.1 }, frame, new Vec3(0, 0, 0.06))
    kit.boxPart(root, 'Sill', { w: 2.4, h: 0.12, d: 0.34 }, frame, new Vec3(0, -0.93, 0.08))

    // 窗帘：帘杆 + 左右帘布 + 帘头
    const curtain = style(C.curtain, { shade: shift(C.curtain, -0.16) })
    kit.capPart(root, 'Rod', 0.045, 3.5, style(C.woodDark, { shade: shift(C.woodDark, -0.2) }),
        new Vec3(0, 1.06, 0.12), { rot: new Vec3(0, 0, 90) })
    kit.boxPart(root, 'CurtainL', { w: 0.5, h: 2.3, d: 0.18 }, curtain, new Vec3(-1.28, -0.1, 0.12))
    kit.boxPart(root, 'CurtainR', { w: 0.5, h: 2.3, d: 0.18 }, curtain, new Vec3(1.28, -0.1, 0.12))
    kit.boxPart(root, 'Pelmet', { w: 2.9, h: 0.26, d: 0.24 }, curtain, new Vec3(0, 1.02, 0.12))

    // 窗台小摆件：小盆栽
    kit.cylPart(root, 'PotSmall', 0.16, 0.12, 0.2, style(C.pot, { shade: shift(C.pot, -0.2) }),
        new Vec3(0.72, -0.8, 0.12))
    kit.ball(root, 'LeafSmall', 0.15, style(C.leaf, { shade: C.leafDark }), new Vec3(0.72, -0.62, 0.12), {
        scale: new Vec3(1, 0.9, 1),
    })
}

/** 墙面装饰：挂画（含心形图案）+ 挂灯串（灯泡节点）+ 云朵星饰 */
function buildWallDecor(parent: Node, kit: PetBuilderKit): void {
    const root = kit.make3dNode(parent, 'WallDecor', new Vec3(0, 0, 0))

    // 挂画
    const frame = kit.make3dNode(root, 'Painting', new Vec3(1.85, 2.55, -2.8))
    kit.boxPart(frame, 'Frame', { w: 1.5, h: 1.2, d: 0.1 },
        style(C.wood, { shade: C.woodDark }), new Vec3(0, 0, 0))
    kit.boxPart(frame, 'Canvas', { w: 1.26, h: 0.96, d: 0.06 }, style(C.picture, { shade: shift(C.picture, -0.12) }),
        new Vec3(0, 0, 0.04))
    // 心形：两球 + 一锥（朝下的尖）
    kit.ball(frame, 'HeartL', 0.14, style(C.heart, { shade: shift(C.heart, -0.2) }), new Vec3(-0.1, 0.08, 0.07))
    kit.ball(frame, 'HeartR', 0.14, style(C.heart, { shade: shift(C.heart, -0.2) }), new Vec3(0.1, 0.08, 0.07))
    kit.conePart(frame, 'HeartTip', 0.19, 0.26, style(C.heart, { shade: shift(C.heart, -0.2) }),
        new Vec3(0, -0.14, 0.07), { rot: new Vec3(180, 0, 0) })

    // 云朵 + 星星（左上角墙面）
    const cloud = kit.make3dNode(root, 'Cloud', new Vec3(-3.3, 3.5, -2.8))
    kit.ball(cloud, 'c1', 0.26, style(new Color(0xFF, 0xFB, 0xF2, 255), { shade: C.wallShade }),
        new Vec3(-0.24, 0, 0))
    kit.ball(cloud, 'c2', 0.34, style(new Color(0xFF, 0xFB, 0xF2, 255), { shade: C.wallShade }),
        new Vec3(0.15, 0.06, 0))
    kit.ball(cloud, 'c3', 0.22, style(new Color(0xFF, 0xFB, 0xF2, 255), { shade: C.wallShade }),
        new Vec3(0.5, -0.02, 0))
    kit.ball(cloud, 'star', 0.1, style(C.bulb, { glow: true }), new Vec3(0.9, 0.34, 0))

    // 置物架（右后墙）：两层木板 + 书本 + 小盆栽
    const shelf = kit.make3dNode(root, 'Shelf', new Vec3(3.35, 0, -2.7))
    const woodStyle = style(C.wood, { shade: C.woodDark })
    kit.boxPart(shelf, 'BoardTop', { w: 1.7, h: 0.08, d: 0.44 }, woodStyle, new Vec3(0, 2.28, 0))
    kit.boxPart(shelf, 'BoardBottom', { w: 1.7, h: 0.08, d: 0.44 }, woodStyle, new Vec3(0, 1.52, 0))
    kit.boxPart(shelf, 'SideL', { w: 0.09, h: 1.5, d: 0.44 }, woodStyle, new Vec3(-0.8, 1.9, 0))
    kit.boxPart(shelf, 'SideR', { w: 0.09, h: 1.5, d: 0.44 }, woodStyle, new Vec3(0.8, 1.9, 0))
    // 书本（竖放，彩色书脊）
    const books: Array<[Color, number, number]> = [[C.book1, -0.5, 0.16], [C.book2, -0.3, 0.14], [C.book3, -0.11, 0.18]]
    for (const [color, x, h] of books) {
        kit.boxPart(shelf, 'Book', { w: 0.13, h, d: 0.3 }, style(color, { shade: shift(color, -0.22) }),
            new Vec3(x, 2.32 + h / 2, 0.02))
    }
    kit.cylPart(shelf, 'PotShelf', 0.18, 0.14, 0.22, style(C.pot, { shade: shift(C.pot, -0.2) }),
        new Vec3(0.45, 1.62 + 0.11, 0))
    kit.ball(shelf, 'LeafShelf', 0.17, style(C.leaf, { shade: C.leafDark }), new Vec3(0.45, 1.94, 0), {
        scale: new Vec3(1, 0.92, 1),
    })
    kit.ball(shelf, 'LeafShelf2', 0.11, style(C.leaf, { shade: C.leafDark }), new Vec3(0.62, 1.83, 0.03))
}

/** 圆地毯（三层同心圆 + 边缘滚边），宠物活动区中心 */
function buildRug(parent: Node, kit: PetBuilderKit): void {
    const root = kit.make3dNode(parent, 'Rug', new Vec3(0, 0, 0.35))
    kit.cylPart(root, 'Edge', 2.42, 2.42, 0.045, style(shift(C.rugOuter, -0.2), { shade: shift(C.rugOuter, -0.32) }),
        new Vec3(0, 0.022, 0))
    kit.cylPart(root, 'Outer', 2.3, 2.3, 0.05, style(C.rugOuter, { shade: shift(C.rugOuter, -0.14) }),
        new Vec3(0, 0.03, 0))
    kit.cylPart(root, 'Inner', 1.92, 1.92, 0.055, style(C.rugInner, { shade: shift(C.rugInner, -0.14) }),
        new Vec3(0, 0.035, 0))
    kit.cylPart(root, 'Center', 0.95, 0.95, 0.06, style(C.rugCenter, { shade: shift(C.rugCenter, -0.14) }),
        new Vec3(0, 0.04, 0))
}

/** 宠物窝：外圈软垫 + 内垫 + 靠枕（右侧中景） */
function buildBed(parent: Node, kit: PetBuilderKit): void {
    const root = kit.make3dNode(parent, 'PetBed', new Vec3(2.6, 0, 1.15))
    kit.cylPart(root, 'Rim', 0.88, 0.98, 0.34, style(C.bedRim, { shade: shift(C.bedRim, -0.18) }),
        new Vec3(0, 0.17, 0))
    kit.cylPart(root, 'Cushion', 0.72, 0.72, 0.16, style(C.bedCushion, { shade: shift(C.bedCushion, -0.16) }),
        new Vec3(0, 0.26, 0))
    kit.ball(root, 'Pillow', 0.34, style(new Color(0xFF, 0xE3, 0xC9, 255), { shade: new Color(0xE8, 0xC6, 0xA6, 255) }),
        new Vec3(-0.2, 0.34, -0.42), { scale: new Vec3(1.15, 0.72, 0.85) })
}

/** 斗柜（左侧背景）：柜体 + 抽屉线 + 把手 + 台面摆件 */
function buildCabinet(parent: Node, kit: PetBuilderKit): void {
    const root = kit.make3dNode(parent, 'Cabinet', new Vec3(-3.55, 0, -0.1))
    const woodStyle = style(C.wood, { shade: C.woodDark })
    kit.boxPart(root, 'Body', { w: 1.25, h: 1.35, d: 1.0 }, woodStyle, new Vec3(0, 0.675, 0))
    kit.boxPart(root, 'Top', { w: 1.35, h: 0.09, d: 1.1 }, style(shift(C.wood, 0.14), { shade: C.wood }),
        new Vec3(0, 1.39, 0))
    const drawerLine = style(shift(C.woodDark, -0.16), { shade: shift(C.woodDark, -0.2) })
    for (const y of [0.42, 0.92]) {
        kit.boxPart(root, 'DrawerGap', { w: 1.19, h: 0.045, d: 1.02 }, drawerLine, new Vec3(0, y, 0))
    }
    kit.ball(root, 'Knob1', 0.055, style(new Color(0xFF, 0xE0, 0xA8, 255), { shade: C.woodDark }),
        new Vec3(0, 0.66, 0.53))
    kit.ball(root, 'Knob2', 0.055, style(new Color(0xFF, 0xE0, 0xA8, 255), { shade: C.woodDark }),
        new Vec3(0, 1.14, 0.53))
    // 台灯（暖光）
    kit.capPart(root, 'LampPole', 0.035, 0.42, style(C.woodDark, { shade: shift(C.woodDark, -0.2) }),
        new Vec3(0.32, 1.64, 0))
    kit.conePart(root, 'LampShade', 0.26, 0.34, style(new Color(0xFF, 0xD9, 0x9E, 255), { glow: true }),
        new Vec3(0.32, 1.98, 0))
}

/** 大绿植（左后角）：陶盆 + 层叠叶片 */
function buildPlant(parent: Node, kit: PetBuilderKit): void {
    const root = kit.make3dNode(parent, 'Plant', new Vec3(-3.7, 0, -2.1))
    kit.cylPart(root, 'Pot', 0.42, 0.32, 0.55, style(C.pot, { shade: shift(C.pot, -0.2) }),
        new Vec3(0, 0.275, 0))
    kit.capPart(root, 'Stem', 0.05, 0.9, style(C.leafDark, { shade: shift(C.leafDark, -0.2) }),
        new Vec3(0, 1.0, 0))
    const leaves: Array<[number, number, number, number]> = [
        [0, 1.45, 0, 0.34], [0.3, 1.25, 0.1, 0.28], [-0.3, 1.22, 0.08, 0.3],
        [0.16, 1.62, -0.08, 0.24], [-0.18, 1.58, -0.06, 0.22],
    ]
    for (const [x, y, z, r] of leaves) {
        kit.ball(root, 'Leaf', r, style(C.leaf, { shade: C.leafDark }), new Vec3(x, y, z), {
            scale: new Vec3(1, 0.72, 1),
        })
    }
}

/** 散落玩具：皮球 / 毛线球 / 小黄鸭 / 骨头（中景与窝边，避免遮挡宠物正面）；返回皮球供氛围动画引用 */
function buildToys(parent: Node, kit: PetBuilderKit): Node {
    const root = kit.make3dNode(parent, 'Toys', new Vec3(0, 0, 0))
    // 皮球（带白色条纹感：叠加一圈小球）
    const ball = kit.make3dNode(root, 'ToyBall', new Vec3(-1.35, 0.19, 1.05))
    kit.ball(ball, 'Core', 0.19, style(C.ball, { shade: shift(C.ball, -0.24) }), new Vec3(0, 0, 0))
    kit.ball(ball, 'Stripe', 0.195, style(new Color(0xFD, 0xF8, 0xF0, 255), { shade: shift(C.ball, -0.34) }),
        new Vec3(0, 0, 0), { scale: new Vec3(0.32, 1.02, 1.02) })
    // 毛线球
    kit.ball(root, 'Yarn', 0.17, style(C.yarn, { shade: shift(C.yarn, -0.22) }), new Vec3(-1.95, 0.17, 0.5))
    kit.torusPart(root, 'YarnRing', 0.17, 0.02, style(shift(C.yarn, -0.28), { shade: shift(C.yarn, -0.36) }),
        new Vec3(-1.95, 0.17, 0.5), { rot: new Vec3(24, 0, 18) })
    // 小黄鸭
    const duck = kit.make3dNode(root, 'Duck', new Vec3(2.15, 0, 1.75))
    kit.ball(duck, 'Body', 0.19, style(C.duck, { shade: shift(C.duck, -0.2) }), new Vec3(0, 0.16, 0), {
        scale: new Vec3(1, 0.9, 1.1),
    })
    kit.ball(duck, 'Head', 0.13, style(C.duck, { shade: shift(C.duck, -0.2) }), new Vec3(0, 0.36, 0.06))
    kit.conePart(duck, 'Beak', 0.055, 0.12, style(C.duckBeak, { shade: shift(C.duckBeak, -0.2) }),
        new Vec3(0, 0.34, 0.2), { rot: new Vec3(90, 0, 0) })
    // 骨头玩具
    const bone = kit.make3dNode(root, 'Bone', new Vec3(1.45, 0.1, 2.0))
    kit.capPart(bone, 'Bar', 0.06, 0.34, style(new Color(0xFD, 0xF2, 0xDE, 255), { shade: new Color(0xE3, 0xD2, 0xB8, 255) }),
        new Vec3(0, 0, 0), { rot: new Vec3(0, 0, 74) })
    kit.ball(bone, 'KnobL', 0.09, style(new Color(0xFD, 0xF2, 0xDE, 255), { shade: new Color(0xE3, 0xD2, 0xB8, 255) }),
        new Vec3(-0.17, 0.04, 0))
    kit.ball(bone, 'KnobR', 0.09, style(new Color(0xFD, 0xF2, 0xDE, 255), { shade: new Color(0xE3, 0xD2, 0xB8, 255) }),
        new Vec3(0.17, -0.04, 0))
    return ball
}

/** 前景：地毯前缘靠垫（制造前后景层次） */
function buildForeground(parent: Node, kit: PetBuilderKit): void {
    const root = kit.make3dNode(parent, 'Foreground', new Vec3(0, 0, 0))
    kit.ball(root, 'Cushion', 0.46, style(C.cushion, { shade: shift(C.cushion, -0.16) }),
        new Vec3(-1.3, 0.16, 2.45), { scale: new Vec3(1.25, 0.55, 0.95) })
    kit.ball(root, 'CushionSmall', 0.3, style(shift(C.cushion, -0.12), { shade: shift(C.cushion, -0.24) }),
        new Vec3(2.0, 0.12, 2.6), { scale: new Vec3(1.2, 0.6, 1) })
}

/** 飘浮光点（暖白微光，缓慢上升；由 update 循环驱动） */
function buildOrbs(parent: Node, kit: PetBuilderKit): Node[] {
    const root = kit.make3dNode(parent, 'Orbs', new Vec3(0, 0, 0))
    const orbs: Node[] = []
    const seeds: Array<[number, number, number, number]> = [
        [-2.4, 0.9, -1.2, 0.05], [1.7, 1.3, -0.6, 0.042], [-0.9, 1.9, -1.8, 0.048],
        [2.6, 0.7, 0.6, 0.038], [-2.9, 1.7, 0.9, 0.045], [0.6, 2.3, -1.4, 0.04],
        [3.1, 1.8, -2.0, 0.05], [-1.8, 0.6, 1.9, 0.036],
    ]
    seeds.forEach(([x, y, z, r], index) => {
        const orb = kit.ball(root, 'Orb' + index, r, {
            color: C.orb, shade: C.orb, glow: true, alpha: 168, outline: 0,
        }, new Vec3(x, y, z))
        orbs.push(orb)
    })
    return orbs
}

/** 挂灯串：暖黄灯泡沿后墙上方弧线分布（轻微呼吸闪烁） */
function buildLampString(parent: Node, kit: PetBuilderKit): Node[] {
    const root = kit.make3dNode(parent, 'LampString', new Vec3(0, 0, -2.72))
    const bulbs: Node[] = []
    for (let index = 0; index < 9; index += 1) {
        const t = index / 8
        const x = -4.4 + t * 8.8
        const y = 3.72 - Math.sin(t * Math.PI) * 0.42
        const bulb = kit.ball(root, 'Bulb' + index, 0.085, {
            color: C.bulb, shade: shift(C.bulb, -0.08), glow: true, outline: 0,
        }, new Vec3(x, y, 0))
        bulbs.push(bulb)
    }
    return bulbs
}

/** 窗边阳光光斑（地板上斜置的暖光片，轻微呼吸） */
function buildSunBeam(parent: Node, kit: PetBuilderKit): Node {
    const root = kit.make3dNode(parent, 'SunBeam', new Vec3(0, 0, 0))
    return kit.boxPart(root, 'Beam', { w: 3.2, h: 0.02, d: 2.0 }, {
        color: C.sun, shade: C.sun, glow: true, alpha: 70, outline: 0,
    }, new Vec3(0.55, 0.012, -1.35), { rot: new Vec3(0, -18, 0) })
}
