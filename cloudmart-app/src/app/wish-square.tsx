import { View, Text, FlatList, TouchableOpacity, Image, ActivityIndicator, RefreshControl } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'
import type { WishListItem, WishCategory } from '@/types'

const PAGE_SIZE = 12

export default function WishSquareScreen() {
  const insets = useSafeAreaInsets()
  const [wishes, setWishes] = useState<WishListItem[]>([])
  const [categories, setCategories] = useState<WishCategory[]>([])
  const [activeCategory, setActiveCategory] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [hasMore, setHasMore] = useState(true)
  const [cursor, setCursor] = useState<string | null>(null)

  useEffect(() => {
    const fetchCategories = async () => {
      try {
        const res = await wishApi.getCategories()
        if (res.data?.success) {
          setCategories(res.data.data)
        }
      } catch {
        // 错误已由 request 拦截器处理
      }
    }
    fetchCategories()
  }, [])

  const loadWishes = useCallback(
    async (categoryId: number | null, nextCursor?: string | null, reset = false) => {
      if (reset) {
        setLoading(true)
      } else {
        setLoadingMore(true)
      }
      try {
        const res = await wishApi.listWishes({
          categoryId: categoryId ?? undefined,
          cursor: nextCursor ?? undefined,
          pageSize: PAGE_SIZE,
        })
        if (res.data?.success) {
          const list = res.data.data as unknown as WishListItem[]
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
    },
    [wishes]
  )

  useEffect(() => {
    loadWishes(activeCategory, null, true)
  }, [activeCategory])

  const onRefresh = useCallback(async () => {
    setRefreshing(true)
    await loadWishes(activeCategory, null, true)
    setRefreshing(false)
  }, [activeCategory, loadWishes])

  const onEndReached = useCallback(() => {
    if (hasMore && !loadingMore && cursor) {
      loadWishes(activeCategory, cursor)
    }
  }, [hasMore, loadingMore, cursor, activeCategory, loadWishes])

  const renderItem = ({ item }: { item: WishListItem }) => (
    <TouchableOpacity
      activeOpacity={0.8}
      onPress={() => router.push(`/wish-detail?id=${item.id}`)}
      style={{
        flex: 1,
        margin: Spacing.xs,
        borderRadius: BorderRadius.lg,
        backgroundColor: WishColors.bgContainer,
        borderWidth: 1,
        borderColor: WishColors.border,
        overflow: 'hidden',
      }}
    >
      {item.mediaUrls && item.mediaUrls.length > 0 ? (
        <Image source={{ uri: item.mediaUrls[0] }} style={{ width: '100%', height: 130, resizeMode: 'cover' }} />
      ) : (
        <View
          style={{
            width: '100%',
            height: 130,
            backgroundColor: WishColors.bgElevated,
            justifyContent: 'center',
            alignItems: 'center',
          }}
        >
          <Text style={{ fontSize: 30, opacity: 0.4 }}>
            {item.fruitType === 'SPARK' ? '⭐' : item.fruitType === 'BLOOM' ? '🌸' : item.fruitType === 'RESONANCE' ? '💫' : '🌱'}
          </Text>
        </View>
      )}
      <View style={{ padding: Spacing.sm }}>
        <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: WishColors.text }} numberOfLines={2}>
          {item.title}
        </Text>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 6 }}>
          <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }} numberOfLines={1}>
            {item.authorNickname}
          </Text>
          <Text style={{ fontSize: FontSize.xs, color: WishColors.accentCyan }}>✨ {item.supportCount}</Text>
        </View>
      </View>
    </TouchableOpacity>
  )

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
          心愿广场
        </Text>
        <TouchableOpacity onPress={() => router.push('/wish-create')} style={{ marginLeft: 'auto' }}>
          <Text style={{ fontSize: FontSize.md, color: WishColors.primary }}>＋ 许愿</Text>
        </TouchableOpacity>
      </View>

      {/* 分类筛选 */}
      <FlatList
        horizontal
        data={[{ id: 0, name: '全部' }, ...categories]}
        keyExtractor={(item) => String(item.id)}
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={{ paddingVertical: Spacing.sm, paddingHorizontal: Spacing.md }}
        renderItem={({ item }) => {
          const isActive = item.id === 0 ? activeCategory === null : activeCategory === item.id
          return (
            <TouchableOpacity
              onPress={() => setActiveCategory(item.id === 0 ? null : item.id)}
              style={{
                paddingHorizontal: Spacing.md,
                paddingVertical: 6,
                marginRight: Spacing.sm,
                borderRadius: 20,
                borderWidth: 1,
                borderColor: isActive ? WishColors.primary : WishColors.border,
                backgroundColor: isActive ? 'rgba(233,69,96,0.15)' : 'transparent',
              }}
            >
              <Text style={{ fontSize: FontSize.sm, color: isActive ? WishColors.primary : WishColors.textSecondary }}>
                {item.name}
              </Text>
            </TouchableOpacity>
          )
        }}
      />

      {loading ? (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <ActivityIndicator size="large" color={WishColors.primary} />
        </View>
      ) : (
        <FlatList
          data={wishes}
          keyExtractor={(item) => String(item.id)}
          renderItem={renderItem}
          numColumns={2}
          columnWrapperStyle={undefined}
          contentContainerStyle={{ paddingHorizontal: Spacing.sm, paddingBottom: insets.bottom + 24 }}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={WishColors.primary} />}
          onEndReached={onEndReached}
          onEndReachedThreshold={0.3}
          ListEmptyComponent={
            <View style={{ alignItems: 'center', padding: Spacing.xl * 2 }}>
              <Text style={{ fontSize: 48, opacity: 0.3 }}>🌌</Text>
              <Text style={{ fontSize: FontSize.md, color: WishColors.textTertiary, marginTop: Spacing.md }}>
                这里还很安静，来种下第一颗心愿吧
              </Text>
            </View>
          }
          ListFooterComponent={
            loadingMore ? <ActivityIndicator size="small" color={WishColors.primary} style={{ margin: Spacing.md }} /> : null
          }
        />
      )}
    </View>
  )
}
