import RichHtml from '@/components/RichHtml'
import { View, Text, ScrollView, TouchableOpacity, Image, TextInput, Alert, ActivityIndicator, KeyboardAvoidingView, Platform, useWindowDimensions } from 'react-native'
import { useState, useEffect } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { useAuthStore } from '@/store/auth'
import GiftSection from '@/components/GiftSection'
import { communityApi } from '@/api/community'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

const REPORT_REASONS = ['垃圾广告', '色情低俗', '违法违规', '侵权抄袭', '人身攻击', '虚假信息', '其他']

interface PostData {
  id: number
  title: string
  content: string
  images?: string[]
  coverImage?: string
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
  /** 后端 PostCommentVO 兜底字段 */
  authorNickname?: string
  authorAvatar?: string
  likeCount?: number
  isLiked?: boolean
  parentId?: number | null
  replyToUserId?: number | null
  replyToNickname?: string | null
  replies?: CommentData[]
}

export default function PostDetailScreen() {
  const { width: contentWidth } = useWindowDimensions()
  const theme = useTheme()
  const { id } = useLocalSearchParams<{ id: string }>()
  const postId = Number(id)

  const [post, setPost] = useState<PostData | null>(null)
  const [comments, setComments] = useState<CommentData[]>([])
  const [commentContent, setCommentContent] = useState('')
  const [isFollowing, setIsFollowing] = useState(false)
  const [loading, setLoading] = useState(true)
  const [replyTo, setReplyTo] = useState<CommentData | null>(null)
  // 相关推荐（对齐 Web 端 PostDetail：同话题帖 fallback 推荐流）
  const [relatedPosts, setRelatedPosts] = useState<PostData[]>([])
  const [giftTick] = useState(0)
  const [commentPage, setCommentPage] = useState(1)
  const [commentHasMore, setCommentHasMore] = useState(false)
  const { user } = useAuthStore()

  useEffect(() => {
    if (postId) {
      loadPost()
      loadComments()
    }
  }, [postId])

  const loadPost = async () => {
    try {
      const res = await communityApi.getPost(postId)
      const detail = res.data?.data
      setPost(detail)
      // 浏览足迹上报（对齐 Web 端 PostDetail）
      if (detail) {
        void communityApi
          .recordBrowseHistory({
            targetType: 'POST',
            targetId: detail.id,
            title: detail.title,
            cover: detail.coverImage,
          })
          .catch(() => {})
        loadRelatedPosts(detail)
      }
      // 关注状态回显（后端 PostVO.user.isFollowed）
      if (detail?.user && (detail.user as { isFollowed?: boolean }).isFollowed != null) {
        setIsFollowing(!!(detail.user as { isFollowed?: boolean }).isFollowed)
      }
    } catch {
      // API unavailable
    } finally {
      setLoading(false)
    }
  }

  /** 相关推荐：首个话题下的帖子，不足时回退推荐流（对齐 Web 端逻辑） */
  const loadRelatedPosts = async (detail: PostData) => {
    const firstTagId = detail.tags?.[0]?.id
    try {
      if (firstTagId) {
        const res = await communityApi.getTagPosts(firstTagId, { page: 1, pageSize: 8 })
        const list = (res.data as { data?: { list?: PostData[] } })?.data?.list ?? (res.data?.data as unknown as PostData[]) ?? []
        setRelatedPosts(list.filter((p) => p.id !== detail.id).slice(0, 4))
        if (list.filter((p) => p.id !== detail.id).length >= 4) return
      }
      const res = await communityApi.getFeed({ page: 1, pageSize: 8 })
      const list = (res.data as { data?: { list?: PostData[] } })?.data?.list ?? []
      setRelatedPosts((prev) => {
        const seen = new Set([detail.id, ...prev.map((p) => p.id)])
        const extra = list.filter((p) => !seen.has(p.id)).slice(0, 4 - prev.length)
        return [...prev, ...extra]
      })
    } catch {
      // 相关推荐失败静默
    }
  }

  const loadComments = async (pageNum = 1, append = false) => {
    try {
      const res = await communityApi.getComments(postId, { page: pageNum, pageSize: 20 })
      const list = res.data?.data?.list || []
      setComments((prev) => (append ? [...prev, ...list] : list))
      setCommentPage(pageNum)
      setCommentHasMore(list.length >= 20)
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
    if (!user?.id) {
      Alert.alert('提示', '请先登录')
      return
    }
    try {
      // 回复模式：携带 parentId 与 replyToUserId（对齐 Web 端楼中楼）
      await communityApi.createComment(postId, {
        content: commentContent,
        parentId: replyTo?.parentId ?? replyTo?.id,
        replyToUserId: replyTo ? (replyTo.replyToUserId ?? replyTo.user?.id) : undefined,
      })
      setCommentContent('')
      setReplyTo(null)
      Alert.alert('提示', '评论成功')
      loadComments(1, false)
    } catch {
      Alert.alert('错误', '评论失败')
    }
  }

  /** 评论点赞/取消（对齐 Web 端评论工具条） */
  const handleCommentLike = async (comment: CommentData) => {
    try {
      if (comment.isLiked) await communityApi.unlikeComment(comment.id)
      else await communityApi.likeComment(comment.id)
      const patch = (c: CommentData): CommentData =>
        c.id === comment.id
          ? { ...c, isLiked: !c.isLiked, likeCount: Math.max(0, (c.likeCount ?? 0) + (c.isLiked ? -1 : 1)) }
          : { ...c, replies: c.replies?.map(patch) }
      setComments((prev) => prev.map(patch))
    } catch {
      Alert.alert('错误', '操作失败')
    }
  }

  /** 删除自己的评论 */
  const handleCommentDelete = (comment: CommentData) => {
    Alert.alert('删除评论', '确定删除这条评论吗？', [
      { text: '取消', style: 'cancel' },
      {
        text: '删除',
        style: 'destructive',
        onPress: async () => {
          try {
            await communityApi.deleteComment(postId, comment.id)
            setComments((prev) => prev.filter((c) => c.id !== comment.id))
          } catch {
            Alert.alert('错误', '删除失败')
          }
        },
      },
    ])
  }

  const handleCommentPress = (comment: CommentData) => {
    const isMine = comment.user?.id === user?.id
    const buttons: Array<{ text: string; style?: 'cancel' | 'destructive' | 'default'; onPress?: () => void }> = [
      { text: '取消', style: 'cancel' },
      { text: '回复', onPress: () => setReplyTo(comment) },
    ]
    if (isMine) {
      buttons.push({ text: '删除', style: 'destructive', onPress: () => handleCommentDelete(comment) })
    }
    Alert.alert('评论操作', '', buttons)
  }

  /** 编辑自己的帖子 */
  const handleEditPost = () => {
    router.push(`/(tabs)/publish?edit=${postId}`)
  }

  /** 删除自己的帖子 */
  const handleDeletePost = () => {
    Alert.alert('删除帖子', '删除后不可恢复，确定删除吗？', [
      { text: '取消', style: 'cancel' },
      {
        text: '删除',
        style: 'destructive',
        onPress: async () => {
          try {
            await communityApi.deletePost(postId)
            Alert.alert('提示', '已删除')
            router.back()
          } catch {
            Alert.alert('错误', '删除失败')
          }
        },
      },
    ])
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
    const isMine = post?.user?.id === user?.id
    const buttons: Array<{ text: string; style?: 'cancel' | 'destructive' | 'default'; onPress?: () => void }> = [
      { text: '取消', style: 'cancel' },
      { text: '分享', onPress: handleShare },
      { text: '举报', style: 'destructive', onPress: handleReport },
    ]
    if (isMine) {
      buttons.push({ text: '编辑', onPress: handleEditPost })
      buttons.push({ text: '删除', style: 'destructive', onPress: handleDeletePost })
    }
    Alert.alert('更多操作', '', buttons)
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
          <RichHtml content={post.content} width={contentWidth} color={theme.text} fontSize={FontSize.md} />

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

        {/* 全站虚拟礼物（对齐 Web 端帖子详情礼物区块） */}
        <GiftSection targetType="POST" targetId={postId} refreshTick={giftTick} />

        {/* 相关推荐（对齐 Web 端 PostDetail） */}
        {relatedPosts.length > 0 && (
          <View style={{ padding: Spacing.lg, borderTopWidth: 8, borderTopColor: theme.bgPage }}>
            <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: theme.text, marginBottom: Spacing.lg }}>相关推荐</Text>
            {relatedPosts.map((rel) => (
              <TouchableOpacity
                key={rel.id}
                activeOpacity={0.7}
                onPress={() => router.push(`/post-detail?id=${rel.id}`)}
                style={{ flexDirection: 'row', gap: Spacing.md, paddingVertical: Spacing.sm, borderBottomWidth: 1, borderBottomColor: theme.border }}
              >
                {rel.coverImage ? (
                  <Image source={{ uri: rel.coverImage }} style={{ width: 72, height: 52, borderRadius: BorderRadius.sm }} />
                ) : null}
                <View style={{ flex: 1 }}>
                  <Text numberOfLines={2} style={{ fontSize: FontSize.sm, fontWeight: '600', color: theme.text }}>{rel.title}</Text>
                  <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: 2 }}>❤ {rel.likeCount} · 💬 {rel.commentCount}</Text>
                </View>
              </TouchableOpacity>
            ))}
          </View>
        )}

        {/* Comments */}
        <View style={{ padding: Spacing.lg, borderTopWidth: 8, borderTopColor: theme.bgPage }}>
          <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: theme.text, marginBottom: Spacing.lg }}>
            评论 ({post.commentCount})
          </Text>
          {comments.map((comment) => (
            <TouchableOpacity
              key={comment.id}
              activeOpacity={0.7}
              onPress={() => handleCommentPress(comment)}
              style={{ flexDirection: 'row', marginBottom: Spacing.lg }}
            >
              {(comment.user?.avatar || comment.authorAvatar) && (
                <Image source={{ uri: comment.user?.avatar || comment.authorAvatar }} style={{ width: 32, height: 32, borderRadius: 16, marginRight: Spacing.md }} />
              )}
              <View style={{ flex: 1 }}>
                <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginBottom: 2 }}>
                  {comment.user?.nickname ?? comment.authorNickname ?? '匿名用户'}
                  {comment.replyToNickname ? ` · 回复 @${comment.replyToNickname}` : ''}
                </Text>
                <Text style={{ fontSize: FontSize.md, color: theme.text, lineHeight: 22 }}>{comment.content}</Text>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.lg, marginTop: 4 }}>
                  <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>{formatTime(comment.createdAt)}</Text>
                  <TouchableOpacity activeOpacity={0.7} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }} onPress={() => handleCommentLike(comment)}>
                    <Text style={{ fontSize: FontSize.xs, color: comment.isLiked ? theme.accentRed : theme.textTertiary }}>
                      {comment.isLiked ? '❤️' : '🤍'} {(comment.likeCount ?? 0) > 0 ? comment.likeCount : ''}
                    </Text>
                  </TouchableOpacity>
                </View>
                {comment.replies && comment.replies.length > 0 && (
                  <View style={{ marginTop: Spacing.sm, paddingLeft: Spacing.md, borderLeftWidth: 2, borderLeftColor: theme.border, gap: Spacing.sm }}>
                    {comment.replies.map((reply) => (
                      <TouchableOpacity key={reply.id} activeOpacity={0.7} onPress={() => handleCommentPress(reply)}>
                        <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginBottom: 2 }}>
                          {reply.user?.nickname ?? reply.authorNickname ?? '匿名用户'}
                          {reply.replyToNickname ? ` · 回复 @${reply.replyToNickname}` : ''}
                        </Text>
                        <Text style={{ fontSize: FontSize.md, color: theme.text, lineHeight: 22 }}>{reply.content}</Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                )}
              </View>
            </TouchableOpacity>
          ))}
          {commentHasMore && (
            <TouchableOpacity activeOpacity={0.7} onPress={() => loadComments(commentPage + 1, true)} style={{ alignItems: 'center', paddingVertical: Spacing.md }}>
              <Text style={{ fontSize: FontSize.sm, color: theme.primary }}>加载更多评论</Text>
            </TouchableOpacity>
          )}
        </View>
      </ScrollView>

      {/* Bottom Action Bar */}
      {replyTo && (
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: Spacing.lg, paddingVertical: Spacing.xs, backgroundColor: theme.primary + '14' }}>
          <Text style={{ fontSize: FontSize.xs, color: theme.primary }}>回复 @{replyTo.user?.nickname ?? replyTo.authorNickname ?? '匿名用户'}</Text>
          <TouchableOpacity hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }} onPress={() => setReplyTo(null)}>
            <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>取消</Text>
          </TouchableOpacity>
        </View>
      )}
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
