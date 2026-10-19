import { View, Text, FlatList, TouchableOpacity, ActivityIndicator, RefreshControl } from 'react-native'
import { useCallback, useEffect, useState } from 'react'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi, type CapsuleWallItem } from '@/api/wish'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'

const PAGE_SIZE = 10

/**
 * R17 公共胶囊墙（Expo 用户端）。
 * 匿名公共墙：只展示作者主动发布且当前仍在墙的胶囊，不暴露作者身份与媒体附件。
 */
export default function CapsuleWallScreen() {
    const insets = useSafeAreaInsets()
    const [items, setItems] = useState<CapsuleWallItem[]>([])
    const [loading, setLoading] = useState(true)
    const [refreshing, setRefreshing] = useState(false)
    const [loadingMore, setLoadingMore] = useState(false)
    const [page, setPage] = useState(1)
    const [total, setTotal] = useState(0)

    const fetchPage = useCallback(async (nextPage: number, append: boolean) => {
        try {
            const res = await wishApi.getCapsuleWall(nextPage, PAGE_SIZE)
            const data = res.data.data
            setItems((prev) => (append ? [...prev, ...data.records] : data.records))
            setTotal(data.total ?? 0)
            setPage(nextPage)
        } catch {
            // 墙列表后台加载失败保持空态
        } finally {
            setLoading(false)
            setRefreshing(false)
            setLoadingMore(false)
        }
    }, [])

    useEffect(() => {
        async function run() {
            await fetchPage(1, false)
        }
        void run()
    }, [fetchPage])

    const onRefresh = () => {
        setRefreshing(true)
        fetchPage(1, false)
    }

    const onEndReached = () => {
        if (loadingMore || items.length >= total) return
        setLoadingMore(true)
        fetchPage(page + 1, true)
    }

    const formatDate = (iso: string | null) => (iso ? new Date(iso).toLocaleDateString('zh-CN') : '')

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
                    公共胶囊墙
                </Text>
            </View>

            <FlatList
                data={items}
                keyExtractor={(item) => String(item.capsuleId)}
                contentContainerStyle={{ padding: Spacing.lg, paddingBottom: insets.bottom + 40, flexGrow: 1 }}
                refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={WishColors.accentCyan} />}
                onEndReached={onEndReached}
                onEndReachedThreshold={0.4}
                ListHeaderComponent={
                    <Text style={{ fontSize: FontSize.xs, lineHeight: 20, color: WishColors.textTertiary, marginBottom: Spacing.md }}>
                        时间到的那一刻，有些话值得被更多人看见。这里每颗胶囊都是匿名展出的时光信笺。
                    </Text>
                }
                ListEmptyComponent={
                    loading ? (
                        <ActivityIndicator color={WishColors.accentCyan} style={{ marginTop: Spacing.xl * 2 }} />
                    ) : (
                        <View style={{ alignItems: 'center', marginTop: Spacing.xl * 2, gap: Spacing.md }}>
                            <Text style={{ fontSize: 44 }}>🌌</Text>
                            <Text style={{ fontSize: FontSize.sm, color: WishColors.textTertiary }}>墙上还没有胶囊</Text>
                            <TouchableOpacity
                                onPress={() => router.replace('/capsule/list')}
                                style={{
                                    paddingHorizontal: Spacing.xl,
                                    paddingVertical: Spacing.sm + 2,
                                    borderRadius: 24,
                                    borderWidth: 1,
                                    borderColor: '#a29bfe',
                                }}
                            >
                                <Text style={{ color: '#c9b8ff', fontSize: FontSize.sm }}>去看我的胶囊</Text>
                            </TouchableOpacity>
                        </View>
                    )
                }
                ListFooterComponent={
                    loadingMore ? (
                        <ActivityIndicator color={WishColors.accentCyan} style={{ marginVertical: Spacing.lg }} />
                    ) : items.length > 0 ? (
                        <Text style={{ textAlign: 'center', fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: Spacing.lg }}>
                            共 {total} 颗时光信笺
                        </Text>
                    ) : null
                }
                renderItem={({ item }) => (
                    <View
                        style={{
                            marginBottom: Spacing.md,
                            padding: Spacing.lg,
                            borderRadius: BorderRadius.lg,
                            backgroundColor: WishColors.bgContainer,
                            borderWidth: 1,
                            borderColor: WishColors.border,
                        }}
                    >
                        <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: WishColors.text, marginBottom: Spacing.xs }}>
                            {item.title}
                        </Text>
                        <Text style={{ fontSize: FontSize.sm, lineHeight: 22, color: WishColors.textSecondary }}>{item.content}</Text>
                        <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: Spacing.sm }}>
                            🕐 {formatDate(item.openedAt)} 开启
                        </Text>
                    </View>
                )}
            />
        </View>
    )
}
