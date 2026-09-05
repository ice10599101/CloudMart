import { useEffect, useRef } from 'react'
import { Button, Modal } from 'antd'
import type { LevelUpEvent } from '@/api/wish'
import styles from './LevelUpModal.module.css'

interface LevelUpModalProps {
  /** 等级提升事件（null 时弹窗隐藏；由签到等响应携带） */
  levelUp: LevelUpEvent | null
  onClose: () => void
}

interface Particle {
  x: number
  y: number
  vx: number
  vy: number
  size: number
  color: string
  life: number
  decay: number
}

const LEVEL_TITLES: Record<number, string> = {
  1: '追梦新人',
  2: '梦想家',
  3: '追光者',
  4: '星火引路人',
  5: '宇宙守护者',
}

/** 粒子色板：星光金 + 心愿紫 + 微光青（与世界树视觉一致） */
const PARTICLE_COLORS = ['#FFD700', '#FFE98A', '#9370DB', '#00D4FF', '#FF9FF3', '#7EF0C0']

/**
 * 等级提升庆祝弹窗（文档 L1922：粒子炸裂庆祝动效）。
 *
 * <p>canvas 全屏粒子炸裂（无第三方依赖，rAF 驱动，弹窗关闭即停帧）；
 * 与移动三端动效节奏一致：炸裂约 1.8s、金色为主色调。</p>
 */
export default function LevelUpModal({ levelUp, onClose }: LevelUpModalProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const rafRef = useRef<number>(0)
  const particlesRef = useRef<Particle[]>([])

  useEffect(() => {
    if (!levelUp) return

    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    const resize = () => {
      canvas.width = window.innerWidth
      canvas.height = window.innerHeight
    }
    resize()
    window.addEventListener('resize', resize)

    // 双侧爆点：弹窗中心两侧各一（Modal 默认垂直居中）
    const cx = window.innerWidth / 2
    const cy = window.innerHeight / 2
    const particles: Particle[] = []
    for (const originX of [cx - 180, cx + 180]) {
      for (let i = 0; i < 90; i++) {
        // 粒子炸裂：均匀角度 + 随机速度/大小/寿命
        const angle = (Math.PI * 2 * i) / 90 + Math.random() * 0.2
        const speed = 3 + Math.random() * 7
        particles.push({
          x: originX,
          y: cy,
          vx: Math.cos(angle) * speed,
          vy: Math.sin(angle) * speed,
          size: 3 + Math.random() * 4,
          color: PARTICLE_COLORS[Math.floor(Math.random() * PARTICLE_COLORS.length)],
          life: 1,
          decay: 0.012 + Math.random() * 0.01,
        })
      }
    }
    particlesRef.current = particles

    const tick = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height)
      let alive = false
      for (const p of particlesRef.current) {
        if (p.life <= 0) continue
        alive = true
        p.x += p.vx
        p.y += p.vy
        p.vy += 0.12 // 重力
        p.vx *= 0.985
        p.vy *= 0.985
        p.life -= p.decay
        ctx.globalAlpha = Math.max(p.life, 0)
        ctx.fillStyle = p.color
        ctx.beginPath()
        ctx.arc(p.x, p.y, p.size * Math.max(p.life, 0.2), 0, Math.PI * 2)
        ctx.fill()
      }
      ctx.globalAlpha = 1
      if (alive) {
        rafRef.current = requestAnimationFrame(tick)
      }
    }
    rafRef.current = requestAnimationFrame(tick)

    return () => {
      cancelAnimationFrame(rafRef.current)
      window.removeEventListener('resize', resize)
    }
  }, [levelUp])

  return (
    <>
      {levelUp && <canvas ref={canvasRef} className={styles.canvas} aria-hidden />}
      <Modal
        open={Boolean(levelUp)}
        onCancel={onClose}
        footer={
          <Button type="primary" onClick={onClose}>
            开启新旅程
          </Button>
        }
        centered
        destroyOnClose
      >
        {levelUp && (
          <div style={{ padding: '8px 0' }}>
            <h2 className={styles.title}>✨ 等级提升 ✨</h2>
            <div className={styles.levelRow}>
              <div className={`${styles.levelBadge} ${styles.levelBadgeOld}`}>
                Lv.{levelUp.previousLevel}
              </div>
              <span className={styles.arrow}>➜</span>
              <div className={styles.levelBadge}>Lv.{levelUp.newLevel}</div>
            </div>
            <p className={styles.newTitle}>
              恭喜晋升「{levelUp.newLevelTitle || LEVEL_TITLES[levelUp.newLevel] || `Lv.${levelUp.newLevel}`}」
            </p>
            <p className={styles.desc}>你的坚持被宇宙看见了，继续许下心愿吧</p>
          </div>
        )}
      </Modal>
    </>
  )
}
