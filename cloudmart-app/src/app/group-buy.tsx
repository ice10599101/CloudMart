import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  Image,
  ActivityIndicator,
  RefreshControl,
  Alert,
} from 'react-native'
import { useState, useEffect, useCallback, useRef } from 'react'
import { router } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { marketingApi } from '@/api/marketing'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import type { GroupActivity } from '@/types'

const PAGE_SIZE = 20

function formatCountdown(endTime: string): string {
  const diff = new Date(endTime).getTime() - Date.now()
  if (diff <= 0) return '已结束'
  const hours = Math.floor(diff / 3600000)
  const minutes = Math.floor((diff % 3600000) / 60000)
  const seconds = Math.floor((diff % 60000) / 1000)
  if (hours > 24) {
    const days = Math.floor(hours / 24)
    return `剩余${days}天`
  }
  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

function GroupActivityCard({
  activity,
  theme,
  onJoin,
}: {
  activity: GroupActivity
  theme: ReturnType<typeof useTheme>
  onJoin: (activity: GroupActivity) => void
}) {
  const progress = Math.min(activity.currentCount / activity.groupSize, 1)
  const isEnded = activity.status !== 0 || new Date(activity.endTime).getTime() <= Date.now()

  return (
    <View
      style={{
        flexDirection: 'row',
        backgroundColor: theme.bgContainer,
        borderRadius: BorderRadius.lg,
        overflow: 'hidden',
        borderWidth: 1,
        borderColor: theme.border,
        marginBottom: Spacing.md,
        ...theme.shadowCard,
      }}
    >
      {/* Product Image */}
      {activity.productImage ? (
        <Image source={{ uri: activity.productImage }} style={{ width: 140, height: '100%', resizeMode: 'cover' }} />
      ) : (
        <View
          style={{
            width: 140,
            height: 180,
            backgroundColor: theme.bgInput,
            justifyContent: 'center',
            alignItems: 'center',
          }}
        >
          <Text style={{ fontSize: 32, opacity: 0.3 }}>👥</Text>
        </View>
      )}

      {/* Info */}
      <View style={{ flex: 1, padding: Spacing.md, justifyContent: 'space-between' }}>
        {/* Product name */}
        <Text
          numberOfLines={2}
          style={{ fontSize: FontSize.md, fontWeight: '600', color: theme.text, lineHeight: 20 }}
        >
          {activity.productName}
        </Text>

        {/* Prices */}
        <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: Spacing.sm, marginTop: Spacing.sm }}>
          <Text style={{ fontSize: FontSize.xxl, fontWeight: '800', color: theme.accentRed }}>
            ¥{activity.groupPrice}
          </Text>
          <Text
            style={{
              fontSize: FontSize.sm,
              color: theme.textTertiary,
              textDecorationLine: 'line-through',
            }}
          >
            ¥{activity.originalPrice}
          </Text>
        </View>

        {/* Group size info */}
        <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, marginTop: Spacing.xs }}>
          {activity.groupSize}人团 · 已有{activity.currentCount}人参与
        </Text>

        {/* Progress bar */}
        <View
          style={{
            height: 6,
            backgroundColor: theme.border,
            borderRadius: 3,
            overflow: 'hidden',
            marginTop: Spacing.xs,
          }}
        >
          <View
            style={{
              height: '100%',
              width: `${progress * 100}%`,
              backgroundColor: theme.accentRed,
              borderRadius: 3,
            }}
          />
        </View>

        {/* Bottom row: countdown + button */}
        <View
          style={{
            flexDirection: 'row',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginTop: Spacing.sm,
          }}
        >
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>
            {isEnded ? '已结束' : formatCountdown(activity.endTime)}
          </Text>
          <TouchableOpacity
            activeOpacity={0.7}
            disabled={isEnded}
            onPress={() => onJoin(activity)}
            style={{
              paddingHorizontal: Spacing.lg,
              paddingVertical: Spacing.xs,
              borderRadius: BorderRadius.xl,
              backgroundColor: isEnded ? theme.bgInput : theme.accentRed,
            }}
          >
            <Text
              style={{
                fontSize: FontSize.sm,
                fontWeight: '600',
                color: isEnded ? theme.textTertiary : '#FFFFFF',
              }}
            >
              参与拼团
            </Text>
          </TouchableOpacity>
        </View>
      </View>
    </View>
  )
}

export default function GroupBuyPage() {
  const theme = useTheme()
  const [activities, setActivities] = useState<GroupActivity[]>([])
  const [loading, setLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const [, setTick] = useState(0)

  const loadActivities = useCallback(
    async (pageNum: number, reset = false) => {
      if (loading) return
      setLoading(true)
      try {
        const res = await marketingApi.getGroupActivities({ page: pageNum, pageSize: PAGE_SIZE })
        const newItems: GroupActivity[] = (res.data as any)?.data?.list || []
        setActivities(reset ? newItems : (prev) => [...prev, ...newItems])
        setHasMore(newItems.length >= PAGE_SIZE)
        setPage(pageNum)
      } catch {
        if (reset) setActivities([])
      } finally {
        setLoading(false)
        setRefreshing(false)
      }
    },
    [loading],
  )

  useEffect(() => {
    loadActivities(1, true)
  }, [])

  // Countdown timer - tick every second to refresh countdown display
  useEffect(() => {
    countdownRef.current = setInterval(() => setTick((t) => t + 1), 1000)
    return () => {
      if (countdownRef.current) clearInterval(countdownRef.current)
    }
  }, [])

  const onRefresh = useCallback(() => {
    setRefreshing(true)
    loadActivities(1, true)
  }, [loadActivities])

  const handleLoadMore = useCallback(() => {
    if (hasMore && !loading) {
      loadActivities(page + 1)
    }
  }, [hasMore, loading, page, loadActivities])

  const handleJoin = useCallback((activity: GroupActivity) => {
    Alert.alert('参与拼团', `确定要参与「${activity.productName}」的${activity.groupSize}人拼团吗？`, [
      { text: '取消', style: 'cancel' },
      {
        text: '确定',
        onPress: () => router.push(`/product/${activity.productId}`),
      },
    ])
  }, [])

  const renderItem = ({ item }: { item: GroupActivity }) => (
    <GroupActivityCard activity={item} theme={theme} onJoin={handleJoin} />
  )

  const renderEmpty = () => (
    <View style={{ alignItems: 'center', paddingVertical: Spacing.xxxl * 3 }}>
      <Text style={{ fontSize: 48, marginBottom: Spacing.lg, opacity: 0.3 }}>👥</Text>
      <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>暂无拼团活动</Text>
      <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: Spacing.xs }}>
        精彩拼团活动即将上线
      </Text>
    </View>
  )

  const renderFooter = () => {
    if (loading && !refreshing) {
      return <ActivityIndicator color={theme.primary} style={{ marginVertical: Spacing.xl }} />
    }
    if (!hasMore && activities.length > 0) {
      return (
        <View
          style={{
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'center',
            paddingVertical: Spacing.xl,
          }}
        >
          <View style={{ flex: 1, height: 1, backgroundColor: theme.border, marginHorizontal: Spacing.lg }} />
          <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>到底啦</Text>
          <View style={{ flex: 1, height: 1, backgroundColor: theme.border, marginHorizontal: Spacing.lg }} />
        </View>
      )
    }
    return null
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      {/* Header */}
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'center',
          paddingHorizontal: Spacing.lg,
          paddingVertical: Spacing.lg,
          backgroundColor: theme.bgHeader,
          borderBottomWidth: 1,
          borderBottomColor: theme.border,
        }}
      >
        <Text style={{ fontSize: FontSize.xl, fontWeight: '700', color: theme.text }}>👥 拼团专区</Text>
      </View>

      <FlatList
        data={activities}
        keyExtractor={(item) => String(item.id)}
        contentContainerStyle={{ padding: Spacing.lg, paddingBottom: Spacing.xxl }}
        renderItem={renderItem}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={theme.primary} />}
        onEndReached={handleLoadMore}
        onEndReachedThreshold={0.3}
        ListEmptyComponent={!loading ? renderEmpty : null}
        ListFooterComponent={renderFooter}
      />
    </View>
  )
}
