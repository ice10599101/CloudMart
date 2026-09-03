import { View, Text, FlatList, TouchableOpacity, ActivityIndicator, RefreshControl, ScrollView, Alert } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors, FRUIT_LABELS, FRUIT_COLORS, formatCount } from '@/constants/wish-theme'
import WishBGM from '@/components/WishBGM'
import type { MyWishListItem, WishStatus } from '@/types'

const PAGE_SIZE = 10

/** 状态筛选（对齐 WEB 端 MyWishes / Mobile 端 myWishes） */
const STATUS_FILTERS: { label: string; value: '' | WishStatus }[] = [
  { label: '全部', value: '' },
  { label: '进行中', value: 'ACTIVE' },
  { label: '还愿中', value: 'FULFILLING' },
  { label: '已还愿', value: 'FULFILLED' },
]

export default function MyWishesScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [wishes, setWishes] = useState<MyWishListItem[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [hasMore, setHasMore] = useState(true)
  const [cursor, setCursor] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState<'' | WishStatus>('')

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
      const res = await wishApi.listMyWishes({
        status: statusFilter || undefined,
        cursor: nextCursor ?? undefined,
        pageSize: PAGE_SIZE,
      })
      if (res.data?.success) {
        const list = res.data.data
        const meta = res.data.meta as { nextCursor?: string | null; hasMore?: boolean } | undefined
        setWishes(prev => (reset ? list : [...prev, ...list]))
        setHasMore(meta?.hasMore ?? list.length >= PAGE_SIZE)
        setCursor(meta?.nextCursor ?? null)
      }
    } catch {
      if (reset) setWishes([])
    } finally {
      setLoading(false)
      setLoadingMore(false)
    }
  }, [statusFilter])

  useEffect(() => {
    if (isLoggedIn) loadWishes(null, true)
  }, [isLoggedIn, loadWishes])

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

  /** 删除心愿（对齐 WEB/Mobile：二次确认） */
  const handleDelete = (id: number | string) => {
    Alert.alert('确认删除', '删除后不可恢复，确定删除吗？', [
      { text: '取消', style: 'cancel' },
      {
        text: '删除',
        style: 'destructive',
        onPress: async () => {
          try {
            const res = await wishApi.deleteWish(id)
            if (res.data?.success) {
              setWishes(prev => prev.filter(w => w.id !== id))
            }
          } catch {
            // 错误已由 request 处理
          }
        },
      },
    ])
  }

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
            <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>✨ {formatCount(item.lightCount)}</Text>
          </View>
        </View>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.md }}>
          <Text style={{ fontSize: FontSize.sm, color: WishColors.accentCyan, fontWeight: '700' }}>{item.progress}%</Text>
          <TouchableOpacity
            activeOpacity={0.6}
            accessibilityLabel={`删除心愿 ${item.title}`}
            accessibilityRole="button"
            onPress={() => handleDelete(item.id)}
            style={{
              paddingHorizontal: Spacing.sm,
              paddingVertical: 4,
              borderRadius: 12,
              backgroundColor: 'rgba(255,77,79,0.15)',
            }}
          >
            <Text style={{ fontSize: FontSize.xs, color: '#ff4d4f' }}>删除</Text>
          </TouchableOpacity>
        </View>
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
        <TouchableOpacity onPress={() => router.back()} accessibilityLabel="返回" accessibilityRole="button">
          <Text style={{ fontSize: FontSize.lg, color: WishColors.textSecondary }}>‹ 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>我的心愿</Text>
        <TouchableOpacity onPress={() => router.push('/wish-create')} accessibilityLabel="新建心愿" accessibilityRole="button">
          <Text style={{ fontSize: FontSize.md, color: WishColors.primary }}>＋ 新建心愿</Text>
        </TouchableOpacity>
      </View>

      {/* 快捷入口（对齐 WEB/Mobile：我的收藏/虚拟工坊/我的徽章） */}
      <View style={{ flexDirection: 'row', gap: Spacing.sm, paddingHorizontal: Spacing.md, paddingTop: Spacing.sm }}>
        {([
          { icon: '📖', label: '我的收藏', path: '/wish-collections' },
          { icon: '🎁', label: '虚拟工坊', path: '/workshop' },
          { icon: '🏆', label: '我的徽章', path: '/badge-wall' },
        ] as const).map(entry => (
          <TouchableOpacity
            key={entry.path}
            activeOpacity={0.7}
            accessibilityLabel={entry.label}
            accessibilityRole="button"
            onPress={() => router.push(entry.path)}
            style={{
              flex: 1,
              alignItems: 'center',
              paddingVertical: Spacing.sm,
              borderRadius: BorderRadius.md,
              backgroundColor: WishColors.bgContainer,
              borderWidth: 1,
              borderColor: WishColors.border,
            }}
          >
            <Text style={{ fontSize: 18 }}>{entry.icon}</Text>
            <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary, marginTop: 2 }}>{entry.label}</Text>
          </TouchableOpacity>
        ))}
      </View>

      {/* 状态筛选（对齐 WEB/Mobile：全部/进行中/还愿中/已还愿） */}
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={{ flexGrow: 0 }}
        contentContainerStyle={{ paddingHorizontal: Spacing.md, paddingVertical: Spacing.sm, gap: Spacing.sm }}
      >
        {STATUS_FILTERS.map(filter => {
          const active = statusFilter === filter.value
          return (
            <TouchableOpacity
              key={filter.value}
              activeOpacity={0.7}
              accessibilityLabel={`筛选${filter.label}`}
              accessibilityRole="button"
              onPress={() => setStatusFilter(filter.value)}
              style={{
                paddingHorizontal: Spacing.lg,
                paddingVertical: 6,
                borderRadius: 20,
                backgroundColor: active ? WishColors.primary : WishColors.bgContainer,
                borderWidth: 1,
                borderColor: active ? WishColors.primary : WishColors.border,
              }}
            >
              <Text style={{ fontSize: FontSize.xs, color: active ? '#fff' : WishColors.textSecondary }}>{filter.label}</Text>
            </TouchableOpacity>
          )
        })}
      </ScrollView>

      {loading ? (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <ActivityIndicator size="large" color={WishColors.primary} />
        </View>
      ) : (
        <FlatList
          data={wishes}
          keyExtractor={(item) => String(item.id)}
          renderItem={renderItem}
          contentContainerStyle={{ paddingTop: Spacing.sm, paddingBottom: insets.bottom + 24 }}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={WishColors.primary} />}
          onEndReached={onEndReached}
          onEndReachedThreshold={0.3}
          ListEmptyComponent={
            <View style={{ alignItems: 'center', padding: Spacing.xl * 2 }}>
              <Text style={{ fontSize: 48, opacity: 0.3 }}>🌱</Text>
              <Text style={{ fontSize: FontSize.md, color: WishColors.textTertiary, marginTop: Spacing.md }}>
                还没有心愿，点击上方「新建心愿」许下第一个吧
              </Text>
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
