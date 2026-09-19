import { forwardRef, useCallback, useEffect, useImperativeHandle, useRef, useState } from 'react'
import type { BattleRound, HostToGame, PetDisplayState, PetIntentAction } from '../bridge'
import PetFxLayer from './PetFxLayer'
import StageHud from './StageHud'
import { PetStageEngine } from './stageEngine'
import type { FxEvent, StageHudSnapshot, StagePetState } from './stageEngine'
import styles from './stage3d.module.css'

/**
 * 原生 3D 宠物舞台（Three.js）。
 *
 * 与宿主的关系：对外暴露与 Cocos 舞台一致的接口（PetStageHandle.post / onIntent / onPetTapped），
 * 因此 PetHome.tsx 只需替换舞台组件，业务逻辑零改动；桥协议（HostToGame / 意图枚举）完全复用。
 *
 * 为什么是原生实现：pet-game（Cocos）构建产物在当前工具链下无法启动
 * （spine 打桩模块 embind 重复注册 + 内置 effect 缺少编译产物），详情见交付说明；
 * 本项目宿主侧本就设计了"构建产物缺失 → 原生舞台"的 Fail-Open 通路，
 * 这里把原降级舞台升级为完整的 Three.js 实现（宠物 / 房间 / 动画 / 特效 / HUD 全套）。
 */

export interface PetStage3DProps {
    pet: PetDisplayState | null
    onIntent: (action: PetIntentAction) => void
    onPetTapped?: () => void
    className?: string
}

export interface PetStage3DHandle {
    post: (message: HostToGame) => void
}

/** 舞台状态映射：宿主下发的展示状态与服务端字段一一对应（不做任何数值换算） */
function toStagePetState(pet: PetDisplayState): StagePetState {
    return {
        name: pet.name,
        species: pet.species,
        growthStage: pet.growthStage,
        level: pet.level,
        expPercent: pet.expPercent,
        hp: pet.hp,
        maxHp: pet.maxHp,
        hunger: pet.hunger,
        happiness: pet.happiness,
        energy: pet.energy,
        cleanliness: pet.cleanliness,
        status: pet.status,
        activityName: pet.activityName,
        speech: pet.speech,
        color: pet.color,
        accessory: pet.accessory,
        evolutionStage: pet.evolutionStage,
    }
}

const MAX_FX = 36

const PetStage3D = forwardRef<PetStage3DHandle, PetStage3DProps>(function PetStage3D(
    { pet, onIntent, onPetTapped, className },
    ref,
) {
    const containerRef = useRef<HTMLDivElement>(null)
    const canvasRef = useRef<HTMLCanvasElement>(null)
    const engineRef = useRef<PetStageEngine | null>(null)

    const [snapshot, setSnapshot] = useState<StageHudSnapshot | null>(null)
    const [fxItems, setFxItems] = useState<FxEvent[]>([])
    const [bubble, setBubble] = useState<{ id: number; text: string } | null>(null)
    const [ready, setReady] = useState(false)

    // 回调放进 ref：引擎只创建一次，但始终调用最新的业务处理函数
    const intentRef = useRef(onIntent)
    const tapRef = useRef(onPetTapped)
    intentRef.current = onIntent
    tapRef.current = onPetTapped

    const bubbleSeed = useRef(0)
    const bubbleTimer = useRef(0)

    const handleFx = useCallback((event: FxEvent) => {
        setFxItems((prev) => {
            const next = [...prev, event]
            return next.length > MAX_FX ? next.slice(next.length - MAX_FX) : next
        })
    }, [])

    const handleFxDone = useCallback((id: number) => {
        setFxItems((prev) => prev.filter((item) => item.id !== id))
    }, [])

    const handleBubble = useCallback((text: string, duration: number) => {
        bubbleSeed.current += 1
        const id = bubbleSeed.current
        setBubble({ id, text })
        window.clearTimeout(bubbleTimer.current)
        bubbleTimer.current = window.setTimeout(() => {
            setBubble((current) => (current && current.id === id ? null : current))
        }, duration * 1000)
    }, [])

    // 引擎生命周期：挂载创建、卸载释放
    useEffect(() => {
        const canvas = canvasRef.current
        const container = containerRef.current
        if (!canvas || !container) {
            return
        }
        const engine = new PetStageEngine(canvas, {
            onIntent: (intent) => intentRef.current(intent as PetIntentAction),
            onPetTapped: () => tapRef.current && tapRef.current(),
            onHud: setSnapshot,
            onBubble: handleBubble,
            onFx: handleFx,
        })
        engineRef.current = engine

        const rect = container.getBoundingClientRect()
        engine.resize(Math.max(1, rect.width), Math.max(1, rect.height))
        setReady(true)

        const observer = new ResizeObserver((entries) => {
            for (const entry of entries) {
                const { width, height } = entry.contentRect
                engine.resize(Math.max(1, width), Math.max(1, height))
            }
        })
        observer.observe(container)

        return () => {
            observer.disconnect()
            engine.dispose()
            engineRef.current = null
            window.clearTimeout(bubbleTimer.current)
        }
    }, [handleBubble, handleFx])

    // 状态下发：props.pet 变化 → 引擎同步（外观/数值/情绪）
    useEffect(() => {
        if (!pet || !ready) {
            return
        }
        engineRef.current?.setPet(toStagePetState(pet))
    }, [pet, ready])

    // 宿主消息：与 Cocos 版本共用同一协议
    useImperativeHandle(ref, () => ({
        post: (message: HostToGame) => {
            const engine = engineRef.current
            if (!engine) {
                return
            }
            switch (message.type) {
                case 'init':
                case 'petState':
                    engine.setPet(toStagePetState(message.pet))
                    break
                case 'actionResult':
                    engine.playActionResult(message.action, message.ok, message.message)
                    break
                case 'battleRounds':
                    engine.playBattle(message.rounds as BattleRound[], message.won)
                    break
                case 'chatBubble':
                    handleBubble(message.content, 4.2)
                    break
                default:
                    break
            }
        },
    }), [handleBubble])

    const handleIntent = useCallback((intent: string) => {
        engineRef.current?.notifyIntent(intent)
    }, [])

    return (
        <div ref={containerRef} className={`${styles.stage} ${className || ''}`}>
            <canvas ref={canvasRef} className={styles.canvas} />
            {!ready && (
                <div className={styles.loading}>
                    <span className={styles.loadingPaw}>🐾</span>
                    正在布置小家…
                </div>
            )}
            <StageHud snapshot={snapshot} bubble={bubble} onIntent={handleIntent} />
            <PetFxLayer items={fxItems} onDone={handleFxDone} />
        </div>
    )
})

export default PetStage3D
