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
    // 明度阶梯（按相对亮度排序，v6 重排）：地板 210 > 地毯 181 > 墙 154 > 木家具 134 > 墙暗部 117
    // 重排原因：v5 的墙 0xE3C49E 与地板 0xD2AD80 亮度只差 3%，打光再准也拉不开层次，
    // 整个画面陷在一条极窄的明度带里 → 读作"塑料"。
    wall: new Color(0xC6, 0x9A, 0x70, 255),
    wallShade: new Color(0x7E, 0x56, 0x36, 255),
    baseboard: new Color(0xB4, 0x88, 0x5C, 255),
    baseboardShade: new Color(0x8E, 0x67, 0x40, 255),
    // 地板：v6 从 0xE4D2B8 压到 0xCBB495 —— 地板是全亮面（ndl 0.78 → lit 1.0），
    // 再叠加天光与反弹光后原色会冲到 ~240 直接过曝发白，暖燕麦底子全丢。
    floor: new Color(0xCB, 0xB4, 0x95, 255),
    floorShade: new Color(0xA0, 0x8B, 0x6E, 255),
    floorLine: new Color(0x8E, 0x76, 0x58, 255),
    rugOuter: new Color(0xE0, 0xA0, 0x99, 255),
    rugInner: new Color(0xE8, 0xC8, 0x94, 255),
    // 中心圆略深于角色主体色：让奶油白宠物在浅色地毯上有清晰的明度层级
    rugCenter: new Color(0xD6, 0xA0, 0x99, 255),
    // 接触阴影用色（暖褐，绝不用黑：冷黑阴影在暖色空间里会发脏）
    shadow: new Color(0x6B, 0x4A, 0x33, 255),
    wood: new Color(0xAF, 0x7C, 0x4C, 255),
    woodDark: new Color(0x8B, 0x62, 0x40, 255),
    bedRim: new Color(0xA8, 0x77, 0x48, 255),
    bedCushion: new Color(0xF6, 0xBF, 0xCE, 255),
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

/**
 * 快速风格：主色 + 可选暗部/描边覆盖。
 *
 * `plain` 用于需要**标准 alpha 混合**的部件（光点/阳光片/玻璃）：走引擎内置材质
 * （自定义 effect 的 transparent 技术在本构建链下会丢失材质属性）。
 */
function style(color: Color, opts?: {
    shade?: Color; outline?: number; alpha?: number; glow?: boolean; plain?: boolean
}): PartStyle {
    return {
        color,
        shade: opts?.shade,
        outline: opts?.outline ?? 0,
        alpha: opts?.alpha,
        glow: opts?.glow,
        plain: opts?.plain,
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
    buildContactShadows(parent, kit)
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
            color: C.orb, shade: C.orb, glow: true, alpha: 168, outline: 0, plain: true,
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
/**
 * 家具与道具的接触阴影 + 墙脚环境遮蔽。
 *
 * 本工程**没有实时阴影**（场景无光源节点、ShadowsInfo 关闭，pet-toon 也是自研着色器
 * 不采样阴影贴图），所以任何贴地物件都不会在地板上留影 —— 结果是所有家具都"浮"着。
 * 这里用柔边贴花逐个补：暖褐色 + 径向 alpha 渐变，叠在物体正下方的地板上。
 *
 * ⚠️ 高度必须按**落点所在的那一层**给：地毯是逐层叠起来的圆盘
 * （Edge 顶 ≈0.045 / Outer ≈0.055 / Inner ≈0.063 / Center ≈0.07，圆心在 z=0.35、半径 2.42），
 * 所以落在地毯上的物件（球、磨牙棒）阴影必须抬到地毯面之上，
 * 否则会被地毯整个盖住 —— 第一版全放在 y=0.009，结果一个都看不见。
 *
 * 摆位与各 build*() 一一对应（改摆位时必须同步改这里）。
 */
function buildContactShadows(parent: Node, kit: PetBuilderKit): void {
    const root = kit.make3dNode(parent, 'ContactShadows', new Vec3(0, 0, 0))
    const blob = (name: string, rx: number, rz: number, x: number, y: number, z: number, alpha: number): void => {
        kit.decal(root, name, [rx, 0.008, rz], {
            color: C.shadow, shade: C.shadow, alpha, soft: [0.02, 1.0],
        }, new Vec3(x, y, z))
    }
    // 地毯：圆心 (0, 0.35)、半径 2.42；Inner 半径 1.92
    blob('ShadowCabinet', 1.15, 0.62, -3.55, 0.009, -0.10, 78)   // 柜子（裸地板）
    blob('ShadowPlant', 0.44, 0.44, -3.70, 0.009, -2.10, 66)     // 盆栽（裸地板）
    blob('ShadowBed', 1.05, 0.78, 2.60, 0.009, 1.15, 70)         // 猫窝（裸地板）
    blob('ShadowDuck', 0.32, 0.24, 2.15, 0.009, 1.75, 70)        // 小黄鸭（裸地板）
    blob('ShadowBall', 0.28, 0.28, -1.35, 0.066, 1.05, 74)       // 玩具球（地毯 Inner 面之上）
    blob('ShadowBone', 0.38, 0.16, 1.45, 0.058, 2.00, 62)        // 磨牙棒（地毯 Outer 面之上）
    // 墙脚环境遮蔽：墙与地板交界处的暗带，是"空间感"最廉价也最有效的一笔
    blob('AOBackWall', 5.20, 0.55, 0.00, 0.012, -2.45, 58)       // 后墙（墙面 z=-2.85）
    blob('AOLeftWall', 0.52, 4.20, -4.10, 0.012, 0.40, 46)       // 左墙（墙面 x=-4.45）
}

/**
 * 窗光落在地板上的柔光斑（"光落地"是判断一个虚拟空间是否真实最有效的线索）。
 *
 * 旧实现是一块 alpha 70 的**硬边**方板（boxPart + plain 引擎材质）——
 * 在没有实时阴影的场景里，它读作"地上贴了一块白斑"而不是光，反而加重了假。
 * 现改为 `kit.decal`：单位椭球把归一化径向距离写进 uv.x，由 pet-toon 的 decalCtrl
 * 生成径向 alpha 渐变，压扁成地面椭圆后边缘自然化开；长轴按窗光入射方向
 * （世界 +x/+z，即右前方）旋转对齐 —— 光斑的朝向必须与主光一致，否则方向感互相打架。
 * 两层叠加（主斑 + 外圈光晕）避免单层椭圆露出边界。
 *
 * 落点按几何反推：窗在 (-2.0, 2.45, -2.85)，主光行进方向 (0.46,-0.56,0.69)，
 * 落到 y=0 的地面时为 t = 2.45/0.56 ≈ 4.38 → (0.0, 0, 0.17)。
 * 那个点正好是地毯中心（宠物站位），所以光斑会被地毯吃掉大部分 ——
 * 因此这里把光斑放在地毯边缘外的裸地板上（左后），既在画面里看得见，也还在窗光的来向上。
 */
function buildSunBeam(parent: Node, kit: PetBuilderKit): Node {
    const root = kit.make3dNode(parent, 'SunBeam', new Vec3(0, 0, 0))
    kit.decal(root, 'Pool', [2.30, 0.012, 1.35], {
        color: C.sun, shade: C.sun, alpha: 132, soft: [0.06, 1.0],
    }, new Vec3(-1.55, 0.014, -1.70), new Vec3(0, -56, 0))
    kit.decal(root, 'Halo', [3.30, 0.010, 2.00], {
        color: C.sun, shade: C.sun, alpha: 54, soft: [0.12, 1.0],
    }, new Vec3(-1.05, 0.011, -1.35), new Vec3(0, -56, 0))
    return root
}
