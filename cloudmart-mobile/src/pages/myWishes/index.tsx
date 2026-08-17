import { useState, useEffect, useCallback } from 'react'
import { View, Text, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import { WISH_THEME_STYLE } from '@/styles/wish-theme'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import WishBGM from '@/components/WishBGM'
import type { MyWishListItem, WishStatus, FruitType } from '@/types'
import styles from './index.module.scss'

const PAGE_SIZE = 20

const STATUS_FILTERS: { label: string; value: '' | WishStatus }[] = [
  { label: '全部', value: '' },
  { label: '进行中', value: 'ACTIVE' },
  { label: '还愿中', value: 'FULFILLING' },
  { label: '已还愿', value: 'FULFILLED' },
]

const FRUIT_LABELS: Record<FruitType, string> = {
  GLOW: '微光',
  RESONANCE: '共鸣',
  BLOOM: '绽放',
  SPARK: '星火',
}

const FRUIT_COLORS: Record<FruitType, string> = {
  GLOW: '#00d4ff',
  RESONANCE: '#9370db',
  BLOOM: '#ff6b6b',
  SPARK: '#ffd700',
}

export default function MyWishesPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [wishes, setWishes] = useState<MyWishListItem[]>([])
  const [statusFilter, setStatusFilter] = useState<string>('')
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)

  const fetchWishes = useCallback(async (reset: boolean) => {
    if (!isLoggedIn) {
      setLoading(false)
      return
    }
    if (reset) {
      setLoading(true)
      setCursor(null)
    } else {
      setLoadingMore(true)
    }

    try {
      const res = await wishApi.listMyWishes({
        status: (statusFilter || undefined) as WishStatus | undefined,
        cursor: reset ? undefined : cursor ?? undefined,
        pageSize: PAGE_SIZE,
      })

      if (res.data.success) {
        const newItems = res.data.data
        setWishes(prev => reset ? newItems : [...prev, ...newItems])
        const meta = res.data.meta
        setCursor(meta?.nextCursor ?? null)
        setHasMore(meta?.hasMore ?? false)
      }
    } catch {
      // 错误已由 request 处理
    } finally {
      setLoading(false)
      setLoadingMore(false)
    }
  }, [isLoggedIn, statusFilter, cursor])

  useEffect(() => {
    if (!isLoggedIn) {
      Taro.redirectTo({ url: '/pages/login/index' })
      return
    }
    fetchWishes(true)
  }, [isLoggedIn, statusFilter])

  const onScrollToLower = () => {
    if (hasMore && !loading && !loadingMore) {
      fetchWishes(false)
    }
  }

  const handleDelete = async (id: number) => {
    const res = await Taro.showModal({
      title: '确认删除',
      content: '删除后不可恢复，确定删除吗？',
    })
    if (!res.confirm) return

    try {
      const result = await wishApi.deleteWish(id)
      if (result.data.success) {
        Taro.showToast({ title: '已删除', icon: 'success' })
        setWishes(prev => prev.filter(w => w.id !== id))
      }
    } catch {
      // 错误已由 request 处理
    }
  }

  if (loading) {
    return (
      <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}rpx`, minHeight: '100vh' }}>
        <CustomNavBar title='我的心愿' back />
        <View className={styles.loading}>
          <View className={styles.spinner} />
        </View>
      </View>
    )
  }

  return (
    <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}rpx`, minHeight: '100vh' }}>
      <CustomNavBar title='我的心愿' back />

      {/* 状态筛选 */}
      <ScrollView scrollX className={styles.filterBar}>
        {STATUS_FILTERS.map(filter => (
          <View
            key={filter.value}
            className={`${styles.filterChip} ${statusFilter === filter.value ? styles.filterChipActive : ''}`}
            onClick={() => setStatusFilter(filter.value)}
          >
            <Text>{filter.label}</Text>
          </View>
        ))}
      </ScrollView>

      {/* 新建按钮 */}
      <View className={styles.toolbar}>
        <View
          className={styles.createBtn}
          onClick={() => Taro.navigateTo({ url: '/pages/wishCreate/index' })}
        >
          <Text className={styles.createBtnText}>+ 新建心愿</Text>
        </View>
      </View>

      {wishes.length === 0 ? (
        <View className={styles.empty}>
          <Text className={styles.emptyIcon}>🌟</Text>
          <Text className={styles.emptyText}>还没有心愿，许下第一个心愿吧</Text>
          <View
            className={styles.emptyBtn}
            onClick={() => Taro.navigateTo({ url: '/pages/wishCreate/index' })}
          >
            <Text className={styles.emptyBtnText}>发布心愿</Text>
          </View>
        </View>
      ) : (
        <ScrollView
          scrollY
          className={styles.scroll}
          onScrollToLower={onScrollToLower}
          lowerThreshold={100}
        >
          {wishes.map(wish => (
            <View
              key={wish.id}
              className={styles.wishCard}
              onClick={() => Taro.navigateTo({ url: `/pages/wishDetail/index?id=${wish.id}` })}
            >
              <View className={styles.cardLeft}>
                <Text className={styles.fruitTag} style={{ background: FRUIT_COLORS[wish.fruitType] }}>
                  {FRUIT_LABELS[wish.fruitType]}
                </Text>
                <View className={styles.cardInfo}>
                  <Text className={styles.cardTitle}>{wish.title}</Text>
                  <Text className={styles.cardDate}>
                    {new Date(wish.createdAt).toLocaleDateString('zh-CN')}
                  </Text>
                </View>
              </View>
              <View className={styles.cardRight}>
                <View className={styles.progressWrap}>
                  <View className={styles.progressBg}>
                    <View
                      className={styles.progressBar}
                      style={{ width: `${wish.progress}%`, background: FRUIT_COLORS[wish.fruitType] }}
                    />
                  </View>
                  <Text className={styles.progressText}>{wish.progress}%</Text>
                </View>
                <View
                  className={styles.deleteBtn}
                  onClick={(e) => { e.stopPropagation(); handleDelete(wish.id) }}
                >
                  <Text className={styles.deleteText}>删除</Text>
                </View>
              </View>
            </View>
          ))}

          {loadingMore && (
            <View className={styles.loadingMore}>
              <View className={styles.spinnerSmall} />
              <Text className={styles.loadingText}>加载中...</Text>
            </View>
          )}

          {!hasMore && wishes.length > 0 && (
            <View className={styles.endText}>
              <Text>已经到底啦~</Text>
            </View>
          )}
          <View style={{ height: '120rpx' }} />
        </ScrollView>
      )}
      <WishBGM />
    </View>
  )
}
