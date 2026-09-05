import { useEffect, useMemo, useRef } from 'react'
import { Animated, Easing, Modal, Pressable, Text, View } from 'react-native'
import { Vibration } from 'react-native'
import type { LevelUpEvent } from '@/api/wish'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'

interface LevelUpModalProps {
    /** 等级提升事件（null 时隐藏） */
    levelUp: LevelUpEvent | null
    /** 关闭回调（点击遮罩/按钮） */
    onClose: () => void
}

/** 粒子数量（RN Animated 节点，控制在渲染压力内） */
const PARTICLE_COUNT = 18
/** 粒子炸裂半径 px */
const BURST_RADIUS = 150

interface Particle {
    key: string
    size: number
    color: string
    dx: number
    dy: number
    duration: number
}

/**
 * 等级提升庆祝弹窗（文档 L1922/L1923：庆祝弹窗（粒子炸裂）+ APP 等级提升推送本地通知）。
 *
 * 粒子炸裂用 RN Animated 实现（每个粒子独立 translate + opacity + scale），
 * 触感反馈（Vibration.vibrate）增强仪式感；弹窗弹出为 spring 缓动。
 */
export default function LevelUpModal({ levelUp, onClose }: LevelUpModalProps) {
    const burstAnims = useRef<Animated.Value[]>([])
    const modalScale = useRef(new Animated.Value(0.7)).current
    const modalOpacity = useRef(new Animated.Value(0)).current

    // 粒子角度均分 360°，颜色/大小按索引伪随机（保证重放稳定）
    const particles = useMemo<Particle[]>(() => {
        const colors = ['#ffd700', '#00d4ff', '#9370db', '#ff8fab']
        return Array.from({ length: PARTICLE_COUNT }, (_, i) => {
            const angle = (Math.PI * 2 * i) / PARTICLE_COUNT + (i % 3) * 0.08
            return {
                key: `p-${levelUp?.newLevel ?? 0}-${i}`,
                size: 4 + (i % 3) * 2,
                color: colors[i % colors.length],
                dx: Math.cos(angle) * BURST_RADIUS,
                dy: Math.sin(angle) * BURST_RADIUS,
                duration: 700 + (i % 4) * 150,
            }
        })
    }, [levelUp?.newLevel])

    useEffect(() => {
        if (!levelUp) return
        Vibration.vibrate(30)

        // 弹窗弹出（spring）
        modalOpacity.setValue(0)
        modalScale.setValue(0.7)
        Animated.parallel([
            Animated.timing(modalOpacity, { toValue: 1, duration: 250, useNativeDriver: true }),
            Animated.spring(modalScale, { toValue: 1, friction: 6, tension: 40, useNativeDriver: true }),
        ]).start()

        // 粒子炸裂：每个粒子从中心位移到目标点并淡出
        burstAnims.current.forEach((anim) => anim.setValue(0))
        Animated.parallel(
            burstAnims.current.map((anim, i) =>
                Animated.timing(anim, {
                    toValue: 1,
                    duration: particles[i]?.duration ?? 800,
                    easing: Easing.out(Easing.cubic),
                    useNativeDriver: true,
                }),
            ),
        ).start()
    }, [levelUp, modalOpacity, modalScale, particles])

    // Animated.Value 池与粒子列表一一对应
    if (burstAnims.current.length !== PARTICLE_COUNT) {
        burstAnims.current = Array.from({ length: PARTICLE_COUNT }, () => new Animated.Value(0))
    }

    if (!levelUp) return null

    return (
        <Modal transparent visible animationType="none" onRequestClose={onClose}>
            <Pressable style={overlayStyle} onPress={onClose}>
                {/* 粒子炸裂层 */}
                <View style={particleLayerStyle} pointerEvents="none">
                    {particles.map((p, i) => {
                        const anim = burstAnims.current[i]
                        return (
                            <Animated.View
                                key={p.key}
                                style={{
                                    position: 'absolute',
                                    width: p.size,
                                    height: p.size,
                                    borderRadius: p.size / 2,
                                    backgroundColor: p.color,
                                    transform: [
                                        {
                                            translateX: anim.interpolate({
                                                inputRange: [0, 1],
                                                outputRange: [0, p.dx],
                                            }),
                                        },
                                        {
                                            translateY: anim.interpolate({
                                                inputRange: [0, 1],
                                                outputRange: [0, p.dy],
                                            }),
                                        },
                                        {
                                            scale: anim.interpolate({
                                                inputRange: [0, 1],
                                                outputRange: [1, 0.2],
                                            }),
                                        },
                                    ],
                                    opacity: anim.interpolate({
                                        inputRange: [0, 0.1, 1],
                                        outputRange: [1, 1, 0],
                                    }),
                                }}
                            />
                        )
                    })}
                </View>

                {/* 弹窗主体（点击不冒泡关闭） */}
                <Pressable onPress={(e) => e.stopPropagation()}>
                    <Animated.View
                        style={[
                            modalStyle,
                            { opacity: modalOpacity, transform: [{ scale: modalScale }] },
                        ]}
                    >
                        <Text style={titleStyle}>✨ 等级提升 ✨</Text>
                        <View style={levelRowStyle}>
                            <View style={[levelBadgeStyle, levelBadgeOldStyle]}>
                                <Text style={[levelBadgeTextStyle, { color: 'rgba(255,255,255,0.55)' }]}>
                                    Lv.{levelUp.previousLevel}
                                </Text>
                            </View>
                            <Text style={arrowStyle}>➜</Text>
                            <View style={levelBadgeStyle}>
                                <Text style={levelBadgeTextStyle}>Lv.{levelUp.newLevel}</Text>
                            </View>
                        </View>
                        <Text style={newTitleStyle}>恭喜晋升「{levelUp.newLevelTitle}」</Text>
                        <Pressable
                            onPress={onClose}
                            accessibilityLabel="开启新旅程"
                            accessibilityRole="button"
                            style={({ pressed }) => [
                                confirmBtnStyle,
                                pressed && { transform: [{ scale: 0.97 }] },
                            ]}
                        >
                            <Text style={confirmTextStyle}>开启新旅程</Text>
                        </Pressable>
                    </Animated.View>
                </Pressable>
            </Pressable>
        </Modal>
    )
}

const overlayStyle = {
    flex: 1,
    backgroundColor: 'rgba(4, 10, 28, 0.82)',
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
}

const particleLayerStyle = {
    position: 'absolute' as const,
    top: '40%' as const,
    left: '50%' as const,
    width: 0,
    height: 0,
}

const modalStyle = {
    width: 300,
    paddingVertical: Spacing.xl,
    paddingHorizontal: Spacing.lg,
    borderRadius: BorderRadius.xl,
    backgroundColor: '#101a3a',
    borderWidth: 1,
    borderColor: 'rgba(255, 215, 0, 0.35)',
    alignItems: 'center' as const,
}

const titleStyle = {
    fontSize: FontSize.xl,
    fontWeight: '700' as const,
    color: '#ffd700',
}

const levelRowStyle = {
    flexDirection: 'row' as const,
    alignItems: 'center' as const,
    gap: Spacing.md,
    marginTop: Spacing.lg,
}

const levelBadgeStyle = {
    minWidth: 72,
    paddingVertical: 8,
    paddingHorizontal: Spacing.md,
    borderRadius: BorderRadius.md,
    backgroundColor: 'rgba(255, 215, 0, 0.2)',
    borderWidth: 1,
    borderColor: 'rgba(255, 215, 0, 0.55)',
    alignItems: 'center' as const,
}

const levelBadgeOldStyle = {
    backgroundColor: 'rgba(255, 255, 255, 0.06)',
    borderColor: 'rgba(255, 255, 255, 0.25)',
}

const levelBadgeTextStyle = {
    fontSize: FontSize.lg,
    fontWeight: '700' as const,
    color: '#ffd700',
}

const arrowStyle = {
    fontSize: FontSize.md,
    color: 'rgba(255, 255, 255, 0.6)',
}

const newTitleStyle = {
    marginTop: Spacing.md,
    fontSize: FontSize.sm,
    color: 'rgba(255, 255, 255, 0.85)',
}

const confirmBtnStyle = {
    marginTop: Spacing.lg,
    paddingVertical: Spacing.sm,
    paddingHorizontal: Spacing.xl,
    borderRadius: BorderRadius.full,
    backgroundColor: '#ffd700',
}

const confirmTextStyle = {
    fontSize: FontSize.md,
    fontWeight: '600' as const,
    color: '#0c1b3a',
}
