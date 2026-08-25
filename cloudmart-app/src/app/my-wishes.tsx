import { View, Text, FlatList, TouchableOpacity, ActivityIndicator, RefreshControl } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors, FRUIT_LABELS, FRUIT_COLORS, WISH_STATUS_LABELS, formatCount } from '@/constants/wish-theme'
import WishBGM from '@/components/WishBGM'
import type { MyWishListItem } from '@/types'

const PAGE_SIZE = 10

export default function MyWishesScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [wishes, setWishes] = useState<MyWishListItem[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [hasMore, setHasMore] = useState(true)
  const [cursor, setCursor] = useState<string | null>(null)

  useEffect(() => {
    if (!isLoggedIn) {
      router.replace('/login')
    }
  }, [isLoggedIn])

  const loadWishes = useCallback(async (nextCursor?: string | null, reset = false) => {
    if (reset) {
      setLoading(true)
    } else {
      setLoadingMore(true)
    }
    try {
      const res = await wishApi.listMyWishes({ cursor: nextCursor ?? undefined, pageSize: PAGE_SIZE })
      if (res.data?.success) {
        const list = res.data.data
        const meta = res.data.meta as { nextCursor?: string | null; hasMore?: boolean } | undefined
        setWishes(reset ? list : [...wishes, ...list])
        setHasMore(meta?.hasMore ?? list.length >= PAGE_SIZE)
        setCursor(meta?.nextCursor ?? null)
      }
    } catch {
      if (reset) setWishes([])
    } finally {
      setLoading(false)
      setLoadingMore(false)
    }
  }, [wishes])

  useEffect(() => {
    if (isLoggedIn) loadWishes(null, true)
  }, [isLoggedIn])

  const onRefresh = useCallback(async () => {
    setRefreshing(true)
    await loadWishes(null, true)
    setRefreshing(false)
  }, [loadWishes])

  const onEndReached = useCallback(() => {
    if (hasMore && !loadingMore && cursor) {
      loadWishes(cursor)
    }
  }, [hasMore, loadingMore, cursor, loadWishes])

  const renderItem = ({ item }: { item: MyWishListItem }) => (
    <TouchableOpacity
      activeOpacity={0.8}
      onPress={() => router.push(`/wish-detail?id=${item.id}`)}
      style={{
        marginHorizontal: Spacing.md,
        marginBottom: Spacing.md,
        padding: Spacing.lg,
        borderRadius: BorderRadius.lg,
        backgroundColor: WishColors.bgContainer,
        borderWidth: 1,
        borderColor: WishColors.border,
      }}
    >
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <View
          style={{
            width: 40,
            height: 40,
            borderRadius: 20,
            backgroundColor: 'rgba(255,255,255,0.06)',
            justifyContent: 'center',
            alignItems: 'center',
          }}
        >
          <Text style={{ fontSize: 18 }}>
            {item.fruitType === 'SPARK' ? '⭐' : item.fruitType === 'BLOOM' ? '🌸' : item.fruitType === 'RESONANCE' ? '💫' : '🌱'}
          </Text>
        </View>
        <View style={{ flex: 1, marginLeft: Spacing.md }}>
          <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text }} numberOfLines={1}>
            {item.title}
          </Text>
          <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: 4, gap: Spacing.sm }}>
            <Text style={{ fontSize: FontSize.xs, color: FRUIT_COLORS[item.fruitType] }}>
              {FRUIT_LABELS[item.fruitType]}
            </Text>
            <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>
              {WISH_STATUS_LABELS[item.status] || item.status}
            </Text>
            <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>✨ {formatCount(item.lightCount)}</Text>
          </View>
        </View>
        <Text style={{ fontSize: FontSize.sm, color: WishColors.accentCyan, fontWeight: '700' }}>{item.progress}%</Text>
      </View>
      <View style={{ height: 6, borderRadius: 3, backgroundColor: 'rgba(255,255,255,0.08)', marginTop: Spacing.md, overflow: 'hidden' }}>
        <View
          style={{
            width: `${Math.min(Math.max(item.progress, 0), 100)}%`,
            height: '100%',
            borderRadius: 3,
            backgroundColor: FRUIT_COLORS[item.fruitType] || WishColors.accentCyan,
          }}
        />
      </View>
    </TouchableOpacity>
  )

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: Spacing.md,
          borderBottomWidth: 1,
          borderBottomColor: WishColors.border,
        }}
      >
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.lg, color: WishColors.textSecondary }}>‹ 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>我的果实</Text>
        <TouchableOpacity onPress={() => router.push('/wish-create')}>
          <Text style={{ fontSize: FontSize.md, color: WishColors.primary }}>＋ 许愿</Text>
        </TouchableOpacity>
      </View>

      {loading ? (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <ActivityIndicator size="large" color={WishColors.primary} />
        </View>
      ) : (
        <FlatList
          data={wishes}
          keyExtractor={(item) => String(item.id)}
          renderItem={renderItem}
          contentContainerStyle={{ paddingTop: Spacing.md, paddingBottom: insets.bottom + 24 }}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={WishColors.primary} />}
          onEndReached={onEndReached}
          onEndReachedThreshold={0.3}
          ListEmptyComponent={
            <View style={{ alignItems: 'center', padding: Spacing.xl * 2 }}>
              <Text style={{ fontSize: 48, opacity: 0.3 }}>🌱</Text>
              <Text style={{ fontSize: FontSize.md, color: WishColors.textTertiary, marginTop: Spacing.md }}>
                还没有心愿种子
              </Text>
              <TouchableOpacity
                onPress={() => router.push('/wish-create')}
                style={{
                  marginTop: Spacing.lg,
                  paddingHorizontal: Spacing.xl,
                  paddingVertical: Spacing.sm,
                  borderRadius: 24,
                  backgroundColor: WishColors.primary,
                }}
              >
                <Text style={{ color: '#fff', fontSize: FontSize.md, fontWeight: '600' }}>去许愿</Text>
              </TouchableOpacity>
            </View>
          }
          ListFooterComponent={
            loadingMore ? <ActivityIndicator size="small" color={WishColors.primary} style={{ margin: Spacing.md }} /> : null
          }
        />
      )}

      <WishBGM />
    </View>
  )
}
