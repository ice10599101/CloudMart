import { useState, useEffect, useCallback } from 'react'
import { View, Text, ScrollView, Image } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { communityApi } from '@/api/community'
import { useThemeClass } from '@/composables/useThemeClass'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import styles from './index.module.scss'

interface PostItem {
  id: number
  title: string
  content?: string
  coverImage?: string
  images?: string[]
  likeCount: number
  commentCount: number
  viewCount?: number
  createdAt: string
  user?: {
    id: number
    nickname: string
    avatar: string
  }
}

const PAGE_SIZE = 20

export default function TopicDetailPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()

  const params = Taro.getCurrentInstance().router?.params
  const tagId = params?.id || ''
  const tagName = params?.name || ''

  const [posts, setPosts] = useState<PostItem[]>([])
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [totalCount, setTotalCount] = useState(0)
  const [isSearchMode, setIsSearchMode] = useState(false)

  const isSearchFallback = !tagId && !!tagName

  const loadPosts = useCallback(async (pageNum: number, isRefresh = false) => {
    if (loading) return
    if (!isRefresh && !hasMore) return

    setLoading(true)
    try {
      let res: any
      if (isSearchFallback) {
        setIsSearchMode(true)
        res = await communityApi.searchPosts({ keyword: tagName!, page: pageNum, pageSize: PAGE_SIZE })
      } else {
        res = await communityApi.getTagPosts(tagId, { page: pageNum, pageSize: PAGE_SIZE })
      }
      const list: PostItem[] = res.data?.data?.list || res.data?.data || []
      const total = res.data?.data?.total || res.data?.meta?.total || 0

      if (isRefresh) {
        setPosts(list)
      } else {
        setPosts((prev) => [...prev, ...list])
      }
      setTotalCount(total)
      setHasMore(list.length >= PAGE_SIZE)
      setPage(pageNum)
    } catch {
      if (isRefresh) setPosts([])
      Taro.showToast({ title: '加载失败', icon: 'none' })
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [tagId, tagName, isSearchFallback, hasMore, loading])

  useEffect(() => {
    loadPosts(1, true)
  }, [loadPosts])

  const handleRefresh = async () => {
    setRefreshing(true)
    setHasMore(true)
    await loadPosts(1, true)
  }

  const handleLoadMore = () => {
    if (hasMore && !loading) {
      loadPosts(page + 1)
    }
  }

  const handlePostClick = (postId: number) => {
    Taro.navigateTo({ url: `/pages/postDetail/index?id=${postId}` })
  }

  const formatCount = (count: number) => {
    if (count >= 10000) return `${(count / 10000).toFixed(1)}w`
    if (count >= 1000) return `${(count / 1000).toFixed(1)}k`
    return String(count)
  }

  const formatTime = (time: string) => {
    const date = new Date(time)
    const now = new Date()
    const diff = now.getTime() - date.getTime()
    if (diff < 60000) return '刚刚'
    if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
    if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`
    return `${date.getMonth() + 1}/${date.getDate()}`
  }

  const renderPostCard = (post: PostItem) => {
    const coverImage = post.coverImage || post.images?.[0]
    return (
      <View key={post.id} className={styles.postCard} onClick={() => handlePostClick(post.id)}>
        {coverImage && (
          <Image className={styles.postCover} src={coverImage} mode='aspectFill' />
        )}
        <View className={styles.postBody}>
          <Text className={styles.postTitle}>{post.title}</Text>
          {post.content && (
            <Text className={styles.postContent}>{post.content}</Text>
          )}
          <View className={styles.postFooter}>
            {post.user && (
              <View className={styles.authorInfo}>
                <Image className={styles.authorAvatar} src={post.user.avatar} mode='aspectFill' />
                <Text className={styles.authorName}>{post.user.nickname}</Text>
              </View>
            )}
            <View className={styles.postStats}>
              <Text className={styles.statItem}>❤️ {formatCount(post.likeCount)}</Text>
              <Text className={styles.statItem}>💬 {formatCount(post.commentCount)}</Text>
              <Text className={styles.statTime}>{formatTime(post.createdAt)}</Text>
            </View>
          </View>
        </View>
      </View>
    )
  }

  const displayName = tagName || (tagId ? `话题${tagId}` : '话题')

  return (
    <View data-theme={dataTheme} className={styles.page} style={{ ...themeStyle, paddingTop: `${statusBarHeight + navBarHeight}px` }}>
      <CustomNavBar title="话题详情" back />

      {/* Header */}
      <View className={styles.header}>
        <View className={styles.headerContent}>
          <Text className={styles.topicName}>#{displayName}</Text>
          <Text className={styles.topicCount}>
            {totalCount > 0 ? `${totalCount} 篇内容` : '暂无内容'}
          </Text>
          {isSearchMode && (
            <Text className={styles.searchHint}>按关键词搜索的结果</Text>
          )}
        </View>
      </View>

      {/* Post List */}
      <ScrollView
        scrollY
        className={styles.scrollContent}
        refresherEnabled
        refresherTriggered={refreshing}
        onRefresherRefresh={handleRefresh}
        onScrollToLower={handleLoadMore}
      >
        {posts.length > 0 ? (
          <View className={styles.postList}>
            {posts.map(renderPostCard)}
          </View>
        ) : !loading ? (
          <View className={styles.empty}>
            <Text className={styles.emptyIcon}>🏷️</Text>
            <Text className={styles.emptyText}>暂无相关内容</Text>
            <Text className={styles.emptySubText}>换个话题看看吧</Text>
          </View>
        ) : null}

        {loading && (
          <View className={styles.loadingMore}>
            <View className={styles.spinner} />
            <Text className={styles.loadingText}>加载中...</Text>
          </View>
        )}

        {!hasMore && posts.length > 0 && (
          <View className={styles.noMore}>
            <Text className={styles.noMoreText}>没有更多了</Text>
          </View>
        )}
      </ScrollView>
    </View>
  )
}
