import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { View, Text, Image, TouchableOpacity, TextInput, FlatList, ActivityIndicator, Alert } from 'react-native'
import { wishApi } from '@/api/wish'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'
import type { ApiResponse, WishCommentItem } from '@/types'

/**
 * 心愿评论模块（Sprint 1.2，APP 端）。
 *
 * 结构：扁平列表 + parentId/replyToNickname（回复缩进前端组装）；
 * 页面滚动由本组件的 FlatList 承载（headerComponent 插槽渲染心愿内容与互动区），
 * 评论通过 onEndReached 触底加载（cursor 分页）；仅作者本人可删除自己的评论（软删）。
 */

const COMMENT_PAGE_SIZE = 10
const COMMENT_CONTENT_MAX = 500

interface WishCommentSectionProps {
  wishId: number
  commentCount: number
  isLoggedIn: boolean
  currentUserId?: number
  onCountChange: (delta: number) => void
  onRequireLogin: () => void
  /** 渲染在评论区上方的页面内容（媒体/信息卡/互动按钮组） */
  headerComponent?: ReactNode
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleString('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/** 业务错误提示（APP 端 request 不统一弹错，组件自行 Alert） */
function alertBusinessError(body: ApiResponse<unknown>, fallback: string) {
  Alert.alert('提示', body.error?.message ?? fallback)
}

export default function WishCommentSection({
  wishId,
  commentCount,
  isLoggedIn,
  currentUserId,
  onCountChange,
  onRequireLogin,
  headerComponent,
}: WishCommentSectionProps) {
  const [comments, setComments] = useState<WishCommentItem[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [content, setContent] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [replyTo, setReplyTo] = useState<WishCommentItem | null>(null)
  /** 防重复请求与过期响应覆盖 */
  const requestSeq = useRef(0)

  const loadFirstPage = useCallback(async () => {
    const seq = ++requestSeq.current
    setLoading(true)
    try {
      const res = await wishApi.listComments(wishId, { pageSize: COMMENT_PAGE_SIZE })
      if (seq !== requestSeq.current) return
      if (res.data?.success) {
        setComments(res.data.data)
        setCursor(res.data.meta?.nextCursor ?? null)
        setHasMore(Boolean(res.data.meta?.hasMore))
      }
    } catch {
      // 评论加载失败不阻断详情展示
    } finally {
      if (seq === requestSeq.current) setLoading(false)
    }
  }, [wishId])

  useEffect(() => {
    loadFirstPage()
  }, [loadFirstPage])

  const loadMore = useCallback(async () => {
    if (!hasMore || loadingMore || !cursor) return
    const seq = ++requestSeq.current
    setLoadingMore(true)
    try {
      const res = await wishApi.listComments(wishId, { cursor, pageSize: COMMENT_PAGE_SIZE })
      if (seq !== requestSeq.current) return
      if (res.data?.success) {
        // cursor 分页按 id 去重合并，防御服务端数据变动导致的重复
        setComments((prev) => {
          const seen = new Set(prev.map((c) => c.id))
          return [...prev, ...res.data.data.filter((c) => !seen.has(c.id))]
        })
        setCursor(res.data.meta?.nextCursor ?? null)
        setHasMore(Boolean(res.data.meta?.hasMore))
      }
    } catch {
      // 加载更多失败静默，用户可再次触底重试
    } finally {
      if (seq === requestSeq.current) setLoadingMore(false)
    }
  }, [wishId, hasMore, loadingMore, cursor])

  const startReply = (comment: WishCommentItem) => {
    if (!isLoggedIn) {
      Alert.alert('提示', '登录后即可回复', [
        { text: '取消', style: 'cancel' },
        { text: '去登录', onPress: onRequireLogin },
      ])
      return
    }
    setReplyTo(comment)
  }

  const handleSubmit = async () => {
    const trimmed = content.trim()
    if (!trimmed) {
      Alert.alert('提示', '写点什么再发送吧')
      return
    }
    setSubmitting(true)
    try {
      const res = await wishApi.createComment(wishId, {
        content: trimmed,
        parentId: replyTo?.id,
      })
      if (res.data?.success) {
        setContent('')
        setReplyTo(null)
        onCountChange(1)
        loadFirstPage()
        Alert.alert('提示', replyTo ? '回复已发送' : '评论已发表')
      } else if (res.data) {
        alertBusinessError(res.data, '发表失败，请稍后重试')
      }
    } catch {
      Alert.alert('错误', '发表失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  const handleDelete = (commentId: number) => {
    Alert.alert('删除评论', '删除后不可恢复，确定删除吗？', [
      { text: '取消', style: 'cancel' },
      {
        text: '删除',
        style: 'destructive',
        onPress: async () => {
          try {
            const res = await wishApi.deleteComment(wishId, commentId)
            if (res.data?.success) {
              setComments((prev) => prev.filter((c) => c.id !== commentId))
              onCountChange(-1)
            } else if (res.data) {
              alertBusinessError(res.data, '删除失败，请稍后重试')
            }
          } catch {
            Alert.alert('错误', '删除失败，请稍后重试')
          }
        },
      },
    ])
  }

  const renderItem = ({ item }: { item: WishCommentItem }) => {
    const isReply = item.parentId !== null
    const isMine = item.userId === currentUserId
    return (
      <View style={{ flexDirection: 'row', paddingTop: Spacing.md, paddingLeft: isReply ? Spacing.xl : 0 }}>
        {item.avatar ? (
          <Image source={{ uri: item.avatar }} style={{ width: 32, height: 32, borderRadius: 16 }} />
        ) : (
          <View
            style={{
              width: 32,
              height: 32,
              borderRadius: 16,
              backgroundColor: 'rgba(255,255,255,0.1)',
              justifyContent: 'center',
              alignItems: 'center',
            }}
          >
            <Text style={{ fontSize: 12, color: WishColors.accentGold }}>★</Text>
          </View>
        )}
        <View style={{ flex: 1, marginLeft: Spacing.sm }}>
          <View style={{ flexDirection: 'row', alignItems: 'center' }}>
            <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary, fontWeight: '600' }}>
              {item.nickname}
            </Text>
            <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginLeft: 'auto' }}>
              {formatTime(item.createdAt)}
            </Text>
          </View>
          {/* content 后端已 XSS 转义，可直接渲染 */}
          <Text style={{ fontSize: FontSize.md, color: WishColors.text, lineHeight: 22, marginTop: 2 }}>
            {isReply && item.replyToNickname ? `回复 @${item.replyToNickname}：` : ''}
            {item.content}
          </Text>
          <View style={{ flexDirection: 'row', gap: Spacing.lg, marginTop: Spacing.xs }}>
            <TouchableOpacity onPress={() => startReply(item)} accessibilityLabel={`回复 ${item.nickname} 的评论`}>
              <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>回复</Text>
            </TouchableOpacity>
            {isMine && (
              <TouchableOpacity onPress={() => handleDelete(item.id)} accessibilityLabel="删除我的评论">
                <Text style={{ fontSize: FontSize.xs, color: 'rgba(233,69,96,0.75)' }}>删除</Text>
              </TouchableOpacity>
            )}
          </View>
        </View>
      </View>
    )
  }

  const renderListFooter = () => {
    if (loadingMore) {
      return (
        <View style={{ paddingVertical: Spacing.md, alignItems: 'center' }}>
          <ActivityIndicator size="small" color={WishColors.textTertiary} />
        </View>
      )
    }
    if (hasMore) {
      return (
        <View style={{ paddingVertical: Spacing.md, alignItems: 'center' }}>
          <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>上拉加载更多</Text>
        </View>
      )
    }
    if (comments.length > 0) {
      return (
        <View style={{ paddingVertical: Spacing.md, alignItems: 'center' }}>
          <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>已经到底啦~</Text>
        </View>
      )
    }
    return null
  }

  return (
    <FlatList
      data={comments}
      keyExtractor={(item) => String(item.id)}
      renderItem={renderItem}
      onEndReached={loadMore}
      onEndReachedThreshold={0.3}
      ListHeaderComponent={
        <View>
          {headerComponent}
          {/* 评论区标题 + 输入区 */}
          <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: Spacing.sm, marginTop: Spacing.lg }}>
            <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: WishColors.text }}>评论</Text>
            <Text style={{ fontSize: FontSize.sm, color: WishColors.textTertiary }}>{commentCount}</Text>
          </View>
          {isLoggedIn ? (
            <View style={{ marginTop: Spacing.md }}>
              {replyTo && (
                <View
                  style={{
                    flexDirection: 'row',
                    alignItems: 'center',
                    padding: Spacing.sm,
                    borderRadius: BorderRadius.sm,
                    backgroundColor: 'rgba(0,212,255,0.08)',
                    marginBottom: Spacing.sm,
                  }}
                >
                  <Text style={{ flex: 1, fontSize: FontSize.sm, color: WishColors.accentCyan }}>
                    回复 @{replyTo.nickname}
                  </Text>
                  <TouchableOpacity onPress={() => setReplyTo(null)} accessibilityLabel="取消回复">
                    <Text style={{ fontSize: FontSize.sm, color: WishColors.textTertiary }}>取消</Text>
                  </TouchableOpacity>
                </View>
              )}
              <TextInput
                value={content}
                onChangeText={(t) => setContent(t.slice(0, COMMENT_CONTENT_MAX))}
                placeholder={replyTo ? `回复 @${replyTo.nickname}...` : '写下你的鼓励与祝福...'}
                placeholderTextColor={WishColors.textTertiary}
                multiline
                editable={!submitting}
                style={{
                  minHeight: 84,
                  padding: Spacing.md,
                  borderRadius: BorderRadius.md,
                  backgroundColor: 'rgba(255,255,255,0.08)',
                  color: WishColors.text,
                  fontSize: FontSize.md,
                  textAlignVertical: 'top',
                }}
              />
              <TouchableOpacity
                style={{
                  marginTop: Spacing.sm,
                  paddingVertical: Spacing.md,
                  borderRadius: BorderRadius.xl,
                  alignItems: 'center',
                  backgroundColor: WishColors.primary,
                  opacity: content.trim() && !submitting ? 1 : 0.5,
                }}
                disabled={!content.trim() || submitting}
                onPress={handleSubmit}
                accessibilityLabel={replyTo ? '发送回复' : '发表评论'}
              >
                <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: '#fff' }}>
                  {submitting ? '发送中...' : replyTo ? '发送回复' : '发表评论'}
                </Text>
              </TouchableOpacity>
            </View>
          ) : (
            <TouchableOpacity
              style={{
                marginTop: Spacing.md,
                paddingVertical: Spacing.md,
                borderRadius: BorderRadius.lg,
                alignItems: 'center',
                borderWidth: 1,
                borderColor: WishColors.border,
                backgroundColor: 'rgba(255,255,255,0.04)',
              }}
              onPress={onRequireLogin}
              accessibilityLabel="登录后发表评论"
            >
              <Text style={{ fontSize: FontSize.md, color: WishColors.textSecondary }}>登录后发表评论</Text>
            </TouchableOpacity>
          )}
          {/* 首屏加载/空态（列表项由 renderItem 渲染） */}
          {loading ? (
            <View style={{ paddingVertical: Spacing.xl, alignItems: 'center' }}>
              <ActivityIndicator size="small" color={WishColors.textTertiary} />
            </View>
          ) : comments.length === 0 ? (
            <View style={{ paddingVertical: Spacing.xl, alignItems: 'center', gap: Spacing.sm }}>
              <Text style={{ fontSize: 28, opacity: 0.4 }}>💬</Text>
              <Text style={{ fontSize: FontSize.sm, color: WishColors.textTertiary }}>
                还没有评论，来写下第一条祝福吧
              </Text>
            </View>
          ) : (
            <View style={{ borderTopWidth: 1, borderTopColor: WishColors.border, marginTop: Spacing.sm }} />
          )}
        </View>
      }
      ListFooterComponent={renderListFooter}
      contentContainerStyle={{ padding: Spacing.md, paddingBottom: 120 }}
      keyboardShouldPersistTaps="handled"
    />
  )
}
