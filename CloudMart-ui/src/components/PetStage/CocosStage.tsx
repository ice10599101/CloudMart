import {
  forwardRef,
  useEffect,
  useImperativeHandle,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import {
  PET_GAME_FRAME_PATH,
  PET_GAME_READY_TIMEOUT_MS,
  isGameToHost,
} from './bridge'
import type { BattleRound, GameToHost, HostToGame, PetDisplayState, PetIntentAction } from './bridge'
import styles from './style.module.css'

/**
 * Cocos 宠物舞台宿主组件（保留通路，默认不启用）。
 *
 * iframe 加载 pet-game web-mobile 构建产物（同源静态目录 /pet-game），postMessage 双向桥接；
 * 构建产物缺失（ready 超时）时 Fail-Open 渲染调用方提供的原生降级舞台。
 *
 * ⚠️ 现状说明：当前 cocos-cli 构建产物存在两处既有缺陷（spine 打桩模块 embind
 * "Cannot register public name '' twice" 中断 cc.game.init；内置 effect 缺少编译产物导致
 * "program not found"），舞台无法启动。因此默认走 native/PetStage3D（Three.js 原生实现）；
 * 待构建链路修复后，把 index.tsx 的 STAGE_ENGINE 切回 'cocos' 即可复用本组件。
 */

export interface CocosStageProps {
  pet: PetDisplayState | null
  onIntent: (action: PetIntentAction) => void
  onPetTapped?: () => void
  fallback?: ReactNode
  className?: string
}

interface BattleOverlayProps {
  rounds: BattleRound[]
  won: boolean
  onClose: () => void
}

/** 对战回合浮层（Cocos 场景内的演出互为冗余，可跳过） */
function BattleOverlay({ rounds, won, onClose }: BattleOverlayProps) {
  const [step, setStep] = useState(0)
  useEffect(() => {
    if (step >= rounds.length) {
      return
    }
    const timer = window.setTimeout(() => setStep((s) => s + 1), 900)
    return () => window.clearTimeout(timer)
  }, [step, rounds.length])

  return (
    <div className={styles.battleMask} onClick={onClose}>
      <div className={styles.battlePanel} onClick={(e) => e.stopPropagation()}>
        {step < rounds.length ? (
          <>
            <p className={styles.battleRound}>第 {rounds[step].round} 回合</p>
            <p className={styles.battleText}>
              {rounds[step].actorName} {rounds[step].dodged ? '出手，被闪开了！' : `造成 ${rounds[step].damage} 点伤害`}
              {rounds[step].critical && ' ⚡暴击'}
            </p>
            <p className={styles.battleText}>
              {rounds[step].targetName} 剩余 HP {rounds[step].targetRemainingHp}
            </p>
            <p className={styles.battleHint}>点击任意处加速</p>
          </>
        ) : (
          <>
            <p className={styles.battleResult}>{won ? '⚔️ 对战大获全胜！' : '💧 惜败了，下次再战！'}</p>
            <button type="button" className={styles.battleClose} onClick={onClose}>
              知道了
            </button>
          </>
        )}
      </div>
    </div>
  )
}

const CocosStage = forwardRef<{ post: (message: HostToGame) => void }, CocosStageProps>(function CocosStage(
  { pet, onIntent, onPetTapped, fallback, className },
  ref,
) {
  const frameRef = useRef<HTMLIFrameElement>(null)
  const [gameReady, setGameReady] = useState(false)
  const [gameFailed, setGameFailed] = useState(false)
  const [battle, setBattle] = useState<{ rounds: BattleRound[]; won: boolean } | null>(null)
  const petRef = useRef(pet)
  petRef.current = pet
  const gameReadyRef = useRef(false)

  useEffect(() => {
    gameReadyRef.current = gameReady
  }, [gameReady])

  useEffect(() => {
    const onMessage = (event: MessageEvent) => {
      if (!isGameToHost(event.data)) {
        return
      }
      const message = event.data as GameToHost
      if (message.type === 'ready') {
        setGameReady(true)
      } else if (message.type === 'intent') {
        onIntent(message.action)
      } else if (message.type === 'petTapped') {
        onPetTapped?.()
      }
    }
    window.addEventListener('message', onMessage)
    return () => window.removeEventListener('message', onMessage)
  }, [onIntent, onPetTapped])

  // ready 超时 → 构建产物缺失/加载失败，Fail-Open 切原生降级舞台
  useEffect(() => {
    const timer = window.setTimeout(() => {
      if (!gameReadyRef.current) {
        setGameFailed(true)
      }
    }, PET_GAME_READY_TIMEOUT_MS)
    return () => window.clearTimeout(timer)
  }, [])

  // 状态/初始化下发：游戏 ready 或宠物状态变化时同步
  useEffect(() => {
    if (gameReady && petRef.current) {
      frameRef.current?.contentWindow?.postMessage(
        { source: 'pet-host', type: 'init', pet: petRef.current },
        window.location.origin,
      )
    }
  }, [gameReady, pet])

  useImperativeHandle(ref, () => ({
    post: (message: HostToGame) => {
      if (message.type === 'battleRounds') {
        setBattle({ rounds: message.rounds, won: message.won })
      }
      frameRef.current?.contentWindow?.postMessage(message, window.location.origin)
    },
  }))

  return (
    <div className={`${styles.stage} ${className || ''}`}>
      {!gameFailed ? (
        <iframe
          ref={frameRef}
          title="宠物舞台"
          className={styles.frame}
          src={PET_GAME_FRAME_PATH}
        />
      ) : (
        (fallback ?? (
          <div className={styles.missing}>
            <span className={styles.missingEmoji}>🐾</span>
            <p>宠物舞台资源未部署（pet-game 构建产物缺失）</p>
            <p className={styles.missingHint}>
              构建方法见 pet-game/README.md；宠物养成业务不受影响
            </p>
          </div>
        ))
      )}
      {battle && (
        <BattleOverlay rounds={battle.rounds} won={battle.won} onClose={() => setBattle(null)} />
      )}
    </div>
  )
})

export default CocosStage
