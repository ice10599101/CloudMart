import { useState, useEffect, useCallback } from 'react'
import { View, Text, Image, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { giftApi } from '@/api/gift'
import { wishApi } from '@/api/wish'
import type { GiftRecordItem, GiftTargetType, MyGiftSummary } from '@/types'
import { useAuthStore } from '@/store/auth'
import { useAuthGuard } from '@/composables/useAuthGuard'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import styles from './index.module.scss'

const PAGE_SIZE = 20

/**
 * 我的礼物（对齐 Web /gift/my）：资产总览（送/收累计件数与星光）+ 当前星光余额
 * +「我送出的 / 我收到的」cursor 分页记录，记录可跳回送礼场景（心愿/帖子/直播间）。
 * 礼物为即时消费资产，无库存语义；分页契约 = id 倒序 cursor（上一页末条 id）。
 */

type RecordTab = 'sent' | 'received'

const TARGET_META: Record<GiftTargetType, { label: string; path: (id: number) => string }> = {
  WISH: { label: '心愿', path: (id) => `/pages/wishDetail/index?id=${id}` },
  POST: { label: '帖子', path: (id) => `/pages/postDetail/index?id=${id}` },
  LIVE_ROOM: { label: '直播间', path: (id) => `/pages/liveRoom/index?roomId=${id}` },
}

function formatDateTime(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleString('zh-CN', { hour12: false })
}

export default function MyGiftsPage() {
  useAuthGuard()
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()
  const [summary, setSummary] = useState<MyGiftSummary | null>(null)
  const [balance, setBalance] = useState<number | null>(null)
  const [tab, setTab] = useState<RecordTab>('sent')
  const [records, setRecords] = useState<GiftRecordItem[]>([])
  const [cursor, setCursor] = useState<number | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)

  const load = useCallback(
    async (tabKey: RecordTab, nextCursor: number | null, reset: boolean) => {
      if (!isLoggedIn) {
        setLoading(false)
        return
      }
      if (reset) setLoading(true)
      else setLoadingMore(true)
      try {
        const fetcher =
          tabKey === 'sent' ? giftApi.listSentGiftRecords : giftApi.listReceivedGiftRecords
        const res = await fetcher({
          cursor: reset ? undefined : (nextCursor ?? undefined),
          pageSize: PAGE_SIZE,
        })
        if (res.data.success) {
          const list = Array.isArray(res.data.data) ? res.data.data : []
          setRecords((prev) => (reset ? list : [...prev, ...list]))
          setCursor(list.length > 0 ? list[list.length - 1].id : null)
          setHasMore(list.length >= PAGE_SIZE)
        }
      } finally {
        setLoading(false)
        setLoadingMore(false)
      }
    },
    [isLoggedIn],
  )

  useEffect(() => {
    if (!isLoggedIn) {
      setLoading(false)
      return
    }
    giftApi
      .getMyGiftSummary()
      .then((res) => {
        if (res.data.success && res.data.data) setSummary(res.data.data)
      })
      .catch(() => setSummary(null))
    wishApi
      .getMyResources()
      .then((res) => {
        if (res.data.success && res.data.data) setBalance(res.data.data.balance ?? null)
      })
      .catch(() => setBalance(null))
  }, [isLoggedIn])

  useEffect(() => {
    load(tab, null, true)
  }, [tab, load])

  const handleLoadMore = async () => {
    if (!hasMore || loadingMore) return
    await load(tab, cursor, false)
  }

  const goTarget = (record: GiftRecordItem) => {
    const meta = TARGET_META[record.targetType]
    Taro.navigateTo({ url: meta.path(record.targetId) }).catch(() => {
      Taro.showToast({ title: '该场景暂不可达', icon: 'none' })
    })
  }

  const goStarlightLog = () => {
    Taro.navigateTo({ url: '/pages/starlightLog/index' })
  }

  const renderRecord = (record: GiftRecordItem) => {
    const meta = TARGET_META[record.targetType]
    return (
      <View key={record.id} className={styles.recordCard}>
        {record.giftIconUrl ? (
          <Image className={styles.recordIcon} src={record.giftIconUrl} mode='aspectFill' />
        ) : (
          <View className={styles.recordIconFallback}>
            <Text>🎁</Text>
          </View>
        )}
        <View className={styles.recordMain}>
          <View className={styles.recordTitleRow}>
            <Text className={styles.recordName}>
              {record.giftName} ×{record.count}
            </Text>
            <Text className={styles.recordPrice}>✦ {record.totalPrice}</Text>
          </View>
          <View className={styles.recordMetaRow}>
            {tab === 'sent' ? (
              <Text className={styles.recordMeta}>
                送给我 · {meta.label}
                <Text className={styles.recordLink} onClick={() => goTarget(record)}>
                  查看
                </Text>
              </Text>
            ) : (
              <Text className={styles.recordMeta}>
                来自 {record.senderNickname ?? `用户#${record.senderId}`} · {meta.label}
              </Text>
            )}
            <Text className={styles.recordTime}>{formatDateTime(record.createdAt)}</Text>
          </View>
          {record.message && <Text className={styles.recordMessage}>“{record.message}”</Text>}
        </View>
      </View>
    )
  }

  return (
    <View className={styles.page} style={{ paddingTop: statusBarHeight + navBarHeight }}>
      <CustomNavBar title='我的礼物' back />

      <ScrollView className={styles.list} scrollY onScrollToLower={handleLoadMore}>
        {!isLoggedIn ? (
          <View className={styles.empty}>
            <Text>请先登录</Text>
          </View>
        ) : (
          <>
            <View className={styles.summaryCard}>
              <View className={styles.summaryTitle}>
                <Text>礼物资产</Text>
              </View>
              <View className={styles.statGrid}>
                <View className={styles.statItem}>
                  <Text className={styles.statValue}>{summary?.sentCount ?? 0}</Text>
                  <Text className={styles.statLabel}>累计送出（件）</Text>
                </View>
                <View className={styles.statItem}>
                  <Text className={styles.statValue}>
                    ✦ {summary?.sentStarlight ?? 0}
                  </Text>
                  <Text className={styles.statLabel}>送出消耗星光</Text>
                </View>
                <View className={styles.statItem}>
                  <Text className={styles.statValue}>{summary?.receivedCount ?? 0}</Text>
                  <Text className={styles.statLabel}>累计收到（件）</Text>
                </View>
                <View className={styles.statItem}>
                  <Text className={styles.statValue}>
                    ✦ {summary?.receivedStarlight ?? 0}
                  </Text>
                  <Text className={styles.statLabel}>收到星光价值</Text>
                </View>
              </View>
              <View className={styles.balanceRow}>
                <Text className={styles.balanceValue}>当前星光余额 ✦ {balance ?? 0}</Text>
                <Text className={styles.balanceLink} onClick={goStarlightLog}>
                  查看星光流水 ›
                </Text>
              </View>
            </View>

            <View className={styles.tabBar}>
              <Text
                className={`${styles.tabItem} ${tab === 'sent' ? styles.tabActive : ''}`}
                onClick={() => setTab('sent')}
              >
                我送出的
              </Text>
              <Text
                className={`${styles.tabItem} ${tab === 'received' ? styles.tabActive : ''}`}
                onClick={() => setTab('received')}
              >
                我收到的
              </Text>
            </View>

            {loading ? (
              <View className={styles.empty}>
                <Text>加载中...</Text>
              </View>
            ) : records.length === 0 ? (
              <View className={styles.empty}>
                <Text>{tab === 'sent' ? '还没有送出礼物，去心愿广场逛逛吧' : '还没有收到礼物'}</Text>
              </View>
            ) : (
              records.map(renderRecord)
            )}
            {loadingMore && (
              <View className={styles.empty}>
                <Text>加载更多...</Text>
              </View>
            )}
          </>
        )}
      </ScrollView>
    </View>
  )
}
