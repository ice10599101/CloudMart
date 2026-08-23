import { View, Text, FlatList, TouchableOpacity, ActivityIndicator, Alert } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'
import { formatLocal, reportTimezoneIfNeeded } from '@/utils/wish-timezone'
import { cancelCapsuleReminder } from '@/utils/capsule-notifications'
import type { CapsuleItem, CapsuleStatus } from '@/types'

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

const TABS: { key: CapsuleStatus; label: string }[] = [
    { key: 'SEALED', label: '🔒 封印中' },
    { key: 'AVAILABLE', label: '🎁 待开启' },
    { key: 'OPENED', label: '💌 已开启' },
    { key: 'CANCELLED', label: '🌑 已取消' },
]

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

export default function CapsuleListScreen() {
    const insets = useSafeAreaInsets()
    const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
    const [statusFilter, setStatusFilter] = useState<CapsuleStatus>('SEALED')
    const [items, setItems] = useState<CapsuleItem[]>([])
    const [cursor, setCursor] = useState<string | undefined>()
    const [hasMore, setHasMore] = useState(false)
    const [loading, setLoading] = useState(true)
    const [loadingMore, setLoadingMore] = useState(false)

    useEffect(() => {
        if (!isLoggedIn) {
            router.replace('/login')
            return
        }
        reportTimezoneIfNeeded()
    }, [isLoggedIn])

    useEffect(() => {
        wishApi
            .listMyCapsules({ status: statusFilter, pageSize: PAGE_SIZE })
            .then((res) => {
                if (res.data?.success) {
                    setItems(res.data.data)
                    setCursor(res.data.meta?.nextCursor ?? undefined)
                    setHasMore(Boolean(res.data.meta?.hasMore))
                }
            })
            .catch(() => {
                // 错误已由 request 拦截器处理
            })
            .finally(() => setLoading(false))
    }, [statusFilter])

    /** 切换状态页签：事件回调中重置加载态（避免 effect 内同步 setState 级联渲染） */
    const selectFilter = (key: CapsuleStatus) => {
        if (key === statusFilter) return
        setLoading(true)
        setStatusFilter(key)
    }

    const loadMore = useCallback(async () => {
        if (!hasMore || loadingMore || !cursor) return
        setLoadingMore(true)
        try {
            const res = await wishApi.listMyCapsules({ status: statusFilter, cursor, pageSize: PAGE_SIZE })
            if (res.data?.success) {
                setItems((prev) => [...prev, ...res.data.data])
                setCursor(res.data.meta?.nextCursor ?? undefined)
                setHasMore(Boolean(res.data.meta?.hasMore))
            }
        } catch {
            // 静默：上拉重试
        } finally {
            setLoadingMore(false)
        }
    }, [cursor, hasMore, loadingMore, statusFilter])

    const handleCancel = (capsule: CapsuleItem) => {
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
                                setItems((prev) => prev.map((it) => (it.id === capsule.id ? r.data.data : it)))
                            }
                        })
                        .catch(() => {
                            // 错误已由 request 拦截器处理
                        })
                },
            },
        ])
    }

    const renderItem = ({ item }: { item: CapsuleItem }) => {
        const meta = STATUS_META[item.status]
        const cancellable = item.status === 'SEALED' || item.status === 'AVAILABLE'
        return (
            <TouchableOpacity
                activeOpacity={0.85}
                onPress={() => router.push(`/capsule/${item.id}`)}
                style={{
                    backgroundColor: WishColors.bgContainer,
                    borderWidth: 1,
                    borderColor: WishColors.border,
                    borderRadius: BorderRadius.lg,
                    padding: Spacing.md,
                    marginBottom: Spacing.sm + 4,
                }}
            >
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }}>
                    <Text style={{ fontSize: 24 }}>{meta.emoji}</Text>
                    <Text style={{ flex: 1, fontSize: FontSize.md, fontWeight: '700', color: WishColors.text }} numberOfLines={1}>
                        {item.title}
                    </Text>
                    <Text style={{ fontSize: FontSize.xs, color: meta.color }}>{meta.label}</Text>
                </View>
                <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary, marginTop: Spacing.sm }}>
                    {item.status === 'OPENED'
                        ? `已于 ${formatLocal(item.openedAt)} 开启`
                        : item.status === 'CANCELLED'
                            ? `封存于 ${formatLocal(item.createdAt)}`
                            : `${remainText(item.openAt)} · ${formatLocal(item.openAt)}`}
                </Text>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: Spacing.xs }}>
                    <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>创建时区 {item.openAtTimezone}</Text>
                    {cancellable && (
                        <TouchableOpacity
                            onPress={() => handleCancel(item)}
                            hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
                        >
                            <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, textDecorationLine: 'underline' }}>
                                取消胶囊
                            </Text>
                        </TouchableOpacity>
                    )}
                </View>
            </TouchableOpacity>
        )
    }

    return (
        <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
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
                <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text, marginLeft: Spacing.md }}>
                    我的时间胶囊
                </Text>
            </View>

            {/* 状态 Tabs */}
            <View style={{ flexDirection: 'row', gap: Spacing.sm, padding: Spacing.md }}>
                {TABS.map((tab) => (
                    <TouchableOpacity
                        key={tab.key}
                        onPress={() => selectFilter(tab.key)}
                        style={{
                            flex: 1,
                            alignItems: 'center',
                            paddingVertical: 6,
                            borderRadius: 20,
                            borderWidth: 1,
                            borderColor: statusFilter === tab.key ? '#4ecdc4' : WishColors.border,
                            backgroundColor: statusFilter === tab.key ? 'rgba(78,205,196,0.14)' : 'transparent',
                        }}
                    >
                        <Text
                            style={{
                                fontSize: FontSize.xs,
                                color: statusFilter === tab.key ? '#4ecdc4' : WishColors.textSecondary,
                            }}
                        >
                            {tab.label}
                        </Text>
                    </TouchableOpacity>
                ))}
            </View>

            {loading ? (
                <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
                    <ActivityIndicator color={WishColors.primary} />
                </View>
            ) : (
                <FlatList
                    data={items}
                    keyExtractor={(item) => String(item.id)}
                    renderItem={renderItem}
                    onEndReached={loadMore}
                    onEndReachedThreshold={0.3}
                    contentContainerStyle={{ padding: Spacing.md, paddingTop: 0, paddingBottom: insets.bottom + 100 }}
                    ListEmptyComponent={
                        <View style={{ alignItems: 'center', paddingVertical: 80 }}>
                            <Text style={{ fontSize: 48 }}>{statusFilter === 'SEALED' ? '🔒' : STATUS_META[statusFilter].emoji}</Text>
                            <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary, marginTop: Spacing.md, textAlign: 'center' }}>
                                {statusFilter === 'SEALED' ? '还没有封印中的胶囊\n写一封给未来的自己吧' : '这里还没有胶囊'}
                            </Text>
                            {statusFilter === 'SEALED' && (
                                <TouchableOpacity
                                    onPress={() => router.push('/capsule-create')}
                                    style={{
                                        marginTop: Spacing.lg,
                                        paddingHorizontal: Spacing.xl,
                                        paddingVertical: Spacing.sm + 4,
                                        borderRadius: 24,
                                        borderWidth: 1,
                                        borderColor: '#4ecdc4',
                                    }}
                                >
                                    <Text style={{ color: '#4ecdc4', fontSize: FontSize.sm }}>封存第一个胶囊</Text>
                                </TouchableOpacity>
                            )}
                        </View>
                    }
                    ListFooterComponent={
                        loadingMore ? <ActivityIndicator color={WishColors.primary} style={{ marginVertical: Spacing.md }} /> : null
                    }
                />
            )}

            {/* 悬浮创建按钮 */}
            <TouchableOpacity
                onPress={() => router.push('/capsule-create')}
                style={{
                    position: 'absolute',
                    right: Spacing.lg,
                    bottom: insets.bottom + Spacing.xl,
                    width: 56,
                    height: 56,
                    borderRadius: 28,
                    backgroundColor: '#2a9d8f',
                    justifyContent: 'center',
                    alignItems: 'center',
                    shadowColor: '#4ecdc4',
                    shadowOpacity: 0.4,
                    shadowRadius: 12,
                    shadowOffset: { width: 0, height: 4 },
                    elevation: 6,
                }}
            >
                <Text style={{ color: '#fff', fontSize: 28, lineHeight: 32 }}>＋</Text>
            </TouchableOpacity>
        </View>
    )
}
