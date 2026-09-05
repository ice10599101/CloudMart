import { useEffect, useRef, useState } from 'react'
import { Text } from '@tarojs/components'
import styles from './index.module.scss'

interface StarCountUpProps {
    /** 目标数值 */
    value: number
    /** 本次增量（>0 时触发滚动动画并短暂显示 +delta 徽标） */
    delta?: number
    /** 文本样式类名（外层控制字号/颜色） */
    className?: string
    /** 滚动时长 ms（默认 900，三端节奏一致） */
    duration?: number
}

/**
 * 星光余额数字滚动组件（文档 L1921：星光变化有数字滚动动效，三端节奏一致）。
 *
 * value 变化时从旧值 ease-out 滚到新值；delta > 0 时右上角浮现 +delta 星光徽标，
 * 2s 后淡出。小程序/H5 共用（Taro 组件，无 DOM 依赖）。
 */
export default function StarCountUp({ value, delta = 0, className, duration = 900 }: StarCountUpProps) {
    const [display, setDisplay] = useState(value)
    const [badgeVisible, setBadgeVisible] = useState(false)
    const prevValueRef = useRef(value)
    const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

    // 数字滚动：ease-out 曲线，从旧值过渡到新值
    useEffect(() => {
        const from = prevValueRef.current
        prevValueRef.current = value
        if (from === value) return

        const startAt = Date.now()
        const tick = () => {
            const progress = Math.min((Date.now() - startAt) / duration, 1)
            // ease-out cubic
            const eased = 1 - Math.pow(1 - progress, 3)
            setDisplay(Math.round(from + (value - from) * eased))
            if (progress < 1) {
                timerRef.current = setTimeout(tick, 16)
            }
        }
        timerRef.current = setTimeout(tick, 16)
        return () => {
            if (timerRef.current) clearTimeout(timerRef.current)
        }
    }, [value, duration])

    // +delta 徽标：出现 2s 后淡出
    useEffect(() => {
        if (delta <= 0) {
            setBadgeVisible(false)
            return
        }
        setBadgeVisible(true)
        const timer = setTimeout(() => setBadgeVisible(false), 2000)
        return () => clearTimeout(timer)
    }, [delta])

    return (
        <Text className={`${styles.wrap} ${className ?? ''}`}>
            <Text className={styles.value}>{display}</Text>
            {badgeVisible && delta > 0 && <Text className={styles.badge}>+{delta} ✨</Text>}
        </Text>
    )
}
