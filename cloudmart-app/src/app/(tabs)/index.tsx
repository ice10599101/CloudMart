import { View, Text, ScrollView, TouchableOpacity, Image, RefreshControl, ActivityIndicator, FlatList } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { useAuthStore } from '@/store/auth'
import { communityApi } from '@/api/community'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import type { Post } from '@/types'

const TABS = [
  { id: 0, name: '推荐' },
  { id: 1, name: '关注' },
  { id: 2, name: '热门' },
  { id: 3, name: '最新' },
]

function PostCard({ post, theme }: { post: Post; theme: ReturnType<typeof useTheme> }) {
  return (
    <TouchableOpacity
      activeOpacity={0.8}
      onPress={() => router.push(`/post-detail?id=${post.id}`)}
      style={{
        backgroundColor: theme.bgContainer,
        borderRadius: BorderRadius.lg,
        overflow: 'hidden',
        marginBottom: Spacing.md,
        ...theme.shadowCard,
      }}
    >
      {post.images && post.images.length > 0 && (
        <Image
          source={{ uri: post.images[0] }}
          style={{ width: '100%', height: 180, resizeMode: 'cover' }}
        />
      )}
      <View style={{ padding: Spacing.md }}>
        {post.title ? (
          <Text
            numberOfLines={2}
            style={{ fontSize: FontSize.md, fontWeight: '600', color: theme.text, marginBottom: Spacing.xs }}
          >
            {post.title}
          </Text>
        ) : null}
        <Text
          numberOfLines={2}
          style={{ fontSize: FontSize.sm, color: theme.textSecondary, lineHeight: 18 }}
        >
          {post.content}
        </Text>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: Spacing.sm }}>
          <View style={{ flexDirection: 'row', alignItems: 'center' }}>
            {post.user?.avatar ? (
              <Image source={{ uri: post.user.avatar }} style={{ width: 22, height: 22, borderRadius: 11, marginRight: Spacing.xs }} />
            ) : (
              <View style={{ width: 22, height: 22, borderRadius: 11, backgroundColor: theme.primaryGlow, justifyContent: 'center', alignItems: 'center', marginRight: Spacing.xs }}>
                <Text style={{ fontSize: 10, color: theme.primary }}>{post.user?.nickname?.[0] || '?'}</Text>
              </View>
            )}
            <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>{post.user?.nickname || '匿名'}</Text>
          </View>
          <View style={{ flexDirection: 'row', alignItems: 'center' }}>
            <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>❤ {post.likeCount}</Text>
            <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginLeft: Spacing.sm }}>💬 {post.commentCount}</Text>
          </View>
        </View>
      </View>
    </TouchableOpacity>
  )
}

export default function HomePage() {
  const theme = useTheme()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [activeTab, setActiveTab] = useState(0)
  const [posts, setPosts] = useState<Post[]>([])
  const [loading, setLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)

  const loadPosts = useCallback(async (pageNum: number, reset = false) => {
    if (loading) return
    setLoading(true)
    try {
      let res
      if (activeTab === 1) {
        res = await communityApi.getFollowingFeed({ page: pageNum, pageSize: 10 })
      } else if (activeTab === 2) {
        res = await communityApi.getFeed({ page: pageNum, pageSize: 10 })
      } else if (activeTab === 3) {
        res = await communityApi.getFeed({ page: pageNum, pageSize: 10 })
      } else {
        res = await communityApi.getFeed({ page: pageNum, pageSize: 10 })
      }
      const newPosts = (res.data as any)?.data?.list || []
      setPosts(reset ? newPosts : [...posts, ...newPosts])
      setHasMore(newPosts.length >= 10)
      setPage(pageNum)
    } catch {
      if (reset) setPosts([])
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [activeTab, loading, posts])

  useEffect(() => {
    loadPosts(1, true)
  }, [activeTab])

  const onRefresh = useCallback(() => {
    setRefreshing(true)
    loadPosts(1, true)
  }, [loadPosts])

  const handleLoadMore = () => {
    if (hasMore && !loading) loadPosts(page + 1)
  }

  const handleTabChange = (tabId: number) => {
    if (tabId === 1 && !isLoggedIn) {
      router.push('/login')
      return
    }
    setActiveTab(tabId)
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      {/* Header */}
      <View style={{ paddingHorizontal: Spacing.lg, paddingTop: Spacing.lg, paddingBottom: Spacing.sm, backgroundColor: theme.bgBase }}>
        {/* Search Bar */}
        <TouchableOpacity
          activeOpacity={0.7}
          onPress={() => router.push('/search')}
          style={{
            flexDirection: 'row',
            alignItems: 'center',
            backgroundColor: theme.bgInput,
            borderRadius: BorderRadius.xl,
            paddingHorizontal: Spacing.lg,
            paddingVertical: Spacing.md,
            marginBottom: Spacing.md,
          }}
        >
          <Text style={{ fontSize: 16, marginRight: Spacing.sm }}>🔍</Text>
          <Text style={{ fontSize: FontSize.md, color: theme.textTertiary }}>搜索内容、用户、话题</Text>
        </TouchableOpacity>

        {/* Tab Pills */}
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingBottom: Spacing.sm }}>
          {TABS.map((tab) => (
            <TouchableOpacity
              key={tab.id}
              activeOpacity={0.7}
              onPress={() => handleTabChange(tab.id)}
              style={{
                paddingHorizontal: Spacing.xl,
                paddingVertical: Spacing.sm,
                borderRadius: BorderRadius.xl,
                backgroundColor: activeTab === tab.id ? theme.primary : 'transparent',
                marginRight: Spacing.sm,
              }}
            >
              <Text
                style={{
                  fontSize: FontSize.md,
                  fontWeight: activeTab === tab.id ? '600' : '400',
                  color: activeTab === tab.id ? '#FFFFFF' : theme.textSecondary,
                }}
              >
                {tab.name}
              </Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
      </View>

      {/* Post Feed */}
      <FlatList
        data={posts}
        keyExtractor={(item) => String(item.id)}
        numColumns={2}
        columnWrapperStyle={{ paddingHorizontal: Spacing.lg, gap: Spacing.md }}
        contentContainerStyle={{ paddingBottom: Spacing.xxl }}
        renderItem={({ item }) => <PostCard post={item} theme={theme} />}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={theme.primary} />}
        onEndReached={handleLoadMore}
        onEndReachedThreshold={0.3}
        ListEmptyComponent={
          !loading ? (
            <View style={{ alignItems: 'center', paddingVertical: Spacing.xxxl * 2 }}>
              <Text style={{ fontSize: 48, marginBottom: Spacing.lg }}>📭</Text>
              <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>暂无内容</Text>
              <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: Spacing.xs }}>下拉刷新试试</Text>
            </View>
          ) : null
        }
        ListFooterComponent={
          loading ? (
            <ActivityIndicator color={theme.primary} style={{ marginVertical: Spacing.xl }} />
          ) : !hasMore && posts.length > 0 ? (
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', paddingVertical: Spacing.xl }}>
              <View style={{ flex: 1, height: 1, backgroundColor: theme.border, marginHorizontal: Spacing.lg }} />
              <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>到底啦</Text>
              <View style={{ flex: 1, height: 1, backgroundColor: theme.border, marginHorizontal: Spacing.lg }} />
            </View>
          ) : null
        }
      />
    </View>
  )
}
