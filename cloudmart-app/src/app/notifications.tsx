import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  Image,
  ActivityIndicator,
  RefreshControl,
  ScrollView,
} from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { notificationApi } from '@/api/notification'
import { wishApi } from '@/api/wish'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import type { ExpectedActionType, Notification } from '@/types'

const PAGE_SIZE = 20

const TAB_LIST = [
  { label: '全部', type: 0 },
  { label: '点赞', type: 1 },
  { label: '评论', type: 2 },
  { label: '关注', type: 3 },
  { label: '系统', type: 4 },
] as const

function formatRelativeTime(dateStr: string): string {
  const now = Date.now()
  const target = new Date(dateStr).getTime()
  const diffMs = now - target

  if (diffMs < 0) return '刚刚'

  const diffSeconds = Math.floor(diffMs / 1000)
  if (diffSeconds < 60) return '刚刚'

  const diffMinutes = Math.floor(diffSeconds / 60)
  if (diffMinutes < 60) return `${diffMinutes}分钟前`

  const diffHours = Math.floor(diffMinutes / 60)
  if (diffHours < 24) return `${diffHours}小时前`

  const date = new Date(dateStr)
  const month = date.getMonth() + 1
  const day = date.getDate()
  return `${month}月${day}日`
}

function NotificationItem({
  item,
  theme,
  onPress,
  onExpectedAction,
}: {
  item: Notification
  theme: ReturnType<typeof useTheme>
  onPress: (item: Notification) => void
  onExpectedAction: (item: Notification, action: ExpectedActionType) => void
}) {
  const TYPE_ICON_MAP: Record<number, string> = {
    1: '👍',
    2: '💬',
    3: '👤',
    4: '🔔',
  }

  return (
    <TouchableOpacity
      activeOpacity={0.7}
      onPress={() => onPress(item)}
      style={{
        flexDirection: 'row',
        alignItems: 'flex-start',
        padding: Spacing.lg,
        backgroundColor: item.isRead ? theme.bgContainer : theme.bgElevated,
        borderBottomWidth: 1,
        borderBottomColor: theme.border,
      }}
    >
      {/* Avatar or type icon */}
      {item.sender?.avatar ? (
        <Image
          source={{ uri: item.sender.avatar }}
          style={{
            width: 44,
            height: 44,
            borderRadius: BorderRadius.full,
            resizeMode: 'cover',
          }}
        />
      ) : (
        <View
          style={{
            width: 44,
            height: 44,
            borderRadius: BorderRadius.full,
            backgroundColor: theme.bgInput,
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Text style={{ fontSize: 20 }}>{TYPE_ICON_MAP[Number(item.type)] ?? '🔔'}</Text>
        </View>
      )}

      {/* Content */}
      <View style={{ flex: 1, marginLeft: Spacing.md }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <Text
            numberOfLines={1}
            style={{
              flex: 1,
              fontSize: FontSize.md,
              fontWeight: item.isRead ? '400' : '600',
              color: theme.text,
              marginRight: Spacing.sm,
            }}
          >
            {item.title}
          </Text>

          <View style={{ flexDirection: 'row', alignItems: 'center' }}>
            {!item.isRead && (
              <View
                style={{
                  width: 8,
                  height: 8,
                  borderRadius: 4,
                  backgroundColor: theme.primary,
                  marginRight: Spacing.sm,
                }}
              />
            )}
            <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>
              {formatRelativeTime(item.createdAt)}
            </Text>
          </View>
        </View>

        <Text
          numberOfLines={2}
          style={{
            fontSize: FontSize.sm,
            color: theme.textSecondary,
            lineHeight: 20,
            marginTop: Spacing.xs,
          }}
        >
          {item.content}
        </Text>
        {item.type === 'CHECKIN_REMINDER' && item.bizType === 'EXPECTED_MANAGEMENT' && item.bizId ? (
          <View style={{ flexDirection: 'row', gap: Spacing.sm, marginTop: Spacing.sm }}>
            {(
              [
                { action: 'EXTEND', label: '延长预期' },
                { action: 'ADJUST', label: '调整目标' },
                { action: 'TO_CAPSULE', label: '转入胶囊' },
              ] as Array<{ action: ExpectedActionType; label: string }>
            ).map(({ action, label }) => (
              <TouchableOpacity
                key={action}
                accessibilityLabel={`${label}（心愿 ${item.bizId}）`}
                onPress={() => onExpectedAction(item, action)}
                style={{
                  paddingHorizontal: Spacing.md,
                  paddingVertical: 6,
                  borderRadius: BorderRadius.full,
                  borderWidth: 1,
                  borderColor: theme.primary,
                }}
              >
                <Text style={{ fontSize: FontSize.xs, color: theme.primary }}>{label}</Text>
              </TouchableOpacity>
            ))}
          </View>
        ) : null}
      </View>
    </TouchableOpacity>
  )
}

export default function NotificationsScreen() {
  const theme = useTheme()

  const [activeTab, setActiveTab] = useState(0)
  const [notifications, setNotifications] = useState<Notification[]>([])
  const [loading, setLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)

  const loadNotifications = useCallback(
    async (pageNum: number, reset = false) => {
      if (loading) return
      setLoading(true)
      try {
        const params: { type?: number; page: number; pageSize: number } = {
          page: pageNum,
          pageSize: PAGE_SIZE,
        }
        if (activeTab !== 0) {
          params.type = activeTab
        }
        const res = await notificationApi.getList(params)
        const newItems: Notification[] = (res.data as any)?.data?.list || []
        setNotifications(reset ? newItems : [...notifications, ...newItems])
        setHasMore(newItems.length >= PAGE_SIZE)
        setPage(pageNum)
      } catch {
        if (reset) setNotifications([])
      } finally {
        setLoading(false)
        setRefreshing(false)
      }
    },
    [loading, notifications, activeTab],
  )

  useEffect(() => {
    loadNotifications(1, true)
  }, [activeTab])

  const onRefresh = useCallback(() => {
    setRefreshing(true)
    loadNotifications(1, true)
  }, [loadNotifications])

  const handleLoadMore = useCallback(() => {
    if (hasMore && !loading) {
      loadNotifications(page + 1)
    }
  }, [hasMore, loading, page, loadNotifications])

  const handleTabChange = useCallback((type: number) => {
    setActiveTab(type)
    setNotifications([])
    setPage(1)
    setHasMore(true)
  }, [])

  const handleNotificationPress = useCallback(
    async (item: Notification) => {
      if (!item.isRead) {
        try {
          await notificationApi.markRead(item.id)
          setNotifications((prev) =>
            prev.map((n) => (n.id === item.id ? { ...n, isRead: true } : n)),
          )
        } catch {
          // 标记已读失败仍允许跳转
        }
      }

      switch (item.type) {
        case 1: // 点赞 → 帖子详情
        case 2: // 评论 → 帖子详情
          router.push('/post-detail')
          break
        case 3: // 关注 → 用户主页
          if (item.sender) {
            router.push(`/user-profile?id=${item.sender.id}`)
          }
          break
        case 4: // 系统 → 不跳转
          break
      }
    },
    [],
  )

  const handleMarkAllRead = useCallback(async () => {
    try {
      await notificationApi.markAllRead()
      setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })))
    } catch {
      // 静默失败
    }
  }, [])

  /** 预期管理通知 3 选项（延长预期/调整目标/转入时间胶囊，Sprint 2.5） */
  const handleExpectedAction = useCallback(async (item: Notification, action: ExpectedActionType) => {
    const wishId = item.bizId
    if (!wishId) return
    // 埋点失败不阻断跳转（转化率数据允许少量丢失）
    try {
      await wishApi.recordExpectedAction(wishId, action)
    } catch {
      // ignore
    }
    if (action === 'EXTEND') {
      router.push({ pathname: '/wish-detail', params: { id: String(wishId), extend: '1' } })
    } else if (action === 'ADJUST') {
      router.push({ pathname: '/ai-assistant', params: { wishId: String(wishId) } })
    } else {
      router.push({ pathname: '/capsule-create', params: { wishId: String(wishId) } })
    }
  }, [])

  const renderNotificationItem = ({ item }: { item: Notification }) => (
    <NotificationItem
      item={item}
      theme={theme}
      onPress={handleNotificationPress}
      onExpectedAction={handleExpectedAction}
    />
  )

  const renderEmpty = () => (
    <View style={{ alignItems: 'center', paddingVertical: Spacing.xxxl * 3 }}>
      <Text style={{ fontSize: 48, marginBottom: Spacing.lg, opacity: 0.3 }}>🔔</Text>
      <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>暂无通知</Text>
    </View>
  )

  const renderFooter = () => {
    if (loading && !refreshing) {
      return <ActivityIndicator color={theme.primary} style={{ marginVertical: Spacing.xl }} />
    }
    if (!hasMore && notifications.length > 0) {
      return (
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', paddingVertical: Spacing.xl }}>
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
          justifyContent: 'space-between',
          paddingHorizontal: Spacing.lg,
          paddingVertical: Spacing.md,
          backgroundColor: theme.bgHeader,
          borderBottomWidth: 1,
          borderBottomColor: theme.border,
        }}
      >
        <TouchableOpacity activeOpacity={0.7} onPress={() => router.back()} hitSlop={{ top: 12, bottom: 12, left: 12, right: 12 }}>
          <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>← 返回</Text>
        </TouchableOpacity>

        <Text style={{ fontSize: FontSize.xl, fontWeight: '600', color: theme.text }}>通知</Text>

        <TouchableOpacity activeOpacity={0.7} onPress={handleMarkAllRead}>
          <Text style={{ fontSize: FontSize.sm, color: theme.primary }}>全部已读</Text>
        </TouchableOpacity>
      </View>

      {/* Tab bar */}
      <View style={{ backgroundColor: theme.bgBase }}>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={{ paddingHorizontal: Spacing.lg, paddingVertical: Spacing.md }}
        >
          {TAB_LIST.map((tab) => {
            const isActive = activeTab === tab.type
            return (
              <TouchableOpacity
                key={tab.type}
                activeOpacity={0.7}
                onPress={() => handleTabChange(tab.type)}
                style={{
                  paddingHorizontal: Spacing.lg,
                  paddingVertical: Spacing.sm,
                  borderRadius: BorderRadius.xl,
                  backgroundColor: isActive ? theme.primary : theme.bgInput,
                  marginRight: Spacing.sm,
                }}
              >
                <Text
                  style={{
                    fontSize: FontSize.md,
                    fontWeight: isActive ? '600' : '400',
                    color: isActive ? '#FFFFFF' : theme.textSecondary,
                  }}
                >
                  {tab.label}
                </Text>
              </TouchableOpacity>
            )
          })}
        </ScrollView>
      </View>

      {/* Notification list */}
      <FlatList
        data={notifications}
        keyExtractor={(item) => String(item.id)}
        contentContainerStyle={{ paddingBottom: Spacing.xxl }}
        renderItem={renderNotificationItem}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={theme.primary} />}
        onEndReached={handleLoadMore}
        onEndReachedThreshold={0.3}
        ListEmptyComponent={!loading ? renderEmpty : null}
        ListFooterComponent={renderFooter}
      />
    </View>
  )
}
