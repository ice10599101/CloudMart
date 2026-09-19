import { useState, useEffect } from 'react'
import { View, Text, ScrollView, Image } from '@tarojs/components'
import Taro from '@tarojs/taro'
import classnames from 'classnames'
import PostCard from '@/components/PostCard'
import WaterfallFlow from '@/components/WaterfallFlow'
import EmptyState from '@/components/EmptyState'
import { communityApi } from '@/api/community'
import { productApi } from '@/api/product'
import type { Post, Product } from '@/types'
import { ICON_BASE64 } from '@/components/Icon'
import { useThemeClass } from '@/composables/useThemeClass'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import CustomTabBar from '@/components/CustomTabBar'
import styles from './index.module.scss'

// 对齐 Web 端 Home：推荐/关注/热门/好物（热门=客户端热度排序、好物=好物帖过滤，与 Web 同逻辑）
const TABS = [
  { id: 0, name: '推荐' },
  { id: 1, name: '关注' },
  { id: 2, name: '热门' },
  { id: 3, name: '好物' },
]

/** 热度分（对齐 Web 端 hot 排序：点赞*3 + 评论*2 + 收藏 + 浏览*0.01） */
const hotScore = (p: Post) =>
  p.likeCount * 3 + p.commentCount * 2 + (p.collectCount ?? 0) + ((p as unknown as { viewCount?: number }).viewCount ?? 0) * 0.01

export default function HomePage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()

  const [activeTab, setActiveTab] = useState(0)
  const [posts, setPosts] = useState<Post[]>([])
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)
  const [hotTopics, setHotTopics] = useState<Array<{ id: number | string; name: string; postCount?: number }>>([])
  const [recommendUsers, setRecommendUsers] = useState<Array<{ userId: number; nickname: string; avatar?: string | null; signature?: string | null }>>([])
  const [hotProducts, setHotProducts] = useState<Product[]>([])

  useEffect(() => {
    loadPosts(1, true)
    // 侧栏数据横向化：热门话题 + 推荐关注（对齐 Web 端 Home 侧栏）
    communityApi
      .getHotTags()
      .then((res) => setHotTopics((res.data?.data || []).slice(0, 8)))
      .catch(() => {})
    communityApi
      .getRecommendUsers({ limit: 6 })
      .then((res) => setRecommendUsers(res.data?.data || []))
      .catch(() => {})
    // 热销好物（对齐 Web 端侧栏：销量 Top5）
    productApi
      .search({ page: 1, size: 5, sort: 'sales_desc' })
      .then((res) => setHotProducts((res.data?.data as unknown as { products?: Product[] })?.products || []))
      .catch(() => {})
  }, [])

  const loadPosts = async (pageNum: number, reset = false) => {
    if (loading) return
    setLoading(true)
    try {
      let res
      if (activeTab === 1) {
        res = await communityApi.getFollowingFeed({ page: pageNum, pageSize: 10 })
      } else {
        res = await communityApi.getFeed({ page: pageNum, pageSize: 10 })
      }
      let newPosts = res.data?.data?.list || []
      // 热门：本地热度排序（对齐 Web 端 fetchTab 逻辑）
      if (activeTab === 2) newPosts = [...newPosts].sort((a, b) => hotScore(b) - hotScore(a))
      // 好物：仅保留关联好物的帖子（对齐 Web 端 goods 过滤）
      if (activeTab === 3) newPosts = newPosts.filter((p) => (p as unknown as { productId?: number | null }).productId != null)
      setPosts(reset ? newPosts : [...posts, ...newPosts])
      setHasMore(newPosts.length >= 10)
      setPage(pageNum)
    } catch {
      if (reset) setPosts([])
    } finally {
      setLoading(false)
    }
  }

  const handleSearch = () => {
    Taro.navigateTo({ url: '/pages/search/index' })
  }

  const handleLoadMore = () => {
    if (hasMore && !loading) loadPosts(page + 1)
  }

  return (
    <View data-theme={dataTheme} className={styles.page} style={{ ...themeStyle, paddingTop: `${statusBarHeight + navBarHeight}px` }}>
      <CustomNavBar title="CloudMart" />
{/* Search Bar */}
      <View className={styles.searchBar} onClick={handleSearch}>
        <Image src={ICON_BASE64.search.default} style={{ width: '18px', height: '18px' }} mode='aspectFit' />
        <Text className={styles.searchPlaceholder}>搜索内容、用户、话题</Text>
      </View>

      {/* Wish Universe Entry */}
      <View className={styles.wishEntry} onClick={() => Taro.navigateTo({ url: '/pages/wishHome/index' })}>
        <Text className={styles.wishEntryIcon}>✨</Text>
        <View className={styles.wishEntryText}>
          <Text className={styles.wishEntryTitle}>心愿宇宙</Text>
          <Text className={styles.wishEntryDesc}>种下一颗心愿种子，看它发光</Text>
        </View>
        <Text className={styles.wishEntryArrow}>→</Text>
      </View>

      {/* Tab Pills */}
      <ScrollView scrollX className={styles.tabScroll}>
        <View className={styles.tabList}>
          {TABS.map((tab) => (
            <View
              key={tab.id}
              className={classnames(styles.tabPill, activeTab === tab.id && styles.tabPillActive)}
              onClick={() => setActiveTab(tab.id)}
            >
              <Text className={classnames(styles.tabPillText, activeTab === tab.id && styles.tabPillTextActive)}>
                {tab.name}
              </Text>
            </View>
          ))}
        </View>
      </ScrollView>

      {/* Content */}
      <ScrollView scrollY className={styles.content} onScrollToLower={handleLoadMore}>
        {/* 热门话题横滑（对齐 Web 端侧栏「热门话题」） */}
        {hotTopics.length > 0 && (
          <View className={styles.topicStrip}>
            <Text className={styles.stripTitle}>🔥 热门话题</Text>
            <ScrollView scrollX enhanced showScrollbar={false} className={styles.stripScroll}>
              <View className={styles.stripRow}>
                {hotTopics.map((topic) => (
                  <View
                    key={topic.id}
                    className={styles.topicChip}
                    onClick={() => Taro.navigateTo({ url: `/pages/topicDetail/index?id=${topic.id}&name=${encodeURIComponent(topic.name)}` })}
                  >
                    <Text className={styles.topicChipText}>#{topic.name}</Text>
                    {topic.postCount != null && <Text className={styles.topicChipCount}>{topic.postCount}</Text>}
                  </View>
                ))}
              </View>
            </ScrollView>
          </View>
        )}
        {/* 热销好物横滑（对齐 Web 端侧栏「热销好物」） */}
        {hotProducts.length > 0 && (
          <View className={styles.topicStrip}>
            <Text className={styles.stripTitle}>🛍️ 热销好物</Text>
            <ScrollView scrollX enhanced showScrollbar={false} className={styles.stripScroll}>
              <View className={styles.stripRow}>
                {hotProducts.map((product) => (
                  <View
                    key={product.id}
                    className={styles.productChip}
                    onClick={() => Taro.navigateTo({ url: `/pages/productDetail/index?id=${product.id}` })}
                  >
                    {product.mainImage && <Image className={styles.productChipImage} src={product.mainImage} mode='aspectFill' />}
                    <Text className={styles.productChipName} numberOfLines={1}>{product.name}</Text>
                    <Text className={styles.productChipPrice}>¥{product.price}</Text>
                  </View>
                ))}
              </View>
            </ScrollView>
          </View>
        )}

        {/* 推荐关注横滑（对齐 Web 端侧栏「推荐关注」） */}
        {recommendUsers.length > 0 && (
          <View className={styles.topicStrip}>
            <Text className={styles.stripTitle}>👥 推荐关注</Text>
            <ScrollView scrollX enhanced showScrollbar={false} className={styles.stripScroll}>
              <View className={styles.stripRow}>
                {recommendUsers.map((u) => (
                  <View key={u.userId} className={styles.userChip} onClick={() => Taro.navigateTo({ url: `/pages/userProfile/index?userId=${u.userId}` })}>
                    {u.avatar ? (
                      <Image className={styles.userChipAvatar} src={u.avatar} mode='aspectFill' />
                    ) : (
                      <View className={styles.userChipAvatarFallback}>
                        <Text className={styles.userChipAvatarText}>{(u.nickname || '?')[0]}</Text>
                      </View>
                    )}
                    <Text className={styles.userChipName}>{u.nickname}</Text>
                  </View>
                ))}
              </View>
            </ScrollView>
          </View>
        )}
        {posts.length > 0 ? (
          <WaterfallFlow gap={16}>
            {posts.map((post) => (
              <PostCard key={post.id} post={post} />
            ))}
          </WaterfallFlow>
        ) : (
          !loading && <EmptyState title="暂无内容" description="下拉刷新试试" />
        )}
        {loading && (
          <View className={styles.loading}>
            <View className={styles.loadingDot} />
            <Text className={styles.loadingText}>加载中</Text>
          </View>
        )}
        {!hasMore && posts.length > 0 && (
          <View className={styles.footer}>
            <View className={styles.footerLine} />
            <Text className={styles.footerText}>到底啦</Text>
            <View className={styles.footerLine} />
          </View>
        )}
      </ScrollView>
      <CustomTabBar />
    </View>
  )
}
