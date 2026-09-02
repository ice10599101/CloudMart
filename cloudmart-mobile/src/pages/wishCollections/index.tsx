import { useState, useEffect, useCallback } from 'react'
import { View, Text, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import type { WishCollectionItem } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import styles from './index.module.scss'

const PAGE_SIZE = 20

const FRUIT_LABELS: Record<string, string> = {
  GLOW: '微光',
  RESONANCE: '共鸣',
  BLOOM: '绽放',
  SPARK: '星火',
}

/**
 * 我的收藏（Sprint 1.5 验收项，四AB B2 移动端）：
 * 收藏列表（游标分页 + 取消收藏 → 跳详情）。
 */
export default function WishCollectionsPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [items, setItems] = useState<WishCollectionItem[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [removingId, setRemovingId] = useState<string | number | null>(null)

  const fetchList = useCallback(async (reset: boolean) => {
    if (!isLoggedIn) {
      setLoading(false)
      return
    }
    const cursorParam = reset ? undefined : (cursor ?? undefined)
    const res = await wishApi.listWishCollections(cursorParam, PAGE_SIZE)
    if (res.data.success) {
      const page = res.data.data ?? []
      setItems((prev) => (reset ? page : [...prev, ...page]))
      setCursor(res.data.meta?.nextCursor ?? null)
      setHasMore(Boolean(res.data.meta?.nextCursor))
    }
    setLoading(false)
  }, [isLoggedIn, cursor])

  useEffect(() => {
    fetchList(true)
  }, [fetchList])

  const handleLoadMore = async () => {
    if (!hasMore || loadingMore) return
    setLoadingMore(true)
    try {
      await fetchList(false)
    } finally {
      setLoadingMore(false)
    }
  }

  const handleUncollect = async (item: WishCollectionItem) => {
    setRemovingId(item.wishId)
    try {
      const res = await wishApi.uncollectWish(item.wishId)
      if (res.data.success) {
        setItems((prev) => prev.filter((it) => it.wishId !== item.wishId))
        Taro.showToast({ title: '已取消收藏', icon: 'none' })
      }
    } catch (err) {
      const errNode = err as { data?: { error?: { message?: string } } }
      Taro.showToast({ title: errNode?.data?.error?.message || '操作失败，请稍后重试', icon: 'none' })
    } finally {
      setRemovingId(null)
    }
  }

  return (
    <View className={styles.page} style={{ paddingTop: statusBarHeight + navBarHeight }}>
      <CustomNavBar title="我的收藏" back />
      <ScrollView
        className={styles.list}
        scrollY
        onScrollToLower={handleLoadMore}
      >
        {!isLoggedIn ? (
          <View className={styles.empty}>
            <Text>请先登录后查看收藏</Text>
          </View>
        ) : !loading && items.length === 0 ? (
          <View className={styles.empty}>
            <Text>还没有收藏的心愿，去心愿广场逛逛吧</Text>
          </View>
        ) : (
          items.map((item) => (
            <View
              key={item.wishId}
              className={styles.card}
              onClick={() => Taro.navigateTo({ url: `/pages/wishDetail/index?id=${item.wishId}` })}
            >
              <View className={styles.cardHeader}>
                <Text className={styles.cardTitle}>{item.title}</Text>
                <Text className={styles.fruitTag}>{FRUIT_LABELS[item.fruitType] ?? item.fruitType}</Text>
              </View>
              <Text className={styles.cardMeta}>
                作者：{item.authorNickname || '匿名'} · 收藏于 {new Date(item.collectedAt).toLocaleDateString('zh-CN')}
              </Text>
              <View className={styles.cardActions}>
                <View
                  className={styles.removeBtn}
                  onClick={(e) => {
                    e.stopPropagation()
                    if (removingId === item.wishId) return
                    handleUncollect(item)
                  }}
                >
                  <Text>{removingId === item.wishId ? '移除中...' : '取消收藏'}</Text>
                </View>
              </View>
            </View>
          ))
        )}
        {loading && (
          <View className={styles.empty}><Text>加载中...</Text></View>
        )}
        {loadingMore && (
          <View className={styles.empty}><Text>加载更多...</Text></View>
        )}
      </ScrollView>
    </View>
  )
}
