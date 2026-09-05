import { useEffect, useRef, useState } from 'react'
import { Animated, Easing, Text, View } from 'react-native'
import { WishColors } from '@/constants/wish-theme'
import { FontSize } from '@/constants/theme'

interface StarCountUpProps {
    /** 目标数值 */
    value: number
    /** 本次增量（>0 时触发滚动动画并短暂显示 +delta 徽标） */
    delta?: number
    /** 字号（默认 30，MyWishes 余额卡片口径） */
    fontSize?: number
    /** 字体颜色（默认星光金） */
    color?: string
    /** 滚动时长 ms（默认 900，三端节奏一致） */
    duration?: number
}

/**
 * 星光余额数字滚动组件（文档 L1921：星光变化有数字滚动动效，三端节奏一致）。
 *
 * value 变化时从旧值 ease-out 滚到新值（Animated.Value 驱动，useNativeDriver
 * 不可用于文本，退回 JS 驱动的 setState 补间）；delta > 0 时右上角浮现
 * +delta 徽标（opacity + translateY 弹出），2s 后淡出。
 */
export default function StarCountUp({
    value,
    delta = 0,
    fontSize = 30,
    color = WishColors.accentGold,
    duration = 900,
}: StarCountUpProps) {
    const [display, setDisplay] = useState(value)
    const prevValueRef = useRef(value)
    const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
    const badgeOpacity = useRef(new Animated.Value(0)).current

    // 数字滚动：ease-out 曲线，从旧值补间到新值
    useEffect(() => {
        const from = prevValueRef.current
        prevValueRef.current = value
        if (from === value) return

        const startAt = Date.now()
        const tick = () => {
            const progress = Math.min((Date.now() - startAt) / duration, 1)
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

    // +delta 徽标：弹出后 2s 淡出
    useEffect(() => {
        if (delta <= 0) {
            badgeOpacity.setValue(0)
            return
        }
        Animated.sequence([
            Animated.timing(badgeOpacity, {
                toValue: 1,
                duration: 250,
                easing: Easing.out(Easing.ease),
                useNativeDriver: true,
            }),
            Animated.delay(2000),
            Animated.timing(badgeOpacity, {
                toValue: 0,
                duration: 350,
                useNativeDriver: true,
            }),
        ]).start()
    }, [delta, badgeOpacity])

    return (
        <View style={{ flexDirection: 'row', alignItems: 'baseline' }}>
            <Text style={{ fontSize, fontWeight: '700', color, lineHeight: fontSize + 4 }}>{display}</Text>
            {delta > 0 && (
                <Animated.Text
                    style={{
                        marginLeft: 6,
                        fontSize: FontSize.xs,
                        fontWeight: '600',
                        color: WishColors.accentGold,
                        opacity: badgeOpacity,
                    }}
                >
                    +{delta} ✨
                </Animated.Text>
            )}
        </View>
    )
}
