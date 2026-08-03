import { useState, useEffect, useCallback } from 'react'
import { View, Text, Image, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { liveApi } from '@/api/live'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

interface LiveRoom {
  id: number
  title: string
  coverImage: string
  anchorAvatar: string
  anchorName: string
  viewerCount: number
  status: number
  startTime?: string
}

const TAB_LIST = [
  { key: 'all', label: '全部' },
  { key: 'living', label: '直播中' },
  { key: 'upcoming', label: '预告' },
]

type TabKey = typeof TAB_LIST[number]['key']

const STATUS_MAP: Record<number, { icon: string; text: string; className: string }> = {
  1: { icon: '🔴', text: '直播中', className: styles.badgeLiving },
  0: { icon: '📅', text: '即将开始', className: styles.badgeUpcoming },
  2: { icon: '', text: '已结束', className: styles.badgeEnded },
}

function formatViewerCount(count: number): string {
  if (count >= 10000) return `${(count / 10000).toFixed(1)}万`
  if (count >= 1000) return `${(count / 1000).toFixed(1)}k`
  return String(count)
}

export default function LivePage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const [activeTab, setActiveTab] = useState<TabKey>('all')
  const [rooms, setRooms] = useState<LiveRoom[]>([])
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)
  const PAGE_SIZE = 10

  const statusParam = activeTab === 'living' ? 1 : activeTab === 'upcoming' ? 0 : undefined

  const fetchRooms = useCallback(async (pageNum: number, isRefresh = false) => {
    if (loading) return
    setLoading(true)
    try {
      const params: { page: number; pageSize: number; status?: number } = {
        page: pageNum,
        pageSize: PAGE_SIZE,
      }
      if (statusParam !== undefined) params.status = statusParam
      const res = await liveApi.getRooms(params)
      const list: LiveRoom[] = res.data?.data?.list || res.data?.data || []
      setRooms(isRefresh ? list : [...rooms, ...list])
      setHasMore(list.length >= PAGE_SIZE)
      setPage(pageNum)
    } catch {
      if (isRefresh) setRooms([])
    } finally {
      setLoading(false)
    }
  }, [loading, rooms, statusParam])

  useEffect(() => {
    fetchRooms(1, true)
  }, [activeTab])

  const handleRefresh = async () => {
    await fetchRooms(1, true)
  }

  const handleLoadMore = () => {
    if (hasMore && !loading) {
      fetchRooms(page + 1)
    }
  }

  const handleRoomTap = (id: number) => {
    Taro.navigateTo({ url: `/pages/liveRoom/index?id=${id}` })
  }

  const renderRoomCard = (room: LiveRoom) => {
    const statusInfo = STATUS_MAP[room.status] || STATUS_MAP[2]
    return (
      <View key={room.id} className={styles.card} onClick={() => handleRoomTap(room.id)}>
        <View className={styles.cardCover}>
          <Image className={styles.coverImage} src={room.coverImage} mode='aspectFill' />
          <View className={styles.coverGradient} />
          <View className={`${styles.badge} ${statusInfo.className}`}>
            <Text className={styles.badgeText}>{statusInfo.icon} {statusInfo.text}</Text>
          </View>
          <View className={styles.viewerBadge}>
            <Text className={styles.viewerBadgeText}>👁 {formatViewerCount(room.viewerCount)}</Text>
          </View>
        </View>
        <View className={styles.cardInfo}>
          <Text className={styles.roomTitle}>{room.title}</Text>
          <View className={styles.anchorRow}>
            <Image className={styles.anchorAvatar} src={room.anchorAvatar} />
            <Text className={styles.anchorName}>{room.anchorName}</Text>
          </View>
        </View>
      </View>
    )
  }

  const renderEmpty = () => (
    <View className={styles.empty}>
      <Text className={styles.emptyIcon}>📺</Text>
      <Text className={styles.emptyText}>暂无直播</Text>
      <Text className={styles.emptySubText}>下拉刷新试试</Text>
    </View>
  )

  const renderLoading = () => (
    <View className={styles.loading}>
      <View className={styles.spinner} />
    </View>
  )

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <View className={styles.header}>
        <Text className={styles.title}>📺 直播</Text>
      </View>
      <View className={styles.tabs}>
        {TAB_LIST.map((tab) => (
          <View
            key={tab.key}
            className={`${styles.tab} ${activeTab === tab.key ? styles.tabActive : ''}`}
            onClick={() => setActiveTab(tab.key)}
          >
            <Text className={activeTab === tab.key ? styles.tabTextActive : styles.tabText}>
              {tab.label}
            </Text>
          </View>
        ))}
      </View>
      <ScrollView
        scrollY
        className={styles.content}
        refresherEnabled
        refresherTriggered={loading && page === 1}
        onRefresherRefresh={handleRefresh}
        onScrollToLower={handleLoadMore}
      >
        {rooms.length > 0 ? (
          <View className={styles.roomGrid}>
            {rooms.map(renderRoomCard)}
          </View>
        ) : !loading ? renderEmpty() : null}
        {loading && renderLoading()}
        {!hasMore && rooms.length > 0 && (
          <View className={styles.noMore}>
            <Text className={styles.noMoreText}>没有更多了</Text>
          </View>
        )}
      </ScrollView>
    </View>
  )
}
