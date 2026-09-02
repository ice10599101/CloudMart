import { View, Text, FlatList, TouchableOpacity, ActivityIndicator, RefreshControl } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import type { WishCollectionItem } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors, FRUIT_LABELS } from '@/constants/wish-theme'

const PAGE_SIZE = 20

/**
 * 我的收藏（Sprint 1.5 验收项，四AB B2 APP 端）：
 * 收藏列表（游标分页 + 取消收藏 → 跳详情）。
 */
export default function WishCollectionsScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [items, setItems] = useState<WishCollectionItem[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [hasMore, setHasMore] = useState(true)
  const [cursor, setCursor] = useState<string | null>(null)
  const [removingId, setRemovingId] = useState<string | number | null>(null)

  useEffect(() => {
    if (!isLoggedIn) {
      router.replace('/login')
    }
  }, [isLoggedIn])

  const loadList = useCallback(async (nextCursor?: string | null, reset = false) => {
    if (reset) setLoading(true)
    else setLoadingMore(true)
    try {
      const res = await wishApi.listWishCollections(nextCursor ?? undefined, PAGE_SIZE)
      if (res.data?.success) {
        const list = res.data.data ?? []
        const meta = res.data.meta as { nextCursor?: string | null } | undefined
        setItems((prev) => (reset ? list : [...prev, ...list]))
        setCursor(meta?.nextCursor ?? null)
        setHasMore(Boolean(meta?.nextCursor))
      }
    } catch {
      if (reset) setItems([])
    } finally {
      setLoading(false)
      setLoadingMore(false)
    }
  }, [])

  useEffect(() => {
    if (isLoggedIn) loadList(null, true)
  }, [isLoggedIn])

  const handleUncollect = async (item: WishCollectionItem) => {
    setRemovingId(item.wishId)
    try {
      const res = await wishApi.uncollectWish(item.wishId)
      if (res.data?.success) {
        setItems((prev) => prev.filter((it) => it.wishId !== item.wishId))
      }
    } catch (err) {
      const errNode = err as { response?: { data?: { error?: { message?: string } } } }
      alert(errNode?.response?.data?.error?.message || '操作失败，请稍后重试')
    } finally {
      setRemovingId(null)
    }
  }

  const renderItem = ({ item }: { item: WishCollectionItem }) => (
    <TouchableOpacity
      activeOpacity={0.85}
      onPress={() => router.push(`/wish-detail?id=${item.wishId}`)}
      style={{
        backgroundColor: WishColors.bgContainer,
        borderRadius: BorderRadius.lg,
        padding: Spacing.md,
        marginBottom: Spacing.sm,
      }}
    >
      <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 4 }}>
        <Text style={{ flex: 1, fontSize: FontSize.md, fontWeight: '600', color: WishColors.text }} numberOfLines={1}>
          {item.title}
        </Text>
        <Text style={{ fontSize: FontSize.xs, color: WishColors.accentCyan }}>
          {FRUIT_LABELS[item.fruitType] ?? item.fruitType}
        </Text>
      </View>
      <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginBottom: Spacing.sm }}>
        作者：{item.authorNickname || '匿名'} · 收藏于 {new Date(item.collectedAt).toLocaleDateString('zh-CN')}
      </Text>
      <TouchableOpacity
        activeOpacity={0.85}
        disabled={removingId === item.wishId}
        onPress={() => handleUncollect(item)}
        style={{
          alignSelf: 'flex-end',
          paddingHorizontal: Spacing.md,
          paddingVertical: 4,
          borderRadius: BorderRadius.md,
          borderWidth: 1,
          borderColor: 'rgba(255, 107, 107, 0.5)',
        }}
      >
        <Text style={{ fontSize: FontSize.xs, color: '#ff6b6b' }}>
          {removingId === item.wishId ? '移除中...' : '取消收藏'}
        </Text>
      </TouchableOpacity>
    </TouchableOpacity>
  )

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text, padding: Spacing.lg, paddingBottom: Spacing.sm }}>
        我的收藏
      </Text>
      <FlatList
        data={items}
        keyExtractor={(item) => String(item.wishId)}
        renderItem={renderItem}
        contentContainerStyle={{ padding: Spacing.md }}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={async () => {
              setRefreshing(true)
              await loadList(null, true)
              setRefreshing(false)
            }}
            tintColor={WishColors.accentCyan}
          />
        }
        onEndReachedThreshold={0.2}
        onEndReached={() => {
          if (hasMore && !loadingMore && !loading) loadList(cursor, false)
        }}
        ListEmptyComponent={
          loading ? (
            <ActivityIndicator color={WishColors.accentCyan} style={{ marginTop: 80 }} />
          ) : (
            <Text style={{ textAlign: 'center', marginTop: 80, color: WishColors.textTertiary, fontSize: FontSize.sm }}>
              还没有收藏的心愿，去心愿广场逛逛吧
            </Text>
          )
        }
        ListFooterComponent={loadingMore ? <ActivityIndicator color={WishColors.accentCyan} /> : null}
      />
    </View>
  )
}
