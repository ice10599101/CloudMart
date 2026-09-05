import { useState, useEffect, useCallback } from 'react'
import { View, Text, FlatList, TouchableOpacity, ActivityIndicator, RefreshControl } from 'react-native'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import type { ResourceLogItem } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'

const PAGE_SIZE = 20

/**
 * 星光流水（Sprint 1.4 验收「三端展示一致」，遗留 P1 APP 端）：
 * EARN/SPEND 筛选 + 游标分页。
 */
export default function StarlightLogScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [logs, setLogs] = useState<ResourceLogItem[]>([])
  const [filter, setFilter] = useState<'' | 'EARN' | 'SPEND'>('')
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [hasMore, setHasMore] = useState(true)
  const [cursor, setCursor] = useState<string | number | null>(null)

  useEffect(() => {
    if (!isLoggedIn) router.replace('/login')
  }, [isLoggedIn])

  const loadLogs = useCallback(async (nextCursor?: string | number | null, reset = false) => {
    if (reset) setLoading(true)
    else setLoadingMore(true)
    try {
      const res = await wishApi.getMyResourceLogs({
        type: filter || undefined,
        cursor: nextCursor ?? undefined,
        pageSize: PAGE_SIZE,
      })
      if (res.data?.success) {
        const list = res.data.data ?? []
        setLogs((prev) => (reset ? list : [...prev, ...list]))
        setCursor(list.length > 0 ? String(list[list.length - 1].id) : null)
        setHasMore(list.length >= PAGE_SIZE)
      }
    } catch {
      if (reset) setLogs([])
    } finally {
      setLoading(false)
      setLoadingMore(false)
    }
  }, [filter])

  useEffect(() => {
    if (isLoggedIn) loadLogs(null, true)
  }, [isLoggedIn])

  const renderItem = ({ item }: { item: ResourceLogItem }) => {
    const positive = item.amount > 0
    return (
      <View
        style={{
          flexDirection: 'row',
          justifyContent: 'space-between',
          alignItems: 'center',
          backgroundColor: WishColors.bgContainer,
          borderRadius: BorderRadius.lg,
          padding: Spacing.md,
          marginBottom: Spacing.sm,
        }}
      >
        <View style={{ flex: 1 }}>
          <Text style={{ fontSize: FontSize.sm, fontWeight: '500', color: WishColors.text }}>{item.reason}</Text>
          <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 2 }}>
            {new Date(item.createdAt).toLocaleString('zh-CN')} · 余额 {item.balanceAfter}
          </Text>
        </View>
        <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: positive ? '#52c41a' : '#ff6b6b' }}>
          {positive ? '+' : ''}{item.amount}
        </Text>
      </View>
    )
  }

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: Spacing.lg, paddingBottom: Spacing.sm }}>
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.md, color: WishColors.accentCyan }}>← 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>星光流水</Text>
        <View style={{ width: 48 }} />
      </View>

      <View style={{ flexDirection: 'row', gap: Spacing.sm, paddingHorizontal: Spacing.lg, paddingBottom: Spacing.sm }}>
        {(['', 'EARN', 'SPEND'] as const).map((f) => (
          <TouchableOpacity
            key={f || 'all'}
            activeOpacity={0.85}
            onPress={() => setFilter(f)}
            style={{
              paddingHorizontal: Spacing.md,
              paddingVertical: 6,
              borderRadius: BorderRadius.md,
              backgroundColor: filter === f ? 'rgba(0, 212, 255, 0.12)' : 'transparent',
              borderWidth: 1,
              borderColor: filter === f ? WishColors.accentCyan : WishColors.border,
            }}
          >
            <Text style={{ fontSize: FontSize.xs, color: filter === f ? WishColors.accentCyan : WishColors.textSecondary }}>
              {f === '' ? '全部' : f === 'EARN' ? '收入' : '支出'}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      <FlatList
        data={logs}
        keyExtractor={(item) => String(item.id)}
        renderItem={renderItem}
        contentContainerStyle={{ padding: Spacing.lg }}
        refreshControl={
          <RefreshControl
            refreshing={loading && !loadingMore}
            onRefresh={() => loadLogs(null, true)}
            tintColor={WishColors.accentCyan}
          />
        }
        onEndReachedThreshold={0.2}
        onEndReached={() => {
          if (hasMore && !loadingMore && !loading) loadLogs(cursor, false)
        }}
        ListEmptyComponent={
          loading ? (
            <ActivityIndicator color={WishColors.accentCyan} style={{ marginTop: 60 }} />
          ) : (
            <Text style={{ textAlign: 'center', marginTop: 60, color: WishColors.textTertiary, fontSize: FontSize.sm }}>
              暂无星光流水
            </Text>
          )
        }
        ListFooterComponent={loadingMore ? <ActivityIndicator color={WishColors.accentCyan} /> : null}
      />
    </View>
  )
}
