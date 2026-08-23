import { useState, useEffect, useCallback } from 'react'
import { View, Text, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import { WISH_THEME_STYLE } from '@/styles/wish-theme'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import { reportTimezoneIfNeeded } from '@/utils/wish-timezone'
import type { CapsuleItem, CapsuleStatus } from '@/types'
import styles from './index.module.scss'

const PAGE_SIZE = 20

interface StatusMeta {
    emoji: string
    label: string
    color: string
}

const STATUS_META: Record<CapsuleStatus, StatusMeta> = {
    SEALED: { emoji: '🔒', label: '封印中', color: '#4ecdc4' },
    AVAILABLE: { emoji: '🎁', label: '待开启', color: '#ffd700' },
    OPENED: { emoji: '💌', label: '已开启', color: '#3ddc97' },
    CANCELLED: { emoji: '🌑', label: '已取消', color: 'rgba(255,255,255,0.35)' },
}

const TABS: { key: CapsuleStatus; emoji: string; label: string }[] = [
    { key: 'SEALED', emoji: '🔒', label: '封印中' },
    { key: 'AVAILABLE', emoji: '🎁', label: '待开启' },
    { key: 'OPENED', emoji: '💌', label: '已开启' },
    { key: 'CANCELLED', emoji: '🌑', label: '已取消' },
]

function formatLocal(iso: string | null): string {
    if (!iso) return ''
    const d = new Date(iso)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function remainText(openAt: string): string {
    const ms = new Date(openAt).getTime() - Date.now()
    if (ms <= 0) return '已到期'
    const minutes = Math.floor(ms / 60000)
    if (minutes < 60) return `${minutes} 分钟后开启`
    const hours = Math.floor(minutes / 60)
    if (hours < 24) return `${hours} 小时后开启`
    const days = Math.floor(hours / 24)
    if (days < 365) return `${days} 天后开启`
    return `${Math.floor(days / 365)} 年后开启`
}

export default function CapsuleListPage() {
    const { statusBarHeight, navBarHeight } = getNavBarMetrics()
    const { isLoggedIn } = useAuthStore()
    const [statusFilter, setStatusFilter] = useState<CapsuleStatus>('SEALED')
    const [items, setItems] = useState<CapsuleItem[]>([])
    const [cursor, setCursor] = useState<string | undefined>()
    const [hasMore, setHasMore] = useState(false)
    const [loading, setLoading] = useState(true)
    const [loadingMore, setLoadingMore] = useState(false)

    useEffect(() => {
        if (!isLoggedIn) {
            Taro.redirectTo({ url: '/pages/login/index' })
            return
        }
        reportTimezoneIfNeeded()
    }, [isLoggedIn])

    useEffect(() => {
        setLoading(true)
        wishApi
            .listMyCapsules({ status: statusFilter, pageSize: PAGE_SIZE })
            .then((res) => {
                if (res.data.success) {
                    setItems(res.data.data)
                    setCursor(res.data.meta?.nextCursor ?? undefined)
                    setHasMore(Boolean(res.data.meta?.hasMore))
                }
            })
            .catch(() => {
                // 错误已由 request 处理
            })
            .finally(() => setLoading(false))
    }, [statusFilter])

    const handleLoadMore = useCallback(async () => {
        if (!hasMore || loadingMore || !cursor) return
        setLoadingMore(true)
        try {
            const res = await wishApi.listMyCapsules({ status: statusFilter, cursor, pageSize: PAGE_SIZE })
            if (res.data.success) {
                setItems((prev) => [...prev, ...res.data.data])
                setCursor(res.data.meta?.nextCursor ?? undefined)
                setHasMore(Boolean(res.data.meta?.hasMore))
            }
        } catch {
            // 静默：下拉重试
        } finally {
            setLoadingMore(false)
        }
    }, [cursor, hasMore, loadingMore, statusFilter])

    const handleCancel = (capsule: CapsuleItem) => {
        Taro.showModal({
            title: '取消这个胶囊？',
            content: '取消后内容永久无法开启，此操作不可恢复',
            confirmText: '取消胶囊',
            confirmColor: '#e94560',
            success: (res) => {
                if (!res.confirm) return
                wishApi
                    .cancelCapsule(capsule.id)
                    .then((r) => {
                        if (r.data.success) {
                            Taro.showToast({ title: '已取消', icon: 'success' })
                            setItems((prev) =>
                                prev.map((it) => (it.id === capsule.id ? r.data.data : it)),
                            )
                        }
                    })
                    .catch(() => {
                        // 错误已由 request 处理
                    })
            },
        })
    }

    const goDetail = (id: number) => {
        Taro.navigateTo({ url: `/pages/capsuleDetail/index?id=${id}` })
    }

    return (
        <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}rpx`, minHeight: '100vh' }}>
            <CustomNavBar title='我的时间胶囊' back />

            {/* 状态 Tabs */}
            <View className={styles.tabs}>
                {TABS.map((tab) => (
                    <View
                        key={tab.key}
                        className={`${styles.tab} ${statusFilter === tab.key ? styles.tabActive : ''}`}
                        onClick={() => setStatusFilter(tab.key)}
                    >
                        <Text className={styles.tabText}>
                            {tab.emoji} {tab.label}
                        </Text>
                    </View>
                ))}
            </View>

            <ScrollView scrollY className={styles.scroll} onScrollToLower={handleLoadMore}>
                {loading ? (
                    <View className={styles.emptyWrap}>
                        <Text className={styles.emptyText}>加载中...</Text>
                    </View>
                ) : items.length === 0 ? (
                    <View className={styles.emptyWrap}>
                        <Text className={styles.emptyEmoji}>
                            {statusFilter === 'SEALED' ? '🔒' : STATUS_META[statusFilter].emoji}
                        </Text>
                        <Text className={styles.emptyText}>
                            {statusFilter === 'SEALED' ? '还没有封印中的胶囊，写一封给未来的自己吧' : '这里还没有胶囊'}
                        </Text>
                        {statusFilter === 'SEALED' && (
                            <View
                                className={styles.createBtn}
                                onClick={() => Taro.navigateTo({ url: '/pages/capsuleCreate/index' })}
                            >
                                <Text className={styles.createBtnText}>封存第一个胶囊</Text>
                            </View>
                        )}
                    </View>
                ) : (
                    <View className={styles.cardList}>
                        {items.map((item) => {
                            const meta = STATUS_META[item.status]
                            const cancellable = item.status === 'SEALED' || item.status === 'AVAILABLE'
                            return (
                                <View key={item.id} className={styles.card} onClick={() => goDetail(item.id)}>
                                    <View className={styles.cardHeader}>
                                        <Text className={styles.cardEmoji}>{meta.emoji}</Text>
                                        <Text className={styles.cardTitle}>{item.title}</Text>
                                        <Text className={styles.cardStatus} style={{ color: meta.color }}>
                                            {meta.label}
                                        </Text>
                                    </View>
                                    <View className={styles.cardMeta}>
                                        <Text className={styles.metaLine}>
                                            {item.status === 'OPENED'
                                                ? `已于 ${formatLocal(item.openedAt)} 开启`
                                                : item.status === 'CANCELLED'
                                                    ? `封存于 ${formatLocal(item.createdAt)}`
                                                    : `${remainText(item.openAt)} · ${formatLocal(item.openAt)}`}
                                        </Text>
                                        <Text className={styles.metaSub}>创建时区 {item.openAtTimezone}</Text>
                                    </View>
                                    {cancellable && (
                                        <View
                                            className={styles.cancelLink}
                                            onClick={(e) => {
                                                e.stopPropagation()
                                                handleCancel(item)
                                            }}
                                        >
                                            <Text className={styles.cancelLinkText}>取消胶囊</Text>
                                        </View>
                                    )}
                                </View>
                            )
                        })}
                        {hasMore && (
                            <View className={styles.loadMore} onClick={handleLoadMore}>
                                <Text className={styles.loadMoreText}>{loadingMore ? '加载中...' : '加载更多'}</Text>
                            </View>
                        )}
                    </View>
                )}
                <View style={{ height: '60rpx' }} />
            </ScrollView>

            {/* 悬浮创建按钮 */}
            <View
                className={styles.fab}
                onClick={() => Taro.navigateTo({ url: '/pages/capsuleCreate/index' })}
            >
                <Text className={styles.fabText}>＋</Text>
            </View>
        </View>
    )
}
