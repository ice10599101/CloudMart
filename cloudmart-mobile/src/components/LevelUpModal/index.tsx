import { useEffect, useMemo, useState } from 'react'
import { View, Text } from '@tarojs/components'
import Taro from '@tarojs/taro'
import type { LevelUpEvent } from '@/api/wish'
import styles from './index.module.scss'

interface LevelUpModalProps {
    /** 等级提升事件（null 时隐藏） */
    levelUp: LevelUpEvent | null
    /** 关闭回调（点击遮罩/按钮） */
    onClose: () => void
}

/** 粒子炸裂数量（小程序/H5 共用，控制渲染节点数） */
const PARTICLE_COUNT = 18
/** 粒子炸裂半径 rpx */
const BURST_RADIUS = 340

interface Particle {
    key: string
    size: number
    color: string
    dx: number
    dy: number
    duration: number
}

/**
 * 等级提升庆祝弹窗（文档 L1922：等级提升有庆祝弹窗（粒子炸裂））。
 *
 * 粒子炸裂用 transition 实现（mount 后位移到目标点），避免依赖 canvas 与
 * CSS 变量的跨端差异；触感反馈（vibrateShort）在支持的平台上增强仪式感。
 */
export default function LevelUpModal({ levelUp, onClose }: LevelUpModalProps) {
    const [burst, setBurst] = useState(false)

    useEffect(() => {
        if (!levelUp) {
            setBurst(false)
            return
        }
        Taro.vibrateShort({ type: 'light' }).catch(() => {
            // 平台不支持时静默降级
        })
        // 下一帧触发粒子从中心向四周炸裂（transition 驱动）
        const timer = setTimeout(() => setBurst(true), 50)
        return () => clearTimeout(timer)
    }, [levelUp])

    // 粒子角度均分 360°，颜色/大小按索引伪随机（保证重放稳定）
    const particles = useMemo<Particle[]>(() => {
        const colors = ['#ffd700', '#00d4ff', '#9370db', '#ff8fab']
        return Array.from({ length: PARTICLE_COUNT }, (_, i) => {
            const angle = (Math.PI * 2 * i) / PARTICLE_COUNT + (i % 3) * 0.08
            return {
                key: `p-${levelUp?.newLevel ?? 0}-${i}`,
                size: 8 + ((i * 7) % 10),
                color: colors[i % colors.length],
                dx: Math.cos(angle) * BURST_RADIUS,
                dy: Math.sin(angle) * BURST_RADIUS,
                duration: 0.7 + (i % 4) * 0.15,
            }
        })
    }, [levelUp?.newLevel])

    if (!levelUp) return null

    const handleClose = () => {
        Taro.vibrateShort({ type: 'light' }).catch(() => {})
        onClose()
    }

    return (
        <View className={styles.overlay} onClick={handleClose} catchMove>
            {/* 粒子炸裂层（pointer-events 由样式禁用，点击穿透到遮罩） */}
            <View className={styles.particleLayer} aria-hidden>
                {particles.map((p) => (
                    <View
                        key={p.key}
                        className={styles.particle}
                        style={{
                            width: `${p.size}rpx`,
                            height: `${p.size}rpx`,
                            backgroundColor: p.color,
                            transitionDuration: `${p.duration}s`,
                            transform: burst
                                ? `translate(${p.dx}rpx, ${p.dy}rpx) scale(0.2)`
                                : 'translate(0, 0) scale(1)',
                            opacity: burst ? 0 : 1,
                        }}
                    />
                ))}
            </View>

            <View className={styles.modal} onClick={(e) => e.stopPropagation()}>
                <View className={styles.ring} />
                <Text className={styles.title}>✨ 等级提升 ✨</Text>
                <View className={styles.levelRow}>
                    <View className={`${styles.levelBadge} ${styles.levelBadgeOld}`}>
                        <Text>Lv.{levelUp.previousLevel}</Text>
                    </View>
                    <Text className={styles.arrow}>➜</Text>
                    <View className={styles.levelBadge}>
                        <Text>Lv.{levelUp.newLevel}</Text>
                    </View>
                </View>
                <Text className={styles.newTitle}>恭喜晋升「{levelUp.newLevelTitle}」</Text>
                <View className={styles.confirmBtn} onClick={handleClose}>
                    <Text className={styles.confirmText}>开启新旅程</Text>
                </View>
            </View>
        </View>
    )
}
