import { View, Text, ScrollView, TouchableOpacity, Image, ActivityIndicator, Modal, Animated, Easing, Alert } from 'react-native'
import { useEffect, useMemo, useState } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import * as Haptics from 'expo-haptics'
import { wishApi } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'
import { formatLocal } from '@/utils/wish-timezone'
import { cancelCapsuleReminder } from '@/utils/capsule-notifications'
import type { CapsuleItem } from '@/types'

/** 拆信动效节奏：封蜡碎裂(0.5s) → 信封翻盖(0.6s) → 信纸升起(0.7s)，与 Web/移动端一致 */
const OPEN_ANIM_MS = 1800

/** 拆信仪式遮罩：三阶段 Animated（火蜡淡出 → 信封展开 → 信纸升起） */
function OpeningCeremony({ visible }: { visible: boolean }) {
    // Animated.Value 为可变驱动对象：useMemo 固定实例（组件生命周期内恒定，非渲染数据）
    const waxOpacity = useMemo(() => new Animated.Value(1), [])
    const waxScale = useMemo(() => new Animated.Value(1), [])
    const flapRotate = useMemo(() => new Animated.Value(0), [])
    const letterY = useMemo(() => new Animated.Value(40), [])
    const letterOpacity = useMemo(() => new Animated.Value(0), [])

    useEffect(() => {
        if (!visible) return
        waxOpacity.setValue(1)
        waxScale.setValue(1)
        flapRotate.setValue(0)
        letterY.setValue(40)
        letterOpacity.setValue(0)
        Animated.sequence([
            // 封蜡碎裂
            Animated.parallel([
                Animated.timing(waxOpacity, { toValue: 0, duration: 500, useNativeDriver: true }),
                Animated.timing(waxScale, { toValue: 1.8, duration: 500, easing: Easing.out(Easing.cubic), useNativeDriver: true }),
            ]),
            // 信封翻盖
            Animated.timing(flapRotate, { toValue: 1, duration: 600, easing: Easing.inOut(Easing.quad), useNativeDriver: true }),
            // 信纸升起
            Animated.parallel([
                Animated.timing(letterY, { toValue: 0, duration: 700, easing: Easing.out(Easing.cubic), useNativeDriver: true }),
                Animated.timing(letterOpacity, { toValue: 1, duration: 700, useNativeDriver: true }),
            ]),
        ]).start()
    }, [visible, waxOpacity, waxScale, flapRotate, letterY, letterOpacity])

    const flapInterpolate = flapRotate.interpolate({
        inputRange: [0, 1],
        outputRange: ['0deg', '-150deg'],
    })

    return (
        <Modal visible={visible} transparent animationType="fade">
            <View style={{ flex: 1, backgroundColor: 'rgba(5,8,20,0.94)', justifyContent: 'center', alignItems: 'center' }}>
                <View style={{ width: 150, height: 100, alignItems: 'center', justifyContent: 'flex-start' }}>
                    {/* 信封翻盖 */}
                    <Animated.View
                        style={{
                            position: 'absolute',
                            top: 0,
                            width: 0,
                            height: 0,
                            borderLeftWidth: 40,
                            borderRightWidth: 40,
                            borderBottomWidth: 34,
                            borderLeftColor: 'transparent',
                            borderRightColor: 'transparent',
                            borderBottomColor: '#3d5a80',
                            transform: [{ rotateX: flapInterpolate }, { translateY: -16 }],
                        }}
                    />
                    {/* 信封主体 */}
                    <View
                        style={{
                            width: 140,
                            height: 90,
                            borderRadius: 6,
                            backgroundColor: '#2c3e63',
                            borderWidth: 1,
                            borderColor: 'rgba(255,255,255,0.18)',
                            marginTop: 18,
                        }}
                    />
                    {/* 信纸升起 */}
                    <Animated.View
                        style={{
                            position: 'absolute',
                            bottom: 6,
                            width: 110,
                            paddingVertical: 10,
                            borderRadius: 6,
                            backgroundColor: '#f5f0e1',
                            alignItems: 'center',
                            opacity: letterOpacity,
                            transform: [{ translateY: letterY }],
                        }}
                    >
                        <Text style={{ fontSize: 26 }}>💌</Text>
                    </Animated.View>
                    {/* 火漆封印 */}
                    <Animated.View style={{ position: 'absolute', top: 6, opacity: waxOpacity, transform: [{ scale: waxScale }] }}>
                        <Text style={{ fontSize: 34 }}>🔴</Text>
                    </Animated.View>
                </View>
                <Text style={{ marginTop: Spacing.xl, fontSize: FontSize.md, color: 'rgba(255,255,255,0.7)' }}>正在拆封……</Text>
            </View>
        </Modal>
    )
}

export default function CapsuleDetailScreen() {
    const insets = useSafeAreaInsets()
    const params = useLocalSearchParams<{ id?: string }>()
    const capsuleId = Number(params.id)
    const isLoggedIn = useAuthStore((s) => s.isLoggedIn)

    const [capsule, setCapsule] = useState<CapsuleItem | null>(null)
    const [loading, setLoading] = useState(true)
    const [opening, setOpening] = useState(false)
    const [revealed, setRevealed] = useState(false)
    // 倒计时基准时间：初始 0（capsule 未加载前不参与判定），加载成功/定时器回调中更新
    const [now, setNow] = useState(0)

    useEffect(() => {
        if (!isLoggedIn) {
            router.replace('/login')
            return
        }
        if (!Number.isFinite(capsuleId)) {
            // 非法路由参数：直接回列表（不在 effect 内同步 setState）
            router.replace('/capsule/list')
            return
        }
        wishApi
            .getCapsuleDetail(capsuleId)
            .then((res) => {
                if (res.data?.success) {
                    setCapsule(res.data.data)
                    setNow(Date.now())
                    if (res.data.data.status === 'OPENED') setRevealed(true)
                }
            })
            .catch(() => {
                // 错误已由 request 拦截器处理（404 = 不存在或非本人）
            })
            .finally(() => setLoading(false))
    }, [isLoggedIn, capsuleId])

    // 封印中倒计时：每 30 秒刷新（到期瞬间亮出拆开按钮，容忍扫描间隙）
    useEffect(() => {
        if (!capsule || revealed) return
        const timer = setInterval(() => setNow(Date.now()), 30_000)
        return () => clearInterval(timer)
    }, [capsule, revealed])

    const expired = useMemo(
        () => (capsule ? new Date(capsule.openAt).getTime() <= now : false),
        [capsule, now],
    )

    const handleOpen = async () => {
        if (opening) return
        setOpening(true)
        Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => undefined)
        try {
            const res = await wishApi.openCapsule(capsuleId)
            if (res.data?.success) {
                // 已开启：撤销到期本地推送（静默降级）
                cancelCapsuleReminder(capsuleId).catch(() => undefined)
                Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => undefined)
                // 拆信动效播完再揭晓内容（未到期 409/已取消会被 catch，动效不触发）
                setTimeout(() => {
                    setCapsule(res.data.data)
                    setRevealed(true)
                    setOpening(false)
                }, OPEN_ANIM_MS)
            } else {
                setOpening(false)
            }
        } catch {
            Alert.alert('提示', '现在还不能开启，请稍后重试')
            setOpening(false)
        }
    }

    const handleCancel = () => {
        if (!capsule) return
        Alert.alert('取消这个胶囊？', '取消后内容永久无法开启，此操作不可恢复', [
            { text: '再想想', style: 'cancel' },
            {
                text: '取消胶囊',
                style: 'destructive',
                onPress: () => {
                    wishApi
                        .cancelCapsule(capsule.id)
                        .then((r) => {
                            if (r.data?.success) {
                                cancelCapsuleReminder(capsule.id).catch(() => undefined)
                                setCapsule(r.data.data)
                            }
                        })
                        .catch(() => {
                            // 错误已由 request 拦截器处理
                        })
                },
            },
        ])
    }

    if (loading) {
        return (
            <View style={{ flex: 1, backgroundColor: WishColors.bgBase, justifyContent: 'center', alignItems: 'center' }}>
                <ActivityIndicator size="large" color={WishColors.accentCyan} />
                <Text style={{ marginTop: Spacing.md, fontSize: FontSize.sm, color: WishColors.textTertiary }}>胶囊苏醒中...</Text>
            </View>
        )
    }

    if (!capsule) {
        return (
            <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top, justifyContent: 'center', alignItems: 'center' }}>
                <Text style={{ fontSize: 56 }}>🕳️</Text>
                <Text style={{ marginTop: Spacing.md, fontSize: FontSize.md, color: WishColors.textSecondary }}>胶囊不存在或不属于你</Text>
                <TouchableOpacity
                    onPress={() => router.replace('/capsule/list')}
                    style={{ marginTop: Spacing.lg, paddingHorizontal: Spacing.xl, paddingVertical: Spacing.sm + 4, borderRadius: 24, borderWidth: 1, borderColor: '#4ecdc4' }}
                >
                    <Text style={{ color: '#4ecdc4', fontSize: FontSize.sm }}>返回我的胶囊</Text>
                </TouchableOpacity>
            </View>
        )
    }

    return (
        <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
            {/* 顶栏 */}
            <View
                style={{
                    flexDirection: 'row',
                    alignItems: 'center',
                    padding: Spacing.md,
                    borderBottomWidth: 1,
                    borderBottomColor: WishColors.border,
                }}
            >
                <TouchableOpacity onPress={() => router.back()}>
                    <Text style={{ fontSize: FontSize.lg, color: WishColors.textSecondary }}>‹ 返回</Text>
                </TouchableOpacity>
                <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text, marginLeft: Spacing.md }} numberOfLines={1}>
                    时间胶囊
                </Text>
            </View>

            {/* 已取消：终态展示 */}
            {capsule.status === 'CANCELLED' ? (
                <ScrollView contentContainerStyle={{ padding: Spacing.xl, alignItems: 'center', paddingTop: 100 }}>
                    <Text style={{ fontSize: 64 }}>🌑</Text>
                    <Text style={{ marginTop: Spacing.md, fontSize: FontSize.xl, fontWeight: '700', color: WishColors.text, textAlign: 'center' }}>
                        {capsule.title}
                    </Text>
                    <Text style={{ marginTop: Spacing.sm, fontSize: FontSize.sm, lineHeight: 22, textAlign: 'center', color: WishColors.textTertiary }}>
                        该胶囊已被取消{'\n'}封存内容永久不可开启
                    </Text>
                    <TouchableOpacity
                        onPress={() => router.replace('/capsule/list')}
                        style={{
                            marginTop: Spacing.xl,
                            paddingHorizontal: Spacing.xl,
                            paddingVertical: Spacing.sm + 4,
                            borderRadius: 28,
                            backgroundColor: '#2a9d8f',
                        }}
                    >
                        <Text style={{ color: '#fff', fontSize: FontSize.md }}>返回我的胶囊</Text>
                    </TouchableOpacity>
                </ScrollView>
            ) : !revealed ? (
                /* 未开启：封印视图（倒计时 / 待拆封） */
                <ScrollView contentContainerStyle={{ padding: Spacing.xl, alignItems: 'center', paddingTop: 80 }}>
                    <Text style={{ fontSize: FontSize.xl, fontWeight: '700', color: WishColors.text, textAlign: 'center' }}>
                        {capsule.title}
                    </Text>

                    {/* 信封封印 */}
                    <View style={{ width: 150, height: 110, alignItems: 'center', justifyContent: 'flex-start', marginTop: Spacing.xl }}>
                        <View
                            style={{
                                width: 140,
                                height: 90,
                                borderRadius: 6,
                                backgroundColor: '#2c3e63',
                                borderWidth: 1,
                                borderColor: 'rgba(255,255,255,0.18)',
                                marginTop: 18,
                            }}
                        />
                        <Text style={{ position: 'absolute', top: 4, fontSize: 34 }}>🔴</Text>
                        {capsule.status === 'AVAILABLE' && <Text style={{ position: 'absolute', bottom: 4, fontSize: 22 }}>🎁</Text>}
                    </View>

                    {capsule.status === 'SEALED' && !expired ? (
                        <>
                            <Text style={{ marginTop: Spacing.lg, fontSize: FontSize.sm, color: WishColors.textSecondary }}>🔒 封印中 · 内容被时间封存</Text>
                            <CountdownRow openAt={capsule.openAt} now={now} />
                            <Text style={{ marginTop: Spacing.md, fontSize: FontSize.xs, lineHeight: 18, textAlign: 'center', color: WishColors.textTertiary }}>
                                预定开启：{formatLocal(capsule.openAt)}{'\n'}创建时区 {capsule.openAtTimezone} · 按 UTC 判定，跨时区不影响到期
                            </Text>
                        </>
                    ) : (
                        <>
                            <Text style={{ marginTop: Spacing.lg, fontSize: FontSize.md, color: '#ffd700' }}>🎁 到了拆开它的时刻</Text>
                            <TouchableOpacity
                                activeOpacity={0.85}
                                onPress={handleOpen}
                                disabled={opening}
                                style={{
                                    marginTop: Spacing.lg,
                                    paddingHorizontal: Spacing.xl * 2,
                                    paddingVertical: Spacing.md,
                                    borderRadius: 28,
                                    backgroundColor: opening ? 'rgba(42,157,143,0.5)' : '#2a9d8f',
                                    shadowColor: '#4ecdc4',
                                    shadowOpacity: 0.4,
                                    shadowRadius: 12,
                                    shadowOffset: { width: 0, height: 4 },
                                    elevation: 6,
                                }}
                            >
                                {opening ? (
                                    <ActivityIndicator size="small" color="#fff" />
                                ) : (
                                    <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: '#fff' }}>拆开胶囊</Text>
                                )}
                            </TouchableOpacity>
                            <TouchableOpacity onPress={handleCancel} hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }} style={{ marginTop: Spacing.lg }}>
                                <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, textDecorationLine: 'underline' }}>取消这个胶囊</Text>
                            </TouchableOpacity>
                        </>
                    )}
                </ScrollView>
            ) : (
                /* 已开启：拆信揭晓 */
                <ScrollView contentContainerStyle={{ padding: Spacing.lg, paddingBottom: insets.bottom + 60 }}>
                    <View style={{ alignItems: 'center', marginTop: Spacing.md }}>
                        <Text style={{ fontSize: 56 }}>💌</Text>
                        <Text style={{ marginTop: Spacing.md, fontSize: FontSize.xl, fontWeight: '700', color: WishColors.text, textAlign: 'center' }}>
                            {capsule.title}
                        </Text>
                    </View>

                    {/* 信纸 */}
                    <View
                        style={{
                            marginTop: Spacing.lg,
                            padding: Spacing.lg,
                            borderRadius: BorderRadius.lg,
                            backgroundColor: '#f5f0e1',
                        }}
                    >
                        <Text style={{ fontSize: FontSize.md, lineHeight: 28, color: '#3a3226' }}>{capsule.content}</Text>
                    </View>

                    {capsule.mediaUrls && capsule.mediaUrls.length > 0 && (
                        <View style={{ marginTop: Spacing.md, gap: Spacing.sm }}>
                            {capsule.mediaUrls.map((url) => (
                                <Image
                                    key={url}
                                    source={{ uri: url }}
                                    style={{ width: '100%', height: 220, borderRadius: BorderRadius.md }}
                                    resizeMode="cover"
                                />
                            ))}
                        </View>
                    )}

                    <View style={{ marginTop: Spacing.lg, gap: Spacing.xs }}>
                        <Text style={{ fontSize: FontSize.xs, color: '#3ddc97' }}>已于 {formatLocal(capsule.openedAt)} 开启</Text>
                        <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>封存于 {formatLocal(capsule.createdAt)}</Text>
                        <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>创建时区 {capsule.openAtTimezone}</Text>
                    </View>

                    <TouchableOpacity
                        onPress={() => router.replace('/capsule/list')}
                        style={{
                            marginTop: Spacing.xl,
                            paddingVertical: Spacing.md,
                            borderRadius: 28,
                            backgroundColor: '#2a9d8f',
                            alignItems: 'center',
                        }}
                    >
                        <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: '#fff' }}>返回我的胶囊</Text>
                    </TouchableOpacity>
                </ScrollView>
            )}

            <OpeningCeremony visible={opening} />
        </View>
    )
}

/** 封印中倒计时：天 + 时（与 Web 端口径一致，分钟级刷新） */
function CountdownRow({ openAt, now }: { openAt: string; now: number }) {
    const remain = Math.max(0, new Date(openAt).getTime() - now)
    const days = Math.floor(remain / 86400000)
    const hours = Math.floor((remain % 86400000) / 3600000)
    return (
        <View style={{ flexDirection: 'row', gap: Spacing.lg, marginTop: Spacing.lg }}>
            <View style={{ alignItems: 'center', minWidth: 90, padding: Spacing.md, borderRadius: BorderRadius.lg, backgroundColor: WishColors.bgContainer, borderWidth: 1, borderColor: WishColors.border }}>
                <Text style={{ fontSize: 34, fontWeight: '700', color: '#4ecdc4' }}>{days}</Text>
                <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>天</Text>
            </View>
            <View style={{ alignItems: 'center', minWidth: 90, padding: Spacing.md, borderRadius: BorderRadius.lg, backgroundColor: WishColors.bgContainer, borderWidth: 1, borderColor: WishColors.border }}>
                <Text style={{ fontSize: 34, fontWeight: '700', color: '#4ecdc4' }}>{hours}</Text>
                <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>时</Text>
            </View>
        </View>
    )
}
