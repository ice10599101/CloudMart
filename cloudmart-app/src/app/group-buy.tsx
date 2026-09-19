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
import type { GroupActivity, GroupOrder } from '@/types'

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
  // 我的拼团（对齐 Web 端「拼团活动 / 我的拼团」双 Tab）
  const [mainTab, setMainTab] = useState<'activities' | 'mine'>('activities')
  const [myGroups, setMyGroups] = useState<GroupOrder[]>([])
  const [myLoading, setMyLoading] = useState(false)
  const [joiningId, setJoiningId] = useState<number | null>(null)
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

  /** 我的拼团列表（getGroupOrders，对齐 Web 端） */
  const loadMyGroups = useCallback(async () => {
    setMyLoading(true)
    try {
      const res = await marketingApi.getGroupOrders({ page: 1, pageSize: 50 })
      setMyGroups((res.data as { data?: { list?: GroupOrder[] } })?.data?.list ?? [])
    } catch {
      setMyGroups([])
    } finally {
      setMyLoading(false)
    }
  }, [])

  /** 参与拼团（真实调 joinGroup；满员/重复/冷却分文案） */
  const handleJoin = useCallback((activity: GroupActivity) => {
    Alert.alert('参与拼团', `确定要参与「${activity.productName}」的${activity.groupSize}人拼团吗？`, [
      { text: '取消', style: 'cancel' },
      {
        text: '确定',
        onPress: async () => {
          if (joiningId === activity.id) return
          setJoiningId(activity.id)
          try {
            const res = await marketingApi.joinGroup({ activityId: activity.id })
            const groupOrder = (res.data as { data?: { id?: number } })?.data
            Alert.alert('参与成功', '拼团订单已创建，成团后自动发货', [
              { text: '查看我的拼团', onPress: () => { setMainTab('mine'); void loadMyGroups() } },
              { text: '留在当前页' },
            ])
          } catch (err) {
            const message =
              (err as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message
            Alert.alert('参与失败', message || '请稍后重试')
          } finally {
            setJoiningId(null)
          }
        },
      },
    ])
  }, [joiningId, loadMyGroups])

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

      {/* 主 Tab（对齐 Web 端：拼团活动 / 我的拼团） */}
      <View style={{ flexDirection: 'row', backgroundColor: theme.bgHeader, borderBottomWidth: 1, borderBottomColor: theme.border }}>
        {([
          { key: 'activities', label: '拼团活动' },
          { key: 'mine', label: '我的拼团' },
        ] as const).map((tab) => (
          <TouchableOpacity
            key={tab.key}
            activeOpacity={0.7}
            onPress={() => {
              setMainTab(tab.key)
              if (tab.key === 'mine') void loadMyGroups()
            }}
            style={{ flex: 1, alignItems: 'center', paddingVertical: Spacing.md, borderBottomWidth: 2, borderBottomColor: mainTab === tab.key ? theme.primary : 'transparent' }}
          >
            <Text style={{ fontSize: FontSize.md, fontWeight: mainTab === tab.key ? '600' : '400', color: mainTab === tab.key ? theme.primary : theme.textSecondary }}>
              {tab.label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {mainTab === 'mine' ? (
        <FlatList
          data={myGroups}
          keyExtractor={(item) => String(item.id)}
          contentContainerStyle={{ padding: Spacing.lg, paddingBottom: Spacing.xxl }}
          refreshControl={<RefreshControl refreshing={myLoading} onRefresh={loadMyGroups} tintColor={theme.primary} />}
          renderItem={({ item }) => (
            <View style={{ backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.lg, marginBottom: Spacing.md }}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: Spacing.sm }}>
                <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: theme.text }}>我的拼团 #{item.id}</Text>
                <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: theme.primary }}>
                  {item.status === 'PENDING' ? '拼团中' : item.status === 'SUCCESS' ? '拼团成功' : item.status === 'FAILED' ? '拼团失败' : item.status}
                </Text>
              </View>
              <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary }}>
                {item.leaderUserId ? `团长用户 #${item.leaderUserId} · ` : ''}成团进度 {item.currentNumber}/{item.targetNumber} 人
              </Text>
              <View style={{ height: 6, borderRadius: 3, backgroundColor: theme.border, marginTop: Spacing.sm, overflow: 'hidden' }}>
                <View style={{ height: 6, borderRadius: 3, backgroundColor: theme.primary, width: `${item.targetNumber > 0 ? Math.min((item.currentNumber / item.targetNumber) * 100, 100) : 0}%` }} />
              </View>
              <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: Spacing.sm }}>
                成团截止 {new Date(item.expireTime).toLocaleString()}
              </Text>
            </View>
          )}
          ListEmptyComponent={!myLoading ? (
            <View style={{ alignItems: 'center', paddingVertical: Spacing.xxxl * 3 }}>
              <Text style={{ fontSize: 48, marginBottom: Spacing.lg, opacity: 0.3 }}>👥</Text>
              <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>还没有参与拼团</Text>
              <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: Spacing.xs }}>去拼团活动页开一团吧</Text>
            </View>
          ) : null}
        />
      ) : (
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
      )}
    </View>
  )
}
