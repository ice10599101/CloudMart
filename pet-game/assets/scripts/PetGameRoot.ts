import {
    _decorator,
    Animation,
    AnimationClip,
    Camera,
    Color,
    Component,
    DirectionalLight,
    director,
    EffectAsset,
    instantiate,
    Layers,
    Material,
    Node,
    Prefab,
    SkeletalAnimation,
    Texture2D,
    UITransform,
    Vec3,
    Vec4,
    resources,
    screen,
} from 'cc'
import { HostToGame, PetDisplayState, PetGameBridge } from './PetGameBridge'
import { PetBuilderKit } from './PetBuilderKit'
import { PetEffects } from './PetEffects'
import { buildRoom, RoomRefs } from './PetRoomBuilder'

const { ccclass } = _decorator

/**
 * 家园场景主组件（视觉重构 v9 · 水果宠物打样）。
 *
 * 结构：
 *  - 3D 世界：房间（PetRoomBuilder，统一走 pet-toon）+ 宠物（外部绑定模型，见下）
 *  - 相机机位（横屏 room / 竖屏 portrait 自动选择）
 *  - 房间氛围动画（光点上升 / 灯泡呼吸 / 光斑呼吸 / 玩具轻摆）
 *  - 2D 覆盖层：特效粒子 + 世界坐标投影
 *  - 通信桥（契约冻结，见 PetGameBridge）
 *
 * 宠物：五果阵容 = 西瓜 / 火龙果 / 橘子 / 草莓 / 蓝莓（用户定稿 2026-09-28）。
 * 打样第一只是**草莓**（tools/fruit-pipeline/build_strawberry.py 一条龙产出）：
 *  - 水滴闭合曲面 + 贴图种籽 + 五瓣萼片 + 3D 眼球/嘴/腮红/围巾
 *  - 4 骨轻绑定（Body/Calyx/EyeL/EyeR），三条剪辑：Idle 4.00s（呼吸+微倾+萼片漂摆）、
 *    Happy 1.67s（下蹲→起跳→滞空笑眼→落地挤压→回弹）、Blink 0.25s
 *  - 挤压/拉伸以地面为锚（Body 骨头埋在脚下），是水果动感的核心
 *
 * 职责边界（不变）：只做展示与动画，数值全部来自宿主下发的 PetDisplayState，
 * 用户操作只回传 intent。契约一个字段都没改。
 */

/**
 * 宠物模型资源路径（相对 assets/resources/）。
 *
 * ⚠️ glTF 导入后主资源（gltf-scene）的子资源名等于文件名本身，
 * 所以资源库注册的路径是 `目录/文件名/文件名`（猫版实测确认的规则，水果沿用）。
 */
const PET_MODEL_PATH = 'models/fruit/strawberry/strawberry'
/** 模型所在目录（剪辑子资源按 `目录/剪辑名` 取） */
const PET_MODEL_DIR = 'models/fruit/strawberry'
/** 需要用到的剪辑名。缺哪个就跳过哪个，不能因为少一条剪辑就让整只宠物不出现。 */
const PET_CLIPS = ['Idle', 'Happy', 'Blink']
/**
 * 模型缩放：草莓在 Blender 里全高 0.95（体 0.82 + 萼片），
 * 1.5 倍 → 全高约 1.43，略矮于旧猫（1.53），坐得更"墩"，与房间机位标定匹配（?probe=1 复核）。
 */
const PET_MODEL_SCALE = 1.5
/** 宠物身高（世界单位），供构图探针使用 */
const PET_HEIGHT = 0.95 * PET_MODEL_SCALE
/** 宠物站位（地毯中央；与房间里的软影、玩具球对齐） */
const PET_POS = new Vec3(0, 0, 0.35)

/**
 * 相机机位。
 *
 * 主视角：轻微俯视的 3/4 视角，脚底落在地板上、头顶留出呼吸空间；
 * 比 v5 后退约 12% 把柜子/盆栽/窝/玩具收进画面 —— 一个"住着人"的空间需要生活痕迹。
 * 竖屏另给一组：竖屏可视横向范围窄，沿用横屏机位会让角色横向顶边。
 */
const CAMERA_SHOT = {
    room: { pos: [1.30, 1.36, 3.52], target: [0, 0.60, 0.10] },
    portrait: { pos: [1.98, 1.48, 5.36], target: [0, 0.56, 0.16] },
    front: { pos: [0, 0.86, 3.25], target: [0, 0.74, 0.20] },
    q34: { pos: [-2.00, 1.00, 2.80], target: [0, 0.76, 0.20] },
} as const

/** 纯色背景（?plain=1 验收模式：去掉房间，只留角色自证轮廓与材质） */
const PLAIN_BG = new Color(0xCF, 0xC9, 0xD6, 255)

/**
 * 头部世界坐标（特效锚点）。
 *
 * 由模型坐标系反推：双眼中心 Blender 坐标 (0, -0.30, 0.54)，导出为 glTF（Y 向上）
 * 后变 (0, 0.54, 0.30)，乘缩放 1.5 → (0, 0.81, 0.45)。
 */
const HEAD_OFFSET = new Vec3(0, 0.81, 0.45)

@ccclass('PetGameRoot')
export class PetGameRoot extends Component {

    private readonly bridge = new PetGameBridge()
    private readonly ccRuntime = (globalThis as unknown as { cc: never }).cc

    private kit: PetBuilderKit | null = null
    private world3d: Node | null = null
    private room: RoomRefs | null = null
    private camera3d: Camera | null = null
    private rootTransform: UITransform | null = null

    private effects: PetEffects | null = null

    /** 宠物节点（模型异步加载完成前为 null） */
    private petNode: Node | null = null
    private petAnim: SkeletalAnimation | Animation | null = null
    /** 最近一次下发的状态：模型加载是异步的，到位后要用它补播正确的动画 */
    private pendingPet: PetDisplayState | null = null
    /** 0.35~1.0：数值低时把待机动作放慢（蔫、没精神），是全片唯一的"状态→动画"映射 */
    private speedScale = 1
    private blinkTimer = 3.5
    private blinkReady = false

    private plain = false
    private shot = 'room'
    private probing = false
    /** ?rawmat=1：跳过 pet-toon 材质替换（诊断蒙皮/材质问题用，保留 glTF 原材质） */
    private rawMat = false
    private toonAsset: EffectAsset | null = null

    private time = 0
    private roomTime = 0
    private readonly orbSeeds: number[] = []

    start(): void {
        const params = new URLSearchParams(window.location.search)
        this.plain = params.get('plain') === '1'
        this.probing = params.get('probe') === '1'
        this.shot = params.get('shot')
            || (window.innerHeight > window.innerWidth ? 'portrait' : 'room')

        this.rootTransform = this.node.getComponent(UITransform)
        this.kit = new PetBuilderKit(this.ccRuntime)
        this.rawMat = params.get('rawmat') === '1'

        // 自定义材质（pet-toon）必须先加载：EffectAsset.get 只能查到已加载的资产，
        // 若在加载完成前构建，所有部件会静默退化成引擎内置材质。
        resources.load('effects/pet-toon', EffectAsset, (error, asset) => {
            console.log(`[pet-probe] resources.load pet-toon: err=${error ? String(error) : 'none'} ` +
                `asset=${asset ? asset.name : 'null'}`)
            if (!error && asset) {
                this.toonAsset = asset
                this.kit!.setToonEffect(asset)
                console.log(`[pet-probe] toon ready: isToon=${this.kit!.isToon}`)
            } else {
                console.warn('[pet-game] pet-toon 加载失败，材质回退为内置材质', error)
            }
            this.buildScene(params)
        })
    }

    private buildScene(params: URLSearchParams): void {
        this.buildWorld()
        this.buildOverlay()
        this.bindBridge()
        if (!this.plain) {
            this.buildPetShadow()
            this.loadPet()
        }

        if (this.plain) {
            this.node.active = false
        }
        if (params.get('probe') === '1') {
            this.probeFraming()
        }
        if (params.get('probeMat') === '1') {
            this.probeMaterials()
        }

        this.bridge.send({ source: 'pet-game', type: 'ready' })
    }

    // ---------------- 宠物（外部绑定模型） ----------------

    /**
     * 加载并实例化宠物模型。
     *
     * 注意这是**异步**的：`ready` 会先发给宿主，模型可能稍后才到位。
     * 因此服务端下发的状态先存进 `pendingPet`，模型就绪后立刻补播正确动画 ——
     * 否则会出现"模型加载出来了但站着不动"或"低状态还蹦得很欢"。
     */
    private loadPet(): void {
        resources.load(PET_MODEL_PATH, Prefab, (error, prefab) => {
            if (error || !prefab) {
                console.warn('[pet-game] 宠物模型加载失败，场景保持无角色状态', error)
                return
            }
            const node = instantiate(prefab)
            node.name = 'Pet'
            node.setScale(PET_MODEL_SCALE, PET_MODEL_SCALE, PET_MODEL_SCALE)
            node.setPosition(PET_POS)
            this.world3d!.addChild(node)
            this.petNode = node

            if (this.probing) {
                this.dumpTree(node, 0)
            }
            if (!this.rawMat) {
                this.configurePetMaterials(node)
            }

            // 逐条加载剪辑。
            // ⚠️ 不能用 `resources.load([多条路径], ...)` —— 它是**全有或全无**：
            // 只要有一条路径不存在，整批都失败，结果整只宠物静止不动。猫版已实测踩到。
            const clipPaths = PET_CLIPS.map(name => `${PET_MODEL_DIR}/${name}`)
            const loaded: AnimationClip[] = []
            let remaining = clipPaths.length
            const settle = (): void => {
                remaining -= 1
                if (remaining > 0) {
                    return
                }
                this.attachClips(node, loaded)
            }
            for (const path of clipPaths) {
                resources.load(path, AnimationClip, (clipError, clip) => {
                    if (clipError || !clip) {
                        console.warn(`[pet-game] 剪辑缺失（跳过）: ${path}`)
                    } else {
                        loaded.push(clip)
                    }
                    settle()
                })
            }
        })
    }

    /**
     * 把剪辑挂到宠物根节点上并起播。
     *
     * 组件必须挂在 `Pet`（prefab 根）而不是 `FruitRig`：剪辑里的轨道路径是
     * `FruitRig/Body/...`，从根解析才匹配；挂到 FruitRig 上会整体少一层，全部绑不上。
     */
    private attachClips(node: Node, list: AnimationClip[]): void {
        if (!list.length) {
            console.warn('[pet-game] 没有任何可用剪辑，宠物保持静止')
            return
        }
        const anim = node.addComponent(SkeletalAnimation)
        // 用实时骨骼动画：导入的 glTF 剪辑没有烘焙贴图动画，开着会走空分支
        anim.useBakedAnimation = false
        anim.clips = list
        const idle = list.find(c => c.name === 'Idle')
        if (idle) {
            anim.defaultClip = idle
        }
        console.log(`[pet-probe] pet clips=[${list.map(c => c.name).join(', ')}] ` +
            `default=${idle ? idle.name : 'null'}`)
        this.blinkReady = list.some(c => c.name === 'Blink')
        this.petAnim = anim
        this.playClip('Idle', true)
        this.applyStatsToAnimation()
    }

    /** 打印宠物节点树与各节点组件（?probe=1）—— glTF 导入的层级只能靠实测，不能猜 */
    private dumpTree(node: Node, depth: number): void {
        const names: string[] = []
        for (const c of node.components) {
            let extra = ''
            const model = c as unknown as {
                material?: {
                    effectAsset?: { name?: string }
                    getProperty?: (k: string) => unknown
                }
                skinningRoot?: unknown
            }
            if (model.material && model.material.effectAsset) {
                extra = ` effect=${model.material.effectAsset.name}`
                const get = model.material.getProperty
                if (get) {
                    const tex = get.call(model.material, 'mainTexture')
                    const albedo = get.call(model.material, 'albedo')
                    extra += ` tex=${tex ? (tex as { name?: string }).name : 'null'}`
                    extra += ` albedo=${albedo ? JSON.stringify(albedo) : 'null'}`
                }
            }
            if (model.skinningRoot) {
                extra += ' [skinned]'
            }
            names.push(c.constructor.name + extra)
        }
        console.log(`[pet-probe] ${'  '.repeat(depth)}${node.name} layer=${node.layer} [${names.join('+') || '-'}]`)
        for (const child of node.children) {
            this.dumpTree(child, depth + 1)
        }
    }

    /**
     * 把宠物换成**和房间同一套** pet-toon 材质（颜色各自保留）。
     *
     * 为什么不沿用 glTF 导入的 `builtin-standard`：实测它在场景里渲染成惨白一片、毫无体积
     * （引擎 PBR 光照在这个 headless 管线下没有按预期参与计算，猫版已逐一排查过）。
     * 让宠物和房间共用同一个自研着色器 —— 光照语言一致、视觉完全统一，而且完全可控。
     *
     * 每块网格保留自己的 GLB 内嵌 albedo（草莓体表=种籽贴图，萼片/眼/围巾=纯色小贴图），
     * 只换着色器不换颜色。暗部色/轮廓色按草莓的玫瑰红重新调过。
     *
     * 蒙皮：pet-toon 的顶点着色器走 `CCVertInput(In)`，引擎按模型的蒙皮信息自动注入
     * `CC_USE_SKINNING`，无需手工处理。
     */
    private configurePetMaterials(node: Node): void {
        const visit = (n: Node): void => {
            for (const comp of n.components) {
                const renderer = comp as unknown as {
                    material?: Material
                    setMaterial?: (mat: Material, index?: number) => void
                }
                const src = renderer.material
                if (!src) {
                    continue
                }
                const embedded = src.getProperty('mainTexture') as Texture2D | null
                const toon = new Material()
                try {
                    toon.initialize({ effectAsset: this.toonAsset!, technique: 0 })
                    toon.setProperty('mainColor', new Color(255, 255, 255, 255))
                    if (embedded) {
                        toon.setProperty('mainTexture', embedded)
                    }
                    // 暗部**深玫瑰灰**：草莓饱和度高，暗部偏冷会发灰、偏暖会发橙，
                    // 用带玫瑰倾向的暖灰保住"红而不焦"（猫版的暖灰 166,148,130 在这里会脏）
                    toon.setProperty('shadeColor', new Color(190, 118, 130, 255))
                    toon.setProperty('shadeCtrl', new Vec4(0.72, 0.12, 0.16, 0.12))
                    toon.setProperty('outlineCtrl', new Vec4(0.0035, 0, 0, 0))
                    toon.setProperty('outlineColor', new Color(128, 72, 84, 255))
                    // 高光略强略聚（软陶/果蜡质感）
                    toon.setProperty('furCtrl', new Vec4(0.16, 2.0, 0.06, 10.0))
                    // 主光方向：与房间一致（左前上主光 + 右前下冷补光），猫版验证过的参数
                    toon.setProperty('lightDir', new Vec4(-0.85, 0.30, 0.32, 0.0))
                    toon.setProperty('fillDir', new Vec4(0.42, -0.18, 0.86, 0.0))
                    toon.setProperty('lightCtrl', new Vec4(0.88, 0.28, 0.14, 0.14))
                    // ⚠️ mapCtrl 必须放在**所有 setProperty 之后**：材质 uniform 在首次绑定后
                    // 才同步"最后一次写入"的值（猫版已实测多轮）
                    toon.setProperty('mapCtrl', new Vec4(1, 0, 0, 1))
                    // ⚠️ 必须走 setMaterial 显式替换：模型是异步加载的，首帧可能已经渲过，
                    // `.material = toon` 赋值不会触发蒙皮网格的渲染侧重绑
                    renderer.setMaterial!(toon, 0)
                } catch (error) {
                    console.warn('[pet-game] 宠物 pet-toon 材质初始化失败，保留原材质', error)
                    continue
                }
                const texBack = toon.getProperty('mainTexture') as Texture2D | null
                console.log(`[pet-probe] pet material ${n.name}: pet-toon ` +
                    `tex=${texBack ? (texBack as unknown as { uuid?: string }).uuid : 'null'}`)
            }
            for (const child of n.children) {
                visit(child)
            }
        }
        visit(node)
    }

    /** 播片：名字不存在时静默回落（模型可能还没带上该剪辑，不能因此崩掉整场） */
    private playClip(name: string, loop: boolean): void {
        const anim = this.petAnim
        if (!anim) {
            return
        }
        const has = anim.clips.some(c => c.name === name)
        if (!has) {
            return
        }
        const state = anim.getState(name)
        if (state) {
            state.speed = this.speedScale
        }
        anim.play(name)
        if (!loop && anim.defaultClip) {
            // 单次动作播完回到待机
            this.scheduleOnce(() => this.playClip('Idle', true), anim.getState(name)?.duration ?? 1)
        }
    }

    /** 用服务端数值决定待机强度：越虚弱，动作越慢（唯一的"状态→动画"映射） */
    private applyStatsToAnimation(): void {
        const pet = this.pendingPet
        if (!pet) {
            return
        }
        const worst = Math.min(
            pet.maxHp > 0 ? pet.hp / pet.maxHp : 1,
            pet.hunger / 100, pet.happiness / 100, pet.energy / 100, pet.cleanliness / 100,
        )
        // 0.45(极差) ~ 1.0(健康)：线性但夹住下界，太慢会看起来卡住
        this.speedScale = Math.max(0.45, Math.min(1, 0.45 + worst * 0.55))
        const anim = this.petAnim
        if (anim) {
            for (const clip of anim.clips) {
                const state = anim.getState(clip.name)
                if (state) {
                    state.speed = this.speedScale
                }
            }
        }
    }

    private get headWorld(): Vec3 {
        return new Vec3(PET_POS.x + HEAD_OFFSET.x, HEAD_OFFSET.y, PET_POS.z + HEAD_OFFSET.z)
    }

    // ---------------- 探针 ----------------

    private probeFraming(): void {
        this.scheduleOnce(() => {
            const camera = this.camera3d
            if (!camera) {
                console.log('[pet-probe] camera missing')
                return
            }
            const foot = camera.worldToScreen(new Vec3(PET_POS.x, 0, PET_POS.z), new Vec3())
            const top = camera.worldToScreen(new Vec3(PET_POS.x, PET_HEIGHT, PET_POS.z), new Vec3())
            const screenHeight = screen.windowSize.height
            console.log(`[pet-probe] cam=${camera.node.position.toString()} fov=${camera.fov} ` +
                `screen=${screenHeight}px foot=(${foot.x.toFixed(1)},${foot.y.toFixed(1)}) ` +
                `top=(${top.x.toFixed(1)},${top.y.toFixed(1)}) ` +
                `height=${Math.abs(top.y - foot.y).toFixed(1)}px ` +
                `ratio=${(Math.abs(top.y - foot.y) / screenHeight * 100).toFixed(1)}%`)
        }, 1.2)
    }

    private probeMaterials(): void {
        this.scheduleOnce(() => {
            const registry = (EffectAsset as unknown as { getAll?: () => unknown }).getAll?.()
            const list: EffectAsset[] = registry instanceof Map
                ? Array.from(registry.values() as Iterable<EffectAsset>)
                : (Array.isArray(registry) ? registry as EffectAsset[] : [])
            console.log(`[pet-probe] loaded effects (${list.length}): ` +
                list.map(entry => entry && entry.name).join(' | '))
            const visit = (node: Node): void => {
                for (const comp of node.components) {
                    const model = comp as unknown as {
                        mesh?: unknown
                        material?: { passes?: unknown[]; effectAsset?: { name?: string } }
                    }
                    if (!model.mesh || !model.material) {
                        continue
                    }
                    console.log(`[pet-probe] mat ${node.name} effect=${model.material.effectAsset?.name} ` +
                        `passes=${model.material.passes ? model.material.passes.length : -1}`)
                }
                for (const child of node.children) {
                    visit(child)
                }
            }
            if (this.petNode) {
                visit(this.petNode)
            }
        }, 1.5)
    }

    update(dt: number): void {
        const step = Math.min(dt, 0.05)
        this.time += step
        this.roomTime += step
        this.roomAmbience(step)
        this.driveBlink(step)
    }

    /**
     * 眨眼：随机间隔触发。
     * 剪辑存在时才跑（模型可能没带上 Blink），否则会每几秒白播一次。
     */
    private driveBlink(dt: number): void {
        if (!this.blinkReady || !this.petAnim) {
            return
        }
        this.blinkTimer -= dt
        if (this.blinkTimer > 0) {
            return
        }
        this.blinkTimer = 2.4 + Math.random() * 3.6
        const anim = this.petAnim
        const state = anim.getState('Blink')
        if (state) {
            state.speed = 1
        }
        anim.play('Blink')
    }

    onDestroy(): void {
        this.bridge.dispose()
    }

    // ---------------- 场景构建 ----------------

    private buildWorld(): void {
        const kit = this.kit!
        const scene = this.node.scene
        this.world3d = kit.make3dNode(scene, 'World3D', new Vec3(0, 0, 0))
        this.room = this.plain ? null : buildRoom(this.world3d, kit)
        if (!this.plain) {
            this.buildLighting(scene)
        }

        const cameraNode = scene.getChildByName('Main3DCamera')
        this.camera3d = cameraNode ? cameraNode.getComponent(Camera) : null
        if (cameraNode && this.camera3d) {
            const shot = CAMERA_SHOT[this.shot as keyof typeof CAMERA_SHOT] || CAMERA_SHOT.room
            cameraNode.setPosition(shot.pos[0], shot.pos[1], shot.pos[2])
            cameraNode.lookAt(new Vec3(shot.target[0], shot.target[1], shot.target[2]), new Vec3(0, 1, 0))
            this.camera3d.clearColor = this.plain ? PLAIN_BG : new Color(0x6E, 0x5A, 0x66, 255)
        }
    }

    /**
     * 场景光照 —— **只服务宠物**。
     *
     * 房间所有部件走自研 `pet-toon`（自带写死的三点光，不依赖引擎光源）；
     * 宠物 glTF 材质必须靠引擎光源才出体积。补一盏平行光对齐 pet-toon 主光方向，
     * 因为 pet-toon 无视引擎光源，这一步对房间**零影响**。
     */
    private buildLighting(scene: Node): void {
        // 主光（暖，左前上）：**必须从镜头这一侧来**（猫版实证：放窗户那侧只照亮背面）
        const key = new Node('PetKeyLight')
        key.layer = Layers.Enum.DEFAULT
        scene.addChild(key)
        key.setPosition(-3.2, 2.5, 2.3)
        key.lookAt(new Vec3(0, 0.62, 0.35), new Vec3(0, 1, 0))
        const keyLight = key.addComponent(DirectionalLight)
        keyLight.color = new Color(255, 231, 198)
        // ⚠️ 强度必须压得低：无色调映射管线线性值直接 clip，过亮会把体积全顶丢
        keyLight.illuminance = 34000

        // 补光（冷，右前下）：压住暗部、给一点冷暖对比
        const fill = new Node('PetFillLight')
        fill.layer = Layers.Enum.DEFAULT
        scene.addChild(fill)
        fill.setPosition(3.0, 0.9, 2.0)
        fill.lookAt(new Vec3(0, 0.55, 0.35), new Vec3(0, 1, 0))
        const fillLight = fill.addComponent(DirectionalLight)
        fillLight.color = new Color(196, 208, 255)
        fillLight.illuminance = 7000

        // 环境光：HDR / LDR 两份字段都要写（猫版实证：只写 LDR 在 HDR 分支下不生效；
        // HDR 字段在部分引擎形态下是只读 getter，直接赋值会抛 TypeError 中断 buildScene）
        const ambient = director.getScene()!.globals.ambient
        const a = ambient as unknown as Record<string, unknown>
        const skyLDR = new Color(200, 205, 215)
        const groundLDR = new Color(140, 112, 84)
        ambient.skyColor = skyLDR
        ambient.groundAlbedo = groundLDR
        ambient.skyIllum = 1400
        const hdrWritable = (() => {
            const desc = Object.getOwnPropertyDescriptor(a, 'skyColorHDR')
                ?? Object.getOwnPropertyDescriptor(Object.getPrototypeOf(ambient), 'skyColorHDR')
            return !desc || desc.set !== undefined
        })()
        if (hdrWritable) {
            a.skyColorHDR = skyLDR
            a.groundAlbedoHDR = groundLDR
            a.skyIllumHDR = 1400
        } else {
            console.warn('[pet-game] HDR ambient 只读（preview 形态），跳过 HDR 双写')
        }
        console.log(`[pet-probe] lighting: key=${keyLight.illuminance} fill=${fillLight.illuminance} ` +
            `ambLDR=${ambient.skyIllum} ambHDR=${String(a.skyIllumHDR)} hdrWritable=${hdrWritable}`)
    }

    /**
     * 宠物脚下的接触阴影。
     * 场景没开实时阴影（房间各部件靠手工软影补），宠物也必须补一个，
     * 否则它会"浮"在地毯上。用 kit.decal 的柔边贴花。草莓 footprint 略小于猫。
     */
    private buildPetShadow(): Node {
        const root = this.kit!.make3dNode(this.world3d!, 'PetShadow', new Vec3(0, 0, 0))
        this.kit!.decal(root, 'Blob', [0.58, 0.010, 0.45], {
            color: new Color(0x6B, 0x4A, 0x33, 255),
            shade: new Color(0x6B, 0x4A, 0x33, 255),
            alpha: 82,
            soft: [0.02, 1.0],
        }, new Vec3(PET_POS.x, 0.075, PET_POS.z))
        return root
    }

    private buildOverlay(): void {
        this.effects = new PetEffects(
            (name: string, x: number, y: number) => this.makeUiNode(name, x, y),
            (world: Vec3) => this.project(world),
        )
    }

    private makeUiNode(name: string, x: number, y: number): Node {
        const node = new Node(name)
        node.layer = Layers.Enum.UI_2D
        node.addComponent(UITransform).setContentSize(8, 8)
        node.setPosition(x, y, 0)
        this.node.addChild(node)
        return node
    }

    private project(world: Vec3): Vec3 {
        if (!this.camera3d || !this.rootTransform) {
            return new Vec3(0, 0, 0)
        }
        const screenPos = this.camera3d.worldToScreen(world, new Vec3())
        const windowSize = screen.windowSize
        const canvasSize = this.rootTransform.contentSize
        if (!windowSize.width || !windowSize.height || !canvasSize.width) {
            return new Vec3(0, 0, 0)
        }
        const scaleX = canvasSize.width / windowSize.width
        const scaleY = canvasSize.height / windowSize.height
        return new Vec3(
            screenPos.x * scaleX - canvasSize.width / 2,
            screenPos.y * scaleY - canvasSize.height / 2,
            0,
        )
    }

    /** 房间氛围：光点上升循环 / 灯泡呼吸 / 阳光光斑呼吸 / 玩具球轻摆 */
    private roomAmbience(dt: number): void {
        const room = this.room
        if (!room) {
            return
        }
        room.orbs.forEach((orb, index) => {
            if (this.orbSeeds.length <= index) {
                this.orbSeeds.push(Math.random() * 6.28)
            }
            const seed = this.orbSeeds[index]
            const y = orb.position.y + dt * 0.16
            orb.setPosition(
                orb.position.x + Math.sin(this.roomTime * 0.8 + seed) * dt * 0.1,
                y > 3.4 ? 0.5 : y,
                orb.position.z,
            )
            const scale = 0.85 + Math.sin(this.roomTime * 1.7 + seed) * 0.15
            orb.setScale(scale, scale, scale)
        })
        room.bulbs.forEach((bulb, index) => {
            const scale = 0.92 + Math.sin(this.roomTime * 2.1 + index * 0.7) * 0.08
            bulb.setScale(scale, scale, scale)
        })
        const beamScale = 1 + Math.sin(this.roomTime * 0.9) * 0.06
        room.sunBeam.setScale(beamScale, 1, beamScale)
        const toy = room.toyBall
        toy.setPosition(
            -1.35 + Math.sin(this.roomTime * 0.6) * 0.1,
            0.19 + Math.abs(Math.sin(this.roomTime * 1.4)) * 0.03,
            1.05,
        )
    }

    // ---------------- 桥接 ----------------

    private bindBridge(): void {
        this.bridge.bind((message: HostToGame) => {
            switch (message.type) {
                case 'init':
                case 'petState':
                    this.pendingPet = message.pet
                    this.applyStatsToAnimation()
                    break
                case 'actionResult':
                    if (message.ok) {
                        this.playClip('Happy', false)
                        this.effects && this.effects.sparkle(this.headWorld, 3)
                    } else {
                        this.effects && this.effects.floatText(this.headWorld, '呜…', new Color(255, 200, 200, 255))
                    }
                    break
                case 'battleRounds':
                    if (this.effects) {
                        this.effects.stars(this.headWorld, message.won ? 8 : 3)
                    }
                    break
                case 'chatBubble':
                    // 气泡属界面层（旧 HUD 已删），新 UI 层落地前先用舞台浮字顶一下
                    this.effects && this.effects.floatText(this.headWorld, message.content, new Color(255, 250, 240, 255), 20)
                    break
                default:
                    break
            }
        })
    }
}
