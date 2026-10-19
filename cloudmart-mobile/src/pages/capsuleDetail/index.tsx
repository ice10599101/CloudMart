import { useState, useEffect, useMemo } from 'react'
import { View, Text, ScrollView, Image, Picker } from '@tarojs/components'
import Taro, { useRouter } from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import { WISH_THEME_STYLE } from '@/styles/wish-theme'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import WishBGM from '@/components/WishBGM'
import { reportTimezoneIfNeeded, localToUtcIso, getTimezoneId } from '@/utils/wish-timezone'
import { errorMessage } from '@/utils/request'
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
    const { user, isLoggedIn } = useAuthStore()
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
        // 修复：路由参数 id 恒为字符串，Number.isFinite(string) 恒 false 导致详情永不加载
        if (!capsuleId) {
            setLoading(false)
            return
        }
        wishApi
            .getCapsuleDetail(capsuleId)
            .then((res) => setCapsule(res.data.data))
            .catch((err) => {
                Taro.showToast({ title: errorMessage(err, '胶囊详情加载失败'), icon: 'none' })
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

    // T22 改期：仅作者本人、SEALED 且未到期、次数未超限时可见（上限服务端权威）
    const isReschedulable = (is: CapsuleItem) =>
        !!user && is.userId === user.id && is.status === 'SEALED'
        && remainMs > 0 && is.rescheduleCount < is.rescheduleLimit
    const [rescheduleDate, setRescheduleDate] = useState('')
    const [rescheduleTime, setRescheduleTime] = useState('')
    const [rescheduling, setRescheduling] = useState(false)

    const handleReschedule = async () => {
        if (rescheduling || !capsule || !rescheduleDate || !rescheduleTime) return
        const newOpenAt = localToUtcIso(rescheduleDate, rescheduleTime)
        if (new Date(newOpenAt).getTime() <= Date.now()) {
            Taro.showToast({ title: '新的开启时间必须在未来', icon: 'none' })
            return
        }
        setRescheduling(true)
        try {
            const res = await wishApi.rescheduleCapsule(capsule.id, {
                newOpenAt,
                timezone: getTimezoneId(),
            })
            setCapsule(res.data.data)
            setRescheduleDate('')
            setRescheduleTime('')
            Taro.vibrateShort({ type: 'light' })
            Taro.showToast({ title: '改期成功', icon: 'success' })
        } catch (err) {
            Taro.showToast({ title: errorMessage(err, '改期失败，请稍后再试'), icon: 'none' })
        } finally {
            setRescheduling(false)
        }
    }

    const handleOpen = async () => {
        if (opening || !capsule) return
        setOpening(true)
        try {
            const res = await wishApi.openCapsule(capsule.id)
            Taro.vibrateShort({ type: 'heavy' })
            setCapsule(res.data.data)
        } catch (err) {
            Taro.showToast({ title: errorMessage(err, '开启失败，请稍后再试'), icon: 'none' })
        } finally {
            setOpening(false)
        }
    }

    const handlePreview = (urls: string[], current: string) => {
        Taro.previewImage({ urls, current })
    }

    // R17 公共墙操作（申请/撤回/撤下），成功后本地刷新墙状态
    const refreshCapsule = async () => {
        try {
            const res = await wishApi.getCapsuleDetail(capsuleId)
            setCapsule(res.data.data)
        } catch {
            // 列表/详情以返回成功为准，查询失败忽略
        }
    }

    const handleApplyWall = (is: CapsuleItem) => {
        Taro.showModal({
            title: '直接上墙',
            content: '确认后这颗胶囊会立即匿名展示在公共墙（不公开你的身份、媒体附件）。',
            confirmText: '直接上墙',
            success: async (res) => {
                if (!res.confirm) return
                try {
                    await wishApi.applyCapsuleToWall(is.id)
                    Taro.showToast({ title: '已匿名发布到公共墙', icon: 'success' })
                    refreshCapsule()
                } catch (err) {
                    Taro.showToast({ title: errorMessage(err, '发布失败，请稍后再试'), icon: 'none' })
                }
            },
        })
    }

    const handleWithdrawWall = (is: CapsuleItem) => {
        const isApproved = is.wallStatus === 2
        Taro.showModal({
            title: isApproved ? '从公共墙撤下？' : '撤回上墙申请？',
            content: isApproved
                ? '撤下后胶囊将立即不在公共墙展示，之后可重新申请上墙'
                : '撤回后审核终止，之后可重新提交申请',
            confirmText: isApproved ? '确认撤下' : '撤回申请',
            confirmColor: '#e94560',
            success: async (res) => {
                if (!res.confirm) return
                try {
                    await wishApi.withdrawCapsuleWall(is.id)
                    Taro.showToast({ title: isApproved ? '已撤下' : '已撤回', icon: 'success' })
                    refreshCapsule()
                } catch (err) {
                    Taro.showToast({ title: errorMessage(err, '操作失败，请稍后再试'), icon: 'none' })
                }
            },
        })
    }

    /** R17 已开启胶囊的墙状态/操作卡片 */
    const renderWallCard = (is: CapsuleItem) => {
        const wall = is.wallStatus ?? 0
        return (
            <View className={styles.wallCard}>
                <Text className={styles.wallCardTitle}>🌌 公共胶囊墙</Text>
                {wall === 0 && (
                    <View className={styles.wallCardActions}>
                        <Text className={styles.wallCardTip}>确认后立即匿名展示在公共墙</Text>
                        <View className={styles.wallCardBtn} onClick={() => handleApplyWall(is)}>
                            <Text className={styles.wallCardBtnText}>直接上墙</Text>
                        </View>
                    </View>
                )}
                {wall === 1 && (
                    <View className={styles.wallCardActions}>
                        <Text className={styles.wallCardTip}>⏳ 审核中，请耐心等待</Text>
                        <View className={styles.wallCardBtnGhost} onClick={() => handleWithdrawWall(is)}>
                            <Text className={styles.wallCardGhostText}>撤回申请</Text>
                        </View>
                    </View>
                )}
                {wall === 2 && (
                    <View className={styles.wallCardActions}>
                        <Text className={styles.wallCardTip}>🌌 已在公共墙匿名展出</Text>
                        <View className={styles.wallCardBtnGhost} onClick={() => handleWithdrawWall(is)}>
                            <Text className={styles.wallCardGhostText}>撤下</Text>
                        </View>
                    </View>
                )}
                {wall === 3 && (
                    <View className={styles.wallCardActions}>
                        <Text className={styles.wallCardTip}>
                            未通过审核{is.wallReason ? `：${is.wallReason}` : ''}
                        </Text>
                        <View className={styles.wallCardBtn} onClick={() => handleApplyWall(is)}>
                            <Text className={styles.wallCardBtnText}>重新申请</Text>
                        </View>
                    </View>
                )}
                {wall === 4 && (
                    <View className={styles.wallCardActions}>
                        <Text className={styles.wallCardTip}>
                            已从公共墙撤下{is.wallReason ? `（${is.wallReason}）` : ''}
                        </Text>
                        <View className={styles.wallCardBtn} onClick={() => handleApplyWall(is)}>
                            <Text className={styles.wallCardBtnText}>重新申请</Text>
                        </View>
                    </View>
                )}
            </View>
        )
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
            {isReschedulable(is) && (
                <View className={styles.rescheduleCard}>
                    <Text className={styles.rescheduleTitle}>
                        改期（已用 {is.rescheduleCount}/{is.rescheduleLimit} 次）
                    </Text>
                    <View className={styles.reschedulePickers}>
                        <Picker
                            mode='date'
                            value={rescheduleDate}
                            onChange={(e) => setRescheduleDate(String(e.detail.value))}
                        >
                            <View className={styles.pickerItem}>
                                <Text className={styles.pickerText}>{rescheduleDate || '选择日期'}</Text>
                            </View>
                        </Picker>
                        <Picker
                            mode='time'
                            value={rescheduleTime}
                            onChange={(e) => setRescheduleTime(String(e.detail.value))}
                        >
                            <View className={styles.pickerItem}>
                                <Text className={styles.pickerText}>{rescheduleTime || '选择时间'}</Text>
                            </View>
                        </Picker>
                    </View>
                    <View className={styles.rescheduleBtn} onClick={handleReschedule}>
                        <Text className={styles.rescheduleBtnText}>{rescheduling ? '提交中…' : '确认改期'}</Text>
                    </View>
                </View>
            )}
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
            {user && is.userId === user.id && renderWallCard(is)}
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
