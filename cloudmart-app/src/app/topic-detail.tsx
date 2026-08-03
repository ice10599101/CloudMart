import { View, Text, FlatList, TouchableOpacity, Image, ActivityIndicator } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { communityApi } from '@/api/community'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import type { Post, Tag } from '@/types'

const PAGE_SIZE = 10

export default function TopicDetailScreen() {
  const theme = useTheme()
  const { id, name } = useLocalSearchParams<{ id?: string; name?: string }>()
  const tagId = id ? Number(id) : 0
  const tagName = name || '话题'

  const [posts, setPosts] = useState<Post[]>([])
  const [tag, setTag] = useState<Tag | null>(null)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [isSubscribed, setIsSubscribed] = useState(false)
  const [subscribing, setSubscribing] = useState(false)

  useEffect(() => {
    loadPosts(1, true)
  }, [tagId, tagName])

  const loadPosts = useCallback(async (targetPage: number, isRefresh = false) => {
    if (loading && !isRefresh) return
    setLoading(true)

    try {
      let res
      if (tagId) {
        res = await communityApi.getTagPosts(tagId, { page: targetPage, pageSize: PAGE_SIZE })
      } else if (tagName) {
        res = await communityApi.searchPosts({ keyword: tagName, page: targetPage, pageSize: PAGE_SIZE })
      } else {
        setPosts([])
        setLoading(false)
        return
      }

      const list: Post[] = res.data?.data?.list || res.data?.data || []
      const total = res.data?.meta?.total ?? res.data?.data?.total ?? 0

      if (isRefresh || targetPage === 1) {
        setPosts(list)
      } else {
        setPosts((prev) => [...prev, ...list])
      }
      setHasMore(list.length >= PAGE_SIZE)
      setPage(targetPage)

      // Extract tag info from first post if available
      if (targetPage === 1 && list.length > 0 && list[0].tags) {
        const matchedTag = list[0].tags.find((t) => t.name === tagName || t.id === tagId)
        if (matchedTag) {
          setTag(matchedTag)
          setIsSubscribed(matchedTag.isSubscribed ?? false)
        }
      }
    } catch {
      if (isRefresh || targetPage === 1) setPosts([])
      setHasMore(false)
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [tagId, tagName, loading])

  const handleRefresh = useCallback(async () => {
    setRefreshing(true)
    await loadPosts(1, true)
  }, [loadPosts])

  const loadMore = useCallback(async () => {
    if (loading || !hasMore) return
    await loadPosts(page + 1)
  }, [loadPosts, page, loading, hasMore])

  const handleSubscribe = async () => {
    if (subscribing || !tag) return
    setSubscribing(true)
    try {
      if (isSubscribed) {
        await communityApi.unfollowUser(tag.id)
      } else {
        await communityApi.followUser(tag.id)
      }
      setIsSubscribed(!isSubscribed)
    } catch {
      // subscribe action failed silently
    } finally {
      setSubscribing(false)
    }
  }

  const postCount = tag?.postCount ?? posts.length

  const renderPostItem = ({ item }: { item: Post }) => (
    <TouchableOpacity
      activeOpacity={0.7}
      onPress={() => router.push(`/post-detail?id=${item.id}`)}
      style={{
        flexDirection: 'row',
        backgroundColor: theme.bgContainer,
        borderRadius: BorderRadius.lg,
        overflow: 'hidden',
        borderWidth: 1,
        borderColor: theme.border,
        marginBottom: Spacing.md,
      }}
    >
      {item.images?.[0] ? (
        <Image source={{ uri: item.images[0] }} style={{ width: 110, height: 110, resizeMode: 'cover' }} />
      ) : (
        <View style={{ width: 110, height: 110, backgroundColor: theme.primaryGlow, justifyContent: 'center', alignItems: 'center' }}>
          <Text style={{ fontSize: 28, opacity: 0.3 }}>📝</Text>
        </View>
      )}
      <View style={{ flex: 1, padding: Spacing.md, justifyContent: 'space-between' }}>
        <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: theme.text }} numberOfLines={2}>{item.title}</Text>
        {item.content ? (
          <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, marginTop: Spacing.xs }} numberOfLines={1}>{item.content}</Text>
        ) : null}
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }}>
          {item.user?.avatar ? (
            <Image source={{ uri: item.user.avatar }} style={{ width: 20, height: 20, borderRadius: 10 }} />
          ) : null}
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }} numberOfLines={1}>
            {item.user?.nickname || '匿名用户'}
          </Text>
        </View>
        <View style={{ flexDirection: 'row', gap: Spacing.md }}>
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>❤️ {item.likeCount}</Text>
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>💬 {item.commentCount}</Text>
        </View>
      </View>
    </TouchableOpacity>
  )

  const renderEmpty = () => (
    <View style={{ alignItems: 'center', paddingTop: 100 }}>
      <Text style={{ fontSize: 48, marginBottom: Spacing.lg, opacity: 0.3 }}>🏷️</Text>
      <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>暂无相关帖子</Text>
      <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: Spacing.xs }}>
        快去发布第一条相关帖子吧
      </Text>
    </View>
  )

  const renderFooter = () => {
    if (loading && !refreshing) {
      return (
        <View style={{ paddingVertical: Spacing.lg, alignItems: 'center' }}>
          <ActivityIndicator size="small" color={theme.primary} />
        </View>
      )
    }
    if (!hasMore && posts.length > 0) {
      return (
        <View style={{ paddingVertical: Spacing.lg, alignItems: 'center' }}>
          <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>没有更多了</Text>
        </View>
      )
    }
    return null
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      {/* Header */}
      <View style={{
        flexDirection: 'row',
        alignItems: 'center',
        paddingHorizontal: Spacing.lg,
        paddingVertical: Spacing.md,
        backgroundColor: theme.bgContainer,
        borderBottomWidth: 1,
        borderBottomColor: theme.border,
        gap: Spacing.md,
      }}>
        <TouchableOpacity activeOpacity={0.7} onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.xl, color: theme.text }}>←</Text>
        </TouchableOpacity>
        <View style={{ flex: 1 }}>
          <Text style={{ fontSize: FontSize.xl, fontWeight: '700', color: theme.text }} numberOfLines={1}>
            #{tagName}
          </Text>
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: 2 }}>
            {postCount} 篇帖子
          </Text>
        </View>
        {tag ? (
          <TouchableOpacity
            activeOpacity={0.7}
            onPress={handleSubscribe}
            disabled={subscribing}
            style={{
              paddingHorizontal: Spacing.lg,
              paddingVertical: Spacing.sm,
              borderRadius: BorderRadius.xl,
              backgroundColor: isSubscribed ? 'transparent' : theme.primary,
              borderWidth: 1,
              borderColor: isSubscribed ? theme.border : theme.primary,
            }}
          >
            <Text style={{
              fontSize: FontSize.sm,
              color: isSubscribed ? theme.textSecondary : (theme.isDark ? '#000' : '#FFF'),
              fontWeight: '600',
            }}>
              {isSubscribed ? '已订阅' : '+ 订阅'}
            </Text>
          </TouchableOpacity>
        ) : null}
      </View>

      {/* Post List */}
      <FlatList
        data={posts}
        keyExtractor={(item) => String(item.id)}
        renderItem={renderPostItem}
        contentContainerStyle={{ padding: Spacing.lg }}
        refreshing={refreshing}
        onRefresh={handleRefresh}
        onEndReached={loadMore}
        onEndReachedThreshold={0.3}
        ListEmptyComponent={!loading ? renderEmpty : undefined}
        ListFooterComponent={renderFooter}
      />
    </View>
  )
}
