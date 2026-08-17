import { useState, useEffect } from 'react'
import { View, Text, ScrollView, Image } from '@tarojs/components'
import Taro from '@tarojs/taro'
import classnames from 'classnames'
import PostCard from '@/components/PostCard'
import WaterfallFlow from '@/components/WaterfallFlow'
import EmptyState from '@/components/EmptyState'
import { communityApi } from '@/api/community'
import type { Post } from '@/types'
import { ICON_BASE64 } from '@/components/Icon'
import { useThemeClass } from '@/composables/useThemeClass'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import CustomTabBar from '@/components/CustomTabBar'
import styles from './index.module.scss'

const TABS = [
  { id: 0, name: '推荐' },
  { id: 1, name: '关注' },
  { id: 2, name: '热门' },
  { id: 3, name: '最新' },
]

export default function HomePage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()

  const [activeTab, setActiveTab] = useState(0)
  const [posts, setPosts] = useState<Post[]>([])
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)

  useEffect(() => {
    loadPosts(1, true)
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
      const newPosts = res.data?.data?.list || []
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
