import { useState, useEffect, useCallback } from 'react'
import { View, Text, ScrollView } from '@tarojs/components'
import { wishApi } from '@/api/wish'
import type { ResourceLogItem } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import styles from './index.module.scss'

const PAGE_SIZE = 20

/**
 * 星光流水（Sprint 1.4 验收「星光流水列表 三端展示一致」，遗留 P1）：
 * EARN/SPEND 筛选 + 游标分页。
 */
export default function StarlightLogPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()
  const [logs, setLogs] = useState<ResourceLogItem[]>([])
  const [filter, setFilter] = useState<'' | 'EARN' | 'SPEND'>('')
  const [cursor, setCursor] = useState<string | number | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)

  const fetchLogs = useCallback(async (reset: boolean) => {
    if (!isLoggedIn) {
      setLoading(false)
      return
    }
    const cursorParam = reset ? undefined : (cursor ?? undefined)
    const res = await wishApi.getMyResourceLogs({
      type: filter || undefined,
      cursor: cursorParam,
      pageSize: PAGE_SIZE,
    })
    if (res.data.success) {
      const page = res.data.data ?? []
      setLogs((prev) => (reset ? page : [...prev, ...page]))
      setCursor(page.length > 0 ? String(page[page.length - 1].id) : null)
      setHasMore(page.length >= PAGE_SIZE)
    }
    setLoading(false)
  }, [isLoggedIn, filter, cursor])

  useEffect(() => {
    fetchLogs(true)
  }, [fetchLogs])

  const handleLoadMore = async () => {
    if (!hasMore || loadingMore) return
    setLoadingMore(true)
    try {
      await fetchLogs(false)
    } finally {
      setLoadingMore(false)
    }
  }

  return (
    <View className={styles.page} style={{ paddingTop: statusBarHeight + navBarHeight }}>
      <CustomNavBar title="星光流水" back />
      <ScrollView className={styles.list} scrollY onScrollToLower={handleLoadMore}>
        {!isLoggedIn ? (
          <View className={styles.empty}><Text>请先登录</Text></View>
        ) : loading ? (
          <View className={styles.empty}><Text>加载中...</Text></View>
        ) : logs.length === 0 ? (
          <View className={styles.empty}><Text>暂无星光流水</Text></View>
        ) : (
          logs.map((log) => {
            const positive = log.amount > 0
            return (
              <View key={String(log.id)} className={styles.logCard}>
                <View className={styles.logLeft}>
                  <Text className={styles.logSource}>{log.reason}</Text>
                  <Text className={styles.logTime}>
                    {new Date(log.createdAt).toLocaleString('zh-CN')} · 余额 {log.balanceAfter}
                  </Text>
                </View>
                <Text
                  className={styles.logDelta}
                  style={{ color: positive ? '#52c41a' : '#ff6b6b' }}
                >
                  {positive ? '+' : ''}{log.amount}
                </Text>
              </View>
            )
          })
        )}
        {loadingMore && <View className={styles.empty}><Text>加载更多...</Text></View>}
        {filter !== '' && (
          <View className={styles.filterRow}>
            <Text className={styles.filterBtn} onClick={() => { setFilter(''); fetchLogs(true) }}>清除筛选</Text>
          </View>
        )}
      </ScrollView>
      <View className={styles.filterBar}>
        <Text
          className={`${styles.filterItem} ${filter === '' ? styles.filterActive : ''}`}
          onClick={() => { setFilter(''); fetchLogs(true) }}
        >
          全部
        </Text>
        <Text
          className={`${styles.filterItem} ${filter === 'EARN' ? styles.filterActive : ''}`}
          onClick={() => { setFilter('EARN'); fetchLogs(true) }}
        >
          收入
        </Text>
        <Text
          className={`${styles.filterItem} ${filter === 'SPEND' ? styles.filterActive : ''}`}
          onClick={() => { setFilter('SPEND'); fetchLogs(true) }}
        >
          支出
        </Text>
      </View>
    </View>
  )
}
