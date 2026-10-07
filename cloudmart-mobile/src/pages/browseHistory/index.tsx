import { useState, useEffect, useCallback } from 'react'
import { View, Text, Image, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { communityApi } from '@/api/community'
import { resolveFileUrl } from '@/api/file'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import type { BrowseHistoryItem } from '@/types'
import styles from './index.module.scss'

const PAGE_SIZE = 20

const TARGET_ICON: Record<string, string> = {
  PRODUCT: '🛍️',
  POST: '📝',
  WISH: '🌟',
}

function formatDay(time: string): string {
  const d = new Date(time)
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

/**
 * P2-28 我的足迹（社区浏览历史）：商品/帖子/心愿三类目标倒序时间线，
 * 点击跳对应详情；分页加载（服务端单用户上限 100 条裁剪）。
 */
export default function BrowseHistoryPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  useAuthGuard()
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const [items, setItems] = useState<BrowseHistoryItem[]>([])
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)

  const load = useCallback(async (pageNum: number, append: boolean) => {
    if (append) setLoadingMore(true)
    try {
      const res = await communityApi.getMyBrowseHistory({ page: pageNum, pageSize: PAGE_SIZE })
      const list = (res.data?.data as unknown as { list?: BrowseHistoryItem[] } | BrowseHistoryItem[] | undefined)
      const rows = Array.isArray(list) ? list : (list?.list ?? [])
      setItems((prev) => (append ? [...prev, ...rows] : rows))
      setPage(pageNum)
      setHasMore(rows.length >= PAGE_SIZE)
    } catch {
      setHasMore(false)
    } finally {
      setLoading(false)
      setLoadingMore(false)
    }
  }, [])

  useEffect(() => {
    void load(1, false)
  }, [load])

  const goTarget = (item: BrowseHistoryItem) => {
    if (item.targetType === 'PRODUCT') {
      Taro.navigateTo({ url: `/pages/productDetail/index?id=${item.targetId}` })
    } else if (item.targetType === 'POST') {
      Taro.navigateTo({ url: `/pages/postDetail/index?id=${item.targetId}` })
    } else if (item.targetType === 'WISH') {
      Taro.navigateTo({ url: `/pages/wishDetail/index?id=${item.targetId}` })
    }
  }

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <CustomNavBar title="我的足迹" back />
      <ScrollView
        scrollY
        className={styles.list}
        style={{ paddingTop: `${statusBarHeight + navBarHeight}px` }}
        onScrollToLower={() => hasMore && !loadingMore && load(page + 1, true)}
      >
        {loading ? (
          <Text className={styles.emptyText}>加载中...</Text>
        ) : items.length === 0 ? (
          <Text className={styles.emptyText}>还没有浏览记录，去逛逛吧</Text>
        ) : (
          items.map((item) => (
            <View key={item.id} className={styles.row} onClick={() => goTarget(item)}>
              {item.cover ? (
                <Image className={styles.cover} src={resolveFileUrl(item.cover)} mode='aspectFill' />
              ) : (
                <View className={styles.coverPlaceholder}>
                  <Text className={styles.coverIcon}>{TARGET_ICON[item.targetType] ?? '👁️'}</Text>
                </View>
              )}
              <View className={styles.info}>
                <Text className={styles.title}>{item.title || '（无标题）'}</Text>
                <Text className={styles.meta}>
                  {TARGET_ICON[item.targetType] ?? ''} {formatDay(item.viewedAt)}
                </Text>
              </View>
            </View>
          ))
        )}
        {loadingMore && <Text className={styles.emptyText}>加载更多...</Text>}
        {!hasMore && items.length > 0 && <Text className={styles.emptyText}>最多保留最近 100 条足迹</Text>}
      </ScrollView>
    </View>
  )
}
