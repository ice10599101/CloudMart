import * as THREE from 'three'
import { PetAnimations } from './animations'
import { PetMaterialKit } from './materials'
import { buildPet, PetRig } from './petModel'
import { buildRoom, RoomRefs } from './roomModel'
import { EMOTION_PROFILE, PetEmotion, resolvePalette, STATUS_LABEL, GROWTH_LABEL } from './theme'

/**
 * 原生 3D 舞台引擎（Three.js）。
 *
 * 职责：场景/相机/光照/渲染循环、宠物与房间的构建与重建、情绪状态机、演出调度、
 * 点击拾取、世界坐标 → 屏幕坐标投影（供 HUD 与粒子层对齐），以及资源释放。
 *
 * 与 React 的边界：引擎不感知 React；通过回调向外推：
 *  - onHud：宠物数值与状态（HUD 渲染用；仅在变化时推送）
 *  - onBubble / onFx：气泡文案与粒子事件（DOM 层播放，坐标已换算为舞台像素）
 *  - onIntent / onPetTapped：用户意图回传宿主
 *
 * 所有业务数值仍然来自服务端（宿主下发），引擎只做展示与动画，不做任何数值计算。
 */

export interface StagePetState {
    name: string
    species: string
    growthStage: string
    level: number
    expPercent: number
    hp: number
    maxHp: number
    hunger: number
    happiness: number
    energy: number
    cleanliness: number
    status: string
    activityName?: string
    speech?: string
    color?: string
    accessory?: string
    evolutionStage?: number
}

export interface StageHudSnapshot {
    name: string
    species: string
    level: number
    growthLabel: string
    statusLabel: string
    expPercent: number
    hp: number
    maxHp: number
    hunger: number
    happiness: number
    energy: number
    cleanliness: number
    busyIntent: string | null
}

export type FxKind = 'heart' | 'star' | 'bubble' | 'zzz' | 'food' | 'text' | 'ring' | 'sparkle'

export interface FxEvent {
    id: number
    kind: FxKind
    /** 舞台内像素坐标（左上角为原点） */
    x: number
    y: number
    text?: string
    color?: string
    /** 爆发数量（heart/star/bubble 用） */
    count?: number
}

export interface StageCallbacks {
    onIntent: (intent: string) => void
    onPetTapped: () => void
    onHud: (snapshot: StageHudSnapshot) => void
    onBubble: (text: string, duration: number) => void
    onFx: (event: FxEvent) => void
}

interface Performance {
    kind: PetEmotion
    duration: number
    elapsed: number
    hold: boolean
    events: Array<{ at: number; run: () => void; fired: boolean }>
    onDone?: () => void
}

/** 宠物站位（地毯中央；y 取地毯表面高度，避免脚陷进地毯） */
const PET_POS = new THREE.Vector3(0, 0.075, 0.35)
/** 宠物头顶（粒子发射点） */
const HEAD_WORLD = new THREE.Vector3(0, 2.0, 0.45)

export class PetStageEngine {

    private readonly canvas: HTMLCanvasElement
    private readonly callbacks: StageCallbacks
    private readonly kit = new PetMaterialKit()
    private readonly scene = new THREE.Scene()
    private camera: THREE.PerspectiveCamera
    private renderer: THREE.WebGLRenderer
    private readonly raycaster = new THREE.Raycaster()
    private readonly pointer = new THREE.Vector2()

    private world = new THREE.Group()
    private room: RoomRefs | null = null
    private rig: PetRig | null = null
    private petGroup: THREE.Group | null = null

    private pet: StagePetState | null = null
    private petKey = ''
    private emotion: PetEmotion = 'idle'
    private performance: Performance | null = null
    private sleepHold = false

    private time = 0
    private roomTime = 0
    private lastFrame = 0
    private raf = 0
    private width = 960
    private height = 560

    private blinkTimer = 2.2
    private blinkPhase: 'open' | 'closing' | 'opening' = 'open'
    private blinkProgress = 0
    private lookTimer = 1.6
    private lookCur = { x: 0, y: 0 }
    private lookTarget = { x: 0, y: 0 }
    private idleTimer = 6
    private zzzTimer = 0
    private fxSeed = 0
    private lastSnapshot = ''
    private orbSeeds: number[] = []
    private disposed = false

    constructor(canvas: HTMLCanvasElement, callbacks: StageCallbacks) {
        this.canvas = canvas
        this.callbacks = callbacks
        // 构图：宠物占画面高度约一半（焦点是角色，房间是背景）
        this.camera = new THREE.PerspectiveCamera(40, 16 / 9, 0.1, 100)
        this.camera.position.set(0, 2.05, 5.9)
        this.camera.lookAt(0, 1.05, 0)

        this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true })
        this.renderer.shadowMap.enabled = true
        this.renderer.shadowMap.type = THREE.PCFShadowMap
        this.renderer.outputColorSpace = THREE.SRGBColorSpace
        this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
        this.renderer.setClearColor(0x000000, 0)

        this.buildLights()
        this.scene.add(this.world)
        this.room = buildRoom(this.world, this.kit)

        this.canvas.addEventListener('pointerdown', this.onPointerDown)
        this.clockStart()
    }

    // ---------------- 对外 API ----------------

    /** 应用服务端宠物状态（外观变化重建模型；数值推送 HUD；升级播放星光演出） */
    setPet(state: StagePetState): void {
        const previousLevel = this.pet ? this.pet.level : 0
        const previousStatus = this.pet ? this.pet.status : ''
        this.pet = state
        this.rebuildIfNeeded(state)

        if (state.level > previousLevel && previousLevel > 0) {
            this.startPerformance('love', 1.6, () => {
                this.emit('star', HEAD_WORLD, { count: 8 })
                this.emit('ring', HEAD_WORLD)
            })
            this.callbacks.onBubble(`我升级啦！现在是 Lv.${state.level}～`, 4)
        } else if (state.speech && state.status !== 'RESTING') {
            this.callbacks.onBubble(state.speech, 3.4)
        }

        // 休息状态：进入/离开趴睡姿态
        const resting = state.status === 'RESTING'
        if (resting && !this.sleepHold) {
            this.sleepHold = true
            this.startPerformance('sleep', 1.1, undefined, true)
        } else if (!resting && this.sleepHold) {
            this.sleepHold = false
            this.startWake()
        } else if (!resting && previousStatus === 'RESTING') {
            this.sleepHold = false
            this.startWake()
        }
        this.pushHud()
    }

    /** 交互结果演出（喂食/玩耍/清洁/休息/失败），与宿主 API 结果一一对应 */
    playActionResult(action: string, ok: boolean, message?: string): void {
        if (!ok) {
            this.startPerformance('sad', 1.0)
            this.callbacks.onBubble(`呜…${message || '先看看我的状态吧'}`, 3)
            return
        }
        switch (action) {
            case 'feed': {
                this.startPerformance('eat', 2.3, () => {
                    this.emit('heart', HEAD_WORLD, { count: 3 })
                }, false, [
                    { at: 0.35, run: () => this.emit('food', new THREE.Vector3(0, 1.02, 0.62), { text: '🍖' }) },
                    { at: 1.25, run: () => this.emit('heart', HEAD_WORLD, { count: 2 }) },
                    {
                        at: 1.55,
                        run: () => this.emit('text', new THREE.Vector3(0, 1.1, 1.0),
                            { text: message || '饱食度 +30', color: '#FFE0A8' }),
                    },
                ])
                this.callbacks.onBubble('哇，是好吃哒！', 2.6)
                break
            }
            case 'play': {
                this.startPerformance('play', 1.8, () => this.emit('heart', HEAD_WORLD, { count: 2 }))
                this.emit('star', HEAD_WORLD, { count: 5, color: '#FFC4E2' })
                this.callbacks.onBubble(message || '再来一次！再来一次！', 3)
                break
            }
            case 'clean': {
                this.startPerformance('clean', 1.9, () => {
                    this.emit('sparkle', HEAD_WORLD, { count: 4 })
                })
                this.emit('bubble', HEAD_WORLD, { count: 10 })
                this.callbacks.onBubble(message || '洗得香喷喷～', 3)
                break
            }
            case 'rest': {
                this.sleepHold = true
                this.startPerformance('sleep', 1.2, undefined, true)
                this.zzzTimer = 1.1
                this.callbacks.onBubble(message || '呼…呼…睡一会儿…', 4)
                break
            }
            default: {
                this.startPerformance('love', 1.1, () => this.emit('ring', HEAD_WORLD))
                break
            }
        }
        this.pushHud()
    }

    /** 对战演出：服务端回合流水逐条播放（浮字 + 暴击星爆） */
    playBattle(rounds: Array<{ actorName: string; damage: number; critical: boolean; dodged: boolean }>, won: boolean): void {
        if (rounds.length === 0) {
            return
        }
        const step = (index: number): void => {
            if (this.disposed) {
                return
            }
            if (index >= rounds.length) {
                this.callbacks.onBubble(won ? '⚔️ 大获全胜！' : '💧 惜败了，下次再战！', 4)
                if (won) {
                    this.startPerformance('happy', 1.6, () => this.emit('star', HEAD_WORLD, { count: 9 }))
                } else {
                    this.startPerformance('sad', 1.1)
                }
                return
            }
            const round = rounds[index]
            const text = round.dodged
                ? `${round.actorName} 被闪开！`
                : `${round.actorName} -${round.damage}${round.critical ? ' 暴击!' : ''}`
            this.emit('text', new THREE.Vector3(0, 1.6, 0.9), { text, color: round.critical ? '#FF9C9C' : '#FFE8D0' })
            this.emit(round.critical ? 'star' : 'sparkle', new THREE.Vector3(0, 1.15, 0.8),
                round.critical ? { count: 4, color: '#FF9C9C' } : { count: 2 })
            window.setTimeout(() => step(index + 1), 850)
        }
        step(0)
    }

    /** 更新渲染尺寸（容器尺寸变化时调用） */
    resize(width: number, height: number): void {
        if (width <= 0 || height <= 0) {
            return
        }
        this.width = width
        this.height = height
        this.camera.aspect = width / height
        this.camera.updateProjectionMatrix()
        this.renderer.setSize(width, height, false)
    }

    /** 释放全部 GPU 资源与事件监听 */
    dispose(): void {
        this.disposed = true
        cancelAnimationFrame(this.raf)
        this.canvas.removeEventListener('pointerdown', this.onPointerDown)
        this.scene.traverse((object) => {
            const mesh = object as THREE.Mesh
            if (mesh.geometry) {
                mesh.geometry.dispose()
            }
        })
        this.kit.dispose()
        this.renderer.dispose()
    }

    // ---------------- 场景构建 ----------------

    /** 光照：暖色半球天光 + 带软阴影的主光 + 冷色补光（立体感与层次的关键） */
    private buildLights(): void {
        const hemi = new THREE.HemisphereLight(0xFFF8EC, 0xE8CDA8, 0.8)
        this.scene.add(hemi)

        const key = new THREE.DirectionalLight(0xFFF0DA, 1.95)
        key.position.set(3.6, 6.4, 4.8)
        key.castShadow = true
        key.shadow.mapSize.set(2048, 2048)
        key.shadow.camera.near = 1
        key.shadow.camera.far = 24
        key.shadow.camera.left = -6
        key.shadow.camera.right = 6
        key.shadow.camera.top = 6
        key.shadow.camera.bottom = -3
        key.shadow.bias = -0.0012
        key.shadow.normalBias = 0.02
        this.scene.add(key)

        const rim = new THREE.DirectionalLight(0xFFD9C0, 0.4)
        rim.position.set(-5, 3.4, -2.5)
        this.scene.add(rim)
    }

    private clockStart(): void {
        this.lastFrame = performance.now()
        this.raf = requestAnimationFrame(this.tick)
    }

    /** 物种/皮肤/配饰/成长阶段变化时重建宠物（其余情况零开销） */
    private rebuildIfNeeded(state: StagePetState): void {
        const key = `${state.species}|${state.color || ''}|${state.accessory || ''}|${state.growthStage}|${state.evolutionStage || 0}`
        if (key === this.petKey && this.petGroup) {
            return
        }
        this.petKey = key
        if (this.petGroup) {
            this.disposeGroup(this.petGroup)
            this.world.remove(this.petGroup)
            this.petGroup = null
        }
        if (this.rig) {
            const oldShadow = this.rig.shadow
            oldShadow.geometry.dispose()
            ;(oldShadow.material as THREE.Material).dispose()
            this.world.remove(oldShadow)
            this.rig = null
        }
        this.rig = buildPet(this.world, this.kit, state.species || 'CAT',
            resolvePalette(state.species || 'CAT', state.color), state.accessory || 'none')
        this.petGroup = this.rig.root
        this.petGroup.position.copy(PET_POS)
        this.rig.basePos = PET_POS.clone()
        this.rig.shadow.position.set(PET_POS.x, PET_POS.y + 0.007, PET_POS.z + 0.05)
        this.applyGrowth(state)
        this.applyEmotion(true)
        PetAnimations.resetPose(this.rig)
    }

    /** 成长阶段/进化阶段 → 体型（只改缩放，不重建节点） */
    private applyGrowth(state: StagePetState): void {
        if (!this.rig) {
            return
        }
        const base: Record<string, number> = { BABY: 0.86, YOUNG: 0.94, ADULT: 1 }
        const evolution = Math.min(2, Math.max(0, state.evolutionStage || 0))
        const scale = (base[state.growthStage] || 1) * (1 + evolution * 0.06)
        this.rig.root.scale.set(scale, scale, scale)
    }

    private disposeGroup(group: THREE.Object3D): void {
        group.traverse((object) => {
            const mesh = object as THREE.Mesh
            if (mesh.geometry) {
                mesh.geometry.dispose()
            }
        })
    }

    // ---------------- 主循环 ----------------

    private tick = (): void => {
        if (this.disposed) {
            return
        }
        this.raf = requestAnimationFrame(this.tick)
        const now = performance.now()
        const dt = Math.min((now - this.lastFrame) / 1000, 0.05)
        this.lastFrame = now
        this.time += dt
        this.roomTime += dt
        this.update(dt)
        this.renderer.render(this.scene, this.camera)
    }

    private update(dt: number): void {
        this.updatePerformance(dt)
        this.driveIdle(dt)
        this.driveBlink(dt)
        this.driveLook(dt)
        this.driveIdleAction(dt)
        this.driveRoom(dt)
    }

    /** 演出调度：按进度应用姿态 + 触发时间轴事件 + 收尾 */
    private updatePerformance(dt: number): void {
        const active = this.performance
        if (!active || !this.rig) {
            return
        }
        active.elapsed += dt
        const p = Math.min(1, active.elapsed / active.duration)
        for (const event of active.events) {
            if (!event.fired && active.elapsed >= event.at) {
                event.fired = true
                event.run()
            }
        }
        PetAnimations.perform(this.rig, active.kind, p)
        if (p >= 1) {
            const done = active.onDone
            this.performance = null
            if (!active.hold && this.rig) {
                PetAnimations.resetPose(this.rig)
                if (this.pet) {
                    this.applyGrowth(this.pet)
                }
            }
            this.applyEmotion(true)
            if (done) {
                done()
            }
            this.pushHud()
        }
    }

    /** 启动一次演出（hold = true 表示保持终态直到被唤醒，用于入睡） */
    private startPerformance(kind: PetEmotion, duration: number, onDone?: () => void, hold = false,
                             events: Array<{ at: number; run: () => void }> = []): void {
        if (!this.rig) {
            return
        }
        this.performance = {
            kind,
            duration,
            elapsed: 0,
            hold,
            events: events.map(event => ({ at: event.at, run: event.run, fired: false })),
            onDone,
        }
        this.applyEmotion()
        this.pushHud()
    }

    /** 唤醒：从趴姿回到站姿（0.8s 独立进程，由 driveIdle 驱动） */
    private startWake(): void {
        this.performance = null
        this.microAction = null
        this.wakeRemaining = 0.8
    }

    private wakeRemaining = 0
    private microAction: { kind: 'glance' | 'hop'; duration: number; elapsed: number; yaw: number; pitch: number } | null = null

    private driveIdle(dt: number): void {
        const rig = this.rig
        if (!rig) {
            return
        }
        if (this.wakeRemaining > 0) {
            this.wakeRemaining = Math.max(0, this.wakeRemaining - dt)
            PetAnimations.performWake(rig, 1 - this.wakeRemaining / 0.8)
            if (this.wakeRemaining === 0) {
                PetAnimations.resetPose(rig)
                if (this.pet) {
                    this.applyGrowth(this.pet)
                }
                this.applyEmotion(true)
            }
            return
        }
        const emotion = this.currentEmotion()
        // 仅当演出/小动作会写 body 时才锁定呼吸（glance 只转头，保留呼吸更自然）
        const lockBody = (this.performance !== null && this.performance.kind !== 'idle')
            || (this.microAction !== null && this.microAction.kind === 'hop')
        PetAnimations.idle(rig, this.time, emotion, lockBody)
        this.driveMicro(dt)
        if (this.sleepHold && !this.performance) {
            this.zzzTimer -= dt
            if (this.zzzTimer <= 0) {
                this.zzzTimer = 1.6
                this.emit('zzz', new THREE.Vector3(HEAD_WORLD.x + 0.2, 1.7, HEAD_WORLD.z))
            }
        }
    }

    /** 待机小动作（歪头张望 / 小跳）：独立于正式演出的短进程 */
    private driveMicro(dt: number): void {
        const action = this.microAction
        const rig = this.rig
        if (!action || !rig) {
            return
        }
        action.elapsed += dt
        const p = action.elapsed / action.duration
        if (p >= 1) {
            this.microAction = null
            PetAnimations.resetPose(rig)
            this.applyEmotion(true)
            return
        }
        if (action.kind === 'glance') {
            PetAnimations.performGlance(rig, p, action.yaw, action.pitch)
        } else {
            PetAnimations.performHop(rig, p)
        }
    }

    private driveBlink(dt: number): void {
        const rig = this.rig
        if (!rig) {
            return
        }
        const baseEye = EMOTION_PROFILE[this.currentEmotion()].eye
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
            this.blinkTimer = 2.2 + Math.random() * 3.4
        }
    }

    private driveLook(dt: number): void {
        const rig = this.rig
        if (!rig) {
            return
        }
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

    /** 待机小动作：每 8-16 秒来一个（歪头/小跳/看向肚子/冒星星） */
    private driveIdleAction(dt: number): void {
        const rig = this.rig
        if (!rig || this.performance || this.sleepHold || this.wakeRemaining > 0) {
            return
        }
        this.idleTimer -= dt
        if (this.idleTimer > 0) {
            return
        }
        this.idleTimer = 8 + Math.random() * 8
        const pick = Math.floor(Math.random() * 4)
        switch (pick) {
            case 0:
                this.microAction = { kind: 'glance', duration: 1.1, elapsed: 0, yaw: (Math.random() * 2 - 1) * 26, pitch: 0 }
                break
            case 1:
                this.microAction = { kind: 'hop', duration: 0.55, elapsed: 0, yaw: 0, pitch: 0 }
                break
            case 2:
                this.microAction = { kind: 'glance', duration: 1.1, elapsed: 0, yaw: 0, pitch: 18 }
                break
            default:
                this.emit('sparkle', HEAD_WORLD, { count: 2 })
                this.microAction = { kind: 'glance', duration: 1.1, elapsed: 0, yaw: 14, pitch: -6 }
                break
        }
    }

    /** 房间氛围：光点上升 / 灯泡呼吸 / 光斑明暗 / 玩具球轻摆 */
    private driveRoom(dt: number): void {
        const room = this.room
        if (!room) {
            return
        }
        room.orbs.forEach((orb, index) => {
            if (this.orbSeeds.length <= index) {
                this.orbSeeds.push(Math.random() * Math.PI * 2)
            }
            const seed = this.orbSeeds[index]
            const nextY = orb.position.y + dt * 0.16
            orb.position.set(
                orb.position.x + Math.sin(this.roomTime * 0.8 + seed) * dt * 0.1,
                nextY > 3.4 ? 0.5 : nextY,
                orb.position.z,
            )
            const scale = 0.85 + Math.sin(this.roomTime * 1.7 + seed) * 0.15
            orb.scale.setScalar(scale)
        })
        room.bulbs.forEach((bulb, index) => {
            const scale = 0.92 + Math.sin(this.roomTime * 2.1 + index * 0.7) * 0.08
            bulb.scale.setScalar(scale)
        })
        const material = room.sunBeam.material as THREE.MeshBasicMaterial
        material.opacity = 0.24 + Math.sin(this.roomTime * 0.9) * 0.06
        room.toyBall.position.set(
            -1.35 + Math.sin(this.roomTime * 0.6) * 0.1,
            0.19 + Math.abs(Math.sin(this.roomTime * 1.4)) * 0.03,
            1.05,
        )
    }

    // ---------------- 情绪 ----------------

    private currentEmotion(): PetEmotion {
        if (this.performance) {
            return this.performance.kind
        }
        if (this.sleepHold) {
            return 'sleep'
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

    private applyEmotion(force = false): void {
        const rig = this.rig
        if (!rig) {
            return
        }
        const emotion = this.currentEmotion()
        if (!force && emotion === this.emotion) {
            return
        }
        this.emotion = emotion
        const profile = EMOTION_PROFILE[emotion]
        PetAnimations.setMouth(rig, profile.mouth)
        PetAnimations.setBlush(rig, emotion === 'happy' || emotion === 'pet' || emotion === 'love' ? 1.25 : 1)
        if (!this.performance) {
            PetAnimations.earPose(rig, this.time, profile.droop)
        }
    }

    // ---------------- 交互 ----------------

    private onPointerDown = (event: PointerEvent): void => {
        if (!this.rig || this.disposed) {
            return
        }
        const rect = this.canvas.getBoundingClientRect()
        this.pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
        this.pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
        this.raycaster.setFromCamera(this.pointer, this.camera)
        const hits = this.raycaster.intersectObject(this.rig.root, true)
        if (hits.length === 0) {
            return
        }
        this.handlePetTapped()
    }

    private handlePetTapped(): void {
        const rig = this.rig
        if (!rig) {
            return
        }
        if (this.sleepHold) {
            this.sleepHold = false
            this.startWake()
            this.callbacks.onBubble('唔…谁呀…？', 2.6)
            this.callbacks.onPetTapped()
            return
        }
        this.startPerformance('pet', 1.1, () => this.emit('heart', HEAD_WORLD, { count: 2 }))
        const speech = ['嘿嘿，好痒好痒～', '最喜欢主人了！', '再摸一会儿嘛～', '咕噜咕噜…'][Math.floor(Math.random() * 4)]
        this.callbacks.onBubble(speech, 2.6)
        this.callbacks.onPetTapped()
    }

    /** HUD 意图：回传宿主（数值与幂等由服务端保证） */
    notifyIntent(intent: string): void {
        this.callbacks.onIntent(intent)
        this.emit('sparkle', HEAD_WORLD, { count: 2 })
    }

    // ---------------- 输出（HUD / 粒子） ----------------

    /** 推送 HUD 快照（仅在内容变化时推送，避免无谓的 React 渲染） */
    private pushHud(): void {
        const pet = this.pet
        if (!pet) {
            return
        }
        const busy = this.performance && ['eat', 'play', 'clean', 'sleep'].includes(this.performance.kind)
            ? (this.performance.kind === 'eat' ? 'feed'
                : this.performance.kind === 'play' ? 'play'
                    : this.performance.kind === 'clean' ? 'clean' : 'rest')
            : null
        const snapshot: StageHudSnapshot = {
            name: pet.name,
            species: pet.species,
            level: pet.level,
            growthLabel: GROWTH_LABEL[pet.growthStage] || '',
            statusLabel: STATUS_LABEL[pet.status] || '悠闲中',
            expPercent: pet.expPercent,
            hp: pet.hp,
            maxHp: pet.maxHp,
            hunger: pet.hunger,
            happiness: pet.happiness,
            energy: pet.energy,
            cleanliness: pet.cleanliness,
            busyIntent: busy,
        }
        const key = JSON.stringify(snapshot)
        if (key === this.lastSnapshot) {
            return
        }
        this.lastSnapshot = key
        this.callbacks.onHud(snapshot)
    }

    /** 世界坐标 → 舞台像素坐标 */
    private toScreen(world: THREE.Vector3): { x: number; y: number } {
        return PetAnimations.project(world, this.camera, this.width, this.height)
    }

    /** 触发粒子事件（坐标已换算，DOM 层直接使用） */
    private emit(kind: FxKind, world: THREE.Vector3, options: { count?: number; text?: string; color?: string } = {}): void {
        const point = this.toScreen(world)
        this.callbacks.onFx({
            id: ++this.fxSeed,
            kind,
            x: point.x,
            y: point.y,
            count: options.count,
            text: options.text,
            color: options.color,
        })
    }
}
