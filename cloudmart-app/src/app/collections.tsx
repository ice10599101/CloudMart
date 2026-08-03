import { View, Text, ScrollView, TouchableOpacity, Image, ActivityIndicator, Alert } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { useAuthStore } from '@/store/auth'
import { communityApi } from '@/api/community'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

interface PostItem {
  id: number
  title: string
  content?: string
  summary?: string
  coverImage?: string
  likeCount: number
  commentCount: number
  createdAt: string
  status?: number
}

interface CommentItem {
  id: number
  postId: number
  postTitle?: string
  content: string
  replyToNickname?: string
  likeCount: number
  createdAt: string
}

const TAB_LIST = [
  { key: 'posts', label: '我的帖子', icon: '📝' },
  { key: 'drafts', label: '我的草稿', icon: '📋' },
  { key: 'liked', label: '我的点赞', icon: '👍' },
  { key: 'replies', label: '我的回复', icon: '💬' },
]

type TabKey = typeof TAB_LIST[number]['key']

export default function CollectionsScreen() {
  const theme = useTheme()
  const { user } = useAuthStore()
  const params = useLocalSearchParams<{ type?: string }>()
  const initialTab = TAB_LIST.some((t) => t.key === params.type) ? params.type as TabKey : 'posts'

  const [activeTab, setActiveTab] = useState<TabKey>(initialTab)
  const [posts, setPosts] = useState<PostItem[]>([])
  const [drafts, setDrafts] = useState<PostItem[]>([])
  const [likedPosts, setLikedPosts] = useState<PostItem[]>([])
  const [comments, setComments] = useState<CommentItem[]>([])
  const [loading, setLoading] = useState(false)

  const fetchPosts = useCallback(async () => {
    if (!user?.id) return
    setLoading(true)
    try {
      const res = await communityApi.getUserPosts(user.id, { page: 1, pageSize: 50 })
      setPosts(res.data?.data?.list || res.data?.data || [])
    } catch {
      setPosts([])
    } finally {
      setLoading(false)
    }
  }, [user?.id])

  const fetchDrafts = useCallback(async () => {
    setLoading(true)
    try {
      const res = await communityApi.getUserDrafts({ page: 1, pageSize: 50 })
      setDrafts(res.data?.data?.list || res.data?.data || [])
    } catch {
      setDrafts([])
    } finally {
      setLoading(false)
    }
  }, [])

  const fetchLiked = useCallback(async () => {
    setLoading(true)
    try {
      const res = await communityApi.getLikedPosts({ page: 1, pageSize: 50 })
      setLikedPosts(res.data?.data?.list || res.data?.data || [])
    } catch {
      setLikedPosts([])
    } finally {
      setLoading(false)
    }
  }, [])

  const fetchComments = useCallback(async () => {
    setLoading(true)
    try {
      const res = await communityApi.getMyComments({ page: 1, pageSize: 50 })
      setComments(res.data?.data?.list || res.data?.data || [])
    } catch {
      setComments([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (activeTab === 'posts') fetchPosts()
    else if (activeTab === 'drafts') fetchDrafts()
    else if (activeTab === 'liked') fetchLiked()
    else if (activeTab === 'replies') fetchComments()
  }, [activeTab, fetchPosts, fetchDrafts, fetchLiked, fetchComments])

  const handlePublishDraft = (id: number) => {
    Alert.alert('确认发布', '确定要发布该草稿吗？发布后将在社区公开展示。', [
      { text: '取消', style: 'cancel' },
      {
        text: '发布',
        onPress: async () => {
          try {
            await communityApi.updatePost(id, { status: 1 })
            Alert.alert('提示', '发布成功')
            fetchDrafts()
          } catch {
            Alert.alert('错误', '发布失败')
          }
        },
      },
    ])
  }

  const handleDeleteDraft = (id: number) => {
    Alert.alert('确认删除', '确定要删除该草稿吗？删除后无法恢复。', [
      { text: '取消', style: 'cancel' },
      {
        text: '删除',
        style: 'destructive',
        onPress: async () => {
          try {
            await communityApi.deletePost(id)
            Alert.alert('提示', '删除成功')
            fetchDrafts()
          } catch {
            Alert.alert('错误', '删除失败')
          }
        },
      },
    ])
  }

  const renderPostCard = (post: PostItem) => (
    <TouchableOpacity
      key={post.id}
      activeOpacity={0.7}
      onPress={() => router.push(`/post-detail?id=${post.id}`)}
      style={{
        width: '48%',
        backgroundColor: theme.bgContainer,
        borderRadius: BorderRadius.lg,
        overflow: 'hidden',
        borderWidth: 1,
        borderColor: theme.border,
      }}
    >
      {post.coverImage ? (
        <Image source={{ uri: post.coverImage }} style={{ width: '100%', height: 120, resizeMode: 'cover' }} />
      ) : (
        <View style={{ width: '100%', height: 120, backgroundColor: theme.primaryGlow, justifyContent: 'center', alignItems: 'center' }}>
          <Text style={{ fontSize: 32, opacity: 0.3 }}>📝</Text>
        </View>
      )}
      <View style={{ padding: Spacing.sm }}>
        <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: theme.text }} numberOfLines={2}>{post.title || '未命名'}</Text>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: Spacing.xs }}>
          <View style={{ flexDirection: 'row', gap: Spacing.sm }}>
            <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>❤️ {post.likeCount}</Text>
            <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>💬 {post.commentCount}</Text>
          </View>
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>{new Date(post.createdAt).toLocaleDateString()}</Text>
        </View>
      </View>
    </TouchableOpacity>
  )

  const renderDraftCard = (draft: PostItem) => (
    <TouchableOpacity
      key={draft.id}
      activeOpacity={0.7}
      onPress={() => router.push(`/publish?edit=${draft.id}`)}
      style={{
        backgroundColor: theme.bgContainer,
        borderRadius: BorderRadius.lg,
        padding: Spacing.lg,
        borderWidth: 1,
        borderColor: theme.border,
      }}
    >
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <View style={{ flex: 1, marginRight: Spacing.md }}>
          <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: theme.text }} numberOfLines={1}>
            {draft.title || '未命名草稿'}
          </Text>
          <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: Spacing.xs }} numberOfLines={2}>
            {draft.summary || draft.content?.substring(0, 80) || '暂无内容'}
          </Text>
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: Spacing.xs }}>
            最后编辑：{new Date(draft.createdAt).toLocaleString()}
          </Text>
        </View>
        <View style={{ flexDirection: 'row', gap: Spacing.sm }}>
          <TouchableOpacity
            onPress={() => handlePublishDraft(draft.id)}
            style={{
              paddingHorizontal: Spacing.md,
              paddingVertical: Spacing.xs,
              borderRadius: BorderRadius.sm,
              backgroundColor: theme.primaryGlow,
              borderWidth: 1,
              borderColor: theme.primary + '4D',
            }}
          >
            <Text style={{ fontSize: FontSize.xs, color: theme.primary, fontWeight: '600' }}>发布</Text>
          </TouchableOpacity>
          <TouchableOpacity
            onPress={() => handleDeleteDraft(draft.id)}
            style={{
              paddingHorizontal: Spacing.md,
              paddingVertical: Spacing.xs,
              borderRadius: BorderRadius.sm,
              borderWidth: 1,
              borderColor: theme.accentRed + '4D',
            }}
          >
            <Text style={{ fontSize: FontSize.xs, color: theme.accentRed, fontWeight: '600' }}>删除</Text>
          </TouchableOpacity>
        </View>
      </View>
    </TouchableOpacity>
  )

  const renderCommentCard = (comment: CommentItem) => (
    <TouchableOpacity
      key={comment.id}
      activeOpacity={0.7}
      onPress={() => router.push(`/post-detail?id=${comment.postId}`)}
      style={{
        backgroundColor: theme.bgContainer,
        borderRadius: BorderRadius.lg,
        padding: Spacing.lg,
        borderWidth: 1,
        borderColor: theme.border,
      }}
    >
      <Text style={{ fontSize: FontSize.md, color: theme.text, lineHeight: 22 }} numberOfLines={3}>{comment.content}</Text>
      <View style={{ flexDirection: 'row', gap: Spacing.md, marginTop: Spacing.sm }}>
        <Text style={{ fontSize: FontSize.sm, color: theme.primary }}>原帖：{comment.postTitle || `帖子#${comment.postId}`}</Text>
        {comment.replyToNickname && (
          <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary }}>回复 @{comment.replyToNickname}</Text>
        )}
      </View>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: Spacing.xs }}>
        <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>{new Date(comment.createdAt).toLocaleString()}</Text>
        <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>❤️ {comment.likeCount}</Text>
      </View>
    </TouchableOpacity>
  )

  const renderEmpty = (icon: string, text: string, subText?: string) => (
    <View style={{ alignItems: 'center', paddingTop: 100 }}>
      <Text style={{ fontSize: 48, marginBottom: Spacing.lg, opacity: 0.3 }}>{icon}</Text>
      <Text style={{ fontSize: FontSize.lg, color: theme.textSecondary }}>{text}</Text>
      {subText && <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: Spacing.xs }}>{subText}</Text>}
    </View>
  )

  const renderContent = () => {
    if (loading) {
      return (
        <View style={{ alignItems: 'center', paddingTop: 100 }}>
          <ActivityIndicator size="large" color={theme.primary} />
        </View>
      )
    }

    switch (activeTab) {
      case 'posts':
        return posts.length > 0
          ? <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.md }}>{posts.map(renderPostCard)}</View>
          : renderEmpty('📝', '暂无帖子', '去社区发帖吧')
      case 'drafts':
        return drafts.length > 0
          ? <View style={{ gap: Spacing.md }}>{drafts.map(renderDraftCard)}</View>
          : renderEmpty('📋', '暂无草稿', '发布内容时可保存为草稿稍后编辑')
      case 'liked':
        return likedPosts.length > 0
          ? <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.md }}>{likedPosts.map(renderPostCard)}</View>
          : renderEmpty('👍', '暂无点赞')
      case 'replies':
        return comments.length > 0
          ? <View style={{ gap: Spacing.md }}>{comments.map(renderCommentCard)}</View>
          : renderEmpty('💬', '暂无回复')
      default:
        return null
    }
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      {/* Tab Bar */}
      <View style={{
        flexDirection: 'row',
        backgroundColor: theme.bgContainer,
        borderBottomWidth: 1,
        borderBottomColor: theme.border,
      }}>
        {TAB_LIST.map((tab) => {
          const isActive = activeTab === tab.key
          return (
            <TouchableOpacity
              key={tab.key}
              activeOpacity={0.7}
              onPress={() => setActiveTab(tab.key)}
              style={{
                flex: 1,
                alignItems: 'center',
                paddingVertical: Spacing.md,
                position: 'relative',
              }}
            >
              <Text style={{ fontSize: 18, marginBottom: 2 }}>{tab.icon}</Text>
              <Text style={{
                fontSize: FontSize.sm,
                color: isActive ? theme.primary : theme.textTertiary,
                fontWeight: isActive ? '600' : '400',
              }}>
                {tab.label}
              </Text>
              {isActive && (
                <View style={{
                  position: 'absolute',
                  bottom: 0,
                  width: 20,
                  height: 3,
                  borderRadius: 1.5,
                  backgroundColor: theme.primary,
                }} />
              )}
            </TouchableOpacity>
          )
        })}
      </View>

      {/* Content */}
      <ScrollView contentContainerStyle={{ padding: Spacing.lg, paddingBottom: 40 }}>
        {renderContent()}
      </ScrollView>
    </View>
  )
}
