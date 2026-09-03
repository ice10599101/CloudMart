import { useState, useEffect, useMemo } from 'react'
import { View, Text, ScrollView, Image } from '@tarojs/components'
import Taro, { useRouter } from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import { WISH_THEME_STYLE } from '@/styles/wish-theme'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import WishBGM from '@/components/WishBGM'
import { reportTimezoneIfNeeded } from '@/utils/wish-timezone'
import type { CapsuleItem } from '@/types'
import styles from './index.module.scss'

const COUNTDOWN_INTERVAL_MS = 1000

function formatLocal(iso: string | null): string {
    if (!iso) return ''
    const d = new Date(iso)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatCountdown(ms: number): string {
    const totalSeconds = Math.max(0, Math.floor(ms / 1000))
    const days = Math.floor(totalSeconds / 86400)
    const hours = Math.floor((totalSeconds % 86400) / 3600)
    const minutes = Math.floor((totalSeconds % 3600) / 60)
    const seconds = totalSeconds % 60
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${days}天 ${pad(hours)}:${pad(minutes)}:${pad(seconds)}`
}

export default function CapsuleDetailPage() {
    const { statusBarHeight, navBarHeight } = getNavBarMetrics()
    const router = useRouter()
    const capsuleId = router.params.id ?? ''
    const { isLoggedIn } = useAuthStore()
    const [capsule, setCapsule] = useState<CapsuleItem | null>(null)
    const [loading, setLoading] = useState(true)
    const [opening, setOpening] = useState(false)
    const [now, setNow] = useState(Date.now())

    useEffect(() => {
        if (!isLoggedIn) {
            Taro.redirectTo({ url: '/pages/login/index' })
            return
        }
        reportTimezoneIfNeeded()
        if (!Number.isFinite(capsuleId)) return
        wishApi
            .getCapsuleDetail(capsuleId)
            .then((res) => {
                if (res.data.success) setCapsule(res.data.data)
            })
            .catch(() => {
                // 错误已由 request 处理
            })
            .finally(() => setLoading(false))
    }, [isLoggedIn, capsuleId])

    const isOpenable = useMemo(() => {
        if (!capsule) return false
        if (capsule.status === 'OPENED') return false
        if (capsule.status === 'CANCELLED') return false
        return new Date(capsule.openAt).getTime() <= now
    }, [capsule, now])

    // 倒计时：SEALED/AVAILABLE 且未到期时每秒刷新
    useEffect(() => {
        if (!capsule || capsule.status === 'OPENED' || capsule.status === 'CANCELLED') return
        if (new Date(capsule.openAt).getTime() <= Date.now()) return
        const timer = setInterval(() => setNow(Date.now()), COUNTDOWN_INTERVAL_MS)
        return () => clearInterval(timer)
    }, [capsule])

    const remainMs = capsule ? new Date(capsule.openAt).getTime() - now : 0

    const handleOpen = async () => {
        if (opening || !capsule) return
        setOpening(true)
        try {
            const res = await wishApi.openCapsule(capsule.id)
            if (res.data.success) {
                Taro.vibrateShort({ type: 'heavy' })
                setCapsule(res.data.data)
            }
        } catch {
            // 未到期 409 等错误已由 request 处理
        } finally {
            setOpening(false)
        }
    }

    const handlePreview = (urls: string[], current: string) => {
        Taro.previewImage({ urls, current })
    }

    const renderSealed = (is: CapsuleItem) => (
        <View className={styles.sealedView}>
            <View className={styles.sealBadge}>
                <Text className={styles.sealBadgeEmoji}>🔒</Text>
            </View>
            <Text className={styles.title}>{is.title}</Text>
            <Text className={styles.sealedTip}>
                {is.status === 'CANCELLED' ? '此胶囊已取消，内容永久封存' : '封印中 · 到期前任何人无法查看内容'}
            </Text>
            {is.status !== 'CANCELLED' && (
                <View className={styles.countdownCard}>
                    <Text className={styles.countdownLabel}>开启倒计时</Text>
                    <Text className={styles.countdownValue}>
                        {remainMs > 0 ? formatCountdown(remainMs) : '已到期，可以开启了'}
                    </Text>
                    <Text className={styles.countdownDate}>{formatLocal(is.openAt)}</Text>
                    <Text className={styles.countdownTz}>到期判定按 UTC · 跨时区旅行不影响</Text>
                </View>
            )}
            <View className={styles.metaRow}>
                <Text className={styles.metaText}>封存于 {formatLocal(is.createdAt)}</Text>
                <Text className={styles.metaText}>创建时区 {is.openAtTimezone}</Text>
            </View>
            {isOpenable && (
                <View
                    className={`${styles.openBtn} ${opening ? styles.openBtnLoading : ''}`}
                    onClick={handleOpen}
                >
                    <Text className={styles.openBtnText}>{opening ? '拆信中...' : '🎁 拆开胶囊'}</Text>
                </View>
            )}
        </View>
    )

    const renderOpened = (is: CapsuleItem) => (
        <View className={styles.revealedView}>
            <View className={styles.revealBadge}>
                <Text className={styles.revealEmoji}>💌</Text>
                {Array.from({ length: 8 }).map((_, i) => (
                    <View
                        key={i}
                        className={styles.revealStar}
                        style={{
                            left: `${50 + 42 * Math.cos((i / 8) * Math.PI * 2)}%`,
                            top: `${50 + 42 * Math.sin((i / 8) * Math.PI * 2)}%`,
                            animationDelay: `${(i % 4) * 0.08}s`,
                        }}
                    />
                ))}
            </View>
            <Text className={styles.title}>{is.title}</Text>
            <View className={styles.letterPaper}>
                <Text className={styles.letterContent}>{is.content}</Text>
            </View>
            {is.mediaUrls && is.mediaUrls.length > 0 && (
                <View className={styles.mediaGrid}>
                    {is.mediaUrls.map((url) => (
                        <Image
                            key={url}
                            className={styles.mediaImg}
                            src={url}
                            mode='aspectFill'
                            onClick={() => handlePreview(is.mediaUrls!, url)}
                        />
                    ))}
                </View>
            )}
            <View className={styles.metaRow}>
                <Text className={styles.metaText}>已于 {formatLocal(is.openedAt)} 开启</Text>
                <Text className={styles.metaText}>封存于 {formatLocal(is.createdAt)}</Text>
                <Text className={styles.metaText}>创建时区 {is.openAtTimezone}</Text>
            </View>
            <View
                className={styles.backBtn}
                onClick={() => Taro.navigateBack({ delta: 1 }).catch(() => Taro.reLaunch({ url: '/pages/capsuleList/index' }))}
            >
                <Text className={styles.backBtnText}>返回我的胶囊</Text>
            </View>
        </View>
    )

    return (
        <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}px`, minHeight: '100vh' }}>
            <CustomNavBar title='时间胶囊' back />
            <ScrollView scrollY className={styles.scroll}>
                {loading || !capsule ? (
                    <View className={styles.emptyWrap}>
                        <Text className={styles.emptyText}>加载中...</Text>
                    </View>
                ) : capsule.status === 'OPENED' ? (
                    renderOpened(capsule)
                ) : (
                    renderSealed(capsule)
                )}
                <View style={{ height: '80rpx' }} />
            </ScrollView>

            <WishBGM />
        </View>
    )
}
