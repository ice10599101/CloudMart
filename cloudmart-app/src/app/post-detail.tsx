import { View, Text, ScrollView, TouchableOpacity, Image, TextInput, Alert, ActivityIndicator, KeyboardAvoidingView, Platform } from 'react-native'
import { useState, useEffect } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { communityApi } from '@/api/community'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

const REPORT_REASONS = ['垃圾广告', '色情低俗', '违法违规', '侵权抄袭', '人身攻击', '虚假信息', '其他']

interface PostData {
  id: number
  title: string
  content: string
  images?: string[]
  tags?: { id: number; name: string }[]
  likeCount: number
  commentCount: number
  collectCount: number
  isLiked?: boolean
  isCollected?: boolean
  createdAt: string
  user?: { id: number; nickname: string; avatar?: string }
}

interface CommentData {
  id: number
  content: string
  createdAt: string
  user?: { id: number; nickname: string; avatar?: string }
}

export default function PostDetailScreen() {
  const theme = useTheme()
  const { id } = useLocalSearchParams<{ id: string }>()
  const postId = Number(id)

  const [post, setPost] = useState<PostData | null>(null)
  const [comments, setComments] = useState<CommentData[]>([])
  const [commentContent, setCommentContent] = useState('')
  const [isFollowing, setIsFollowing] = useState(false)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (postId) {
      loadPost()
      loadComments()
    }
  }, [postId])

  const loadPost = async () => {
    try {
      const res = await communityApi.getPost(postId)
      setPost(res.data?.data)
    } catch {
      // API unavailable
    } finally {
      setLoading(false)
    }
  }

  const loadComments = async () => {
    try {
      const res = await communityApi.getComments(postId, { page: 1, pageSize: 50 })
      setComments(res.data?.data?.list || [])
    } catch {
      // API unavailable
    }
  }

  const handleLike = async () => {
    if (!post) return
    try {
      if (post.isLiked) await communityApi.unlikePost(post.id)
      else await communityApi.likePost(post.id)
      setPost({ ...post, isLiked: !post.isLiked, likeCount: post.isLiked ? post.likeCount - 1 : post.likeCount + 1 })
    } catch {
      Alert.alert('错误', '操作失败')
    }
  }

  const handleCollect = async () => {
    if (!post) return
    try {
      if (post.isCollected) await communityApi.uncollectPost(post.id)
      else await communityApi.collectPost(post.id)
      setPost({ ...post, isCollected: !post.isCollected, collectCount: post.isCollected ? post.collectCount - 1 : post.collectCount + 1 })
    } catch {
      Alert.alert('错误', '操作失败')
    }
  }

  const handleFollow = async () => {
    if (!post?.user) return
    try {
      if (isFollowing) await communityApi.unfollowUser(post.user.id)
      else await communityApi.followUser(post.user.id)
      setIsFollowing(!isFollowing)
    } catch {
      Alert.alert('错误', '操作失败')
    }
  }

  const handleComment = async () => {
    if (!commentContent.trim()) return
    try {
      await communityApi.createComment(postId, { content: commentContent })
      setCommentContent('')
      Alert.alert('提示', '评论成功')
      loadComments()
    } catch {
      Alert.alert('错误', '评论失败')
    }
  }

  const handleShare = () => {
    Alert.alert('分享', '复制链接分享给好友', [
      { text: '取消', style: 'cancel' },
      {
        text: '复制链接',
        onPress: async () => {
          try {
            await communityApi.sharePost(postId)
            Alert.alert('提示', '链接已复制')
          } catch {
            Alert.alert('错误', '分享失败')
          }
        },
      },
    ])
  }

  const handleReport = () => {
    Alert.alert('举报', '请选择举报原因', [
      { text: '取消', style: 'cancel' },
      ...REPORT_REASONS.map((reason) => ({
        text: reason,
        onPress: async () => {
          try {
            await communityApi.report({ targetType: 'POST', targetId: postId, reason })
            Alert.alert('提示', '举报成功')
          } catch {
            Alert.alert('错误', '举报失败')
          }
        },
      })),
    ])
  }

  const handleMoreActions = () => {
    Alert.alert('更多操作', '', [
      { text: '取消', style: 'cancel' },
      { text: '分享', onPress: handleShare },
      { text: '举报', style: 'destructive', onPress: handleReport },
    ])
  }

  const formatTime = (time: string) => {
    const date = new Date(time)
    const now = new Date()
    const diff = now.getTime() - date.getTime()
    if (diff < 60000) return '刚刚'
    if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
    return `${date.getMonth() + 1}月${date.getDate()}日`
  }

  if (loading) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.bgBase, justifyContent: 'center', alignItems: 'center' }}>
        <ActivityIndicator size="large" color={theme.primary} />
      </View>
    )
  }

  if (!post) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.bgBase, justifyContent: 'center', alignItems: 'center' }}>
        <Text style={{ color: theme.textSecondary }}>内容不存在</Text>
      </View>
    )
  }

  return (
    <KeyboardAvoidingView style={{ flex: 1, backgroundColor: theme.bgBase }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={{ paddingBottom: 80 }}>
        {/* Author */}
        <View style={{ flexDirection: 'row', alignItems: 'center', padding: Spacing.lg }}>
          {post.user && (
            <TouchableOpacity onPress={() => router.push(`/user-profile?id=${post.user!.id}`)}>
              <Image source={{ uri: post.user.avatar }} style={{ width: 40, height: 40, borderRadius: 20, marginRight: Spacing.md }} />
            </TouchableOpacity>
          )}
          <View style={{ flex: 1 }}>
            {post.user && (
              <TouchableOpacity onPress={() => router.push(`/user-profile?id=${post.user!.id}`)}>
                <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: theme.text }}>{post.user.nickname}</Text>
              </TouchableOpacity>
            )}
            <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: 2 }}>{formatTime(post.createdAt)}</Text>
          </View>
          <TouchableOpacity
            onPress={handleFollow}
            style={{ paddingHorizontal: Spacing.lg, paddingVertical: Spacing.sm, borderRadius: BorderRadius.xl, borderWidth: 1, borderColor: theme.primary }}
          >
            <Text style={{ fontSize: FontSize.sm, color: theme.primary }}>{isFollowing ? '已关注' : '关注'}</Text>
          </TouchableOpacity>
        </View>

        {/* Content */}
        <View style={{ paddingHorizontal: Spacing.lg }}>
          <Text style={{ fontSize: FontSize.xl, fontWeight: '600', color: theme.text, lineHeight: 28, marginBottom: Spacing.md }}>{post.title}</Text>
          <Text style={{ fontSize: FontSize.md, color: theme.text, lineHeight: 24, marginBottom: Spacing.md }}>{post.content}</Text>

          {post.images?.map((img, i) => (
            <Image key={i} source={{ uri: img }} style={{ width: '100%', height: 200, borderRadius: BorderRadius.md, marginBottom: Spacing.sm, resizeMode: 'cover' }} />
          ))}

          {post.tags && post.tags.length > 0 && (
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm, marginTop: Spacing.md, marginBottom: Spacing.lg }}>
              {post.tags.map((tag) => (
                <Text key={tag.id} style={{ fontSize: FontSize.sm, color: theme.primary }}>#{tag.name}</Text>
              ))}
            </View>
          )}
        </View>

        {/* Comments */}
        <View style={{ padding: Spacing.lg, borderTopWidth: 8, borderTopColor: theme.bgPage }}>
          <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: theme.text, marginBottom: Spacing.lg }}>
            评论 ({post.commentCount})
          </Text>
          {comments.map((comment) => (
            <View key={comment.id} style={{ flexDirection: 'row', marginBottom: Spacing.lg }}>
              {comment.user && (
                <Image source={{ uri: comment.user.avatar }} style={{ width: 32, height: 32, borderRadius: 16, marginRight: Spacing.md }} />
              )}
              <View style={{ flex: 1 }}>
                {comment.user && <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginBottom: 2 }}>{comment.user.nickname}</Text>}
                <Text style={{ fontSize: FontSize.md, color: theme.text, lineHeight: 22 }}>{comment.content}</Text>
                <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: 4 }}>{formatTime(comment.createdAt)}</Text>
              </View>
            </View>
          ))}
        </View>
      </ScrollView>

      {/* Bottom Action Bar */}
      <View style={{
        position: 'absolute',
        bottom: 0,
        left: 0,
        right: 0,
        flexDirection: 'row',
        alignItems: 'center',
        padding: Spacing.md,
        backgroundColor: theme.bgContainer,
        borderTopWidth: 1,
        borderTopColor: theme.border,
        gap: Spacing.md,
      }}>
        <TextInput
          placeholder="写评论..."
          placeholderTextColor={theme.textTertiary}
          value={commentContent}
          onChangeText={setCommentContent}
          style={{
            flex: 1,
            height: 40,
            backgroundColor: theme.bgInput,
            borderRadius: BorderRadius.xl,
            paddingHorizontal: Spacing.lg,
            color: theme.text,
            fontSize: FontSize.md,
          }}
        />
        <TouchableOpacity onPress={handleComment}>
          <Text style={{ fontSize: FontSize.md, color: theme.primary, fontWeight: '600' }}>发送</Text>
        </TouchableOpacity>
        <TouchableOpacity onPress={handleLike} style={{ alignItems: 'center' }}>
          <Text style={{ fontSize: 20 }}>{post.isLiked ? '❤️' : '🤍'}</Text>
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>{post.likeCount}</Text>
        </TouchableOpacity>
        <TouchableOpacity onPress={handleCollect} style={{ alignItems: 'center' }}>
          <Text style={{ fontSize: 20 }}>{post.isCollected ? '⭐' : '☆'}</Text>
          <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>{post.collectCount}</Text>
        </TouchableOpacity>
        <TouchableOpacity onPress={handleMoreActions} style={{ alignItems: 'center' }}>
          <Text style={{ fontSize: 20 }}>⋯</Text>
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  )
}
