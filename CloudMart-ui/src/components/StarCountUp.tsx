import { useEffect, useRef, useState } from 'react'
import styles from './StarCountUp.module.css'

interface StarCountUpProps {
  /** 目标数值（变化时触发滚动动画） */
  value: number
  /** 动画时长 ms（默认 800，三端节奏一致：星光变化约 0.8s） */
  duration?: number
  /** 是否展示 ⭐ 图标 */
  showIcon?: boolean
  /** 正向变化时的 +N 浮动提示（如签到 +5） */
  delta?: number
  className?: string
  style?: React.CSSProperties
}

/**
 * 星光余额数字滚动动效（文档 L1921：星光变化有数字滚动动效）。
 *
 * <p>requestAnimationFrame 缓动（easeOutCubic），数值变化时从旧值滚到新值；
 * 滚动期间只更新文本节点，不阻塞主线程。delta 非 0 时附带
 * +N / -N 浮动提示（金色上浮 / 红色下沉，方向差异，文档 L1956）。</p>
 */
export default function StarCountUp({
  value,
  duration = 800,
  showIcon = true,
  delta,
  className,
  style,
}: StarCountUpProps) {
  const [display, setDisplay] = useState(value)
  const fromRef = useRef(value)
  const rafRef = useRef<number>(0)
  const [floatKey, setFloatKey] = useState(0)
  const prevDeltaRef = useRef(delta)

  useEffect(() => {
    const from = fromRef.current
    if (from === value) return

    const start = performance.now()
    const tick = (now: number) => {
      const t = Math.min((now - start) / duration, 1)
      // easeOutCubic：先快后慢，星光入账观感
      const eased = 1 - Math.pow(1 - t, 3)
      setDisplay(Math.round(from + (value - from) * eased))
      if (t < 1) {
        rafRef.current = requestAnimationFrame(tick)
      } else {
        fromRef.current = value
      }
    }
    rafRef.current = requestAnimationFrame(tick)

    return () => cancelAnimationFrame(rafRef.current)
  }, [value, duration])

  // delta 变化才重新播浮动提示（余额刷新但 delta 相同引用时不重复播放）
  useEffect(() => {
    if (delta !== undefined && delta !== 0 && delta !== prevDeltaRef.current) {
      setFloatKey((k) => k + 1)
    }
    prevDeltaRef.current = delta
  }, [delta])

  return (
    <span className={`${styles.wrap} ${className ?? ''}`} style={style}>
      {showIcon && <span aria-hidden>⭐</span>}
      <span className={styles.num}>{display}</span>
      {delta !== undefined && delta !== 0 && (
        <span
          key={floatKey}
          className={`${styles.float} ${delta > 0 ? styles.floatUp : styles.floatDown}`}
          aria-hidden
        >
          {delta > 0 ? `+${delta}` : delta}
        </span>
      )}
    </span>
  )
}
