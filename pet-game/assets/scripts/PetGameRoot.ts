import {
    _decorator,
    Camera,
    Color,
    Component,
    EventTouch,
    Layers,
    Node,
    UITransform,
    Vec3,
    screen,
} from 'cc'
import {
    BattleRound,
    HostToGame,
    PetDisplayState,
    PetGameBridge,
    PetIntentAction,
} from './PetGameBridge'
import { PetBuilderKit } from './PetBuilderKit'
import { buildPet, PetRig } from './PetModelBuilder'
import { PetAnimations } from './PetAnimations'
import { PetEffects } from './PetEffects'
import { PetHud } from './PetHud'
import { PetEmotion, resolvePalette } from './PetGameTheme'
import { buildRoom, RoomRefs } from './PetRoomBuilder'

const { ccclass } = _decorator

/**
 * 宠物 3D 场景主组件（视觉重构 v3，Cocos Creator 4.0 alpha，全部内容程序化构建）。
 *
 * 结构：
 *  - 3D 世界（Scene / DEFAULT 层）：温馨房间（PetRoomBuilder）+ Q 版宠物（PetModelBuilder），
 *    统一走自定义卡通材质 pet-toon（两段明暗 + 顶部天光 + 轮廓光 + 描边）；
 *  - 2D UI（Root / UI_2D 层）：HUD（PetHud）+ 特效粒子（PetEffects）。
 *
 * 生命感来自两条线：
 *  1. 逐帧待机驱动（update）：呼吸 / 尾巴 / 耳朵 / 眨眼 / 视线 / 呆毛 / 随机小动作；
 *  2. 情绪状态机：由服务端数值（饱食/心情/精力/清洁）推导基础情绪，交互时临时覆盖，
 *     情绪决定表情（嘴部变体）、耳朵耷拉度、尾巴活跃度、眼睛开合。
 *
 * 服务端权威契约不变：本组件只做展示与动画，数值全部来自宿主下发的 PetDisplayState，
 * 用户操作仅回传 intent，由宿主调用 mall-pet API 后以 petState/actionResult 回灌。
 */

/** 宠物世界位置（地毯中央；影子等地面元素与其对齐） */
const PET_POS = new Vec3(0, 0, 0.35)

/** 演示模式（?demo=1）：未接宿主时也能完整展示视觉与动画，用于开发与验收 */
const DEMO_STATE: PetDisplayState = {
    name: '糖糖', species: 'CAT', growthStage: 'YOUNG', level: 6, expPercent: 0.62,
    hp: 92, maxHp: 100, hunger: 58, happiness: 82, energy: 74, cleanliness: 90,
    status: 'IDLE', speech: '主人，陪我玩一会嘛～', color: 'orange', accessory: 'bell', evolutionStage: 0,
}

/** 情绪 → 表演参数（耳朵/尾巴/眼睛/嘴） */
const EMOTION_PROFILE: Record<PetEmotion, { droop: number; mood: number; eye: number; mouth: 'smile' | 'sad' | 'open' }> = {
    idle: { droop: 0.12, mood: 0.45, eye: 1, mouth: 'smile' },
    happy: { droop: 0, mood: 1, eye: 1.04, mouth: 'smile' },
    hungry: { droop: 0.55, mood: 0.22, eye: 0.92, mouth: 'sad' },
    sad: { droop: 0.8, mood: 0.15, eye: 0.82, mouth: 'sad' },
    sleepy: { droop: 0.6, mood: 0.25, eye: 0.5, mouth: 'smile' },
    eat: { droop: 0.1, mood: 0.8, eye: 1, mouth: 'open' },
    play: { droop: 0, mood: 1, eye: 1.06, mouth: 'open' },
    clean: { droop: 0.3, mood: 0.6, eye: 0.9, mouth: 'smile' },
    sleep: { droop: 0.9, mood: 0.1, eye: 0.05, mouth: 'smile' },
    pet: { droop: 0.05, mood: 0.9, eye: 0.55, mouth: 'smile' },
    love: { droop: 0, mood: 1, eye: 0.6, mouth: 'smile' },
}

@ccclass('PetGameRoot')
export class PetGameRoot extends Component {

    private readonly bridge = new PetGameBridge()
    private readonly ccRuntime = (globalThis as unknown as { cc: never }).cc

    private kit: PetBuilderKit | null = null
    private world3d: Node | null = null
    private petNode: Node | null = null
    private rig: PetRig | null = null
    private room: RoomRefs | null = null
    private camera3d: Camera | null = null
    private rootTransform: UITransform | null = null

    private hud: PetHud | null = null
    private effects: PetEffects | null = null

    private pet: PetDisplayState | null = null
    /** 演出锁：非空时暂停 body/head 的逐帧驱动，避免与 tween 演出争抢 */
    private performance: PetEmotion | null = null
    private emotion: PetEmotion = 'idle'
    private petKey = ''

    private time = 0
    private blinkTimer = 2.2
    private blinkPhase: 'open' | 'closing' | 'opening' = 'open'
    private blinkProgress = 0
    private lookTimer = 1.6
    private lookCur = { x: 0, y: 0 }
    private lookTarget = { x: 0, y: 0 }
    private idleTimer = 5
    private zzzTimer = 0
    private lastLevel = 0
    private roomTime = 0
    private readonly orbSeeds: number[] = []

    start(): void {
        this.rootTransform = this.node.getComponent(UITransform)
        this.kit = new PetBuilderKit(this.ccRuntime)
        this.buildWorld()
        this.buildStage()
        this.buildUi()
        this.bindBridge()

        this.bridge.send({ source: 'pet-game', type: 'ready' })
        if (new URLSearchParams(window.location.search).get('demo') === '1') {
            this.applyPetState(DEMO_STATE)
        }
    }

    update(dt: number): void {
        const step = Math.min(dt, 0.05)
        this.time += step
        this.roomTime += step
        this.idleDrive(step)
        this.roomAmbience(step)
        this.hud && this.hud.update(step)
    }

    onDestroy(): void {
        this.bridge.dispose()
    }

    // ---------------- 场景构建 ----------------

    /** 3D 世界：房间 + 相机色调（宠物在 buildStage 中按状态构建） */
    private buildWorld(): void {
        const kit = this.kit!
        const scene = this.node.scene
        this.world3d = kit.make3dNode(scene, 'World3D', new Vec3(0, 0, 0))
        this.room = buildRoom(this.world3d, kit)

        const cameraNode = scene.getChildByName('Main3DCamera')
        this.camera3d = cameraNode ? cameraNode.getComponent(Camera) : null
        if (this.camera3d) {
            // 室内暖色兜底背景（墙体之外的边缘区域）
            this.camera3d.clearColor = new Color(0x6E, 0x5A, 0x66, 255)
        }
    }

    /** 宠物本体（物种/皮肤/配饰变化时整体重建；影子固定在房间层） */
    private buildStage(): void {
        const kit = this.kit!
        const pet = this.pet
        const species = pet ? pet.species : 'CAT'
        const colorKey = pet ? pet.color : undefined
        const accessory = pet ? pet.accessory : 'none'
        this.petNode = kit.make3dNode(this.world3d!, 'Pet', PET_POS.clone())
        this.rig = buildPet(this.petNode, this.world3d!, kit, species || 'CAT',
            resolvePalette(species || 'CAT', colorKey), accessory || 'none')
    }

    private rebuildPetIfNeeded(pet: PetDisplayState): void {
        const key = `${pet.species}|${pet.color || ''}|${pet.accessory || ''}|${pet.growthStage}|${pet.evolutionStage || 0}`
        if (key === this.petKey || !this.petNode || !this.world3d) {
            return
        }
        this.petKey = key
        const hadRig = !!this.rig
        this.petNode.destroy()
        if (this.rig) {
            this.rig.shadow.destroy()
        }
        this.buildStage()
        if (this.rig) {
            this.applyEmotion()
            if (hadRig) {
                PetAnimations.cheer(this.rig)
            }
        }
    }

    // ---------------- 2D UI ----------------

    private buildUi(): void {
        const size = this.rootTransform ? this.rootTransform.contentSize : null
        const width = size ? size.width : 960
        const height = size ? size.height : 548
        this.hud = new PetHud(this.node, width, height, (intent: string) => this.onHudIntent(intent))
        this.hud.build()
        this.effects = new PetEffects(
            this.node,
            (name: string, x: number, y: number) => this.makeUiNode(name, x, y),
            (world: Vec3) => this.project(world),
        )
        this.buildHotspot()
    }

    /** UI 层节点工厂（特效层使用） */
    private makeUiNode(name: string, x: number, y: number): Node {
        const node = new Node(name)
        node.layer = Layers.Enum.UI_2D
        node.addComponent(UITransform).setContentSize(8, 8)
        node.setPosition(x, y, 0)
        this.node.addChild(node)
        return node
    }

    /** 宠物点击热区（UI 层覆盖宠物显示区域，比 3D 射线拾取跨端更稳） */
    private buildHotspot(): void {
        const size = this.rootTransform ? this.rootTransform.contentSize : null
        const height = size ? size.height : 548
        const hotspot = this.makeUiNode('pet-hotspot', 0, height * 0.06)
        hotspot.getComponent(UITransform)!.setContentSize(330, 320)
        hotspot.on(Node.EventType.TOUCH_END, (event: EventTouch) => {
            event.propagationStopped = true
            this.onPetTapped()
        }, this)
    }

    /** 世界坐标 → UI 坐标（粒子对齐宠物用） */
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

    /** 宠物头顶（世界坐标；爱心/星星/气泡的发射点） */
    private get headWorld(): Vec3 {
        return new Vec3(PET_POS.x, 1.95, PET_POS.z + 0.1)
    }

    /** 宠物嘴边（世界坐标；食物/浮字的落点） */
    private get mouthWorld(): Vec3 {
        return new Vec3(PET_POS.x, 1.1, PET_POS.z + 0.5)
    }

    // ---------------- 逐帧生命感 ----------------

    private idleDrive(dt: number): void {
        const rig = this.rig
        if (!rig) {
            return
        }
        const profile = EMOTION_PROFILE[this.emotion]
        const locked = this.performance !== null
        const sleeping = this.performance === 'sleep'

        // 呼吸：演出锁定时让位给 tween；睡觉时改为极缓慢的腹部起伏
        if (!locked) {
            PetAnimations.breathe(rig, this.time, 2.05)
        } else if (sleeping) {
            const wave = Math.sin(this.time * 1.1)
            rig.body.setScale(1.02 + wave * 0.012, 0.99 - wave * 0.012, 1.02)
        }

        if (!locked) {
            PetAnimations.tailSway(rig, this.time, profile.mood)
            PetAnimations.earPose(rig, this.time, profile.droop)
        } else if (sleeping) {
            PetAnimations.earPose(rig, this.time, 0.92)
        }
        PetAnimations.tuftIdle(rig, this.time)

        this.driveBlink(dt, profile.eye)
        this.driveLook(dt)
        this.driveIdleAction(dt)

        if (sleeping) {
            this.zzzTimer -= dt
            if (this.zzzTimer <= 0) {
                this.zzzTimer = 1.5
                this.effects && this.effects.sleepZ(this.headWorld, Math.floor(this.time) % 2)
            }
        }
    }

    /** 眨眼：随机间隔 + 快速闭合/张开；情绪影响基础开合度（困倦半闭眼） */
    private driveBlink(dt: number, baseEye: number): void {
        const rig = this.rig!
        this.blinkTimer -= dt
        if (this.blinkPhase === 'open') {
            PetAnimations.blink(rig, baseEye)
            if (this.blinkTimer <= 0) {
                this.blinkPhase = 'closing'
                this.blinkProgress = 0
            }
            return
        }
        this.blinkProgress += dt
        if (this.blinkPhase === 'closing') {
            const t = Math.min(1, this.blinkProgress / 0.07)
            PetAnimations.blink(rig, baseEye * (1 - t * 0.94))
            if (t >= 1) {
                this.blinkPhase = 'opening'
                this.blinkProgress = 0
            }
            return
        }
        const t = Math.min(1, this.blinkProgress / 0.1)
        PetAnimations.blink(rig, baseEye * (0.06 + t * 0.94))
        if (t >= 1) {
            this.blinkPhase = 'open'
            this.blinkProgress = 0
            this.blinkTimer = 2.0 + Math.random() * 3.2
        }
    }

    /** 视线：周期性看向随机方向，平滑追随（瞳孔偏移，制造"在观察"的感觉） */
    private driveLook(dt: number): void {
        const rig = this.rig!
        this.lookTimer -= dt
        if (this.lookTimer <= 0) {
            this.lookTimer = 2.4 + Math.random() * 4.2
            this.lookTarget = {
                x: (Math.random() * 2 - 1) * 0.9,
                y: (Math.random() * 2 - 1) * 0.5,
            }
        }
        const k = Math.min(1, dt * 4)
        this.lookCur.x += (this.lookTarget.x - this.lookCur.x) * k
        this.lookCur.y += (this.lookTarget.y - this.lookCur.y) * k
        PetAnimations.look(rig, this.lookCur.x, this.lookCur.y)
    }

    /** 待机小动作：每 8-16 秒随机来一个（歪头/抖耳/看肚子/小跳），制造"它自己在动" */
    private driveIdleAction(dt: number): void {
        if (this.performance !== null) {
            return
        }
        this.idleTimer -= dt
        if (this.idleTimer > 0) {
            return
        }
        this.idleTimer = 8 + Math.random() * 8
        const rig = this.rig!
        const pick = Math.floor(Math.random() * 4)
        switch (pick) {
            case 0:
                PetAnimations.glance(rig, (Math.random() * 2 - 1) * 26, (Math.random() * 2 - 1) * 10)
                break
            case 1:
                PetAnimations.hop(rig)
                break
            case 2:
                PetAnimations.glance(rig, 0, 18)
                break
            default:
                this.effects && this.effects.sparkle(this.headWorld, 2)
                PetAnimations.glance(rig, 14, -6)
                break
        }
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
        toy.setPosition(-1.35 + Math.sin(this.roomTime * 0.6) * 0.1, 0.19 + Math.abs(Math.sin(this.roomTime * 1.4)) * 0.03, 1.05)
    }

    // ---------------- 情绪 ----------------

    /** 由服务端数值推导基础情绪（交互演出时被 performance 覆盖） */
    private computeEmotion(): PetEmotion {
        if (this.performance) {
            return this.performance
        }
        const pet = this.pet
        if (!pet) {
            return 'idle'
        }
        const hunger = pet.hunger / 100
        const happiness = pet.happiness / 100
        const energy = pet.energy / 100
        const clean = pet.cleanliness / 100
        if (energy < 0.2) {
            return 'sleepy'
        }
        if (hunger < 0.25) {
            return 'hungry'
        }
        if (happiness < 0.28 || clean < 0.2) {
            return 'sad'
        }
        if (happiness > 0.78) {
            return 'happy'
        }
        return 'idle'
    }

    private applyEmotion(): void {
        const rig = this.rig
        if (!rig) {
            return
        }
        const emotion = this.computeEmotion()
        this.emotion = emotion
        const profile = EMOTION_PROFILE[emotion]
        PetAnimations.setMouth(rig, profile.mouth)
        PetAnimations.setBlush(rig, emotion === 'happy' || emotion === 'pet' || emotion === 'love' ? 1.25 : 1)
        if (this.performance === null) {
            PetAnimations.earPose(rig, this.time, profile.droop)
        }
    }

    /** 临时情绪演出（love/eat 等），结束后回到派生情绪 */
    private withPerformance(emotion: PetEmotion, run: () => void): void {
        this.performance = emotion
        this.applyEmotion()
        run()
    }

    private endPerformance(): void {
        this.performance = null
        this.applyEmotion()
    }

    // ---------------- 桥接 ----------------

    private bindBridge(): void {
        this.bridge.bind((message: HostToGame) => {
            switch (message.type) {
                case 'init':
                case 'petState':
                    this.applyPetState(message.pet)
                    break
                case 'actionResult':
                    this.playActionResult(message.action, message.ok, message.message)
                    break
                case 'battleRounds':
                    this.playBattle(message.rounds, message.won)
                    break
                case 'chatBubble':
                    this.hud && this.hud.showBubble(message.content, 4.2)
                    break
                default:
                    break
            }
        })
    }

    /** 应用服务端状态：数值 → HUD 平滑动画；外观变化 → 重建宠物；升级 → 星光演出 */
    private applyPetState(pet: PetDisplayState): void {
        const previousLevel = this.pet ? this.pet.level : 0
        const wasSleeping = this.performance === 'sleep'
        this.pet = pet
        this.rebuildPetIfNeeded(pet)
        this.hud && this.hud.setName(`${pet.name}`)
        this.hud && this.hud.setLevel(pet.level)
        this.hud && this.hud.setStatus(this.statusText(pet))
        this.hud && this.hud.setState('hp', pet.maxHp > 0 ? pet.hp / pet.maxHp : 0)
        this.hud && this.hud.setState('hunger', pet.hunger / 100)
        this.hud && this.hud.setState('happiness', pet.happiness / 100)
        this.hud && this.hud.setState('energy', pet.energy / 100)
        this.hud && this.hud.setState('cleanliness', pet.cleanliness / 100)
        this.hud && this.hud.setExp(pet.expPercent)

        if (pet.level > previousLevel && previousLevel > 0 && this.rig) {
            this.withPerformance('love', () => {
                PetAnimations.levelUp(this.rig!, () => this.endPerformance())
                this.effects && this.effects.stars(this.headWorld, 8)
                this.effects && this.effects.ring(this.headWorld)
                this.hud && this.hud.showBubble(`我升级啦！现在是 Lv.${pet.level}～`, 4)
            })
            this.blinkTimer = 3
        } else if (pet.speech && !wasSleeping) {
            this.hud && this.hud.showBubble(pet.speech)
        } else {
            this.applyEmotion()
        }

        // 服务端状态离开休息 → 唤醒
        if (wasSleeping && pet.status !== 'RESTING') {
            this.wakeUp()
        }
    }

    private statusText(pet: PetDisplayState): string {
        const label: Record<string, string> = {
            IDLE: '悠闲中', WORKING: '打工中', STUDYING: '读书中', FISHING: '捞瓶中', RESTING: '休息中',
        }
        const stage: Record<string, string> = { BABY: '幼年', YOUNG: '成长期', ADULT: '成年' }
        const activity = pet.activityName ? ` · ${pet.activityName}` : ''
        return `${label[pet.status] || '悠闲中'} · ${stage[pet.growthStage] || ''}${activity}`
    }

    /** 交互结果演出：喂食 / 玩耍 / 清洁 / 休息 / 失败（情绪 + 动画 + 粒子 + 浮字） */
    private playActionResult(action: string, ok: boolean, message: string | undefined): void {
        const rig = this.rig
        if (!rig) {
            return
        }
        if (this.performance === 'sleep' && action !== 'rest') {
            this.wakeUp()
        }
        if (!ok) {
            this.withPerformance('sad', () => {
                PetAnimations.hurt(rig, () => this.endPerformance())
                this.hud && this.hud.showBubble('呜…' + (message || '先看看我的状态吧'))
            })
            return
        }
        switch (action) {
            case 'feed':
                this.playFeedSequence(rig, message)
                break
            case 'play':
                this.withPerformance('play', () => {
                    this.lookTarget = { x: 0.9, y: 0.4 }
                    PetAnimations.play(rig, () => {
                        this.endPerformance()
                        this.effects && this.effects.hearts(this.headWorld, 2)
                    })
                    this.effects && this.effects.stars(this.headWorld, 5, new Color(255, 196, 226, 255))
                    this.hud && this.hud.showBubble(message || '再来一次！再来一次！')
                })
                break
            case 'clean':
                this.withPerformance('clean', () => {
                    PetAnimations.clean(rig, () => this.endPerformance())
                    this.effects && this.effects.bubbles(this.headWorld, 10)
                    this.hud && this.hud.showBubble(message || '洗得香喷喷～')
                })
                break
            case 'rest':
                this.withPerformance('sleep', () => {
                    PetAnimations.sleepEnter(rig)
                    this.zzzTimer = 1.2
                    this.hud && this.hud.showBubble(message || '呼…呼…睡一会儿…', 4)
                })
                break
            default:
                this.withPerformance('love', () => {
                    PetAnimations.hop(rig, () => this.endPerformance())
                    this.effects && this.effects.ring(this.headWorld)
                })
                break
        }
    }

    /** 喂食完整序列：看向食物 → 食物飞入 → 进食咀嚼 → 爱心与数值反馈 */
    private playFeedSequence(rig: PetRig, message: string | undefined): void {
        this.withPerformance('eat', () => {
            this.lookTarget = { x: -0.6, y: -0.9 }
            PetAnimations.glance(rig, -10, 22)
            this.scheduleOnce(() => {
                this.effects && this.effects.food(this.mouthWorld, '🍖', () => {
                    if (this.performance !== 'eat') {
                        return
                    }
                    PetAnimations.feed(rig, () => {
                        this.endPerformance()
                        this.effects && this.effects.hearts(this.headWorld, 3)
                        this.effects && this.effects.sparkle(this.mouthWorld, 3)
                    })
                    this.scheduleOnce(() => {
                        if (this.effects) {
                            this.effects.floatText(this.mouthWorld, message || '饱食度 +30', new Color(255, 226, 168, 255))
                        }
                    }, 0.5)
                })
            }, 0.45)
        })
        this.hud && this.hud.showBubble('哇，是好吃哒！', 2.6)
    }

    private wakeUp(): void {
        const rig = this.rig
        if (!rig) {
            return
        }
        this.performance = null
        PetAnimations.wakeUp(rig, () => this.applyEmotion())
        this.applyEmotion()
    }

    /** 宠物被点击/抚摸：看向用户 → 蹭头享受 → 爱心（Cocos 场景内的点击可直接反馈，不等服务端） */
    private onPetTapped(): void {
        const rig = this.rig
        if (!rig) {
            return
        }
        const sleeping = this.performance === 'sleep'
        if (sleeping) {
            this.wakeUp()
            this.hud && this.hud.showBubble('唔…谁呀…？')
            this.bridge.send({ source: 'pet-game', type: 'petTapped' })
            return
        }
        this.withPerformance('pet', () => {
            this.lookTarget = { x: 0, y: 0 }
            PetAnimations.glance(rig, 0, -8)
            this.scheduleOnce(() => {
                if (this.performance !== 'pet') {
                    return
                }
                PetAnimations.petting(rig, () => this.endPerformance())
                this.effects && this.effects.hearts(this.headWorld, 2)
            }, 0.3)
        })
        const speech = ['嘿嘿，好痒好痒～', '最喜欢主人了！', '再摸一会儿嘛～', '咕噜咕噜…'][Math.floor(Math.random() * 4)]
        this.hud && this.hud.showBubble(speech, 2.6)
        this.bridge.send({ source: 'pet-game', type: 'petTapped' })
    }

    /** HUD 意图：回传宿主（数值/幂等由服务端保证），带即时按钮反馈 */
    private onHudIntent(intent: string): void {
        this.bridge.send({ source: 'pet-game', type: 'intent', action: intent as PetIntentAction })
        this.effects && this.effects.sparkle(this.headWorld, 2)
    }

    // ---------------- 对战演出（服务端回合流水逐条播放，点击可跳过） ----------------

    private playBattle(rounds: BattleRound[], won: boolean): void {
        const rig = this.rig
        if (!rig || rounds.length === 0) {
            return
        }
        const step = (index: number): void => {
            if (index >= rounds.length) {
                this.hud && this.hud.showBubble(won ? '⚔️ 大获全胜！' : '💧 惜败了，下次再战！', 4)
                if (won) {
                    this.withPerformance('love', () => {
                        PetAnimations.cheer(this.rig!, () => this.endPerformance())
                        this.effects && this.effects.stars(this.headWorld, 9)
                    })
                } else {
                    this.withPerformance('sad', () => {
                        PetAnimations.hurt(this.rig!, () => this.endPerformance())
                    })
                }
                return
            }
            const round = rounds[index]
            const text = round.dodged
                ? `${round.actorName} 出手被闪开！`
                : `${round.actorName} 造成 ${round.damage} 伤害${round.critical ? ' 暴击！' : ''}`
            this.effects && this.effects.floatText(this.headWorld, text, new Color(255, 220, 220, 255), 22)
            if (this.effects) {
                if (round.critical) {
                    this.effects.stars(this.mouthWorld, 4, new Color(255, 140, 140, 255))
                } else {
                    this.effects.sparkle(this.mouthWorld, 2)
                }
            }
            this.scheduleOnce(() => step(index + 1), 0.85)
        }
        step(0)
    }
}
