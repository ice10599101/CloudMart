import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  Image,
  ActivityIndicator,
  RefreshControl,
  StyleSheet,
} from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { liveApi } from '@/api/live'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

export interface LiveRoom {
  id: number
  title: string
  coverImage: string
  anchorName: string
  anchorAvatar: string
  viewerCount: number
  status: number // 0=scheduled, 1=live, 2=ended
  startTime?: string
}

const PAGE_SIZE = 20

type TabKey = 'all' | 'live' | 'scheduled'

const TABS: { key: TabKey; label: string; status?: number }[] = [
  { key: 'all', label: '全部' },
  { key: 'live', label: '直播中', status: 1 },
  { key: 'scheduled', label: '预告', status: 0 },
]

function formatViewerCount(count: number): string {
  if (count >= 10000) {
    return `${(count / 10000).toFixed(1)}w`
  }
  if (count >= 1000) {
    return `${(count / 1000).toFixed(1)}k`
  }
  return String(count)
}

function LiveRoomCard({ room, theme }: { room: LiveRoom; theme: ReturnType<typeof useTheme> }) {
  const isLive = room.status === 1
  const isScheduled = room.status === 0

  return (
    <TouchableOpacity
      activeOpacity={0.7}
      onPress={() => router.push(`/live-room?id=${room.id}`)}
      style={{
        backgroundColor: theme.bgContainer,
        borderRadius: BorderRadius.lg,
        overflow: 'hidden',
        borderWidth: 1,
        borderColor: theme.border,
        marginBottom: Spacing.lg,
        ...theme.shadowCard,
      }}
    >
      {/* Cover image */}
      <View style={{ position: 'relative' }}>
        <Image
          source={{ uri: room.coverImage }}
          style={{ width: '100%', height: 200, resizeMode: 'cover' }}
        />
        {/* Gradient overlay at bottom */}
        <View
          style={{
            position: 'absolute',
            bottom: 0,
            left: 0,
            right: 0,
            height: 80,
            backgroundColor: 'rgba(0, 0, 0, 0.5)',
          }}
        />

        {/* Status badge */}
        <View
          style={{
            position: 'absolute',
            top: Spacing.md,
            left: Spacing.md,
            flexDirection: 'row',
            alignItems: 'center',
            paddingHorizontal: Spacing.md,
            paddingVertical: Spacing.xs,
            borderRadius: BorderRadius.xl,
            backgroundColor: isLive ? 'rgba(255, 71, 87, 0.9)' : isScheduled ? 'rgba(255, 215, 0, 0.9)' : 'rgba(0, 0, 0, 0.5)',
          }}
        >
          <Text style={{ fontSize: FontSize.xs, fontWeight: '700', color: '#FFFFFF' }}>
            {isLive ? '🔴 直播中' : isScheduled ? '📅 即将开始' : '已结束'}
          </Text>
        </View>

        {/* Viewer count on cover */}
        <View
          style={{
            position: 'absolute',
            top: Spacing.md,
            right: Spacing.md,
            flexDirection: 'row',
            alignItems: 'center',
            paddingHorizontal: Spacing.sm,
            paddingVertical: Spacing.xs,
            borderRadius: BorderRadius.xl,
            backgroundColor: 'rgba(0, 0, 0, 0.55)',
          }}
        >
          <Text style={{ fontSize: FontSize.xs, color: '#FFFFFF', fontWeight: '500' }}>
            👁 {formatViewerCount(room.viewerCount)}
          </Text>
        </View>

        {/* Title on gradient overlay */}
        <Text
          numberOfLines={2}
          style={{
            position: 'absolute',
            bottom: Spacing.md,
            left: Spacing.md,
            right: Spacing.md,
            fontSize: FontSize.lg,
            fontWeight: '700',
            color: '#FFFFFF',
            lineHeight: 22,
          }}
        >
          {room.title}
        </Text>
      </View>

      {/* Anchor info */}
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          padding: Spacing.md,
        }}
      >
        <Image
          source={{ uri: room.anchorAvatar }}
          style={{
            width: 32,
            height: 32,
            borderRadius: BorderRadius.full,
            backgroundColor: theme.bgInput,
          }}
          resizeMode="cover"
        />
        <Text
          numberOfLines={1}
          style={{
            flex: 1,
            fontSize: FontSize.md,
            color: theme.textSecondary,
            marginLeft: Spacing.sm,
          }}
        >
          {room.anchorName}
        </Text>
        {isScheduled && room.startTime && (
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>
            {formatStartTime(room.startTime)}
          </Text>
        )}
      </View>
    </TouchableOpacity>
  )
}

function formatStartTime(time: string): string {
  const date = new Date(time)
  const now = new Date()
  const isToday = date.toDateString() === now.toDateString()
  const pad = (n: number) => String(n).padStart(2, '0')
  const timeStr = `${pad(date.getHours())}:${pad(date.getMinutes())}`
  if (isToday) return `今天 ${timeStr}`
  const tomorrow = new Date(now)
  tomorrow.setDate(tomorrow.getDate() + 1)
  if (date.toDateString() === tomorrow.toDateString()) return `明天 ${timeStr}`
  return `${pad(date.getMonth() + 1)}/${pad(date.getDate())} ${timeStr}`
}

export default function LivePage() {
  const theme = useTheme()
  const [activeTab, setActiveTab] = useState<TabKey>('all')
  const [rooms, setRooms] = useState<LiveRoom[]>([])
  const [loading, setLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)

  const getStatusParam = useCallback((): number | undefined => {
    const tab = TABS.find((t) => t.key === activeTab)
    return tab?.status
  }, [activeTab])

  const loadRooms = useCallback(
    async (pageNum: number, reset = false) => {
      if (loading) return
      setLoading(true)
      try {
        const res = await liveApi.getRooms({
          page: pageNum,
          pageSize: PAGE_SIZE,
          status: getStatusParam(),
        })
        const newItems: LiveRoom[] = (res.data as any)?.data?.list ?? (res.data as any)?.data ?? []
        const list = Array.isArray(newItems) ? newItems : []
        setRooms(reset ? list : (prev) => [...prev, ...list])
        setHasMore(list.length >= PAGE_SIZE)
        setPage(pageNum)
      } catch {
        if (reset) setRooms([])
      } finally {
        setLoading(false)
        setRefreshing(false)
      }
    },
    [loading, getStatusParam],
  )

  useEffect(() => {
    loadRooms(1, true)
  }, [activeTab])

  const onRefresh = useCallback(() => {
    setRefreshing(true)
    loadRooms(1, true)
  }, [loadRooms])

  const handleLoadMore = useCallback(() => {
    if (hasMore && !loading) {
      loadRooms(page + 1)
    }
  }, [hasMore, loading, page, loadRooms])

  const renderItem = ({ item }: { item: LiveRoom }) => (
    <LiveRoomCard room={item} theme={theme} />
  )

  const renderEmpty = () => (
    <View style={{ alignItems: 'center', paddingVertical: Spacing.xxxl * 3 }}>
      <Text style={{ fontSize: 48, marginBottom: Spacing.lg, opacity: 0.3 }}>📺</Text>
      <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>暂无直播间</Text>
      <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: Spacing.xs }}>
        下拉刷新查看最新直播
      </Text>
    </View>
  )

  const renderFooter = () => {
    if (loading && !refreshing) {
      return <ActivityIndicator color={theme.primary} style={{ marginVertical: Spacing.xl }} />
    }
    if (!hasMore && rooms.length > 0) {
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
        <Text style={{ fontSize: FontSize.xl, fontWeight: '700', color: theme.text }}>📺 直播</Text>
      </View>

      {/* Tab filter */}
      <View
        style={{
          flexDirection: 'row',
          paddingHorizontal: Spacing.lg,
          paddingVertical: Spacing.md,
          gap: Spacing.sm,
        }}
      >
        {TABS.map((tab) => {
          const isActive = activeTab === tab.key
          return (
            <TouchableOpacity
              key={tab.key}
              activeOpacity={0.7}
              onPress={() => setActiveTab(tab.key)}
              style={{
                paddingHorizontal: Spacing.lg,
                paddingVertical: Spacing.sm,
                borderRadius: BorderRadius.xl,
                backgroundColor: isActive ? theme.primary : theme.bgContainer,
                borderWidth: 1,
                borderColor: isActive ? theme.primary : theme.border,
              }}
            >
              <Text
                style={{
                  fontSize: FontSize.md,
                  fontWeight: isActive ? '700' : '500',
                  color: isActive ? '#FFFFFF' : theme.textSecondary,
                }}
              >
                {tab.label}
              </Text>
            </TouchableOpacity>
          )
        })}
      </View>

      {/* Room list */}
      <FlatList
        data={rooms}
        keyExtractor={(item) => String(item.id)}
        contentContainerStyle={{ paddingHorizontal: Spacing.lg, paddingBottom: Spacing.xxl }}
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
