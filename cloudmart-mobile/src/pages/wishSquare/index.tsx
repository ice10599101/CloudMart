import { useState, useEffect, useCallback } from 'react'
import { View, Text, ScrollView, Image, Input } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import { WISH_THEME_STYLE } from '@/styles/wish-theme'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import WishBGM from '@/components/WishBGM'
import WaterfallFlow from '@/components/WaterfallFlow'
import type { WishListItem, WishCategory, FruitType } from '@/types'
import styles from './index.module.scss'

const PAGE_SIZE = 20

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: '进行中',
  FULFILLING: '还愿中',
  FULFILLED: '已还愿',
}

const FRUIT_COLORS: Record<FruitType, string> = {
  GLOW: '#00d4ff',
  RESONANCE: '#9370db',
  BLOOM: '#ff6b6b',
  SPARK: '#ffd700',
}

function formatCount(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

export default function WishSquarePage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [wishes, setWishes] = useState<WishListItem[]>([])
  const [categories, setCategories] = useState<WishCategory[]>([])
  const [categoryId, setCategoryId] = useState<number | undefined>()
  const [keyword, setKeyword] = useState('')
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)

  const fetchWishes = useCallback(async (reset: boolean) => {
    if (reset) {
      setLoading(true)
      setCursor(null)
    } else {
      setLoadingMore(true)
    }

    try {
      const res = await wishApi.listWishes({
        categoryId,
        keyword: keyword || undefined,
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
  }, [categoryId, keyword, cursor])

  useEffect(() => {
    fetchWishes(true)
  }, [categoryId, keyword])

  useEffect(() => {
    const fetchCategories = async () => {
      try {
        const res = await wishApi.getCategories()
        if (res.data.success) {
          setCategories(res.data.data)
        }
      } catch {
        // ignore
      }
    }
    fetchCategories()
  }, [])

  const onScrollToLower = () => {
    if (hasMore && !loading && !loadingMore) {
      fetchWishes(false)
    }
  }

  const handleSearch = (value: string) => {
    setKeyword(value)
  }

  const handleCategoryTap = (id: number | undefined) => {
    setCategoryId(id)
  }

  const renderWishCard = (wish: WishListItem) => (
    <View
      key={wish.id}
      className={styles.wishCard}
      onClick={() => Taro.navigateTo({ url: `/pages/wishDetail/index?id=${wish.id}` })}
    >
      {wish.mediaUrls && wish.mediaUrls.length > 0 ? (
        <Image className={styles.cardCover} src={wish.mediaUrls[0]} mode='aspectFill' />
      ) : (
        <View className={styles.cardCoverPlaceholder}>
          <Text style={{ color: FRUIT_COLORS[wish.fruitType], fontSize: '40rpx' }}>★</Text>
        </View>
      )}
      <View className={styles.cardBody}>
        <Text className={styles.cardTitle}>{wish.title}</Text>
        <Text className={styles.cardDesc}>{wish.description}</Text>
        {wish.tags && wish.tags.length > 0 && (
          <View className={styles.cardTags}>
            {wish.tags.slice(0, 3).map(tag => (
              <Text key={tag} className={styles.tag}>{tag}</Text>
            ))}
          </View>
        )}
        <View className={styles.cardFooter}>
          <View className={styles.author}>
            {wish.authorAvatar ? (
              <Image className={styles.avatar} src={wish.authorAvatar} mode='aspectFill' />
            ) : (
              <View className={styles.avatarPlaceholder}>
                <Text style={{ fontSize: '20rpx', color: FRUIT_COLORS[wish.fruitType] }}>★</Text>
              </View>
            )}
            <Text className={styles.authorName}>{wish.authorNickname}</Text>
          </View>
          <View className={styles.stats}>
            <Text className={styles.statItem}>♥ {formatCount(wish.supportCount)}</Text>
            <Text className={styles.statItem}>💬 {formatCount(wish.commentCount)}</Text>
          </View>
        </View>
        {wish.status !== 'ACTIVE' && (
          <Text className={styles.statusTag}>{STATUS_LABELS[wish.status] || wish.status}</Text>
        )}
      </View>
    </View>
  )

  return (
    <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}rpx`, minHeight: '100vh' }}>
      <CustomNavBar title='心愿广场' back />
      {/* 搜索栏 */}
      <View className={styles.searchBar}>
        <Input
          className={styles.searchInput}
          placeholder='搜索心愿...'
          value={keyword}
          onInput={e => setKeyword(e.detail.value)}
          onConfirm={() => handleSearch(keyword)}
          confirmType='search'
        />
      </View>

      {/* 分类筛选 */}
      <ScrollView scrollX className={styles.categoryBar}>
        <View
          className={`${styles.categoryChip} ${categoryId === undefined ? styles.categoryChipActive : ''}`}
          onClick={() => handleCategoryTap(undefined)}
        >
          <Text>全部</Text>
        </View>
        {categories.map(cat => (
          <View
            key={cat.id}
            className={`${styles.categoryChip} ${categoryId === cat.id ? styles.categoryChipActive : ''}`}
            onClick={() => handleCategoryTap(cat.id)}
          >
            <Text>{cat.name}</Text>
          </View>
        ))}
      </ScrollView>

      {loading ? (
        <View className={styles.loading}>
          <View className={styles.spinner} />
        </View>
      ) : wishes.length === 0 ? (
        <View className={styles.empty}>
          <Text className={styles.emptyIcon}>🌟</Text>
          <Text className={styles.emptyText}>暂无心愿，成为第一个许愿的人吧</Text>
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
          <View className={styles.masonryWrap}>
            <WaterfallFlow gap={16}>
              {wishes.map(renderWishCard)}
            </WaterfallFlow>
          </View>

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
